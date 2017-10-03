package langparser;

import java.util.*;


// Parse a sentence into grammatical words
// This interface can be implemented by different classes to parse different languages
public interface LangParser {

    // Part Of Speech universal tags
    // TODO: use universal Dependencies tags
    enum POSTag {
        PUNCTUATION,
        SYMBOL,
        NUMERAL,
        PARTICLE,
        DETERMINER,     // a modifying word that determines the kind of reference a noun or noun group has, for example a, the, every.
        CONJUNCTION,
        AUXILIARY_VERB,
        SUFFIX,
        ADJECTIVE,
        NOUN,
        UNKNOWN
    }

    class TextToken {

        public POSTag tag;
        public String currentForm;      // as it appears in the parsed sentence
        public String canonicalForm;    // canonical/base form of a word, e.g. infinitive for verbs, etc.. (used in dictionary look-ups)

        public TextToken(POSTag t, String currf, String canonf) {
            tag = t;
            currentForm =  currf;
            canonicalForm = canonf;
        }
    }

    List<TextToken> syntaxicParse(String text);
}

