package jijimaku.services.dictionary;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.services.LanguageService;
import jijimaku.services.LanguageService.Language;
import jijimaku.utils.FileManager;

import cn.kk.extractor.lingoes.LingoesLd2Extractor;


public class DictionaryLingoesLd2 implements Dictionary {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final List<String> CLEANUP_RE = Arrays.asList(
      "【例】.*"  // Remove example sentences in Japanese dictionaries
  );

  private String title;
  private Language languageFrom;

  public DictionaryLingoesLd2(File dictFile, String dictLanguageConfig) {
    Map<String,String> definitions;
    try {
      title = dictFile.getName();
      LingoesLd2Extractor extractor = new LingoesLd2Extractor();
      definitions = extractor.extractLd2ToMap(dictFile);
    } catch (Exception exc) {
      LOGGER.error("Problem reading LD2 dictionary file {}", dictFile.getAbsolutePath());
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }

    for (Map.Entry<String, String> def : definitions.entrySet()) {
      String lemma = def.getKey();
      // Strip lemma from comma (happen in some dictionaries)
      lemma = lemma.replaceAll("^,|,$", "");

      if (lemma.contains(",") || lemma.contains(";")) {
        LOGGER.error("Lemma {} contains a comma", lemma);
        continue;
      }

      if (!entriesByLemma.containsKey(lemma)) {
        entriesByLemma.put(lemma, new ArrayList<>());
      }
      String value = def.getValue();
      // this is a reference to another entry
      if (definitions.containsKey(value)) {
        value = definitions.get(value);
      }

      // Cleanup ld2 dictionary value
      for (String re : CLEANUP_RE) {
        value = value.replaceAll(re, "");
      }

      List<String> lemmas = Arrays.asList(lemma);
      List<String> senses = Arrays.asList(value);
      DictionaryEntry dictEntry = new DictionaryEntry(lemmas, senses, null, null);
      entriesByLemma.get(lemma).add(dictEntry);
    }

    languageFrom = detectLanguage(dictLanguageConfig, dictFile.getName(), definitions);
    if (languageFrom == null) {
      LOGGER.error("Cannot detect language of LD2 dictionary {}, please use the "
          + "'dictionaryLanguage' config option set to a correct language", dictFile.getAbsolutePath());
      throw new UnexpectedCriticalError();
    }
    LOGGER.info("Using {} dictionary '{}'", languageFrom, title);
  }

  private Language detectLanguage(String dictLanguageConfig, String dictFileName, Map<String,String> definitions) {
    Language detected = null;

    // Read dictionary language from config
    if (dictLanguageConfig != null && !dictLanguageConfig.isEmpty()) {
      detected = LanguageService.getLanguageFromStr(dictLanguageConfig);
    }

    // Detect dictionary language from file name
    if (detected == null) {
      detected = LanguageService.detectFromFilename(dictFileName);
    }

    // Perform language detection on the longuest entries for more accuracy
    if (detected == null) {
      // Perform language detection on the longuest entries for more accuracy
      List<String> longestWords = definitions
          .keySet().stream()
          .sorted((String s1, String s2) -> Integer.compare(s2.length(), s1.length()))
          .limit(5)
          .collect(Collectors.toList());
      detected = LanguageService.detectFromList(longestWords);
    }
    return detected;
  }

  public String getTitle() {
    return title;
  }

  public Language getLanguageFrom() {
    return languageFrom;
  }
}