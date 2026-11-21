# Nearly: Tor and I2P Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers and release engineers of Nearly
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/src/main/java/org/elektronnet/mobile/node/` (`NetworkServices.kt`, `ChildService.kt`, `TorConfig.kt`, `I2pConfig.kt`, `TorLogStatus.kt`), `native-build/fetch-helpers.sh`, `native-build/helpers/`, `doc-elektron/guideline-tor-and-i2p.md`
- **Fork base:** [`tpo/core/tor`](https://gitlab.torproject.org/tpo/core/tor), [`PurpleI2P/i2pd`](https://github.com/PurpleI2P/i2pd)
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal

All Nearly traffic (Nostr relays, public posts, encrypted chats, model downloads) goes through Tor or I2P by default. IPv4 without a proxy is only a fallback and MUST be an explicit, visible choice of the user. Nearly has no node of its own and never acts as a relay or exit, it is a pure client.

## 2. Design taken over from elektron-net-mobile

- **Native child processes.** Tor and the I2P router are native executables shipped as `libtor.so` and `libi2pd.so` in `jniLibs`. A `NetworkServices` class starts them as child processes of the app and stops them with it. Nothing runs while the app does not need the network.
- **Loopback only.** Tor listens on `127.0.0.1` (SOCKS and control), the I2P router on `127.0.0.1`. No port is exposed to other devices.
- **Ports resolved at start.** Preferred ports are Tor SOCKS 9050 and control 9051, I2P SAM 7656. If a port is in use (Orbot, another app), the app moves up in steps of 10 to the next free port, once per start, before configuring the helpers and the HTTP client. The debug flavor prefers 19050, 19051 and 17656 so both flavors run side by side.
- **Cookie authentication.** Tor uses `CookieAuthentication 1` with the cookie file in the app-private data directory. No password.
- **Generated configuration.** `torrc` and `i2pd.conf` are written by the app, values are clamped and stripped of line breaks.
- **Dynamic libc.** Both programs MUST stay dynamically linked against the system C library. A fully static executable cannot resolve host names on Android.
- **Build.** Tor comes from the official GitLab repository only, the newest stable tag is resolved on every build and the exact commit and SHA-256 of each binary is recorded in a manifest next to the binaries. Dependencies (zlib, OpenSSL, libevent, Boost) are pinned with checksums. Nothing is built on the phone.

## 3. Differences for Nearly

| Topic | elektron-net-mobile | Nearly |
|---|---|---|
| Role | node, optional relay and exit | client only |
| Tor config | client or relay or exit | fixed client: `ClientOnly 1`, `ORPort 0`, `ExitRelay 0`, `ExitPolicy reject *:*` |
| Consumer | the node (`-onion`, `-proxy`, `-torcontrol`) | the Nostr client (OkHttp WebSocket through the SOCKS proxy) |
| I2P | SAM bridge only, SOCKS and HTTP proxy disabled | needs a proxy, see section 5 |
| Settings | relay bandwidth, accounting, exit policy | none of these, only the network mode |

The relay and exit settings, the monthly accounting and `ServerDNSResolvConfFile` are not needed and MUST NOT be copied.

## 4. Tor

- The Nostr client uses an OkHttp client with a `Proxy(SOCKS, 127.0.0.1:<socks>)`. Host names MUST be resolved by Tor, not by the device, so the client must not resolve the relay host itself (use an unresolved socket address).
- `.onion` relays are reached directly through the proxy. Clearnet relays are reached through Tor exits.
- Before the first connection the app waits for the bootstrap status of Tor (log line "Bootstrapped 100%") and shows the progress. Until then no Nostr connection is opened.
- Streams of different identities SHOULD use different SOCKS credentials (stream isolation), so a public identity and the session of a chat do not share a circuit.

## 5. I2P

The router is `i2pd`, as in the reference. Differences:

- `[sam]` stays enabled on loopback. The `[httpproxy]` and `[socksproxy]` sections are enabled on loopback as well, because Nostr relays are WebSocket servers and a plain proxy is the simplest way to reach `.i2p` relays from OkHttp. This is the part that elektron-net-mobile does not have and that MUST be verified on a device. If the SOCKS proxy of i2pd turns out to be unusable for WebSocket, a SAM stream client is the fallback.
- Other sections (`http` console, `bob`, `i2cp`, `i2pcontrol`, `upnp`) stay disabled. `floodfill = false` and transit is off (`notransit = true`) because Nearly is a client and must not relay for others on a phone.
- The first start needs several minutes (reseed, peer discovery, tunnels). "Running" means the router process is up. The app MUST show a separate state for "tunnels ready" and not open connections before.
- The reseed certificates are copied from the app assets to the I2P data directory on first start, as in the reference.

## 6. Network mode and fallback

| Mode | Behavior |
|---|---|
| Tor (default) | all traffic through Tor |
| I2P | all traffic through I2P, only `.i2p` relays and relays that are reachable through the I2P outproxy, if configured |
| Tor and I2P | each relay uses the network of its address, clearnet relays use Tor |
| Direct (IPv4) | no proxy, visible warning in the UI. Never the default and never switched on automatically |

If Tor or I2P cannot start, the app MUST NOT silently fall back to a direct connection. It shows the failure and offers the direct mode as an explicit choice.

## 7. Privacy rules

- No identifier of the user (npub, username, e-mail) is sent anywhere except as part of Nostr events through the chosen network.
- Location data is never sent in clear. Fuzzy position handling is a separate guideline.
- Logs of the helpers are sanitized (addresses, paths and keys removed) before they are shown or shared, as `LogSanitizer.kt` does in the reference.
- The app does not use the system DNS for relay host names in Tor or I2P mode.

## 8. Build and release

- Take over `native-build/fetch-helpers.sh`, the helper build container and `helpers-source.env`, reduced to the client case. The result is `libtor.so` and `libi2pd.so` per ABI (`arm64-v8a`, `x86_64`) with 16 KB page alignment, plus a manifest with repository, tag, commit and SHA-256.
- The release workflow MUST fail when a binary is missing or its checksum differs from the manifest.
- The binaries are built in CI only. The release page lists the Tor and i2pd versions.
- `jniLibs.useLegacyPackaging = true` is required so the executables are extracted to the native library directory and can be started.

## 9. License and attribution

`elektron-net-mobile` is MIT licensed, copyright Elektron Net Developers, Ali Kutlusoy and Milio aka 1000io. Code that is taken over keeps this notice. Tor (BSD 3-clause) and i2pd (BSD 3-clause) keep their own licenses and are listed in the third-party notice of the app.

## 10. Tests

| Test | What it proves |
|---|---|
| Config test | generated `torrc` and `i2pd.conf` for every mode, port resolution, clamping |
| Supervision test | helpers follow the settings, restart on a changed configuration, a failing or missing binary is reported and skipped |
| Tor integration test | a real Tor accepts the generated configuration and the Nostr client reaches a test relay through the SOCKS port |
| I2P integration test | a real i2pd accepts the generated configuration and the proxy answers |
| No-leak test | in Tor mode no connection leaves the app without the proxy |

## 11. Checklist

- [ ] Take over and reduce `NetworkServices`, `ChildService`, port resolution
- [ ] Client-only `torrc`, I2P configuration with proxy section
- [ ] Build scripts for `libtor.so` and `libi2pd.so`, manifest and checksum check in the release workflow
- [ ] OkHttp client with SOCKS proxy and unresolved host names
- [ ] Bootstrap and tunnel status in the UI
- [ ] Explicit direct mode, no silent fallback
- [ ] First run on a device: Tor bootstrap, `.onion` relay, I2P tunnels, I2P proxy with WebSocket

## 12. Open Questions

1. Is the i2pd SOCKS proxy sufficient for WebSocket to `.i2p` relays, or is a SAM client needed?
2. Which Nostr relays are reachable over `.onion` and `.i2p`, and does the project run its own?
3. Should stream isolation separate public posts from chats by default?
