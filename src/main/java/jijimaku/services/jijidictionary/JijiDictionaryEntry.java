package jijimaku.services.jijidictionary;

import java.util.List;
import java.util.Objects;

/**
 * Contains one entry of a Jiji dictionary.
 */
public class JijiDictionaryEntry {
  private final List<String> lemmas;
  private final List<String> senses;
  private final List<String> pronounciation;
  private final Integer frequency;

  public JijiDictionaryEntry(List<String> lemmas, Integer frequency, List<String> senses, List<String> pronounciation) {
    Objects.requireNonNull(lemmas, "lemmas should not be null");
    Objects.requireNonNull(senses, "senses should not be null");
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

  // Overrides equals ans hashCode for proper use in collections
  @Override
  public boolean equals(Object otherObj) {
    if (this == otherObj) {
      return true;
    }
    if (otherObj == null || getClass() != otherObj.getClass()) {
      return false;
    }

    JijiDictionaryEntry otherEntry = (JijiDictionaryEntry) otherObj;
    if (!lemmas.equals(otherEntry.lemmas)) {
      return false;
    }
    if (!senses.equals(otherEntry.senses)) {
      return false;
    }
    if (pronounciation != null ? !pronounciation.equals(otherEntry.pronounciation) : otherEntry.pronounciation != null) {
      return false;
    }
    return frequency != null ? frequency.equals(otherEntry.frequency) : otherEntry.frequency == null;
  }

  @Override
  public int hashCode() {
    int result = lemmas.hashCode();
    result = 31 * result + senses.hashCode();
    result = 31 * result + (pronounciation != null ? pronounciation.hashCode() : 0);
    result = 31 * result + (frequency != null ? frequency.hashCode() : 0);
    return result;
  }
}