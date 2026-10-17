# Verificacao do artigo-base no addon QKD

Esta pasta contem a verificacao da reconstrucao multifibra usada na qualificacao. Ela nao e uma pasta de simulacao normal lida por `simulationControl.Main`; por isso fica separada dos cenarios C1/C2/C3.

Use o JAR do addon em `simulations/SNetS-SDM-QKD.jar`.

## Verificacao de rede / Q-TRx

Entre em `verificacao/QKD_reference/Dibaj_MF` e execute:

```bat
run_reference_verification.bat
```

O runner Java processa 3 topologias x 10 replicacoes x 10 valores de QTND = 300 observacoes e compara a camada QKD/Q-TRx com a reconstrucao Python previamente verificada.

Resultados e figuras sao escritos em `snets_results/`.

## Verificacao fisica

```bat
run_reference_physical.bat
```

Resultados e figuras sao escritos em `physical_results/`.

## Observacao metodologica

Os traces roteados da reconstrucao Python sao mantidos para a comparacao quantitativa QKD porque o artigo-base nao publica todos os detalhes necessarios para reproduzir exatamente cada desempate de roteamento. O recomputo de rotas em Java e diagnostico, nao e usado como criterio de igualdade de Q-TRx.

## Verificacao de rede no motor nativo do SNetS (Parte 22)

Alem do modo acima (que reaplica os traces ja roteados pela reconstrucao em Python), o
artigo-base agora tambem roda inteiramente dentro do SNetS:

```bat
Dibaj_MF\run_native_reference.bat
```

ou, a partir da pasta `simulations`:

```bat
java -cp SNetS-SDM-QKD.jar qkd.reference.QKDNativeReferenceMain all QKD_reference/Dibaj_MF QKD_reference/Dibaj_MF_SNetS
```

O programa cria 30 casos SNetS comuns em `Dibaj_MF_SNetS/<topologia>/<topologia>_QTND_XX`
(Small Core, Butterfly e Metro x QTND 5..50 km, 10 replicacoes cada), executa todos com
`simulationControl.Main` e grava em `Dibaj_MF_SNetS/results/`:

- `snets_native_raw.csv` - Q-TRx de cada topologia/QTND/replicacao (pico de QChs ativos);
- `snets_native_statistics.csv` - media, desvio, IC95% (t de Student), curva normalizada,
  ponto digitalizado do artigo e media da reconstrucao em Python;
- `snets_native_summary.csv` e `snets_native_report.txt` - QTND otimo (SNetS, Python, artigo),
  otimo por replicacao, MAE/RMSE e bloqueios.

Mapeamento do cenario MF para o SNetS: 10 fibras classicas = 10 "nucleos" sem acoplamento
(960 slots de 12,5 GHz = 160 canais de 75 GHz) + 1 fibra quantica = nucleo reservado para QKD;
RMSTA classico `qrmsta` (K = 2, modulacao por distancia, first fit na grade de 75 GHz, canal do
PICh reservado, caminho com menor indice de canal final); QChs por trecho QTND
(`qchGranularity = span`), compartilhados pelos dois sentidos, 256 slots TDM, QChs nos
comprimentos de onda de menor atenuacao; trafego semiestatico com exatamente o numero de
requisicoes da reconstrucao (`qkdExactRequestCount`).

Resultado obtido (10 replicacoes): QTND otimo = 25 km (Small Core), 30 km (Butterfly) e
25 km (Metro), os mesmos da reconstrucao em Python; diferenca media para a reconstrucao de
5,1 / 3,8 / 2,4 Q-TRx e para a Fig. 7 digitalizada de 16,2 / 11,5 / 13,9 Q-TRx; nenhum bloqueio.
O verificador `qkd.verification.QKDNativeArticleBaseVerification` repete esse teste.

## Verificacao fisica (complemento)

`run_reference_physical.bat` agora tambem grava `table5_intermediate_values.csv` (valores da
Tabela 5 da qualificacao, SKR/Q-TRx maximo e SKR com mu = 0,7 em 20 e 70 km, cada um com
status OK/MISMATCH) e `mu_sweep_skr.csv` (SKR x mu, cenarios 1 e 4 de Mehdizadeh et al.).

Os tres modos tambem podem ser executados pela interface (`launcher/SNetS-Launcher.bat`,
botao "Verificacao QKD").
