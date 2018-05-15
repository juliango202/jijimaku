package jijimaku.services.langparser;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


// Parse a sentence into grammatical words
// This interface can be implemented by different classes to parse different languages
public interface LangParser {

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

  List<Language> LANGUAGES_WITHOUT_SPACES = Arrays.asList(
      Language.Japanese, Language.Chinese, Language.Vietnamese
  );

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

  /**
   * Filtering pass to merge some SCONJ with the previous VERBS/AUX in Japanese
   * This is so that for example 継ぎ-まし-て appears as one word in the subtitles
   * TODO: see to move this in the word highlight step only
   */
  List<String> PART_OF_VERB_CONJUNCTIONS = Arrays.asList(
      "て", "で", "ちゃ"
  );
  default List<TextToken> mergeJapaneseVerbs(List<TextToken> tokens) {
    List<TextToken> filteredTokens = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      TextToken token = tokens.get(i);
      TextToken lastOk = filteredTokens.isEmpty() ? null : filteredTokens.get(filteredTokens.size() - 1);
      boolean isPartOfVerbConj = (token.getPartOfSpeech() == LangParser.PosTag.SCONJ && PART_OF_VERB_CONJUNCTIONS.contains(token.getTextForm()));
      if (lastOk != null
          && (lastOk.getPartOfSpeech() == LangParser.PosTag.AUX || lastOk.getPartOfSpeech() == LangParser.PosTag.VERB)
          && isPartOfVerbConj) {
        TextToken completeVerb = new TextToken(lastOk.getPartOfSpeech(), lastOk.getTextForm() + token.getTextForm(),
            lastOk.getFirstCanonicalForm(), lastOk.getSecondCanonicalForm());
        filteredTokens.set(filteredTokens.size() - 1, completeVerb);
        continue;
      }
      filteredTokens.add(token);
    }
    return filteredTokens;
  }


  default List<TextToken> parse(String text) {
    List<TextToken> tokens = this.syntaxicParse(text);
    if (getLanguage() == Language.Japanese) {
      tokens = mergeJapaneseVerbs(tokens);
    }

    // Log details on how the text was parsed for debugging
    String parsedTokens = tokens.stream().map(TextToken::getTextForm).collect(Collectors.joining ("|"));
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
    return LANGUAGES_WITHOUT_SPACES.contains(getLanguage()) ? "" : " ";
  }

  // Returned the language supported by the parser
  Language getLanguage();

  Logger getLogger();

  // Ideally this should be private but private interface methods are only supported in Java 9
  List<TextToken> syntaxicParse(String text);
}

