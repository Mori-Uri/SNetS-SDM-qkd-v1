# Parte 28: mudanças do coorientador e "Gerar JAR" no launcher

## Commit ec65200 ("Ajustes no XT.", Alexandre Fontinele, 15/10)

- **`Crosstalk.java` e `PhysicalLayer.java`.** No modelo de crosstalk junto com os outros efeitos
  (`crosstalkModel = 1`, `XT_TOGETHER`), o crosstalk passa a ser calculado em cada enlace, com o núcleo e o espectro
  daquele enlace (`calculateCrosstalkPowerInLink`), em vez de um valor da rota inteira. No modelo Johannisson
  (`physicalLayerModel = 1`), ele é dividido pelo número de modos de polarização.
- **`Amplifier.setNoiseFigure`.** Corrigido: o valor linear agora é calculado a partir do valor em dB. Antes era
  calculado a partir do valor linear antigo. O simulador não chama esse método hoje.
- **Arquivos removidos:** `network_qkd_snippet.txt` e `routesByPar.txt`.
- **Conflitos com a Parte 27:** nenhum. Os dois commits não têm arquivos em comum, e o código QKD não usa os métodos
  alterados.
- **Efeito nos resultados.** O mesmo caso de teste (com `crosstalkModel = 0` e `physicalLayerModel = 0`) deu CSVs
  idênticos com o JAR antigo e com o novo. As mudanças só aparecem com `crosstalkModel = 1` ou
  `physicalLayerModel = 1`.
- **Verificação.** O `SNetS-SDM-QKD.jar` foi gerado de novo com essas mudanças. As 21 verificações QKD passaram.

## Launcher 1.9.0: botão "Gerar JAR"

- **Classe nova.** O botão chama a classe `launcher.JarBuilder`, que roda como uma execução comum.
- **O que ela faz:**
  - compila todos os `.java` de `src/main/java`, menos `simulationControl/Main.java`, contra um JAR base;
  - monta o JAR novo a partir do JAR base, trocando as classes do projeto pelas recompiladas;
  - remove as classes antigas do projeto que não têm mais código-fonte;
  - guarda o JAR anterior em `simulations/backup_jar`;
  - opcionalmente, roda as verificações QKD.
- **Por que `Main.java` fica de fora.** Ele usa uma versão do Firebase mais nova que a do JAR atual e só compila com o
  Maven. Por isso a classe `Main` vem do JAR base.
- **Linha de comando:**
  `java -cp launcher/snets-launcher.jar launcher.JarBuilder --src src/main/java --base simulations/SNetS-SDM-QKD.jar --out simulations/SNetS-SDM-QKD.jar [--verify]`.
