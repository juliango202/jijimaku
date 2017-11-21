package jijimaku.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility class to manage file IO & encoding.
 */
public class FileManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

