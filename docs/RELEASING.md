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
2. Закоммитить, поставить тег и запушить:
   ```
   git tag v0.2.0
   git push origin main v0.2.0
   ```
3. Workflow `Release` проверит, что тег совпадает с `versionName`, прогонит тесты, соберёт
   подписанный APK `LibriaTV-0.2.0.apk` и опубликует GitHub Release с автоматическими заметками.
4. Приложения у пользователей увидят новую версию при следующем запуске (проверка не чаще раза в 6 ч).

## Локальная подписанная сборка

```
./gradlew :app:assembleRelease -PsigningProperties=/путь/к/signing.properties
```

`signing.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Без этого параметра release-сборка собирается неподписанной (удобно для форков).

## Форки

Проверка обновлений смотрит в репозиторий из `BuildConfig.UPDATE_REPO`
(по умолчанию `feskolech/anilibria-androidtv`). Для своего форка: `-PupdateRepo=owner/repo`.
Автообновление работает только с публичным репозиторием (GitHub API без токена).
