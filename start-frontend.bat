@echo off
cd /d "%~dp0"

if not exist "node_modules\vite\bin\vite.js" (
    echo Installing locked frontend dependencies...
    call npm ci
    if errorlevel 1 (
        pause
        exit /b 1
    )
)

set "VITE_USE_REAL_API=true"

echo Starting frontend (Vite dev server) on http://localhost:5173 ...
echo Press Ctrl+C to stop.
echo.

call npm run dev
if errorlevel 1 (
    echo.
    echo If "npm is not recognized", install Node.js first, or run "npm install".
    pause
)
