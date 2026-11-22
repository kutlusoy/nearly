# Nearly: Nostr Client Guideline

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/` (`Schnorr.kt`, `Event.kt`, `RelayConnection.kt`, `NostrClient.kt`, `Reconnect.kt`, `Nip44.kt`, `GiftWrap.kt`), tests in `app/src/test/java/com/kutlusoy/nearly/nostr/`
- **See also:** [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-product-concept.md`](./guideline-product-concept.md), [NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md), [NIP-17](https://github.com/nostr-protocol/nips/blob/master/17.md), [NIP-44](https://github.com/nostr-protocol/nips/blob/master/44.md), [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Components

| File | Purpose |
|---|---|
| `Schnorr.kt` | BIP-340 signing and verification on secp256k1 (Bouncy Castle curve arithmetic) |
| `Event.kt` | `Event` (NIP-01 id, signing from a `Session`, JSON), `Filter`, and the relay messages `EVENT`, `EOSE`, `OK`, `NOTICE`, `CLOSED` |
| `RelayConnection.kt` | one WebSocket to one relay (OkHttp), subscribe, unsubscribe, publish |
| `NostrClient.kt` | several relays at once: publish, merged subscriptions, duplicate and forgery filtering, reconnect and rotation |
| `Reconnect.kt` | `Backoff`, `Scheduler`, and `RelayList` (reads `relays.txt`) |
| `Nip44.kt` | NIP-44 version 2 encryption |
| `GiftWrap.kt` | NIP-59 gift wrap and NIP-17 direct messages |
| `AndroidRelays.kt` | loads `relays.txt` from the app assets |

## 2. Rules

- **Signing (MUST):** events are signed only through a `Session`. The private key is handed to `Schnorr.sign` inside `useSecretKey` and not kept.
- **Every incoming event is verified (MUST):** the id is recomputed from the content and the signature is checked against the public key. Events that fail are dropped silently. Relays are not trusted.
- **Event id:** the serialization follows NIP-01. Only the characters named there are escaped. A test compares the id with a SHA-256 of the exact string.
- **Network (MUST):** every connection asks `RelayRouter.route(host)`. A blocked route means no connection. There is no fallback to a direct connection.
- **TLS (MUST):** `ws://` is refused for clearnet hosts. Only `.onion` and `.i2p` hosts may use `ws://`, because those connections are encrypted end to end. Tests can allow it explicitly.
- **No local DNS (MUST):** over Tor or I2P the OkHttp client uses a `Dns` that always fails, so the device never resolves a relay name.
- **Bad relay data (MUST NOT crash):** malformed or unknown messages are ignored.
- **Deduplication:** the same event from several relays is delivered once (bounded memory of 5,000 ids).
- **Private messages:** NIP-44 for the encryption, NIP-59 gift wrap with a seal signed by the sender and a wrap signed by a one-time key, random timestamps up to two days in the past. The author of the message MUST equal the signer of the seal, otherwise the message is dropped. Every message is wrapped twice, for the recipient and for the sender.

## 3. Relay list and connection stability

- **Relay list:** `relays.txt` in the repository root is copied into the app assets by the build (`syncRelays` in `app/build.gradle`) and read with `RelayList.parse`. Comment lines, invalid and duplicate entries are dropped. Darknet addresses (`.onion`, `.i2p`) come first.
- **Connections:** the client keeps `targetConnections` relays connected (default 4). The other relays of the list are spares.
- **Reconnect:** a lost connection is retried with `Backoff`: 2 s, 4 s, 8 s and so on up to 5 minutes, with 20 percent jitter. A success resets the counter.
- **Rotation:** after 3 failed attempts a relay is replaced by a spare one and moves to the end of the list, so a dead relay does not block a connection slot.
- **Permanent failure:** a blocked route, an unencrypted address or an invalid address is not retried. The relay is marked `Failed` and replaced by a spare one.
- **Closed by the relay:** a close frame of the relay is answered, so the connection is not left half open.
- **After a reconnect:** subscriptions are sent again and every published event that this relay has not confirmed (`OK`) is sent again. The memory is bounded to the last 100 events.
- **Retry on demand:** `retryNow()` resets the counters and tries again at once. The app calls it when the network returns, when Tor is ready or when the network mode changes.
- **Status:** `statuses()` gives per relay the state (`Idle`, `Standby`, `Connecting`, `Open`, `WaitingToRetry`, `Failed`), the attempts, the time to the next retry and the last error, for the status screen.

## 4. Tests

| Test | What it proves |
|---|---|
| `EventTest` | BIP-340 reference vectors 0 and 1, wrong signature or key is rejected, a signed event is valid and every changed field makes it invalid, NIP-01 id serialization, filter JSON, relay message parsing |
| `NostrClientTest` | with mock relays: valid events arrive once, a forged id and a tampered event are dropped, publish reaches the relay and reports `OK`, darknet and TLS rules are enforced before any connection |
| `ReconnectTest` | backoff growth, bounds and cap, relay list parsing, retry after a lost connection with subscription and unconfirmed event sent again, no retry for a blocked relay until the mode changes, rotation to a spare relay, cancel on disconnect |
| `Nip44Test` | conversation key vector, the padding table of the specification, round trip, tampering, wrong key |
| `GiftWrapTest` | recipient and sender can read, others cannot, hidden sender and time, forged author is rejected, tampered and foreign events are ignored |

Checked on the JVM with mock relays. Not yet checked: a real relay, a connection through Tor, the Android build with OkHttp and R8.

## 5. Not yet covered

- **Relay selection by quality:** the rotation is by failure only. Measuring latency and success rate per relay is open.
- **Network events:** the hook that calls `retryNow()` on a connectivity change and on Tor readiness belongs to the Android layer and is not written.
- **Chat store and screens:** messages are not stored locally yet, there are no screens.
- **Profile, posts, follows:** kinds 0, 1, 3 and the picture kind, built on this client.
- **Proof of work, authentication (NIP-42) and rate limits** of relays.
- **Geohash presence:** ephemeral events with a `g` tag, see the product concept.
- **Stream isolation:** separate Tor circuits for public and private traffic.

## 6. Checklist

- [x] BIP-340 sign and verify with reference vectors
- [x] Events, filters, relay messages
- [x] Relay connection through `RelayRouter`, no local DNS, TLS rule
- [x] Multi relay client with verification and deduplication
- [x] Reconnect with backoff, rotation and relay list
- [x] NIP-44 and NIP-17 chat primitives
- [ ] Android hook for network and Tor readiness
- [ ] Local message store
- [ ] Profile, posts, follows
- [ ] Test against a real relay over Tor

## 7. Open Questions

1. Which relays does the first version ship with, and are there `.onion` relays among them?
2. Is 4 connected relays the right default for Tor bandwidth and battery?
