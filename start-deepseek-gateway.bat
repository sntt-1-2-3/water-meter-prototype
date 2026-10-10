@echo off
setlocal

echo ============================================================
echo   Free DeepSeek Gateway  (token-free-gateway)
echo   OpenAI-compatible base URL: http://localhost:3456/v1
echo ============================================================
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js not found. Please install Node.js first.
    pause
    exit /b 1
)

where token-free-gateway >nul 2>nul
if errorlevel 1 (
    echo [1/3] Installing token-free-gateway (global) ...
    call npm install -g token-free-gateway
    if errorlevel 1 (
        echo [ERROR] Failed to install token-free-gateway.
        pause
        exit /b 1
    )
)

echo.
echo [2/3] Authorizing DeepSeek web session. Chrome will open.
echo       Log in to https://chat.deepseek.com and KEEP the chat page open,
echo       then come back here and press Enter to select the DeepSeek provider.
echo       (If it does not return, just press Ctrl+C - credentials are saved.)
call token-free-gateway webauth

echo.
echo [3/3] Starting gateway on http://localhost:3456 ...
echo       Press Ctrl+C to stop.
call token-free-gateway serve

pause
