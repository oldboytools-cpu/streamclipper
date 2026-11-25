# VFAB AI Creator Studio — Product Requirements & Implementation Backlog

This document records the requested product scope for VFAB AI. It is a delivery backlog, not a claim that every feature below is already implemented.

## Product principle

VFAB AI is a local-first AI clipping and creator studio. Windows is the primary processing workstation; Android is a companion controller; cloud processing is optional. The local workflow must remain usable without paid AI APIs.

## 1. Clip discovery, ranking, and output count

### Required behavior
- Analyse the full VOD or local recording, subject to available source access and local resources.
- Detect all candidate moments that pass the configured quality threshold.
- Score and rank candidates by available signals: setup/payoff, speech/context, emotion/reaction, audio activity, chat activity, heatmap and supported visual/HUD cues.
- Do not impose a fixed three-clip output limit.
- Let the user set a maximum clip count. A value of 0 means render every candidate that passes the quality threshold.
- Preserve ranking: best-scoring clips first. Remove or suppress duplicate/overlapping moments where the selector can identify them.
- Show score, category, reason and timestamps for each candidate so the user can review before export.

### Current implementation status
- The underlying CLI already supports `--max-clips 0` for all clips above the quality threshold and ranks candidates.
- The dashboard previously defaulted to 3. This change makes the default 0 (all qualifying clips) and adds a regression test.
- Ranking quality, duplication control and full end-to-end rendering still require validation on representative long VODs.

## 2. AI intelligence

### Required behavior
- Audio intelligence: voice activity, excitement, laughter, shouting, reactions and meaningful audio peaks.
- Speech intelligence: transcription, context, setup and payoff.
- Visual intelligence: gameplay changes, visible reactions, HUD/event cues and relevant scene changes.
- Engagement intelligence: chat replay and platform heatmap signals when the source exposes them.
- A combined ranking layer that uses only signals actually available for the source.
- Gaming profiles, with GTA 6 event detection explicitly treated as experimental until validated.
- Clear fallback behavior when captions, chat replay, heatmaps, webcam or platform data are unavailable.

### Current implementation status
- Existing engine contains audio scoring, speech recognition, chat/heatmap inputs, face-related analysis and HUD event support.
- GTA 6 HUD recognition is experimental; YouTube heatmap/chat may not be available for every stream.
- Dashboard now defaults to Turbo mode: candidate-region transcription with Whisper Tiny and single-beam decoding. Balanced regions, full-stream transcription and no-transcription modes remain selectable. Turbo is faster but less accurate for speech than larger models; candidate-region analysis can miss quiet moments outside selected regions.
- MP4 export now smoke-tests Intel Quick Sync (`h264_qsv`) and uses it when the FFmpeg build and driver can encode; if unavailable or a render fails, it falls back to x264 `veryfast`. Hardware speed-up is not yet benchmarked on the user's specific laptop.
- GitHub Actions CI passed on Windows and Ubuntu with Python 3.10 and 3.12 for the Turbo-mode changes.
- Better multimodal fusion and quality evaluation remain development work.

## 3. Editing studio

### Required behavior
- Vertical 9:16 and square output options.
- Caption presets, animated captions, hook/headline treatment and editable subtitle timing.
- Reframe and crop controls, subject/face tracking, zoom and safe-zone control.
- Timeline editing, trim/split/reorder, transitions and audio controls.
- Brand kit: logo, colours, fonts, handle, intro/outro and reusable templates.
- Thumbnail generation and clip preview before export.
- Export profiles for Shorts, Reels and TikTok.

### Current implementation status
- Existing dashboard/editor has reframe, caption styling, zoom/effects, brand-kit support and export functionality.
- A full, polished timeline and native desktop editing experience still need design, implementation and testing.

## 4. Publishing

### Required behavior
- Manual download/review/publish workflow.
- Connected-account publishing and scheduling where official platform APIs permit it.
- Per-platform title, description, tags, captions, privacy and thumbnail settings.
- Upload progress, retry handling, publishing history and clear API/permission errors.
- Never claim a post was published until the platform confirms success.

### Current implementation status
- Existing code has YouTube and Instagram publishing integrations and a TikTok-oriented manual export/pack path.
- Real account connections, publishing permissions, scheduling behavior and retries require live integration tests.
- Direct TikTok publishing is not claimed as complete.

## 5. Windows application

### Required behavior
- Installable Windows application/package with guided setup and dependency checks.
- Start/stop controls, local dashboard, processing queue, history, logs and settings.
- Background processing, recovery/resume, notifications and safe workspace management.
- CPU mode supported; optional compatible GPU acceleration where available.

### Current implementation status
- Python engine, dashboard, Windows setup and launch scripts exist.
- A PyInstaller one-folder Windows bundle containing `VFABAI.exe` has been built successfully by GitHub Actions; the packaged CLI `--help` smoke check passed.
- The Windows workflow now builds a user-level Inno Setup installer (`VFAB-AI-Setup.exe`) in addition to the portable ZIP; this installer's build is being verified.
- This is a portable bundle, not a signed installer. FFmpeg/FFprobe are installed separately, and Whisper model files download on first use.
- A fully polished desktop shell, background-service UX, signing and installer-grade setup still require additional work.

## 6. Android application

### Required behavior
- Native application screens for URL/file submission, job progress, clip previews, download/share, history and settings.
- Secure pairing to a laptop workspace or private cloud workspace.
- Push notifications when jobs complete or fail.
- Graceful reconnect when the laptop or network is unavailable.

### Current implementation status
- The Android debug APK builds successfully.
- A native Kotlin project-control screen now supports workspace connection, VOD/live job submission, Turbo mode, all qualifying ranked clips, project status refresh, cancel and resume.
- A native ranked-clips screen now loads clip scores and reasons, previews rendered MP4s in-app, downloads clips with the authenticated workspace cookie and shares the downloaded video through Android's share sheet. The APK build is running for this update.
- The full clip editor, preview, trim and export screens still open the existing dashboard inside the app; the entire editing experience is not yet native.
- Background push notifications, release signing and Play Store preparation still require separate implementation and release setup.

## 7. Optional cloud workspace

### Required behavior
- Optional remote processing so the laptop does not need to stay on.
- Private HTTPS access, durable storage, job queue, quotas, retention and secure account isolation.
- Explicit cost controls and clear user consent for paid cloud compute.
- Local mode must continue to work independently.

### Current implementation status
- A Render Blueprint configures a paid, private, single-owner workspace.
- It has not been deployed or production-verified.
- This configuration is not a multi-user SaaS and must not be exposed as one.

## 8. Accounts, subscriptions and scale

### Required behavior
- Individual accounts, authentication, account recovery and session/device management.
- Per-user data and job isolation.
- Subscription plans, payments, receipts, cancellation and usage/credit metering.
- Abuse prevention, quotas, support tooling, audit logs and data deletion/export.

### Current implementation status
- Not implemented. This requires a production backend, database, payment provider, security review and operational policies.

## 9. Release acceptance gates

A feature is complete only when its acceptance test passes; source code or a configuration file alone is not proof of a working release.

1. **Engine gate:** Process a representative long VOD end-to-end, produce multiple ranked clips, and verify the configured count including 0 = all passing clips.
2. **Quality gate:** Review ranked results on varied gaming streams and measure duplicate rate, false positives and missed high-value moments.
3. **Editing gate:** Export vertical clips with correct audio, captions, framing, branding and thumbnails.
4. **Publishing gate:** Test connected platform accounts and confirm success/failure states using official APIs.
5. **Windows gate:** Install and launch on a clean supported Windows machine; test restart and job recovery.
6. **Android gate:** Install a release build; pair securely; submit a job; preview/download; test reconnect and notifications.
7. **Cloud gate:** Deploy, test persistent storage, private access, recovery, cost controls and backup/retention.
8. **SaaS gate:** Complete authentication, authorization, tenant isolation, billing, quota enforcement, security review and deletion workflows.

## 10. Delivery order

1. Stabilise end-to-end local processing, Turbo-mode speed, and unlimited/ranked clip selection.
2. Validate and improve AI highlight quality.
3. Complete editing and brand-kit experience.
4. Verify publishing integrations.
5. Package Windows and complete native Android.
6. Deploy optional private cloud processing.
7. Build multi-user accounts and subscriptions only after security and billing design.

