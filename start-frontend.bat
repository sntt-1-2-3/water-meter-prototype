@echo off
cd /d "%~dp0"

echo Starting frontend (Vite dev server) on http://localhost:5173 ...
echo Press Ctrl+C to stop.
echo.

call npm run dev
if errorlevel 1 (
    echo.
    echo If "npm is not recognized", install Node.js first, or run "npm install".
    pause
)
