# QKD Experiments - SNetS

Esta pasta pertence somente ao addon QKD. Os cenarios normais do simulador continuam em `simulations/` e os algoritmos classicos nao sao modificados.

## Arquivos

- `campaign_config.json`: lista C1/C2/C3 e valores de QTND.
- `run_smoke_qkd.py`: teste rapido de integracao em copias temporarias; nao altera os cenarios reais.
- `run_base_scenarios.bat`: executa C1, C2 e C3 com o JAR QKD.
- `prepare_qtnd_sweep.py`: cria copias C2/C3 para QTND de 5 a 50 km.
- `run_qtnd_sweep.bat`: executa as copias geradas.
- `analyze_qkd_campaign.py`: calcula medias, desvio-padrao e IC95% e gera graficos quando `matplotlib` estiver instalado.

## JARs

- `simulations/SNetS-SDM-SBRC26.jar`: JAR classico original. Foi preservado.
- `simulations/SNetS-SDM-QKD.jar`: JAR com o addon QKD. Use este para C2/C3 e para as verificacoes QKD.

## Teste rapido (smoke)

A partir de `simulations/QKD_experiments`:

```bat
python run_smoke_qkd.py
```

O script cria `smoke_work/`, reduz temporariamente requests/loadPoints/replications/threads e aumenta QKD para 20% apenas para exercitar o codigo. Esses resultados nao sao dados cientificos.

## Cenarios base

```bat
run_base_scenarios.bat
```

Executa:

- C1: `NSFNet_C1_Classical7` - 7 nucleos classicos e algoritmo classico `csbasdm`.
- C2: `NSFNet_C2_QKD_NoXT` - 6 nucleos classicos + 1 QKD, algoritmo addon `qabne`, XT->QKD desligado.
- C3: `NSFNet_C3_QKD_XT` - 6 nucleos classicos + 1 QKD, algoritmo addon `qabne`, XT->QKD ligado.

## Sweep de QTND

Gerar copias sem alterar os cenarios base:

```bat
python prepare_qtnd_sweep.py
```

Opcionalmente:

```bat
python prepare_qtnd_sweep.py --replications 10 --threads 10
```

Executar:

```bat
run_qtnd_sweep.bat
```

## Analise

```bat
python analyze_qkd_campaign.py
```

Os arquivos sao gravados em `analysis_results/`.

A campanha final do artigo ainda exige congelar a metodologia de warm-up, numero efetivo de requisicoes, quantidade final de replicacoes e criterios de precisao estatistica. Os scripts desta pasta servem para a avaliacao geral e para preparar essa campanha.
