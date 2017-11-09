package jijimaku;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import jijimaku.jijidictionary.JijiDictionaryEntry;
import jijimaku.models.SubtitlesCollection;
import org.apache.commons.io.FileUtils;
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

import static jijimaku.AppConst.VALID_SUBFILE_EXT;


// Background task that annotates a subtitle file with words definition
public class WorkerSubAnnotator extends SwingWorker<Integer, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private File searchDirectory;
  private YamlConfig config;

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
   * Swing worker to search and annotate the subtitle files.
   * @param searchDirectory disk directory where to search subtitles(recursive)
   */
  public WorkerSubAnnotator(File searchDirectory) {
    if (searchDirectory == null || !searchDirectory.isDirectory()) {
      LOGGER.error("Invalid search directory {}", String.valueOf(searchDirectory));
      throw new UnexpectedError();
    }
    this.searchDirectory = searchDirectory;
  }

  private JapaneseParser langParser = null;
  private JijiDictionary dict = null;
  private HashSet<String> ignoreWordsSet = null;  // user list of words to ignore during annotation
  private SubtitleFile subtitleFile = null;


  // Initialization => load dictionary & parser data
  // This is a quite heavy operation so it should be launched from background thread
  private void doInitialization() {
    LOGGER.info("-------------------------- Initialization --------------------------");
    // Load configuration
    LOGGER.info("Loading configuration...");
    this.config = new YamlConfig(AppConst.CONFIG_FILE);
    this.ignoreWordsSet = config.getIgnoreWords();

    // Initialize dictionary
    LOGGER.info("Loading dictionnary...");
    dict = new JijiDictionary(AppConst.JMDICT_FILE);

    // Initialize parser
    LOGGER.info("Instantiate parser...");
    langParser = new JapaneseParser(config);

    // Read subtitles styles from config
    subtitleFile = new SubtitleFile(config);

    LOGGER.info("Ready to work!");
  }

  // Return true if file was annotated, false otherwise
  private boolean annotateSubtitleFile(File f) throws IOException, FatalParsingException {

    boolean displayOtherLemma = config.getDisplayOtherLemma();

    subtitleFile.readFromSrt(f);

    // Loop through the subtitle file captions one by one
    int nbAnnotations = 0;
    while (subtitleFile.hasNextCaption()) {

      List<String> colors = new ArrayList<>(config.getColors().values());
      String currentCaptionText = subtitleFile.nextCaption();
      subtitleFile.setCaptionStyle();

      // Parse subtitle and lookup definitions
      List<String> annotations = new ArrayList<>();
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
        String color = colors.iterator().next();
        Collections.rotate(colors, -1);
        for(JijiDictionaryEntry def : defs) {
          // Each definition is made of several lemmas and several senses
          // Depending on "displayOtherLemma" option, display only the lemma corresponding to the subtitle word, or all lemmas
          String lemmas = def.getLemmas().stream().map(l -> {
            if (l.equals(token.getCanonicalForm()) || l.equals(token.getTextForm())) {
              return SubtitleFile.assColorize(l, color);
            } else if(displayOtherLemma) {
              return l;
            } else {
              return null;
            }
          }).filter(Objects::nonNull).collect(Collectors.joining(", "));
          // We don't know which sense corresponds to the subtitle so we can't do the same unfortunately ^^
          // Just concat all senses
          List<String> senses = def.getSenses();

          annotations.add("★ " + lemmas + " " + String.join(" --- ", senses));
        }

        // Set a different color for words that are defined
        subtitleFile.colorizeCaptionWord(token.getTextForm(), color);
      }

      if ( annotations.size() > 0) {
        nbAnnotations += annotations.size();
        subtitleFile.addAnnotationCaption(SubStyle.Definition, String.join("\\N", annotations));
      }
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
      throw new Exception("Worker should not run on the EDT thread!");
    }

    if (langParser == null) {
      doInitialization();
    }

    LOGGER.info("-------------------------- Searching for subtitles in {} --------------------------", searchDirectory.getAbsolutePath());
    SubtitlesCollection coll = new SubtitlesCollection();
    for (File fileEntry : FileUtils.listFiles(searchDirectory, VALID_SUBFILE_EXT, true)) {
      if (!fileEntry.isHidden() && !SubtitleFile.isSubDictFile(fileEntry)) {
        coll.canBeAnnotated.add(fileEntry.getAbsolutePath());
        LOGGER.info("Found " + fileEntry.getName());
      }
      if (isCancelled()) {
        LOGGER.debug("WorkerSubAnnotator was cancelled.");
        return 0;
      }
    }

    if (coll.isEmpty()) {
      LOGGER.info("No subtitle found in this directory.");
      return 0;
    }

    LOGGER.info("-------------------------- Annotating subtitles --------------------------");
    Integer nbAnnotated = 0;
    for (String filePath : coll.canBeAnnotated) {
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
      if (isCancelled()) {
        LOGGER.debug("WorkerSubAnnotator was cancelled.");
        return 0;
      }
    }
    return nbAnnotated;
  }

}

