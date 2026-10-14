# Addon QKD — Parte 23: cenário de estudo da qualificação

Objetivo: permitir simular no SNetS o cenário dos slides 26 a 37 da apresentação de qualificação
("Requisição Protegida por QKD"). A Parte 23 também acrescenta, no launcher, o modo de execução
Clássica/QKD.

Componentes clássicos protegidos (CLASSICAL_BASE_SHA256.txt): nenhum foi alterado.

## Cobertura dos slides

| Slide | Elemento | Situação antes | Parte 23 |
|---|---|---|---|
| 24–25, 28 | QTND, nós confiáveis, Q-TRx | já existia (QKDTopologyPlanner) | planejamento exibido no launcher; nº de nós confiáveis no QKDMetrics |
| 26, 36 | Admissão conjunta e proteção das sessões ativas | já existia | sem mudança |
| 27, 33–35 | Demanda de chave por requisição (ex.: 2 Mbit/s) | só fração global e classes de demanda | `keyRateMbps` e `qkdProtectedProbability` em cada gerador do `traffic` |
| 29 | Rota QKD alternativa | QKD sempre seguia a rota clássica | `network.qkd.alternativeRoutes` = k rotas alternativas (Yen) |
| 30 | QCh no núcleo 1 e PICh num núcleo clássico | PICh não modelado no SDM | `network.qkd.pichSlots` = slots do PICh por sessão |
| 31–35 | Mesmo slot em todos os trechos e SKR por trecho ≥ demanda | QCh independente por enlace/trecho | `network.qkd.qchGranularity = "route"` |

## Novas opções no arquivo `network` (bloco `qkd`)

```json
"qkd": {
  "enabled": true, "qtndKm": 30.0, "quantumCoreId": 1,
  "qchGranularity": "route",
  "qchSlotPolicy": "firstfit",
  "wavelengthPolicy": "grid",
  "alternativeRoutes": 2,
  "pichSlots": 1
}
```

- **`qchGranularity`**: o valor `"route"` dá um QCh dedicado por sessão, com os mesmos slots contíguos do
  núcleo QKD em todos os enlaces (e trechos) da rota QKD.
  - As faixas livres em todos os enlaces são testadas em ordem. Para cada faixa, a SKR de todos os trechos
    é calculada no comprimento de onda daquela faixa, com a interferência clássica atual mais a do candidato
    pendente. É alocada a primeira faixa cuja SKR mínima atende à demanda de chave.
  - Com `wavelengthPolicy = "grid"`, o comprimento de onda do slot vem da grade do núcleo, centrada no mínimo
    de atenuação. Com `"fixed"`, todos os slots usam λ de mínima atenuação; aí só o crosstalk diferencia os
    slots.
  - Os modos `link` e `span` continuam existindo e são os usados na reprodução do artigo-base.
- **`qchSlotPolicy`**:
  - `firstfit`: menor índice de slot primeiro, como no slide 31.
  - `lowestloss`: começa pelo slot mais próximo de 1550 nm.
- **`alternativeRoutes`** (padrão 0): se o QCh não puder ser provisionado na rota do circuito clássico, o
  simulador tenta as k menores rotas alternativas por comprimento (algoritmo de Yen, determinístico). O
  circuito clássico continua na rota escolhida pelo RMSCA.
- **`pichSlots`** (padrão 0): o PICh de cada sessão ocupa os mesmos slots de um núcleo clássico em todos os
  enlaces da rota QKD.
  - A escolha é first-fit: o núcleo clássico de menor índice e, nele, o slot de menor índice.
  - O espectro do candidato clássico pendente é tratado como ocupado.
  - O PICh ocupa espectro, mas a potência dele não entra no cálculo do crosstalk.

## Novas opções no arquivo `traffic` (por gerador, opcionais)

```json
{ "source": "2", "destination": "3", "bitRate": 1.0E11, "arrivalRate": 2.0, "holdRate": 1.0,
  "arrivalRateIncrease": 1.0, "qkdProtectedProbability": 0.2, "keyRateMbps": 2.0 }
```

- `qkdProtectedProbability`: fração das requisições do gerador protegidas por QKD. Quando ausente, vale
  `simulation.qkdProtectedRequestProbability`.
- `keyRateMbps`: demanda de chave fixa. Quando ausente, a demanda é sorteada de `simulation.qkdKeyDemand`.
  O sorteio da política continua sendo consumido, para manter as sequências aleatórias alinhadas.

## Nova métrica: `QKDMetrics.csv`

- Usa o formato padrão do SNetS (`Metrics,LoadPoint,src,dest, ,rep0..`). Por isso pode ser lida pela aba
  Resultados e pelo Estúdio de gráficos.
- É ativada por `activeMetrics.QKDMetrics` (padrão `true`) e só é gravada com QKD habilitado.
- Linhas:
  - bloqueio QKD (total e por causa: recursos clássicos, espectro do QCh, SKR, PICh, outros);
  - aceitação e fração de requisições protegidas;
  - candidatos clássicos rejeitados para proteger sessões QKD;
  - fração de sessões em rota alternativa;
  - SKR e QBER na admissão, demanda média de chave;
  - QChs, pares Q-TRx e sessões ativos (média PASTA e pico);
  - Q-TRx, saltos e km médios por sessão;
  - nós confiáveis planejados e QTND;
  - bloqueio QKD por par.
- O `QKDStatistics.csv` (formato antigo, uma linha por replicação) continua sendo gravado.

## Verificação

- `qkd.verification.QKDQualificationScenarioVerification` refaz o cenário dos slides e confere:
  - topologia de 5 nós, MCF de 7 núcleos, núcleo 1 reservado e QTND de 30 km;
  - os trechos e os 13 nós confiáveis;
  - o QCh no mesmo slot em todos os trechos, com SKR ≥ 2 Mbit/s;
  - o PICh no núcleo 0;
  - a rota alternativa quando 1→3 não tem slot QKD livre;
  - as causas de bloqueio `[SPECTRUM]` e `[SKR]` e a liberação completa;
  - os campos QKD do tráfego;
  - uma simulação completa do SNetS que grava `QKDMetrics.csv`.
- Os 21 verificadores passam, inclusive a reprodução nativa do artigo-base, que manteve os mesmos resultados.

## Caso-exemplo

`simulations/QKD_qualificacao/Exemplo_5nos_QTND30`:

- 20 % de requisições protegidas e 2 Mbit/s de chave;
- `qabne`, QoT/XT ligados;
- 5000 requisições, 3 pontos de carga e 3 replicações.

Na nuvem (2 núcleos) leva cerca de 2 min 40 s. O bloqueio clássico ficou entre 1 % e 14 % e o bloqueio QKD
entre 1 % e 14 %, todo por recursos clássicos nesta carga.

## Observações para a análise

- Com a SKR testada slot a slot, as sessões QKD tendem a evitar os slots sobrepostos por circuitos
  clássicos dos núcleos vizinhos. Por isso a QBER de admissão fica próxima de `e_d`. O bloqueio por SKR só
  aparece quando não há faixa livre sem interferência, ou quando a demanda de chave é alta.
- Com `wavelengthPolicy = "grid"` e `firstfit`, o slot 1 fica na ponta da grade (≈1566 nm), onde a SKR é
  menor. `qchSlotPolicy = "lowestloss"` prioriza os slots perto de 1550 nm.
- O custo por requisição cresce com o número de sessões QKD ativas: cada candidato clássico é avaliado contra
  todas elas. Cargas com centenas de sessões simultâneas levam minutos por ponto de carga.
