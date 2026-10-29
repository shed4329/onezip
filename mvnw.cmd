@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\maven.ps1" %*
exit /b %ERRORLEVEL%
