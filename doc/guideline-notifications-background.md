# Nearly: Notifications and Background Delivery Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Android developers and product of Nearly
- **Reference implementation:** `chat/ChatService.kt` (`onIncoming`), `chat/ChatController.kt`, `network/NetworkLink.kt`. Nothing runs when the app is closed today: messages arrive only while the app is open.
- **See also:** [`guideline-chat.md`](./guideline-chat.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [`guideline-google-play.md`](./guideline-google-play.md), [UnifiedPush](https://unifiedpush.org)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Android behaviour and Play rules change between versions and MUST be checked against the current documentation before implementation.

---

## 1. The problem

Nostr has no push server. A message is a wrap on a relay, and the phone must ask for it. Tor makes a connection slow and costly. Android stops background work to save the battery. Without a plan, nobody learns about a new message until the app is opened.

## 2. Options

| Option | How it works | Cost | Privacy |
|---|---|---|---|
| A. Foreground service | the app keeps Tor and the relay connections open and shows a permanent quiet notification | battery and memory, a notification that cannot be swiped away | best, nothing leaves the phone |
| B. Periodic check | a worker starts Tor and reads the inbox every 15 to 30 minutes | late messages, the Tor start each time is slow and costs battery | good |
| C. UnifiedPush | a distributor app (for example ntfy) wakes the app, a small server watches the relays and sends an empty wake up | needs the distributor app and a server | the server learns the public key and the times of messages, not the content |
| D. Google push (FCM) | a server sends the wake up through Google | needs a server and Google services | the server and Google learn timing, not suitable for the privacy aim |

## 3. Decision proposal

1. **Version 0.1:** the app works while it is open and says so in the chat screen.
2. **Version 0.2:** option A as an opt in `Stay connected` switch in the settings, with a clear text about battery and the permanent notification. Option B as the fallback when the switch is off, every 30 minutes, only on a network that is not metered unless the user allows it.
3. **Later:** option C as a choice for people who run a distributor and a server of their own or trust one. No server of the project is planned (decision 9 and 14 keep the architecture without operators). Option D is not planned.

The product owner decides whether A is enough for the release. This is an open question.

## 4. Foreground service rules

- A service type that fits message delivery is declared in the manifest and in the Play declaration, and the matching permission is requested. The exact type for Android 14 and later MUST be taken from the current documentation.
- The service starts only after sign in and the opt in, stops at sign out and when the user switches it off. It keeps the `NetworkLink` and the `ChatService` alive and nothing else.
- Reconnect with backoff as in the relay client. After a long loss of network the inbox filter starts three days before the last sync (existing rule in `ChatService.start`).
- The permanent notification is low priority, shows the connection state (`Connected, 3 relays`, `Connecting`) and opens the app. No exact battery optimisation exemption is requested, the Play policy restricts it. A hint on where to find the setting is enough.

## 5. Notification content

| Event | Notification | Default text | Setting |
|---|---|---|---|
| New message from a shown chat | yes | `New message` | show the name and a preview: off by default |
| Message request (hidden) | no | | a quiet count in the app only |
| Interest received | no | | never shown, nobody learns it before a match |
| Match | yes | `You have a new match` | name: off by default |
| Someone you follow posted | no in version 1 | | later, off by default |

- Channels: `Messages` (default importance), `Matches` (default), `Connection` (low, for the service). The user can change each in the system settings.
- **Lock screen (MUST).** The text of a message never shows on the lock screen unless the user turned previews on. The notification uses the public version `New message`.
- **Tap (MUST).** A tap opens the chat of that person. The same person gets one notification that updates, not a stack.
- **Mute (SHOULD).** A chat can be muted. Quiet hours (from, to) are a setting, in that time the channel is silent.
- Notifications come from `ChatService.onIncoming` and the match event, after the policy and block checks, so hidden and blocked messages never make a sound.
- **Permission (MUST).** The notification permission of Android 13 and later is asked after the first chat or match with a short reason, not at the first start.
- The unread badge of the app icon follows the same counts as the Chats tab (`ChatUiState.unread`).

## 6. Tests

To write: no notification for hidden requests, blocked senders and interests, the public text on the lock screen, one notification per chat that updates, tap routing to the chat, quiet hours with a fake clock, the service start and stop with sign in, sign out and the switch, the permission flow on Android 13 and older. A real device test for battery and Doze belongs to the release checks.

## 7. Checklist

- [ ] Notification channels and the permission flow
- [ ] Notifications for chat messages and matches with the privacy defaults
- [ ] Foreground service with the opt in switch
- [ ] Periodic check as fallback
- [ ] Settings: previews, quiet hours, mute
- [ ] Battery and Doze test on real phones
- [ ] UnifiedPush decision

## 8. Open Questions

1. Is the foreground service enough for the release, or is UnifiedPush needed for normal people?
2. Is the permanent notification acceptable to the target users?
3. Which foreground service type does Play accept for this use?
