package jijimaku.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.AppConfig;
import jijimaku.models.DictionaryMatch;
import jijimaku.models.ServicesParam;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParser.TextToken;
import jijimaku.services.langrules.LangRules;
import jijimaku.utils.FileManager;
import jijimaku.utils.SubtitleFile;

import subtitleFile.FatalParsingException;

/**
 * Service that add the dictionary anotation to the subtitles.
 */
public class AnnotationService {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  // POS tag that does not represent words
  private static final EnumSet<LangParser.PosTag> POS_TAGS_NOT_WORD = EnumSet.of(
          LangParser.PosTag.PUNCT,
          LangParser.PosTag.SYM,
          LangParser.PosTag.NUM,
          LangParser.PosTag.X
  );

  private final AppConfig config;
  private final LangParser langParser;
  private final JijiDictionary dict;
  private final List<String> ignoreWordsList;
  private final EnumSet<LangParser.PosTag> partOfSpeechToAnnotate;
  private LangRules langRules;

  public AnnotationService(ServicesParam services) {
    config = services.getConfig();
    langParser = services.getParser();
    dict = services.getDictionary();
    ignoreWordsList = config.getIgnoreWords();
    partOfSpeechToAnnotate = config.getPartOfSpeechToAnnotate();

    // Instantiate the class for language-specific rules if available
    langRules = null;
    String language = langParser.getLanguage().toString();
    try {
      Class cls = Class.forName("jijimaku.services.langrules.LangRules" + language);
      langRules = (LangRules) cls.newInstance();
      LOGGER.debug("Using " + language + " specific annotation rules");
    } catch (ClassNotFoundException exc) {
      LOGGER.debug("No specific annotation rules found for language " + language);
    } catch (IllegalAccessException | InstantiationException exc) {
      LOGGER.error("Could not instantiate LangRules class for language " + language);
    }
  }

  /**
   * Return true if param str is the lemma matched by param dm.
   */
  private boolean isMatchedLemma(String str, DictionaryMatch dm) {
    return str.equals(dm.getFirstCanonicalForm()) || str.equals(dm.getSecondCanonicalForm()) || str.equals(dm.getTextForm());
  }

  /**
   * Return true if param str contains the lemma matched by param dm.
   */
  private boolean containsMatchedLemma(String str, DictionaryMatch dm) {
    return str.contains(dm.getFirstCanonicalForm()) || str.contains(dm.getSecondCanonicalForm()) || str.contains(dm.getTextForm());
  }

  /**
   * Return true if param strList contains the lemma matched by param dm.
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
    String ws = langParser.getWordSeparator();

    String firstCanonicalForm = tokens.stream().map(TextToken::getFirstCanonicalForm).collect(Collectors.joining(ws));
    List<JijiDictionaryEntry> entries = dict.search(firstCanonicalForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries, ws);
    }

    // If there is no entry for the canonical form, search the exact text
    String textForm = tokens.stream().map(tt -> tt.getTextForm().toLowerCase()).collect(Collectors.joining(ws));
    entries = dict.search(textForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries, ws);
    }

    // If still no entry, search the second canonical form
    String secondCanonicalForm = tokens.stream().map(TextToken::getSecondCanonicalForm).collect(Collectors.joining(ws));
    entries = dict.search(secondCanonicalForm);
    if (!entries.isEmpty()) {
      return new DictionaryMatch(tokens, entries, ws);
    }

    return null;
  }

  /**
   * Return all the dictionary matches for one caption.
   * For example the parsed sentence => I|think|he|made|it|up should likely return four
   * DictionaryMatches => I|to think|he|to make it up
   * For now use simple prefix matching
   * Could potentially be improved using https://github.com/robert-bor/aho-corasick
   */
  private List<DictionaryMatch> getDictionaryMatches(String caption) {
    // A syntaxic parse of the caption returns a list of tokens.
    List<TextToken> captionTokens = langParser.parse(caption);

    // Apply language specific filter
    if (langRules != null) {
      captionTokens = langRules.filterTokens(captionTokens);
    }

    // Next we must group tokens together if they is a corresponding definition in the dictionary.
    List<DictionaryMatch> matches = new ArrayList<>();
    while (!captionTokens.isEmpty()) {

      // Skip token that are not words or should be ignored
      if (POS_TAGS_NOT_WORD.contains(captionTokens.get(0).getPartOfSpeech())) {
        captionTokens = captionTokens.subList(1, captionTokens.size());
        continue;
      }

      // Find the next DictionaryMatch
      // Start with all tokens and remove one by one until we have a match
      List<TextToken> maximumTokens = new ArrayList<>(captionTokens);
      DictionaryMatch match = dictionaryMatch(maximumTokens);
      while (match == null && !maximumTokens.isEmpty()) {
        maximumTokens = maximumTokens.subList(0, maximumTokens.size() - 1);
        match = dictionaryMatch(maximumTokens);
      }

      // If no match is found, or the match is invalid for this language, just skip the current token
      if (match == null || (langRules != null && !langRules.isValidMatch(match))) {
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

      // Ignore matches that don't have any partOfSpeech to annotate
      if (dm.getTokens().stream().noneMatch(t -> partOfSpeechToAnnotate.contains(t.getPartOfSpeech()))) {
        return false;
      }

      // Filter using language-specific rules
      if (langRules != null && langRules.isIgnoredMatch(dm)) {
        return false;
      }

      // Filter using ignoreTags option
      Optional<String> tagMatch = dm.getTags().stream()
          .filter(t -> config.getIgnoreTags().contains(t))
          .findFirst();
      if (tagMatch.isPresent()) {
        LOGGER.debug("{} ignored because tag {} is present in ignoreTags config", dm.getTextForm(), tagMatch.get());
        return false;
      }

      // Ignore user words list
      if (containsMatchedLemma(ignoreWordsList, dm)) {
        LOGGER.debug("{} ignored because it is present in ignoreWords config", dm.getTextForm());
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
      if (def.getPronunciations() != null) {
        // Do not display pronunciation information if it is already present in lemmas
        boolean inLemma = def.getPronunciations().stream().anyMatch(lemmas::contains);
        if (!inLemma) {
          pronounciationStr = " [" + String.join(", ", def.getPronunciations()) + "] ";
          // If text word is not in lemma, the match must come from pronunciation => colorize
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
   * Clean up caption text before parsing.
   */
  private String cleanCaptionText(String caption) {
    String cleaned = caption.trim();
    // Replace newlines(<br>) by word separator
    cleaned = cleaned.replaceAll("<br\\s*/?>", langParser.getWordSeparator());
    // Remove html tags
    cleaned = cleaned.replaceAll("\\<[^>]*>","");
    // Replace consecutive dots(...) by only one to facilitate parsing
    cleaned = cleaned.replaceAll("\\.+",".");
    return cleaned;
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
      String currentCaptionText = cleanCaptionText(subtitle.nextCaption());
      List<String> colors = new ArrayList<>(config.getHighlightColors());

      // Parse subtitle and lookup definitions
      List<String> alreadyDefinedWords = new ArrayList<>();
      List<String> annotations = new ArrayList<>();
      List<DictionaryMatch> filteredMatches = getFilteredMatches(currentCaptionText);
      if (filteredMatches.isEmpty()) {
        LOGGER.debug("No dictionary match.");
      } else {
        LOGGER.debug("dictionary matches: " + filteredMatches.stream().map(DictionaryMatch::getTextForm).collect(Collectors.joining(", ")));
      }

      for (DictionaryMatch match : filteredMatches) {
        String color = colors.iterator().next();
        List<String> tokenDefs = annotateDictionaryMatch(match, color);
        if (!tokenDefs.isEmpty() && !alreadyDefinedWords.contains(match.getTextForm())) {
          annotations.addAll(tokenDefs);
          // Set a different color for words that are defined
          subtitle.colorizeCaptionWord(match.getTextForm(), color, langParser.getWordSeparator());
          Collections.rotate(colors, -1);
          alreadyDefinedWords.add(match.getTextForm());
        }
      }
      subtitle.annotate(annotations);
    }

    return subtitle.getNbCaptionAnnotated() == 0 ? null : subtitle.toAssFormat();
  }
}
