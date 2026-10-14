@echo off
setlocal EnableExtensions

REM ================================================================
REM SNetS-QKD - Stage 5
REM QKD key policy and SKR demand attached to SNetS requests
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

if not exist "target\classes\util\RandGenerator.class" (
    echo.
    echo ERRO: target\classes do SNetS nao foi encontrado.
    echo Esta etapa usa as classes ja compiladas do simulador como dependencias.
    echo Verifique se voce esta executando o BAT dentro do projeto SNetS completo.
    pause
    exit /b 1
)

echo ================================================================
echo SNetS-QKD - Etapa 5
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_CMD%"
echo   java  = "%JAVA_CMD%"
echo.
"%JAVAC_CMD%" -version
"%JAVA_CMD%" -version

echo.
echo Compilando integracao QKD no modelo de requisicao do SNetS...

if not exist "target\qkd-stage5" mkdir "target\qkd-stage5"

"%JAVAC_CMD%" -source 8 -target 8 -encoding UTF-8 -cp "target\classes" -d "target\qkd-stage5" ^
    "src\main\java\qkd\QuantumKeyDemand.java" ^
    "src\main\java\qkd\QuantumRequestPolicy.java" ^
    "src\main\java\request\RequestForConnection.java" ^
    "src\main\java\network\RequestGenerator.java" ^
    "src\main\java\qkd\QuantumRequestGenerationVerification.java"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando Etapa 5...
echo.

REM target\qkd-stage5 comes first so the modified RequestForConnection and
REM RequestGenerator classes override the original compiled versions only for
REM this verification execution.
"%JAVA_CMD%" -cp "target\qkd-stage5;target\classes" qkd.QuantumRequestGenerationVerification ^
    "simulations\QKD_sims\Stage5_RequestIntegration\qkd_request_generation.properties"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Stage 5 failed.
    pause
    exit /b 1
)

echo.
echo Stage 5 finished successfully.
echo.
echo Resultados gerados em:
echo simulations\QKD_sims\Stage5_RequestIntegration\results
pause
endlocal
