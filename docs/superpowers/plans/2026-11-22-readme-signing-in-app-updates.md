# Public README, Launcher Icon, Signed Releases & In-App Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the app with a screenshot README, an adaptive launcher icon, a signed tag-driven GitHub release pipeline, and a Morphe-style in-app updater that checks GitHub Releases, downloads and verifies the APK, and hands it to Android's installer.

**Architecture:** GitHub Releases is the single source of truth. A tag-derived Gradle version is built and signed by a GitHub Actions workflow, which attaches the APK and `SHA256SUMS` to the release. The app fetches `releases/latest`, compares a strict semantic version, and shows a Compose dialog; an explicit Update tap downloads the asset over HTTPS, verifies its SHA-256 and package identity, then launches the system installer through a FileProvider URI. All update strings live in the existing single-file translation catalog.

**Tech Stack:** Kotlin 2.1, Jetpack Compose Material 3, Room 2.7, `kotlinx.serialization.json`, `HttpURLConnection`, `FileProvider`, GitHub Actions, JUnit 4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-22-readme-signing-in-app-updates-design.md`

## Global Constraints

- **Platform floors:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, package `com.empathie.energyaccounting`.
- **No Google Play Services, Firebase, analytics, ads, or tracking.** The updater adds only `INTERNET` and `REQUEST_INSTALL_PACKAGES`.
- **No new production dependency for the updater.** Use `HttpURLConnection` and the existing `kotlinx.serialization.json`.
- **All user-facing strings** go in `app/translations.json` with non-blank `"en"` and `"it"` values. `syncTranslations` regenerates the XML resources.
- **Release identity is permanent.** One release keystore, alias `empathie`, never committed, backed up before the first release. Every future release uses the same key.
- **Version from tag:** `vMAJOR.MINOR.PATCH`; `versionName` is the tag without `v`; `versionCode = major × 10000 + minor × 100 + patch`. Local defaults are `0.3.0` / `300`.
- **Free runners only.** Standard `ubuntu-latest` GitHub-hosted runners; no larger runners, macOS, Windows, or local runners.
- **Update safety:** never download before an explicit Update tap; never install silently; verify SHA-256, package name, and signer; delete failed downloads.
- **Publishing is approval-gated:** the implementation may prepare the tree, workflows, and a locally signed build, but it must not push a release tag or create a GitHub Release without the owner's explicit go-ahead. Making the repository public and running the live `v0.4.1` test are deferred until the owner asks.
- **README screenshots** come from `mobile-mcp` on an API 35 emulator, using the synthetic demo backup. No mockups.
- **License:** GPL-3.0-only, copyright 2026 iseppe. Full GNU GPL v3 text in `LICENSE`; no per-file headers in this round.
- **Local environment:** `JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec`; Android SDK at `/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools` (already recorded in the gitignored `local.properties`).

## Review Focus

1. **Malformed or hostile release JSON:** missing APK asset, non-HTTPS download URL, missing digest and `SHA256SUMS`, prerelease/draft, malformed JSON, or an enormous release body must never produce an installable offer. Parser returns `null` or the source returns `null`; no unverifiable update is shown. Pinned in Task 8 and Task 9.
2. **24-hour throttle and snooze boundaries:** exactly 24 h, clock moved backwards, `lastCheck = 0`, same-version snooze still valid, newer version despite a snooze, and an expired snooze inside the throttle. Pinned in Task 7 and Task 12.
3. **Download and verification failures:** truncated download, checksum mismatch, package-name mismatch, signer mismatch, unreadable APK, low storage, and cancellation must delete the partial file, keep user data intact, and show a specific message. Pinned in Task 9, Task 10, and Task 12.
4. **Install-permission lifecycle:** user returns from Settings without enabling, enables it, cancels the system installer, or hits an OEM that ignores the intent. The app re-checks `canRequestPackageInstalls()`, never crashes, and never installs silently. Pinned in Task 10, Task 12, and the manual test in Task 14.
5. **Debug-versus-release identity:** debug builds must not check for or offer updates; a release APK cannot install over a debug build; the updater must refuse a differently signed APK instead of letting the system installer fail. Pinned in Task 10 and Task 12; documented in the README migration note in Task 2.

---

## File Structure

```text
README.md                                                               (Create)
LICENSE                                                                 (Create)
docs/RELEASING.md                                                       (Create)
docs/release-notes/v0.3.0.md                                            (Create)
docs/release-notes/v0.4.0.md                                            (Create)
docs/images/icon.svg                                                    (Create)
docs/screenshots/01-daily-account.png                                   (Create)
docs/screenshots/02-add-entry.png                                       (Create)
docs/screenshots/03-insights.png                                        (Create)
.github/workflows/ci.yml                                                (Create)
.github/workflows/release.yml                                           (Create)
app/build.gradle.kts                                                    (Modify: signing, version properties, BuildConfig)
app/src/main/AndroidManifest.xml                                        (Modify: icon, INTERNET, REQUEST_INSTALL_PACKAGES)
app/src/main/res/values/colors.xml                                      (Create: ic_launcher_background)
app/src/main/res/drawable/ic_launcher_foreground.xml                    (Create)
app/src/main/res/drawable/ic_launcher_monochrome.xml                    (Create)
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml                      (Create)
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml                (Create)
app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml                      (Create: monochrome)
app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml                (Create: monochrome)
app/src/main/res/xml/file_paths.xml                                     (Modify: updates cache path)
app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt                    (Modify)
app/src/main/kotlin/com/empathie/energyaccounting/update/SemanticVersion.kt                       (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateInfo.kt                            (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParser.kt                   (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdatePolicy.kt                          (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/HttpTransport.kt                         (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ReleaseSource.kt                         (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSource.kt                   (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ApkVerifier.kt                           (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ApkInstaller.kt                          (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateViewModel.kt                       (Create)
app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdateDialog.kt                       (Create)
app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdatesSection.kt                     (Create)
app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt                                (Modify)
app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt                 (Modify)
app/translations.json                                                   (Modify)
app/src/test/kotlin/com/empathie/energyaccounting/data/AppSettingsTest.kt                         (Modify)
app/src/test/kotlin/com/empathie/energyaccounting/ui/LauncherIconTest.kt                          (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/SemanticVersionTest.kt                   (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParserTest.kt               (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/UpdatePolicyTest.kt                      (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSourceTest.kt               (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/Sha256Test.kt                            (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/ApkInstallerIntentTest.kt                (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/UpdateViewModelTest.kt                   (Create)
```

---

### Task 1: Adaptive launcher icon

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- Create: `docs/images/icon.svg`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/ui/LauncherIconTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: manifest icon resources `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`; `docs/images/icon.svg` for the README.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    @Test
    fun `manifest declares both launcher and round icons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertNotEquals("application android:icon is missing", 0, info.icon)
        assertNotEquals("application android:roundIcon is missing", 0, info.roundIcon)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.ui.LauncherIconTest" --console=plain`

Expected: FAIL — `application android:icon is missing`.

- [ ] **Step 3: Add the icon resources and manifest attributes**

Use the geometry and path data in the spec's Appendix A. Create:

- `app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#131720</color>
</resources>
```

- `app/src/main/res/drawable/ic_launcher_foreground.xml` — a 108 × 108 dp vector. Draw the Lucide brain twice with `translate(22 22) scale(2.6667)`, clipping the left half at x = 12 and the right half at x = 12; left stroke `#5EC2A6`, right stroke `#E29B61`, stroke width `1.6`. Draw the Lucide bolt with `translate(35.04 35.04) scale(1.58)`, fill `#F7F5F0`, stroke `#131720`, stroke width `1.2`. Keep a comment: `<!-- Brain and bolt geometry: Lucide, ISC license. -->`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — the same brain path in one 108 × 108 vector, full stroke `#FFFFFF`, plus the bolt filled white with no dark outline.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml` and `ic_launcher_round.xml` — the same plus `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />`.
- `docs/images/icon.svg` — the 1024 × 1024 preview from spec Appendix A.
- In `AndroidManifest.xml`, add to `<application>`: `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.ui.LauncherIconTest" --console=plain`

Expected: PASS.

- [ ] **Step 5: Verify the packaged APK points at the icon**

Run:

```bash
./gradlew :app:assembleDebug --console=plain
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "application:|application-icon"
```

Expected: the application line names `res/mipmap-anydpi-v26/ic_launcher.xml` (or the v33 variant) and no `application-icon` error.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res app/src/main/AndroidManifest.xml docs/images/icon.svg app/src/test/kotlin/com/empathie/energyaccounting/ui/LauncherIconTest.kt
git commit -m "feat: add adaptive two-hemispheres launcher icon"
```

---

### Task 2: README, GPL-3.0 license, and emulator screenshots

**Files:**
- Create: `README.md`
- Create: `LICENSE`
- Create: `docs/screenshots/01-daily-account.png`
- Create: `docs/screenshots/02-add-entry.png`
- Create: `docs/screenshots/03-insights.png`

**Interfaces:**
- Consumes: `docs/images/icon.svg` from Task 1; `synthetic-data/energy-accounting-demo-backup.json`.
- Produces: the public README and its screenshot assets.

- [ ] **Step 1: Install the headless emulator**

No device is connected. Install the emulator and an AOSP API 35 image (about 1.5 GB). This is approved as part of this plan; if the download fails or disk space is insufficient, stop and ask.

```bash
export JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec
export ANDROID_HOME=/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

yes | sdkmanager --licenses
sdkmanager "emulator" "system-images;android-35;default;x86_64"
echo no | avdmanager create avd -n empathie -k "system-images;android-35;default;x86_64" -d pixel_7
```

Expected: `avdmanager` reports the AVD was created. If `pixel_7` is not a known device profile in this SDK, use `-d pixel` instead.

- [ ] **Step 2: Boot and configure the emulator**

```bash
"$ANDROID_HOME/emulator/emulator" -avd empathie -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
adb root
adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell settings put system time_12_24 24
adb shell setprop persist.sys.timezone Europe/Rome
adb shell "date 111309302026.00"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell settings get secure user_setup_complete
```

If a setup wizard blocks the launcher, set `adb shell settings put secure user_setup_complete 1` and `adb shell settings put global device_provisioned 1`, then reboot. If `adb root` fails, use the app's Developer Mode fake clock to set 13/11/2026 instead of changing the system clock.

- [ ] **Step 3: Install the debug build and demo data**

```bash
./gradlew :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push synthetic-data/energy-accounting-demo-backup.json /sdcard/Download/
```

Use `mobile_list_available_devices` to get the emulator device id. Pass it to every `mobile-mcp` call. Launch the app with `mobile_launch_app` (package `com.empathie.energyaccounting`).

- [ ] **Step 4: Restore the demo backup through the UI**

Using `mobile_list_elements_on_screen`, `mobile_click_on_screen_at_coordinates`, and `mobile_type_keys`:

1. Open the **Manage & Export** tab.
2. Tap **Restore JSON Backup**.
3. Choose **Replace all local data**.
4. In the system file picker, open **Downloads** and select `energy-accounting-demo-backup.json`.
5. Confirm the restore completes.

Expected: the Daily Account tab shows entries for **13/11/2026** across the four slots.

- [ ] **Step 5: Capture the Daily Account screenshot**

Open the **Daily Account** tab, stay on Today (13/11/2026), scroll to the top, and capture:

```text
mobile_save_screenshot(device, saveTo: "/var/home/iseppe/git/empathie-energy-accounting/docs/screenshots/01-daily-account.png", maxSize: 2400)
```

- [ ] **Step 6: Capture the Add Entry screenshot**

Tap the add button, ensure the sheet is fully open with templates, time-slot chips, dimension chips, direction selector, and the full-width slider visible. Do not show the keyboard. Capture to `docs/screenshots/02-add-entry.png`.

- [ ] **Step 7: Capture the Insights screenshot**

Close the sheet, open the **Insights** tab, select **Last 30 days**, ensure the trend chart and breakdowns are visible, and capture to `docs/screenshots/03-insights.png`.

- [ ] **Step 8: Resize and border the screenshots**

```bash
for f in docs/screenshots/0*.png; do
  magick "$f" -resize 720x -bordercolor '#D9D3C7' -border 1 "$f"
done
identify docs/screenshots/0*.png
```

Expected: three PNGs about 720 px wide. Inspect each with the `read` tool; they must contain no personal data, no dev-mode card, and no notification icons.

- [ ] **Step 9: Write `README.md`**

Use this structure and exact badge links:

```markdown
<p align="center">
  <img src="docs/images/icon.svg" width="88" alt="Empathie Energy Accounting icon">
</p>

# Empathie Energy Accounting

A private, offline-first Android ledger for the Energy Accounting method.

[![Download APK](https://img.shields.io/badge/Download-APK-2ea44f?logo=android&logoColor=white)](https://github.com/iseppe/energy-accounting/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#install)

<p align="center">
  <img src="docs/screenshots/01-daily-account.png" width="240" alt="Daily Account">
  <img src="docs/screenshots/02-add-entry.png" width="240" alt="Add Entry">
  <img src="docs/screenshots/03-insights.png" width="240" alt="Insights">
</p>

## What it is
...
## Features
...
## Install
...
## Privacy
...
## Build from source
...
## License
...
```

Required facts, not optional:

- Energy Accounting method credit: Maja Toudal and Tony Attwood.
- “Not medical advice.”
- Android 8.0 or newer; allow installs from unknown sources.
- In-app updates are available from v0.4.0; after installing v0.4.0 they need no file manager.
- Debug-to-release migration: export JSON backup, uninstall the debug build, install the release, restore.
- No account, ads, analytics, or trackers; all entries stay in a local Room database; the only network access is the optional GitHub update check.
- Build: JDK 17+, Android SDK 35, `./gradlew assembleDebug`, test `./gradlew testDebugUnitTest`, edit `app/translations.json` rather than the generated XML.
- GPL-3.0-only license; full text in `LICENSE`; copyright 2026 iseppe.
- Credits: Lucide brain/zap glyphs (ISC) used in the launcher icon.

- [ ] **Step 10: Write `LICENSE`**

Download the official GNU GPL v3 text and verify it:

```bash
curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE
head -1 LICENSE
grep -c "GNU GENERAL PUBLIC LICENSE" LICENSE
```

Expected: the first line is `GNU GENERAL PUBLIC LICENSE` and the text contains `Version 3, 29 June 2007`. Do not modify the license text; the copyright notice lives in the README (`Copyright (C) 2026 iseppe. Licensed under GPL-3.0-only.`).

- [ ] **Step 11: Verify the README and images**

Run:

```bash
grep -n "TODO\|TBD\|placeholder" README.md
test -f LICENSE
for f in docs/screenshots/0*.png; do test -f "$f"; done
git status --short
```

Expected: no placeholder matches; all files exist.

- [ ] **Step 12: Commit**

```bash
git add README.md LICENSE docs/screenshots docs/images/icon.svg
git commit -m "docs: add README, screenshots and GPL-3.0 license"
```

---

### Task 3: Tag-driven versioning and release signing config

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: Gradle properties `appVersionName` / `appVersionCode`; environment-driven `release` signing config; `BuildConfig.UPDATE_REPO`; `BuildConfig.BUILD_ID` in the form `v<version>-<shortSha>`.

- [ ] **Step 1: Demonstrate the current version ignores Gradle properties**

Run:

```bash
export JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec
export ANDROID_HOME=/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools
./gradlew :app:assembleDebug -PappVersionName=9.9.9 -PappVersionCode=999 --console=plain
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

Expected: `versionCode='2' versionName='0.2.0'` — the properties are ignored.

- [ ] **Step 2: Modify `app/build.gradle.kts`**

At the top, after `gitShortSha()`:

```kotlin
val appVersionName: String = providers.gradleProperty("appVersionName").orElse("0.3.0").get()
val appVersionCode: Int = providers.gradleProperty("appVersionCode").map { it.toInt() }.orElse(300).get()
```

Replace the hardcoded `versionCode = 2` / `versionName = "0.2.0"` with the two values. Replace the `BUILD_ID` field with:

```kotlin
buildConfigField("String", "BUILD_ID", "\"v$appVersionName-${gitShortSha()}\"")
buildConfigField("String", "UPDATE_REPO", "\"iseppe/energy-accounting\"")
```

Add a release signing config before `buildTypes`:

```kotlin
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull

signingConfigs {
    if (
        releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
}
```

In `buildTypes.release`, set `signingConfig = signingConfigs.findByName("release")`. Remove the now-unused `LocalDateTime` / timestamp imports.

- [ ] **Step 3: Ignore keystore files**

Append to `.gitignore`:

```text
*.jks
*.keystore
keystore.properties
```

- [ ] **Step 4: Run the property build and verify the version**

Run:

```bash
./gradlew :app:assembleDebug -PappVersionName=9.9.9 -PappVersionCode=999 --console=plain
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

Expected: `versionCode='999' versionName='9.9.9'`.

- [ ] **Step 5: Run the unit tests**

Run: `./gradlew :app:testDebugUnitTest --console=plain`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts .gitignore
git commit -m "build: derive version from tag and add release signing config"
```

---

### Task 4: Release keystore, GitHub secrets, and maintainer notes

**Files:**
- Create: `docs/RELEASING.md`
- Modify: `.gitignore` (only if Task 3 missed an entry)

**Interfaces:**
- Consumes: the signing config from Task 3.
- Produces: `~/.android/keystores/empathie-release.jks`, its password file, four GitHub Actions secrets, and `docs/RELEASING.md`.

- [ ] **Step 1: Generate the release keystore**

```bash
export JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p ~/.android/keystores
chmod 700 ~/.android/keystores
umask 077
openssl rand -base64 32 > ~/.android/keystores/empathie-release.password
keytool -genkeypair -v \
  -keystore ~/.android/keystores/empathie-release.jks \
  -alias empathie \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Empathie Energy Accounting, O=Empathie, C=IT" \
  -storepass "$(< ~/.android/keystores/empathie-release.password)" \
  -keypass "$(< ~/.android/keystores/empathie-release.password)"
keytool -list -v -keystore ~/.android/keystores/empathie-release.jks \
  -storepass "$(< ~/.android/keystores/empathie-release.password)" | head -20
```

Expected: one RSA 4096-bit entry with alias `empathie`. Do not print the password.

- [ ] **Step 2: Store the four GitHub Actions secrets**

```bash
base64 -w0 ~/.android/keystores/empathie-release.jks \
  | gh secret set ANDROID_KEYSTORE_BASE64 -R iseppe/energy-accounting
gh secret set ANDROID_KEYSTORE_PASSWORD -R iseppe/energy-accounting \
  < ~/.android/keystores/empathie-release.password
gh secret set ANDROID_KEY_ALIAS -R iseppe/energy-accounting --body empathie
gh secret set ANDROID_KEY_PASSWORD -R iseppe/energy-accounting \
  < ~/.android/keystores/empathie-release.password
gh secret list -R iseppe/energy-accounting
```

Expected: the four secret names are listed. Values are never printed.

- [ ] **Step 3: Build a signed release locally**

```bash
export ANDROID_KEYSTORE_PATH="$HOME/.android/keystores/empathie-release.jks"
export ANDROID_KEYSTORE_PASSWORD="$(< ~/.android/keystores/empathie-release.password)"
export ANDROID_KEY_ALIAS=empathie
export ANDROID_KEY_PASSWORD="$ANDROID_KEYSTORE_PASSWORD"
./gradlew :app:assembleRelease -PappVersionName=0.3.0 -PappVersionCode=300 --console=plain
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging \
  app/build/outputs/apk/release/app-release.apk | sed -n '1p'
```

Expected: `apksigner` reports verified v1/v2/v3 schemes and the RSA 4096 certificate; `aapt` reports `versionCode='300' versionName='0.3.0'`.

- [ ] **Step 4: Write `docs/RELEASING.md`**

Document, without secret values:

- Keystore path and alias; the fact that the key and password must be backed up in two places.
- The four secret names and how to set them.
- The local signed-build environment variables.
- The tag convention and version-code formula.
- The release steps: clean tree, tests pass, write `docs/release-notes/<tag>.md`, tag `vX.Y.Z`, push to `upstream`, watch the workflow, verify assets/checksum/signature.
- The debug-to-release migration note.
- The temporary `v0.4.1` updater test and its cleanup.
- The release-notes file convention: if `docs/release-notes/<tag>.md` exists, the workflow uses it instead of generated notes.

- [ ] **Step 5: Tell the user to back up the key**

Say where the keystore and password file are and that losing them breaks update continuity. Do not print the password. This is a required handoff, not a code step.

- [ ] **Step 6: Stop at the backup gate**

Do not proceed to Task 6 (the first release tag) until the user explicitly confirms that the keystore and password are backed up in two off-machine places and stored in a password manager. Setting the GitHub secrets before this gate is fine; publishing a release is not.

- [ ] **Step 7: Commit the maintainer notes**

```bash
git add docs/RELEASING.md .gitignore
git commit -m "docs: document signing and release process"
```

---

### Task 5: CI and tag-triggered release workflows

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: the Gradle properties and signing environment from Task 3, and the secrets from Task 4.
- Produces: a CI workflow on `master`/PRs and a release workflow on `v*` tags that creates a GitHub Release with the APK and `SHA256SUMS`.

- [ ] **Step 1: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [master]
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v4
      - name: Install Android SDK packages
        run: |
          sdkmanager "platforms;android-35" "build-tools;35.0.0"
      - name: Test and build
        run: ./gradlew testDebugUnitTest assembleDebug --console=plain
```

- [ ] **Step 2: Validate the CI YAML**

Run:

```bash
python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/ci.yml
```

Expected: no output.

- [ ] **Step 3: Write `.github/workflows/release.yml`**

```yaml
name: Release APK

on:
  push:
    tags: ['v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - uses: android-actions/setup-android@v4
      - name: Install Android SDK packages
        run: |
          sdkmanager "platforms;android-35" "build-tools;35.0.0"
      - name: Decode release keystore
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: |
          set -euo pipefail
          test -n "$ANDROID_KEYSTORE_BASE64"
          umask 077
          echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"
          echo "ANDROID_KEYSTORE_PATH=$RUNNER_TEMP/release.jks" >> "$GITHUB_ENV"
      - name: Derive version from tag
        run: |
          set -euo pipefail
          VERSION="${GITHUB_REF_NAME#v}"
          if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            echo "Tag must be vMAJOR.MINOR.PATCH" >&2
            exit 1
          fi
          IFS=. read -r MAJOR MINOR PATCH <<< "$VERSION"
          CODE=$((10#$MAJOR * 10000 + 10#$MINOR * 100 + 10#$PATCH))
          echo "APP_VERSION=$VERSION" >> "$GITHUB_ENV"
          echo "APP_VERSION_CODE=$CODE" >> "$GITHUB_ENV"
      - name: Test
        run: ./gradlew testDebugUnitTest --console=plain
      - name: Build signed release
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          ./gradlew assembleRelease \
            -PappVersionName="$APP_VERSION" \
            -PappVersionCode="$APP_VERSION_CODE" \
            --console=plain
      - name: Verify and package
        run: |
          set -euo pipefail
          APK="app/build/outputs/apk/release/app-release.apk"
          test -f "$APK"
          "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" | sed -n '1p'
          "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" \
            | grep -F "versionCode='$APP_VERSION_CODE' versionName='$APP_VERSION'"
          "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$APK"
          mkdir -p dist
          cp "$APK" "dist/empathie-energy-accounting-v$APP_VERSION.apk"
          (cd dist && sha256sum *.apk > SHA256SUMS)
      - name: Create GitHub release
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -euo pipefail
          NOTES_ARGS=(--generate-notes)
          if [ -f "docs/release-notes/$GITHUB_REF_NAME.md" ]; then
            NOTES_ARGS=(--notes-file "docs/release-notes/$GITHUB_REF_NAME.md")
          fi
          gh release create "$GITHUB_REF_NAME" \
            --title "Empathie Energy Accounting $GITHUB_REF_NAME" \
            "${NOTES_ARGS[@]}" \
            "dist/empathie-energy-accounting-v$APP_VERSION.apk" \
            "dist/SHA256SUMS"
```

- [ ] **Step 4: Validate the release YAML**

Run:

```bash
python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/release.yml
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows
git commit -m "ci: add test workflow and signed tag-driven release workflow"
```

---

### Task 6: First signed release v0.3.0

**Files:**
- Create: `docs/release-notes/v0.3.0.md`

**Interfaces:**
- Consumes: Tasks 3–5.
- Produces: GitHub Release `v0.3.0` with a signed APK and `SHA256SUMS`; proof the pipeline works.

- [ ] **Step 1: Write and commit `docs/release-notes/v0.3.0.md`**

Include a short feature summary and, prominently:

```markdown
## Upgrading from a debug build

This is the first release signed with the project's release key. Android will
not install it over a debug-signed build. Export a JSON backup from the debug
app first, uninstall it, install this APK, then restore the backup.
```

Commit it:

```bash
git add docs/release-notes/v0.3.0.md
git commit -m "docs: add v0.3.0 release notes"
```

- [ ] **Step 2: Confirm the tree and tests are clean**

```bash
git status --short
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: no uncommitted changes, BUILD SUCCESSFUL.

- [ ] **Step 3: Stop for explicit publishing approval**

Do not push the tag yet. Ask the user for explicit permission to publish the private `v0.3.0` release. If they prefer, hand them the commands and stop here. This plan is allowed to prepare the release, but not to publish it unasked.

- [ ] **Step 4: Push `master`, then tag and push**

The repository's only remote is named `upstream`.

```bash
git push upstream master
git tag -a v0.3.0 -m "Empathie Energy Accounting v0.3.0"
git push upstream v0.3.0
```

- [ ] **Step 5: Watch the release workflow**

```bash
gh run list -R iseppe/energy-accounting --limit 5
gh run watch -R iseppe/energy-accounting <run-id>
```

Expected: the `Release APK` run succeeds.

- [ ] **Step 6: Verify the release assets and notes**

```bash
gh release view v0.3.0 -R iseppe/energy-accounting
mkdir -p /tmp/opencode/release-v0.3.0
gh release download v0.3.0 -R iseppe/energy-accounting -D /tmp/opencode/release-v0.3.0
(cd /tmp/opencode/release-v0.3.0 && sha256sum -c SHA256SUMS)
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  /tmp/opencode/release-v0.3.0/empathie-energy-accounting-v0.3.0.apk
```

Expected: APK and `SHA256SUMS` are attached; checksum OK; signature verified; the release body contains the migration warning.

- [ ] **Step 7: If the workflow fails**

Read `gh run view <run-id> --log-failed`. Fix the workflow or Gradle config, commit, delete the tag and release locally/remotely, and re-tag the fixed commit. Do not reuse the broken tag.

- [ ] **Step 8: Stop and hand off the data warning**

Before anyone installs the release-signed APK over an existing debug build, export a JSON backup. Tell the user this explicitly. This step produces no commit.

---

### Task 7: Semantic version and update policy

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/SemanticVersion.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/UpdatePolicy.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/SemanticVersionTest.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/UpdatePolicyTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion>` with `companion object { fun parse(raw: String?): SemanticVersion? }` and `toString() = "$major.$minor.$patch"`.
  - `object UpdatePolicy` with `const val CHECK_INTERVAL_MS = 86_400_000L`, `const val SNOOZE_INTERVAL_MS = 86_400_000L`, `fun shouldCheck(now: Long, lastCheck: Long, enabled: Boolean): Boolean`, and `fun isSnoozed(version: String, snoozedVersion: String?, snoozedUntil: Long, now: Long): Boolean`.

- [ ] **Step 1: Write the failing version tests**

```kotlin
package com.empathie.energyaccounting.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `parses tags with and without a v prefix`() {
        assertEquals(SemanticVersion(0, 4, 0), SemanticVersion.parse("v0.4.0"))
        assertEquals(SemanticVersion(0, 4, 0), SemanticVersion.parse("0.4.0"))
        assertEquals(SemanticVersion(1, 12, 3), SemanticVersion.parse("v1.12.3"))
    }

    @Test
    fun `rejects incomplete, prerelease and junk tags`() {
        listOf(null, "", "v0.4", "0.4.0-beta", "v0.4.0.1", "banana", "v1.2.x").forEach {
            assertNull("should reject $it", SemanticVersion.parse(it))
        }
    }

    @Test
    fun `orders versions numerically`() {
        assertTrue(SemanticVersion.parse("0.4.0")!! > SemanticVersion.parse("0.3.9")!!)
        assertTrue(SemanticVersion.parse("1.0.0")!! > SemanticVersion.parse("0.99.99")!!)
        assertEquals("0.4.0", SemanticVersion(0, 4, 0).toString())
    }
}
```

- [ ] **Step 2: Run the version tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.SemanticVersionTest" --console=plain`

Expected: FAIL — unresolved reference `SemanticVersion`.

- [ ] **Step 3: Implement `SemanticVersion`**

Strict regex `^v?(\\d+)\\.(\\d+)\\.(\\d+)$`; parse with `toIntOrNull`; reject overflow. Implement `Comparable` lexicographically by major/minor/patch. `toString()` returns the dotted triple.

- [ ] **Step 4: Write the failing policy tests**

```kotlin
package com.empathie.energyaccounting.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `disabled checks never run`() {
        assertFalse(UpdatePolicy.shouldCheck(now, 0, enabled = false))
    }

    @Test
    fun `checks when never checked or at the 24 hour boundary`() {
        assertTrue(UpdatePolicy.shouldCheck(now, 0, enabled = true))
        assertTrue(UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS, enabled = true))
        assertFalse(UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS + 1, enabled = true))
    }

    @Test
    fun `a clock moved backwards triggers a check`() {
        assertTrue(UpdatePolicy.shouldCheck(now, now + 60_000, enabled = true))
    }

    @Test
    fun `snooze applies only to the same version before expiry`() {
        assertTrue(UpdatePolicy.isSnoozed("0.4.0", "0.4.0", now + 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.0", "0.4.0", now - 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.1", "0.4.0", now + 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.0", null, 0, now))
    }
}
```

- [ ] **Step 5: Run the policy tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.UpdatePolicyTest" --console=plain`

Expected: FAIL — unresolved reference `UpdatePolicy`.

- [ ] **Step 6: Implement `UpdatePolicy`**

Use the exact constants and signatures above. `shouldCheck` returns `enabled && (lastCheck <= 0 || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS)`. `isSnoozed` returns `snoozedVersion == version && now < snoozedUntil`.

- [ ] **Step 7: Run both tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.*" --console=plain`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/update/SemanticVersion.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/update/UpdatePolicy.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/SemanticVersionTest.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/UpdatePolicyTest.kt
git commit -m "feat: add update version and policy primitives"
```

---

### Task 8: GitHub release parsing

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateInfo.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParser.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParserTest.kt`

**Interfaces:**
- Consumes: `SemanticVersion` from Task 7.
- Produces:
  - `data class UpdateInfo(version, tagName, releaseName, releaseNotes, releaseUrl, apkUrl, apkAssetName, apkSizeBytes, apkSha256, checksumsUrl: String? = null)`.
  - `object GitHubReleaseParser { fun parse(json: String): UpdateInfo? }`.

- [ ] **Step 1: Write the failing parser tests**

Use a fixture constant `VALID_JSON` with `tag_name: "v0.4.0"`, `body: "## What's new\n- In-app updates"`, one APK asset with `digest: "sha256:<64 hex>"`, and one `SHA256SUMS` asset.

Assertions:

```kotlin
@Test fun `parses a valid release and selects the APK asset`() {
    val info = GitHubReleaseParser.parse(VALID_JSON)!!
    assertEquals(SemanticVersion(0, 4, 0), info.version)
    assertEquals("empathie-energy-accounting-v0.4.0.apk", info.apkAssetName)
    assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", info.apkSha256)
    assertTrue(info.releaseNotes.contains("In-app updates"))
}

@Test fun `keeps the checksums URL when no digest is present`() { ... }

@Test fun `rejects no APK, drafts, prereleases, non-HTTPS assets and malformed JSON`() { ... }

@Test fun `truncates an enormous release body`() { ... }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.GitHubReleaseParserTest" --console=plain`

Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `UpdateInfo` and `GitHubReleaseParser`**

Use `kotlinx.serialization` DTOs with `ignoreUnknownKeys = true`. Reject `draft`/`prerelease`; parse the version; choose the first `.apk` asset; require `https://`; accept a digest matching `sha256:[0-9a-fA-F]{64}` and lowercase it; otherwise leave `apkSha256 = ""` and set `checksumsUrl` from the `SHA256SUMS` asset. Truncate `body` to 8,000 characters. Return null on any failure.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.GitHubReleaseParserTest" --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateInfo.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParser.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParserTest.kt
git commit -m "feat: parse GitHub release metadata"
```

---

### Task 9: Network release source

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/HttpTransport.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/ReleaseSource.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSource.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSourceTest.kt`

**Interfaces:**
- Consumes: `UpdateInfo`, `GitHubReleaseParser`.
- Produces:
  - `interface HttpTransport { fun getText(url: String, headers: Map<String, String>): String; fun download(url: String, destination: File, onProgress: (Long) -> Unit) }`
  - `class HttpStatusException(val code: Int) : IOException("HTTP $code")`
  - `interface ReleaseSource { suspend fun latestRelease(): UpdateInfo?; suspend fun downloadApk(info: UpdateInfo, destination: File, onProgress: (Long, Long) -> Unit) }`
  - `class GitHubReleaseSource(private val repo: String, private val transport: HttpTransport = UrlConnectionTransport()) : ReleaseSource`

- [ ] **Step 1: Write the failing source tests**

Create a `FakeTransport` that records URLs/headers and returns queued JSON/checksum strings or writes bytes. Tests:

```kotlin
@Test fun `latestRelease returns a release with the digest`() { ... }

@Test fun `latestRelease resolves the hash from SHA256SUMS when the digest is absent`() {
    transport.textResponses += VALID_JSON_WITHOUT_DIGEST
    transport.textResponses += "0123...  empathie-energy-accounting-v0.4.0.apk\n"
    val info = GitHubReleaseSource("iseppe/energy-accounting", transport).latestRelease()!!
    assertEquals("0123...", info.apkSha256)
}

@Test fun `latestRelease returns null on 404 and propagates other network errors`() { ... }

@Test fun `downloadApk writes the bytes and reports progress`() { ... }

@Test fun `downloadApk fails when the byte count does not match`() { ... }
```

Use the 64-hex string consistently.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.GitHubReleaseSourceTest" --console=plain`

Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the transport, source and URL handling**

`UrlConnectionTransport`:

- `getText`: `HttpURLConnection`, 10 s connect, 20 s read, set headers, read at most 1 MB, throw `HttpStatusException(code)` for non-2xx.
- `download`: 15 s connect, 30 s read, `instanceFollowRedirects = true`, stream to the destination in 64 KB chunks, call `onProgress` at most every 250 ms or 256 KB.

`GitHubReleaseSource`:

- API URL `https://api.github.com/repos/$repo/releases/latest`; headers `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`, `User-Agent: EmpathieEnergyAccounting/<version>` (use `BuildConfig.VERSION_NAME`? `GitHubReleaseSource` is pure JVM; pass the user agent in the constructor or use a constant. Use `private val userAgent: String = "EmpathieEnergyAccounting"` with an optional constructor parameter).
- Catch `HttpStatusException(404)` and return null.
- Parse; if `apkSha256` is blank, GET `checksumsUrl` (at most 64 KB), parse lines `<hash>  <name>` handling CRLF, and find `apkAssetName`. If missing, return null.
- `downloadApk`: call `transport.download(info.apkUrl, destination) { downloaded -> currentCoroutineContext().ensureActive(); onProgress(downloaded, info.apkSizeBytes) }`; verify the final length when `apkSizeBytes > 0`; otherwise throw `IOException`.
- Wrap blocking calls in `withContext(Dispatchers.IO)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.GitHubReleaseSourceTest" --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/update/HttpTransport.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/update/ReleaseSource.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSource.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSourceTest.kt
git commit -m "feat: fetch and download GitHub release assets"
```

---

### Task 10: APK verification, installer, manifest and FileProvider

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/ApkVerifier.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/ApkInstaller.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/file_paths.xml`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/Sha256Test.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/ApkInstallerIntentTest.kt`

**Interfaces:**
- Consumes: `UpdateInfo` from Task 8.
- Produces:
  - `sealed interface VerificationResult { data object Valid; data class Invalid(val error: VerificationError) }`
  - `enum class VerificationError { CHECKSUM, PACKAGE, SIGNATURE, UNREADABLE }`
  - `interface ApkVerifier { fun verify(apk: File, expectedSha256: String, expectedPackageName: String): VerificationResult }`
  - `class AndroidApkVerifier(private val context: Context) : ApkVerifier`
  - `interface ApkInstaller { fun canInstall(): Boolean; fun openInstallPermissionSettings(); fun install(apk: File) }`
  - `class AndroidApkInstaller(private val context: Context) : ApkInstaller` with `internal fun installIntent(apkUri: Uri): Intent` and `internal fun unknownSourcesIntent(): Intent`.

- [ ] **Step 1: Write the failing SHA-256 and intent tests**

```kotlin
@Test fun `sha256 of known bytes is stable`() {
    val file = File.createTempFile("sha", ".txt").apply { writeText("hello") }
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Sha256.of(file))
}

@Test fun `install intent grants read access to an APK content URI`() {
    val installer = AndroidApkInstaller(ApplicationProvider.getApplicationContext())
    val intent = installer.installIntent(Uri.parse("content://com.empathie.energyaccounting.fileprovider/updates/app.apk"))
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("application/vnd.android.package-archive", intent.type)
    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
}

@Test fun `unknown sources intent targets this package`() {
    val installer = AndroidApkInstaller(ApplicationProvider.getApplicationContext())
    val intent = installer.unknownSourcesIntent()
    assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
    assertEquals("package:com.empathie.energyaccounting", intent.data.toString())
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.Sha256Test" --tests "com.empathie.energyaccounting.update.ApkInstallerIntentTest" --console=plain`

Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `Sha256`, `AndroidApkVerifier` and `AndroidApkInstaller`**

`Sha256.of(file)` streams 64 KB chunks into `MessageDigest.getInstance("SHA-256")` and returns lowercase hex.

`AndroidApkVerifier`:

- Compare the file's SHA-256 with `expectedSha256`; mismatch → `CHECKSUM`.
- `getPackageArchiveInfo` with `GET_SIGNING_CERTIFICATES` (API 28+) or `GET_SIGNATURES` (API 26–27); null → `UNREADABLE`; package name mismatch → `PACKAGE`.
- Read the installed app's signer the same way. Compare the first certificate bytes. Empty archive signer or mismatch → `SIGNATURE`.

`AndroidApkInstaller`:

- `canInstall()` → `context.packageManager.canRequestPackageInstalls()`.
- `unknownSourcesIntent()` → `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))` plus `FLAG_ACTIVITY_NEW_TASK`.
- `installIntent(apkUri)` → `Intent(ACTION_VIEW).setDataAndType(apkUri, "application/vnd.android.package-archive").addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)`.
- `install(apk)` → FileProvider URI with authority `${context.packageName}.fileprovider`, start the intent, catch `ActivityNotFoundException`.

- [ ] **Step 4: Add the manifest permission and FileProvider path**

In `AndroidManifest.xml`, add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

In `app/src/main/res/xml/file_paths.xml`, add beside the reports path:

```xml
<cache-path name="updates" path="updates/" />
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.*" --console=plain`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/update/ApkVerifier.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/update/ApkInstaller.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/xml/file_paths.xml \
        app/src/test/kotlin/com/empathie/energyaccounting/update/Sha256Test.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/ApkInstallerIntentTest.kt
git commit -m "feat: verify downloaded APKs and launch the system installer"
```

---

### Task 11: Update preferences

**Files:**
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/data/AppSettingsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `updateChecksEnabled`, `lastUpdateCheckEpochMillis`, `snoozedUpdateVersion`, `snoozedUpdateUntilEpochMillis`, `snoozeUpdate(version, untilEpochMillis)`, `clearUpdateSnooze()`, and their setters.

- [ ] **Step 1: Write the failing tests**

Add to `AppSettingsTest`:

```kotlin
@Test
fun `update preferences default to automatic, never checked and no snooze`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE).edit().clear().commit()
    val settings = AppSettings(context)

    assertTrue(settings.updateChecksEnabled())
    assertEquals(0L, settings.lastUpdateCheckEpochMillis())
    assertNull(settings.snoozedUpdateVersion())
    assertEquals(0L, settings.snoozedUpdateUntilEpochMillis())
}

@Test
fun `update preferences persist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("empathie_settings", Context.MODE_PRIVATE).edit().clear().commit()
    val settings = AppSettings(context)

    settings.setUpdateChecksEnabled(false)
    settings.setLastUpdateCheckEpochMillis(1234L)
    settings.snoozeUpdate("0.4.0", 5678L)

    assertFalse(AppSettings(context).updateChecksEnabled())
    assertEquals(1234L, AppSettings(context).lastUpdateCheckEpochMillis())
    assertEquals("0.4.0", AppSettings(context).snoozedUpdateVersion())
    assertEquals(5678L, AppSettings(context).snoozedUpdateUntilEpochMillis())

    settings.clearUpdateSnooze()
    assertNull(settings.snoozedUpdateVersion())
    assertEquals(0L, settings.snoozedUpdateUntilEpochMillis())
}
```

Add imports for `assertNull`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.data.settings.AppSettingsTest" --console=plain`

Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the preferences**

Add keys `update_checks_enabled`, `last_update_check_epoch`, `snoozed_update_version`, `snoozed_update_until` and the methods listed above. Defaults: true, 0L, null, 0L. `snoozeUpdate` writes both the version and the until value. `clearUpdateSnooze` removes both keys.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.data.settings.AppSettingsTest" --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/data/AppSettingsTest.kt
git commit -m "feat: persist update check and snooze preferences"
```

---

### Task 12: UpdateViewModel state machine

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateViewModel.kt`
- Test: `app/src/test/kotlin/com/empathie/energyaccounting/update/UpdateViewModelTest.kt`

**Interfaces:**
- Consumes: `SemanticVersion`, `UpdateInfo`, `UpdatePolicy`, `ReleaseSource`, `ApkVerifier`, `VerificationResult`, `ApkInstaller`, `AppSettings`.
- Produces:

```kotlin
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class Verifying(val info: UpdateInfo) : UpdateUiState
    data class PermissionRequired(val info: UpdateInfo) : UpdateUiState
    data class Installing(val info: UpdateInfo) : UpdateUiState
    data class Error(val info: UpdateInfo?, val error: VerificationError?, val kind: UpdateError) : UpdateUiState
}

enum class UpdateError { NETWORK, STORAGE, INSTALLER, UNKNOWN }

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object UpToDate : UpdateCheckStatus
    data object Failed : UpdateCheckStatus
}

class UpdateViewModel(
    private val source: ReleaseSource,
    private val settings: AppSettings,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val cacheDir: File,
    private val currentVersion: SemanticVersion,
    private val currentVersionName: String,
    private val isReleaseBuild: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel()
```

Public members: `state: StateFlow<UpdateUiState>`, `checkStatus: StateFlow<UpdateCheckStatus>`, `checksEnabled: StateFlow<Boolean>`, `checkOnLaunch()`, `checkNow()`, `onResume()`, `onLater()`, `onUpdate()`, `cancelDownload()`, `retry()`, `setChecksEnabled(Boolean)`, `openInstallSettings()`, `showDebugDialog()`.

- [ ] **Step 1: Write the failing ViewModel tests**

Create fakes:

```kotlin
private class FakeReleaseSource(
    var release: UpdateInfo? = null,
    var error: Exception? = null,
) : ReleaseSource {
    var latestCalls = 0
    var downloadCalls = 0
    override suspend fun latestRelease(): UpdateInfo? {
        latestCalls++
        error?.let { throw it }
        return release
    }
    override suspend fun downloadApk(info: UpdateInfo, destination: File, onProgress: (Long, Long) -> Unit) {
        downloadCalls++
        destination.writeBytes(byteArrayOf(1, 2, 3))
        onProgress(3, info.apkSizeBytes)
    }
}

private class FakeVerifier(var result: VerificationResult = VerificationResult.Valid) : ApkVerifier {
    var seen: File? = null
    override fun verify(apk: File, expectedSha256: String, expectedPackageName: String): VerificationResult {
        seen = apk
        return result
    }
}

private class FakeInstaller(var canInstallValue: Boolean = true) : ApkInstaller {
    var installed: File? = null
    var openedSettings = 0
    override fun canInstall() = canInstallValue
    override fun openInstallPermissionSettings() { openedSettings++ }
    override fun install(apk: File) { installed = apk }
}
```

Tests, each with `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before` and `resetMain()` in `@After`:

```kotlin
@Test fun `debug builds never check`()
@Test fun `disabled settings never check`()
@Test fun `automatic check is throttled inside 24 hours`()
@Test fun `a snooze expiry forces a check inside the throttle`()
@Test fun `a newer release becomes Available`()
@Test fun `an equal or older release is UpToDate`()
@Test fun `Later snoozes the same version`()
@Test fun `Update downloads, verifies and installs`()
@Test fun `checksum failure deletes the file and shows a checksum error`()
@Test fun `signature or package failure shows the matching error`()
@Test fun `permission required pauses until the user returns`()
```

Key assertions:

- Debug/disabled/throttled: `source.latestCalls == 0`.
- Newer: `vm.state.value is UpdateUiState.Available` and `(state as Available).info.version == SemanticVersion(0,4,0)`.
- Equal: `state.value is Idle`, `checkStatus.value == UpToDate`.
- Later: after `onLater()`, `settings.snoozedUpdateVersion() == "0.4.0"`, `state.value is Idle`; advance `now` by 24 h + 1 and call `checkOnLaunch()` → Available.
- Update: after `onUpdate()`, `installer.installed != null`, `state.value is Idle`.
- Checksum failure: `verifier.result = Invalid(CHECKSUM)`, after `onUpdate()` `state.value is Error`, `(state as Error).error == CHECKSUM`, and the verifier's `seen` file does not exist.
- Signature/package failure: `verifier.result = Invalid(SIGNATURE)` (or `PACKAGE`) produces `Error` with the same `VerificationError` and deletes the file.
- Permission: `installer.canInstallValue = false`, `onUpdate()` → PermissionRequired; set true, `onResume()` → installed.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.UpdateViewModelTest" --console=plain`

Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `UpdateViewModel`**

Follow spec §5.6 exactly:

- `init` cleans `.part` files and APKs older than 24 h in `cacheDir/updates`.
- `checkOnLaunch` applies the debug/disabled/state/snooze-expiry/throttle guards.
- `check(manual)` runs the source, updates `lastCheck` only on success, compares versions, and applies `UpdatePolicy.isSnoozed` for automatic checks.
- `onLater` snoozes the version for `UpdatePolicy.SNOOZE_INTERVAL_MS`.
- `onUpdate` checks `installer.canInstall()`, checks free space (`cacheDir.usableSpace < info.apkSizeBytes * 2` → STORAGE), then starts the download job.
- Download to `cacheDir/updates/<asset>.part`, rename, verify, delete on failure, install on success. On install, snooze the version for 24 h before `installer.install`.
- `cancelDownload` cancels the job and deletes the part.
- `setChecksEnabled(false)` cancels a download, clears the dialog, and stops checks.
- `openInstallSettings` delegates to the installer.
- `showDebugDialog` sets `Available` with a fabricated next version and blank `apkUrl`; `onUpdate` refuses a blank URL with `UNKNOWN`.
- Map `IOException` to NETWORK, `ActivityNotFoundException`/installer failure to INSTALLER, verification errors to the matching UI error.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.empathie.energyaccounting.update.UpdateViewModelTest" --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateViewModel.kt \
        app/src/test/kotlin/com/empathie/energyaccounting/update/UpdateViewModelTest.kt
git commit -m "feat: add update check and install state machine"
```

---

### Task 13: Update dialog, Updates card, and UI wiring

**Files:**
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdateDialog.kt`
- Create: `app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdatesSection.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt`
- Modify: `app/translations.json`

**Interfaces:**
- Consumes: `UpdateViewModel`, `UpdateUiState`, `UpdateCheckStatus`, the string keys in spec §4.7.
- Produces: the floating dialog and the Manage & Export Updates card.

- [ ] **Step 1: Add the translation keys**

Merge the keys from spec §4.7 into `app/translations.json`, keeping the existing JSON structure. Every key needs `"en"` and `"it"`.

- [ ] **Step 2: Create `UpdateDialog.kt`**

```kotlin
@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current

    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) return

    Dialog(
        onDismissRequest = viewModel::onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title, version line, release notes (scrollable, heightIn(max = 180.dp)),
                // progress states, and footer buttons per spec §4.6.
            }
        }
    }
}
```

Implement every state from spec §4.6. Use `LocalAppHaptics` on button taps. Open release URLs with `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))` inside `runCatching`. Use `stringResource` for every label. The `Update` button is disabled when `info.apkUrl.isBlank()` (the dev preview).

- [ ] **Step 3: Create `UpdatesSection.kt`**

```kotlin
@Composable
fun UpdatesSection(viewModel: UpdateViewModel) {
    val checksEnabled by viewModel.checksEnabled.collectAsStateWithLifecycle()
    val checkStatus by viewModel.checkStatus.collectAsStateWithLifecycle()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.update_check_automatic), modifier = Modifier.weight(1f))
                Switch(checked = checksEnabled, onCheckedChange = viewModel::setChecksEnabled)
            }
            Row {
                Text(stringResource(R.string.update_current_version), modifier = Modifier.weight(1f))
                Text("v${BuildConfig.VERSION_NAME}")
            }
            Button(
                onClick = { viewModel.checkNow() },
                enabled = checkStatus != UpdateCheckStatus.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (checkStatus == UpdateCheckStatus.Checking) R.string.update_checking else R.string.update_check_now))
            }
            when (checkStatus) {
                UpdateCheckStatus.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodySmall)
                UpdateCheckStatus.Failed -> Text(stringResource(R.string.update_check_failed), style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: Wire `MainActivity`**

- Create the ViewModel with `viewModel(factory = ...)` using `app.settings`, `GitHubReleaseSource(BuildConfig.UPDATE_REPO)`, `AndroidApkVerifier(app)`, `AndroidApkInstaller(app)`, `app.cacheDir`, `SemanticVersion.parse(BuildConfig.VERSION_NAME) ?: SemanticVersion(0, 0, 0)`, `BuildConfig.VERSION_NAME`, and `!BuildConfig.DEBUG`.
- `LaunchedEffect(Unit) { delay(1_500); updateViewModel.checkOnLaunch() }`.
- `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updateViewModel.onResume() }`.
- Pass `updateViewModel` into `AppShell`, then `ManageExportScreen`.
- Render `UpdateDialog(updateViewModel)` inside `EmpathieTheme`, after `AppShell`.

- [ ] **Step 5: Wire `ManageExportScreen`**

- Add `updateViewModel: UpdateViewModel` to the signature.
- Add `item { UpdatesSection(updateViewModel) }` after the export card and before the Developer Mode block.
- In the Developer Mode card, add a `TextButton` with `R.string.update_simulate_dev` that calls `updateViewModel.showDebugDialog()`.

- [ ] **Step 6: Build and run the tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL, including `TranslationCatalogTest`.

- [ ] **Step 7: Verify the UI on the emulator**

Install the new debug APK, open Manage & Export, toggle the automatic-checks switch, tap Check now, then unlock Developer Mode and tap **Show update dialog**. Inspect the dialog, the progress state (if reachable), and the Updates card. Confirm the Italian labels by switching the app language.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/empathie/energyaccounting/ui/update \
        app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt \
        app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt \
        app/translations.json
git commit -m "feat: show update dialog and manage update settings"
```

---

### Task 14: Release v0.4.0 and end-to-end update test

**Files:**
- Create: `docs/release-notes/v0.4.0.md`

**Interfaces:**
- Consumes: Tasks 7–13.
- Produces: GitHub Release `v0.4.0`; a verified in-app update from v0.4.0 to a temporary v0.4.1, if the repository is public.

- [ ] **Step 1: Write and commit `docs/release-notes/v0.4.0.md`**

Include:

```markdown
## In-app updates

This is the first version that can update itself. It checks GitHub Releases on
launch and offers the new APK inside the app.

If you installed v0.3.0 manually, install this version once by hand. After
that, updates are handled in-app.
```

Commit it:

```bash
git add docs/release-notes/v0.4.0.md
git commit -m "docs: add v0.4.0 release notes"
```

- [ ] **Step 2: Confirm tests and the tree**

```bash
git status --short
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: clean, BUILD SUCCESSFUL.

- [ ] **Step 3: Stop for explicit publishing approval**

Do not push the tag yet. Ask the user for explicit permission to publish the private `v0.4.0` release. If they prefer, hand them the commands and stop here.

- [ ] **Step 4: Tag and release v0.4.0**

```bash
git push upstream master
git tag -a v0.4.0 -m "Empathie Energy Accounting v0.4.0"
git push upstream v0.4.0
gh run list -R iseppe/energy-accounting --limit 5
gh run watch -R iseppe/energy-accounting <run-id>
gh release view v0.4.0 -R iseppe/energy-accounting
```

Expected: the release contains `empathie-energy-accounting-v0.4.0.apk` and `SHA256SUMS`, and the notes mention the one-time manual install for v0.3.0 users.

- [ ] **Step 5: Install the release-signed APK on the emulator**

Download the v0.4.0 assets, then install the release-signed APK. If a debug build is installed, uninstall it first (this deletes app data; the emulator only has demo data):

```bash
mkdir -p /tmp/opencode/release-v0.4.0
gh release download v0.4.0 -R iseppe/energy-accounting -D /tmp/opencode/release-v0.4.0
adb uninstall com.empathie.energyaccounting
adb install -r /tmp/opencode/release-v0.4.0/empathie-energy-accounting-v0.4.0.apk
```

Restore the demo backup if needed.

- [ ] **Step 6: Test the live check only if the repository is public**

If the repository is still private, stop here. The app's unauthenticated GitHub request will return 404, so a live test is impossible. Do not change repository visibility without the user's explicit instruction. Record the deferral in the final report.

If it is public, create a temporary higher release from the same commit:

```bash
git tag -a v0.4.1 -m "Temporary updater test"
git push upstream v0.4.1
gh run watch -R iseppe/energy-accounting <run-id>
```

Open the v0.4.0 app, wait for the update dialog, tap **Update**, let the download finish, allow installs from unknown sources when prompted, and confirm the system installer completes. Verify the app relaunches as v0.4.1 and the demo data is intact. Then clean up:

```bash
gh release delete v0.4.1 -R iseppe/energy-accounting --yes --cleanup-tag
git tag -d v0.4.1
```

- [ ] **Step 7: Report the result**

State whether the live end-to-end test ran or was deferred because the repository is private. No commit.

---

### Task 15: Final README and public-repository polish

**Files:**
- Modify: `README.md`
- Optional: `docs/screenshots/04-update.png`

**Interfaces:**
- Consumes: the v0.4.0 release and the live update test result.
- Produces: the final public-facing README.

- [ ] **Step 1: Update the README for v0.4.0**

- Add “Automatic in-app updates from GitHub Releases” to the feature list.
- Replace “in-app updates arrive in v0.4.0” with the current behavior.
- Optionally replace the static download badge with a live release badge:
  `[![Latest release](https://img.shields.io/github/v/release/iseppe/energy-accounting?label=latest)](https://github.com/iseppe/energy-accounting/releases/latest)`
- Keep the GPL-3.0, Android 8.0+, and privacy sections.
- If you captured an update-dialog screenshot, add `docs/screenshots/04-update.png` and place it in the screenshot row or a short “In-app updates” subsection.

- [ ] **Step 2: Set the repository description and topics**

When the repository is public (or now, if the owner wants):

```bash
gh repo edit iseppe/energy-accounting \
  --description "Offline-first Android app for Energy Accounting: a daily ledger of what drains and recharges energy." \
  --add-topic android --add-topic kotlin --add-topic jetpack-compose \
  --add-topic energy-accounting --add-topic autism --add-topic adhd \
  --add-topic auadhd --add-topic offline-first --add-topic privacy
```

- [ ] **Step 3: Verify the public README**

- Open the rendered GitHub README and confirm the icon, badges, and screenshots load.
- Confirm the `/releases/latest` link resolves to v0.4.0.
- Confirm the GPL-3.0 license is detected.
- Confirm no secret, keystore, or personal data appears in the repository or screenshots.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/screenshots
git commit -m "docs: finalize README for v0.4.0"
```

---

## Self-Review Checklist (for the implementer before handing off)

- Every spec requirement maps to a task: icon (Task 1), README/screenshots/license (Task 2), signing/versioning (Tasks 3–4), workflows/first release (Tasks 5–6), updater domain/network/verify/install (Tasks 7–12), UI (Task 13), v0.4.0 release and live test (Task 14), final README/public polish (Task 15).
- Type names match across tasks: `UpdateInfo`, `SemanticVersion`, `VerificationResult`, `VerificationError`, `UpdateUiState`, `UpdateError`, `UpdateCheckStatus`, `ReleaseSource`, `HttpTransport`, `ApkVerifier`, `ApkInstaller`.
- Review Focus items have tests in the owning tasks.
- No task downloads or installs an update without an explicit user action.
- No secret value is written to the repository, logs, or chat.
