# GarPulse

[![License: GPL-3.0-or-later](https://img.shields.io/github/license/addycloud/garpulse)](LICENSE)
![minSdk 28](https://img.shields.io/badge/minSdk-28-3DDC84?logo=android&logoColor=white)

A private, **read-only** health metrics widget app for Android. GarPulse reads your
health data from [Health Connect](https://developer.android.com/health-connect) and
the [Garmin Personal API](https://github.com/matin/garth), and shows it right on your
home screen — no accounts, no trackers, your data stays on your device.

> No accounts. No trackers. Just your data, on your device.

## Features

- **Universal widget** — place any metric on your home screen, edit-first add flow
- **Multi-source** — Health Connect / Garmin API / AUTO dual-source, no switching needed
- **Image widgets** — use your own photo as the background, metrics layered on top
- **Material You** — dynamic colors, live refresh

## Build

Requirements: Android Studio (or CLI), JDK 17.

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## License

[GPL-3.0-or-later](LICENSE)

> Documentation is a work in progress and will be updated as development continues.
