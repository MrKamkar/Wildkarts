@echo off
title Wildkarts Game
cd /d "%~dp0"
echo Starting Wildkarts Game...
call gradlew.bat :lwjgl3:run
pause
