package jijimaku.services.dictionary;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.utils.FileManager;


/**
 * Contains one entry of a Jiji dictionary.
 */
public class DictionaryEntry {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String FREQUENCY_TAG_PREFIX = "freq";

  private final List<String> lemmas;
  private final List<String> senses;
  private final List<String> pronunciations;
  private final List<String> tags;
  private final Integer frequency;

  public DictionaryEntry(List<String> lemmas, List<String> senses, List<String> pronunciations, List<String> tags) {
    Objects.requireNonNull(lemmas, "lemmas should not be null");
    Objects.requireNonNull(senses, "senses should not be null");
    this.lemmas = lemmas;
    this.senses = senses;
    this.pronunciations = pronunciations;
    this.tags = tags;
    this.frequency = getFrequencyFromTags(tags);
  }

  private Integer getFrequencyFromTags(List<String> tags) {
    if (tags == null) {
      return null;
    }
    List<String> frequencyTags = this.tags.stream()
        .filter(t -> t.startsWith(FREQUENCY_TAG_PREFIX)).collect(Collectors.toList());
    if (frequencyTags.isEmpty()) {
      return null;
    }
    if (frequencyTags.size() > 1) {
      LOGGER.error("JijiDictionaryEntry {} has multiple frequency tags.", String.join(", ", lemmas));
      return null;
    }
    String frequencyStr = frequencyTags.get(0).substring(FREQUENCY_TAG_PREFIX.length());
    return Integer.parseInt(frequencyStr);
  }

  public List<String> getLemmas() {
    return lemmas;
  }

  public List<String> getSenses() {
    return senses;
  }

  public List<String> getPronunciations() {
    return pronunciations;
  }

  public List<String> getTags() {
    return tags;
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

    DictionaryEntry otherEntry = (DictionaryEntry) otherObj;
    if (!lemmas.equals(otherEntry.lemmas)) {
      return false;
    }
    if (!senses.equals(otherEntry.senses)) {
      return false;
    }
    if (pronunciations != null ? !pronunciations.equals(otherEntry.pronunciations) : otherEntry.pronunciations != null) {
      return false;
    }
    return tags != null ? tags.equals(otherEntry.tags) : otherEntry.tags == null;
  }

  @Override
  public int hashCode() {
    int result = lemmas.hashCode();
    result = 31 * result + senses.hashCode();
    result = 31 * result + (pronunciations != null ? pronunciations.hashCode() : 0);
    result = 31 * result + (tags != null ? tags.hashCode() : 0);
    return result;
  }
}