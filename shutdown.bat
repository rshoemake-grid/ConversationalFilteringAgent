@echo off
setlocal
echo Shutting down Conversational Filtering Agent...
echo.

echo Stopping backend (port 8080)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
  taskkill /PID %%a /F >nul 2>&1
  echo   Backend stopped.
)

echo Stopping frontend (port 5173)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5173 ^| findstr LISTENING') do (
  taskkill /PID %%a /F >nul 2>&1
  echo   Frontend stopped.
)

echo.
echo Done.
