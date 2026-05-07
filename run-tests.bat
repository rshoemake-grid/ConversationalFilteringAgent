@echo off
setlocal

echo === Running Backend Tests ===
cd /d "%~dp0backend"
call mvnw.cmd test -q
if errorlevel 1 (
  echo Backend tests failed.
  exit /b 1
)
echo Backend tests passed.
echo.

echo === Running Frontend Tests ===
cd /d "%~dp0frontend"
call npm test
if errorlevel 1 (
  echo Frontend tests failed.
  exit /b 1
)
echo Frontend tests passed.
echo.

echo === All tests passed ===
exit /b 0
