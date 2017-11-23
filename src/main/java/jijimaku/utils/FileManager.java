package jijimaku.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;

/**
 * Utility class to manage file IO & encoding.
 */
public class FileManager {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  /**
   * Return the directory from which the application is run.
   * In case of a compiled JAR this is not the current directory unfortunately
   * See https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
   */
  public static String getAppDirectory() {
    // TODO: check for spaces and unicode char in path
    Path jarDirectory;
    try {
      jarDirectory = Paths.get(FileManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    } catch (URISyntaxException exc) {
      LOGGER.error("Cannot retrieve app directory", exc);
      throw new UnexpectedError();
    }
    File[] files = jarDirectory.toFile().listFiles((dir1, name) -> name.endsWith(".jar"));
    if (files.length == 0) {
      // If there is no JAR(this is development mode?) just return the current directory
      return ".";
    }
    return jarDirectory.toString();
  }

  /**
   * Directory where to store log files.
   */
  public static String getLogsDirectory() {
    return getAppDirectory() + "/logs";
  }

  /**
   * Read a text file detecting encoding using http://userguide.icu-project.org/conversion/detection
   * Return the file contents as a String.
   */
  public static String fileAnyEncodingToString(File f) throws IOException {

    byte[] byteData = IOUtils.toByteArray(new FileInputStream(f));

    CharsetDetector detector = new CharsetDetector();

    String unicodeData = detector.getString(byteData, null);
    // Add to newline at the end of the file otherwise the subtitle parser library can get confused by EOF
    unicodeData += System.getProperty("line.separator") + System.getProperty("line.separator");
    CharsetMatch match = detector.detect();
    if (match != null && match.getConfidence() > 60) {
      LOGGER.debug("{} has a detected encoding: {}", f.getName(), match.getName());
      if (match.getLanguage() != null) {
        LOGGER.debug("{} has a detected language: {}", f.getName(), match.getLanguage());
      }
    }
    return unicodeData;
  }

  public static void writeStringArrayToFile(String fileFullPath, String[] lines) throws IOException {
    BufferedWriter bw = Files.newBufferedWriter(Paths.get(fileFullPath), StandardCharsets.UTF_8);
    for (String line : lines) {
      bw.write(line);
      bw.newLine();
    }
    bw.flush();
    bw.close();
  }
}

