package jijimaku.workers;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;
import jijimaku.models.ServicesParam;
import jijimaku.services.AnnotationService;
import jijimaku.utils.SubtitleFile;
import jijimaku.utils.FileManager;


/**
 * Swing worker that annotates the subtitle files.
 */
public class WorkerAnnotate extends SwingWorker<Void, Object> {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private final File searchDirectory;
  private final String[] searchExtensions;
  private final AnnotationService annotationService;

  /**
   * Constructor.
   * @param searchDirectory disk directory where to search subtitles(recursive)
   */
  public WorkerAnnotate(File searchDirectory, String[] searchExtensions, ServicesParam services) {
    if (searchDirectory == null || !searchDirectory.isDirectory()) {
      LOGGER.error("Invalid search directory {}", String.valueOf(searchDirectory));
      throw new UnexpectedError();
    }
    this.searchDirectory = searchDirectory;
    this.searchExtensions = searchExtensions;
    this.annotationService = new AnnotationService(services);
  }


  @Override
  public Void doInBackground() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new Exception("Worker should not run on the EDT thread!");
    }

    LOGGER.info("------------------- Searching in {} -------------------", searchDirectory.getAbsolutePath());
    Integer nbAnnotated = 0;
    for (File fileEntry : FileUtils.listFiles(searchDirectory, searchExtensions, true)) {
      try {
        String fileContents = FileManager.fileAnyEncodingToString(fileEntry);

        if (fileEntry.isHidden() || SubtitleFile.isJijimakuFile(fileContents)) {
          LOGGER.debug("{} is one of our annotated subtitle, skip it.", fileEntry.getName());
          continue;
        }
        LOGGER.info("Processing " + fileEntry.getName() + "...");

        if (annotationService.annotateSubtitleFile(fileEntry.getParent(), fileEntry.getName(), fileContents)) {
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

