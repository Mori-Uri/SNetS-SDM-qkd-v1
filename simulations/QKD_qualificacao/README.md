# Cenário de estudo da qualificação (slides 26–37)

`Exemplo_5nos_QTND30` reproduz o cenário dos slides:

- Topologia de 5 nós: 1-2 76 km, 1-3 86, 2-5 90, 5-3 80, 2-4 100, 3-4 70.
- MCF de 7 núcleos com o núcleo 1 reservado aos QChs e QTND de 30 km (13 nós confiáveis).
- QCh com o mesmo slot em todos os trechos da rota (`qchGranularity = route`), PICh com 1 slot num núcleo
  clássico e até 2 rotas QKD alternativas.
- Tráfego uniforme de 100 a 500 Gbit/s (proporção 5:4:3:2:1), com 20 % das requisições protegidas por QKD
  e demanda de 2 Mbit/s de chave (campos `qkdProtectedProbability` e `keyRateMbps` de cada gerador).
- Métricas: BlockingProbability, BitRateBlockingProbability, SpectrumUtilization e QKDMetrics.

Para executar: abra o caso no launcher → aba Executar. Depois veja os resultados na aba Resultados ou no
botão Gráficos (modelos "QKD: …").

Para variar o QTND, use a aba Varredura com o campo `network: qkd.qtndKm`. No Estúdio de gráficos, o modelo
"QKD: métrica × QTND" põe o QTND no eixo x.
