package jijimaku.services;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import jijimaku.errors.UnexpectedError;
import jijimaku.utils.FileManager;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import subtitleFile.Caption;
import subtitleFile.FatalParsingException;
import subtitleFile.FormatASS;
import subtitleFile.FormatSRT;
import subtitleFile.Style;
import subtitleFile.TimedTextFileFormat;
import subtitleFile.TimedTextObject;


// A class to work with ASS/SRT subtitle files
// Underwood for now we use the multi-format subtitle library https://github.com/JDaren/subtitleConverter
public class SubtitleService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String JIJIMAKU_SIGNATURE = "ANNOTATED-BY-JIJIMAKU";

  public enum SubStyle {
    Definition,
    Default
  }

  private static final String DEFAULT_STYLES = "[V4+ Styles]\n"
      + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut"
      + ", ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
      + "Style: " + SubStyle.Definition + ",Arial,8,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n"
      + "Style: " + SubStyle.Default + ",Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";


  private TimedTextObject tto;
  private Hashtable<String, Style> substyles;
  private TreeMap<Integer, Caption> annotationCaptions;

  // Caption iterator
  private Iterator<Map.Entry<Integer, Caption>> captionIt;
  private Map.Entry<Integer, Caption> currentCaptionEntry;

  public SubtitleService(YamlConfig yamlConfig) {
    try {
      // Read subtitle styles from config, or use default if missing
      TimedTextFileFormat ttff = new FormatASS();
      String styleStr = yamlConfig.containsKey("subtitlesStyles") ? yamlConfig.getString("subtitlesStyles") : DEFAULT_STYLES;
      tto = ttff.parseFile("", new ByteArrayInputStream(styleStr.getBytes("UTF-8")));
      substyles = tto.styling;
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException("config.yaml should be encoded in UTF8");
    } catch (FatalParsingException e) {
      throw new IllegalArgumentException("The ASS subtitle styles specified in the config file seem invalid(parsing error)");
    } catch (IOException e) {
      throw new IllegalArgumentException("The ASS subtitle styles specified in the config file seem invalid(IO error)");
    }
  }

  // Read a file and return true if it was written by us
  // (search for app signature in first 5 lines)
  public static boolean isSubDictFile(File f) throws IOException {
    InputStreamReader in = new InputStreamReader(FileManager.getUtf8Stream(f));
    try (BufferedReader br = new BufferedReader(new BufferedReader(in))) {
      String line;
      int lineNum = 0;
      while ((line = br.readLine()) != null && lineNum < 5) {
        if (line.contains(JIJIMAKU_SIGNATURE)) {
          return true;
        }
        lineNum++;
      }
    }
    return false;
  }


  public void readFromSrt(File f) throws IOException, FatalParsingException {

    if (!FilenameUtils.getExtension(f.getName()).equals("srt") && !FilenameUtils.getExtension(f.getName()).equals("ass")) {
      LOGGER.error("invalid subtitle file extension file: {}", f.getName());
      throw new UnexpectedError();
    }

    // Read subtitle file contents
    TimedTextFileFormat ttff = FilenameUtils.getExtension(f.getName()).equals("srt")
        ? new FormatSRT()
        : new FormatASS();
    tto = ttff.parseFile(f.getName(), FileManager.getUtf8Stream(f));
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
    String colorizedWord = addStyleToText(word, TEXTSTYLE.COLOR, htmlHexColor);
    currentCaptionEntry.getValue().content = currentCaptionEntry.getValue().content.replace(word, colorizedWord);
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
    Iterator<Map.Entry<Integer, Caption>> it = tto.captions.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, Caption> caption = it.next();
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
  public static String addStyleToText(String str, TEXTSTYLE style, String param) {
    switch (style) {
      case BOLD:
        return "{\\b1}" + str + "{\\r}";
      case COLOR:
        String assHexColor = param.substring(5,7) + param.substring(3,5) + param.substring(1,3);
        return "{\\c&" + assHexColor  + "&}" + str + "{\\r}";
      case ZOOM:
        Integer assZoom = Math.round(Float.parseFloat(param) * 100);
        return "{\\fscx" + assZoom  + "\\fscy" + assZoom + "}" + str + "{\\r}";
      default:
        return str;
    }
  }

  public static String addStyleToText(String str, TEXTSTYLE style) {
    return addStyleToText(str, style, null);
  }

  public enum TEXTSTYLE {
    BOLD,
    COLOR,
    ZOOM
  }
}

