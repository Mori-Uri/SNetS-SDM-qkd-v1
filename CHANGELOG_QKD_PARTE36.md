# Parte 36: correção do QABNE ("Core not selected.")

- **O problema.** Quando o `qabne` não achava espaço para uma modulação, ele devolvia "falhou" sem limpar a faixa nem
  gravar o núcleo no circuito. O ABNE clássico (`csbasdm`) faz as duas coisas.
- **As consequências.** O `spcsav2`, que testa as modulações da mais eficiente para a menos eficiente, tinha dois efeitos:
  - imprimia "Core not selected." a cada tentativa sem núcleo;
  - se uma modulação anterior tivesse achado faixa, testava a camada física da modulação seguinte com essa faixa antiga,
    que tem outro tamanho.
- **A correção.** O `QABNE` agora grava a faixa (nula na falha) e o núcleo também quando falha, como o `CSBASDM2`.
  A escolha do núcleo e da faixa não mudou.
- **Efeito.**
  - A mensagem sumiu.
  - Nos casos com QKD, o bloqueio muda um pouco nas cargas altas. Exemplo, na Metropolitana com 25 % protegidas:
    10,28 % → 9,22 % e 15,97 % → 16,72 %.
  - Os casos clássicos (`csbasdm`) não mudam.
  - As 21 verificações passam.
- **Atenção.** Simulações QKD rodadas com o jar anterior devem ser rodadas de novo.
