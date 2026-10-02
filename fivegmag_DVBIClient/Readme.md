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

## What it does

| Function | ETSI TS 103 770 V1.2.1 |
| --- | --- |
| Service list from a URL, or picked from a Service List Registry query with `TargetCountry`; a regulator list is the default whenever the response has one; the list's `@id` is checked against the registry's `ServiceListId` | clauses 5.1.3.2, 5.3; table 12; table 83 NOTE 2 |
| Services, `ServiceName` in the device language, logos (JPEG or PNG first), `TargetRegion`, `ParentalRating`, subscription packages | clauses 5.5.2, 5.2.6.2, 5.5.28; tables 15, 16 |
| Channel numbers from the one LCN table of the user's region, `LCNRange` for the rest; hidden services reachable by entering their number | clauses 5.5.12, 5.5.29; table 23 |
| Instance selection: scheduled hours, instances that cannot play discarded, then `@priority`; the next instance on a playback error; re-evaluation when an instance enters or leaves its hours | clauses 5.2.5.2, 5.2.13 |
| DASH (`DASHDeliveryParameters`) and HLS (annex G.2.2 and G.2.3) played with Media3 ExoPlayer | clause 5.5.4; annex G |
| 5G Broadcast instances (`IdentifierBasedDeliveryParameters` with an `mbms://` locator) shown with a badge, the locator checked against 3GPP TS 26.347 V18.1.0 clause 8.2.2, and not played: there is no MBMS Client on Android yet | clause 9.3.3 |
| HTTP: `max-age`, `If-Modified-Since`, `If-None-Match`, no retry after 400 or 406, `Retry-After`, the back-off, the next `ServiceListURI` on failure | clause 4.3; ETSI TS 102 796 V1.8.1 clause 7.3.2.6 |
| Plain HTTP shown with a warning quoting the clause 7.3 exception and saying whether the endpoint is on the phone's private subnet | clause 7.3 |
| Now/next in the channel list and the player, a schedule view, programme information; a 404 from the content guide re-acquires the service list, then backs off | clauses 4.3.3.4, 6.1, 6.5, 6.6 |
| Parental restriction by age, the programme's rating taking precedence over the service's | clause 5.5.28 |

The DVB-I logic is ported from the browser client
[rt-dvb-i-application](https://github.com/5G-MAG/rt-dvb-i-application), with its clause citations.

### Plugging in an MBMS Client

`mbms/IMbmsStreamingClient.kt` is the part of the TS 26.347 Media Streaming Service API (clause 6.3, method
names of the annex B.3 IDL) this client calls: `registerStreamingApp`, `getStreamingServices`,
`startStreamingService`, `stopStreamingService`, `deregisterStreamingApp`, and the callbacks
`registerStreamingResponse`, `serviceStarted`, `streamingServiceError` and `streamingServiceListUpdate`. The
client registers with the service class of TS 103 770 table 106, `urn:dvb:metadata:serviceClass:DVB-I_Service_Instance:1`.
`NoMbmsClient` refuses the registration, so 5G Broadcast instances are discarded and another instance plays.
An Android MBMS Client implements the interface and is set in `DvbiSession.mbmsClient`; the player then starts
the User Service whose `serviceId` is the locator's prefix and plays its `ManifestURI`.

### Not implemented

Linked applications and XML AIT (no application engine: an instance with an application controlling media
presentation is discarded, as clause 5.2.13 requires), playlist servers (clause 5.2.7.2), More Episodes and
Box Sets (clauses 6.7, 6.8), on-demand programmes, the daily service list update (clause 5.1.7), and
re-authentication after 401 or 403.

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

## The demo over Wi-Fi

The phone and the laptop are on the same Wi-Fi network (for the demo, 192.168.1.0/24, with the laptop at
192.168.1.202). On the laptop, start the DVB-I live demo of
[rt-dvb-i-examples](https://github.com/5G-MAG/rt-dvb-i-examples) (`scripts/dvbi-live-demo`) so that it serves
on its Wi-Fi address rather than on `localhost`:

```sh
cd rt-dvb-i-examples/scripts/dvbi-live-demo
DEMO_HOST=192.168.1.202 ./start-all.sh
# ...
DEMO_HOST=192.168.1.202 ./stop-all.sh
```

The demo's own README describes its options. The service list, logos, content guide and media URLs all come
from the service list, so nothing else is configured in the app.

On the phone, the defaults point at the laptop:

| Setting | Default |
| --- | --- |
| Service list URL | `http://192.168.1.202:4000/service-list.xml` |
| Service List Registry endpoint | `http://192.168.1.202:7000/query` |

TS 103 770 V1.2.1 clause 5.1.3.2 defines the registry endpoint as the scheme, authority and path, so the path
`/query` is part of the setting. Other defaults can be built in with
`-PdvbiServiceListUrl=... -PdvbiRegistryUrl=...`, together with `-PdvbiCleartextHosts=...` for their host.

In **Settings**, *Query the registry and pick a list* sends the query (with `TargetCountry` when a country is
set) and offers the lists with the default marked; *Save* installs the chosen one. The channel list shows the
plain HTTP warning at the top while the endpoints are reached without TLS.

## Development

This project follows the [Gitflow workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).
The `development` branch of this project serves as an integration branch for new features.
