package jijimaku.services.dictionary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import jijimaku.AppConfig;
import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.services.LanguageService.Language;
import jijimaku.utils.FileManager;


/**
 * Provide dictionary definitions for words.
 * This interface can be implemented by different classes to support different dictionary formats
 */
public interface Dictionary {

  String LANGUAGE_TAGS_DIR = "language-tags/";

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
   * Add a dictionary entry.
   */
  default void addEntry(List<String> lemmas, List<String> senses, List<String> pronunciations, Set<String> tags, AppConfig config) {
    // Cleanup senses and add default tags for the entry
    senses = cleanupSenses(senses, config.getDictionaryCleanupRegexp());
    DictionaryEntry dictEntry = new DictionaryEntry(lemmas, senses, pronunciations, tags);

    // Index entries by lemma
    for (String lemma : lemmas) {
      if (!entriesByLemma.containsKey(lemma)) {
        entriesByLemma.put(lemma, new ArrayList<>());
      }
      entriesByLemma.get(lemma).add(dictEntry);
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

  /**
   * Load default word tags for the dictionary language.
   * For each language there is a directory containing the language tags text files(*.txt)
   * One text file corresponds to one tag, and must contain one lemma to be tagged per line
   */
  default void loadLanguageTags() {
    String tagsDir = FileManager.getAppDirectory() + "/" + LANGUAGE_TAGS_DIR + getLanguageFrom().toString().toLowerCase();
    try {
      Files.list(Paths.get(tagsDir))
          .filter(s -> s.toString().endsWith(".txt"))
          .forEach(path -> {
            String fileName = path.getFileName().toString();
            String tag = fileName.substring(0, fileName.lastIndexOf("."));
            try {
              Files.lines(path).flatMap(l -> search(l).stream()).forEach(entry -> entry.addTag(tag));
            } catch (IOException exc) {
              getLogger().debug(exc);
              getLogger().error("Error while loading {} language tags {}", getLanguageFrom().toString(), tag);
              throw new UnexpectedCriticalError();
            }
          });
    } catch (IOException exc) {
      getLogger().debug(exc);
      getLogger().error("Error while loading {} language tags", getLanguageFrom().toString());
      throw new UnexpectedCriticalError();
    }
  }

  Logger getLogger();

  String getTitle();

  Language getLanguageFrom();
}
