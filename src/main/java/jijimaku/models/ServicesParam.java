package jijimaku.models;

import jijimaku.services.Config;
import jijimaku.services.SubtitleService;
import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.langparser.LangParser;

/**
 * Container for the app services. (Parameter Object).
 */
public class ServicesParam {

  private final Config config;
  private final JijiDictionary dictionary;
  private final LangParser parser;
  private final SubtitleService subtitleService;

  public ServicesParam(Config config, JijiDictionary dictionary, LangParser parser, SubtitleService subtitleService) {
    this.dictionary = dictionary;
    this.parser = parser;
    this.subtitleService = subtitleService;
    this.config = config;
  }

  public Config getConfig() {
    return config;
  }

  public JijiDictionary getDictionary() {
    return dictionary;
  }

  public LangParser getParser() {
    return parser;
  }

  public SubtitleService getSubtitleService() {
    return subtitleService;
  }
}
