CS 371M Alpha Release

Name1: Wade Burch
EID:   wab256
CSID:  nolnoch
Email: nolnoch@cs.utexas.edu

Name2: Nathan Zuiker
EID:   <unknown>
CSID:  <unknown>
Email: <unknown>


Current Functionality:

  The pause button on the main screen will start and stop a demo song in the
  application's assets.  The menu options are mostly functional as well.

Non-apparent Features:

  On a capable phone, the app will keep a running count of the steps the user
  has taken and calculate a Steps Per Minute (SPM) value that updates every
  four seconds.
  
  The fact that the music plays is due to the underlying NDK native C library
  exposing low-level OpenSL ES audio functions to the Android layer.  While
  this is not necessary to play music, it is necessary to alter the music.

Works in Progress:

  The external library, EchoNest, has been fully implemented to lookup songs
  and retrieve the BPM along with other profiling data (length, genre, etc).
  As of yet, I have not yet gotten the external JAR to be recognized at
  compile time despite it being fully available to Eclipse in auto-completing
  the API calls.
  
  The Playlist organizer is currently under development.

Dropped Features:

  None.

External Sources:

  Android NDK NativeAudio Sample
  https://developer.android.com/tools/sdk/ndk/index.html

  EchoNest API
  http://echonest.com/

Original Sources:

  Everything Java and XML you see has been coded by hand. The native C
  code in the 'jni' folder is a modified version of the NativeAudio library
  from Android's NDK tutorial.  I have so far added the PlaybackrateItf
  functions, but I don't have a phone capable of testing them yet.
