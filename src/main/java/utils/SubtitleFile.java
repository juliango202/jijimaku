package utils;

import subtitleFile.*;

import java.io.*;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;


// A class to work with ASS/SRT subtitle files
// Underwood for now we use the multi-format subtitleFile library by J. David Requejo
public class SubtitleFile {

    public enum SubStyle {
        Definition,
        Default
    }
    public static final String DEFAULT_STYLES = "[V4+ Styles]\n" +
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
            "Style: "+ SubStyle.Definition +",Arial,6,&H009799af,&H00677f69,&H00000000,&H99000000,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n" +
            "Style: "+ SubStyle.Default +",Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";


    private TimedTextObject tto;
    private Hashtable<String, Style> substyles;
    private TreeMap<Integer, Caption> annotationCaptions;

    // Caption iterator
    private Iterator<Map.Entry<Integer, Caption>> captionIt;
    private Map.Entry<Integer, Caption> currentCaptionEntry;

    public SubtitleFile(YamlConfig yamlConfig) {
        try {
            // Read subtitle styles from config, or use default if missing
            TimedTextFileFormat ttff = new FormatASS();
            String styleStr = yamlConfig.containsKey("subtitlesStyles") ? yamlConfig.getString("subtitlesStyles") : DEFAULT_STYLES;
            tto = ttff.parseFile("", new ByteArrayInputStream(styleStr.getBytes("UTF-8")));
            substyles = tto.styling;

        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("config.yaml should be encoded in UTF8");
        }
        catch (FatalParsingException e) {
            throw new IllegalArgumentException("The ASS subtitle styles specified in the config file seem invalid(parsing error)");
        }
        catch (IOException e) {
            throw new IllegalArgumentException("The ASS subtitle styles specified in the config file seem invalid(IO error)");
        }
    }

    public void readFromSRT(File f) throws IOException, FatalParsingException {
        // Read subtitle file contents
        TimedTextFileFormat ttff = new FormatSRT();
        tto = ttff.parseFile(f.getName(), FileManager.getUtf8Stream(f));
        tto.styling = substyles;
        if(tto.warnings.length() > "List of non fatal errors produced during parsing:\n\n".length()) {
            System.out.println("\n" + tto.warnings);
        }
        annotationCaptions = new TreeMap<Integer, Caption>();
        captionIt = tto.captions.entrySet().iterator();
    }

    public boolean hasNextCaption() {
        return captionIt.hasNext();
    }

    public String nextCaption() {
        currentCaptionEntry = (Map.Entry<Integer, Caption>) captionIt.next();
        return currentCaptionEntry.getValue().content.replaceAll("<br\\s*/?>","");
    }

    public void setCaptionStyle(SubStyle style) {
        currentCaptionEntry.getValue().style = substyles.get("Default");
    }

    public void colorizeCaptionWord(String word, String color) {
        currentCaptionEntry.getValue().content = currentCaptionEntry.getValue().content.replace(word,"{\\c&"+ color.substring(1) +"&}" + word + "{\\r}");
    }

    public void addAnnotationCaption(SubStyle style, String annotationStr) {
        // When adding a new caption to the subtitle we must find a key(int time) not yet used
        Integer time = currentCaptionEntry.getKey() + 1;
        while( tto.captions.containsKey(time) || annotationCaptions.containsKey(time) ) time++;
        Caption annotation = new Caption();
        annotation.content = annotationStr;
        annotation.start = currentCaptionEntry.getValue().start;
        annotation.end = currentCaptionEntry.getValue().end;
        annotation.style = substyles.get(style.toString());
        annotationCaptions.put(time, annotation);
    }

    public void writeToASS(String outFile) throws IOException {
        // Before writing we merge original captions and annotations
        if(!annotationCaptions.isEmpty()) {
            Iterator<Map.Entry<Integer, Caption>> i = annotationCaptions.entrySet().iterator();
            while(i.hasNext()) {
                Map.Entry<Integer, Caption> entry = (Map.Entry<Integer, Caption>) i.next();
                tto.captions.put( entry.getKey(), entry.getValue() );
            }
        }
        FileManager.writeStringArrayToFile(outFile,tto.toASS());
    }

}

