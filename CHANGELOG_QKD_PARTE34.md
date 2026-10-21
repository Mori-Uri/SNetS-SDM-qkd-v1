# Parte 34: demanda de chave pela taxa clássica

- **Campo novo `simulation.qkdKeyDemand.mode`.** Pode ser:
  - `"uniform"`, o padrão (também quando o campo não existe): igual a antes. Os resultados são idênticos byte a byte aos da Parte 33.
  - `"byBitRate"`: cada requisição protegida sem `keyRateMbps` pede a taxa de chave que corresponde à sua taxa clássica.
- **Regra do `"byBitRate"`.** As taxas clássicas dos geradores e as taxas de chave (tamanho da chave ÷ intervalo) são
  ordenadas da menor para a maior e pareadas pela posição:
  - com o mesmo número de taxas dos dois lados, é 1ª com 1ª, 2ª com 2ª, e assim por diante;
  - com mais taxas clássicas, taxas vizinhas dividem a mesma chave;
  - com menos, elas se espalham da menor à maior chave.

  Como a requisição protegida é uma requisição clássica sorteada, as demandas ficam nas mesmas proporções das taxas
  clássicas.
- **Tabela opcional `keyRateMbpsByBitRate`.** Mapeia Gb/s para Mbit/s, por exemplo `{"100": 1, "200": 2, "400": 4}`,
  e substitui a regra automática para as taxas que lista.
- **Precedência.** O `keyRateMbps` de um gerador continua vencendo. O sorteio da política ainda é feito em cada
  requisição, o que mantém o gerador aleatório do QKD alinhado com o modo uniforme.
- **Classes alteradas:**
  - `QKDKeyDemandPolicy`, `QKDRequestGenerator`, `QKDMesh`, `QKDMeshFactory`;
  - `SimulationConfig.QKDKeyDemandConfig`.
