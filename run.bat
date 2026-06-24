@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Elite Smart Trading Bot - Setup ^& Run
echo ============================================
echo.

set JAVAFX_VERSION=21.0.5
set JAVAFX_DIR=%~dp0javafx-sdk
set JAVAFX_ZIP=%~dp0javafx-sdk.zip
set JAVAFX_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VERSION%/openjfx-%JAVAFX_VERSION%_windows-x64_bin-sdk.zip

REM Gson library
set GSON_VERSION=2.10.1
set LIB_DIR=%~dp0lib
set GSON_JAR=%LIB_DIR%\gson-%GSON_VERSION%.jar
set GSON_URL=https://repo1.maven.org/maven2/com/google/code/gson/gson/%GSON_VERSION%/gson-%GSON_VERSION%.jar

REM Check if Java is available
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install Java 17 or higher.
    goto end
)

REM Check if JavaFX SDK exists
if not exist "%JAVAFX_DIR%\lib" (
    echo JavaFX SDK not found. Downloading...
    echo.
    
    powershell -Command "Write-Host 'Downloading JavaFX %JAVAFX_VERSION%...' -ForegroundColor Cyan"
    powershell -Command "Invoke-WebRequest -Uri '%JAVAFX_URL%' -OutFile '%JAVAFX_ZIP%'"
    
    if not exist "%JAVAFX_ZIP%" (
        echo ERROR: Failed to download JavaFX SDK.
        echo Please download manually from: https://gluonhq.com/products/javafx/
        goto end
    )
    
    echo Extracting JavaFX SDK...
    powershell -Command "Expand-Archive -Path '%JAVAFX_ZIP%' -DestinationPath '%~dp0' -Force"
    
    REM Rename extracted folder
    for /d %%i in ("%~dp0javafx-sdk-*") do (
        if exist "%JAVAFX_DIR%" rmdir /s /q "%JAVAFX_DIR%"
        ren "%%i" javafx-sdk
    )
    
    del "%JAVAFX_ZIP%" 2>nul
    
    echo JavaFX SDK installed successfully!
    echo.
)

REM Check if Gson exists
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"
if not exist "%GSON_JAR%" (
    echo Downloading Gson library...
    powershell -Command "Invoke-WebRequest -Uri '%GSON_URL%' -OutFile '%GSON_JAR%'"
    
    if not exist "%GSON_JAR%" (
        echo ERROR: Failed to download Gson library.
        goto end
    )
    
    echo Gson library installed successfully!
    echo.
)

REM Create output directory
if not exist "%~dp0build\classes" mkdir "%~dp0build\classes"

REM Compile
echo Compiling source files...
set JAVAFX_MODS=--module-path "%JAVAFX_DIR%\lib" --add-modules javafx.controls,javafx.fxml
set CLASSPATH="%GSON_JAR%"

set SRC_FILES=
for /r "%~dp0src\main\java" %%f in (*.java) do set SRC_FILES=!SRC_FILES! "%%f"

javac %JAVAFX_MODS% -cp %CLASSPATH% -d "%~dp0build\classes" %SRC_FILES%

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Compilation failed!
    goto end
)

echo Compilation successful!
echo.

REM Run
echo Starting Elite Smart Trading Bot...
echo.

java %JAVAFX_MODS% -cp "%~dp0build\classes;%GSON_JAR%" com.elitebot.Main

:end
endlocal
pause

