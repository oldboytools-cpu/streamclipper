from streamclipper.gta import build_command, build_parser


def test_gta_shortcut_uses_free_vertical_gaming_defaults():
    args = build_parser().parse_args(["https://www.youtube.com/watch?v=example"])
    cmd = build_command(args)

    assert cmd[cmd.index("clip-stream")] == "clip-stream"
    assert cmd[cmd.index("--url") + 1] == "https://www.youtube.com/watch?v=example"
    assert cmd[cmd.index("--ratio") + 1] == "9:16"
    assert cmd[cmd.index("--preset") + 1] == "gta6-streamer-9x16"
    assert cmd[cmd.index("--max-clips") + 1] == "10"
    assert cmd[cmd.index("--min-score") + 1] == "50"
    assert cmd[cmd.index("--hud") + 1] == "auto"
    assert cmd[cmd.index("--llm") + 1] == "none"
    assert "--render" in cmd
    assert "--no-draft" in cmd
    assert "--capcut-drafts" not in cmd


def test_gta_shortcut_can_make_capcut_drafts_and_use_local_ollama():
    args = build_parser().parse_args(
        [
            "https://www.twitch.tv/videos/example",
            "--capcut-drafts",
            "--llm",
            "ollama",
            "--hud",
            "gta6",
        ]
    )
    cmd = build_command(args)

    assert "--no-draft" not in cmd
    assert cmd[cmd.index("--llm") + 1] == "ollama"
    assert cmd[cmd.index("--hud") + 1] == "gta6"
