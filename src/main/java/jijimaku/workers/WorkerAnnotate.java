package jijimaku.workers;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;
import jijimaku.models.ServicesParam;
import jijimaku.services.AnnotationService;
import jijimaku.utils.SubtitleFile;
import jijimaku.utils.FileManager;
import subtitleFile.FatalParsingException;


/**
 * Swing worker that annotates the subtitle files.
 */
public class WorkerAnnotate extends SwingWorker<Void, Object> {
  private static final Logger LOGGER;

  private static final String ASS_FILE_BACKUP_SUFFIX = "._original";

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

  /**
   * Process one file.
   * @return if the file was annotated, false otherwise.
   */
  private boolean processFile(File fileEntry) throws IOException, FatalParsingException {
    String fileContents = FileManager.fileAnyEncodingToString(fileEntry);
    if (fileEntry.isHidden() || SubtitleFile.isJijimakuFile(fileContents)) {
      LOGGER.debug("{} is one of our annotated subtitle, skip it.", fileEntry.getName());
      return false;
    }
    String fileName = fileEntry.getName();
    String fileBaseName = FilenameUtils.getBaseName(fileName);

    LOGGER.info("Processing " + fileName + "...");
    String[] annotated = annotationService.annotateSubtitleFile(fileName, fileContents);
    if (annotated == null) {
      LOGGER.info("Nothing to annotate was found in this file(wrong language?)");
      return false;
    }

    // For ASS files, make a copy because the original file will be overwritten
    if (FilenameUtils.getExtension(fileName).equals("ass")) {
      if (fileBaseName.endsWith(ASS_FILE_BACKUP_SUFFIX)) {
        // This is already our copy, just remove suffix when writing out the result
        fileBaseName = fileBaseName.substring(0, fileBaseName.lastIndexOf(ASS_FILE_BACKUP_SUFFIX));
      } else {
        Files.copy(Paths.get(fileEntry.toURI()), Paths.get(fileEntry.getParent() + "/" + fileBaseName + ASS_FILE_BACKUP_SUFFIX + ".ass"));
      }
    }

    String outFile = fileEntry.getParent() + "/" + fileBaseName + ".ass";
    FileManager.writeStringArrayToFile(outFile, annotated);
    return true;
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
        if (processFile(fileEntry)) {
          nbAnnotated++;
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

