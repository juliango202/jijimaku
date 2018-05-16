package jijimaku.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.JijimakuError;
import jijimaku.errors.UnexpectedError;

import subtitleFile.Caption;
import subtitleFile.FatalParsingException;
import subtitleFile.FormatASS;
import subtitleFile.FormatSRT;
import subtitleFile.Style;
import subtitleFile.TimedTextFileFormat;
import subtitleFile.TimedTextObject;


/**
 * A class to work with ASS/SRT subtitle files.
 * Underwood for now we use the multi-format subtitle library https://github.com/JDaren/subtitleConverter
 */
public class SubtitleFile {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String JIJIMAKU_SIGNATURE = "ANNOTATED-BY-JIJIMAKU";

  public enum SubStyle {
    Definition,
    Default
  }

  private final TimedTextObject timedText;
  private final Hashtable<String, Style> styles;
  private final TreeMap<Integer, Caption> annotationCaptions;

  // Caption iterator
  private final Iterator<Map.Entry<Integer, Caption>> captionIter;
  private Map.Entry<Integer, Caption> currentCaption;

  private int nbCaptionAnnotated = 0;

  public SubtitleFile(String fileName, String fileContents, String stylesStr) throws IOException, FatalParsingException {
    LOGGER.debug("Parsing subtitle file {}", fileName);

    TimedTextFileFormat timedTextFormat;
    switch (FilenameUtils.getExtension(fileName)) {
      case "ass":
        timedTextFormat = new FormatASS();
        break;
      case "srt":
        timedTextFormat = new FormatSRT();
        break;
      default:
        LOGGER.error("invalid subtitle file extension file: {}", fileName);
        throw new UnexpectedError();
    }

    // Convert String to InputStream to match subtitleFile API
    byte[] byteData = fileContents.getBytes("UTF-8");
    // Must use BOMInputStream otherwise files with BOM will broke :(((
    // => http://stackoverflow.com/questions/4897876/reading-utf-8-bom-marker
    try (BOMInputStream inputStream = new BOMInputStream(new ByteArrayInputStream(byteData))) {
      timedText = timedTextFormat.parseFile(fileName, inputStream, StandardCharsets.UTF_8);
    }

    if (timedText.warnings.length() > "List of non fatal errors produced during parsing:\n\n".length()) {
      LOGGER.warn("There was some warnings during parsing. See logs.");
      LOGGER.debug("Got warnings: {}", "\n" + timedText.warnings);
    }

    styles = parseStyles(stylesStr);
    timedText.styling = styles;
    timedText.description = JIJIMAKU_SIGNATURE;
    annotationCaptions = new TreeMap<>();

    // Initialization: set style to Default
    timedText.captions.values().stream().forEach(c -> c.style = styles.get("Default"));

    captionIter = timedText.captions.entrySet().iterator();
  }

  /**
   * Set subtitles display styles.
   * @param stylesStr String representing the styles in ASS format. See DEFAULT_STYLES for an example.
   */
  private Hashtable<String, Style> parseStyles(String stylesStr) {
    try {
      // Read subtitle styles from config, or use default if missing
      TimedTextFileFormat emptyTimedText = new FormatASS();
      TimedTextObject styledTimedText = emptyTimedText.parseFile("", new ByteArrayInputStream(stylesStr.getBytes("UTF-8")));
      return styledTimedText.styling;
    } catch (UnsupportedEncodingException exc) {
      LOGGER.error("Cannot understand subtitle styles definition. Possibly some characters not encoded in UTF8?");
      LOGGER.debug("Got exception when parsing styles {}", stylesStr, exc);
      throw new UnexpectedError();
    } catch (FatalParsingException | IOException exc) {
      LOGGER.error("The subtitle styles seem invalid");
      LOGGER.debug("Got exception when parsing styles {}", stylesStr, exc);
      throw new UnexpectedError();
    }
  }

  public boolean hasNext() {
    return captionIter.hasNext();
  }

  public String nextCaption() {
    currentCaption = captionIter.next();
    return currentCaption.getValue().content;
  }

  private String findWordRegexp(String expression, String wordSeparator) {
    if (wordSeparator.isEmpty()) {
      // We want to find the word even if it spread over multiple lines
      // Solution is from https://stackoverflow.com/a/9896878/257272
      // Build a regexp with potential new line <br />* after every character except the last one.
      // (?!$) is a negative lookahead meaning the line below won't match the last character of the String
      return expression.replaceAll("(.(?!$))", "$1(?:<br />)*");
    } else if (wordSeparator.equals(" ")) {
      // Same thing but only search for newline(<br>) at word boundary i.e. space
      return "\\b" + expression.replaceAll(" ", "(?:\\\\s|<br />)*") + "\\b";
    } else {
      throw new JijimakuError("findWordRegexp not implemented for wordSeparator " + wordSeparator);
    }
  }

  public void colorizeCaptionWord(String expression, String htmlHexColor, String wordSeparator) {
    StringBuilder content = new StringBuilder(currentCaption.getValue().content);
    String findWordRe = findWordRegexp(expression, wordSeparator);
    Matcher matcher = Pattern.compile(findWordRe).matcher(content.toString());
    if (!matcher.find()) {
      LOGGER.debug("Couldn't colorize word {} because it wasn't found in {}", expression, content.toString());
      return;
    }

    String startStyle = "{\\c&" + htmlColorToAss(htmlHexColor)  + "&}";
    content.insert(matcher.start(), startStyle);
    String endStyle = "{\\r}";
    content.insert(matcher.end() + startStyle.length(), endStyle);

    // If there is another match, cancel the coloring because we don't know which one correspond to our annotation
    if (matcher.find()) {
      LOGGER.debug("Couldn't colorize word {} because there is several matches in {}", expression, content.toString());
      return;
    }

    currentCaption.getValue().content = content.toString();
  }

  /**
   * Add a short "by Jijimaku" message in the subtitle caption at the start of the video.
   */
  public void addJijimakuMark(String dictionaryTitle) {
    Integer firstCaptionTimeMs = timedText.captions.entrySet().iterator().next().getKey();
    if (firstCaptionTimeMs == 0) {
      // Rare case, give up
      return;
    }

    // Caption is read from recource file
    TimedTextFileFormat ttff = new FormatASS();
    try {
      TimedTextObject tto = ttff.parseFile("", getClass().getClassLoader().getResourceAsStream("JijimakuMark.ass"));
      Caption jijimakuMark = tto.captions.values().iterator().next();
      jijimakuMark.content = "★ Definitions by {\\c&AAAAFF&}{\\b1}JIJIMAKU{\\r} using {\\c&FFAAAA&}" + dictionaryTitle + "{\\r}";
      annotationCaptions.put(0, jijimakuMark);
    } catch (IOException exc) {
      LOGGER.error("Cannot read JijimakuMark.ass.", exc);
    } catch (FatalParsingException exc) {
      LOGGER.error("Cannot parse JijimakuMark.ass.", exc);
    }
  }

  public void annotate(List<String> annotations) {
    if (annotations.isEmpty()) {
      return;
    }
    // When adding a new caption to the subtitle we must find a key(int time) not yet used
    Integer time = currentCaption.getKey() + 1;
    while (timedText.captions.containsKey(time) || annotationCaptions.containsKey(time)) {
      time++;
    }
    Caption annotation = new Caption();
    annotation.content = String.join("\\N", annotations);
    annotation.start = currentCaption.getValue().start;
    annotation.end = currentCaption.getValue().end;
    annotation.style = styles.get(SubStyle.Definition.toString());
    annotationCaptions.put(time, annotation);
    nbCaptionAnnotated++;
  }

  public String[] toAssFormat() {
    // Before exporting we add all the annotations to the file captions
    timedText.captions.putAll(annotationCaptions);
    return timedText.toASS();
  }

  public int getNbCaptionAnnotated() {
    return nbCaptionAnnotated;
  }

  /**
   * Read a file and return true if it was written by us.
   * (search for app signature in first 5 lines)
   */
  public static boolean isJijimakuFile(String fileContents) throws IOException {
    return fileContents.contains(JIJIMAKU_SIGNATURE);
  }

  /**
   * Style some text string using ASS style tags.
   * See http://docs.aegisub.org/3.2/ASS_Tags/
   */
  public static String addStyleToText(String str, TextStyle style, String param) {
    switch (style) {
      case BOLD:
        return "{\\b1}" + str + "{\\r}";
      case COLOR:
        return "{\\c&" + htmlColorToAss(param)  + "&}" + str + "{\\r}";
      case ZOOM:
        Integer assZoom = Math.round(Float.parseFloat(param) * 100);
        return "{\\fscx" + assZoom  + "\\fscy" + assZoom + "}" + str + "{\\r}";
      default:
        return str;
    }
  }

  public static String addStyleToText(String str, TextStyle style) {
    return addStyleToText(str, style, null);
  }

  /**
   * Convert a html color which is RGB to ASS color which is BGR.
   */
  private static String htmlColorToAss(String col) {
    return col.substring(5,7) + col.substring(3,5) + col.substring(1,3);
  }

  public enum TextStyle {
    BOLD,
    COLOR,
    ZOOM
  }
}

