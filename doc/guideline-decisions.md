# Nearly: Decision Record

- **Version:** 0.6 (draft)
- **Date:** November 21, 2026
- **Audience:** All contributors
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md), [`guideline-local-ai.md`](./guideline-local-ai.md), [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-nostr-client.md`](./guideline-nostr-client.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)

This record holds the decisions of the product owner. Where it differs from an older guideline, this record applies. The product concept is updated to match.

---

## 1. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Private photos | Sent end to end encrypted and stored for a limited time on a relay or blob server. The sender chooses how long, the default is 7 days. If a photo is not delivered before it expires, the sender is notified. Peer to peer transfer over onion services is dropped. |
| 2 | Video | Removed from the product completely. Photos and text only. |
| 3 | Order of work | Encrypted chat first, then profile, posts and follows. |
| 4 | Relays | Public relays at the start. A text file in the repository lists them (`relays.txt`), later with relays of the project. |
| 5 | Map precision | Coarse cells (about 1 km) as the default, finer levels optional with a warning. |
| 6 | Visibility | Users can choose "visible to followers only" in the swipe feed. |
| 7 | Chat preview | No preview of a private photo inside the chat message. |
| 8 | Public photos | Two to three existing Blossom servers at the start, an own server later. |
| 9 | Moderation | Own filters in the client. There are no administrators or owners, no moderation relay. |
| 10 | Key backup on a relay | No. Nothing about the key is stored on a relay. |
| 11 | Username | Required. It is also the public name in the app. |
| 12 | Key without password | Later, as an option, not in 0.1. |
| 13 | I2P | Version 1 ships with Tor only. |
| 14 | Relay connections | As stable as possible: reconnect with backoff, several relays at once. |
| 15 | License | MIT, see `LICENSE`. The license covers the code, user data is not part of it. |
| 16 | Language of the repository | Everything in the repository is English. |
| 17 | Age | 18 and older. The user enters the date of birth at registration, the profile shows the age as in Tinder. |
| 18 | Key | Derived from username, e-mail and password, so an account can always be restored with the same three entries. Supersedes the random key with a local encrypted copy. |
| 19 | Password | The app generates 5 random words from the BIP-39 English list (55 bits). The user may choose an own password if it reaches the same estimated strength. 3 words were requested and rejected as too weak (33 bits). |
| 20 | Password change | Not possible, a new password is a new identity. The UI says so at account creation. |
| 21 | Existing keys | Importing an existing Nostr key is not supported in the first version. |
| 22 | Interface style | Close to Instagram (profile, stories, photo grid) and Tinder (swipe cards), with a little of the vertical full-screen feel of TikTok, without video. |
| 23 | Local AI | Stays part of the product: translation, reply and conversation suggestions, summaries, spam filter, photo blur. All on the device. |
| 24 | Languages | Other European languages as well, for the **interface and for the AI translation**. First set as a proposal: German, English, French, Spanish, Italian, Portuguese, Dutch, Polish, Turkish. |
| 25 | Model size and delivery | Models are small. A small base pack is part of the build, all other packs are downloaded from the repository. Supersedes the rule of the product concept that no model is part of the app. Downloads use HTTPS only (not plain HTTP), are checked against a signed manifest and checksums, and go through Tor by default. |
| 26 | Where the models live | In a separate repository, **`kutlusoy/models`**, with one folder per app (`nearly/` for this app), so the owner can use it for other apps too. Supersedes the earlier decision for a `models/` folder in the app repository. The manifest and the signature are in the git tree, the model files are release assets (tags `nearly-v<version>`), because the git tree rejects files above 100 MB and keeps every binary in the history. The manifest is signed with an offline Ed25519 key and carries a growing `sequence` number. Details in `guideline-local-ai.md`. |

## 2. Consequences

- **Private photos:** the file is encrypted on the device with a random key, uploaded as an opaque blob with an expiration time, and the key and the address travel in the encrypted chat message (NIP-17). The server sees only an encrypted blob and a size. Needs a blob server that supports expiring uploads and is reachable through Tor. The notification for an undelivered photo needs a delivery receipt from the recipient.
- **Age and date of birth:** the date of birth is part of the profile data and is published as a derived age, not as the date. The exact rule (publish age or an age range, update on birthdays) is open. A date of birth that makes the user younger than 18 blocks registration.
- **No administrators:** takedown and legal contact in the store listing must be solved without an operator who can remove content from relays. This is a risk for the store release.
- **Username as public name:** uniqueness cannot be guaranteed on Nostr. The public key is the real identity, the username is a label. The UI shows a short part of the key next to the name to tell people apart.
- **Derived key:** the password is the only secret and the public key allows offline guessing. The strength of the password and the cost of the derivation are the whole protection, see `guideline-nostr-keys.md`.
- **Many languages:** every language is a separate download for the AI, with its own storage use and a quality check. Reply suggestions are offered only in languages that pass the quality test, other languages use English and the translation chain. The store listing needs a text per language, the interface needs a translation per language.
- **Model delivery:** the base pack adds to every APK and update, so it is limited in size. Packs are public files, so a direct download is possible when the user chooses it, plain HTTP is never used. The signature key of the manifest is a critical secret and stays offline.

## 3. Open Questions

1. Which blob server supports expiring uploads over Tor? Does the project run its own?
2. Is the age published as an exact number or as a range?
3. Which public relays are reachable through Tor and accept the event kinds we use?
4. Is 64 MiB for the key derivation acceptable on the weakest supported phones?
5. Is the first language set right, and which languages come next?
