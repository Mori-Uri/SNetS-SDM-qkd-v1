@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%SNetS-SDM-QKD.jar"
set "SCENARIO=%~1"
if "%SCENARIO%"=="" set "SCENARIO=NSFNet_QKD_test"
if not exist "%JAR%" (
  echo ERRO: %JAR% nao encontrado.
  pause
  exit /b 1
)
if not exist "%ROOT%%SCENARIO%\simulation" (
  echo ERRO: simulacao "%SCENARIO%" nao encontrada em %ROOT%
  echo Uso: run_SNetS-SDM-QKD_jar.bat NSFNet_C3_QKD_XT
  pause
  exit /b 1
)
pushd "%ROOT%"
java -jar "%JAR%" "%SCENARIO%"
set "RC=%ERRORLEVEL%"
popd
if not "%RC%"=="0" (
  echo ERRO: simulacao terminou com codigo %RC%.
)
pause
exit /b %RC%
