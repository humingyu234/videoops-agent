@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%SCRIPT_DIR%start-local-user-api.ps1" %*
exit /b %ERRORLEVEL%
