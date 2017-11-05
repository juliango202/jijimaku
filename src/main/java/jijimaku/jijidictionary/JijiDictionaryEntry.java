package jijimaku.jijidictionary;

import java.util.List;

/**
 * Contains one entry of a Jiji dictionary.
 */
public class JijiDictionaryEntry {
  private final List<String> lemmas;
  private final List<String> senses;
  private final Integer frequency;

  public JijiDictionaryEntry(List<String> lemmas, Integer frequency, List<String> senses) {
    this.lemmas = lemmas;
    this.frequency = frequency;
    this.senses = senses;
  }

  public List<String> getLemmas() {
    return lemmas;
  }

  public List<String> getSenses() {
    return senses;
  }

  public Integer getFrequency() {
    return frequency;
  }
}