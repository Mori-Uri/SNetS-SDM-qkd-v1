# Parte 27: ocupação de cada slot e correções do launcher

## Simulador: novo arquivo `<caso>_SlotUtilization.csv`

- **O que é.** Para cada ponto de carga, enlace, núcleo e replicação, a fração das observações em que cada slot
  estava ocupado. É a mesma amostragem do `SpectrumUtilization`: uma observação a cada chegada de requisição.
- **Formato.** Cabeçalho `Metrics,LoadPoint,Link,Core,Replication,1,2,…,S`, com uma coluna por slot. Cada linha
  traz `Slot utilization`, o ponto de carga, o enlace `<a-b>`, o núcleo, a replicação e os S valores.
- **O que conta como ocupado.**
  - As bandas de guarda contam como ocupadas. Por isso a média de uma linha fica acima da `Utilization per Link and
    Core`, que conta só os slots dos circuitos.
  - O núcleo reservado à QKD é lido pela visão QKD do espectro, então aparecem os QChs e PIChs. A visão clássica
    mostraria esse núcleo como cheio.
- **Como ligar e desligar.** A métrica `activeMetrics.SlotUtilization` vem ligada por padrão e só grava o arquivo se
  `SpectrumUtilization` também estiver ligada. Casos antigos, sem a chave, também gravam o arquivo.
- **Tamanho.** Numa rede de 22 enlaces dirigidos × 7 núcleos × 320 slots, com 5 pontos × 5 replicações, o arquivo
  tem cerca de 4–5 MB.
- **Classes alteradas:**
  - `measurement.SpectrumUtilization`: contadores por enlace, núcleo e slot, e `appendSlotUtilizationRows`;
  - `SimulationManagement.buildSlotUtilizationCsv`;
  - `SimulationRequest.Result`: `slotUtilization` e `FILE_SLOT_UTILIZATION`;
  - `SimulationFileManager`;
  - `SimulationConfig.Metrics`: `SlotUtilization`.
- **O que não mudou.** Nenhuma decisão de alocação, e nenhum dos CSVs que já existiam.
- **Observação sobre um CSV antigo.** No `SpectrumUtilization.csv` de casos com QKD, a "Utilization Per Slot" (que é
  do SNetS original, não foi alterada) conta o núcleo QKD como sempre ocupado. Para a ocupação por slot, use o
  arquivo novo.

## Launcher 1.7.0

- **Gráficos › novo modelo "Ocupação dos slots de cada núcleo em cada enlace"** (mapa de calor do espectro).
  - Opções: ponto de carga, enlaces, núcleos, faixa de slots, ordem dos enlaces, escala de cores (contínua ou em 5
    faixas) e faixa da escala.
  - Ao passar o mouse sobre uma célula, aparece a ocupação.
- **Correção:** editar o rótulo de uma série tirava o foco da caixa a cada letra. O painel de séries era redesenhado
  porque a assinatura das séries incluía os nomes.
- **Correção:** "Cannot read properties of undefined (reading 'style')" no bloqueio por núcleo com causas, quando havia
  vários casos com mais causas do que casos. O estilo agora é buscado pela chave da série.
- Os grupos de caixas de seleção dos filtros novos usam `div` em vez de `label`. Assim, clicar no texto não aciona o
  primeiro botão do grupo.

## Parte 27b: verificação em pasta própria, diagnóstico do espectro e gráficos novos

### Pasta `verificacao`, separada de `simulations`

- **Para onde foram os resultados.** O plano de verificação grava agora em `<projeto>/verificacao/QKD_ladder/<perfil>/<passo>/`,
  e não mais em `simulations/`. Cada passo tem casos SNetS, `checks.csv`, `data.csv`, `charts.csv`, `cases.txt`,
  `report.txt` e `status.txt`.
- **O artigo-base também mudou de lugar.** `QKD_reference` (Dibaj_MF e Dibaj_MF_SNetS) é lido de
  `verificacao/QKD_reference`. Quando ele ainda estiver em `simulations/QKD_reference`, o lugar antigo continua valendo.
- **Linha de comando.** `QKDLadderMain` aceita `--out <pasta>`. Sem a opção, usa a pasta `verificacao` ao lado de
  `simulations`.
- **Caminhos no `cases.txt`.** O `cases.txt` grava os casos como `@verificacao/…`, que é o prefixo que o launcher usa
  para essa pasta.
- **Verificadores.** Os verificadores do artigo-base procuram primeiro `verificacao/QKD_reference/Dibaj_MF`. Os demais
  verificadores só escrevem no console.

### Diagnóstico do espectro dos bloqueios (BlockingDetails.csv)

- **Métrica nova.** `Blocking probability by spectrum diagnosis`, com a coluna `Cause`.
- **Quando e como é calculada.** Para cada requisição bloqueada, o simulador olha o espectro da rota do candidato
  bloqueado no momento do bloqueio. Usa o menor número de slots que alguma modulação com alcance suficiente exige e
  considera o melhor estado entre os núcleos clássicos:
  - `Spectrum: not enough free slots`: em todos os núcleos, algum enlace não tinha slots livres suficientes;
  - `Spectrum: continuity fragmentation`: cada enlace tinha slots livres suficientes, mas não os mesmos;
  - `Spectrum: contiguity fragmentation`: os slots livres comuns à rota bastavam, mas não eram vizinhos;
  - `Spectrum: free band available`: havia uma faixa contígua e contínua, então o bloqueio veio da camada física
    (QoT/XT) ou da proteção QKD;
  - `Spectrum: QKD side`: o lado QKD recusou um candidato válido;
  - `Spectrum: no route`: não havia rota.
- **Por que é útil.** As parcelas somam o bloqueio total e não dependem da ordem dos testes do algoritmo. Com o
  PRMSCA, a causa "Fragmentation" do SNetS quase nunca aparece, porque um candidato com faixa é sempre escolhido.
- **O que não mudou.** Nenhuma decisão de alocação nem os outros CSVs. A medição só acontece nos bloqueios.

### Launcher 1.8.0

- **Árvore.** Os casos da pasta `verificacao` aparecem numa seção própria, "Verificação", abaixo de `simulations`.
  O seletor de casos do estúdio só os mostra quando se marca "Incluir os casos da pasta verificacao".
- **Modelos de gráfico agrupados** em Bloqueio, Causas de bloqueio, Núcleos, Espectro e utilização, Fragmentação,
  Mapas de calor, Correlações, QKD e Outros. Os nomes ficaram mais curtos e alguns modelos têm uma descrição. Os ids
  são os mesmos, então as figuras salvas continuam abrindo.
- **Modelos novos:**
  - diagnóstico do espectro (empilhado, em 100 % e × carga);
  - fragmentação externa × carga, sua entropia, fragmentação por enlace e fragmentação relativa por tamanho;
  - mapa de calor da fragmentação na topologia;
  - perfil de ocupação por slot, com uma linha por núcleo;
  - correlações, um tipo novo de dispersão:
    - por enlace: utilização × bloqueio, utilização × fragmentação e fragmentação × bloqueio;
    - por núcleo: utilização × bloqueio;
    - por carga: utilização × bloqueio e fragmentação × bloqueio;
    - em todos, com reta de ajuste e r de Pearson.
- **Nós confiáveis nas topologias.** Na aba Rede e nos mapas de calor, os nós confiáveis aparecem como pontos menores,
  a cada QTND km a partir da origem de cada sentido, como faz o planejador QKD. Dá para ligar e desligar e escolher a
  cor e o raio.
- **Correção.** Os mapas na topologia agora aceitam a dimensão `link` em minúsculas, como a do
  `ExternalFragmentation.csv`.
- **Casos novos.** O modelo de caso do launcher (`templates/simulation`) vem com `ExternalFragmentation` e
  `RelativeFragmentation` ligadas. Nos casos existentes, ligue as duas na aba Métricas.
