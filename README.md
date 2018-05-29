Jijimaku is a Java application for inserting dictionary definitions inside video subtitle files(*.srt, *.ass). It's a tool for people who want to improve their foreign language skill by watching videos.

**Currently in beta, only Japanese subtitles are supported.**

![Snap 1](https://juliango202.github.io/img/jijimaku/snap1.jpg)

## Installation
To install, get the [latest release](https://github.com/juliango202/jijimaku/releases), unzip in some directory and run the Jar file(Java 8 is required). See config.yaml for configuration options.

## Why Jijimaku?
Past a certain level, there is no better way to learn a language than going full native, i.e. speaking with native speakers,
reading the books and watching the video aimed at native speakers.

The only thing you need is a tool to pickup new words as you go. When speaking to native people you can just ask them to explain the word,
and when reading some material a pop-up dictionary works great(for example rikaichan add-on for Japanese).

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
- "ignoreTags" is a list of tags to ignore. Entries in a jiji dictionary can be tagged, for example the tags freq01 to freq12 can be used to represent the frequency of the entry. This option let you ignore words that are frequent enough that you expect to know them already
- "ignoreWords" is a list of words that can be safely ignored because you already know them (could be an export from anki/SRS application). In the future a great addition would be a video player plugin that lets you add to this list the defined words you already know by just pressing some key(see TODO).

Once installed, see config.yaml for more information on these options.

## TODO:
- video player plugin(VLC/mpv.io lua script?) that let you select among the defined words those you already know(by pressing a key) and add them automatically to the ignoreWords list. This would allow Jijimaku to match precisely a user true level after a few weeks of usage.
- lemmatizer library for other languages support: https://bitbucket.org/hlavki/jlemmagen/.
  Word list in google syntaxnet models could help
