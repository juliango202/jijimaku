package jijimaku.services.langrules;

import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.models.DictionaryMatch;
import jijimaku.utils.FileManager;

/**
 * Japanese specific rules used when annotating subtitles.
 */
public class LangRulesJapanese implements LangRules {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final Pattern IS_HIRAGANA_RE = Pattern.compile("^[\\p{InHiragana}\\u30FC]+$");
  private static final Pattern IS_KATAKANA_RE = Pattern.compile("^[\\p{InKatakana}\\u30FC]+$");

  @Override
  public boolean isValidMatch(DictionaryMatch match) {

    // Do not accept the match if it is a short sequence of hiragana
    // because it is most likely a wrong grouping of independent grammar conjunctions
    // and unlikely to be an unusual word that needs to be defined
    // (but make an exception for verbs)
    if (match.getTextForm().length() <= 3 && IS_HIRAGANA_RE.matcher(match.getTextForm()).matches() && !match.hasVerb()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isIgnoredMatch(DictionaryMatch match) {

    // For now ignore all-kana matches except if there is a verb
    // TODO: This is not ideal for beginners so it should be an option in config.yaml
    if ((IS_HIRAGANA_RE.matcher(match.getTextForm()).matches() || IS_KATAKANA_RE.matcher(match.getTextForm()).matches())
        && !match.hasVerb()) {
      return true;
    }

    return false;
  }
}
