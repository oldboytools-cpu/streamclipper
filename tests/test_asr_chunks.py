from types import SimpleNamespace

from streamclipper.asr import Whisper, choose_model, chunk_bounds, read_wav16k, silence_gaps, transcribe_full
from streamclipper.state import RunState


def _islands(spans):
    return [{"cut_start": a, "cut_end": b, "speech_start": a, "speech_end": b} for a, b in spans]


def _talking_stream(dur, pauses):
    """Speech everywhere except the given (a, b) pauses."""
    spans, t = [], 0.0
    for a, b in sorted(pauses):
        spans.append((t, a))
        t = b
    spans.append((t, dur))
    return _islands(spans)


def _in_gap(t, gaps):
    return any(a <= t <= b for a, b in gaps)


def test_cuts_land_in_silences():
    dur = 1700.0
    pauses = [(589.0, 590.0), (612.0, 612.6), (1195.0, 1196.0), (1230.0, 1232.0)]
    isl = _talking_stream(dur, pauses)
    bounds = chunk_bounds(dur, isl, 600.0, 15.0)
    gaps = silence_gaps(isl, dur)
    assert bounds[0][0] == 0.0 and bounds[-1][1] == dur
    for (_, b0), (a1, _) in zip(bounds, bounds[1:], strict=False):
        assert b0 == a1
        assert _in_gap(b0, gaps), b0
    assert abs(bounds[0][1] - 589.5) < 1e-6  # nearest pause to 600 s within +-15 s


def test_no_pause_in_reach_cuts_at_target_and_short_streams_are_one_chunk():
    isl = _talking_stream(1300.0, [(100.0, 101.0)])
    assert [round(b, 1) for _, b in chunk_bounds(1300.0, isl, 600.0, 15.0)] == [600.0, 1200.0, 1300.0]
    assert chunk_bounds(610.0, isl, 600.0, 15.0) == [(0.0, 610.0)]
    assert chunk_bounds(0.0, isl) == []


class FakeTranscriber:
    label = "fake"

    def __init__(self):
        self.calls = []

    def __call__(self, a, b):
        self.calls.append((a, b))
        return [{"w": f"w{int(a)}", "s": a + 1.0, "e": a + 1.5}], "en"


ST = {"asr": "full", "whisper_model": "auto", "device": "cpu", "min_dur": 15.0, "max_dur": 45.0}


def test_resume_skips_done_chunk_files(tmp_path, capsys):
    dur = 1700.0
    isl = _talking_stream(dur, [(589.0, 590.0), (1195.0, 1196.0)])
    state = RunState(tmp_path / "state.json", "file:x", ST)
    fake = FakeTranscriber()
    words, lang, info = transcribe_full("x.wav", tmp_path, state, isl, dur, transcribe_chunk=fake)
    assert len(fake.calls) == 3 and lang == "en" and info["chunks"] == 3
    assert [w["w"] for w in words] == ["w0", "w589", "w1195"]
    assert (tmp_path / "asr" / "chunk_0002.json").is_file()

    # Simulate a run that stopped after chunk 0: forget chunks 1 and 2.
    (tmp_path / "asr" / "chunk_0002.json").unlink()
    state.stages["asr_chunks"] = [0, 1]
    state.save()
    (tmp_path / "asr" / "chunk_0001.json").unlink()
    state2 = RunState(tmp_path / "state.json", "file:x", ST)
    fake2 = FakeTranscriber()
    capsys.readouterr()
    words2, _, info2 = transcribe_full("x.wav", tmp_path, state2, isl, dur, transcribe_chunk=fake2)
    out = capsys.readouterr().out
    assert "Resuming: 1/3 chunks done" in out
    assert len(fake2.calls) == 2 and fake2.calls[0][0] == 589.5
    assert words2 == words
    assert info2["resumed_chunks"] == 1
    assert state2.stages["asr_chunks"] == [0, 1, 2]


def test_read_wav16k_gives_float_samples(tmp_path):
    """Whisper gets arrays, never file paths (faster-whisper's PyAV decoder breaks with av >= 19)."""
    import wave

    import numpy as np

    wav = tmp_path / "a.wav"
    with wave.open(str(wav), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(16000)
        f.writeframes(np.array([0, 16384, -32768], dtype="<i2").tobytes())
    a = read_wav16k(wav)
    assert a.dtype == np.float32
    assert a.tolist() == [0.0, 0.5, -1.0]


def test_tiny_model_is_explicit_fast_cpu_choice():
    assert choose_model("cpu", 7 * 3600, "tiny") == ["tiny"]


def test_tiny_model_uses_fast_decode_settings():
    class FakeModel:
        def transcribe(self, audio, **kwargs):
            self.kwargs = kwargs
            return [], SimpleNamespace(language="en")

    model = FakeModel()
    words, language = Whisper(model, "tiny", "cpu", "int8").run([], "en")
    assert words == [] and language == "en"
    assert model.kwargs["beam_size"] == 1
    assert model.kwargs["best_of"] == 1
    assert model.kwargs["condition_on_previous_text"] is False
