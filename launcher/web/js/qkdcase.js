/*
 * Clássica × QKD: execution mode of a case, the "QKD" tab (QTND and trusted
 * nodes, QKD core, QCh policy, alternative routes, PICh, key demand, QKD
 * metrics) and the QKD traffic panel shown in the Traffic tab.
 *
 * Everything is written to the normal case files:
 *   network.qkd.{enabled,qtndKm,quantumCoreId,qchGranularity,qchSlotPolicy,
 *                wavelengthPolicy,alternativeRoutes,pichSlots,...}
 *   simulation.{qkdProtectedRequestProbability,qkdKeyDemand,qkdWarmupFraction,
 *               qkdExactRequestCount,activeMetrics.QKDMetrics}
 *   traffic.requestGenerators[i].{qkdProtectedProbability,keyRateMbps}
 */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtBitRate, parseNumber, isPlainObject, deepClone } = App;
  /** 0..1 → "25" (pt-BR comma, no useless decimals). */
  const fmtPct = (f) => String(Math.round(Number(f) * 100 * 1000) / 1000).replace('.', ',');
  const st = () => App.state;

  const QKD_DEFAULTS = {
    enabled: true, qtndKm: 30.0, quantumCoreId: 1, qchGranularity: 'route', qchSlotPolicy: 'firstfit',
    wavelengthPolicy: 'grid', alternativeRoutes: 2, pichSlots: 1,
  };

  function isQkd(model) {
    const m = model || st().model;
    return !!(m && isPlainObject(m.network) && isPlainObject(m.network.qkd) && m.network.qkd.enabled);
  }

  function cores() {
    const net = st().model.network;
    return isPlainObject(net) && Array.isArray(net.cores) ? net.cores : [];
  }

  /** Peripheral core with the fewest neighbours (core 1 in a 7-core MCF), as in the qualification. */
  function defaultQuantumCore() {
    const cs = cores();
    if (!cs.length) return 0;
    const sorted = cs.slice().sort((a, b) => (a.adjacentCores || []).length - (b.adjacentCores || []).length || a.id - b.id);
    return sorted[0].id;
  }

  // ------------------------------------------------------------------ trusted-node planning (same rule as QKDTopologyPlanner)

  function spansOf(lengthKm, qtnd) {
    if (!(lengthKm > 0) || !(qtnd > 0)) return [];
    const n = Math.max(1, Math.ceil(lengthKm / qtnd - 1e-9));
    const out = [];
    for (let i = 0; i < n; i++) out.push(Math.min(lengthKm, (i + 1) * qtnd) - i * qtnd);
    return out;
  }

  function planning(net, qtnd) {
    const links = isPlainObject(net) && Array.isArray(net.links) ? net.links : [];
    const seen = new Map();
    for (const l of links) {
      const a = String(l.source), b = String(l.destination);
      const key = a < b ? a + '|' + b : b + '|' + a;
      if (!seen.has(key)) seen.set(key, { a: a < b ? a : b, b: a < b ? b : a, km: Number(l.size) || 0, dirs: 0 });
      seen.get(key).dirs++;
    }
    const rows = [...seen.values()].map((r) => Object.assign(r, { spans: spansOf(r.km, qtnd) }));
    const trusted = rows.reduce((s, r) => s + Math.max(0, r.spans.length - 1), 0);
    const spans = rows.reduce((s, r) => s + r.spans.length, 0);
    const maxSpans = Math.max(0, ...rows.map((r) => r.spans.length));
    return { rows, trusted, spans, maxSpans };
  }

  // ------------------------------------------------------------------ mode bar

  function drawModeBar() {
    const box = document.getElementById('caseMode');
    if (!box) return;
    clear(box);
    if (!st().casePath || !isPlainObject(st().model.network)) return;
    const q = isQkd();
    box.append(el('span.small.muted', 'Execução'),
      el('div.seg.mode-seg',
        el('button', { type: 'button', 'aria-pressed': String(!q), on: { click: () => { if (q) toClassical(); } } }, 'Clássica'),
        el('button', { type: 'button', 'aria-pressed': String(q), on: { click: () => { if (!q) enableDialog(); } } }, 'QKD')),
      q ? el('span.small.muted', 'QTND ' + fmtNum(Number(st().model.network.qkd.qtndKm)) + ' km · núcleo QKD ' + st().model.network.qkd.quantumCoreId) : null);
  }

  function refreshAll(tab) {
    App.drawTabs();
    drawModeBar();
    App.refreshDirty();
    if (tab) App.selectTab(tab); else App.renderTab();
  }

  const csaKey = () => 'snets.classicalCsa.' + st().casePath;

  function toClassical() {
    App.setValue('network', ['qkd', 'enabled'], false);
    const sim = st().model.simulation;
    let note = '';
    if (isPlainObject(sim) && ['qabne', 'qfirstfit'].includes(String(sim.coreAndSpectrumAssignment || '').toLowerCase())) {
      let prev = null;
      try { prev = localStorage.getItem(csaKey()); } catch (e) { /* ignore */ }
      const target = prev || 'csbasdm';
      note = ' coreAndSpectrumAssignment: ' + sim.coreAndSpectrumAssignment + ' → ' + target + (prev ? ' (o algoritmo usado antes do QKD)' : ' (ABNE clássico)') + '.';
      App.setValue('simulation', ['coreAndSpectrumAssignment'], target);
    }
    App.toast('Caso em modo clássico (network.qkd.enabled = false). As demais opções QKD foram mantidas.' + note, 'ok', 7000);
    refreshAll(st().tab === 'qkd' ? 'overview' : null);
  }

  function enableDialog() {
    const net = st().model.network;
    const prev = isPlainObject(net.qkd) ? net.qkd : {};
    const qtnd = el('input.input.num', { type: 'text', value: String(prev.qtndKm || QKD_DEFAULTS.qtndKm) });
    const coreSel = el('select.input', cores().map((c) => el('option', { value: String(c.id) },
      'Núcleo ' + c.id + ' (' + (c.adjacentCores || []).length + ' vizinhos)')));
    coreSel.value = String(prev.quantumCoreId !== undefined && prev.quantumCoreId >= 0 ? prev.quantumCoreId : defaultQuantumCore());
    const alt = el('input.input.num', { type: 'text', value: String(prev.alternativeRoutes !== undefined ? prev.alternativeRoutes : QKD_DEFAULTS.alternativeRoutes) });
    const pich = el('input.input.num', { type: 'text', value: String(prev.pichSlots !== undefined ? prev.pichSlots : QKD_DEFAULTS.pichSlots) });
    const applyTraffic = el('input', { type: 'checkbox', checked: true });
    const frac = el('input.input.num', { type: 'text', value: '0,2' });
    const key = el('input.input.num', { type: 'text', value: '2' });
    const preview = el('div.small.muted');
    const upd = () => {
      const q = parseNumber(qtnd.value);
      const p = planning(net, q);
      preview.textContent = q > 0 ? p.rows.length + ' enlaces → ' + p.spans + ' trechos QKD, ' + p.trusted + ' nós confiáveis' : 'Informe um QTND > 0.';
    };
    qtnd.addEventListener('input', upd); upd();
    App.modal('Ativar QKD neste caso', el('div.form-grid',
      el('p.small', 'O caso passa a simular requisições protegidas por QKD: cada enlace é dividido em trechos de no máximo QTND km (nós confiáveis) e as sessões QKD usam um núcleo reservado da MCF.'),
      el('div.fields',
        field('QTND — distância entre nós confiáveis', qtnd, 'km'),
        field('Núcleo reservado aos QChs', coreSel),
        field('Rotas QKD alternativas', alt, 'além da rota clássica'),
        field('Slots do PICh por sessão', pich, '0 = não modelar')),
      preview,
      el('label.check', applyTraffic, ' Marcar todo o tráfego atual como QKD com:'),
      el('div.fields', field('Fração protegida', frac, '0 a 1'), field('Demanda de chave', key, 'Mbit/s'))), async () => {
      const q = parseNumber(qtnd.value), a = parseNumber(alt.value), p = parseNumber(pich.value);
      if (!(q > 0)) throw new Error('QTND deve ser > 0.');
      if (!(a >= 0) || !(p >= 0)) throw new Error('Rotas alternativas e slots do PICh devem ser ≥ 0.');
      const qkd = Object.assign({}, QKD_DEFAULTS, prev, {
        enabled: true, qtndKm: q, quantumCoreId: Number(coreSel.value), alternativeRoutes: Math.round(a), pichSlots: Math.round(p),
      });
      if (!prev.qchGranularity) qkd.qchGranularity = 'route';
      App.setValue('network', ['qkd'], qkd);
      const sim = st().model.simulation;
      if (isPlainObject(sim)) {
        if (!isPlainObject(sim.activeMetrics)) sim.activeMetrics = {};
        sim.activeMetrics.QKDMetrics = true;
        if (!isPlainObject(sim.qkdKeyDemand)) sim.qkdKeyDemand = { keySizeBits: 256, updateIntervalsMicroseconds: [128.0], randomSeed: 2024 };
        if (sim.qkdProtectedRequestProbability === undefined) sim.qkdProtectedRequestProbability = 0.0;
        if (sim.qkdExactRequestCount === undefined) sim.qkdExactRequestCount = true;
        if (sim.qkdWarmupFraction === undefined) sim.qkdWarmupFraction = 0.1;
        if (!['qabne', 'qfirstfit'].includes(String(sim.coreAndSpectrumAssignment || '').toLowerCase())) {
          try { localStorage.setItem(csaKey(), String(sim.coreAndSpectrumAssignment || '')); } catch (e) { /* ignore */ }
          App.toast('coreAndSpectrumAssignment "' + sim.coreAndSpectrumAssignment + '" trocado por "qabne" (o modo QKD exige um algoritmo do addon: qabne ou qfirstfit).', 'warn', 8000);
          sim.coreAndSpectrumAssignment = 'qabne';
        }
      }
      if (applyTraffic.checked) {
        const f = parseNumber(frac.value), k = parseNumber(key.value);
        if (!(f >= 0 && f <= 1)) throw new Error('Fração protegida deve estar entre 0 e 1.');
        if (!(k > 0)) throw new Error('Demanda de chave deve ser > 0.');
        applyQkdFields(() => true, f, k);
      }
      App.toast('QKD ativado (alterações ainda não salvas). Revise na aba QKD.', 'ok');
      refreshAll('qkd');
    }, 'Ativar QKD');
  }

  function field(label, input, unit) {
    return el('label.field', el('div.flabel', el('span', label), unit ? el('span.unit', '(' + unit + ')') : null), input);
  }

  // ------------------------------------------------------------------ QKD tab

  function renderTab() {
    const root = el('div.stack');
    const net = st().model.network, sim = st().model.simulation;
    if (!isQkd()) {
      root.append(el('section.panel', el('h2', 'Este caso está em modo clássico'),
        el('p.lead', 'Ative o modo QKD no seletor "Execução" acima para configurar QTND, núcleo reservado, tráfego e métricas QKD.'),
        el('button.btn.primary', { type: 'button', on: { click: enableDialog } }, 'Ativar QKD')));
      return root;
    }
    const q = net.qkd;
    const set = (path, v, redraw) => { App.setValue('network', ['qkd'].concat(path), v); drawModeBar(); if (redraw) App.renderTab(); };
    const num = (key, label, unit, opts) => {
      opts = opts || {};
      const i = el('input.input.num', { type: 'text', value: q[key] === undefined ? '' : String(q[key]).replace('.', ',') });
      i.addEventListener('change', () => {
        const v = parseNumber(i.value);
        const ok = Number.isFinite(v) && v >= (opts.min !== undefined ? opts.min : 0) && (!opts.int || Number.isInteger(v));
        i.classList.toggle('invalid', !ok);
        if (ok) set([key], v, opts.redraw);
      });
      return field(label, i, unit);
    };
    const sel = (key, label, options, redraw) => {
      const s = el('select.input', options.map(([v, l]) => el('option', { value: String(v) }, l)));
      s.value = String(q[key] !== undefined ? q[key] : options[0][0]);
      s.addEventListener('change', () => {
        const o = options.find(([v]) => String(v) === s.value);
        set([key], o ? o[0] : s.value, redraw);
      });
      return field(label, s);
    };

    // ------------------------------- infrastructure
    const quick = el('div.toolbar', { style: { margin: '6px 0 0' } }, el('span.small.muted', 'QTND rápido:'),
      [10, 20, 25, 30, 40, 50, 60].map((v) => el('button.btn.small', {
        type: 'button', 'aria-pressed': String(Number(q.qtndKm) === v),
        on: { click: () => set(['qtndKm'], v, true) },
      }, v + ' km')));
    const coreOpts = cores().map((c) => [c.id, 'Núcleo ' + c.id + ' — ' + (c.adjacentCores || []).length + ' vizinhos (' + (c.adjacentCores || []).join(', ') + ')']);
    const gran = q.qchGranularity || 'link';
    root.append(el('section.panel',
      el('h2', 'Infraestrutura QKD'),
      el('p.lead', 'Parâmetros do bloco qkd do arquivo network. Os enlaces são divididos em trechos de no máximo QTND km; cada trecho executa sua própria sessão BB84 entre nós confiáveis (um par Q-TRx por trecho).'),
      el('div.fields',
        num('qtndKm', 'QTND — distância entre nós confiáveis', 'km', { min: 1e-9, redraw: true }),
        sel('quantumCoreId', 'Núcleo reservado aos QChs', coreOpts.length ? coreOpts : [[q.quantumCoreId, String(q.quantumCoreId)]], true),
        sel('qchGranularity', 'Alocação do QCh', [
          ['route', 'route — mesmo slot em todos os trechos da rota QKD (qualificação)'],
          ['link', 'link — um QCh por enlace, compartilhado pela taxa de chave'],
          ['span', 'span — pools por trecho com TDM (artigo-base, Dibaj et al.)']], true),
        gran === 'route' ? sel('qchSlotPolicy', 'Ordem de escolha do slot do QCh', [
          ['firstfit', 'firstfit — menor índice de slot primeiro'],
          ['lowestloss', 'lowestloss — slot mais próximo do mínimo de atenuação']]) : null,
        sel('wavelengthPolicy', 'Comprimento de onda do QCh', [
          ['grid', 'grid — λ do slot na grade de 12,5 GHz (a SKR depende do slot)'],
          ['fixed', 'fixed — todos os QChs em λ de mínima atenuação']]),
        num('alternativeRoutes', 'Rotas QKD alternativas', 'k menores, além da rota clássica', { int: true }),
        num('pichSlots', 'Slots do PICh por sessão', 'em núcleo clássico; 0 = não modelar', { int: true }),
        gran === 'span' ? num('tdmSlots', 'Slots TDM por QCh', '0 = contínuo', { int: true }) : null),
      quick,
      el('div.notice.info', { style: { marginTop: '10px' } },
        gran === 'route'
          ? 'Admissão de uma requisição protegida: (1) RMSCA clássico; (2) OSNR/XT do candidato e proteção das sessões QKD ativas; (3) rota QKD — a rota clássica e, se falhar, até ' + (q.alternativeRoutes || 0) + ' alternativa(s); (4) espectro QKD — o primeiro slot livre em todos os enlaces da rota cuja SKR em todos os trechos atende à demanda de chave' + (q.pichSlots > 0 ? '; (5) PICh com ' + q.pichSlots + ' slot(s) num núcleo clássico ao longo da rota.' : '.')
          : 'Os modos link/span existem para reproduzir o artigo-base; o cenário da qualificação usa "route".')));

    // ------------------------------- planning
    const plan = planning(net, Number(q.qtndKm));
    const maxKm = Math.max(1, ...plan.rows.map((r) => r.km));
    const bar = (r) => {
      const w = 260;
      const g = App.svg('svg', { width: w, height: 16, viewBox: `0 0 ${w} 16`, class: 'spanbar' });
      let x = 0;
      r.spans.forEach((s, i) => {
        const sw = s / maxKm * (w - 4);
        g.appendChild(App.svg('rect', { x: x + 1, y: 3, width: Math.max(1, sw - 2), height: 10, rx: 2, class: i % 2 ? 'sb-b' : 'sb-a' }));
        if (i > 0) g.appendChild(App.svg('circle', { cx: x, cy: 8, r: 3.2, class: 'sb-tn' }));
        x += sw;
      });
      return g;
    };
    root.append(el('section.panel',
      el('h2', 'Planejamento dos nós confiáveis (QTND = ' + fmtNum(Number(q.qtndKm)) + ' km)'),
      el('div.kv',
        kvBox('Enlaces (pares de fibras)', fmtNum(plan.rows.length)),
        kvBox('Trechos QKD', fmtNum(plan.spans)),
        kvBox('Nós confiáveis', fmtNum(plan.trusted)),
        kvBox('Máx. trechos num enlace', fmtNum(plan.maxSpans))),
      el('div.table-wrap', { style: { marginTop: '12px' } }, el('table.grid.data',
        el('thead', el('tr', el('th', 'Enlace'), el('th.num', 'Comprimento'), el('th', 'Trechos (km)'), el('th.num', 'Nós confiáveis'), el('th', ''))),
        el('tbody', plan.rows.map((r) => el('tr',
          el('td', r.a + ' — ' + r.b + (r.dirs < 2 ? ' (1 sentido)' : '')),
          el('td.num', fmtNum(r.km) + ' km'),
          el('td.mono', r.spans.map((s) => fmtNum(s, 2)).join(' + ')),
          el('td.num', String(Math.max(0, r.spans.length - 1))),
          el('td', bar(r))))))),
      el('p.small.muted', { style: { marginTop: '8px' } }, 'Regra do simulador (QKDTopologyPlanner): trechos de QTND km a partir da origem do enlace e o restante no último trecho. Q-TRx de uma sessão = número de trechos da rota QKD.')));

    // ------------------------------- demand
    if (isPlainObject(sim)) {
      const kd = isPlainObject(sim.qkdKeyDemand) ? sim.qkdKeyDemand : null;
      const rates = kd && Array.isArray(kd.updateIntervalsMicroseconds) ? kd.updateIntervalsMicroseconds.map((us) => (kd.keySizeBits || 256) / us) : [];
      const tr = trafficQkdSummary();
      if (!isPlainObject(sim.qkdKeyDemand)) sim.qkdKeyDemand = { keySizeBits: 256, updateIntervalsMicroseconds: [128.0], randomSeed: 2024 };
      const simNum = (path, label, unit, check) => {
        const cur = path.reduce((o, k) => (o ? o[k] : undefined), sim);
        const i = el('input.input.num', { type: 'text', value: cur === undefined ? '' : String(cur).replace('.', ',') });
        i.addEventListener('change', () => {
          const v = parseNumber(i.value);
          const ok = Number.isFinite(v) && (!check || check(v));
          i.classList.toggle('invalid', !ok);
          if (ok) { App.setValue('simulation', path, v); }
        });
        return field(label, i, unit);
      };
      // 1.12.0: the fraction is typed in % (the file keeps 0..1)
      const simPct = (path, label, unit) => {
        const cur = path.reduce((o, k) => (o ? o[k] : undefined), sim);
        const i = el('input.input.num', { type: 'text', value: cur === undefined ? '' : fmtPct(cur) });
        i.addEventListener('change', () => {
          const v = parseNumber(i.value);
          const ok = Number.isFinite(v) && v >= 0 && v <= 100;
          i.classList.toggle('invalid', !ok);
          if (ok) { App.setValue('simulation', path, v / 100); App.renderTab(); }
        });
        return field(label, i, unit);
      };
      const intervals = el('input.input.mono', { type: 'text', value: kd ? (kd.updateIntervalsMicroseconds || []).join(', ') : '' });
      intervals.addEventListener('change', () => {
        const arr = intervals.value.split(/[;,\s]+/).filter(Boolean).map((x) => parseNumber(x));
        const ok = arr.length && arr.every((x) => x > 0);
        intervals.classList.toggle('invalid', !ok);
        if (ok) { App.setValue('simulation', ['qkdKeyDemand'], Object.assign({ keySizeBits: 256, randomSeed: 2024 }, kd || {}, { updateIntervalsMicroseconds: arr })); App.renderTab(); }
      });
      const exact = el('input', { type: 'checkbox', checked: !!sim.qkdExactRequestCount });
      exact.addEventListener('change', () => App.setValue('simulation', ['qkdExactRequestCount'], exact.checked));
      const met = el('input', { type: 'checkbox', checked: !(isPlainObject(sim.activeMetrics) && sim.activeMetrics.QKDMetrics === false) });
      met.addEventListener('change', () => { if (!isPlainObject(sim.activeMetrics)) sim.activeMetrics = {}; App.setValue('simulation', ['activeMetrics', 'QKDMetrics'], met.checked); });
      root.append(el('section.panel',
        el('h2', 'Demanda QKD e métricas'),
        qkdKvBoxes(tr),
        el('div.fields', { style: { marginTop: '12px' } },
          simPct(['qkdProtectedRequestProbability'], 'Fração protegida padrão (%)', 'de 0 a 100 %; vale para os geradores sem qkdProtectedProbability'),
          simNum(['qkdKeyDemand', 'keySizeBits'], 'Tamanho da chave', 'bits', (v) => v > 0 && Number.isInteger(v)),
          field('Intervalos de renovação da chave', intervals, 'µs; ' + (rates.length ? rates.map((r) => fmtNum(r, 3)).join(' / ') + ' Mbit/s' : '—')),
          simNum(['qkdWarmupFraction'], 'Warm-up das métricas QKD', 'fração das chegadas', (v) => v >= 0 && v < 1)),
        el('div.checks', { style: { marginTop: '8px' } },
          el('label.check', exact, ' Número exato de requisições (sem a "cauda" de 1 chegada por gerador)'),
          el('label.check', met, ' Gerar QKDMetrics.csv (bloqueio QKD por causa, SKR, QBER, Q-TRx, nós confiáveis, por par)')),
        el('div.toolbar', { style: { marginTop: '10px' } },
          el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('traffic') } }, 'Tráfego QKD por gerador →'),
          el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('qkdPhysicalLayer') } }, 'Camada física QKD (BB84, detector, fibra) →'),
          el('button.btn.small', { type: 'button', on: { click: () => App.figures.openWith([st().casePath], { template: 'qkdBp' }) } }, 'Gráficos QKD →'))));
    }
    return root;
  }

  function kvBox(k, v, unit) { return el('div', el('div.k', k), el('div.v', v, unit ? el('small', ' ' + unit) : null)); }

  /** 1.12.0: the values of the current files, side by side (QKD tab and QKD traffic panel). */
  function qkdKvBoxes(t) {
    return el('div.kv',
      kvBox('Geradores com campos QKD', fmtNum(t.withFields) + ' / ' + fmtNum(t.total)),
      kvBox('Requisições protegidas (esperado)', fmtNum(100 * t.protectedShare, 1), '%'),
      kvBox('Demanda média de chave', Number.isFinite(t.meanKey) ? fmtNum(t.meanKey, 2) : '—', 'Mbit/s'),
      kvBox('Carga inicial', fmtNum(t.loadBase, 1), 'Erlang'),
      kvBox('Incremento por ponto', fmtNum(t.loadInc, 1), 'Erlang'),
      Number.isFinite(t.loadLast) ? kvBox('Carga do último ponto (' + t.points + ')', fmtNum(t.loadLast, 1), 'Erlang') : null);
  }

  // ------------------------------------------------------------------ traffic

  function trafficQkdSummary() {
    const tr = st().model.traffic, sim = st().model.simulation || {};
    const gens = isPlainObject(tr) && Array.isArray(tr.requestGenerators) ? tr.requestGenerators : [];
    const globalP = Number(sim.qkdProtectedRequestProbability) || 0;
    const kd = isPlainObject(sim.qkdKeyDemand) ? sim.qkdKeyDemand : null;
    const policyMean = kd && Array.isArray(kd.updateIntervalsMicroseconds) && kd.updateIntervalsMicroseconds.length
      ? kd.updateIntervalsMicroseconds.reduce((s, us) => s + (kd.keySizeBits || 256) / us, 0) / kd.updateIntervalsMicroseconds.length : NaN;
    let lam = 0, lamP = 0, keyW = 0, withFields = 0, erl = 0, erlInc = 0;
    const probs = new Map(), keys = new Map();
    for (const g of gens) {
      const l = Number(g.arrivalRate) || 0;
      const mu = Number(g.holdRate) || 1;
      erl += l / mu; erlInc += (Number(g.arrivalRateIncrease) || 0) / mu;
      if (g.qkdProtectedProbability !== undefined) probs.set(Number(g.qkdProtectedProbability), (probs.get(Number(g.qkdProtectedProbability)) || 0) + 1);
      if (g.keyRateMbps !== undefined) keys.set(Number(g.keyRateMbps), (keys.get(Number(g.keyRateMbps)) || 0) + 1);
      const p = g.qkdProtectedProbability !== undefined ? Number(g.qkdProtectedProbability) : globalP;
      const k = g.keyRateMbps !== undefined ? Number(g.keyRateMbps) : policyMean;
      if (g.qkdProtectedProbability !== undefined || g.keyRateMbps !== undefined) withFields++;
      lam += l; lamP += l * p; keyW += l * p * k;
    }
    const mode = (m) => { let best, n = -1; for (const [v, c] of m) if (c > n) { best = v; n = c; } return best; };
    const points = Number(sim.loadPoints) || 0;
    return { total: gens.length, withFields, protectedShare: lam ? lamP / lam : 0, meanKey: lamP ? keyW / lamP : NaN,
      loadBase: erl, loadInc: erlInc, loadLast: points > 0 ? erl + (points - 1) * erlInc : NaN, points,
      commonProb: probs.size ? mode(probs) : globalP, commonKey: keys.size ? mode(keys) : NaN, anyKeyField: keys.size > 0, policyMean };
  }

  function applyQkdFields(match, prob, keyMbps) {
    const tr = st().model.traffic;
    if (!isPlainObject(tr) || !Array.isArray(tr.requestGenerators)) return 0;
    const gens = deepClone(tr.requestGenerators);
    let n = 0;
    for (const g of gens) {
      if (!match(g)) continue;
      n++;
      if (prob === null) { delete g.qkdProtectedProbability; delete g.keyRateMbps; continue; }
      g.qkdProtectedProbability = prob;
      if (keyMbps === undefined || keyMbps === null || Number.isNaN(keyMbps)) delete g.keyRateMbps;
      else g.keyRateMbps = keyMbps;
    }
    App.setValue('traffic', ['requestGenerators'], gens);
    return n;
  }

  /** Panel inserted in the Traffic tab when the case is in QKD mode. */
  const QKD_TERMS = [
    ['Requisição protegida por QKD', 'Requisição que, além do circuito clássico, precisa de uma sessão de distribuição quântica de chaves (QKD) entre a origem e o destino: um canal quântico (QCh) no núcleo reservado e pares Q-TRx em cada nó confiável da rota QKD. Ela só é aceita se o circuito clássico E a sessão QKD forem provisionados; se a parte QKD falhar, a requisição inteira é bloqueada (bloqueio QKD).'],
    ['Fração protegida (qkdProtectedProbability)', 'Probabilidade de cada requisição deste gerador ser protegida por QKD, sorteada requisição a requisição. No launcher é digitada em %: 0 % = nenhuma; 100 % = todas (no arquivo fica de 0 a 1). Não é fração da banda nem da carga: a carga clássica do gerador continua a mesma, só parte das requisições passa a pedir também chave quântica.', 'Ex.: 20 % → em média 1 de cada 5 requisições do gerador é protegida.'],
    ['Fração padrão (simulation)', 'qkdProtectedRequestProbability, na aba Simulação: vale para os geradores que não têm o campo próprio.'],
    ['Demanda de chave (keyRateMbps)', 'Taxa de chave secreta que a sessão QKD precisa entregar, em Mbit/s, enquanto a conexão estiver ativa. A sessão só é aceita se a taxa de chave segura (SKR) de todos os trechos da rota QKD for pelo menos esta demanda.', 'Ex.: chave AES de 256 bits renovada a cada 128 µs = 256/128 = 2 Mbit/s.'],
    ['Política qkdKeyDemand (sem keyRateMbps)', 'A demanda é sorteada para cada requisição protegida: tamanho da chave ÷ intervalo de renovação, com o intervalo escolhido ao acaso da lista do arquivo simulation.', 'Padrão: 256 bits e 64, 128 ou 256 µs → 4, 2 ou 1 Mbit/s.'],
    ['Requisições protegidas (esperado)', 'Média das frações protegidas ponderada pela carga de cada gerador: a porcentagem das requisições da simulação que devem pedir QKD.'],
    ['Lista QKD dedicada', 'Geradores extras com fração 1, para estudar só pares escolhidos. Eles somam carga à rede: cada requisição também ocupa espectro clássico.'],
  ];

  function trafficPanel(sum) {
    const net = st().model.network;
    const nodes = isPlainObject(net) && Array.isArray(net.nodes) ? net.nodes.map((n) => String(n.name)) : [];
    const rates = sum.rates.map((r) => r.rate);
    // 1.12.0: the boxes start with the values of THIS case's files (they used to keep the last typed values)
    const t0 = trafficQkdSummary();
    const caseKey = st().casePath;
    if (!trafficPanel.state || trafficPanel.stateFor !== caseKey) {
      const keyNow = Number.isFinite(t0.commonKey) ? t0.commonKey : t0.policyMean;
      trafficPanel.state = { src: '', dst: '', rates: new Set(), prob: fmtPct(t0.commonProb || 0), key: Number.isFinite(keyNow) ? String(keyNow).replace('.', ',') : '2',
        policy: t0.withFields > 0 && !t0.anyKeyField, dedSrc: '', dedDst: '', dedRate: '100',
        dedLoad: String(Math.round(t0.loadBase * 10) / 10 || 10).replace('.', ','), dedInc: String(Math.round(t0.loadInc * 10) / 10 || 5).replace('.', ',') };
      trafficPanel.stateFor = caseKey;
    }
    const s = trafficPanel.state;
    const nodeSel = (key) => {
      const x = el('select.input', el('option', { value: '' }, 'qualquer'), nodes.map((n) => el('option', { value: n }, n)));
      x.value = s[key];
      x.addEventListener('change', () => { s[key] = x.value; });
      return x;
    };
    const rateBox = el('div.checks', rates.map((r) => {
      const c = el('input', { type: 'checkbox', checked: s.rates.size === 0 || s.rates.has(r) });
      c.addEventListener('change', () => {
        if (s.rates.size === 0) rates.forEach((x) => s.rates.add(x));
        if (c.checked) s.rates.add(r); else s.rates.delete(r);
      });
      return el('label.check', c, ' ' + fmtBitRate(r));
    }));
    const inp = (key) => { const i = el('input.input.num', { type: 'text', value: s[key] }); i.addEventListener('input', () => { s[key] = i.value; }); return i; };
    const usePolicy = el('input', { type: 'checkbox', checked: s.policy });
    usePolicy.addEventListener('change', () => { s.policy = usePolicy.checked; });
    const match = (g) => (!s.src || String(g.source) === s.src) && (!s.dst || String(g.destination) === s.dst)
      && (s.rates.size === 0 || s.rates.has(Number(g.bitRate)));
    const t = t0;
    const byRate = new Map();
    for (const g of sum.gens) {
      const r = Number(g.bitRate);
      const e = byRate.get(r) || { n: 0, q: 0, p: [], k: [] };
      e.n++;
      if (g.qkdProtectedProbability !== undefined) { e.q++; e.p.push(Number(g.qkdProtectedProbability)); }
      if (g.keyRateMbps !== undefined) e.k.push(Number(g.keyRateMbps));
      byRate.set(r, e);
    }
    const rngPct = (a) => (a.length ? (Math.min(...a) === Math.max(...a) ? fmtPct(a[0]) + ' %' : fmtPct(Math.min(...a)) + '–' + fmtPct(Math.max(...a)) + ' %') : '—');
    const rng = (a) => (a.length ? (Math.min(...a) === Math.max(...a) ? fmtNum(a[0], 3) : fmtNum(Math.min(...a), 3) + '–' + fmtNum(Math.max(...a), 3)) : '—');

    const apply = el('button.btn.primary', { type: 'button', on: { click: () => {
      const pp = parseNumber(s.prob), k = s.policy ? null : parseNumber(s.key);
      if (!(pp >= 0 && pp <= 100)) { App.toast('Fração protegida deve estar entre 0 e 100 %.', 'err'); return; }
      const p = pp / 100;
      if (!s.policy && !(k > 0)) { App.toast('Demanda de chave deve ser > 0 Mbit/s.', 'err'); return; }
      const n = applyQkdFields(match, p, k);
      App.toast('Campos QKD aplicados a ' + fmtNum(n) + ' gerador(es) (não salvo).', 'ok');
      App.renderTab();
    } } }, 'Aplicar aos geradores filtrados');
    const remove = el('button.btn', { type: 'button', on: { click: () => {
      const n = applyQkdFields(match, null);
      App.toast('Campos QKD removidos de ' + fmtNum(n) + ' gerador(es).', 'ok');
      App.renderTab();
    } } }, 'Remover campos QKD');
    const addDedicated = el('button.btn', { type: 'button', on: { click: () => {
      const rate = parseNumber(s.dedRate) * 1e9, load = parseNumber(s.dedLoad), inc = parseNumber(s.dedInc), key = s.policy ? null : parseNumber(s.key);
      if (!(rate > 0) || !(load >= 0) || !(inc >= 0) || (!s.policy && !(key > 0))) { App.toast('Valores inválidos para os geradores QKD dedicados.', 'err'); return; }
      const pairs = [];
      for (const a of nodes) for (const b of nodes) if (a !== b && (!s.dedSrc || a === s.dedSrc) && (!s.dedDst || b === s.dedDst)) pairs.push([a, b]);
      if (!pairs.length) { App.toast('Nenhum par selecionado.', 'err'); return; }
      const tr = st().model.traffic;
      const gens = deepClone(isPlainObject(tr) && Array.isArray(tr.requestGenerators) ? tr.requestGenerators : []);
      for (const [a, b] of pairs) {
        const g = { source: a, destination: b, bitRate: rate, arrivalRate: load / pairs.length, holdRate: 1.0,
          arrivalRateIncrease: inc / pairs.length, qkdProtectedProbability: 1.0 };
        if (key !== null) g.keyRateMbps = key;
        gens.push(g);
      }
      if (!isPlainObject(tr)) App.setValue('traffic', [], { requestGenerators: gens });
      else App.setValue('traffic', ['requestGenerators'], gens);
      App.toast(pairs.length + ' geradores QKD dedicados adicionados (não salvo).', 'ok');
      App.renderTab();
    } } }, 'Adicionar geradores QKD');

    return el('section.panel.qkd-traffic',
      el('h2', 'Tráfego QKD'),
      el('p.lead', 'Cada gerador do arquivo traffic pode ter dois campos opcionais: a fração protegida (qkdProtectedProbability) e a demanda de chave (keyRateMbps). Sem eles valem a fração padrão e a política qkdKeyDemand do arquivo simulation. Gerar um tráfego novo acima apaga esses campos: aplique-os de novo aqui.'),
      App.traffic.glossary('O que significa cada variável QKD', QKD_TERMS, 'qkdtraffic', true),
      qkdKvBoxes(t),
      el('div.stack-s', { style: { margin: '12px 0 0' } },
        el('h3', 'Demanda de chave (vale para as duas ações abaixo)'),
        el('div.fields', field('Demanda fixa por requisição', inp('key'), 'Mbit/s → campo keyRateMbps de cada gerador')),
        el('label.check', usePolicy, ' Não fixar: usar a política qkdKeyDemand da aba QKD (tamanho da chave ÷ intervalo sorteado; padrão 1, 2 ou 4 Mbit/s por requisição)')),
      el('div.table-wrap', { style: { margin: '12px 0' } }, el('table.grid.data',
        el('thead', el('tr', el('th', 'Taxa'), el('th.num', 'Geradores'), el('th.num', 'Com campos QKD'), el('th.num', 'Fração protegida'), el('th.num', 'Chave (Mbit/s)'))),
        el('tbody', [...byRate.entries()].sort((a, b) => a[0] - b[0]).map(([r, e]) => el('tr',
          el('td', fmtBitRate(r)), el('td.num', fmtNum(e.n)), el('td.num', fmtNum(e.q)), el('td.num', rngPct(e.p)), el('td.num', rng(e.k))))))),
      el('div.two-col',
        el('div.stack-s',
          el('h3', 'Marcar geradores existentes'),
          el('div.fields', field('Origem', nodeSel('src')), field('Destino', nodeSel('dst'))),
          rates.length ? el('div.field', el('div.flabel', 'Taxas'), rateBox,
            rates.length > 1 ? App.allNoneButtons(
              () => { rates.forEach((x) => s.rates.add(x)); rateBox.querySelectorAll('input[type=checkbox]').forEach((c) => { c.checked = true; }); },
              () => { s.rates.clear(); rates.forEach((x) => s.rates.add(-1)); rateBox.querySelectorAll('input[type=checkbox]').forEach((c) => { c.checked = false; }); }) : null) : null,
          el('div.fields', field('Fração protegida (%)', inp('prob'), 'de 0 a 100 %')),
          el('div.help', 'Ex.: 20 % e 2 Mbit/s → em média 1 de cada 5 requisições dos geradores filtrados pede também uma sessão QKD que entregue 2 Mbit/s de chave; as outras são só clássicas. A demanda de chave usada é a do quadro "Demanda de chave" acima.'),
          el('div.toolbar', apply, remove)),
        el('div.stack-s',
          el('h3', 'Gerar lista QKD dedicada'),
          el('p.small.muted', 'Acrescenta novos geradores em que todas as requisições são protegidas (fração = 1), para os pares escolhidos ("qualquer" = todos os nós). A carga informada é somada à carga do tráfego atual e dividida igualmente entre os pares; cada requisição ocupa um circuito clássico da taxa indicada e uma sessão QKD com a demanda de chave indicada.'),
          el('div.fields', field('Origem', nodeSel('dedSrc')), field('Destino', nodeSel('dedDst')),
            field('Taxa clássica', inp('dedRate'), 'Gb/s'),
            field('Carga inicial total', inp('dedLoad'), 'Erlang'), field('Incremento por ponto', inp('dedInc'), 'Erlang')),
          el('div.toolbar', addDedicated))));
  }

  App.qkdcase = { isQkd, drawModeBar, renderTab, trafficPanel, planning, spansOf, enableDialog, trafficQkdSummary };
})(window.App);
