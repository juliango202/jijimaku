package jijimaku.services.jijidictionary;

import java.util.List;

/**
 * Contains one entry of a Jiji dictionary.
 */
public class JijiDictionaryEntry {
  private final List<String> lemmas;
  private final List<String> senses;
  private final List<String> pronounciation;
  private final Integer frequency;

  public JijiDictionaryEntry(List<String> lemmas, Integer frequency, List<String> senses, List<String> pronounciation) {
    this.lemmas = lemmas;
    this.frequency = frequency;
    this.senses = senses;
    this.pronounciation = pronounciation;
  }

  public List<String> getLemmas() {
    return lemmas;
  }

  public List<String> getSenses() {
    return senses;
  }

  public List<String> getPronounciation() {
    return pronounciation;
  }

  public Integer getFrequency() {
    return frequency;
  }
}