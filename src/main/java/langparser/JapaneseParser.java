package langparser;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;
import utils.YamlConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;


//-----------------------------------------------------------------------
// Parse a Japanese sentence into words via the KUROMOJI(unidoct) library
//-----------------------------------------------------------------------

public class JapaneseParser implements LangParser {

    private Tokenizer tokenizer;

    public JapaneseParser(YamlConfig yamlConfig) {

        try {
            // Use YAML "properNouns" option to indicate a custom dict of proper nouns with their pronunciation
            // This is to help the parser recognize proper nouns in sentences
            if( yamlConfig.containsKey("properNouns") ) {
                // Build custom user dict file in KUROMOJI format containing all the proper nouns
                // For infos on format see: https://github.com/elastic/elasticsearch-analysis-kuromoji#user-dictionary
                String properNounsDict = "";
                for (Map.Entry<String, String> wEnt : yamlConfig.getStringDictionary("properNouns").entrySet()) {
                    properNounsDict += wEnt.getKey() + "," + wEnt.getKey() + "," + wEnt.getValue() + ",カスタム名詞\n";
                }
                ByteArrayInputStream properNounsStream = new ByteArrayInputStream(properNounsDict.getBytes("UTF-8"));
                tokenizer = new Tokenizer.Builder().userDictionary(properNounsStream).build();
            }
            else {
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

            if(!token.isKnown()) {
                // If the parser doesn't recognized the token, return it as unknown
                tokens.add(new TextToken(POSTag.UNKNOWN,token.getWrittenForm(),token.getWrittenForm()));
                continue;
            }

            // Get the word features from kuromoji (index 0 & 1 corresponds to grammatical type & subtype)
            String[] features = token.getAllFeaturesArray();
            POSTag tag = POSTag.UNKNOWN;

            // Try to identify token type from Kuromoji subtype first(more precise)
            switch (features[1]) {
                case "数詞":  tag = POSTag.NUMERAL;  break;
            }
            if(tag == POSTag.UNKNOWN) {
                switch (features[0]) {
                    case "記号":       tag = POSTag.PUNCTUATION;break;
                    case "助詞":       tag = POSTag.PARTICLE;break;
                    case "助動詞":     tag = POSTag.AUXILIARY_VERB;break;
                    case "接尾辞":     tag = POSTag.SUFFIX;break;
                    case "補助記号":   tag = POSTag.SYMBOL;break;
                    case "連体詞":     tag = POSTag.DETERMINER;break;
                    case "接続詞":     tag = POSTag.CONJUNCTION;break;
                }
            }

            tokens.add(new TextToken(tag,token.getWrittenForm(),token.getWrittenBaseForm()));
        }
        return tokens;
    }
}

