package jijimaku;

/**
 * Created by julian on 11/6/17.
 */
class AppConst {
  static final String APP_TITLE = "Jijimaku Subtitles Dictionary";

  static final String APP_DESC = "This program reads subtitle files in the chosen directory tree and add the "
      + "dictionary definitions for the words encountered. \n\n"
      + "The result is a subtitle file(format ASS) that can be used for language learning: subtitles appears at the bottom "
      + "and words definitions at the top.\n\n"
      + "See config.yaml for options.\n";

  //-----------------------------------------------------
  static final String CONFIG_FILE = "config.yaml";

  //static final String OUTPUT_SUB_EXT = "ass";

  // For now only SRT subtitles are supported
  // but it should be fairly easy to add other formats when needed
  static final String[] VALID_SUBFILE_EXT = {"srt","ass"};
}
