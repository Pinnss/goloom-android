# HANDOFF — что нужно знать про этот проект

Это Android-клиент VPN-протокола **Goloom** (туннель Telemost-внутри-WG). Проект живёт в **отдельном репозитории** `Sv9toslavPinigin/goloom-android`, потому что Go-сервер и SDK хранятся отдельно (`goloom-poc`).

---

## Договорённости (из чата 2026-05-04)

### 1. Где что лежит

| Компонент | Путь | Репо |
|---|---|---|
| Go SDK + сервер | `C:\Users\pcex\IdeaProjects\goloom-poc\` | внутренний |
| Мобильный bridge (Go) | `C:\Users\pcex\IdeaProjects\goloom-poc\mobile\` | в составе goloom-poc |
| Скрипт сборки .aar | `C:\Users\pcex\IdeaProjects\goloom-poc\mobile\scripts\build-android.sh` | в составе goloom-poc |
| **Этот Android-проект** | `C:\Users\pcex\IdeaProjects\goloom-android\` | `Sv9toslavPinigin/goloom-android` |
| Собранный .aar (артефакт) | `app/libs/goloom.aar` | **НЕ коммитим в git** (см. `.gitignore`) |

### 2. Как обновлять SDK (.aar)

```bash
# 1. Сборка в goloom-poc
cd C:/Users/pcex/IdeaProjects/goloom-poc
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.1.10909125  # путь к NDK на твоей машине
./mobile/scripts/build-android.sh

# 2. Копирование в Android-проект
cp goloom-poc/build/android/goloom.aar C:/Users/pcex/IdeaProjects/goloom-android/app/libs/goloom.aar

# 3. В Android Studio: File → Sync Project with Gradle Files
```

`build-android.sh` сам ставит точки после успеха и кладёт результат в `goloom-poc/build/android/goloom.aar`.

### 3. Connection string format (расширенный, 2026-05-04)

Сервер админки отдаёт **один QR-код / ссылку** вида:

```
goloom://<base64url-json>
```

Внутри base64 — JSON с полями:

| Поле | Тип | Назначение |
|---|---|---|
| `m` | string | Telemost meeting URL |
| `tag` | string | Inbound tag |
| `n` | string? | Display name (опционально) |
| `wgcp` | string | WG client private key (base64) |
| `wgsp` | string | WG server public key (base64) |
| `wga` | string | WG client address (CIDR, напр. `10.66.1.2/24`) |
| `wge` | string | WG endpoint, ВСЕГДА `127.0.0.1:51820` |
| `wgd` | string | WG DNS, comma-separated (`1.1.1.1,8.8.8.8`) |
| `psk` | string? | Pre-shared key (опционально) |
| `km`/`ks`/`kr` | int? | KCP-параметры (опционально) |

Это значит **один QR-код = всё для подключения**. WG-конфиг **не подгружается отдельно** — он восстанавливается из этих 5 полей в Kotlin при подключении (`ConnStr.toWireGuardConfig()`).

### 4. Решённые при старте проекта компромиссы

- **Платформа**: только Android (iOS — отдельная задача позже).
- **Минимум**: minSdk 26, targetSdk 35, Kotlin + Compose + Material 3, dark theme only.
- **applicationId**: `app.goloom.client`.
- **Kill switch**: НЕ реализован. При разрыве туннеля — простой disconnect, трафик идёт через обычный канал. Реальный kill switch с блокировкой при разрыве — отдельная задача (нужно продумывать поведение реконнекта).
- **Пинг до сервера**: НЕ показываем (специфика протокола — пинг малоинформативен).
- **Per-app split tunnel**: реализуем как в проекте-референсе `tun` (whitelist/blacklist по списку установленных приложений).
- **Eye animation**: всегда полный fiery (без fallback на sleek для слабых устройств).
- **Обновления**: через GitHub Releases API, репо публичный сделается когда дойдём до этой фичи.
- **Уведомление foreground**: текст "Goloom is running" (минимально).
- **i18n**: ru + en, переключатель в Settings.
- **Unit-тесты**: пишем сразу для ConnStr, ProfileStore, LogStore.

### 5. Референс-проект

Большая часть граблей решена в `C:\Users\pcex\IdeaProjects\tun\` (приложение TurnGate). Архитектурные паттерны (single-Activity + enum-nav, ProfileStore через SharedPreferences, LogStore с ротацией, AppRoutingScreen) скопированы оттуда **с адаптацией** под палитру 0 и компоненты Goloom.

### 6. Дизайн

Источник истины — html-прототип в Claude Design (`C:\Users\pcex\IdeaProjects\goloom\` worktree, дизайн распакован в `/tmp/goloom-design/`). Палитра, типографика и анимация ока — из `goloom-system.jsx`. Композиция экранов — из `goloom-screens.jsx`.

В дизайне НЕТ экрана `AppRoutingScreen` (Settings → Network → Per-app VPN) — добавляем сами по образцу tun, в стиле палитры 0.

В дизайне НЕТ отдельного `ProfileEditScreen` — добавляем минимально (Name + опционально WG-overrides), потому что после импорта пользователь может захотеть переименовать профиль.

---

## Контакты / триггеры

- Любые изменения формата `goloom://...` ПЕРЕД релизом обсуждать с автором сервера, синхронно править `internal/connstr/connstr.go` (Go) и `app/src/main/java/app/goloom/client/data/ConnStr.kt` (Kotlin).
- При обновлении `mobile/api.go` — обязательно пересобрать `.aar` и подменить файл в `app/libs/`.

---

## Что сделано на старте (2026-05-04)

Первичный коммит покрывает 31 пункт начального плана:

**Фундамент**
- HANDOFF.md (этот файл) + README + .gitignore
- Расширение `internal/connstr/connstr.go` полями `wgcp/wgsp/wga/wge/wgd` + `WGClientConfig()` метод
- `goloom-poc/mobile/scripts/build-android.sh` для gomobile bind

**Gradle scaffold**
- `settings.gradle.kts` + `build.gradle.kts` + `gradle/libs.versions.toml`
- `app/build.gradle.kts` (Compose 2024.12, Material3, WireGuard, ZXing+CameraX+ML Kit для QR)
- AndroidManifest с deep link `goloom://`, foreground service, VPN permission, QUERY_ALL_PACKAGES
- Темы (только dark), палитра, locale_config (en/ru), file_paths, backup_rules
- Adaptive launcher icon (vector eye)

**Compose design system** (`design/`)
- Tokens — палитра 0 (8 backgrounds + ember + ok/warn/err)
- Typography — Material3 + mono для технических полей
- Theme (dark scheme + edge-to-edge)
- GoloomEye — Canvas с procedural fiery corona, 5 параллельных анимаций (corona × 2, flicker, pupil-y, pupil-x)
- Icons — outlined-набор Material с алиасами `GIcons.Plus/Settings/...`
- Components — GButton (3 variants × 3 sizes), GCard, GRow, GSwitch, GChev, GSectionLabel, GTopBar, GIconBtn, GoloomLogo, GProfilePill, GUpdateBanner, GConnectButton

**Data layer** (`data/`)
- ConnStr — парсер `goloom://<base64-json>` + рендер wg-quick конфига
- Profile + ProfileStore — SharedPreferences JSON с активным id
- LogEntry + LogStore — StateFlow + ротация файла >500KB
- SettingsManager — поведенческие настройки + язык + routing mode
- AppListStore — для per-app split tunnel
- ConnectionState (sealed) + TunnelStats

**Tunnel layer** (`tunnel/`)
- GoloomController — single source of truth для UI (StateFlow)
- GoloomVpnService — VpnService + foreground "Goloom is running"
  - Поднимает Mobile.Client (gomobile) → SocketProtector + LogSink
  - Парсит inline WG-конфиг → строит VpnService.Builder с telemost-IP исключениями
  - Per-app routing через addAllowedApplication / addDisallowedApplication
  - Stats-loop каждую секунду

**Utilities** (`util/`)
- UpdateChecker — GitHub releases API
- DeepLink — извлечение connstr из VIEW-Intent
- QrUtils — encode через ZXing
- Clipboard, Sharing — wrappers над системными API

**11 Compose-экранов** (`screens/`)
- MainScreen + ProfileSwitcherSheet (bottom sheet смены профиля)
- ProfilesScreen, ProfileDetailsScreen (read-only + QR/Copy/Share/Delete), ProfileEditScreen
- ImportSheet (CameraX + ML Kit Barcode + paste from clipboard)
- SettingsScreen (с диалогом смены языка)
- LogsScreen (фильтр-чипы, copy/share/clear)
- ParametersScreen, UpdatesScreen, AboutScreen
- AppRoutingScreen (3 mode + список приложений с поиском)

**Tests**
- ConnStrTest, ProfileStoreTest, LogStoreTest (Robolectric + JUnit5 + AssertJ + MockK)
- 16 тестов покрывают парсинг, CRUD, ротацию

**Локализация**
- res/values/strings.xml (en) + res/values-ru/strings.xml
- Переключатель в Settings → Language через `AppCompatDelegate.setApplicationLocales`

## Финальное состояние (после первой live-сессии 2026-05-04)

**Туннель работает.** APK на устройстве, VPN-иконка в статус-баре, handshake + keepalive с peer'ом, Chrome видит сеть, реальный трафик идёт через Goloom-relay → Telemost → wg-server.

### Что меняли по ходу первого запуска (важно для будущих итераций)

1. **`mobile/scripts/build-android.sh`** — добавлен `GOFLAGS=-ldflags=-checklinkname=0`. Go 1.25 ужесточил проверку `//go:linkname`, а транзитивно-зависимый `wlynxg/anet` (через `pion/transport`) использует linkname к `net.zoneCache`, который удалили. Без этого `.aar` не линкуется. Будущая версия `anet` может это исправить — проверять при апгрейде Go.

2. **`mobile/api.go::Connect`** — раньше блокировал на `joiner.Run(ctx)` пока сессия не упадёт; для мобилы это было фатально (UI висел "Connecting" вечно). Переписан через канал `Client.sessionDone`: `runSession` возвращает `ConnectResult` сразу после handshake, а supervisor читает результат joiner'а из канала.

3. **`mobile/wgembed.go`** (новый файл) — встроенный pure-go WireGuard userspace на `golang.zx2c4.com/wireguard`. Метод `Client.AdoptTun(fd int64, wgConfig string)` принимает уже-открытый TUN-fd от `VpnService.Builder.establish()` и поднимает device. Заменяет проблемную интеграцию с `wireguard-android:tunnel`.

4. **Удалили зависимость `com.wireguard.android:tunnel` (как backend).** Использовалась `GoBackend.setState()` ломалась двумя способами:
   - Хардкодит путь `/data/data/com.wireguard.android` для UAPI-сокета — наш package туда писать не может, горутина uapi падает в SIGSEGV (~100ms после `wgTurnOn ok`).
   - Поднимает свой `VpnService` параллельно с нашим — конфликт, второй не стартует.
   Парсер `com.wireguard.config.Config` остался — он чисто-Java, не использует JNI.

5. **`ParcelFileDescriptor.detachFd()`** — без detach()  PFD остаётся владельцем fd, и его финализатор закроет fd в произвольный момент → VpnService свернёт TUN (иконка пропадает) → пакеты в никуда. detach() обязателен, передаёт ownership Go-стороне.

6. **`Builder.addDisallowedApplication(packageName)`** — наш собственный процесс ходит **мимо VPN**. Без этого pion/webrtc сокеты в Goloom-relay ловятся TUN, ICE candidates рекурсируют → `iceConnectionState: failed` → handshake retries без ответа. `excludeRoute` по конкретным IP не покрывает все ICE candidates (они появляются динамически). `addDisallowedApplication(self)` — стандартный паттерн VPN-приложений, решает разом.

### Минимальный путь "от чистого листа до APK на телефоне"

```bash
# 1. Один раз: NDK через sdkmanager
echo y | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat --install "ndk;26.1.10909125"

# 2. Один раз: gomobile bind зависимость
cd ~/IdeaProjects/goloom-poc
go get golang.org/x/mobile/bind
# (см. tools.go — он закрепляет это в go.mod через build-tag)

# 3. Сборка SDK
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
export PATH=$PATH:~/go/bin
export GOLOOM_TARGETS="android/arm64,android/arm"   # пропускаем x86 — ускоряет
./mobile/scripts/build-android.sh

# 4. Перенос артефакта
cp build/android/goloom.aar ~/IdeaProjects/goloom-android/app/libs/

# 5. Сборка APK
cd ~/IdeaProjects/goloom-android
./gradlew --no-daemon assembleDebug

# 6. Установка на телефон. На MIUI/HyperOS обычная adb install падает с
#    INSTALL_FAILED_USER_RESTRICTED — обход через pm install с pkg-installer:
export MSYS_NO_PATHCONV=1
adb shell am force-stop app.goloom.client
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/goloom.apk
adb shell pm install -r -i com.android.vending /data/local/tmp/goloom.apk
adb shell rm /data/local/tmp/goloom.apk
```

## Версионирование (семвер)

Источник истины — `version.properties` в корне (одно поле `VERSION=MAJOR.MINOR.PATCH`).

`app/build.gradle.kts` парсит этот файл и автоматически выводит:
- `versionName` — то же, что `VERSION` (включая pre-release suffix, если будет, например `0.2.0-rc1`)
- `versionCode` — `MAJOR*10000 + MINOR*100 + PATCH` (для `0.1.0` → `100`)

Релиз делаем так:
1. Обновляем `version.properties`: `VERSION=0.2.0`
2. Коммитим: `git commit -am "release v0.2.0"`
3. Тегируем: `git tag v0.2.0 && git push --follow-tags`
4. CI workflow увидит `refs/tags/v*` → соберёт APK → создаст GitHub Release с `goloom-v0.2.0.apk`

Расхождение `version.properties` vs git-тег CI **не валидирует** — следим вручную (для срочных хотфиксов это допустимая гибкость).

`UpdateChecker` дёргает `/repos/<owner>/<repo>/releases/latest`, читает `tag_name`, сравнивает по semver — поэтому единый источник версии даёт согласованный auto-update flow.

## Release signing

Ключи через `keystore.properties` в корне (НЕ коммитится, в `.gitignore`):
```properties
storeFile=release.jks
storePassword=…
keyAlias=goloom
keyPassword=…
```

Если файла нет (CI без секретов / свежий checkout) — `release` build падает на `debug.keystore`. APK работает, но **подпись разная между сборками** → atomic update через `pm install -r` будет ломаться. Для production обязательно положить `keystore.properties` (или передать через CI secrets и записать в файл перед `gradlew assembleRelease`).

## CI workflow

`.github/workflows/build.yml`:
- Триггеры: push в `main`, теги `v*`, PR в `main`, ручной dispatch
- Чекаутит **этот repo + `Sv9toslavPinigin/goloom-poc`** (нужен для сборки `.aar`)
- Ставит Go, NDK, gomobile
- Собирает `goloom.aar` через `mobile/scripts/build-android.sh`
- Кладёт в `app/libs/`, гоняет тесты, собирает APK
- При теге `v*` создаёт GitHub Release с APK как asset

Если `goloom-poc` приватный — в Settings → Secrets добавить `GOLOOM_POC_TOKEN` (PAT с `repo:read`), workflow его подхватит.

## Что НЕ сделано / открытые вопросы

- **`goloom.aar` в `app/libs/`** — артефакт не коммитится в git (см. `.gitignore`).
- **`tunFd` close при фейле AdoptTun** — fd мы уже detach'нули, поэтому в teardown больше не закрываем. Если AdoptTun упал — fd утекает (но Go-сторона должна забрать его в любой ошибке).
- **Параметры из ParametersScreen в туннель** — `mtu`/`keepalive`/`dns` теперь редактируемы в UI и пишутся в SettingsManager, но при работающей сессии применятся только после reconnect. Для on-the-fly применения нужен reload в `Client.AdoptTun` через UAPI.
- **iOS** — отдельный repo, подключить ту же `Client.AdoptTun` логику для NEPacketTunnelProvider.
- **Per-app routing UI** работает, но нужен force-reconnect баннер при изменении (сейчас юзер вынужден сам нажать disconnect/connect).
- **Update notification на новый release** — есть только баннер на главном при on-resume. Нет push'а или background-проверки. Хорошо бы подключить `WorkManager` 1×/день.
