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
| JDK         | **17 or 21**  | Gradle 8.13 does **not** run on newer JDKs (e.g. 25).          |
| Android SDK | Platform 36   | Install via Android Studio → SDK Manager.                      |
| Gradle      | 8.13          | Use the wrapper (`./gradlew`) — never install manually.        |

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

Release signing reads `keystore.properties` (gitignored) in the project root:

```properties
storeFile=/absolute/path/to/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

If that file is missing, the build still succeeds but produces an **unsigned** APK.

```bash
./gradlew :app:assembleFossRelease
# or
./gradlew :app:assembleWearRelease
```

Output: `app/build/outputs/apk/<flavor>/release/MoneyPal-v<version>.apk`

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
