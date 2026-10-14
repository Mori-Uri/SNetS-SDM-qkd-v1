@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%ROOT%..\..\SNetS-SDM-QKD.jar"
if not exist "%JAR%" (
  echo ERRO: JAR QKD nao encontrado: %JAR%
  echo Gere/use simulations\SNetS-SDM-QKD.jar ou passe o caminho como primeiro argumento.
  pause
  exit /b 1
)
set "OUT=%ROOT%snets_results"
if not exist "%OUT%" mkdir "%OUT%"
java -cp "%JAR%" qkd.reference.QKDReferenceArticleMain "%ROOT%" "%OUT%"
if errorlevel 1 (
  echo ERRO na verificacao do cenario de referencia.
  pause
  exit /b 1
)
python "%ROOT%scripts\plot_snets_reference.py" "%OUT%" --reference-root "%ROOT%"
if errorlevel 1 (
  echo AVISO: resultados Java foram gerados, mas os graficos nao.
  echo Instale Python com matplotlib e pandas se quiser gerar as figuras.
)
echo.
echo Resultados em: %OUT%
pause
