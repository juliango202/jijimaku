package jijimaku.workers;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import jijimaku.jijidictionary.JijiDictionaryEntry;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jijimaku.error.UnexpectedError;
import jijimaku.jijidictionary.JijiDictionary;
import jijimaku.langparser.JapaneseParser;
import jijimaku.langparser.LangParser.PosTag;
import jijimaku.langparser.LangParser.TextToken;
import jijimaku.utils.SubtitleFile;
import jijimaku.utils.SubtitleFile.SubStyle;
import jijimaku.utils.YamlConfig;

import subtitleFile.FatalParsingException;


// Background task that annotates a subtitle file with words definition
public class WorkerSubAnnotator extends SwingWorker<Integer, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private List<String> subtitlesFilePaths;
  private String jmdictFile;
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
   * @param jmdictFile the dictionnary file
   * @param configFile the config file
   */
  public WorkerSubAnnotator(List<String> subtitlesFilePaths, String jmdictFile, String configFile) {
    this.subtitlesFilePaths = subtitlesFilePaths;
    this.jmdictFile = jmdictFile;
    this.configFile = configFile;
  }

  private JapaneseParser langParser = null;
  private JijiDictionary dict = null;
  private HashSet<String> ignoreWordsSet = null;  // user list of words to ignore during annotation
  private SubtitleFile subtitleFile = null;


  // Initialization => load dictionary & parser data
  // This is a quite heavy operation so it should be launched from background thread
  private void doInitialization() {
    // Load configuration
    LOGGER.info("Loading configuration...");
    YamlConfig config = new YamlConfig(configFile);
    this.ignoreWordsSet = config.getIgnoreWords();

    // Initialize dictionary
    LOGGER.info("Loading dictionnary...");
    dict = new JijiDictionary(jmdictFile);

    // Initialize parser
    LOGGER.info("Instantiate parser...");
    langParser = new JapaneseParser(config);

    // Read subtitles styles from config
    subtitleFile = new SubtitleFile(config);

    LOGGER.info("Ready to work!");
  }

  // Return true if file was annotated, false otherwise
  private boolean annotateSubtitleFile(File f) throws IOException, FatalParsingException {

    if (!FilenameUtils.getExtension(f.getName()).equals("srt") && !FilenameUtils.getExtension(f.getName()).equals("ass")) {
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

        // Ignore unimportant grammatical words
        if (POS_TAGS_TO_IGNORE.contains(token.getPartOfSpeech())) {
          continue;
        }

        // Ignore user words list
        if (ignoreWordsSet.contains(token.getTextForm()) || ignoreWordsSet.contains(token.getCanonicalForm())) {
          continue;
        }

        // Ignore words that are not in dictionary
        List<JijiDictionaryEntry> defs = dict.getMeaning(token.getCanonicalForm());
        if (defs.isEmpty()) {
          defs = dict.getMeaning(token.getTextForm());
        }
        if (defs.isEmpty()) {
          continue;
        }

        // If all is good, add definitions to subtitle annotation
        for(JijiDictionaryEntry def : defs) {
          List<String> senses = def.getSenses();
          allAnnotations += "★ " + String.join(", ", def.getLemmas()) + " " + String.join(" --- ", senses) + "\\N";
        }
        nbAnnotations++;

        // Set a different color for words that are defined
        subtitleFile.colorizeCaptionWord(token.getTextForm(), "#AAAAFF");
      }

      //LOGGER.info(parse);
      //LOGGER.info(dict.getParse(currentCaptionText)+"\n");
      subtitleFile.addAnnotationCaption(SubStyle.Definition, allAnnotations);
    }

    if (nbAnnotations == 0) {
      return false;
    }
    subtitleFile.writeToAss(f.getParent() + "/" + FilenameUtils.getBaseName(f.getName()) + ".ass");
    return true;
  }

  @Override
  public Integer doInBackground() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new Exception("SubtitlesTranslator should not run on the EDT thread!");
    }

    if (langParser == null) {
      doInitialization();
    }

    Integer nbAnnotated = 0;
    for (String filePath : subtitlesFilePaths) {
      File fileEntry = new File(filePath);
      try {
        LOGGER.info("Annotate " + fileEntry.getName() + "...");
        boolean annotated = annotateSubtitleFile(fileEntry);
        if (annotated) {
          nbAnnotated++;
        } else {
          LOGGER.info("Nothing to annotate was found in this file(wrong language?)");
        }
      } catch (Exception exc) {
        LOGGER.error("Error while trying to annotate {}. See log for details. Skip file.", filePath);
        LOGGER.debug("Got exception", exc);
      }
    }
    return nbAnnotated;
  }
}

