@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0VFABAI.exe" (
  echo VFAB AI executable was not found in this folder.
  echo Extract the full VFAB-AI-Windows bundle before running this file.
  pause
  exit /b 1
)
if not defined STREAMCLIPPER_WORKSPACE set "STREAMCLIPPER_WORKSPACE=%USERPROFILE%\Videos\VFAB-AI"
echo VFAB AI workspace: %STREAMCLIPPER_WORKSPACE%
echo Keep this window open while using the dashboard.
"%~dp0VFABAI.exe" dashboard --workspace "%STREAMCLIPPER_WORKSPACE%" --port 8765
echo.
echo VFAB AI has stopped. Review the messages above.
pause
