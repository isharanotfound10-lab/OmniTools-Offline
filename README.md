# OmniTools Offline

An offline-first toolbox project inspired by the idea of an all-in-one utility site. This is an original implementation and does not use 10015.io's source code or branding.

## Current foundation

- Responsive dark/purple interface with a light appearance toggle.
- Search and category filtering.
- Local text utilities: case conversion, word/character counting, duplicate-line removal, natural line sorting, and reversing.
- Local developer utilities: JSON formatting, Base64 conversion, URL encoding/decoding, and HTML escaping.
- HEX to RGB/HSL conversion, secure random password generation, and local image metadata inspection.
- Static website packaging without runtime CDN dependencies.
- Capacitor Android wrapper generation and GitHub Actions artifacts for the APK and website ZIP.

## Build locally

Requires Node.js 20+ and Java 21 for Android builds.

```sh
npm install
npm test
npm run build
npx cap add android
npx cap sync android
cd android
./gradlew assembleDebug
```

The website output is in `dist/`. The Android debug APK is generated under `android/app/build/outputs/apk/debug/`.

## Offline behavior

The implemented tools run in the browser/app and do not upload input. The first Android build needs internet access in GitHub Actions to download build dependencies; the installed app's bundled website does not require an internet connection for its current tools.

## Roadmap and limitations

This is the initial foundation, not yet a complete 10015.io feature-parity remake. More tools must be implemented and individually tested. Advanced PDF, image conversion, barcode, and other functions may require additional local libraries and offline testing. Do not treat a roadmap item as implemented until it is present and tested.