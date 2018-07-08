package jijimaku.services.dictionary;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.AppConfig;
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

  private String title;
  private Language languageFrom;

  public DictionaryLingoesLd2(File dictFile, AppConfig config) {
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

    languageFrom = detectLanguage(config.getDictionaryLanguage(), dictFile.getName(), definitions);
    if (languageFrom == null) {
      LOGGER.error("Cannot detect language of LD2 dictionary {}, please use the "
          + "'dictionaryLanguage' config option set to a correct language", dictFile.getAbsolutePath());
      throw new UnexpectedCriticalError();
    }
    LOGGER.info("Using {} dictionary '{}'", languageFrom, title);

    for (Map.Entry<String, String> def : definitions.entrySet()) {
      String lemma = def.getKey();
      // Strip lemma from comma (happen in some LD2 dictionaries)
      lemma = lemma.replaceAll("^,|,$", "");

      if (lemma.contains(",") || lemma.contains(";")) {
        LOGGER.debug("Lemma {} contains a comma, it will be ignored", lemma);
        continue;
      }

      String value = def.getValue();
      // this is a reference to another entry
      if (definitions.containsKey(value)) {
        value = definitions.get(value);
      }

      addEntry(Collections.singletonList(lemma), Collections.singletonList(value), null, null, config);
    }

    loadLanguageTags();
  }

  private Language detectLanguage(String dictLanguageConfig, String dictFileName, Map<String,String> definitions) {
    Language detected = null;

    // Read dictionary language from config
    if (dictLanguageConfig != null && !dictLanguageConfig.isEmpty()) {
      detected = LanguageService.getLanguageFromStr(dictLanguageConfig);
    }

    LOGGER.info("Config value dictionaryLanguage is missing, will try to detect LD2 dictionary language...");

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

  public Logger getLogger() {
    return LOGGER;
  }
}