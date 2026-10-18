# Parte 29: onde no espectro as requisições são bloqueadas

## Simulador: novo arquivo `<caso>_SlotBlocking.csv`

- **O que registra.** Para cada requisição, olha o candidato:
  - quando a requisição é aceita, o candidato é o circuito aceito;
  - quando é bloqueada, é o candidato que foi bloqueado.

  Os slots da faixa desse candidato (sem bandas de guarda), no núcleo dele, contam uma tentativa em cada enlace da rota.
  Se a requisição foi bloqueada, esses slots contam também um bloqueio da causa, a mesma do `BlockingDetails.csv`.
- **Formato.** `Metrics,LoadPoint,Link,Core,Replication,Cause,1..S`, só com contagens inteiras. Linhas só com zeros
  ficam de fora. As métricas são:
  - `Slot attempts`, `Slot blocked` e `Slot blocked by cause`, com uma coluna por slot;
  - `Requests on link and core`, `Blocked requests on link and core` e `... by cause`, com um valor só: cada
    requisição conta uma vez por enlace e núcleo, e não uma vez por slot;
  - `Blocked requests without spectrum band`: bloqueios sem candidato com faixa (sem slots livres, sem rota), que não
    têm lugar no espectro.
- **Como ligar e desligar.** A métrica `activeMetrics.SlotBlocking` vem ligada por padrão e só grava o arquivo se
  `BlockingProbability` também estiver ligada.
- **Classes alteradas:**
  - `qkd.metrics.BlockingDetailRecorder`: contadores e `toSlotCsv`;
  - `SimulationManagement.buildSlotBlockingCsv`;
  - `SimulationRequest`;
  - `SimulationFileManager`;
  - `SimulationConfig.Metrics`.
- **O que não mudou.** Nenhuma decisão de alocação, e nenhum dos CSVs que já existiam. As 21 verificações passam.
- **Cuidado ao comparar.** A fração por slot pesa cada requisição pelo número de slots e de enlaces que ela usa.
  Requisições longas e largas são bloqueadas mais vezes, então essa fração fica acima da probabilidade de bloqueio por
  requisição.

## Launcher 1.10.0

- **Modelos novos em Mapas de calor:**
  - bloqueadas ÷ tentativas em cada slot;
  - causa do bloqueio em cada slot (uma cor por causa);
  - só os bloqueios por QoTO.
- **Colunas à direita do mapa.** "Requisições bloqueadas/feitas" e a porcentagem, por núcleo de cada enlace.
- **Opções:**
  - o que cada slot mostra: fração, contagem ou causa;
  - a causa;
  - mostrar só os núcleos com bloqueio;
  - a cor dos slots sem tentativa.
- **Dica.** Passe o mouse sobre um slot para ver as tentativas, os bloqueios e as causas.
