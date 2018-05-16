package jijimaku.services.langrules;

import java.util.List;

import jijimaku.models.DictionaryMatch;
import jijimaku.services.langparser.LangParser.TextToken;

/**
 * Languages specific rules used when annotating subtitles.
 */
public interface LangRules {

  /**
   * Filter tokens before searching for a dictionary match.
   */
  List<TextToken> filterTokens(List<TextToken> tokens);

  /**
   * Return true if a DictionaryMatch is considered valid for this language.
   */
  boolean isValidMatch(DictionaryMatch match);

  /**
   * Return true if a DictionaryMatch should be ignored for this language.
   */
  boolean isIgnoredMatch(DictionaryMatch match);
}
