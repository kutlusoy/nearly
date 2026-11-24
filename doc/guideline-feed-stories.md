# Nearly: Feed, Posts and Stories Guideline

- **Version:** 0.2 (draft)
- **Date:** November 24, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `feed/` (`FeedService.kt`, `FeedStore.kt`, `FeedController.kt`), `nostr/Posts.kt`, `nostr/Stories.kt`, `ui/` (`FeedScreen.kt`, `FeedPosts.kt`), `media/`, tests `FeedServiceTest.kt`, `FeedFlowTest.kt` and `FeedTest.kt`. The feed shows real posts and stories, the app has no sample data.
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) sections 5 and 6.3, [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-photo-upload.md`](./guideline-photo-upload.md), [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md), [NIP-09](https://github.com/nostr-protocol/nips/blob/master/09.md), [NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. NIP numbers MUST be checked against the current texts before implementation.

---

## 1. What the feed is

Built: the feed tab with the stories row, `People near you`, the posts of the followed people and of the user, the composer for posts and stories, likes, deletion requests, the local store and the checks of foreign events. Not built yet: drafts, alt text, paging of older posts, the data saver, kind 3 follows on the relays, public hashtag pages.

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
- **Composer (built).** One screen for a post or a story with a switch. The text of a post has up to 500 characters, a story up to 140, a post up to 10 photos, a story one. Hashtags in the text become `t` tags. The photo picker is the system picker. Drafts and alt text are to build. One screen for text and photos. Photos come from the system photo picker, are prepared on the device (EXIF and GPS removed, upright, at most 1600 pixels, thumbnail and BlurHash) and uploaded as described in [`guideline-photo-upload.md`](./guideline-photo-upload.md). The user can add alt text for each photo.
- **State (built).** A post is shown at once with the state `Sending`, `Sent` or `Failed` with a retry, like chat messages. The state is stored. A draft that survives closing the app, and a post with an unfinished upload that stays a draft, are to build: today a failed upload keeps the composer open with a note.
- **Replies.** Public comments are not part of version 0.1 (less to moderate). Replies to a post go into a private chat with the author. Whether public comments follow is an open question.
- **Location in posts.** A post carries no location tag by default. Nothing in a photo reveals a place, EXIF is removed.
- **Delete (built).** The author deletes a post with a deletion request (kind 5, NIP-09) after a confirmation. The post leaves the phone at once, the dialog says that relays may keep it. A deletion request of a followed person removes the post here as well, a deletion of another key changes nothing. There is no edit, a corrected post replaces the old one after a deletion.

## 4. Stories

- A story is a photo or a short text (up to 140 characters) with an `expiration` tag 24 hours ahead (NIP-40) and the tag `t` = `nearly-story`. Photos follow the same preparation as posts, one photo per story.
- **Valid** means: not past the `expiration`, and not older than 24 hours by `created_at`. Relays that ignore the expiration are covered by the client check (MUST).
- A ring means unseen, grey means seen. Seen stories are stored on the device. A story opens full screen, a tap on the right side goes on, on the left side back, a down swipe closes.
- **Built.** A text story is a kind 1 event, a photo story a kind 20 event, both with the tag `nearly-story` and the expiration. The feed does not show them as posts. The viewer opens full screen with progress bars, tap right and left, a close button and the back key. Stories of blocked people are dropped. A ring means unseen, the seen state is stored.
- Highlights that do not expire are a later step and need a separate event (open question).
- Stories of blocked people are never loaded.

## 5. Loading

- **Filter.** `FeedFilters.following` (groups of 100 authors, kinds 1 and 20, `since`, `limit` 50). The first page covers the last 7 days, older posts load on scroll with `until`.
- **Relays.** At least two relays are read and the results merged by event id. Posts are written to every relay the app is connected to.
- **Checks (MUST).** The signature is verified by the client, the content is parsed defensively (`PostCodec`), replies and unknown kinds are not shown in the feed, posts of blocked and muted keys are removed before display, an event from the future by more than 5 minutes is ignored.
- **Store (built).** Posts and stories are kept in a local database (`feed-<account>.db`: `posts` with id, author, kind, time, JSON and state, `liked`, `reactions`, `seen`), at most 600 events, deleted with the account data. The feed opens from the store at once and the network refreshes it.
- **Photos.** The BlurHash fills the space while loading, then the thumbnail, then the full photo when the post is on screen. At most 3 downloads at the same time over Tor. Every download is compared with its hash. The photo cache is limited to 100 MB, the least recently used files go first. A `data saver` option loads thumbnails only.
- **Likes (built).** The own like is stored on the device and published as a kind 7 reaction. Taking it back publishes a deletion request for the reaction. The count is shown only on the own posts, from the reactions that were loaded, because the app cannot know the total of a foreign post.

## 6. Follows

- Today follows are local (`followed` table). They become a kind 3 contact list that is read before every change, as the rule in [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md) demands (`FollowService`). On a new phone the list is read from the relays.
- Following someone who is blocked lifts the block, as in the chat.

## 7. Empty and error states

No follows: the row `People near you` is the main content. No posts yet: a hint and the composer button. Offline: the stored posts with a banner. A failed photo: the BlurHash stays with a retry button.

## 8. Tests

Built: order and filter of the posts, blocked and not followed people, the state of a post and the retry, photo posts and replies, a like and taking it back, deletion by the author and by another key, stories with the validity rules and the seen state, events from the future and with bad signatures, live posts, two phones with a follower (post, story, like, deletion), a failed upload, the screens (empty feed, like, delete, retry, story viewer, composer limits). To write: the draft after a restart, the store limit, merge of several relays, `People near you` without the swipe filters on real cards.

## 9. Checklist

- [x] Post, reaction and follow services, defensive parsing, feed filters
- [x] Photo preparation and upload service
- [x] Local store of posts and stories
- [x] Feed screen on real data, stories row, near you row
- [x] Composer, photo picker
- [ ] Drafts, alt text
- [x] Likes, deletion request
- [ ] Kind 3 follows on the relays
- [ ] Older posts on scroll, data saver

## 10. Open Questions

1. Public comments in a later version, and how are they moderated?
2. Highlights as a separate event kind?
3. Is a count of likes per post worth the extra queries over Tor?
