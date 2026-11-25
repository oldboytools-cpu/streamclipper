@echo off
setlocal
cd /d "%~dp0\.."

if not exist ".venv\Scripts\activate.bat" (
    echo VFAB AI is not installed yet. Run scripts\install_vfab_windows.bat first.
    pause
    exit /b 1
)

call .venv\Scripts\activate.bat
if not defined STREAMCLIPPER_WORKSPACE set "STREAMCLIPPER_WORKSPACE=%USERPROFILE%\Videos\VFAB-AI"
echo.
echo Keep this terminal open while the Android app is connected.
echo Copy the full network URL containing ?token= into the VFAB AI Android app.
echo Only use this on a trusted private Wi-Fi. Do not share the URL.
echo.
streamclipper dashboard --lan --no-browser --workspace "%STREAMCLIPPER_WORKSPACE%"
pause
