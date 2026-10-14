@echo off
setlocal EnableExtensions

rem ================================================================
rem SNetS-QKD - Etapa 1: verificacao isolada da camada fisica QKD
rem Local esperado:
rem simulations\QKD_sims\Stage1_PhysicalLayer\run_stage1.bat
rem ================================================================

rem Vai da pasta Stage1_PhysicalLayer ate a raiz do projeto SNetS.
cd /d "%~dp0\..\..\.."

set "JAVAC_EXE="
set "JAVA_EXE="

rem 1) Tenta localizar o JDK pelo PATH.
for /f "delims=" %%I in ('where javac 2^>nul') do (
    if not defined JAVAC_EXE set "JAVAC_EXE=%%I"
)

if defined JAVAC_EXE (
    for %%I in ("%JAVAC_EXE%") do set "JAVA_EXE=%%~dpIjava.exe"
)

rem 2) Se nao encontrou, tenta JAVA_HOME.
if not defined JAVAC_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" (
            set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
            set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        )
    )
)

rem 3) Tenta alguns locais comuns de instalacao no Windows.
if not defined JAVAC_EXE (
    for /d %%D in ("C:\Program Files\Java\jdk*") do (
        if exist "%%~fD\bin\javac.exe" (
            set "JAVAC_EXE=%%~fD\bin\javac.exe"
            set "JAVA_EXE=%%~fD\bin\java.exe"
        )
    )
)

if not defined JAVAC_EXE (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk*") do (
        if exist "%%~fD\bin\javac.exe" (
            set "JAVAC_EXE=%%~fD\bin\javac.exe"
            set "JAVA_EXE=%%~fD\bin\java.exe"
        )
    )
)

if not defined JAVAC_EXE (
    for /d %%D in ("C:\Program Files\Microsoft\jdk-*") do (
        if exist "%%~fD\bin\javac.exe" (
            set "JAVAC_EXE=%%~fD\bin\javac.exe"
            set "JAVA_EXE=%%~fD\bin\java.exe"
        )
    )
)

if not defined JAVAC_EXE (
    for /d %%D in ("C:\Program Files\Zulu\zulu*") do (
        if exist "%%~fD\bin\javac.exe" (
            set "JAVAC_EXE=%%~fD\bin\javac.exe"
            set "JAVA_EXE=%%~fD\bin\java.exe"
        )
    )
)

rem 4) Se ainda nao encontrou javac, nao ha JDK acessivel ao script.
if not defined JAVAC_EXE (
    echo.
    echo ================================================================
    echo JDK NAO ENCONTRADO
    echo ================================================================
    echo.
    echo O comando javac faz parte do JDK, nao apenas do Java/JRE.
    echo Instale um JDK ou configure JAVA_HOME/PATH para a pasta do JDK.
    echo.
    echo Depois, abra um novo Prompt de Comando e teste:
    echo     java -version
    echo     javac -version
    echo     echo %%JAVA_HOME%%
    echo.
    echo O SNetS esta configurado para compilar codigo-fonte Java 8
    echo ^(-source 1.8 -target 1.8^). Um JDK moderno, como JDK 17,
    echo consegue compilar essa etapa mantendo compatibilidade com Java 8.
    echo ================================================================
    echo.
    pause
    exit /b 1
)

if not exist "%JAVA_EXE%" (
    echo.
    echo java.exe nao foi encontrado ao lado de javac.exe.
    echo JDK detectado: "%JAVAC_EXE%"
    echo.
    pause
    exit /b 1
)

echo ================================================================
echo SNetS-QKD - Etapa 1
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_EXE%"
echo   java  = "%JAVA_EXE%"
echo.
"%JAVAC_EXE%" -version
"%JAVA_EXE%" -version

echo.
echo Compilando a camada fisica QKD...

if not exist "target\qkd-stage1" mkdir "target\qkd-stage1"

"%JAVAC_EXE%" -encoding ISO-8859-1 -source 1.8 -target 1.8 ^
  -d "target\qkd-stage1" ^
  "src\main\java\qkd\QuantumParameters.java" ^
  "src\main\java\qkd\QuantumPhysicalLayer.java" ^
  "src\main\java\qkd\QuantumPhysicalLayerVerification.java"

if errorlevel 1 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando verificacao...
echo.

"%JAVA_EXE%" -cp "target\qkd-stage1" qkd.QuantumPhysicalLayerVerification ^
  "simulations\QKD_sims\Stage1_PhysicalLayer\qkd_parameters.properties"

set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo Stage 1 finished successfully.
) else (
    echo Stage 1 finished with error code %EXIT_CODE%.
)

echo.
pause
exit /b %EXIT_CODE%
