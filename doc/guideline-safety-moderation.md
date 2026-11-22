# Nearly: Safety and Moderation Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Product, legal and Android developers of Nearly
- **Reference implementation:** `chat/ChatController.kt` and `chat/ChatStore.kt` (message requests, block), `ui/ContactScreens.kt` (block and unblock), tests `MessagePolicyTest.kt` and `BlockTest.kt`. Everything else here is to build.
- **See also:** [`guideline-chat.md`](./guideline-chat.md), [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-feed-stories.md`](./guideline-feed-stories.md), [`guideline-google-play.md`](./guideline-google-play.md), [`guideline-local-ai.md`](./guideline-local-ai.md), [NIP-51](https://github.com/nostr-protocol/nips/blob/master/51.md), [NIP-56](https://github.com/nostr-protocol/nips/blob/master/56.md), [NIP-13](https://github.com/nostr-protocol/nips/blob/master/13.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. NIP numbers MUST be checked against the current texts before implementation. This guideline is a technical plan and no legal advice, the terms and the store text need a review by a lawyer.

---

## 1. Principle

There are no administrators and no moderation relay (decision 9). Safety is made of **filters on the phone of each user**, small default protections, and honest words about what cannot be done. A real case already happened in the first test: a bot sent a link to a new account within minutes. The protections below come from that.

## 2. Layers

| Layer | What it does | State |
|---|---|---|
| Who can write to me | default `Only people I follow`, the rest is a hidden request, an answer to the own message and a match are always shown | built |
| Block | deletes the chat and the follow, drops later messages on arrival, list with unblock in the settings | built, local only |
| Mute list on the relays | the block list as an encrypted NIP-51 list (kind 10000, private items in the encrypted content), so it follows the account to a new phone and other Nostr apps may use it | to build |
| Link warning | links are text and cannot be tapped today. When links become tappable: a dialog shows the full address and the domain, short link domains get an extra warning, no automatic preview or opening | to build |
| Suspicious first message | a first message with a link, an address or a request for money is collapsed with a warning in the request list | to build |
| Report | optional public report, see section 4 | to build |
| Rate limits | at most 100 interests per day and 30 new chats per hour from the own device | to build |
| Local AI | marks spam and sensitive photos on the device, never decides about a person | later, see [`guideline-local-ai.md`](./guideline-local-ai.md) |

## 3. Mute list

- Kind 10000 (replaceable). The blocked keys are private items: a JSON list of `p` tags encrypted to the own key (NIP-44) in the content. The public tags stay empty so the list does not tell others whom the user blocked.
- **Read before write (MUST).** As with the follow list, the app reads the newest list before a change and refuses to overwrite a list it could not read (same rule as `FollowNotLoadedException`). An empty answer of one relay is not proof of an empty list.
- The local `blocked` table stays the source for the screens, the list on the relays is merged into it at sign in (union). Removing a key removes it from both.
- Muted words and muted hashtags (kind 10000 as well) filter the feed and the message requests on the device.

## 4. Report

- A report is a public event (kind 1984, NIP-56) with a `p` tag of the person, an `e` tag for a post, and a type: spam, impersonation, nudity, illegal content, underage, harassment, other. It names the reporter, so the screen says this before it is sent.
- **Block is the default action.** `Block and report` asks for the consent to the public report, it is never combined silently. Without the consent only the block happens.
- A report has no addressee that must act. It helps only clients and lists that read reports. It is no replacement for a takedown channel.
- From a report of own device a person can also be removed from the feed and swipe at once (same as a block).

## 5. Content that is not allowed

- Terms and the store listing say: users must be 18 or older, no illegal content, no harassment, no sexual content with minors, no impersonation, no spam, no offers of paid services. They say plainly that the app has no operator who can delete content from relays.
- The app never shows or caches content it recognises as illegal. A takedown request is answered by the contact of the terms with what the client can do: a local filter list that can be updated with the app, and the hint to ask the relay operator.
- **Underage (MUST).** A card or profile with an age below 18 is not shown. A report of type `underage` is the strongest signal and moves the person into a local deny list.
- The age is a statement of the user and cannot be checked. The store text does not promise it.

## 6. Protection in the chat

- A new contact (not followed, no match) shows a quiet banner once: `Do not send money, codes or private photos to people you have not met.`
- No read receipts, no typing indicator, no online status in version 1. Nobody learns whether a message was seen.
- Private photos (later) expire, cannot be forwarded by the app and are not previewed in the chat.
- **Proof of work for first contact (MAY).** A small proof of work on the outer event (NIP-13) makes mass messages expensive. Relays and the cost on weak phones decide. Open question.

## 7. Privacy basics

- Public: the kind 0 profile, the discovery card, posts and stories, reactions, the presence heartbeat while active. Private: chats (end to end), interest signals, decisions, blocks (encrypted), the date of birth, the exact position, the history of positions (none exists).
- The settings list this in one screen `What others can see`, in plain words, as the product concept demands.
- A way to delete the local data and the account is required (see [`guideline-profile-details.md`](./guideline-profile-details.md)).

## 8. Store requirements

The store rules for dating and user generated content ask for in app report and block, terms, a contact and age limits. The first two exist or are planned here, the others belong in [`guideline-google-play.md`](./guideline-google-play.md). The current policy text MUST be checked before the first upload.

## 9. Tests

To write: the mute list round trip with encryption, the refusal to overwrite an unread list, union at sign in, suspicious first message detection with real spam samples (the link from the first test among them), report consent flow, deny list for underage, rate limits with a fake clock.

## 10. Checklist

- [x] Message requests and the default policy
- [x] Block, unblock, the list in the settings, dropped on arrival
- [ ] Mute list on the relays, muted words
- [ ] Suspicious first message and link dialog
- [ ] Report with consent
- [ ] Rate limits
- [ ] Terms, takedown contact, `What others can see` screen

## 11. Open Questions

1. Does a first contact proof of work cost too much on weak phones, and do the public relays accept it?
2. Who reads the reports, and is a project list of known bots acceptable without becoming a moderation authority?
3. How is a takedown request handled legally when the content lives on third party relays?
