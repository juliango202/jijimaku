package jijimaku.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;
import jijimaku.utils.FileManager;

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
public class SubtitleService {
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

  private static final String DEFAULT_STYLES = "[V4+ Styles]\n"
      + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut"
      + ", ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
      + "Style: " + SubStyle.Definition + ",Arial,%s,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n"
      + "Style: " + SubStyle.Default + ",Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";


  private TimedTextObject tto;
  private Hashtable<String, Style> substyles;
  private TreeMap<Integer, Caption> annotationCaptions;

  // Caption iterator
  private Iterator<Map.Entry<Integer, Caption>> captionIt;
  private Map.Entry<Integer, Caption> currentCaptionEntry;

  public SubtitleService(Config config) {
    try {
      // Read subtitle styles from config, or use default if missing
      TimedTextFileFormat ttff = new FormatASS();
      final String styles;
      if (config.getSubtitleStyles() != null) {
        styles = config.getSubtitleStyles();
      } else {
        styles = String.format(DEFAULT_STYLES,
            config.getDefinitionSize()
        );
      }
      tto = ttff.parseFile("", new ByteArrayInputStream(styles.getBytes("UTF-8")));
      substyles = tto.styling;
    } catch (UnsupportedEncodingException exc) {
      LOGGER.error("Cannot understand subtitle styles definition. Most likely {} contains some characters not encoded in UTF8.",
          config.getConfigFilePath());
      LOGGER.debug("Got exception", exc);
      throw new UnexpectedError();
    } catch (FatalParsingException | IOException exc) {
      LOGGER.error("The ASS subtitle styles specified in {} seem invalid", config.getConfigFilePath());
      LOGGER.debug("Got exception", exc);
      throw new UnexpectedError();
    }
  }


  /**
   * Read a file and return true if it was written by us.
   * (search for app signature in first 5 lines)
   */
  public static boolean isSubDictFile(String fileContents) throws IOException {
    return fileContents.contains(JIJIMAKU_SIGNATURE);
  }


  public void readFile(String fileName, String fileContents) throws IOException, FatalParsingException {
    if (!FilenameUtils.getExtension(fileName).equals("srt") && !FilenameUtils.getExtension(fileName).equals("ass")) {
      LOGGER.error("invalid subtitle file extension file: {}", fileName);
      throw new UnexpectedError();
    }

    // Read subtitle file contents
    TimedTextFileFormat ttff = FilenameUtils.getExtension(fileName).equals("srt")
        ? new FormatSRT()
        : new FormatASS();

    // Convert String to InputStream to match subtitleFile API
    byte[] byteData = fileContents.getBytes("UTF-8");
    // Must use BOMInputStream otherwise files with BOM will broke :(((
    // => http://stackoverflow.com/questions/4897876/reading-utf-8-bom-marker
    try (BOMInputStream inputStream = new BOMInputStream(new ByteArrayInputStream(byteData))) {
      tto = ttff.parseFile(fileName, inputStream, StandardCharsets.UTF_8);
    }

    tto.styling = substyles;
    tto.description = JIJIMAKU_SIGNATURE;
    if (tto.warnings.length() > "List of non fatal errors produced during parsing:\n\n".length()) {
      LOGGER.warn("\n" + tto.warnings);
    }
    annotationCaptions = new TreeMap<>();
    captionIt = tto.captions.entrySet().iterator();
  }

  public boolean hasNextCaption() {
    return captionIt.hasNext();
  }

  public String nextCaption() {
    currentCaptionEntry = captionIt.next();
    return currentCaptionEntry.getValue().content.replaceAll("<br\\s*/?>", "");
  }

  public void setCaptionStyle() {
    currentCaptionEntry.getValue().style = substyles.get("Default");
  }


  public void colorizeCaptionWord(String word, String htmlHexColor) {
    StringBuilder content = new StringBuilder(currentCaptionEntry.getValue().content);

    // We want to find the word even if it spread over multiple lines
    // Solution is from https://stackoverflow.com/a/9896878/257272
    // Build a regexp with potential new line <br />* after every character except the last one.
    // (?!$) is a negative lookahead meaning the line below won't match the last character of the String
    String regex = word.replaceAll("(.(?!$))", "$1(?:<br />)*");
    Matcher matcher = Pattern.compile(regex).matcher(content.toString());
    if (!matcher.find()) {
      LOGGER.debug("Couldn't colorize word {} because it wasn't found in {}", word, content.toString());
      return;
    }

    String startStyle = "{\\c&" + htmlColorToAss(htmlHexColor)  + "&}";
    content.insert(matcher.start(), startStyle);
    String endStyle = "{\\r}";
    content.insert(matcher.end() + startStyle.length(), endStyle);

    currentCaptionEntry.getValue().content = content.toString();
  }


  public void addJijimakuMark() {
    Integer firstCaptionTimeMs = tto.captions.entrySet().iterator().next().getKey();
    if (firstCaptionTimeMs == 0) {
      // Rare case, give up
      return;
    }

    // Caption is read from recource file
    TimedTextFileFormat ttff = new FormatASS();
    try {
      TimedTextObject tto = ttff.parseFile("", getClass().getClassLoader().getResourceAsStream("JijimakuMark.ass"));
      Caption jijimakuMark = tto.captions.values().iterator().next();
      jijimakuMark.content = "★ Definitions by {\\c&AAAAFF&}{\\b1}JIJIMAKU{\\r} using {\\c&FFAAAA&}Jim's Breen Japanese dictionary{\\r}";
      annotationCaptions.put(0, jijimakuMark);
    } catch (IOException exc) {
      LOGGER.error("Cannot read JijimakuMark.ass.", exc);
    } catch (FatalParsingException exc) {
      LOGGER.error("Cannot parse JijimakuMark.ass.", exc);
    }
  }

  public void addAnnotationCaption(SubStyle style, String annotationStr) {
    // When adding a new caption to the subtitle we must find a key(int time) not yet used
    Integer time = currentCaptionEntry.getKey() + 1;
    while (tto.captions.containsKey(time) || annotationCaptions.containsKey(time)) {
      time++;
    }
    Caption annotation = new Caption();
    annotation.content = annotationStr;
    annotation.start = currentCaptionEntry.getValue().start;
    annotation.end = currentCaptionEntry.getValue().end;
    annotation.style = substyles.get(style.toString());
    annotationCaptions.put(time, annotation);
  }

  public void writeToAss(String outFile) throws IOException {
    // Fix newline problem while https://github.com/JDaren/subtitleConverter/issues/36 is not resolved
    // --------------------------------------------
    for (Map.Entry<Integer, Caption> caption : tto.captions.entrySet()) {
      caption.getValue().content = caption.getValue().content.replaceAll("<br\\s*/?>", "\\\\N");
    }
    // --------------------------------------------

    // Before writing we merge original captions and annotations
    if (!annotationCaptions.isEmpty()) {
      for (Map.Entry<Integer, Caption> entry : annotationCaptions.entrySet()) {
        tto.captions.put(entry.getKey(), entry.getValue());
      }
    }
    FileManager.writeStringArrayToFile(outFile, tto.toASS());
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

  // Ass color format is RGB

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

