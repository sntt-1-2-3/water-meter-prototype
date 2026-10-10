@echo off
cd /d "%~dp0"

echo Starting backend and frontend in two windows...
start "water-backend" cmd /k ""%~dp0start-backend.bat""
start "water-frontend" cmd /k ""%~dp0start-frontend.bat""

echo Waiting for frontend to be ready...
timeout /t 8 /nobreak >nul

echo Opening browser at http://localhost:5173 ...
start "" http://localhost:5173
