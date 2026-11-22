# Launcher Icon, README Branding & v0.4.1 Patch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Two-Hemispheres launcher icon actually render on a clean launcher and the owner’s phone, center and rename the README header to “Energy Accounting,” remove “Empathie” from user-visible surfaces, and ship the fixes in a private `v0.4.1` patch through PR #4.

**Architecture:** Add an instrumentation test that draws the real `ApplicationInfo` icon and asserts its palette, plus a release-workflow icon assertion. If the Android adaptive vector renders blank, replace its foreground/monochrome layers with PNGs generated from the approved SVG and add legacy PNG fallbacks. README/branding changes are text-only. `v0.4.1` is tagged from merged `master`; the package, signing key alias/DN, and `v0.4.0` tag/assets stay unchanged.

**Tech Stack:** Kotlin, Compose, Android adaptive icons/VectorDrawable, `androidTest`/`isolatedTest`, ImageMagick/rsvg-convert, GitHub Actions, `gh`, Markdown.

**Spec:** `docs/superpowers/specs/2026-11-22-launcher-icon-readme-branding-v0.4.1-design.md`

## Global Constraints

- `applicationId`/namespace `com.empathie.energyaccounting`, internal Kotlin packages/imports, release keystore alias `empathie`, and certificate DN `CN=Empathie Energy Accounting, O=Empathie, C=IT` must not change.
- The repository stays **private**; the live updater test remains deferred. Do not change visibility.
- `v0.4.0` tag/assets are immutable. The new patch is `v0.4.1`, `versionCode 401`; same release key; APK + `SHA256SUMS`; private release.
- Do not create a temporary `v0.4.1` live-test release; this is the real patch.
- PR #4 (`openchamber/readme-signing-in-app-updates` → `master`) is the delivery vehicle.
- No secrets/keystore reads or prints; the release workflow consumes the existing secrets.
- User-visible spelling is **Energy Accounting**; no “Empathie” in README, app label, in-app copy, release pages, or maintainer-facing release docs. The package and signing identity are exempt.
- Android icon resources must keep the safe zone; the SVG remains the source of truth.
- All tests and builds must pass before each commit; docs-only changes still get a build check.

## Review Focus

1. **The packaged icon vs the rendered icon.** The `v0.4.0` APK contains `application-icon res/IG.xml`, yet the phone shows no icon. The plan must prove the rendered palette on a clean launcher (Task 1), not just a non-zero resource ID, and conditionally replace the vector (Task 2).
2. **Release path shortening.** The release build shortens resource paths (`res/IG.xml`) while the debug build keeps long names. The release-workflow `aapt application-icon` assertion and clean-AVD install must cover the packaged release, not only the debug app.
3. **Launcher cache vs APK defect.** A clean AVD that renders the icon means the phone needs a reboot/launcher-cache clear. A blank clean AVD means the icon resources must change. Task 1 records which case applies; Task 2 branches on it.
4. **Branding scope.** “Empathie” must disappear from README/icon SVG/release notes/release titles/workflow/runbook, but remain in the package, internal code, keystore alias/DN, and internal design docs. Task 4 checks the exact surfaces.
5. **v0.4.1 integrity.** The patch must be the same release key, `401`/`0.4.1`, APK + `SHA256SUMS`, private, and must not move or replace `v0.3.0`/`v0.4.0` tags/assets. Task 5 verifies the assets and the edited release pages.

## File Structure

```text
README.md                                                               (Modify: centered header, Energy Accounting title/alt)
docs/images/icon.svg                                                    (Modify: title/aria)
docs/release-notes/v0.3.0.md                                            (Modify: title)
docs/release-notes/v0.4.0.md                                            (Modify: title/body if needed)
docs/release-notes/v0.4.1.md                                            (Create)
docs/RELEASING.md                                                       (Modify: title; key identity note)
.github/workflows/release.yml                                           (Modify: icon assertion + future release title)
settings.gradle.kts                                                     (Modify: rootProject.name, optional)
app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/LauncherIconInstrumentedTest.kt (Create)
app/src/main/res/drawable/ic_launcher_foreground.xml                    (Conditional: keep or replace)
app/src/main/res/drawable/ic_launcher_monochrome.xml                    (Conditional: keep or replace)
app/src/main/res/drawable-*/ic_launcher_foreground.png                  (Conditional: create density PNGs)
app/src/main/res/drawable-*/ic_launcher_monochrome.png                   (Conditional: create white PNGs)
app/src/main/res/mipmap-*/ic_launcher.png                               (Conditional: legacy fallback)
app/src/main/res/mipmap-*/ic_launcher_round.png                         (Conditional: legacy fallback)
```

---

### Task 1: Prove how the real icon renders, and assert the packaged icon

**Files:**
- Create: `app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/LauncherIconInstrumentedTest.kt`
- Modify: `.github/workflows/release.yml` (icon assertion)
- Test: the new instrumentation class

**Interfaces:**
- Consumes: the installed app’s `ApplicationInfo` icon and the release APK.
- Produces: a recorded clean-launcher result; a passing instrumentation test; a release-workflow icon check.

- [ ] **Step 1: Write the icon render test**

```kotlin
package com.empathie.energyaccounting.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LauncherIconInstrumentedTest {

    @Test
    fun `launcher icon draws the slate sage amber and paper palette`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 108, 108)
        icon.draw(Canvas(bitmap))

        val pixels = IntArray(108 * 108)
        bitmap.getPixels(pixels, 0, 108, 0, 0, 108, 108)

        fun countNear(rgb: Int): Int = pixels.count { pixel ->
            abs(Color.red(pixel) - Color.red(rgb)) <= 28 &&
                abs(Color.green(pixel) - Color.green(rgb)) <= 28 &&
                abs(Color.blue(pixel) - Color.blue(rgb)) <= 28
        }

        assertTrue("slate background missing", countNear(0xFF131720.toInt()) > 1_000)
        assertTrue("sage hemisphere missing", countNear(0xFF5EC2A6.toInt()) > 80)
        assertTrue("amber hemisphere missing", countNear(0xFFE29B61.toInt()) > 80)
        assertTrue("paper bolt missing", countNear(0xFFF7F5F0.toInt()) > 80)
    }
}
```

- [ ] **Step 2: Run the test on API 35**

```bash
./gradlew :app:connectedIsolatedTestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.empathie.energyaccounting.ui.LauncherIconInstrumentedTest
```

Record: PASS means the Android icon renders in the test; FAIL with missing palette means the vector is blank and Task 2 must replace it. The project uses `testBuildType = "isolatedTest"`; do not look for `connectedDebugAndroidTest`.

- [ ] **Step 3: Verify a clean launcher**

Install the current `v0.4.0` release APK on a fresh AVD (or uninstall the app first), open the launcher, and capture the app icon:

```bash
adb install -r /tmp/opencode/release-v0.4.0-verify/empathie-energy-accounting-v0.4.0.apk
adb shell input keyevent KEYCODE_HOME
adb shell input swipe 540 2000 540 600 300
adb exec-out screencap -p > /tmp/opencode/v040-launcher.png
```

Inspect `/tmp/opencode/v040-launcher.png`. Record whether the Two-Hemispheres icon or the default/blank icon appears.

- [ ] **Step 4: Add the release-workflow assertion**

In `.github/workflows/release.yml`, after the existing `aapt` version grep in “Verify and package”:

```bash
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" \
  | grep -qE "application-icon-(160|240|320):"
```

Expected: the release APK passes (the current `v0.4.0` APK reports `application-icon-160:'res/IG.xml'`). The `aapt` `application:` summary line is not required.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/kotlin/com/empathie/energyaccounting/ui/LauncherIconInstrumentedTest.kt .github/workflows/release.yml
git commit -m "test: render the launcher icon and assert its packaged resource"
```

---

### Task 2: Fix the adaptive icon only if Task 1 shows a blank/wrong render

**Files:**
- Conditional modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Conditional modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Conditional create: `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png`
- Conditional create: `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_monochrome.png`
- Conditional create: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- Conditional create: the matching `ic_launcher_round.png`
- Verify: `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-anydpi-v33/ic_launcher.xml`

**Interfaces:**
- Consumes: Task 1’s render result and `docs/images/icon.svg`.
- Produces: an icon that renders on the AdaptiveIconDrawable/launcher or a documented no-change result.

- [ ] **Step 1: Branch on Task 1**

If the render test passes **and** the clean AVD shows the Two-Hemispheres icon, the APK is not the problem: keep the vector resources, document the phone-side reboot/launcher-cache step, and go to Task 3. If either shows blank/default/wrong, continue.

- [ ] **Step 2: Generate PNGs from the SVG**

Use the approved icon geometry (slate background, sage left / amber right brain, paper bolt) from `docs/images/icon.svg`. For the adaptive foreground, make a **foreground-only** copy with the slate background removed and the brain/bolt scaled into the central 66/108 safe zone; never use the full-tile SVG as the foreground, because its brain is 70.3% of the tile and would be clipped. Render the foreground-only source at the density sizes, and render the full-tile source for the legacy mipmaps:

```bash
# FOREGROUND_SVG is a copy of docs/images/icon.svg with the slate <rect> removed
# and the brain/bolt scaled to the 66/108 safe zone.
mkdir -p app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}
for d in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
  density=${d%%:*}; size=${d##*:}
  rsvg-convert -w "$size" -h "$size" "$FOREGROUND_SVG" \
    -o "app/src/main/res/drawable-$density/ic_launcher_foreground.png"
done
```

The generated foreground must be transparent outside the brain/bolt and centered in the safe zone. Generate `ic_launcher_monochrome.png` in white (no background) at the same sizes. Render the **full-tile** SVG into the legacy `mipmap-*` `ic_launcher.png`/`ic_launcher_round.png` at 48/72/96/144/192 px for launchers that ignore the adaptive icon.

- [ ] **Step 3: Point the adaptive icon at the PNGs**

In `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, keep `<background android:drawable="@color/ic_launcher_background"/>` and change the foreground to the PNG resource. In `mipmap-anydpi-v33/...`, point the foreground and `<monochrome>` at the PNGs. Remove the vector foreground/monochrome if their `anydpi` config would win; keep the SVG source in `docs/images/`. Keep the manifest `android:icon`/`roundIcon` unchanged.

- [ ] **Step 4: Re-run the render test and a clean-AVD install**

```bash
./gradlew :app:connectedIsolatedTestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.empathie.energyaccounting.ui.LauncherIconInstrumentedTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent KEYCODE_HOME
adb exec-out screencap -p > /tmp/opencode/fixed-launcher.png
```

Expected: the palette test passes; the launcher screenshot shows the Two-Hemispheres icon. If the icon still fails, stop and report with the screenshot and the `aapt application-icon` output.

- [ ] **Step 5: Commit the conditional icon fix**

```bash
git add app/src/main/res
git commit -m "fix: make the adaptive launcher icon render reliably"
```

If Task 1 passed and no icon resources changed, skip the commit and record “no icon-resource change; launcher cache.”

---

### Task 3: Center the README header and rename it

**Files:**
- Modify: `README.md`
- Modify: `docs/images/icon.svg` (`<title>`/`aria-label`)

**Interfaces:**
- Consumes: the existing README, icon, badges, screenshots.
- Produces: a centered header titled “Energy Accounting.”

- [ ] **Step 1: Center the header block**

Replace the current icon/title/tagline/badge layout with:

```html
<p align="center">
  <img src="docs/images/icon.png" width="88" alt="Energy Accounting icon">
</p>

<h1 align="center">Energy Accounting</h1>

<p align="center">A private, offline-first Android ledger for the Energy Accounting method.</p>

<p align="center">
  [Download APK badge] [GPL-3.0 badge] [Android 8.0+ badge]
</p>
```

Keep the existing three badge URLs and the centered screenshot row. Do not center the body sections.

- [ ] **Step 2: Update the title and icon metadata**

Change the README H1 and alt from “Empathie Energy Accounting” to “Energy Accounting.” Change `docs/images/icon.svg`’s `<title>`/`aria-label` to “Energy Accounting.” The PNG icon has no text; no regeneration is needed for this rename.

- [ ] **Step 3: Verify the rendered header and links**

Open the README in a Markdown preview or the GitHub file view; confirm the icon, title, tagline, badges, and screenshots are all centered and the badge/asset links resolve. Run:

```bash
grep -n "Empathie" README.md docs/images/icon.svg || true
test -f docs/images/icon.png && test -f LICENSE
```

- [ ] **Step 4: Commit**

```bash
git add README.md docs/images/icon.svg
git commit -m "docs: center the README header and rename it Energy Accounting"
```

---

### Task 4: Remove “Empathie” from user-visible surfaces

**Files:**
- Modify: `docs/release-notes/v0.3.0.md`, `docs/release-notes/v0.4.0.md`
- Modify: `.github/workflows/release.yml`
- Modify: `docs/RELEASING.md`
- Modify: `settings.gradle.kts` (optional internal root name)
- External: GitHub release names/bodies for `v0.3.0`/`v0.4.0`

**Interfaces:**
- Consumes: the existing release notes, workflow title, runbook.
- Produces: user-visible copy that says “Energy Accounting” only; package/keystore identity untouched.

- [ ] **Step 1: Update release notes titles and workflow title**

Change the `v0.3.0` and `v0.4.0` notes titles to `# Energy Accounting v…`; scan their bodies for “Empathie.” Change the release workflow’s `--title` to `Energy Accounting $GITHUB_REF_NAME`. Change the `docs/RELEASING.md` title to `# Releasing Energy Accounting`; add a sentence that the keystore alias/path and certificate DN retain the historical “Empathie” identity because changing them would break update continuity (they are not user-visible).

- [ ] **Step 2: Optionally rename the Gradle root project**

`settings.gradle.kts` `rootProject.name = "EmpathieEnergyAccounting"` may become `"EnergyAccounting"`; it is internal, not user-visible. Do not change `applicationId`/`namespace` or any Kotlin package.

- [ ] **Step 3: Edit the existing GitHub release pages**

After the notes files are committed:

```bash
gh release edit v0.3.0 -R iseppe/energy-accounting \
  --title "Energy Accounting v0.3.0" \
  --notes-file docs/release-notes/v0.3.0.md
gh release edit v0.4.0 -R iseppe/energy-accounting \
  --title "Energy Accounting v0.4.0" \
  --notes-file docs/release-notes/v0.4.0.md
```

The tags and assets are untouched. Confirm:

```bash
gh release view v0.3.0 -R iseppe/energy-accounting --json name,body,assets
gh release view v0.4.0 -R iseppe/energy-accounting --json name,body,assets
```

- [ ] **Step 4: Verify the user-visible surfaces**

```bash
grep -rin "empathie" README.md docs/images/icon.svg docs/release-notes \
  docs/RELEASING.md .github/workflows/release.yml || true
```

Expected: no “Empathie” except the deliberate keystore alias/path/DN notes in `docs/RELEASING.md`. The internal Kotlin package and design docs are exempt.

- [ ] **Step 5: Commit**

```bash
git add docs/release-notes .github/workflows/release.yml docs/RELEASING.md settings.gradle.kts
git commit -m "docs: remove Empathie from user-visible release surfaces"
```

---

### Task 5: Update PR #4 and publish the private v0.4.1 patch

**Files:**
- Create: `docs/release-notes/v0.4.1.md`
- Tag: `v0.4.1`
- PR: #4 branch

**Interfaces:**
- Consumes: Tasks 1–4, the existing release workflow/secrets, PR #4.
- Produces: merged `master`, a private `v0.4.1` release (`401`/`0.4.1`) with APK + `SHA256SUMS`.

- [ ] **Step 1: Write the v0.4.1 notes**

Use the existing release-note style. Cover the launcher icon fix/verification, the final updater hardening (storage errors, settings guard, cancellation state, package/unreadable copy, flaky-test stabilization), the README/branding rename, the private-feed caveat, the one-time install from `v0.4.0`, Android 8+, GPL-3.0-only, and checksum verification. Title it `# Energy Accounting v0.4.1`.

- [ ] **Step 2: Push the branch and update PR #4**

```bash
git push upstream openchamber/readme-signing-in-app-updates
gh pr view 4 -R iseppe/energy-accounting
```

Update the PR body if needed to include the icon/README/branding changes. Wait for the PR CI to pass.

- [ ] **Step 3: Merge PR #4 and tag v0.4.1**

After the PR checks pass and the owner confirms the merge:

```bash
gh pr merge 4 -R iseppe/energy-accounting --merge
git fetch upstream
git tag -a v0.4.1 -m "Energy Accounting v0.4.1" upstream/master
git push upstream v0.4.1
```

Do not check out `master` in this worktree; the main checkout owns that branch. The tag points at the merged remote `master` commit, which already contains the release notes from the PR. If the merge method/owner review is not settled, stop and report; do not force.

- [ ] **Step 4: Watch the release workflow and verify**

```bash
gh run list -R iseppe/energy-accounting --limit 5
gh run watch -R iseppe/energy-accounting <run-id>
gh release view v0.4.1 -R iseppe/energy-accounting
```

Download the assets, `sha256sum -c SHA256SUMS`, `apksigner verify --print-certs`, and `aapt dump badging ... | sed -n '1p'` → `versionCode='401' versionName='0.4.1'`. Confirm the release body has no “Empathie” and the assets are APK + `SHA256SUMS`; the release is private/non-draft.

- [ ] **Step 5: Install the patch on the phone and clear the launcher cache**

Install `v0.4.1` manually (the private repo’s updater cannot see it). If the old icon persists, reboot or clear the launcher’s cache; confirm the Two-Hemispheres icon. Report the result.

---

### Task 6: Final verification

**Files:** none.

- [ ] **Step 1: Full unit suite**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
```

Expected: BUILD SUCCESSFUL; 175+ tests, 0 failures.

- [ ] **Step 2: Icon evidence**

Run `LauncherIconInstrumentedTest` on API 35 (and API 26 if available); inspect the clean-AVD launcher screenshot and the phone after the cache clear. Confirm the release APK’s `application-icon` assertion and the release-workflow icon step.

- [ ] **Step 3: Branding evidence**

Confirm the rendered README header is centered and titled “Energy Accounting”; `grep` finds no user-visible “Empathie” outside the deliberate signing-identity note; the `v0.3.0`/`v0.4.0`/`v0.4.1` release pages show the new titles; the app label remains “Energy Accounting.”

- [ ] **Step 4: Release evidence**

Confirm `v0.4.1` assets, checksum, RSA 4096 certificate, version `401`/`0.4.1`, private/non-draft, and CI/release workflow green. Confirm `v0.3.0`/`v0.4.0` tags/assets were not moved or replaced.

- [ ] **Step 5: Report**

State the icon result (APK defect fixed vs launcher cache), the verification evidence, the release URL, the PR merge, and any residual concerns. No secrets.

---

## Self-Review Checklist

- Every spec decision maps to a task: icon test/conditional fix (1–2), README center/name (3), Empathie cleanup (4), PR #4/v0.4.1 (5), verification (6).
- The package, namespace, keystore alias/DN, and `v0.4.0` tag/assets are never changed.
- The repo stays private; the live updater test is not run.
- The icon fix is evidence-driven; the phone cache is verified before/after.
- The release is `401`/`0.4.1`, same key, APK + `SHA256SUMS`, private.
- No secret value is printed, committed, or read.
