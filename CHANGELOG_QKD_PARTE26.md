# Parte 26: pedidos do coorientador

## 1. Camada física QKD separada (arquivo e classes)

- **Novo arquivo de caso `qkdPhysicalLayer`**, só com os parâmetros QKD. O `physicalLayer` fica só com a camada física
  clássica. Os parâmetros estão agrupados:
  - `source`: μ, T_s, potência e largura do QCh;
  - `protocol`: f, e_d;
  - `detector`: η_d, p_dark, T_g, η_f;
  - `fiber`: α(λ);
  - `crosstalk`: crosstalk ligado ou desligado e modelo de transferência.

  Classe: `qkd.physical.QKDPhysicalLayerConfig`.
- **Compatibilidade.** Casos antigos, com os blocos `qkdPhysical`/`qkdReceiver` dentro do `physicalLayer`, continuam
  funcionando com os mesmos resultados. Se o arquivo novo existir, ele vale e os blocos antigos são ignorados (o
  simulador avisa). Depois de uma execução, o SNetS não recoloca mais os blocos QKD no `physicalLayer`.
- **Migração.**
  - No launcher: botão "Mover para a Camada física QKD" na aba Camada física.
  - Em lote: `java -cp SNetS-SDM-QKD.jar qkd.tools.QKDPhysicalLayerMigration <pasta> [--dry-run]`. Guarda uma cópia
    `physicalLayer.before_p26`.
- **Classes.**
  - `BB84DecoyModel` continua sozinho: QBER e SKR do BB84 com estados decoy.
  - O resto da física QKD foi reunido em `qkd.physical.QKDPhysicalLayer`: potência de XT por trecho, referência ao
    receptor, conversão em p_b e avaliação de trecho, enlace e rota.
  - Esta classe substitui `QKDCrosstalkPowerCalculator`, `QKDCrosstalkNoiseModel`, `QKDCrosstalkReceiverTransfer`,
    `QKDRoutePhysicalEvaluator` e `capacity.QKDLinkChannelEvaluator`, que foram removidas.
  - As equações e a ordem das contas não mudaram. O mesmo caso deu CSVs idênticos antes e depois.
  - Continuam separadas as classes de resultado (DTOs) e o enum `QKDCrosstalkReceiverTransferMode`.
- **Novo modo de transferência `attenuated`** (Moghaddam 2021, eq. 1): P_XT · 10^(−αL/10).

## 2. PICh segmentado e compartilhado

- **`network.qkd.pichMode`.**
  - `segment` (novo padrão): o PICh acompanha o QCh. É um PICh por enlace da rota QKD, terminando nos nós, compartilhado
    pelas sessões que usam o enlace, como o pool de QChs. Classe `qkd.capacity.QKDPichPool`.
  - `route`: comportamento das Partes 23–25, com um PICh dedicado por sessão e contínuo na rota. Com `route` os
    resultados são idênticos aos da Parte 25 (conferido).
- **`network.qkd.pichSessionsPerChannel`**: sessões por PICh (0 = ilimitado).
- **Métricas novas no QKDMetrics.csv**: `Mean open PIChs`, `Peak open PIChs`.

## 3. Transmissores e receptores quânticos (QTx/QRx)

- **Uso.** Cada trecho de um QCh ativo usa um QTx no nó de origem e um QRx no nó de destino, seja ele um nó da rede ou
  um nó confiável intermediário (`QKD_TN[...]`). A contagem é feita em `QKDSharedResourceManager`.
- **Limites opcionais** (0 = ilimitado):
  - `network.qkd.qTransmittersPerNode` e `qReceiversPerNode`;
  - `qTransmittersPerTrustedNode` e `qReceiversPerTrustedNode` (−1 = igual aos nós da rede);
  - valor próprio por nó nos campos `qTransmitters` e `qReceivers` da lista `nodes`.
- **Nova causa de bloqueio.** Uma sessão sem QTx/QRx livre é bloqueada com a causa `[QTRX]`:
  - QKDMetrics: `QKD blocking probability by lack of QTx/QRx`;
  - BlockingDetails: `QKD QTx/QRx`.
- **Novas métricas por nó**: `Mean/Peak QTx/QRx in use per node`, mais os totais da rede.
- **Causas reclassificadas** nas granularidades link e span: a falta de espectro para abrir um QCh, antes contada como
  `[SKR]`, agora aparece como `[SPECTRUM]`. As decisões não mudam.

## 4. Compartilhamento dos QChs e TDM

- **`network.qkd.qchSharing`** (granularidades link e span):
  - `shared` (padrão): a sessão usa primeiro a taxa de chave que sobra nos QChs ativos;
  - `dedicated`: cada sessão abre o seu próprio QCh.
- **`tdmSlots = 0`** continua desligando o TDM. A explicação foi incluída no launcher.

## 5. Referências no código

As classes e os métodos de cálculo QKD citam no Javadoc as referências pela chave (DIBAJ24, MOGH21, LACERDA24,
NOTA-RX, MORI26, AHMED26a…). A tabela com chaves, arquivos de `docs/artigos` e onde cada referência é usada está em
`docs/REFERENCIAS_QKD.md`.

## 6. Cenários de verificação de dois artigos

Novos passos no plano de verificação (launcher: Verificação QKD › Plano de verificação):

- **Fase M**, Moghaddam, Beyranvand e Salehi (IEEE JSAC 2021):
  - **M1**: SKR × comprimento da Fig. 5, com contagem escura e XT. Os alcances batem com o artigo: 299 km contra ≈ 300 km
    e 204 km contra ≈ 205 km. Com XT de h = 6,3e-10, deu 129 km contra ≈ 115 km. Com XT de h = 4,6e-11 (p_dark =
    1e-6), deu 201 km contra ≈ 190 km.
  - **M2 e M3**: SKR média na admissão nas redes da Fig. 6. O SNetS coincide com a média independente: 43,56 contra
    43,58 Mbit/s e 4,761 contra 4,754 Mbit/s. Com o fator ½ de peneiramento, fica −12 % e +8 % dos valores do artigo.
- **Fase Q**, QKD-Analytical (Ahmed, Chatterjee e Oki, IEEE INFOCOM 2026):
  - **Q1**: as regras de alocação foram reconstruídas e reproduzem exatamente o exemplo da Fig. 2: 1200 sucessores do
    estado vazio, 34193 estados e as taxas λ/162 e λ/192.
  - **Q2**: a CTMC exata coincide com o Monte Carlo quando as saídas têm taxa n·μ. A eq. (3) lida ao pé da letra
    subestima o bloqueio.
  - **Q3**: Fig. 3(a), com |W| = 6, por Monte Carlo. A ordem de grandeza bate em carga alta. Em carga baixa o bloqueio
    sai até 3–4× menor, e o CS-FF sai abaixo do artigo.

Novos pacotes: `qkd.analytical` (modelo, CTMC e Monte Carlo) e `qkd.tools`.
