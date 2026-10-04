# AGENTS.md — правила для агента-разработчика

Ты пишешь Android TV клиент AniLibria. Перед любой задачей прочитай `docs/SPEC.md`
(что строим) и нужный раздел `docs/ROADMAP.md` (что делать сейчас).

## Окружение
- Windows 11 amd64, PowerShell. JDK 21 (`F:\Android Studio\jbr` или системный).
- Android SDK: `F:\androisdk` (`local.properties`: `sdk.dir=F\:\\androisdk`, файл в .gitignore).
- `GRADLE_USER_HOME=F:\gradle-home` — НИКОГДА не складывай кэши/артефакты на диск C:.
- Сборка: `.\gradlew.bat :app:assembleDebug`, тесты: `.\gradlew.bat :app:testDebugUnitTest`,
  линт: `.\gradlew.bat :app:lintDebug`.
- Целевое устройство: Ugoos SK4 (arm64, Android 14, пульт D-pad). Эмулятор: Android TV API 34 x86.

## Стек (не менять без явного указания в задаче)
- Kotlin, один Gradle-модуль `app`, version catalog `gradle/libs.versions.toml`.
- UI: Jetpack Compose + `androidx.tv:tv-material` (Compose for TV). Без Leanback.
- Навигация: `androidx.navigation:navigation-compose`.
- Сеть: Retrofit + OkHttp + kotlinx.serialization (`ignoreUnknownKeys = true`, `explicitNulls = false`).
- DI: Hilt.  Асинхронность: Coroutines/Flow.  Картинки: Coil 3.
- Плеер: Media3 ExoPlayer (+ media3-exoplayer-hls, media3-ui) с кастомным Compose-оверлеем.
- Хранилище: DataStore Preferences (токен, device_id, настройки), Room — только если задача требует.
- minSdk 24, targetSdk/compileSdk 36. Package / applicationId: `ru.feskolech.anilibriatv`.

## Архитектура
```
app/src/main/java/ru/feskolech/anilibriatv/
  data/api/        Retrofit-интерфейсы и DTO (1:1 с OpenAPI, суффикс Dto)
  data/repo/       репозитории: маппинг Dto -> domain, кэш, обработка ошибок
  domain/          модели приложения (Release, Episode, Torrent, ScheduleItem, User...)
  ui/<feature>/    Screen.kt (Compose) + ViewModel.kt (StateFlow<UiState>)
  ui/components/   общие TV-компоненты (PosterCard, Row, ErrorState, LoadingState)
  ui/theme/
  di/
```
- ViewModel отдаёт один `StateFlow<XxxUiState>` (Loading/Content/Error). Никакой логики в Composable.
- Все строки — в `res/values/strings.xml` (русский язык UI).
- Ошибки сети показываем экраном/тостом с кнопкой «Повторить», приложение не падает.

## API
- База: `https://anilibria.top/api/v1/` (запасной: `https://aniliberty.top/api/v1/`, переключение
  при сетевой ошибке). Картинки — относительные пути, префиксуй `https://anilibria.top`.
- Полная спецификация: `docs/api/openapi-v1.json` (OpenAPI 3). Сверяй DTO с ней, а не с памятью.
- Авторизация: заголовок `Authorization: Bearer <token>` через OkHttp Interceptor. На 401 —
  чистим токен и переводим в состояние «гость».
- НЕ использовать старый API (`/public/api/index.php`, `wwnd.space`) — он мёртв.

## TV UX правила
- Всё управляется D-pad: каждый интерактивный элемент фокусируемый, фокус виден (scale + рамка).
- При открытии экрана фокус ставится на осмысленный первый элемент (FocusRequester).
- Кнопка Back всегда работает предсказуемо (закрывает оверлей → экран → выход с подтверждением на главной).
- Overscan: отступы контента 48dp по горизонтали, 27dp по вертикали.
- Тёмная тема, акцентный цвет AniLibria `#B32121`.

## Порядок работы над задачей
1. Делай только то, что описано в задаче. Не трогай чужие фичи без нужды.
2. После изменений обязательно `assembleDebug` и `testDebugUnitTest` — оба должны быть зелёными.
3. Для репозиториев/маппинга пиши unit-тесты (MockWebServer + JSON-фикстуры в `app/src/test/resources`).
4. Не коммить и не пушь — это делает ревьюер. Не меняй AGENTS.md/docs/SPEC.md.
5. В конце выведи краткий отчёт: что сделано, какие файлы, результат сборки/тестов, открытые вопросы.
6. Секреты, пароли, ключи подписи в репозиторий не кладём.
