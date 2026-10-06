@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo VITC Backend - verified Windows build
echo ==============================================
echo.

where java >nul 2>nul || (
  echo ERROR: Java was not found. Install JDK 21 and add it to PATH.
  pause
  exit /b 1
)

where mvn >nul 2>nul || (
  echo ERROR: Maven was not found. Install Maven and add it to PATH.
  pause
  exit /b 1
)

java -version 2>&1 | findstr /R /C:"version \"21\." >nul || (
  echo ERROR: This project requires Java 21.
  java -version
  pause
  exit /b 1
)

echo Building and running all tests...
echo NOTE: This uses package without clean to avoid Windows target-folder locks.
call mvn package
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Review the Maven error shown above.
  pause
  exit /b 1
)

echo.
echo BUILD SUCCESSFUL: target\vitc-backend-1.0.0.jar
pause
exit /b 0