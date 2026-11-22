# Nearly: Local AI Guideline

- **Version:** 0.4 (draft)
- **Date:** November 22, 2026
- **Audience:** Android developers, release engineers and whoever prepares the models
- **Reference implementation:** none yet. The models repository: [`kutlusoy/models`](https://github.com/kutlusoy/models), folder `nearly/`
- **Fork base:** [`microsoft/BitNet`](https://github.com/microsoft/BitNet) as `kutlusoy/bitnet` (`bitnet.cpp`, CPU kernels, models)
- **See also:** [`guideline-product-concept.md`](./guideline-product-concept.md) section 8, [`guideline-decisions.md`](./guideline-decisions.md) rows 23 to 26, [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), model card [`microsoft/bitnet-b1.58-2B-4T`](https://huggingface.co/microsoft/bitnet-b1.58-2B-4T), technical report [arXiv 2504.12285](https://arxiv.org/abs/2504.12285)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional. Sizes in this document are targets or assumptions, not measurements. They MUST be measured before a decision is final.

---

## 1. Principles

- **Small.** Every model is as small as it can be for its task. A task that a small classifier or a small translation model solves does not get a language model.
- **On the device.** All AI work runs on the phone. Nothing from a chat, photo or profile goes to a server for the AI. No telemetry.
- **Two ways to get a model:** inside the build (the base pack) or downloaded from the models repository (all other packs).
- **Optional.** Without any pack the app works fully. Each feature switches on when its pack is present.
- **Languages:** the translation covers the European languages of the product concept, the interface is translated into the same languages.

## 2. Packs

| Pack | Content | Delivery | Target size |
|---|---|---|---|
| Base | language detection, spam and bot classifier, translation for the first pair (English and German) | in the build | 50 MB or less in total |
| Language | translation to and from English for one language | download | 15 to 40 MB per language (assumption) |
| Reply | small language model for reply suggestions and summaries | download | as small as the quality test allows, about 1 GB at most |
| Photo | classifier for sensitive photos | download | a few MB to tens of MB |

- English is the pivot, so one language pack serves all pairs with English. Two languages without a direct pair translate in two steps.
- The base pack is in the build so the first translation works at once and offline. Its size is limited because every MB goes into every APK and every update.
- The reply pack is the only large one. It stays optional and is offered with its size and a Wi-Fi hint.
- The app shows the storage use per pack and lets the user remove packs.

## 3. Where the packs live

In a separate repository, **`kutlusoy/models`**, which holds one folder per app. Nearly uses the folder `nearly/`. Other apps of the same owner can use the repository with their own folder.

- **In the git tree (text files only):** `nearly/models.json` (the manifest), `nearly/models.json.sig` (its signature), `nearly/licenses/`, the shared `scripts/` and the checks.
- **Release assets:** the model files themselves, attached to releases with tags `nearly-v<version>`. Reason: GitHub rejects single files above 100 MB in the git tree, and every binary that is committed stays in the history forever. Release assets avoid both. The limits of GitHub MUST be checked again before the first model release. Git LFS is not used because of its download quota.
- **Manifest:** one entry per pack: id, type, version, task, language, size, SHA-256, download URL, license, license file, minimum RAM, minimum app version. Fields and rules are in the README of the models repository and are checked by `scripts/check_manifest.py`.
- **Signature:** Ed25519 over the exact bytes of `models.json`, made on an offline machine. The private key never goes into GitHub. The public key is built into the app.
- **Replay protection:** the manifest has a `sequence` number that grows with every change. The app refuses a signed manifest with a lower number than the last one it saw.
- **Where the app reads it:** `models.json` and `models.json.sig` from the default branch of the models repository, the packs from the release URLs of the manifest.
- **Inside the build:** the base pack is put into the app assets by the release workflow. The workflow takes the file from the release named in the manifest and checks it against the hash that is pinned in the app repository, so the build is reproducible.
- **Transport:** HTTPS only. Plain HTTP MUST NOT be used. By default downloads go through Tor like the rest of the traffic. A download over a direct connection is possible only when the user chooses it (for example on Wi-Fi for a large pack), because the files are public and do not carry personal data. GitHub redirects release downloads to a CDN host, so the client follows the redirect over HTTPS and checks the final host against a list.
- **Checks (MUST):** signature of the manifest, SHA-256 and size of every file against the manifest, free storage. A file that fails is deleted and never loaded.
- **Install:** download to a temporary file in private app storage, resume with HTTP range requests, verify, then move to the final place in one step. A pack is active only after the check.
- **Update:** the app checks the manifest at most once a day while the user has packs installed, and only through the configured network path.

## 4. Build

- Publishing a pack follows the steps in `nearly/README.md` of the models repository: release with the files, manifest entry with size and hash, check, offline signature, commit.
- The app release workflow fetches the base pack from the models release, checks the pinned hash and puts it into the assets. A changed hash needs a commit in the app repository, so the base pack never changes silently.
- Native code (`bitnet.cpp` or another runtime) is cross built with the NDK in the same pinned container as Tor and i2pd. `bitnet.cpp` documents CPU kernels for ARM and x86 and no Android build, so the Android build is a work item and not a given.
- **Store size:** an app bundle for Google Play has size limits for the base module. A large pack MUST NOT go into the base module. If a pack should be offered through Google Play later, Play asset delivery is an option. The current limits MUST be checked before that.

## 5. Interface in the code

One interface, swappable parts:

- `translate(text, from, to)`, `detectLanguage(text)`
- `suggestReplies(conversation, tone)`, `summarize(text)`
- `classify(text or photo)` with a label and a confidence

Every part reports whether its pack is installed. The UI shows a feature only when the pack is there, or offers to download it.

## 6. Model choice

- **Translation:** small open translation models per language pair, run through a small runtime on the device. The models and their licenses MUST be checked per language. A language without a usable open model stays out of the first set. BitNet b1.58 2B4T is NOT the translation model, see section 6.1.
- **Language model for replies:** candidates are BitNet b1.58 2B4T and the Falcon-E and Falcon3 1.58 bit models of 1 to 3 billion parameters. The decision follows the test in section 7. A multilingual model of similar size may be better for languages other than English.
- **Classifiers:** small text and image classifiers. Embedding models of BitNet are documented for x86 only and are no option on phones for now.

### 6.1 What the model card of BitNet b1.58 2B4T says

Source: the model card on Hugging Face, read on November 22, 2026. The model was published in April 2025, later changes are not checked. Numbers of the card are the numbers of Microsoft, not measurements on phones.

| Topic | Statement of the card |
|---|---|
| Size | "~2 Billion" parameters in the text, the hub metadata shows 0.8B parameters (packed tensors). The card does not give file sizes. The real file size MUST be taken from the GGUF repository. |
| Quantization | native 1.58 bit weights (ternary) and 8 bit activations, trained from scratch, not quantized afterwards |
| Context | 4096 tokens at most |
| Tokenizer | LLaMA 3 tokenizer, vocabulary of 128,256 |
| Training | 4 trillion tokens, then supervised fine tuning on instruction and chat data, then DPO |
| Languages | English. "Limited support for non-English languages and underrepresented domains." Translation is not mentioned. |
| License | MIT for weights and code |
| Memory (non embedding) | 0.4 GB, against 0.8 to 4.8 GB for the compared models |
| Latency, energy | 29 ms per token and 0.028 J per token on CPU, hardware and batch size are not stated |
| Quality | average 54.19 over 16 benchmarks, above four of five compared models and below Qwen2.5 1.5B (55.23). Weaker points: IFEval 53.48, MT-bench 5.85, TriviaQA 33.57. |
| Intended use | "research and development purposes", not recommended for commercial or real world use "without further testing and development" |
| Known risks | biased, inaccurate or harmful output possible, an elevated defect rate on election related questions |

Variants:

| Repository | Use |
|---|---|
| `microsoft/bitnet-b1.58-2B-4T` | packed 1.58 bit weights, for the transformers path |
| `microsoft/bitnet-b1.58-2B-4T-bf16` | master weights for training and fine tuning only |
| `microsoft/bitnet-b1.58-2B-4T-gguf` | GGUF for `bitnet.cpp`, **the one Nearly uses** |

Warning of the card: the speed, latency and energy gains exist only with the dedicated C++ implementation `bitnet.cpp`. With the standard transformers library, even with the required fork, there is no gain and the speed can be worse than a full precision model, on CPU and GPU. Nearly therefore uses `bitnet.cpp` and nothing else.

### 6.2 Consequences for Nearly

- **No translation with BitNet.** The model is English centered. Translation and every non English text stay with the translation models of section 6. This holds until the test of section 7 shows otherwise for a language.
- **Reply suggestions and summaries in English first.** The reply pack can start with English conversations. A German or other message is translated to English by the language pack, the reply is made in English and translated back. The double translation costs quality and time and MUST be part of the test. A multilingual model may replace it per language.
- **Context of 4096 tokens.** The app sends the last messages only and cuts the rest. The prompt with the instruction and the tone fits into this budget.
- **Format of the prompt.** The card shows a system message ("You are a helpful AI assistant.") and a chat template of the tokenizer, but not the template text. The template MUST be read from the GGUF file and tested with `bitnet.cpp` before the interface is written.
- **Safety is ours.** Suggestions are labeled, never sent automatically, filtered for harmful or pushy text and never about elections or other sensitive topics (section 8). The card says the model is a research model, so the quality test is a gate and not a formality.
- **Hosting.** MIT allows a copy of the GGUF file in the models repository as a release asset. The license text goes into `nearly/licenses/` and the manifest entry names the source repository and the hash.
- **Sizes.** The card counts 0.4 GB without the embeddings. The vocabulary of 128,256 tokens adds to that. File size and memory on real phones MUST be measured before the reply pack is offered with a size.
- **Texts around the model.** Claims about 20 or more languages or about phone assistants in blogs and social media posts are not in the model card and are not used. The posts on X from April 2025 repeat the numbers of the paper (less memory, less energy, 2 to 6 times faster on CPUs) and the note "needs bitnet.cpp". They contain no measurement on Android and no translation result.

## 7. Test plan before the choice

On at least three real phones (weak, middle, strong) with the target languages:

| What | How |
|---|---|
| Size | file size on disk and memory while running |
| Speed | time to translate a chat message, tokens per second for replies |
| Battery | drain per 100 translated messages |
| Quality | a fixed set of chat messages per language, rated by native speakers, compared with a larger reference model |
| Pivot | the same messages through translate, reply in English, translate back, against a direct reply of a multilingual model |
| Prompt | the chat template of the GGUF, the 4096 token budget, answers to rude and to romantic messages |
| Safety | the model does not leak or invent personal data, suggestions stay polite and do not push the user |

A pack is offered in a language only if the quality test passes for that language.

## 8. Privacy and safety rules

- All processing on the device, no network call for the AI itself. The only network traffic is the download of packs and the manifest.
- The AI reads a chat only when the user uses a feature in that chat.
- Suggestions are labeled as suggestions and are never sent automatically.
- The AI never decides about people: no automatic block, no ranking of people by looks.
- A pack never contains user data. The signed manifest and the checksums protect against a swapped file, the license list in the app shows what each model is.

## 9. Checklist

- [x] Models repository `kutlusoy/models` with the folder `nearly/`, manifest schema, checks and offline signing scripts
- [x] Model card of BitNet b1.58 2B4T read, facts and consequences in sections 6.1 and 6.2
- [ ] Signature key created offline, backups made, public key built into the app
- [ ] File size, chat template and tokenizer taken from `microsoft/bitnet-b1.58-2B-4T-gguf`
- [ ] First pack published (release, manifest entry, signature)
- [ ] Download client in the app: HTTPS, Tor by default, resume, checksum, signature, sequence check, atomic install
- [ ] Base pack in the app release workflow by a pinned hash
- [ ] `bitnet.cpp` or another runtime cross built for Android
- [ ] Test plan run on three phones
- [ ] Translation models and licenses checked for the first language set
- [ ] Storage screen with removal of packs

## 10. Open Questions

1. Which translation model family and runtime are used?
2. Does BitNet b1.58 2B4T reach the quality bar for English reply suggestions, and which multilingual model is the alternative for German and the other first languages?
3. Is the base pack of 50 MB or less right, or should the app ship smaller and download even the first pair?
4. Who holds the signature key and how is it backed up?
5. Is there an Android build of `bitnet.cpp` in the meantime, or does the fork `kutlusoy/bitnet` have to make one?
