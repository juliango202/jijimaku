package jijimaku;
/*
 *  Subtitles Dictionary
 */

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jijimaku.error.SubsDictError;
import jijimaku.error.UnexpectedError;
import jijimaku.models.SubtitlesCollection;
import jijimaku.workers.WorkerSubAnnotator;
import jijimaku.workers.WorkerSubFinder;


class AppMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private AppGui gui;
  private List<String> diskSubtitles;
  private File searchDirectory;

  private AppMain() {
    // Initialize GUI
    gui = new AppGui(AppConst.APP_TITLE, this);
    System.out.println(AppConst.APP_DESC);
    setState(AppState.READY);

    // Global exception handler
    Thread.setDefaultUncaughtExceptionHandler((thr, exc) -> {
      if (exc instanceof SubsDictError) {
        if (exc.getMessage() != null && !exc.getMessage().isEmpty()) {
          LOGGER.error(exc.getMessage());
        }
      } else {
        LOGGER.error("Got an unexpected error", exc);
      }
      setState(AppState.READY);
    });
  }

  public static void main(String[] args) {
    // Create GUI in the EDT
    SwingUtilities.invokeLater(AppMain::new);
  }

  private Object getTaskResult(SwingWorker taskWorker, String taskLabel) {
    try {
      return taskWorker.get();
    } catch (InterruptedException e) {
      LOGGER.error("{} task was interrupted.", taskLabel);
      return null;
    } catch (ExecutionException e) {
      Throwable originalExc = e.getCause();
      if (originalExc instanceof SubsDictError) {
        // Propagate our exceptions to the main error handler
        throw (SubsDictError) originalExc;
      }
      LOGGER.error("{} task returned an error:", taskLabel, originalExc);
      throw new UnexpectedError();
    }
  }

  void setSearchDirectory(File searchDirectory) {
    LOGGER.debug("Searching in directory: {}", searchDirectory.getName());
    this.searchDirectory = searchDirectory;
    setState(AppMain.AppState.SEARCHING_SUBTITLES);
  }

  private void setState(AppState state) {
    LOGGER.debug("Set app state to {}", state.toString());

    switch (state) {
      case READY:
        gui.setReadyState(true);
        String supportedExtensions = String.join(", *.", Arrays.asList(AppConst.VALID_SUBFILE_EXT));
        System.out.format("\n➟ Click the 'Find subtitles' button to find and process subtitle files(*.%s)\n", supportedExtensions);
        break;
      case SEARCHING_SUBTITLES:
        gui.setReadyState(false);
        LOGGER.info("-- Searching for subtitles in {} -------------------------------", searchDirectory.getAbsolutePath());
        WorkerSubFinder finder = new WorkerSubFinder(searchDirectory, AppConst.VALID_SUBFILE_EXT);
        finder.addPropertyChangeListener(evt -> {
          if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
            SubtitlesCollection coll = (SubtitlesCollection) getTaskResult(finder, "");
            if (coll == null || coll.canBeAnnotated.isEmpty()) {
              setState(AppState.READY);
            } else {
              diskSubtitles = coll.canBeAnnotated;
              setState(AppState.ANNOTATING_SUBTITLES);
            }
          }
        });
        finder.execute();
        break;
      case ANNOTATING_SUBTITLES:
        LOGGER.info("-- Annotating subtitles -------------------------------");
        WorkerSubAnnotator translater = new WorkerSubAnnotator(diskSubtitles, AppConst.JMDICT_FILE, AppConst.CONFIG_FILE);
        translater.addPropertyChangeListener(evt -> {
          if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
            Integer nbAnnotated = (Integer) getTaskResult(translater, "Subtitle annotation");
            LOGGER.info("{} subtitle files were annotated.", nbAnnotated);
            setState(AppState.READY);
          }
        });
        translater.execute();
        break;

      default:
        break;
    }
  }

  // Use a simple state driven behavour
  private enum AppState {
    READY,
    SEARCHING_SUBTITLES,
    ANNOTATING_SUBTITLES
  }

  private static class AppConst {
    static final String APP_TITLE = "Jijimaku Subtitles Dictionary";

    static final String APP_DESC = "This program reads subtitle files in the chosen directory tree and add the "
        + "dictionary definitions for the words encountered. \n\n"
        + "The result is a subtitle file(format ASS) that can be used for language learning: subtitles appears at the bottom "
        + "and words definitions at the top.\n\n"
        + "See config.yaml for options.";

    //-----------------------------------------------------
    static final String CONFIG_FILE = "config.yaml";
    static final String JMDICT_FILE = "jiji.jmdict.yaml"; // => use data/JMDict_small to debug

    //static final String OUTPUT_SUB_EXT = "ass";

    // For now only SRT subtitles are supported
    // but it should be fairly easy to add other formats when needed
    static final String[] VALID_SUBFILE_EXT = {"srt","ass"};
  }
}
