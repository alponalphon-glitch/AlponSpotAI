@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (gradle %*) else (echo Gradle 8.9 is required. Install Gradle or run the GitHub Actions workflow. & exit /b 1)
