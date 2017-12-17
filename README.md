Jijimaku is a Java application for inserting dictionary definition inside video subtitle files(*.srt, *.ass). It can be used to improve one's language skill when watching videos in a foreign language.

**Currently in beta, only Japanese subtitles are supported.**

![Snap 1](https://juliango202.github.io/img/jijimaku/snap1.jpg)

## Installation
To install, get the [latest release](https://github.com/juliango202/jijimaku/releases), unzip in some directory and run the Jar file(Java 8 is required). See config.yaml for configuration options.

## Why Jijimaku?
Past a certain level, there is no better way to learn a language than going full native, i.e. speaking with native speakers, 
reading the books and watching the video aimed at native speakers.

The only thing you need is a tool to pickup new words as you go. When speaking to native people you can just ask them to explain the word,
and when reading some material a pop-up dictionary works great(that would be rikaichan for Japanese).

But for video the choice is more limited. 
There are some all-in-one video players or websites where in addition to the video frame there is
a special window for analysis and information about the subtitles, for example word definitions and additional examples.
While this can work quite well, unfortunately it involves various constrains: you must use a laptop to watch the video, 
sometimes with a specific O.S., it's difficult to watch the video with other people, for online apps the choice of video is severely restricted, etc...

Jijimaku takes another approach, the video itself **is** the interface, word definitions are overlayed via the subtitles, and interaction is deliberately minimalist: press pause if you need to get more time to read the definition, then press resume, and that's it. 

This has the big advantage to make Jijimaku as universal as video playing: use it with any O.S., on smartphone, on TV, etc... It also minimizes distractions so that users can really focus on the video.

## Ignore words that you already know
In the same spirit of minimizing distraction, it is best to make Jijimaku ignores the words that you already know or don't need defined.
Currently two options are offered for this:
- "ignoreFrequencies" when the frequency information is available, this let you ignore words that are frequent enough so that you expect to know them already
- "ignoreWords" is a list of words that can be safely ignored because you already know them (could be an export from anki/SRS application)

Once installed, see config.yaml for more information on these options.

## TODO:
- lemmatizer library for other languages support: https://bitbucket.org/hlavki/jlemmagen/

word list in google syntaxnet models could help
