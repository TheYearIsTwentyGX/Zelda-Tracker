#!/usr/bin/env bash
#
# Build the debug APK and install it on a phone over Wireless Debugging.
#
#   install-wireless                 find the phone, install, and open the app
#   install-wireless --serial S      install on this adb serial (or set ANDROID_SERIAL)
#   install-wireless --no-launch     install but do not open the app
#
# Finds the phone the way Android Studio does: a wireless device adb already
# has, otherwise whatever adb's mDNS discovery advertises as `_adb-tls-connect`.
# The connect port changes every time Wireless Debugging is toggled, which is
# why nothing here is hardcoded.
#
# Pairing cannot happen here: it needs the six-digit code off the phone's
# screen, and a TGXCode button cannot ask for one. A phone that has never been
# paired gets the exact `adb pair` command to run once instead.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE=gx.zelda.zeldagx

SERIAL="${ANDROID_SERIAL:-}"; LAUNCH=1
while [ $# -gt 0 ]; do
    case "$1" in
        --serial) shift; SERIAL="${1:-}" ;;
        --serial=*) SERIAL="${1#*=}" ;;
        --no-launch) LAUNCH=0 ;;
        -h|--help) sed -n '3,7p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "install: unknown option '$1'" >&2; exit 1 ;;
    esac
    shift
done

say() { printf '%s\n' "$*"; }
die() { printf 'install: %s\n' "$1" >&2; shift; for l in "$@"; do printf '  %s\n' "$l" >&2; done; exit 1; }

# --- the SDK ----------------------------------------------------------------

# local.properties is gitignored, so a fresh worktree (or the main checkout)
# may not have one. Exporting ANDROID_HOME lets Gradle find the SDK anyway.
if [ -z "${ANDROID_HOME:-}" ]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$HERE/local.properties" 2>/dev/null | tail -1)"
    export ANDROID_HOME="${SDK_DIR:-$HOME/Android/Sdk}"
fi
ADB="$ANDROID_HOME/platform-tools/adb"
[ -x "$ADB" ] || die "no adb at $ADB." \
    "Set ANDROID_HOME, or sdk.dir in local.properties, to your Android SDK."

# --- the phone --------------------------------------------------------------

# Serials of devices adb has in the `device` state that came over the network:
# mDNS-connected ones look like adb-XXXX._adb-tls-connect._tcp, manual ones ip:port.
wireless_devices() {
    "$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }' \
        | grep -E '\._adb-tls-connect\.|^[0-9.]+:[0-9]+$'
}

if [ -z "$SERIAL" ]; then
    FOUND="$(wireless_devices)"
    if [ -z "$FOUND" ]; then
        say "No wireless device connected — looking for one on the network…"
        ADDRS="$("$ADB" mdns services 2>/dev/null | awk '$2 ~ /_adb-tls-connect/ { print $3 }')"
        for addr in $ADDRS; do
            say "  adb connect $addr"
            "$ADB" connect "$addr" 2>&1 | sed 's/^/    /'
        done
        FOUND="$(wireless_devices)"
    fi
    COUNT="$(printf '%s' "$FOUND" | grep -c .)"
    if [ "$COUNT" = 0 ]; then
        PAIRING="$("$ADB" mdns services 2>/dev/null | awk '$2 ~ /_adb-tls-pairing/ { print $3; exit }')"
        die "no phone found over Wireless Debugging." \
            "On the phone: Settings → Developer options → Wireless debugging, turn it on," \
            "with the phone on the same Wi-Fi as this PC. Then press Install again." \
            "" \
            "Never paired this phone? Tap 'Pair device with pairing code' and run:" \
            "  \"$ADB\" pair ${PAIRING:-<ip:port shown on the phone>} <code>"
    fi
    if [ "$COUNT" -gt 1 ]; then
        die "more than one wireless device — pick one with --serial:" $FOUND
    fi
    SERIAL="$FOUND"
fi

[ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null)" = device ] || die "$SERIAL is not connected." \
    "Check it with: \"$ADB\" devices -l"

MODEL="$("$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
say "Installing on ${MODEL:-device} ($SERIAL)"

# --- build and install ------------------------------------------------------

# ANDROID_SERIAL scopes installDebug to this one device, so a USB phone or an
# emulator that happens to be attached as well does not get it too.
cd "$HERE" || exit 1
if ! ANDROID_SERIAL="$SERIAL" ./gradlew :androidApp:installDebug; then
    die "the build or install failed — see above."
fi

if [ "$LAUNCH" = 1 ]; then
    say "Opening ZeldaGX…"
    "$ADB" -s "$SERIAL" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
        || say "  Installed, but could not open it — start it from the phone."
fi
say "Done."
