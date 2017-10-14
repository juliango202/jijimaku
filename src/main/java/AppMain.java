
/*
 *  Subtitles Dictionary
 */

import error.SubsDictError;
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


    // STATES MANAGEMENT
    // ========================================================================
    // Use a simple state machine to drive behaviour
    enum AppState {
        READY,
        SEARCHING_SUBTITLES,
        ANNOTATING_SUBTITLES
    }

    private Object runTask(SwingWorker taskWorker, String taskLabel) {
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

    public void setState(AppState state) {
        LOGGER.debug("Set app state to {}", state.toString());

        switch (state) {
            case READY:
                gui.setReadyState(true);
                System.out.format("\n➟ Click the 'Search' button to start searching all subtitles in the current directory (%s)\n",System.getProperty("user.dir"));
                break;
            case SEARCHING_SUBTITLES:
                gui.setReadyState(false);
                System.out.println("\n-- Searching for subtitles -------------------------------");
                WorkerSubFinder finder = new WorkerSubFinder(AppConst.VALID_SUBFILE_EXT);
                finder.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
                        SubtitlesCollection coll = (SubtitlesCollection)runTask(finder, "");
                        diskSubtitles = coll.canBeAnnotated;
                        setState(AppState.ANNOTATING_SUBTITLES);
                    }
                });
                finder.execute();
                break;
            case ANNOTATING_SUBTITLES:
                System.out.println("\n-- Annotating subtitles -------------------------------");
                WorkerSubAnnotator translater = new WorkerSubAnnotator(diskSubtitles, AppMain.class.getResourceAsStream(AppConst.JMDICT_FILE), AppConst.CONFIG_FILE);
                translater.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
                        runTask(translater, "Subtitle annotation");
                    }
                });
                translater.execute();
                break;

            default:
                break;
        }
    }
}
