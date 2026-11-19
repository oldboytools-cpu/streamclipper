"""One-command, free GTA gaming Shorts workflow.

This is a thin preset over StreamClipper's existing clip-stream pipeline. It uses
local speech recognition and the built-in scoring/rendering pipeline; no hosted
AI API is required. The GTA VI HUD profile remains experimental.
"""

from __future__ import annotations

import argparse
import subprocess
import sys


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="streamclipper-gta-shorts",
        description=(
            "Turn a YouTube/Twitch/Kick GTA stream VOD into ranked 9:16 Shorts. "
            "Uses local processing by default; no paid AI API is required."
        ),
    )
    parser.add_argument("url", help="YouTube, Twitch or Kick VOD URL you have permission to clip")
    parser.add_argument("--out", default="gta6_shorts", help="Output/work folder (default: gta6_shorts)")
    parser.add_argument("--max-clips", type=int, default=10, help="Maximum MP4s to render (default: 10)")
    parser.add_argument("--min-score", type=float, default=50, help="Minimum highlight score, 0-100 (default: 50)")
    parser.add_argument("--target-dur", type=float, default=30, help="Target clip length in seconds (default: 30)")
    parser.add_argument("--min-dur", type=float, default=15, help="Minimum clip length in seconds (default: 15)")
    parser.add_argument("--max-dur", type=float, default=45, help="Maximum clip length in seconds (default: 45)")
    parser.add_argument(
        "--hud",
        choices=("auto", "gta6", "gta5", "generic", "off"),
        default="auto",
        help="Game HUD profile; auto detects GTA VI from the VOD title (default: auto)",
    )
    parser.add_argument(
        "--llm",
        choices=("none", "ollama"),
        default="none",
        help="Optional local Ollama model for copywriting; none is the fully free/no-model default",
    )
    parser.add_argument(
        "--capcut-drafts",
        action="store_true",
        help="Also create editable CapCut Desktop drafts (MP4s are created either way)",
    )
    return parser


def build_command(args: argparse.Namespace) -> list[str]:
    """Return the streamclipper CLI invocation for the parsed arguments."""
    command = [
        sys.executable,
        "-m",
        "streamclipper",
        "clip-stream",
        "--url",
        args.url,
        "--out",
        args.out,
        "--prefix",
        "GTA6",
        "--max-clips",
        str(args.max_clips),
        "--min-score",
        str(args.min_score),
        "--target-dur",
        str(args.target_dur),
        "--min-dur",
        str(args.min_dur),
        "--max-dur",
        str(args.max_dur),
        "--ratio",
        "9:16",
        "--preset",
        "gta6-streamer-9x16",
        "--caption-style",
        "auto",
        "--hook-style",
        "bold-box",
        "--hook-mode",
        "tease-climax",
        "--content",
        "gaming",
        "--hud",
        args.hud,
        "--asr",
        "auto",
        "--device",
        "auto",
        "--whisper-model",
        "auto",
        "--llm",
        args.llm,
        "--render",
    ]
    if not args.capcut_drafts:
        command.append("--no-draft")
    return command


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if not args.url.strip():
        parser.error("URL cannot be empty")
    if args.max_clips < 0:
        parser.error("--max-clips must be zero or greater (0 means all qualifying clips)")
    if not 0 <= args.min_score <= 100:
        parser.error("--min-score must be between 0 and 100")
    if not (0 < args.min_dur <= args.target_dur <= args.max_dur <= 59.5):
        parser.error("clip durations must satisfy 0 < min-dur <= target-dur <= max-dur <= 59.5 seconds")

    print("Starting the GTA gaming Shorts workflow...")
    print("Output folder:", args.out)
    print("AI API mode: local/free (no hosted AI API)")
    print("Note: GTA VI HUD recognition is experimental; normal audio/gameplay scoring still runs.")
    try:
        completed = subprocess.run(build_command(args), check=False)
    except KeyboardInterrupt:
        print("\nStopped by user.")
        return 130
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
