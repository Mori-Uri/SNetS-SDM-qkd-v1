# Parte 25: detalhes do bloqueio (BlockingDetails.csv)

Toda simulação com a métrica BlockingProbability ativa (clássica ou QKD) grava agora `<caso>_BlockingDetails.csv`.
O novo arquivo só observa as decisões do plano de controle e não muda nenhuma delas. Os demais CSVs ficam
idênticos aos de antes; isso foi conferido com o mesmo caso rodado no jar anterior e no novo.

## Conteúdo

Layout SNetS: `Metrics,LoadPoint,Class,Core,Cause,Link, ,rep0..`.

- **Dimensões**
  - `Class`: `all` (todas as requisições), `classical` (não protegidas) ou `qkd` (protegidas). As duas últimas só
    aparecem quando há QKD.
  - `Cause`: as causas do SNetS (Fragmentation, QoTN, QoTO, Crosstalk, Crosstalk in other, Lack of
    transmitters/receivers), mais:
    - `Lack of free spectrum`: o "other" do SNetS quando algum enlace da rota não tem nenhum slot livre no núcleo do
      candidato (`isBlockingByLackOfFreeSpectrum`);
    - `Other`: o restante do "other" do SNetS;
    - causas QKD, para um candidato clássico válido recusado pelo addon: `QKD session protection` (protegeria mal
      uma sessão ativa), `QKD QCh spectrum`, `QKD SKR`, `QKD PICh` e `QKD other` (falha QKD de uma requisição
      protegida).
- **Métricas**
  - `Blocking probability` e `Requests` por classe.
  - `Blocking probability by cause` por classe. As causas somam o bloqueio da classe.
  - `Blocking probability per core`, `... per core by cause` (bloqueadas no núcleo ÷ requisições do núcleo, mesma
    regra do SNetS) e `Share of all requests blocked per core by cause` (÷ todas as requisições, que soma o bloqueio
    total).
  - `Link blocking probability` e `Link requests` por enlace direcional e classe, e `Link blocking probability by QKD
    causes`. Uma requisição conta em todos os enlaces da rota tentada.
- **Transiente:** é o mesmo dos CSVs clássicos (10 % das requisições).

## Arquivos

- **Novos**
  - `qkd/metrics/BlockingDetailRecorder.java`: contadores e CSV.
  - `qkd/metrics/ObservedClassicalControlPlane.java`: o `ControlPlane` clássico sem mudanças. Só chama o
    registrador depois de `handleRequisition`.
- **Alterados**
  - `QKDControlPlane`: guarda a causa QKD da última recusa (proteção, precommit ou fallback) e registra cada
    requisição.
  - `simulator/Simulation.java`: o caminho clássico transparente usa `ObservedClassicalControlPlane`.
  - `SimulationManagement`, `SimulationRequest` e `SimulationFileManager`: montam, guardam e leem o novo arquivo.
- **Inalterados:** `GPRMCSA.java`, `CSBASDM.java`, `ControlPlane.java` e o jar SBRC26.

## Conferências feitas

As conferências usaram qTest_Maluco, qTest_C e qTest (1500 E), com 4000 e 10000 requisições.

- `Blocking probability` (all) e `per core` são idênticos aos do BlockingProbability.csv.
- O "other" clássico é igual a falta de espectro + outros + causas QKD. Exemplo: 0,733 % = 0,644 % (QKD SKR) +
  0,089 % (proteção).
- `Blocking probability by cause` na classe qkd é igual ao QKDMetrics: a classe qkd somada às causas clássicas é
  igual ao "QKD blocking probability by classical resources".
