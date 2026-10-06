@echo off
setlocal
cd /d "%~dp0"

if not exist "target\vitc-backend-1.0.0.jar" (
  echo The backend JAR has not been built yet.
  echo Run BUILD-WINDOWS.cmd first.
  pause
  exit /b 1
)

echo Starting VITC Backend at http://localhost:8080
echo MySQL must be running with database access configured in application.properties.
echo Press Ctrl+C to stop the backend.
echo.
java -jar "target\vitc-backend-1.0.0.jar"
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
  echo.
  echo Backend stopped with error code %EXIT_CODE%.
  pause
)
exit /b %EXIT_CODE%