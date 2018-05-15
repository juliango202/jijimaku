package jijimaku;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jijimaku.errors.JijimakuError;
import jijimaku.services.langparser.LangParser;
import jijimaku.utils.SubtitleFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedError;
import jijimaku.utils.FileManager;


/**
 * Jijimaku configuration parameters(read from a YAML file).
 */
public class AppConfig {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String DEFAULT_ASS_STYLES = "[V4+ Styles]\n"
      + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut"
      + ", ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
      + "Style: " + SubtitleFile.SubStyle.Definition + ",Arial,%d,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n"
      + "Style: " + SubtitleFile.SubStyle.Default + ",Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";


  // Yaml properties
  private final String configFilePath;
  private final Map<String, Object> configMap;

  // Jijimaku config values
  private final String dictionary;
  private final Integer definitionSize;
  private final List<String> highlightColors;
  private final Boolean displayOtherLemma;
  private final List<Integer> ignoreFrequencies;
  private final List<String> ignoreWords;
  private final List<String> partOfSpeechToAnnotate;

  private final String assStyles;
  private final Map<String, String> properNouns;


  /**
   * Read user preferences from a YAML config file.
   */
  public AppConfig(File configFile) {
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

    // TODO: check type of array values(not done on cast)
    dictionary = getConfigValue("dictionary", String.class);
    definitionSize = getConfigValue("definitionSize", Integer.class);
    highlightColors = getConfigValue("highlightColors", (new ArrayList<String>()).getClass());
    displayOtherLemma = getConfigValue("displayOtherLemma", Boolean.class);
    ignoreFrequencies = getConfigValue("ignoreFrequencies", (new ArrayList<Integer>()).getClass());
    ignoreWords = getConfigValue("ignoreWords", (new ArrayList<String>()).getClass());
    partOfSpeechToAnnotate = getConfigValue("partOfSpeechToAnnotate", (new ArrayList<String>()).getClass());

    properNouns = new HashMap<>();  // Ignore fo now
    assStyles = getConfigValue("assStyles", String.class);
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

  /**
   * Name of the dictionary file used to source words definitions.
   * The dictionary file must follow the JIJI format => https://github.com/juliango202/jiji
   */
  public String getDictionary() {
    return dictionary;
  }


  /**
   * Font-size to use when writing dictionary definitions.
   */
  public Integer getDefinitionSize() {
    return definitionSize != null ? definitionSize : 8;
  }

  /**
   * List of colors to use successively to highlight the defined words in a subtitle caption.
   */
  public List<String> getHighlightColors() {
    if (highlightColors != null && highlightColors.contains(null)) {
      throw new JijimakuError("highlightColors list should not contain null");
    }
    return highlightColors != null ? highlightColors : new ArrayList<>(Arrays.asList("#FFFFFF"));
  }

  /**
   * Flag to display all lemmas of a defined word or not.
   */
  public Boolean getDisplayOtherLemma() {
    return displayOtherLemma;
  }

  /**
   * Ignore words if their frequency is one of the list.
   */
  public List<Integer> getIgnoreFrequencies() {
    if (ignoreFrequencies != null && ignoreFrequencies.contains(null)) {
      throw new JijimakuError("ignoreFrequencies list should not contain null");
    }
    return ignoreFrequencies != null ? ignoreFrequencies : new ArrayList<>();
  }

  /**
   * Set of subtitles words that should be ignored(not annotated).
   */
  public List<String> getIgnoreWords() {
    if (ignoreWords != null && ignoreWords.contains(null)) {
      throw new JijimakuError("ignoreWords list should not contain null");
    }
    return ignoreWords != null ? ignoreWords : new ArrayList<>();
  }

  /**
   * List of PartOfSpeech that should be annotated (the others will be ignored).
   */
  public EnumSet<LangParser.PosTag> getPartOfSpeechToAnnotate() {
    if (partOfSpeechToAnnotate != null && partOfSpeechToAnnotate.contains(null)) {
      throw new JijimakuError("partOfSpeechToAnnotate list should not contain null");
    }
    List<String> posListStr = partOfSpeechToAnnotate != null ? partOfSpeechToAnnotate : new ArrayList<>();
    try {
      List<LangParser.PosTag> posList = posListStr.stream().map(p -> LangParser.PosTag.valueOf(p)).collect(Collectors.toList());
      return EnumSet.copyOf(posList);
    } catch (IllegalArgumentException | NullPointerException exc) {
      LOGGER.debug(exc);
      throw new JijimakuError("The partOfSpeechToAnnotate list in config.yaml contains an invalid value");
    }
  }

  /**
   * Return the whole ASS subtitle style definition string if present.
   * See DEFAULT_ASS_STYLES for an example,
   * and https://www.matroska.org/technical/specs/subtitles/ssa.html for the specs.
   * Return null if SUBTITLE_STYLES is missing from config
   */
  public String getSubtitleStyles() {
    if (assStyles != null) {
      return assStyles;
    }
    return String.format(DEFAULT_ASS_STYLES,
      getDefinitionSize()
    );
  }

  public Map<String,String> getProperNouns() {
    return properNouns;
  }
}

