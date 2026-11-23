# Nearly: Profile Details Guideline

- **Version:** 0.2 (draft)
- **Date:** November 23, 2026
- **Audience:** Product, design and Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/ui/` (`MyProfile.kt`, `ProfileExtras.kt`, `EditProfileScreen.kt`, `MainScreen.kt` profile tab, `PersonDetail.kt`, `ContactScreens.kt`), `nostr/Profile.kt`, `nostr/DiscoveryCard.kt`, `media/PhotoBackend.kt`. The fields are kept on the device and published in the discovery card, the name, text, age and first photo also in kind 0.
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
| Pronouns | 20 characters | card | |
| Orientation | straight, gay, lesbian, bisexual, asexual, pansexual, other | card | |
| Family plans | want, do not want, have, not sure | card | |
| Zodiac sign | the twelve signs | card | |
| Diet | eats everything, vegetarian, vegan, pescatarian, other | card | |
| School, pets | 60 and 40 characters | card | |
| Link | 80 characters of letters, digits and `@._:/#?=&%~+-`, shown as text, never tappable | card | |
| Prompts | up to 3 answers (140 characters) to a fixed list of 10 questions | card | |
| Photos | up to 6 in the card, the first is the profile picture | kind 0 `picture`, card `imeta` | |

Rules:

- **Nothing is mandatory except name and age (MUST).** Every other field can stay empty and is then not published. The completeness hint is advice only.
- **City is free text (MUST NOT be parsed as a place).** It is a label. The place of the card comes only from the location of the phone and is a cell, see [`guideline-discovery-swipe.md`](./guideline-discovery-swipe.md). A user who types a different city than the cell shows the label, nothing else changes.
- **Text is cleaned (MUST):** control characters removed, white space collapsed, links are shown as text. About may not contain an address or phone number check, but the edit screen warns once when it sees a phone number or an e-mail address pattern.
- **Publishing.** Every save writes kind 0 and the discovery card. Both are replaceable, the newest wins.
- **New phone (built).** When the local profile is empty after the sign in, the newest own card is read from the relays and the profile is built from it (`DiscoveryService.loadOwn`, `MyProfile.fromCard`). Fields that were hidden are not in the card and stay empty. A profile that already has content is never overwritten.
- **Hidden fields (built).** The edit screen lists every field except the text and the photos as a chip `Hide ...`. A hidden field is not written to the card (`MyProfile.cardExtras` and the card snapshot). Gender and looking for are needed for filters, hiding them makes the person invisible to filters that ask for them, the screen says so.
- **Foreign cards (MUST).** Every new field is checked on its own: codes must be in the allowed set, numbers in range, text cleaned and shortened, prompts need a known question and a non empty answer, a bad value is dropped and the rest of the card stays.
- **Contact data warning (built).** A phone number or an e-mail address in the text or in an answer shows a warning (`hasContactData`).

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
- **Delete account (built).** Deleting an identity on Nostr is not possible. `Settings, Delete account` (with a confirmation) hides the card, publishes an empty kind 0 (`{}`) and a deletion request (kind 5, NIP-09) for the card, waits four seconds so the relays get the events, removes the chat database and the profile on the phone, and signs out. The screen says that relays may keep copies. Posts do not exist yet, their deletion request is still to do.

## 6. Tests

Built: `CardExtrasTest` (facts travel in the card, bad values dropped one by one, the own card is read again, the deletion request), `ProfileFieldsTest` (every field kept on the phone, hidden fields not in the card, empty answers stay home, the profile built from the card, prompt rules, the link characters, the contact data pattern, the completeness hint), `ProfileCompleteTest` (the edit screen with a screenshot, facts shown to others, hiding changes the card, a new phone gets the profile back, the account deletion). To write: no overwrite of newer data from another device, the first photo as profile picture.

## 7. Checklist

- [x] Fields, limits, the completeness hint, the edit screen, the profile tab
- [x] Profile view with the photo carousel, real people from the cards
- [x] Publish kind 0 (with the first photo as picture) and the card after every change
- [x] Photo picker, upload, remove, make first (crop and drag order are still to do)
- [x] Hidden fields
- [x] Read of the own card at sign in (empty profile only)
- [x] Delete account
- [x] Pronouns, orientation, family plans, zodiac sign, diet, pets, link, prompts
- [ ] Crop, camera and the order of photos by dragging
- [ ] Verified photo badge

## 8. Open Questions

1. Exact age or a range in the public profile (decision record, open)?
2. Orientation is sensitive: is it kept optional and hideable as built, or dropped?
3. Is a verified photo badge (a pose check on the device) wanted later?
