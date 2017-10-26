package jijimaku.langparser;

import java.util.List;


// Parse a sentence into grammatical words
// This interface can be implemented by different classes to parse different languages
public interface LangParser {

  // Part Of Speech universal tags
  // TODO: use universal Dependencies tags
  enum PosTag {
    PUNCTUATION,
    SYMBOL,
    NUMERAL,
    PARTICLE,
    DETERMINER,     // a modifying word that determines the kind of reference a noun or noun group has, for example a, the, every.
    CONJUNCTION,
    AUXILIARY_VERB,
    SUFFIX,
    //ADJECTIVE,
    //NOUN,
    UNKNOWN
  }

  class TextToken {

    public PosTag tag;
    public String currentForm;      // as it appears in the parsed sentence
    public String canonicalForm;    // canonical/base form of a word, e.g. infinitive for verbs, etc.. (used in dictionary look-ups)

    TextToken(PosTag t, String currf, String canonf) {
      tag = t;
      currentForm = currf;
      canonicalForm = canonf;
    }
  }

  List<TextToken> syntaxicParse(String text);
}

