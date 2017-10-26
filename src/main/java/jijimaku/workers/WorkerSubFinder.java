package jijimaku.workers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jijimaku.error.UnexpectedError;
import jijimaku.models.SubtitlesCollection;
import jijimaku.utils.FileManager;


public class WorkerSubFinder extends SwingWorker<SubtitlesCollection, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String APP_SIGNATURE = "BY-SUBTITLEDICTIONARY";

  private String[] supportedExtensions;
  private File searchDirectory;

  /**
   * Swing worker that search for subtitle files on the disk.
   * @param searchDirectory The directory where to search for subtitles. Subdirectory will be searched recursively
   * @param supportedExtensions list of file extensions supported for subtitles
   */
  public WorkerSubFinder(File searchDirectory, String[] supportedExtensions) {
    if (searchDirectory == null || !searchDirectory.isDirectory()) {
      LOGGER.error("Invalid search directory {}", String.valueOf(searchDirectory));
      throw new UnexpectedError();
    }
    this.supportedExtensions = supportedExtensions;
    this.searchDirectory = searchDirectory;
  }

  // Read a file and return true if it was written by us
  // (search for app signature in first 5 lines)
  private boolean isSubDictFile(File f) {
    try {
      InputStreamReader in = new InputStreamReader(FileManager.getUtf8Stream(f));
      try (BufferedReader br = new BufferedReader(new BufferedReader(in))) {
        String line;
        int lineNum = 0;
        while ((line = br.readLine()) != null && lineNum < 5) {
          if (line.startsWith(";" + APP_SIGNATURE)) {
            return true;
          }
          lineNum++;
        }
      }
      return false;
    } catch (IOException exc) {
      LOGGER.error("Could not process file {}", f.getName(), exc);
      return false;
    }
  }

  @Override
  public SubtitlesCollection doInBackground() {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new UnexpectedError("FileFinder should not run on the EDT thread!");
    }

    // Read all subtitles files from current directory
    // Look for subtitles we can process
    SubtitlesCollection coll = new SubtitlesCollection();
    for (File fileEntry : FileUtils.listFiles(searchDirectory, supportedExtensions, true)) {
      if (!fileEntry.isHidden() && !isSubDictFile(fileEntry)) {
        coll.canBeAnnotated.add(fileEntry.getAbsolutePath());
        LOGGER.info("Found " + fileEntry.getName());
      }
      if (isCancelled()) {
        break;
      }
    }

    if (coll.isEmpty()) {
      LOGGER.info("No subtitle found in this directory.");
    }
    return coll;
  }
}

