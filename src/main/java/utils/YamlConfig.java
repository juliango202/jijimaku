package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import error.UnexpectedError;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;


public class YamlConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, Object> configMap = null;

	public YamlConfig(String configFilePath) {
        try {
            Yaml yaml = new Yaml();
            InputStream stream = FileManager.getUtf8Stream(new File(configFilePath));
            configMap = (Map<String, Object>) yaml.load(stream);
        }
        catch (IOException exc) {
          LOGGER.error("Problem reading YAML config {}", configFilePath);
          LOGGER.debug("Exception details", exc);
          throw new UnexpectedError();
        }
	}

    public boolean containsKey(String key) {
        return configMap.containsKey(key);
    }

    public Map<String, String> getStringDictionary(String configKey) {
        return (Map< String, String>)configMap.get(configKey);
    }

    public String getString(String key) {
        return (String) configMap.get(key);
    }

    public String getIgnoreWords() {
        return (String)configMap.get("ignoreWords");
    }


    public String getSubtitlesStyle() {
        if(configMap.containsKey("subtitlesStyles")) {
            return (String) configMap.get("subtitlesStyles");
        }
        else {
            return "[V4+ Styles]\n" +
                    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
                    "Style: Definition,Arial,6,&H009799af,&H00677f69,&H00000000,&H99000000,0,0,0,0,100,100,0,0,1,1,1,7,3,0,2,0\n" +
                    "Style: Default,Arial,28,16777215,16777215,0,2147483648,0,0,0,0,100,100,0,0,1,2,2,2,20,20,15,0";
        }
    }

}

