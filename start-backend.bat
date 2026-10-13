@echo off
setlocal
cd /d "%~dp0backend"

rem Locate the full JDK from JAVA_HOME or the javac executable on PATH.
if not defined JAVA_HOME (
    for /f "delims=" %%J in ('where javac.exe 2^>nul') do (
        if not defined JAVA_HOME for %%D in ("%%~dpJ..") do set "JAVA_HOME=%%~fD"
    )
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] A full JDK 17+ is required. Set JAVA_HOME to your JDK directory.
    pause
    exit /b 1
)
echo JAVA_HOME = %JAVA_HOME%
echo.
echo Starting backend on http://127.0.0.1:8000 ...
echo (first run downloads Maven + dependencies, may take a few minutes)
echo Press Ctrl+C to stop.
echo.

where mvn.cmd >nul 2>nul
if errorlevel 1 (
    call mvnw.cmd spring-boot:run
) else (
    call mvn.cmd spring-boot:run
)
if errorlevel 1 pause
