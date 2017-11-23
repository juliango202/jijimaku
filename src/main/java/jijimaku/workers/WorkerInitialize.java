package jijimaku.workers;

import java.io.File;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;
import jijimaku.models.ServicesParam;
import jijimaku.services.Config;
import jijimaku.services.SubtitleService;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.langparser.JapaneseParser;
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
      throw new Exception("Worker should not run on the EDT thread!");
    }

    LOGGER.info("-------------------------- Initialization --------------------------");
    String appDirectory = FileManager.getAppDirectory();
    LOGGER.debug("Application directory seems to be {}", appDirectory);

    // Load configuration
    LOGGER.info("Loading configuration...");
    File configFile = new File(appDirectory + "/" + configFilePath);
    if (!configFile.exists()) {
      LOGGER.error("Could not find config file {} in directory {}", configFilePath, appDirectory);
      throw new UnexpectedError();
    }

    Config config = new Config(configFile);

    // Initialize dictionary
    LOGGER.info("Loading dictionnary...");
    File dictionaryFile = new File(appDirectory + "/" + config.getJijiDictionary());
    if (!dictionaryFile.exists()) {
      LOGGER.error("Could not find the dictionary file {} in directory {}", config.getJijiDictionary(), appDirectory);
      throw new UnexpectedError();
    }
    JijiDictionary dict = new JijiDictionary(dictionaryFile);

    // Initialize parser
    LOGGER.info("Instantiate parser...");
    JapaneseParser langParser = new JapaneseParser(config);

    // Read subtitles styles from config
    SubtitleService subtitleService = new SubtitleService(config);

    LOGGER.info("Ready to work!");

    return new ServicesParam(config, dict, langParser, subtitleService);
  }
}

