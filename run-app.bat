@echo off
setlocal
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

echo Starting backend on http://localhost:8080...
start "Backend" cmd /k "cd /d "%ROOT%\backend" && mvnw.cmd spring-boot:run -q -Dmaven.resources.skip=true"

echo Waiting for backend to start...
set /a count=0
:wait_loop
timeout /t 2 /nobreak >nul
set /a count+=1
for /f "delims=" %%i in ('curl -s -o nul -w "%%{http_code}" -X POST -H "Content-Type: application/json" -H "Referer: http://localhost:5173/" -d "{\"mode\":\"convo_commerce\",\"message\":\"hi\"}" http://localhost:8080/api/chat 2^>nul') do set "HTTP_CODE=%%i"
if "%HTTP_CODE:~0,1%"=="2" (
  echo Backend ready.
  goto backend_ready
)
if %count% geq 15 (
  echo Backend failed to start in time.
  exit /b 1
)
goto wait_loop

:backend_ready
echo.
echo Starting frontend on http://localhost:5173...
start "Frontend" cmd /k "cd /d "%ROOT%\frontend" && npm run dev"

echo.
echo Application running:
echo   Backend:  http://localhost:8080
echo   Frontend: http://localhost:5173
echo.
echo Close the Backend and Frontend windows to stop.
pause
