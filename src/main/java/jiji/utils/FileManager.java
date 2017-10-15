package jiji.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Utility class to manage file IO & encoding
public class FileManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Return an utf-8 inputstream for the file
  // convert encoding if necessary, using http://userguide.icu-project.org/conversion/detection
  // NOTE: this will load the entire file in memory
  public static InputStream getUtf8Stream(File f) throws IOException {

    byte[] byteData = IOUtils.toByteArray(new FileInputStream(f));

    CharsetDetector detector = new CharsetDetector();

    String unicodeData = detector.getString(byteData, null);
    // Add to newline at the end of the file otherwise the subtitle parser library can get confused by EOF
    unicodeData += System.getProperty("line.separator") + System.getProperty("line.separator");
    CharsetMatch match = detector.detect();
    if (match != null && match.getConfidence() > 60) {
      LOGGER.info("{} has a detected encoding: {} and language: {}", f.getName(), match.getName(), match.getLanguage());
    }

    // TO DO => log file encoding => match.getName()
    byte[] unicodeByteData = unicodeData.getBytes("UTF-8");

    // Must use BOMInputStream otherwise files with BOM will broke :(((
    // => http://stackoverflow.com/questions/4897876/reading-utf-8-bom-marker
    return new BOMInputStream(new ByteArrayInputStream(unicodeByteData));
  }

  public static void writeStringArrayToFile(String fileFullPath, String[] lines) throws IOException {
    BufferedWriter bw = new BufferedWriter(new FileWriter(fileFullPath));
    for (String line : lines) {
      bw.write(line);
      bw.newLine();
    }
    bw.flush();
    bw.close();
  }
}

