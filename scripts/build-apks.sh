#!/usr/bin/env bash
# MoneyPal — one-click local APK builder.
#
# Builds every distributable APK from the current working tree:
#   1. :app:assembleFossRelease  -> MoneyPal-foss (F-Droid/Izzy, no Play Services)
#   2. :app:assembleWearRelease  -> MoneyPal wear flavor (with Wear bridge)
#   3. :wear:assembleRelease     -> standalone Wear OS watch app
#
# Outputs are copied (never moved) into dist/<version>-<shortsha>/ with
# unambiguous names, so the wear-flavor APK and the watch-app APK can never
# overwrite each other (both would otherwise be "MoneyPal-WearOS-v<ver>.apk").
#
# Usage:
#   scripts/build-apks.sh                 # build only
#   scripts/build-apks.sh --with-tests    # run unit tests first, abort on failure
#   scripts/build-apks.sh --no-sign       # force unsigned release builds
#
# Exit codes: 0 success, 1 preflight failure, 2 build failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WITH_TESTS=0
FORCE_NO_SIGN=0
for arg in "$@"; do
  case "$arg" in
    --with-tests) WITH_TESTS=1 ;;
    --no-sign)    FORCE_NO_SIGN=1 ;;
    *) echo "Unknown option: $arg"; exit 1 ;;
  esac
done

log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN:\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. JDK: Gradle 8.13 fails on JDK > 21; accept 17..21 only.
# ---------------------------------------------------------------------------
CANDIDATE_JDKS=(
  "${JAVA_HOME:-}"
  "C:/Program Files/Android/Android Studio/jbr"
  "C:/Program Files/Android/Android Studio/jbrs/default"
  "C:/Program Files/Java/jdk-21"
  "C:/Program Files/Java/jdk-17"
  "$HOME/.jdks/temurin-21"
  "$HOME/.jdks/corretto-21"
  "$HOME/.jdks/temurin-17"
)
JDK_OK=""
for candidate in "${CANDIDATE_JDKS[@]}"; do
  [ -n "$candidate" ] || continue
  java_bin="$candidate/bin/java"
  [ -f "$java_bin" ] || [ -f "${java_bin}.exe" ] || continue
  version="$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  case "$version" in
    17|18|19|20|21) JDK_OK="$candidate"; break ;;
    *) warn "JDK $version at $candidate is unsupported (need 17-21), trying next..." ;;
  esac
done
[ -n "$JDK_OK" ] || fail "No JDK 17-21 found. Install one or set JAVA_HOME."
export JAVA_HOME="$JDK_OK"
log "Using JDK: $JAVA_HOME"

# ---------------------------------------------------------------------------
# 2. Android SDK: local.properties (sdk.dir) or ANDROID_HOME.
# ---------------------------------------------------------------------------
if [ ! -f "$ROOT/local.properties" ] && [ -z "${ANDROID_HOME:-}" ]; then
  fail "Neither local.properties (sdk.dir) nor ANDROID_HOME is set."
fi
log "Android SDK: ${ANDROID_HOME:-from local.properties}"

# ---------------------------------------------------------------------------
# 3. Signing: keystore.properties present -> signed release; else unsigned.
# ---------------------------------------------------------------------------
if [ "$FORCE_NO_SIGN" -eq 1 ]; then
  warn "--no-sign passed: building UNSIGNED release APKs."
elif [ ! -f "$ROOT/keystore.properties" ]; then
  warn "keystore.properties not found -> release APKs will be UNSIGNED."
  warn "Add keystore.properties (storeFile/storePassword/keyAlias/keyPassword) for signed builds."
fi

# ---------------------------------------------------------------------------
# 4. Optional test gate.
# ---------------------------------------------------------------------------
if [ "$WITH_TESTS" -eq 1 ]; then
  log "Running unit tests (foss + wear flavor + watch + sync-contract)..."
  ./gradlew --console=plain -q \
    :app:testFossDebugUnitTest :sync-contract:test :wear:testDebugUnitTest \
    || fail "Unit tests failed — aborting (run with no flags to skip tests)."
  log "All tests passed."
fi

# ---------------------------------------------------------------------------
# 5. Build all three APKs.
# ---------------------------------------------------------------------------
log "Building :app:assembleFossRelease :app:assembleWearRelease :wear:assembleRelease ..."
./gradlew --console=plain \
  :app:assembleFossRelease :app:assembleWearRelease :wear:assembleRelease \
  || fail "Gradle build failed (exit $?)."

# ---------------------------------------------------------------------------
# 6. Collect outputs with unambiguous names.
# ---------------------------------------------------------------------------
VERSION=$(grep '^VERSION_NAME=' version.properties | cut -d= -f2)
SHORT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "nogit")
DIST="$ROOT/dist/${VERSION}-${SHORT_SHA}"
mkdir -p "$DIST"

copy_apk() { # copy_apk <glob> <final-name>
  local found=""
  for f in $1; do
    [ -f "$f" ] || continue
    cp -f "$f" "$DIST/$2"
    log "  -> $DIST/$2"
    found=1
  done
  [ -n "$found" ] || fail "Expected APK not found: $1"
}

copy_apk "app/build/outputs/apk/foss/release/*.apk"  "MoneyPal-foss-v${VERSION}.apk"
copy_apk "app/build/outputs/apk/wear/release/*.apk"  "MoneyPal-phone-wear-v${VERSION}.apk"
copy_apk "wear/build/outputs/apk/release/*.apk"      "MoneyPal-watchapp-v${VERSION}.apk"

echo
log "All APKs built successfully: $DIST"
