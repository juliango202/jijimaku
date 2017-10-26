package jijimaku.models;

import java.util.ArrayList;
import java.util.List;


public class SubtitlesCollection {
  public List<String> canBeAnnotated;   // List of subtitles files that we can process

  public SubtitlesCollection() {
    canBeAnnotated = new ArrayList<>();
  }

  public boolean isEmpty() {
    return canBeAnnotated.size() == 0;
  }
}
