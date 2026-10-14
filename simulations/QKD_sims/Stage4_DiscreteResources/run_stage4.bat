@echo off
setlocal EnableExtensions

REM ================================================================
REM SNetS-QKD - Stage 4
REM Key demand + QTND segmentation + discrete Q-TRx accounting
REM ================================================================

cd /d "%~dp0\..\..\.."

set "JAVAC_CMD="
set "JAVA_CMD="

where javac >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    for /f "delims=" %%I in ('where javac') do if not defined JAVAC_CMD set "JAVAC_CMD=%%I"
)

where java >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    for /f "delims=" %%I in ('where java') do if not defined JAVA_CMD set "JAVA_CMD=%%I"
)

if not defined JAVAC_CMD if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
if not defined JAVA_CMD if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

if not defined JAVAC_CMD (
    echo.
    echo ERRO: compilador javac nao encontrado.
    echo Verifique se o JDK esta instalado e se JAVA_HOME/PATH estao configurados.
    pause
    exit /b 1
)

if not defined JAVA_CMD (
    echo.
    echo ERRO: executavel java nao encontrado.
    pause
    exit /b 1
)

echo ================================================================
echo SNetS-QKD - Etapa 4
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_CMD%"
echo   java  = "%JAVA_CMD%"
echo.
"%JAVAC_CMD%" -version
"%JAVA_CMD%" -version

echo.
echo Compilando planejamento discreto de recursos QKD...

if not exist "target\qkd-stage4" mkdir "target\qkd-stage4"

"%JAVAC_CMD%" -source 8 -target 8 -encoding UTF-8 -d "target\qkd-stage4" ^
    "src\main\java\qkd\QuantumParameters.java" ^
    "src\main\java\qkd\QuantumPhysicalLayer.java" ^
    "src\main\java\qkd\QuantumKeyDemand.java" ^
    "src\main\java\qkd\QuantumSegment.java" ^
    "src\main\java\qkd\QuantumResourcePlan.java" ^
    "src\main\java\qkd\QuantumResourcePlanner.java" ^
    "src\main\java\qkd\QuantumResourcePlanningParameters.java" ^
    "src\main\java\qkd\QuantumResourcePlanningVerification.java"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando Etapa 4...
echo.

"%JAVA_CMD%" -cp "target\qkd-stage4" qkd.QuantumResourcePlanningVerification ^
    "simulations\QKD_sims\Stage4_DiscreteResources\qkd_resource_planning.properties"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Stage 4 failed.
    pause
    exit /b 1
)

echo.
echo Stage 4 finished successfully.
echo.
echo Resultados gerados em:
echo simulations\QKD_sims\Stage4_DiscreteResources\results
pause
endlocal
