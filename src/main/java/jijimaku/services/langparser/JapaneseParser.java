package jijimaku.services.langparser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;

import jijimaku.services.Config;


//-----------------------------------------------------------------------
// Parse a Japanese sentence into words via the KUROMOJI(unidoct) library
//-----------------------------------------------------------------------

public class JapaneseParser implements LangParser {
  private static final String MISSING_FORM = "*";

  private static final List<String> PUNCTUATION_TOKENS = Arrays.asList(
      "｡", "…｡", "｢", "｣", "（", "）"
  );

  private static final List<String> RENTAISHI_DET = Arrays.asList(
          "その", "どの", "この"
  );

  private Tokenizer tokenizer;

  public JapaneseParser(Config config) {

    try {
      // Use YAML "properNouns" option to indicate a custom dict of proper nouns with their pronunciation
      // This is to help the parser recognize proper nouns in sentences
      Map<String,String> properNouns = config.getProperNouns();
      if (properNouns != null) {
        // Build custom user dict file in KUROMOJI format containing all the proper nouns
        // For infos on format see: https://github.com/elastic/elasticsearch-analysis-kuromoji#user-dictionary
        String properNounsDict = "";
        for (Map.Entry<String, String> wordTrad : properNouns.entrySet()) {
          properNounsDict += wordTrad.getKey() + "," + wordTrad.getKey() + "," + wordTrad.getValue() + ",カスタム名詞\n";
        }
        ByteArrayInputStream properNounsStream = new ByteArrayInputStream(properNounsDict.getBytes("UTF-8"));
        tokenizer = new Tokenizer.Builder().userDictionary(properNounsStream).build();
      } else {
        tokenizer = new Tokenizer.Builder().build();
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Return the universal dependency Part Of Speech tag for a given token.
   * The source for Japanese word feature to Universal Dependency mapping
   * is http://universaldependencies.org/ja/overview/morphology.html
   */
  private PosTag getTokenPosTag(Token token, String writtenForm) {
    // Get the word features from kuromoji (index 0 & 1 corresponds to japanese grammatical type & subtype)
    String[] features = token.getAllFeaturesArray();

    // Strangely Kuromoji does not classify correctly some punctuation ?
    // Force punctuation characters to be classified as punctuation
    if (PUNCTUATION_TOKENS.contains(writtenForm)) {
      return PosTag.PUNCT;
    }

    switch (features[1]) {
      case "数詞":
        return PosTag.NUM;
    }

    switch (features[0]) {
      case "連体詞":
        return RENTAISHI_DET.contains(writtenForm) ? PosTag.DET : PosTag.ADJ;
      case "形容詞":
      case "形状詞":
        return PosTag.ADJ;
      case "副詞":
        return PosTag.ADV;
      case "感動詞":
        return PosTag.INTJ;

      default:
        return PosTag.UNKNOWN;
    }
  }

  public List<TextToken> syntaxicParse(String text) {

    // We use kuromoji-unidoct as parsing dictionary (larger)
    // to use the default ipadic, replace the kuromoji JAR and use the following code instead:
    // Tokenizer tokenizer = Tokenizer.builder().mode(Mode.SEARCH).build(); then => token.getBaseForm()

    return tokenizer.tokenize(text).stream().map(token -> {
      // Map Kuromoji results to our custom TextToken class
      String writtenForm = !token.getWrittenForm().equals(MISSING_FORM)
              ? token.getWrittenForm()
              : token.getSurface();
      String writtenBaseForm = !token.getWrittenBaseForm().equals(MISSING_FORM)
              ? token.getWrittenBaseForm()
              : null;
      PosTag pos = getTokenPosTag(token, writtenForm);
      return new TextToken(pos, writtenForm, writtenBaseForm);
    }).collect(Collectors.toList());
  }
}

