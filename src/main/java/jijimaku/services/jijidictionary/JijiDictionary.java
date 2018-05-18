package jijimaku.services.jijidictionary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.utils.FileManager;

//private Trie atrie;
//atrie = atrieBuilder.build();
//final Trie.TrieBuilder atrieBuilder = Trie.builder().removeOverlaps();
//public String getParse(String text) {
//    Collection<Token> tokens = atrie.tokenize(text);
//    String res = "";
//    for (Token token : tokens) {
//    if (token.isMatch()) {
//    res += token.getFragment() + "|";
//    } else {
//    res += "*|";
//    }
//    }
//    return res;
//    }

// Simple XML SAX parser to load JMDict xml data into a java Hashmap
@SuppressWarnings("checkstyle")
public class JijiDictionary {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String DICT_INFO_KEY = "_about_this_dictionary";
  private static final String DICT_TITLE_KEY = "title";
  private static final String DICT_LICENCE_KEY = "licence";
  private static final String DICT_LANGUAGES_KEY = "languages";
  private static final String DICT_LANGUAGES_FROM_KEY = "from";
  private static final String DICT_LANGUAGES_TO_KEY = "to";

  private static final String SENSE_KEY = "sense";
  private static final String SENSES_KEY = "senses";
  private static final String PRONUNCIATION_KEY = "pronunciation";
  private static final String FREQUENCY_KEY = "frequency";
  private static final String LEMMAS_SPLIT_RE = "\\s*,\\s*";
  private static final String PRONUNCIATION_SPLIT_RE = "\\s*,\\s*";

  private Map<String, List<JijiDictionaryEntry>> entriesByLemma = new HashMap<>();
  private Map<String, List<JijiDictionaryEntry>> entriesByPronunciation = new HashMap<>();
  private String title;
  private String licence;
  private String languageFrom;
  private String languageTo;

  @SuppressWarnings("unchecked")
  private void parseAboutThisDictionary(Object yamlObj) {
    Map<String, Object> infoMap = (Map<String, Object>) yamlObj;
    title = (String)infoMap.get(DICT_TITLE_KEY);
    licence = (String)infoMap.get(DICT_LICENCE_KEY);
    Map<String, Object> langMap = (Map<String, Object>) infoMap.get(DICT_LANGUAGES_KEY);
    languageFrom = (String)langMap.get(DICT_LANGUAGES_FROM_KEY);
    languageTo = (String)langMap.get(DICT_LANGUAGES_TO_KEY);
  }

  @SuppressWarnings("unchecked")
  public JijiDictionary(File jijiDictFile) {
    try {
      Yaml yaml = new Yaml();
      String yamlStr = FileManager.fileAnyEncodingToString(jijiDictFile);
      Map<String, Object> yamlMap = (Map<String, Object>) yaml.load(yamlStr);
      yamlMap.keySet().stream().forEach(key -> {
        if (key.equals(DICT_INFO_KEY)) {
          this.parseAboutThisDictionary(yamlMap.get(key));
          LOGGER.info(String.format("Using %s dictionary '%s'", languageFrom, title));
          return;
        }

        // Parse a word entry
        Map<String, Object> entryMap = (Map<String, Object>) yamlMap.get(key);

        // Parse senses
        List<String> senses = new ArrayList<>();
        List<String> pronunciations = null;
        if (entryMap.containsKey(SENSE_KEY)) {
          senses.add((String)entryMap.get(SENSE_KEY));
        } else if (entryMap.containsKey(SENSES_KEY)) {
          senses.addAll((ArrayList<String>)entryMap.get(SENSES_KEY));
        } else {
          LOGGER.error("Jiji dictionary entry {} has no sense defined.", key);
          return;
        }

        if (entryMap.containsKey(PRONUNCIATION_KEY)) {
          String pronunciationStr = ((String)entryMap.get(PRONUNCIATION_KEY));
          pronunciations = Arrays.asList(pronunciationStr.split(PRONUNCIATION_SPLIT_RE));
        }

        // Parse frequency
        Integer frequency = entryMap.containsKey(FREQUENCY_KEY)
            ? (Integer)entryMap.get(FREQUENCY_KEY)
            : null;

        // Create Jiji dictionary entry
        List<String> lemmas = Arrays.asList(key.split(LEMMAS_SPLIT_RE));
        JijiDictionaryEntry jijiEntry = new JijiDictionaryEntry(lemmas, frequency, senses, pronunciations);

        // Index entries by lemma
        for (String lemma : lemmas) {
          if (!entriesByLemma.containsKey(lemma)) {
            entriesByLemma.put(lemma, new ArrayList<>());
          }
          entriesByLemma.get(lemma).add(jijiEntry);
        }

        // Index entries by pronunciation
        if (pronunciations != null) {
          for (String pronunciation : pronunciations) {
            if (!entriesByPronunciation.containsKey(pronunciation)) {
              entriesByPronunciation.put(pronunciation, new ArrayList<>());
            }
            entriesByPronunciation.get(pronunciation).add(jijiEntry);
          }
        }
      });
    } catch (IOException exc) {
      LOGGER.error("Problem reading jijiDictFile {}", jijiDictFile.getAbsolutePath());
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }
  }

  /**
   * Search for a lemma in the dictionary.
   */
  public List<JijiDictionaryEntry> search(String w) {
    if (entriesByLemma.containsKey(w)) {
      return entriesByLemma.get(w);
    } else {
      return java.util.Collections.emptyList();
    }
  }

  /**
   * Search an entry by pronounciation.
   */
  public List<JijiDictionaryEntry> searchByPronunciation(String p) {
    if (entriesByPronunciation.containsKey(p)) {
      return entriesByPronunciation.get(p);
    } else {
      return java.util.Collections.emptyList();
    }
  }

  public String getTitle() {
    return title;
  }

  public String getLicence() {
    return licence;
  }

  public String getLanguageFrom() {
    return languageFrom;
  }

  public String getLanguageTo() {
    return languageTo;
  }
}

