<p align="center">
  <img src="docs/media/streamclipper_banner.png" alt="StreamClipper" width="720">
</p>

<p align="center">
  <b>Paste a stream link. Get the best moments as ready-to-post Shorts and editable CapCut projects. Free.</b>
</p>

<p align="center">
  <a href="https://colab.research.google.com/github/oldboytools-cpu/streamclipper/blob/main/notebooks/StreamClipper_Colab.ipynb"><img src="https://colab.research.google.com/assets/colab-badge.svg" alt="Open in Colab"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg" alt="License: AGPL-3.0"></a>
  <a href="https://github.com/alokbaraii/streamclipper"><img src="https://img.shields.io/github/stars/alokbaraii/streamclipper?style=social" alt="GitHub stars"></a>
</p>

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https%3A%2F%2Fgithub.com%2Foldboytools-cpu%2Fstreamclipper%2Ftree%2Fdeploy%2Fvfab-cloud-first)

## Why StreamClipper

- **Finds the best clips, not the most clips.** Each clip has a setup, a payoff and the reaction, with a hook in the first 2 seconds.
- **Clips live streams while they run.** A clip is ready about 35 seconds after the moment.
- **Any length.** 8-10 hour streams work. Progress is saved, so a crash or Colab disconnect resumes.
- **Ready to post.** 1080x1920 MP4s with animated captions (15 styles), a hook headline and a thumbnail.
- **Editable.** Every clip is also a CapCut Desktop project, with each layer on its own track.
- **Reframe editor.** Split a clip into parts and choose what each part shows: its own layout, crop and keyframed pan, or Auto-reframe to follow the face.
- **Sees the game screen.** GTA V's WASTED, BUSTED, mission screens and wanted stars count towards the score (`--hud`; GTA 6 profile experimental).
- **Submagic-style edits, all optional.** Punch-in zoom, filler-word removal, speech cleanup and your own brand kit.
- **Auto-publish** to YouTube Shorts and Instagram Reels with your own API credentials, or schedule a week (`streamclipper publish`).
- **MediaPipe face tracking** follows the face and picks who is talking in podcasts and Auto-reframe.
- **Free, no watermark, no limits.** Runs on free Google Colab or your own PC. No paid AI needed.

Works with Twitch, YouTube and Kick links, or a local video file. Made for gaming streams; podcasts are in beta.

## Quick start

### Option 1: Google Colab (no strong PC needed)

1. Click **Open in Colab** above.
2. Choose **Runtime > Change runtime type > T4 GPU**.
3. Paste your stream link, then **Runtime > Run all**.

Your clips land in Google Drive under `MyDrive/StreamClipper/`.

### Option 2: Windows PC

Needs Python 3.10+ and ffmpeg (`winget install Gyan.FFmpeg`).

```powershell
git clone https://github.com/oldboytools-cpu/streamclipper.git
cd streamclipper
python -m pip install -e ".[asr,gpu,vision]"
streamclipper doctor
streamclipper clip-stream --url "https://www.twitch.tv/videos/<id>" --out work --render
```

No NVIDIA GPU? Install with `".[asr,vision]"` instead. It works on the CPU, just slower.

Open `work\\index.html` to see your clips ranked by score.

## GTA VI streamer Shorts

The fork includes a GTA-focused shortcut with 9:16 output, gaming highlight scoring, automatic category captions, a hook banner, light payoff zoom and up to 10 rendered MP4s. It uses the existing local pipeline; the shortcut does not require a paid AI API.

### Google Colab (recommended)

1. Open the **Open in Colab** badge at the top of this fork.
2. Select **Runtime > Change runtime type > T4 GPU** when available.
3. Run the cells from top to bottom.
4. Paste the YouTube VOD URL in cell 5 and run cell 7. The notebook saves results to `MyDrive/StreamClipper/`.

The fork's notebook defaults are pre-set for GTA VI: 9:16, 10 clips, score threshold 50, auto captions, bold-box hooks, the GTA VI HUD profile and light payoff zoom. You still need to paste your own stream URL and approve Google Drive access.

### Windows command

After installing the project and its ASR/vision extras, run:

```powershell
python -m pip install -e ".[asr,vision]"
streamclipper-gta-shorts "https://www.youtube.com/watch?v=<VIDEO_ID>" --out gta6_shorts
```

Add `.[asr,gpu,vision]` instead when using a supported NVIDIA GPU. The GTA VI HUD profile is experimental and is based on the existing GTA V event template; it can add WASTED/BUSTED/mission/wanted-level signals, but should not be treated as a reliable GTA VI-specific detector yet. Audio/gameplay highlight scoring continues to work if HUD recognition misses an event.


## Live clipping

```powershell
streamclipper live --url https://www.twitch.tv/<channel> --out live_clips --render
```

Leave it running. Each good moment becomes a clip in `live_clips\`. Press Ctrl+C to stop.
## Dashboard
Run `streamclipper dashboard`. It runs on your PC and opens in the browser: paste a link on Home, watch your projects, play clips, reframe them in the editor, re-style them, open them in CapCut and mark what you posted.
![Dashboard](docs/media/dashboard.png)
With `--lan`, Share makes view-only links that work for people on the same Wi-Fi/network. Details: [docs/GUIDE.md](docs/GUIDE.md#dashboard).

## Common options

| You want | Add |
|---|---|
| A different caption look | `--caption-style comic-burst` (see all with `streamclipper styles`) |
| Square video | `--ratio 1:1` |
| More or fewer clips | `--min-score 40` (more) or `--max-clips 5` (cap) |
| Better titles with a free local AI | `--llm ollama` |
| A podcast instead of gameplay | `--preset podcast-9x16` |
| Set the webcam box yourself | `--cam-crop x,y,w,h` |

![Caption styles](docs/media/styles_gallery.jpg)

## Open a clip in CapCut

Close CapCut, run `clip-stream` without `--no-draft`, then open CapCut. Your clips are at the top of the project list. From Colab, download `capcut_drafts.zip` and run `streamclipper import-draft --zip capcut_drafts.zip`.

## Status

| Mode | Status |
|---|---|
| Gaming VODs | Stable. Tested on real streams. |
| Live clipping | New. Tested on a real Twitch stream. YouTube Live and Kick not tested yet. |
| Podcasts | Beta. |
| Colab | Tested on a real Colab T4: speech recognition at 58.8x realtime. |

## Docs

- [Full guide](docs/GUIDE.md): every command and flag, presets, caption styles, how scoring works, speeds and troubleshooting.
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

## Legal

Only clip streams you have permission to clip, such as a streamer's official clipping program. CapCut effects are copied from your own CapCut install. Fonts are under the OFL or Apache licences; dashboard icons are from Lucide (ISC).

Code licence: [AGPL-3.0](LICENSE). You can use, change and share it for free. If you run a changed version as an online service, you must share your source code too.


## Private cloud deployment (single-owner web workspace)

A Render deployment blueprint is included in `render.yaml`. It runs the existing dashboard behind Render's HTTPS proxy, uses a persistent disk for uploaded videos and generated clips, and protects the workspace with a generated bearer token. It is a **single-owner private workspace**, not a multi-user SaaS: anyone who obtains the token can control the workspace. Keep deployment logs and the token private.

1. Push this repository to your GitHub account (this branch is already published by the assistant as `deploy/vfab-cloud-first`).
2. In Render, create a new Blueprint from this repository and select `render.yaml`.
3. Render provisions the web service, persistent disk and a strong generated `STREAMCLIPPER_ACCESS_TOKEN`.
4. After deployment, open the service logs and use the printed HTTPS URL containing `?token=...`. Save it privately; it is the workspace access link.
5. The service uses CPU processing. Long videos can take a long time and may exceed the memory/disk limits of a small instance. The included blueprint uses a paid instance and persistent storage; hosting and compute are not free.

For production multi-user access, add per-user authentication, quotas, isolated worker queues, object storage and a database before inviting other users. Do not expose this single-token workspace publicly or share its URL.
