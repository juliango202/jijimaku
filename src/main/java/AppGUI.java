
import utils.TextAreaOutputStream;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.PrintStream;



public class AppGUI extends JFrame implements ActionListener {

    private JButton searchBt, annotateBt, cleanupBt, logBt, quitBt;
    private AppEventListener evtListener;

    /** Creates the reusable dialog. */
    public AppGUI(String title, AppEventListener listener) {
        super(title);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        createInterfaceComponents(getContentPane());
        pack();
        setVisible( true );
        evtListener = listener;
    }

    /** Returns an ImageIcon, or null if the path was invalid. */
    private ImageIcon createImageIcon(String path, String description) {
        java.net.URL imgURL = getClass().getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL, description);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }


    // CREATE INTERFACE COMPONENTS
    // ========================================================================
    private void createInterfaceComponents(Container pane) {

        if (!(pane.getLayout() instanceof BorderLayout)) {
            System.out.println("Container doesn't use BorderLayout!");
            System.exit(1);
        }

        createConsoleTextare(pane);
        createMenuButtons(pane);
    }

    private void createConsoleTextare(Container pane) {
        // The main GUI component is a textarea that will display System.out
        JTextArea ta = new JTextArea(30,65);
        ta.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        TextAreaOutputStream taos = new TextAreaOutputStream( ta, 1000 );
        PrintStream ps = new PrintStream( taos );
        System.setOut( ps );
        System.setErr( ps );

        // Make the textarea scrollable
        JScrollPane scrollTa = new JScrollPane( ta );
        scrollTa.setBorder(BorderFactory.createEmptyBorder());
        pane.add( scrollTa, BorderLayout.CENTER);
    }

    private void createMenuButtons(Container pane) {
        // Make a horizontal box at the bottom to hold menu buttons
        Box menuBox = Box.createHorizontalBox();
        pane.add(menuBox, BorderLayout.PAGE_END );
        menuBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        // Add all the menu buttons
        Font menuFont = new Font("Arial", Font.PLAIN, 12);
        // SEARCH button ------------
        searchBt= new JButton("Search",createImageIcon("icon_search.png",  "search icon"));
        searchBt.addActionListener(this);
        searchBt.setFont(menuFont);
        menuBox.add(searchBt);
        menuBox.add(Box.createRigidArea(new Dimension(2, 0)));
        // ANNOTATE button ------------
        annotateBt = new JButton("Annotate",createImageIcon("icon_play.png",  "annotate icon"));
        annotateBt.addActionListener(this);
        annotateBt.setFont(menuFont);
        menuBox.add(annotateBt);
        menuBox.add(Box.createRigidArea(new Dimension(2, 0)));
        // CLEANUP button ------------
        cleanupBt = new JButton("Cleanup",createImageIcon("icon_bin.png",  "cleanup icon"));
        cleanupBt.addActionListener(this);
        cleanupBt.setFont(menuFont);
        menuBox.add(cleanupBt);
        menuBox.add(Box.createHorizontalGlue());
        // LOG button ------------
        logBt = new JButton("See log",createImageIcon("icon_log.png",  "log icon"));
        logBt.addActionListener(this);
        logBt.setFont(menuFont);
        menuBox.add(logBt);
        menuBox.add(Box.createRigidArea(new Dimension(2, 0)));
        // QUIT button ------------
        quitBt = new JButton("Quit",createImageIcon("icon_exit.png",  "log icon"));
        quitBt.addActionListener(this);
        quitBt.setFont(menuFont);
        menuBox.add(quitBt);
    }


    // INTERACTION WITH APPMAIN
    // ========================================================================

    // Enable / Disable interface buttons
    public void setButtonsState(Boolean canSearch, Boolean canAnnotate, Boolean canClean, Boolean canLog) {
        searchBt.setEnabled(canSearch);
        annotateBt.setEnabled(canAnnotate);
        cleanupBt.setEnabled(canClean);
        logBt.setEnabled(canLog);
    }

    // Event management => exit the app if QUIT button is pressed, otherwise bubble the event to AppMain
    public void actionPerformed(ActionEvent e) {

        if(e.getSource() == quitBt) {
            dispose();
            System.exit(0);
        }
        else if(e.getSource() == searchBt) { evtListener.onAppEvent(AppEvent.SEARCH_BT_CLICK,null); }
        else if(e.getSource() == annotateBt) { evtListener.onAppEvent(AppEvent.ANNOTATE_BT_CLICK,null); }
        else if(e.getSource() == cleanupBt  ) { evtListener.onAppEvent(AppEvent.CLEANUP_BT_CLICK,null); }
        else if(e.getSource() == logBt      ) { evtListener.onAppEvent(AppEvent.LOG_BT_CLICK,null); }
    }

}