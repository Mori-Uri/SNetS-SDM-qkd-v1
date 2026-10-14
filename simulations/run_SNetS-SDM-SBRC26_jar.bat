@echo off
for /D %%D in (*) do (
    java -jar SNetS-SDM-SBRC26.jar "%%D"
)
pause
