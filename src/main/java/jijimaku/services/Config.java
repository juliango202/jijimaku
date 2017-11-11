package jijimaku.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jijimaku.utils.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedError;


public class Config {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SUBTITLE_STYLES = "subtitlesStyles";
  private static final String JIJI_DICTIONARY = "jijiDictionary";
  private static final String PROPER_NOUNS = "properNouns";

  private final String configFilePath;

  private Map<String, Object> configMap = null;

  /**
   * Read user preferences from YAML file.
   * @param configFilePath path to the YAML file
   */
  @SuppressWarnings("unchecked")
  public Config(String configFilePath) {
    this.configFilePath = configFilePath;
    try {
      Yaml yaml = new Yaml();
      InputStream stream = FileManager.getUtf8Stream(new File(configFilePath));

      configMap = (Map<String, Object>) yaml.load(stream);
    } catch (IOException exc) {
      LOGGER.error("Problem reading YAML config {}", configFilePath);
      LOGGER.debug("Got exception", exc);
      throw new UnexpectedError();
    }
  }

  private <T> T getConfigValue(String configKey) {
    if (!configMap.containsKey(configKey)) {
      return null;
    }
    return (T) configMap.get(configKey);
  }

  public String getConfigFilePath() {
    return configFilePath;
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> getStringDictionary(String configKey) {
    return (Map<String, String>) configMap.get(configKey);
  }



  public boolean getDisplayOtherLemma() {
    return (boolean)configMap.get("displayOtherLemma");
  }

  public Map<String,String> getColors() {
    return (HashMap)configMap.get("colors");
  }

  public HashSet<String> getIgnoreWords() {
    String ignoreWordsText = (String) configMap.get("ignoreWords");
    List<String> wordList = Arrays.asList(ignoreWordsText.split("\\r?\\n"));
    wordList.removeIf(word -> word.trim().isEmpty());
    return new HashSet<>(wordList);
  }

  public Map<String,String> getProperNouns() {
    return getConfigValue(PROPER_NOUNS);
  }

  /**
   * Return the JijiDictionary file(*.jiji.zip, *.jiji.yaml) used for looking up definitions.
   */
  public String getJijiDictionary() {
    return getConfigValue(JIJI_DICTIONARY);
  }

  /**
   * Return the whole ASS subtitle style definition string if present.
   * See SubtitleService.DEFAULT_STYLES for an example
   * Return null if SUBTITLE_STYLES is missing from config
   */
  public String getSubtitleStyles() {
    return getConfigValue(SUBTITLE_STYLES);
  }


  /**
   * Generate ASS format subtitle style from user preferences.
   * @return The subtitle style to use in ASS format
   */
  //  public String getSubtitlesStyle() {
  //    if (configMap.containsKey("subtitlesStyles")) {
  //      return (String) configMap.get("subtitlesStyles");
  //    } else {
  //      return "[V4+ Styles]\n"
  //          + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut"
  //          + ", ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
  //          + "Style: Definition,Arial,6,&H009799af,&H00677f69,&H00000000,&H99000000,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n"
  //          + "Style: Default,Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";
  //    }
  //  }

}

