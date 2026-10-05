# LibriaTV

[Русский](README.md) · **English**

An unofficial [AniLiberty](https://aniliberty.top) (formerly AniLibria) client for Android TV and
TV boxes. It is built on the current v1 API, so signing in works (unlike the old AniLibria TV app).
The whole UI is made for a remote control: arrows and OK. Content is in Russian.

![Home](docs/screenshots/home.jpg)

> **AniLibria → AniLiberty.** In May 2025 the project split: the fandub team, the site
> (anilibria.top / aniliberty.top) and the whole dub archive since 2012 are now **AniLiberty**, while
> the AniLibria name stayed with the official dubbing studio. LibriaTV uses the AniLiberty catalog —
> every fandub release, including the old AniLibria-era dubs.
>
> Not affiliated with the AniLiberty team or the AniLibria studio. All content belongs to its rights
> holders and is streamed from AniLiberty servers; the app itself stores and shares nothing.

## Features

- **Sign in** with a code: open the link or QR on your phone and enter the code shown on the TV.
  Login and password also work.
- **Home**: an endless feed of new episodes, Continue watching, airing today and tomorrow,
  recommendations, with a poster or live-preview backdrop.
- **New episodes of your favorites** on launch and as an Android TV home-screen channel;
  unfinished episodes in the system's Watch Next row.
- **Favorites and lists**: Watching, Planned, Watched, On hold, Dropped; rate titles.
- **Release page**: all seasons of a franchise, similar titles, episode list with thumbnails.
- **Catalog** with filters (genres, type, season, years, status), sorting and "I'm feeling lucky".
- **Search**, including voice search.
- **Weekly schedule.**
- **Player**: 480/720/1080p, skip opening and ending (manual or automatic), speed, sleep timer,
  night sound mode, seek previews, auto-play next episode, frame-rate matching. Watch progress
  syncs with your account.
- **Torrents via [TorrServe](https://github.com/YouROK/TorrServer)**: with TorrServe installed you
  pick the episode inside a torrent and it gets marked as watched. Without it, the magnet opens in
  any torrent app or goes to your phone as a QR code.
- **Phone remote**: scan the QR on the TV to search and control playback from your phone on the
  same Wi-Fi.
- **Appearance**: dark or OLED-black theme, 6 accent colours, interface size 90–130 %.
- **Self-update** from GitHub Releases. Crash reports only with your consent.
- Russian and English UI.

| | |
|---|---|
| ![Release page](docs/screenshots/release.jpg) | ![Catalog](docs/screenshots/catalog.jpg) |
| ![Favorites and lists](docs/screenshots/favorites.jpg) | ![Schedule](docs/screenshots/schedule.jpg) |

## Install

1. Download `LibriaTV-*.apk` from [Releases](../../releases/latest).
2. Install it on the box any way you like (USB stick and a file manager, Send Files to TV, adb):
   ```
   adb connect <box-ip>:5555
   adb install -r LibriaTV-<version>.apk
   ```
3. After that the app updates itself: it offers new versions when they are released.

Requires Android 7.0+ (Android TV, Google TV, TV boxes). Google services are not needed.

## Build

JDK 21 and the Android SDK (platform 36). SDK path in `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Signed releases: see [docs/RELEASING.md](docs/RELEASING.md).

## Feedback

Bugs and ideas go to [Issues](../../issues).

## License

[GPL-3.0](LICENSE)
