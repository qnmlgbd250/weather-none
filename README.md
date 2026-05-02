<p align="center">
  <img src="screenshot.jpg" width="360" alt="SkyPulse Weather App"/>
</p>

<h1 align="center">SkyPulse</h1>

<p align="center">
  <strong>Modern Android Weather App</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/API-Caiyun%20Weather-FF6B35?style=flat-square" alt="Caiyun Weather"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-orange?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"/>
</p>

---

## Features

- **Real-time Weather** — Current temperature, humidity, wind, pressure, visibility, and air quality
- **Hourly Forecast** — 48-hour temperature curve with smooth Bezier interpolation, precipitation probability
- **Daily Forecast** — 15-day outlook with temperature range bars
- **Weather Alerts** — Severe weather warnings from Caiyun API
- **Location-based** — GPS auto-detection with Geocoder reverse geocoding
- **Apple-style Icons** — All weather icons drawn programmatically with Canvas (zero image assets)
- **Glassmorphism UI** — Frosted glass cards with dynamic animated backgrounds
- **Pull-to-Refresh** — Material 3 pull refresh with custom indicator

## Architecture

```
MVVM + Repository Pattern
├── api/            Retrofit API interface + OkHttp client
├── model/          Data classes (Moshi codegen)
├── repository/     Data layer abstraction
├── viewmodel/      State management with StateFlow
└── ui/
    ├── components/ Reusable Compose components
    ├── screen/     Main weather screen
    └── theme/      Colors, typography, Material 3 theme
```

## Tech Stack

| Category | Library |
|----------|---------|
| UI | Jetpack Compose, Material 3, Canvas API |
| Architecture | ViewModel, StateFlow, Coroutines |
| Networking | Retrofit 2.9 + Moshi 1.15 (kapt codegen) + OkHttp |
| Location | Google Play Services Location |
| Permissions | Accompanist Permissions |
| Build | Gradle KTS, AGP 8.2, Kotlin 1.9 |

## Getting Started

```bash
git clone git@github.com:qnmlgbd250/weather-none.git
```

Open in **Android Studio Hedgehog** (or later) → Sync Gradle → Run on device.

> **Note:** Requires a physical device for GPS location. Emulator will use default location (Beijing).

## API

Powered by [Caiyun Weather API](https://docs.caiyunapp.com/) v2.6:

```
GET /v2.6/{token}/{lon},{lat}/weather?alert=true&dailysteps=15&hourlysteps=48
```

## Screenshots

<p align="center">
  <img src="screenshot.jpg" width="280" alt="Main Screen"/>
</p>

## License

```
MIT License

Copyright (c) 2026 SkyPulse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
