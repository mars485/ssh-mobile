@echo off
rem Обёртка для запуска rustore-publish.ps1 без изменения ExecutionPolicy системы.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rustore-publish.ps1" %*
exit /b %ERRORLEVEL%
