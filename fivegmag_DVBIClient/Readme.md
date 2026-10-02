<h1 align="center">DVB-I Client</h1>
<p align="center">
  <img src="https://img.shields.io/badge/Status-Under_Development-yellow" alt="Under Development">
  <img src="https://img.shields.io/badge/License-5G--MAG%20Public%20License%20(v1.0)-blue" alt="License">
</p>

## Introduction

The DVB-I Client is a native Android application that finds, lists and plays DVB-I services as specified in
ETSI TS 103 770 V1.2.1 (Service Discovery and Programme Metadata for DVB-I). It follows the conventions of the
[5GMSd-Aware Application](../fivegmag_5GMSdAwareApplication) in this repository (Gradle layout, plugin and
library versions, Media3 ExoPlayer 1.10.0 for playback).

Baseline: ETSI TS 103 770 V1.2.1 (2024-09). Where the client follows ETSI TS 102 796 (cache rules) it is
V1.8.1; the MBMS URL check follows 3GPP TS 26.347 V18.1.0.

## Building

The build needs a JDK 17 or 21 (the Android Gradle Plugin 9.2.0 does not run on newer ones) and the Android
SDK with platform `android-37.0` and build-tools `37.0.0`. Everything else (Gradle 9.5.0, the plugins and the
libraries) is downloaded by the Gradle wrapper from Google's Maven repository and Maven Central. No 5G-MAG
library has to be published to Maven Local first.

### Installing the toolchain in the user directory

These commands install the toolchain without root rights and without changing any shell profile. They are
the ones used to build this application on Linux x86_64.

```sh
# JDK 21 (Eclipse Temurin 21.0.12.1+1) under ~/opt
mkdir -p ~/opt && cd ~/opt
curl -LO https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz
echo "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94  OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz" | sha256sum -c
tar -xzf OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz

# Android command-line tools (build 15859902) under ~/Android/Sdk
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  commandlinetools-linux-15859902_latest.zip" | sha256sum -c
unzip -q commandlinetools-linux-15859902_latest.zip && mv cmdline-tools latest

# Environment for this shell only
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

# SDK licences (read them), then the packages the build uses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
```

The checksums are the ones published on adoptium.net and developer.android.com/studio for these files.

### Building the APK and running the tests

With `JAVA_HOME` and `ANDROID_HOME` set as above:

```sh
cd fivegmag_DVBIClient/
./gradlew test             # JVM unit tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

### Plain HTTP and the network security configuration

ETSI TS 103 770 V1.2.1 clause 7.3 requires HTTP over TLS towards service list registries, service list servers
and content guide servers, with one exception: "For the specific case that a DVB-I client connects to a DVB-I
metadata endpoint located on the same private subnet (see clause 3 of IETF RFC 1918 [27]), HTTP may be used
without TLS."

Android blocks plain HTTP unless the application's network security configuration allows it. This
application does not allow it in general: the build writes a configuration that permits cleartext only to the
hosts named in the Gradle property `dvbiCleartextHosts` (comma separated), and to no other host. The default
is `192.168.1.202`, the demo laptop's Wi-Fi address. For another address:

```sh
./gradlew assembleDebug -PdvbiCleartextHosts=192.168.0.10
```

The list is fixed when the APK is built, because Android reads the network security configuration from the
APK. HTTPS works towards any host.

## Install

Over adb, with the phone connected by USB and USB debugging enabled:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Without adb, copy `app-debug.apk` to the phone (for example by download or file transfer) and open it there,
allowing the installation of applications from that source when Android asks.

## Development

This project follows the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).
The `development` branch of this project serves as an integration branch for new features.
