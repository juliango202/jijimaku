package jijimaku.services.langparser;

import cz.cuni.mff.ufal.udpipe.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jijimaku.AppConfig;
import jijimaku.utils.FileManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


//-----------------------------------------------------------------------
// Use UDPipe(http://lindat.mff.cuni.cz/services/udpipe/) to parse languages
//-----------------------------------------------------------------------

public class LangParserUDPipe implements LangParser {
  private static final Logger LOGGER;
  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String MODEL_EXT = ".udpipe";
  private static final int SEARCH_MODEL_MAX_DEPTH = 3;

  private Model model;
  private InputFormat tokenizer;

  public LangParserUDPipe(AppConfig config, String language) {
    model = getUDPipeModel(language);
    tokenizer = model.newTokenizer(Model.getDEFAULT());
  }

  private Model getUDPipeModel(String language) {
    // Look in app directory for a udpipe model file that matches the dictionary language
    String modelFile = null;
    try (Stream<Path> stream = Files.walk(Paths.get(FileManager.getAppDirectory()), SEARCH_MODEL_MAX_DEPTH)) {
      List<String> models = stream
          .map(String::valueOf)
          .filter(path -> path.toLowerCase().endsWith(MODEL_EXT) && path.toLowerCase().contains(language.toLowerCase()))
          .sorted()
          .collect(Collectors.toList());
      if (models.isEmpty()) {
        LOGGER.error(String.format("Cannot find a parser model file(%s) for the language '%s'.", MODEL_EXT, language));
        System.exit(1);
      } else if (models.size() > 1) {
        String allModels = models.stream().collect(Collectors.joining(", "));
        LOGGER.warn(String.format("Found %d models for language '%s', the first one will be used: %s", models.size(), language, allModels));
      }

      modelFile = models.get(0);
      LOGGER.info("Using udpipe model file " + models.get(0));
    } catch (IOException exc) {
      LOGGER.error("Error while searching for a parser model file("+ MODEL_EXT +").", exc);
      System.exit(1);
    }

    Model model = Model.load(modelFile);
    if (model == null) {
      LOGGER.error(String.format("Cannot load parser model from file '%s'", modelFile));
      System.exit(1);
    }
    return model;
  }

  /**
   * Use the UDPipe API to parse sentences in a text
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
      LOGGER.warn(String.format("UDPipe returned an error while parsing sentences in %s: %s", text, error.getMessage()));
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
        LOGGER.warn(String.format("UDPipe returned an error while tagging %s: %s", text, error.getMessage()));
        continue;
      }
      model.parse(s, Model.getDEFAULT(), error);
      if (error.occurred()) {
        LOGGER.warn(String.format("UDPipe returned an error while parsing %s: %s", text, error.getMessage()));
        continue;
      }

      Words words = s.getWords();
      for (int i = 1; i < words.size(); i++) {
        Word w = words.get(i);

        String writtenForm = w.getForm();
        if (writtenForm == null || writtenForm.isEmpty()) {
          LOGGER.warn(String.format("UDPipe returned an invalid or empty word while parsing %s", text));
          continue;
        }
        PosTag pos = null;
        try {
          pos = PosTag.valueOf(w.getUpostag());
        } catch (IllegalArgumentException exc) {
          LOGGER.warn(String.format("UDPipe returned an invalid POS tag for word %s", writtenForm));
          continue;
        }

        String firstCanonicalForm = w.getLemma();
        if (firstCanonicalForm == null || firstCanonicalForm.isEmpty()) {
          LOGGER.warn(String.format("UDPipe returned an invalid lemma for word %s", writtenForm));
        }

        tokens.add(new TextToken(pos, writtenForm, firstCanonicalForm, null));
      }
    }
    return tokens;
  }
}
