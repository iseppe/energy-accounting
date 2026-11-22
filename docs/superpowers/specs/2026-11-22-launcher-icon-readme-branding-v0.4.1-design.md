# Energy Accounting — Launcher Icon, README Branding & v0.4.1 Patch Design

## 1. Intent and Why

The private `v0.4.0` release is installed, but the launcher still does not show the new Two-Hemispheres icon. The README header is also left-aligned under the centered icon, and user-visible surfaces still say “Empathie Energy Accounting.” The app package may keep `com.empathie.energyaccounting`, and the release signing identity must not change.

This design closes those three issues, verifies the icon with real rendering evidence instead of resource-ID tests, and delivers the fixes in a private `v0.4.1` patch (the `v0.4.0` APK and tag are immutable). The repository stays private and the live updater test remains deferred.

## 2. Findings

- The `v0.4.0` APK **contains** the icon: `aapt dump badging` reports `application-icon-160/240/320: 'res/IG.xml'`, and the resource table has `mipmap/ic_launcher` for `anydpi` and `anydpi-v33`. The debug APK reports the long path `res/mipmap-anydpi-v33/ic_launcher.xml`. The release build shortens resource paths; the debug does not.
- The release APK’s `aapt dump badging` omits the `application:` summary line, while the debug APK prints it. This is an aapt/resource-path-shortening quirk, not proof that the launcher icon is missing.
- The existing `LauncherIconTest` checks only that the manifest icon resource IDs are non-zero. It never draws the icon. The README uses a PNG rendered from the SVG; the Android adaptive vector (`clip-path` groups) has never been visually verified.
- Therefore the phone’s missing icon is either a launcher cache from the previous install, or the Android vector rendering blank/incorrect. The first implementation step is to render the icon on a clean API 35 launcher and in an instrumentation test; the icon fix is conditional on that evidence.

## 3. Goals

- Make the launcher icon visible on a clean device and on the owner’s phone, with a test that draws the real `ApplicationInfo` icon and asserts the palette.
- Add a release-workflow assertion that the packaged APK exposes an application icon.
- Center the entire README header (icon, title, tagline, badges) and rename the title to **Energy Accounting**.
- Remove “Empathie” from user-visible surfaces: README, icon SVG title/aria, release-note titles and bodies, GitHub release names, future release workflow title, and the maintainer release runbook title.
- Keep `com.empathie.energyaccounting`, the internal Kotlin packages/imports, the release keystore alias `empathie`, and the certificate DN `CN=Empathie Energy Accounting, O=Empathie, C=IT`. Changing the signing identity would break updates; none of it is user-visible in the app.
- Update PR **#4** with the icon/README/branding changes, merge it to `master`, and publish a private `v0.4.1` patch (`versionCode 401`) containing the icon fix, the final updater hardening already on PR #4, and the release-note/title cleanup.
- Edit the existing `v0.3.0` and `v0.4.0` GitHub release titles/bodies to remove “Empathie”; the tags and assets stay unchanged.

## 4. Non-Goals

- Changing the applicationId, namespace, Kotlin package, launcher key alias/DN, or repository visibility.
- Rebuilding or replacing the `v0.4.0` tag/assets.
- Running the live updater test (the repository is private).
- Creating a temporary `v0.4.1` live-test release; the `v0.4.1` here is a real private patch.
- Redesigning the icon or changing the README screenshots in this pass.
- Sanitizing internal design docs under `docs/superpowers` beyond the new spec/plan.

## 5. Decisions

### 5.1 Icon: verify first, then fix conditionally

- Add `LauncherIconInstrumentedTest` in `app/src/androidTest`:
  - Load `targetContext.packageManager.getApplicationIcon(targetContext.packageName)`.
  - Draw it into a 108 × 108 ARGB bitmap on a transparent canvas.
  - Assert that a meaningful number of pixels match the expected palette: slate `#131720`, sage `#5EC2A6`, amber `#E29B61`, paper `#F7F5F0` (allow anti-aliasing tolerance).
  - Run on API 35 and, if the AVD setup is available, API 26. The project uses `testBuildType = "isolatedTest"`, so the Gradle task is `connectedIsolatedTestAndroidTest` with the class filter.
- Add a release-workflow check: after `aapt dump badging`, require at least one `application-icon-160/240/320` line. The release APK already has it; this prevents a future packaging regression.
- Install the release APK on a clean AVD/launcher and capture the launcher icon. This distinguishes an APK/vector defect from the phone’s launcher cache.
- **If the icon renders correctly:** no icon-resource change; document the phone-side reboot/launcher-cache step and keep the render test + release assertion.
- **If the icon is blank/incorrect:** replace the adaptive foreground and monochrome vectors with PNGs rendered from a **foreground-only extraction** of `docs/images/icon.svg`:
  - Remove the slate background from the source and scale the brain/bolt to the central 66/108 safe zone; the full-tile SVG keeps its 70.3% brain for the README/legacy tiles and must not be used directly as the adaptive foreground.
  - `ic_launcher_foreground.png` at `drawable-xxxhdpi` (432 × 432, transparent) and the matching density sizes; `ic_launcher_monochrome.png` in white for the monochrome layer.
  - Keep the SVG as the source; the adaptive icon XML references the PNGs.
  - Add a legacy `mipmap-*` `ic_launcher.png`/`ic_launcher_round.png` set (full tile, rendered from the full SVG) as a fallback for launchers that ignore the adaptive icon.
  - Re-run the render test and the clean-AVD launcher check.
- The phone-side step is still required after installing `v0.4.1`: reboot or clear the launcher’s cache if the old icon persists.

### 5.2 README header

Center the entire header block:

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

The screenshots remain in their existing centered row. The title changes from “Empathie Energy Accounting” to “Energy Accounting.” The README alt text and the `docs/images/icon.svg` `<title>`/`aria-label` change to “Energy Accounting.”

### 5.3 User-visible “Empathie” cleanup

Remove it from:

- `README.md` title/alt.
- `docs/images/icon.svg` title/aria.
- `docs/release-notes/v0.3.0.md`, `v0.4.0.md`, and the new `v0.4.1.md` titles (and any body occurrence).
- `.github/workflows/release.yml` future `--title`.
- `docs/RELEASING.md` title; keep the key path/alias/DN factual and note they are the immutable signing identity.
- Existing GitHub release names/bodies for `v0.3.0` and `v0.4.0` via `gh release edit --title ... --notes-file ...`; the tags/assets are untouched.
- `settings.gradle.kts` `rootProject.name` may become `EnergyAccounting` (internal only).

Keep `com.empathie.energyaccounting`, the Kotlin package, the application class, internal design docs, the keystore alias/path, and the certificate DN. The app label is already **Energy Accounting** through `app_name`; no in-app string change is required.

### 5.4 PR #4 and v0.4.1

- Work on the current branch `openchamber/readme-signing-in-app-updates`; PR #4 is open against `master`.
- Add the icon/README/branding changes to that branch; push; the PR updates. The PR already contains the final README and the post-v0.4.0 hardening.
- After review/merge, create `docs/release-notes/v0.4.1.md` and tag `v0.4.1` (`versionCode 401`). The release is private; assets APK + `SHA256SUMS`; the same RSA 4096 key.
- The release notes cover the icon fix, the final updater hardening, the README/branding cleanup, and the one-time install from `v0.4.0` (no in-app update while the repo is private).
- Edit the existing `v0.3.0`/`v0.4.0` release titles/bodies; do not move their tags or assets.
- The owner approved this private `v0.4.1` patch. The live updater test remains deferred.

## 6. Verification

- Unit suite: `./gradlew :app:testDebugUnitTest`; 175 tests baseline plus any new icon test.
- Instrumentation: `LauncherIconInstrumentedTest` on API 35 (and API 26 if available); palette pixels present.
- Release APK: `aapt dump badging | grep application-icon`; `apksigner verify --print-certs`; `versionCode='401' versionName='0.4.1'`; checksum.
- Clean-AVD install of the `v0.4.1` release APK; launcher screenshot shows the icon; phone install + reboot/launcher-cache clear.
- README rendered header centered; title “Energy Accounting”; all links/assets resolve; no “Empathie” in the user-visible files or release pages.
- `gh release view` for `v0.3.0`, `v0.4.0`, `v0.4.1`: titles/bodies clean, assets intact, private.
- CI and the release workflow green; no `v0.4.1` test/temp release beyond the real patch.

## 7. Risks

- The launcher cache can still hide a correct new icon; the verification must include a clean AVD and the phone-side cache step.
- A PNG foreground changes the adaptive-icon resource shape; verify the safe zone and the monochrome layer at 108 dp.
- The release resource optimizer shortens file paths; the render test, `aapt application-icon` assertion, and clean-AVD install cover the packaged result.
- The release key and alias must not change; every `v0.4.1` asset is signed with the same RSA 4096 certificate.
- Editing published release bodies is user-visible but does not change tags/assets; keep the update notes and checksums intact.
- The repository stays private, so the updater cannot see `v0.4.1`; the owner installs the APK manually.

## 8. What Is Left to the Builder

- Exact PNG density sizes and safe-zone placement.
- The exact palette-pixel thresholds for the render test.
- Whether API 26 instrumentation is available in the environment; report it honestly.
- The final README prose around the centered header.
- The exact `v0.4.1` release notes wording, following the existing release-note style.
