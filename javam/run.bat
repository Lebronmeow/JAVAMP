@echo off
echo ============================================
echo Carbon Footprint Logger - Startup Script
echo ============================================
echo.

echo Checking Maven installation...
call mvn --version
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven and try again
    pause
    exit /b 1
)

echo.
echo Building and running the application...
echo.

call mvn clean javafx:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to run the application
    echo Please check the error messages above
    pause
    exit /b 1
)

pause
