package jijimaku;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintStream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedError;
import jijimaku.utils.FileManager;
import jijimaku.utils.TextAreaOutputStream;


/**
 * Class to layout GUI components.
 */
@SuppressWarnings("serial")
class AppGui extends JFrame implements ActionListener {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private JButton searchBt;
  private JButton quitBt;
  private JFileChooser fileChooser;
  private AppMain app;

  /**
   * Creates the reusable dialog.
   */
  AppGui(String title, AppMain app) {
    super(title);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    createInterfaceComponents(getContentPane());
    pack();
    setVisible(true);
    this.app = app;
    fileChooser = new JFileChooser();
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    Image image = Toolkit.getDefaultToolkit().getImage(getClass().getClassLoader().getResource("iconJijimaku.png"));
    setIconImage(image);

  }

  /**
   * Returns an ImageIcon, or null if the path was invalid.
   */
  private ImageIcon createImageIcon(String path, String description) {
    java.net.URL imgUrl = getClass().getClassLoader().getResource(path);
    if (imgUrl != null) {
      return new ImageIcon(imgUrl, description);
    } else {
      System.err.println("Couldn't find file: " + path);
      return null;
    }
  }

  // CREATE INTERFACE COMPONENTS

  private void createInterfaceComponents(Container pane) {

    if (!(pane.getLayout() instanceof BorderLayout)) {
      LOGGER.error("Container doesn't use BorderLayout!");
      throw new UnexpectedError();
    }

    createConsoleTextare(pane);
    createMenuButtons(pane);
  }

  private void createConsoleTextare(Container pane) {
    // The main GUI component is a textarea that will display System.out
    JTextArea ta = new JTextArea(30, 65);
    ta.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    TextAreaOutputStream taos = new TextAreaOutputStream(ta, 1000);
    PrintStream ps = new PrintStream(taos);
    System.setOut(ps);
    System.setErr(ps);

    // Make the textarea scrollable
    JScrollPane scrollTa = new JScrollPane(ta);
    scrollTa.setBorder(BorderFactory.createEmptyBorder());
    pane.add(scrollTa, BorderLayout.CENTER);
  }

  private void createMenuButtons(Container pane) {
    // Make a horizontal box at the bottom to hold menu buttons
    Box menuBox = Box.createHorizontalBox();
    pane.add(menuBox, BorderLayout.PAGE_END);
    menuBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)));

    // Add the menu buttons
    Font menuFont = new Font("Arial", Font.PLAIN, 12);

    // SEARCH button ------------
    searchBt = new JButton("Find subtitles", createImageIcon("iconSearch.png", "search icon"));
    searchBt.addActionListener(this);
    searchBt.setFont(menuFont);
    menuBox.add(searchBt);
    menuBox.add(Box.createRigidArea(new Dimension(2, 0)));

    // QUIT button ------------
    menuBox.add(Box.createHorizontalGlue());
    quitBt = new JButton("Quit", createImageIcon("iconTransparent.png", "use transparent icon for padding"));
    quitBt.addActionListener(this);
    quitBt.setFont(menuFont);
    menuBox.add(quitBt);
  }


  // INTERACTION WITH APPMAIN

  /**
   * Enable / Disable directory selection.
   */
  void toggleDirectorySelector(Boolean isEnabled) {
    searchBt.setEnabled(isEnabled);
  }

  /**
   * Event management.
   * => exit the app if QUIT button is pressed, otherwise bubble the event to AppMain
   */
  public void actionPerformed(ActionEvent evt) {
    if (evt.getSource() == quitBt) {
      dispose();
      System.exit(0);
    } else if (evt.getSource() == searchBt) {
      fileChooser.showSaveDialog(null);
      File file = fileChooser.getSelectedFile();
      if (file != null) {
        app.setSearchDirectory(file);
      }
    }
  }

}