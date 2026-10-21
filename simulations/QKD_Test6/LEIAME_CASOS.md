# QKD_Test6: casos do estudo QKD (NSFNet, 14 nós, enlaces de 300 a 1800 km)

Gerado automaticamente por `launcher/.work/gen/make_tests.py`.

- **Topologia:** NSFNet, 14 nós, enlaces de 300 a 1800 km. **Carga:** 150 Erlangs no 1º ponto, +130 por ponto, 8 pontos (150, 280, 410, 540, 670, 800, 930, 1060).
  Escolhida para o bloqueio ir de cerca de 0,1 % a cerca de 10 %.
- **Simulação (igual em todos):** 30 000 requisições por ponto, 3 replicações, as mesmas sementes.
  Os clássicos usam `csbasdm`; os casos com QKD (inclusive `Q25_R0`) usam `qabne`.
- **Tráfego:** todos os pares, taxas de 100 a 500 Gb/s na proporção 5:4:3:2:1.

## Nomes

- `Classico`: QKD desligado, 7 núcleos, `csbasdm`.
- `C_1Block`: QKD desligado, núcleo 1 desativado (6 núcleos, como os casos QKD), `csbasdm`.
- `Q25_R0`: QKD ligado (núcleo 1 reservado) com 0 % protegidas e `qabne`: a referência clássica com o mesmo algoritmo.
- `Q25_R25_C0`: igual ao `Q25_R25`, com o núcleo quântico no 0 (central).
- `Q<QTND>_R<fração>`: caso base com QTND em km e fração protegida em %. Nele, a chave é sorteada entre 1, 2 e 4 Mbit/s,
  o PICh vale 0, o comprimento de onda é fixed, o crosstalk está ligado, não há rotas alternativas e os QTx/QRx são
  ilimitados.
- Variações, cada uma mudando só uma coisa em relação ao seu caso base:

| Sufixo | O que muda |
|---|---|
| `_fixed` | chave fixa em 2 Mbit/s |
| `_fixed10` | chave fixa em 10 Mbit/s (demanda alta: mais QChs e Q-TRx por enlace) |
| `_bitrate` | chave pela taxa clássica (100–200 Gb/s → 1 Mbit/s, 300–400 → 2, 500 → 4) |
| `_PICh1` | PICh de 1 slot por enlace |
| `_grid` | comprimento de onda do QCh = λ do slot |
| `_XToff` | sem crosstalk dos canais clássicos sobre o QCh |
| `_ALT3` | até 3 rotas QKD alternativas |
| `_QTRx<n>` | no máximo n QTx e n QRx por nó |

QTND avaliados: 25 km (QTx/QRx no _QTRx: 4), 50 km (QTx/QRx no _QTRx: 4), 75 km (QTx/QRx no _QTRx: 8), 100 km (QTx/QRx no _QTRx: 16).
