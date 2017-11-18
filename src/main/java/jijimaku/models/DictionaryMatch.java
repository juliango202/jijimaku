package jijimaku.models;

import java.util.List;
import java.util.stream.Collectors;

import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.LangParser.TextToken;

/**
 * A DictionaryMatch represent a list of successive text tokens that together
 * match one or more definitions in our dictionary.
 */
public class DictionaryMatch {
  private final List<TextToken> tokens;
  private final List<JijiDictionaryEntry> dictionaryEntries;

  public DictionaryMatch(List<TextToken> tokens, List<JijiDictionaryEntry> dictionaryEntries) {
    this.tokens = tokens;
    this.dictionaryEntries = dictionaryEntries;
  }

  public String getTextForm() {
    return tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining(""));
  }

  public String getCanonicalForm() {
    return tokens.stream().map(TextToken::getCanonicalForm).collect(Collectors.joining(""));
  }

  public List<JijiDictionaryEntry> getDictionaryEntries() {
    return dictionaryEntries;
  }

  public List<TextToken> getTokens() {
    return tokens;
  }
}