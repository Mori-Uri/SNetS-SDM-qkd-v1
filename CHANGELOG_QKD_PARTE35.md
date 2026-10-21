# Parte 35: estudos Test5/Test6, variações num só lugar, gráficos de custo

## Simulador

- As mensagens "Core not selected." que vinham do código QKD foram retiradas. O núcleo de um candidato sem núcleo agora é
  lido sem imprimir nada, em `QKDProvisioningService`, `QKDPhysicalLayer` e `QKDExistingSessionImpactEvaluator`.
  - A mesma mensagem ainda aparece quando o próprio algoritmo `spcsav2` não acha núcleo. Os algoritmos não foram mexidos.
  - Os resultados não mudam: idênticos byte a byte, e as 21 verificações passam.

## Casos

- `simulations/QKD_Test5`: topologia Metropolitana, 94 casos. `simulations/QKD_Test6`: NSFNet com QTND de 25, 50, 75 e
  100 km, 121 casos.
- Os dois foram gerados por `launcher/.work/gen/make_tests.py`. Os nomes e as cargas estão em `LEIAME_CASOS.md`, dentro
  de cada pasta.
- A configuração anterior do Test5 ficou em `launcher/.work/backup_test5`.

## Launcher 1.14.0

Ver o README do launcher.
