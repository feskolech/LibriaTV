# Выпуск версии

## Один раз: секреты в GitHub

Settings → Secrets and variables → Actions → New repository secret:

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | keystore в base64 (`base64 -w0 libriatv-release.jks`) |
| `KEYSTORE_PASSWORD` | пароль keystore |
| `KEY_ALIAS` | `libriatv` |
| `KEY_PASSWORD` | пароль ключа |

Ключ подписи хранится **вне репозитория**. Потеряете ключ — установленные у людей версии
больше не обновятся (Android требует ту же подпись), придётся удалять и ставить заново.
Сделайте резервную копию keystore и паролей.

## Каждый релиз

1. Поднять `versionCode` (+1) и `versionName` в `app/build.gradle.kts`.
2. Описать изменения в `docs/release-notes/<versionName>.md` (по-русски и по-английски) —
   этот текст станет описанием GitHub Release и разделом «Что нового» в приложении.
3. Закоммитить, поставить тег и запушить:
   ```
   git tag v0.2.0
   git push origin main v0.2.0
   ```
4. Workflow `Release` проверит, что тег совпадает с `versionName`, прогонит тесты, соберёт
   подписанный APK `LibriaTV-0.2.0.apk` и опубликует GitHub Release с текстом из файла заметок
   (если файла нет — с автоматическим списком изменений).
5. Приложения у пользователей увидят новую версию при следующем запуске (проверка не чаще раза в 6 ч).

## Локальная подписанная сборка

```
./gradlew :app:assembleRelease -PsigningProperties=/путь/к/signing.properties
```

`signing.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Без этого параметра release-сборка собирается неподписанной (удобно для форков).

## Форки

Проверка обновлений смотрит в репозиторий из `BuildConfig.UPDATE_REPO`
(по умолчанию `feskolech/LibriaTV`). Для своего форка: `-PupdateRepo=owner/repo`.
Автообновление работает только с публичным репозиторием (GitHub API без токена).

## Сборка с отчётами о падениях

По умолчанию `CRASH_REPORT_URL` пустой: ACRA не инициализируется, отчёты не собираются.
Для сборки с отчётами задайте HTTPS URL приёмника и, при необходимости, Basic-auth через
локальные Gradle-свойства (не сохраняйте их в репозитории):

```
./gradlew :app:assembleRelease -PcrashReportUrl=https://example.org/crash \
  -PcrashReportLogin=LOGIN -PcrashReportPassword=PASSWORD \
  -PsigningProperties=/путь/к/signing.properties
```

Логин и пароль попадают в APK (BuildConfig) и легко извлекаются из него: Basic-auth защищает
приёмник только от случайных ботов, а не от целенаправленного мусора.

Приёмник должен принимать POST с `Content-Type: application/json` и отвечать кодом 2xx.
Отчёт содержит версию приложения, модель устройства, Android, время и стек падения.
DataStore, SharedPreferences и logcat не включены. После падения отчёт остаётся на устройстве
до выбора «Отправить», «Не отправлять» или «Всегда отправлять» при следующем запуске.
Автоматическую отправку можно отключить в Настройках.
