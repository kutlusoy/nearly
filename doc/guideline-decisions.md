# Nearly: Decision Record

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** All contributors
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md), [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-nostr-client.md`](./guideline-nostr-client.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)

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
| 10 | Key backup on a relay | No. Only the local backup through the `nsec` export. |
| 11 | Username | Required. It is also the public name in the app. |
| 12 | Key without password | Later, as an option, not in 0.1. |
| 13 | I2P | Version 1 ships with Tor only. |
| 14 | Relay connections | As stable as possible: reconnect with backoff, several relays at once. |
| 15 | License | MIT, see `LICENSE`. The license covers the code, user data is not part of it. |
| 16 | Language | Everything in the repository is English. |
| 17 | Age | 18 and older. The user enters the date of birth at registration, the profile shows the age as in Tinder. |

## 2. Consequences

- **Private photos:** the file is encrypted on the device with a random key, uploaded as an opaque blob with an expiration time, and the key and the address travel in the encrypted chat message (NIP-17). The server sees only an encrypted blob and a size. Needs a blob server that supports expiring uploads and is reachable through Tor. The notification for an undelivered photo needs a delivery receipt from the recipient.
- **Age and date of birth:** the date of birth is part of the profile data and is published as a derived age, not as the date. The exact rule (publish age or an age range, update on birthdays) is open. A date of birth that makes the user younger than 18 blocks registration.
- **No administrators:** takedown and legal contact in the store listing must be solved without an operator who can remove content from relays. This is a risk for the store release.
- **Username as public name:** uniqueness cannot be guaranteed on Nostr. The public key is the real identity, the username is a label. The UI shows a short part of the key next to the name to tell people apart.

## 3. Open Questions

1. Which blob server supports expiring uploads over Tor? Does the project run its own?
2. Is the age published as an exact number or as a range?
3. Which public relays are reachable through Tor and accept the event kinds we use?
