package jijimaku.services.langparser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.services.LanguageService.Language;
import jijimaku.utils.FileManager;

import cz.cuni.mff.ufal.udpipe.InputFormat;
import cz.cuni.mff.ufal.udpipe.Model;
import cz.cuni.mff.ufal.udpipe.ProcessingError;
import cz.cuni.mff.ufal.udpipe.Sentence;
import cz.cuni.mff.ufal.udpipe.Word;
import cz.cuni.mff.ufal.udpipe.Words;
import cz.cuni.mff.ufal.udpipe.udpipe_java;

//-----------------------------------------------------------------------
// Use UDPipe(http://lindat.mff.cuni.cz/services/udpipe/) to parse languages
//-----------------------------------------------------------------------

public class LangParserUdpipe implements LangParser {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String MODEL_EXT = ".udpipe";
  private static final int SEARCH_MODEL_MAX_DEPTH = 3;

  private Model model;
  private InputFormat tokenizer;
  private Language language;

  public LangParserUdpipe(Language language) {
    String udpipeNativeLibPath = getUdPipeNativeLibPath();
    try {
      udpipe_java.setLibraryPath(udpipeNativeLibPath);
    } catch (Exception exc) {
      LOGGER.debug(exc);
      LOGGER.error("Error while trying to load udpipe native library " + udpipeNativeLibPath);
      throw new UnexpectedCriticalError();
    }
    this.language = language;
    model = getUdpipeModel();
    tokenizer = model.newTokenizer(Model.getDEFAULT());
    LOGGER.debug("Parsing using UDPipe for language " + language.toString());
  }

  private String getUdPipeNativeLibPath() {
    String baseDir = FileManager.getAppDirectory() + "/lib/udpipe-1.2.0";
    String archSuffix = System.getProperty("os.arch").contains("64") ? "64" : "32";
    String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
    if ((osName.contains("mac")) || (osName.contains("darwin"))) {
      return baseDir + "/bin-osx/libudpipe_java.dylib";
    } else if (osName.contains("win")) {
      return baseDir + "/bin-win" + archSuffix + "/udpipe_java.dll";
    } else if (osName.contains("nux")) {
      return baseDir + "/bin-linux" + archSuffix + "/libudpipe_java.so";
    } else {
      LOGGER.error("Cannot detect the OS to target the correct UdPipe native lib: " + osName);
      throw new UnexpectedCriticalError();
    }
  }

  private Model getUdpipeModel() {
    // Look in app directory for a udpipe model file that matches the dictionary language
    String modelFile;
    try (Stream<Path> stream = Files.walk(Paths.get(FileManager.getAppDirectory()), SEARCH_MODEL_MAX_DEPTH)) {
      List<String> models = stream
          .filter(path -> path.getFileName().toString().endsWith(MODEL_EXT)
              && path.getFileName().toString().toLowerCase().startsWith(language.toString().toLowerCase()))
          .sorted((p1, p2) -> {
            try {
              return Long.compare(Files.size(p2), Files.size(p1));
            } catch (IOException e) {
              LOGGER.error("Problem getting file size of {} {}", p1, p2);
              return 0;
            }
          })
          .map(String::valueOf)
          .collect(Collectors.toList());
      if (models.isEmpty()) {
        LOGGER.error("Cannot find a parser model file({}) for the language '{}'.", MODEL_EXT, language.toString());
        throw new UnexpectedCriticalError();
      } else if (models.size() > 1) {
        String allModels = models.stream().collect(Collectors.joining(", "));
        LOGGER.warn("Found {} models for language '{}', the largest one will be used: {}",
            models.size(), language.toString(), allModels);
      }

      modelFile = models.get(0);
      LOGGER.debug("Using udpipe model file " + models.get(0));
    } catch (IOException exc) {
      LOGGER.debug(exc);
      LOGGER.error("Error while searching for a parser model file '{}'", MODEL_EXT);
      throw new UnexpectedCriticalError();
    }

    Model udpipeModel = Model.load(modelFile);
    if (udpipeModel == null) {
      LOGGER.error("Cannot load parser model from file '{}'", modelFile);
      throw new UnexpectedCriticalError();
    }
    return udpipeModel;
  }

  /**
   * Use the UDPipe API to parse sentences in a text.
   */
  private List<Sentence> parseSentences(String text) {
    tokenizer.setText(text);
    List<Sentence> sentences = new ArrayList<>();
    Sentence sentence = new Sentence();
    ProcessingError error = new ProcessingError();
    while (tokenizer.nextSentence(sentence, error)) {
      sentences.add(sentence);
      sentence = new Sentence();
    }
    if (error.occurred()) {
      LOGGER.warn("UDPipe returned an error while parsing sentences in {}: {}", text, error.getMessage());
      return new ArrayList<>();
    }
    return sentences;
  }

  /**
   * Use the UDPipe library to parse a text, and map the results to our custom TextToken class.
   */
  @Override
  public List<TextToken> syntaxicParse(String text) {
    ProcessingError error = new ProcessingError();
    List<TextToken> tokens = new ArrayList<>();
    for (Sentence s : parseSentences(text)) {

      model.tag(s, Model.getDEFAULT(), error);
      if (error.occurred()) {
        LOGGER.warn("UDPipe returned an error while tagging {}: {}", text, error.getMessage());
        continue;
      }
      model.parse(s, Model.getDEFAULT(), error);
      if (error.occurred()) {
        LOGGER.warn("UDPipe returned an error while parsing {}: {}", text, error.getMessage());
        continue;
      }

      Words words = s.getWords();
      for (int i = 1; i < words.size(); i++) {
        Word w = words.get(i);

        String writtenForm = w.getForm();
        if (writtenForm == null || writtenForm.isEmpty()) {
          LOGGER.warn("UDPipe returned an invalid or empty word while parsing {}", text);
          continue;
        }
        PosTag pos;
        try {
          pos = PosTag.valueOf(w.getUpostag());
        } catch (IllegalArgumentException exc) {
          LOGGER.warn("UDPipe returned an invalid POS tag for word {}", writtenForm);
          continue;
        }

        String firstCanonicalForm = w.getLemma();
        if (firstCanonicalForm == null || firstCanonicalForm.isEmpty()) {
          LOGGER.warn("UDPipe returned an invalid lemma for word {}", writtenForm);
          firstCanonicalForm = null;
        } else if (firstCanonicalForm.equals(writtenForm)) {
          firstCanonicalForm = null;
        }

        tokens.add(new TextToken(pos, writtenForm, firstCanonicalForm, null));
      }
    }
    return tokens;
  }

  public Language getLanguage() {
    return language;
  }

  public Logger getLogger() {
    return LOGGER;
  }
}
