# VFAB AI — Windows + Android project

VFAB AI is a local-first livestream and VOD clipping workspace. The Windows laptop runs the existing clipping engine and dashboard. The Android application is a companion client that securely opens that same dashboard, so both devices work with one project library and one processing queue.

## Project components

- Windows engine and dashboard: the repository's existing Python clipping pipeline, FFmpeg rendering, project history, editing, captions, live mode and exports.
- Android companion: a Kotlin Android application that accepts the private dashboard URL, stores it in app-private preferences, opens the dashboard in a WebView, and supports media file selection.
- Optional cloud deployment: the Render Blueprint in render.yaml. It is a paid, private, single-owner deployment, not a free public SaaS.
- Android build: the GitHub Actions workflow builds a debug APK and publishes it as a downloadable workflow artifact.

## Windows installation

Requirements: Windows 10/11, Python 3.10 or 3.11 x64, FFmpeg, and an internet connection for the initial Python package installation. An NVIDIA GPU is not required.

1. Download or clone this repository branch.
2. Install FFmpeg if it is not already installed. One Windows package-manager command is: winget install --id Gyan.FFmpeg --exact
3. Run scripts/install_vfab_windows.bat.
4. Run scripts/start_vfab_windows.bat to open the dashboard on the laptop.
5. Use the dashboard to submit a supported stream/VOD URL, monitor processing, edit clips and download exports.

For this Intel CPU / integrated-graphics laptop, install the CPU extras as the script does. Do not install the GPU/CUDA extra. Processing can be substantially slower than on an NVIDIA GPU, especially for long streams and speech recognition.

## Connect the Android app to the laptop

1. Connect the Android phone and Windows laptop to the same trusted Wi-Fi network.
2. On Windows, run scripts/start_vfab_phone.bat and leave its terminal open.
3. Copy the complete network URL printed in the terminal. It includes a private access token.
4. Open VFAB AI on Android, paste that full URL and press Connect.
5. The phone will open the same dashboard and project library running on the laptop.

The first connection exchanges the token for a protected cookie inside the Android WebView. The app stores the original private URL locally so it can reconnect. Treat the URL as a password. Do not post it, forward it, or expose the laptop port to the public internet. If Windows Firewall asks, allow access only on a Private network.

The laptop must remain powered on and the dashboard terminal must remain running while the phone uses local mode. If the laptop's Wi-Fi IP changes, run the phone launcher again and enter the newly printed URL.

## Build and install the Android APK

The source is in mobile/android. A GitHub Actions workflow at .github/workflows/android-apk.yml builds a debug APK whenever Android source changes are pushed to deploy/vfab-cloud-first, and can also be run manually.

After the workflow succeeds:
1. Open the repository's Actions tab.
2. Open the workflow named Build VFAB AI Android APK.
3. Download the VFAB-AI-Android-debug-APK artifact.
4. Extract the ZIP and install app-debug.apk on your Android phone. Android may ask you to allow installation from the browser or file manager used to open the APK.

This is a debug APK for direct testing, not a Play Store release. A signed release APK/AAB needs a private signing key and a separate release workflow.

## Optional cloud connection

The existing render.yaml creates a private single-owner cloud workspace with a generated access token and persistent storage. The selected Render CPU instance and disk are paid resources; check current pricing before creating it. After deployment, copy the private HTTPS token URL from the service logs into the Android app. Cloud mode can be reached when the laptop is off, but cloud CPU processing and storage are billed by the host.

Do not treat this single-token cloud workspace as a multi-user consumer service. It does not yet include separate user accounts, per-user isolation, subscription billing or a public production API.

## What this version does and does not do

Included in this branch:
- Existing stream/VOD clip pipeline and desktop dashboard.
- Laptop-local processing with CPU-capable speech recognition and optional CPU vision.
- Android companion that connects to the same dashboard.
- Upload selection in the Android WebView.
- Android debug-APK build workflow.
- Optional private Render deployment configuration.

Not included or not claimed:
- No Android-side video processing; the laptop or cloud worker does the heavy work.
- No offline clipping when the laptop/cloud worker is unavailable.
- No native push notifications, billing, multi-user accounts or Play Store release signing.
- The dashboard is reused inside the Android app; this is not a separate native redesign of every dashboard screen.
- Live-source support and platform access depend on YouTube/Twitch/Kick availability, cookies and platform rules. Some live-source paths are marked untested in the upstream README.
- No claim that an APK has already been built or that cloud deployment has already succeeded. Use the Actions workflow to create the APK and verify the resulting build.

## License and platform rules

The clipping engine in this repository is licensed under AGPL-3.0-or-later. Preserve the upstream license and copyright notices, and comply with AGPL source-disclosure obligations when distributing a modified version or offering it as a network service. Only process media you own or are authorized to clip, and follow the source platform's terms.
