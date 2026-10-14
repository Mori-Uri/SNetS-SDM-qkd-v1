@echo off
setlocal
set "ROOT=%~dp0"
set "SIM=%ROOT%.."
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%SIM%\SNetS-SDM-QKD.jar"
if not exist "%JAR%" (
  echo ERRO: JAR nao encontrado: %JAR%
  pause
  exit /b 1
)
pushd "%SIM%"
for %%D in (NSFNet_C1_Classical7 NSFNet_C2_QKD_NoXT NSFNet_C3_QKD_XT) do (
  echo.
  echo ============================================================
  echo Executando %%D
  echo ============================================================
  java -jar "%JAR%" "%%D"
  if errorlevel 1 (
    popd
    echo ERRO durante %%D
    pause
    exit /b 1
  )
)
popd
python "%ROOT%analyze_qkd_campaign.py"
echo.
echo Cenarios base concluidos e analisados.
pause
