# Nearly: Product Concept (Swipe, Map, Short Video, Pages)

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** none yet for the product features. Network layer: `app/src/main/java/com/kutlusoy/nearly/network/`, key management: `app/src/main/java/com/kutlusoy/nearly/nostr/`
- **See also:** [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), `nearly-entwurf.md` (product draft v0.1), [`permissionlesstech/bitchat`](https://github.com/permissionlesstech/bitchat) (geohash channels, to be read in the source before implementation)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Nostr event kinds below follow the NIPs and the BitChat documentation as known when this draft was written and MUST be checked against the current NIP texts and the BitChat source before implementation.

---

## 1. Idea in one paragraph

Nearly combines three things: the **content formats of Instagram and TikTok** (photos, short videos of at most 60 seconds, text), the **decision loop of Tinder** (swipe on people, mutual interest opens a chat) and a **local map**. Everyone has a profile page like on Instagram. Everyone appears in the swipe timeline. Only people who are currently active appear on the map. All of it runs on the existing **Nostr infrastructure** (relays, events, NIPs), no server of our own. Private photos and videos never touch a relay or a media server, they travel **peer to peer** only.

## 2. Content types

| Type | Limit | Where it lives |
|---|---|---|
| Text post | length by relay policy, the app suggests 500 characters | Nostr relays, kind 1 |
| Photo post, one or more photos | up to 10 photos, caption | event on relays (NIP-68, kind 20), files on media servers (section 7.1) |
| Short video | **60 seconds maximum**, vertical preferred | event on relays (NIP-71, kind 22), file on media servers (section 7.1) |
| Story | photo or video of at most 15 seconds, disappears after 24 hours | same as above with an expiration tag (NIP-40) |
| Reaction | like on a post | kind 7 |
| Private message, text | text | NIP-17 gift wrap (kind 1059) with NIP-44 encryption, on relays |
| Private photo, video, voice | video 60 seconds maximum | **peer to peer only**, never on a relay or media server (section 7.2) |

The 60 second limit MUST be enforced in the client at recording and import (cut or reject), not only on the server.

## 3. Modes

### 3.1 Swipe mode (default)

- A vertical or card based timeline of people. Each card shows the profile photo or the newest video, name, age range, distance class and the latest post.
- Gestures as in the draft: right = interest, left = "not now" (snooze, comes back later), up = skip (no signal). Follow is a separate button and independent of the swipe.
- Everyone who is reachable by the filters appears here, active or not. The timeline is the main place to find people.
- Interest is private. Only a mutual interest opens a chat. A right swipe MUST NOT be visible to the other person unless the interest is mutual. It is sent as an encrypted NIP-17 style message, not as a public reaction.
- Videos auto play muted in the card, tap for sound and the full video.

### 3.2 Map mode

- The map (MapLibre, OpenStreetMap) shows **only people who are active now**. Inactive people never appear on the map.
- "Active" is an explicit, temporary state: the user switches it on for a chosen duration (15 minutes, 1 hour, 4 hours, until midnight). Opening the app does not switch it on.
- A marker shows the profile photo, the fuzzy position and the activity status. Tapping it opens the page of the person (section 5) with the swipe actions.
- Position is always fuzzy (section 6). The marker is never the exact position.

### 3.3 Feed

- A following feed (posts of people the user follows, newest first) and a discovery feed (short videos nearby, vertical, TikTok style).

## 4. Navigation

Bottom bar: **Swipe**, **Map**, **Feed**, **Chats**, **Profile**. A floating button creates a post, story or video. The user can lock the app to one mode in settings.

## 5. Pages like Instagram

- **Profile page:** photo, name, short text, interests, number of posts and followers, buttons Follow, Interest, Message, and a grid of photos and videos.
- **Highlights:** stories that the user pins to the profile and that do not expire.
- **Own page:** edit profile, manage posts, choose who may see what (public, followers only), manage the active status on the map.
- Pages are built from Nostr events of the user (profile kind 0, posts, follows kind 3). Nothing is stored on a central server.

## 6. Location and presence, as in BitChat

BitChat uses Nostr for location based chat: a position is turned into a **geohash**, events carry it in a `g` tag, **ephemeral events** (kinds 20000 to 29999, not stored by relays) carry the messages and a periodic presence heartbeat, and the app talks to **relays chosen by geographic proximity** from a directory of relays with their coordinates. Nearly takes the same building blocks. The BitChat source is the reference and MUST be read before implementation, the details below come from its documentation.

- **Presence as a heartbeat (MUST).** Active users send a signed ephemeral event (the BitChat presence kind 20001 is the model) with a `g` tag while the active state is on. Relays do not store it. When the heartbeats stop, or the user switches off, the person disappears from the map after a short timeout on the viewers' side. No location history exists anywhere.
- **Coarse cells (MUST).** The `g` tag carries a geohash of **low precision**. Users choose the precision level in settings (BitChat has several levels from region down to block). The default SHOULD be coarse (about 1 km cells). Finer cells reveal more and are opt in with a warning. Exact coordinates MUST NOT leave the device. The client adds a random shift inside the cell and sends heartbeats at a fixed, not too frequent interval.
- **Geo relays (SHOULD).** Presence and map queries go to relays near the cell, from a directory shipped with the app and updated as a signed file. Relays are reached through Tor or I2P as in the network guideline. The directory MUST include `.onion` relays.
- **Query privacy.** A subscription with a `g` filter tells the relay which area the user looks at. The client SHOULD subscribe to a coarser prefix than needed and filter on the device.
- **Matching on the map.** The map shows presence events of the visible cells. Tapping a marker opens the profile, interest signals stay private (section 3.1).
- **Permission.** Only approximate location, no background location. Presence is sent only while the user has it on and the app runs.
- **Hide.** Users can block people and can use the app without presence. The swipe timeline works without a position, using chosen regions.
- **Not in BitChat.** BitChat also has a Bluetooth mesh without any internet. Nearly does not plan that for now.

## 7. Media

### 7.1 Public media

- Files of public posts, stories and profile images go to Blossom compatible media servers (content addressed by SHA-256), the event carries URL, hash and metadata. Servers MUST be reachable through Tor or I2P. Users can configure their own server.
- The client MUST strip EXIF and GPS data and re-encode locally before upload. H.264 or H.265, 720p default, a bitrate that keeps a 60 second clip small enough for Tor (target below about 15 MB, to be measured). A poster image and a low quality preview are created.
- Loading: poster first, video on demand, prefetch of the next card only on Wi-Fi or within the data budget. A data saver mode shows photos only.

### 7.2 Private media: peer to peer only

Private photos, videos and voice messages MUST NOT be uploaded to any relay, media server or cloud, not even encrypted. Only the offer travels through the encrypted chat, the file travels directly from device to device over Tor.

Flow:

1. The sender encrypts the file on the device with a fresh random key (chunked XChaCha20-Poly1305) and computes the hash of the ciphertext.
2. The sender starts a temporary onion service through the Tor control port that the app already has (ADD_ONION with a one-time key, no key file) and serves the encrypted file on a loopback port.
3. The sender sends the offer as an encrypted NIP-17 message: onion address, size, hash, key, expiry time (default 24 hours), file type and duration. No file bytes and no preview of the content (open question 5).
4. The receiver downloads through Tor, with resume, checks the hash, decrypts, and stores the file in private app storage. A receipt goes back through the chat.
5. After the receipt or the expiry the sender closes the onion service. Nothing remains on any server.

Consequences that MUST be explained in the UI:

- **Both devices must be online.** There is no store and forward. If the receiver is offline the transfer stays "waiting" and the sender keeps the service running until the expiry. The sender must keep Nearly running, Android needs a foreground service for this, and the system limits how long it may run.
- **Slow.** A 60 second video through a Tor onion service takes minutes. A progress display and the option to cancel are needed.
- **No forwarding protection.** The receiver can always copy what they see. The app only blocks screenshots in private media screens, offers "view once" and deletes cache files after viewing.
- **I2P.** The same flow with an I2P server tunnel is possible with i2pd but needs a different router configuration (server tunnel, not just the SOCKS proxy). Not planned for the first version, Tor only.

Related rules:

- Private media is stored only in encrypted app storage and MUST NOT go into image or video caches that are shared with public media.
- Chat text and the offer stay on relays (NIP-17 gift wrap), encrypted.

## 8. Rules the store and the law require

- **Age:** 18+ only, with a confirmation at account creation.
- **Report and block:** every post, profile and chat has report and block. Blocking and muting work locally by public key. Reports go to the user's own moderation list and optionally to a community moderation relay.
- **Content policy and takedown contact** in the app terms and in the store listing. Illegal material MUST NOT be shown or cached. Peer to peer private media cannot be inspected by anyone but the receiver, this is a legal and store policy question to clarify before release.
- **Store listing:** Google Play rules for dating and user generated content apply, check the current policy before the first upload.
- **No central moderation is possible.** Only client side filters, block lists and optional shared lists. The terms MUST say so.

## 9. Role of the local AI

Translation of posts and chats, spam and bot filter, blur for sensitive media, reply suggestions in chats (opt in). Details are in a separate guideline. Classification of private media would run on the receiver's device after download.

## 10. Network impact

All relay traffic and all media transfers run through Tor or I2P as in `guideline-tor-and-i2p.md`. Videos are slow. The first version SHOULD ship text and photos first and add video once load times are measured over Tor. The Tor configuration needs a check that temporary onion services are allowed in the client-only setup.

## 11. Staging

| Version | Content |
|---|---|
| 0.0.x | skeleton, network layer (done), key management (done) |
| 0.1 | Nostr client: signing, relay connection through Tor, profile, text and photo posts, follows |
| 0.2 | swipe mode with private interest signal, encrypted chat |
| 0.3 | map mode with geohash presence |
| 0.4 | private photos peer to peer, then short video (60 seconds), stories |
| 0.5 | local AI: translation and filters |
| 1.0 | moderation tools, age gate, store release |

## 12. Open Questions

1. Which geohash precisions are offered, and is the coarse default (about 1 km) acceptable for the map?
2. Who maintains the directory of geo relays, and does the project run its own `.onion` relays?
3. Is video part of the first usable version, or does it follow after photos?
4. Who moderates: only filters and personal lists, or also a moderation relay?
5. Is a tiny blurred preview of a private photo inside the encrypted chat message acceptable, or must the chat carry nothing derived from the file?
6. Is a peer to peer transfer that needs both devices online acceptable for private media, or is an encrypted temporary store on a relay allowed as an option for offline recipients (this would break the rule "nowhere else")?
7. Can users choose to be visible in the swipe timeline only to followers?
