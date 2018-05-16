package jijimaku.services.langrules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.models.DictionaryMatch;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParser.TextToken;
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

  private static final List<String> PART_OF_VERB_CONJUNCTIONS = Arrays.asList(
      "て", "で", "ちゃ"
  );

  /**
   * Filtering pass to merge some SCONJ with the previous VERBS/AUX in Japanese.
   * This is so that for example 見つけ-て appears as one word in the subtitles
   */
  @Override
  public List<TextToken> filterTokens(List<TextToken> tokens) {
    List<TextToken> filteredTokens = new ArrayList<>();
    for (TextToken token : tokens) {
      TextToken lastOk = filteredTokens.isEmpty() ? null : filteredTokens.get(filteredTokens.size() - 1);
      boolean isPartOfVerbConj = (token.getPartOfSpeech() == LangParser.PosTag.SCONJ && PART_OF_VERB_CONJUNCTIONS.contains(token.getTextForm()));
      if (lastOk != null
          && (lastOk.getPartOfSpeech() == LangParser.PosTag.AUX || lastOk.getPartOfSpeech() == LangParser.PosTag.VERB)
          && isPartOfVerbConj) {
        TextToken completeVerb = new TextToken(lastOk.getPartOfSpeech(), lastOk.getTextForm() + token.getTextForm(),
            lastOk.getFirstCanonicalForm(), lastOk.getSecondCanonicalForm());
        filteredTokens.set(filteredTokens.size() - 1, completeVerb);
        continue;
      }
      filteredTokens.add(token);
    }
    return filteredTokens;
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
