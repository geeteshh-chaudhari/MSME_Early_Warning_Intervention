@echo off
cd /d "%~dp0"
set "DASHBOARD_PYTHON=.venv\Scripts\python.exe"
if not exist "%DASHBOARD_PYTHON%" set "DASHBOARD_PYTHON=..\Recommendation-service\.venv\Scripts\python.exe"
if not exist "%DASHBOARD_PYTHON%" (
  echo Python environment not found. See README.md for setup.
  exit /b 1
)
"%DASHBOARD_PYTHON%" -m dashboard.serve
exit /b %errorlevel%
