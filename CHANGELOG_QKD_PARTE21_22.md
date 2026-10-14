# Entrega QKD - Partes 21 e 22 (+ interface)

Continuacao das Partes 18-20. Objetivo: fechar a camada QKD do simulador segundo a
qualificacao, permitir verificar o artigo-base (Dibaj et al., 2024, cenario multifibra)
dentro do proprio SNetS e concluir a interface (`launcher/`).

Os componentes classicos protegidos em `CLASSICAL_BASE_SHA256.txt` (GPRMCSA.java,
CSBASDM.java e SNetS-SDM-SBRC26.jar) nao foram alterados.

## 1. Parte 21 - QChs por trecho QTND (modelo do artigo-base)

Ate a Parte 20 cada QCh cobria um enlace inteiro (capacidade = trecho gargalo, Q-TRx = numero
de trechos do enlace). A qualificacao (Secao 4.3) e o artigo-base usam um conjunto de QChs por
trecho QTND. Os dois modelos agora existem e sao escolhidos em `network.qkd`:

| Campo | Valores | Padrao | Efeito |
|---|---|---|---|
| `qchGranularity` | `link`, `span` | `link` | `span`: um pool de QChs por trecho; Q-TRx = QChs ativos somados sobre os trechos |
| `undirectedChannels` | `true`/`false` | `false` | os dois sentidos de um par de fibras compartilham os pools (chave simetrica) |
| `tdmSlots` | inteiro >= 0 | `0` | `0` = compartilhamento continuo pela taxa; `N` = cada demanda reserva ceil(d/C*N) slots de um unico QCh (sem dividir a demanda entre QChs) |
| `wavelengthPolicy` | `fixed`, `grid` | `fixed` | `grid`: o k-esimo QCh de um trecho usa o k-esimo melhor comprimento de onda da grade de 12,5 GHz |

Com os padroes o comportamento das Partes 1-20 (C2/C3) e identico; todos os verificadores
antigos continuam passando. Verificador novo: `QKDSpanGranularityVerification`.

## 2. Parte 22 - artigo-base executado pelo motor do SNetS

`qkd.reference.QKDNativeReferenceMain` gera 30 casos SNetS comuns
(`simulations/QKD_reference/Dibaj_MF_SNetS`), executa-os com `simulationControl.Main` e
compara com a reconstrucao em Python e com a Fig. 7 digitalizada. Requisicoes, rotas,
alocacao classica, segmentacao QTND, TDM e contagem de Q-TRx sao todos produzidos pelo
simulador (nao ha mais dependencia dos traces roteados nesse modo).

| Topologia | QTND otimo SNetS | Python | Artigo | MAE vs Python | MAE vs artigo |
|---|---|---|---|---|---|
| Small Core | 25 km (7/10 replicacoes; 30 km em 3/10) | 25 km | 25 km | 5,1 Q-TRx | 16,2 Q-TRx |
| Butterfly | 30 km (10/10) | 30 km | 25 km | 3,8 Q-TRx | 11,5 Q-TRx |
| Metro | 25 km (10/10) | 25 km | 25 km | 2,4 Q-TRx | 13,9 Q-TRx |

Sem bloqueio classico ou QKD (como na reconstrucao). Verificador: `QKDNativeArticleBaseVerification`.
A verificacao fisica agora grava a Tabela 5 da qualificacao e SKR x mu com status por valor.

Componentes novos usados pelo cenario de referencia (todos no namespace do addon):

- `qrmsta` (`qkd.algorithms.integrated.QKDReferenceRMSTA`): RMSTA classico do artigo -
  K caminhos, modulacao por distancia, first fit na grade de canais de 75 GHz, canal do PICh
  reservado e escolha do caminho com menor indice de canal final.
- `qfirstfit` (`qkd.algorithms.coreSpectrumAssignment.QKDFirstFit`): first fit sobre os
  nucleos classicos (o nucleo QKD nunca e candidato).
- `physicalLayer.qkdPhysical`: parametros BB84 (mu, Ts, eta_ec, e_d, eta_d, p_dark, largura
  do QCh, alpha(lambda)) configuraveis por arquivo; ausente = valores do artigo.
- `simulation.qkdExactRequestCount`: processa exatamente `requests` chegadas (ver item 4).

## 3. Metricas e metodologia

- Warm-up QKD: os contadores do `QKDStatistics.csv` agora descartam o transiente com a mesma
  regra das metricas classicas do SNetS (10% das chegadas). Configuravel em
  `simulation.qkdWarmupFraction` (0 para estudos semiestaticos).
- Novas colunas no fim do `QKDStatistics.csv` (as antigas foram mantidas na mesma ordem):
  `QKDAcceptanceRatio`, `QKDBlockingProbability`, `MeanActiveQChs`, `MeanActiveQTRxPairs`,
  `MeanActiveQKDSessions` (media nas chegadas apos o warm-up; por PASTA estima a media
  temporal), `QTND_km`, `WarmupArrivals`.
- Desempenho: quando `qkdReceiver.intercoreCrosstalkEnabled = false` (MF ou C2), p_b = p_dark
  nao depende do estado classico; a protecao/atualizacao das sessoes ativas a cada mudanca
  classica e pulada (antes era O(sessoes) por alocacao). O cenario de referencia passou de
  ~90 s para ~2 s por caso.

## 4. Pontos de atencao encontrados na auditoria

1. Cauda de chegadas: o SNetS para de gerar quando `requests` chegadas foram geradas, mas cada
   gerador ja tem uma chegada agendada. Com 910 geradores (NSFNet, 5 taxas) e `requests = 100`,
   cada replicacao processa ~1010 chegadas - ~90% vem dessa cauda. Isso vale para C1, C2 e C3
   atuais. Antes da campanha final: aumentar `requests` (>> numero de geradores) ou, em
   cenarios QKD, ativar `qkdExactRequestCount`. A interface mostra um aviso.
2. `SimulationManagement`: o contador de replicacoes concluidas era incrementado sem
   sincronizacao (podia travar a espera final) e uma replicacao com excecao travava o programa.
   Agora o contador e sincronizado e uma falha encerra a execucao com mensagem e codigo != 0.
3. No C3 (XT ligado) com os parametros atuais o bloqueio QKD e muito alto (83-96% nas
   replicacoes do teste reduzido): e um resultado do modelo, mas merece revisao das hipoteses
   do receptor (eta_f = 1, Tg = 100 ps, receiver_referenced) antes do artigo. A aba
   "Varredura" da interface gera os casos de sensibilidade.

## 5. Interface (`launcher/`)

- Pagina "Verificacao QKD": executa os 20 verificadores em fila e as tres verificacoes do
  artigo-base (fisica, traces Python e SNetS nativo) com tabelas e graficos (SNetS x Python x
  artigo, absoluto e normalizado).
- Aba "Varredura": cria casos variando um ou dois campos (QTND, eta_f, modelo de transferencia,
  Tg, fracao protegida, algoritmo...), com execucao em fila.
- Fila de execucao, geracao de `.sh`, leitura do `QKDStatistics.csv` nos graficos e eixo x
  "Parametro dos casos" para comparar varreduras.
- Formularios com os novos campos QKD e botoes para adicionar campos conhecidos ausentes.

## Arquivos

Modificados: `simulator/Simulation.java`, `simulationControl/SimulationManagement.java`,
`simulationControl/parsers/{NetworkConfig,PhysicalLayerConfig,SimulationConfig}.java`,
`qkd/algorithms/QKDAlgorithmFactory.java`, `qkd/capacity/{QKDChannelShare,QKDSharedChannel,
QKDSharedResourceManager}.java`, `qkd/control/{QKDControlPlane,QKDExistingSessionImpactEvaluator}.java`,
`qkd/metrics/QKDMetricsSnapshot.java`, `qkd/network/{QKDMesh,QKDMeshFactory,QKDNetworkParameters}.java`,
`qkd/reference/QKDReferencePhysicalMain.java`, `qkd/traffic/QKDRequestGenerator.java`,
`qkd/verification/QKDExistingSessionProtectionVerification.java` (fixture com warm-up 0),
`simulations/SNetS-SDM-QKD.jar` (copia anterior em `simulations/backup_jar/`),
`simulations/QKD_reference/README.md`.

Adicionados: `qkd/algorithms/integrated/QKDReferenceRMSTA.java`,
`qkd/algorithms/coreSpectrumAssignment/QKDFirstFit.java`, `qkd/traffic/QKDArrivalBudget.java`,
`qkd/reference/QKDNativeReferenceMain.java`, `qkd/verification/QKDSpanGranularityVerification.java`,
`qkd/verification/QKDNativeArticleBaseVerification.java`,
`simulations/QKD_reference/Dibaj_MF/run_native_reference.bat`,
`simulations/QKD_reference/Dibaj_MF_SNetS/` (30 casos + resultados),
`docs/VALIDACAO_QKD_PARTES1_A_22.txt`, este arquivo.

O JAR foi compilado com `javac --release 8` a partir de `src/` usando as dependencias contidas
no JAR anterior (o `Main.class` original foi mantido, pois `Main.java` nao mudou).
