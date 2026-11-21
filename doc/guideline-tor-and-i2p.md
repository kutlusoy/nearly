# Nearly: Tor and I2P Guideline

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers and release engineers of Nearly
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/src/main/java/org/elektronnet/mobile/node/` (`NetworkServices.kt`, `ChildService.kt`, `TorConfig.kt`, `I2pConfig.kt`, `TorLogStatus.kt`), `native-build/fetch-helpers.sh`, `native-build/helpers/`, `doc-elektron/guideline-tor-and-i2p.md`. In this repository: `app/src/main/java/com/kutlusoy/nearly/network/`, `native-build/`, `.github/workflows/helpers.yml`
- **Fork base:** [`tpo/core/tor`](https://gitlab.torproject.org/tpo/core/tor), [`PurpleI2P/i2pd`](https://github.com/PurpleI2P/i2pd)
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal

All Nearly traffic (Nostr relays, public posts, encrypted chats, model downloads) goes through Tor or I2P by default. IPv4 without a proxy is only a fallback and MUST be an explicit, visible choice of the user. Nearly has no node of its own and never acts as a relay or exit, it is a pure client.

## 2. Design taken over from elektron-net-mobile

- **Native child processes.** Tor and the I2P router are native executables shipped as `libtor.so` and `libi2pd.so` in `jniLibs`. `NetworkServices` starts them as child processes of the app and stops them with it. Nothing runs while the app does not need the network.
- **Loopback only.** Tor listens on `127.0.0.1` (SOCKS and control), the I2P router on `127.0.0.1` (SOCKS). No port is exposed to other devices.
- **Ports resolved at start.** Preferred ports are Tor SOCKS 9050 and control 9051, I2P SOCKS 4447. If a port is in use (Orbot, another app), `NetworkPorts.resolved()` moves up in steps of 10 to the next free port, once per start. The debug flavor prefers 19050, 19051 and 14447 so both flavors run side by side.
- **Cookie authentication.** Tor uses `CookieAuthentication 1` with the cookie file in the app-private data directory. No password.
- **Generated configuration.** `torrc` and `i2pd.conf` are written by the app.
- **Dynamic libc.** Both programs MUST stay dynamically linked against the system C library. A fully static executable cannot resolve host names on Android. The helper build checks this and the 16 KB page alignment for every binary.
- **Build.** Tor comes from the official GitLab repository only, the newest stable tag is resolved on every build and the exact commit and SHA-256 of each binary is recorded in a manifest next to the binaries. Dependencies (zlib, OpenSSL, libevent, Boost) are pinned with checksums. Nothing is built on the phone.

## 3. Differences for Nearly

| Topic | elektron-net-mobile | Nearly |
|---|---|---|
| Role | node, optional relay and exit | client only |
| Tor config | client or relay or exit | fixed client: `ClientOnly 1`, `ORPort 0`, `ExitRelay 0`, `ExitPolicy reject *:*` |
| Consumer | the node (`-onion`, `-proxy`, `-torcontrol`) | the Nostr client, through `RelayRouter` |
| I2P | SAM bridge only, SOCKS and HTTP proxy disabled | SOCKS proxy only, SAM disabled |
| Settings | relay bandwidth, accounting, exit policy | network mode and I2P IPv6 |

The relay and exit settings, the monthly accounting, `ServerDNSResolvConfFile`, the stable onion and I2P address keys and the node arguments are not needed and MUST NOT be copied.

## 4. Code

| File | Purpose |
|---|---|
| `NetworkMode.kt` | `Tor` (default), `I2p`, `TorAndI2p`, `Direct`, plus `NetworkSettings` |
| `NetworkPorts.kt` | preferred ports and `resolved()` |
| `TorConfig.kt`, `I2pConfig.kt` | generated `torrc` and `i2pd.conf` |
| `ChildService.kt`, `OutputBuffer.kt` | one child process, its state (`Starting`, `Running`, `Failed`) and its last output lines |
| `NetworkServices.kt` | starts, restarts and stops the helpers from `NetworkSettings` and an active flag, reports `torReady` and `i2pReady` |
| `RelayRouter.kt` | decides per relay host: SOCKS proxy of Tor or I2P, direct, or blocked |
| `TorLogStatus.kt`, `LogSanitizer.kt` | bootstrap percentage, log cleaning (IPs, onion and I2P addresses, Nostr keys, paths) |
| `AndroidNetwork.kt` | Android glue: native library directory, private data directory, copy of the reseed certificates from the assets |

## 5. Tor

- Connections to relays use the proxy from `RelayRouter.route(host)`. The target address MUST be created with `RelayRouter.target(host, port)`, which keeps the host name unresolved so Tor resolves it, never the device.
- `.onion` relays are reached directly through the proxy. Clearnet relays are reached through Tor exits.
- `NetworkServices.torReady` is true only when the process answers on the SOCKS port and the log has reported `Bootstrapped 100%`. The SOCKS port opens before bootstrapping ends, so the port alone is not enough. No Nostr connection is opened before `torReady`.
- Streams of different identities SHOULD use different SOCKS credentials (stream isolation). Not implemented yet.

## 6. I2P

The router is `i2pd`. Differences from the reference:

- `[socksproxy]` is enabled on loopback and is the only service. Nostr relays are WebSocket servers, a SOCKS proxy is the simplest way to reach `.i2p` relays from an HTTP client. `[sam]`, `[httpproxy]`, `[http]`, `[bob]`, `[i2cp]`, `[i2pcontrol]` and `[upnp]` are disabled.
- `floodfill = false` and `notransit = true`: Nearly is a client and does not relay for others on a phone.
- The first start needs several minutes (reseed, peer discovery, tunnels). `i2pReady` currently means that the router answers on its SOCKS port. That does not prove that tunnels exist. A reliable tunnel signal is not defined yet and MUST be checked on a device, until then the UI shows I2P as "starting" for a fixed time after the first start and connections may fail early.
- The reseed certificates are copied from the app assets to the I2P data directory on first start.
- Whether the i2pd SOCKS proxy works for WebSocket connections to `.i2p` relays is not verified.

## 7. Network mode and fallback

| Mode | Behavior (`RelayRouter`) |
|---|---|
| Tor (default) | everything through Tor, `.i2p` blocked |
| I2P | only `.i2p` hosts through I2P, everything else blocked |
| Tor and I2P | `.i2p` through I2P, everything else through Tor |
| Direct (IPv4) | no proxy, `.onion` and `.i2p` blocked. Visible warning in the UI. Never the default and never switched on automatically |

If Tor or I2P cannot start, the app MUST NOT silently fall back to a direct connection. `RelayRouter` never returns `Direct` unless the mode is `Direct`. The UI shows the failure and offers the direct mode as an explicit choice.

## 8. Privacy rules

- No identifier of the user (npub, username, e-mail) is sent anywhere except as part of Nostr events through the chosen network.
- Location data is never sent in clear. Fuzzy position handling is a separate guideline.
- Logs of the helpers are cleaned by `LogSanitizer` before they are shown or shared.
- In Tor and I2P mode the app does not use the system DNS for relay host names.

## 9. Build and release

- `native-build/build-helpers.sh [arm64-v8a|x86_64|all]` resolves the newest stable Tor and i2pd tags (`fetch-helpers.sh`), builds both in the pinned NDK container (`docker/Dockerfile.ndk`, `helpers/build-helpers-in-container.sh`) and installs the results into `app/src/main/jniLibs/<abi>/`, `assets/i2pd/certificates` and `assets/network-manifest/helpers-<abi>.env` (repository, tag, commit and SHA-256 of both binaries).
- The NDK version is in `native-build/ndk.env`. `NDK_SHA256` MUST be pinned there after comparing `native-build/pin-ndk.sh` with the value Google publishes. Until it is, the workflow computes it and prints a warning.
- `.github/workflows/helpers.yml` builds the helpers and publishes the artifact `helpers`. `release.yml` and `test-release.yml` use it. The release workflow fails when a binary is missing or differs from its manifest. CI (`ci.yml`) builds without the helpers, so the app shows the networks as not included.
- `jniLibs.useLegacyPackaging = true` and the ABI filter (`arm64-v8a`, `x86_64`) are set in `app/build.gradle`.
- The release notes list the Tor and i2pd versions (planned, not in the notes yet).

## 10. License and attribution

`elektron-net-mobile` is MIT licensed, copyright Elektron Net Developers, Ali Kutlusoy and Milio aka 1000io. Code that is taken over keeps this notice. Tor (BSD 3-clause) and i2pd (BSD 3-clause) keep their own licenses and are listed in the third-party notice of the app.

## 11. Tests

| Test | What it proves |
|---|---|
| `NetworkConfigTest` | client-only `torrc`, `i2pd.conf` with only the SOCKS proxy, port resolution, log cleaning, bootstrap parsing |
| `RelayRouterTest` | route per mode and host type, no direct route for darknet hosts, unresolved target |
| `NetworkServicesTest` | missing binaries are skipped, only the needed helper starts, `active = false` stops everything, a failing binary is reported with its output (uses stand-in shell scripts) |

Not covered: the Android cross-build of both helpers, running them on a device, a real Tor bootstrap, an I2P session, and a WebSocket connection through either proxy.

## 12. Checklist

- [x] `NetworkServices`, `ChildService`, port resolution
- [x] Client-only `torrc`, I2P configuration with SOCKS proxy
- [x] `RelayRouter` with no silent fallback
- [x] Build scripts for `libtor.so` and `libi2pd.so`, manifest check in the release workflow
- [ ] Pin `NDK_SHA256` in `native-build/ndk.env`
- [ ] First CI build of both helpers
- [ ] OkHttp client with the route from `RelayRouter` (with the Nostr client)
- [ ] UI: network mode, status, explicit direct mode
- [ ] Stream isolation
- [ ] First run on a device: Tor bootstrap, `.onion` relay, I2P tunnels, I2P proxy with WebSocket

## 13. Open Questions

1. Is the i2pd SOCKS proxy sufficient for WebSocket to `.i2p` relays, or is a SAM client needed?
2. Which Nostr relays are reachable over `.onion` and `.i2p`, and does the project run its own?
3. How is I2P tunnel readiness detected reliably with i2pd?
4. Should stream isolation separate public posts from chats by default?
