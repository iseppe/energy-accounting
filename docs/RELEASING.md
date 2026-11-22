# Releasing Energy Accounting

Maintainer notes for signing, versioning, and publishing release APKs.
No secret values belong in this file or anywhere in the repository.

## Release key

- Keystore: `~/.android/keystores/empathie-release.jks` (PKCS#12, RSA 4096-bit, validity 10,000 days).
- Key alias: `empathie`.
- Password: stored in `~/.android/keystores/empathie-release.password` (mode 600), kept beside the keystore and in the owner's password manager. The keystore and its password file are mode 600 inside a mode-700 directory; keep them that way.

The keystore path, key alias `empathie`, and certificate DN
`CN=Empathie Energy Accounting, O=Empathie, C=IT` keep the historical
“Empathie” naming: they are the permanent signing identity. They are not
user-visible, and they must not be renamed or regenerated, because changing the
signing identity would break in-place updates.

**The key is permanent.** Android app updates must be signed with the same key
for the lifetime of the app. If the keystore or its password is lost, installed
users can never update in place: they would have to uninstall and lose their
local data. Treat the key as irreplaceable:

- Back up the keystore **and** the password in **two off-machine locations**
  (for example two encrypted external drives or two cloud stores you control).
- Store the password in a password manager as well.
- Never commit the keystore, the password file, or any copy of their contents.

## GitHub Actions secrets

The release workflow (`.github/workflows/release.yml`) reads four repository
secrets:

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded on one line |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `empathie` |
| `ANDROID_KEY_PASSWORD` | the key password (same as the keystore password) |

To (re)create them from the files above, pipe the values in so they never
appear on the command line, in shell history, or in logs:

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

`gh secret list` shows names and timestamps only; secret values are never
printable. To verify the keystore entry without printing the password, use
keytool's file-backed password option:

```bash
keytool -list -v \
  -keystore ~/.android/keystores/empathie-release.jks \
  -storepass:file ~/.android/keystores/empathie-release.password \
  | head -20
```

## Secret discipline

- Never print, `cat`, `echo`, or log the keystore contents or the password.
- Never commit the keystore, the password file, or a `keystore.properties`
  with real values (`.gitignore` already excludes `*.jks`, `*.keystore`, and
  `keystore.properties`).
- Always pass passwords to `keytool` with `-storepass:file` (or
  `-storepass:env`), never inline on a command line.
- Always pipe secret values into `gh secret set` (stdin or `--body` with a
  literal alias only); never put a password in a command-line argument.
- When reading the password into a shell variable for a local build, use
  `$(< file)` redirection and do not echo the variable.

## Building a signed release locally

The `release` signing config in `app/build.gradle.kts` is driven entirely by
environment variables. Without them the release build is **unsigned**
(Gradle produces `app-release-unsigned.apk`) and must never be shipped.

```bash
export JAVA_HOME=/var/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec
export ANDROID_HOME=/var/home/linuxbrew/.linuxbrew/share/android-commandlinetools
export ANDROID_KEYSTORE_PATH="$HOME/.android/keystores/empathie-release.jks"
export ANDROID_KEYSTORE_PASSWORD="$(< "$HOME/.android/keystores/empathie-release.password")"
export ANDROID_KEY_ALIAS=empathie
export ANDROID_KEY_PASSWORD="$ANDROID_KEYSTORE_PASSWORD"

./gradlew :app:assembleRelease -PappVersionName=0.3.0 -PappVersionCode=300 --console=plain
```

Verify the result before shipping anything:

```bash
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging \
  app/build/outputs/apk/release/app-release.apk | sed -n '1p'
```

`apksigner` must verify the APK and report the expected RSA 4096 release
certificate. Add `--verbose` when you want to inspect the signature schemes; a
v2-only scheme is correct here: at `minSdk = 26` a v1 (JAR) signature is
unnecessary, and v3 exists only for key rotation. `aapt` must report the expected
`versionCode='…' versionName='…'`. Unset the password variables when finished.

## Versioning

- Tags follow `vMAJOR.MINOR.PATCH`, for example `v0.3.0`.
- `versionName` is the tag without the leading `v`.
- `versionCode = major × 10000 + minor × 100 + patch`:
  `v0.3.0` → `300`, `v0.4.0` → `400`, `v1.0.0` → `10000`.
- Gradle accepts `-PappVersionName=…` and `-PappVersionCode=…`; local builds
  default to `0.3.0` / `300`, and the release workflow derives both from the
  pushed tag (`GITHUB_REF_NAME`), so a release can never carry a stale version.
- `BuildConfig.BUILD_ID` is `v<versionName>-<shortSha>`.

## Cutting a release

1. Start from a clean working tree on the intended commit; `git status` must be
   empty and the full test suite must pass
   (`./gradlew testDebugUnitTest`).

   Run the launcher-icon render test on an emulator (API 35 and, if available,
   API 26) and install the release APK on a clean AVD to confirm the icon
   renders; the release workflow's `application-icon-*` check is only a
   packaging guard.

2. Write the release notes to `docs/release-notes/<tag>.md` (see below) and
   commit them.
3. Tag the commit and push the tag to `upstream`:

   ```bash
   git tag -a v0.3.0 -m "Release v0.3.0"
   git push upstream v0.3.0
   ```

4. Watch the release workflow in the Actions tab of `iseppe/energy-accounting`.
   It runs the unit tests, builds a signed `assembleRelease` from the
   tag-derived version, and publishes a GitHub Release with
   `energy-accounting-v<version>.apk` and `SHA256SUMS`.
5. Verify the published release:
   - Both assets exist and the release body is the intended notes.
   - `sha256sum -c SHA256SUMS` matches the downloaded APK.
   - `apksigner verify --print-certs` on the downloaded APK shows the expected
     RSA 4096 certificate.

Tags are immutable. If a release is broken, publish a new patch tag; never
replace assets under an existing tag.

**Publishing is approval-gated.** Preparing the tree, workflows, and a local
signed build is fine, but do not push a release tag or create a release without
the owner's explicit go-ahead.

## Release-notes convention

If `docs/release-notes/<tag>.md` exists (for example
`docs/release-notes/v0.3.0.md`), the release workflow uses its contents as the
GitHub Release body instead of generated notes. Prefer curated notes for every
release.

## Live updater test

Once the repository is public and a newer signed release exists, the in-app
updater can be exercised end to end (update check → download → checksum and
signature verification → install). Use a real signed release; do not create and
delete a version that has already shipped. Run this once after the repository
becomes public.

## Backup gate

Before any release tag is pushed, the owner must confirm that the keystore and
its password are backed up in two off-machine places and stored in a password
manager. Setting the GitHub secrets does not require this gate; publishing does.
