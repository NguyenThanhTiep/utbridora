@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-local.ps1" %*
set "UTBRIDORA_EXIT=%ERRORLEVEL%"
if "%~1"=="" pause
exit /b %UTBRIDORA_EXIT%
