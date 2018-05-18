package jijimaku;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.services.langparser.LangParser.PosTag;
import jijimaku.utils.FileManager;
import jijimaku.utils.SubtitleFile;


/**
 * Jijimaku configuration parameters(read from a YAML file).
 */
public class AppConfig {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final Pattern IS_HTML_COLOR = Pattern.compile("^#[a-fA-F]{6}$");

  private static final String DEFAULT_ASS_STYLES = "[V4+ Styles]\n"
      + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut"
      + ", ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
      + "Style: " + SubtitleFile.SubStyle.Definition + ",Arial,%d,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n"
      + "Style: " + SubtitleFile.SubStyle.Default + ",Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";

  private static final List<String> DEFAULT_HIGHLIGHT_COLORS = Collections.singletonList(
      "#FFFFFF"
  );

  // Yaml properties
  private final String configFilePath;
  private final Map<String, Object> configMap;

  // Jijimaku config values
  private final String dictionary;
  private final Integer definitionSize;
  private final Boolean displayOtherLemma;
  private final List<Integer> ignoreFrequencies;
  private final List<String> ignoreWords;

  private final EnumSet<PosTag> partOfSpeechToAnnotate;
  private List<String> highlightColors;

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
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }

    dictionary = getConfigValue("dictionary", String.class);
    definitionSize = getConfigValue("definitionSize", Integer.class, 8);
    displayOtherLemma = getConfigValue("displayOtherLemma", Boolean.class);

    ignoreWords = getConfigList("ignoreWords", String.class);
    ignoreFrequencies = getConfigList("ignoreFrequencies", Integer.class);

    // Validate partOfSpeechToAnnotate config
    List<String> posListStr = getConfigList("partOfSpeechToAnnotate", String.class);
    try {
      List<PosTag> posList = posListStr.stream().map(PosTag::valueOf).collect(toList());
      partOfSpeechToAnnotate = EnumSet.copyOf(posList);
    } catch (IllegalArgumentException | NullPointerException exc) {
      LOGGER.error("The partOfSpeechToAnnotate list in config.yaml contains an invalid value");
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }

    // Validate highlightColors config
    highlightColors = getConfigList("highlightColors", String.class);
    highlightColors = highlightColors.stream().filter(c -> {
      if (!IS_HTML_COLOR.matcher(c).matches()) {
        LOGGER.warn("config.yaml contains an invalid color(expected format is #FFFFFF): " + c);
        return false;
      }
      return true;
    }).collect(toList());
    if (highlightColors.isEmpty()) {
      highlightColors = DEFAULT_HIGHLIGHT_COLORS;
    }

    // Validate assStyles config
    String defaultStyles = String.format(DEFAULT_ASS_STYLES, getDefinitionSize());
    assStyles = getConfigValue("assStyles", String.class, defaultStyles);

    // TODO: properNouns list provided by user for Japanese parsing
    // Ignore fo now
    properNouns = new HashMap<>();
  }

  /**
   * Generic method to get a config value of some type.
   * @param paramKey Yaml file key for the value
   * @param paramType Java class that can represent this value
   * @param defaultValue default value to use if config value is missing
   * @return the config value as a paramType instance, or null if missing from config.
   */
  private <T> T getConfigValue(String paramKey, Class<T> paramType, T defaultValue) {
    if (!configMap.containsKey(paramKey) || configMap.get(paramKey) == null) {
      return defaultValue;
    }
    try {
      return paramType.cast(configMap.get(paramKey));
    } catch (ClassCastException exc) {
      LOGGER.error("Config parameter {} in file {} is not in the expected format(see logs). "
          + "Check the documentation for the proper syntax.", paramKey, configFilePath);
      LOGGER.debug(exc);
      throw new UnexpectedCriticalError();
    }
  }

  /**
   * If no default value is provided, use default=null.
   */
  private <T> T getConfigValue(String paramKey, Class<T> paramType) {
    return getConfigValue(paramKey, paramType, null);
  }

  /**
   * Generic method to get a config list of some type.
   * Check each element is not null and is an instance of the provided type
   */
  private <T> List<T> getConfigList(String paramKey, Class<T> listEltsType) {
    List<T> list = getConfigValue(paramKey, (new ArrayList<T>()).getClass());
    if (list == null) {
      list = new ArrayList<>();
    }

    // Check each list element is valid
    return list.stream().filter(e -> {
      if (e == null) {
        LOGGER.warn("config.yaml contains a null value in list " + paramKey);
        return false;
      } else if (!listEltsType.isInstance(e)) {
        LOGGER.warn("config.yaml list {} contains a value that is not {}", paramKey, listEltsType.getCanonicalName());
        return false;
      }
      return true;
    }).collect(Collectors.toList());
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
  private Integer getDefinitionSize() {
    return definitionSize;
  }

  /**
   * List of colors to use successively to highlight the defined words in a subtitle caption.
   */
  public List<String> getHighlightColors() {
    return highlightColors;
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
    return ignoreFrequencies;
  }

  /**
   * Set of subtitles words that should be ignored(not annotated).
   */
  public List<String> getIgnoreWords() {
    return ignoreWords;
  }

  /**
   * List of PartOfSpeech that should be annotated (the others will be ignored).
   */
  public EnumSet<PosTag> getPartOfSpeechToAnnotate() {
    return partOfSpeechToAnnotate;
  }

  /**
   * Return the whole ASS subtitle style definition string if present.
   * See DEFAULT_ASS_STYLES for an example,
   * and https://www.matroska.org/technical/specs/subtitles/ssa.html for the specs.
   */
  public String getSubtitleStyles() {
    return assStyles;
  }

  public Map<String,String> getProperNouns() {
    return properNouns;
  }
}

