# Building MoneyPal locally

Clear, brief instructions to build the APK on your machine.

## 0. One-click: all APKs

```bash
scripts/build-apks.sh              # foss + phone-wear + watch app
scripts/build-apks.sh --with-tests # gate on the unit suite first
```

Detects JDK 17–21 and the SDK, builds everything, and copies the APKs to
`dist/<version>-<sha>/` with unambiguous names
(`MoneyPal-v…`, `MoneyPal-phone-wear-v…`, `MoneyPal-watchapp-v…`).
This is for local builds and testing — releases still go through fastlane
(changelog guard, signing).

## 1. Prerequisites

| Tool        | Version       | Notes                                                          |
|:------------|:--------------|:---------------------------------------------------------------|
| JDK         | **17 or 21**  | The daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties` (auto-provisioned); the launcher JVM may be newer (e.g. Android Studio's JBR). |
| Android SDK | Platform 36   | Install via Android Studio → SDK Manager.                      |
| Gradle      | 9.5.0 (AGP 9.3.3) | Use the wrapper (`./gradlew`) — never install manually.    |

Point the build at your SDK with **one** of:

- `ANDROID_HOME` environment variable, **or**
- a `local.properties` file in the project root containing `sdk.dir=/path/to/Android/sdk`
  (gitignored — create it yourself).

## 2. Build a debug APK (recommended first build)

The `:app` module has **no default flavor** — flavor-qualified tasks are mandatory.

```bash
# FOSS flavor (no Play Services — F-Droid/IzzyOnDroid build)
./gradlew :app:assembleFossDebug

# Wear flavor (includes the Wear OS bridge, uses Play Services)
./gradlew :app:assembleWearDebug
```

Output APKs:

```
app/build/outputs/apk/foss/debug/MoneyPal-v<version>.apk
app/build/outputs/apk/wear/debug/MoneyPal-WearOS-v<version>.apk
```

## 3. Build a release APK (signed)

Release signing reads `keystore.properties` (gitignored) in the project root —
for **both** `:app` and `:wear` (the watch module was un-signed before
2026-09-26; it now follows the same contract):

```properties
storeFile=C:/absolute/path/to/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Use an absolute path with forward slashes for `storeFile`. If the file is
missing or incomplete, the build still succeeds but produces an **unsigned**
APK — Android will refuse to install it, so verify before distributing:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/foss/release/MoneyPal-foss-v<version>.apk
```

```bash
./gradlew :app:assembleFossRelease
# or
./gradlew :app:assembleWearRelease :wear:assembleRelease
```

Output: `app/build/outputs/apk/<flavor>/release/MoneyPal-v<version>.apk`

> **Signing key note:** the current release key was created 2026-09-26
> (v2.3.0) after the original became unavailable. Upgrades from ≤ v2.1.0
> require uninstalling first (signature mismatch). The keystore +
> `keystore.properties` are backed up off-repo — never commit them
> (`*.jks`/`*.keystore`/`keystore.properties` are gitignored).

## 4. Install on a connected device

```bash
adb install -r app/build/outputs/apk/foss/debug/MoneyPal-v<version>.apk
```

## 5. Clean / verify before committing code

```bash
./gradlew clean
./gradlew :app:compileFossDebugKotlin :app:compileWearDebugKotlin \
  :wear:compileDebugKotlin :sync-contract:compileKotlin
```

## 6. Useful extras

```bash
# Watch module APK
./gradlew :wear:assembleDebug

# Skip the wear module entirely (FOSS-only machines)
./gradlew -Pmoneypal.includeWearModule=false ...
```

> **Tip:** first build downloads dependencies and can take several minutes —
> that's normal. Subsequent builds are incremental and much faster.
