package jijimaku.models;

import java.util.List;
import java.util.stream.Collectors;

import jijimaku.services.dictionary.DictionaryEntry;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParser.TextToken;

/**
 * A DictionaryMatch represent a list of successive text tokens that together
 * match one or more definitions in our dictionary.
 */
public class DictionaryMatch {
  private final List<TextToken> tokens;
  private final List<DictionaryEntry> dictionaryEntries;
  private final String wordSeparator;

  public DictionaryMatch(List<TextToken> tokens, List<DictionaryEntry> dictionaryEntries, String wordSeparator) {
    this.tokens = tokens;
    this.dictionaryEntries = dictionaryEntries;
    this.wordSeparator = wordSeparator;
  }

  public String getTextForm() {
    return tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining(wordSeparator));
  }

  public String getFirstCanonicalForm() {
    return tokens.stream().map(TextToken::getFirstCanonicalForm).collect(Collectors.joining(wordSeparator));
  }

  public String getSecondCanonicalForm() {
    return tokens.stream().map(TextToken::getSecondCanonicalForm).collect(Collectors.joining(wordSeparator));
  }

  public List<DictionaryEntry> getDictionaryEntries() {
    return dictionaryEntries;
  }

  public List<TextToken> getTokens() {
    return tokens;
  }

  public boolean hasVerb() {
    return tokens.stream().anyMatch(t -> t.getPartOfSpeech().equals(LangParser.PosTag.VERB));
  }

  /**
   * For now a dictionary matches that contains several entries is assigned
   * all the tags of all the entries.
   * This gives the best results when the user want to ignore some tags.
   */
  public List<String> getTags() {
    return dictionaryEntries.stream()
        .filter(de -> de.getTags() != null)
        .flatMap(de -> de.getTags().stream())
        .collect(Collectors.toList());
  }
}