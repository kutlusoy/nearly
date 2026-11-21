# Nearly: Nostr Key Management Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly, security reviewers
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/`, tests in `app/src/test/java/com/kutlusoy/nearly/nostr/`
- **See also:** [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [NIP-19](https://github.com/nostr-protocol/nips/blob/master/19.md), [NIP-49](https://github.com/nostr-protocol/nips/blob/master/49.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Model

The identity of a user is a Nostr key pair (secp256k1). The private key is generated on the device and stored only in encrypted form. The user logs in with **username, e-mail and password**. The password decrypts the key. Username and e-mail are not part of the key and are not sent anywhere by the key management.

## 2. Components

| File | Purpose |
|---|---|
| `Bech32.kt` | bech32 for `npub`, `nsec` and `ncryptsec` (no 90 character limit) |
| `NostrKeys.kt` | key generation, x-only public key (BIP-340), NIP-19 encoding and decoding of `npub` and `nsec`, hex |
| `Nip49.kt` | password encryption of the key: scrypt, XChaCha20-Poly1305, `ncryptsec` |
| `PasswordPolicy.kt` | live feedback while typing: which rules are met and a strength from 0 to 4 |
| `AccountId.kt` | stable id from username and e-mail (normalized, scrypt), names the local key file |
| `EncryptedKeyStore.kt` | store interface and a file implementation (atomic write, private directory) |
| `KeyVault.kt` | create, import, unlock, export, change password, delete, and the in-memory `Session` |

The crypto uses the lightweight API of Bouncy Castle (`bcprov-jdk18on`): secp256k1 parameters, scrypt and ChaCha20-Poly1305. HChaCha20 for the 24 byte nonce is implemented in `Nip49.kt`.

## 3. Rules

- **Password policy (MUST):** at least 12 characters, lower and upper case letter, digit, special character, and the password does not contain the username. `KeyVault` refuses weaker passwords with `WeakPasswordException`, which carries the rule results for the UI.
- **Storage (MUST):** only the `ncryptsec` is stored. The clear key exists only in memory inside a `Session` and in short lived buffers that are zeroed after use. `Session.lock()` zeroes the key.
- **scrypt cost (MUST):** `log_n = 16` (64 MiB) by default, the value is stored in the `ncryptsec`. Decryption rejects values outside 10 to 22, so a manipulated file cannot force a huge allocation.
- **Export (MUST):** showing the `nsec` needs the password again, also in an open session. The UI MUST show a clear warning and block screenshots on that screen.
- **Backup (MUST):** after account creation the app shows the `nsec` once and asks the user to store it. A lost password with no `nsec` backup means a lost identity, there is no recovery.
- **Wrong password:** reported as `WrongPasswordException`. An unknown account is a separate error, so the UI can say which of the two it is. Rate limiting of attempts belongs in the UI layer (open).
- **Import:** an existing `nsec` can be imported and is then protected like a new key.
- **No clear key on servers (MUST):** the `nsec` and the password never leave the device.

## 4. Tests

| Test | What it proves |
|---|---|
| `KeyCryptoTest` | the HChaCha20 reference vector, the public key of private key 1, the NIP-19 examples, the NIP-49 test vector, round trip, wrong password, damaged checksum |
| `KeyVaultTest` | password rules, create and unlock, name and e-mail normalization, wrong password and unknown account, duplicates, import, change password, delete, locked session, path tricks in the store |

The crypto functions were checked on the JVM against the published vectors of the NIPs. The Android build with Bouncy Castle and R8 is not yet verified.

## 5. Not yet covered

- **Event signing (BIP-340 Schnorr):** next step, with the Nostr client.
- **Android hardware protection:** wrapping the stored `ncryptsec` with an Android Keystore key and biometric unlock (opt in) is not implemented.
- **Relay backup of the encrypted key:** storing the `ncryptsec` on a relay under the account id is not implemented. Open risks: anyone who learns the id can fetch the key and try passwords offline (limited by scrypt and the password policy), and anyone can publish a fake entry under the same id. If it is added it MUST be opt in and use an author-bound event after the first login, not a lookup by tag alone.
- **Remote signer (NIP-46, Amber):** upgrade path from the draft, not implemented.
- **Attempt limit and lock after inactivity:** UI layer.

## 6. Checklist

- [x] Key generation, `npub` and `nsec`
- [x] NIP-49 encryption with the published test vector
- [x] Password policy with live feedback data
- [x] Local encrypted store, create, import, unlock, export, change password, delete
- [ ] First Android build with Bouncy Castle and R8
- [ ] UI: registration, login, backup screen with screenshot block
- [ ] BIP-340 signing
- [ ] Android Keystore wrapping, optional biometrics
- [ ] Decide on the relay backup

## 7. Open Questions

1. Is a relay backup of the encrypted key wanted at all, given the offline guessing risk?
2. Should username and e-mail be required for a purely local account, or only for the optional relay backup?
3. Is an optional passphrase-free mode with only Android Keystore protection acceptable?
