# Nearly: Feed, Posts and Stories Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/ui/` (`FeedScreen.kt`, `Feed.kt`), `nostr/Posts.kt`, `nostr/Services.kt`, `nostr/Follows.kt`, `media/`. The feed shows sample posts, the post services and the photo preparation exist and are tested without a screen.
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) sections 5 and 6.3, [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-photo-upload.md`](./guideline-photo-upload.md), [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md), [NIP-09](https://github.com/nostr-protocol/nips/blob/master/09.md), [NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. NIP numbers MUST be checked against the current texts before implementation.

---

## 1. What the feed is

The feed is the home of the people the user follows, as in Instagram. It ignores the discovery filters of the swipe mode (decision of the product owner). It has three parts from top to bottom:

1. **Stories row**: round photos with a ring for people with a story that is still valid. The own story is first, with a plus.
2. **People near you**: a row of cards of people from the area, with a Follow button, so the feed is not empty for a new user. Shown while the user follows fewer than 20 people, and always reachable by a button.
3. **Posts** of the followed people, newest first, one post after the other in full width (the TikTok like full height card is a view option, not the default).

## 2. People near you

- Source: the discovery cards of [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), with a fixed radius of 25 km and **no** use of the swipe filters.
- Left out: the own key, followed and blocked keys. At most 20 cards, nearest first, then people recently active.
- A tap on a card opens the profile view, the button follows. Follows are local and kind 3 (section 6).

## 3. Posts

| Type | Kind | Rules |
|---|---|---|
| Text post | 1 | at most 500 characters (the app limit, relays may allow more), hashtags as `t` tags |
| Photo post | 20 | 1 to 10 photos, a caption of up to 500 characters, one `imeta` tag per photo with URL, type, hash, size, `alt` and BlurHash |
| Reaction | 7 | content `+` for a like, tags `e`, `p`, `k` |

- **No video (MUST).** No video file is accepted, displayed or uploaded.
- **Composer.** One screen for text and photos. Photos come from the system photo picker, are prepared on the device (EXIF and GPS removed, upright, at most 1600 pixels, thumbnail and BlurHash) and uploaded as described in [`guideline-photo-upload.md`](./guideline-photo-upload.md). The user can add alt text for each photo.
- **State.** A post is shown at once with the state `Sending`, `Sent` or `Failed` with a retry, like chat messages. A draft survives closing the app. A post with an unfinished upload stays a draft.
- **Replies.** Public comments are not part of version 0.1 (less to moderate). Replies to a post go into a private chat with the author. Whether public comments follow is an open question.
- **Location in posts.** A post carries no location tag by default. Nothing in a photo reveals a place, EXIF is removed.
- **Delete.** The author can delete a post with a deletion request (kind 5, NIP-09). Relays may keep the post, the screen says `requested` and not `deleted`. There is no edit, a corrected post replaces the old one after a deletion.

## 4. Stories

- A story is a photo or a short text (up to 140 characters) with an `expiration` tag 24 hours ahead (NIP-40) and the tag `t` = `nearly-story`. Photos follow the same preparation as posts, one photo per story.
- **Valid** means: not past the `expiration`, and not older than 24 hours by `created_at`. Relays that ignore the expiration are covered by the client check (MUST).
- A ring means unseen, grey means seen. Seen stories are stored on the device. A story opens full screen, a tap on the right side goes on, on the left side back, a down swipe closes.
- Highlights that do not expire are a later step and need a separate event (open question).
- Stories of blocked people are never loaded.

## 5. Loading

- **Filter.** `FeedFilters.following` (groups of 100 authors, kinds 1 and 20, `since`, `limit` 50). The first page covers the last 7 days, older posts load on scroll with `until`.
- **Relays.** At least two relays are read and the results merged by event id. Posts are written to every relay the app is connected to.
- **Checks (MUST).** The signature is verified by the client, the content is parsed defensively (`PostCodec`), replies and unknown kinds are not shown in the feed, posts of blocked and muted keys are removed before display, an event from the future by more than 5 minutes is ignored.
- **Store.** Posts and stories are kept in a local table (`posts`: id, author, kind, time, JSON), at most 500 posts, deleted with the account data on sign out. The feed opens from the store at once and the network refreshes it.
- **Photos.** The BlurHash fills the space while loading, then the thumbnail, then the full photo when the post is on screen. At most 3 downloads at the same time over Tor. Every download is compared with its hash. The photo cache is limited to 100 MB, the least recently used files go first. A `data saver` option loads thumbnails only.
- **Likes.** The own like is stored on the device and published. The count is the number of reactions loaded for the post, not a global total, and the screen shows no count above what it knows.

## 6. Follows

- Today follows are local (`followed` table). They become a kind 3 contact list that is read before every change, as the rule in [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md) demands (`FollowService`). On a new phone the list is read from the relays.
- Following someone who is blocked lifts the block, as in the chat.

## 7. Empty and error states

No follows: the row `People near you` is the main content. No posts yet: a hint and the composer button. Offline: the stored posts with a banner. A failed photo: the BlurHash stays with a retry button.

## 8. Tests

To write: parsing of bad posts and stories, the story validity rules with a fake clock, merge and order of the relays, removal of blocked authors, the composer with a failing upload and the retry, the draft after a restart, deletion request, the store limit, `People near you` without the swipe filters.

## 9. Checklist

- [x] Post, reaction and follow services, defensive parsing, feed filters
- [x] Photo preparation and upload service
- [ ] Local store of posts and stories
- [ ] Feed screen on real data, stories row, near you row
- [ ] Composer, photo picker, drafts
- [ ] Likes, deletion request
- [ ] Kind 3 follows on the relays

## 10. Open Questions

1. Public comments in a later version, and how are they moderated?
2. Highlights as a separate event kind?
3. Is a count of likes per post worth the extra queries over Tor?
