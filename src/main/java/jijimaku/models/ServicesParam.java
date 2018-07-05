package jijimaku.models;

import jijimaku.AppConfig;
import jijimaku.services.dictionary.Dictionary;
import jijimaku.services.langparser.LangParser;

/**
 * Container for the app services. (Parameter Object).
 */
public class ServicesParam {

  private final AppConfig config;
  private final Dictionary dictionary;
  private final LangParser parser;

  public ServicesParam(AppConfig config, Dictionary dictionary, LangParser parser) {
    this.dictionary = dictionary;
    this.parser = parser;
    this.config = config;
  }

  public AppConfig getConfig() {
    return config;
  }

  public Dictionary getDictionary() {
    return dictionary;
  }

  public LangParser getParser() {
    return parser;
  }
}
