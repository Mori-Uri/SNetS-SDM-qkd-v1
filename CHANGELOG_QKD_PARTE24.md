# Addon QKD — Parte 24: plano de verificação (do artigo-base ao cenário completo)

Objetivo: uma escada de cenários que parte da reprodução do artigo-base (Dibaj et al., 2024, cenário
multifibra) e chega a um cenário SDM/QKD completo numa topologia grande. Cada passo muda um único
fator e tem critérios de aprovação. Há também verificações extras com resposta conhecida.

Componentes clássicos protegidos (CLASSICAL_BASE_SHA256.txt): nenhum foi alterado.

## Como executar

- **Pelo launcher:** botão "Verificação QKD" › painel "Plano de verificação".
  - Cada passo tem o botão "Executar".
  - Também há "Executar fase" e "Executar tudo", ambos em fila.
  - O botão "Resultados" mostra os critérios, os gráficos e os casos do passo.
- **Pela linha de comando**, a partir da pasta `simulations`:

  ```bat
  java -cp SNetS-SDM-QKD.jar qkd.ladder.QKDLadderMain B2 . --profile rapido
  java -cp SNetS-SDM-QKD.jar qkd.ladder.QKDLadderMain list
  ```

  Opções:
  - `--profile rapido|completo`;
  - `--threads N`: total de threads por simulação;
  - `--force`: simula de novo mesmo os casos que já têm resultado.

Perfis:

| | Rápido | Completo |
|---|---|---|
| Valores de QTND | 10, 20, 25, 30, 40 km | 5 a 50 km |
| Replicações | 3 | 10 |
| Chegadas por replicação (passos dinâmicos) | 20 000 | 100 000 |
| Uso | conferir a cadeia (minutos por passo) | resultados finais (pode levar horas) |

Os casos ficam em `simulations/QKD_ladder/<perfil>/<passo>/<caso>`.

- São casos SNetS comuns: podem ser abertos no launcher, com gráficos, mapa de calor dos enlaces e
  diagrama dos núcleos.
- A pasta de cada passo guarda os arquivos de resultado do plano:
  - `checks.csv`: critérios, com status OK / FALHA / AVISO / INFO;
  - `data.csv` e `charts.csv`: dados dos gráficos;
  - `cases.txt`: lista dos casos;
  - `report.txt`: relatório;
  - `status.txt`: status geral do passo.
- Um passo que depende de outro (por exemplo, o B2 compara com o B1) simula os casos que faltam e
  reaproveita os que já existem.
  - O reaproveitamento é identificado por um hash da configuração (`.ladder_key`), porque o SNetS
    regrava os arquivos de configuração ao final.

## Passos

| Passo | Muda | Critérios principais |
|---|---|---|
| A1 | — (reprodução nativa do artigo) | Curva de Q-TRx em U; ótimo a ≤ 5 km do Python; ótimo de cada replicação a ≤ 5 km de 25 km; MAE ≤ 10 Q-TRx; bloqueio nulo |
| A2 | Ativa as métricas de bloqueio e utilização | Resultados idênticos ao A1; bloqueio nulo |
| A3 | Carga × fatores (QTND 25 km) | Bloqueio não decrescente; tráfego em que o bloqueio chega a 1 % comparado ao artigo |
| B1 | MCF de 2 núcleos (0 clássico, 1 QKD), sem XT/OSNR | Clássico idêntico a um gêmeo sem requisições protegidas; PXT = 0 |
| B2 | + XT | O XT age (recusas para proteger QChs); bloqueio QKD ≥ B1 |
| B2L | XT com acoplamento 1e-12 | Idêntico ao B1 |
| B3 | + OSNR/QoT | Bloqueio por QoT presente |
| C1 / C2 | MCF de 7 núcleos, QKD na borda (1) / no centro (0) | Vizinhança; XT age; bloqueio no centro ≥ na borda |
| D1 | Tráfego dinâmico (Poisson, várias cargas) | Bloqueio não decrescente; lei de Little; bloqueio QKD = clássico com 100 % protegidas; conservação |
| D2 | Dobro de chegadas e 20 % de aquecimento | Compatível com o D1 |
| E1–E4 | Modo route; + rotas alternativas; + PICh; + demanda por gerador | SKR ≥ demanda; alternativas não pioram; PICh; fração protegida ≈ 0,2 |
| F1 / F2 | Cenário completo (modelo clássico da qualificação) em 5 nós / USA | Todos os anteriores numa rede grande |
| X1 | Erlang-B (enlace único) | Bloqueio = fórmula |
| X2 | Nós confiáveis | = Σ ⌈L/QTND⌉ − 1; monotonia; QTND ≥ maior enlace ⇒ 0 |
| X3 | SKR independente (sem simulação) | Diferença ≤ 1e-9; Tabela 5; ótimo da Fig. 5(b) |
| X4 | Limites | Fração 0 ⇒ nenhum QKD e núcleo QKD intocado; demanda → 0 ⇒ sem bloqueio por SKR |
| X5 | Monotonia | Mais demanda ⇒ mais bloqueio por SKR; mais núcleos ⇒ menos bloqueio clássico |
| X6 | Reprodutibilidade | Mesmos resultados com a mesma semente, inclusive com outro número de threads |
| X7 | Conservação em todos os casos do plano | Aceitas + bloqueadas = tentativas; causas somam o total |
| X8 | Simetria dos sentidos (D1) | Diferença média entre sentidos ≤ 25 % |

## Escolhas de modelagem dos passos SDM

- **Carga dos passos B e C**
  - A carga é a do artigo × (núcleos clássicos ÷ 10) × 2.
  - O fator 2 é o ponto de 1 % de bloqueio encontrado no A3.
  - Com essa carga, o espectro clássico chega à região dos QChs e o crosstalk passa a ter efeito.
- **Nova opção `network.qkd.spanLanePlacement`** (modos span e link)
  - `"first"` é o padrão e mantém o artigo-base inalterado.
  - `"wavelength"` coloca cada QCh no slot do seu comprimento de onda da grade, no meio da banda.
    Assim, a sobreposição espectral com os núcleos vizinhos é coerente com o λ usado na SKR.
  - Com `"first"`, os QChs ocupam os slots 1, 2, …, que ficam sob o canal reservado ao PICh e nunca
    recebem crosstalk.
- **Limiares de XT clássicos nos passos B a E**
  - Seguem o modelo do artigo, com limiares de XT 0 dB.
  - O crosstalk clássico → clássico entra na fase F, com o modelo da qualificação (qabne, que evita XT).
  - O qrmsta do artigo coloca os circuitos no mesmo canal de todos os núcleos. Com limiares reais,
    ele bloquearia quase tudo por XT.
- **Passos dinâmicos**
  - Usam 240 slots por enlace (40 canais de 75 GHz).
  - Assim cada replicação cobre várias durações médias, com algumas centenas de conexões
    simultâneas, e não milhares.

## Observações dos primeiros resultados (perfil rápido)

- **A3:** o bloqueio de 1 % aparece com cerca de 2× o tráfego do artigo (≈ 1250–1340 Tbit/s
  contra 612–654 Tbit/s).
  - O modelo de capacidade clássica da reconstrução (10 fibras × 159 canais, K = 2) tem mais folga
    que a do artigo.
  - É uma diferença de suposição, não de implementação.
- **B2:** um canal clássico de 0 dBm no núcleo vizinho, na mesma frequência do QCh, leva p_b → 1 e a
  SKR → 0.
  - Só são admitidas sessões sem sobreposição espectral.
  - Os candidatos clássicos que cairiam sobre um QCh ativo são recusados, o que dá até ~35 % de
    bloqueio.
  - Esse é o custo da coexistência sem coordenação espectral entre núcleos.
- **D1:** com 100 % das requisições protegidas e o mesmo aquecimento, o bloqueio QKD é idêntico ao
  bloqueio clássico.
  - A lei de Little vale com erro de até ~2,5 %.
  - Com poucas chegadas por replicação, o transitório inicial puxa a ocupação para baixo; o D2 mostra
    isso.

## Desempenho

`QKDControlPlane` reavaliava o estado físico de todas as sessões QKD ativas a cada circuito clássico
alocado ou liberado. Agora:

- só reavalia as sessões com QCh num enlace do circuito que mudou, porque o crosstalk de um enlace
  depende apenas dos circuitos daquele enlace;
- cada QCh compartilhado é avaliado uma vez por atualização.

Os resultados são idênticos, inclusive a coluna ActiveRefreshes. O caso C1 (7 núcleos, XT) caiu de
160 s para 33 s.
