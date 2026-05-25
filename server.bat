@echo off
title Wildkarts Server
cd /d "%~dp0"
echo Starting Wildkarts Server...
call gradlew.bat :server:run
pause
