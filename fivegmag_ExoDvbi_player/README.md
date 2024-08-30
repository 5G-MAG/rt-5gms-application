<h1 align="center">Media Client for 5G Media Streaming / 5G Broadcast</h1>
<p align="center">
  <img src="https://img.shields.io/badge/Status-Under_Development-yellow" alt="Under Development">
  <img src="https://img.shields.io/github/v/tag/5G-MAG/rt-5gms-application-provider?label=version" alt="Version">
  <img src="https://img.shields.io/badge/License-5G--MAG%20Public%20License%20(v1.0)-blue" alt="License">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

## Introduction
This project uses the Android ExoPlayer and the DVB-I Reference Client functionality to provide the capabilities to select and play
back media content.

### ExoPlayer

ExoPlayer is an application level media player for Android. It provides an
alternative to Android’s MediaPlayer API for playing audio and video both
locally and over the Internet. ExoPlayer supports features not currently
supported by Android’s MediaPlayer API, including DASH and SmoothStreaming
adaptive playbacks.

Unlike the MediaPlayer API, ExoPlayer is easy to customize
and extend, and can be updated through Play Store application updates.

### DVB-I Reference Application

The DVB-I Reference Application project consists of a backend and frontend parts:
* The backend allows generation and editing of DVB-I service lists. A DVB-I backend is not an ingredient of this project but is expected to exist as an accessible service. This project currently references the backend provided in scope of the DVB-I project: http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/
* The frontend referenced in this project is a DVB-I compatible PWA client for use with a browser: https://stage.sofiadigital.fi/dvb/dvb-i-reference-application/frontend/android/player.html. This project is using Android WebView to display the DVB-I frontend to the user, allowing the selection of available test streams. WebView is part of the Android WebKit package. Required classes are listed below in the clause on dependencies. The ExoPlayer instance within this project is called via WebView generated intents.

## Building

Clone the repository, including submodules

```
git clone --recurse-submodules https://github.com/5G-MAG/rt-5gms-application.git
```

Build the patched Exoplayer project:
```
cd ~/rt-5gms-application/fivegmag_ExoDvbi_player/ExoPlayer
./gradlew assembleRelease
```

Copy the patch available inside the ```patch``` folder into the Exoplayer project, then apply it and rebuild:
```
cp ~/rt-5gms-application/fivegmag_ExoDvbi_player/patch/Exoplayer.patch ~/rt-5gms-application/fivegmag_ExoDvbi_player/ExoPlayer/Exoplayer.patch
cd ~/rt-5gms-application/fivegmag_ExoDvbi_player/ExoPlayer
git apply Exoplayer.patch
./gradlew assembleRelease
```

Find the built .apk files in the Exoplayer folder:
```
cd ~/rt-5gms-application/fivegmag_ExoDvbi_player/ExoPlayer/demos/main/buildout/outputs/apk
```

Pick the desired .apk (Debug/Release version, with/without Decoder Extensions) and install on your target device via e.g. ADB

In case of an error ```SDK location not found```, add a ```local.properties``` file to the Exoplayer directory specifying the path to the Android SDK. For instance, on a MAC: 
```
sdk.dir=/Users/<username>/Library/Android/sdk
```
## Installing

To install the APK on a device run:  
```
adb -s <deviceID> install -r noDecoderExtensionsDvbiapp/release/demo-noDecoderExtensions-dvbiapp-release.apk
```

## License

Please note that (1)ExoPlayer, (2)DVB-I client front end, (3)and the code managing the DVB-I frontend in scope of ExoPlayer, are distributed under their individual license.
Please note that the ExoPlayer code referenced by this project is licensed under Apache 2.0 license,
using this project also requires including of Android WebView classes.
The DVB-I frontend referenced by this project uses the MIT license.
The code managing the DVB-I frontend in scope of ExoPlayer is contributed under 5G-MAG Public License (v1.0).# DVBI App
