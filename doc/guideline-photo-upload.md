# Nearly: Photo Upload Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers of Nearly
- **Reference implementation:** `app/src/main/java/com/kutlusoy/nearly/nostr/` (`ImageSanitizer.kt`, `Blossom.kt`), tests in `app/src/test/java/com/kutlusoy/nearly/nostr/PhotoUploadTest.kt`
- **See also:** [`guideline-profile-posts-follows.md`](./guideline-profile-posts-follows.md), [`guideline-decisions.md`](./guideline-decisions.md) rows 1, 7 and 8, [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), Blossom [BUD-02](https://github.com/hzrd149/blossom/blob/master/buds/02.md) and [BUD-11](https://github.com/hzrd149/blossom/blob/master/buds/11.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Scope

This guideline covers **public photos**: profile pictures and photo posts. The photo is cleaned on the device, uploaded to a Blossom media server over Tor, and the post carries only the address, the type and the SHA-256. Private photos (end to end encrypted, with a retention time) are a separate step.

## 2. Flow

1. The Android layer decodes the photo, applies the rotation, scales it, and encodes it again as JPEG or PNG. The new file has no metadata.
2. `PhotoUploadService.upload` checks the file: real type by the first bytes (not by the file name), size, and the EXIF orientation.
3. `ImageSanitizer.strip` removes all metadata that is left. The picture data is not changed.
4. `BlossomClient.upload` sends `PUT /upload` to the first server in the list that accepts the file.
5. The answer is checked, the result is a `PhotoRef` (URL, type, hash, width, height, alt text) for `PostService.postPhotos` or the profile.

## 3. Rules

- **Metadata (MUST):** EXIF (including GPS), XMP, comments and other application segments are removed from JPEG, text and EXIF chunks from PNG. The color profile (ICC) and the basic JFIF header stay. A photo that could not be cleaned is not uploaded.
- **Orientation (MUST):** the orientation lives in the EXIF data. Removing it from a rotated photo would turn the picture. So `PhotoUploadService` refuses a photo with an orientation other than normal. The Android layer MUST rotate the pixels before.
- **Formats:** JPEG and PNG. Other formats (GIF, WebP, HEIC) are converted by the Android layer or refused.
- **Size (MUST):** at most 10 MB per photo. A normal phone photo after scaling is far below that.
- **Authorization (BUD-11):** the upload carries a signed event of kind 24242 in the `Authorization` header, as base64url without padding. The token has the tag `t=upload`, an `expiration` five minutes ahead, the hash of the file as `x` tag, and a `server` tag with the host name, so a stolen token cannot be used on another server. The header `X-SHA-256` carries the same hash.
- **Trust no server (MUST):** the answer must name the same hash and size as the file that was sent, and the address in it must be `https` (or a darknet address). Otherwise the server is skipped and the next one is tried.
- **Download (MUST):** a loaded photo is compared with its hash and size limit, a changed file is refused. Posts from others carry the hash, so the check works for every photo.
- **Network (MUST):** every request goes through `RelayRouter`. Over Tor the name of the server is resolved by Tor, the device resolves nothing. A blocked route or an unencrypted address to a clearnet server is refused before any request. Darknet servers (`.onion`, `.i2p`) may use plain http because the connection is encrypted end to end.
- **Servers:** two to three Blossom servers at the start, the first one that accepts is used. Copying the file to more servers (mirror) is not done yet.

## 4. Tests

`PhotoUploadTest` covers: metadata is removed from JPEG and PNG while the picture data and the color profile stay byte for byte, the orientation is found before it is lost, unknown and damaged files are refused, the upload request (method, path, headers, exact bytes, a valid signed token with the right tags and expiration), fallback to the next server, a server that stored something else or returns an unsafe address, route and size rules before any request, download with hash check, and the service from file to a post reference.

The JPEG and PNG files in the tests are small synthetic files, built to contain EXIF, GPS, XMP and comment segments. They are not real camera photos.

Checked on the JVM against a mock server, not against a real Blossom server and not over Tor.

## 5. Not yet covered

- **Android layer:** picking a photo, decoding with rotation, scaling, encoding, a thumbnail and a blurred placeholder.
- **Upload over Tor in practice:** time and failure handling for slow connections (resume, progress).
- **Server list and fallback by quality**, mirror to several servers.
- **Private photos** with end to end encryption and a retention time. Blossom has no standard expiration, a server with expiring uploads is still to be found (open question 1 of the decision record).
- **Deleting** an uploaded photo (BUD-12).
- **Display:** loading, caching in a place that is cleaned, blurred preview.

## 6. Checklist

- [x] Image check and metadata removal for JPEG and PNG
- [x] Blossom upload with a scoped signed token and answer checks
- [x] Download with hash check
- [x] Upload service from file to a post reference
- [ ] Android layer for decoding, rotation and scaling
- [ ] Test against a real Blossom server over Tor
- [ ] Choose the first two or three servers
- [ ] Private photos

## 7. Open Questions

1. Which Blossom servers are used at the start, and do they accept uploads over Tor without payment?
2. Is 10 MB the right limit, or should the app scale photos to a smaller size by default?
