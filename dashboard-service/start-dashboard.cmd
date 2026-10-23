@echo off
setlocal
cd /d "%~dp0"
set "DASHBOARD_PYTHON=.venv\Scripts\python.exe"
if not exist "%DASHBOARD_PYTHON%" set "DASHBOARD_PYTHON=..\.venv\Scripts\python.exe"
if not exist "%DASHBOARD_PYTHON%" set "DASHBOARD_PYTHON=..\Recommendation-service\.venv\Scripts\python.exe"
if not exist "%DASHBOARD_PYTHON%" (
  echo Python environment not found. See README.md for setup.
  pause
  exit /b 1
)
"%DASHBOARD_PYTHON%" -X utf8 -m dashboard.serve
set "DASHBOARD_EXIT_CODE=%errorlevel%"
if not "%DASHBOARD_EXIT_CODE%"=="0" (
  echo.
  echo Dashboard failed with exit code %DASHBOARD_EXIT_CODE%. Read the error above.
  pause
)
exit /b %DASHBOARD_EXIT_CODE%
