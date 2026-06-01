@echo off
title Wildkarts Server
cd /d "%~dp0"

REM Gradle needs JDK, not Java 8 JRE from PATH
if exist "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

echo Starting Wildkarts Server...
call gradlew.bat :server:run
pause
