package jijimaku.services.langparser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jijimaku.AppConfig;
import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.utils.FileManager;

/**
 * Parse a Japanese sentence into words via the KUROMOJI(unidoct) library.
 */
public class LangParserKuromoji implements LangParser {
  private static final Logger LOGGER;

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  private static final String MISSING_FORM = "*";

  private static final List<String> PUNCTUATION_TOKENS = Arrays.asList(
      "｡", "…｡", "｢", "｣", "、", "（", "）", "."
  );

  private static final List<String> RENTAISHI_DET = Arrays.asList(
      "その", "どの", "この"
  );

  private static final List<String> NOUN_CONJUNCTIONS = Arrays.asList(
      "と", "か"
  );

  private Tokenizer tokenizer;

  public LangParserKuromoji(AppConfig config) {

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
      LOGGER.debug("Parsing Japanese language using the kuromoji-unidict library");
    } catch (NoClassDefFoundError exc) {
      LOGGER.error("Could not find the kuromoji parser classes. Please see the jijimaku "
          + "documentation concerning the Japanese language (you must download the kuromoji-unidic-0.9.0.jar "
          + "file and place it in the /lib directory.");
      throw new UnexpectedCriticalError();
    } catch (IOException exc) {
      LOGGER.debug(exc);
      LOGGER.error("Error while initializing the kuromoji Japanese parser");
      throw new UnexpectedCriticalError();
    }
  }

  /**
   * Return the universal dependency Part Of Speech tag for a given token.
   * The source for Japanese word feature to Universal Dependency mapping
   * is http://universaldependencies.org/ja/overview/morphology.html
   */
  private PosTag getTokenPosTag(Token previousToken, Token currentToken, String writtenForm) {
    // Get the word features from kuromoji (index 0 & 1 corresponds to japanese grammatical type & subtype)
    String[] features = currentToken.getAllFeaturesArray();
    String[] previousFeatures = previousToken == null ? null : previousToken.getAllFeaturesArray();

    // Strangely Kuromoji does not classify correctly some punctuation ?
    // Force punctuation characters to be classified as punctuation
    if (PUNCTUATION_TOKENS.contains(writtenForm)) {
      return PosTag.PUNCT;
    }

    switch (features[1]) {
      case "数詞":
        return PosTag.NUM;
      case "固有名詞":
        return PosTag.PROPN;
      case "副助詞":
      case "終助詞":
        return PosTag.PART;
      case "接続助詞":
      case "準体助詞":
        return PosTag.SCONJ;
      case "格助詞":
        return NOUN_CONJUNCTIONS.contains(writtenForm) ? PosTag.CCONJ : PosTag.ADP;
      case "普通名詞":
        return PosTag.NOUN;
      default:
        break;
    }

    switch (features[0]) {
      case "連体詞":
        return RENTAISHI_DET.contains(writtenForm) ? PosTag.DET : PosTag.ADJ;
      case "形容詞":
        if (features[1].equals("非自立可能") && previousFeatures != null
            && (previousFeatures[0].equals("形容詞") || previousFeatures[0].equals("形状詞"))) {
          return PosTag.AUX;
        }
        return PosTag.ADJ;
      case "形状詞":
        return PosTag.ADJ;
      case "副詞":
        return PosTag.ADV;
      case "感動詞":
        return PosTag.INTJ;
      case "接頭辞":
      case "接尾辞":
        return PosTag.NOUN;
      case "動詞":
        if (features[1].equals("非自立可能") && previousFeatures != null && previousFeatures[0].equals("動詞")) {
          return PosTag.AUX;
        }
        return PosTag.VERB;
      case "助動詞":
        return PosTag.AUX;
      case "接続詞":
        return PosTag.CCONJ;
      case "代名詞":
        return PosTag.PRON;
      case "補助記号":
        return PosTag.SYM;
      case "空白":
        return PosTag.X;

      default:
        return PosTag.UNKNOWN;
    }
  }

  /**
   * Use the kuromoji library to parse a text, and map the results to our custom TextToken class.
   */
  @Override
  public List<TextToken> syntaxicParse(String text) {
    // We use kuromoji-unidict as parsing dictionary (larger)
    // to use the default ipadic, replace the kuromoji JAR and use the following code instead:
    // Tokenizer tokenizer = Tokenizer.builder().mode(Mode.SEARCH).build(); then => token.getBaseForm()
    List<Token> kuroTokens = tokenizer.tokenize(text);
    return IntStream.range(0, kuroTokens.size()).mapToObj(idx -> {
      Token token = kuroTokens.get(idx);
      Token previousToken = idx == 0 ? null : kuroTokens.get(idx);
      String writtenForm = !token.getWrittenForm().equals(MISSING_FORM)
              ? token.getWrittenForm()
              : token.getSurface();
      String firstCanonicalForm = !token.getWrittenBaseForm().equals(MISSING_FORM)
          ? token.getWrittenBaseForm()
          : null;
      String secondCanonicalForm = !token.getLemma().equals(MISSING_FORM)
          ? token.getLemma()
          : null;
      PosTag pos = getTokenPosTag(previousToken, token, writtenForm);
      return new TextToken(pos, writtenForm, firstCanonicalForm, secondCanonicalForm);
    }).collect(Collectors.toList());
  }

  public Language getLanguage() {
    return Language.Japanese;
  }

  public Logger getLogger() {
    return LOGGER;
  }
}

