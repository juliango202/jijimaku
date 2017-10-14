package workers;

import models.SubtitlesCollection;
import utils.FileManager;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.Collection;


public class WorkerSubFinder extends SwingWorker<SubtitlesCollection, Object> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static final String APP_SIGNATURE = "BY-SUBTITLEDICTIONARY";

    // Read a file and return true if it was written by us
    // (search for app signature in first 5 lines)
    static public boolean isSubDictFile(File f) {
        try {
            InputStreamReader in= new InputStreamReader(FileManager.getUtf8Stream(f));
            try (BufferedReader br = new BufferedReader(new BufferedReader(in))) {
                String line;
                int iLine = 0;
                while ((line = br.readLine()) != null && iLine < 5) {
                    if(line.startsWith(";"+ APP_SIGNATURE)) return true;
                    iLine++;
                }
            }
            return false;
        }
        catch (IOException exc) {
            LOGGER.error("Could not process file {}", f.getName(), exc);
            return false;
        }
    }

    private String[] supportedExtensions;

    public WorkerSubFinder(String[] supportedExtensions) {
        this.supportedExtensions = supportedExtensions;
    }

    // Return an iterator on files in the current directory and its children(recursive)
    public Collection<File> listCurrentDirFiles(String[] extensions) {

        File pathObj = new File(System.getProperty("user.dir"));
        if(!pathObj.isDirectory()) {
            System.out.println("Error while reading current directory");
            System.exit(1);
        }
        return FileUtils.listFiles(pathObj, extensions, true);
    }

    @Override
    public SubtitlesCollection doInBackground() throws Exception {
        if( SwingUtilities.isEventDispatchThread() ) throw new Exception("FileFinder should not run on the EDT thread!");

        // Read all subtitles files from current directory
        // Look for subtitles we can process
        SubtitlesCollection coll = new SubtitlesCollection();
        for (File fileEntry : listCurrentDirFiles(supportedExtensions)) {
            if( !fileEntry.isHidden() ) {
                coll.canBeAnnotated.add(fileEntry.getAbsolutePath());
                System.out.println("Found " + fileEntry.getName());
            }
            if (isCancelled()) {
                break;
            }
        }

        if( coll.isEmpty() ) System.out.println("No subtitle found in this directory.");
        return coll;
    }
}

