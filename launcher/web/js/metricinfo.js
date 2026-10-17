/*
 * Descriptions (pt-BR) of the metrics written by SNetS and by the QKD addon,
 * of the CSV dimensions and of the columns of the data tables. Used by the
 * Results tab and the chart studio to explain what is being plotted.
 */
(function (App) {
  'use strict';

  // [regex on the metric name, description, unit]
  const M = [
    // ---------------------------------------------------------------- BlockingProbability.csv
    [/^blocking probability$/i, 'Probabilidade de bloqueio de circuito (PBC): requisições bloqueadas ÷ requisições geradas no ponto de carga, contando só depois do transiente (os primeiros 10 % das requisições são descartados).', 'fração (0–1)'],
    [/^blocking probability by qotn$/i, 'Parcela da PBC causada por QoTN: o novo circuito não atingiu a OSNR mínima da sua modulação. Razão sobre o total de requisições, então as causas somam a PBC.', 'fração'],
    [/^blocking probability by qoto$/i, 'Parcela da PBC causada por QoTO: o novo circuito degradaria a OSNR de circuitos já ativos abaixo do limiar deles.', 'fração'],
    [/^blocking probability by crosstalk$/i, 'Parcela da PBC causada por crosstalk (XT): o XT sofrido pelo novo circuito, vindo dos núcleos vizinhos, passa do limiar da modulação.', 'fração'],
    [/^blocking probability by crosstalk in other$/i, 'Parcela da PBC causada por XTO: o novo circuito faria o XT de circuitos já ativos passar do limiar deles.', 'fração'],
    [/^blocking probability by fragmentation$/i, 'Parcela da PBC por fragmentação: no núcleo do candidato havia slots livres comuns a toda a rota em número suficiente, mas nenhuma faixa contígua do tamanho exigido.', 'fração'],
    [/^blocking probability by lack of transmitters$/i, 'Parcela da PBC por falta de transmissor livre no nó de origem.', 'fração'],
    [/^blocking probability by lack of receivers$/i, 'Parcela da PBC por falta de receptor livre no nó de destino.', 'fração'],
    [/^blocking probability by other$/i, 'Parcela da PBC por "outros": (1) algum enlace da rota sem nenhum slot livre no núcleo do candidato (o SNetS chama isso de "other", não de fragmentação); (2) com QKD, candidatos válidos recusados pelo addon (proteção das sessões QKD ativas ou falha QKD de uma requisição protegida); (3) o que não se encaixa nas demais causas. O BlockingDetails.csv separa esses casos.', 'fração'],
    // ---------------------------------------------------------------- BlockingDetails.csv (Parte 25)
    [/^blocking probability by cause$/i, 'BlockingDetails: parcela do bloqueio por causa (coluna Cause) na classe da coluna Class (all = todas as requisições, classical = não protegidas, qkd = protegidas por QKD). Razão sobre as requisições da classe: as causas somam o bloqueio da classe. Inclui "Lack of free spectrum" (o "other" do SNetS quando falta slot livre) e as causas QKD (proteção de sessões ativas, espectro do QCh, SKR, PICh).', 'fração'],
    [/^blocking probability per core by cause$/i, 'BlockingDetails: bloqueio do núcleo (coluna Core) por causa: bloqueadas com candidato no núcleo e com esta causa ÷ requisições com candidato no núcleo. As causas de um núcleo somam o "Blocking probability per core" dele.', 'fração'],
    [/^share of all requests blocked per core by cause$/i, 'BlockingDetails: requisições bloqueadas com candidato no núcleo e com esta causa ÷ todas as requisições. Somando núcleos e causas obtém-se o bloqueio total.', 'fração'],
    [/^link blocking probability by qkd causes$/i, 'BlockingDetails: requisições recusadas por causas QKD cuja rota tentada passa pelo enlace ÷ requisições cuja rota passa pelo enlace.', 'fração'],
    [/^link blocking probability$/i, 'BlockingDetails: bloqueio do enlace direcional: requisições bloqueadas cuja rota tentada passa pelo enlace ÷ requisições cuja rota (tentada ou usada) passa por ele, na classe da coluna Class. Uma requisição bloqueada conta em todos os enlaces da rota tentada, então o valor indica onde o bloqueio acontece, não qual enlace o causou.', 'fração'],
    [/^link requests$/i, 'BlockingDetails: número de requisições (depois do transiente) cuja rota passa pelo enlace, na classe indicada.', 'requisições'],
    [/^requests$/i, 'BlockingDetails: número de requisições contadas (depois do transiente) na classe indicada.', 'requisições'],
    [/^blocking probability per bitrate$/i, 'PBC só das requisições da taxa de bits indicada na coluna BitRate.', 'fração'],
    [/^blocking probability per core$/i, 'BlockingDetails: bloqueadas com candidato no núcleo ÷ requisições com candidato no núcleo (mesma regra do SNetS).', 'fração'],
    [/^blocking probability per core/i, 'Bloqueio por núcleo: requisições bloqueadas cujo candidato estava no núcleo N ÷ requisições cujo candidato estava no núcleo N. Não soma 1 entre os núcleos.', 'fração'],
    [/^blocking probability per pair and bitrate$/i, 'PBC das requisições do par origem→destino (src, dest) e da taxa de bits indicados.', 'fração'],
    [/^blocking probability per pair$/i, 'PBC das requisições do par origem→destino indicado (src, dest).', 'fração'],
    [/^numm? attempts counter/i, 'Número médio de tentativas do algoritmo de potência PABS por requisição.', 'tentativas'],
    [/^numm? attempts qoto counter/i, 'Número médio de tentativas do PABS rejeitadas por QoTO.', 'tentativas'],
    // ---------------------------------------------------------------- BitRateBlockingProbability.csv
    [/^bitrate blocking probability$/i, 'Probabilidade de bloqueio de banda (PBB): soma das taxas de bits bloqueadas ÷ soma das taxas requisitadas. Pesa mais as requisições de taxa alta.', 'fração'],
    [/^bitrate blocking probability by (crosstalk in others|qotn|qoto|crosstalk|fragmentation|lack of transmitters|lack of receivers|other)$/i, 'Parcela da PBB por esta causa (mesmas causas da probabilidade de bloqueio de circuito, ponderadas pela taxa de bits).', 'fração'],
    [/^bitrate blocking probability per bitrate$/i, 'PBB restrita à taxa de bits indicada.', 'fração'],
    [/^bitrate blocking probability per core/i, 'PBB por núcleo: banda bloqueada no núcleo N ÷ banda requisitada no núcleo N.', 'fração'],
    [/^bitrate blocking probability per pair and bitrate$/i, 'PBB do par origem→destino e da taxa indicados.', 'fração'],
    [/^bitrate blocking probability per pair$/i, 'PBB do par origem→destino indicado.', 'fração'],
    [/^general requested bitrate$/i, 'Taxa de bits total requisitada no ponto de carga (denominador da PBB).', 'bit/s'],
    // ---------------------------------------------------------------- SpectrumUtilization.csv
    [/^utilization$/i, 'Utilização do espectro: fração média dos slots ocupados em todos os enlaces e núcleos, amostrada a cada chegada de requisição.', 'fração'],
    [/^utilization per link$/i, 'Utilização do espectro de cada enlace (todos os núcleos), indicado na coluna Link.', 'fração'],
    [/^utilization per core\s*$/i, 'Utilização média de cada núcleo (coluna Core) considerando todos os enlaces.', 'fração'],
    [/^utilization per link and core$/i, 'Utilização de cada núcleo de cada enlace.', 'fração'],
    [/^utilization per slot$/i, 'Fração do tempo em que cada slot (coluna Slot) esteve ocupado, em média sobre os enlaces.', 'fração'],
    // ---------------------------------------------------------------- ModulationUtilization.csv
    [/^percentage of cicuits per modulation and bandwidth$/i, 'Fração dos circuitos estabelecidos com cada modulação, separada pela largura de banda (taxa) do circuito.', 'fração'],
    [/^percentage of cicuits per modulation$/i, 'Fração dos circuitos estabelecidos que usaram cada formato de modulação (soma 1 entre as modulações).', 'fração'],
    // ---------------------------------------------------------------- SpectrumSizeStatistics / fragmentation
    [/^requisitons per number of slots per link$/i, 'Distribuição do número de slots ocupados pelas requisições, por enlace.', 'fração'],
    [/^requisitons per number of slots$/i, 'Distribuição do número de slots ocupados pelas requisições.', 'fração'],
    [/^external fragmentation \(vertical\)$/i, 'Fragmentação externa vertical: 1 − (maior bloco livre ÷ total de slots livres) de cada núcleo, média nos núcleos de cada enlace e em todos os enlaces, observada a cada chegada (o núcleo reservado à QKD entra na média).', 'fração'],
    [/^external fragmentation \(horizontal\)$/i, 'Fragmentação externa horizontal: a mesma medida na interseção do espectro livre ao longo da rota de cada requisição, no núcleo escolhido para ela.', 'fração'],
    [/^external fragmentation per link$/i, 'Fragmentação externa de cada enlace: 1 − (maior bloco livre ÷ slots livres), média nos núcleos do enlace e no tempo. 0 = todo o espaço livre é um bloco só.', 'fração'],
    [/^entropy external fragmentation/i, 'Fragmentação externa medida pela entropia da distribuição dos blocos livres.', 'adimensional'],
    [/^relative fragmentation/i, 'Fragmentação relativa para requisições de c slots: fração do espectro livre que não comporta um bloco de c slots.', 'fração'],
    // ---------------------------------------------------------------- Tx/Rx/Regenerators
    [/^tx utilization per node$/i, 'Uso médio de transmissores em cada nó.', 'transmissores'],
    [/^rx utilization per node$/i, 'Uso médio de receptores em cada nó.', 'receptores'],
    [/^regenerators utilization per node$/i, 'Uso médio de regeneradores em cada nó.', 'regeneradores'],
    [/^max tx utilization per node$/i, 'Maior número de transmissores usados ao mesmo tempo em cada nó.', 'transmissores'],
    [/^max rx utilization per node$/i, 'Maior número de receptores usados ao mesmo tempo em cada nó.', 'receptores'],
    [/^max regenerators utilization per node$/i, 'Maior número de regeneradores usados ao mesmo tempo em cada nó.', 'regeneradores'],
    [/^tx utilization$/i, 'Uso médio de transmissores na rede.', 'transmissores'],
    [/^rx utilization$/i, 'Uso médio de receptores na rede.', 'receptores'],
    [/^regenerators utilization$/i, 'Uso médio de regeneradores na rede.', 'regeneradores'],
    // ---------------------------------------------------------------- energy
    [/^total consumed energy/i, 'Energia total consumida pela rede no período simulado.', 'J'],
    [/^average power consumption/i, 'Potência média consumida pela rede.', 'W'],
    [/^energy efficiency/i, 'Eficiência energética: bits transmitidos por joule consumido.', 'bit/J'],
    [/^total data transmitted/i, 'Total de bits transmitidos no período simulado.', 'bit'],
    [/^total energy consumption by transponders/i, 'Energia consumida pelos transponders.', 'J'],
    [/^total energy consumption by oxcs/i, 'Energia consumida pelos OXCs.', 'J'],
    [/^total energy consumption by amplifiers/i, 'Energia consumida pelos amplificadores.', 'J'],
    // ---------------------------------------------------------------- grooming
    [/^rate of requests by circuit$/i, 'Média de requisições atendidas por circuito (agregação de tráfego).', 'requisições/circuito'],
    [/^maximum requests by circuit$/i, 'Maior número de requisições agregadas num mesmo circuito.', 'requisições'],
    [/^virtual hops$/i, 'Média de saltos virtuais (circuitos encadeados) por requisição agregada.', 'saltos'],
    [/^maximum virtual hops$/i, 'Maior número de saltos virtuais de uma requisição.', 'saltos'],
    // ---------------------------------------------------------------- physical layer / crosstalk statistics
    [/^average osnr per circuit/i, 'OSNR média dos circuitos estabelecidos.', 'dB'],
    [/^average osnr per pair/i, 'OSNR média dos circuitos de cada par origem→destino.', 'dB'],
    [/^average launch power per circuit/i, 'Potência de lançamento média dos circuitos.', 'dBm'],
    [/^average launch power per pair/i, 'Potência de lançamento média por par.', 'dBm'],
    [/^average crosstalk per circuit/i, 'Crosstalk médio sofrido pelos circuitos estabelecidos.', 'dB'],
    [/^average crosstalk per pair/i, 'Crosstalk médio por par origem→destino.', 'dB'],
    [/^average crosstalk per overlaps/i, 'Crosstalk médio dos circuitos agrupados pelo número de núcleos vizinhos com sobreposição espectral (coluna overlaps). −200 dB = sem amostras.', 'dB'],
    [/^maximum crosstalk per overlaps/i, 'Maior crosstalk observado para cada número de sobreposições.', 'dB'],
    [/^minimum crosstalk per overlaps/i, 'Menor crosstalk observado para cada número de sobreposições.', 'dB'],
    // ---------------------------------------------------------------- spectrum diagnosis (Parte 27)
    [/^blocking probability by spectrum diagnosis$/i, 'Bloqueio separado pelo estado do espectro da rota tentada no momento do bloqueio, independentemente da causa escolhida pelo algoritmo. Com o menor número de slots que alguma modulação de alcance suficiente exige: "faltavam slots livres" = em todos os núcleos clássicos algum enlace não tinha slots livres suficientes; "continuidade" = cada enlace tinha slots suficientes, mas não os mesmos; "contiguidade" = havia slots livres comuns à rota em número suficiente, mas não vizinhos; "havia faixa livre" = existia uma faixa contígua e contínua, então o bloqueio veio da camada física (QoT/XT) ou da proteção das sessões QKD. As parcelas somam o bloqueio total.', 'fração'],
    // ---------------------------------------------------------------- SlotUtilization.csv (Parte 27)
    [/^slot utilization$/i, 'Ocupação de cada slot de cada núcleo de cada enlace: fração das observações (uma a cada chegada de requisição) em que o slot estava ocupado. As bandas de guarda contam como ocupadas; no núcleo reservado à QKD aparecem os QChs e PIChs. Uma linha do CSV por ponto de carga, enlace, núcleo e replicação, com uma coluna por slot.', 'fração'],
    // ---------------------------------------------------------------- QKDMetrics.csv (Parte 23)
    [/^qkd blocking probability$/i, 'Probabilidade de bloqueio QKD: requisições protegidas por QKD recusadas ÷ requisições protegidas (após o warm-up). Uma requisição protegida só é aceita se o circuito clássico e a sessão QKD forem estabelecidos juntos.', 'fração'],
    [/^qkd blocking probability by classical resources$/i, 'Parcela do bloqueio QKD em que o lado clássico falhou (espectro, OSNR, XT ou proteção de sessões QKD ativas). As cinco causas somam o bloqueio QKD.', 'fração'],
    [/^qkd blocking probability by qch spectrum$/i, 'Parcela do bloqueio QKD por falta de QCh: não havia slot do núcleo QKD livre em todos os enlaces de nenhuma rota QKD tentada.', 'fração'],
    [/^qkd blocking probability by skr$/i, 'Parcela do bloqueio QKD por SKR insuficiente: havia slots livres, mas em nenhum deles a SKR do pior trecho atingia a demanda de chave.', 'fração'],
    [/^qkd blocking probability by pich$/i, 'Parcela do bloqueio QKD por falta de PICh: no modo segment (padrão), algum enlace da rota QKD não tinha PICh aberto com vaga nem slot livre num núcleo clássico para abrir outro; no modo route, não havia o mesmo slot livre em todos os enlaces.', 'fração'],
    [/^qkd blocking probability by lack of qtx\/qrx$/i, 'Parcela do bloqueio QKD por falta de transmissor (QTx) ou receptor (QRx) quântico livre num nó do QCh que precisaria ser aberto (limites qTransmittersPerNode / qReceiversPerNode).', 'fração'],
    [/^mean open pichs$/i, 'Número médio de PIChs abertos (modo segment: um por enlace com sessões, ou mais se houver limite de sessões por PICh).', 'canais'],
    [/^peak open pichs$/i, 'Maior número de PIChs abertos ao mesmo tempo.', 'canais'],
    [/^mean qtx in use \(network\)$/i, 'Soma, em todos os nós (inclusive os confiáveis intermediários), dos transmissores quânticos em uso: um por trecho de cada QCh ativo. É igual ao número de pares Q-TRx.', 'QTx'],
    [/^mean qrx in use \(network\)$/i, 'Soma dos receptores quânticos em uso em todos os nós.', 'QRx'],
    [/^mean qtx in use per node$/i, 'QTx em uso no nó da coluna src (média por chegada, após o aquecimento). Nós QKD_TN[...] são os nós confiáveis criados a cada QTND dentro dos enlaces.', 'QTx'],
    [/^mean qrx in use per node$/i, 'QRx em uso no nó da coluna src (média por chegada).', 'QRx'],
    [/^peak qtx in use per node$/i, 'Maior número de QTx em uso ao mesmo tempo no nó (dimensionamento do equipamento).', 'QTx'],
    [/^peak qrx in use per node$/i, 'Maior número de QRx em uso ao mesmo tempo no nó.', 'QRx'],
    [/^qkd blocking probability by other$/i, 'Parcela do bloqueio QKD por outras falhas (planejamento da rota, parâmetros inválidos).', 'fração'],
    [/^qkd blocking probability per pair$/i, 'Bloqueio QKD das requisições protegidas do par origem→destino indicado (src, dest).', 'fração'],
    [/^qkd acceptance ratio$/i, 'Fração das requisições protegidas aceitas (1 − bloqueio QKD).', 'fração'],
    [/^fraction of qkd-protected requests$/i, 'Fração das requisições que chegaram protegidas por QKD (confere a fração configurada no tráfego).', 'fração'],
    [/^classical candidates rejected to protect qkd/i, 'Candidatos clássicos recusados por degradarem sessões QKD ativas, por requisição. Mede o custo da proteção QKD para o tráfego clássico; conta candidatos, podendo passar de 1 tentativa por requisição.', 'candidatos/requisição'],
    [/^alternative qkd route ratio$/i, 'Fração das sessões QKD aceitas que usaram uma rota alternativa (diferente da rota do circuito clássico).', 'fração'],
    [/^mean admission skr/i, 'SKR média, na admissão, do pior trecho da rota QKD (a SKR da sessão).', 'Mbit/s'],
    [/^minimum admission skr/i, 'Menor SKR de sessão observada na admissão.', 'Mbit/s'],
    [/^mean admission qber$/i, 'QBER média das sessões na admissão (pior trecho). Próxima do erro de alinhamento e_d quando o crosstalk é desprezível.', 'fração'],
    [/^maximum admission qber$/i, 'Maior QBER de sessão observada na admissão.', 'fração'],
    [/^mean key demand/i, 'Demanda média de material de chave das sessões aceitas.', 'Mbit/s'],
    [/^mean active qchs$/i, 'Número médio de QChs ativos na rede (amostrado nas chegadas; pela propriedade PASTA equivale à média no tempo).', 'QChs'],
    [/^mean active q-trx pairs$/i, 'Número médio de pares transmissor-receptor quânticos (Q-TRx) em uso: um por trecho de cada QCh ativo.', 'pares'],
    [/^mean active qkd sessions$/i, 'Número médio de sessões QKD ativas.', 'sessões'],
    [/^peak active qchs$/i, 'Maior número de QChs ativos ao mesmo tempo.', 'QChs'],
    [/^peak q-trx pairs$/i, 'Maior número de pares Q-TRx em uso ao mesmo tempo (dimensionamento do hardware quântico).', 'pares'],
    [/^mean q-trx pairs per admitted session$/i, 'Pares Q-TRx por sessão aceita = número de trechos QTND da rota QKD.', 'pares'],
    [/^mean hops per admitted session$/i, 'Número médio de enlaces da rota QKD das sessões aceitas.', 'enlaces'],
    [/^mean qkd route length/i, 'Comprimento médio da rota QKD das sessões aceitas.', 'km'],
    [/^planned trusted nodes$/i, 'Nós confiáveis da topologia planejada com o QTND (fronteiras entre trechos, contadas uma vez por par de fibras).', 'nós'],
    [/^planned qkd spans/i, 'Total de trechos QKD planejados, somando os enlaces direcionais.', 'trechos'],
    [/^qtnd/i, 'QTND usado: distância máxima entre nós confiáveis.', 'km'],
    // ---------------------------------------------------------------- QKDStatistics.csv (colunas)
    [/^totalrequestattempts$/i, 'Requisições processadas (clássicas e protegidas) após o warm-up.', 'requisições'],
    [/^protectedattempts$/i, 'Requisições protegidas por QKD processadas.', 'requisições'],
    [/^accepted$/i, 'Requisições protegidas aceitas.', 'requisições'],
    [/^rejected$/i, 'Requisições protegidas recusadas.', 'requisições'],
    [/^released$/i, 'Sessões QKD encerradas (saída da requisição).', 'sessões'],
    [/^existingsessionimpactchecks$/i, 'Vezes em que um candidato clássico foi testado contra as sessões QKD ativas.', 'verificações'],
    [/^classicalrejectionsbyqkdimpact$/i, 'Candidatos clássicos recusados por degradarem sessões QKD ativas.', 'candidatos'],
    [/^activerefreshes$/i, 'Recálculos da SKR/QBER de sessões ativas após mudanças no tráfego clássico.', 'recálculos'],
    [/^precommitadmissionchecks$/i, 'Provisionamentos QKD tentados antes de efetivar o circuito clássico.', 'tentativas'],
    [/^precommitadmissionrejections$/i, 'Provisionamentos QKD que falharam antes de efetivar o circuito clássico.', 'tentativas'],
    [/^activatedqchs$/i, 'QChs ativados.', 'QChs'], [/^deactivatedqchs$/i, 'QChs desativados.', 'QChs'],
    [/^tdmreuses$/i, 'Reaproveitamentos de QCh já ativo (modos link/span).', 'reservas'],
    [/^capacityfailures$/i, 'Falhas de reserva de capacidade/espectro QKD.', 'falhas'],
    [/^peakactiveqchs$/i, 'Pico de QChs ativos.', 'QChs'], [/^peakqtrxpairs$/i, 'Pico de pares Q-TRx.', 'pares'],
    [/^accepteddemand_mbps$/i, 'Soma das demandas de chave aceitas.', 'Mbit/s'],
    [/^meanadmissionrawskr_mbps$/i, 'SKR média de sessão na admissão.', 'Mbit/s'],
    [/^minimumadmissionrawskr_mbps$/i, 'Menor SKR de sessão na admissão.', 'Mbit/s'],
    [/^meanadmissionqber$/i, 'QBER média na admissão.', 'fração'], [/^maximumadmissionqber$/i, 'Maior QBER na admissão.', 'fração'],
    [/^maximumadmissionpxt_w$/i, 'Maior potência de crosstalk num trecho QKD na admissão.', 'W'],
    [/^qkdacceptanceratio$/i, 'Fração das requisições protegidas aceitas.', 'fração'],
    [/^qkdblockingprobability$/i, 'Fração das requisições protegidas recusadas.', 'fração'],
    [/^meanactiveqchs$/i, 'QChs ativos em média.', 'QChs'], [/^meanactiveqtrxpairs$/i, 'Pares Q-TRx em uso em média.', 'pares'],
    [/^meanactiveqkdsessions$/i, 'Sessões QKD ativas em média.', 'sessões'],
    [/^qtnd_km$/i, 'QTND usado.', 'km'], [/^warmuparrivals$/i, 'Chegadas descartadas como warm-up nas estatísticas QKD.', 'chegadas'],
  ];

  const DIMS = {
    link: 'enlace direcional "<a-b>" (de a para b); all = todos os enlaces',
    core: 'índice do núcleo da MCF; all = todos',
    src: 'nó de origem; all = todos', dest: 'nó de destino; all = todos',
    bitrate: 'taxa de bits da requisição (bit/s); all = todas',
    modulation: 'formato de modulação', bandwidth: 'largura de banda (taxa) do circuito; all = todas',
    overlaps: 'número de núcleos vizinhos com sobreposição espectral', 'number of slots': 'número de slots',
    slot: 'índice do slot no espectro (1 = primeiro)', node: 'nó da rede',
    class: 'classe da requisição: all = todas, classical = não protegidas, qkd = protegidas por QKD',
    cause: 'causa do bloqueio (classificação do SNetS, com "other" separado em falta de espectro livre, outros e causas QKD)',
  };

  function describe(metric) {
    const name = String(metric || '').trim();
    for (const [re, d, u] of M) if (re.test(name)) return { desc: d, unit: u };
    return null;
  }

  const TITLES = [
    [/^blocking probability$/i, 'Probabilidade de Bloqueio de Circuito'],
    [/^blocking probability by /i, 'Probabilidade de Bloqueio'],
    [/^link blocking probability/i, 'Probabilidade de Bloqueio do Enlace'],
    [/^external fragmentation per link/i, 'Fragmentação externa do enlace'],
    [/^external fragmentation/i, 'Fragmentação externa'],
    [/^entropy external fragmentation/i, 'Entropia da fragmentação'],
    [/^relative fragmentation/i, 'Fragmentação relativa'],
    [/^slot utilization/i, 'Ocupação do slot'],
    [/^blocking probability per /i, 'Probabilidade de Bloqueio'],
    [/^bitrate blocking probability/i, 'Probabilidade de Bloqueio de Banda'],
    [/^utilization/i, 'Utilização do Espectro'],
    [/^percentage of cicuits per modulation/i, 'Percentual de Circuitos'],
    [/^qkd blocking probability/i, 'Probabilidade de Bloqueio QKD'],
    [/^qkd acceptance ratio$/i, 'Taxa de Aceitação QKD'],
    [/skr/i, 'SKR (Mbit/s)'],
    [/qber/i, 'QBER'],
    [/q-trx/i, 'Pares Q-TRx'],
    [/qchs/i, 'QChs'],
    [/qkd sessions/i, 'Sessões QKD'],
    [/osnr/i, 'OSNR (dB)'],
    [/crosstalk/i, 'Crosstalk (dB)'],
  ];

  /** Default y-axis title for a metric. */
  function axisTitle(metric) {
    const name = String(metric || '').trim();
    for (const [re, t] of TITLES) if (re.test(name)) return t;
    const d = describe(name);
    return d && d.unit && !/^fração/.test(d.unit) ? name + ' (' + d.unit + ')' : name;
  }

  function dimension(name) {
    return DIMS[String(name || '').trim().toLowerCase()] || null;
  }

  App.metricInfo = { describe, dimension, axisTitle };
})(window.App);
