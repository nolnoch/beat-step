CS 371M Beta Release - Beat to the Step

Wade Burch (nolnoch)


*** Overview ***


1. Features
    - Scans SD card for all audio tracks
    - Analyzes track for BPM count
    - Allows setting of simulated SPM (steps per minute)
    - Changes playback rate to match SPM
    - *Can* correct pitch on supporting phones
    - *Can* detect step rate on support phones
    - *Can* seek through track (back/forth) on supporting phones

2. Incomplete
    - No playlist creation
    - Not quite polished UI in my opinion
    - Does not manually cache results of analysis.

3. Added features
    - The sliding bar for SPM change is a new idea, courtesy of Mike

4. External sources
    - EchoNest API for song analysis
    - Android NDK example for basic song playback

5. Internal sources
    - Everything except the included EchoNest .jar and NDK sample functions
    - But I rewrote the NDK sample function to decode MP3 to a raw PCM data
      buffer, then wrote it to play back from that.  I then added the playback
      rate change code.

(B)
