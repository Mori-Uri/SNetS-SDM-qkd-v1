# Parte 37: bloqueio por núcleo e causa "Outros"

## O problema

Quando o algoritmo não acha faixa em nenhuma modulação, o `spcsav2` grava no circuito um núcleo SORTEADO (código
clássico, sem semente). Esse núcleo nunca foi tentado, mas o SNetS o usava para duas coisas:

- **Atribuir o bloqueio a um núcleo.** O sorteio podia cair no núcleo quântico ou num núcleo desativado, que então
  apareciam com 100 % de bloqueio sem nunca receber circuito.
- **Diagnosticar a causa.** O diagnóstico rodava nesse núcleo sorteado, e daí saíam "Outros", "Falta de espectro
  livre" e parte da "Fragmentação" sem relação com o que aconteceu.

## A correção no BlockingDetails.csv

Só observação; nenhuma decisão muda.

- Essas requisições não são mais atribuídas a núcleo nenhum.
- A causa delas é diagnosticada em todos os núcleos utilizáveis, sem o quântico e os desativados:
  - **Lack of free spectrum:** nenhum núcleo tem slots livres suficientes em todos os enlaces da rota;
  - **Fragmentation:** há slots suficientes, mas não contíguos ou não alinhados;
  - **Free band in a core not tried (causa nova):** havia uma faixa livre num núcleo que o algoritmo não tentou. O
    spcsav2 + ABNE testa um núcleo por modulação, em rodízio.
- Falta de transmissor ou receptor continua com a causa do SNetS.

Exemplo, na Metropolitana com Q25_R0 e 3400 Erlangs: "Outros" e "Falta de espectro" foram a 0, a fragmentação caiu de
1,31 % para 0,48 %, e 1,61 % (16 % dos bloqueios) passou a "havia faixa em núcleo não tentado".

## O que não mudou

- O BlockingProbability.csv clássico do SNetS continua igual; o caso clássico de teste ficou idêntico byte a byte.
- As 21 verificações passam.
- Os resultados já rodados não mudam. Para ver as causas novas é preciso rodar de novo; o launcher 1.15.0 já esconde os
  núcleos não usados também nos resultados antigos.
