package jijimaku.services;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jijimaku.models.DictionaryMatch;
import jijimaku.models.ServicesParam;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.LangParser;

import subtitleFile.FatalParsingException;


/**
 * Created by julian on 11/23/17.
 */
public class AnnotationService {

  private static final Pattern IS_HIRAGANA_RE = Pattern.compile("^\\p{IsHiragana}+$");

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

  private final Config config;
  private final LangParser langParser;
  private final JijiDictionary dict;
  private final SubtitleService subtitleService;

  public AnnotationService(ServicesParam services) {
    config = services.getConfig();
    langParser = services.getParser();
    dict = services.getDictionary();
    subtitleService = services.getSubtitleService();
  }

  /**
   * Search a list of tokens in the dictionary.
   *
   * @return a DictionaryMatch entry if the provided tokens match a definition, null otherwise.
   */
  private DictionaryMatch dictionaryMatch(List<LangParser.TextToken> tokens) {
    if (tokens.isEmpty()) {
      return null;
    }

    String canonicalForm = tokens.stream().map(LangParser.TextToken::getCanonicalForm).collect(Collectors.joining(""));
    List<JijiDictionaryEntry> entries = dict.search(canonicalForm);

    // If there is no entry for the canonical form, search the exact text
    if (entries.isEmpty()) {
      String textForm = tokens.stream().map(LangParser.TextToken::getTextForm).collect(Collectors.joining(""));
      entries = dict.search(textForm);
    }

    // If still no entry, search for the pronunciation
    // In Japanese sometimes words with kanji are written in kanas for emphasis or simplicity
    // and we want to catch those. Except for one character strings where there are too many results
    // for this to be relevant.
    if (entries.isEmpty() && canonicalForm.length() > 1) {
      entries = dict.searchByPronunciation(canonicalForm);
    }

    if (entries.isEmpty()) {
      return null;
    } else {
      return new DictionaryMatch(tokens, entries);
    }
  }

  /**
   * Return all the dictionary matches for one caption.
   * For example the parsed sentence => I|think|he|made|it|up should likely return four
   * DictionaryMatches => I|to think|he|to make it up
   */
  private List<DictionaryMatch> getDictionaryMatches(String caption) {
    // A syntaxic parse of the caption returns a list of tokens.
    List<LangParser.TextToken> captionTokens = langParser.syntaxicParse(caption);

    // Next we must group tokens together if they is a corresponding definition in the dictionary.
    List<DictionaryMatch> matches = new ArrayList<>();
    while (!captionTokens.isEmpty()) {

      // Skip token that are not words
      if (POS_TAGS_NOT_WORD.contains(captionTokens.get(0).getPartOfSpeech())) {
        captionTokens = captionTokens.subList(1, captionTokens.size());
        continue;
      }

      // Find the next DictionaryMatch
      // Start with all tokens and remove one by one until we have a match
      List<LangParser.TextToken> maximumTokens = new ArrayList<>(captionTokens);
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
      if (match.getTextForm().length() <= 3 && IS_HIRAGANA_RE.matcher(match.getTextForm()).matches()) {
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
      Set<String> ignoreWordsSet = config.getIgnoreWords();
      if (ignoreWordsSet.contains(dm.getTextForm()) || ignoreWordsSet.contains(dm.getCanonicalForm())) {
        return false;
      }

      // Filter using ignoreFrequency option
      if (dm.getDictionaryEntries().stream().allMatch(de -> config.getIgnoreFrequencies().contains(de.getFrequency()))) {
        return false;
      }

      return true;
    }).collect(Collectors.toList());
  }

  /**
   * Parse a subtitle file and add annotation if dictionary definitions were found.
   *
   * @return true if at least one annotation was added, false otherwise.
   */
  public boolean annotateSubtitleFile(String directory, String fileName, String fileContents) throws IOException, FatalParsingException {

    Boolean displayOtherLemma = config.getDisplayOtherLemma();

    subtitleService.readFile(fileName, fileContents);

    // Loop through the subtitle file captions one by one
    int nbAnnotations = 0;
    while (subtitleService.hasNextCaption()) {

      List<String> colors = new ArrayList<>(config.getColors().values());
      String currentCaptionText = subtitleService.nextCaption();
      subtitleService.setCaptionStyle();

      // Parse subtitle and lookup definitions
      List<String> alreadyDefinedWords = new ArrayList<>();
      List<String> annotations = new ArrayList<>();
      for (DictionaryMatch match : getFilteredMatches(currentCaptionText)) {
        String color = colors.iterator().next();
        List<String> tokenDefs = new ArrayList<>();
        for (JijiDictionaryEntry def : match.getDictionaryEntries()) {
          // Each definition is made of several lemmas and several senses
          // Depending on "displayOtherLemma" option, display only the lemma corresponding to the subtitle word, or all lemmas
          String lemmas = def.getLemmas().stream().map(l -> {
            if (l.equals(match.getCanonicalForm()) || l.equals(match.getTextForm())) {
              return SubtitleService.addStyleToText(l, SubtitleService.TextStyle.COLOR, color);
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
            String langLevelChar = Character.toString((char) (9312 + def.getFrequency()));
            langLevelStr = " " + SubtitleService.addStyleToText(langLevelChar, SubtitleService.TextStyle.BOLD) + " ";
          }

          String pronounciationStr = "";
          if (def.getPronounciation() != null) {
            // Do not display pronounciation information if it is already present in lemmas
            boolean inLemma = def.getPronounciation().stream().anyMatch(lemmas::contains);
            if (!inLemma) {
              pronounciationStr = " [" + String.join(", ", def.getPronounciation()) + "] ";
            }
          }

          tokenDefs.add("★ " + lemmas + pronounciationStr + langLevelStr + String.join(" --- ", senses));
        }

        if (!tokenDefs.isEmpty() && !alreadyDefinedWords.contains(match.getTextForm())) {
          annotations.addAll(tokenDefs);
          // Set a different color for words that are defined
          subtitleService.colorizeCaptionWord(match.getTextForm(), color);
          Collections.rotate(colors, -1);
          alreadyDefinedWords.add(match.getTextForm());
        }
      }

      if (annotations.size() > 0) {
        nbAnnotations += annotations.size();
        subtitleService.addJijimakuMark();
        subtitleService.addAnnotationCaption(SubtitleService.SubStyle.Definition, String.join("\\N", annotations));
      }
    }

    if (nbAnnotations == 0) {
      return false;
    }
    subtitleService.writeToAss(directory + "/" + FilenameUtils.getBaseName(fileName) + ".ass");
    return true;
  }
}
