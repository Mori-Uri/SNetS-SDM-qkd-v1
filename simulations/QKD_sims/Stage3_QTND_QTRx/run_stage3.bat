@echo off
setlocal EnableExtensions

rem ================================================================
rem SNetS-QKD - Etapa 3: QTND x wavelength / SKR por Q-TRx
rem Local esperado:
rem simulations\QKD_sims\Stage3_QTND_QTRx\run_stage3.bat
rem ================================================================

rem Vai da pasta Stage3_QTND_QTRx ate a raiz do projeto SNetS.
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

rem 3) Tenta locais comuns no Windows.
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

if not defined JAVAC_EXE (
    echo.
    echo ================================================================
    echo JDK NAO ENCONTRADO
    echo ================================================================
    echo.
    echo Configure JAVA_HOME/PATH ou instale um JDK.
    echo Depois teste em um novo Prompt de Comando:
    echo     java -version
    echo     javac -version
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
echo SNetS-QKD - Etapa 3
echo ================================================================
echo JDK detectado:
echo   javac = "%JAVAC_EXE%"
echo   java  = "%JAVA_EXE%"
echo.
"%JAVAC_EXE%" -version
"%JAVA_EXE%" -version

echo.
echo Compilando a analise QTND / Q-TRx...

if not exist "target\qkd-stage3" mkdir "target\qkd-stage3"

"%JAVAC_EXE%" -encoding ISO-8859-1 -source 1.8 -target 1.8 ^
  -d "target\qkd-stage3" ^
  "src\main\java\qkd\QuantumParameters.java" ^
  "src\main\java\qkd\QuantumPhysicalLayer.java" ^
  "src\main\java\qkd\QuantumQtndParameters.java" ^
  "src\main\java\qkd\QuantumQtndQtrxSweep.java"

if errorlevel 1 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida. Executando Etapa 3...
echo.

"%JAVA_EXE%" -cp "target\qkd-stage3" qkd.QuantumQtndQtrxSweep ^
  "simulations\QKD_sims\Stage3_QTND_QTRx\qkd_qtnd.properties"

set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo Stage 3 finished successfully.
    echo.
    echo Resultados gerados em:
    echo simulations\QKD_sims\Stage3_QTND_QTRx\results
) else (
    echo Stage 3 finished with error code %EXIT_CODE%.
)

echo.
pause
exit /b %EXIT_CODE%
