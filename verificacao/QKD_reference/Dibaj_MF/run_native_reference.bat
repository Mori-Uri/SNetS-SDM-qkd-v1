@echo off
setlocal
REM Artigo-base (Dibaj et al. 2024, cenario multifibra) executado pelo motor do SNetS.
REM Gera 30 casos SNetS em ..\Dibaj_MF_SNetS (3 topologias x QTND 5..50 km, 10 replicacoes),
REM executa todos e compara com a reconstrucao em Python e com a Fig. 7 digitalizada.
set "ROOT=%~dp0"
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%ROOT%..\..\..\simulations\SNetS-SDM-QKD.jar"
if not exist "%JAR%" (
  echo ERRO: JAR QKD nao encontrado: %JAR%
  pause
  exit /b 1
)
set "NATIVE=%ROOT%..\Dibaj_MF_SNetS"
java -cp "%JAR%" qkd.reference.QKDNativeReferenceMain all "%ROOT%." "%NATIVE%"
set "RC=%ERRORLEVEL%"
echo.
echo Resultados em: %NATIVE%\results
pause
exit /b %RC%
