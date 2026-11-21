# Nearly: Profile, Posts and Follows Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/` (`Profile.kt`, `Follows.kt`, `Posts.kt`, `Services.kt`), tests in `app/src/test/java/com/kutlusoy/nearly/nostr/ProfileFollowsPostsTest.kt`
- **See also:** [`guideline-nostr-client.md`](./guideline-nostr-client.md), [`guideline-decisions.md`](./guideline-decisions.md), [`guideline-product-concept.md`](./guideline-product-concept.md), [NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md), [NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. The event kinds are checked against the NIPs only as far as the author knew them. They MUST be checked against the current NIP texts before a release.

---

## 1. Event types

| Content | Kind | Notes |
|---|---|---|
| Profile | 0 | JSON with `name`, `about`, `picture`, `age`, `languages`. Replaceable: the newest event wins. |
| Follows | 3 | `p` tags with the key and optional relay and petname. Replaceable. |
| Text post | 1 | `t` tags for hashtags, an `e` tag with the marker `reply` for answers |
| Photo post | 20 (NIP-68) | `imeta` tags with `url`, `m`, `x`, `dim`, `alt`, one tag per photo, 1 to 10 photos |
| Reaction | 7 | content `+`, tags `e`, `p`, `k` |

Photo files are uploaded elsewhere (Blossom servers). A post only refers to them by https URL, type and SHA-256. The upload is not built yet.

## 2. Rules

- **Date of birth stays on the device (MUST).** `BirthDate` is used at registration to check that the user is 18 or older and to compute the age. Only the age goes into the profile (`age`). Whether an exact age or a range is published is open.
- **Foreign data is checked (MUST).** A profile or post from a relay is parsed defensively: control characters are removed, name and text are shortened, a picture or photo URL must be `https`, an age outside 18 to 120 is dropped, language codes must be 2 or 3 letters, a photo tag with a wrong URL, type or hash is ignored. Unusable events give `null` and are not shown.
- **Replaceable events:** for profile and follow list the newest event wins, with the lowest id as the tie breaker (`Replaceable.newest`).
- **A follow list is never edited blind (MUST).** A contact list replaces the older list completely. If the read failed and the app started from an empty list, one follow would erase all others. `FollowService` therefore allows a change only after the list was read (`load`), or after the caller confirmed that the account is new (`startEmpty`). Otherwise it throws `FollowNotLoadedException`. An empty answer of the first relay does not count as "new account".
- **Newer than the old list:** a replacement gets a time at least one second after the list it replaces, also when two changes happen in the same second.
- **Unknown tags are kept:** tags of the old list that Nearly does not know are written back unchanged.
- **Feed:** `FeedFilters.following` cuts the followed accounts into groups of 100 authors per filter, because relays limit the number of authors.
- **All incoming events are verified** by `NostrClient` before the services see them.

## 3. Layers

- `NostrTransport` is the small interface the services need (publish, subscribe). `ClientTransport` connects it to `NostrClient`. Tests use a fake transport.
- `ProfileService`: publish and read a profile. `FollowService`: read, follow and unfollow. `PostService`: text and photo posts, likes, the feed of the followed accounts.

## 4. Tests

`ProfileFollowsPostsTest` covers: age from the birth date including leap days and the 18 limit, profile round trip without the date of birth, shortening and checking of foreign profiles, follow list round trip with unknown tags, bad and duplicate entries, the newest event rule, text and photo posts, refusal of bad input, checked photo tags, reactions, feed filter groups, and the services with a fake relay including the rule that an unread follow list is never overwritten.

Checked on the JVM with a fake transport, not against a real relay.

## 5. Not yet covered

- **Photo upload** to Blossom servers over Tor, thumbnails and blurred placeholders.
- **Stories** (photo or text with an expiration tag), highlights.
- **Counts** of followers and posts. They need queries that relays answer differently, the profile page may show only what is loaded.
- **Reading the follow list again** after another device changed it.
- **Local store** of profiles and posts, search, hashtags page.
- **Delete and edit** of posts (NIP-09).

## 6. Checklist

- [x] Profile, follows, text and photo posts, reactions as events
- [x] Defensive parsing of foreign data
- [x] Follow list protected against blind overwrites
- [x] Services over a transport interface, tested with a fake relay
- [ ] Photo upload and the Blossom client
- [ ] Stories
- [ ] Local store
- [ ] Screens: registration, profile, feed

## 7. Open Questions

1. Is the age published as an exact number or as a range?
2. Should `age` stay a custom profile field, or is a separate event kind better so other Nostr apps do not show it?
3. Which Blossom servers are used at the start?
