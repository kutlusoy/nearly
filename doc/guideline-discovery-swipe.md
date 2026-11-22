# Nearly: Discovery and Swipe Guideline

- **Version:** 0.2 (draft)
- **Date:** November 22, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/` (`DiscoveryCard.kt`, `Geohash.kt`), `discovery/` (`NearbyController.kt`, `AndroidAreaSource.kt`), `ui/` (`SwipeScreen.kt`, `PersonDetail.kt`, `FeedScreen.kt`, `DiscoveryFilter.kt`), tests `DiscoveryCardTest.kt` and `NearbyTest.kt`. The decisions, the interest signal and the match are not built yet.
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) section 6.1, [`guideline-decisions.md`](./guideline-decisions.md), [`guideline-profile-details.md`](./guideline-profile-details.md), [`guideline-map-presence.md`](./guideline-map-presence.md), [`guideline-chat.md`](./guideline-chat.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [NIP-78](https://github.com/nostr-protocol/nips/blob/master/78.md), [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md), [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Event kinds and NIP numbers are written as the author knew them and MUST be checked against the current NIP texts before implementation.

---

## 1. Scope and state

The swipe mode shows one person at a time, as Tinder does. This guideline covers where the people come from, how the deck is built, what a swipe does, how a match comes about without a server, and which settings exist.

Built: the screens, the filter with its rules, the profile view with a photo carousel, the discovery card (publish, hide, read by cell, checks), the approximate place from the phone (permission prompt), the deck from real people of the area, follow and message from the full view, and `People near you` in the feed. A card without a photo shows a colour gradient with the first letter, a photo is not required yet. Photos: the card carries up to six photos and the screens show them (the editor uploads them, see [`guideline-photo-upload.md`](./guideline-photo-upload.md)). Missing: stored decisions, the interest signal and the match, the lifestyle fields in the card.

## 2. The discovery card

A person is findable in swipe mode only with a **discovery card**, an addressable event that the app publishes for its own account. A plain Nostr profile (kind 0) is not enough, because it has no place, no gender and no goal.

| Part | Value |
|---|---|
| Kind | 30078 (NIP-78, application specific data), `d` tag `nearly.card` |
| Tags | `t` = `nearly`, `g` = cell of the home area at three lengths (for example `u33db`, `u33d`, `u33`), `expiration` (NIP-40) 30 days ahead, up to 6 `imeta` tags (url, hash, size, BlurHash, `alt`) |
| Content | JSON: `v` (1), `age`, `gender` (`w`, `m`, `x`), `looking` (one of `serious`, `open`, `casual`, `fun`, `friends`, `unsure`), `langs` (codes), `job`, `city`, `about` (up to 500 characters), `interests` (up to 10), optional `height`, `drinking`, `smoking`, `exercise` |

Rules:

- **Coarse place (MUST).** The cell of a card has five characters (about 5 km by 5 km). A card stays on relays for days, so it is coarser than the presence cell on the map (about 1 km, short lived). The exact position never leaves the device.
- **Distance on the device (MUST).** The distance is computed between the cell centres and shown as a class: `in your area`, up to 5, 10, 25, 50, 100 km, `more than 100 km`. A user who switches off `show distance` hides the class on the own card.
- **Refresh (MUST).** The card is published at registration, after every change of the profile and at least every 7 days while the app is used. The expiration makes stale cards disappear.
- **Hide (MUST).** `visible = off` replaces the card with an empty card of the same `d` tag and the same `g` tags (no content). The `g` tags are needed: a reader that watches the area filters by them and would never see a hide event without them. A deletion request (kind 5, NIP-09) may follow. Relays that ignore replacements keep an old card until it expires, the settings say so. A newer time than the card it replaces is required, also within the same second.
- **Foreign cards are checked (MUST).** Dropped: age outside 18 to 99, no valid photo URL (`https`), more than 6 photos, unknown enum values, text over the limits, control characters, a card whose `t` or `d` tag does not fit. The newest event of a key wins, a hide event counts like a card (lowest id as the tie breaker, as in `Replaceable.newest`). A card without a kind 0 profile is shown with the name from the card, or dropped if there is none.
- **Followers only.** Decision 6 asks for `visible to followers only`. A public card cannot do that. Version 1 offers only on and off. A real followers only mode needs a card encrypted per follower and is an open question.

## 3. Finding people

- **Query.** A subscription `{kinds:[30078], #t:[nearly], #g:[cells], limit:200}` with up to 9 cells (the own cell and its neighbours) at the length that covers the chosen radius: 5 characters up to about 10 km, 4 up to about 60 km, 3 up to the maximum of 160 km. Paging uses `until`.
- **Query privacy.** The relay learns the coarse area (at most the 3 or 4 character block), never the position, and over Tor not the address either.
- **Relays.** At least two relays of `relays.txt`. A card is accepted when it came from one relay and is valid, repeated copies are merged.
- **Cache.** Cards live in a local table with the time of reading, at most 2000, the oldest are removed. The deck is built from the cache first, the network adds to it.

## 4. Building the deck

`DiscoveryFilter.deck` stays as it is: the people that fit come first, nearest first, then the ones just outside the limits if the person allows it (`furtherAway` doubles the distance, `slightlyOutsideAge` adds 5 years). Added on top:

1. Remove the own key, blocked keys, and keys with a decision that is still valid (section 5).
2. Remove cards older than 30 days and people who never showed activity within `recentlyActiveOnly` (7 days, by the time of the card).
3. Take at most 50 cards into the deck at once, load more when 10 are left. Photos of the next 3 cards are loaded in advance, over Tor one request at a time.
4. The feed ignores all of this. The people of the feed are those the user follows, see [`guideline-feed-stories.md`](./guideline-feed-stories.md).

An empty deck shows the reason (no card in the area, filters too narrow, offline) and one button for the matching fix (widen the filters, try again).

## 5. A swipe

| Gesture | Button | Meaning | Comes back |
|---|---|---|---|
| right | heart | interest, private signal (section 6) | never, until it is a match or the user withdraws it |
| left | cross | not now | after 7 days |
| up | arrow | skip, no signal | after 30 days |

- Decisions are stored on the device (`swipes`: key, decision, time) and are part of nothing that is published.
- **Undo (SHOULD).** The last swipe can be taken back for 8 seconds. A signal is sent only after that time, so undo does not need to recall anything.
- **Look before the swipe (MUST).** A tap on the card opens the full view: all photos with the left and right tap zones and the page dots, all fields, the way back by one tap. Report and block are in the menu of the full view.
- Follow is a separate button and not part of the swipe.
- **Rate (SHOULD).** At most 100 interest signals per day. The counter shows `no more today` when reached.

## 6. Interest and match without a server

- **Signal.** An interest is a **rumor of its own kind** (the number is chosen before implementation, outside kind 14, so chat clients ignore it) with `content` `{"v":1,"t":"interest"}` and a `p` tag of the person, sealed and wrapped as in NIP-17 and NIP-59 for the person only, with an `expiration` of 30 days on the wrap. The chat code drops rumors of other kinds today (`ChatService.handle`), so nothing leaks into the chat.
- **Receiving.** A signal is stored as `likes_me` (key, time) and is **never shown**: no list, no counter, no notification. Only a swipe of the user decides. At most 5000 entries are kept, blocked keys and repeats are ignored.
- **Match.** When the user swipes right on a person who is in `likes_me`, or a signal arrives from a person the user swiped right on, both entries meet and the match is set on the device. Both phones decide on their own, no third message is needed. The person who completes the match sees `It's a match` at once, the other phone shows it when its signal arrives and the app is open.
- **After the match.** The chat of the pair is visible whatever `who can write to me` says (`matches` joins follows and own messages in `ChatController.isVisible`), and the profiles stay open for both.
- **Unmatch.** The user removes the match on the own phone, the chat is deleted and the person can be blocked. No message tells the other side.
- **Honesty.** Relays may drop a wrap, then a match comes late or never. The app says nothing about missing signals.

## 7. Settings (as in Tinder)

Built in `DiscoveryFilter`: show me (women, men, non binary people, at least one), looking for (several goals), distance 1 to 160 km, age from and to (18 to 99), `see people a bit further away`, `see people slightly outside my age`, `only recently active`, `show my distance`, `visible`. All are kept on the device and are used only for swipe and map.

## 8. Tests

Built: card encode and parse with bad data, expiry and cell rules, the nine cell cover, the service with a fake relay including hiding and another area, two phones finding each other through one fake relay, the profile fields in the card, hide and block removing a person, the swipe, follow and message with a real person, the permission prompt, and the feed row. To write: deck order and decision rules with a fake clock, undo before the signal leaves, match from both directions, `likes_me` never reaching the screen, rate limit.

## 9. Checklist

- [x] Screens, filter and deck rules
- [x] Card event, publish, hide and refresh at every profile change (the weekly refresh is still to do)
- [x] Query by cells, defensive parsing (the local cache is still to do)
- [x] Real people in swipe and in `People near you`, follow and message
- [ ] Stored decisions, undo, return times
- [ ] Interest signal and match, chat opens
- [ ] Report and block from the full view
- [x] Photos in the card (up to six `imeta` tags, shown in the card, the detail view and the feed row)

## 10. Open Questions

1. Which rumor kind number is used for the interest signal?
2. Followers only visibility: encrypted card per follower, or dropped?
3. Should relays be asked for the number of people in an area to show a hint in the empty state, or is the cache enough?
