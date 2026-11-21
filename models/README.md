# Models

This folder describes the AI packs of Nearly. See `doc/guideline-local-ai.md` for the rules.

## Layout

- `models.json`: the manifest, one entry per pack (id, version, language, task, size, SHA-256, URL, license, minimum RAM, minimum app version)
- `models.json.sig`: signature of the manifest, checked by the app with a public key that is built in
- `licenses/`: the license text of every model
- `scripts/`: conversion and packaging scripts

## Where the files are

The model files themselves are **release assets of this repository** (tag `models-<version>`), not files in the git tree. GitHub rejects single files above 100 MB and every committed binary stays in the history for good, so only small text files live in this folder. The manifest points to the release assets by URL and SHA-256.

## Rules

- Every pack has a license entry and a license file.
- A pack that is not in the signed manifest is never loaded by the app.
- The base pack (language detection, spam classifier, translation English and German) is fetched by the app release workflow by its pinned hash and put into the app assets.
