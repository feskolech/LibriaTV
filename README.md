# AniLibria TV

Неофициальный клиент AniLibria для Android TV. Сейчас реализован каркас приложения: тёмная тема и навигация с экранами-заглушками. Функции каталога, авторизации и воспроизведения будут добавлены в следующих задачах.

## Сборка

Нужны JDK 21 и Android SDK с платформой API 36. Укажите SDK в `local.properties` (`sdk.dir=...`) или через `ANDROID_HOME`.

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = 'F:\androisdk'
$env:GRADLE_USER_HOME = 'F:\gradle-home'
$env:JAVA_OPTS = '-Xms256m -Xmx2g -XX:MaxMetaspaceSize=768m'
$env:GRADLE_OPTS = '-Dorg.gradle.internal.instrumentation.agent=false'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Установка на приставку

Включите отладку по сети на Android TV, узнайте IP-адрес устройства и выполните:

```powershell
adb connect <IP-адрес>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Приложение появляется в меню Android TV. Пункты бокового меню выбираются D-pad и кнопкой OK.
