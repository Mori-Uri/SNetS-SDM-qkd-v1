@echo off
setlocal EnableExtensions

REM ================================================================
REM SNetS-QKD - Stage 9
REM Physical-link-aware QTND segmentation and cross-route TDM sharing
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
    pause
    exit /b 1
)
if not defined JAVA_CMD (
    echo.
    echo ERRO: executavel java nao encontrado.
    pause
    exit /b 1
)
if not exist "target\classes\util\RandGenerator.class" (
    echo.
    echo ERRO: target\classes do SNetS nao foi encontrado.
    pause
    exit /b 1
)
if not exist "simulations\SNetS-SDM-SBRC26.jar" (
    echo.
    echo ERRO: simulations\SNetS-SDM-SBRC26.jar nao foi encontrado.
    pause
    exit /b 1
)

echo ================================================================
echo SNetS-QKD - Etapa 9
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_CMD%"
echo   java  = "%JAVA_CMD%"
echo.
"%JAVAC_CMD%" -version
"%JAVA_CMD%" -version

echo.
echo Compilando segmentacao QKD orientada pelos enlaces fisicos do SNetS...

if exist "target\qkd-stage9" rmdir /s /q "target\qkd-stage9"
mkdir "target\qkd-stage9"

"%JAVAC_CMD%" -source 8 -target 8 -encoding UTF-8 -cp "target\classes;simulations\SNetS-SDM-SBRC26.jar" -d "target\qkd-stage9" ^
    "src\main\java\qkd\QuantumParameters.java" ^
    "src\main\java\qkd\QuantumPhysicalLayer.java" ^
    "src\main\java\qkd\QuantumKeyDemand.java" ^
    "src\main\java\qkd\QuantumSegment.java" ^
    "src\main\java\qkd\QuantumResourcePlan.java" ^
    "src\main\java\qkd\QuantumResourcePlanningParameters.java" ^
    "src\main\java\qkd\QuantumResourcePlanner.java" ^
    "src\main\java\qkd\QuantumRequestPolicy.java" ^
    "src\main\java\qkd\QuantumTdmChannel.java" ^
    "src\main\java\qkd\QuantumTdmSegmentAllocation.java" ^
    "src\main\java\qkd\QuantumTdmAllocation.java" ^
    "src\main\java\qkd\QuantumTdmChannelSnapshot.java" ^
    "src\main\java\qkd\QuantumTdmResourceManager.java" ^
    "src\main\java\request\RequestForConnection.java" ^
    "src\main\java\network\RequestGenerator.java" ^
    "src\main\java\simulationControl\parsers\QKDConfig.java" ^
    "src\main\java\simulationControl\parsers\SimulationRequest.java" ^
    "src\main\java\simulationControl\SimulationFileManager.java" ^
    "src\main\java\network\Mesh.java" ^
    "src\main\java\simulationControl\SimulationManagement.java" ^
    "src\main\java\network\ControlPlane.java" ^
    "src\main\java\qkd\QuantumLinkAwarePlanningVerification.java"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando Etapa 9...
echo.

"%JAVA_CMD%" -cp "target\qkd-stage9;target\classes;simulations\SNetS-SDM-SBRC26.jar" qkd.QuantumLinkAwarePlanningVerification ^
    "simulations\QKD_sims\Stage9_LinkAwarePlanning\SNetS_QKD_LinkAwareCase"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Stage 9 verification failed.
    pause
    exit /b 1
)

echo.
echo Stage 9 finished successfully.
echo.
echo Resultados gerados em:
echo simulations\QKD_sims\Stage9_LinkAwarePlanning\SNetS_QKD_LinkAwareCase\stage9_results
pause
endlocal
