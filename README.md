# Goloom for Android

Android client for the **Goloom** VPN protocol — tunnels traffic inside a Telemost (Yandex's WebRTC video) media stream and unwraps it on a VPS into kernel WireGuard.

## Build

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35
- (Only when rebuilding the SDK) Go 1.22+, gomobile, Android NDK r26

### Quick start

The project depends on a `goloom.aar` produced by the [Pinnss/goloom-server](https://github.com/Pinnss/goloom-server) gomobile bridge. Build it once:

```bash
git clone https://github.com/Pinnss/goloom-server.git
cd goloom-server
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.1.10909125
./mobile/scripts/build-android.sh
```

Then drop the artifact into this project:

```bash
cp build/android/goloom.aar /path/to/goloom-android/app/libs/goloom.aar
```

Open the project in Android Studio, Sync Gradle, Run > app.

`goloom.aar` is **not** committed to git (see `.gitignore`) — it is a 16 MB native binary that has to be rebuilt anyway when the SDK changes. Releases on this repo include a pre-built APK with the SDK already linked.

## Releasing

Single-source-of-truth version: `version.properties` at the repo root. The script `scripts/release.sh`:

1. Reads `VERSION=`,
2. Rebuilds the SDK from a sibling `../goloom-server/` checkout,
3. Copies the `.aar` into `app/libs/`,
4. Builds a debug APK,
5. Drops it at the repo root as `goloom-v<VERSION>.apk` (committed alongside the source — same pattern as the reference TurnGate project).

After running the script:

```bash
git add goloom-v0.1.7.apk
git commit -m "release v0.1.7"
git tag v0.1.7
git push --follow-tags
gh release create v0.1.7 goloom-v0.1.7.apk --generate-notes
```

The in-app `UpdateChecker` watches `releases/latest`, so the GitHub Release is what users see.

## Project layout

```
app/src/main/java/app/goloom/client/
├── data/                  ConnStr parser, ProfileStore, LogStore, SettingsManager, AppListStore
├── design/                Tokens, Theme, Typography, Icons, GoloomEye (Canvas animation), Components
├── screens/               11 Compose screens (Main, Profiles, Settings, Logs, Parameters, Updates,
│                          About, AppRouting, ImportSheet, ProfileDetails, ProfileEdit)
├── tunnel/                GoloomController, GoloomVpnService (VpnService + foreground notification)
├── util/                  UpdateChecker, UpdateState, Downloader, Installer, DeepLink, Clipboard,
│                          Sharing, NetworkMonitor, QrUtils
├── GoloomApp.kt           Application class (i18n init + NetworkMonitor.start)
├── MainActivity.kt        Single-activity navigation, BackHandler, deep-link handler
└── Navigation.kt          Sealed `Screen` class
```

## License

Apache-2.0.
