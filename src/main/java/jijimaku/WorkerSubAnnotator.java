package jijimaku;

import static jijimaku.AppConst.VALID_SUBFILE_EXT;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import jijimaku.models.DictionaryMatch;
import jijimaku.services.langparser.LangParser.TextToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jijimaku.errors.UnexpectedError;
import jijimaku.services.Config;
import jijimaku.services.SubtitleService;
import jijimaku.services.SubtitleService.SubStyle;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.jijidictionary.JijiDictionaryEntry;
import jijimaku.services.langparser.JapaneseParser;
import jijimaku.services.langparser.LangParser.PosTag;

import subtitleFile.FatalParsingException;



// Background task that annotates a subtitle file with words definition
public class WorkerSubAnnotator extends SwingWorker<Void, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private File searchDirectory;
  private Config config;

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
  private Set<String> ignoreWordsSet = null;  // user list of words to ignore during annotation
  private SubtitleService subtitleService = null;


  // Initialization => load dictionary & parser data
  // This is a quite heavy operation so it should be launched from background thread
  private void doInitialization() {
    LOGGER.info("-------------------------- Initialization --------------------------");
    // Load configuration
    LOGGER.info("Loading configuration...");
    this.config = new Config(AppConst.CONFIG_FILE);
    this.ignoreWordsSet = config.getIgnoreWords();

    // Initialize dictionary
    LOGGER.info("Loading dictionnary...");
    dict = new JijiDictionary(config.getJijiDictionary());

    // Initialize parser
    LOGGER.info("Instantiate parser...");
    langParser = new JapaneseParser(config);

    // Read subtitles styles from config
    subtitleService = new SubtitleService(config);

    LOGGER.info("Ready to work!");
  }

  /**
   * @return a DictionaryMatch entry if the provided tokens match a definition, null otherwise.
   */
  private DictionaryMatch dictionaryMatch(List<TextToken> tokens) {
    if (tokens.isEmpty()) {
      return null;
    }

    String canonicalForm = tokens.stream().map(TextToken::getCanonicalForm).collect(Collectors.joining(""));
    List<JijiDictionaryEntry> entries = dict.getEntriesForWord(canonicalForm);
    if (entries.isEmpty()) {
      String textForm = tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining(""));
      entries = dict.getEntriesForWord(textForm);
    }

    if (entries.isEmpty()) {
      return null;
    } else {
      return new DictionaryMatch(tokens, entries);
    }
  }

  /**
   * Return all the dictionary matches for one caption.
   * For example the parsed sentence => I|think|he|made|it|up should likely return four
   * DictionaryMatches => I|to think|he|to make it up
   */
  private List<DictionaryMatch> getDictionaryMatches(String caption) {
    // A syntaxic parse of the caption returns a list of tokens.
    List<TextToken> captionTokens = langParser.syntaxicParse(caption);

    // Next we must group tokens together if they is a corresponding definition in the dictionary.
    List<DictionaryMatch> matches = new ArrayList<>();
    while (!captionTokens.isEmpty()) {
      // Find the next DictionaryMatch
      // Start with all tokens and remove one by one until we have a match
      List<TextToken> maximumTokens = new ArrayList<>(captionTokens);
      DictionaryMatch match = dictionaryMatch(maximumTokens);
      while (match == null && maximumTokens.size() > 0) {
        maximumTokens = maximumTokens.subList(0, maximumTokens.size() - 1);
        match = dictionaryMatch(maximumTokens);
      }

      if (match == null) {
        // We could not find a match for current token, just remove it
        captionTokens = captionTokens.subList(1, captionTokens.size());
      } else {
        matches.add(match);
        captionTokens = captionTokens.subList(match.getTokens().size(), captionTokens.size());
      }
    }
    return matches;
  }

  /**
   * Filter the DictionaryMatches to display depending on user preferences.
   */
  private List<DictionaryMatch> getFilteredMatches(String caption) {
    List<DictionaryMatch> allMatches = getDictionaryMatches(caption);
    return allMatches.stream().filter(dm -> {

      // Ignore user words list
      if (ignoreWordsSet.contains(dm.getTextForm()) || ignoreWordsSet.contains(dm.getCanonicalForm())) {
        return false;
      }

      // Ignore unimportant grammatical words
      if (dm.getTokens().stream().allMatch(t -> POS_TAGS_TO_IGNORE.contains(t.getPartOfSpeech()))) {
        return false;
      }

      // Filter using ignoreFrequency option
      if (dm.getDictionaryEntries().stream().allMatch(de -> config.getIgnoreFrequencies().contains(de.getFrequency()))) {
        return false;
      }

      return true;
    }).collect(Collectors.toList());
  }


  // Return true if file was annotated, false otherwise
  private boolean annotateSubtitleFile(File f) throws IOException, FatalParsingException {

    Boolean displayOtherLemma = config.getDisplayOtherLemma();

    subtitleService.readFile(f);

    // Loop through the subtitle file captions one by one
    int nbAnnotations = 0;
    while (subtitleService.hasNextCaption()) {

      List<String> colors = new ArrayList<>(config.getColors().values());
      String currentCaptionText = subtitleService.nextCaption();
      subtitleService.setCaptionStyle();

      // Parse subtitle and lookup definitions
      List<String> annotations = new ArrayList<>();
      for (DictionaryMatch  match : getFilteredMatches(currentCaptionText)) {
        String color = colors.iterator().next();

        List<String> tokenDefs = new ArrayList<>();
        for (JijiDictionaryEntry def : match.getDictionaryEntries()) {
          // Each definition is made of several lemmas and several senses
          // Depending on "displayOtherLemma" option, display only the lemma corresponding to the subtitle word, or all lemmas
          String lemmas = def.getLemmas().stream().map(l -> {
            if (l.equals(match.getCanonicalForm()) || l.equals(match.getTextForm())) {
              return SubtitleService.addStyleToText(l, SubtitleService.TextStyle.COLOR, color);
            } else if (displayOtherLemma) {
              return l;
            } else {
              return null;
            }
          }).filter(Objects::nonNull).collect(Collectors.joining(", "));
          // We don't know which sense corresponds to the subtitle so we can't do the same unfortunately ^^
          // => just concat all senses
          List<String> senses = def.getSenses();
          // Represent language level with unicode characters ①, ②, ③, ④, ...
          String langLevelStr = " ";
          if (def.getFrequency() != null) {
            String langLevelChar = Character.toString((char)(9312 + def.getFrequency()));
            langLevelStr = " " + SubtitleService.addStyleToText(langLevelChar, SubtitleService.TextStyle.BOLD) + " ";
          }

          String pronounciationStr = "";
          if (def.getPronounciation() != null) {
            pronounciationStr = " [" + String.join(", ", def.getPronounciation()) + "] ";
          }

          tokenDefs.add("★ " + lemmas + pronounciationStr + langLevelStr + String.join(" --- ", senses));
        }

        if (!tokenDefs.isEmpty()) {
          annotations.addAll(tokenDefs);
          // Set a different color for words that are defined
          match.getTokens().forEach(t -> {
            subtitleService.colorizeCaptionWord(t.getTextForm(), color);
          });
          Collections.rotate(colors, -1);
        }
      }

      if (annotations.size() > 0) {
        nbAnnotations += annotations.size();
        subtitleService.addAnnotationCaption(SubStyle.Definition, String.join("\\N", annotations));
      }
    }

    if (nbAnnotations == 0) {
      return false;
    }
    subtitleService.writeToAss(f.getParent() + "/" + FilenameUtils.getBaseName(f.getName()) + ".ass");
    return true;
  }

  @Override
  public Void doInBackground() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new Exception("Worker should not run on the EDT thread!");
    }

    if (langParser == null) {
      doInitialization();
    }

    LOGGER.info("------------------- Searching in {} -------------------", searchDirectory.getAbsolutePath());
    Integer nbAnnotated = 0;
    for (File fileEntry : FileUtils.listFiles(searchDirectory, VALID_SUBFILE_EXT, true)) {
      try {
        if (fileEntry.isHidden() || SubtitleService.isSubDictFile(fileEntry)) {
          LOGGER.debug("{} is one of our annotated subtitle, skip it.", fileEntry.getName());
          continue;
        }
        LOGGER.info("Processing " + fileEntry.getName() + "...");
        if (annotateSubtitleFile(fileEntry)) {
          nbAnnotated++;
        } else {
          LOGGER.info("Nothing to annotate was found in this file(wrong language?)");
        }
      } catch (Exception exc) {
        LOGGER.error("Error while trying to annotate {}. See log for details. Skip file.", fileEntry.getName());
        LOGGER.debug("Got exception", exc);
      }

      if (isCancelled()) {
        LOGGER.debug("WorkerSubAnnotator was cancelled.");
        return null;
      }
    }

    if (nbAnnotated > 0) {
      LOGGER.info("{} subtitle files were annotated.", nbAnnotated);
    } else {
      LOGGER.info("No subtitle found in this directory.");
    }
    return null;
  }

}

