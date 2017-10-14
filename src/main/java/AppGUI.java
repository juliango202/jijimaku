
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.TextAreaOutputStream;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;


// Class to layout GUI components

@SuppressWarnings("serial")
public class AppGUI extends JFrame implements ActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private JButton searchBt, quitBt;
    private JFileChooser fileChooser;
    private AppMain app;

    /** Creates the reusable dialog. */
    public AppGUI(String title, AppMain app) {
        super(title);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        createInterfaceComponents(getContentPane());
        pack();
        setVisible( true );
        this.app = app;
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
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

        // Add the menu buttons
        Font menuFont = new Font("Arial", Font.PLAIN, 12);

        // SEARCH button ------------
        searchBt= new JButton("Find subtitles",createImageIcon("icon_search.png",  "search icon"));
        searchBt.addActionListener(this);
        searchBt.setFont(menuFont);
        menuBox.add(searchBt);
        menuBox.add(Box.createRigidArea(new Dimension(2, 0)));

        // QUIT button ------------
        menuBox.add(Box.createHorizontalGlue());
        quitBt = new JButton("Quit",createImageIcon("icon_exit.png",  "log icon"));
        quitBt.addActionListener(this);
        quitBt.setFont(menuFont);
        menuBox.add(quitBt);
    }


    // INTERACTION WITH APPMAIN
    // ========================================================================

    // Enable / Disable interface buttons
    public void setReadyState(Boolean canSearch) {
        searchBt.setEnabled(canSearch);
    }

    // Event management => exit the app if QUIT button is pressed, otherwise bubble the event to AppMain
    public void actionPerformed(ActionEvent e) {
        if(e.getSource() == quitBt) {
            dispose();
            System.exit(0);
        } else if(e.getSource() == searchBt) {
            fileChooser.showSaveDialog(null);
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                app.setSearchDirectory(file);
            }
        }
    }

}