# Nearly: Account and Key Guideline

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly, security reviewers
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/`, tests in `app/src/test/java/com/kutlusoy/nearly/nostr/`
- **See also:** [`guideline-decisions.md`](./guideline-decisions.md), [`guideline-nostr-client.md`](./guideline-nostr-client.md), [NIP-19](https://github.com/nostr-protocol/nips/blob/master/19.md), [NIP-49](https://github.com/nostr-protocol/nips/blob/master/49.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Model

The Nostr key of a user follows from **username, e-mail and password**. The same three entries give the same identity on every device, so an account can always be restored without a server, a backup file or a relay. Username and e-mail are the salt, the password is the only secret.

The public key is public. Anyone who knows username and e-mail can try passwords offline and compare the result with the public key. Nothing limits the number of tries. The whole protection is the password and the cost of the key derivation. Therefore the password MUST have enough entropy.

## 2. The password

- The app generates **5 random words** from the BIP-39 English list (`Passphrase.generate()`), 11 bits per word, 55 bits in total. The user writes them down or stores them in a password manager.
- Input is tolerant: upper or lower case, spaces, hyphens and commas are all accepted and give the same key (`Passphrase.canonical()`).
- Own passwords are accepted only when a rough estimate reaches 55 bits (`PasswordPolicy`). The estimate halves the value of a random character formula on purpose, because people choose patterns. Repeated words count once. A password that contains the username is refused.
- Why 5 and not 3 words: 3 words are 33 bits and are searched in hours at one million tries per second. 5 words need more than a thousand years at the same speed.
- **The password cannot be changed.** A new password is a new key and therefore a new identity. The UI MUST say this at account creation.
- **E-mail typos create another identity silently.** The UI MUST ask for the e-mail twice at account creation and SHOULD warn at sign in when no profile exists for the derived key.

## 3. Components

| File | Purpose |
|---|---|
| `Passphrase.kt` | BIP-39 word list (2048 words, `src/main/resources/bip39/english.txt`), generator, tolerant parsing, canonical form |
| `KeyDerivation.kt` | Argon2id (64 MiB, 4 passes) over the canonical password with a salt from username and e-mail, result is the private key |
| `PasswordPolicy.kt` | entropy estimate, strength 0 to 4 for the live display, username rule |
| `KeyVault.kt` | sign up, sign in, export `nsec`, forget the local copy, and the in-memory `Session` |
| `AccountId.kt` | stable local id from username and e-mail, names the local file |
| `EncryptedKeyStore.kt` | store interface and a file implementation (atomic write, private directory) |
| `NostrKeys.kt`, `Bech32.kt` | key pair, `npub` and `nsec` (NIP-19) |
| `Nip49.kt` | password encryption of the local copy (scrypt, XChaCha20-Poly1305) |

## 4. Rules

- **Sign up (MUST):** refuse weak passwords (`WeakPasswordException`), derive the key, store a copy encrypted with the password (NIP-49) on the device.
- **Sign in:** on a known device the local copy is decrypted, which is fast and detects a wrong password (`WrongPasswordException`). On a new device the key is derived, which takes some seconds and cannot tell a wrong password from a right one, because every input gives some key. The UI checks the relays for a profile of the derived key.
- **Memory (MUST):** the clear key exists only in memory inside a `Session` and in short lived buffers that are zeroed after use. `Session.lock()` zeroes it.
- **Export (MUST):** showing the `nsec` needs the password again. The UI shows a warning and blocks screenshots there.
- **No secret on servers (MUST):** neither the password nor the key leaves the device. Nothing is stored on a relay.
- **Cost parameters (MUST NOT change):** username normalization, the salt scheme, the Argon2id parameters and the word canonicalization decide the key. A change would give every user another identity. A test pins the result of the default parameters.
- **Memory of the phone:** 64 MiB is a compromise for the Android heap. It is not verified on weak devices. A higher cost needs a new scheme version with a migration.

## 5. Tests

| Test | What it proves |
|---|---|
| `KeyCryptoTest` | HChaCha20 reference vector, NIP-19 examples, the NIP-49 test vector, round trip, wrong password, damaged checksum |
| `KeyDerivationTest` | the word list is the BIP-39 list (checksum), generator, tolerant parsing, same input same key, any changed input another key, cost is part of the result, regression value of the default cost |
| `KeyVaultTest` | password strength, same identity on a new device, wrong password on a known device, duplicates, export, forgetting the local copy, locked session, path tricks in the store |

The regression value is computed by this implementation, it is not an external test vector.

## 6. Not yet covered

- **UI:** account creation with the generated words, double e-mail entry, sign in, export screen with screenshot block, attempt limit on the device.
- **Android Keystore and biometrics** for the local copy (an option later).
- **Importing an existing Nostr key** is not supported. Such a key could not be restored from username, e-mail and password. It can be added later as a separate kind of account.
- **Remote signer (NIP-46):** not planned.
- The Android build with Bouncy Castle (Argon2, scrypt, secp256k1) and R8 is not yet verified.

## 7. Checklist

- [x] Passphrase generator with the BIP-39 list
- [x] Key derivation with Argon2id and a pinned regression value
- [x] Password policy with entropy estimate
- [x] Sign up, sign in, export, forget
- [ ] UI for account creation and sign in
- [ ] First Android build and a timing check of the derivation on a phone
- [ ] Android Keystore wrapping (optional)

## 8. Open Questions

1. Is 64 MiB acceptable on the weakest supported phones, or is a lower cost needed with a longer passphrase (6 words)?
2. Should the app offer 6 words as an option for users who want more margin?
