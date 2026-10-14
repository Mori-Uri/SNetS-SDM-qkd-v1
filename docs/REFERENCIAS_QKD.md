# Referências usadas no código QKD

Cada classe e cada método de cálculo do addon QKD cita na documentação (Javadoc) as referências abaixo pela chave entre
colchetes. Os PDFs estão em `docs/artigos` e não vão para o repositório GitHub, por causa dos direitos autorais.

| Chave | Referência | Arquivo em docs/artigos | Onde é usada |
|---|---|---|---|
| DIBAJ24 | M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base) | `# Cost-Optimized Quantum Communication Networks ...pdf` | `BB84DecoyModel` (eq. 1); nós confiáveis a cada QTND (`QKDTopologyPlanner`, `QKDSpan`, `QKDLinkPlan`, `QKDRoutePlanner`); QChs compartilhados, Q-TRx e TDM (`QKDSharedResourceManager`, `QKDSharedChannel`); RMSTA (`QKDReferenceRMSTA`); `qkd.reference.*`; plano de verificação, fases A–F |
| MEHDI24 | P. Mehdizadeh, M. R. Dibaj et al., "Quantum-Classical Coexistence in Multi-Band Optical Networks: A Noise Analysis of QKD", IEEE Commun. Lett. 28(3), 2024 | `# Quantum-Classical Coexistence in Multi-Band Optical.pdf` | atenuação α(λ) (`QKDPhysicalParameters`) |
| MOGH21 | E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in Space Division Multiplexed Elastic Optical Networks Secured With QKD", IEEE JSAC 39(9):2688–2700, 2021 | `2021 - Resource_Allocation_in_Space_Division_Multiplexed_...pdf` | BB84 (eqs. 9–18), ruído de XT (eq. 1, modo `attenuated`), fundo p_b; demanda de chave de 256 bits (Sec. VI-B); CCh/PICh na mesma rota (eq. 22, `QKDPichPool`); plano de verificação, fase M |
| LACERDA24 | J. C. Lacerda Jr., A. V. T. Cartaxo, A. C. B. Soares, "Novel dynamic impairment-aware algorithm for modulation, core, and spectrum assignment in SDM-EONs", Opt. Switch. Netw. 51, 2024 | `Jurandir - Novel dynamic impairment-aware ...pdf` | modelo de crosstalk (P·ISO·h·L) em `QKDPhysicalLayer`; teste nos outros circuitos (`QKDExistingSessionImpactEvaluator`) |
| LOBATO19 | F. R. L. Lobato et al., "Inter-core crosstalk aware greedy algorithm for spectrum and core assignment in SDM-EONs", Opt. Switch. Netw. 33, 2019 | `(2019) xtawaregreedyalgorithm ...pdf` | crosstalk intercore em `QKDPhysicalLayer` |
| HAYASHI11 | T. Hayashi et al., "Design and fabrication of ultra-low crosstalk and low-loss multi-core fiber", Opt. Express, 2011 | `Crosstalk - Design and fabrication ...pdf` | coeficiente de acoplamento h |
| NOTA-RX | U. P. Mori, nota técnica "Definição da contribuição de ruído do crosstalk intercore no receptor QKD", 2026 | `Equacao_ruido_crosstalk_receptor_QKD.pdf` | seções 2 e 3 de `QKDPhysicalLayer`; `QKDCrosstalkReceiverTransferMode` |
| MORI26 | U. P. Mori, qualificação "Planejamento de Nós Confiáveis para QKD em SDM-EONs: Análise da Coexistência Quântico-Clássica", UFPI, 2026 | `UrielPinheiroMori_Qualificação.pdf` | parâmetros (Tabela 2), verificação (Tabela 5), modos route/link/span, PICh, rotas alternativas, admissão e proteção das sessões (`QKDControlPlane`, `QKDProvisioningService`) |
| ABNE20 | J. Lacerda Jr. et al., "ABNE – Algoritmo de Balanceamento Inter-Núcleos para Redes Ópticas Elásticas com SDM", 2020 | `(2020) ABNE ...pdf` | `QABNE` |
| FUJII14 | S. Fujii et al., "On-Demand Spectrum and Core Allocation for Reducing Crosstalk in Multicore Fibers in EONs", JOCN 6(12), 2014 | `Core priorization - On-Demand Spectrum and Core - FUJI.pdf` | priorização de núcleos (`QABNE`) |
| CHAT15 | B. C. Chatterjee, N. Sarma, E. Oki, "Routing and Spectrum Allocation in Elastic Optical Networks: A Tutorial", IEEE COMST 17(3), 2015 | `Charteje - Routing and Spectrum Allocation ...pdf` | first fit, k menores caminhos |
| SNETS17 | A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) | `SNETS.pdf` | plano de controle, malha, gerador de tráfego, causas de bloqueio |
| AHMED26a | I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking Probabilities in Priority-Aware QKD over SDM-EONs", IEEE INFOCOM 2026 | `QKD-Analytical - ...pdf` | `qkd.analytical.*`; canal clássico QKD com banda de guarda; plano de verificação, fase Q |
| AHMED26b | I. Ahmed, B. C. Chatterjee, E. Oki, "AnaQKD: Analytical Modeling of Blocking Probability with QKD in SS-EONs", IEEE ICC 2026 | `AnaQKD - ...pdf` | `qkd.analytical.*` (extensão futura para fibras multinúcleo e multimodo) |
| CHAT26 | B. C. Chatterjee, E. Oki, "Towards Secure and Flexible Optical Networks – A Tutorial on Quantum-Classical Coexistence", ICNC 2026 | `Towards Secure and Flexible Optical Networks ...pdf` | `QKDPichPool` |
| XIE24 | C. Xie, Y. Sun et al., "Synergistic Resource Allocation with Key Generation and Consumption Matching in Quantum Secured Multicore Fiber Optical Networks", ACP/IPOC 2024 | `Synergistic Resource Allocation ...pdf` | compartilhamento da taxa de chave (`QKDSharedResourceManager`) |
| JAIN91 | R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991 | `The Art of Computer Systems Performance Analysis ...pdf` | lei de Little, Erlang-B, intervalos de confiança, transiente, cadeias de Markov |
| HERON09 | S. Heron, "Advanced Encryption Standard (AES)", Network Security, 2009 | `Advanced Encryption Standard (AES).pdf` | chave AES de 256 bits (`QKDKeyDemand`) |

Referências citadas, mas que não estão na pasta:

- H.-K. Lo, X. Ma, K. Chen, "Decoy State Quantum Key Distribution", Phys. Rev. Lett. 94, 230504 (2005). É a origem do
  BB84 com estados decoy usado por DIBAJ24 e MOGH21.
- J. Y. Yen, "Finding the K shortest loopless paths in a network", Management Science 17(11), 1971 (rotas QKD
  alternativas).
