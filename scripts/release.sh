#!/usr/bin/env bash
# Сборка APK + копия в корень репозитория с семантическим именем.
# Запускать из корня goloom-android.
#
# По шагам:
#   1) ../goloom-poc/mobile/scripts/build-android.sh -> goloom.aar
#   2) cp .aar -> app/libs/
#   3) ./gradlew assembleDebug
#   4) cp APK -> goloom-v$VERSION.apk в корень
#   5) подсказка для git tag + gh release
#
# Не пушит, не тэгает — это руками после визуальной проверки APK.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Где лежит SDK (sibling-папка по умолчанию).
SDK_REPO="${GOLOOM_POC_REPO:-${ANDROID_ROOT}/../goloom-poc}"

if [[ ! -d "${SDK_REPO}" ]]; then
    echo "ERROR: goloom-poc не найден по пути ${SDK_REPO}"
    echo "       Либо положи рядом, либо задай: GOLOOM_POC_REPO=/path/to/goloom-poc"
    exit 1
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    echo "ERROR: ANDROID_NDK_HOME не задан. Например:"
    echo "  export ANDROID_NDK_HOME=\$HOME/android-sdk/ndk/26.1.10909125"
    exit 1
fi

VERSION=$(grep '^VERSION=' "${ANDROID_ROOT}/version.properties" | cut -d= -f2 | tr -d '[:space:]')
if [[ -z "${VERSION}" ]]; then
    echo "ERROR: VERSION в version.properties пуст"
    exit 1
fi

echo "==> goloom-v${VERSION} release build"
echo "    SDK: ${SDK_REPO}"
echo "    NDK: ${ANDROID_NDK_HOME}"
echo

echo "==> step 1/4: build goloom.aar"
(
    cd "${SDK_REPO}"
    GOLOOM_TARGETS="${GOLOOM_TARGETS:-android/arm64,android/arm}" \
        ./mobile/scripts/build-android.sh
)

echo "==> step 2/4: copy .aar"
mkdir -p "${ANDROID_ROOT}/app/libs"
cp "${SDK_REPO}/build/android/goloom.aar" "${ANDROID_ROOT}/app/libs/goloom.aar"

echo "==> step 3/4: gradle assembleDebug"
(
    cd "${ANDROID_ROOT}"
    ./gradlew --no-daemon assembleDebug
)

OUT="${ANDROID_ROOT}/goloom-v${VERSION}.apk"
echo "==> step 4/4: copy APK to ${OUT}"
cp "${ANDROID_ROOT}/app/build/outputs/apk/debug/app-debug.apk" "${OUT}"
SIZE=$(du -h "${OUT}" | cut -f1)

echo
echo "==> OK: ${OUT} (${SIZE})"
echo
echo "Дальше руками:"
echo "  1) Проверь APK на устройстве:"
echo "     adb install -r ${OUT}"
echo "  2) Закоммить:"
echo "     git add goloom-v${VERSION}.apk"
echo "     git commit -m \"release v${VERSION}\""
echo "  3) Тег + push:"
echo "     git tag v${VERSION}"
echo "     git push --follow-tags"
echo "  4) (опционально) GitHub Release:"
echo "     gh release create v${VERSION} ${OUT} --generate-notes"
