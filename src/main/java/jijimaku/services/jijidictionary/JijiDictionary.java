package jijimaku.services.jijidictionary;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedError;
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
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DICTIONARY_INFO_KEY = "about_this_dictionary";
  private static final String SENSE_KEY = "sense";
  private static final String SENSES_KEY = "senses";
  private static final String PRONUNCIATION_KEY = "pronunciation";
  private static final String FREQUENCY_KEY = "frequency";
  private static final String LEMMAS_SPLIT_RE = "\\s*,\\s*";
  private static final String PRONUNCIATION_SPLIT_RE = "\\s*,\\s*";

  private Map<String, List<JijiDictionaryEntry>> entriesByLemma = new HashMap<>();
  private Map<String, List<JijiDictionaryEntry>> entriesByPronunciation = new HashMap<>();

  @SuppressWarnings("unchecked")
  public JijiDictionary(String jijiDictFile) {
    try {
      Yaml yaml = new Yaml();
      InputStream stream = FileManager.getUtf8Stream(new File(jijiDictFile));
      Map<String, Object> yamlMap = (Map<String, Object>) yaml.load(stream);
      yamlMap.keySet().stream().forEach(key -> {
        if (!key.equals(DICTIONARY_INFO_KEY)) {

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
        }
      });
    } catch (IOException exc) {
      LOGGER.error("Problem reading jijiDictFile {}", jijiDictFile);
      LOGGER.debug("Exception details", exc);
      throw new UnexpectedError();
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
}

