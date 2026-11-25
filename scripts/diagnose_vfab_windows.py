"""Isolate Windows native-library crashes in a VFAB AI / StreamClipper venv.

Run with the project's .venv Python. Each import is tested in its own child process so
STATUS_ILLEGAL_INSTRUCTION in one DLL does not prevent the remaining checks.
"""
from __future__ import annotations

import platform
import subprocess
import sys
import textwrap

CHECKS = [
    ("NumPy", "numpy", "import numpy as m; print('numpy', m.__version__)"),
    ("CTranslate2", "ctranslate2", "import ctranslate2 as m; "
     "print('ctranslate2', m.__version__); "
     "print('compute_types', m.get_supported_compute_types('cpu'))"),
    ("Faster-Whisper", "faster_whisper", "import faster_whisper as m; "
     "print('faster_whisper', getattr(m, '__version__', 'version not exposed'))"),
    ("ONNX Runtime", "onnxruntime", "import onnxruntime as m; "
     "print('onnxruntime', m.__version__); "
     "print('providers', m.get_available_providers())"),
    ("MediaPipe", "mediapipe", "import mediapipe as m; print('mediapipe', m.__version__)"),
    ("OpenCV", "cv2", "import cv2 as m; print('opencv', m.__version__)"),
    ("StreamClipper CLI imports", "streamclipper", "import streamclipper.cli; print('streamclipper CLI import OK')"),
]

CHILD = r'''
import importlib.util, sys, traceback
name, module, code = sys.argv[1], sys.argv[2], sys.argv[3]
if importlib.util.find_spec(module) is None:
    print(name + ': NOT INSTALLED')
    raise SystemExit(0)
try:
    exec(compile(code, '<vfab-diagnostic>', 'exec'), {})
except BaseException:
    traceback.print_exc()
    raise SystemExit(2)
'''


def main() -> int:
    print("VFAB AI Windows diagnostic")
    print("=" * 64)
    print("Python:", sys.version.replace("\\n", " "))
    print("Executable:", sys.executable)
    print("Platform:", platform.platform())
    print("Machine:", platform.machine())
    print("CPU reported by Python:", platform.processor() or "not reported")
    print()
    print("Testing installed native modules independently. Each check has a 25-second limit.")
    bad = []
    for label, module, code in CHECKS:
        print("\\n[TEST]", label, flush=True)
        try:
            result = subprocess.run(
                [sys.executable, "-X", "faulthandler", "-c", CHILD, label, module, code],
                capture_output=True, text=True, errors="replace", timeout=25,
            )
        except subprocess.TimeoutExpired:
            print("RESULT: TIMEOUT")
            bad.append(label + " timed out")
            continue
        output = (result.stdout or "") + (result.stderr or "")
        if output.strip():
            print(textwrap.indent(output.strip(), "  "))
        rc = result.returncode
        if rc == 0:
            print("RESULT: PASS")
        else:
            unsigned = rc & 0xFFFFFFFF
            print(f"RESULT: FAIL (exit={rc}, Windows code=0x{unsigned:08X})")
            if unsigned == 0xC000001D:
                print("  Meaning: STATUS_ILLEGAL_INSTRUCTION; this native component crashed.")
            bad.append(label + f" failed (0x{unsigned:08X})")
    print("\\n" + "=" * 64)
    if bad:
        print("FAILED CHECKS:")
        for item in bad:
            print(" -", item)
    else:
        print("All installed module import checks passed.")
    print("Send the entire output above to ChatGPT.")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
