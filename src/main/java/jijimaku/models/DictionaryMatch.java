package jijimaku.models;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.LangParser;
import jijimaku.services.langparser.LangParser.TextToken;

/**
 * A DictionaryMatch represent a list of successive text tokens that together
 * match one or more definitions in our dictionary.
 */
public class DictionaryMatch {
  private final List<TextToken> tokens;
  private final List<JijiDictionaryEntry> dictionaryEntries;
  private final String wordSeparator;

  public DictionaryMatch(List<TextToken> tokens, List<JijiDictionaryEntry> dictionaryEntries, String wordSeparator) {
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

  public List<JijiDictionaryEntry> getDictionaryEntries() {
    return dictionaryEntries;
  }

  public List<TextToken> getTokens() {
    return tokens;
  }

  public boolean hasVerb() {
    return tokens.stream().anyMatch(t -> t.getPartOfSpeech().equals(LangParser.PosTag.VERB));
  }

  /**
   * Return the smallest frequency among the dictionary entries.
   * This gives the best results to represent the frequency of a match with several entries.
   */
  public Integer getFrequency() {
     OptionalInt minFrequency = dictionaryEntries.stream()
        .filter(de -> de.getFrequency() != null)
        .mapToInt(JijiDictionaryEntry::getFrequency)
        .min();
    return minFrequency.isPresent() ? minFrequency.getAsInt() : null;
  }
}