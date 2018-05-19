package jijimaku.models;

import jijimaku.AppConfig;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.langparser.LangParser;

/**
 * Container for the app services. (Parameter Object).
 */
public class ServicesParam {

  private final AppConfig config;
  private final JijiDictionary dictionary;
  private final LangParser parser;

  public ServicesParam(AppConfig config, JijiDictionary dictionary, LangParser parser) {
    this.dictionary = dictionary;
    this.parser = parser;
    this.config = config;
  }

  public AppConfig getConfig() {
    return config;
  }

  public JijiDictionary getDictionary() {
    return dictionary;
  }

  public LangParser getParser() {
    return parser;
  }
}
