package jijimaku;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker.StateValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jijimaku.errors.SubsDictError;
import jijimaku.errors.UnexpectedError;


/**
 * Launch Jijimaku and handle application states.
 */
class AppMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private AppGui gui;
  private File searchDirectory;

  private AppMain() {
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

    // Initialize GUI
    gui = new AppGui(AppConst.APP_TITLE, this);
    System.out.println(AppConst.APP_DESC);
    setState(AppState.READY);
  }

  public static void main(String[] args) {
    // Create GUI in the EDT
    SwingUtilities.invokeLater(AppMain::new);
  }

  private void launchAnnotationTask() {
    WorkerSubAnnotator annotator = new WorkerSubAnnotator(searchDirectory);
    annotator.addPropertyChangeListener(evt -> {
      if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
        try {
          annotator.get();
          setState(AppState.READY);
        } catch (InterruptedException e) {
          LOGGER.warn("Subtitle annotation task was interrupted.");
        } catch (ExecutionException e) {
          Throwable originalExc = e.getCause();
          if (originalExc instanceof SubsDictError) {
            // Propagate our exceptions to the main error handler
            throw (SubsDictError) originalExc;
          }
          LOGGER.error("Subtitle annotation task returned an error:", originalExc);
          throw new UnexpectedError();
        }
      }
    });
    annotator.execute();
  }

  /**
   * Use a simple state driven behaviour.
   */
  private void setState(AppState state) {
    LOGGER.debug("Set app state to {}", state.toString());

    switch (state) {
      case READY:
        gui.setReadyState(true);
        String supportedExtensions = String.join(", *.", Arrays.asList(AppConst.VALID_SUBFILE_EXT));
        System.out.format("\n➟ Click the 'Find subtitles' button to find and process subtitle files(*.%s)\n", supportedExtensions);
        break;

      case ANNOTATING_SUBTITLES:
        gui.setReadyState(false);
        launchAnnotationTask();
        break;

      default:
        break;
    }
  }

  private enum AppState {
    READY,
    ANNOTATING_SUBTITLES
  }

  void setSearchDirectory(File searchDirectory) {
    this.searchDirectory = searchDirectory;
    setState(AppMain.AppState.ANNOTATING_SUBTITLES);
  }
}
