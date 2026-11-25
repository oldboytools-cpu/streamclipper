@echo off
setlocal
cd /d "%~dp0.."
if not exist ".venv\Scripts\python.exe" (
  echo Project virtual environment not found.
  echo Run scripts\install_vfab_windows.bat first.
  pause
  exit /b 1
)
".venv\Scripts\python.exe" -m pip install pyinstaller
if errorlevel 1 goto :fail
".venv\Scripts\python.exe" -m PyInstaller --noconfirm --clean --onedir --name VFABAI --paths src --collect-all streamclipper --collect-all faster_whisper --collect-all ctranslate2 --collect-all av --collect-all mediapipe --collect-all onnxruntime --collect-all cv2 --collect-all huggingface_hub src\streamclipper\__main__.py
if errorlevel 1 goto :fail
copy /Y scripts\start_vfab_exe.bat dist\VFABAI\Start-VFAB-AI.bat >nul
echo.
echo Build complete: dist\VFABAI\VFABAI.exe
echo FFmpeg must still be installed separately.
pause
exit /b 0
:fail
echo.
echo Build failed. Review the error above.
pause
exit /b 1
