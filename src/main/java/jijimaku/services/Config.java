package jijimaku.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedError;
import jijimaku.utils.FileManager;


/**
 * Jijimaku configuration parameters(read from a YAML file).
 */
public class Config {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  // Yaml properties
  private final String configFilePath;
  private final Map<String, Object> configMap;

  // Jijimaku config values
  private final Map<String,String> colors;
  private final Set<String> ignoreWords;
  private final Boolean displayOtherLemma;
  private final Map<String, String> properNouns;
  private final String jijiDictionary;
  private final String assStyles;
  private final Map<String, String> definitionStyle;
  private final List<Integer> ignoreFrequencies;

  /**
   * Read user preferences from a YAML config file.
   */
  public Config(File configFile) {
    this.configFilePath = configFile.getAbsolutePath();

    // Parse YAML file
    try {
      Yaml yaml = new Yaml();
      String yamlStr = FileManager.fileAnyEncodingToString(configFile);
      configMap = (new HashMap<String, Object>()).getClass().cast(yaml.load(yamlStr));
    } catch (IOException | ClassCastException exc) {
      LOGGER.error("Problem reading YAML config {}", configFilePath);
      LOGGER.debug("Got exception", exc);
      throw new UnexpectedError();
    }

    // Load config values
    final Map<String, String> hashMapStringString = new HashMap<>();

    // TODO: check type of hashmap keys(not done on cast)

    colors = getConfigValue("colors", hashMapStringString.getClass());
    properNouns = getConfigValue("properNouns", hashMapStringString.getClass());
    definitionStyle = getConfigValue("definitionStyle", hashMapStringString.getClass());
    ignoreFrequencies = getConfigValue("ignoreFrequencies", (new ArrayList<Integer>()).getClass());

    displayOtherLemma = getConfigValue("displayOtherLemma", Boolean.class);
    jijiDictionary = getConfigValue("jijiDictionary", String.class);
    assStyles = getConfigValue("assStyles", String.class);

    // Load ignore words, convert config String to a Set
    final String ignoreWordsText = getConfigValue("ignoreWords", String.class);
    if (ignoreWordsText == null) {
      ignoreWords = null;
    } else {
      List<String> wordList = Arrays.asList(ignoreWordsText.split("\\r?\\n"));
      wordList.removeIf(word -> word.trim().isEmpty());
      ignoreWords = new HashSet<>(wordList);
    }
  }

  /**
   * Generic method to get a config value of some type.
   * @param paramKey Yaml file key for the value
   * @param paramType Java class that can represent this value
   * @return the config value as a paramType instance, or null if missing from config.
   */
  private <T> T getConfigValue(String paramKey, Class<T> paramType) {
    if (!configMap.containsKey(paramKey)) {
      return null;
    }
    try {
      return paramType.cast(configMap.get(paramKey));
    } catch (ClassCastException exc) {
      LOGGER.error("Config parameter {} in file {} is not in the expected format(see logs). Check the documentation for the proper syntax.",
          paramKey, configFilePath);
      LOGGER.debug("Got exception", exc);
      throw new UnexpectedError();
    }
  }

  String getConfigFilePath() {
    return configFilePath;
  }


  /**
   * Color configuration for defined words.
   */
  public Map<String,String> getColors() {
    return colors;
  }

  /**
   * Set of subtitles words that should be ignored(not annotated).
   */
  public Set<String> getIgnoreWords() {
    return ignoreWords;
  }

  /**
   * Flag to display all lemmas of words or not.
   */
  public Boolean getDisplayOtherLemma() {
    return displayOtherLemma;
  }

  public Map<String,String> getProperNouns() {
    return properNouns;
  }

  /**
   * Return the JijiDictionary file(*.jiji.zip, *.jiji.yaml) used for looking up definitions.
   */
  public String getJijiDictionary() {
    return jijiDictionary;
  }

  /**
   * Return the whole ASS subtitle style definition string if present.
   * See SubtitleService.DEFAULT_STYLES for an example,
   * and https://www.matroska.org/technical/specs/subtitles/ssa.html for the specs.
   * Return null if SUBTITLE_STYLES is missing from config
   */
  public String getSubtitleStyles() {
    return assStyles;
  }

  /**
   * Color of text when writing definitions.
   */
  public String getDefinitionColor() {
    return definitionStyle.get("color");
  }

  /**
   * Size of text when writing definitions.
   */
  public String getDefinitionSize() {
    return definitionStyle.get("size") != null ? definitionStyle.get("size") : "8";
  }

  /**
   * Ignore words if their frequency is one of the list.
   */
  public List<Integer> getIgnoreFrequencies() {
    if (ignoreFrequencies != null && ignoreFrequencies.contains(null)) {
      throw new AssertionError("ignoreFrequencies list should not contain null");
    }
    return ignoreFrequencies != null ? ignoreFrequencies : new ArrayList<>();
  }
}

