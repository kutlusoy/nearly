# Nearly: Local AI Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers, release engineers and whoever prepares the models
- **Reference implementation:** none yet
- **Fork base:** [`microsoft/BitNet`](https://github.com/microsoft/BitNet) as `kutlusoy/bitnet` (`bitnet.cpp`, CPU kernels, models)
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) section 8, [`guideline-decisions.md`](./guideline-decisions.md) rows 23 to 25, [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Sizes in this document are targets or assumptions, not measurements. They MUST be measured before a decision is final.

---

## 1. Principles

- **Small.** Every model is as small as it can be for its task. A task that a small classifier or a small translation model solves does not get a language model.
- **On the device.** All AI work runs on the phone. Nothing from a chat, photo or profile goes to a server for the AI. No telemetry.
- **Two ways to get a model:** inside the build (the base pack) or downloaded from the repository (all other packs).
- **Optional.** Without any pack the app works fully. Each feature switches on when its pack is present.

## 2. Packs

| Pack | Content | Delivery | Target size |
|---|---|---|---|
| Base | language detection, spam and bot classifier, translation for the first pair (English and German) | in the build | 50 MB or less in total |
| Language | translation to and from English for one language, interface independent | download | 15 to 40 MB per language (assumption) |
| Reply | small language model for reply suggestions and summaries | download | as small as the quality test allows, about 1 GB at most |
| Photo | classifier for sensitive photos | download | a few MB to tens of MB |

- English is the pivot, so one language pack serves all pairs with English. Two languages without a direct pair translate in two steps.
- The base pack is in the build so the first translation works at once and offline. Its size is limited because every MB goes into every APK and every update.
- The reply pack is the only large one. It stays optional and is offered with its size and a Wi-Fi hint.
- The app shows the storage use per pack and lets the user remove packs.

## 3. Where the packs live

- **Inside the build:** the base pack is put into the app assets by the release workflow. The workflow takes the files from the model release by hash (section 4), so the build is reproducible and the files are not committed into the app repository.
- **Download:** from the release assets of a separate models repository (proposal: `kutlusoy/nearly-models`). GitHub releases are used, not files in the git tree, because the git tree has a small file limit and release assets can be much larger (to be verified against the current GitHub limits).
- **Manifest:** every model release contains `models.json` with one entry per pack: id, version, language, task, size, SHA-256, download URL, license, minimum RAM, minimum app version. The manifest is signed.
- **Transport:** HTTPS only. Plain HTTP MUST NOT be used. By default downloads go through Tor like the rest of the traffic. A download over a direct connection is possible only when the user chooses it (for example on Wi-Fi for a large pack), because the files are public and do not carry personal data. GitHub redirects release downloads to a CDN host, so the client must follow the redirect over HTTPS and check the final host against a list.
- **Checks (MUST):** SHA-256 of the file against the signed manifest, signature of the manifest with a public key that is built into the app, size limit, free storage check. A file that fails is deleted and never loaded.
- **Install:** download to a temporary file in private app storage, resume with HTTP range requests, verify, then move to the final place in one step. A pack is active only after the check.
- **Update:** the app checks the manifest at most once a day while the user has packs installed, and only through the configured network path.

## 4. Build

- A models workflow in the models repository builds or converts the model files, writes the manifest, signs it and attaches both to a release.
- The app release workflow fetches the base pack by the hash that is pinned in the app repository, checks it and puts it into the assets. A changed hash needs a commit, so the base pack never changes silently.
- Native code (`bitnet.cpp` or another runtime) is cross built with the NDK in the same pinned container as Tor and i2pd. `bitnet.cpp` documents CPU kernels for ARM and x86 and no Android build, so the Android build is a work item and not a given.
- **Store size:** an app bundle for Google Play has size limits for the base module. A large pack MUST NOT go into the base module. If a pack should be offered through Google Play later, Play asset delivery is an option. The current limits MUST be checked before that.

## 5. Interface in the code

One interface, swappable parts:

- `translate(text, from, to)`, `detectLanguage(text)`
- `suggestReplies(conversation, tone)`, `summarize(text)`
- `classify(text or photo)` with a label and a confidence

Every part reports whether its pack is installed. The UI shows a feature only when the pack is there, or offers to download it.

## 6. Model choice

- **Translation:** small open translation models per language pair, run through a small runtime on the device. The models and their licenses MUST be checked per language. A language without a usable open model stays out of the first set.
- **Language model for replies:** candidates are BitNet b1.58 2B and the Falcon-E and Falcon3 1.58 bit models of 1 to 3 billion parameters. The decision follows a test, see section 7. A multilingual model of similar size may be better for languages other than English.
- **Classifiers:** small text and image classifiers. Embedding models of BitNet are documented for x86 only and are no option on phones for now.

## 7. Test plan before the choice

On at least three real phones (weak, middle, strong) with the target languages:

| What | How |
|---|---|
| Size | file size on disk and memory while running |
| Speed | time to translate a chat message, tokens per second for replies |
| Battery | drain per 100 translated messages |
| Quality | a fixed set of chat messages per language, rated by native speakers, compared with a larger reference model |
| Safety | the model does not leak or invent personal data, suggestions stay polite and do not push the user |

A pack is offered in a language only if the quality test passes for that language.

## 8. Privacy and safety rules

- All processing on the device, no network call for the AI itself. The only network traffic is the download of packs and the manifest.
- The AI reads a chat only when the user uses a feature in that chat.
- Suggestions are labeled as suggestions and are never sent automatically.
- The AI never decides about people: no automatic block, no ranking of people by looks.
- A pack never contains user data. The signed manifest and the checksums protect against a swapped file, the license list in the app shows what each model is.

## 9. Checklist

- [ ] Models repository and release workflow with a signed manifest
- [ ] Signature key created offline, public key built into the app
- [ ] Download client: HTTPS, Tor by default, resume, checksum, signature, atomic install
- [ ] Base pack in the app release workflow by a pinned hash
- [ ] `bitnet.cpp` or another runtime cross built for Android
- [ ] Test plan run on three phones
- [ ] Translation models and licenses checked for the first language set
- [ ] Storage screen with removal of packs

## 10. Open Questions

1. Is a separate models repository (`kutlusoy/nearly-models`) acceptable, or should the models live in the app repository releases?
2. Which translation model family and runtime are used?
3. Which language model reaches the quality bar for reply suggestions in German and the other first languages?
4. Is the base pack of 50 MB or less right, or should the app ship smaller and download even the first pair?
5. Who holds the signature key and how is it backed up?
