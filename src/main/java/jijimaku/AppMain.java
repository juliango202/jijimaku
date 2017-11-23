package jijimaku;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker.StateValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.SubsDictError;
import jijimaku.errors.UnexpectedError;
import jijimaku.models.ServicesParam;
import jijimaku.utils.FileManager;

/**
 * Launch Jijimaku and handle application states.
 */
class AppMain {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

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
    // TODO: check and simplify exception flow
    Thread.setDefaultUncaughtExceptionHandler((thr, exc) -> {
      if (exc instanceof SubsDictError) {
        if (exc.getMessage() != null && !exc.getMessage().isEmpty()) {
          LOGGER.error(exc.getMessage());
        }
      } else {
        LOGGER.error("Got an unexpected error", exc);
      }
      setState(AppState.WAIT_FOR_DIRECTORY_CHOICE);
    });

    // Initialize GUI
    gui = new AppGui(AppConst.APP_TITLE, this);
    System.out.println(AppConst.APP_DESC);

    launchInitializationWorker();
    setState(AppState.WAIT_FOR_INITIALIZATION);
  }

  private void launchInitializationWorker() {
    WorkerInitialization initializer = new WorkerInitialization(AppConst.CONFIG_FILE);
    initializer.addPropertyChangeListener(evt -> {
      if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
        try {
          services = initializer.get();
          initialized = true;
          setState(searchDirectory != null ? AppState.ANNOTATE_SUBTITLES : AppState.WAIT_FOR_DIRECTORY_CHOICE);
        } catch (InterruptedException exc) {
          LOGGER.warn("Initialization worker was interrupted.");
        } catch (ExecutionException exc) {
          LOGGER.debug("Got exception:", exc);
          LOGGER.error("Initialization worker returned an error. Check the logs.");
          System.exit(1);
        }
      }
    });
    initializer.execute();
  }

  private void launchAnnotationTask() {
    WorkerSubAnnotator annotator = new WorkerSubAnnotator(searchDirectory, services);
    annotator.addPropertyChangeListener(evt -> {
      if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
        try {
          annotator.get();
          setState(AppState.WAIT_FOR_DIRECTORY_CHOICE);
        } catch (InterruptedException exc) {
          LOGGER.warn("Subtitle annotation task was interrupted.");
        } catch (ExecutionException exc) {
          Throwable originalExc = exc.getCause();
          if (originalExc instanceof SubsDictError) {
            // Propagate our exceptions to the main error handler
            throw (SubsDictError) originalExc;
          }
          LOGGER.debug("Got exception:", originalExc);
          LOGGER.error("Subtitle annotation task returned an error. Check the logs.");
          throw new UnexpectedError();
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

      case WAIT_FOR_DIRECTORY_CHOICE:
        gui.toggleDirectorySelector(true);
        String supportedExtensions = String.join(", *.", Arrays.asList(AppConst.VALID_SUBFILE_EXT));
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
