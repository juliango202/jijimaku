package jijimaku.services;

import jijimaku.AppConfig;
import jijimaku.utils.FileManager;
import jijimaku.utils.SubtitleFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jijimaku.models.DictionaryMatch;
import jijimaku.models.ServicesParam;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParser.TextToken;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import subtitleFile.FatalParsingException;


/**
 * Created by julian on 11/23/17.
 */
public class AnnotationService {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final Pattern IS_HIRAGANA_RE = Pattern.compile("^[\\p{InHiragana}\\u30FC]+$");
  private static final Pattern IS_KATAKANA_RE = Pattern.compile("^[\\p{InKatakana}\\u30FC]+$");

  // POS tag that does not represent words
  private static final EnumSet<LangParser.PosTag> POS_TAGS_NOT_WORD = EnumSet.of(
          LangParser.PosTag.PUNCT,
          LangParser.PosTag.SYM,
          LangParser.PosTag.NUM,
          LangParser.PosTag.X
  );

  private static final EnumSet<LangParser.PosTag> POS_TAGS_IGNORE_WORD = EnumSet.of(
          LangParser.PosTag.PART,
          LangParser.PosTag.DET,
          LangParser.PosTag.CCONJ,
          LangParser.PosTag.SCONJ,
          LangParser.PosTag.AUX
  );

  private final AppConfig config;
  private final LangParser langParser;
  private final JijiDictionary dict;
  private final List<String> ignoreWordsList;

  public AnnotationService(ServicesParam services) {
    config = services.getConfig();
    langParser = services.getParser();
    dict = services.getDictionary();
    ignoreWordsList = config.getIgnoreWords();
  }

  /**
   * Return true if param str is the lemma matched by param dm
   */
  private boolean isMatchedLemma(String str, DictionaryMatch dm) {
    return str.equals(dm.getFirstCanonicalForm()) || str.equals(dm.getSecondCanonicalForm()) || str.equals(dm.getTextForm());
  }

  /**
   * Return true if param str contains the lemma matched by param dm
   */
  private boolean containsMatchedLemma(String str, DictionaryMatch dm) {
    return str.contains(dm.getFirstCanonicalForm()) || str.contains(dm.getSecondCanonicalForm()) || str.contains(dm.getTextForm());
  }

  /**
   * Return true if param strList contains the lemma matched by param dm
   */
  private boolean containsMatchedLemma(List<String> strList, DictionaryMatch dm) {
    return strList.contains(dm.getFirstCanonicalForm()) || strList.contains(dm.getSecondCanonicalForm()) || strList.contains(dm.getTextForm());
  }

  /**
   * Search a list of tokens in the dictionary.
   *
   * @return a DictionaryMatch entry if the provided tokens match a definition, null otherwise.
   */
  private DictionaryMatch dictionaryMatch(List<TextToken> tokens) {
    if (tokens.isEmpty()) {
      return null;
    }

    String firstCanonicalForm = tokens.stream().map(TextToken::getFirstCanonicalForm).collect(Collectors.joining(""));
    List<JijiDictionaryEntry> entries = dict.search(firstCanonicalForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries);
    }

    // If there is no entry for the canonical form, search the exact text
    String textForm = tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining(""));
    entries = dict.search(textForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries);
    }

    // If still no entry, search the second canonical form
    String secondCanonicalForm = tokens.stream().map(TextToken::getSecondCanonicalForm).collect(Collectors.joining(""));
    entries = dict.search(secondCanonicalForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries);
    }

    // If still no entry, search for the pronunciation
    // In Japanese sometimes words with kanji are written in kanas for emphasis or simplicity
    // and we want to catch those. Except for one character strings where there are too many results
    // for this to be relevant.
//    if (canonicalForm.length() > 1) {
//      entries = dict.searchByPronunciation(canonicalForm);
//      if (!entries.isEmpty()) {
//        return new DictionaryMatch(tokens, entries);
//      }
//    }

    return null;
  }

  private static final List<String> PART_OF_VERB_CONJUNCTIONS = Arrays.asList(
      "て", "で", "ちゃ"
  );

  /**
   * Filtering pass to merge some SCONJ with the previous VERBS/AUX in Japanese
   * This is so that for example 継ぎ-まし-て appears as one word in the subtitles
   */
  private List<TextToken> mergeJapaneseVerbs(List<TextToken> tokens) {
    List<TextToken> filteredTokens = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      TextToken token = tokens.get(i);
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

  /**
   * Return all the dictionary matches for one caption.
   * For example the parsed sentence => I|think|he|made|it|up should likely return four
   * DictionaryMatches => I|to think|he|to make it up
   */
  private List<DictionaryMatch> getDictionaryMatches(String caption) {
    // A syntaxic parse of the caption returns a list of tokens.
    List<TextToken> basicTokens = langParser.syntaxicParse(caption);
    List<TextToken> captionTokens = mergeJapaneseVerbs(basicTokens);

    String parsedTokens = captionTokens.stream().map(textToken -> textToken.getTextForm()).collect (Collectors.joining ("|"));

    LOGGER.debug("original: " + caption);
    LOGGER.debug("parsed: " + parsedTokens );

    // Next we must group tokens together if they is a corresponding definition in the dictionary.
    List<DictionaryMatch> matches = new ArrayList<>();
    while (!captionTokens.isEmpty()) {

      // Skip token that are not words or should be ignored
      if (POS_TAGS_NOT_WORD.contains(captionTokens.get(0).getPartOfSpeech())
          || POS_TAGS_IGNORE_WORD.contains(captionTokens.get(0).getPartOfSpeech())) {
        captionTokens = captionTokens.subList(1, captionTokens.size());
        continue;
      }

      // Find the next DictionaryMatch
      // Start with all tokens and remove one by one until we have a match
      List<TextToken> maximumTokens = new ArrayList<>(captionTokens);
      DictionaryMatch match = dictionaryMatch(maximumTokens);
      while (match == null && maximumTokens.size() > 0) {
        maximumTokens = maximumTokens.subList(0, maximumTokens.size() - 1);
        match = dictionaryMatch(maximumTokens);
      }

      if (match == null) {
        // We could not find a match for current token, just remove it
        captionTokens = captionTokens.subList(1, captionTokens.size());
        continue;
      }

      // Do not accept the match if it is a short sequence of hiragana
      // because it is most likely a wrong grouping of independent grammar conjunctions
      // and unlikely to be an unusual word that needs to be defined
      // (but make an exception for verbs)
      if (match.getTextForm().length() <= 3 && IS_HIRAGANA_RE.matcher(match.getTextForm()).matches() && !match.hasVerb()) {
        captionTokens = captionTokens.subList(1, captionTokens.size());
        continue;
      }

      matches.add(match);
      captionTokens = captionTokens.subList(match.getTokens().size(), captionTokens.size());

    }
    return matches;
  }

  /**
   * Filter the DictionaryMatches to display depending on user preferences.
   */
  private List<DictionaryMatch> getFilteredMatches(String caption) {
    List<DictionaryMatch> allMatches = getDictionaryMatches(caption);
    return allMatches.stream().filter(dm -> {

      // Ignore unimportant grammatical words
      if (dm.getTokens().stream().allMatch(t -> POS_TAGS_IGNORE_WORD.contains(t.getPartOfSpeech()))) {
        return false;
      }

      // Ignore user words list
      if (containsMatchedLemma(ignoreWordsList, dm)) {
        return false;
      }

      // For now ignore all-kana matches except if there is a verb
      if((IS_HIRAGANA_RE.matcher(dm.getTextForm()).matches() || IS_KATAKANA_RE.matcher(dm.getTextForm()).matches())
          && !dm.hasVerb()) {
        return false;
      }

      // Filter using ignoreFrequency option
      if (config.getIgnoreFrequencies().contains(dm.getFrequency())) {
        return false;
      }

      return true;
    }).collect(Collectors.toList());
  }

  private List<String> annotateDictionaryMatch(DictionaryMatch match, String color) {
    Boolean displayOtherLemma = config.getDisplayOtherLemma();
    List<String> tokenDefs = new ArrayList<>();
    for (JijiDictionaryEntry def : match.getDictionaryEntries()) {
      // Each definition is made of several lemmas and several senses
      // Depending on "displayOtherLemma" option, display only the lemma corresponding to the subtitle word, or all lemmas
      String lemmas = def.getLemmas().stream().map(l -> {
        if (isMatchedLemma(l, match)) {
          return SubtitleFile.addStyleToText(l, SubtitleFile.TextStyle.COLOR, color);
        } else if (displayOtherLemma) {
          return l;
        } else {
          return null;
        }
      }).filter(Objects::nonNull).collect(Collectors.joining(", "));
      // We don't know which sense corresponds to the subtitle so we can't do the same unfortunately ^^
      // => just concat all senses
      List<String> senses = def.getSenses();
      // Represent language level with unicode characters ①, ②, ③, ④, ...
      String langLevelStr = " ";
      if (def.getFrequency() != null) {
        String langLevelChar = Character.toString((char) ("①".charAt(0) + (def.getFrequency() - 1)));
        langLevelStr = " " + SubtitleFile.addStyleToText(langLevelChar, SubtitleFile.TextStyle.BOLD) + " ";
      }

      String pronounciationStr = "";
      if (def.getPronounciation() != null) {
        // Do not display pronounciation information if it is already present in lemmas
        boolean inLemma = def.getPronounciation().stream().anyMatch(lemmas::contains);
        if (!inLemma) {
          pronounciationStr = " [" + String.join(", ", def.getPronounciation()) + "] ";
          // If text word is not in lemma, the match must come from pronounciation => colorize
          if (!containsMatchedLemma(lemmas, match)) {
            pronounciationStr = SubtitleFile.addStyleToText(pronounciationStr, SubtitleFile.TextStyle.COLOR, color);
          }
        }
      }

      tokenDefs.add("★ " + lemmas + pronounciationStr + langLevelStr + String.join(" --- ", senses));
    }
    return tokenDefs;
  }

  /**
   * Parse a subtitle file and add annotation if dictionary definitions were found.
   *
   * @return true if at least one annotation was added, false otherwise.
   */
  public String[] annotateSubtitleFile(String fileName, String fileContents) throws IOException, FatalParsingException {
    SubtitleFile subtitle = new SubtitleFile(fileName, fileContents, config.getSubtitleStyles());
    subtitle.addJijimakuMark(dict.getTitle());

    // Loop through the subtitle file captions one by one
    while (subtitle.hasNext()) {
      String currentCaptionText = subtitle.nextCaption();
      List<String> colors = new ArrayList<>(config.getHighlightColors());

      // Parse subtitle and lookup definitions
      List<String> alreadyDefinedWords = new ArrayList<>();
      List<String> annotations = new ArrayList<>();
      for (DictionaryMatch match : getFilteredMatches(currentCaptionText)) {
        String color = colors.iterator().next();
        List<String> tokenDefs = annotateDictionaryMatch(match, color);
        if (!tokenDefs.isEmpty() && !alreadyDefinedWords.contains(match.getTextForm())) {
          annotations.addAll(tokenDefs);
          // Set a different color for words that are defined
          subtitle.colorizeCaptionWord(match.getTextForm(), color);
          Collections.rotate(colors, -1);
          alreadyDefinedWords.add(match.getTextForm());
        }
      }
      subtitle.annotate(annotations);
    }

    return subtitle.getNbCaptionAnnotated() == 0 ? null : subtitle.toAssFormat();
  }
}
