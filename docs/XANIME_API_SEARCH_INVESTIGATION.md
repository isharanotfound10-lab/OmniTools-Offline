# XAnime API Search Investigation

## Safety boundary

This investigation is isolated on `feature/xanime-api-search`, based on `xanime-app`.
It does not modify `xanime-app` or `main`. The current HTML/Jsoup search remains the known-good behavior.

## Current implementation

The current search path in `MainActivity.kt` requests the site's search URL (`/?s=...`, with `paged=...` for pagination), parses the returned HTML with `SiteParser.parseSearch`, then fetches each result page to collect episode-related details and thumbnails. The result model currently carries title, URL, thumbnail and duration.

## What could be verified

- The site's search form submits a GET request to the site root with the `s` query parameter.
- The site is identified as WordPress by the available site scan.
- WordPress documents a standard REST search route at `/wp-json/wp/v2/search`. Its documented search-result schema guarantees only `id`, `title`, `url`, `type` and `subtype`; it does not promise the app's thumbnail, duration or episode-count fields.
- Attempts to inspect the site's REST index and test the standard REST search route were inaccessible from the available network fetch path. Therefore, the existence and response schema of a site-specific endpoint are **not confirmed**.

## Decision

Do not replace the existing HTML parser with a guessed endpoint. A blind API-only switch could remove thumbnails, durations, episode counts or pagination, and could make search return no results if the route is unavailable.

## Safe implementation plan once an endpoint is verified

1. Capture a real successful JSON response for a known search term and at least two pages.
2. Confirm stable fields for title, canonical result URL, thumbnail, duration and pagination. Confirm whether episode counts are in the search response or require detail requests.
3. Add an API parser and unit tests using the captured response shape.
4. Make API search first, but fall back to the current HTML/Jsoup path on HTTP errors, invalid JSON, missing required card fields or empty results.
5. Keep the existing card UI, ordering, pagination, detail extraction and original URLs unchanged.
6. Run unit tests and the Android debug build before considering the change ready to merge.

## Current status

Investigation checkpoint only. No runtime search behavior has been changed in this branch yet. This draft pull request is intentionally not merge-ready; the existing `xanime-app` branch remains the working version.
