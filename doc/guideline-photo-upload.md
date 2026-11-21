# Nearly: Photo Upload Guideline

- **Version:** 0.2 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/media/` (`PhotoProcessor.kt`, `PhotoGeometry.kt`, `BlurHash.kt`, `PhotoPublisher.kt`) and `app/src/main/java/com/kutlusoy/nearly/nostr/` (`ImageSanitizer.kt`, `Blossom.kt`), tests in the matching `src/test` folders
- **See also:** [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-decisions.md`](./guideline-decisions.md) rows 1, 7 and 8, [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), Blossom [BUD-02](https://github.com/hzrd149/blossom/blob/master/buds/02.md) and [BUD-11](https://github.com/hzrd149/blossom/blob/master/buds/11.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Scope

This guideline covers **public photos**: profile pictures and photo posts. The photo is prepared on the device, uploaded to a Blossom media server over Tor, and the post carries only the address, the type, the SHA-256 and a small placeholder. Private photos (end to end encrypted, with a retention time) are a separate step.

## 2. Flow

1. The user picks a photo (the system photo picker needs no storage permission). The caller passes a function that opens it.
2. `PhotoProcessor` decodes it at a reduced size, reads the EXIF orientation, turns the pixels upright, scales it to at most 1600 pixels on the long side, and encodes a JPEG. It also makes a thumbnail (320 pixels) and a BlurHash. The new file has no metadata.
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
- **Size (MUST):** at most 10 MB per photo. A scaled phone photo is far below that.
- **Authorization (BUD-11):** the upload carries a signed event of kind 24242 in the `Authorization` header, as base64url without padding. The token has the tag `t=upload`, an `expiration` five minutes ahead, the hash of the file as `x` tag, and a `server` tag with the host name, so a stolen token cannot be used on another server. The header `X-SHA-256` carries the same hash.
- **Trust no server (MUST):** the answer must name the same hash and size as the file that was sent, and the address in it must be `https` (or a darknet address). Otherwise the server is skipped and the next one is tried.
- **Download (MUST):** a loaded photo is compared with its hash and size limit, a changed file is refused.
- **Network (MUST):** every request goes through `RelayRouter`. Over Tor the name of the server is resolved by Tor, the device resolves nothing. A blocked route or an unencrypted address to a clearnet server is refused before any request.
- **Servers:** two to three Blossom servers at the start, the first one that accepts is used. Copying the file to more servers is not done yet.

## 5. Tests

| Test | What it proves |
|---|---|
| `PhotoGeometryTest` | scaling, sample size, and the corners of all eight EXIF orientations against the EXIF table |
| `BlurHashTest` | length and validity, the average and main color of solid colors, left and right halves keep their sides, invalid hashes, a photo post carries only a valid BlurHash |
| `PhotoProcessorTest` | with Robolectric and real bitmaps: a JPEG with EXIF orientation, GPS and a camera maker comes out clean, orientations 2, 3, 6 and 8 give an upright photo (checked by the colors of the halves), a large photo is scaled with a thumbnail and a BlurHash, a transparent PNG gets a white background, a file that is no photo is refused |
| `PhotoUploadTest` | metadata removal from synthetic JPEG and PNG, the upload request with its signed scoped token, fallback to the next server, answer checks, route and size rules, download with hash check, the service from file to a post reference |

The photo tests run on the JVM with Robolectric (native graphics), they do not need a phone. The photos in `PhotoProcessorTest` are made from two color bitmaps, not from a real camera. The BlurHash method gives a slight gradient on solid colors, as the reference method does, so those tests check the average.

Not checked: a real camera photo, HEIC and WebP input, a real Blossom server, an upload over Tor, and the speed of the processing on a weak phone.

## 6. Not yet covered

- **Picking and capturing:** the system photo picker and the camera in the interface.
- **Upload over Tor in practice:** progress, resume and failure handling for slow connections.
- **Server list and fallback by quality**, mirror to several servers.
- **Private photos** with end to end encryption and a retention time. Blossom has no standard expiration, a server with expiring uploads is still to be found (open question 1 of the decision record).
- **Deleting** an uploaded photo (BUD-12).
- **Display:** loading, a cache that is cleaned, the BlurHash as placeholder while loading.

## 7. Checklist

- [x] Image check and metadata removal for JPEG and PNG
- [x] Blossom upload with a scoped signed token and answer checks
- [x] Download with hash check
- [x] Upload service from file to a post reference
- [x] Decode, rotate, scale, JPEG, thumbnail, BlurHash on the device (tested with Robolectric)
- [ ] Photo picker and camera in the interface
- [ ] Test with real camera photos and on a weak phone
- [ ] Test against a real Blossom server over Tor
- [ ] Choose the first two or three servers
- [ ] Private photos

## 8. Open Questions

1. Which Blossom servers are used at the start, and do they accept uploads over Tor without payment?
2. Is 1600 pixels and JPEG quality 85 the right default, or should the app scale smaller to save Tor bandwidth?
