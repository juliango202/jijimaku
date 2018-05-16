package jijimaku.services.langrules;

import jijimaku.models.DictionaryMatch;

/**
 * Languages specific rules used when annotating subtitles.
 */
public interface LangRules {

  /**
   * Return true if a DictionaryMatch is considered valid for this language.
   */
  boolean isValidMatch(DictionaryMatch match);

  /**
   * Return true if a DictionaryMatch should be ignored for this language.
   */
  boolean isIgnoredMatch(DictionaryMatch match);
}
