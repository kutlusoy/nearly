# Nearly: Google Play, Application ID and Signing Fingerprint

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Maintainers who register the app with Google and later publish on Google Play
- **Reference implementation:** `app/build.gradle` (application ID, signing config), `.github/workflows/release.yml`, `tools/release-notes.sh`
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. The rule in one sentence

The application ID `com.kutlusoy.nearly` and one private release key are fixed now for GitHub and Google Play. The same key signs the GitHub APK and, through Play App Signing, the Play version, so updates work across both channels.

| Item | Value |
|---|---|
| Application ID | `com.kutlusoy.nearly` (permanent after the first Play upload) |
| Debug application ID | `com.kutlusoy.nearly.debug`, never registered |
| Release key | `nearly-release.jks`, alias `nearly-release` |
| Fingerprint | SHA-256 of the release certificate, shown on every release page |

## 2. Where the fingerprint comes from

- From the keystore: `keytool -list -v -keystore nearly-release.jks -alias nearly-release | grep SHA256`
- From every release page: the release workflow prints the SHA-256 and SHA-1 of the APK certificate with colons, as the console expects.
- Optional pin: the secret `NEARLY_RELEASE_CERT_SHA256` makes the release fail when the APK carries another certificate.

## 3. Android developer verification

Google verifies developers of apps on certified devices, also outside Google Play. Check the current rules in the Play Console documentation before registering.

1. Play Console, "Identity verification for Android developers", tab "Packages", "Register package name".
2. Package name `com.kutlusoy.nearly`, friendly name `Nearly`.
3. "Add key" and paste the SHA-256 fingerprint of the release certificate.
4. The console shows a snippet. Save it as the only content of `app/src/main/assets/adi-registration.properties` and commit it. The release workflow warns when the file is missing and fails when it is malformed or not part of the APK.
5. Publish a release, download the APK (the release page says "included in the APK") and upload it in the console as proof of ownership.

Do not register `com.kutlusoy.nearly.debug` and never register the test key.

## 4. Play App Signing with the existing key

By default Google creates a new signing key. For Nearly this MUST NOT be accepted, a different key would break updates between GitHub and Play.

1. Play Console, "Create app": name `Nearly`, "App", free, accept the declarations.
2. First release (Test and release, Internal testing, "Create new release"): choose "Use Play App Signing" and the option to upload a key exported from a Java keystore.
3. Download `pepk.jar` and `encryption_public_key.pem` from the console and export the key:

```bash
java -jar pepk.jar --keystore=nearly-release.jks --alias=nearly-release --output=nearly-play-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
```

4. Upload `nearly-play-key.zip`, then delete it.
5. Upload `Nearly-<version>.aab` from the GitHub release.
6. Under "App integrity, App signing" check that the app signing certificate SHA-256 equals the fingerprint from section 2. If it differs, Play created its own key and the setup MUST be redone before real users install.

A separate upload key is recommended by Google. If offered, create it, store `nearly-upload.jks` like the release key and keep it out of the repository.

## 5. Store listing checklist

| Item | Where in the console |
|---|---|
| Name, short and full description | Main store listing |
| Icon 512 x 512, feature graphic 1024 x 500, screenshots | Main store listing |
| Privacy policy URL | App content, Privacy policy |
| Data safety form | App content, Data safety |
| Ads declaration, content rating, target audience | App content |
| Location permission justification (fuzzy location) | App content, once the permission is added |

## 6. Checklist

- [ ] Release key created offline, fingerprint noted
- [ ] Package name registered with the fingerprint
- [ ] `app/src/main/assets/adi-registration.properties` committed, release says "included"
- [ ] APK of that release uploaded as proof
- [ ] Play app created with `com.kutlusoy.nearly`
- [ ] Release key exported with PEPK, certificate fingerprints match
- [ ] Store listing and policy forms filled
