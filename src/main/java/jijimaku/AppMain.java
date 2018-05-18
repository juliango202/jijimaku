package jijimaku;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker.StateValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.JijimakuError;
import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.models.ServicesParam;
import jijimaku.utils.FileManager;
import jijimaku.workers.WorkerAnnotate;
import jijimaku.workers.WorkerInitialize;

/**
 * Launch Jijimaku and handle application states.
 */
class AppMain {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String APP_TITLE = "Jijimaku Subtitles Dictionary";

  private static final String APP_DESC = "This program reads subtitle files in the chosen directory tree and add the "
          + "dictionary definitions for the words encountered. \n\n"
          + "The result is a subtitle file(format ASS) that can be used for language learning: subtitles appears at the bottom "
          + "and words definitions at the top.\n\n"
          + "See config.yaml for options.\n";

  private static final String CONFIG_FILE = "config.yaml";

  private static final String[] VALID_SUBFILE_EXT = {"srt","ass"};


  private AppGui gui;
  private ServicesParam services;

  private File searchDirectory = null;
  private boolean initialized = false;

  public static void main(String[] args) {
    // Create GUI in the EDT
    SwingUtilities.invokeLater(AppMain::new);
  }

  private AppMain() {
    // Global exception handler
    Thread.setDefaultUncaughtExceptionHandler((thr, exc) -> {
      if (exc instanceof JijimakuError) {
        if (exc.getMessage() != null && !exc.getMessage().isEmpty()) {
          LOGGER.error(exc.getMessage());
        }
        if (exc instanceof UnexpectedCriticalError) {
          setState(AppState.CRITICAL_ERROR);
          return;
        }
      } else {
        LOGGER.debug(exc);
        LOGGER.error("Got an unexpected error. Check the logs.");
      }
      setState(AppState.WAIT_FOR_DIRECTORY_CHOICE);
    });

    // Initialize GUI
    gui = new AppGui(APP_TITLE, this);
    System.out.println(APP_DESC);

    LOGGER.debug("library path: " + System.getProperty("java.library.path"));

    launchInitializationWorker();
    setState(AppState.WAIT_FOR_INITIALIZATION);
  }

  private void handleWorkerException(Exception exc) {
    if (exc instanceof InterruptedException) {
      LOGGER.debug(exc);
      LOGGER.warn("Worker thread was interrupted.");
      Thread.currentThread().interrupt();
    } else if (exc instanceof ExecutionException) {
      Throwable originalExc = exc.getCause();
      if (originalExc instanceof JijimakuError) {
        // Propagate our exceptions to the main error handler
        throw (JijimakuError) originalExc;
      }
      LOGGER.debug(originalExc);
      LOGGER.error("Worker thread returned an error. Check the logs.");
      throw new UnexpectedCriticalError();
    }
  }

  private void launchInitializationWorker() {
    WorkerInitialize initializer = new WorkerInitialize(CONFIG_FILE);
    initializer.addPropertyChangeListener(evt -> {
      if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
        try {
          services = initializer.get();
          initialized = true;
          setState(searchDirectory != null ? AppState.ANNOTATE_SUBTITLES : AppState.WAIT_FOR_DIRECTORY_CHOICE);
        } catch (InterruptedException  | ExecutionException exc) {
          handleWorkerException(exc);
        }
      }
    });
    initializer.execute();
  }

  private void launchAnnotationTask() {
    WorkerAnnotate annotator = new WorkerAnnotate(searchDirectory, VALID_SUBFILE_EXT, services);
    annotator.addPropertyChangeListener(evt -> {
      if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
        try {
          annotator.get();
          setState(AppState.WAIT_FOR_DIRECTORY_CHOICE);
        } catch (InterruptedException  | ExecutionException exc) {
          handleWorkerException(exc);
        }
      }
    });
    searchDirectory = null;
    annotator.execute();
  }

  void setSearchDirectory(File searchDirectory) {
    this.searchDirectory = searchDirectory;
    setState(initialized ? AppState.ANNOTATE_SUBTITLES : AppState.WAIT_FOR_INITIALIZATION);
  }

  /**
   * Use a simple state driven behaviour.
   */
  private enum AppState {
    CRITICAL_ERROR,
    WAIT_FOR_INITIALIZATION,
    WAIT_FOR_DIRECTORY_CHOICE,
    ANNOTATE_SUBTITLES
  }

  /**
   * Set a new AppState.
   */
  private void setState(AppState state) {
    LOGGER.debug("Set app state to {}", state.toString());

    switch (state) {
      case WAIT_FOR_INITIALIZATION:
        // Nothing to do but wait
        break;

      case CRITICAL_ERROR:
        gui.toggleDirectorySelector(false);
        break;

      case WAIT_FOR_DIRECTORY_CHOICE:
        gui.toggleDirectorySelector(true);
        String supportedExtensions = String.join(", *.", Arrays.asList(VALID_SUBFILE_EXT));
        System.out.format("\n➟ Click the 'Find subtitles' button to find and process subtitle files(*.%s)\n", supportedExtensions);
        break;

      case ANNOTATE_SUBTITLES:
        gui.toggleDirectorySelector(false);
        launchAnnotationTask();
        break;

      default:
        break;
    }
  }
}
