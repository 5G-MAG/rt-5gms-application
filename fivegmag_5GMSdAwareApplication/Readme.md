<h1 align="center">5GMSd-Aware Application</h1>
<p align="center">
  <img src="https://img.shields.io/badge/Status-Under_Development-yellow" alt="Under Development">
  <img src="https://img.shields.io/github/v/tag/5G-MAG/rt-5gms-application?label=version" alt="Version">
  <img src="https://img.shields.io/badge/License-5G--MAG%20Public%20License%20(v1.0)-blue" alt="License">
</p>

## Introduction

The 5GMSd-Aware Application is an application in the UE, provided by the 5GMSd Application Provider,
that contains the service logic of the 5GMSd application service, and interacts with other 5GMSd
Client and Network functions via the interfaces and APIs defined in the 5GMSd architecture.
The 5GMSd-Aware Application controls the Media Session Handler via a UE-internal API defined at
reference point M6d. This reference point could, for example, be realized as a JavaScript API in a
web browser or via Inter Process Communication (IPC) for native Android applications. The
5GMSd-Aware Application controls the Media Player via a UE-internal API defined at reference point
M7.

Additional information can be found at: https://5g-mag.github.io/Getting-Started/pages/5g-media-streaming/

### About the implementation

The 5GMSd-Aware Application is an Android application that serves as a reference implementation for
5G Downlink Media Streaming. It uses
the [Media Stream Handler](https://github.com/5G-MAG/rt-5gms-media-stream-handle) for playback and
communication with
the [Media Session Handler](https://github.com/5G-MAG/rt-5gms-media-session-handler).

## Downloading

Release versions can be downloaded from
the [releases](https://github.com/5G-MAG/rt-5gms-application/releases) page.

The source can be obtained by cloning the github repository.

```
cd ~
git clone https://github.com/5G-MAG/rt-5gms-application.git
```

## Install dependencies

The 5GMSd-Aware Application requires
the [Common Android Library](https://github.com/5G-MAG/rt-5gms-common-android-library) and
the [Media Stream Handler](https://github.com/5G-MAG/rt-5gms-media-stream-handler) to run.

Both are included as Maven dependencies in the `build.gradle`:

````
dependencies {
   implementation 'com.fivegmag:a5gmscommonlibrary:1.0.0'
   implementation 'com.fivegmag:a5gmsmediastreamhandler:1.0.0'
}
````

Note that the version number (in the example above it is set to `1.0.0`) might differ depending on
the version of the 5GMS Aware Application.

To install these two dependencies follow the corresponding installation guides in the Readme
documentation of both projects. Make sure to publish both of them to a local Maven repository:

* [Common Android Library](https://github.com/5G-MAG/rt-5gms-common-android-library#publish-to-local-maven-repository)
* [Media Stream Handler](https://github.com/5G-MAG/rt-5gms-media-stream-handler#publish-to-local-maven-repository)

## Building

Call the following command in order to generate the `apk` bundles.

````
cd fivegmag_5GMSdAwareApplication/
./gradlew assemble
````

The resulting `apk` bundles can be found in `fivegmag_5GMSdAwareApplication/app/build/outputs/apk`.
The debug build is located in `debug` folder the release build in the `release` folder.

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
   device: `adb -s <deviceID> install -r app/build/outputs/apk/debug/app-debug.apk`. Using `-r`
   we reinstall an existing app, keeping its data. In the example above replace `<deviceId>`
   with `CQ30022U4R`.

## Running

After installing the 5GMSd-Aware Application it can be started from the Android app selection
screen.

As an alternative, the app can also be run from the command
line: `adb shell am start -n com.fivegmag.a5gmsdawareapplication/com.fivegmag.a5gmsdawareapplication.MainActivity `

## Configuration

The 5GMSd-Aware Application is configured with a set of JSON-based configurations - M8
configurations - that
populate its user interface with content selections. Each M8 configuration corresponds to a
different content
catalogue and is described in a JSON document which may either point to a local file distributed
with the `apk` or
to URLs on an external M8 server. The items listed in the M8 configuration are used to populate the
lower menu in
the user interface of the 5GMSd-Aware Applicaion and allow selection of individual content items by
the user.

The list of M8 configurations is configured in an XML file distributed with the `apk`. These are
used to populate
the upper menu in the user interface of the 5GMSd-Aware Application and allow the user to switch
between
different available content catalogues.

The 5GMSd-Aware Application ships with an M8 configuration file and two static example M8
configuration JSON files.

The format of the M8 configuration is not standardised by 3GPP, and so the following is provided as
an example
only. A real 5GMSd-Aware Application is expected to communicate with its own back-end content
catalogue at
reference point M8 using an application-specific format.

### M8 endpoint configuration

The URL to the M8 endpoints can be configured in `src/main/assets/config.properties`:

```` 
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
   <entry key="m8LocalSingleMedia">m8/config_single_media.json</entry>
   <entry key="m8LocalMultiMedia">m8/config_multi_media.json</entry>
   <entry key="m85GMAGHost">https://rt.5g-mag.com/</entry>
   <entry key="m8LocalDummyHost">http://10.147.67.179:3003/m8/</entry>
</properties>
````

Each entry needs to have a unique `key` that is used to populate the selection spinner in the Main
Acitivity. The
URL to the to the M8 JSON file can either be relative pointing to a local file in the `assets`
folder or absolute pointing to a server side endpoint.

### Local M8 examples

The two local M8 example files are located in  `src/main/assets/m8`.

`config_single_media.js` contains unique a `provisioningSessionId` for each asset and requires the
ApplicationFunction to provide valid `mediaPlayerEntry` values for playback.

`config_multi_media.js` uses the same `provisioningSessionId` for multiple assets which is why
the `mediaPlayerEntry` values are directly provided.

For more information about the different M8 formats refer to
the [Github discussion](https://github.com/5G-MAG/rt-5gms-application/discussions/6)

## Development

This project follows
the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
. The `development`
branch of this project serves as an integration branch for new features. Consequently, please make
sure to switch to the `development`
branch before starting the implementation of a new feature. 
