@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%ROOT%..\..\..\simulations\SNetS-SDM-QKD.jar"
if not exist "%JAR%" (
  echo ERRO: JAR QKD nao encontrado: %JAR%
  echo Gere/use simulations\SNetS-SDM-QKD.jar ou passe o caminho como primeiro argumento.
  pause
  exit /b 1
)
set "OUT=%ROOT%physical_results"
if not exist "%OUT%" mkdir "%OUT%"
java -cp "%JAR%" qkd.reference.QKDReferencePhysicalMain "%OUT%"
if errorlevel 1 exit /b 1
python "%ROOT%scripts\plot_physical_reference.py" "%OUT%"
if errorlevel 1 (
  echo AVISO: CSVs fisicos foram gerados, mas os graficos nao.
)
echo.
echo Resultados fisicos em: %OUT%
pause
