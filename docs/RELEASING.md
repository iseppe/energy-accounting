# Releasing Energy Accounting

Maintainer runbook for signing, versioning, publishing, and verifying releases.
The repository is **public**; releases are public and the in-app updater uses the
unauthenticated GitHub API.

Read [`AGENTS.md`](../AGENTS.md) for the development conventions. No secret
values belong in this file or anywhere in the repository.

## Release key

- Keystore: `$HOME/.android/keystores/empathie-release.jks` (PKCS#12, RSA
  4096-bit, validity 10,000 days).
- Key alias: `empathie`.
- Password: stored in `$HOME/.android/keystores/empathie-release.password`
  (mode 600), kept beside the keystore and in the owner's password manager. The
  keystore and its password file are mode 600 inside a mode-700 directory; keep
  them that way.

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
base64 -w0 "$HOME/.android/keystores/empathie-release.jks" \
  | gh secret set ANDROID_KEYSTORE_BASE64 -R iseppe/energy-accounting
gh secret set ANDROID_KEYSTORE_PASSWORD -R iseppe/energy-accounting \
  < "$HOME/.android/keystores/empathie-release.password"
gh secret set ANDROID_KEY_ALIAS -R iseppe/energy-accounting --body empathie
gh secret set ANDROID_KEY_PASSWORD -R iseppe/energy-accounting \
  < "$HOME/.android/keystores/empathie-release.password"
gh secret list -R iseppe/energy-accounting
```

`gh secret list` shows names and timestamps only; secret values are never
printable. To verify the keystore entry without printing the password, use
keytool's file-backed password option:

```bash
keytool -list -v \
  -keystore "$HOME/.android/keystores/empathie-release.jks" \
  -storepass:file "$HOME/.android/keystores/empathie-release.password" \
  | head -20
```

## Secret discipline

- Never print, `cat`, `echo`, or log the keystore contents or the password.
- Never commit the keystore, the password file, or a `keystore.properties` with
  real values. `.gitignore` excludes `*.jks`, `*.keystore`, `keystore.properties`,
  `.env*`, `*.password`, `*.p12`, `*.pfx`, `*.pem`, `*.key`, and
  `secrets.properties`.
- Always pass passwords to `keytool` with `-storepass:file` (or
  `-storepass:env`), never inline on a command line.
- Always pipe secret values into `gh secret set` (stdin or `--body` with a
  literal alias only); never put a password in a command-line argument.
- When reading the password into a shell variable for a local build, use
  `$(< file)` redirection and do not echo the variable.
- Never put a token in the app; the updater is intentionally unauthenticated.

## Versioning and artifacts

- Tags follow `vMAJOR.MINOR.PATCH` (`v0.4.3`).
- `versionName` is the tag without the leading `v`.
- `versionCode = major × 10000 + minor × 100 + patch`:
  `v0.3.0` → `300`, `v0.4.0` → `400`, `v0.4.3` → `403`, `v1.0.0` → `10000`.
- The release workflow derives both from the pushed tag (`GITHUB_REF_NAME`).
  Do not hand-edit a version for a release.
- `BuildConfig.BUILD_ID` is `v<versionName>-<shortSha>`.
- Release assets are `energy-accounting-v<version>.apk` and `SHA256SUMS`.
- The release certificate SHA-256 must stay
  `27443856c29d1aeea4db0492eed2e933174b30b63e1abc90e117557d3535b442`
  (RSA 4096). The release workflow fails if it changes.

## Building a signed release locally

The `release` signing config is driven entirely by environment variables.
Without all four, Gradle produces `app-release-unsigned.apk`; never ship it.

```bash
: "${JAVA_HOME:?set JAVA_HOME to a JDK 17+}"
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
export ANDROID_KEYSTORE_PATH="$HOME/.android/keystores/empathie-release.jks"
export ANDROID_KEYSTORE_PASSWORD="$(< "$HOME/.android/keystores/empathie-release.password")"
export ANDROID_KEY_ALIAS=empathie
export ANDROID_KEY_PASSWORD="$ANDROID_KEYSTORE_PASSWORD"

./gradlew :app:assembleRelease \
  -PappVersionName=0.4.3 -PappVersionCode=403 --console=plain
```

Verify the result before shipping anything:

```bash
APK=app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$APK"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" | sed -n '1p'
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" \
  | grep -E "application-icon-(160|240|320):"
```

`apksigner` must verify the APK and report the expected RSA 4096 release
certificate. Add `--verbose` to inspect the signature schemes; a v2-only scheme
is correct at `minSdk = 26`. `aapt` must report the expected
`versionCode='…' versionName='…'` and an application icon. Unset the password
variables when finished.

## Cutting a release

Publishing is **approval-gated**. Preparing the tree, workflows, notes, and a
local signed build is fine; do not push a release tag or create a release
without the owner's explicit go-ahead. The key/password backup gate below must
also be confirmed.

1. **Start from merged master.** `git fetch upstream`; confirm
   `git status` is empty and the intended changes are in `upstream/master`.
2. **Run the verification suite.** From the release commit:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
     -PappVersionName=<version> -PappVersionCode=<code> --console=plain
   ```
   Run the launcher-icon render test on an emulator (API 35, and API 26 if
   available) and install the release APK on a clean AVD to confirm the icon
   renders. The workflow's `application-icon-*` assertion is only a packaging
   guard.
3. **Write release notes.** Add `docs/release-notes/v<version>.md` with curated,
   user-facing notes; commit and merge it before tagging. See the notes
   convention below.
4. **Confirm the backup gate.** The owner must confirm the keystore and password
   are backed up in two off-machine places and in a password manager.
5. **Tag the merged master.**
   ```bash
   git tag -a v<version> -m "Energy Accounting v<version>" upstream/master
   git push upstream v<version>
   ```
   Do not tag a feature branch commit.
6. **Watch the release workflow.** `Release APK` runs the unit tests, builds a
   signed APK from the tag-derived version, verifies the version, application
   icon, and certificate fingerprint, then creates the public release with
   `energy-accounting-v<version>.apk` and `SHA256SUMS`.
   ```bash
   gh run list -R iseppe/energy-accounting --limit 5
   gh run watch -R iseppe/energy-accounting <run-id>
   ```
7. **Verify the published release.**
   ```bash
   mkdir -p /tmp/energy-accounting-release
   gh release download v<version> -R iseppe/energy-accounting \
     --dir /tmp/energy-accounting-release --clobber
   (cd /tmp/energy-accounting-release && sha256sum -c SHA256SUMS)
   "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
     /tmp/energy-accounting-release/energy-accounting-v<version>.apk
   "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging \
     /tmp/energy-accounting-release/energy-accounting-v<version>.apk | sed -n '1p'
   gh release view v<version> -R iseppe/energy-accounting
   ```
   Confirm: assets are exactly the APK and `SHA256SUMS`; the body is the
   intended notes; `versionCode`/`versionName` match; the certificate digest is
   the expected one; the release is public and not a draft/prerelease.
8. **Run the live updater test** from the previous release (see below), then
   report the result.

Tags and assets are immutable. If a release is broken, publish the next patch
tag. Never replace assets, move a tag, or force-push a release branch.

## Release-notes convention

`docs/release-notes/v<tag>.md` is the GitHub Release body if it exists; otherwise
the workflow falls back to generated notes. Always add curated notes.

- Start with `# Energy Accounting v<version>`.
- Explain user-visible changes, upgrade path, installation, checksum
  verification, license, and the standard “Not medical advice.” paragraph.
- Do not mention debug-build migration; the owner was the only debug installer.
- Keep notes short and within the app's Markdown subset (headings, bold,
  bullets, paragraphs, links, inline code). The previously installed release
  displays notes raw; a short, simple body is safer.
- Never include secrets, real user data, or local paths.
- After publishing, verify the release page title and body.

## Live updater test

The app checks the public `iseppe/energy-accounting` Releases API without
authentication. Run this after every release, from the previous release to the
new one:

1. On a spare emulator (or a phone with test data), install the previous signed
   release.
2. Open **Manage & Export → Check now** (or wait for the launch check).
3. Confirm the new version appears; download it; verify checksum, package, and
   signer; install through the system installer.
4. Reopen the app and confirm the installed `versionName`/`versionCode`.
5. Record the result; no temporary release or asset replacement is needed.

GitHub's anonymous API allows 60 requests/hour per IP. Repeated checks can show
“Couldn’t check for updates”; wait for the `x-ratelimit-reset` time instead of
changing code or using a token.

## CI and workflow troubleshooting

- CI runs `./gradlew testDebugUnitTest assembleDebug assembleRelease` on pushes
  to `master` and on pull requests. The release workflow runs the unit tests and
  a signed build on a tag.
- Known flakes: `ActivityTemplatesScreenTest` Compose timeout,
  `EnergyAccountingDaoTest` SQLite exceptions, and a transient Robolectric Maven
  artifact download failure. Read the log, re-run the failed job once, and do not
  change code for a flake without evidence.
- If the release workflow fails before creating the release, re-run the failed
  job on the same tag. If it fails after a partial release, do not replace
  assets; ship a new patch.
- Never delete or move the tag to recover from a flake.

## Public repository settings

The repository is public. Keep these settings on:

- **Secret scanning** and **secret scanning push protection** (0 alerts expected;
  enable if GitHub ever disables them).
- Rulesets **Protect master** (deletion and non-fast-forward blocked) and
  **Protect release tags** (`refs/tags/v*`, deletion and non-fast-forward
  blocked).
- Only `master` is a long-lived branch. Feature branches are short-lived and
  deleted after merge.
- CI has `contents: read`; the release workflow has `contents: write`. Do not
  broaden permissions.
- Security reports go through GitHub Security Advisories; do not ask users to
  post secrets in an issue.

## Backup gate

Before any release tag is pushed, the owner must confirm that the keystore and
its password are backed up in two off-machine places and stored in a password
manager. Setting the GitHub secrets does not require this gate; publishing does.
