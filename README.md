<p align="center">
  <img src=".github/banner.svg" width="100%" alt="Reference Tools · 5G Media Streaming (5GMS) Architecture: 5GMS-Aware Application">
</p>

<p align="center">
  Applications for testing and demonstrating the other 5G-MAG Reference Tools for 5G Media
  Streaming (5GMS).
</p>

<p align="center">
  <img alt="Status" src="https://img.shields.io/endpoint?url=https%3A%2F%2Fwww.5g-mag.com%2Fbadges%2Fstatus%2Frt-5gms-application.json">
  <a href="https://github.com/5G-MAG/rt-5gms-application/releases"><img alt="Version"
    src="https://img.shields.io/github/v/release/5G-MAG/rt-5gms-application?label=Version"></a>
  <a href="LICENSE"><img alt="License: multiple, per folder"
    src="https://img.shields.io/badge/License-Multiple-blue"></a>
</p>

<p align="center">
  <a href="https://www.5g-mag.com/reference-tools/5gms/">Project page</a> &nbsp;&middot;&nbsp;
  <a href="https://github.com/5G-MAG/rt-5gms-application/issues">Issues</a> &nbsp;&middot;&nbsp;
  <a href="https://www.5g-mag.com/contributing">Contributing</a>
</p>

---

## At a glance

|  |  |
|---|---|
| **Part of** | [5G Media Streaming (5GMS) Architecture](https://www.5g-mag.com/reference-tools/5gms/), alongside [cmcd-toolkit](https://github.com/5G-MAG/cmcd-toolkit), [rt-5gc-service-consumers](https://github.com/5G-MAG/rt-5gc-service-consumers), [rt-5gms-application-function](https://github.com/5G-MAG/rt-5gms-application-function), [rt-5gms-application-provider](https://github.com/5G-MAG/rt-5gms-application-provider), [rt-5gms-application-server](https://github.com/5G-MAG/rt-5gms-application-server), [rt-5gms-common-android-library](https://github.com/5G-MAG/rt-5gms-common-android-library), [rt-5gms-examples](https://github.com/5G-MAG/rt-5gms-examples), [rt-5gms-media-session-handler](https://github.com/5G-MAG/rt-5gms-media-session-handler), [rt-5gms-media-stream-handler](https://github.com/5G-MAG/rt-5gms-media-stream-handler), [rt-cmmf-encoder](https://github.com/5G-MAG/rt-cmmf-encoder), [rt-media-origin](https://github.com/5G-MAG/rt-media-origin) |

## Introduction

This repository holds applications that test and demonstrate the other 5GMS Reference Tools. Each
folder is a separate application with its own code, documentation and licence file; the build and
install instructions are in each folder's README.

| Application | Licence | Description |
|---|---|---|
| [5GMSd-Aware Application](https://github.com/5G-MAG/rt-5gms-application/tree/development/fivegmag_5GMSdAwareApplication) ([Readme](fivegmag_5GMSdAwareApplication/Readme.md)) | 5G-MAG Public License v1.0 | An Android application that serves as a reference implementation for 5GMS downlink media streaming. It uses the Media Stream Handler for playback and for communication with the Media Session Handler. |
| [Exo DVB-I Player](https://github.com/5G-MAG/rt-5gms-application/tree/development/fivegmag_ExoDvbi_player) ([README](fivegmag_ExoDvbi_player/README.md)) | 5G-MAG Public License v1.0 for the ExoPlayer extensions, MIT for the DVB-I frontend, Apache 2.0 for ExoPlayer | Uses the Android ExoPlayer and the DVB-I Reference Client to select and play back media content. |

## Contributing

Contributions are welcome. How to raise an issue, fork the repository and open a pull request, and
the Contributor License Agreement required before code can be merged, are described at
<https://www.5g-mag.com/contributing>.

## License

Each folder within this repository may contain code with a different licence. See [LICENSE](LICENSE),
and the licence file in each folder:
[fivegmag_5GMSdAwareApplication/License.md](fivegmag_5GMSdAwareApplication/License.md) and
[fivegmag_ExoDvbi_player/LICENSE](fivegmag_ExoDvbi_player/LICENSE).
