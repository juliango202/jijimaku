package workers;

import langdictionary.JMDict;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import error.SubsDictError;
import error.UnexpectedError;
import subtitleFile.*;

import javax.swing.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ExecutionException;

import langparser.JapaneseParser;
import langparser.LangParser.*;
import utils.SubtitleFile;
import utils.SubtitleFile.SubStyle;
import utils.YamlConfig;


// Background task that annotates a subtitle file with words definition
public class WorkerSubAnnotator extends SwingWorker<Void, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private List<String> subtitlesFilePaths;
    private InputStream jmdictIn;
    private String configFile;

    private static final EnumSet<POSTag> POSTagsToIgnore = EnumSet.of(
        POSTag.PUNCTUATION,
        POSTag.SYMBOL,
        POSTag.NUMERAL,
        POSTag.PARTICLE,
        POSTag.DETERMINER,
        POSTag.CONJUNCTION,
        POSTag.AUXILIARY_VERB,
        POSTag.SUFFIX
    );

    public WorkerSubAnnotator(List<String> subtitlesFilePaths, InputStream jmdictIn, String configFile) {
        this.subtitlesFilePaths = subtitlesFilePaths;
        this.jmdictIn = jmdictIn;
        this.configFile = configFile;
    }

    private JapaneseParser langParser = null;
    private JMDict dict = null;
    private HashSet<String> ignoreWordsSet = null;  // user list of words to ignore during annotation
    private SubtitleFile subtitleFile = null;


    // Initialization => load dictionary & parser data
    // This is a quite heavy operation so it should be launched from background thread
    private void doInitialization() {
        System.out.print( "Initializing parser and dictionary...");

        // Load configuration
        YamlConfig config = new YamlConfig(configFile);
        System.out.print(".");

        // Load user list of words to ignore
        ignoreWordsSet = new HashSet<String>();
        for (String w : new HashSet<String>(Arrays.asList(config.getIgnoreWords().split("\\r?\\n")))) {
            if (w.trim().length() > 0) ignoreWordsSet.add( w.trim() );
        }

        // Initialize dictionary
        dict = new JMDict(jmdictIn);
        dict.outDict();
        System.out.print( ".");

        // Initialize parser
        langParser = new JapaneseParser(config);
        System.out.print( ".");

        // Read subtitles styles from config
        subtitleFile = new SubtitleFile(config);

        System.out.println( "OK");
    }

    // Return true if file was annotated, false otherwise
    private boolean annotateSubtitleFile(File f) throws IOException, FatalParsingException {

        if(!FilenameUtils.getExtension(f.getName()).equals("srt")) {
            LOGGER.error("invalid SRT file: {}", f.getName());
            throw new UnexpectedError();
        }

        subtitleFile.readFromSRT(f);

        // Loop through the subtitle file captions one by one
        int nbAnnotations = 0;
        while(subtitleFile.hasNextCaption()) {

            String currentCaptionText = subtitleFile.nextCaption();
            subtitleFile.setCaptionStyle(SubStyle.Default);

            // Parse subtitle and lookup definitions
            String allAnnotations = "";
            String parse = "";
            for( TextToken token : langParser.syntaxicParse(currentCaptionText)) {

                parse += token.currentForm + "|";
                // Ignore user words list
                if(ignoreWordsSet.contains(token.currentForm) || ignoreWordsSet.contains(token.canonicalForm)) continue;

                // Ignore unimportant grammatical words
                if(POSTagsToIgnore.contains(token.tag)) continue;

                // Ignore words that are not in dictionary
                String def = dict.getMeaning(token.canonicalForm);
                if( def.isEmpty() ) def = dict.getMeaning(token.currentForm);
                if( def.isEmpty() ) continue;

                // If all is good, add definition to subtitle annotation
                allAnnotations += "★ " + def + "\\N\\n";
                nbAnnotations++;

                // Set a different color for words that are defined
                subtitleFile.colorizeCaptionWord(token.currentForm,"#AAAAFF");
            }

            //System.out.println(parse);
            //System.out.println(dict.getParse(currentCaptionText)+"\n");
            subtitleFile.addAnnotationCaption(SubStyle.Definition, allAnnotations);
        }

        if(nbAnnotations == 0 ) return false;
        subtitleFile.writeToASS(f.getParent() + "/" + FilenameUtils.getBaseName(f.getName()) + ".ass");
        return true;
    }

    @Override
    public Void doInBackground() throws Exception {
        if( SwingUtilities.isEventDispatchThread() ) throw new Exception("SubtitlesTranslator should not run on the EDT thread!");

        if(langParser == null) doInitialization();

        for (String filePath : subtitlesFilePaths) {
            File fileEntry = new File(filePath);
            try {
                System.out.print("Annotate " + fileEntry.getName() + "...");
                boolean annotated = annotateSubtitleFile( fileEntry );
                System.out.println( annotated? "OK" : "PASS" );
            }
            catch (Exception e) {
                // on error print stack trace and continue to next file
                System.out.println( "ERROR" );
                e.printStackTrace();
                System.out.println();
            }
        }
        return null;
    }
}

