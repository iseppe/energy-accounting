# AGENTS.md — Energy Accounting

This is the working agreement for AI agents and developers in this repository.
Read it before changing code, tests, release workflows, or public copy.

## 1. What this is

**Energy Accounting** is an offline-first Android ledger for the Energy Accounting
method by Maja Toudal and Tony Attwood. It tracks daily Gain/Spend entries across
four Time Slots and a single Energy Dimension per Entry, with local reminders,
insights, CSV/PDF/JSON export, and in-app updates from GitHub Releases.

The repository is **public**. The app has no account, no analytics, no trackers,
and no Google Play Services. All user data stays in a local Room database.

Terminology is defined in [`GLOSSARY.md`](GLOSSARY.md). Domain decisions live in
[`docs/adr`](docs/adr). If a change introduces or renames a domain concept, update
the glossary and add an ADR rather than inventing parallel vocabulary.

## 2. Non-negotiable constraints

- **Application identity is frozen.** Do not change `applicationId` / namespace
  `com.empathie.energyaccounting`, the internal Kotlin packages, the release
  keystore path/alias, or the certificate DN
  `CN=Empathie Energy Accounting, O=Empathie, C=IT`.
  Changing the signing identity breaks in-place updates for installed users.
- **The release key is permanent.** Never read, print, copy, or commit the
  keystore or its password. Use the four existing GitHub Actions secrets and the
  commands in [`docs/RELEASING.md`](docs/RELEASING.md).
- **User-visible name is “Energy Accounting.”** Do not add “Empathie” to the
  README, app label, in-app copy, release titles/notes, GitHub release pages, or
  maintainer release docs. The package, internal class names such as
  `EmpathieTheme` / `LocalEmpathieColors`, the keystore alias/path, and the
  certificate DN are deliberate technical exceptions.
- **New release assets use `energy-accounting-v<version>.apk`.** Existing
  `v0.3.0`/`v0.4.0` asset filenames are immutable; never rename or replace them.
- **Tags and release assets are immutable.** A broken release gets a new patch
  tag; never move a tag, force-push a release branch, or replace an asset.
- **No debug-build migration notes.** The owner is the only person who ever
  installed a debug build; the public install/updater text does not need the
  old debug-to-release backup warning. Do not reintroduce it.
- **Public repository hygiene.** Never commit secrets, keystores, password
  files, tokens, local properties, real user data, or personal paths. Use
  `synthetic-data/` for demos and screenshots.
- **No new Markdown dependency.** The update dialog uses the small parser in
  `ui/update/ReleaseNotesMarkdown.kt`; extend it and its tests if the notes
  format needs more.
- **Keep the updater unauthenticated.** The app calls the public GitHub API
  with no token. Do not add a token or a private-feed assumption.

## 3. Quick start

Set `JAVA_HOME` to a JDK 17+ and `ANDROID_HOME` to the Android SDK 35
installation. CI uses JDK 17; local JDK 21 is fine because source/target are
Java 17. On this Homebrew machine:

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec"
# Set ANDROID_HOME to the SDK root containing build-tools/, platforms/, and
# platform-tools/; CI exports its own.
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK root}"
```

`local.properties` is ignored and must never be committed.

```bash
# Unit tests (Robolectric + Compose)
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug

# Full local verification with a release version
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  -PappVersionName=0.4.3 -PappVersionCode=403

# Regenerate values/strings.xml, values-it/strings.xml, assets/translations.json
./gradlew :app:syncTranslations
```

`assembleRelease` is **unsigned** unless all four release signing environment
variables are present. Never ship an unsigned APK.

### Instrumentation

The project uses `testBuildType = "isolatedTest"`; instrumentation runs against a
separate application ID with the `.uitest` suffix. `connectedDebugAndroidTest`
does not exist.

```bash
./gradlew :app:connectedIsolatedTestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.empathie.energyaccounting.ui.LauncherIconInstrumentedTest
```

Available AVDs on this workstation: `empathie` (API 35) and `empathie26`
(API 26). Use the default emulator GPU; `-gpu swiftshader_indirect` segfaults on
this machine. Do not run mutable instrumentation against the owner’s personal
install; use an emulator or the isolated app ID.

`ApkVerifierInstrumentedTest` exercises the real verifier (valid APK, wrong
package, corrupted APK, signer mismatch). Its signature-mismatch case documents
the required published release APK path in the test class.

## 4. Repository map

```text
app/src/main/kotlin/com/empathie/energyaccounting/
  MainActivity.kt              single-activity shell, tabs, notification deep link,
                               app-level notification permission prompt
  EnergyAccountingApp.kt       application wiring, notification channel
  data/local/                  Room database, DAO, entities, migrations
  data/repository/             repository rules, ordering, templates, reminders
  data/export/                 JSON backup/restore, CSV, practitioner PDF
  data/settings/               device-only preferences (theme, language, updates)
  data/diagnostics/            debug log exporter, clock, logger
  domain/model/                Entry, DailyAccount, TimeSlot, Direction, templates
  domain/service/              insights calculator, energy formatter
  reminder/                    AlarmManager scheduling and notification receiver
  ui/daily|insights|manage/    Compose screens and ViewModels
  ui/update/                   update dialog, updates card, release-notes renderer
  update/                      GitHub release source, parser, verifier, installer,
                               version policy, update state machine
app/src/test/                  JVM/Robolectric unit + Compose tests
app/src/androidTest/           isolatedTest instrumentation tests
docs/adr/                      architecture decision records
docs/superpowers/specs|plans/  design specs and implementation plans
docs/release-notes/            curated GitHub release bodies
docs/RELEASING.md              signing, versioning, publishing runbook
synthetic-data/                reproducible demo backup (no real data)
```

Release/version rules, workflows, and secrets are in
[`docs/RELEASING.md`](docs/RELEASING.md). This file summarizes them; the runbook
is authoritative for commands.

## 5. Architecture and domain conventions

- Kotlin 2.1, Jetpack Compose Material 3, Room 2.7, Kotlin serialization,
  coroutines/Flow. No third-party DI, analytics, networking, or Markdown stack.
- `MainActivity` owns the app shell. Feature state lives in ViewModels exposing
  `StateFlow`; screens collect with `collectAsStateWithLifecycle`.
- The database is the source of truth. Repository methods preserve manual Entry
  ordering inside a Time Slot and across moves.
- **One Energy Dimension per Entry and Activity Template** (ADR-0003). Do not
  reintroduce primary/secondary dimensions (ADR-0002 is superseded).
- A Daily Account is zero-based: Total Gain − Total Spend = Net Balance, reset
  each day. Four fixed Time Slots; Energy Value is 1–100.
- Backups are versioned; restore supports Replace and Merge. Read
  `BackupSerializer` and the existing tests before changing schema or ordering.
- Room schema changes require a migration, updated schema, migration tests, and
  backup/restore coverage. Never use destructive migration in release.
- Use `AppSettings` for device-only preferences (theme, language, vibration,
  update checks, snooze, notification prompt flag). Settings are not part of
  backups.
- Use `BuildConfig.UPDATE_REPO` and `BuildConfig.BUILD_ID`; do not hardcode a
  repository URL or version elsewhere.

## 6. UI and copy conventions

- Use `EmpathieTheme`, `LocalEmpathieColors`, `LocalAppHaptics`, `SelectablePill`,
  and existing section/card patterns. Match the surrounding screen rather than
  introducing a new design language.
- All user-facing text goes through `stringResource` / `translations.json`.
  English and Italian are both required.
- Keep the app label **Energy Accounting**; the install/update copy must match
  the current public behavior, not a past private-repo state.
- Use Material 3 bottom sheets for modal flows (Add Entry, Calendar, update
  prompt). Keep action rows visible and scroll long content independently.
- Accessibility matters: content descriptions for icon-only actions, readable
  touch targets, no color-only state. Preserve edge-to-edge and predictive back.
- Haptics are optional and respect the user setting.
- Release notes are user-facing Markdown. The app renders headings, bold,
  bullets, paragraphs, links, and inline code; keep notes within that subset and
  keep them short because older installed versions show them raw.

## 7. Translations

`app/translations.json` is the source of truth. Do not edit the generated XML or
`app/src/main/assets/translations.json` by hand.

1. Add the key to `app/translations.json` with both `"en"` and `"it"`.
2. Run `./gradlew :app:syncTranslations`.
3. Commit the generated `values/strings.xml`, `values-it/strings.xml`, and
   `assets/translations.json` together with the source.
4. `TranslationCatalogTest` fails if a language or key is missing; run it.

## 8. Testing

- Put JVM/Robolectric and Compose tests in `app/src/test`; instrumentation in
  `app/src/androidTest`.
- Test real behavior, not mocks of the class under test. Use `synthetic-data/`
  for demo-backed device tests; never copy the owner’s real database.
- New behavior starts with a failing test. Run the focused test, then the full
  `:app:testDebugUnitTest` before committing.
- For changes touching notifications, run the reminder/notification tests and
  manually post a test notification on API 35. For icon changes, run
  `LauncherIconInstrumentedTest` on API 35 and, if available, API 26.
- The launcher adaptive vectors use Android `PathParser`. Keep arc flags
  space-separated (the XML comment in the two drawables says so); do not
  re-minify `docs/images/icon.svg` into the Android vectors. The render test
  draws the real packaged icon and asserts the palette.
- CI baseline as of v0.4.3: 180+ unit tests and a release build. Do not rely on
  a count; rely on the suite.
- Known flakes: `ActivityTemplatesScreenTest` Compose timeouts,
  `EnergyAccountingDaoTest` SQLite exceptions, and a transient Robolectric
  Maven artifact download failure. Inspect the log and re-run the failed job
  once; do not change code for a flake without evidence.

## 9. Git, worktrees, and pull requests

- Do not develop on `master`. Use a feature branch such as
  `openchamber/<slug>` or `feature/<slug>`, or a worktree.
- Worktrees live under `.worktrees/` and are ignored. The main checkout owns
  `master`; do not check out `master` in a linked worktree. `.superpowers/` is
  local agent scratch and is ignored; specs/plans are committed.
- Open a PR to `master`. Wait for CI (`.github/workflows/ci.yml`), merge, and
  delete the branch. One logical change per commit; use `feat:`, `fix:`,
  `docs:`, `chore:`, or `test:` messages.
- `master` and `v*` tags are protected by rulesets against deletion and
  force-push. Never bypass them. Never force-push a shared branch or move a tag.
- Before deleting a branch, confirm its content is in `master` or archive it in a
  local bundle.

## 10. Release and versioning

Full commands and the publishing gate: [`docs/RELEASING.md`](docs/RELEASING.md).

- Tags are `vMAJOR.MINOR.PATCH`; `versionName` is the tag without `v`;
  `versionCode = major×10000 + minor×100 + patch`. The release workflow derives
  both from the tag. Do not hand-edit versions for a release.
- `docs/release-notes/v<tag>.md` is required and becomes the GitHub release body.
  Start with `# Energy Accounting v<tag>`, then upgrade/install notes, privacy,
  and “Not medical advice.” Keep the old/new update-dialog compatibility in mind.
- Tag from **merged remote `master`**, not a feature commit:
  `git tag -a vX.Y.Z -m "Energy Accounting vX.Y.Z" upstream/master`.
- The **Release APK** workflow runs tests, builds a signed APK, checks the
  version, application icon, and release certificate fingerprint, then publishes
  `energy-accounting-vX.Y.Z.apk` + `SHA256SUMS`.
- Verify every release: `sha256sum -c SHA256SUMS`, `apksigner
  verify --print-certs`, `aapt dump badging` → expected version/code/icon, and
  check the release body/assets.
- Run the live updater test after release: install the previous release, use
  **Manage & Export → Check now**, download/verify/install the new one, and
  confirm the installed version. No temporary release is needed.
- Publishing is **approval-gated**: do not push a release tag or create a
  release without the owner’s explicit go-ahead.
- If a release is broken, ship the next patch. Never replace assets or move a
  tag.

## 11. Updater behavior

- Public GitHub Releases only; unauthenticated API. The latest release is the
  update source. The app selects the `.apk` asset and resolves its SHA-256 from
  `SHA256SUMS`.
- Before install, the APK must match the published SHA-256, the package name, and
  the installed app’s signing certificate. Do not weaken or bypass these checks.
- Release asset names are not assumed; the parser reads the actual asset name.
- Automatic checks are at most once a day; **Check now** bypasses the throttle.
  Later/snooze holds a version for 24 hours.
- GitHub’s anonymous API is limited (60 requests/hour/IP). Repeated test checks
  can show “Couldn’t check for updates”; wait for `x-ratelimit-reset` instead of
  changing code.
- The update dialog uses the small Markdown parser. If you add a new Markdown
  feature, update `ReleaseNotesMarkdown` and its tests, and keep the notes short
  enough for the previous release’s dialog.

## 12. Notifications and icons

- Android 13+ requires `POST_NOTIFICATIONS`. The app asks once when any reminder
  is enabled, including after a backup restore; if denied, the Manage & Export
  warning card offers Allow/Open notification settings. Do not rely only on the
  reminder-toggle path.
- The receiver suppresses reminders when notifications are disabled. Do not
  bypass that check.
- The status-bar small icon must be a monochrome vector; it is
  `drawable/ic_notification.xml` (white bolt). Do not use the full-color adaptive
  launcher icon as the small icon. Set the launcher icon as the notification’s
  large icon so the expanded notification stays correct.
- Notification channels, exact alarms, and reminder scheduling are sensitive;
  test on API 35 and API 26.

## 13. Security, privacy, and public-repo rules

- The repository is public. Anything committed, released, or placed in an issue
  or PR is public. Do not add PII, real backups, real screenshots, local paths,
  personal email addresses, tokens, or secrets.
- The keystore, password file, and `local.properties` live outside the repo and
  are ignored. Never read or print them; use the runbook’s file-backed commands.
- GitHub secret scanning and push protection are enabled; branch/tag rulesets
  protect `master` and `v*`. Do not disable them.
- The four Actions secrets are the only signing secrets:
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`. Never put their values in the repo or logs.
- The app has no analytics or telemetry. The only network access is the update
  check/download. Keep it that way.
- Security reports: use GitHub Security Advisories on the repository; do not
  publish a working exploit in an issue.

## 14. Current release line

As of 2026-11-23:

| Tag | Code | Notes |
| --- | --- | --- |
| `v0.3.0` | 300 | first release-signed build |
| `v0.4.0` | 400 | in-app updater |
| `v0.4.1` | 401 | launcher icon, branding, updater hardening |
| `v0.4.2` | 402 | notification permission, bolt icon, formatted update sheet |
| `v0.4.3` | 403 | short-screen update sheet, inline code, card polish |

All use the same RSA 4096 release key. New releases are public; the app can
update from v0.4.0 onward. Do not assume the latest version without checking
`gh release view` / `git tag`.

## 15. Documentation and plans

- `README.md` is user-facing; keep it current for install, updater, privacy, and
  build instructions.
- `GLOSSARY.md` is the domain vocabulary; `docs/adr/` records durable design
  decisions.
- For multi-step features, write a spec in `docs/superpowers/specs/` and a
  task-by-task plan in `docs/superpowers/plans/`; keep both public-safe.
- Prefer linking to the runbook over duplicating secret-handling commands.
- Do not add local absolute paths (`/home/...`) to new docs or commits.

## 16. Agent checklist

Before editing:

1. Read this file, the relevant spec/plan, and the glossary/ADR for the area.
2. Check the current branch/status and do not overwrite unrelated user work.
3. Confirm whether the change is user-visible, release-related, or
   security-sensitive; ask before publishing or destructive actions.

Before committing:

1. Write or update the failing test first for behavior changes.
2. Run focused tests, then `./gradlew :app:testDebugUnitTest`.
3. Run `:app:assembleDebug`; for release changes, the release build with the
   intended `-PappVersionName`/`-PappVersionCode`.
4. Add both translations and run `:app:syncTranslations`.
5. Check public-safe content (no secrets/PII/local paths) and `git diff --check`.
6. Commit, push the feature branch, open a PR, and wait for CI.

Before releasing:

1. Merge the fix to `master`; do not tag a feature commit.
2. Add `docs/release-notes/v<version>.md`.
3. Verify the key/password backup gate and the owner’s explicit approval.
4. Tag from `upstream/master`, push the tag, watch the release workflow, and run
   the verification checklist plus the live updater test.
