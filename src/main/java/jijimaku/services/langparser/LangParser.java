package jijimaku.services.langparser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import jijimaku.errors.UnexpectedCriticalError;

/**
 * Parse a sentence into grammatical words.
 * This interface can be implemented by different classes to parse different languages
 */
public interface LangParser {

  @SuppressWarnings("unused")
  enum Language {
    Ancient_Greek,
    Arabic,
    Basque,
    Belarusian,
    Bulgarian,
    Catalan,
    Chinese,
    Coptic,
    Croatian,
    Czech,
    Danish,
    Dutch,
    English,
    Estonian,
    Finnish,
    French,
    Galician,
    German,
    Gothic,
    Greek,
    Hebrew,
    Hindi,
    Hungarian,
    Indonesian,
    Irish,
    Italian,
    Japanese,
    Kazakh,
    Korean,
    Latin,
    Latvian,
    Lithuanian,
    Norwegian,
    Old_Church_Slavonic,
    Persian,
    Polish,
    Portuguese,
    Romanian,
    Russian,
    Sanskrit,
    Slovak,
    Slovenian,
    Spanish,
    Swedish,
    Tamil,
    Turkish,
    Ukrainian,
    Urdu,
    Uyghur,
    Vietnamese
  }

  // Part Of Speech universal tags
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
    private final String firstCanonicalForm; // canonical/base form of a word, e.g. infinitive for verbs, etc.. (used in dictionary look-ups)
    private final String secondCanonicalForm; // canonical/base form of a word, e.g. infinitive for verbs, etc.. (used in dictionary look-ups)

    public TextToken(PosTag posTag, String textForm, String firstCanonicalForm, String secondCanonicalForm) {
      if (textForm == null || textForm.isEmpty()) {
        throw new IllegalArgumentException("Cannot create a TextToken from an empty string.");
      }
      this.posTag = posTag;
      this.textForm = textForm;
      this.firstCanonicalForm = firstCanonicalForm != null && !firstCanonicalForm.isEmpty()
          ? firstCanonicalForm
          : textForm.toLowerCase();
      this.secondCanonicalForm = secondCanonicalForm != null && !secondCanonicalForm.isEmpty()
          ? secondCanonicalForm
          : textForm.toLowerCase();
    }

    public PosTag getPartOfSpeech() {
      return posTag;
    }

    public String getTextForm() {
      return textForm;
    }

    public String getFirstCanonicalForm() {
      return firstCanonicalForm;
    }

    public String getSecondCanonicalForm() {
      return secondCanonicalForm;
    }
  }


  default List<TextToken> parse(String text) {
    List<TextToken> tokens = this.syntaxicParse(text);

    // Log details on how the text was parsed for debugging
    String parsedTokens = tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining("|"));
    getLogger().debug("original: " + text);
    getLogger().debug("parsed: " + parsedTokens);

    String parsingInfo = tokens.stream().map(textToken -> textToken.getTextForm() + "\t"
        + (textToken.getFirstCanonicalForm().equals(textToken.getTextForm()) ? "-" : textToken.getFirstCanonicalForm()) + "\t"
        + (textToken.getSecondCanonicalForm().equals(textToken.getTextForm()) ? "-" : textToken.getSecondCanonicalForm()) + "\t"
        + textToken.getPartOfSpeech()).collect(Collectors.joining("\n"));

    getLogger().debug("parsing info: \n" + parsingInfo);
    return tokens;
  }

  default String getWordSeparator() {
    List<Language> languagesWithoutSpaces = Arrays.asList(
        Language.Japanese, Language.Chinese, Language.Vietnamese
    );
    return languagesWithoutSpaces.contains(getLanguage()) ? "" : " ";
  }

  default Language getLanguageFromStr(String languageStr) {
    try {
      return Language.valueOf(languageStr);
    } catch (IllegalArgumentException exc) {
      getLogger().debug(exc);
      getLogger().error("Unsupported language '{}', should be one of: {}",
          languageStr,
          Stream.of(Language.values()).map(Enum::toString).collect(Collectors.joining(", ")));
      throw new UnexpectedCriticalError();
    }
  }

  // Returned the language supported by the parser
  Language getLanguage();

  Logger getLogger();

  // Ideally this should be private but private interface methods are only supported in Java 9
  List<TextToken> syntaxicParse(String text);
}

