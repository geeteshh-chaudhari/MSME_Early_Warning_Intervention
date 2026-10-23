@echo off
setlocal
cd /d "%~dp0"
set "RECOMMENDATION_PYTHON=.venv\Scripts\python.exe"
if not exist "%RECOMMENDATION_PYTHON%" set "RECOMMENDATION_PYTHON=..\.venv\Scripts\python.exe"
if not exist "%RECOMMENDATION_PYTHON%" (
  echo Python environment not found. See README.md for setup.
  pause
  exit /b 1
)
"%RECOMMENDATION_PYTHON%" -X utf8 -m recommendation.serve
set "RECOMMENDATION_EXIT_CODE=%errorlevel%"
if not "%RECOMMENDATION_EXIT_CODE%"=="0" (
  echo.
  echo Recommendation service failed with exit code %RECOMMENDATION_EXIT_CODE%. Read the error above.
  pause
)
exit /b %RECOMMENDATION_EXIT_CODE%
