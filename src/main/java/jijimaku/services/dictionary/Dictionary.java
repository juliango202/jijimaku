package jijimaku.services.dictionary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jijimaku.services.LanguageService.Language;

/**
 * Provide dictionary definitions for words.
 * This interface can be implemented by different classes to support different dictionary formats
 */
public interface Dictionary {

  // Default cleanup of dictionary definitions
  // to get rid of things not important before displaying the text on screen.
  List<String> DEFAULT_CLEANUP_RE = Arrays.asList(
      "【例】.*",  // Remove example sentences in Japanese dictionaries
      "\\(用例\\).*"  // Remove example sentences in Japanese dictionaries
  );

  Map<String, List<DictionaryEntry>> entriesByLemma = new HashMap<>();

  /**
   * Search for a lemma in the dictionary.
   */
  default List<DictionaryEntry> search(String w) {
    if (entriesByLemma.containsKey(w)) {
      return entriesByLemma.get(w);
    } else {
      return java.util.Collections.emptyList();
    }
  }

  /**
   * Cleanup dictionary definitions according to the dictionaryCleanupRegexp config option
   * and apply default cleanup regexps.
   */
  default List<String> cleanupSenses(List<String> senses, String dictionaryCleanupRegexp) {
    return senses.stream()
        .map(s -> {
          for (String re : DEFAULT_CLEANUP_RE) {
            s = s.replaceAll(re, "");
          }
          if (dictionaryCleanupRegexp != null) {
            s = s.replaceAll(dictionaryCleanupRegexp, "");
          }
          return s;
        })
        .collect(Collectors.toList());
  }

  String getTitle();

  Language getLanguageFrom();
}
