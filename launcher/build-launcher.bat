@echo off
REM Recompila o snets-launcher.jar (requer um JDK 8+ com javac e jar no PATH).
REM Nao depende do Maven nem do codigo do simulador.
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
javac -source 8 -target 8 -encoding UTF-8 -d build\classes src\launcher\*.java
if errorlevel 1 goto :fail
xcopy /e /i /q web build\classes\web >nul
xcopy /e /i /q templates build\classes\templates >nul
echo Main-Class: launcher.LauncherServer> build\manifest.txt
jar cfm snets-launcher.jar build\manifest.txt -C build\classes .
if errorlevel 1 goto :fail
rmdir /s /q build
echo OK: snets-launcher.jar gerado.
pause
exit /b 0
:fail
echo ERRO na compilacao.
pause
exit /b 1
