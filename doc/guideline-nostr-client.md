# Nearly: Nostr Client Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/` (`Schnorr.kt`, `Event.kt`, `RelayConnection.kt`, `NostrClient.kt`), tests in `app/src/test/java/com/kutlusoy/nearly/nostr/`
- **See also:** [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-product-concept.md`](./guideline-product-concept.md), [NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Components

| File | Purpose |
|---|---|
| `Schnorr.kt` | BIP-340 signing and verification on secp256k1 (Bouncy Castle curve arithmetic) |
| `Event.kt` | `Event` (NIP-01 id, signing from a `Session`, JSON), `Filter`, and the relay messages `EVENT`, `EOSE`, `OK`, `NOTICE`, `CLOSED` |
| `RelayConnection.kt` | one WebSocket to one relay (OkHttp), subscribe, unsubscribe, publish |
| `NostrClient.kt` | several relays at once: publish to all, merged subscriptions, duplicate and forgery filtering |

## 2. Rules

- **Signing (MUST):** events are signed only through a `Session`. The private key is handed to `Schnorr.sign` inside `useSecretKey` and not kept.
- **Every incoming event is verified (MUST):** the id is recomputed from the content and the signature is checked against the public key. Events that fail are dropped silently. Relays are not trusted.
- **Event id:** the serialization follows NIP-01. Only the characters named there are escaped. A test compares the id with a SHA-256 of the exact string.
- **Network (MUST):** every connection asks `RelayRouter.route(host)`. A blocked route means no connection, with the reason reported through `NostrClient.failures`. There is no fallback to a direct connection.
- **TLS (MUST):** `ws://` is refused for clearnet hosts. Only `.onion` and `.i2p` hosts may use `ws://`, because those connections are encrypted end to end. Tests can allow it explicitly.
- **No local DNS (MUST):** over Tor or I2P the OkHttp client uses a `Dns` that always fails, so the device never resolves a relay name. With a SOCKS proxy OkHttp passes the unresolved host to the proxy.
- **Bad relay data (MUST NOT crash):** malformed or unknown messages are ignored.
- **Publishing:** events are kept for relays that are not connected yet and sent when the connection opens. `OK` results are reported per relay.
- **Deduplication:** the same event from several relays is delivered once (bounded memory of 5,000 ids).

## 3. Tests

| Test | What it proves |
|---|---|
| `EventTest` | BIP-340 reference vectors 0 and 1, wrong signature or key is rejected, a signed event is valid and every changed field makes it invalid, NIP-01 id serialization, filter JSON, relay message parsing |
| `NostrClientTest` | with mock relays: valid events arrive once, a forged id and a tampered event are dropped, publish reaches the relay and reports `OK`, darknet and TLS rules are enforced before any connection |

Checked on the JVM. Not yet checked: a real relay, a connection through Tor or I2P, the Android build with OkHttp and R8.

## 4. Not yet covered

- **Reconnect with backoff** and relay health. A closed connection is only recorded.
- **Relay list:** a configured list of relays, `.onion` first, and reading and writing the user's own relay list (NIP-65).
- **Encrypted chat:** NIP-17 gift wrap with NIP-44. Needs ECDH, HKDF and ChaCha20 as in NIP-44, the next step.
- **Profile, posts, follows:** kinds 0, 1, 3 and the picture kind, built on this client.
- **Proof of work, authentication (NIP-42) and rate limits** of relays.
- **Geohash presence:** ephemeral events with a `g` tag, see the product concept.
- **Stream isolation:** separate Tor circuits for public and private traffic.

## 5. Checklist

- [x] BIP-340 sign and verify with reference vectors
- [x] Events, filters, relay messages
- [x] Relay connection through `RelayRouter`, no local DNS, TLS rule
- [x] Multi relay client with verification and deduplication
- [ ] Reconnect and relay list
- [ ] NIP-44 and NIP-17 chat
- [ ] Profile, posts, follows
- [ ] Test against a real relay over Tor

## 6. Open Questions

1. Which relays does the first version ship with, and are there `.onion` relays among them?
2. Should the client connect to all relays or a small subset chosen per task, to save Tor bandwidth?
