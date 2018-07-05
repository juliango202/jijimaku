package jijimaku.services.dictionary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jijimaku.services.LanguageService.Language;

/**
 * Provide dictionary definitions for words.
 * This interface can be implemented by different classes to support different dictionary formats
 */
public interface Dictionary {

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

  String getTitle();

  Language getLanguageFrom();
}
