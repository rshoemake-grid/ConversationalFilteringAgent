@echo off
cd /d "%~dp0backend"
echo Starting backend on http://localhost:8080...
call mvnw.cmd spring-boot:run -Dmaven.resources.skip=true
