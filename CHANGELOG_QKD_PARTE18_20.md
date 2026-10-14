# Entrega QKD - Partes 18, 19 e 20

Esta entrega foi aplicada diretamente sobre o arquivo `SNetS-SDM-qkd-v1.rar` enviado pelo usuario. A estrutura original `simulations/` foi preservada; nao foi criada uma pasta paralela chamada `scenario`.

## Arquivos modificados

1. `src/main/java/qkd/verification/QKDArticleBaseReferenceVerification.java`
   - caminho padrao da verificacao do artigo-base atualizado para `simulations/QKD_reference/Dibaj_MF`.

2. `simulations/NSFNet_C2_QKD_NoXT/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

3. `simulations/NSFNet_C3_QKD_XT/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

4. `simulations/NSFNet_QKD_test/simulation`
   - `coreAndSpectrumAssignment` alterado de `csbasdm` para `qabne`.

5. `target/classes/`
   - recompilado para refletir o codigo-fonte atual.

## Arquivos/pastas adicionados

- `simulations/SNetS-SDM-QKD.jar`
  - executavel separado para o addon QKD.
  - o JAR classico original `SNetS-SDM-SBRC26.jar` foi mantido.

- `simulations/run_SNetS-SDM-QKD_jar.bat`
  - executa um cenario QKD pelo nome.

- `simulations/QKD_reference/`
  - dados, traces, scripts e resultados da verificacao do artigo-base Dibaj MF.

- `simulations/QKD_experiments/`
  - Parte 20: preparacao automatica de sweep QTND, smoke test, execucao C1/C2/C3, analise estatistica e geracao de graficos.

- `README_QKD_ADDON.md`
  - guia rapido da arquitetura addon e execucao.

- `CLASSICAL_BASE_SHA256.txt`
  - hashes dos principais componentes classicos preservados.

- `docs/VALIDACAO_QKD_PARTES1_A_20.txt`
  - saida dos verificadores executados nesta entrega.

- `docs/Estado_Atual_Implementacao_QKD_SNetS.docx`
  - documento tecnico com tudo que foi implementado, objetivo final e pendencias.

## Arquivos removidos

**Nenhum arquivo-fonte classico foi removido.**

O algoritmo classico `CSBASDM/ABNE` e o factory classico `GPRMCSA` nao foram alterados nesta entrega. A adaptacao QKD usa `qkd.algorithms.coreSpectrumAssignment.QABNE` e `QKDAlgorithmFactory`.

## Validacoes executadas

- Compilacao Java 8 da arvore atual: OK.
- Todos os verificadores QKD disponiveis: OK.
- Parte 18, isolamento ABNE/qABNE: OK.
- Parte 19, artigo-base: 300/300 observacoes Q-TRx iguais a reconstrucao Python.
- Smoke C1/C2/C3 com JAR QKD: os tres cenarios chegaram a `Finish!`.
- Gerador de sweep QTND: 20 cenarios gerados corretamente (C2/C3 x 10 QTNDs).
