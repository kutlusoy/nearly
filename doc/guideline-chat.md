# Nearly: Chat Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/chat/` (`ChatService.kt`, `ChatStore.kt`, `ChatController.kt`), `network/NetworkLink.kt`, `ui/ChatsScreen.kt`, `ui/ConversationScreen.kt`, tests `ChatServiceTest.kt`, `SqliteChatStoreTest.kt`, `ChatUiTest.kt`
- **See also:** [`guideline-nostr-client.md`](./guideline-nostr-client.md), [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. How a message travels

- Private messages follow NIP-17 with the gift wraps of NIP-59 and the encryption of NIP-44. The relays see a wrap (kind 1059) signed by a one time key, addressed to one public key, with a time that is randomly up to two days in the past. They do not see the sender, the text or the real time.
- A message is wrapped twice: once for the other person and once for the sender. The sender finds the whole history again on a new phone with the same account.
- The state of a sent message follows the answer of the relays about the wrap for the other person. `Sending` until one relay accepts (`Sent`), `Failed` when at least three answers refuse it. A later acceptance turns `Failed` into `Sent`.
- The relay client keeps published events and sends them to every relay that connects later. A message written offline leaves as soon as a relay is reachable.
- A message that is still `Sending` two minutes after the app was closed becomes `Failed` at the next start, because the relay client lost it. The person can send it again, it goes out as a new message.

## 2. Receiving

- The app subscribes to wraps addressed to its key. After the first start the filter begins three days before the last sync, because of the random times of the wraps.
- Every wrap id is remembered. A wrap that arrives twice, from several relays or after a restart, is read once.
- Anything that is not a valid wrap, has a rumor of another kind than 14, is empty or longer than 5000 characters is dropped without an error. The seal author MUST be the author of the rumor (checked in `GiftWrap.unwrap`).
- The chat with a person is identified by the key of the other person. A rumor written by the own key (from another own device) is filed under the first `p` tag.

## 3. Starting a chat

- Until the swipe and the map show real people, a chat starts with the ID (npub) of the other person: typed, pasted, or from a link `nostr:npub1...`. The 64 hex digits are accepted too.
- The own ID is shown in the chat list with Copy and Share.
- The name of the other person comes from its public profile (kind 0). The app publishes the own profile with the user name at every sign in. Until a name is known, the short npub is shown.

## 4. Storage

- One SQLite database per account on the phone (`chat-<first 16 hex of the key>.db`), tables `messages`, `wraps`, `peers`, `meta`. Messages are not encrypted in the database, the app storage is private to the app and excluded from backups.
- Signing out keeps the database. Deleting a conversation is possible in the store (`deleteConversation`), a screen for it is still to do.

## 5. Network

- `NetworkLink` starts Tor when the mode needs it and connects to the relays of `relays.txt` when Tor has finished its bootstrap. The state (`Starting Tor 45 %`, `Connecting`, `Online, 3 of 3 relays`, `Tor is not part of this build`) is shown above the chat list and in the settings.
- Two modes: through Tor (default, relays do not see the IP address) and direct (a choice for tests, relays see the IP address). The settings say this in plain words.
- Without the Tor binary in the build (CI build without helpers) the app offers the direct mode and does nothing in secret.
- Messages arrive while the app is open. There is no background delivery yet. It needs a foreground service or a push service and is a separate decision.

## 6. Limits

- At most 2000 characters per message sent, 5000 accepted.
- No attachments, no photos, no reactions, no read receipts in this version. Private photos between two people follow the photo guideline.

## 7. Checklist

- [x] Messages go both ways in tests with the real wrap and unwrap code and a relay in memory
- [x] History comes back on a new device, duplicates are read once, broken events are ignored
- [ ] Tested on two phones against real relays through Tor and directly
- [ ] Public relays of `relays.txt` checked (reachable, accept kind 1059 and kind 0)
- [ ] Block and report a person
- [ ] Delete a conversation from the screen
- [ ] Notifications and background delivery

## 8. Open Questions

1. Which relays accept gift wraps from unknown keys without payment or proof of work?
2. How does a person without a friend find the first chat partner before the swipe and the map show real people?
3. Do we keep messages from unknown people in a separate request list?
