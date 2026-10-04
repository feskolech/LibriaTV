# LibriaTV

**RU** · [EN](#english)

Неофициальный клиент [AniLibria](https://anilibria.top) для Android TV и ТВ-приставок.
Работает на новом API v1: вход в аккаунт (по коду с телефона или по логину), избранное,
новые эпизоды, расписание, поиск, просмотр в 480/720/1080 с пропуском опенингов,
раздачи через TorrServe.

> Проект в разработке. План — [docs/ROADMAP.md](docs/ROADMAP.md), ТЗ — [docs/SPEC.md](docs/SPEC.md).
> Не связан с командой AniLibria; весь контент принадлежит правообладателям.

## Установка

1. Скачайте `LibriaTV-*.apk` из [Releases](../../releases).
2. Поставьте на приставку любым способом (флешка + файловый менеджер, Send Files to TV, adb):
   ```
   adb connect <IP-приставки>:5555
   adb install -r LibriaTV-x.y.z.apk
   ```

Требования: Android 7.0+ (Android TV, Google TV, любые ТВ-боксы). Google-сервисы не нужны.

## Сборка

JDK 21, Android SDK (platform 36). Путь к SDK — в `local.properties` (`sdk.dir=...`) или `ANDROID_HOME`.

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Лицензия

[GPL-3.0](LICENSE)

---

## English

Unofficial [AniLibria](https://anilibria.top) client for Android TV and TV boxes, built on the
v1 API: account login (phone code or password), favorites, new episodes feed, schedule, search,
480/720/1080 playback with opening skip, torrents via TorrServe. Content is in Russian.
Not affiliated with the AniLibria team. Download the APK from [Releases](../../releases).
Licensed under [GPL-3.0](LICENSE).
