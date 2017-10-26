package jijimaku.langdictionary;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.ahocorasick.trie.Token;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jijimaku.utils.FileManager;

// Simple XML SAX parser to load JMDict xml data into a java Hashmap
@SuppressWarnings("checkstyle")
public class JmDict {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<String, String> dict = new HashMap<String, String>();
  private Trie atrie;

  public String getMeaning(String w) {
    if (dict.containsKey(w)) {
      return dict.get(w);
    } else {
      return "";
    }
  }

  public void outDict() {
    String[] toReturn = new String[dict.size()];
    int i = 0;
    for (String key : dict.keySet()) {
      toReturn[i++] = key;
    }
    try {
      FileManager.writeStringArrayToFile("mydictwords.txt", toReturn);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String getParse(String text) {
    Collection<Token> tokens = atrie.tokenize(text);
    String res = "";
    for (Token token : tokens) {
      if (token.isMatch()) {
        res += token.getFragment() + "|";
      } else {
        res += "*|";
      }
    }
    return res;
  }

  public void displayDict() {

    Iterator<Entry<String, String>> i = dict.entrySet().iterator();
    while (i.hasNext()) {

      Entry<String, String> entry = (Entry<String, String>) i.next();
      LOGGER.info(entry.getValue());
    }
  }

  public JmDict(InputStream in) {
    //final Trie.TrieBuilder atrieBuilder = Trie.builder().removeOverlaps();

    if (in == null) {
      throw new IllegalArgumentException("invalid input stream for JMDict");
    }
    try {

      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      //factory.setAttribute("http://apache.org/xml/properties/entity-expansion-limit", new Integer("100000"));
      SAXParser saxParser = factory.newSAXParser();
      //saxParser.setProperty(XMLConstants.JDK_ENTITY_EXPANSION_LIMIT, "100000");

      DefaultHandler handler = new DefaultHandler() {

        private int count;
        private String xmlText;
        private String meaningText = "";
        private ArrayList<String> kanjiForms = new ArrayList<>();
        private ArrayList<String> kanaForms = new ArrayList<>();
        private ArrayList<String> meanings = new ArrayList<>();
        private boolean isUsuallyKana = false;

        public void endElement(String uri, String localName, String qqName) throws SAXException {

          if (qqName.equals("entry")) {

            count++;

            //if (kanjiForms.isEmpty() || isUsuallyKana) {

            // Kana words have no kanji, index them using the kana data
            kanjiForms.addAll(kanaForms);
            //}

            for (String word : kanjiForms) {

              // Store kanji entry in dict
              String pronounciation = "";
              // Do not record pronunciation for kana words
              if (kanaForms.contains(word)) {
                for (String s : kanaForms) {

                  if (!pronounciation.isEmpty()) {
                    pronounciation += ",  ";
                  }
                  pronounciation += s;
                }
              }

              String dictEntry = "{\\fs9\\c&FFFFFF&}" + word + "{\\r}"; //word;

              if (!pronounciation.isEmpty()) {
                dictEntry += "【" + pronounciation + "】";
              }
              dictEntry += " ";

              //String m = "";
              for (int i = 0; i < meanings.size(); i++) {
                if (meanings.size() > 1) {
                  dictEntry += "(" + (i + 1) + ") ";
                }
                dictEntry += meanings.get(i) + " ";
              }

              if (dict.containsKey(word)) {
                dict.put(word, dict.get(word) + "\\N\\n　" + dictEntry);
              } else {
                dict.put(word, dictEntry);
                //atrieBuilder.addKeyword(word);
              }
            }

            kanjiForms = new ArrayList<>();
            kanaForms = new ArrayList<>();
            meanings = new ArrayList<>();
            isUsuallyKana = false;
          } else if (qqName.equals("keb")) {
            kanjiForms.add(xmlText);
          } else if (qqName.equals("reb")) {
            kanaForms.add(xmlText);
          } else if (qqName.equals("sense")) {
            if (!meaningText.isEmpty()) {
              meanings.add(meaningText);
            }
            meaningText = "";
          } else if (qqName.equals("gloss")) {
            meaningText += xmlText + "; ";
          } else if (qqName.equals("misc") && xmlText.equals("word usually written using kana alone")) {
            isUsuallyKana = true;
          }

        }

        public void characters(char [] ch, int start, int length) throws SAXException {

          xmlText = new String(ch, start, length);
        }

      };

      saxParser.parse(in, handler);
      //atrie = atrieBuilder.build();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}

