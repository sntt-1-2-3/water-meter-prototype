@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   智水云 · 一键启动
echo   免费 DeepSeek 网关 + 后端 + 前端
echo ============================================================
echo.

rem ---- 检查依赖 ----
where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] 未检测到 Node.js，请先安装 Node.js。
    pause
    exit /b 1
)

where token-free-gateway >nul 2>nul
if errorlevel 1 (
    echo [1/5] 首次运行：安装免费 DeepSeek 网关 token-free-gateway ...
    call npm install -g token-free-gateway
    if errorlevel 1 (
        echo [ERROR] 安装 token-free-gateway 失败。
        pause
        exit /b 1
    )
)

rem ---- 环境变量（会自动传递给后端 / 前端窗口）----
set "DEEPSEEK_ENABLED=true"
set "DEEPSEEK_BASE_URL=http://localhost:3456/v1"
set "VITE_USE_REAL_API=true"

echo [2/5] 启动免费 DeepSeek 网关 http://localhost:3456/v1 ...
start "deepseek-gateway" cmd /k "token-free-gateway serve"

echo [3/5] 启动后端 http://127.0.0.1:8000 （DeepSeek 已启用）...
start "water-backend" cmd /k ""%~dp0start-backend.bat""

echo [4/5] 启动前端 http://localhost:5173 （真实接口已启用）...
start "water-frontend" cmd /k ""%~dp0start-frontend.bat""

echo [5/5] 等待服务就绪后打开浏览器 ...
timeout /t 12 /nobreak >nul
start "" http://localhost:5173

echo.
echo ============================================================
echo  三个窗口分别运行：网关 / 后端 / 前端
echo    网关  http://localhost:3456
echo    后端  http://127.0.0.1:8000
echo    前端  http://localhost:5173
echo.
echo  首次使用若 AI 报错，请先运行 start-deepseek-gateway.bat
echo  完成 DeepSeek 网页登录授权（仅需一次），再重新运行本脚本。
echo  停止：分别关闭三个窗口即可。
echo ============================================================
echo.
pause
