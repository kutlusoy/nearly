# Nearly: Profile Details Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/ui/` (`MyProfile.kt`, `EditProfileScreen.kt`, `MainScreen.kt` profile tab, `PersonDetail.kt`, `ContactScreens.kt`), `nostr/Profile.kt`, `nostr/DiscoveryCard.kt`, `media/PhotoBackend.kt`. The fields are kept on the device and published in the discovery card, the name, text, age and first photo also in kind 0.
- **See also:** [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md), [`guideline-photo-upload.md`](./guideline-photo-upload.md), [`guideline-stay-signed-in.md`](./guideline-stay-signed-in.md), [`guideline-safety-moderation.md`](./guideline-safety-moderation.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. The profile in three places

- **Own profile (tab):** name, age, text, photo grid, the numbers (posts, followers, following), the button `Edit profile`, the settings. The numbers for posts and followers stay 0 until real data exists, `following` counts the people the user follows.
- **Profile view of others (swipe, map, feed, follows):** the same fields as below, a photo carousel, the actions of the place it was opened from.
- **Edit screen:** one scrolling form with all fields and a hint of how complete the profile is.

## 2. Fields

| Field | Limit | Public as | Private use |
|---|---|---|---|
| User name | from registration, cannot be changed (it is part of the account) | kind 0 `name`, card | label in the app |
| Age | 18 to 99, from the date of birth | kind 0 `age`, card | the date of birth stays on the device |
| About | 500 characters | kind 0 `about`, card | |
| Gender | woman, man, non binary | card | filter of others |
| Looking for | serious, open, casual, fun, friends, unsure | card | filter of others |
| Interests | up to 10, 24 characters each, suggestions plus own | card | used to sort later (AI) |
| Languages | from the list of 19 | kind 0 `languages`, card | translation hint |
| Job, school, city | free text, shortened | card | |
| Height | 100 to 250 cm | card | |
| Drinking, smoking, exercise | never, sometimes, often | card | |
| Photos | up to 6 in the card, the first is the profile picture | kind 0 `picture`, card `imeta` | |

Rules:

- **Nothing is mandatory except name and age (MUST).** Every other field can stay empty and is then not published. The completeness hint is advice only.
- **City is free text (MUST NOT be parsed as a place).** It is a label. The place of the card comes only from the location of the phone and is a cell, see [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md). A user who types a different city than the cell shows the label, nothing else changes.
- **Text is cleaned (MUST):** control characters removed, white space collapsed, links are shown as text. About may not contain an address or phone number check, but the edit screen warns once when it sees a phone number or an e-mail address pattern.
- **Publishing.** Every save writes kind 0 and the discovery card. Both are replaceable, the newest wins. The app reads the own events at sign in and keeps newer data from another device instead of overwriting it blindly (same care as with follows).
- **Hidden fields.** Each field has an eye: hidden fields are not written to the card. Gender and looking for are needed for filters, hiding them makes the person invisible to filters that ask for them, the screen says so.

## 3. Photos

- 1 to 6 photos in the card, up to 10 in a post. The first photo is the profile picture and the first card photo.
- Picking, removing and `Make first` are on the edit screen, cropping to 4 to 5 and the order by drag are still to do. Every photo is prepared on the device as in [`guideline-photo-upload.md`](./guideline-photo-upload.md) (EXIF removed, 1600 pixels, BlurHash).
- The first upload asks for the photo permission only through the system photo picker, no storage permission. The camera is optional.
- A profile without a photo is shown with a colour gradient and the first letter. A photo is not required yet, it MAY become a condition for the swipe mode.
- No face check, no check of who is in the photo. The user is told not to use photos of other people, report and block exist for abuse.

## 4. The profile view of others

- Name, age, distance class, the carousel, about, the chips for goal and interests, job and city, languages, the rest in a quiet block.
- Actions by place: swipe (interest, not now, skip), map (same), feed and follows (follow, message), chat header (the profile with the ID, block).
- **The ID is always visible and copyable (MUST),** so two people with the same name can be told apart. The profile of someone who is blocked shows only the unblock button.

## 5. Account area

- Settings show the user name, the age, the short ID, the version and the sign out. `Back up your account` shows the user name, e-mail, five words, `npub` and `nsec` only after a confirmation, hidden by default, copy buttons clear the clipboard after one minute (SHOULD).
- The user stays signed in until the sign out (see [`guideline-stay-signed-in.md`](./guideline-stay-signed-in.md)). The sign out asks once and deletes the sealed login.
- **Delete account (to build).** Deleting an identity on Nostr is not possible. The option publishes empty kind 0 and card events, a deletion request for the posts, removes all local data and signs out. The screen says what remains on relays.

## 6. Tests

To write: field limits and cleaning, the warning for phone numbers, hidden fields leaving the card, publish and read round trip of kind 0 and the card, no overwrite of newer events from another device, the first photo as profile picture, no card without a photo, the delete account flow.

## 7. Checklist

- [x] Fields, limits, the completeness hint, the edit screen, the profile tab
- [x] Profile view with the photo carousel, real people from the cards
- [x] Publish kind 0 (with the first photo as picture) and the card after every change
- [x] Photo picker, upload, remove, make first (crop and drag order are still to do)
- [ ] Hidden fields
- [ ] Read of the own events at sign in
- [ ] Delete account

## 8. Open Questions

1. Exact age or a range in the public profile (decision record, open)?
2. Are the lifestyle fields (drinking, smoking, exercise) kept, or too much for a first version?
3. Is a verified photo badge (a pose check on the device) wanted later?
