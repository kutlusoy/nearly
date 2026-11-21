# Nearly: Product Concept (Swipe, Map, Short Video, Pages)

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** none yet, see `app/src/main/java/com/kutlusoy/nearly/network/` for the network layer this concept builds on
- **See also:** [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), `nearly-entwurf.md` (product draft v0.1)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Nostr event kinds below follow the NIPs as known when this draft was written and MUST be checked against the current NIP texts before implementation.

---

## 1. Idea in one paragraph

Nearly combines three things: the **content formats of Instagram and TikTok** (photos, short videos of at most 60 seconds, text), the **decision loop of Tinder** (swipe on people, mutual interest opens a chat) and a **local map**. Everyone has a profile page like on Instagram. Everyone appears in the swipe timeline. Only people who are currently active appear on the map.

## 2. Content types

| Type | Limit | Nostr (to verify) |
|---|---|---|
| Text post | length by relay policy, the app suggests 500 characters | kind 1 |
| Photo post, one or more photos | up to 10 photos, caption | picture event (NIP-68, kind 20) |
| Short video | **60 seconds maximum**, vertical preferred | video event (NIP-71, kind 22 for short video) |
| Story | photo or video of at most 15 seconds, disappears after 24 hours | same kinds with an expiration tag (NIP-40) |
| Reaction | like on a post | kind 7 |
| Private message | text, photo, short video, voice | NIP-17 gift wrap (kind 1059) with NIP-44 encryption |

The 60 second limit MUST be enforced in the client at recording and import (cut or reject), not only on the server. Relays and media servers MAY enforce it additionally.

## 3. Modes

### 3.1 Swipe mode (default)

- A vertical or card based timeline of people. Each card shows the profile photo or the newest video, name, age range, distance class and the latest post.
- Gestures as in the draft: right = interest, left = "not now" (snooze, comes back later), up = skip (no signal). Follow is a separate button and independent of the swipe.
- Everyone who is reachable by the filters appears here, active or not. The timeline is the main place to find people.
- Interest is private. Only a mutual interest opens a chat. A right swipe MUST NOT be visible to the other person unless the interest is mutual. This is implemented as an encrypted signal (NIP-17 style), not as a public reaction.
- Videos auto play muted in the card, tap for sound and the full video.

### 3.2 Map mode

- The map (MapLibre, OpenStreetMap) shows **only people who are active now**. Inactive people never appear on the map.
- "Active" is an explicit, temporary state: the user switches it on for a chosen duration (15 minutes, 1 hour, 4 hours, until midnight). When the time ends the marker disappears on its own. Opening the app does not switch it on.
- A marker shows the profile photo, the fuzzy position and the activity status. Tapping it opens the page of the person (section 5) with the swipe actions.
- Position is always fuzzy (section 6). The marker is never the exact position.

### 3.3 Feed

- A following feed (posts of people the user follows, newest first) and a discovery feed (short videos nearby, vertical, TikTok style). Both can be reached from the bottom navigation.

## 4. Navigation

Bottom bar: **Swipe**, **Map**, **Feed**, **Chats**, **Profile**. A floating button creates a post, story or video. The user can lock the app to one mode (for example swipe only) in settings.

## 5. Pages like Instagram

- **Profile page:** photo, name, short text, interests, number of posts and followers, buttons Follow, Interest, Message, and a grid of photos and videos.
- **Highlights:** stories that the user pins to the profile and that do not expire.
- **Own page:** edit profile, manage posts, choose who may see what (public, followers only), manage the active status on the map.
- Pages are built from Nostr events of the user (profile kind 0, posts, follows kind 3). Nothing is stored on a central server.

## 6. Location and presence (privacy)

- Presence is a replaceable, expiring Nostr event (NIP-78 application data, kind 30078, with an expiration tag from NIP-40) that carries a **geohash of low precision** and the end time. After the end time clients and relays drop it.
- The draft asks for 100 to 500 m. A geohash cell of precision 7 is about 150 m and reveals too much when combined with repeated updates and time. The default SHOULD be precision 6 (about 1.2 km by 0.6 km), and the client MUST add a random shift inside the cell and never publish a position more often than every few minutes. Exact coordinates MUST NOT leave the device.
- Distance based sorting happens on the device from the fuzzy cells. The app MUST NOT answer queries that allow trilateration (for example exact distances to many fake observers).
- The location permission is "approximate" only. Background location is not requested. Presence is published only while the user has it switched on and the app can refresh it.
- Users can hide from specific people (block) and from everyone (no presence at all) without losing the swipe timeline.

## 7. Media

- **Storage:** media files are uploaded to Blossom compatible media servers (content addressed by SHA-256), the event only carries the URL, hash and metadata. Servers MUST be reachable through Tor or I2P. Users can configure their own server.
- **Metadata:** the client MUST strip EXIF and GPS data from photos and videos and re-encode locally before upload. Faces and names in filenames are not the app's concern, location data is.
- **Video encoding on the device:** H.264 or H.265, 720p default, a bitrate that keeps a 60 second clip small enough for Tor (target below about 15 MB, to be measured). A low-quality preview and a poster image are created for fast loading.
- **Loading:** poster first, video on demand and prefetch of the next card only on Wi-Fi or when the data budget allows. Tor throughput is low, so a "data saver" mode with photos only is needed.
- **Moderation signal:** the local model (section 9) classifies incoming media thumbnails for nudity and spam before display and offers blur with tap to show.

## 8. Rules the store and the law require

A dating style app with user generated content has duties that a decentralized design makes harder. These MUST be solved before any Play Store release:

- **Age:** 18+ only. Age confirmation at account creation. No accounts or content of minors.
- **Report and block:** every post, profile and chat has report and block. Blocking and muting work locally by public key. Reports go to the user's own moderation list and optionally to a community moderation relay.
- **Content policy:** in-app terms, no sexual content involving minors, no illegal content. Illegal material MUST NOT be shown or cached. A hash based check against known material is a legal question to clarify before release.
- **Takedown contact:** a contact for legal requests in the store listing.
- **Store listing:** Google Play rules for dating and user generated content apply. Check the current policy before the first upload.
- **No central moderation is possible.** The product can only offer client side filters, block lists and optional shared lists. This limitation MUST be documented in the terms.

## 9. Role of the local AI

- Translation of posts and chats, spam and bot filter, blur for sensitive media, reply suggestions in chats (opt in). Details are in a separate guideline.

## 10. Network impact

- All media and relay traffic runs through Tor or I2P as in `guideline-tor-and-i2p.md`. This makes videos slow. The first version SHOULD ship photos and text first and add video once load times are measured over Tor.

## 11. Staging

| Version | Content |
|---|---|
| 0.0.x | skeleton, network layer (done), key management |
| 0.1 | Nostr client: profile, text and photo posts, follows, relay connection through Tor |
| 0.2 | swipe mode with private interest signal, encrypted chat |
| 0.3 | map mode with timed presence |
| 0.4 | short video (60 seconds), stories |
| 0.5 | local AI: translation and filters |
| 1.0 | moderation tools, age gate, store release |

## 12. Open Questions

1. Is video part of the first usable version, or does it follow after photos work over Tor?
2. Which media servers (Blossom) are used, and does the project run its own reachable through Tor and I2P?
3. Is the default presence precision 6 acceptable, or does the product need finer positions (and accept the privacy cost)?
4. Who moderates? Only client side filters and personal lists, or also a project run moderation relay?
5. Can users choose to be visible in the swipe timeline only to people who follow them (followers only)?
