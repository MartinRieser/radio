@echo off
chcp 65001 >nul
echo ========================================================
echo  PulseCast: APK bauen ^& per USB auf Telefon installieren
echo ========================================================
echo.
echo 1. Prüfe verbundenes USB-Gerät...
adb devices
echo.
echo 2. Baue APK mit neuesten Trainingsdaten...
call .\gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] Build fehlgeschlagen!
    pause
    exit /b 1
)
copy app\build\outputs\apk\debug\app-debug.apk PulseCast-debug.apk >nul
echo.
echo 3. Installiere PulseCast-debug.apk auf Telefon...
adb install -r PulseCast-debug.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  [ERFOLG] PulseCast wurde erfolgreich installiert!
    echo ========================================================
) else (
    echo.
    echo [HINWEIS] Ist das Telefon per USB verbunden und USB-Debugging aktiv?
)
echo.
pause
