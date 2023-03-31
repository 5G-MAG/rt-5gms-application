# 5G-MAG Reference Tools: 5GMSd Aware Application

This repository holds the 5GMSd Aware Application implementation of the 5G-MAG Reference Tools.

## Introduction

The 5GMSd Aware Application is an Android application that serves as a reference implementation for
5GMS downlink media streaming. It uses
the [Media Stream Handler](https://github.com/5G-MAG/rt-5gms-media-stream-handle) for playback and
communication with
the [Media Session Handler](https://github.com/5G-MAG/rt-5gms-media-session-handler).

The 5GMSd Aware Application is an application in the UE, provided by the 5GMS Application Provider,
that contains the service logic of the 5GMS application service, and interacts with other 5GMS
Client and Network functions via the interfaces and APIs defined in the 5GMS architecture.

## Downloading

Release versions can be downloaded from
the [releases](https://github.com/5G-MAG/rt-5gms-aware-application/releases) page.

The source can be obtained by cloning the github repository.

```
cd ~
git clone https://github.com/5G-MAG/rt-5gms-aware-application.git
```

## Building

Call the following command in order to generate the `apk` bundles.

````
./gradlew assemble
````

The resulting `apk` bundles can be found in `app/build/outputs/apk`. The debug build is located
in `debug` folder the release build in the `release` folder.

## Install

To install the `apk` on an Android device follow the following steps:

1. Connect your Android device to your development machine
2. Call `adb devices` to list the available Android devices. The output should look like the
   following:

````
List of devices attached
CQ30022U4R	device
````

3. Install the `apk` on the target
   device: `adb -s <deviceID> install -r app/build/outputs/apk/release/app-debug.apk`. Using `-r`
   we reinstall an existing app, keeping its data.

## Running

After installing the 5GMSd Aware Application it can be started from the Android app selection
screen.

As an alternative we can also run the app from the command
line: `adb shell am start -n com.fivegmag.a5gmsdawareapplication/com.fivegmag.a5gmsdawareapplication.MainActivity `

## Configuration

The 5GMSd Aware Application ships with a configuration file for the M8 interface and two static
example M8 configuration JSON files.

### M8 endpoint configuration

The base URL to the hosted M8 endpoint and the two local M8 example files can be configured
in `src/main/assets/config.properties`:

```` 
m8StaticSingleJsonUrl = m8/config_single_media.json
m8StaticMultiJsonUrl = m8/config_multi_media.json
m8HostingEndpoint = https://rt.5g-mag.com/
````

### Local M8 examples

The two local M8 example files are located in  `src/main/assets/m8`.

`config_single_media.js` contains unique a `provisioningSessionId` for each asset and requires the
ApplicationFunction to provide valid `mediaPlayerEntry` values for playback.

`config_multi_media.js` uses the same `provisioningSessionId` for multiple assets which is why
the `mediaPlayerEntry` values are directly provided.

For more information about the different M8 formats refer to the [Github discussion](https://github.com/5G-MAG/rt-5gms-application/discussions/6)

## Development

This project follows
the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
. The `development`
branch of this project serves as an integration branch for new features. Consequently, please make
sure to switch to the `development`
branch before starting the implementation of a new feature. 