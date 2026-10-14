@echo off
setlocal EnableExtensions

REM ================================================================
REM SNetS-QKD - Stage 6
REM Native SNetS simulation-folder integration of QKD configuration
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

if not exist "target\classes\network\Pair.class" (
    echo.
    echo ERRO: target\classes do SNetS nao foi encontrado.
    echo Execute esta etapa dentro do projeto SNetS completo.
    pause
    exit /b 1
)

if not exist "simulations\SNetS-SDM-SBRC26.jar" (
    echo.
    echo ERRO: simulations\SNetS-SDM-SBRC26.jar nao foi encontrado.
    echo O JAR original e usado apenas para disponibilizar as dependencias do projeto, como Gson.
    pause
    exit /b 1
)

echo ================================================================
echo SNetS-QKD - Etapa 6
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_CMD%"
echo   java  = "%JAVA_CMD%"
echo.
"%JAVAC_CMD%" -version
"%JAVA_CMD%" -version

echo.
echo Compilando integracao nativa da configuracao QKD ao SNetS...

if not exist "target\qkd-stage6" mkdir "target\qkd-stage6"

"%JAVAC_CMD%" -source 8 -target 8 -encoding UTF-8 ^
    -cp "target\classes;simulations\SNetS-SDM-SBRC26.jar" ^
    -d "target\qkd-stage6" ^
    "src\main\java\qkd\QuantumKeyDemand.java" ^
    "src\main\java\qkd\QuantumParameters.java" ^
    "src\main\java\qkd\QuantumRequestPolicy.java" ^
    "src\main\java\request\RequestForConnection.java" ^
    "src\main\java\network\RequestGenerator.java" ^
    "src\main\java\simulationControl\parsers\QKDConfig.java" ^
    "src\main\java\simulationControl\parsers\SimulationRequest.java" ^
    "src\main\java\simulationControl\SimulationFileManager.java" ^
    "src\main\java\network\Mesh.java" ^
    "src\main\java\simulationControl\SimulationManagement.java" ^
    "src\main\java\qkd\QuantumFullSNetSIntegrationVerification.java"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando Etapa 6...
echo.

REM target\qkd-stage6 comes first so the modified classes override the
REM original compiled SNetS versions only during this verification.
"%JAVA_CMD%" -cp "target\qkd-stage6;target\classes;simulations\SNetS-SDM-SBRC26.jar" ^
    qkd.QuantumFullSNetSIntegrationVerification ^
    "simulations\QKD_sims\Stage6_FullSNetSIntegration" ^
    "SNetS_QKD_ReferenceCase"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Stage 6 failed.
    pause
    exit /b 1
)

echo.
echo Stage 6 finished successfully.
echo.
echo Resultados gerados em:
echo simulations\QKD_sims\Stage6_FullSNetSIntegration\SNetS_QKD_ReferenceCase\stage6_results
pause
endlocal
