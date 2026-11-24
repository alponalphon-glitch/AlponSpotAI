@echo off
setlocal
java -version 2>&1 | findstr /C:"17." >nul
if errorlevel 1 (
  echo JDK 17 is required. Set JAVA_HOME to JDK 17.
  exit /b 1
)
gradle --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease
if errorlevel 1 exit /b %errorlevel%
echo Build completed. APK files are in app\build\outputs\apk\
