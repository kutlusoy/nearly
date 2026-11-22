# Nearly: Stay Signed In and Account Backup Guideline

- **Version:** 0.1 (draft)
- **Date:** November 22, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/SavedLogin.kt`, `ui/AppViewModel.kt`, `ui/BackupScreen.kt`, tests `SavedLoginTest.kt` and `StaySignedInTest.kt`
- **See also:** [`guideline-nostr-keys.md`](./guideline-nostr-keys.md), [`guideline-decisions.md`](./guideline-decisions.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Behavior

- After a sign up or a sign in the user MUST stay signed in until the sign out. A restart of the app or of the phone opens the account again without any entry.
- The sign out MUST delete the saved login and lock the session in memory.
- Without a usable Android Keystore the app MUST NOT save anything. The user then signs in again after a restart.

## 2. What is saved

One file, `session.bin` in the no backup directory of the app, holds the sealed bytes of: username, e-mail, the five recovery words, the secret key (hex) and the age. The file is sealed with AES-256-GCM. The key lives in the Android Keystore (alias `nearly-saved-login`), is hardware backed where the phone has it, and cannot be exported. `allowBackup` is off, so the file does not leave the phone with a cloud or device backup. A file that does not open (other key, changed byte) is treated as not saved.

The words are kept so that the settings can show the complete backup later. The secret key alone would open the account, the words add nothing for an attacker who already has the file and the Keystore, but they are needed for the person to sign in on another phone.

## 3. Backup screen

The settings lead to "Back up your account". The screen shows:

| Entry | Hidden until "Show secrets" | Note |
|---|---|---|
| Username | no | needed to sign in again |
| E-mail | no | needed to sign in again |
| Five recovery words | yes | needed to sign in again, empty note if this phone does not hold them |
| Public ID (npub) | no | safe to share |
| Secret key (nsec) | yes | never share, opens the account in other Nostr apps |

- The screen MUST show the warning that anyone who has the details can take over the account and that nobody can reset them.
- Secrets MUST stay hidden until the person asks. Copying a secret marks the clipboard entry as sensitive (`android.content.extra.IS_SENSITIVE`) so the system hides the preview.
- The values are read when the screen opens and kept only by that screen, not in the app state.

## 4. Open points

- [ ] Ask for the device lock (BiometricPrompt) before the secrets are shown.
- [ ] Offer to export the backup as a printable page or an encrypted file.

## 5. Checklist

- [ ] Sign up, close the app, open it: the account is open.
- [ ] Sign out, open the app: the welcome screen.
- [ ] Settings, Back up your account, Show secrets: all five entries are right.
- [ ] Sign in on a second phone with username, e-mail and the five words: the same npub.
