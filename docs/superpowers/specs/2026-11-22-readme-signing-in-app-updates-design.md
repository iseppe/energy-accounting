# Empathie Energy Accounting — Public README, Signed Releases & In-App Updates Design

## 1. Intent and Why

Empathie Energy Accounting is a native Android app for the Energy Accounting method (Maja Toudal and Tony Attwood): a zero-based daily ledger of what drains and recharges energy across four time slots. It is currently a private repository with no README, no release, no launcher icon, and no way to receive updates except reinstalling a locally built APK.

To share it publicly and keep installed users current without Google Play, three things are needed:

1. **A public-facing README** that explains the app, shows real screenshots, and gives one obvious download path.
2. **A repeatable signed release pipeline** where a tag produces an installable APK attached to a GitHub Release.
3. **A Morphe-style in-app updater**: the app checks the latest upstream release, shows a floating prompt, downloads the new APK inside the app, verifies it, and hands it to Android's installer. The user never opens a file manager.

The app will be published under the GNU General Public License v3.0 (GPL-3.0-only). The repository is private today and will become public later; the updater's live GitHub check only works once it is public.

## 2. Goals, Non-Goals, Anti-Goals

### Goals

- A short, calm README with: what the app is, three real screenshots, install instructions, privacy statement, build instructions, and GPL-3.0 license.
- A minimal adaptive launcher icon: deep slate background, a brain split into sage (left) and amber (right), with a warm-paper lightning bolt through the centre; plus a monochrome layer for Android 13 themed icons.
- Every release is signed with one stable release key and attached to a GitHub Release together with a `SHA256SUMS` file.
- Version name and version code come from the git tag, so a release cannot be built with a stale version.
- GitHub-hosted Actions only if the work fits a free tier; it does, even while the repository is private.
- In-app update checks at most once every 24 hours, on launch and on resume.
- A floating dialog with **Later** and **Update** only. Update downloads inside the app, verifies the SHA-256 and signer, then opens the system installer.
- Every update-related string exists in English and Italian in `app/translations.json`.
- All user data stays on device. The only network request is the optional update check/download.

### Non-Goals

- IzzyOnDroid, F-Droid, Obtanium listings or badges in this round. The README leaves room for them later.
- Google Play publishing or Play Services.
- Silent/background installation without a user tap.
- Delta updates, split APKs, or ABI-specific builds.
- A full in-app changelog renderer. Release notes are displayed as plain text.
- Automatic version bumps committed to the repository. The tag is the source of truth.
- Reproducible-build attestations or binary transparency.

### Anti-Goals

- Installing an APK the user did not explicitly ask to install.
- Embedding a GitHub token in the app. The app only talks to a public repository.
- Downloading over cleartext HTTP.
- Trusting the download without checking its digest and package identity.
- Losing the release key or changing it between releases.
- Adding analytics, ads, crash reporting, or a third-party update SDK.
- Making the app unusable offline or requiring an account.

## 3. Constraints

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, package `com.empathie.energyaccounting`.
- Local builds require an Android SDK location: either `local.properties` with `sdk.dir=...` (gitignored) or `ANDROID_HOME` set. This machine's SDK is `/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools`; the working `JAVA_HOME` is `/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec`.
- Kotlin 2.1, Jetpack Compose Material 3, Room 2.7, `kotlinx.serialization.json` already present. No new production dependency is required for the updater; use `HttpURLConnection`.
- No Google Play Services, Firebase, analytics, or tracking.
- All user-facing strings live in `app/translations.json` with non-blank `"en"` and `"it"` values. `syncTranslations` generates the Android resources at build time.
- The Android release APK must be signed with a private release key. The key and passwords never enter git.
- GitHub Actions: standard Ubuntu hosted runners. No larger runners, macOS, Windows, or paid plans.
- The repository is `iseppe/energy-accounting`; the app talks to its public Releases API.
- The README uses the GitHub repository name and the app name "Empathie Energy Accounting".
- The license is GPL-3.0-only, copyright 2026 iseppe. The full GNU GPL v3 text lives in `LICENSE`; no per-file headers are added in this round.

## 4. Decisions

### 4.1 Launcher Icon

Use the chosen **Two Hemispheres** direction:

- Background: deep slate `#131720`.
- Left hemisphere: sage `#5EC2A6`.
- Right hemisphere: amber `#E29B61`.
- Lightning bolt: warm paper `#F7F5F0`.
- The brain is the Lucide `brain` outline (ISC license); the bolt is the Lucide `zap` shape. Both are stroke/fill geometry that can be recreated as Android vector drawables.
- Compose the icon as an adaptive icon: a solid background layer and a foreground layer. Keep the brain inside the adaptive-icon safe zone (the central 66% of the 108 × 108 dp canvas). The bolt sits on top of the brain's central divide.
- Provide a monochrome foreground layer for Android 13+ themed icons: the same brain and bolt in a single colour; Android tints it.
- Declare `android:icon` and `android:roundIcon` in `AndroidManifest.xml`. Because `minSdk = 26`, the adaptive icon XML is sufficient; no legacy PNG mipmaps are required.
- Add `docs/images/icon.svg` as the README/logo source with the same geometry.

Exact geometry and path data are in Appendix A.

### 4.2 README and Screenshots

`README.md` is intentionally short. Structure:

1. Icon and title: “Empathie Energy Accounting”.
2. One-line pitch: a private, offline-first daily energy ledger for autistic and AuDHD people, based on the Energy Accounting method.
3. Badge row: static **Download APK** badge linking to `/releases/latest`, GPL-3.0 license badge, Android 8.0+ badge.
4. Screenshot row: Daily Account, Add Entry, Insights.
5. **What it is**: the zero-based ledger, four time slots, gain/spend, one energy dimension per entry, net balance; credit Maja Toudal and Tony Attwood; “not medical advice”.
6. **Features**: templates, custom dimensions, check-in reminders that skip logged slots, insights and trend chart, calendar history, JSON backup/restore, CSV and practitioner PDF export with share, English/Italian, light/dark themes, haptics.
7. **Install**: download the latest APK, Android 8.0+, allow installs from unknown sources, in-app updates, no Google services. A note for anyone moving from a debug build: export a JSON backup, uninstall the debug app, install the release, restore.
8. **Privacy**: local Room database; no account, ads, analytics, or trackers; the only network access is the optional GitHub update check, which can be turned off.
9. **Build from source**: JDK 17+, Android SDK 35, `./gradlew assembleDebug`, test command, translation catalog note.
10. **License**: GPL-3.0-only, copyright 2026 iseppe, link to `LICENSE`.

Badges are static shields.io images until the first release exists. After `v0.3.0`, replace the download badge with a live “latest release” badge if desired. Do **not** add IzzyOnDroid or Obtanium badges yet.

Screenshots:

- Captured from a headless API 35 AOSP emulator via `mobile-mcp`, not a physical phone. Installing the emulator and system image (about 1.5 GB) is approved as part of this work; if the download fails or disk space is insufficient, stop and ask.
- Emulator date set to **13/11/2026**, timezone Europe/Rome, English, light theme, clean status bar (09:30, full battery, no notification icons).
- Debug APK installed, then `synthetic-data/energy-accounting-demo-backup.json` restored with “Replace all local data”.
- Three PNGs, 720 px wide, with a subtle warm border: `docs/screenshots/01-daily-account.png`, `02-add-entry.png`, `03-insights.png`. A fourth Calendar screenshot is optional.
- If a screenshot task cannot be completed because no emulator image can be installed, stop and ask; do not substitute mockups.

### 4.3 Signing, Versioning and Releases

**Release key:**

- One RSA 4096-bit key, alias `empathie`, PKCS#12 keystore, validity 10,000 days.
- Stored outside the repository at `~/.android/keystores/empathie-release.jks`; password in a `chmod 600` file beside it and in the owner's password manager.
- The key and passwords are backed up in at least two places. Losing the key means installed users cannot update without uninstalling.
- **Hard handoff gate:** after generating the key and setting the secrets, stop. Do not push a release tag until the owner confirms the keystore and password are backed up in two places and stored in a password manager.
- GitHub Actions secrets:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`

**Versioning:**

- Tags are `vMAJOR.MINOR.PATCH` (for example `v0.4.0`).
- `versionName` is the tag without `v`.
- `versionCode = major × 10000 + minor × 100 + patch`, so `v0.3.0` → `300`, `v0.4.0` → `400`, `v1.0.0` → `10000`.
- Gradle accepts `-PappVersionName=…` and `-PappVersionCode=…`; local builds default to `0.3.0` / `300`. The release workflow derives both from `GITHUB_REF_NAME`.
- `BuildConfig.BUILD_ID` becomes `v<versionName>-<shortSha>`; remove the hardcoded `v0.2.0`.
- Add `BuildConfig.UPDATE_REPO = "iseppe/energy-accounting"` as the single place the updater's repository is defined.

**Release workflow** (`.github/workflows/release.yml`):

- Trigger: push of a tag matching `v*`.
- Permissions: `contents: write`.
- Ubuntu hosted runner, JDK 17, Gradle cache, Android SDK 35/build-tools 35.
- Run `./gradlew testDebugUnitTest`.
- Decode the keystore from `ANDROID_KEYSTORE_BASE64` into `$RUNNER_TEMP/release.jks`.
- Build `assembleRelease` with the tag-derived version.
- Verify with `apksigner verify --print-certs`.
- Rename to `empathie-energy-accounting-v<version>.apk`, generate `SHA256SUMS`, and create the GitHub Release with generated notes plus both assets.
- If `docs/release-notes/<tag>.md` exists, use it as the release body instead of generated notes. This is how the first release carries the debug-to-release migration warning.
- No workflow artifacts beyond the release assets; keep artifact storage negligible.

**CI workflow** (`.github/workflows/ci.yml`):

- Trigger: push to `master` and pull requests.
- Run unit tests and `assembleDebug`.
- No signing secrets.

**Free tier:** standard GitHub-hosted runners are free and unmetered for public repositories. While private, GitHub Free includes $12 of compute per month (about 2,000 Linux 2-core minutes) and 500 MB of artifact storage. The release job is expected to take 8–12 minutes including tests. No local runner is needed.

**Release sequence:**

- `v0.3.0`: the current app, as the baseline release.
- `v0.4.0`: the app with the updater.
- A temporary `v0.4.1` may be created from the same commit to test the updater, then deleted.
- Tags and releases are approval-gated. The implementation may prepare the tree, workflows, and local signed build, but it must not push a release tag or create a release without the owner's explicit go-ahead.
- Making the repository public and running the live `v0.4.1` updater test are deferred until the owner explicitly asks. Until then, the live GitHub check cannot work.

**Migration warning:** current installs are debug-signed. The release-signed APK cannot install over them. Export a JSON backup, uninstall the debug build, install the release, restore the backup. This must be in the README and the release notes for the first signed release.

### 4.4 Update Check

- Source of truth: `GET https://api.github.com/repos/iseppe/energy-accounting/releases/latest`.
- Headers: `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`, and a `User-Agent` containing the app name and version.
- No authentication and no token. The repository must be public for this to succeed. While private, automatic checks fail silently and no dialog appears.
- Compare the release tag against `BuildConfig.VERSION_NAME` using a strict `v?MAJOR.MINOR.PATCH` parser. Ignore drafts, prereleases, and tags that do not parse.
- Run on app launch and on resume if the last successful check is at least 24 hours old.
- Only a successful response (including “no releases yet”) updates the last-check timestamp. Network failures do not suppress the next attempt for a day.
- “Later” snoozes the same version for 24 hours. A newer version prompts immediately.
- Automatic checks default to **on**, with a switch in Manage & Export. Turning the switch off dismisses any open prompt, cancels an in-flight download, and stops automatic checks. Manual “Check now” still works.
- Manual “Check now” bypasses the 24-hour throttle. Its result is shown in the Updates card: “You’re up to date”, “Couldn’t check for updates”, or the update dialog.
- No check in debug builds. Developer Mode may show the dialog with fabricated data for layout review; it must be clearly labelled as a dev action.

### 4.5 Download, Verification and Install

- The user must tap **Update**; nothing downloads before that.
- Require `INTERNET` and `REQUEST_INSTALL_PACKAGES`. Do not add `SYSTEM_ALERT_WINDOW` or `WRITE_EXTERNAL_STORAGE`.
- Download from the release asset's `browser_download_url` over HTTPS into `cacheDir/updates/<asset-name>.part`, reporting progress. Rename to `.apk` only after completion.
- Verify:
  1. HTTP success and byte count equal to the release asset's `size`.
  2. SHA-256 equals GitHub's asset `digest` (`sha256:<64 hex>`), or the entry in the release's `SHA256SUMS` asset if the digest is absent.
  3. Package name equals `com.empathie.energyaccounting`.
  4. The APK's signing certificate SHA-256 equals the installed app's signing certificate. If the archive's signer cannot be read, fail closed and show the signature error; Android's installer is the final authority.
- On any verification failure, delete the downloaded file and show a specific error, not a generic crash.
- Before installing, check `PackageManager.canRequestPackageInstalls()`. If false, show an explanation and open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package. On return, re-check and continue automatically.
- Install with `Intent.ACTION_VIEW` on a FileProvider URI with MIME type `application/vnd.android.package-archive` and `FLAG_GRANT_READ_URI_PERMISSION`.
- The system installer's confirmation is the only install step; the app never installs silently.
- Cancel deletes the `.part` file. A failed or cancelled download leaves the app and data untouched.
- Clean old files from `cacheDir/updates` on app start and before a new download. Delete `.part` files immediately and completed APKs older than 24 hours; never delete the file just handed to the system installer.
- The downloaded APK is never exposed outside the app except through the FileProvider URI.

### 4.6 UI

**Floating update dialog** — shown over any tab:

- Rounded card, maximum width 420 dp, dimmed background, dismissible by back/outside (same as Later).
- Header icon and title “Update available”.
- Version line, e.g. “Version 0.4.0 is ready to install.”
- “What’s new” section with the release body as scrollable plain text, maximum height ~180 dp. Strip nothing; GitHub’s Markdown punctuation reads acceptably as text.
- Footer: `Later` (text button) and `Update` (filled button).
- Downloading: title “Downloading update…”, determinate progress bar, “1.2 MB of 15.4 MB”, `Cancel`.
- Verifying: indeterminate progress, “Verifying update…”.
- Permission needed: title “Allow app installs”, explanation, `Open settings`, `Later`.
- Installing: “Opening installer…”, then dismiss.
- Error: title “Update failed”, specific message, `Try again`, `Later`, and `View release` when a release URL exists.
- All buttons use the existing haptics wrapper.

**Updates card** in Manage & Export, above the Developer Mode/Build footer:

- Title “Updates”.
- Row “Check for updates automatically” with a `Switch`, default on.
- Row “Current version” with `v<BuildConfig.VERSION_NAME>`.
- Button “Check now”, disabled while checking, with the result line beneath.
- In Developer Mode only: a “Show update dialog” button that displays the dialog with fabricated version and notes for layout review. It must not download or install.

### 4.7 Copy (English / Italian)

Add these keys to `app/translations.json`:

| Key | English | Italian |
|---|---|---|
| `updates_title` | Updates | Aggiornamenti |
| `update_check_automatic` | Check for updates automatically | Controlla gli aggiornamenti automaticamente |
| `update_check_now` | Check now | Controlla ora |
| `update_checking` | Checking… | Controllo in corso… |
| `update_up_to_date` | You’re up to date | Sei aggiornato |
| `update_check_failed` | Couldn’t check for updates | Impossibile controllare gli aggiornamenti |
| `update_current_version` | Current version | Versione attuale |
| `update_available_title` | Update available | Aggiornamento disponibile |
| `update_available_version` | Version %1$s is ready to install. | La versione %1$s è pronta per l’installazione. |
| `update_release_notes` | What’s new | Novità |
| `update_later` | Later | Più tardi |
| `update_now` | Update | Aggiorna |
| `update_downloading` | Downloading update… | Download dell’aggiornamento… |
| `update_download_progress` | %1$s of %2$s | %1$s di %2$s |
| `update_verifying` | Verifying update… | Verifica dell’aggiornamento… |
| `update_installing` | Opening installer… | Apertura del programma di installazione… |
| `update_cancel` | Cancel | Annulla |
| `update_permission_title` | Allow app installs | Consenti installazioni |
| `update_permission_body` | Android needs your permission to install the update. Open the settings and allow installs from Energy Accounting. | Android ha bisogno del tuo permesso per installare l’aggiornamento. Apri le impostazioni e consenti le installazioni da Energy Accounting. |
| `update_open_settings` | Open settings | Apri impostazioni |
| `update_error_title` | Update failed | Aggiornamento non riuscito |
| `update_error_network` | Couldn’t reach GitHub. Check your connection and try again. | Impossibile raggiungere GitHub. Controlla la connessione e riprova. |
| `update_error_checksum` | The downloaded file didn’t match the published checksum. It was deleted. | Il file scaricato non corrisponde al checksum pubblicato. È stato eliminato. |
| `update_error_signature` | The update is not signed by the same key as this app. Installation was blocked. | L’aggiornamento non è firmato con la stessa chiave dell’app. Installazione bloccata. |
| `update_error_storage` | Not enough free space to download the update. | Spazio insufficiente per scaricare l’aggiornamento. |
| `update_error_installer` | No installer is available on this device. | Nessun programma di installazione disponibile sul dispositivo. |
| `update_error_generic` | Something went wrong. Try again. | Qualcosa è andato storto. Riprova. |
| `update_retry` | Try again | Riprova |
| `update_view_release` | View release | Vedi release |
| `update_simulate_dev` | Show update dialog | Mostra finestra aggiornamento |

The existing `TranslationCatalogTest` requires every key to have both languages and must continue to pass.

## 5. Detailed Design

### 5.1 Update Domain Model

Pure Kotlin, no Android imports:

```kotlin
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    companion object {
        fun parse(raw: String?): SemanticVersion?
    }
}

data class UpdateInfo(
    val version: SemanticVersion,
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkAssetName: String,
    val apkSizeBytes: Long,
    val apkSha256: String,
    val checksumsUrl: String? = null,
)
```

`UpdateInfo.apkSha256` is always lowercase 64-character hex. If neither the asset digest nor a `SHA256SUMS` entry can be resolved, the release is not offered as an update (the parser/source returns `null`), because an unverifiable APK is not useful.

### 5.2 Release Parsing

`GitHubReleaseParser.parse(json: String): UpdateInfo?`

- Deserialize `tag_name`, `name`, `body`, `html_url`, `draft`, `prerelease`, and `assets[]` (`name`, `browser_download_url`, `size`, `digest`, `content_type`) with `kotlinx.serialization`.
- Reject draft/prerelease releases.
- Parse `tag_name` with `SemanticVersion.parse`; reject if it fails.
- Select the first asset whose name ends in `.apk` (case-insensitive). Reject the release if none exists.
- Require `browser_download_url` to start with `https://`.
- Accept `digest` only if it matches `sha256:` followed by 64 hex characters. Otherwise leave it null and let the source look for a `SHA256SUMS` asset.
- Truncate `body` to 8,000 characters to bound dialog memory.
- The `SHA256SUMS` asset is any asset named exactly `SHA256SUMS`; parse `<hex>  <filename>` lines, ignore blank lines, and pick the line matching the APK asset name.

### 5.3 Network Source

```kotlin
interface ReleaseSource {
    suspend fun latestRelease(): UpdateInfo?
    suspend fun downloadApk(
        info: UpdateInfo,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    )
}
```

`GitHubReleaseSource(repo: String, transport: HttpTransport = UrlConnectionTransport())`:

- `latestRelease()` GETs `https://api.github.com/repos/$repo/releases/latest`, parses it, and if the digest is missing, GETs the `SHA256SUMS` asset and resolves the APK hash. Returns null on 404 (no releases), failed parse, or unverifiable hash. Throws `IOException` for network/server errors.
- `downloadApk()` GETs the asset URL with `Accept: application/octet-stream`, follows redirects, streams to the destination, calls `onProgress` at most once per 250 ms or every 256 KB, and throws `IOException` on failure. It checks the final byte count against `info.apkSizeBytes` when that is positive. The progress wrapper calls `CoroutineContext.ensureActive()` so a cancel is honored between chunks rather than waiting for the socket timeout.
- Timeouts: 10 s connect, 20 s read for JSON; 15 s connect, 30 s read for the APK.
- Read at most 1 MB for JSON and 64 KB for `SHA256SUMS`.

```kotlin
interface HttpTransport {
    fun getText(url: String, headers: Map<String, String>): String
    fun download(url: String, destination: File, onProgress: (downloadedBytes: Long) -> Unit)
}
```

`UrlConnectionTransport` uses `HttpURLConnection`, `instanceFollowRedirects = true`, `connectTimeout`, `readTimeout`, and `User-Agent`.

### 5.4 Verification

```kotlin
sealed interface VerificationResult {
    data object Valid : VerificationResult
    data class Invalid(val error: VerificationError) : VerificationResult
}

enum class VerificationError { CHECKSUM, PACKAGE, SIGNATURE, UNREADABLE }

interface ApkVerifier {
    fun verify(apk: File, expectedSha256: String, expectedPackageName: String): VerificationResult
}
```

`AndroidApkVerifier`:

1. Compute SHA-256 over the file; compare case-insensitively.
2. `PackageManager.getPackageArchiveInfo` with `GET_SIGNING_CERTIFICATES` (API 28+) or `GET_SIGNATURES` (API 26–27); check `packageName`.
3. Compare the archive signer certificate bytes with the installed app's signer certificate bytes. If the archive signer is unavailable, return `SIGNATURE`.
4. Return `UNREADABLE` if the file cannot be read or parsed.

A pure `Sha256.of(file: File): String` helper is unit-tested with a temp file.

### 5.5 Installer

```kotlin
interface ApkInstaller {
    fun canInstall(): Boolean
    fun openInstallPermissionSettings()
    fun install(apk: File)
}
```

`AndroidApkInstaller`:

- `canInstall()` returns `Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()`. Since `minSdk = 26`, it is the latter.
- `openInstallPermissionSettings()` starts `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `Uri.parse("package:${context.packageName}")`.
- `install(apk)` gets a FileProvider URI with authority `${packageName}.fileprovider`, builds `ACTION_VIEW` with type `application/vnd.android.package-archive`, adds `FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`, and starts it with the application context. It catches `ActivityNotFoundException` and surfaces `INSTALLER`.

`file_paths.xml` gains `<cache-path name="updates" path="updates/" />` beside the existing reports path.

### 5.6 State Machine

```kotlin
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class Verifying(val info: UpdateInfo) : UpdateUiState
    data class PermissionRequired(val info: UpdateInfo) : UpdateUiState
    data class Installing(val info: UpdateInfo) : UpdateUiState
    data class Error(val info: UpdateInfo?, val error: VerificationError? , val kind: UpdateError) : UpdateUiState
}

enum class UpdateError { NETWORK, STORAGE, INSTALLER, UNKNOWN }

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object UpToDate : UpdateCheckStatus
    data object Available : UpdateCheckStatus
    data object Failed : UpdateCheckStatus
}
```

Flow:

1. `checkOnLaunch()`: return if `!isReleaseBuild`, if checks are disabled, if a dialog is already showing (`state != Idle`), or if less than 24 h since the last successful check and no snooze has just expired. Otherwise call `check(manual = false)`. A snooze expiry forces a check even inside the 24-hour throttle so the prompt returns on the first launch after “Later” expires.
2. `check(manual)`: set `checkStatus = Checking`; fetch `latestRelease()`; on success update the last-check timestamp; if no info or `info.version <= currentVersion`, set `UpToDate` and keep `state = Idle`; if an update exists but the same version is snoozed until a future time and this is not manual, stay `Idle`; otherwise set `state = Available(info)`.
3. On network failure: automatic checks set `checkStatus = Failed` without a dialog; manual checks do the same and show the result line. Do not update the last-check timestamp.
4. `onLater()`: store `snoozedVersion = info.version.toString()` and `snoozedUntil = now + 24 h`; set `state = Idle`.
5. `onUpdate()`: if `installer.canInstall()` is false, set `PermissionRequired`; otherwise start the download job.
6. Download: clean old files; create `cacheDir/updates`; download to `<asset>.part`; update progress; rename on success; set `Verifying`.
7. Verify: on `Invalid`, delete the file and set `Error(info, error, CHECKSUM/PACKAGE/SIGNATURE as applicable)`; on `Valid`, set `Installing`, snooze this version for 24 hours (so a cancelled system installer does not immediately re-prompt), call `installer.install(file)`, then `Idle`.
8. `onReturnedFromSettings()`: if `state is PermissionRequired` and `installer.canInstall()`, start the download.
9. `cancelDownload()`: cancel the job, delete `.part`, set `Idle`.
10. `retry()`: if the last info exists, `onUpdate()`; otherwise `checkNow()`.

All network and file work runs on `Dispatchers.IO`. The ViewModel uses `viewModelScope`. A download job is stored so it can be cancelled.

### 5.7 Settings

Add to `AppSettings`:

- `updateChecksEnabled(): Boolean` default `true`
- `setUpdateChecksEnabled(enabled: Boolean)`
- `lastUpdateCheckEpochMillis(): Long` default `0`
- `setLastUpdateCheckEpochMillis(epochMillis: Long)`
- `snoozedUpdateVersion(): String?`
- `snoozedUpdateUntilEpochMillis(): Long` default `0`
- `snoozeUpdate(version: String, untilEpochMillis: Long)`
- `clearUpdateSnooze()`

Keys: `update_checks_enabled`, `last_update_check_epoch`, `snoozed_update_version`, `snoozed_update_until`.

### 5.8 UI Wiring

- `MainActivity` creates one `UpdateViewModel` using the application, `AppSettings`, `GitHubReleaseSource(BuildConfig.UPDATE_REPO)`, `AndroidApkVerifier`, and `AndroidApkInstaller`.
- A `LaunchedEffect(Unit)` calls `checkOnLaunch()` after a short delay (about 1.5 s) so the first frame is not blocked.
- `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` calls `onResume()`, which runs the throttled check and `onReturnedFromSettings()`.
- `UpdateDialog` is rendered above `AppShell` inside `EmpathieTheme`.
- `ManageExportScreen` receives the `UpdateViewModel`, places `UpdatesSection` above the Build footer, and passes it into the Developer Mode card for the dev-only simulation.
- The dialog's `onDismissRequest` maps to `onLater()`.
- No update UI is shown if `BuildConfig.DEBUG` and `devModeEnabled` is false; the dev simulation is the only debug entry point.

### 5.9 File Structure

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
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml                 (Create)
app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml                      (Create: monochrome)
app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml                 (Create: monochrome)
app/src/main/res/xml/file_paths.xml                                     (Modify: updates cache path)
app/src/main/kotlin/com/empathie/energyaccounting/data/settings/AppSettings.kt (Modify: update preferences)
app/src/main/kotlin/com/empathie/energyaccounting/update/SemanticVersion.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateInfo.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParser.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdatePolicy.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ReleaseSource.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSource.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/HttpTransport.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ApkVerifier.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/ApkInstaller.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/update/UpdateViewModel.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdateDialog.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/ui/update/UpdatesSection.kt (Create)
app/src/main/kotlin/com/empathie/energyaccounting/MainActivity.kt (Modify: create/observe UpdateViewModel, render dialog)
app/src/main/kotlin/com/empathie/energyaccounting/ui/manage/ManageExportScreen.kt (Modify: UpdatesSection, dev simulation)
app/translations.json                                                   (Modify: update copy)
app/src/test/kotlin/com/empathie/energyaccounting/data/AppSettingsTest.kt (Modify: update preferences)
app/src/test/kotlin/com/empathie/energyaccounting/update/SemanticVersionTest.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseParserTest.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/UpdatePolicyTest.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/GitHubReleaseSourceTest.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/Sha256Test.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/update/UpdateViewModelTest.kt (Create)
app/src/test/kotlin/com/empathie/energyaccounting/ui/LauncherIconTest.kt (Create)
```

## 6. Testing

Automated:

- `SemanticVersionTest`: parse `v0.4.0`, `0.4.0`, reject `0.4`, `v0.4.0-beta`, `banana`; compare 0.4.0 > 0.3.9; 1.0.0 > 0.99.99.
- `GitHubReleaseParserTest`: valid release with one APK and `sha256:` digest; missing digest with `SHA256SUMS` fallback; no APK → null; prerelease/draft → null; non-HTTPS asset → null; malformed JSON → null.
- `UpdatePolicyTest`: 24 h throttle boundary; disabled checks; snooze same version until time; newer version ignores snooze; expired snooze prompts.
- `GitHubReleaseSourceTest`: fake transport returns JSON and checksum text, asserts selected APK URL, digest resolution, 404 → null, network error propagates; download writes bytes and reports progress.
- `Sha256Test`: known content produces the known hash; missing file throws.
- `UpdateViewModelTest`: automatic check disabled; throttled; newer version → Available; equal/older → UpToDate; Later snoozes; onUpdate with permission → download/verify/install; checksum failure → Error and file deleted; permission required → PermissionRequired then resume; debug build does nothing.
- `AppSettingsTest`: update preferences default and persist.
- `LauncherIconTest`: `applicationInfo.icon != 0`, `getApplicationIcon` returns a non-null drawable, and the manifest declares `roundIcon`.
- `TranslationCatalogTest` continues to pass with the new EN/IT keys.

Manual:

- Build a signed release locally with the release key and inspect `aapt dump badging` for versionName/versionCode and `apksigner verify --print-certs`.
- Install the signed release on the emulator, then create a temporary higher release and confirm the prompt, progress, verification, unknown-sources flow, and successful install.
- Confirm cancel/back leaves the app running and data intact.
- Confirm the dialog is localized in Italian and the Updates card switch persists across restart.
- Confirm the README screenshots match the current UI and contain no personal data.

## 7. Release Sequence

1. Write and commit the spec and plan.
2. Implement the icon, README, screenshots, signing, and workflows.
3. Tag `v0.3.0`, let Actions publish the first signed release, verify the APK.
4. Install the signed `v0.3.0` on the emulator after exporting any existing debug data.
5. Implement the updater and tag `v0.4.0`.
6. Test with a temporary `v0.4.1` release once the repository is public; delete the temporary release/tag.
7. Final README polish: live release badge, update feature mention, optional update-dialog screenshot.
8. When the repository is made public: set the description and topics, confirm the Releases API is reachable without auth, and consider IzzyOnDroid/Obtanium as a later round.

## 8. Risks and Open Items

- **Keystore loss** is unrecoverable for update continuity. The key and password must be backed up before the first release.
- **Debug-to-release migration** requires a data export and uninstall; this is a one-time user-facing step.
- **Private repository** means the live updater check returns 404 until the repository is public. Unit tests and the dev dialog cover the logic; the real end-to-end test waits for public access.
- **GitHub API rate limit** is 60 unauthenticated requests per hour per IP. A daily check is well inside it; the app treats 403 as a failed check.
- **Release asset replacement** is prevented by treating a tag as immutable. If a release is broken, publish a new patch tag; do not replace assets under an existing tag.
- **Malformed or huge release notes** are bounded by the 8,000-character cap and scrollable UI.
- **No free space** is reported before download when possible and as a storage error on write failure.
- **The system installer can still reject** a valid download because of OEM policy or a revoked permission. The app surfaces the installer error and leaves the file for a retry.
- **The app is not on Google Play**, so `REQUEST_INSTALL_PACKAGES` is only used for self-updates, consistent with sideloaded distribution.
- **Icon licensing**: Lucide is ISC-licensed; keep the attribution comment in the vector drawable and mention it in the README's credits if desired.
- **No launcher icon exists today**; the new adaptive icon is part of this work, not an optional extra.

## 9. What Is Left to the Builder

- Exact Compose spacing/typography inside the dialog and Updates card, following `ManageExportScreen` card conventions.
- Exact README prose beyond the required facts and sections.
- Exact screenshot framing and optional fourth screenshot.
- The temporary `v0.4.1` test mechanics, once repository visibility allows.
- Whether to add IzzyOnDroid/Obtanium metadata in a later round.

## Appendix A — Icon Geometry

SVG viewBox `0 0 1024 1024`, rounded preview tile `rx=220`. Final Android adaptive icon uses a 108 × 108 dp viewport with the same proportions.

Colors:

- Slate `#131720`
- Warm paper `#F7F5F0`
- Sage `#5EC2A6`
- Amber `#E29B61`
- Ink `#23262E`

Brain paths (Lucide `brain`, ISC), drawn at `translate(152 152) scale(30)` with `fill="none"`, `stroke-width="1.6"`, `stroke-linecap="round"`, `stroke-linejoin="round"`:

```text
M12 18V5
M15 13a4.17 4.17 0 01-3-4 4.17 4.17 0 01-3 4
M17.707 6.294A3 3 0 1012 5a3 3 0 10-5.707 1.294
M17.996 5.125a4 4 0 012.526 5.77
M18 18a4 4 0 002-7.465
M19.967 17.482a4 4 0 11-7.967.517 4 4 0 11-7.967-.517
M6 18a4 4 0 01-2-7.465
M6.004 5.125a4 4 0 00-2.526 5.77
```

Left half stroke sage, right half stroke amber, split by a vertical clip at x = 512. On the Android adaptive icon, the split is at the canvas centre.

Bolt path (Lucide `zap`, ISC), drawn at `translate(512 512) scale(15) translate(-12 -12)`, fill warm paper, stroke slate, `stroke-width="1.2"`, `stroke-linejoin="round"`, `paint-order="stroke"`:

```text
M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z
```

Monochrome layer: white brain outline (both halves) + white bolt on transparent, no slate stroke around the bolt, so Android's tint produces a clean single-colour icon.
