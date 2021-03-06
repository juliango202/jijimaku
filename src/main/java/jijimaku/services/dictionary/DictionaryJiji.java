package jijimaku.services.dictionary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import jijimaku.AppConfig;
import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.services.LanguageService;
import jijimaku.services.LanguageService.Language;
import jijimaku.utils.FileManager;


/**
 * Simple YAML parsing to load jiji dictionary data into a java Hashmap.
 */
@SuppressWarnings("checkstyle")
public class DictionaryJiji implements Dictionary {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String DICT_INFO_KEY = "_about_this_dictionary";
  private static final String DICT_TITLE_KEY = "title";
  private static final String DICT_LANGUAGES_KEY = "languages";
  private static final String DICT_LANGUAGES_FROM_KEY = "from";

  private static final String SENSE_KEY = "sense";
  private static final String SENSES_KEY = "senses";
  private static final String PRONUNCIATION_KEY = "pronunciation";
  private static final String TAGS_KEY = "tags";
  private static final String LEMMAS_SPLIT_RE = "\\s*,\\s*";
  private static final String PRONUNCIATIONS_SPLIT_RE = "\\s*,\\s*";
  private static final String TAGS_SPLIT_RE = "\\s*,\\s*";

  private String title;
  private Language languageFrom;

  @SuppressWarnings("unchecked")
  private void parseAboutThisDictionary(Object yamlObj) {
    Map<String, Object> infoMap = (Map<String, Object>) yamlObj;
    title = (String)infoMap.get(DICT_TITLE_KEY);
    Map<String, Object> langMap = (Map<String, Object>) infoMap.get(DICT_LANGUAGES_KEY);
    String languageFromStr = (String)langMap.get(DICT_LANGUAGES_FROM_KEY);
    languageFrom = LanguageService.getLanguageFromStr(languageFromStr);
    if (languageFrom == null) {
      LOGGER.error("Cannot detect language of Jiji dictionary");
      throw new UnexpectedCriticalError();
    }
  }

  @SuppressWarnings("unchecked")
  public DictionaryJiji(File jijiDictFile, AppConfig config) {
    try {
      Yaml yaml = new Yaml();
      String yamlStr = FileManager.fileAnyEncodingToString(jijiDictFile);
      Map<String, Object> yamlMap = (Map<String, Object>) yaml.load(yamlStr);

      this.parseAboutThisDictionary(yamlMap.get(DICT_INFO_KEY));
      LOGGER.info("Using {} dictionary '{}'", languageFrom, title);

      yamlMap.keySet().stream().forEach(key -> {
        if (key.equals(DICT_INFO_KEY)) {
          return;
        }

        // Parse a word entry
        Map<String, Object> entryMap = (Map<String, Object>) yamlMap.get(key);

        // Parse senses
        List<String> senses = new ArrayList<>();
        if (entryMap.containsKey(SENSE_KEY)) {
          senses.add((String)entryMap.get(SENSE_KEY));
        } else if (entryMap.containsKey(SENSES_KEY)) {
          senses.addAll((ArrayList<String>)entryMap.get(SENSES_KEY));
        } else {
          LOGGER.error("Jiji dictionary entry {} has no sense defined.", key);
          return;
        }

        List<String> pronunciations = null;
        if (entryMap.containsKey(PRONUNCIATION_KEY)) {
          String pronunciationStr = ((String)entryMap.get(PRONUNCIATION_KEY));
          pronunciations = Arrays.asList(pronunciationStr.split(PRONUNCIATIONS_SPLIT_RE));
        }

        Set<String> tags = null;
        if (entryMap.containsKey(TAGS_KEY)) {
          String tagStr = ((String)entryMap.get(TAGS_KEY));
          tags = new HashSet<>(Arrays.asList(tagStr.split(TAGS_SPLIT_RE)));
        }

        // Create Jiji dictionary entry
        List<String> lemmas = Arrays.asList(key.split(LEMMAS_SPLIT_RE));
        addEntry(lemmas, senses, pronunciations, tags, config);
      });

      loadLanguageTags();
    } catch (IOException exc) {
      LOGGER.error("Problem reading jijiDictFile {}", jijiDictFile.getAbsolutePath());
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }
  }

  public String getTitle() {
    return title;
  }

  public Language getLanguageFrom() {
    return languageFrom;
  }

  public Logger getLogger() {
    return LOGGER;
  }
}

