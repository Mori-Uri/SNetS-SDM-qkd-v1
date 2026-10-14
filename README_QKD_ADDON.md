# Addon QKD para o SNetS-SDM

Este projeto preserva o SNetS classico e adiciona uma camada QKD opcional.

## Regra arquitetural

- O codigo classico em `src/main/java/gprmcsa/` permanece independente de QKD.
- O algoritmo classico `csbasdm` continua resolvendo para `CSBASDM/ABNE` original.
- O algoritmo QKD `qabne` resolve para `qkd.algorithms.coreSpectrumAssignment.QABNE`.
- Cenarios classicos continuam podendo ser executados com o JAR original `simulations/SNetS-SDM-SBRC26.jar`.
- Cenarios QKD devem ser executados com `simulations/SNetS-SDM-QKD.jar`.

## Onde estao os cenarios

Todos os cenarios normais continuam dentro de `simulations/`:

- `NSFNet_C1_Classical7`: baseline classico, 7 nucleos, `csbasdm`.
- `NSFNet_C2_QKD_NoXT`: 6 nucleos classicos + 1 QKD, `qabne`, impacto XT->QKD desligado.
- `NSFNet_C3_QKD_XT`: 6 nucleos classicos + 1 QKD, `qabne`, impacto XT->QKD ligado.
- `NSFNet_QKD_test`: cenario de desenvolvimento QKD, `qabne`.

## Como executar um cenario QKD

Na pasta `simulations`:

```bat
run_SNetS-SDM-QKD_jar.bat NSFNet_C3_QKD_XT
```

ou diretamente:

```bat
java -jar SNetS-SDM-QKD.jar NSFNet_C3_QKD_XT
```

## Verificacao do artigo-base

Os dados, traces e scripts estao em:

`simulations/QKD_reference/Dibaj_MF/`

Eles geram as curvas de SKR, SKR/Q-TRx e Q-TRx x QTND para Small Core, Butterfly e Metro.

## Avaliacao geral C1/C2/C3

As ferramentas estao em:

`simulations/QKD_experiments/`

Consulte `simulations/QKD_experiments/README_QKD_EXPERIMENTS.md`.

## JARs

O JAR classico original foi mantido. O JAR `SNetS-SDM-QKD.jar` foi criado como executavel separado para nao transformar o addon QKD em requisito obrigatorio do uso classico.

## Partes 21 e 22 (ver CHANGELOG_QKD_PARTE21_22.md)

### Opcoes novas em `network.qkd`

```json
"qkd": {
  "enabled": true, "qtndKm": 25.0, "quantumCoreId": 6,
  "qchGranularity": "span",       // "link" (padrao) ou "span" (pool de QChs por trecho QTND)
  "undirectedChannels": true,     // os dois sentidos compartilham os QChs
  "tdmSlots": 256,                // 0 = compartilhamento continuo (padrao)
  "wavelengthPolicy": "grid"      // "fixed" (padrao) ou "grid"
}
```

### Opcoes novas em `simulation`

- `qkdWarmupFraction` (padrao 0.1): transiente descartado nas estatisticas QKD.
- `qkdExactRequestCount` (padrao false): processa exatamente `requests` chegadas.

### Parametros BB84 em `physicalLayer.qkdPhysical`

`meanPhotonNumber`, `pulseIntervalPs`, `errorCorrectionInefficiency`, `misalignmentError`,
`detectorEfficiency`, `darkCountProbability`, `qchBandwidthGHz`, `qchPowerDbm`,
`attenuationFloorDbPerKm`, `attenuationQuadraticDbPerKmNm2`, `attenuationCenterNm`.

### Algoritmos novos do addon

- `coreAndSpectrumAssignment`: `qabne` (ABNE adaptado) ou `qfirstfit` (first fit nos nucleos classicos).
- `integratedRmlsa`: `qrmsta` (RMSTA classico do artigo-base; exige `rmlsaType = 1`).

### Verificacao do artigo-base dentro do SNetS

```bat
simulations\QKD_reference\Dibaj_MF\run_native_reference.bat
```

### Interface

`launcher\SNetS-Launcher.bat` -> botao "Verificacao QKD" (verificadores e artigo-base),
abas "Varredura" e "Resultados" (inclui o QKDStatistics.csv).

## Parte 23 — cenário da qualificação (ver CHANGELOG_QKD_PARTE23.md)

Opções novas em `network.qkd`: `qchGranularity: "route"` (QCh dedicado com o mesmo slot em todos os
trechos da rota QKD, escolhido pela SKR de cada trecho), `qchSlotPolicy` (`firstfit`/`lowestloss`),
`alternativeRoutes` (rotas QKD alternativas) e `pichSlots` (PICh num núcleo clássico).

Por gerador do `traffic`: `qkdProtectedProbability` e `keyRateMbps`.

Métrica `QKDMetrics` (activeMetrics) → `<caso>_QKDMetrics.csv` no formato padrão do SNetS.

Caso-exemplo: `simulations/QKD_qualificacao/Exemplo_5nos_QTND30`.
Verificador: `java -cp simulations/SNetS-SDM-QKD.jar qkd.verification.QKDQualificationScenarioVerification`.

No launcher: seletor "Execução: Clássica | QKD" no cabeçalho do caso, aba QKD (QTND, nós confiáveis,
núcleo, rotas, PICh, demanda e métricas), painel "Tráfego QKD" na aba Tráfego e modelos QKD no
Estúdio de gráficos.

## Parte 24 — Plano de verificação (artigo-base → cenário completo)

`qkd.ladder.QKDLadderMain` executa uma escada de 25 passos, cada um com critérios de aprovação.

- **Fases:**
  - A: artigo-base, métricas e ponto de 1 % de bloqueio;
  - B: MCF de 2 núcleos sem XT, com XT, limite XT → 0 e com QoT;
  - C: MCF de 7 núcleos, QKD na borda e no centro;
  - D: tráfego dinâmico e convergência;
  - E: modo route, rotas alternativas, PICh e demanda por gerador;
  - F: cenário completo em 5 nós e na USA.
- **Verificações extras:**
  - X1: Erlang-B;
  - X2: nós confiáveis analíticos;
  - X3: SKR independente;
  - X4: limites;
  - X5: monotonia;
  - X6: reprodutibilidade;
  - X7: conservação;
  - X8: simetria.
- Pode ser executado pelo launcher ("Verificação QKD" › "Plano de verificação") ou pela linha de
  comando.
- Detalhes em `CHANGELOG_QKD_PARTE24.md` e `simulations/QKD_ladder/README.md`.

Também nesta parte:

- **Nova opção `network.qkd.spanLanePlacement`:**
  - `"first"` é o padrão;
  - `"wavelength"` coloca o QCh no slot do seu comprimento de onda, no meio da banda.
- **Atualização do estado físico das sessões QKD:** só são reavaliadas as sessões com QCh num enlace
  do circuito que mudou. Os resultados são idênticos e o processamento é cerca de 4× mais rápido com
  XT.

## Parte 25: BlockingDetails.csv

Toda simulação com BlockingProbability ativa grava `<caso>_BlockingDetails.csv`. O arquivo traz o bloqueio de
todas as requisições, das clássicas e das protegidas por QKD, com as causas clássicas e QKD juntas. O "other" do
SNetS aparece separado em falta de espectro livre, outros e recusas do addon QKD. O arquivo traz também o bloqueio
por núcleo e causa e o bloqueio por enlace. Ele só observa as decisões e não muda nenhuma delas. Detalhes em
`CHANGELOG_QKD_PARTE25.md`.

## Parte 26: camada física QKD separada, PICh por enlace, QTx/QRx e novos cenários

- **Camada física QKD.** Os parâmetros QKD ficam no arquivo de caso `qkdPhysicalLayer`, com os grupos `source`,
  `protocol`, `detector`, `fiber` e `crosstalk`. Casos antigos continuam funcionando, e a migração é feita pelo
  launcher ou por `qkd.tools.QKDPhysicalLayerMigration`. No código, `BB84DecoyModel` ficou separado, e o resto da
  física QKD está em `QKDPhysicalLayer`.
- **PICh.** O PICh acompanha o QCh: um por enlace da rota QKD, compartilhado pelas sessões (`pichMode = segment`). O
  modelo antigo continua disponível com `pichMode = route`.
- **QTx/QRx.** O simulador conta os transmissores e receptores quânticos por nó, incluindo os nós confiáveis
  intermediários. Os limites são opcionais e geram uma causa de bloqueio própria.
- **Compartilhamento dos QChs.** `qchSharing = dedicated` faz cada sessão usar o seu próprio QCh. `tdmSlots = 0` desliga
  o TDM.
- **Referências.** As referências de cada cálculo estão no Javadoc e em `docs/REFERENCIAS_QKD.md`.
- **Plano de verificação.** Ganhou as fases M (Moghaddam et al., 2021) e Q (QKD-Analytical, 2026).

Detalhes em `CHANGELOG_QKD_PARTE26.md`.
