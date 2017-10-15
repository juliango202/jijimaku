package jiji.workers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jiji.error.UnexpectedError;
import jiji.langdictionary.JmDict;
import jiji.langparser.JapaneseParser;
import jiji.langparser.LangParser.PosTag;
import jiji.langparser.LangParser.TextToken;
import jiji.utils.SubtitleFile;
import jiji.utils.SubtitleFile.SubStyle;
import jiji.utils.YamlConfig;

import subtitleFile.FatalParsingException;


// Background task that annotates a subtitle file with words definition
public class WorkerSubAnnotator extends SwingWorker<Void, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private List<String> subtitlesFilePaths;
  private InputStream jmdictIn;
  private String configFile;

  private static final EnumSet<PosTag> POS_TAGS_TO_IGNORE = EnumSet.of(
      PosTag.PUNCTUATION,
      PosTag.SYMBOL,
      PosTag.NUMERAL,
      PosTag.PARTICLE,
      PosTag.DETERMINER,
      PosTag.CONJUNCTION,
      PosTag.AUXILIARY_VERB,
      PosTag.SUFFIX
  );

  /**
   * Swing worker to annotate a list of subtitle files.
   * @param subtitlesFilePaths list of subtitle files to process
   * @param jmdictIn the dictionnary file
   * @param configFile the config file
   */
  public WorkerSubAnnotator(List<String> subtitlesFilePaths, InputStream jmdictIn, String configFile) {
    this.subtitlesFilePaths = subtitlesFilePaths;
    this.jmdictIn = jmdictIn;
    this.configFile = configFile;
  }

  private JapaneseParser langParser = null;
  private JmDict dict = null;
  private HashSet<String> ignoreWordsSet = null;  // user list of words to ignore during annotation
  private SubtitleFile subtitleFile = null;


  // Initialization => load dictionary & parser data
  // This is a quite heavy operation so it should be launched from background thread
  private void doInitialization() {
    System.out.print("Initializing parser and dictionary...");

    // Load configuration
    YamlConfig config = new YamlConfig(configFile);
    System.out.print(".");

    // Load user list of words to ignore
    for (String w : config.getIgnoreWords()) {
      if (w.trim().length() > 0) {
        ignoreWordsSet.add(w.trim());
      }
    }

    // Initialize dictionary
    dict = new JmDict(jmdictIn);
    dict.outDict();
    System.out.print(".");

    // Initialize parser
    langParser = new JapaneseParser(config);
    System.out.print(".");

    // Read subtitles styles from config
    subtitleFile = new SubtitleFile(config);

    System.out.println("OK");
  }

  // Return true if file was annotated, false otherwise
  private boolean annotateSubtitleFile(File f) throws IOException, FatalParsingException {

    if (!FilenameUtils.getExtension(f.getName()).equals("srt")) {
      LOGGER.error("invalid SRT file: {}", f.getName());
      throw new UnexpectedError();
    }

    subtitleFile.readFromSrt(f);

    // Loop through the subtitle file captions one by one
    int nbAnnotations = 0;
    while (subtitleFile.hasNextCaption()) {

      String currentCaptionText = subtitleFile.nextCaption();
      subtitleFile.setCaptionStyle();

      // Parse subtitle and lookup definitions
      String allAnnotations = "";
      //String parse = "";
      for (TextToken token : langParser.syntaxicParse(currentCaptionText)) {

        //parse += token.currentForm + "|";
        // Ignore user words list
        if (ignoreWordsSet.contains(token.currentForm) || ignoreWordsSet.contains(token.canonicalForm)) {
          continue;
        }

        // Ignore unimportant grammatical words
        if (POS_TAGS_TO_IGNORE.contains(token.tag)) {
          continue;
        }

        // Ignore words that are not in dictionary
        String def = dict.getMeaning(token.canonicalForm);
        if (def.isEmpty()) {
          def = dict.getMeaning(token.currentForm);
        }
        if (def.isEmpty()) {
          continue;
        }

        // If all is good, add definition to subtitle annotation
        allAnnotations += "★ " + def + "\\N\\n";
        nbAnnotations++;

        // Set a different color for words that are defined
        subtitleFile.colorizeCaptionWord(token.currentForm, "#AAAAFF");
      }

      //System.out.println(parse);
      //System.out.println(dict.getParse(currentCaptionText)+"\n");
      subtitleFile.addAnnotationCaption(SubStyle.Definition, allAnnotations);
    }

    if (nbAnnotations == 0) {
      return false;
    }
    subtitleFile.writeToAss(f.getParent() + "/" + FilenameUtils.getBaseName(f.getName()) + ".ass");
    return true;
  }

  @Override
  public Void doInBackground() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new Exception("SubtitlesTranslator should not run on the EDT thread!");
    }

    if (langParser == null) {
      doInitialization();
    }

    for (String filePath : subtitlesFilePaths) {
      File fileEntry = new File(filePath);
      try {
        System.out.print("Annotate " + fileEntry.getName() + "...");
        boolean annotated = annotateSubtitleFile(fileEntry);
        System.out.println(annotated ? "OK" : "PASS");
      } catch (Exception e) {
        // on error print stack trace and continue to next file
        System.out.println("ERROR");
        e.printStackTrace();
        System.out.println();
      }
    }
    return null;
  }
}

