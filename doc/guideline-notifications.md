# Nearly: Notifications and Background Receiving Guideline

- **Version:** 0.1 (draft)
- **Date:** November 24, 2026
- **Audience:** Product, legal and Android developers of Nearly
- **Reference implementation:** `notify/` (`Notifier.kt`, `AndroidNotifier.kt`, `ReceiveService.kt`, `BootReceiver.kt`), `AppCore.kt`, `MainActivity.kt`, `chat/ChatController.kt` (`Notice`), `ui/NotificationsScreen.kt`, `ui/AppViewModel.kt` (`notice`, `appVisible`), tests `NotifyTest.kt` and `NotifyFlowTest.kt`.
- **See also:** [`guideline-chat.md`](./guideline-chat.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md), [`guideline-map-presence.md`](./guideline-map-presence.md), [`guideline-google-play.md`](./guideline-google-play.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Decisions of the product owner

- **Receiving in the background without Google services.** No push service, no server that learns who writes to whom.
- **The notification shows the name and the text on the display.** Whoever does not want that switches it off.
- **Everything can be switched off:** notifications, the text, the background receiving.

## 2. Background receiving

- A **foreground service** (`ReceiveService`, type `remoteMessaging`) keeps the process alive. The relay connections and the chat run inside the one app state of the process (`AppCore`), which the activity, the service and the boot receiver share. Closing the screen does not close the chat.
- The system demands a **permanent notification** for such a service. It is on the quietest channel (`receiving`, importance minimum), says `Nearly is receiving messages` and opens the app. It can be turned off in the settings.
- The service starts after the sign in when the setting is on, and again after a restart of the phone (`BootReceiver`, the saved login opens the account). It stops at the sign out, when the account is deleted and when the setting is turned off. `START_STICKY` restarts it after the system killed the process.
- The settings offer a button to the battery settings of the phone. Doze and battery savers may delay messages whatever the app does. The test on real phones has to show how much.
- **Tor.** Receiving through Tor costs more battery than a direct connection. The test has to measure it.
- **Store.** `FOREGROUND_SERVICE_REMOTE_MESSAGING` is meant for apps that carry messages. The declaration in the store console and its text MUST be checked before the first upload.

## 3. What is announced

| Event | Notification |
|---|---|
| A message from a person whose chat is visible (followed, matched or written to) | name and text, or `New message` |
| A photo | name and `Photo`, or `New message` |
| A match | `It's a match` and the name, or `You have a new match` |
| A message request from a stranger | nothing |
| A like | nothing, likes are never shown |
| A friend who becomes active | nothing |

- **Nothing while the app is on screen.** The badge in the app is enough. The notifications are cleared when the app comes to the front.
- One notification per person, a new message replaces the old one. A tap opens the chat of the person (`openFromNotification`).
- **Lock screen.** The notification is `private` and carries a public version without the name and the text. The phone setting decides whether the lock screen shows the content.
- **Text off.** The notification then says only `Nearly` and `New message`. The name and the text do not enter the notification.

## 4. Permission

Android 13 and later ask before an app may show notifications. The app asks once after the sign in, and the settings show a button when the permission is missing. Without it the service still keeps the connection, but nothing is shown.

## 5. Settings

`Notifications` in the settings: `Tell me about new messages`, `Show the name and the text`, `Keep receiving when the app is closed` (each a switch, on by default), the battery settings button, and the permission card when it is missing.

## 6. Tests

Built: the notification with name and text, without them, a photo, a match, clearing one and all, no permission, the channels, the service starting and stopping, the foreground notification, a message outside the app, no text, no notification on screen or when off, a hidden request, the setting of the background receiving through the sign in and sign out, a tap on the notification, the settings screen. To write: the boot receiver on a real phone, the behavior under Doze, the battery cost with Tor.

## 7. Checklist

- [x] Notifications for messages and matches, with the text switchable
- [x] Foreground service without Google services
- [x] Restart after boot and after the process was killed
- [x] Permission, settings, battery button
- [ ] Test on real phones: Doze, battery, Tor
- [ ] Store declaration for the service type

## 8. Open Questions

1. How long do messages wait under Doze and under the battery savers of the large makers?
2. Does the store accept `remoteMessaging` for this use, or is another type needed?
3. Is a UnifiedPush option worth it for people who run their own distributor?
