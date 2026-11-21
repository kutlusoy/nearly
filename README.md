# Nearly

Map based, decentralized social app for local discovery. Native Android app (Kotlin), Nostr for identity, follows and encrypted chats, TOR/I2P first networking, small on-device AI.

Current version: see `app/version.txt` (0.0.1, project skeleton).

## Build

```bash
gradle assembleDebug
```

Requires JDK 17 and the Android SDK (compile SDK 36). The CI uses Gradle 8.14.3. Generate the Gradle wrapper once with `gradle wrapper --gradle-version 8.14.3` if you want `./gradlew`.

## Versioning

`app/version.txt` is the single source of the version (`MAJOR.MINOR.PATCH` or with a suffix such as `-rc1`). The version code is `major * 1,000,000 + minor * 1,000 + patch`, computed by `app/build.gradle` and checked by `tools/version.py`.

## Release and signing

- [`doc/guideline-release-signing.md`](doc/guideline-release-signing.md): release keystore, GitHub secrets, release and test workflows
- [`doc/guideline-google-play.md`](doc/guideline-google-play.md): certificate fingerprint, package registration, Play App Signing
