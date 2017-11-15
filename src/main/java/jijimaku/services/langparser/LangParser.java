package jijimaku.services.langparser;

import java.util.List;


// Parse a sentence into grammatical words
// This interface can be implemented by different classes to parse different languages
public interface LangParser {

  // Part Of Speech universal tags
  // TODO: use universal Dependencies tags: http://universaldependencies.org/ja/overview/morphology.html
  // See http://universaldependencies.org/u/pos/all.html
  enum PosTag {
    ADJ,    // adjective
    ADV,    // adverb
    INTJ,   // interjection
    NOUN,   // noun
    PROPN,  // proper noun
    VERB,   // verb
    ADP,    // adposition
    AUX,    // auxiliary
    CCONJ,  // coordinating conjunction
    DET,    // determiner
    NUM,    // numeral
    PART,   // particle
    PRON,   // pronoun
    SCONJ,  // subordinating conjunction
    PUNCT,  // punctuation
    SYM,    // symbol
    X,      // other
    UNKNOWN
  }

  class TextToken {

    private final PosTag posTag;
    private final String textForm;      // as it appears in the parsed sentence
    private final String canonicalForm; // canonical/base form of a word, e.g. infinitive for verbs, etc.. (used in dictionary look-ups)

    TextToken(PosTag posTag, String textForm, String canonicalForm) {
      if (textForm == null || textForm.isEmpty()) {
        throw new IllegalArgumentException("Cannot create a TextToken from an empty string.");
      }
      this.posTag = posTag;
      this.textForm = textForm;
      this.canonicalForm = canonicalForm != null && !canonicalForm.isEmpty()
          ? canonicalForm
          : textForm;
    }

    public PosTag getPartOfSpeech() {
      return posTag;
    }

    public String getTextForm() {
      return textForm;
    }

    public String getCanonicalForm() {
      return canonicalForm;
    }
  }

  List<TextToken> syntaxicParse(String text);
}

