# Nearly: Release Signing and GitHub Secrets

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Maintainers who create the release key and run the release workflow
- **Reference implementation:** `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/test-release.yml`, `app/build.gradle` (signing config)
- **See also:** [`guideline-google-play.md`](./guideline-google-play.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Workflows

| Workflow | Start | Signing | Result |
|---|---|---|---|
| `CI` | every push, pull requests, manual | none (test key if `app/debug.keystore` exists) | test results, lint report, debug APK as build artifact |
| `Test release` | manual | test key `app/debug.keystore` | pre-release `test-<number>`, application ID `com.kutlusoy.nearly.debug` |
| `Release` | push of a tag `v*`, or manual with the version | release keystore from the repository secrets | GitHub release with APK, AAB, `SHA256SUMS.txt` and R8 mapping |

The `Release` workflow stops with an error when a required secret is missing, so it cannot publish an unsigned release by accident.

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
| `NEARLY_KEYSTORE_BASE64` | yes | Base64 text of the keystore file, one line |
| `NEARLY_KEYSTORE_PASSWORD` | yes | keystore password |
| `NEARLY_KEY_ALIAS` | yes | `nearly-release` |
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

The test key is public and only for test builds. Create it once and commit it as `app/debug.keystore`:

```bash
keytool -genkeypair -v -keystore app/debug.keystore -alias nearly-debug \
  -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=Nearly Test,O=Nearly,C=DE"
```

The alias and passwords MUST match `signingConfigs.debug` in `app/build.gradle`. Never register this key anywhere and never use it for the store.

## 5. Run a release

1. Set the version in `app/version.txt`. The version code is derived by `tools/version.py` and `app/build.gradle` (`major * 1,000,000 + minor * 1,000 + patch`). It MUST grow with every release.
2. Tag and push:

```bash
git tag v0.0.1
git push origin v0.0.1
```

   Or run the workflow "Release" manually with the version.
3. The workflow checks tag and version, runs the unit tests, builds the signed APK and AAB and verifies application ID, version name, version code and certificate. The release notes list the certificate SHA-256 and SHA-1, the checksums and the version code.
4. Compare the fingerprint in the notes with the one noted in section 2.

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

- [ ] Release keystore created offline, two backups stored
- [ ] Certificate fingerprint noted
- [ ] Repository secrets set
- [ ] Test key committed as `app/debug.keystore`
- [ ] First release run green, fingerprint in the notes matches
