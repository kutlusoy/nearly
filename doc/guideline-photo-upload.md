# Nearly: Photo Upload Guideline

- **Version:** 0.6 (draft)
- **Date:** November 22, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/media/` (`PhotoProcessor.kt`, `PhotoGeometry.kt`, `BlurHash.kt`, `PhotoPublisher.kt`, `PhotoBackend.kt`) and `app/src/main/java/com/kutlusoy/nearly/nostr/` (`ImageSanitizer.kt`, `Blossom.kt`, `PhotoTransfer.kt`), chat side in `chat/ChatService.kt`, tests in the matching `src/test` folders
- **See also:** [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-decisions.md`](./guideline-decisions.md) rows 1, 7, 8, 32 and 33, [`guideline-chat.md`](./guideline-chat.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), Blossom [BUD-02](https://github.com/hzrd149/blossom/blob/master/buds/02.md), [BUD-11](https://github.com/hzrd149/blossom/blob/master/buds/11.md) and [BUD-12](https://github.com/hzrd149/blossom/blob/master/buds/12.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Scope

This guideline covers **public photos**: profile pictures and photo posts. The photo is prepared on the device, uploaded to a Blossom media server over Tor, and the post carries only the address, the type, the SHA-256 and a small placeholder. Public photos are small on purpose (normal resolution, at most 150 KB). High resolution photos are not public photos, they never go to a public server, see section 7.

## 2. Flow

1. The user picks a photo (the system photo picker needs no storage permission). The caller passes a function that opens it.
2. `PhotoProcessor` decodes it at a reduced size, reads the EXIF orientation, turns the pixels upright, scales it to at most 1080 pixels on the long side, and encodes a JPEG of at most 150 KB (it lowers the quality, then the size, until it fits). It also makes a thumbnail (320 pixels) and a BlurHash. The new file has no metadata.
3. `PhotoPublisher.publish` hands the JPEG to `PhotoUploadService`, which checks it again (real type, size, orientation), removes any metadata that is left (`ImageSanitizer`), and uploads it with `BlossomClient`.
4. The result is a `PhotoRef` (URL, type, hash, width, height, alt text, BlurHash) for `PostService.postPhotos` or the profile.

## 3. Android layer

| Class | Purpose |
|---|---|
| `PhotoGeometry` | scaling that keeps proportions and never enlarges, the decoder sample size (so a 50 megapixel photo does not fill the memory), and the mapping of the eight EXIF orientations to a rotation and a mirror |
| `PhotoProcessor` | decode, orientation, scale, flatten, JPEG, thumbnail, BlurHash, and a final guard |
| `BlurHash` | encoder, decoder and validator for the placeholder (a text of about 30 characters) |
| `PhotoPublisher` | upload of a processed photo, adds the BlurHash to the reference |

Rules:

- **Always JPEG (MUST):** the output is JPEG. A PNG with transparency gets a white background. GIF, WebP and HEIC are decoded by Android and leave as JPEG.
- **Orientation (MUST):** the orientation is applied to the pixels and then removed with all other metadata. The result always has the normal orientation. The table of the eight orientations is covered by a test against the EXIF standard.
- **Guard (MUST):** after encoding, the result is checked with `ImageSanitizer`. A file that is not a JPEG, or still carries an orientation, is not used. Metadata that is somehow left is removed.
- **Memory:** the photo is decoded with a sample size, so only about the target size is held in memory. The photo is opened several times (orientation, size, pixels), the caller passes a function and must not read it from a one-shot stream.
- **Placeholder:** the BlurHash of a 32 pixel version goes into the post as `blurhash`. It is checked on every read, an invalid value is dropped.

## 4. Rules for the upload

- **Metadata (MUST):** EXIF (including GPS), XMP, comments and other application segments are removed from JPEG, text and EXIF chunks from PNG. The color profile (ICC) and the basic JFIF header stay. A photo that could not be cleaned is not uploaded.
- **Orientation:** `PhotoUploadService` refuses a photo with an orientation other than normal. The Android layer turns the pixels before.
- **Size (MUST):** a public photo has normal resolution (1080 pixels) and at most 150 KB. `PhotoUploadService` refuses everything above 400 KB, so a high resolution photo cannot reach a public server by mistake. The client limit of 10 MB only guards the transport.
- **Authorization (BUD-11):** the upload carries a signed event of kind 24242 in the `Authorization` header, as base64url without padding. The token has the tag `t=upload`, an `expiration` five minutes ahead, the hash of the file as `x` tag, and a `server` tag with the host name, so a stolen token cannot be used on another server. The header `X-SHA-256` carries the same hash.
- **Trust no server (MUST):** the answer must name the same hash and size as the file that was sent, and the address in it must be `https` (or a darknet address). Otherwise the server is skipped and the next one is tried.
- **Download (MUST):** a loaded photo is compared with its hash and size limit, a changed file is refused.
- **Network (MUST):** every request goes through `RelayRouter`. Over Tor the name of the server is resolved by Tor, the device resolves nothing. A blocked route or an unencrypted address to a clearnet server is refused before any request.
- **Delete (BUD-12).** `BlossomClient.delete` sends `DELETE /<sha256>` to the server of the blob address with a token of kind 24242 (`t` = `delete`, `x` = hash, `expiration`, `server` = that host). Status 200 and 204 give `Deleted`, 404 gives `Gone` (the file is not there, which is the aim), 401, 402, 403 and 429 give `Refused`, everything else and every network or route problem gives `Unreachable`. The call never throws for server trouble. The same route and TLS rules as for the upload apply. The server is the one in the blob address, so a card of another person cannot make the app send a token to a third server.
- **A server may refuse.** BUD-12 is optional. After `Refused` or `Unreachable` the photo is removed from the profile and the card anyway, and the screen says that the server may still hold the file. The sender cannot force a server.
- **Servers:** two to three Blossom servers at the start, the first one that accepts is used. Copying the file to more servers is not done yet.

## 5. Tests

| Test | What it proves |
|---|---|
| `PhotoGeometryTest` | scaling, sample size, and the corners of all eight EXIF orientations against the EXIF table |
| `BlurHashTest` | length and validity, the average and main color of solid colors, left and right halves keep their sides, invalid hashes, a photo post carries only a valid BlurHash |
| `PhotoProcessorTest` | with Robolectric and real bitmaps: a JPEG with EXIF orientation, GPS and a camera maker comes out clean, orientations 2, 3, 6 and 8 give an upright photo (checked by the colors of the halves), a large photo is scaled with a thumbnail and a BlurHash, a transparent PNG gets a white background, a file that is no photo is refused |
| `PhotoBudgetTest` | a large noisy photo ends at 1080 pixels and under 150 KB, a smaller budget shrinks it further and keeps the proportions, a photo that cannot be made small enough is refused |
| `PublicPhotoLimitTest` | a file over 400 KB is refused before any request reaches the server |
| `BlossomDeleteTest` | the delete request with its scoped token, the mapping of every status, route and TLS refusals, the hash check of the input, and the service deleting an uploaded photo |
| `PhotoUploadTest` | metadata removal from synthetic JPEG and PNG, the upload request with its signed scoped token, fallback to the next server, answer checks, route and size rules, download with hash check, the service from file to a post reference |
| `PhotoTransferTest` | the chunks of a photo of 1.2 MB stay below 60 KB per wrap, any order and repeats give the same photo, a missing piece or a wrong hash gives nothing, every kind of broken chunk is dropped, the receipt names its transfer |
| `ChatPhotoTest` | two accounts over a relay in memory: a photo arrives in pieces, the sender sees "Delivered", metadata is removed, a once photo is gone after viewing and the sender keeps no copy, strangers and blocked people are dropped, a file that is no JPEG is thrown away, a failed photo is sent again, at most three open transfers per person, the database keeps photos and pieces |
| `ChatPhotoUiTest` | progress, the once state, the viewer and the screenshot |

The photo tests run on the JVM with Robolectric (native graphics), they do not need a phone. The photos in `PhotoProcessorTest` are made from two color bitmaps, not from a real camera. The BlurHash method gives a slight gradient on solid colors, as the reference method does, so those tests check the average.

Not checked: a real camera photo, HEIC and WebP input, a real Blossom server, an upload over Tor, real relays for the chat photos, and the speed of the processing on a weak phone.

## 6. In the app

- **Picker and upload.** The profile editor offers up to six photos in a grid with the system photo picker (images only, no storage permission). `AppViewModel.addPhoto` runs `PhotoBackend.upload` off the main thread (prepare with `PhotoProcessor`, upload with `PhotoUploadService`). While it runs the add cell shows a progress ring, a failure shows a note and changes nothing.
- **Remove.** `removePhoto` takes the photo out of the profile and the discovery card at once, republishes the card, and then asks the server to delete the file (BUD-12). The note says `removed, also from the server` or, after `Refused` or `Unreachable`, that the server may still hold the file. `Make first` moves a photo to the front, the first photo is the profile picture (`picture` of kind 0, `https` only).
- **Display.** `RemotePhoto` shows the BlurHash first and the photo when it is loaded. `AppViewModel.loadPhoto` keeps 48 pictures in memory, `PhotoCache` keeps the files on the disk by hash (100 MB, least recently used first, a file name can only be a SHA-256) and at most three downloads run at once. Every download is checked against its hash.
- **Servers.** `BlossomServers.DEFAULT` holds three public servers as a starting point. They are not checked yet (see the open questions). The photo calls use the same routing rules as the relays through `NetworkLink.router`, so over Tor nothing goes out directly.
- **Cards.** The discovery card carries the photos as `imeta` tags (up to six, `https` only), the saved profile keeps them as a checked list.

## 7. High resolution photos in the chat

Decision 33: a high resolution photo is never stored on a public Blossom server. It goes only to the other person, inside the encrypted chat over the relays (`PhotoTransfer.kt`, `PhotoAssembler`, `ChatService.sendPhoto`).

- **Preparation.** A second `PhotoProcessor` (2048 pixels on the long side, quality 82, at most 1.2 MB) makes an upright JPEG without metadata. A photo above 1.2 MB is refused.
- **Chunks.** The file is cut into pieces of 20,000 bytes. Every piece is a rumor of kind 24310 with the transfer id, the SHA-256 of the whole photo, the index, the number of pieces, the size, the dimensions and the once flag, base64 coded and wrapped like a message (NIP-17 and NIP-59) for the other person only. A wrap stays below 60 KB, so relays with a 64 KB event limit take it. There is no copy for the sender.
- **Sending.** The pieces go one after the other on a background thread. Each piece waits for the first accepted answer of a relay (three tries), the progress is the number of accepted pieces. A failed photo is sent again from the store of the sender with the same transfer id.
- **Receiving.** Pieces may come in any order and more than once. When all are there, the hash of the whole is compared, the file must be a JPEG, metadata is removed again, and only then the photo is shown. Anything else is thrown away. A person who may not write to me (policy, block) cannot start a transfer, at most three transfers per person are open, and a partial transfer is removed after three days.
- **Delivery receipt.** The receiver sends a wrap of kind 24311 with the transfer id when the photo is complete and checked. The sender then shows "Delivered".
- **View once.** The receiver shows no preview, opens the photo full screen with the screenshot block (`FLAG_SECURE`) and deletes the file when the viewer closes. After the receipt the sender deletes its own copy of a once photo.
- **Storage.** The finished photo and the pieces live in the chat database of the phone (version 4), they are deleted with the conversation.

Limits to know: a photo costs about 60 events over Tor and takes time on slow relays, relays may drop pieces (then the photo shows as failed on the sender and can be sent again), and a once photo cannot stop the other person from photographing the screen with another device.

## 8. Not yet covered

- **Camera** capture in the interface and cropping before the upload.
- **Upload over Tor in practice:** progress, resume and failure handling for slow connections.
- **Server list and fallback by quality**, mirror to several servers.
- **Check which servers offer BUD-12** (`OPTIONS /<sha256>` with `DELETE` in `Allow`) and show a hint when the chosen server does not.
- **Progress by percent and resume** of a slow upload, the number of uploads that waits when the app closes.

## 9. Checklist

- [x] Image check and metadata removal for JPEG and PNG
- [x] Blossom upload with a scoped signed token and answer checks
- [x] Download with hash check
- [x] Upload service from file to a post reference
- [x] Delete on the server (BUD-12), with a scoped token and mapped answers
- [x] Decode, rotate, scale, JPEG, thumbnail, BlurHash on the device (tested with Robolectric)
- [x] Photo picker, upload, remove, make first, display with placeholder and cache (tested with a fake backend and a fake server)
- [x] Public photos small by rule (1080 pixels, 150 KB, refused above 400 KB)
- [x] High resolution photos in the chat only (chunks over the relays, hash check, delivery receipt, view once), tested with relays in memory
- [ ] High resolution photo between two phones over real relays
- [ ] Camera in the interface
- [ ] Test with real camera photos and on a weak phone
- [ ] Test against a real Blossom server over Tor
- [ ] Choose the first two or three servers

## 10. Open Questions

1. Which Blossom servers are used at the start, and do they accept uploads over Tor without payment?
2. The three starting servers of `BlossomServers.DEFAULT` are unchecked: which accept uploads without payment, work through Tor and offer BUD-12?
3. Is 1080 pixels at 150 KB the right size, or should it be even smaller to save Tor bandwidth?
4. Do the public relays accept about 55 KB wraps and enough of them in a row?
