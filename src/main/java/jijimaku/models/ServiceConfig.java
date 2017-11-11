package jijimaku.models;

import jijimaku.services.jijidictionary.JijiDictionary;
import jijimaku.services.langparser.JapaneseParser;
import jijimaku.services.SubtitleService;
import jijimaku.services.Config;

/**
 * Container for all our services.
 */
public class ServiceConfig {

  private final Config config;
  private final JijiDictionary dictionary;
  private final JapaneseParser parser;
  private final SubtitleService subtitleService;

  public ServiceConfig(Config config, JijiDictionary dictionary, JapaneseParser parser, SubtitleService subtitleService) {
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

  public JapaneseParser getParser() {
    return parser;
  }

  public SubtitleService getSubtitleService() {
    return subtitleService;
  }
}
