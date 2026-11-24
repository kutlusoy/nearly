# Nearly: Map and Presence Guideline

- **Version:** 0.2 (draft)
- **Date:** November 24, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `nostr/Presence.kt`, `presence/` (`PresenceService.kt`, `PresenceController.kt`, `PresenceSettings.kt`), `nostr/Geohash.kt` (`grid`), `ui/MapScreen.kt`, `ui/PrivacyScreen.kt`, `chat/ChatService.kt` (`onPresence`), tests `PresenceServiceTest.kt`, `MapUiTest.kt` and `PresenceFlowTest.kt`.
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) sections 6.2 and 7, [`guideline-decisions.md`](./guideline-decisions.md) row 5, [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md), [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Decisions of the product owner

- **Only friends see the user.** Friends are people the user follows and who follow the user back. Nobody else sees that the user is active or where.
- **A field of about 1 km** is the default. The user can choose about 5 km instead.
- **A grid without a map picture.** No tile server, no map library, so nobody learns which area the user looks at.
- **Everything is a setting** in `Privacy` (section 6): who sees the user (friends or nobody), how exact the field is, and whether the user wants to see the friends on the grid.

## 2. Idea

The map shows the friends who are active **right now**, nobody else. Being active is a decision of the user for a limited time. The place is a coarse field, short lived, never written to a place where it stays, and sent only to friends.

## 3. Friends

Following is a local list today, so friendship needs a handshake that carries no place:

- When the user follows a person, the app sends a **hello** to that person: a private message (rumor kind 24314 inside a gift wrap, NIP-59) with the content `{"v":1,"t":"hello"}`.
- A hello from a person the user does not follow is remembered as `incoming`. When the user follows the person later, they are friends.
- A hello from a person the user follows makes them friends at once, and the app answers with one hello (once per session) so the other side can confirm.
- **The place is sent only to confirmed friends (MUST).** A person who is followed but did not say hello never receives a place, so a one sided follow cannot be used to watch someone.
- Unfollowing ends the friendship on this phone at once: no more heartbeats go out and the dot disappears. A friend who still follows the user keeps sending, the user ignores it.
- Friends and incoming hellos are kept on the device per account. At most 500 incoming hellos, 100 new hellos per session, 30 recipients per heartbeat.

## 4. Becoming active

- The map tab offers `15 min`, `1 hour`, `4 hours` and `until midnight`. Opening the app or the tab does not switch it on.
- A chip shows the remaining time with a stop button. At the end the heartbeats stop, nothing else is needed.
- **First use (MUST).** A screen says in plain words: friends see a field on a grid while the user is active, the field is sent in private messages that disappear after three minutes, nothing is kept, there is no history and no map picture. It names the size of the field and points to the privacy settings. Nothing is shared before `Understood`.
- **Audience `nobody`.** The tab says so and offers the privacy settings. `start` is refused by the service as well.
- Presence needs the **approximate** location permission only, asked on the tab. No background location. The place is read when the active state starts and every 5 minutes while the app is on screen.
- **Stop is clean (MUST).** When the user stops, the time ends, the audience is set to nobody, the user signs out, or the app has been in the background for 30 seconds, the heartbeats stop at once. The dot of the user disappears at the friends after the timeout of section 5.
- **Home area (MAY, not built).** Places where the app never sends a heartbeat (home, work).

## 5. The heartbeat and reading it

| Part | Value |
|---|---|
| Message | rumor kind 24314 with `{"v":1,"t":"here","g":"<cell>"}`, sealed and wrapped for one friend (NIP-59), sent to each friend separately |
| Cell | geohash of 6 characters (about 1.2 km by 0.6 km) or 5 characters (about 5 km), by the setting |
| Outer wrap | `expiration` (NIP-40) 3 minutes ahead, random past time of the seal and the wrap as in every gift wrap |
| Interval | every 60 seconds plus a random delay of up to 10 seconds |

- **Only the cell leaves the phone (MUST).** Never coordinates. A cell longer than 6 characters is refused when read.
- **Private by construction.** A relay sees a wrap for one key from a random key. It sees neither the sender, nor the cell, nor that it is a presence. Nothing is public, so there is no directory to sample.
- **Not kept (MUST).** The chat service does not write the wrap id of a heartbeat to the database, only to a list in memory (2000 entries). The wraps expire after three minutes on relays that know the expiration.
- **Validity.** A heartbeat counts when the sender is a confirmed friend and still followed, is not blocked, the user wants to see friends, the time in the message is at most 180 seconds old and at most 60 seconds ahead, and the cell has 5 or 6 characters.
- **Timeout.** A dot disappears 180 seconds after its last accepted heartbeat.
- **Abuse limits.** At most one accepted heartbeat per friend every 30 seconds, at most 300 dots.
- **Empty at first.** The grid fills within about a minute after a friend becomes active. The screen says `Looking around`, it never claims that nobody is near.

## 6. Screens

- **Grid.** 7 by 7 fields around the own field, north on top. The own field is marked, a field with a friend shows the initial or the number of friends. Under the grid a list shows each active friend with the distance (`In your field` or about N km) and the time. A tap opens the profile of the friend. There is no map picture and no zoom.
- **Privacy (settings, `Privacy`).** `Who sees that I am active`: friends or nobody. `How exact the field is`: about 1 km or about 5 km. `See my friends on the grid`: on or off. `What is public`: the profile, the discovery card with a field of about 5 km while it is visible, posts and stories. Private: the presence, chats, likes, blocks.

## 7. Privacy and safety analysis

| Who | Sees | Mitigation |
|---|---|---|
| A friend | a field of 1 or 5 km while the user is active | the user chooses the friends by following, the time is limited, explicit start, size setting, nobody setting |
| A person who follows the user but is not followed back | nothing | the place goes only to confirmed friends |
| A relay | a wrap for one key, its size and time | encrypted, random sender key, expires after three minutes, Tor hides the address |
| A map or tile server | nothing | no map picture |
| A stalker with a friend account | the movement between fields of one friend | the user decides who is a friend, unfollowing stops it at once, field of 5 km, advice in the first use screen |

A friend sees the field of the user and could also see it change over time. Whoever follows someone back should know that. The first use screen says it.

## 8. Open points

- **Home area.** Not built.
- **Battery.** One location read every 5 minutes and up to 30 wraps every minute through Tor is acceptable only while the app is on screen. A background mode is out of scope.
- **Relays.** A relay that ignores the expiration would replay old heartbeats. They are dropped after unwrapping by the 180 second rule, which costs a decryption each.
- **Clock.** A phone with a wrong clock sees nothing. The app SHOULD point to the clock when no dot is ever accepted.
- **Kind 3.** When follows live on the relays, the friend check can use the follow list of the other person instead of the hello.

## 9. Tests

Built: the handshake (one sided, both sides, unfollow), a friend on the grid of the other, nothing leaves without a friend, the nobody setting, the size setting, the wrap is not stored and expires, timeout and the rate limit, the end of the active time, strangers, blocked people and old heartbeats, the checks of the message, the grid cells, two phones through the view model, the screens (first use, durations, grid, friend list, nobody, permission, privacy settings, time until midnight). To write: the stop after 30 seconds in the background, the cap of 30 recipients and 300 dots, an expired wrap that a relay replays.

## 10. Checklist

- [x] Friend handshake without a place
- [x] Location permission and the cell of 5 or 6 characters
- [x] Heartbeat sender with the stop rules
- [x] Reading, validity rules, timeout, limits
- [x] Grid screen without a map picture
- [x] Privacy settings
- [ ] Home area
- [ ] Friends from the kind 3 list on the relays
