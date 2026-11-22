# Nearly: Map and Presence Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** not built yet. The Map tab shows a placeholder (`MainScreen.kt`). The network layer is in `network/`, events in `nostr/`.
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) sections 6.2 and 7, [`guideline-decisions.md`](./guideline-decisions.md) row 5, [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`permissionlesstech/bitchat`](https://github.com/permissionlesstech/bitchat), [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md), [NIP-13](https://github.com/nostr-protocol/nips/blob/master/13.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. The exact event kind of the heartbeat MUST be taken from the BitChat source and the current NIP list before implementation, this draft only fixes the rules.

---

## 1. Idea

The map shows people who are active **right now**, nobody else. Being active is a decision of the user for a limited time. The position is coarse, short lived and never written to a place where it stays.

## 2. Becoming active

- A button on the map, `I am active`, offers 15 minutes, 1 hour, 4 hours and until midnight. Opening the app or the map does not switch it on.
- A chip shows the remaining time with a stop button. At the end the heartbeats stop, nothing else is needed.
- Before the first use a screen says in plain words: people nearby see a dot in a cell of about 1 km, relays see the cell, nobody sees a history.
- **Home area (SHOULD).** The user may mark places where the app never sends a heartbeat (home, work). The place is stored as a cell on the device. When the phone is inside, `I am active` shows `not shared here`.
- Presence needs the **approximate** location permission only. No background location. The location is read when the active state starts and then every 5 minutes while the app runs, and the heartbeat follows the new cell.

## 3. The heartbeat

| Part | Value |
|---|---|
| Kind | an ephemeral kind (20000 to 29999) as in BitChat, so relays do not store it |
| Tags | `g` = cell of 6 characters (about 1.2 km by 0.6 km), `expiration` (NIP-40) 3 minutes ahead |
| Content | empty |
| Interval | every 60 seconds, plus a short random delay of up to 10 seconds |

Rules:

- **No exact position (MUST).** Only the cell leaves the device. The dot is drawn at a random point inside the cell. The point is drawn once per active session and stays the same until the session ends, so averaging many heartbeats does not reveal the centre and a new session does not follow an old pattern.
- **Nothing in the content (MUST).** Name and photo come from the profile and the discovery card, which the reader loads when a dot is tapped or visible.
- **Stop is clean (MUST).** When the user stops, the app goes to the background for more than 30 seconds, signs out or loses the permission, the heartbeats stop at once. Others see the dot disappear after the timeout of section 5.
- **Relays.** At most three relays are used for presence, chosen from `relays.txt` by the first characters of the cell as the directory grows. All traffic goes through Tor. A directory with coordinates and `.onion` relays is a later step.

## 4. Reading the map

- **Query.** `{kinds:[heartbeat], #g:[cells], since: now minus 180 s}` with the cells in view (at most 9 cells of 6 characters, at lower zoom the 5 and 4 character parents are used and the result is grouped). The relay learns the coarse area in view. The client SHOULD ask for the coarser prefix and filter on the device.
- **Empty at first.** Heartbeats are not stored, so the map fills within about one minute after opening. The map says `looking around` and does not claim that nobody is near.
- **Validity.** A heartbeat counts when its `created_at` is within 180 seconds in the past and 60 seconds in the future of the phone clock, the signature is valid, the key is not blocked, and the key has a valid discovery card that passes the filter of the user (`DiscoveryFilter.matches` with the stretched limits off).
- **Timeout.** A dot disappears 180 seconds after its last valid heartbeat.
- **Abuse limits (MUST).** At most one accepted heartbeat per key every 30 seconds, at most 50 dots per cell and 300 on the map, with followed people and the best matches first. A relay may require proof of work (NIP-13), the client MAY add a small one to the heartbeat to be accepted.
- **Own dot.** The own dot is shown to the user as the others see it (cell and random point).

## 5. Screen

- MapLibre with OpenStreetMap data. The tile source is an open point (section 7). A downloaded region is the offline fallback.
- Markers are round profile photos with a ring in the brand colour and a fine line when the person is a match. Many dots in one place become a number marker (clustering) that opens a list.
- A tap opens a bottom sheet with the card and the actions of the swipe mode (interest, not now, follow, full profile). Interest from the map is the same signal as in swipe mode.
- Filter and visibility of [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md) apply. A person with `visible` off is not shown even when active.
- The map centres on the user on the first open and on the last viewed place after that.

## 6. Privacy and safety analysis

| Who | Sees | Mitigation |
|---|---|---|
| Other users | a dot in a 1 km cell while the user is active | time limited, explicit, home area, no history |
| A relay | the key, the cell, the time of each heartbeat | Tor hides the address, ephemeral kinds are not stored, at most three relays |
| A tile server | the area in view | through Tor, region download, own tile server later |
| An attacker who samples a cell | who is active in it right now | cell size, random point, filter and card needed to be shown |
| A stalker with many heartbeats of one key | movement between cells | keys are pseudonyms, `home area`, session based random point, advice in the first use screen |

Fake dots cost nothing on Nostr. The card requirement, the rate limits and the caps keep the map usable, they do not prove that a person stands where the dot is.

## 7. Open points

- **Tiles.** The public tile servers of OpenStreetMap have a usage policy that does not fit a large app. Before a release: own tile server, an offline region file, or a provider reached through Tor. The policy MUST be read again at that time.
- **Battery.** One location read every 5 minutes and a heartbeat every minute through Tor is acceptable only while the app is on screen. A background mode is out of scope.
- **Clock.** Phones with a wrong clock are not shown or do not see others. The app SHOULD point to the clock when no dot is ever accepted.

## 8. Tests

To write: cell calculation and neighbours, the random point inside the cell and its stability, heartbeat interval and stop rules with a fake clock, the validity rules, timeout, rate limits and caps with a flood of fake keys, clustering, the home area, and the read path with a fake relay.

## 9. Checklist

- [ ] Read the BitChat source, fix the kind and the cell rules
- [ ] Location permission and cell calculation on the device
- [ ] Heartbeat sender with stop rules
- [ ] Map query, validity rules, timeout, limits
- [ ] MapLibre screen with markers, clustering and the bottom sheet
- [ ] Home area
- [ ] Tile source decided

## 10. Open Questions

1. Which kind number does BitChat use for presence, and do the public relays of `relays.txt` forward ephemeral events at all?
2. Which tile source is used?
3. Is a cell of 6 characters (about 1 km) fine enough to be useful and coarse enough to be safe, or should the default be 5 characters?
