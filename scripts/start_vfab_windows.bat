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
echo VFAB AI workspace: %STREAMCLIPPER_WORKSPACE%
echo This dashboard is private to this laptop.
streamclipper dashboard
pause
