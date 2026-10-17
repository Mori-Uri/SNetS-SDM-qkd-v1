/*
 * "Plano de verificação" (Verificação QKD page): the ladder of scenarios that
 * bridges the article-base reproduction to a complete SDM/QKD scenario, plus
 * extra verifications. Each step is run by qkd.ladder.QKDLadderMain (simulator
 * jar) and writes ordinary SNetS cases under verificacao/QKD_ladder/<perfil>/<passo> (Parte 27; before: simulations/)
 * and checks.csv / data.csv / charts.csv / cases.txt / report.txt / status.txt.
 */
(function (App) {
  'use strict';
  const { el, clear, fmtNum } = App;

  const PHASES = [
    {
      id: 'A', title: 'Fase A — Artigo-base (Dibaj et al., 2024, cenário multifibra)',
      desc: '10 fibras clássicas (160 canais de 75 GHz) + 1 fibra quântica por enlace, RMSTA do artigo (K = 2), QChs por trecho QTND compartilhados por TDM (256 slots), tráfego semiestático: todas as requisições protegidas e ativas até o fim. Topologias Small Core, Butterfly e Metro.',
      steps: [
        { id: 'A1', title: 'Reprodução nativa do artigo', changes: 'Nada: é o cenário do artigo executado pelo próprio SNetS (Q-TRx × QTND).',
          checks: 'Curva de Q-TRx em U; QTND ótimo a ≤ 5 km do da reconstrução em Python; em todas as replicações o ótimo fica a ≤ 5 km de 25 km; diferença média ≤ 10 Q-TRx; nenhum bloqueio.' },
        { id: 'A2', title: 'Artigo-base + métricas de bloqueio', changes: 'Ativa BlockingProbability, BitRateBlockingProbability, SpectrumUtilization e QKDMetrics nos mesmos casos.',
          checks: 'Resultados idênticos aos do A1 (medir não altera a simulação); bloqueio nulo na carga do artigo; conservação das contagens.' },
        { id: 'A3', title: 'Ponto de operação: carga até 1 % de bloqueio', changes: 'QTND 25 km e a carga multiplicada por fatores crescentes.',
          checks: 'Bloqueio não diminui com a carga. Informa o tráfego clássico em que o bloqueio chega a 1 % (o artigo usa 625, 612 e 654 Tbit/s).' },
      ],
    },
    {
      id: 'B', title: 'Fase B — Fibra multinúcleo de 2 núcleos (1 clássico + 1 quântico)',
      desc: 'Mesmo molde do A1, trocando as 11 fibras por uma MCF de 2 núcleos vizinhos: núcleo 0 clássico, núcleo 1 reservado para QKD. Carga = carga do artigo × 1/10 (um núcleo clássico) × 2 (ponto de operação do A3). QChs no slot do seu comprimento de onda (meio da banda).',
      steps: [
        { id: 'B1', title: 'Sem XT e sem OSNR', changes: 'Só a fibra muda; nenhum efeito de camada física.',
          checks: 'Sem XT o QKD não interfere no clássico: bloqueio e utilização do núcleo clássico idênticos aos de um gêmeo sem requisições protegidas; nenhuma potência de crosstalk chega ao QKD.' },
        { id: 'B2', title: 'Com crosstalk entre núcleos (XT)', changes: 'Liga o XT (acoplamento 0,012) clássico → quântico.',
          checks: 'O XT age: candidatos clássicos recusados para proteger QChs e bloqueio QKD ≥ B1, em cada topologia e QTND.' },
        { id: 'B2L', title: 'Limite: XT → 0', changes: 'XT ligado com acoplamento 1e-12 (desprezível).',
          checks: 'Q-TRx, aceitações, QChs e bloqueio idênticos aos do B1.' },
        { id: 'B3', title: 'XT + OSNR/QoT (ASE + NLI)', changes: 'Liga QoT com limiares de SNR da qualificação.',
          checks: 'Aparece bloqueio por OSNR (QoTN/QoTO) nos circuitos longos.' },
      ],
    },
    {
      id: 'C', title: 'Fase C — Fibra multinúcleo de 7 núcleos',
      desc: 'Hexágono de 7 núcleos com XT: 6 núcleos clássicos + 1 quântico. Carga = artigo × 6/10 × 2. Compara o QKD num núcleo da borda (3 vizinhos) e no centro (6 vizinhos).',
      steps: [
        { id: 'C1', title: 'QKD no núcleo da borda (1)', changes: '7 núcleos, núcleo quântico 1.',
          checks: 'Núcleo quântico com 3 vizinhos no arquivo network; o XT dos núcleos clássicos vizinhos age sobre o QKD.' },
        { id: 'C2', title: 'QKD no núcleo central (0)', changes: 'Mesmo C1, núcleo quântico 0.',
          checks: '6 vizinhos; bloqueio QKD no centro ≥ na borda (mais vizinhos clássicos ⇒ mais XT).' },
      ],
    },
    {
      id: 'D', title: 'Fase D — Requisições dinâmicas',
      desc: 'C1 com chegadas de Poisson e duração exponencial (holdRate = 1), QTND 25 km, várias cargas (Erlang), 10 % de aquecimento descartado.',
      steps: [
        { id: 'D1', title: 'Tráfego dinâmico', changes: 'Semiestático → dinâmico, várias cargas.',
          checks: 'Bloqueio não diminui com a carga; sessões QKD ativas = carga × (1 − bloqueio) (lei de Little, ± 5 %); conservação.' },
        { id: 'D2', title: 'Convergência', changes: 'Dobro de chegadas por replicação e 20 % de aquecimento.',
          checks: 'Bloqueios compatíveis com o D1 (dentro do IC95% ou 10 %): o tamanho da simulação é suficiente.' },
      ],
    },
    {
      id: 'E', title: 'Fase E — Recursos da qualificação, um de cada vez',
      desc: 'D1 na carga do meio, acrescentando um recurso por passo.',
      steps: [
        { id: 'E1', title: 'Modo route', changes: 'QCh dedicado por sessão, mesmo slot em todos os trechos, escolhido pela SKR a partir do meio da banda (lowestloss), em vez de QChs por trecho com TDM.',
          checks: 'Toda sessão admitida tem SKR ≥ demanda de chave; conservação.' },
        { id: 'E2', title: '+ rotas QKD alternativas', changes: 'k = 2 rotas alternativas (Yen).', checks: 'Bloqueio QKD ≤ E1; fração de sessões em rota alternativa.' },
        { id: 'E3', title: '+ PICh', changes: 'PICh de 1 slot num núcleo clássico, um por enlace da rota QKD (termina nos nós, como o QCh) e compartilhado pelas sessões (pichMode = segment).', checks: 'Bloqueio clássico ≥ E2; bloqueio por falta de PICh.' },
        { id: 'E4', title: '+ demanda por gerador', changes: '20 % das requisições protegidas, 2 Mbit/s por sessão (campos do arquivo traffic).',
          checks: 'Fração protegida ≈ 0,2 (± 4σ); demanda média = 2 Mbit/s; SKR das sessões ≥ 2 Mbit/s.' },
      ],
    },
    {
      id: 'F', title: 'Fase F — Cenário completo',
      desc: 'Modelo clássico padrão do SNetS da qualificação (slots de 12,5 GHz, qabne, 320 slots, limiares de SNR e XT), 7 núcleos, XT + QoT, dinâmico, modo route, rotas alternativas, PICh, 20 % protegidas, 2 Mbit/s, QTND 30 km.',
      steps: [
        { id: 'F1', title: 'Rede de 5 nós da qualificação', changes: 'Small Core / 5 nós, cargas 800–1600 E.',
          checks: 'Bloqueio cresce com a carga; lei de Little (20 %); fração protegida; SKR ≥ 2 Mbit/s; nós confiáveis = fórmula; simetria dos sentidos; conservação.' },
        { id: 'F2', title: 'USA (24 nós)', changes: 'Topologia USA, cargas 600–1200 E.', checks: 'Os mesmos do F1, numa rede grande.' },
      ],
    },
    {
      id: 'X', title: 'Verificações extras (respostas conhecidas)',
      desc: 'Cenários pequenos com resultado analítico ou exato, para confirmar que o simulador faz as contas certas.',
      steps: [
        { id: 'X1', title: 'Erlang-B', changes: 'Um enlace, 10 ou 20 canais, uma taxa (1 canal por requisição), Poisson.', checks: 'Bloqueio simulado = fórmula de Erlang-B em cada carga.' },
        { id: 'X2', title: 'Nós confiáveis analíticos', changes: '4 topologias × QTND 5 a 3000 km.', checks: 'Nós confiáveis = Σ ⌈L/QTND⌉ − 1 e trechos = 2·Σ ⌈L/QTND⌉; monotonia; QTND ≥ maior enlace ⇒ 0 nós.' },
        { id: 'X3', title: 'SKR independente', changes: 'Sem simulação: compara o modelo BB84 do simulador com uma implementação escrita a partir da eq. (1) do artigo.',
          checks: 'Diferença ≤ 1e-9; Tabela 5 da qualificação; ótimo da Fig. 5(b) em 1550 nm e 25 km; SKR cai com a distância e com o ruído.' },
        { id: 'X4', title: 'Limites', changes: 'Fração protegida 0; demanda de chave → 0.',
          checks: 'Fração 0 numa MCF de 2 núcleos = fibra de 1 núcleo sem QKD (idêntico); demanda → 0 ⇒ nenhum bloqueio por SKR.' },
        { id: 'X5', title: 'Monotonicidade', changes: 'Demanda de chave 1–128 Mbit/s; MCF de 2, 7, 12 e 19 núcleos na mesma carga.',
          checks: 'Mais demanda ⇒ mais bloqueio por SKR; mais núcleos clássicos ⇒ menos bloqueio clássico.' },
        { id: 'X6', title: 'Reprodutibilidade', changes: 'Mesmo caso 3 vezes (1, 1 e 2 threads) e com outra semente.',
          checks: 'Resultados idênticos com a mesma semente, inclusive com outro número de threads; outra semente muda os resultados.' },
        { id: 'X7', title: 'Conservação', changes: 'Caso com todos os recursos + todos os casos já simulados do plano.',
          checks: 'Aceitas + bloqueadas = tentativas; causas somam o total; fração protegida coerente entre arquivos.' },
        { id: 'X8', title: 'Simetria', changes: 'Casos do D1.', checks: 'Com tráfego uniforme, a utilização dos dois sentidos de cada enlace é parecida.' },
      ],
    },
    {
      id: 'M', title: 'Fase M — Moghaddam, Beyranvand e Salehi (IEEE JSAC, 2021)',
      desc: 'Parâmetros da Tabela I do artigo (μ = 0,5, T_s = 250 ps, T_d = 100 ps, e_d = 0,015, f = 1,16, η_d = 0,3, α = 0,21 dB/km) e topologias da Fig. 6. O simulador modela o QCh num núcleo dedicado (métodos DC do artigo) com ruído de crosstalk intercore; Raman e FWM não são modelados.',
      steps: [
        { id: 'M1', title: 'SKR × comprimento (Fig. 5)', changes: 'Sem simulação de rede: SKR de um enlace só com contagem escura e com o XT de um canal clássico de 0 dBm no núcleo vizinho (eq. 1 do artigo: P0·e^(−αL)·h·L).',
          checks: 'Alcance com p_dark = 1e-8 e 1e-6 (≈ 300 e 205 km); com XT h = 6,3e-10 /km (≈ 115 km) e 4,6e-11 /km (≈ 190 km); h = 1e-7 zera a SKR. Valores lidos dos gráficos.' },
        { id: 'M2', title: 'Rede metro (Fig. 6b)', changes: '8 nós, enlaces de 20–50 km, nós como relays confiáveis (QTND 150 km), demanda de 2 kbit/s, carga baixa.',
          checks: 'SKR média na admissão do SNetS = média independente (menor SKR da rota mais curta). Compara com a Fig. 10a (≈ 25 Mbit/s, com o fator ½ de peneiramento).' },
        { id: 'M3', title: 'Rede de núcleo pequena (Fig. 6a)', changes: '5 nós, enlaces de 70–100 km, demais condições do M2.',
          checks: 'Mesma conferência do M2; compara com a Fig. 7a (≈ 2,2 Mbit/s).' },
      ],
    },
    {
      id: 'Q', title: 'Fase Q — QKD-Analytical (Ahmed, Chatterjee e Oki, IEEE INFOCOM 2026)',
      desc: 'Modelo exato de cadeia de Markov (CTMC) de um enlace com fibra de 7 núcleos: o núcleo central é dedicado aos QChs; cada requisição leva um canal de dados (f slots), um canal clássico QKD (1 slot, com banda de guarda) e um QCh; slots iguais em núcleos vizinhos são evitados (XT e ruído).',
      steps: [
        { id: 'Q1', title: 'Espaço de estados da Fig. 2', changes: '|W| = 4 slots, 1 classe (2/1/1 slots).',
          checks: 'Reproduz exatamente o artigo: 1200 sucessores do estado vazio, 34193 estados, taxas λ/162 e λ/192 dos estados t = 2 e t = 10.' },
        { id: 'Q2', title: 'CTMC exata × Monte Carlo', changes: 'Bloqueio × carga com |W| = 4, CS-RF e CS-FF.',
          checks: 'A CTMC (saídas com taxa n·μ) coincide com a simulação de Monte Carlo com as mesmas regras; mostra que a eq. (3) lida ao pé da letra subestima o bloqueio.' },
        { id: 'Q3', title: 'Fig. 3(a): |W| = 6', changes: 'Monte Carlo com as regras reconstruídas, CS-RF e CS-FF.',
          checks: 'Mesma ordem de grandeza dos pontos da simulação do artigo (fator ≤ 3); CS-FF bloqueia menos que CS-RF.' },
      ],
    },
  ];

  const STATUS_CLASS = { OK: 'ok', FALHA: 'err', AVISO: 'warn', INFO: 'info' };
  const L = { profile: 'rapido', threads: '', force: false, open: {}, hosts: {}, rows: {} };
  try { L.profile = localStorage.getItem('snets.ladder.profile') || 'rapido'; } catch (e) { /* ignore */ }

  const T = () => App.qkdTools;
  const base = () => (T().info().ladderRel || 'QKD_ladder') + '/' + L.profile + '/';

  /** CSV with quoted fields. */
  function parseCsv(text) {
    if (!text) return [];
    const rows = [];
    let row = [], cur = '', q = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (q) {
        if (c === '"' && text[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') q = false; else cur += c;
      } else if (c === '"') q = true;
      else if (c === ',') { row.push(cur); cur = ''; } else if (c === '\n' || c === '\r') {
        if (c === '\r' && text[i + 1] === '\n') i++;
        row.push(cur); rows.push(row); row = []; cur = '';
      } else cur += c;
    }
    if (cur || row.length) { row.push(cur); rows.push(row); }
    const head = rows.shift() || [];
    return rows.filter((r) => r.some((x) => x !== '')).map((r) => Object.fromEntries(head.map((h, i) => [h, r[i] === undefined ? '' : r[i]])));
  }

  async function loadStatus(step) {
    const s = await T().readText(base() + step + '/status.txt');
    if (!s) return null;
    const [status, when, profile, secs, ran, reused] = s.split(/\r?\n/);
    const checks = parseCsv(await T().readText(base() + step + '/checks.csv'));
    const count = (k) => checks.filter((c) => c.status === k).length;
    return { status, when, profile, secs: Number(secs), ran: Number(ran), reused: Number(reused), ok: count('OK'), fail: count('FALHA'), warn: count('AVISO'), info: count('INFO') };
  }

  function toolId(step) {
    return 'ladder:' + step + ':' + L.profile + (Number(L.threads) > 0 ? ':' + Number(L.threads) : '') + (L.force ? ':force' : '');
  }

  // ------------------------------------------------------------------ panel

  function panel() {
    const info = T().info();
    const box = el('section.panel.ladder');
    if (!info.hasLadder) {
      box.append(el('h2', 'Plano de verificação'), el('div.notice.warn', 'Este JAR não contém o plano de verificação (qkd.ladder.QKDLadderMain). Use o SNetS-SDM-QKD.jar atualizado.'));
      return box;
    }
    const seg = el('div.seg');
    const setProfile = (p) => {
      L.profile = p;
      try { localStorage.setItem('snets.ladder.profile', p); } catch (e) { /* ignore */ }
      [...seg.children].forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.p === p)));
      refreshAll();
    };
    seg.append(
      el('button', { type: 'button', 'data-p': 'rapido', 'aria-pressed': String(L.profile === 'rapido'), on: { click: () => setProfile('rapido') } }, 'Rápido'),
      el('button', { type: 'button', 'data-p': 'completo', 'aria-pressed': String(L.profile === 'completo'), on: { click: () => setProfile('completo') } }, 'Completo'));
    const threads = el('input.input.num', { type: 'number', min: 1, max: 256, placeholder: 'auto', value: L.threads, style: { width: '90px' } });
    threads.addEventListener('input', () => { L.threads = threads.value; });
    const force = el('input', { type: 'checkbox', checked: L.force });
    force.addEventListener('change', () => { L.force = force.checked; });
    const runAll = el('button.btn', { type: 'button', on: { click: () => queueSteps(PHASES.flatMap((p) => p.steps.map((s) => s.id))) } }, 'Executar tudo (em fila)');

    box.append(
      el('h2', 'Plano de verificação: do artigo-base ao cenário completo'),
      el('p.lead', 'Cada passo muda um único fator em relação ao anterior e tem critérios de aprovação. Os passos podem ser executados separadamente: quando um passo depende de outro (por exemplo, o B2 compara com o B1), os casos que faltarem são simulados antes, e os que já existem são reaproveitados. ',
        'Os casos e relatórios ficam em ', el('code', App.displayPath(info.ladderRel || 'QKD_ladder') + '/<perfil>/<passo>'), ' (fora da pasta simulations: aparecem na seção "Verificação" da árvore e podem ser abertos para ver gráficos, mapas de calor e tabelas).'),
      el('div.toolbar.ladder-controls',
        el('span.small.muted', 'Perfil'), seg,
        el('span.small.muted', { title: 'Total de threads de cada simulação (vazio = número de replicações)' }, 'Threads'), threads,
        el('label.check.small', { title: 'Simula de novo mesmo os casos que já têm resultado' }, force, ' refazer simulações'),
        el('span.spacer'), runAll),
      el('p.small.muted', 'Rápido: 5 valores de QTND, 3 replicações e menos chegadas (minutos por passo; serve para conferir a cadeia). Completo: 10 valores de QTND (5–50 km), 10 replicações e simulações longas (pode levar horas; use para os resultados finais). ',
        'Status: OK = todos os critérios atendidos; AVISO = algum critério estatístico ou de tendência não atendido (veja o detalhe); FALHA = algum critério exato não atendido.'));
    for (const ph of PHASES) box.append(phaseCard(ph));
    refreshAll();
    return box;
  }

  function phaseCard(ph) {
    const list = el('div.ladder-steps');
    for (const st of ph.steps) list.append(stepRow(st));
    return el('div.ladder-phase',
      el('div.ladder-phase-head',
        el('div', el('h3', ph.title), el('p.small.muted', ph.desc)),
        el('button.btn.small', { type: 'button', on: { click: () => queueSteps(ph.steps.map((s) => s.id)) } }, 'Executar fase (em fila)')),
      list);
  }

  function stepRow(st) {
    const badge = el('span.tag', '—');
    const meta = el('span.small.muted');
    const host = el('div.ladder-run');
    const detail = el('div.ladder-detail', { hidden: !L.open[st.id] });
    const toggle = el('button.btn.small.ghost', { type: 'button' }, L.open[st.id] ? 'Ocultar' : 'Resultados');
    toggle.addEventListener('click', () => {
      L.open[st.id] = !L.open[st.id];
      detail.hidden = !L.open[st.id];
      toggle.textContent = L.open[st.id] ? 'Ocultar' : 'Resultados';
      if (L.open[st.id]) showDetail(st, detail);
    });
    const run = el('button.btn.small.primary', { type: 'button', on: { click: () => runStep(st.id) } }, 'Executar');
    L.rows[st.id] = { badge, meta, host, detail, toggle };
    L.hosts[st.id] = host;
    const row = el('div.ladder-step',
      el('div.ladder-step-main',
        el('div.ladder-id', st.id),
        el('div.ladder-text',
          el('div', el('b', st.title), ' ', badge, ' ', meta),
          el('div.small', el('span.muted', 'Muda: '), st.changes),
          el('div.small', el('span.muted', 'Verifica: '), st.checks)),
        el('div.ladder-actions', run, toggle)),
      host, detail);
    if (L.open[st.id]) showDetail(st, detail);
    return row;
  }

  async function refreshAll() {
    for (const ph of PHASES) for (const st of ph.steps) refreshRow(st.id);
  }

  async function refreshRow(id) {
    const r = L.rows[id];
    if (!r) return;
    const s = await loadStatus(id);
    if (!s) {
      r.badge.className = 'tag'; r.badge.textContent = 'não executado'; r.meta.textContent = '';
      return;
    }
    r.badge.className = 'tag ' + (STATUS_CLASS[s.status] || '');
    r.badge.textContent = s.status;
    r.meta.textContent = `${s.ok} OK · ${s.fail} falha(s) · ${s.warn} aviso(s) · ${s.when} · ${App.fmtDuration(s.secs * 1000)} · ${s.ran} caso(s) simulado(s), ${s.reused} reaproveitado(s)`;
    if (L.open[id]) {
      const st = PHASES.flatMap((p) => p.steps).find((x) => x.id === id);
      showDetail(st, r.detail);
    }
  }

  async function runStep(id, queued) {
    const r = L.rows[id];
    r.badge.className = 'tag'; r.badge.textContent = queued ? 'na fila' : 'executando';
    const d = await T().runTool(toolId(id), r.host, queued);
    App.loadTree();
    await refreshRow(id);
    if (d && d.state === 'failed' && !(await loadStatus(id))) App.toast(id + ': a execução falhou — veja a saída do processo', 'err', 8000);
    return d;
  }

  async function queueSteps(ids) {
    App.toast(ids.length + ' passo(s) colocados na fila (perfil ' + L.profile + ')', 'ok');
    await Promise.all(ids.map((id) => runStep(id, true)));
  }

  // ------------------------------------------------------------------ results of a step

  async function showDetail(st, box) {
    const [checksTxt, dataTxt, chartsTxt, casesTxt, report] = await Promise.all(['checks.csv', 'data.csv', 'charts.csv', 'cases.txt', 'report.txt']
      .map((f) => T().readText(base() + st.id + '/' + f)));
    clear(box);
    if (!checksTxt) { box.append(el('p.muted.small', 'Ainda sem resultados no perfil ' + L.profile + '.')); return; }
    const checks = parseCsv(checksTxt);
    box.append(el('div.table-wrap', el('table.grid.data.ladder-checks',
      el('thead', el('tr', el('th', ''), el('th', 'Critério'), el('th', 'Medido'), el('th', 'Esperado'))),
      el('tbody', checks.map((c) => el('tr',
        el('td', el('span.tag.' + (STATUS_CLASS[c.status] || ''), c.status)),
        el('td', c.check, c.detail ? el('div.small.muted', c.detail) : null),
        el('td.mono.small', c.measured), el('td.mono.small', c.expected)))))));
    const data = parseCsv(dataTxt);
    const charts = parseCsv(chartsTxt);
    if (charts.length && data.length) {
      const grid = el('div.ladder-charts');
      box.append(grid);
      for (const ch of charts) {
        const rows = data.filter((d) => d.chart === ch.chart);
        if (!rows.length) continue;
        const names = [...new Set(rows.map((d) => d.series))];
        const series = names.map((n, i) => ({
          name: n, slot: i % 8, dash: /Python|Little|Erlang-B|Independente|D1 /.test(n) && !/Simulado/.test(n),
          markersOnly: /Artigo/.test(n), hollow: /Artigo/.test(n),
          points: rows.filter((d) => d.series === n).map((d) => ({ x: Number(d.x), key: Number(d.x), mean: Number(d.y), ci: d.ci95 === '' ? NaN : Number(d.ci95), n: d.n === '' ? 1 : Number(d.n) }))
            .filter((p) => Number.isFinite(p.x) && Number.isFinite(p.mean)).sort((a, b) => a.x - b.x),
        })).filter((s) => s.points.length);
        if (!series.length) continue;
        grid.append(el('div.ladder-chart', el('div.small.muted', ch.ylabel),
          T().chartBox(series, ch.title, ch.xlabel, ch.log === 'true', (p) => ch.xlabel + ': ' + fmtNum(p.x, 2), ch.ylabel)));
      }
    }
    // cases.txt of older runs lists paths under simulations ("QKD_ladder/…"); after the move they are in "@verificacao/"
    const cases = (casesTxt || '').split(/\r?\n/).filter(Boolean)
      .map((c) => (/^QKD_(ladder|reference)\//.test(c) && App.isVerificationPath(base()) ? '@verificacao/' + c : c));
    const caseBox = el('div.ladder-cases');
    if (cases.length) {
      caseBox.append(el('div.small.muted', cases.length + ' caso(s) SNetS deste passo — abra para ver gráficos, mapa de calor dos enlaces e tabelas:'));
      for (const c of cases.slice(0, 80)) caseBox.append(el('button.btn.small.ghost', { type: 'button', title: c, on: { click: () => App.openCase(c, 'results') } }, c.split('/').slice(-1)[0]));
      if (cases.length > 80) caseBox.append(el('span.small.muted', ' … mais ' + (cases.length - 80)));
    }
    box.append(el('div.toolbar', { style: { marginTop: '10px' } },
      el('button.btn.small', { type: 'button', on: { click: () => App.modal(st.id + ' — relatório', el('div.console', { style: { height: '460px' } }, report || ''), null) } }, 'Ver relatório completo')),
    caseBox);
  }

  App.ladder = { panel, PHASES };
})(window.App);
