@echo off
setlocal EnableDelayedExpansion
set "ROOT=%~dp0"
set "SIM=%ROOT%.."
set "GEN=%ROOT%generated_qtnd"
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%SIM%\SNetS-SDM-QKD.jar"
if not exist "%JAR%" (
  echo ERRO: JAR nao encontrado: %JAR%
  pause
  exit /b 1
)
if not exist "%GEN%" (
  echo ERRO: execute prepare_qtnd_sweep.py primeiro.
  pause
  exit /b 1
)
for /D %%D in ("%GEN%\*") do (
  echo.
  echo ============================================================
  echo Executando %%~nxD
  echo ============================================================
  java -jar "%JAR%" "%%D"
  if errorlevel 1 exit /b 1
)
python "%ROOT%analyze_qkd_campaign.py"
echo Sweep QTND concluido e analisado.
pause
