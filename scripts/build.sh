#!/usr/bin/env bash
#
# Build the debug APK.
#
#   build            assemble the debug APK
#   build --clean    clean first, then assemble (a rebuild)
#
# A plain ./gradlew would do, except that local.properties is gitignored, so a
# fresh worktree (or the main checkout) has no sdk.dir and Gradle cannot find
# the SDK. Exporting ANDROID_HOME the same way install-wireless.sh does fixes that.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CLEAN=0
while [ $# -gt 0 ]; do
    case "$1" in
        --clean) CLEAN=1 ;;
        -h|--help) sed -n '3,6p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "build: unknown option '$1'" >&2; exit 1 ;;
    esac
    shift
done

if [ -z "${ANDROID_HOME:-}" ]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$HERE/local.properties" 2>/dev/null | tail -1)"
    export ANDROID_HOME="${SDK_DIR:-$HOME/Android/Sdk}"
fi
[ -d "$ANDROID_HOME" ] || { echo "build: no Android SDK at $ANDROID_HOME — set ANDROID_HOME or sdk.dir in local.properties." >&2; exit 1; }

cd "$HERE" || exit 1
TASKS=(:androidApp:assembleDebug)
[ "$CLEAN" = 1 ] && TASKS=(clean "${TASKS[@]}")

./gradlew "${TASKS[@]}" || { echo "build: failed — see above." >&2; exit 1; }

echo ""
echo "APK: $HERE/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
