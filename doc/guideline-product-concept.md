# Nearly: Product Concept

- **Version:** 0.4 (draft)
- **Date:** November 21, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** network layer `app/src/main/java/com/kutlusoy/nearly/network/`, account, keys, chat `app/src/main/java/com/kutlusoy/nearly/nostr/`. The product features below are not built yet.
- **See also:** [`guideline-decisions.md`](./guideline-decisions.md) (applies first where it differs), [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-nostr-client.md`](./guideline-nostr-client.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`permissionlesstech/bitchat`](https://github.com/permissionlesstech/bitchat) (geohash presence), [`kutlusoy/bitnet`](https://github.com/kutlusoy/bitnet) (local AI)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Nostr event kinds follow the NIPs and the BitChat documentation as known when this draft was written and MUST be checked against the current texts before implementation.

---

## 1. Idea in one paragraph

Nearly is a map based, decentralized social app for people nearby. It takes the **photo and story formats of Instagram**, the **swipe decision loop of Tinder** and a little of the **vertical full-screen feel of TikTok**, without video. Everyone has a profile page. Everyone appears in the swipe timeline. Only people who are currently active appear on the map. Everything runs on **Nostr** (relays, events, NIPs) through **Tor**, with no server of our own. A **small local AI** on the phone translates, filters and helps with replies, and nothing it sees leaves the device. The app is meant for **all of Europe**, so people can write to each other across languages.

## 2. Interface direction

The look and the gestures follow apps people already know, so nobody needs an explanation.

| Part | Model | What it means for Nearly |
|---|---|---|
| Profile page, photo grid, highlights, stories row | Instagram | round story rings on top of the feed, profile with name, age, short text and a grid of photos, highlights that do not expire |
| Swipe cards, right for interest, left for not now, up for skip | Tinder | one large card per person with a photo carousel, tap on the sides for the next photo, buttons below for those who do not swipe |
| Vertical full-screen feed, swipe up for the next post | TikTok (feel only, no video) | the discovery feed shows one post at a time in full height, large photo or large text, quick reactions on the right edge |
| Bottom navigation | all three | five tabs, a floating button for new posts |

Rules for the design:

- One hand use: the main actions sit in the lower half of the screen.
- Dark and light theme, large touch targets, readable at low contrast settings.
- No technical words for users. The words "key", "relay" or "Tor" appear only in an advanced section. The five passphrase words are explained as "your recovery words".
- Slow networks are normal (Tor): placeholders and blurred previews first, full photos after, clear progress and retry.
- Mockups of the five main screens come before the first interface code.

## 3. Languages

- **Interface:** the app text is translated into the European languages in steps. The code and the repository stay English, the translations live in the Android string resources (`values-xx`). Right-to-left support is not needed for the first set.
- **Translation of content:** chats, posts and profiles can be translated on the device (section 7). The sender's language is detected on the device.
- **First set (proposal):** German, English, French, Spanish, Italian, Portuguese, Dutch, Polish and Turkish. Further languages follow by demand and by the availability of open translation models, for example Czech, Romanian, Swedish, Danish, Finnish, Greek, Hungarian, Bulgarian and Ukrainian. The goal is the official languages of the European Union plus Turkish and other languages spoken in Europe.
- **Language packs:** every language is a separate download (section 7.2), the user installs only the languages needed. English acts as the common pivot, so a pair without a direct model translates through English.
- **Profile language:** every user sets spoken languages in the profile. They are used as a filter and for the translation suggestion.

## 4. Account and registration

- Username (public name in the app), e-mail (entered twice, only used to derive the account), and a password. The app suggests **5 random English words** as the password (see `guideline-nostr-keys.md`). The same three entries restore the account on any device.
- The password cannot be changed, a new password means a new identity. The screen says so before the account is created.
- **Age:** 18 and older. The user enters the date of birth, the profile shows the age as in Tinder. A date of birth below 18 blocks registration. Whether the exact age or a range is published is open.
- Registration steps: username, e-mail twice, recovery words (shown, with a copy and a "I wrote them down" check), date of birth, spoken languages, profile photo, approximate location permission.

## 5. Content

| Type | Limit | Where it lives |
|---|---|---|
| Text post | length by relay policy, the app suggests 500 characters | relays, kind 1 |
| Photo post | up to 10 photos with a caption | event on relays (NIP-68, kind 20), files on Blossom servers |
| Story | one photo or short text, disappears after 24 hours | same kinds with an expiration tag (NIP-40) |
| Reaction | like on a post | kind 7 |
| Private message, text | text | NIP-17 gift wrap with NIP-44, on relays (built) |
| Private photo | one or more photos | end to end encrypted blob with an expiration time on a relay or blob server, key and address in the encrypted chat message |

There is **no video** in Nearly.

### 5.1 Public photos

Public photos go to two to three existing Blossom servers at the start, the event carries URL, hash and metadata. Servers MUST be reachable through Tor. The client MUST strip EXIF and GPS data and re-encode the photo locally before upload, and creates a small blurred placeholder and a thumbnail.

### 5.2 Private photos

- The sender chooses how long the encrypted photo stays on the server, **default 7 days**. The file is encrypted on the device with a random key, uploaded as an opaque blob with an expiration time, and the key and the address travel only inside the encrypted chat message.
- The chat message carries no preview derived from the photo.
- The recipient confirms receipt. If the photo expires without a receipt, the sender gets a notification.
- The recipient can copy the photo, the app cannot prevent that. It blocks screenshots on the private photo screen, offers "view once" and deletes cache files after viewing.
- Needs a blob server with expiring uploads that is reachable through Tor (open question).

## 6. Modes and navigation

Bottom bar: **Swipe**, **Map**, **Feed**, **Chats**, **Profile**, plus a floating button for a new post or story. The user can lock the app to one mode in settings.

### 6.1 Swipe mode (default)

- Cards of people with a photo carousel, name, age, distance class and the latest post.
- Right swipe = interest, left swipe = "not now" (comes back later), up = skip (no signal). Follow is a separate button.
- Everyone who matches the filters appears here, active or not.
- Interest is private. It is sent as an encrypted NIP-17 message and becomes visible only on mutual interest, which opens the chat.
- Users can set "visible to followers only".

### 6.2 Map mode

- MapLibre with OpenStreetMap. **Only people who are active now** appear. "Active" is switched on explicitly for 15 minutes, 1 hour, 4 hours or until midnight. Opening the app does not switch it on.
- A marker shows the profile photo and the fuzzy position. A tap opens the profile page with the swipe actions.

### 6.3 Feed

- A following feed (people the user follows, newest first, with the stories row on top) and a discovery feed (posts nearby, full-height cards, TikTok like).

## 7. Location and presence, as in BitChat

BitChat uses Nostr for location based chat: a position becomes a **geohash** in a `g` tag, **ephemeral events** (kinds 20000 to 29999, not stored by relays) carry messages and a presence heartbeat, and the app uses **relays chosen by proximity**. Nearly takes these building blocks. The BitChat source is the reference and MUST be read before implementation.

- **Presence as a heartbeat (MUST).** Active users send a signed ephemeral event with a `g` tag while the active state is on. Relays do not store it. When the heartbeats stop, the person disappears from the map after a short timeout. No location history exists anywhere.
- **Coarse cells (MUST).** The default cell is about 1 km. Finer levels are optional with a warning. Exact coordinates MUST NOT leave the device. The client adds a random shift inside the cell and sends heartbeats at a fixed, not too frequent interval.
- **Geo relays (SHOULD).** Presence and map queries go to relays near the cell, from a directory shipped with the app (`relays.txt` first, later a directory with coordinates), reached through Tor. The directory SHOULD include `.onion` relays.
- **Query privacy.** A subscription with a `g` filter tells the relay which area the user looks at. The client SHOULD subscribe to a coarser prefix and filter on the device.
- **Permission.** Approximate location only, no background location. Presence is sent only while the user has it on and the app runs.
- **Hide.** Users can block people and use the app without presence. The swipe timeline works without a position, with chosen regions.
- BitChat also has a Bluetooth mesh. Nearly does not plan that for now.

## 8. Local AI

The local AI is part of the concept from the first draft: very small, on the device, no cloud. It helps with translation, short summaries, simple suggestions, a spam and bot filter, and replies in chats.

### 8.1 Tasks

| Task | Where it appears | Notes |
|---|---|---|
| Translation | chats, posts, profiles | translate button, optional automatic translation per chat, all languages of section 3 |
| Language detection | before translation | small classifier on the device |
| Reply suggestions and conversation help | chat input | opt in, 2 or 3 short suggestions, the user edits and sends, nothing is sent automatically, a "flirt" tone can be chosen |
| Short summary | long public posts | one or two lines |
| Spam and bot filter | incoming interest signals, chat requests, public posts | runs before display, uncertain cases are marked, not deleted |
| Sensitive photo blur | photos from others | blur with "tap to show" for classified sensitive photos |
| Suggestion for "not now" or skip | swipe cards | only a hint, never an automatic action |

### 8.2 Design

- **Several small parts instead of one model.** Translation uses dedicated translation models, reply suggestions a small language model, spam and photo checks small classifiers. Every part is swappable behind one interface (`translate`, `suggestReply`, `summarize`, `classify`).
- **Translation across Europe.** Small open translation models exist per language pair. They are small enough (tens of MB per pair) to install per language. English is the pivot. The models and their licenses MUST be checked per language before they ship. A language without a usable open model stays out of the first set.
- **Candidates for the language model.** BitNet b1.58 2B (2.4 billion parameters, 1.58 bit weights) from `kutlusoy/bitnet` and the Falcon-E and Falcon3 1.58 bit models of 1 to 3 billion parameters. The `bitnet.cpp` documentation lists CPU kernels for ARM and x86 and calls the 2B model "chat-ready". It documents no Android build, so an NDK cross build is part of the work. The BitNet embedding models are documented for x86 only and cannot be used on phones yet.
- **Reply suggestions in many languages.** Small models are usually strong in English and weaker in other languages, especially smaller European ones. Reply suggestions therefore start in the languages where a model passes a quality test on real phones, and other languages use the translation chain (suggest in English, translate to the target language). Quality MUST be tested per language before it is offered.
- **Size.** The goal of the first draft: 100 to 300 MB per part, at most about 1 GB in total. A 2B model is likely at the upper end or above, and every language pack adds to it. This is to be measured, not assumed. The app shows the storage use per language and lets the user remove packs.
- **Models are not part of the APK.** They are downloaded after the user agrees, through Tor, with a SHA-256 check against a signed list, and stored in private app storage. The AI features switch on only after the download.
- **Devices.** The AI needs a minimum of RAM (about 4 GB is assumed, to be measured). On weaker phones the features stay off and the rest of the app works.
- **Privacy rules (MUST).** All AI work runs on the device. No chat text, photo or profile data goes to a server for the AI. No telemetry. The AI never reads private chats unless the user uses the feature in that chat.
- **No false authority.** Suggestions are labeled as suggestions. The AI never decides about people (no automatic block, no ranking of people by looks).

### 8.3 Open points

- Which model for reply suggestions, and in which languages does it reach the quality bar?
- Which translation models and which language pairs ship first, and what are their licenses?
- Where do the models live (GitHub releases, a blob server) and who signs the model list?

## 9. Rules the store and the law require

- **Age:** 18 and older, with the date of birth at registration.
- **Report and block:** every post, profile and chat has report and block. Blocking and muting work locally by public key. There are no administrators and no moderation relay, moderation is the user's own filters.
- **Content policy and takedown contact** in the terms and in the store listing. Illegal material MUST NOT be shown or cached. Without an operator who can remove content from relays, takedown requests need a solution before a store release.
- **Store listing:** Google Play rules for dating and user generated content apply, check the current policy before the first upload. The listing text is needed in every supported language.
- **No central moderation is possible.** The terms MUST say so.

## 10. Network impact

All relay traffic and all downloads run through Tor as in `guideline-tor-and-i2p.md`. Photos and language packs are the heaviest content. Placeholders, thumbnails, loading on demand and a data saver mode are needed. Version 1 ships with Tor only.

## 11. Staging

| Version | Content |
|---|---|
| 0.0.x | skeleton, network layer, keys and account, Nostr events and relay client, encrypted chat primitives (done) |
| 0.1 | relay reconnect and list, profile, text and photo posts, follows, screens for registration and sign in, English and German interface |
| 0.2 | swipe mode with private interest signal, chat screens |
| 0.3 | map mode with geohash presence |
| 0.4 | private photos with expiring blobs, stories, more interface languages |
| 0.5 | local AI: translation first (first language set), then reply suggestions and filters |
| 1.0 | age gate, report and block, store release |

## 12. Open Questions

1. Blob server for expiring private photos over Tor, and does the project run its own?
2. Age as an exact number or a range in the profile?
3. Which public relays are reachable through Tor and accept the event kinds we use?
4. Local AI: which models, which languages first, where do the models live?
5. How are takedown and legal contact handled without administrators?
6. Is 64 MiB for the account derivation acceptable on the weakest supported phones?
7. Is the first language set (German, English, French, Spanish, Italian, Portuguese, Dutch, Polish, Turkish) right, and which languages come next?
