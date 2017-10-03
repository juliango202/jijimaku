
import javax.swing.*;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;


class WorkerSubEraser extends SwingWorker<Void, Object> {

    private List<String> toErase;
    private AppEventListener evtListener;

    WorkerSubEraser(List<String> fileList, AppEventListener listener) {
        toErase = fileList;
        evtListener = listener;
    }

    @Override
    public Void doInBackground() throws Exception {
        if( SwingUtilities.isEventDispatchThread() ) throw new Exception("FileFinder should not run on the EDT thread!");

        // Check file is a subtitle written by our app and erase it
        for (Iterator<String> iterator = toErase.iterator(); iterator.hasNext();) {
            File fileEntry = new File(iterator.next());
            if(!WorkerSubFinder.isSubDictFile(fileEntry)) throw new Exception("file is not our subtitle:"+fileEntry.getAbsolutePath());
            System.out.println("Delete " + fileEntry.getName());
            if(fileEntry.exists() && !fileEntry.delete()) {
                System.out.println("Could not delete file: "+fileEntry.getAbsolutePath());
            }
            else {
                iterator.remove();
            }
        }
        return null;
    }

    @Override
    protected void done() {
        try {
            evtListener.onAppEvent(AppEvent.CLEANINGUP_SUBTITLES_END, get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}

