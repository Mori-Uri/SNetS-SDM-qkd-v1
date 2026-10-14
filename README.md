<!-- QKD-ADDON -->
> **Extensão QKD (dissertação de Uriel Pinheiro Mori, PPGCC/UFPI).** Esta versão do SNetS-SDM inclui um addon de
> distribuição quântica de chaves (QKD) e um launcher web.
>
> - Addon QKD: `README_QKD_ADDON.md` e `CHANGELOG_QKD_PARTE*.md`.
> - Launcher: `launcher/README.md`. Para abrir, execute `launcher/SNetS-Launcher.bat`.
> - Referências usadas no código: `docs/REFERENCIAS_QKD.md`.
> - Como publicar e compartilhar no GitHub: `docs/GITHUB_PASSO_A_PASSO.md`.
>
> O texto abaixo é o README original do artefato SBRC 2026.

# SNetS-SDM-SBRC26

## Título do artigo

**IMPA: Novo algoritmo para atribuição de potência de forma adaptativa em SDM-EONs**

## Resumo do artigo

Neste trabalho investigamos o problema ampliado PRMCSA, no qual a potência é tratada como variável de decisão. Propomos o algoritmo **IMPA** (*Impairment-Aware Margin Power Assignment*) para atribuição adaptativa de potência em **SDM-EONs**. O IMPA seleciona a menor potência viável que satisfaz simultaneamente margens de **OSNR** e **crosstalk**, considerando também o impacto sobre circuitos vizinhos já estabelecidos. Avaliações nas topologias **NSFNet** e **USA** demonstram que o IMPA reduz significativamente a probabilidade de bloqueio em relação aos algoritmos clássicos e adaptativos da literatura, alcançando uma redução na probabilidade de bloqueio de bitrate de pelo menos **18%** na topologia NSFNet e **46,5%** na topologia USA.

Artigo publicado nos anais do SBRC: [https://sol.sbc.org.br/index.php/sbrc/article/view/42333](https://sol.sbc.org.br/index.php/sbrc/article/view/42333)

---

## Resumo do artefato

Este repositório contém o artefato associado ao artigo submetido ao **SBRC 2026**, incluindo:

- Código-fonte do simulador em **Java**;
- Versão empacotada do simulador em **JAR**;
- Conjuntos de simulação organizados por topologia, algoritmo e parâmetros;
- Resultados experimentais em CSV;
- Ferramenta em **Python** para análise dos resultados e geração de gráficos;
- Arquivos JSON de configuração para recriar automaticamente os gráficos principais;
- Scripts para geração automatizada dos gráficos no Windows PowerShell e no Linux/WSL;
- Dockerfiles para execução em container do analisador e do simulador.

O artefato foi organizado para permitir que revisores:

1. Inspecionem o código-fonte do simulador;
2. Executem uma simulação local usando o JAR já incluído;
3. Recriem os principais gráficos do artigo a partir dos CSVs fornecidos;
4. Executem a geração de gráficos de forma automatizada por linha de comando;
5. Usem Docker como alternativa de ambiente reprodutível.

---

# Estrutura do README.md

Este README está organizado da seguinte forma:

1. [Selos considerados](#selos-considerados)
2. [Informações básicas](#informações-básicas)
3. [Estrutura do repositório](#estrutura-do-repositório)
4. [Ambiente de execução](#ambiente-de-execução)
5. [Dependências](#dependências)
6. [Preocupações com segurança](#preocupações-com-segurança)
7. [Instalação](#instalação)
8. [Quick Start para revisores](#quick-start-para-revisores)
9. [Uso com Docker](#uso-com-docker)
10. [Teste mínimo](#teste-mínimo)
11. [Experimentos](#experimentos)
12. [Sustentabilidade do código](#sustentabilidade-do-código)
13. [Limitações conhecidas](#limitações-conhecidas)
14. [LICENÇA](#licença)

---

<details>
<summary><strong>Selos considerados</strong></summary>

# Selos considerados

Os selos considerados para o processo de avaliação são:

- **Artefatos Disponíveis (Selo D)**
- **Artefatos Funcionais (Selo F)**
- **Artefatos Sustentáveis (Selo S)**
- **Experimentos Reprodutíveis (Selo R)**

---

</details>

<details>
<summary><strong>Informações básicas</strong></summary>

# Informações básicas

## Informações do projeto

- **Projeto:** SNetS-SDM-SBRC26
- **Artigo:** *IMPA: Novo algoritmo para atribuição de potência de forma adaptativa em SDM-EONs*
- **Instituições:** Universidade Federal do Piauí (UFPI) e Instituto Federal do Piauí (IFPI)
- **Trilha:** Trilha Principal
- **Linguagens principais:** Java e Python
- **Artefato principal:** Simulador Java + conjuntos de simulação + analisador gráfico em Python
- **Licença:** MIT

## Objetivo do artefato

O artefato disponibiliza um simulador e um conjunto de resultados experimentais para avaliação de algoritmos em cenários de redes ópticas elásticas com multiplexação por divisão espacial, além de uma ferramenta auxiliar para análise gráfica dos resultados.

O artefato apoia a avaliação do algoritmo **IMPA**, permitindo:

- Examinar a implementação do algoritmo e do simulador;
- Executar cenários locais;
- Verificar resultados nas topologias **NSFNet** e **USA**;
- Recriar gráficos de **PBC** (*Probabilidade de Bloqueio de Circuito*) e **PBBR** (*Probabilidade de Bloqueio de BitRate*);
- Comparar o IMPA com algoritmos clássicos e adaptativos da literatura.

## O que o revisor provavelmente vai usar

| Caminho | Uso |
|---|---|
| `simulations/SNetS-SDM-SBRC26.jar` | Execução do simulador Java em modo local |
| `simulations/Data_Analyzer/SimulationDataAnalyzer.py` | Analisador gráfico Python, com GUI e modo CLI/headless |
| `simulations/Data_Analyzer/README_data_analyzer.md` | Documentação específica do analisador de dados |
| `simulations/Data_Analyzer/configs/` | Configurações JSON para recriar os gráficos do artigo |
| `simulations/Data_Analyzer/generate_article_graphs.ps1` | Script PowerShell para gerar todos os gráficos |
| `simulations/Data_Analyzer/generate_article_graphs.sh` | Script Linux/WSL/Docker para gerar todos os gráficos |
| `simulations/Data_Analyzer/USA/` | CSVs organizados da topologia USA |
| `simulations/Data_Analyzer/NSFNet/` | CSVs organizados da topologia NSFNet |
| `simulations/USA_sims/` | Cenários completos de simulação para USA |
| `simulations/NSFNet_sims/` | Cenários completos de simulação para NSFNet |
| `src/main/java/` | Código-fonte Java do simulador |
| `Dockerfile.analyzer` | Container para o analisador Python |
| `Dockerfile.simulator` | Container para o simulador Java |

---

</details>

<details>
<summary><strong>Estrutura do repositório</strong></summary>

# Estrutura do repositório

Estrutura principal:

```text
SNetS-SDM-SBRC26/
├── src/
│   └── main/
│       └── java/
│           ├── gprmcsa/
│           ├── measurement/
│           ├── network/
│           ├── request/
│           ├── simulationControl/
│           ├── simulator/
│           └── util/
├── simulations/
│   ├── Data_Analyzer/
│   │   ├── NSFNet/
│   │   ├── USA/
│   │   ├── configs/
│   │   │   ├── config_article_PBC_log_USA.json
│   │   │   ├── config_article_PBC_log_NSFNet.json
│   │   │   ├── config_article_PBBR_log_USA.json
│   │   │   ├── config_article_PBBR_log_NSFNet.json
│   │   │   ├── config_article_PBC_Comp_USA.json
│   │   │   └── config_article_PBC_Comp_NSFNet.json
│   │   ├── generate_article_graphs.ps1
│   │   ├── generate_article_graphs.sh
│   │   ├── README_data_analyzer.md
│   │   ├── requirements.txt
│   │   └── SimulationDataAnalyzer.py
│   ├── NSFNet_sims/
│   ├── USA_sims/
│   ├── SNetS-SDM-SBRC26.jar
│   └── run_SNetS-SDM-SBRC26_jar.bat
├── Dockerfile.analyzer
├── Dockerfile.simulator
├── pom.xml
├── LICENSE
└── README.md
```

## Descrição das principais pastas e arquivos

### Raiz do repositório

| Caminho | Descrição |
|---|---|
| `README.md` | Documento principal do artefato. Contém visão geral, dependências, instalação, testes mínimos, uso com Docker e instruções de reprodução. |
| `LICENSE` | Licença do projeto. |
| `pom.xml` | Arquivo Maven do projeto Java. É necessário apenas para quem deseja compilar o simulador a partir do código-fonte. |
| `Dockerfile.analyzer` | Dockerfile usado para criar a imagem do analisador Python. |
| `Dockerfile.simulator` | Dockerfile usado para criar a imagem do simulador Java com Java 8. |

### Código-fonte Java

| Caminho | Descrição |
|---|---|
| `src/main/java/gprmcsa/` | Implementa algoritmos e procedimentos relacionados a roteamento, alocação de recursos, modulação, núcleo, espectro e potência. |
| `src/main/java/measurement/` | Contém classes de medição, estatísticas e coleta de métricas da simulação. |
| `src/main/java/network/` | Contém modelos da rede óptica, como nós, enlaces, rotas, recursos espectrais, núcleos e topologias. |
| `src/main/java/request/` | Contém classes relacionadas às requisições de conexão e demandas de tráfego. |
| `src/main/java/simulationControl/` | Contém classes de controle da simulação, leitura de configurações, validação de parâmetros e organização da execução. |
| `src/main/java/simulator/` | Contém o núcleo do simulador e a lógica principal de execução baseada em eventos. |
| `src/main/java/util/` | Contém classes utilitárias usadas por diferentes partes do simulador. |

### Diretório de simulações e análise

| Caminho | Descrição |
|---|---|
| `simulations/` | Diretório que agrupa o JAR executável, cenários de simulação, resultados e ferramentas de análise. |
| `simulations/SNetS-SDM-SBRC26.jar` | JAR já empacotado do simulador. É o caminho recomendado para executar uma simulação sem recompilar o código Java. |
| `simulations/run_SNetS-SDM-SBRC26_jar.bat` | Script auxiliar para execução do JAR no Windows. |
| `simulations/USA_sims/` | Conjuntos completos de simulação para a topologia USA, organizados por algoritmo e parâmetros experimentais. |
| `simulations/NSFNet_sims/` | Conjuntos completos de simulação para a topologia NSFNet, organizados por algoritmo e parâmetros experimentais. |
| `simulations/Data_Analyzer/` | Ferramenta Python usada para analisar CSVs e gerar gráficos do artigo. |

### Analisador de dados e geração de gráficos

| Caminho | Descrição |
|---|---|
| `simulations/Data_Analyzer/SimulationDataAnalyzer.py` | Programa principal do analisador. Pode ser executado com GUI ou em modo CLI/headless usando `--config`. |
| `simulations/Data_Analyzer/README_data_analyzer.md` | Documentação específica do analisador de dados, incluindo GUI, CLI, Docker, configs e solução de problemas. |
| `simulations/Data_Analyzer/requirements.txt` | Lista de dependências Python necessárias para executar o analisador fora do Docker. |
| `simulations/Data_Analyzer/generate_article_graphs.ps1` | Script PowerShell para gerar automaticamente todos os gráficos principais do artigo no Windows. |
| `simulations/Data_Analyzer/generate_article_graphs.sh` | Script Bash para gerar automaticamente todos os gráficos principais do artigo no Linux, WSL ou Docker. |
| `simulations/Data_Analyzer/USA/` | CSVs organizados para geração dos gráficos da topologia USA. |
| `simulations/Data_Analyzer/NSFNet/` | CSVs organizados para geração dos gráficos da topologia NSFNet. |
| `simulations/Data_Analyzer/configs/` | Arquivos JSON com as configurações usadas para recriar automaticamente os gráficos principais do artigo. |

### Arquivos de configuração dos gráficos do artigo

| Caminho | Descrição |
|---|---|
| `simulations/Data_Analyzer/configs/config_article_PBC_log_USA.json` | Gera o gráfico de Probabilidade de Bloqueio de Circuito em escala logarítmica para a topologia USA. |
| `simulations/Data_Analyzer/configs/config_article_PBC_log_NSFNet.json` | Gera o gráfico de Probabilidade de Bloqueio de Circuito em escala logarítmica para a topologia NSFNet. |
| `simulations/Data_Analyzer/configs/config_article_PBBR_log_USA.json` | Gera o gráfico de Probabilidade de Bloqueio de BitRate em escala logarítmica para a topologia USA. |
| `simulations/Data_Analyzer/configs/config_article_PBBR_log_NSFNet.json` | Gera o gráfico de Probabilidade de Bloqueio de BitRate em escala logarítmica para a topologia NSFNet. |
| `simulations/Data_Analyzer/configs/config_article_PBC_Comp_USA.json` | Gera o gráfico percentual dos componentes de bloqueio para a topologia USA. |
| `simulations/Data_Analyzer/configs/config_article_PBC_Comp_NSFNet.json` | Gera o gráfico percentual dos componentes de bloqueio para a topologia NSFNet. |

### Arquivos gerados durante a execução

| Caminho | Descrição |
|---|---|
| `simulations/Data_Analyzer/outputs/` | Diretório criado pelo analisador para armazenar gráficos e tabelas geradas. |
| `simulations/Data_Analyzer/outputs/article/` | Diretório padrão usado pelos scripts para salvar os gráficos do artigo. |
| `*.svg` | Gráficos exportados pelo analisador. |
| `*_ci.csv` | Tabelas de intervalo de confiança geradas para gráficos de linha. |
| `*_best.csv` | Tabelas com o melhor algoritmo por carga, geradas para gráficos de linha. |

---

</details>

<details>
<summary><strong>Ambiente de execução</strong></summary>

# Ambiente de execução

## Ambiente principal usado pelos autores

O ambiente principal usado para desenvolvimento e execução local do simulador foi:

- **Sistema operacional:** Windows 11 Pro
- **Java:** Java 8
- **Python:** Python 3.10+ / 3.11
- **Execução do simulador:** via JAR local
- **Análise de resultados:** via `SimulationDataAnalyzer.py`

## Ambiente de referência para reprodução

Para reduzir variações de ambiente entre revisores, foram documentados dois caminhos:

1. **Windows 11 Pro com ambiente virtual Python**, correspondente ao ambiente local principal usado pelos autores;
2. **Docker em Ubuntu/Linux/WSL**, usando imagens com dependências controladas.

Ambiente Linux de referência:

- **Ubuntu 24.04 LTS** ou ambiente Linux equivalente;
- **Docker Engine** no Ubuntu/WSL ou Docker Desktop;
- Imagens Docker:
  - `python:3.11-slim-bookworm` para o analisador;
  - `eclipse-temurin:8-jre` para o simulador.

## Configuração de hardware recomendada

Para uma avaliação confortável:

- **CPU:** 10 núcleos ou mais;
- **RAM:** 16 GB ou mais;
- **Armazenamento livre:** 5 GB ou mais.

Essa configuração é uma recomendação prática. O artefato não impõe esses valores como requisitos rígidos para os testes mínimos.

Também é possível ajustar a quantidade de threads usadas pelo simulador no parâmetro `threads`, localizado no arquivo `simulation` dentro das pastas de configuração das simulações.

---

</details>

<details>
<summary><strong>Dependências</strong></summary>

# Dependências

Esta seção separa dependências obrigatórias, opcionais e não necessárias para evitar ambiguidade.

## Dependências obrigatórias para clonar o repositório

É necessário ter **Git** instalado.

### Ubuntu 24.04

```bash
sudo apt update
sudo apt install -y git
```

### Windows 11 Pro

Instale o **Git for Windows** a partir de:

```text
https://git-scm.com/download/win
```

Depois confirme:

```powershell
git --version
```

## Dependências obrigatórias para executar o simulador Java localmente

Para usar o JAR já incluído:

- **Java 8**
- Arquivo `simulations/SNetS-SDM-SBRC26.jar`
- Um diretório de simulação válido em:
  - `simulations/USA_sims/`
  - `simulations/NSFNet_sims/`

### Ubuntu 24.04

Em Ubuntu 24.04, o pacote `openjdk-8-jdk` pode não estar disponível diretamente nos repositórios padrão. Para evitar esse problema, o caminho recomendado para reprodução em Linux é usar Docker com a imagem `eclipse-temurin:8-jre`.

Se desejar instalar Java localmente, use uma distribuição Java 8, como Temurin/OpenJDK 8, e confirme:

```bash
java -version
```

### Windows 11 Pro

Instale uma distribuição Java 8, por exemplo Temurin/OpenJDK 8, e confirme:

```powershell
java -version
```

## Dependências opcionais para compilar o código-fonte Java

Maven é necessário **somente** se o revisor quiser compilar o projeto a partir do código-fonte.

- **Maven 3.x**
- **Java 8 JDK**

### Ubuntu 24.04

```bash
sudo apt update
sudo apt install -y maven
mvn -version
```

### Windows 11 Pro

Instale Maven 3.x e confirme:

```powershell
mvn -version
```

Para a avaliação padrão, **não é necessário compilar o projeto**, pois o JAR já está incluído.

## Dependências obrigatórias para gerar gráficos localmente

Para usar o analisador Python fora do Docker:

- Python 3.10+;
- Ambiente virtual Python;
- Bibliotecas em `simulations/Data_Analyzer/requirements.txt`.

### Ubuntu 24.04

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip python3-tk
```

### Windows 11 Pro

Instale Python 3.10+ pelo instalador oficial ou pela Microsoft Store. O Tkinter normalmente já vem incluído no Python para Windows.

Confirme:

```powershell
python --version
```

## Dependências recomendadas para reprodução via Docker

Para usar Docker:

- Docker Engine em Linux/WSL; ou
- Docker Desktop no Windows.

Com Docker, não é necessário instalar localmente as bibliotecas Python nem o Java dentro do sistema hospedeiro, além do próprio Docker.

## Dependências não necessárias para a avaliação padrão

Não são necessárias para os testes mínimos e para a reprodução dos gráficos principais:

- Firebase;
- Credenciais privadas;
- Servidor distribuído;
- Modo cliente/servidor LAN;
- Infraestrutura externa de nuvem.

---

</details>

<details>
<summary><strong>Preocupações com segurança</strong></summary>

# Preocupações com segurança

O fluxo recomendado para avaliação usa apenas:

- Simulador Java em modo local;
- Analisador Python;
- CSVs já incluídos no repositório;
- Docker local, opcionalmente.

O código possui suporte a modos com Firebase e cliente/servidor, mas esses modos **não são necessários** para a avaliação padrão.

Recomendações:

- Não execute modos que dependam de credenciais privadas;
- Não adicione chaves Firebase ao repositório;
- Use ambiente isolado, como Docker, WSL ou máquina virtual, caso deseje maior isolamento.

---

</details>

<details>
<summary><strong>Instalação</strong></summary>

# Instalação

## Opção A — Windows 11 Pro com ambiente virtual Python

Este é o ambiente principal usado pelos autores para execução local.

### 1. Clonar o repositório

```powershell
git clone https://github.com/alexandrefontinele/SNetS-SDM-SBRC26.git
cd SNetS-SDM-SBRC26
```

### 2. Verificar Java

```powershell
java -version
```

O ideal é usar Java 8.

### 3. Criar ambiente virtual Python para o analisador

```powershell
cd simulations\Data_Analyzer
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Se o PowerShell bloquear a ativação do ambiente virtual, execute apenas na sessão atual:

```powershell
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\.venv\Scripts\Activate.ps1
```

### 4. Abrir o analisador gráfico

```powershell
python .\SimulationDataAnalyzer.py
```

### 5. Voltar para a raiz do repositório

```powershell
cd ..\..
```

## Opção B — Ubuntu 24.04 com ambiente virtual Python

```bash
sudo apt update
sudo apt install -y git python3 python3-venv python3-pip python3-tk
git clone https://github.com/alexandrefontinele/SNetS-SDM-SBRC26.git
cd SNetS-SDM-SBRC26/simulations/Data_Analyzer
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python ./SimulationDataAnalyzer.py
```

## Opção C — Docker no Ubuntu/WSL/Windows

Este é o caminho recomendado quando se deseja reduzir problemas de versão de sistema operacional e dependências locais.

A partir da raiz do repositório:

```bash
docker build -f Dockerfile.analyzer -t snets-analyzer .
docker build -f Dockerfile.simulator -t snets-simulator .
```

Os comandos de execução estão na seção [Uso com Docker](#uso-com-docker).

---

</details>

<details>
<summary><strong>Quick Start para revisores</strong></summary>

# Quick Start para revisores

## Caminho rápido 1 — Recriar todos os gráficos do artigo a partir dos CSVs

### PowerShell no Windows 11 Pro

```powershell
git clone https://github.com/alexandrefontinele/SNetS-SDM-SBRC26.git
cd SNetS-SDM-SBRC26\simulations\Data_Analyzer
python -m venv .venv
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
.\generate_article_graphs.ps1
```

Saída esperada:

```text
outputs/article/
```

Exemplos de arquivos gerados:

```text
PBC_log_USA.svg
PBC_log_NSFNet.svg
PBBR_log_USA.svg
PBBR_log_NSFNet.svg
PBC_Comp_USA.svg
PBC_Comp_NSFNet.svg
```

## Caminho rápido 2 — Recriar todos os gráficos usando Docker

A partir da raiz do repositório:

```bash
docker build -f Dockerfile.analyzer -t snets-analyzer .

docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  bash ./generate_article_graphs.sh
```

Saída esperada:

```text
simulations/Data_Analyzer/outputs/article/
```

## Caminho rápido 3 — Executar uma simulação local com o JAR

Na raiz do repositório:

```bash
java -jar simulations/SNetS-SDM-SBRC26.jar simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

O simulador deve:

- Ler os arquivos de configuração;
- Validar a configuração;
- Inicializar a simulação local;
- Exibir progresso no terminal.

## Caminho rápido 4 — Executar uma simulação local com Docker

```bash
docker build -f Dockerfile.simulator -t snets-simulator .

docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace \
  snets-simulator \
  simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

---

</details>

<details>
<summary><strong>Uso com Docker</strong></summary>

# Uso com Docker

Os comandos desta seção devem ser executados a partir da raiz do repositório.

## Analisador Python

### Build da imagem

```bash
docker build -f Dockerfile.analyzer -t snets-analyzer .
```

### Gerar todos os gráficos do artigo

```bash
docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  bash ./generate_article_graphs.sh
```

### Gerar apenas um gráfico

```bash
docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  python ./SimulationDataAnalyzer.py --config ./configs/config_article_PBC_log_USA.json
```

### Listar métricas detectadas

```bash
docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  python ./SimulationDataAnalyzer.py --config ./configs/config_article_PBC_log_USA.json --list-metrics
```

## Simulador Java

### Build da imagem

```bash
docker build -f Dockerfile.simulator -t snets-simulator .
```

### Executar cenário de exemplo

```bash
docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace \
  snets-simulator \
  simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

---

</details>

<details>
<summary><strong>Teste mínimo</strong></summary>

# Teste mínimo

## Teste mínimo 1 — Gerar um gráfico por linha de comando

Entre na pasta do analisador:

```bash
cd simulations/Data_Analyzer
```

Execute:

```bash
python SimulationDataAnalyzer.py --config configs/config_article_PBC_log_USA.json
```

Resultado esperado:

```text
outputs/article/PBC_log_USA.svg
outputs/article/PBC_log_USA_ci.csv
outputs/article/PBC_log_USA_best.csv
```

Esse teste é preferível para revisão rápida porque não depende de interação manual com a GUI.

## Teste mínimo 2 — Abrir a interface gráfica

```bash
cd simulations/Data_Analyzer
python SimulationDataAnalyzer.py
```

Resultado esperado:

- A interface gráfica deve abrir;
- O revisor deve conseguir carregar CSVs;
- O revisor deve conseguir selecionar métricas;
- O revisor deve conseguir gerar gráficos.

## Teste mínimo 3 — Executar uma simulação local

Na raiz do repositório:

```bash
java -jar simulations/SNetS-SDM-SBRC26.jar simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

Resultado esperado:

- Leitura do diretório de configuração;
- Validação da configuração;
- Inicialização da simulação;
- Saída textual de progresso no terminal.

## Teste mínimo 4 — Executar teste com Docker

```bash
docker build -f Dockerfile.analyzer -t snets-analyzer .

docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  python ./SimulationDataAnalyzer.py --config ./configs/config_article_PBC_log_USA.json
```

---

</details>

<details>
<summary><strong>Experimentos</strong></summary>

# Experimentos

Esta seção descreve formas de reproduzir as principais reivindicações experimentais do artigo.

## Tabela-resumo das reivindicações

| Reivindicação | Como reproduzir | Resultado esperado |
|---|---|---|
| Recriar os gráficos PBC/PBBR e componentes | Rodar `generate_article_graphs.ps1` ou `generate_article_graphs.sh` | Arquivos SVG e CSV em `outputs/article/` |
| Executar simulação local válida | Rodar o JAR com um diretório completo de `USA_sims/` ou `NSFNet_sims/` | Inicialização correta e progresso no terminal |
| Usar ambiente reprodutível | Rodar analisador e simulador via Docker | Mesmos comandos funcionam sem instalar dependências Python/Java localmente |
| Inspecionar implementação | Examinar `src/main/java/` | Código modular e documentado |

## Reivindicação 1 — Recriar os principais gráficos do artigo

### Objetivo

Recriar automaticamente os gráficos principais usando os CSVs já incluídos.

### Procedimento no Windows

```powershell
cd simulations\Data_Analyzer
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\generate_article_graphs.ps1
```

### Procedimento no Linux/WSL

```bash
cd simulations/Data_Analyzer
chmod +x ./generate_article_graphs.sh
./generate_article_graphs.sh
```

### Procedimento via Docker

Na raiz do repositório:

```bash
docker build -f Dockerfile.analyzer -t snets-analyzer .

docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace/simulations/Data_Analyzer \
  snets-analyzer \
  bash ./generate_article_graphs.sh
```

### Resultado esperado

Arquivos em:

```text
simulations/Data_Analyzer/outputs/article/
```

Incluindo:

```text
PBC_log_USA.svg
PBC_log_NSFNet.svg
PBBR_log_USA.svg
PBBR_log_NSFNet.svg
PBC_Comp_USA.svg
PBC_Comp_NSFNet.svg
```

Também podem ser gerados:

```text
*_ci.csv
*_best.csv
```

para os gráficos de linha.

### Recursos esperados

- RAM: ~1–2 GB;
- Tempo: poucos segundos até alguns minutos, dependendo do ambiente.

## Reivindicação 2 — Executar uma simulação local

### Objetivo

Executar pelo menos uma configuração completa de simulação local.

### Diretório de exemplo

```text
simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

Esse diretório contém os arquivos de configuração usados pelo simulador, como:

- `simulation`: parâmetros gerais da execução;
- `network`: configuração da topologia, enlaces, núcleos, modulações e recursos;
- `others`: parâmetros adicionais dos algoritmos;
- `physicalLayer`: parâmetros de camada física e QoT;
- `traffic`: configuração do tráfego e das demandas.

### Procedimento com JAR

```bash
java -jar simulations/SNetS-SDM-SBRC26.jar simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

### Procedimento com Docker

```bash
docker build -f Dockerfile.simulator -t snets-simulator .

docker run --rm \
  --mount type=bind,src="$(pwd)",dst=/workspace \
  -w /workspace \
  snets-simulator \
  simulations/USA_sims/IMPA/USA_IMPA_HXT_mo_0_00_mx_0_00
```

### Resultado esperado

O simulador deve:

- Carregar arquivos do diretório;
- Validar a configuração;
- Iniciar execução local;
- Produzir saída textual com progresso.

### Recursos esperados

- RAM: depende do cenário, mas o teste mínimo deve ser executável em uma máquina comum de avaliação;
- Tempo: variável conforme número de requisições, replicações e threads configuradas.

## Reivindicação 3 — Organização experimental

O repositório contém:

- Topologias `USA_sims` e `NSFNet_sims`;
- Subpastas por algoritmo;
- Configurações parametrizadas;
- CSVs organizados para análise;
- Configs JSON de geração de gráficos.

Essa organização permite rastrear cada resultado até sua topologia, algoritmo e parâmetros experimentais.

## Reivindicação 4 — Código-fonte modular e documentado

O código Java está organizado em módulos como:

- `gprmcsa`: implementa a lógica dos algoritmos de alocação de recursos, incluindo roteamento, espectro, núcleo, modulação, potência, banda de guarda, regeneração, realocação e grooming;
- `measurement`: reúne as métricas e estatísticas da simulação, como probabilidade de bloqueio, bloqueio por taxa de bits, fragmentação, consumo de energia e utilização de recursos;
- `network`: modela os principais elementos da rede e do plano de controle, como nós, enlaces, núcleos, espectro, circuitos, malha e camada física;
- `request`: define as requisições de conexão usadas nas simulações;
- `simulationControl`: controla a execução da simulação, leitura das configurações, gerenciamento dos experimentos e processamento dos resultados;
- `simulator`: contém o núcleo do simulador baseado em eventos;
- `util`: reúne classes utilitárias de apoio usadas em diferentes partes do projeto.

A documentação Javadoc e os comentários de API devem ser mantidos em **inglês** ao longo do projeto para consistência.

---

</details>

<details>
<summary><strong>Sustentabilidade do código</strong></summary>

# Sustentabilidade do código

Para apoiar o **Selo S**, o projeto adota:

- Organização modular em pacotes Java;
- JAR empacotado para execução direta;
- Arquivos de configuração separados dos dados;
- CSVs organizados por topologia;
- Analisador Python com modo GUI e modo CLI/headless;
- Configs JSON para geração reprodutível de gráficos;
- Dockerfiles para isolar dependências;
- Licença MIT.

Política de documentação:

- Javadocs e comentários de API no código Java devem estar em **inglês**;
- README e documentação de uso podem estar em **português**, considerando o público do SBRC;

---

</details>

<details>
<summary><strong>Limitações conhecidas</strong></summary>

# Limitações conhecidas

- O ambiente principal de execução local usado pelos autores foi **Windows 11 Pro**;
- O fluxo Docker foi testado em ambiente Ubuntu/Linux/WSL;
- O Java recomendado para o simulador é **Java 8**;
- Alguns cenários completos podem demandar tempo maior que o ideal para revisão rápida;
- O fluxo recomendado para avaliação rápida é usar primeiro os CSVs já incluídos e gerar os gráficos automaticamente;
- Modos com Firebase não fazem parte do fluxo recomendado;
- A interface gráfica depende de Tkinter, mas o modo CLI/headless permite gerar gráficos sem interação manual;
- Em Ubuntu 24.04, para Java 8, o caminho mais robusto é usar Docker com `eclipse-temurin:8-jre`.

---

</details>

<details>
<summary><strong>LICENÇA</strong></summary>

# LICENÇA

Este projeto está licenciado sob a licença **MIT**. Consulte o arquivo:

```text
LICENSE
```

</details>
