# Goloom for Android

Android-клиент VPN-протокола **Goloom** — туннелирует трафик через Telemost-сессию (видеозвонок-носитель), оборачивая его в WireGuard.

> Перед началом работы прочитай [HANDOFF.md](HANDOFF.md) — там зафиксированы договорённости по сборке, формату connection string и архитектурным решениям.

## Сборка

### Требования
- Android Studio Hedgehog (2023.1.1) или новее, рекомендуется Ladybug+
- JDK 17
- Android SDK 35
- (Только при пересборке SDK) Go 1.22+, gomobile, Android NDK r26

### Быстрый старт

```bash
# 1. Собрать .aar (один раз / после правок mobile/api.go в goloom-poc):
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.1.10909125
cd $HOME/IdeaProjects/goloom-poc
./mobile/scripts/build-android.sh

# 2. Положить артефакт в app/libs/:
cp $HOME/IdeaProjects/goloom-poc/build/android/goloom.aar \
   $HOME/IdeaProjects/goloom-android/app/libs/goloom.aar

# 3. Открыть в Android Studio:
#    File → Open → выбрать папку goloom-android
#    Sync Project with Gradle Files (значок слона)
#    Run → app
```

Без `goloom.aar` проект **не соберётся** — это by design, бинарь не коммитится в git (см. `.gitignore`). Способы получить:

- собрать самому из `goloom-poc/mobile/` (команды выше);
- скачать из последнего релиза `Sv9toslavPinigin/goloom-android` (когда репо станет публичным).

## Запуск

При первом подключении система покажет диалог "Goloom хочет настроить VPN-подключение" — это штатно. После согласия туннель поднимается автоматически.

Импорт профиля:
- **QR**: тап на `+` на главном → скан QR-кода из админки `https://<vps>:9443/`
- **Из буфера**: скопировать `goloom://...` ссылку → тап `+` → "Paste from clipboard"
- **Deep link**: открыть `goloom://...` из любого приложения / браузера → Goloom создаст профиль автоматически

## Тесты

```bash
./gradlew test
```

Покрыты:
- `ConnStrTest` — парсинг production / legacy connection strings, ошибки ввода, рендер WG-конфига
- `ProfileStoreTest` — CRUD, переключение active, перезагрузка из SharedPreferences
- `LogStoreTest` — добавление, очистка, snapshotFormatted

Тесты Robolectric — без эмулятора, но требуют сети для скачивания SDK-стабов при первом запуске.

## Структура

```
app/src/main/java/app/goloom/client/
├── data/                  ConnStr, ProfileStore, LogStore, SettingsManager, AppListStore
├── design/                Tokens, Theme, Typography, Icons, GoloomEye, Components
├── screens/               11 экранов: Main, Profiles[+Details/Edit], Import, Settings,
│                          Logs, Parameters, Updates, About, AppRouting
├── tunnel/                GoloomController, GoloomVpnService
├── util/                  UpdateChecker, DeepLink, QrUtils, Clipboard, Sharing
├── GoloomApp.kt           Application class (i18n init)
├── MainActivity.kt        Single-activity nav (sealed Screen enum)
└── Navigation.kt          Screen sealed class

app/src/main/res/
├── values/                strings.xml (en) + colors + themes
├── values-ru/             strings.xml (ru)
├── xml/                   network_security_config, file_paths,
│                          locales_config, backup_rules, data_extraction_rules
├── drawable/              ic_launcher_eye, ic_launcher_background
└── mipmap-anydpi-v26/     adaptive icons

app/src/test/java/         Robolectric-тесты (ConnStr, ProfileStore, LogStore)
```

## Дизайн

Источник истины — html-прототипы из Claude Design (`goloom-system.jsx`, `goloom-screens.jsx`). Палитра 0 (моно), белый акцент, ember-палитра только для огненного ока.

Око — Compose Canvas с procedural-corona (14 лепестков, dual-rotation 28s/18s, flicker 1.6s, pulse 1.8s). Анимация активна только в state `Connecting`. На `Idle` — холодный, на `On` — платиновый ободок.

## Лицензия

Apache-2.0 (как и `goloom-poc`).
