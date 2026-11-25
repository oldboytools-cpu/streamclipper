@echo off
setlocal
cd /d "%~dp0\.."

echo ==========================================
echo          VFAB AI - Windows Setup
echo ==========================================
echo.

set "PYTHON_LAUNCHER="
py -3.11 --version >nul 2>&1
if not errorlevel 1 set "PYTHON_LAUNCHER=py -3.11"

if not defined PYTHON_LAUNCHER (
    py -3.10 --version >nul 2>&1
    if not errorlevel 1 set "PYTHON_LAUNCHER=py -3.10"
)

if not defined PYTHON_LAUNCHER (
    echo Python 3.10 or 3.11 was not found.
    echo Install Python 3.11 x64 from https://www.python.org/downloads/windows/
    echo Enable the Python Launcher during installation, then run this file again.
    pause
    exit /b 1
)

echo Using %PYTHON_LAUNCHER%
%PYTHON_LAUNCHER% -m venv .venv
if errorlevel 1 goto failed

call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
if errorlevel 1 goto failed

python -m pip install -e ".[asr,vision]"
if errorlevel 1 goto failed

echo.
where ffmpeg >nul 2>&1
if errorlevel 1 (
    echo FFmpeg was not found. Install it with:
    echo   winget install --id Gyan.FFmpeg --exact
    echo Then close and reopen this terminal.
) else (
    echo FFmpeg found.
)

echo.
echo Running VFAB AI system check...
streamclipper doctor
echo.
echo Setup finished. Use scripts\start_vfab_windows.bat to open the laptop dashboard.
echo Use scripts\start_vfab_phone.bat when you want to connect the Android app on the same Wi-Fi.
pause
exit /b 0

:failed
echo.
echo Setup failed. Read the error above, check internet access, and run this file again.
pause
exit /b 1
