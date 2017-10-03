
/*
 *  Subtitles Dictionary
 */

import javax.swing.*;


// Application constants should be defined here
class AppConst {
    public static final String APP_TITLE = "Subtitles Dictionary";

    public static final String APP_DESC =   "This program reads subtitle files(format SRT) in the current directory tree and add the " +
            "dictionary definitions for the words encountered. "+
            "The result is a subtitle file(format ASS) that can be used for language learning: subtitles appears at the bottom "+
            "and words definitions at the top.\n"+
            "(Currently, only Japanese => English is supported.)\n\n" +
            "See config.yaml for options.";

    //-----------------------------------------------------
    static final String CONFIG_FILE = "config.yaml";
    static final String JMDICT_FILE = "resources/JMdict_e"; // => you can use data/JMDict_small to debug

    static final String APP_SIGNATURE = "LLANG-SUBTITLEDICTIONARY";
    static final String OUTPUT_SUB_EXT = "ass";
}


// All the app behaviour logic should be in this file
// Other component interact by sending AppEvents as defined below:
enum AppEvent {
    SEARCH_BT_CLICK,
    ANNOTATE_BT_CLICK,
    CLEANUP_BT_CLICK,
    LOG_BT_CLICK,
    SEARCHING_SUBTITLES_END,
    CLEANINGUP_SUBTITLES_END,
    ANNOTATING_SUBTITLES_END
}

interface AppEventListener {
    void onAppEvent(AppEvent evt,Object param);
}


// JP Subtitles Definitions Writer
// To build the application JAR in Intellij, run Build => Build Artifact
class AppMain implements AppEventListener {

    private AppGUI gui;
    private SubtitlesCollection diskSubtitles;


    public AppMain() {
        // Initialize GUI
        gui = new AppGUI(AppConst.APP_TITLE,this);
        System.out.println(AppConst.APP_DESC);
        setState(AppState.START);
    }

    public static void main(String[] args) {
        // Create GUI in the EDT
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new AppMain();
            }
        });
    }


    // STATES MANAGEMENT
    // ========================================================================
    // Use a simple state machine to drive behaviour
    enum AppState {
        START,
        SEARCHING_SUBTITLES,
        ANNOTATING,
        CLEANING,
        READY_TO_ANNOTATE_OR_CLEAN,
        END
    }

    public void setState(AppState state) {
        // TODO: LOG
        switch (state) {
            case START:
                gui.setButtonsState(true,false,false,false);
                System.out.format("\n➟ Click the 'Search' button to start searching all subtitles in the current directory (%s)\n",System.getProperty("user.dir"));
                break;
            case SEARCHING_SUBTITLES:
                gui.setButtonsState(false,false,false,false);
                System.out.println("\n-- Searching for subtitles -------------------------------");
                WorkerSubFinder finder = new WorkerSubFinder(WorkerSubAnnotator.VALID_SUBFILE_EXT, this);
                finder.execute();
                break;
            case ANNOTATING:
                gui.setButtonsState(false,false,false,false);
                System.out.println("\n-- Annotating subtitles -------------------------------");
                WorkerSubAnnotator translater = new WorkerSubAnnotator(diskSubtitles.canBeAnnotated, this);
                translater.execute();
                break;
            case CLEANING:
                gui.setButtonsState(false,false,false,false);
                System.out.println("\n-- Cleaning up previously annotated files -------------------------------");
                WorkerSubEraser eraser = new WorkerSubEraser(diskSubtitles.wasAnnotated, this);
                eraser.execute();
                break;
            case END:
                gui.setButtonsState(false,false,false,true);
                System.out.println("\n-- THE END -------------------------------");
                break;
            case READY_TO_ANNOTATE_OR_CLEAN:
                gui.setButtonsState(false,diskSubtitles.canBeAnnotated.size() > 0, diskSubtitles.wasAnnotated.size() > 0, true);
                if(diskSubtitles.canBeAnnotated.size() > 0) System.out.format("\n➟ Click the 'Annotate' button to annotate these %d subtitle files with words definitions.\n",diskSubtitles.canBeAnnotated.size());
                if(diskSubtitles.wasAnnotated.size() > 0) System.out.format("\n➟ Click 'Cleanup' to remove previously annotated subtitles.\n");
                break;
            default:
                break;
        }
    }


    // EVENTS MANAGEMENT
    // ========================================================================
    public void onAppEvent(AppEvent evt, Object param) {
        try {
            switch (evt) {
                case SEARCH_BT_CLICK:
                    setState(AppState.SEARCHING_SUBTITLES);
                    break;
                case CLEANUP_BT_CLICK:
                    setState(AppState.CLEANING);
                    break;
                case ANNOTATE_BT_CLICK:
                    setState(AppState.ANNOTATING);
                    break;
                case SEARCHING_SUBTITLES_END:
                    if(!(param instanceof SubtitlesCollection)) throw new Exception("SEARCHING_SUBTITLES_END: unexpected parameter");
                    diskSubtitles = (SubtitlesCollection)param;
                    setState(diskSubtitles.isEmpty() ? AppState.END : AppState.READY_TO_ANNOTATE_OR_CLEAN);
                    break;
                case CLEANINGUP_SUBTITLES_END:
                case ANNOTATING_SUBTITLES_END:
                    setState(AppState.END);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

