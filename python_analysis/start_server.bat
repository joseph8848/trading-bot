@echo off
echo ========================================
echo Starting Python Analysis Server
echo ========================================
echo.

cd /d "%~dp0"

echo Checking Python installation...
python --version
if errorlevel 1 (
    echo ERROR: Python not found. Please install Python 3.8+
    pause
    exit /b 1
)

echo.
echo Starting API server on port 5001...
echo.
python api_server.py

pause
