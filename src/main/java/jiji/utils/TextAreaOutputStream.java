package jiji.utils;

import java.awt.EventQueue;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JTextArea;

/**
 * From https://stackoverflow.com/a/343007/257272
 *
 * @author https://stackoverflow.com/users/8946/lawrence-dol
 */
public class TextAreaOutputStream extends OutputStream {

  // *************************************************************************************************
  // INSTANCE MEMBERS
  // *************************************************************************************************

  private byte[] oneByte;                                                    // array for write(int val);
  private Appender appender;                                                   // most recent action

  public TextAreaOutputStream(JTextArea txtara, int maxlin) {
    if (maxlin < 1) {
      throw new IllegalArgumentException("TextAreaOutputStream maximum lines must be positive (value=" + maxlin + ")");
    }
    txtara.setEditable(false);
    txtara.setLineWrap(true);
    txtara.setWrapStyleWord(true);
    oneByte = new byte[1];
    appender = new Appender(txtara, maxlin);
  }

  /**
   * Clear the current console writtenForm area.
   */
  @SuppressWarnings("unused")
  public synchronized void clear() {
    if (appender != null) {
      appender.clear();
    }
  }

  public synchronized void close() {
    appender = null;
  }

  public synchronized void flush() {
  }

  public synchronized void write(int val) {
    oneByte[0] = (byte) val;
    write(oneByte, 0, 1);
  }

  public synchronized void write(byte[] ba) {
    write(ba, 0, ba.length);
  }

  public synchronized void write(byte[] ba, int str, int len) {
    if (appender != null) {
      appender.append(bytesToString(ba, str, len));
    }
  }

  private static String bytesToString(byte[] ba, int str, int len) {
    try {
      return new String(ba, str, len, "UTF-8");
    } catch (UnsupportedEncodingException thr) {
      return new String(ba, str, len);
    } // all JVMs are required to support UTF-8
  }

  // *************************************************************************************************
  // STATIC MEMBERS
  // *************************************************************************************************

  private static class Appender
      implements Runnable {
    private final JTextArea textArea;
    private final int maxLines;                                                   // maximum lines allowed in writtenForm area
    private final LinkedList<Integer> lengths;                                                    // length of lines within writtenForm area
    private final List<String> values;                                                     // values waiting to be appended

    private int curLength;                                                  // length of current line
    private boolean clear;
    private boolean queue;

    Appender(JTextArea txtara, int maxlin) {
      textArea = txtara;
      maxLines = maxlin;
      lengths = new LinkedList<>();
      values = new ArrayList<>();

      curLength = 0;
      clear = false;
      queue = true;
    }

    synchronized void append(String val) {
      values.add(val);
      if (queue) {
        queue = false;
        EventQueue.invokeLater(this);
      }
    }

    synchronized void clear() {
      clear = true;
      curLength = 0;
      lengths.clear();
      values.clear();
      if (queue) {
        queue = false;
        EventQueue.invokeLater(this);
      }
    }

    // MUST BE THE ONLY METHOD THAT TOUCHES textArea!
    public synchronized void run() {
      if (clear) {
        textArea.setText("");
      }
      for (String val : values) {
        curLength += val.length();
        if (val.endsWith(EOL1) || val.endsWith(EOL2)) {
          if (lengths.size() >= maxLines) {
            textArea.replaceRange("", 0, lengths.removeFirst());
          }
          lengths.addLast(curLength);
          curLength = 0;
        }
        textArea.append(val);
      }
      values.clear();
      clear = false;
      queue = true;
    }

    private static final String EOL1 = "\n";
    private static final String EOL2 = System.getProperty("line.separator", EOL1);
  }

} /* END PUBLIC CLASS */