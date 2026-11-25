@echo off
setlocal
cd /d "%~dp0.."
set "PY=%CD%\.venv\Scripts\python.exe"
if not exist "%PY%" (
  echo VFAB virtual environment not found: %PY%
  echo First run scripts\install_vfab_windows.bat from the project.
  pause
  exit /b 1
)
"%PY%" "%~dp0diagnose_vfab_windows.py"
echo.
echo Diagnostic finished. Copy all output from this window and send it to ChatGPT.
pause
