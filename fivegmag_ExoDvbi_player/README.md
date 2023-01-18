# 5G MAG - Media Client for 5G media broadband/broadcast

This project uses the Android ExoPlayer and the DVB-I Reference Client functionality to provide the capabilities to select and play
back media content.

## ExoPlayer

ExoPlayer is an application level media player for Android. It provides an
alternative to Android’s MediaPlayer API for playing audio and video both
locally and over the Internet. ExoPlayer supports features not currently
supported by Android’s MediaPlayer API, including DASH and SmoothStreaming
adaptive playbacks. Unlike the MediaPlayer API, ExoPlayer is easy to customize
and extend, and can be updated through Play Store application updates.

## DVB-I Reference Application

The DVB-I Reference Application project consists of a backend and frontend parts.
The backend allows generation and editing of DVB-I service lists.
A DVB-I backend is not an ingredient of this project but is expected to exist as an accessible service.
This project curently references the backend provided in scope of the DVB-I project:
http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/
The frontend referenced in this project is a DVB-I compatible PWA client for use with a browser.
https://stage.sofiadigital.fi/dvb/dvb-i-reference-application/frontend/android/player.html
This project is using Android WebView to display the DVB-I frontend to the user,
allowing the selection of available test streams.
WebView is part of the Android WebKit package. Required classes are listed below in the clause on dependencies.
The ExoPlayer instance within this project is called via WebView generated intents.

## License notes

Please note that (1)ExoPlayer, (2)DVB-I client front end, (3)and the code managing the DVB-I frontend in scope of ExoPlayer, are distributed under their individual license.
Please note that the ExoPlayer code referenced by this project is licensed under Apache 2.0 license,
using this project also requires including of Android WebView classes.
The DVB-I frontend referenced by this project uses the MIT license.
The code managing the DVB-I frontend in scope of ExoPlayer is contributed under 5G-MAG Public License (v1.0).# DVBI App

## How to build

Clone the repository, including submodules

```
git clone --recurse-submodules git@github.com:5G-MAG/rt-5gms-application.git
```

From the main project folder, run the provided python file to patch Exoplayer
```
cd rt-5gms-application\fivegmag_ExoDvbi_player
python3 replace.py
```

Enter the Exoplayer folder and build the project:
```
cd Exoplayer
gradlew assembleRelease
```

Find the built .apk files in the Exoplayer folder:
```
.\Exoplayer\demos\main\buildout\outputs\apk
```

Pick the desired .apk (Debug/Release version, with/without Decoder Extensions) and install on your target device via e.g. ADB
