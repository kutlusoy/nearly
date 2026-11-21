# Nearly: Release Signing and GitHub Secrets

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Maintainers who create the release key and run the release workflow
- **Reference implementation:** `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/test-release.yml`, `app/build.gradle` (signing config)
- **See also:** [`guideline-google-play.md`](./guideline-google-play.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Workflows

| Workflow | Start | Signing | Result |
|---|---|---|---|
| `CI` | every push, pull requests, manual | public test key | tests, lint report, debug APK as build artifact `nearly-debug-apk` |
| `Test release` | manual | public test key | pre-release `test-<number>`, the five newest are kept, application ID `com.kutlusoy.nearly.debug` |
| `Release` | push of a tag `v*`, or manual with the version | **release key if the secrets are set, otherwise the public test key** | with the key: signed GitHub release with APK, AAB, `SHA256SUMS.txt` and R8 mapping. Without the key: a pre-release "test build" with a debug APK |

**How `Release` decides:**

- All three secrets `NEARLY_KEYSTORE_BASE64`, `NEARLY_KEYSTORE_PASSWORD`, `NEARLY_KEY_ALIAS` are set: signed release with the release key.
- None of them is set: test release, signed with the public test key, marked as pre-release, with a warning in the log. It can never be mistaken for a release: the title says "test build", the file is named `Nearly-<version>-test.apk` and the application ID is `com.kutlusoy.nearly.debug`.
- Only some of them are set: the workflow stops with an error, because that is a mistake in the settings.
- A test release of a version is replaced by the signed one when the secrets are set later and the workflow runs again for the same tag. The old test file is removed from the release.

The Tor and I2P helper build runs first. A **signed release** needs it and stops when it failed. A **test release** is built anyway and says "Built without Tor and I2P" in its text.

A tag with a suffix such as `v0.1.0-rc1` is published as a pre-release. The tag MUST match `app/version.txt`, otherwise the workflow stops.

## 2. Create the release keystore

Do this once, offline, on a trusted machine. A JDK provides `keytool`.

```bash
keytool -genkeypair -v -keystore nearly-release.jks -alias nearly-release \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

- Passwords MUST be long and unique, stored in a password manager.
- Keep at least two offline backups. **If the file is lost, no update can be signed any more.**
- The keystore MUST NOT be committed. `.gitignore` excludes `*.jks`, `*.keystore` (except `app/debug.keystore`) and `app/signing/`.
- Print the certificate fingerprint and note it down:

```bash
keytool -list -v -keystore nearly-release.jks -alias nearly-release | grep SHA256
```

## 3. Repository secrets

GitHub, Settings, Secrets and variables, Actions, "New repository secret".

| Secret | Required | Value |
|---|---|---|
| `NEARLY_KEYSTORE_BASE64` | for a signed release | Base64 text of the keystore file, one line |
| `NEARLY_KEYSTORE_PASSWORD` | for a signed release | keystore password |
| `NEARLY_KEY_ALIAS` | for a signed release | `nearly-release` |
| `NEARLY_KEY_PASSWORD` | no | key password, defaults to the keystore password |
| `NEARLY_RELEASE_CERT_SHA256` | no | expected certificate SHA-256, the release fails if the APK carries another certificate |

Base64 text:

```bash
base64 -w0 nearly-release.jks > keystore-base64.txt
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\nearly-release.jks).Path)) | Out-File -Encoding ascii -NoNewline keystore-base64.txt
```

With the GitHub CLI:

```bash
gh secret set NEARLY_KEYSTORE_BASE64 --repo kutlusoy/nearly < keystore-base64.txt
gh secret set NEARLY_KEYSTORE_PASSWORD --repo kutlusoy/nearly
gh secret set NEARLY_KEY_ALIAS --repo kutlusoy/nearly --body nearly-release
gh secret set NEARLY_RELEASE_CERT_SHA256 --repo kutlusoy/nearly
```

Delete `keystore-base64.txt` afterwards, it contains the key in readable form.

## 4. Test key

The test key is **public** and only for test builds. It is stored as text in `app/debug.keystore.b64` (JKS keystore, alias `nearly-debug`, store and key password `android`). The workflows turn it into `app/debug.keystore` before the build, if that file does not exist.

- Because the key never changes, every CI and test build is signed the same, and a new test APK installs over the old one without uninstalling.
- The test key MUST NOT be registered anywhere and MUST NOT be used for the store.
- To use an own test key, put a keystore at `app/debug.keystore` (it is not ignored by git). The alias and passwords MUST match `signingConfigs.debug` in `app/build.gradle`.
- Local build: `base64 -d app/debug.keystore.b64 > app/debug.keystore`, then `gradle assembleDebug`.

## 5. Run a release

1. Set the version in `app/version.txt`. The version code is derived by `tools/version.py` and `app/build.gradle` (`major * 1,000,000 + minor * 1,000 + patch`). It MUST grow with every release.
2. Tag and push:

```bash
git tag v0.0.1
git push origin v0.0.1
```

   Or run the workflow "Release" manually with the version.
3. With the secrets: the workflow checks tag and version, runs the unit tests, builds the signed APK and AAB and verifies application ID, version name, version code and certificate. Without them: it builds the test release.
4. Compare the fingerprint in the release notes with the one noted in section 2.

## 6. Signed build on your own machine

Create `app/signing/release.properties`:

```properties
storeFile=nearly-release.jks
storePassword=...
keyAlias=nearly-release
keyPassword=...
```

Copy the keystore into `app/signing/` and run `gradle assembleRelease`. Without this file the release build is unsigned.

## 7. Checklist

- [x] Public test key in the repository, all workflows use it
- [x] Release workflow signs with the secrets, otherwise builds a test release
- [ ] Release keystore created offline, two backups stored
- [ ] Certificate fingerprint noted
- [ ] Repository secrets set
- [ ] First signed release run green, fingerprint in the notes matches
