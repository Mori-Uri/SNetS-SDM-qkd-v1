# SNetS Launcher

Interface web local para configurar, executar e visualizar simulações do SNetS-SDM.

O launcher é um add-on: fica todo nesta pasta `launcher/` e **não altera nem carrega o código do simulador**.
Ele só lê e grava os arquivos de configuração dos casos em `simulations/` e executa o `.jar` do simulador como
processo separado (`java -jar <jar> <caso>`, como os `run_*.bat`).

## Como abrir

Dê um duplo clique em `SNetS-Launcher.bat` (ou rode `java -jar snets-launcher.jar`). O navegador abre em
`http://localhost:8765`. Para encerrar, feche a janela do console.

Opções: `--port 8765`, `--project <pasta do SNetS>`, `--no-browser`.

As ferramentas do simulador (verificadores e `qkd.reference.*`) são iniciadas como processos separados
(`java -cp <jar> <classe>`); só as classes conhecidas podem ser chamadas pela interface.

## O que faz

| Aba | Função |
|---|---|
| Visão geral | Estado dos arquivos do caso, resumo e validação |
| Tráfego | Resumo da carga por ponto, reutilização do tráfego de outro caso, geração de tráfego novo (mesma fórmula do `TrafficFileCreator`) |
| Rede / Camada física / Simulação / Others | Edição em formulário ou JSON. Campos desconhecidos aparecem em "Campos adicionais" e são sempre preservados |
| Métricas | `activeMetrics` → quais CSVs serão gerados |
| Revisar e salvar | Validação + diff por arquivo; só grava os arquivos alterados |
| Executar | Executa com log ao vivo e progresso; coloca vários casos na fila; gera `.bat` ou `.sh` (salvos em `simulations/`) |
| Varredura | Cria cópias do caso variando um ou dois campos (ex.: QTND, η_f, modelo de transferência do XT) e as executa em fila |
| Resultados | Estúdio de gráficos aberto no caso: todos os modelos e opções de estilo do botão Gráficos, comparação com outros casos, explicação da métrica abaixo do gráfico e legenda das colunas abaixo da tabela, dicas ao passar o mouse; exportação SVG/PNG/CSV |

**Modo de execução Clássica / QKD** (cabeçalho do caso). Ao ativar QKD, o launcher pede:

- o QTND;
- o núcleo reservado;
- quantas rotas alternativas usar;
- quantos slots dá ao PICh;
- opcionalmente, a fração protegida e a demanda de chave para todo o tráfego.

Também liga a métrica QKDMetrics. A aba **QKD** mostra o planejamento dos nós confiáveis (trechos por
enlace), as opções do QCh, a demanda de chave e as métricas. O painel **Tráfego QKD** (aba Tráfego) marca
geradores existentes com `qkdProtectedProbability`/`keyRateMbps` ou acrescenta uma lista de geradores QKD
dedicados. Voltar para "Clássica" desliga `network.qkd.enabled` e mantém as demais opções.

Botão **Gráficos** (barra superior, ou "Abrir no Estúdio de gráficos" na aba Resultados): estúdio de figuras
com os resultados de **vários casos ao mesmo tempo**. Modelos prontos:

| Modelo | Origem nos CSVs |
|---|---|
| Probabilidade de bloqueio / bloqueio de banda / utilização × carga | `Blocking probability`, `BitRate blocking probability`, `Utilization` |
| Causas de bloqueio (empilhadas, 100 % ou agrupadas) | `Blocking probability by QoTN/QoTO/crosstalk/crosstalk in other/fragmentation/…` |
| Utilização dos enlaces (ordenada, um painel por caso) | `Utilization Per Link` |
| Circuitos por modulação | `Percentage of cicuits per modulation` |
| Bloqueio / utilização por núcleo (barras) | `Blocking probability per core N`, `Utilization per Core` |
| Uso e bloqueio de cada núcleo (diagrama da fibra, como a Fig. 9 do artigo da Jordana) | `Utilization per Core` + `Blocking probability per core N`; posições lidas de `network.cores` |
| Personalizado | qualquer métrica/dimensão de qualquer CSV |

Tudo é editável: rótulo e ordem dos casos, cor, tipo de traço, espessura, marcador (forma, tamanho, vazado),
paleta, fundo da imagem e do gráfico, largura e altura, fonte e tamanhos, títulos, limites e passo dos eixos,
escala log, formato dos números (vírgula decimal), grade, legenda (posição, colunas, caixa), barras de erro
(IC 95 %, barras ou faixa), valores sobre as barras, painéis. Exporta SVG, PNG (1× a 4×) e a tabela de dados (CSV).
"Salvar" grava a definição em `launcher/figures/<nome>.json`; ao reabrir, o gráfico é recalculado com os CSVs
atuais. "Gravar imagens na pasta" grava `<nome>.svg` e `<nome>.png` na mesma pasta.

Botão **Verificação QKD** (barra superior): executa os verificadores do addon QKD contidos no JAR e as três
verificações do artigo-base (camada física, rede sobre os traces Python e rede no motor nativo do SNetS), com
tabelas e gráficos (SNetS × reconstrução Python × Fig. 7 digitalizada).

As listas de algoritmos (`routing`, `powerAssignment`, `coreAndSpectrumAssignment`, …) são lidas das constantes
de `src/main/java/gprmcsa/GPRMCSA.java` e dos arquivos `*AlgorithmFactory.java` (ex.: `qabne`). As métricas vêm da
classe `Metrics` de `SimulationConfig.java`. Novos algoritmos aparecem automaticamente.

## Observações

- Proteção contra sobrescrita: se um arquivo do caso mudou no disco depois de aberto (por exemplo, editado em
  outro programa), o launcher recusa salvar e pede para recarregar.
- O simulador regrava os arquivos de configuração do caso ao salvar os resultados; o launcher recarrega o caso
  automaticamente ao fim de uma execução.
- Configurações do launcher (pasta do projeto, JAR padrão, argumentos da JVM) ficam em
  `launcher/launcher-settings.properties`.
- O servidor escuta apenas em `localhost`.

## Recompilar

`build-launcher.bat` (Windows) ou `./build.sh` — requer apenas um JDK 8+; não usa Maven.

Estrutura: `src/launcher/` (servidor Java, sem dependências), `web/` (interface HTML/JS),
`templates/` (configuração usada ao criar um caso sem origem).

## Versão 1.3.0

- **Execução**
  - Fechar ou recarregar o navegador não interrompe as simulações: elas rodam no processo do launcher.
  - Fechar a janela principal do launcher encerra todas as execuções.
  - Opção "Abrir janela de console" (só no Windows): cada execução ganha uma janela própria com a saída do simulador e uma barra de progresso. Fechar essa janela cancela só aquela execução.
- **Threads por execução**
  - O campo "Threads desta execução" substitui o valor do arquivo simulation sem alterá-lo (`-Dsnets.threads=N`).
- **Vários casos** (aba Executar)
  - Execução direta no launcher, sem gerar .bat.
  - Em fila: cada caso usa as threads escolhidas.
  - Em paralelo: o total de threads é dividido igualmente entre os casos.
- **Pasta simulations recolhível:** botão « no canto superior esquerdo.
- **Rede › Topologia e fibra**
  - Topologias prontas: NSFNet, USA, COST239, 28 nós, 12 nós, núcleo pequeno, borboleta e metropolitana. Todos os enlaces são bidirecionais e têm os mesmos slots e a mesma largura de slot.
  - Desenho da rede gerado a partir do arquivo network. As posições ficam em `x`/`y` nos nós, e o simulador ignora esses campos.
  - Fibras de 2, 7, 12 e 19 núcleos, com pré-visualização. Os mesmos desenhos aparecem no diagrama por núcleo dos resultados.
- **Gráficos e Resultados › Mapa de calor da utilização dos enlaces**
  - Uma imagem da topologia por ponto de carga.
  - Escala verde → amarelo → laranja → vermelho, com legenda.
  - Os dois sentidos de cada enlace aparecem como faixas paralelas (ou o maior valor, ou a média dos dois).
- **Tráfego:** glossário de cada variável (clássicas e QKD) e textos de ajuda mais claros.

## Versão 1.4.0

- **Verificação QKD › Plano de verificação**
  - Escada de 25 passos, do artigo-base ao cenário completo, mais verificações extras:
    - A: artigo-base;
    - B: MCF de 2 núcleos;
    - C: MCF de 7 núcleos;
    - D: tráfego dinâmico;
    - E: recursos da qualificação;
    - F: cenário completo em 5 nós e na USA;
    - X: Erlang-B, nós confiáveis, SKR independente, limites, monotonia, reprodutibilidade, conservação e simetria.
  - Cada passo pode ser executado sozinho, por fase ou tudo em fila.
  - Há dois perfis: Rápido e Completo.
  - Ao terminar, cada passo mostra os critérios (OK/AVISO/FALHA), gráficos e os casos SNetS gerados, que podem ser abertos com um clique.
- **Rede:** nova opção "Posição dos QChs no núcleo QKD" (`qkd.spanLanePlacement`).

## Versão 1.5.0

- **Execuções:** o painel fecha ao clicar fora dele ou ao apertar Esc.
- **Gráficos › novos modelos** (usam o novo `<caso>_BlockingDetails.csv`, gravado pelo simulador da Parte 25 em casos clássicos e QKD):
  - Bloqueio total × carga, com uma linha para todas as requisições, uma para as clássicas e uma para as protegidas por QKD.
  - Causas de bloqueio de todas as requisições, em valores ou em %. As causas QKD (proteção de sessões ativas, espectro do QCh, SKR e PICh) aparecem junto das clássicas, e o "other" do SNetS é separado em "falta de espectro livre" e "outros".
  - Bloqueio por núcleo com as causas, e a parcela do bloqueio total em cada núcleo por causa.
  - Utilização e bloqueio de cada enlace na topologia: a cor é a utilização, e os rótulos mostram o bloqueio de cada sentido, separado entre clássico (C) e QKD (Q).
- **Barras:** nova opção "Cores (séries) por", para cruzar duas dimensões (por exemplo, Core × Cause).

## Versão 1.6.0

- **Nova aba "Camada física QKD"** (arquivo `qkdPhysicalLayer`), com os parâmetros agrupados em fonte, protocolo,
  detector, fibra e crosstalk.
  - Casos antigos mostram um botão para mover os parâmetros QKD do `physicalLayer` para o arquivo novo.
  - Sem o arquivo, a aba oferece criar um com os valores de referência.
- **Rede › QKD**: novos campos `pichMode`, `pichSessionsPerChannel`, `qchSharing` e limites de QTx/QRx por nó e por nó
  confiável. A ajuda dos slots TDM explica o que é o TDM.
- **Gráficos**:
  - nova causa "QKD: falta de QTx/QRx";
  - modelo "QKD: QTx/QRx em uso por nó (pico)";
  - glossário das métricas novas (PICh abertos, QTx/QRx).
- **Verificação QKD › Plano de verificação**:
  - fase M (Moghaddam et al., 2021): M1–M3;
  - fase Q (QKD-Analytical, 2026): Q1–Q3.
- **Varredura**: os parâmetros do receptor QKD agora apontam para o arquivo `qkdPhysicalLayer`.

## Versão 1.7.0

- **Gráficos › novo modelo "Ocupação dos slots de cada núcleo em cada enlace"** (tipo "Mapa de calor do espectro"):
  - uma linha por núcleo de cada enlace e uma coluna por slot, colorida pela fração do tempo em que o slot estava
    ocupado (média das replicações);
  - escolha do ponto de carga, dos enlaces (todos, só um sentido, ou um a um), dos núcleos e da faixa de slots;
  - escalas contínuas ou em 5 faixas, faixa fixa (0–100 %) ou automática, legenda, altura das linhas e divisões;
  - passar o mouse mostra enlace, núcleo, slots e ocupação.
  - Usa o novo `<caso>_SlotUtilization.csv`, gravado pelo simulador da Parte 27 (métrica `SlotUtilization`, ligada por
    padrão junto com `SpectrumUtilization`).
- **Correção:** editar o nome de uma série (seção "3. Séries e cores") não tira mais o cursor da caixa a cada letra.
- **Correção:** o bloqueio por núcleo com as causas, com vários casos, não dá mais "Cannot read properties of undefined
  (reading 'style')" quando há mais causas do que casos.
- **Métricas:** uma métrica ausente do bloco `activeMetrics` aparece como ligada (é o padrão da classe `Metrics`).

## Versão 1.8.0

- **Verificação em pasta própria.** O plano de verificação grava em `<projeto>/verificacao` (QKD_ladder, QKD_reference).
  Na árvore, essa pasta aparece numa seção "Verificação" separada, e no estúdio de gráficos os casos dela só entram com
  a opção "Incluir os casos da pasta verificacao".
- **Modelos de gráfico agrupados** por assunto: Bloqueio, Causas de bloqueio, Núcleos, Espectro e utilização,
  Fragmentação, Mapas de calor, Correlações, QKD e Outros.
- **Gráficos novos:**
  - diagnóstico do espectro dos bloqueios: falta de slots, fragmentação de continuidade, fragmentação de contiguidade
    ou faixa livre;
  - fragmentação externa, relativa e por enlace, e o mapa de calor da fragmentação;
  - perfil de ocupação por slot;
  - dispersões de correlação (enlace, núcleo ou carga), com reta de ajuste e r de Pearson.
- **Nós confiáveis** como pontos nas topologias da aba Rede e dos mapas de calor.

## Versão 1.9.0

- **Botão "Gerar JAR"** (barra de cima). Compila `src/main/java` e gera o JAR do simulador em `simulations`, sem Maven.
  - **O que o JAR base fornece:** um JAR existente do simulador entra como base, com as bibliotecas (Gson, OpenCSV,
    Firebase) e a classe `simulationControl.Main`, que só compila com o Maven. Todas as outras classes do projeto são
    recompiladas a partir do código (Java 8, ISO-8859-1, como no `pom.xml`).
  - **Backup:** o JAR anterior é guardado em `simulations/backup_jar/<nome>_<data>.jar`. O `SNetS-SDM-SBRC26.jar` não
    pode ser substituído.
  - **Verificações:** opcionalmente, as verificações QKD rodam com o JAR novo.
  - **Andamento:** o progresso e os erros de compilação aparecem em Execuções.
  - **Compilador:** precisa de um JDK. O launcher procura o `javac` no Java que o roda, no `JAVA_HOME`, no `PATH`, em
    `~/.jdks` e em `Arquivos de Programas`. Também dá para informar o caminho em Configurações › Comando javac.
