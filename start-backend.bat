@echo off
setlocal
cd /d "%~dp0backend"

rem ---- locate a JDK 17+ (Spring Boot 3.3 requires JDK 17; Java 21 works) ----
set "JDK_OK="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JDK_OK=1"
)
if not defined JDK_OK (
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21"
        set "JDK_OK=1"
    )
)
if not defined JDK_OK (
    where java >nul 2>nul
    if not errorlevel 1 set "JDK_OK=1"
)
if not defined JDK_OK (
    echo [ERROR] 未找到 JDK 17+。请安装 JDK 21 或设置 JAVA_HOME。
    pause
    exit /b 1
)
if defined JAVA_HOME (
    echo JAVA_HOME = %JAVA_HOME%
) else (
    echo 使用 PATH 中的 java
)
echo.
echo Starting backend on http://127.0.0.1:8000 ...
echo (first run downloads Maven + dependencies, may take a few minutes)
echo Press Ctrl+C to stop.
echo.

call mvnw.cmd spring-boot:run
if errorlevel 1 pause
