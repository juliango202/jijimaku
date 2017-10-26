package jijimaku.langparser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;

import jijimaku.utils.YamlConfig;


//-----------------------------------------------------------------------
// Parse a Japanese sentence into words via the KUROMOJI(unidoct) library
//-----------------------------------------------------------------------

public class JapaneseParser implements LangParser {

  private Tokenizer tokenizer;

  public JapaneseParser(YamlConfig yamlConfig) {

    try {
      // Use YAML "properNouns" option to indicate a custom dict of proper nouns with their pronunciation
      // This is to help the parser recognize proper nouns in sentences
      if (yamlConfig.containsKey("properNouns")) {
        // Build custom user dict file in KUROMOJI format containing all the proper nouns
        // For infos on format see: https://github.com/elastic/elasticsearch-analysis-kuromoji#user-dictionary
        String properNounsDict = "";
        for (Map.Entry<String, String> wordTrad : yamlConfig.getStringDictionary("properNouns").entrySet()) {
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

  public List<TextToken> syntaxicParse(String text) {

    // We use kuromoji-unidoct as parsing dictionary (larger)
    // to use the default ipadic, replace the kuromoji JAR and use the following code instead:
    // Tokenizer tokenizer = Tokenizer.builder().mode(Mode.SEARCH).build();
    // token.getBaseForm()

    List<TextToken> tokens = new ArrayList<>();
    for (Token token : tokenizer.tokenize(text)) {

      if (!token.isKnown()) {
        // If the parser doesn't recognized the token, return it as unknown
        tokens.add(new TextToken(PosTag.UNKNOWN, token.getWrittenForm(), token.getWrittenForm()));
        continue;
      }

      // Get the word features from kuromoji (index 0 & 1 corresponds to grammatical type & subtype)
      String[] features = token.getAllFeaturesArray();

      // Try to identify token type from Kuromoji subtype first(more precise)
      PosTag tag;
      switch (features[1]) {
        case "数詞":
          tag = PosTag.NUMERAL;
          break;
        default:
          tag = PosTag.UNKNOWN;
      }
      if (tag == PosTag.UNKNOWN) {
        switch (features[0]) {
          case "記号":
            tag = PosTag.PUNCTUATION;
            break;
          case "助詞":
            tag = PosTag.PARTICLE;
            break;
          case "助動詞":
            tag = PosTag.AUXILIARY_VERB;
            break;
          case "接尾辞":
            tag = PosTag.SUFFIX;
            break;
          case "補助記号":
            tag = PosTag.SYMBOL;
            break;
          case "連体詞":
            tag = PosTag.DETERMINER;
            break;
          case "接続詞":
            tag = PosTag.CONJUNCTION;
            break;
          default:
            tag = PosTag.UNKNOWN;
        }
      }

      tokens.add(new TextToken(tag, token.getWrittenForm(), token.getWrittenBaseForm()));
    }
    return tokens;
  }
}

