package jijimaku.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.carrotsearch.labs.langid.LangIdV3;


/**
 * Simple class that provides an enum of all supported languages.
 */
public class LanguageService {

  @SuppressWarnings("unused")
  public enum Language {
    ANCIENT_GREEK,
    ARABIC,
    BASQUE,
    BELARUSIAN,
    BULGARIAN,
    CATALAN,
    CHINESE,
    COPTIC,
    CROATIAN,
    CZECH,
    DANISH,
    DUTCH,
    ENGLISH,
    ESTONIAN,
    FINNISH,
    FRENCH,
    GALICIAN,
    GERMAN,
    GOTHIC,
    GREEK,
    HEBREW,
    HINDI,
    HUNGARIAN,
    INDONESIAN,
    IRISH,
    ITALIAN,
    JAPANESE,
    KAZAKH,
    KOREAN,
    LATIN,
    LATVIAN,
    LITHUANIAN,
    NORWEGIAN,
    OLD_CHURCH_SLAVONIC,
    PERSIAN,
    POLISH,
    PORTUGUESE,
    ROMANIAN,
    RUSSIAN,
    SANSKRIT,
    SLOVAK,
    SLOVENIAN,
    SPANISH,
    SWEDISH,
    TAMIL,
    TURKISH,
    UKRAINIAN,
    URDU,
    UYGHUR,
    VIETNAMESE
  }

  public static final List<Language> LANGUAGES_WITHOUT_SPACES = Arrays.asList(
      Language.JAPANESE, Language.CHINESE, Language.VIETNAMESE
  );

  private static final HashMap<String, Language> iso639Languages = new HashMap<>();

  static {
    iso639Languages.put("ar", Language.ARABIC);
    iso639Languages.put("eu", Language.BASQUE);
    iso639Languages.put("be", Language.BELARUSIAN);
    iso639Languages.put("bg", Language.BULGARIAN);
    iso639Languages.put("ca", Language.CATALAN);
    iso639Languages.put("zh", Language.CHINESE);
    iso639Languages.put("hr", Language.CROATIAN);
    iso639Languages.put("cs", Language.CZECH);
    iso639Languages.put("da", Language.DANISH);
    iso639Languages.put("nl", Language.DUTCH);
    iso639Languages.put("en", Language.ENGLISH);
    iso639Languages.put("et", Language.ESTONIAN);
    iso639Languages.put("fi", Language.FINNISH);
    iso639Languages.put("fr", Language.FRENCH);
    iso639Languages.put("gl", Language.GALICIAN);
    iso639Languages.put("de", Language.GERMAN);
    iso639Languages.put("el", Language.GREEK);
    iso639Languages.put("he", Language.HEBREW);
    iso639Languages.put("hi", Language.HINDI);
    iso639Languages.put("hu", Language.HUNGARIAN);
    iso639Languages.put("id", Language.INDONESIAN);
    iso639Languages.put("ga", Language.IRISH);
    iso639Languages.put("it", Language.ITALIAN);
    iso639Languages.put("ja", Language.JAPANESE);
    iso639Languages.put("kk", Language.KAZAKH);
    iso639Languages.put("ko", Language.KOREAN);
    iso639Languages.put("la", Language.LATIN);
    iso639Languages.put("lv", Language.LATVIAN);
    iso639Languages.put("lt", Language.LITHUANIAN);
    iso639Languages.put("no", Language.NORWEGIAN);
    iso639Languages.put("cu", Language.OLD_CHURCH_SLAVONIC);
    iso639Languages.put("fa", Language.PERSIAN);
    iso639Languages.put("pl", Language.POLISH);
    iso639Languages.put("pt", Language.PORTUGUESE);
    iso639Languages.put("ro", Language.ROMANIAN);
    iso639Languages.put("ru", Language.RUSSIAN);
    iso639Languages.put("sa", Language.SANSKRIT);
    iso639Languages.put("sk", Language.SLOVAK);
    iso639Languages.put("sl", Language.SLOVENIAN);
    iso639Languages.put("es", Language.SPANISH);
    iso639Languages.put("sv", Language.SWEDISH);
    iso639Languages.put("ta", Language.TAMIL);
    iso639Languages.put("tr", Language.TURKISH);
    iso639Languages.put("uk", Language.UKRAINIAN);
    iso639Languages.put("ur", Language.URDU);
    iso639Languages.put("ug", Language.UYGHUR);
    iso639Languages.put("vi", Language.VIETNAMESE);
  }

  /**
   * Return the language corresponding to the given ISO 639-1 two-letter code.
   */
  private static Language fromIso639_1(String code) {
    return iso639Languages.get(code.toLowerCase());
  }

  /**
   * Return the language corresponding to the given String.
   */
  public static Language getLanguageFromStr(String languageStr) {
    if (languageStr.length() == 2) {
      // Assume this is an ISO 639-1 two-letter code
      return fromIso639_1(languageStr);
    }
    try {
      return Language.valueOf(languageStr.replace(" ", "_").toUpperCase());
    } catch (IllegalArgumentException exc) {
      return null;
    }
  }

  /**
   * Detect the language from a list of language samples.
   */
  public static Language detectFromList(List<String> langSamples) {
    LangIdV3 langId = new LangIdV3();
    // Compute language prediction counts for the samples
    Map<String, Long> langCounts = langSamples.stream()
        .map(ls -> langId.classify(ls, true).langCode)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    // Allow for at most one wrong(different) guess, if more than one return null
    for (Map.Entry<String, Long> lang : langCounts.entrySet()) {
      if (lang.getValue() >= langSamples.size() - 1) {
        return getLanguageFromStr(lang.getKey());
      }
    }
    return null;
  }

  public static Language detectFromFilename(String filename) {
    Language detected = null;
    int detectedIndex = filename.length();
    for (Language lang : Language.values()) {
      int langIndex = filename.toLowerCase().indexOf(lang.toString().toLowerCase());
      if (langIndex >= 0 && langIndex < detectedIndex) {
        detected = lang;
        detectedIndex = langIndex;
      }
    }
    return detected;
  }
}
