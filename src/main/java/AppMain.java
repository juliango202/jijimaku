
/*
 *  Subtitles Dictionary
 */

import error.SubsDictError;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.SwingWorker.StateValue;

import error.UnexpectedError;
import models.SubtitlesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import workers.WorkerSubAnnotator;
import workers.WorkerSubFinder;


class AppMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static class AppConst {
        public static final String APP_TITLE = "Subtitles Dictionary";

        public static final String APP_DESC =   "This program reads subtitle files(format SRT) in the current directory tree and add the " +
                "dictionary definitions for the words encountered. "+
                "The result is a subtitle file(format ASS) that can be used for language learning: subtitles appears at the bottom "+
                "and words definitions at the top.\n"+
                "(Currently, only Japanese => English is supported.)\n\n" +
                "See config.yaml for options.";

        //-----------------------------------------------------
        static final String CONFIG_FILE = "config.yaml";
        static final String JMDICT_FILE = "/home/julian/Prog/Japanese/JpSubsDict/sampledata/resources/JMdict_e"; // => you can use data/JMDict_small to debug


        static final String OUTPUT_SUB_EXT = "ass";

        // For now only SRT subtitles are supported
        // but it should be fairly easy to add other formats when needed
        static final String[] VALID_SUBFILE_EXT = {"srt"};
    }

    private AppGUI gui;
    private List<String> diskSubtitles;
    private File searchDirectory;


    public AppMain() {
        // Initialize GUI
        gui = new AppGUI(AppConst.APP_TITLE,this);
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
      SwingUtilities.invokeLater(() -> new AppMain());
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
                throw (SubsDictError)originalExc;
            }
            LOGGER.error("{} task returned an error:", taskLabel, originalExc);
            throw new UnexpectedError();
        }
    }

    public void setSearchDirectory(File searchDirectory) {
        LOGGER.debug("Searching in directory: {}", searchDirectory.getName());
        this.searchDirectory = searchDirectory;
        setState(AppMain.AppState.SEARCHING_SUBTITLES);
    }

    // Use a simple state driven behavour
    enum AppState {
        READY,
        SEARCHING_SUBTITLES,
        ANNOTATING_SUBTITLES
    }

    public void setState(AppState state) {
        LOGGER.debug("Set app state to {}", state.toString());

        switch (state) {
            case READY:
                gui.setReadyState(true);
                System.out.format("\n➟ Click the 'Find subtitles' button to find and process subtitle files(*.%s)\n", String.join(", *.", AppConst.VALID_SUBFILE_EXT));
                break;
            case SEARCHING_SUBTITLES:
                gui.setReadyState(false);
                LOGGER.info("-- Searching for subtitles in {} -------------------------------", searchDirectory.getAbsolutePath());
                WorkerSubFinder finder = new WorkerSubFinder(searchDirectory, AppConst.VALID_SUBFILE_EXT);
                finder.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
                        SubtitlesCollection coll = (SubtitlesCollection) getTaskResult(finder, "");
                        diskSubtitles = coll.canBeAnnotated;
                        if (!diskSubtitles.isEmpty()) {
                            setState(AppState.ANNOTATING_SUBTITLES);
                        } else {
                            setState(AppState.READY);
                        }
                    }
                });
                finder.execute();
                break;
            case ANNOTATING_SUBTITLES:
                LOGGER.info("-- Annotating subtitles -------------------------------");
                WorkerSubAnnotator translater = new WorkerSubAnnotator(diskSubtitles, AppMain.class.getResourceAsStream(AppConst.JMDICT_FILE), AppConst.CONFIG_FILE);
                translater.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
                        getTaskResult(translater, "Subtitle annotation");
                    }
                });
                translater.execute();
                break;

            default:
                break;
        }
    }
}
