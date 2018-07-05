package jijimaku.workers;

import java.io.File;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.AppConfig;
import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.models.ServicesParam;
import jijimaku.services.LanguageService;
import jijimaku.services.dictionary.Dictionary;
import jijimaku.services.dictionary.DictionaryJiji;
import jijimaku.services.dictionary.DictionaryLingoesLd2;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParserKuromoji;
import jijimaku.services.langparser.LangParserUdpipe;
import jijimaku.utils.FileManager;


/**
 * Swing worker that initializes all services in a background thread.
 */
public class WorkerInitialize extends SwingWorker<ServicesParam, Object> {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private final String configFilePath;

  /**
   * Constructor.
   * @param configFilePath path to the application config.yaml
   */
  public WorkerInitialize(String configFilePath) {
    this.configFilePath = configFilePath;
  }

  @Override
  public ServicesParam doInBackground() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new RuntimeException("Worker should not run on the EDT thread!");
    }

    LOGGER.info("-------------------------- Initialization --------------------------");
    String appDirectory = FileManager.getAppDirectory();
    LOGGER.debug("Application directory seems to be {}", appDirectory);

    // Load configuration
    LOGGER.info("Loading configuration...");
    File configFile = new File(appDirectory + "/" + configFilePath);
    if (!configFile.exists()) {
      LOGGER.error("Could not find config file {} in directory '{}'", configFilePath, appDirectory);
      throw new UnexpectedCriticalError();
    }

    AppConfig config = new AppConfig(configFile);

    // Initialize dictionary
    LOGGER.info("Loading dictionary...");
    File dictionaryFile = new File(appDirectory + "/" + config.getDictionary());
    if (!dictionaryFile.exists()) {
      LOGGER.error("Could not find the dictionary file {} in directory {}", config.getDictionary(), appDirectory);
      throw new UnexpectedCriticalError();
    }

    Dictionary dict;
    if (config.getDictionary().toLowerCase().endsWith(".ld2")) {
      dict = new DictionaryLingoesLd2(dictionaryFile, config);
    } else {
      dict = new DictionaryJiji(dictionaryFile, config);
    }

    // Initialize parser
    LOGGER.info("Instantiate parser...");
    LangParser langParser;
    if (dict.getLanguageFrom() == LanguageService.Language.JAPANESE) {
      langParser = new LangParserKuromoji(config);
    } else {
      langParser = new LangParserUdpipe(dict.getLanguageFrom());
    }
    LOGGER.info("Ready to work!");

    return new ServicesParam(config, dict, langParser);
  }
}

