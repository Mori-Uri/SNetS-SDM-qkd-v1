# Parte 33: QCh no mapa de espectro, núcleos desativados, modulação por enlace

## Simulador

- **QCh no `SlotBlocking.csv`.** Para cada requisição protegida, a faixa de cada QCh tentado ou aceito no núcleo
  quântico conta como uma tentativa. Se a requisição foi bloqueada por uma causa QKD, essa faixa conta também como
  bloqueio daquela causa.
  - Assim, o núcleo 1 aparece nos mapas de calor de bloqueio por slot.
  - Linha nova `Blocked QKD requests without QCh band`: bloqueios QKD em que nenhum QCh chegou a ser tentado.
  - O registro só observa a alocação; as decisões não mudam. Ver `QKDSharedResourceManager.begin/endQchAttemptLog`.
- **Núcleos desativados (`network.disabledCores`, lista de ids, opcional).** Um núcleo desativado não aceita nenhum
  circuito clássico, QCh nem PICh: `Core.useSpectrum` recusa, e a lista de faixas livres vem vazia.
  - Se o núcleo quântico estiver na lista, ele é ignorado ali.
  - A métrica SlotUtilization trata o núcleo desativado como livre.
  - Sem o campo, os resultados não mudam: no cenário clássico, os 14 CSVs são idênticos byte a byte aos da Parte 29.
- **`BlockingDetails.csv`, métrica nova `Share of circuits per modulation on link`.** Para cada enlace (`Link`), a
  fração dos circuitos aceitos que passam por ele com cada modulação (`Cause` = modulação).
- **Classes alteradas:**
  - `QKDSharedResourceManager`, `QKDControlPlane`, `BlockingDetailRecorder`;
  - `Core`, `Link`, `NetworkConfig`, `SpectrumUtilization`.
- **Testes:**
  - as 21 verificações passam;
  - clássico e QKD dão os mesmos resultados da Parte 29, exceto as linhas novas;
  - com `disabledCores [5,6]`, os núcleos 5 e 6 ficam com uso 0 e nenhuma tentativa, sem erros.

## Launcher 1.12.0

Ver o README do launcher.
