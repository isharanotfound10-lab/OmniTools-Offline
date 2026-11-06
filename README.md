# ABC Bot Status

Native Kotlin + Jetpack Compose Android dashboard for exactly one repository:
`isharanotfound10-lab/abc`.

## Features
- Live latest GitHub Actions run status, run number, workflow, trigger, branch, commit and update time.
- Automatic refresh every 20 seconds and manual refresh.
- Rerun **all jobs** for the latest completed run using GitHub's workflow-run rerun API.
- Rerun button is hidden while the run is queued, waiting or in progress.
- Job list, job steps and durations.
- Recent run history.
- Recent Actions artifacts.
- Repository metadata.
- AMOLED dark purple interface.
- Fine-grained GitHub token encrypted with an Android Keystore AES-GCM key.

## GitHub token setup
Create a fine-grained personal access token restricted to **only** `isharanotfound10-lab/abc`.
Grant:
- Metadata: Read
- Actions: Read and write

Paste the token into Settings when the app first opens. The token is encrypted locally using Android Keystore; it is not embedded in the APK or sent anywhere except api.github.com.

Token settings: https://github.com/settings/personal-access-tokens/new

## Build
GitHub Actions builds and tests the debug APK on every branch push. Download the `ABC-Bot-Status-debug-apk` artifact from the Actions run.

The app does not modify repository files or interact with any repository other than `isharanotfound10-lab/abc`.
