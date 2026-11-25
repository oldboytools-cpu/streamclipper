from types import SimpleNamespace

from streamclipper import render


def test_qsv_encoder_smoke_check_success(monkeypatch):
    render._qsv_encoder_available.cache_clear()
    monkeypatch.setattr(render, "run_cmd", lambda *args, **kwargs: SimpleNamespace(returncode=0))
    assert render._qsv_encoder_available() is True
    assert render._video_encoder_args(True)[:2] == ["-c:v", "h264_qsv"]
    render._qsv_encoder_available.cache_clear()


def test_qsv_encoder_smoke_check_falls_back(monkeypatch):
    render._qsv_encoder_available.cache_clear()
    monkeypatch.setattr(render, "run_cmd", lambda *args, **kwargs: SimpleNamespace(returncode=1))
    assert render._qsv_encoder_available() is False
    args = render._video_encoder_args(False)
    assert args[:2] == ["-c:v", "libx264"]
    assert args[args.index("-preset") + 1] == "veryfast"
    render._qsv_encoder_available.cache_clear()
