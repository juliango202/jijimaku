package langdictionary;

import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import javax.xml.XMLConstants;
import org.ahocorasick.trie.*;
import utils.FileManager;

// Simple XML SAX parser to load JMDict xml data into a java Hashmap 
public class JMDict {
	
	Map<String, String> dict = new HashMap<String, String>();
	Trie atrie;
	
	public String getMeaning( String w ) {
		if( dict. containsKey( w ) ) {
			return dict.get( w );
		}
		else {
			return "";
		}
	}

	public void outDict() {
		String[] toReturn = new String [dict.size()];
		int i = 0;
		for (String key : dict.keySet()) {
			toReturn[i++] = key;
		}
		try {
			FileManager.writeStringArrayToFile("mydictwords.txt",toReturn);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getParse(String text) {
		Collection<Token> tokens = atrie.tokenize(text);
		String res = "";
		for (Token token : tokens) {
			if (token.isMatch()) {
				res += token.getFragment()+"|";
			}
			else res += "*|";
		}
		return res;
	}
	
	public void displayDict() {
		
		Iterator<Entry<String, String>> i = dict.entrySet().iterator();
		while(i.hasNext()) {

			Entry<String, String> entry = (Entry<String, String>) i.next();
			System.out.println( entry.getValue() );
		}
	}

	public JMDict(InputStream in) {
		//final Trie.TrieBuilder atrieBuilder = Trie.builder().removeOverlaps();

	 	if(in == null ) throw new IllegalArgumentException("invalid input stream for JMDict");
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
				private ArrayList<String> kanjiForms = new ArrayList<String>();
				private ArrayList<String> kanaForms = new ArrayList<String>();
				private ArrayList<String> meanings = new ArrayList<String>();
				private boolean isUsuallyKana = false;

				public void endElement(String uri, String localName, String qName) throws SAXException {

					if (qName.equals("entry")) {

						count++;

						//if (kanjiForms.isEmpty() || isUsuallyKana) {

							// Kana words have no kanji, index them using the kana data
							kanjiForms.addAll(kanaForms);
						//}

						for (String word : kanjiForms) {

							// Store kanji entry in dict
							String pronounciation = "";
							// Do not record pronunciation for kana words
							if(kanaForms.contains(word)) {
								for (String s : kanaForms) {

									if (!pronounciation.isEmpty()) {
										pronounciation += ",  ";
									}
									pronounciation += s;
								}
							}

							String dictEntry = "{\\fs9\\c&FFFFFF&}" + word + "{\\r}"; //word;

							if (!pronounciation.isEmpty()) dictEntry += "【" + pronounciation + "】";
							dictEntry += " ";

							//String m = "";
							for (int i = 0; i < meanings.size(); i++) {
								if (meanings.size() > 1) dictEntry += "(" + (i + 1) + ") ";
								dictEntry += meanings.get(i) + " ";
							}

							if (dict.containsKey(word)) {

								dict.put(word, dict.get(word) + "\\N\\n　" + dictEntry);
							} else {

								dict.put(word, dictEntry);
								//atrieBuilder.addKeyword(word);
							}
						}

						kanjiForms = new ArrayList<String>();
						kanaForms = new ArrayList<String>();
						meanings = new ArrayList<String>();
						isUsuallyKana = false;
					} else if (qName.equals("keb")) {
						kanjiForms.add(xmlText);
					} else if (qName.equals("reb")) {
						kanaForms.add(xmlText);
					} else if (qName.equals("sense")) {
						if (!meaningText.isEmpty()) meanings.add(meaningText);
						meaningText = "";
					} else if (qName.equals("gloss")) {
						meaningText += xmlText + "; ";
					} else if (qName.equals("misc") && xmlText.equals("word usually written using kana alone")) {
						isUsuallyKana = true;
					}

				}

				public void characters(char ch[], int start, int length) throws SAXException {

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

