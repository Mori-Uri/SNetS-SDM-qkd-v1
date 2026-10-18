/*
 * Estúdio de gráficos: builds publication figures from the result CSVs of
 * several cases at once (line × load, grouped/stacked bars such as blocking
 * causes, per-link utilization, per-core bars and the multi-core fibre diagram),
 * with full control of the visual style and SVG/PNG export.
 *
 * A figure is a JSON "spec" (cases + data selection + style) that can be saved in
 * launcher/figures/ and reopened later; the model is always rebuilt from the
 * current CSVs, so a saved figure follows new simulation results.
 */
(function (App) {
  'use strict';
  const { el, clear } = App;
  const R = () => App.figRender;

  // ================================================================ CSV access

  const caseCache = new Map();
  const csvCache = new Map();

  async function caseData(path) {
    if (caseCache.has(path)) return caseCache.get(path);
    const p = (async () => {
      const cd = await App.api.readCase(path);
      const files = {};
      for (const f of ['network', 'physicalLayer', 'qkdPhysicalLayer', 'simulation', 'others', 'traffic']) {
        try { files[f] = JSON.parse(cd.files[f]); } catch (e) { /* ignore */ }
      }
      const name = App.caseNameOf(path);
      const csv = (cd.csv || []).map((c) => ({ name: c.name, size: c.size, mtime: c.mtime, suffix: suffixOf(name, c.name) }));
      const l = App.validate.trafficLoad(files.traffic);
      return { path, name, csv, files, loads: l.generators ? l : null };
    })();
    caseCache.set(path, p);
    try { return await p; } catch (e) { caseCache.delete(path); throw e; }
  }

  function suffixOf(caseName, csvName) {
    let s = csvName.replace(/\.csv$/i, '');
    if (s.startsWith(caseName + '_')) s = s.slice(caseName.length + 1);
    return s;
  }

  const NUMLIKE = /^\s*[-+]?((\d+\.?\d*|\.\d+)([eE][-+]?\d+)?|NaN|Infinity)\s*$/;

  /** Robust parser for the SNetS "Metrics,LoadPoint,<dims>, ,rep0.." files (tolerates rows with extra empty dims). */
  function parseCsv(text) {
    const lines = text.split(/\r?\n/);
    const header = (lines[0] || '').split(',');
    if (isSlotHeader(header) && (header[5] || '').trim() === 'Cause') return parseSlotBlockCsv(lines, header);
    if (isSlotHeader(header)) return parseSlotCsv(lines, header);
    if ((header[0] || '').trim().toLowerCase() === 'loadpoint') {
      const w = App.results.parseCsv(text);
      return finishParsed({ ok: w.ok, dims: [], rows: w.rows || [] });
    }
    const lpIdx = header.findIndex((h) => h.trim().toLowerCase() === 'loadpoint');
    if ((header[0] || '').trim().toLowerCase() !== 'metrics' || lpIdx < 0) return { ok: false, lines: lines.slice(0, 30) };
    let hb = header.findIndex((h, i) => i > lpIdx && h.trim() === '');
    if (hb < 0) hb = header.findIndex((h) => /^rep\d+/i.test(h.trim()));
    const dims = header.slice(lpIdx + 1, hb).map((h) => h.trim());
    const rows = [];
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line || !line.trim()) continue;
      const c = line.split(',');
      if (c[0].trim().toLowerCase() === 'metrics') continue;
      const lp = Number(c[lpIdx]);
      if (!Number.isFinite(lp)) continue;
      let k = c.length - 1;
      while (k > lpIdx && c[k].trim() !== '' && NUMLIKE.test(c[k])) k--;
      // k is the blank separator before the replications
      let d = c.slice(lpIdx + 1, k).map((x) => x.trim());
      if (d.length > dims.length) {
        const extra = d.length - dims.length;
        let removed = 0;
        d = d.filter((x) => { if (x === '' && removed < extra) { removed++; return false; } return true; });
        d = d.slice(0, dims.length);
      }
      while (d.length < dims.length) d.push('');
      rows.push({ metric: c[0].trim(), lp, dims: d, reps: c.slice(k + 1).map(Number).filter(Number.isFinite) });
    }
    return finishParsed({ ok: true, dims, rows });
  }

  /** What to do when a results file is missing (Parte 27). */
  function fileHint(file) {
    const f = String(file || '');
    if (/Fragmentation$/.test(f)) return ' Ligue a métrica ' + f + ' na aba Métricas do caso e rode a simulação de novo.';
    if (f === 'SlotBlocking') return ' Ele é gravado pelo simulador da Parte 29 (métrica SlotBlocking, ligada por padrão, junto com BlockingProbability).';
    if (f === 'SlotUtilization') return ' Ele é gravado pelo simulador da Parte 27 (métrica SlotUtilization, ligada por padrão, junto com SpectrumUtilization).';
    if (f === 'BlockingDetails') return ' Ele é gravado pelo simulador da Parte 25 em diante (o diagnóstico do espectro, pelo da Parte 27).';
    return '';
  }

  /** Index of a dimension by name, ignoring case ("Link" and "link"). */
  function dimIndex(p, name) {
    const n = String(name).toLowerCase();
    return p.dims.findIndex((d) => d.toLowerCase() === n);
  }

  // ---------------------------------------------------------------- SlotUtilization.csv (Parte 27)

  function isSlotHeader(header) {
    return (header[0] || '').trim().toLowerCase() === 'metrics' && (header[2] || '').trim() === 'Link'
      && (header[3] || '').trim() === 'Core' && (header[4] || '').trim() === 'Replication';
  }

  /**
   * SlotUtilization.csv: Metrics,LoadPoint,Link,Core,Replication,1..S. One row per load point, link, core and
   * replication; keeps the mean of the replications of each slot (Float64Array) by "lp|link|core".
   */
  function parseSlotCsv(lines, header) {
    const nSlots = header.length - 5;
    const sums = new Map(), counts = new Map();
    const links = [], linkSet = new Set(), cores = new Set(), lps = new Set(), reps = new Set();
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const c = line.split(',');
      if (c.length < 6) continue;
      const lp = Number(c[1]);
      if (!Number.isFinite(lp)) continue;
      const link = c[2].trim(), core = c[3].trim();
      const key = lp + '|' + link + '|' + core;
      let acc = sums.get(key);
      if (!acc) { acc = new Float64Array(nSlots); sums.set(key, acc); counts.set(key, 0); }
      for (let k = 0; k < nSlots; k++) { const v = Number(c[5 + k]); if (Number.isFinite(v)) acc[k] += v; }
      counts.set(key, counts.get(key) + 1);
      if (!linkSet.has(link)) { linkSet.add(link); links.push(link); }
      cores.add(core); lps.add(lp); reps.add(c[4].trim());
    }
    for (const [key, acc] of sums) { const n = counts.get(key) || 1; for (let k = 0; k < nSlots; k++) acc[k] /= n; }
    const p = { ok: true, slot: true, dims: [], rows: [], nSlots, links, cores: [...cores].sort((a, b) => Number(a) - Number(b)), means: sums, nReps: reps.size };
    finishParsed(p);
    p.lps = [...lps].sort((a, b) => a - b);
    return p;
  }

  /** Natural order of link names "<a-b>": by source, then destination (numbers as numbers). */
  function linkCompare(a, b) {
    const pa = /^<([^<>]+?)-([^<>]+)>$/.exec(a), pb = /^<([^<>]+?)-([^<>]+)>$/.exec(b);
    if (!pa || !pb) return String(a).localeCompare(String(b));
    const cmp = (x, y) => { const nx = Number(x), ny = Number(y); return Number.isFinite(nx) && Number.isFinite(ny) ? nx - ny : String(x).localeCompare(String(y)); };
    return cmp(pa[1], pb[1]) || cmp(pa[2], pb[2]);
  }

  function finishParsed(p) {
    if (!p.ok) return p;
    p.metrics = [];
    p.byMetric = new Map();
    for (const r of p.rows) {
      if (!p.byMetric.has(r.metric)) { p.byMetric.set(r.metric, []); p.metrics.push(r.metric); }
      p.byMetric.get(r.metric).push(r);
    }
    p.lps = [...new Set(p.rows.map((r) => r.lp))].sort((a, b) => a - b);
    return p;
  }

  async function getCsv(path, suffix) {
    const cd = await caseData(path);
    const f = cd.csv.find((c) => c.suffix === suffix) || cd.csv.find((c) => c.name.replace(/\.csv$/i, '').endsWith(suffix));
    if (!f) return null;
    const key = path + '|' + f.name + '|' + f.mtime;
    if (!csvCache.has(key)) {
      csvCache.set(key, App.api.csv(path, f.name).then((d) => parseCsv(d.text)).catch((e) => { csvCache.delete(key); throw e; }));
    }
    return csvCache.get(key);
  }

  /** Values of each dimension of a metric (in file order). */
  function dimValues(parsed, metric) {
    const rows = parsed.byMetric.get(metric) || [];
    return parsed.dims.map((_, i) => [...new Set(rows.map((r) => r.dims[i]))]);
  }

  function defaultFilter(vals) { return vals.includes('all') ? 'all' : vals[0]; }

  /** Replications of metric at load point lp, matching filters (dims not in `free`). */
  function repsAt(parsed, metric, lp, filters, freeDim, freeVal) {
    const rows = parsed.byMetric.get(metric) || [];
    const dv = dimValues(parsed, metric);
    const out = [];
    for (const r of rows) {
      if (r.lp !== lp) continue;
      let ok = true;
      for (let i = 0; i < parsed.dims.length; i++) {
        const d = parsed.dims[i];
        const want = d === freeDim ? freeVal : (filters && d in filters && dv[i].includes(filters[d]) ? filters[d] : defaultFilter(dv[i]));
        if (r.dims[i] !== want) { ok = false; break; }
      }
      if (ok) out.push(...r.reps);
    }
    return out;
  }

  function stats(v) { return App.results.stats(v); }

  // ================================================================ labels

  const CAUSES_QKD = [
    [/^QKD blocking probability by classical/i, 'Recursos clássicos', '#4472C4'],
    [/^QKD blocking probability by QCh spectrum/i, 'Espectro do QCh', '#ED7D31'],
    [/^QKD blocking probability by SKR/i, 'SKR insuficiente', '#70AD47'],
    [/^QKD blocking probability by PICh/i, 'PICh', '#FFC000'],
    [/^QKD blocking probability by lack of QTx/i, 'Falta de QTx/QRx', '#5B9BD5'],
    [/^QKD blocking probability by other/i, 'Outros', '#7F7F7F'],
  ];
  const CAUSES = [
    [/by fragmentation/i, 'Fragmentação', '#C00000'],
    [/by qotn/i, 'QoTN', '#FFC000'],
    [/by qoto/i, 'QoTO', '#7030A0'],
    [/by crosstalk in other/i, 'XTO', '#0070C0'],
    [/by crosstalk$/i, 'XT', '#00B050'],
    [/lack of transmitters/i, 'Falta de Tx', '#A5A5A5'],
    [/lack of receivers/i, 'Falta de Rx', '#7F7F7F'],
    [/by other/i, 'Outros', '#000000'],
    [/QKD blocking probability by classical/i, 'Recursos clássicos', '#4472C4'],
    [/QKD blocking probability by QCh spectrum/i, 'Espectro do QCh', '#ED7D31'],
    [/QKD blocking probability by SKR/i, 'SKR insuficiente', '#70AD47'],
    [/QKD blocking probability by PICh/i, 'PICh', '#FFC000'],
  ];

  /** Values of the Cause / Class dimensions of BlockingDetails.csv. */
  const DIM_LABELS = {
    'Fragmentation': ['Fragmentação', '#C00000'], 'QoTN': ['QoTN', '#FFC000'], 'QoTO': ['QoTO', '#7030A0'],
    'Crosstalk': ['XT', '#00B050'], 'Crosstalk in other': ['XTO', '#0070C0'],
    'Lack of transmitters': ['Falta de Tx', '#A5A5A5'], 'Lack of receivers': ['Falta de Rx', '#7F7F7F'],
    'Lack of free spectrum': ['Falta de espectro livre', '#404040'], 'Other': ['Outros', '#000000'],
    'QKD session protection': ['QKD: proteção de sessões ativas', '#9DC3E6'], 'QKD QCh spectrum': ['QKD: espectro do QCh', '#ED7D31'],
    'QKD SKR': ['QKD: SKR insuficiente', '#A9D18E'], 'QKD PICh': ['QKD: PICh', '#F4B183'], 'QKD QTx/QRx': ['QKD: falta de QTx/QRx', '#5B9BD5'], 'QKD other': ['QKD: outros', '#D9D9D9'],
    'Spectrum: free band available': ['Havia faixa livre (bloqueio por QoT/XT ou proteção QKD)', '#7030A0'],
    'Spectrum: contiguity fragmentation': ['Fragmentação: contiguidade (slots livres não vizinhos)', '#ED7D31'],
    'Spectrum: continuity fragmentation': ['Fragmentação: continuidade (slots livres desalinhados entre enlaces)', '#FFC000'],
    'Spectrum: not enough free slots': ['Faltavam slots livres', '#404040'],
    'Spectrum: no route': ['Sem rota', '#A5A5A5'],
    'Spectrum: QKD side': ['Recusada pelo lado QKD', '#5B9BD5'],
    'classical': ['Clássicas (não protegidas)', null], 'qkd': ['Protegidas por QKD', null], 'all': ['Todas as requisições', null],
  };

  function autoLabel(item) {
    if (DIM_LABELS[item]) return DIM_LABELS[item][0];
    for (const [re, l] of CAUSES_QKD) if (re.test(item)) return l;
    for (const [re, l] of CAUSES) if (re.test(item)) return l;
    let m = /per core (\d+)$/i.exec(item);
    if (m) return 'Núcleo ' + m[1];
    return item;
  }

  function causeColor(item) {
    if (DIM_LABELS[item]) return DIM_LABELS[item][1];
    for (const [re, , c] of CAUSES_QKD) if (re.test(item)) return c;
    for (const [re, , c] of CAUSES) if (re.test(item)) return c;
    return null;
  }


  // ---------------------------------------------------------------- SlotBlocking.csv (Parte 29)

  /**
   * SlotBlocking.csv: Metrics,LoadPoint,Link,Core,Replication,Cause,1..S, integer counts. Slot rows ("Slot attempts",
   * "Slot blocked", "Slot blocked by cause") have S values; request rows ("Requests on link and core", "Blocked
   * requests on link and core" [by cause], "Blocked requests without spectrum band") have one. The replications are
   * summed, so ratios are pooled over them.
   */
  function parseSlotBlockCsv(lines, header) {
    const nSlots = header.length - 6;
    const att = new Map(), blkAll = new Map(), blk = new Map();
    const reqAtt = new Map(), reqBlkAll = new Map(), reqBlk = new Map(), noBand = new Map();
    const links = [], linkSet = new Set(), cores = new Set(), lps = new Set(), reps = new Set(), causes = [];
    const addArr = (m, key, c) => { let a = m.get(key); if (!a) { a = new Float64Array(nSlots); m.set(key, a); } for (let k = 0; k < nSlots; k++) { const v = Number(c[6 + k]); if (v) a[k] += v; } };
    const addNum = (m, key, v) => m.set(key, (m.get(key) || 0) + (Number(v) || 0));
    const sub = (m, cause) => { let x = m.get(cause); if (!x) { x = new Map(); m.set(cause, x); if (!causes.includes(cause)) causes.push(cause); } return x; };
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const c = line.split(',');
      if (c.length < 7) continue;
      const lp = Number(c[1]);
      if (!Number.isFinite(lp)) continue;
      const metric = c[0].trim(), link = c[2].trim(), core = c[3].trim(), cause = c[5].trim();
      lps.add(lp); reps.add(c[4].trim());
      if (metric === 'Blocked requests without spectrum band') { addNum(noBand, lp, c[6]); continue; }
      const key = lp + '|' + link + '|' + core;
      if (!linkSet.has(link)) { linkSet.add(link); links.push(link); }
      cores.add(core);
      if (metric === 'Slot attempts') addArr(att, key, c);
      else if (metric === 'Slot blocked') addArr(blkAll, key, c);
      else if (metric === 'Slot blocked by cause') addArr(sub(blk, cause), key, c);
      else if (metric === 'Requests on link and core') addNum(reqAtt, key, c[6]);
      else if (metric === 'Blocked requests on link and core') addNum(reqBlkAll, key, c[6]);
      else if (metric === 'Blocked requests on link and core by cause') addNum(sub(reqBlk, cause), key, c[6]);
    }
    const p = { ok: true, slotblock: true, dims: [], rows: [], nSlots, links, cores: [...cores].sort((a, b) => Number(a) - Number(b)),
      att, blkAll, blk, reqAtt, reqBlkAll, reqBlk, noBand, causes, nReps: reps.size };
    finishParsed(p);
    p.lps = [...lps].sort((a, b) => a - b);
    return p;
  }

  // ================================================================ templates

  // Parte 27: models grouped by subject (optgroups of the "Modelo de gráfico" box); ids are kept, so saved figures still open
  const TEMPLATE_GROUPS = ['Bloqueio', 'Causas de bloqueio', 'Núcleos', 'Espectro e utilização', 'Fragmentação', 'Mapas de calor', 'Correlações', 'QKD', 'Outros'];
  const TEMPLATES = [
    // ---- Bloqueio
    { id: 'bp', group: 'Bloqueio', name: 'Bloqueio de circuitos × carga', type: 'line', file: 'BlockingProbability', metric: 'Blocking probability', y: 'Probabilidade de Bloqueio de Circuito', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'bbp', group: 'Bloqueio', name: 'Bloqueio de banda × carga', type: 'line', file: 'BitRateBlockingProbability', metric: 'BitRate blocking probability', y: 'Probabilidade de Bloqueio de Banda', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'bpAll', group: 'Bloqueio', name: 'Bloqueio total, das clássicas e das protegidas por QKD × carga', type: 'line', file: 'BlockingDetails', metric: 'Blocking probability', seriesBy: 'dim:Class', y: 'Probabilidade de Bloqueio', x: 'Carga (Erlangs)', xMode: 'erl' },
    // ---- Causas de bloqueio
    { id: 'causes', group: 'Causas de bloqueio', name: 'Causas: barras empilhadas', type: 'bars', file: 'BlockingProbability', itemsFrom: 'metrics', itemsRe: /^blocking probability by /i, xAxis: 'cases', stacked: true, y: 'Probabilidade de Bloqueio' },
    { id: 'causes100', group: 'Causas de bloqueio', name: 'Causas: empilhadas em 100 %', type: 'bars', file: 'BlockingProbability', itemsFrom: 'metrics', itemsRe: /^blocking probability by /i, xAxis: 'cases', stacked: true, normalize: true, y: '' },
    { id: 'causesGroup', group: 'Causas de bloqueio', name: 'Causas: barras agrupadas (QoTO e XT)', type: 'bars', file: 'BlockingProbability', itemsFrom: 'metrics', itemsRe: /^blocking probability by (qoto|crosstalk)/i, xAxis: 'cases', stacked: false, y: 'Probabilidade de Bloqueio' },
    { id: 'causesAll', group: 'Causas de bloqueio', name: 'Todas as causas, com as QKD: empilhadas', type: 'bars', file: 'BlockingDetails', metric: 'Blocking probability by cause', itemsFrom: 'dim', dim: 'Cause', xAxis: 'cases', stacked: true, y: 'Probabilidade de Bloqueio' },
    { id: 'causesAll100', group: 'Causas de bloqueio', name: 'Todas as causas, com as QKD: em 100 %', type: 'bars', file: 'BlockingDetails', metric: 'Blocking probability by cause', itemsFrom: 'dim', dim: 'Cause', xAxis: 'cases', stacked: true, normalize: true, y: '' },
    { id: 'diagSpectrum', group: 'Causas de bloqueio', name: 'Diagnóstico do espectro: falta de slots × fragmentação × faixa livre', type: 'bars', file: 'BlockingDetails', metric: 'Blocking probability by spectrum diagnosis', itemsFrom: 'dim', dim: 'Cause', xAxis: 'cases', stacked: true, y: 'Probabilidade de Bloqueio',
      desc: 'Para cada requisição bloqueada, olha o espectro livre da rota tentada: faltavam slots, os slots livres não estavam alinhados nos enlaces (continuidade) ou não eram vizinhos (contiguidade), ou havia uma faixa livre e o bloqueio veio da camada física/proteção QKD. Gravado pelo simulador da Parte 27.' },
    { id: 'diagSpectrum100', group: 'Causas de bloqueio', name: 'Diagnóstico do espectro em 100 %', type: 'bars', file: 'BlockingDetails', metric: 'Blocking probability by spectrum diagnosis', itemsFrom: 'dim', dim: 'Cause', xAxis: 'cases', stacked: true, normalize: true, y: '' },
    { id: 'diagSpectrumLoad', group: 'Causas de bloqueio', name: 'Diagnóstico do espectro × carga (uma linha por diagnóstico)', type: 'line', file: 'BlockingDetails', metric: 'Blocking probability by spectrum diagnosis', seriesBy: 'dim:Cause', y: 'Probabilidade de Bloqueio', x: 'Carga (Erlangs)', xMode: 'erl' },
    // ---- Núcleos
    { id: 'coreBars', group: 'Núcleos', name: 'Bloqueio por núcleo', type: 'bars', file: 'BlockingProbability', itemsFrom: 'metrics', itemsRe: /^blocking probability per core \d+$/i, xAxis: 'items', y: 'Probabilidade de Bloqueio' },
    { id: 'coreCauses', group: 'Núcleos', name: 'Bloqueio por núcleo, com as causas', type: 'bars', file: 'BlockingDetails', metric: 'Blocking probability per core by cause', itemsFrom: 'dim', dim: 'Core', seriesDim: 'Cause', xAxis: 'items', stacked: true, y: 'Probabilidade de Bloqueio do núcleo', x: 'Núcleo' },
    { id: 'coreCausesShare', group: 'Núcleos', name: 'Parcela do bloqueio total em cada núcleo, por causa', type: 'bars', file: 'BlockingDetails', metric: 'Share of all requests blocked per core by cause', itemsFrom: 'dim', dim: 'Core', seriesDim: 'Cause', xAxis: 'items', stacked: true, y: 'Requisições bloqueadas / todas as requisições', x: 'Núcleo' },
    { id: 'coreUtilBars', group: 'Núcleos', name: 'Utilização por núcleo', type: 'bars', file: 'SpectrumUtilization', metric: 'Utilization per Core', itemsFrom: 'dim', dim: 'Core', xAxis: 'items', y: 'Utilização do núcleo', x: 'Núcleo' },
    { id: 'cores', group: 'Núcleos', name: 'Diagrama da fibra: uso e bloqueio de cada núcleo', type: 'cores' },
    // ---- Espectro e utilização
    { id: 'util', group: 'Espectro e utilização', name: 'Utilização do espectro × carga', type: 'line', file: 'SpectrumUtilization', metric: 'Utilization', y: 'Utilização do Espectro', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'links', group: 'Espectro e utilização', name: 'Utilização dos enlaces (ordenada, um painel por caso)', type: 'bars', file: 'SpectrumUtilization', metric: 'Utilization Per Link', itemsFrom: 'dim', dim: 'Link', xAxis: 'items', layout: 'panels', sort: 'asc', y: 'Taxa de Utilização dos Enlaces', x: 'Enlaces' },
    { id: 'slotProfile', group: 'Espectro e utilização', name: 'Perfil de ocupação por slot (uma linha por núcleo)', type: 'slotline', file: 'SlotUtilization', y: 'Ocupação do slot', x: 'Slot',
      desc: 'Ocupação média de cada slot nos enlaces escolhidos, uma linha por núcleo: mostra onde a política de alocação concentra os circuitos (ex.: first fit à esquerda). Usa o SlotUtilization.csv (Parte 27).' },
    { id: 'mod', group: 'Espectro e utilização', name: 'Circuitos por modulação', type: 'bars', file: 'ModulationUtilization', metric: 'Percentage of cicuits per modulation', itemsFrom: 'dim', dim: 'Modulation', xAxis: 'cases', y: 'Percentual de circuitos' },
    // ---- Fragmentação
    { id: 'fragExt', group: 'Fragmentação', name: 'Fragmentação externa × carga (vertical e horizontal)', type: 'line', file: 'ExternalFragmentation', metric: 'External Fragmentation (Vertical)', metrics: ['External Fragmentation (Vertical)', 'External Fragmentation (Horizontal)'], seriesBy: 'metric', y: 'Fragmentação externa', x: 'Carga (Erlangs)', xMode: 'erl',
      desc: 'Vertical: média, nos núcleos de cada enlace e em todos os enlaces, de 1 − (maior faixa livre ÷ total de slots livres), observada a cada chegada. Horizontal: a mesma medida na interseção do espectro livre ao longo da rota de cada requisição, no núcleo escolhido. Requer ExternalFragmentation ligada na aba Métricas.' },
    { id: 'fragEntropy', group: 'Fragmentação', name: 'Entropia da fragmentação × carga', type: 'line', file: 'ExternalFragmentation', metric: 'Entropy External Fragmentation (Vertical)', y: 'Entropia da fragmentação', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'fragLinks', group: 'Fragmentação', name: 'Fragmentação externa por enlace (ordenada)', type: 'bars', file: 'ExternalFragmentation', metric: 'External Fragmentation Per Link', itemsFrom: 'dim', dim: 'link', xAxis: 'items', layout: 'panels', sort: 'asc', y: 'Fragmentação externa', x: 'Enlaces' },
    { id: 'fragRel', group: 'Fragmentação', name: 'Fragmentação relativa por tamanho de requisição (slots)', type: 'bars', file: 'RelativeFragmentation', metric: 'Relative fragmentation per spectrum size (c)', itemsFrom: 'dim', dim: 'spectrum size (c)', xAxis: 'items', y: 'Fragmentação relativa', x: 'Tamanho da requisição (slots)',
      desc: 'Para cada tamanho c (em slots), a fração dos slots livres que não pode receber uma requisição de c slots contíguos. Requer RelativeFragmentation ligada na aba Métricas.' },
    // ---- Mapas de calor
    { id: 'heat', group: 'Mapas de calor', name: 'Topologia: utilização dos enlaces', type: 'topo', file: 'SpectrumUtilization', metric: 'Utilization Per Link' },
    { id: 'heatBlock', group: 'Mapas de calor', name: 'Topologia: utilização e bloqueio dos enlaces (clássico × QKD)', type: 'topo', file: 'SpectrumUtilization', metric: 'Utilization Per Link', topoBlock: true },
    { id: 'heatFrag', group: 'Mapas de calor', name: 'Topologia: fragmentação externa dos enlaces', type: 'topo', file: 'ExternalFragmentation', metric: 'External Fragmentation Per Link' },
    { id: 'slotHeat', group: 'Mapas de calor', name: 'Espectro: ocupação dos slots de cada núcleo em cada enlace', type: 'slots', file: 'SlotUtilization' },
    { id: 'slotBlock', group: 'Mapas de calor', name: 'Espectro: bloqueio em cada slot (bloqueadas ÷ tentativas)', type: 'slotblock', file: 'SlotBlocking', blockMode: 'ratio',
      desc: 'Para cada slot de cada núcleo de cada enlace, a fração das tentativas que terminaram bloqueadas. Uma tentativa é a faixa do candidato da requisição (o circuito aceito, ou o candidato que foi bloqueado) em cada enlace da rota. À direita: requisições bloqueadas ÷ requisições que usaram aquele núcleo do enlace, e a porcentagem. Usa o SlotBlocking.csv (Parte 29).' },
    { id: 'slotBlockCauses', group: 'Mapas de calor', name: 'Espectro: causa do bloqueio em cada slot (todas as causas, uma cor cada)', type: 'slotblock', file: 'SlotBlocking', blockMode: 'causes',
      desc: 'Cada slot recebe a cor da causa com mais bloqueios ali (passe o mouse para ver todas). Cinza: houve tentativas e nenhum bloqueio; branco: nenhuma tentativa. Escolha uma causa em "Causa" para ver só ela. Usa o SlotBlocking.csv (Parte 29).' },
    { id: 'slotBlockQoTO', group: 'Mapas de calor', name: 'Espectro: onde ocorreram os bloqueios por QoTO', type: 'slotblock', file: 'SlotBlocking', blockMode: 'causes', blockCause: 'QoTO',
      desc: 'Só os bloqueios por QoTO (o novo circuito degradaria a QoT de circuitos ativos), na cor da causa. Troque a causa na seção 2 para ver as outras.' },
    // ---- Correlações
    { id: 'scLinkUtilBlock', group: 'Correlações', name: 'Enlaces: utilização × bloqueio (um ponto por enlace)', type: 'scatter', by: 'Link', xFile: 'SpectrumUtilization', xMetric: 'Utilization Per Link', yFile: 'BlockingDetails', yMetric: 'Link blocking probability', x: 'Utilização do enlace', y: 'Bloqueio do enlace',
      desc: 'Cada ponto é um enlace dirigido; r é o coeficiente de correlação de Pearson e a reta tracejada, o ajuste por mínimos quadrados.' },
    { id: 'scLinkUtilFrag', group: 'Correlações', name: 'Enlaces: utilização × fragmentação externa', type: 'scatter', by: 'Link', xFile: 'SpectrumUtilization', xMetric: 'Utilization Per Link', yFile: 'ExternalFragmentation', yMetric: 'External Fragmentation Per Link', x: 'Utilização do enlace', y: 'Fragmentação externa' },
    { id: 'scLinkFragBlock', group: 'Correlações', name: 'Enlaces: fragmentação externa × bloqueio', type: 'scatter', by: 'Link', xFile: 'ExternalFragmentation', xMetric: 'External Fragmentation Per Link', yFile: 'BlockingDetails', yMetric: 'Link blocking probability', x: 'Fragmentação externa', y: 'Bloqueio do enlace' },
    { id: 'scCoreUtilBlock', group: 'Correlações', name: 'Núcleos: utilização × bloqueio (um ponto por núcleo)', type: 'scatter', by: 'Core', xFile: 'SpectrumUtilization', xMetric: 'Utilization per Core', yFile: 'BlockingDetails', yMetric: 'Blocking probability per core', x: 'Utilização do núcleo', y: 'Bloqueio do núcleo' },
    { id: 'scLoadUtilBlock', group: 'Correlações', name: 'Cargas: utilização × bloqueio (um ponto por carga)', type: 'scatter', by: 'LoadPoint', xFile: 'SpectrumUtilization', xMetric: 'Utilization', yFile: 'BlockingProbability', yMetric: 'Blocking probability', x: 'Utilização do espectro', y: 'Probabilidade de bloqueio' },
    { id: 'scLoadFragBlock', group: 'Correlações', name: 'Cargas: fragmentação externa × bloqueio', type: 'scatter', by: 'LoadPoint', xFile: 'ExternalFragmentation', xMetric: 'External Fragmentation (Vertical)', yFile: 'BlockingProbability', yMetric: 'Blocking probability', x: 'Fragmentação externa', y: 'Probabilidade de bloqueio' },
    // ---- QKD
    { id: 'qkdBp', group: 'QKD', name: 'Bloqueio das requisições protegidas × carga', type: 'line', file: 'QKDMetrics', metric: 'QKD blocking probability', y: 'Probabilidade de bloqueio QKD', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'qkdCauses', group: 'QKD', name: 'Causas do bloqueio QKD (empilhadas)', type: 'bars', file: 'QKDMetrics', itemsFrom: 'metrics', itemsRe: /^QKD blocking probability by /i, xAxis: 'cases', stacked: true, y: 'Probabilidade de bloqueio QKD' },
    { id: 'qkdQtrx', group: 'QKD', name: 'Pares Q-TRx ativos × carga', type: 'line', file: 'QKDMetrics', metric: 'Mean active Q-TRx pairs', y: 'Pares Q-TRx ativos (média)', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'qkdSkr', group: 'QKD', name: 'SKR média na admissão × carga', type: 'line', file: 'QKDMetrics', metric: 'Mean admission SKR (Mbit/s)', y: 'SKR (Mbit/s)', x: 'Carga (Erlangs)', xMode: 'erl' },
    { id: 'qkdQtnd', group: 'QKD', name: 'Métrica × QTND (casos de uma varredura)', type: 'line', file: 'QKDMetrics', metric: 'QKD blocking probability', y: 'Probabilidade de bloqueio QKD', x: 'QTND (km)', xMode: 'param', paramPath: 'network:qkd.qtndKm' },
    { id: 'qkdEquip', group: 'QKD', name: 'QTx/QRx em uso por nó (pico)', type: 'bars', file: 'QKDMetrics', metric: 'Peak QTx in use per node', itemsFrom: 'dim', dim: 'src', xAxis: 'items', y: 'QTx em uso (pico)', x: 'Nó', rotate: -60 },
    // ---- Outros
    { id: 'custom', group: 'Outros', name: 'Personalizado (qualquer CSV)', type: 'line', x: 'Carga (Erlangs)', xMode: 'erl' },
  ];

  /** The models as <optgroup>s of <option>s (Parte 27). */
  function templateOptions() {
    return TEMPLATE_GROUPS.map((g) => el('optgroup', { label: g }, TEMPLATES.filter((t) => t.group === g).map((t) => el('option', { value: t.id }, t.name))));
  }

  function newSpec(templateId, cases) {
    const t = TEMPLATES.find((x) => x.id === templateId) || TEMPLATES[0];
    const style = R().defaultStyle();
    style.yTitle = t.y || '';
    style.xTitle = t.x || '';
    if (t.rotate) style.xLabelRotate = t.rotate;
    if (t.type === 'bars') { style.grid = 'y'; style.errorBars = t.stacked ? 'none' : 'bars'; }
    if (t.type === 'cores') { style.height = 640; style.width = 900; }
    if (t.topoBlock) { style.topoLabels = 'block'; style.topoCols = 2; }
    if (t.type === 'topo') { style.width = 1200; style.title = ''; style.fontSize = 14; style.topoNodeR = 13; }
    if (t.type === 'slots' || t.type === 'slotblock') { style.width = 1400; style.title = ''; style.fontSize = 12; }
    if (t.type === 'slotblock') { style.slotScale = 'blues'; style.slotZero = 'gray'; style.slotRange = t.blockMode === 'count' ? 'auto' : 'fixed'; }
    if (t.type === 'scatter') { style.errorBars = 'none'; style.grid = 'both'; style.xAsCategories = false; }
    if (t.type === 'slotline') { style.width = 1000; style.errorBars = 'none'; style.xAsCategories = false; style.lineWidth = 1.5; }
    if (t.layout === 'panels') { style.panelHeight = 300; style.barGap = 0.3; style.legend = 'none'; style.fontSize = 10; style.errorBars = 'none'; style.titleSize = 13; }
    return {
      version: 1, template: t.id, type: t.type,
      cases: (cases || []).map((p) => ({ path: p, label: App.caseNameOf(p) })),
      file: t.file || '', metric: t.metric || '', metrics: (t.metrics || []).slice(), filters: {},
      line: { seriesBy: t.seriesBy || 'case', xMode: t.xMode || 'lp', paramPath: t.paramPath || '', paramLp: -1 },
      bars: {
        lp: -1, itemsFrom: t.itemsFrom || 'metrics', itemsRe: t.itemsRe ? t.itemsRe.source : '', dim: t.dim || '', items: [],
        xAxis: t.xAxis || 'cases', seriesDim: t.seriesDim || '', stacked: !!t.stacked, normalize: !!t.normalize, layout: t.layout || 'grouped', sort: t.sort || 'none', top: 0, hideZero: true,
      },
      cores: { lp: -1, utilFile: 'SpectrumUtilization', utilMetric: 'Utilization per Core', blockFile: 'BlockingProbability', blockPrefix: 'Blocking probability per core', normBlock: false },
      topo: { lps: [], range: 'shared', block: !!t.topoBlock, blockFile: 'BlockingDetails', blockMetric: 'Link blocking probability', blockClasses: 'split' },
      slots: { lp: -1, links: [], cores: [], from: 1, to: 0, order: 'name', caseLayout: 'stack', blockMode: t.blockMode || 'ratio', blockCause: t.blockCause || 'all', onlyBlocked: false, summary: true },
      scatter: { xFile: t.xFile || 'SpectrumUtilization', xMetric: t.xMetric || 'Utilization Per Link', yFile: t.yFile || 'BlockingDetails', yMetric: t.yMetric || 'Link blocking probability',
        by: t.by || 'Link', lp: -1, fx: {}, fy: {}, trend: true, pooled: false },
      labels: {}, seriesStyle: {}, style,
    };
  }

  // ================================================================ state

  const S = {
    spec: null, model: null, svg: null, table: null, saved: [], savedName: '', dirty: false,
    caseFilter: '', seriesKeys: '', busy: false, error: null, fileOptions: [], metricOptions: [], dimOptions: {},
  };

  /** 'page' = botão Gráficos; 'case' = aba Resultados de um caso (um rascunho por caso). */
  S.mode = 'page'; S.casePath = null;
  const draftKey = () => (S.mode === 'case' ? 'snets.figure.case.' + S.casePath : 'snets.figure.draft');

  function persistDraft() {
    try { localStorage.setItem(draftKey(), JSON.stringify({ spec: S.spec, savedName: S.savedName })); } catch (e) { /* ignore */ }
  }
  function loadDraft() {
    try {
      const d = JSON.parse(localStorage.getItem(draftKey()) || 'null');
      if (d && d.spec && d.spec.version === 1) { S.spec = d.spec; S.savedName = d.savedName || ''; }
    } catch (e) { /* ignore */ }
  }

  // ================================================================ model building

  function lpResolve(lp, lps) { return lp === -1 || lp === undefined || !lps.includes(lp) ? (lp === -1 || lp === undefined ? lps[lps.length - 1] : lps[0]) : lp; }

  function caseLabel(c) { return c.label || App.caseNameOf(c.path); }
  function itemLabel(spec, key) { return spec.labels[key] !== undefined && spec.labels[key] !== '' ? spec.labels[key] : autoLabel(key); }

  function styleFor(spec, key, idx, kind, item) {
    const st = spec.style;
    const pal = R().PALETTES[st.palette] || R().PALETTES.office;
    const o = spec.seriesStyle[key] || {};
    const defColor = (item && kind === 'bar' && causeColor(item)) || pal[idx % pal.length];
    const cycle = Math.floor(idx / pal.length);
    if (kind === 'scatter' || kind === 'trend' || kind === 'slotline') {
      // Parte 27: scatter points (no line), fitted lines (dashed, no marker), slot profiles (no marker)
      const base = R().MARKERS[idx % (R().MARKERS.length - 1)];
      return {
        color: o.color || pal[idx % pal.length],
        dash: o.dash || (kind === 'trend' ? 'dashed' : 'solid'),
        width: o.width !== undefined && o.width !== '' ? Number(o.width) : (kind === 'trend' ? 1.4 : st.lineWidth),
        marker: o.marker || (kind === 'scatter' ? base : 'none'),
        markerSize: o.markerSize !== undefined && o.markerSize !== '' ? Number(o.markerSize) : st.markerSize,
        hollow: o.hollow !== undefined ? !!o.hollow : false,
        visible: o.visible !== false,
        line: o.line !== undefined ? o.line !== false : kind !== 'scatter',
      };
    }
    return {
      color: o.color || defColor,
      dash: o.dash || (kind === 'line' ? ['solid', 'dashed', 'dotted', 'dashdot'][cycle % 4] : 'solid'),
      width: o.width !== undefined && o.width !== '' ? Number(o.width) : st.lineWidth,
      marker: o.marker || (kind === 'line' ? R().MARKERS[idx % (R().MARKERS.length - 1)] : 'none'),
      markerSize: o.markerSize !== undefined && o.markerSize !== '' ? Number(o.markerSize) : st.markerSize,
      hollow: o.hollow !== undefined ? !!o.hollow : false,
      visible: o.visible !== false,
      line: o.line !== false,
    };
  }

  function flatten(obj, prefix, out) {
    if (obj === null || obj === undefined) return out;
    if (typeof obj !== 'object') { out[prefix] = obj; return out; }
    if (Array.isArray(obj)) return out;
    for (const k of Object.keys(obj)) flatten(obj[k], prefix ? prefix + '.' + k : k, out);
    return out;
  }

  async function differingParams(spec) {
    if (spec.cases.length < 2) return [];
    const cds = await Promise.all(spec.cases.map((c) => caseData(c.path).catch(() => null)));
    const out = [];
    for (const f of ['simulation', 'network', 'physicalLayer', 'qkdPhysicalLayer', 'others', 'traffic']) {
      const flats = cds.map((cd) => flatten(cd && cd.files[f], '', {}));
      const keys = new Set(flats.flatMap((x) => Object.keys(x)));
      for (const k of keys) {
        const vals = flats.map((x) => x[k]);
        if (vals.some((v) => v === undefined)) continue;
        const nums = vals.map((v) => (typeof v === 'number' ? v : App.parseNumber(String(v))));
        if (nums.some((v) => !Number.isFinite(v))) continue;
        if (new Set(nums).size > 1) out.push(f + ':' + k);
      }
    }
    return out;
  }

  async function buildModel(spec) {
    if (!spec.cases.length) return { empty: 'Selecione um ou mais casos com resultados na seção 1.' };
    if (spec.type === 'cores') return buildCores(spec);
    if (spec.type === 'topo') return buildTopo(spec);
    if (spec.type === 'slots') return buildSlots(spec);
    if (spec.type === 'slotblock') return buildSlotBlock(spec);
    if (spec.type === 'slotline') return buildSlotLine(spec);
    if (spec.type === 'scatter') return buildScatter(spec);
    if (!spec.file) return { empty: 'Escolha o arquivo de resultados.' };
    const parsed = [];
    for (const c of spec.cases) {
      let p = null;
      try { p = await getCsv(c.path, spec.file); } catch (e) { p = null; }
      parsed.push({ c, p: p && p.ok ? p : null });
    }
    const avail = parsed.filter((x) => x.p);
    if (!avail.length) return { empty: 'Nenhum dos casos selecionados tem o arquivo ' + spec.file + '.csv.' + fileHint(spec.file) };
    const missing = parsed.filter((x) => !x.p).map((x) => caseLabel(x.c));
    const m = spec.type === 'line' ? await buildLine(spec, avail) : buildBars(spec, avail);
    m.missing = missing;
    return m;
  }

  async function buildLine(spec, avail) {
    const L = spec.line;
    const metric = spec.metric;
    const multiCase = avail.length > 1;
    const series = [];
    let idx = 0;
    const add = (key, name, points, item) => { series.push({ key, name, points, style: styleFor(spec, key, idx++, 'line', item) }); };
    const metrics = L.seriesBy === 'metric' ? (spec.metrics.length ? spec.metrics : [metric]) : [metric];
    const dimName = L.seriesBy.startsWith('dim:') ? L.seriesBy.slice(4) : '';

    if (L.xMode === 'param') {
      if (!L.paramPath) return { kind: 'line', series: [], empty: 'Escolha o parâmetro do eixo x (os casos precisam diferir em algum parâmetro de configuração).' };
      const groups = new Map();
      for (const { c, p } of avail) {
        const cd = await caseData(c.path);
        const i = L.paramPath.indexOf(':');
        const xv = flatten(cd.files[L.paramPath.slice(0, i)], '', {})[L.paramPath.slice(i + 1)];
        const x = typeof xv === 'number' ? xv : App.parseNumber(String(xv));
        if (!Number.isFinite(x)) continue;
        const lp = lpResolve(L.paramLp, p.lps);
        const subkeys = dimName ? dimValues(p, metric)[p.dims.indexOf(dimName)] || [] : metrics;
        for (const sk of subkeys) {
          const reps = dimName ? repsAt(p, metric, lp, spec.filters, dimName, sk) : repsAt(p, sk, lp, spec.filters);
          const gk = dimName ? 'd:' + sk : 'm:' + sk;
          if (!groups.has(gk)) groups.set(gk, { item: dimName ? sk : sk, pts: new Map() });
          const g = groups.get(gk);
          if (!g.pts.has(x)) g.pts.set(x, { reps: [], labels: [] });
          g.pts.get(x).reps.push(...reps); g.pts.get(x).labels.push(caseLabel(c));
        }
      }
      for (const [gk, g] of groups) {
        const points = [...g.pts.entries()].sort((a, b) => a[0] - b[0]).map(([x, v]) => Object.assign({ x, label: v.labels.join(', ') }, stats(v.reps)));
        add('param|' + gk, itemLabel(spec, g.item), points, g.item);
      }
      return { kind: 'line', series, xNumericOnly: false };
    }

    for (const { c, p } of avail) {
      const cd = await caseData(c.path);
      const xOf = (lp) => +(L.xMode === 'erl' && cd.loads ? cd.loads.base + lp * cd.loads.inc : L.xMode === 'lp1' ? lp + 1 : lp).toPrecision(9);
      if (dimName) {
        const di = p.dims.indexOf(dimName);
        const vals = di >= 0 ? dimValues(p, metric)[di] : [];
        for (const v of vals) {
          const pts = p.lps.map((lp) => Object.assign({ x: xOf(lp), lp }, stats(repsAt(p, metric, lp, spec.filters, dimName, v)))).filter((q) => q.n);
          const key = c.path + '|' + dimName + '=' + v;
          add(key, (multiCase ? caseLabel(c) + ' · ' : '') + itemLabel(spec, v), pts, v);
        }
      } else {
        for (const mt of metrics) {
          const pts = p.lps.map((lp) => Object.assign({ x: xOf(lp), lp }, stats(repsAt(p, mt, lp, spec.filters)))).filter((q) => q.n);
          const key = metrics.length > 1 || L.seriesBy === 'metric' ? c.path + '|m:' + mt : c.path;
          const name = L.seriesBy === 'metric' ? (multiCase ? caseLabel(c) + ' · ' : '') + itemLabel(spec, mt) : caseLabel(c);
          add(key, name, pts, mt);
        }
      }
    }
    return { kind: 'line', series };
  }

  function itemsFor(spec, p) {
    const B = spec.bars;
    if (B.itemsFrom === 'dim') {
      const di = p.dims.indexOf(B.dim);
      if (di < 0) return [];
      const vals = dimValues(p, spec.metric)[di] || [];
      const nonAll = vals.filter((v) => v !== 'all' && v !== '');
      return nonAll.length ? nonAll : vals;
    }
    if (B.items && B.items.length) return B.items.filter((m) => p.byMetric.has(m));
    if (B.itemsRe) {
      const re = new RegExp(B.itemsRe, 'i');
      return p.metrics.filter((m) => re.test(m));
    }
    return spec.metric ? [spec.metric] : p.metrics.slice(0, 1);
  }

  function buildBars(spec, avail) {
    const B = spec.bars;
    // union of items keeping first-seen order
    const items = [];
    for (const { p } of avail) for (const it of itemsFor(spec, p)) if (!items.includes(it)) items.push(it);
    let selItems = items;
    if (B.itemsFrom === 'dim' && B.items && B.items.length) selItems = items.filter((i) => B.items.includes(i));
    if (B.itemsFrom === 'dim' && B.seriesDim && B.seriesDim !== B.dim) return buildMatrixBars(spec, avail, selItems, avail.map(({ p }) => lpResolve(B.lp, p.lps)));
    const valueOf = (p, item) => {
      const lp = lpResolve(B.lp, p.lps);
      const reps = B.itemsFrom === 'dim' ? repsAt(p, spec.metric, lp, spec.filters, B.dim, item) : repsAt(p, item, lp, spec.filters);
      return stats(reps);
    };
    const causeIdx = (it) => (/^QKD /i.test(it) ? -1 : CAUSES.findIndex(([re]) => re.test(it)));
    if (B.itemsFrom === 'metrics' && selItems.length && selItems.every((it) => causeIdx(it) >= 0) && !(B.items && B.items.length)) {
      const order = [1, 0, 2, 4, 3, 5, 6, 7]; // Fragmentação, QoTN, QoTO, XT, XTO, Tx, Rx, Outros (as in the articles)
      const rank = (it) => order.indexOf(causeIdx(it));
      selItems = selItems.slice().sort((a, b) => rank(a) - rank(b));
    }
    if (B.hideZero !== false) {
      const nz = selItems.filter((it) => avail.some(({ p }) => { const v = valueOf(p, it); return Number.isFinite(v.mean) && v.mean !== 0; }));
      if (nz.length) selItems = nz;
    }
    const lpUsed = avail.map(({ p }) => lpResolve(B.lp, p.lps));
    const sortCats = (cats, ser) => {
      if (B.sort === 'none' || !ser.length) return cats;
      const ref = ser[0];
      const v = (c) => { const x = ref.values[c.key]; return x && Number.isFinite(x.mean) ? x.mean : -Infinity; };
      const out = cats.slice().sort((a, b) => v(a) - v(b));
      return B.sort === 'desc' ? out.reverse() : out;
    };
    const topN = (cats) => (B.top > 0 ? cats.slice(0, B.top) : cats);

    if (B.xAxis === 'items' && B.layout === 'panels') {
      const panels = avail.map(({ c, p }, i) => {
        const key = c.path;
        const ser = [{ key, name: caseLabel(c), values: {}, style: styleFor(spec, key, i, 'bar') }];
        const own = itemsFor(spec, p).filter((it) => selItems.includes(it));
        for (const it of own) ser[0].values[it] = valueOf(p, it);
        let cats = own.map((it) => ({ key: it, label: itemLabel(spec, it) }));
        cats = topN(sortCats(cats, ser));
        return { title: caseLabel(c), model: { kind: 'bars', categories: cats, series: ser, stacked: false } };
      });
      return { kind: 'panels', panels, lpUsed, items: selItems };
    }
    let categories, series;
    if (B.xAxis === 'items') {
      categories = selItems.map((it) => ({ key: it, label: itemLabel(spec, it) }));
      series = avail.map(({ c, p }, i) => {
        const values = {};
        for (const it of selItems) values[it] = valueOf(p, it);
        return { key: c.path, name: caseLabel(c), values, style: styleFor(spec, c.path, i, 'bar') };
      });
      categories = topN(sortCats(categories, series));
    } else {
      categories = avail.map(({ c }) => ({ key: c.path, label: caseLabel(c) }));
      series = selItems.map((it, i) => {
        const values = {};
        for (const { c, p } of avail) values[c.path] = valueOf(p, it);
        const key = 'i:' + it;
        return { key, name: itemLabel(spec, it), values, style: styleFor(spec, key, i, 'bar', it) };
      });
    }
    return { kind: 'bars', categories, series, stacked: B.stacked, normalize: B.stacked && B.normalize, lpUsed, items: selItems };
  }

  /**
   * Bars over the values of one dimension (x axis) with the series (colours) from a
   * second dimension, e.g. x = Core, colours = Cause. One panel per case when
   * several cases are selected.
   */
  function buildMatrixBars(spec, avail, selItems, lpUsed) {
    const B = spec.bars;
    const sd = B.seriesDim;
    const sVals = [];
    for (const { p } of avail) {
      const di = p.dims.indexOf(sd);
      if (di < 0) continue;
      for (const v of dimValues(p, spec.metric)[di] || []) if (v !== 'all' && v !== '' && !sVals.includes(v)) sVals.push(v);
    }
    const value = (p, item, sv) => {
      const f = Object.assign({}, spec.filters, { [sd]: sv });
      return stats(repsAt(p, spec.metric, lpResolve(B.lp, p.lps), f, B.dim, item));
    };
    if (selItems.length && selItems.every((it) => /^\d+$/.test(it))) selItems = selItems.slice().sort((x, y) => Number(x) - Number(y));
    const catLabel = (it) => (B.dim === 'Core' && /^\d+$/.test(it) ? 'Núcleo ' + it : itemLabel(spec, it));
    const one = (p) => {
      let used = sVals;
      if (B.hideZero !== false) {
        const nz = sVals.filter((sv) => avail.some(({ p: q }) => selItems.some((it) => { const v = value(q, it, sv); return Number.isFinite(v.mean) && v.mean !== 0; })));
        if (nz.length) used = nz;
      }
      const series = used.map((sv, i) => {
        const values = {};
        for (const it of selItems) values[it] = value(p, it, sv);
        const key = 'sd:' + sv;
        return { key, name: itemLabel(spec, sv), values, style: styleFor(spec, key, i, 'bar', sv) };
      });
      const categories = selItems.map((it) => ({ key: it, label: catLabel(it) }));
      return { kind: 'bars', categories, series, stacked: B.stacked, normalize: B.stacked && B.normalize };
    };
    if (avail.length === 1) return Object.assign(one(avail[0].p), { lpUsed, items: selItems });
    const panels = avail.map(({ c, p }) => ({ title: caseLabel(c), model: one(p) }));
    return { kind: 'panels', panels, lpUsed, items: selItems };
  }

  function coreLayout(cores) { return App.presets.coreLayout(cores); }

  // ---------------------------------------------------------------- link heat map on the topology

  function lpLabel(cd, lp) {
    if (cd && cd.loads) return 'Carga ' + App.fmtNum(cd.loads.base + lp * cd.loads.inc, 0) + ' Erlangs';
    return 'Ponto de carga ' + (lp + 1);
  }

  async function buildTopo(spec) {
    if (!spec.file || !spec.metric) return { empty: 'Escolha o arquivo e a métrica por enlace.' };
    const T = spec.topo;
    const panels = [];
    const missing = [];
    const multi = spec.cases.length > 1;
    const blockMissing = [];
    let blockQkd = false;
    let min = Infinity, max = -Infinity;
    for (const c of spec.cases) {
      const cd = await caseData(c.path);
      let p = null;
      try { p = await getCsv(c.path, spec.file); } catch (e) { p = null; }
      const li = p && p.ok ? dimIndex(p, 'Link') : -1;
      const linkDim = li >= 0 ? p.dims[li] : 'Link';
      if (!(p && p.ok) || !p.byMetric.has(spec.metric) || li < 0) { missing.push(caseLabel(c)); continue; }
      const linkVals = dimValues(p, spec.metric)[li].filter((v) => /^<[^<>]+-[^<>]+>$/.test(v));
      let net = cd.files.network;
      if (!net || !Array.isArray(net.nodes) || !net.nodes.length) {
        // no network file: rebuild nodes/links from the link names of the CSV
        const names = new Set();
        const links = linkVals.map((v) => { const m = /^<([^<>]+?)-([^<>]+)>$/.exec(v); names.add(m[1]); names.add(m[2]); return { source: m[1], destination: m[2], size: NaN }; });
        net = { nodes: [...names].map((n) => ({ name: n })), links };
      }
      const base = App.presets.topoPanel(net);
      const lps = T.lps && T.lps.length ? p.lps.filter((l) => T.lps.includes(l)) : p.lps;
      let bp = null;
      if (T.block) {
        try { bp = await getCsv(c.path, T.blockFile || 'BlockingDetails'); } catch (e) { bp = null; }
        if (!(bp && bp.ok && bp.byMetric.has(T.blockMetric || 'Link blocking probability'))) { bp = null; blockMissing.push(caseLabel(c)); }
      }
      const bMetric = T.blockMetric || 'Link blocking probability';
      const bClasses = bp ? (dimValues(bp, bMetric)[bp.dims.indexOf('Class')] || ['all']) : [];
      if (bClasses.includes('qkd')) blockQkd = true;
      const blockAt = (lp, from, to) => {
        if (!bp) return null;
        const o = {};
        for (const cl of bClasses) o[cl] = stats(repsAt(bp, bMetric, lpResolve(lp, bp.lps), { Class: cl }, 'Link', '<' + from + '-' + to + '>'));
        return o;
      };
      for (const lp of lps) {
        const val = {};
        for (const lv of linkVals) {
          const m = /^<([^<>]+?)-([^<>]+)>$/.exec(lv);
          val[m[1] + '>' + m[2]] = stats(repsAt(p, spec.metric, lp, spec.filters, linkDim, lv));
        }
        const links = base.links.map((l) => {
          const ab = val[l.a + '>' + l.b], ba = val[l.b + '>' + l.a];
          return Object.assign({}, l, { ab: ab ? ab.mean : NaN, ba: ba ? ba.mean : NaN, abS: ab || null, baS: ba || null,
            blkAB: blockAt(lp, l.a, l.b), blkBA: blockAt(lp, l.b, l.a) });
        });
        for (const l of links) for (const v of [l.ab, l.ba]) if (Number.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
        const note = bp ? (bClasses.includes('qkd') && T.blockClasses !== 'all' ? ' — cor: utilização · C: bloqueio clássico · Q: bloqueio QKD' : ' — cor: utilização · B: bloqueio') : '';
        panels.push({ title: (multi ? caseLabel(c) + ' · ' : '') + lpLabel(cd, lp) + note, nodes: base.nodes, links, path: c.path, lp, qtnd: base.qtnd });
      }
    }
    if (!panels.length) return { empty: 'Nenhum caso selecionado tem "' + spec.metric + '" por enlace em ' + spec.file + '.csv.' };
    const st = spec.style;
    if (st.topoMin !== '' && Number.isFinite(Number(st.topoMin))) min = Number(st.topoMin);
    if (st.topoMax !== '' && Number.isFinite(Number(st.topoMax))) max = Number(st.topoMax);
    if (!Number.isFinite(min)) { min = 0; max = 1; }
    if (max <= min) max = min + (min === 0 ? 1 : Math.abs(min) * 0.1);
    const legendTitle = itemLabel(spec, '__heat') === '__heat' ? App.metricInfo.axisTitle(spec.metric) || spec.metric : itemLabel(spec, '__heat');
    const block = T.block ? { qkd: blockQkd && T.blockClasses !== 'all', missing: blockMissing } : null;
    if (block && blockMissing.length) missing.push(...blockMissing.map((x) => x + ' (sem BlockingDetails.csv: rode de novo com o simulador da Parte 25)'));
    return { kind: 'topo', panels, missing, range: { min, max }, legendTitle, block };
  }

  async function buildSlots(spec) {
    const T = spec.slots || (spec.slots = newSpec('slotHeat', []).slots);
    const panels = [];
    const missing = [];
    let nSlotsAll = 0;
    for (const c of spec.cases) {
      const cd = await caseData(c.path);
      let p = null;
      try { p = await getCsv(c.path, 'SlotUtilization'); } catch (e) { p = null; }
      if (!(p && p.ok && p.slot)) { missing.push(caseLabel(c) + ' (sem SlotUtilization.csv: rode de novo com o simulador da Parte 27)'); continue; }
      const lp = lpResolve(T.lp, p.lps);
      let links = p.links.slice();
      if (T.order === 'name') links.sort(linkCompare);
      if (T.links && T.links.length) links = links.filter((l) => T.links.includes(l));
      let cores = p.cores.slice();
      if (T.cores && T.cores.length) cores = cores.filter((k) => T.cores.includes(k));
      const from = Math.max(1, Math.min(p.nSlots, Math.round(Number(T.from) || 1)));
      const to = Number(T.to) > 0 ? Math.max(from, Math.min(p.nSlots, Math.round(Number(T.to)))) : p.nSlots;
      const rows = [];
      for (const link of links) for (const core of cores) {
        const v = p.means.get(lp + '|' + link + '|' + core);
        if (v) rows.push({ link, core, values: v.subarray(from - 1, to) });
      }
      nSlotsAll = Math.max(nSlotsAll, to - from + 1);
      panels.push({ title: (spec.cases.length > 1 ? caseLabel(c) + ' · ' : '') + lpLabel(cd, lp) + (p.nReps > 1 ? ' · média de ' + p.nReps + ' replicações' : ''), rows, from, to, path: c.path, lp });
    }
    if (!panels.length) return { empty: 'Nenhum caso selecionado tem o arquivo SlotUtilization.csv. Ele é gravado pelo simulador da Parte 27 (métrica SlotUtilization, ligada por padrão, junto com SpectrumUtilization).', missing };
    if (!panels.some((x) => x.rows.length)) return { empty: 'Nenhum enlace/núcleo selecionado.', missing };
    const st = spec.style;
    let min = 0, max = 1;
    if (st.slotRange === 'auto') {
      min = Infinity; max = -Infinity;
      for (const pn of panels) for (const r of pn.rows) for (const v of r.values) { if (v < min) min = v; if (v > max) max = v; }
      if (!Number.isFinite(min)) { min = 0; max = 1; }
    }
    if (st.slotMin !== '' && st.slotMin !== undefined && Number.isFinite(Number(st.slotMin))) min = Number(st.slotMin);
    if (st.slotMax !== '' && st.slotMax !== undefined && Number.isFinite(Number(st.slotMax))) max = Number(st.slotMax);
    if (max <= min) max = min + 1e-9;
    const legendTitle = itemLabel(spec, '__slot') === '__slot' ? 'Ocupação do slot' : itemLabel(spec, '__slot');
    return { kind: 'slots', panels, missing, range: { min, max }, legendTitle, nSlots: nSlotsAll };
  }

  /** Colours of the blocking causes in the "causes" mode (Parte 29). */
  const SLOT_NOBLOCK_COLOR = '#D9D9D9';
  function slotCauseColor(cause, i) {
    return causeColor(cause) || ['#C00000', '#7030A0', '#00B050', '#0070C0', '#ED7D31', '#FFC000', '#5B9BD5', '#A5A5A5'][i % 8];
  }

  /** Spectrum blocking heat map: blocked ÷ attempts, counts or the cause of each slot (Parte 29). */
  async function buildSlotBlock(spec) {
    const T = spec.slots || (spec.slots = newSpec('slotBlock', []).slots);
    const st = spec.style;
    const mode = T.blockMode || 'ratio';
    const want = T.blockCause || 'all';
    const panels = [];
    const missing = [];
    const allCauses = [];
    let nSlotsAll = 0;
    const pct = (a, b) => b > 0 ? App.fmtNum(100 * a / b, 1) + ' %' : '–';
    for (const c of spec.cases) {
      const cd = await caseData(c.path);
      let p = null;
      try { p = await getCsv(c.path, 'SlotBlocking'); } catch (e) { p = null; }
      if (!(p && p.ok && p.slotblock)) { missing.push(caseLabel(c) + ' (sem SlotBlocking.csv: rode de novo com o simulador da Parte 29)'); continue; }
      for (const k of p.causes) if (!allCauses.includes(k)) allCauses.push(k);
      const lp = lpResolve(T.lp, p.lps);
      let links = p.links.slice();
      if (T.order === 'name') links.sort(linkCompare);
      if (T.links && T.links.length) links = links.filter((l) => T.links.includes(l));
      let cores = p.cores.slice();
      if (T.cores && T.cores.length) cores = cores.filter((k) => T.cores.includes(k));
      const from = Math.max(1, Math.min(p.nSlots, Math.round(Number(T.from) || 1)));
      const to = Number(T.to) > 0 ? Math.max(from, Math.min(p.nSlots, Math.round(Number(T.to)))) : p.nSlots;
      const n = to - from + 1;
      const causes = want === 'all' ? p.causes : p.causes.filter((k) => k === want);
      const rows = [];
      let totReq = 0, totBlk = 0;
      for (const link of links) for (const core of cores) {
        const key = lp + '|' + link + '|' + core;
        const att = p.att.get(key);
        if (!att) continue;
        const blkArr = want === 'all' ? p.blkAll.get(key) : (p.blk.get(want) || new Map()).get(key);
        const reqA = p.reqAtt.get(key) || 0;
        const reqB = want === 'all' ? (p.reqBlkAll.get(key) || 0) : ((p.reqBlk.get(want) || new Map()).get(key) || 0);
        totReq += reqA; totBlk += reqB;
        if (T.onlyBlocked && !reqB) continue;
        const values = new Float64Array(n);
        const byCause = causes.map((k) => (p.blk.get(k) || new Map()).get(key) || null);
        for (let i = 0; i < n; i++) {
          const a = att[from - 1 + i], b = blkArr ? blkArr[from - 1 + i] : 0;
          values[i] = mode === 'count' ? b : (a > 0 ? b / a : NaN);
        }
        const row = { link, core, values, summary: [App.fmtNum(reqB, 0) + '/' + App.fmtNum(reqA, 0), pct(reqB, reqA)] };
        if (mode === 'causes') {
          row.colors = new Array(n);
          for (let i = 0; i < n; i++) {
            const s = from - 1 + i;
            if (!(att[s] > 0)) { row.colors[i] = null; continue; }
            let best = -1, bv = 0;
            byCause.forEach((arr, j) => { if (arr && arr[s] > bv) { bv = arr[s]; best = j; } });
            row.colors[i] = best < 0 ? SLOT_NOBLOCK_COLOR : slotCauseColor(causes[best], allCauses.indexOf(causes[best]));
          }
        }
        row.tip = (k, e) => {
          let a = 0, b = 0;
          const per = causes.map(() => 0);
          for (let i = k; i < e; i++) {
            const s = from - 1 + i;
            a += att[s]; b += blkArr ? blkArr[s] : 0;
            byCause.forEach((arr, j) => { if (arr) per[j] += arr[s]; });
          }
          const sl = e - k > 1 ? 'slots ' + (from + k) + '–' + (from + e - 1) : 'slot ' + (from + k);
          let t = 'Enlace ' + link + ' · núcleo ' + core + ' · ' + sl + ': ';
          if (!(a > 0)) return t + 'nenhuma tentativa';
          t += App.fmtNum(b, 0) + ' bloqueios em ' + App.fmtNum(a, 0) + ' tentativas (' + pct(b, a) + ')';
          const parts = causes.map((kk, j) => [kk, per[j]]).filter((x) => x[1] > 0).sort((x, y) => y[1] - x[1]);
          if (parts.length && (want === 'all' || mode === 'causes')) t += '\n' + parts.map(([kk, v]) => '  ' + autoLabel(kk) + ': ' + App.fmtNum(v, 0)).join('\n');
          return t;
        };
        rows.push(row);
      }
      nSlotsAll = Math.max(nSlotsAll, n);
      const nb = p.noBand.get(lp) || 0;
      const sub = [lpLabel(cd, lp), p.nReps > 1 ? 'soma de ' + p.nReps + ' replicações' : '', want !== 'all' ? autoLabel(want) : '',
        nb > 0 && want === 'all' ? nb + ' bloqueio' + (nb > 1 ? 's' : '') + ' sem faixa no espectro (fora do mapa)' : ''].filter(Boolean);
      panels.push({ title: (spec.cases.length > 1 ? caseLabel(c) + ' · ' : '') + sub.join(' · '), rows, from, to, path: c.path, lp, noBand: nb, totReq, totBlk });
    }
    S.slotCauses = allCauses;
    if (!panels.length) return { empty: 'Nenhum caso selecionado tem o arquivo SlotBlocking.csv.' + fileHint('SlotBlocking'), missing };
    if (!panels.some((x) => x.rows.length)) return { empty: T.onlyBlocked ? 'Nenhum núcleo de enlace selecionado teve bloqueio' + (want !== 'all' ? ' por ' + autoLabel(want) : '') + ' neste ponto de carga.' : 'Nenhum enlace/núcleo selecionado teve tentativas.', missing };
    let min = 0, max = 1;
    if (mode === 'count' || st.slotRange === 'auto') {
      min = mode === 'count' ? 0 : Infinity; max = -Infinity;
      for (const pn of panels) for (const r of pn.rows) for (const v of r.values) { if (!Number.isFinite(v)) continue; if (v < min) min = v; if (v > max) max = v; }
      if (!Number.isFinite(min) || !Number.isFinite(max)) { min = 0; max = 1; }
    }
    if (st.slotMin !== '' && st.slotMin !== undefined && Number.isFinite(Number(st.slotMin))) min = Number(st.slotMin);
    if (st.slotMax !== '' && st.slotMax !== undefined && Number.isFinite(Number(st.slotMax))) max = Number(st.slotMax);
    if (max <= min) max = min + 1e-9;
    const model = { kind: 'slots', block: mode, panels, missing, range: { min, max }, nSlots: nSlotsAll, noDataColor: st.slotNoData || '#FFFFFF',
      valueFormat: mode === 'count' ? 'count' : 'percent' };
    if (T.summary !== false) model.summaryHeads = [spec.labels.__sumA || 'Bloq./req.', spec.labels.__sumB || '%'];
    if (mode === 'causes') {
      const used = new Set();
      for (const pn of panels) for (const r of pn.rows) for (const c of r.colors) if (c) used.add(c);
      model.legendItems = allCauses.filter((k) => want === 'all' || k === want).map((k) => [slotCauseColor(k, allCauses.indexOf(k)), autoLabel(k)]).filter(([c]) => used.has(c));
      model.legendItems.push([SLOT_NOBLOCK_COLOR, 'Tentado, sem bloqueio' + (want !== 'all' ? ' por esta causa' : '')], [model.noDataColor, 'Nenhuma tentativa']);
    }
    model.legendTitle = slotBlockLegendTitle(spec, mode);
    return model;
  }

  function slotBlockLegendTitle(spec, mode) {
    if (spec.labels.__slot) return spec.labels.__slot;
    return mode === 'causes' ? 'Causa do bloqueio' : mode === 'count' ? 'Bloqueios no slot' : 'Bloqueadas ÷ tentativas';
  }

  /** Mean occupancy of each slot over the chosen links, one line per core (Parte 27). */
  async function buildSlotLine(spec) {
    const T = spec.slots || (spec.slots = newSpec('slotProfile', []).slots);
    const series = [];
    const missing = [];
    let idx = 0;
    for (const c of spec.cases) {
      let p = null;
      try { p = await getCsv(c.path, 'SlotUtilization'); } catch (e) { p = null; }
      if (!(p && p.ok && p.slot)) { missing.push(caseLabel(c) + ' (sem SlotUtilization.csv)'); continue; }
      const lp = lpResolve(T.lp, p.lps);
      let links = p.links.slice();
      if (T.links && T.links.length) links = links.filter((l) => T.links.includes(l));
      let cores = p.cores.slice();
      if (T.cores && T.cores.length) cores = cores.filter((k) => T.cores.includes(k));
      const from = Math.max(1, Math.min(p.nSlots, Math.round(Number(T.from) || 1)));
      const to = Number(T.to) > 0 ? Math.max(from, Math.min(p.nSlots, Math.round(Number(T.to)))) : p.nSlots;
      for (const core of cores) {
        const acc = new Float64Array(to - from + 1);
        let n = 0;
        for (const link of links) {
          const v = p.means.get(lp + '|' + link + '|' + core);
          if (!v) continue;
          for (let k = from - 1; k < to; k++) acc[k - from + 1] += v[k];
          n++;
        }
        if (!n) continue;
        const points = Array.from(acc, (v, k) => ({ x: from + k, mean: v / n, ci: NaN, n: p.nReps, label: 'slot ' + (from + k) }));
        const key = c.path + '|core=' + core;
        series.push({ key, name: (spec.cases.length > 1 ? caseLabel(c) + ' · ' : '') + 'Núcleo ' + core, points, style: styleFor(spec, key, idx++, 'slotline') });
      }
    }
    if (!series.length) return { empty: 'Nenhum caso selecionado tem SlotUtilization.csv com os enlaces/núcleos escolhidos.' + fileHint('SlotUtilization'), missing };
    return { kind: 'line', series, missing, xNumericOnly: true, styleKind: 'slotline' };
  }

  /** Filters of the other dimensions of a metric: the user's choice, else "all", else the first value. */
  function autoFilters(p, metric, freeDim, user) {
    const f = {};
    const vals = dimValues(p, metric);
    p.dims.forEach((d, i) => {
      if (freeDim && d.toLowerCase() === String(freeDim).toLowerCase()) return;
      const vs = vals[i] || [];
      if (user && user[d] !== undefined && vs.includes(user[d])) f[d] = user[d];
      else f[d] = vs.includes('all') ? 'all' : vs[0];
    });
    return f;
  }

  /** Pearson correlation and least-squares line of the points. */
  function fitLine(pts) {
    const n = pts.length;
    if (n < 3) return null;
    let sx = 0, sy = 0;
    for (const q of pts) { sx += q.x; sy += q.mean; }
    const mx = sx / n, my = sy / n;
    let sxx = 0, syy = 0, sxy = 0;
    for (const q of pts) { const dx = q.x - mx, dy = q.mean - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
    if (!(sxx > 0)) return null;
    const b = sxy / sxx, a = my - b * mx;
    const r = syy > 0 ? sxy / Math.sqrt(sxx * syy) : NaN;
    return { a, b, r, n };
  }

  /** x × y of two metrics, one point per link, core or load point (Parte 27). */
  async function buildScatter(spec) {
    const X = spec.scatter;
    if (!X.xFile || !X.xMetric || !X.yFile || !X.yMetric) return { empty: 'Escolha a métrica do eixo x e a do eixo y.' };
    const series = [];
    const missing = [];
    const all = [];
    let idx = 0;
    const by = X.by || 'Link';
    for (const c of spec.cases) {
      const cd = await caseData(c.path);
      let px = null, py = null;
      try { px = await getCsv(c.path, X.xFile); } catch (e) { px = null; }
      try { py = await getCsv(c.path, X.yFile); } catch (e) { py = null; }
      if (!(px && px.ok && px.byMetric.has(X.xMetric))) { missing.push(caseLabel(c) + ' (sem "' + X.xMetric + '" em ' + X.xFile + '.csv)'); continue; }
      if (!(py && py.ok && py.byMetric.has(X.yMetric))) { missing.push(caseLabel(c) + ' (sem "' + X.yMetric + '" em ' + X.yFile + '.csv)'); continue; }
      const pts = [];
      if (by === 'LoadPoint') {
        const fx = autoFilters(px, X.xMetric, null, X.fx), fy = autoFilters(py, X.yMetric, null, X.fy);
        for (const lp of px.lps.filter((l) => py.lps.includes(l))) {
          const sx = stats(repsAt(px, X.xMetric, lp, fx)), sy = stats(repsAt(py, X.yMetric, lp, fy));
          if (!Number.isFinite(sx.mean) || !Number.isFinite(sy.mean)) continue;
          const erl = cd.loads ? cd.loads.base + lp * cd.loads.inc : null;
          pts.push(Object.assign({}, sy, { x: sx.mean, label: (erl !== null ? App.fmtNum(erl, 0) + ' E' : 'ponto ' + (lp + 1)) + ' · x = ' + R().fmtNumber(sx.mean, 'auto', 4, spec.style.decimal) }));
        }
      } else {
        const ix = dimIndex(px, by), iy = dimIndex(py, by);
        if (ix < 0 || iy < 0) { missing.push(caseLabel(c) + ' (as métricas não têm a dimensão ' + by + ')'); continue; }
        const dx = px.dims[ix], dy = py.dims[iy];
        const lpx = lpResolve(X.lp, px.lps), lpy = lpResolve(X.lp, py.lps);
        const fx = autoFilters(px, X.xMetric, dx, X.fx), fy = autoFilters(py, X.yMetric, dy, X.fy);
        const itemsY = new Set(dimValues(py, X.yMetric)[iy] || []);
        for (const it of (dimValues(px, X.xMetric)[ix] || []).filter((v) => v !== 'all' && v !== '' && itemsY.has(v))) {
          const sx = stats(repsAt(px, X.xMetric, lpx, fx, dx, it)), sy = stats(repsAt(py, X.yMetric, lpy, fy, dy, it));
          if (!Number.isFinite(sx.mean) || !Number.isFinite(sy.mean)) continue;
          const lab = by === 'Core' && /^\d+$/.test(it) ? 'Núcleo ' + it : 'Enlace ' + it;
          pts.push(Object.assign({}, sy, { x: sx.mean, label: lab + ' · x = ' + R().fmtNumber(sx.mean, 'auto', 4, spec.style.decimal) }));
        }
      }
      if (!pts.length) { missing.push(caseLabel(c) + ' (nenhum ponto com os dois valores)'); continue; }
      const key = c.path;
      const fit = fitLine(pts);
      const s0 = { key, name: caseLabel(c), points: pts, fit, style: styleFor(spec, key, idx, 'scatter') };
      series.push(s0);
      all.push(...pts);
      if (X.trend && !X.pooled && fit) {
        const xs = pts.map((q) => q.x), x0 = Math.min(...xs), x1 = Math.max(...xs);
        series.push({ key: 'trend|' + key, trendOf: key, name: 'Ajuste · ' + caseLabel(c), points: [{ x: x0, mean: fit.a + fit.b * x0, n: 0 }, { x: x1, mean: fit.a + fit.b * x1, n: 0 }], style: styleFor(spec, 'trend|' + key, idx, 'trend') });
      }
      idx++;
    }
    if (!series.length) return { empty: 'Nenhum caso tem as duas métricas.' + fileHint(X.xFile) + (X.yFile !== X.xFile ? fileHint(X.yFile) : ''), missing };
    let pooledFit = null;
    if (X.trend && X.pooled) {
      pooledFit = fitLine(all);
      if (pooledFit) {
        const xs = all.map((q) => q.x), x0 = Math.min(...xs), x1 = Math.max(...xs);
        series.push({ key: 'trend|all', name: 'Ajuste (todos os casos)', points: [{ x: x0, mean: pooledFit.a + pooledFit.b * x0, n: 0 }, { x: x1, mean: pooledFit.a + pooledFit.b * x1, n: 0 }], style: styleFor(spec, 'trend|all', 0, 'trend') });
      }
    }
    return { kind: 'line', scatter: true, series, missing, xNumericOnly: true, pooledFit, lpUsed: [] };
  }

  async function buildCores(spec) {
    const C = spec.cores;
    const diagrams = [];
    const missing = [];
    for (const c of spec.cases) {
      const cd = await caseData(c.path);
      const coreCfg = (cd.files.network && Array.isArray(cd.files.network.cores) && cd.files.network.cores.length)
        ? cd.files.network.cores : null;
      let up = null, bp = null;
      try { up = await getCsv(c.path, C.utilFile); } catch (e) { /* missing */ }
      try { bp = await getCsv(c.path, C.blockFile); } catch (e) { /* missing */ }
      if (!(up && up.ok) && !(bp && bp.ok)) { missing.push(caseLabel(c)); continue; }
      let cores = coreCfg;
      if (!cores) {
        const dv = up && up.ok && up.byMetric.has(C.utilMetric) ? dimValues(up, C.utilMetric)[up.dims.indexOf('Core')] || [] : [];
        cores = dv.filter((v) => /^\d+$/.test(v)).map((v) => ({ id: Number(v), adjacentCores: [] }));
      }
      const pos = coreLayout(cores);
      const util = {}, block = {};
      for (const core of cores) {
        if (up && up.ok && up.byMetric.has(C.utilMetric)) {
          const lp = lpResolve(C.lp, up.lps);
          util[core.id] = stats(repsAt(up, C.utilMetric, lp, { Link: 'all' }, 'Core', String(core.id))).mean;
        }
        if (bp && bp.ok) {
          const mname = C.blockPrefix + ' ' + core.id;
          if (bp.byMetric.has(mname)) block[core.id] = stats(repsAt(bp, mname, lpResolve(C.lp, bp.lps), spec.filters)).mean;
        }
      }
      if (C.normBlock) {
        const sum = Object.values(block).filter(Number.isFinite).reduce((a, b) => a + b, 0);
        if (sum > 0) for (const k of Object.keys(block)) block[k] /= sum;
      }
      const list = cores.map((core) => {
        const u = util[core.id], b = block[core.id];
        const unused = spec.style.coreHideUnused && (!Number.isFinite(u) || u === 0) && (!Number.isFinite(b) || b === 0);
        const p = pos.get(core.id) || { x: 0, y: 0 };
        return { id: core.id, x: p.x, y: p.y, util: u, block: b, unused };
      });
      diagrams.push({ title: caseLabel(c), cores: list, path: c.path });
    }
    if (!diagrams.length) return { empty: 'Nenhum caso tem ' + C.utilFile + '.csv nem ' + C.blockFile + '.csv.' };
    return {
      kind: 'cores', diagrams, missing,
      blockLabel: itemLabel(spec, '__block') === '__block' ? 'Bloqueio do núcleo' : itemLabel(spec, '__block'),
      utilLabel: itemLabel(spec, '__util') === '__util' ? 'Uso do núcleo' : itemLabel(spec, '__util'),
    };
  }

  // ================================================================ data table / csv

  function tableRows(model, spec) {
    const fmt = (v) => (v && Number.isFinite(v.mean) ? v : null);
    if (!model || model.empty) return null;
    if (model.kind === 'line') {
      const xs = [...new Set(model.series.flatMap((s) => s.points.map((p) => p.x)))].sort((a, b) => a - b);
      const head = [xHeader(spec)].concat(model.series.flatMap((s) => [s.name + ' (média)', s.name + ' (IC95)', s.name + ' (n)']));
      const rows = xs.map((x) => [x].concat(model.series.flatMap((s) => {
        const p = s.points.find((q) => q.x === x);
        return p ? [p.mean, Number.isFinite(p.ci) ? p.ci : '', p.n] : ['', '', ''];
      })));
      return { head, rows };
    }
    if (model.kind === 'bars' || model.kind === 'panels') {
      const ms = model.kind === 'bars' ? [{ title: '', model }] : model.panels;
      const head = ['painel', 'categoria', 'série', 'média', 'IC95', 'n'];
      const rows = [];
      for (const pn of ms) for (const c of pn.model.categories) for (const s of pn.model.series) {
        const v = fmt(s.values[c.key]);
        rows.push([pn.title, c.label, s.name, v ? v.mean : '', v && Number.isFinite(v.ci) ? v.ci : '', v ? v.n : '']);
      }
      return { head, rows };
    }
    if (model.kind === 'topo') {
      const head = ['painel', 'enlace', 'média', 'IC95', 'n'];
      const rows = [];
      for (const p of model.panels) for (const l of p.links) for (const [k, sname] of [['abS', l.a + '→' + l.b], ['baS', l.b + '→' + l.a]]) {
        const v = fmt(l[k]);
        if (v) rows.push([p.title, sname, v.mean, Number.isFinite(v.ci) ? v.ci : '', v.n]);
      }
      return { head, rows };
    }
    if (model.kind === 'slots') {
      const n = Math.max(...model.panels.map((p) => p.to - p.from + 1));
      const first = model.panels[0].from;
      const sum = model.summaryHeads ? ['requisições bloqueadas/feitas', '%'] : [];
      const head = ['painel', 'enlace', 'núcleo'].concat(sum, Array.from({ length: n }, (_, k) => 'slot ' + (first + k)));
      const rows = [];
      for (const p of model.panels) for (const r of p.rows) rows.push([p.title, r.link, Number(r.core)].concat(sum.length ? r.summary : [], Array.from(r.values, (v) => Number.isFinite(v) ? v : '')));
      return { head, rows };
    }
    if (model.kind === 'cores') {
      const head = ['caso', 'núcleo', 'uso', 'bloqueio'];
      const rows = [];
      for (const d of model.diagrams) for (const c of d.cores) rows.push([d.title, c.id, Number.isFinite(c.util) ? c.util : '', Number.isFinite(c.block) ? c.block : '']);
      return { head, rows };
    }
    return null;
  }

  function xHeader(spec) {
    const L = spec.line;
    if (L.xMode === 'erl') return 'Carga (Erlang)';
    if (L.xMode === 'lp1') return 'Ponto de carga';
    if (L.xMode === 'lp') return 'Índice do ponto';
    return L.paramPath || 'Parâmetro';
  }

  // ---------------------------------------------------------------- explanations (metric + table legend)

  function metricsShown(m, spec) {
    if (spec.type === 'cores') return [spec.cores.utilMetric, spec.cores.blockPrefix + ' N'];
    if (spec.type === 'topo') return [spec.metric];
    if (spec.type === 'slots' || spec.type === 'slotline') return ['Slot utilization'];
    if (spec.type === 'slotblock') return ['Slot blocked'];
    if (spec.type === 'scatter') return [spec.scatter.xMetric, spec.scatter.yMetric];
    if (spec.type === 'line') return spec.line.seriesBy === 'metric' ? (spec.metrics.length ? spec.metrics : [spec.metric]) : [spec.metric];
    if (spec.bars.itemsFrom === 'metrics') return m.items && m.items.length ? m.items : [];
    return [spec.metric];
  }

  function aboutPanel(m, spec) {
    const info = App.metricInfo;
    const names = metricsShown(m, spec).filter(Boolean);
    const rows = names.map((n) => {
      const d = info.describe(n);
      return el('div.about-row', el('b', n), d ? el('span.muted', ' (' + d.unit + ')') : null,
        el('div', d ? d.desc : 'Sem descrição cadastrada para esta métrica.'));
    });
    const dims = [];
    const dimNames = new Set(Object.keys(spec.filters || {}));
    if (spec.type === 'line' && spec.line.seriesBy.startsWith('dim:')) dimNames.add(spec.line.seriesBy.slice(4));
    if (spec.type === 'bars' && spec.bars.itemsFrom === 'dim' && spec.bars.dim) dimNames.add(spec.bars.dim);
    for (const d of dimNames) {
      const t = info.dimension(d);
      if (!t) continue;
      const f = spec.filters && spec.filters[d];
      dims.push(el('div.small', el('b', d), ': ', t, f !== undefined && !(spec.type === 'line' && spec.line.seriesBy === 'dim:' + d) && !(spec.type === 'bars' && spec.bars.dim === d) ? ' — filtro atual: ' + f : ''));
    }
    const file = spec.type === 'cores' ? spec.cores.utilFile + '.csv + ' + spec.cores.blockFile + '.csv' : spec.type === 'slots' || spec.type === 'slotline' ? 'SlotUtilization.csv' : spec.type === 'slotblock' ? 'SlotBlocking.csv'
      : spec.type === 'scatter' ? [...new Set([spec.scatter.xFile, spec.scatter.yFile])].map((f) => f + '.csv').join(' + ') : spec.file + '.csv';
    return el('div.metric-about',
      el('div.about-title', 'Sobre ' + (names.length > 1 ? 'as métricas' : 'a métrica'), el('span.about-file', ' · arquivo ' + file)),
      rows, dims.length ? el('div.about-dims', dims) : null);
  }

  function tableLegend(m, spec) {
    const items = [];
    const add = (t, d) => items.push(el('div', el('b', t), ': ', d));
    const stat = () => {
      add('média', 'média aritmética das replicações (colunas rep0, rep1, … do CSV) naquele ponto.');
      add('IC95', 'meia-largura do intervalo de confiança de 95 %: t(n−1)·s/√n, com s o desvio-padrão amostral das replicações. O valor verdadeiro está em média ± IC95 com 95 % de confiança. Fica vazio com menos de 2 replicações.');
      add('n', 'número de replicações com valor numérico (valores NaN são ignorados).');
    };
    if (m.kind === 'line') {
      const L = spec.line;
      add(xHeader(spec), L.xMode === 'erl' ? 'carga oferecida no ponto: soma de arrivalRate ÷ holdRate dos geradores + incremento (arrivalRateIncrease ÷ holdRate) × índice do ponto.'
        : L.xMode === 'lp1' ? 'número do ponto de carga (1 = primeiro).'
          : L.xMode === 'lp' ? 'índice do ponto de carga como aparece no CSV (0 = primeiro).'
            : 'valor deste parâmetro de configuração em cada caso; y é tomado no ponto de carga escolhido.');
      stat();
      const desc = m.series.map((s) => {
        const c = spec.cases.find((x) => x.path === s.key || s.key.startsWith(x.path + '|'));
        return s.name + (c ? ' = caso ' + c.path : '');
      });
      if (desc.length) add('séries', desc.join('; ') + '.');
    } else if (m.kind === 'bars' || m.kind === 'panels') {
      if (m.kind === 'panels') add('painel', 'caso (um gráfico por caso).');
      add('categoria', spec.bars.xAxis === 'cases' ? 'caso (posição no eixo x).' : 'item no eixo x (' + (spec.bars.itemsFrom === 'dim' ? 'valor de ' + spec.bars.dim : 'métrica') + ').');
      add('série', spec.bars.xAxis === 'cases' ? 'item representado pela cor (' + (spec.bars.itemsFrom === 'dim' ? 'valor de ' + spec.bars.dim : 'métrica') + ').' : 'caso representado pela cor.');
      stat();
      add('ponto de carga', 'valores tomados no ponto ' + ((m.lpUsed && m.lpUsed.length) ? [...new Set(m.lpUsed.map((l) => l + 1))].join('/') : '—') + (spec.bars.stacked ? '; empilhadas' + (spec.bars.normalize ? ' e normalizadas para somar 100 %' : '') : '') + '.');
    } else if (m.kind === 'topo') {
      add('painel', 'ponto de carga' + (spec.cases.length > 1 ? ' e caso' : '') + ' (um desenho da topologia por ponto de carga).');
      add('enlace', 'enlace direcional origem→destino; cada enlace da topologia aparece nos dois sentidos.');
      stat();
      add('cores do mapa', 'escala contínua verde (menor valor) → amarelo → laranja → vermelho (maior valor), de ' + App.fmtNum(m.range.min, 4) + ' a ' + App.fmtNum(m.range.max, 4) + ', comum a todos os painéis.');
    } else if (m.kind === 'slots' && m.block) {
      add('painel', 'caso e ponto de carga (um mapa por caso); soma das replicações. Bloqueios sem faixa (sem candidato) não têm lugar no espectro e são contados à parte no título.');
      add('enlace / núcleo', 'uma linha do mapa por núcleo de cada enlace direcional escolhido.');
      add('requisições bloqueadas/feitas', 'quantas requisições tiveram o candidato naquele núcleo daquele enlace (cada requisição conta uma vez por enlace da rota) e quantas delas foram bloqueadas' + (spec.slots.blockCause !== 'all' ? ' pela causa escolhida' : '') + '; ao lado, a porcentagem.');
      add('slot N', m.block === 'count' ? 'número de bloqueios cuja faixa incluía o slot N.' : m.block === 'causes' ? 'cor da causa com mais bloqueios no slot N (vazio = sem tentativas). Na tabela: bloqueadas ÷ tentativas.' : 'bloqueios ÷ tentativas cuja faixa incluía o slot N (vazio = sem tentativas). Requisições grandes ocupam mais slots, por isso a média dos slots fica acima da porcentagem por requisição.');
    } else if (m.kind === 'slots') {
      add('painel', 'caso e ponto de carga (um mapa por caso).');
      add('enlace / núcleo', 'uma linha do mapa por núcleo de cada enlace direcional escolhido.');
      add('slot N', 'fração das observações (uma a cada chegada de requisição) em que o slot N estava ocupado, média das replicações. Bandas de guarda contam como ocupadas; no núcleo QKD, os QChs e PIChs.');
    } else if (m.kind === 'cores') {
      add('caso', 'caso simulado (um diagrama por caso).');
      add('núcleo', 'índice do núcleo na MCF (posições lidas de network.cores).');
      add('uso', 'Utilization per Core: utilização média do núcleo em todos os enlaces (média das replicações).');
      add('bloqueio', 'Blocking probability per core N: bloqueadas no núcleo N ÷ tentativas no núcleo N' + (spec.cores.normBlock ? ', normalizado para somar 1 entre os núcleos' : '') + '.');
    }
    return el('div.table-legend', el('div.about-title', 'Legenda da tabela'), items);
  }

  function toCsv(t) {
    const q = (v) => (/[",;\n]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : String(v));
    return [t.head].concat(t.rows).map((r) => r.map(q).join(',')).join('\n');
  }

  // ================================================================ UI helpers

  function field(label, input, help) {
    return el('label.field', el('div.flabel', label), input, help ? el('div.help', help) : null);
  }
  /** Like field(), but a div: for groups with several buttons/checkboxes (a label would forward clicks to the first one). */
  function groupField(label, content, help) {
    return el('div.field', el('div.flabel', label), content, help ? el('div.help', help) : null);
  }
  function numInput(obj, key, onChange, attrs) {
    const i = el('input.input.num', Object.assign({ type: 'number', value: obj[key] === undefined ? '' : obj[key] }, attrs || {}));
    i.addEventListener('input', () => { obj[key] = i.value === '' ? '' : Number(i.value); onChange(); });
    return i;
  }
  function textInput(obj, key, onChange, attrs) {
    const i = el('input.input', Object.assign({ type: 'text', value: obj[key] === undefined ? '' : obj[key] }, attrs || {}));
    i.addEventListener('input', () => { obj[key] = i.value; onChange(); });
    return i;
  }
  function colorInput(obj, key, onChange) {
    const i = el('input.input.color', { type: 'color', value: toHex(obj[key] || '#000000') });
    i.addEventListener('input', () => { obj[key] = i.value; onChange(); });
    return i;
  }
  function selectInput(obj, key, options, onChange) {
    const s = el('select.input', options.map(([v, l]) => el('option', { value: String(v) }, l)));
    s.value = String(obj[key]);
    s.addEventListener('change', () => {
      const o = options.find(([v]) => String(v) === s.value);
      obj[key] = o ? o[0] : s.value;
      onChange();
    });
    return s;
  }
  function checkInput(obj, key, label, onChange) {
    const c = el('input', { type: 'checkbox' });
    c.checked = !!obj[key];
    c.addEventListener('change', () => { obj[key] = c.checked; onChange(); });
    return el('label.check', c, ' ', label);
  }
  function toHex(c) {
    if (/^#[0-9a-f]{6}$/i.test(c)) return c;
    if (/^#[0-9a-f]{3}$/i.test(c)) return '#' + c.slice(1).split('').map((x) => x + x).join('');
    const m = /rgb\((\d+),\s*(\d+),\s*(\d+)/.exec(c || '');
    if (m) return '#' + [m[1], m[2], m[3]].map((x) => Number(x).toString(16).padStart(2, '0')).join('');
    return '#000000';
  }
  function section(title, open, ...children) {
    const d = el('details.fsec', el('summary', title), el('div.fsec-body', ...children));
    d.open = !!open;
    const k = 'snets.fig.sec.' + title;
    try { const v = localStorage.getItem(k); if (v !== null) d.open = v === '1'; } catch (e) { /* ignore */ }
    d.addEventListener('toggle', () => { try { localStorage.setItem(k, d.open ? '1' : '0'); } catch (e) { /* ignore */ } });
    return d;
  }

  // ================================================================ render page

  let host = null, leftCol = null, previewBox = null, tableBox = null, seriesBox = null, infoBox = null;

  function render() { return renderInto(document.getElementById('figView'), 'page'); }

  /**
   * Draws the studio in a container. mode 'case' is the Results tab of a case:
   * the case is always selected first and the figure is remembered per case.
   */
  async function renderInto(container, mode, casePath) {
    if (mode !== S.mode || casePath !== S.casePath || !S.spec) {
      S.mode = mode === 'case' ? 'case' : 'page';
      S.casePath = S.mode === 'case' ? casePath : null;
      S.spec = null; S.savedName = ''; S.model = null; S.seriesKeys = '';
      loadDraft();
      if (S.spec) S.spec = upgrade(S.spec);
    }
    if (!S.spec) S.spec = newSpec('bp', S.mode === 'case' ? [casePath] : []);
    if (S.mode === 'case' && !S.spec.cases.find((c) => c.path === casePath)) {
      S.spec.cases.unshift({ path: casePath, label: App.caseNameOf(casePath) });
    }
    S.seriesKeys = '';
    host = clear(container);
    leftCol = el('div.fig-controls');
    previewBox = el('div.fig-preview');
    tableBox = el('div.fig-table');
    infoBox = el('div.fig-info');
    const head = S.mode === 'case'
      ? el('div.fig-head',
        el('div', el('h2', 'Resultados do caso'),
          el('p.muted', 'Média das replicações com IC 95 % (t de Student). Escolha o arquivo e a métrica em "2. Dados"; adicione outros casos em "1. Casos" para comparar. Estilo, exportação e figuras salvas funcionam como no botão Gráficos.')),
        el('div.toolbar', { style: { margin: 0 } }, toolbar(),
          el('button.btn.small.ghost', { type: 'button', title: 'Continua esta figura na página Gráficos', on: { click: () => {
            try { localStorage.setItem('snets.figure.draft', JSON.stringify({ spec: S.spec, savedName: S.savedName })); } catch (e) { /* ignore */ }
            S.mode = 'page'; S.casePath = null; S.spec = null;
            App.showFigures();
          } } }, 'Abrir na página Gráficos')))
      : el('div.fig-head',
        el('div', el('h1', 'Estúdio de gráficos'),
          el('p.muted', 'Monte figuras com os CSVs de vários casos, ajuste o estilo e exporte em SVG/PNG. As figuras salvas ficam em launcher/figures e são recalculadas com os resultados atuais.')),
        toolbar());
    host.append(head, el('div.fig-layout', leftCol, el('div.fig-right', infoBox, previewBox, tableBox)));
    await refreshSaved();
    drawControls();
    await refresh();
  }

  function toolbar() {
    const bar = el('div.toolbar.fig-toolbar');
    const savedSel = el('select.input', { style: { maxWidth: '230px' } });
    const fillSaved = () => {
      clear(savedSel).append(el('option', { value: '' }, S.saved.length ? 'Abrir figura salva…' : 'Nenhuma figura salva'),
        S.saved.map((f) => el('option', { value: f.name }, f.name)));
    };
    fillSaved();
    S.fillSaved = fillSaved;
    savedSel.addEventListener('change', async () => {
      const n = savedSel.value;
      savedSel.value = '';
      if (!n) return;
      try {
        const d = await App.api.figure(n);
        const spec = JSON.parse(d.text);
        S.spec = upgrade(spec); S.savedName = n;
        drawControls(); await refresh();
        App.toast('Figura "' + n + '" aberta', 'ok');
      } catch (e) { App.toast(e.message, 'err'); }
    });
    const pngScale = el('select.input', { title: 'Resolução do PNG', style: { width: 'auto' } },
      [[1, 'PNG 1×'], [2, 'PNG 2×'], [3, 'PNG 3× (≈300 dpi)'], [4, 'PNG 4×']].map(([v, l]) => el('option', { value: v }, l)));
    pngScale.value = '3';
    const fname = () => (S.savedName || (S.spec.style.title || S.spec.template || 'figura')).replace(/[^\w\-. ()+À-ÿ]+/g, '_').slice(0, 80) || 'figura';
    bar.append(
      el('button.btn.small', { type: 'button', on: { click: () => newDialog() } }, 'Nova'),
      savedSel,
      el('button.btn.small.primary', { type: 'button', on: { click: () => saveDialog(false) } }, 'Salvar'),
      el('button.btn.small', { type: 'button', on: { click: () => saveDialog(true) } }, 'Salvar como…'),
      el('span.sep'),
      el('button.btn.small', { type: 'button', on: { click: () => { if (S.svg) App.downloadText(fname() + '.svg', R().serialize(S.svg), 'image/svg+xml'); } } }, 'Baixar SVG'),
      pngScale,
      el('button.btn.small', { type: 'button', on: { click: async () => {
        if (!S.svg) return;
        try {
          const url = await R().toPng(S.svg, Number(pngScale.value));
          const a = el('a', { href: url, download: fname() + '.png' }); document.body.appendChild(a); a.click(); a.remove();
        } catch (e) { App.toast(e.message, 'err'); }
      } } }, 'Baixar PNG'),
      el('button.btn.small', { type: 'button', title: 'Grava SVG e PNG em launcher/figures', on: { click: () => saveImages(Number(pngScale.value)) } }, 'Gravar imagens na pasta'),
      el('button.btn.small', { type: 'button', on: { click: () => { if (S.table) App.downloadText(fname() + '.csv', toCsv(S.table), 'text/csv'); } } }, 'Dados (CSV)'),
      el('button.btn.small.ghost', { type: 'button', title: 'Abrir launcher/figures', on: { click: () => App.api.openFiguresFolder().catch((e) => App.toast(e.message, 'err')) } }, 'Abrir pasta'));
    return bar;
  }

  function upgrade(spec) {
    const base = newSpec(spec.template || 'bp', []);
    const out = Object.assign(base, spec);
    out.style = Object.assign(R().defaultStyle(), spec.style || {});
    out.line = Object.assign(base.line, spec.line || {});
    out.bars = Object.assign(base.bars, spec.bars || {});
    out.cores = Object.assign(base.cores, spec.cores || {});
    out.topo = Object.assign(base.topo, spec.topo || {});
    out.slots = Object.assign(base.slots, spec.slots || {});
    out.scatter = Object.assign(base.scatter, spec.scatter || {});
    out.labels = spec.labels || {}; out.seriesStyle = spec.seriesStyle || {}; out.filters = spec.filters || {};
    return out;
  }

  async function refreshSaved() {
    try { S.saved = (await App.api.figures()).items || []; } catch (e) { S.saved = []; }
    if (S.fillSaved) S.fillSaved();
  }

  function newDialog() {
    const sel = el('select.input', templateOptions());
    const keep = el('input', { type: 'checkbox', checked: true });
    App.modal('Nova figura', el('div.form-grid',
      field('Modelo', sel),
      el('label.check', keep, ' Manter os casos selecionados (' + S.spec.cases.length + ')')), async () => {
      const cases = keep.checked ? S.spec.cases : [];
      const labels = keep.checked ? Object.fromEntries(S.spec.cases.map((c) => [c.path, c.label])) : {};
      S.spec = newSpec(sel.value, cases.map((c) => c.path));
      S.spec.cases.forEach((c) => { if (labels[c.path]) c.label = labels[c.path]; });
      if (S.mode === 'case' && !S.spec.cases.find((c) => c.path === S.casePath)) S.spec.cases.unshift({ path: S.casePath, label: App.caseNameOf(S.casePath) });
      S.savedName = '';
      drawControls(); await refresh();
    }, 'Criar');
  }

  function saveDialog(asNew) {
    const doSave = async (name) => {
      const d = await App.api.saveFigure(name, JSON.stringify(S.spec, null, 2));
      S.savedName = name; persistDraft();
      await refreshSaved();
      App.toast('Figura salva em ' + d.file, 'ok');
    };
    if (!asNew && S.savedName) { doSave(S.savedName).catch((e) => App.toast(e.message, 'err')); return; }
    const inp = el('input.input', { type: 'text', value: S.savedName || (S.spec.style.title || '').slice(0, 60), placeholder: 'ex.: Fig5_causas_USA' });
    App.modal('Salvar figura', el('div.form-grid', field('Nome', inp, 'Grava a definição (casos, dados e estilo) em launcher/figures/<nome>.json.')), async () => {
      const n = inp.value.trim();
      if (!n) throw new Error('Informe um nome.');
      if (S.saved.find((f) => f.name === n) && n !== S.savedName && !window.confirm('Já existe uma figura com este nome. Substituir?')) throw new Error('Escolha outro nome.');
      await doSave(n);
    }, 'Salvar');
  }

  async function saveImages(scale) {
    if (!S.svg) return;
    let name = S.savedName;
    if (!name) { App.toast('Salve a figura primeiro (o nome é usado nos arquivos).', 'warn'); saveDialog(true); return; }
    try {
      await App.api.saveFigureImage(name, 'svg', R().serialize(S.svg));
      const png = await R().toPng(S.svg, scale);
      const d = await App.api.saveFigureImage(name, 'png', png);
      App.toast('Imagens gravadas: ' + d.file.replace(/\.png$/, '.{svg,png}'), 'ok');
    } catch (e) { App.toast(e.message, 'err'); }
  }

  // ---------------------------------------------------------------- controls

  function changedData() { S.dirty = true; persistDraft(); drawControls(); refresh(); }
  const changedStyle = App.debounce(() => { S.dirty = true; persistDraft(); refresh(true); }, 120);

  function drawControls() {
    if (!leftCol) return;
    const top = leftCol.scrollTop;
    clear(leftCol);
    const spec = S.spec;
    leftCol.append(
      section('1. Casos (' + spec.cases.length + ')', true, casesPanel()),
      section('2. Dados', true, dataPanel()),
      (seriesBox = el('div')),
      section('4. Aparência', false, appearancePanel()),
      section('5. Eixos e números', false, axesPanel()),
      section('6. Legenda e título', false, legendPanel()));
    drawSeriesPanel();
    leftCol.scrollTop = top;
  }

  function casesPanel() {
    const spec = S.spec;
    const withVerif = S.caseVerif || spec.cases.some((c) => App.isVerificationPath(c.path));
    const all = App.allCases(withVerif).filter((c) => c.csvCount > 0);
    const box = el('div.stack-s');
    const selList = el('div.fig-caselist');
    spec.cases.forEach((c, i) => {
      const lab = el('input.input', { type: 'text', value: c.label, title: 'Rótulo usado no gráfico' });
      lab.addEventListener('input', () => { c.label = lab.value; changedStyle(); });
      selList.append(el('div.fig-case',
        el('div.fig-case-main', lab, el('div.small.muted.ell', { title: c.path }, c.path)),
        el('div.fig-case-btns',
          el('button.btn.small.icon', { type: 'button', title: 'Subir', disabled: i === 0, on: { click: () => { spec.cases.splice(i - 1, 0, spec.cases.splice(i, 1)[0]); changedData(); } } }, '↑'),
          el('button.btn.small.icon', { type: 'button', title: 'Descer', disabled: i === spec.cases.length - 1, on: { click: () => { spec.cases.splice(i + 1, 0, spec.cases.splice(i, 1)[0]); changedData(); } } }, '↓'),
          el('button.btn.small.icon', { type: 'button', title: 'Remover', on: { click: () => { spec.cases.splice(i, 1); changedData(); } } }, '✕'))));
    });
    if (!spec.cases.length) selList.append(el('p.small.muted', 'Nenhum caso selecionado. Marque abaixo os casos cujos resultados entram no gráfico (a ordem define a ordem no gráfico).'));

    const filter = el('input.input', { type: 'search', placeholder: 'Filtrar casos com resultados…', value: S.caseFilter });
    const list = el('div.fig-pick');
    const fill = () => {
      clear(list);
      const q = S.caseFilter.toLowerCase();
      const shown = all.filter((c) => !q || c.path.toLowerCase().includes(q));
      for (const c of shown.slice(0, 400)) {
        const cb = el('input', { type: 'checkbox' });
        cb.checked = !!spec.cases.find((x) => x.path === c.path);
        cb.addEventListener('change', () => {
          if (cb.checked) spec.cases.push({ path: c.path, label: App.caseNameOf(c.path) });
          else spec.cases = spec.cases.filter((x) => x.path !== c.path);
          changedData();
        });
        list.append(el('label.fig-pick-row', cb, el('span.ell', { title: c.path }, c.path), el('span.small.muted', c.csvCount + ' csv')));
      }
      if (!shown.length) list.append(el('p.small.muted', all.length ? 'Nenhum caso corresponde ao filtro.' : 'Nenhum caso com resultados (CSV) encontrado em simulations/.'));
      if (shown.length > 400) list.append(el('p.small.muted', 'Mostrando 400 de ' + shown.length + ' casos; use o filtro.'));
    };
    filter.addEventListener('input', App.debounce(() => { S.caseFilter = filter.value; fill(); }, 120));
    fill();
    const folders = [...new Set(all.map((c) => c.path.split('/').slice(0, -1).join('/')))].filter(Boolean).sort();
    const fsel = el('select.input', el('option', { value: '' }, 'Adicionar todos de uma pasta…'), folders.map((f) => el('option', { value: f }, f + '/')));
    fsel.addEventListener('change', () => {
      const f = fsel.value;
      if (!f) return;
      for (const c of all) if (c.path.split('/').slice(0, -1).join('/') === f && !spec.cases.find((x) => x.path === c.path)) spec.cases.push({ path: c.path, label: App.caseNameOf(c.path) });
      changedData();
    });
    box.append(selList,
      el('div.toolbar', { style: { margin: '6px 0' } }, fsel,
        spec.cases.length ? el('button.btn.small', { type: 'button', on: { click: () => { spec.cases = []; changedData(); } } }, 'Limpar') : null),
      filter,
      App.state.tree && App.state.tree.verification ? el('label.check.small', (() => {
        const cb = el('input', { type: 'checkbox' });
        cb.checked = withVerif;
        cb.addEventListener('change', () => { S.caseVerif = cb.checked; drawControls(); });
        return cb;
      })(), ' Incluir os casos da pasta verificacao (plano de verificação)') : null,
      list);
    return box;
  }

  function dataPanel() {
    const spec = S.spec;
    const box = el('div.stack-s');
    const tmplSel = el('select.input', templateOptions());
    tmplSel.value = spec.template;
    tmplSel.addEventListener('change', () => {
      const keepCases = spec.cases;
      const keepStyle = { width: spec.style.width, font: spec.style.font, fontSize: spec.style.fontSize, decimal: spec.style.decimal, palette: spec.style.palette, bg: spec.style.bg };
      S.spec = newSpec(tmplSel.value, []);
      S.spec.cases = keepCases;
      Object.assign(S.spec.style, keepStyle);
      if (S.spec.template === 'cores') { S.spec.style.width = Math.max(S.spec.style.width, 900); }
      changedData();
    });
    const tdesc = (TEMPLATES.find((t) => t.id === spec.template) || {}).desc;
    box.append(field('Modelo de gráfico', tmplSel, tdesc || 'Preenche tipo, arquivo, métrica e títulos; tudo pode ser ajustado abaixo.'));

    const typeSel = selectInput(spec, 'type', [['line', 'Linhas (métrica × carga ou parâmetro)'], ['bars', 'Barras (agrupadas / empilhadas)'], ['cores', 'Diagrama da fibra (núcleos)'], ['topo', 'Mapa de calor na topologia (enlaces)'], ['slots', 'Mapa de calor do espectro (slots × núcleos × enlaces)'], ['slotblock', 'Mapa de calor do bloqueio no espectro (slots)'], ['slotline', 'Perfil de ocupação por slot (linhas)'], ['scatter', 'Dispersão: métrica × métrica (correlação)']], () => {
      if (spec.type === 'topo' && !spec.file) { spec.file = 'SpectrumUtilization'; spec.metric = 'Utilization Per Link'; }
      if (spec.type === 'slots') { spec.file = 'SlotUtilization'; if (!spec.slots) spec.slots = newSpec('slotHeat', []).slots; if (spec.style.width < 1000) spec.style.width = 1400; }
      if (spec.type === 'slotblock') { spec.file = 'SlotBlocking'; if (!spec.slots) spec.slots = newSpec('slotBlock', []).slots; if (spec.style.width < 1000) spec.style.width = 1400; }
      if (spec.type === 'slotline') { spec.file = 'SlotUtilization'; if (!spec.slots) spec.slots = newSpec('slotProfile', []).slots; spec.style.xAsCategories = false; }
      if (spec.type === 'scatter') { if (!spec.scatter) spec.scatter = newSpec('scLinkUtilBlock', []).scatter; spec.style.xAsCategories = false; spec.style.errorBars = 'none'; }
      changedData();
    });
    box.append(field('Tipo', typeSel));

    if (spec.type === 'cores') { box.append(coresDataFields()); return box; }
    if (spec.type === 'slots' || spec.type === 'slotline' || spec.type === 'slotblock') { box.append(slotsDataFields()); return box; }
    if (spec.type === 'scatter') { box.append(scatterDataFields()); return box; }
    if (spec.type === 'topo') {
      const fileSel = el('select.input', S.fileOptions.length ? S.fileOptions.map((f) => el('option', { value: f.suffix }, f.suffix)) : el('option', { value: spec.file }, spec.file || '—'));
      fileSel.value = spec.file;
      fileSel.addEventListener('change', () => { spec.file = fileSel.value; spec.metric = ''; spec.filters = {}; changedData(); });
      const linkMetrics = S.metricOptions.filter((m) => (S.dimOptions[m] || []).some((d) => d.name.toLowerCase() === 'link' && d.values.some((v) => v !== 'all')));
      const mSel = el('select.input', linkMetrics.length ? linkMetrics.map((m) => el('option', { value: m }, m)) : el('option', { value: spec.metric }, spec.metric || '—'));
      mSel.value = spec.metric;
      mSel.addEventListener('change', () => { spec.metric = mSel.value; spec.filters = {}; changedData(); });
      box.append(field('Arquivo de resultados', fileSel), field('Métrica por enlace', mSel, 'Só aparecem métricas com a dimensão Link (um valor por enlace).'));
      const lps = S.lpOptions || [];
      const T = spec.topo;
      const lpText = (l) => 'Ponto ' + (l + 1) + (S.lpErl && S.lpErl[l] !== undefined ? ' · ' + App.fmtNum(S.lpErl[l], 0) + ' Erlangs' : '');
      const labels = lps.map(lpText);
      box.append(field('Pontos de carga (uma imagem por ponto)', metricChecklist(labels, (T.lps.length ? lps.filter((l) => T.lps.includes(l)) : lps).map(lpText), (arr) => {
        const chosen = lps.filter((l, i) => arr.includes(labels[i]));
        T.lps = chosen.length === lps.length ? [] : chosen;
        changedData();
      }, true)));
      box.append(filterFields(spec, ['Link', 'link']));
      box.append(el('div.checks', checkInput(T, 'block', 'Mostrar também o bloqueio de cada enlace (BlockingDetails.csv)', () => { if (T.block && spec.style.topoLabels !== 'block') spec.style.topoLabels = 'block'; changedData(); })));
      if (T.block) box.append(field('Bloqueio exibido', selectInput(T, 'blockClasses', [['split', 'Clássicas e QKD separadas (quando houver QKD)'], ['all', 'Todas as requisições juntas']], () => changedData()),
        'Bloqueio do enlace = requisições bloqueadas cuja rota tentada passa pelo enlace ÷ requisições cuja rota passa por ele. Cada sentido é um enlace.'));
      box.append(el('p.small.muted', 'As posições dos nós vêm dos campos x/y do arquivo network (gravados ao aplicar uma topologia pronta na aba Rede). Sem eles, uma topologia pronta com os mesmos enlaces é reconhecida; caso contrário, o desenho é automático.'));
      return box;
    }

    // file + metric from the first case that has it
    const fileOpts = S.fileOptions;
    const fileSel = el('select.input', fileOpts.length ? fileOpts.map((f) => el('option', { value: f.suffix }, f.suffix + (f.count < spec.cases.length ? '  (' + f.count + '/' + spec.cases.length + ' casos)' : ''))) : el('option', { value: spec.file }, spec.file || '—'));
    fileSel.value = spec.file;
    fileSel.addEventListener('change', () => { spec.file = fileSel.value; spec.metric = ''; spec.filters = {}; spec.bars.items = []; changedData(); });
    box.append(field('Arquivo de resultados', fileSel));

    const metrics = S.metricOptions;
    const needMetric = spec.type === 'line' || spec.bars.itemsFrom === 'dim';
    if (needMetric) {
      const mSel = el('select.input', metrics.map((m) => el('option', { value: m }, m)));
      mSel.value = spec.metric;
      mSel.addEventListener('change', () => { spec.metric = mSel.value; spec.filters = {}; spec.bars.items = []; changedData(); });
      box.append(field('Métrica', mSel));
    }

    if (spec.type === 'line') {
      const L = spec.line;
      const dims = (S.dimOptions[spec.metric] || []).filter((d) => d.values.length > 1);
      const sOpts = [['case', 'Uma linha por caso'], ['metric', 'Uma linha por métrica (escolha abaixo)']].concat(dims.map((d) => ['dim:' + d.name, 'Uma linha por ' + d.name]));
      box.append(field('Séries', selectInput(L, 'seriesBy', sOpts, () => changedData())));
      if (L.seriesBy === 'metric') box.append(metricChecklist(metrics, spec.metrics, (arr) => { spec.metrics = arr; changedData(); }));
      box.append(field('Eixo x', selectInput(L, 'xMode', [['erl', 'Carga (Erlang)'], ['lp1', 'Ponto de carga (1, 2, 3…)'], ['lp', 'Índice do ponto (0, 1, 2…)'], ['param', 'Parâmetro dos casos (varredura)']], () => changedData())));
      if (L.xMode === 'param') {
        const pSel = el('select.input', (S.paramOptions || []).length ? S.paramOptions.map((p) => el('option', { value: p }, p)) : el('option', { value: '' }, 'os casos não diferem em parâmetros numéricos'));
        pSel.value = L.paramPath;
        pSel.addEventListener('change', () => { L.paramPath = pSel.value; changedData(); });
        box.append(field('Parâmetro', pSel), field('Ponto de carga', lpSelect(L, 'paramLp')));
      }
      box.append(filterFields(spec, L.seriesBy.startsWith('dim:') ? L.seriesBy.slice(4) : null));
    } else {
      const B = spec.bars;
      box.append(field('Barras a partir de', selectInput(B, 'itemsFrom', [['metrics', 'Várias métricas (ex.: causas de bloqueio)'], ['dim', 'Valores de uma dimensão (ex.: Link, Modulation, Core)']], () => { B.items = []; changedData(); })));
      if (B.itemsFrom === 'metrics') {
        const re = B.itemsRe ? new RegExp(B.itemsRe, 'i') : null;
        const current = B.items.length ? B.items : metrics.filter((m) => (re ? re.test(m) : m === spec.metric));
        box.append(metricChecklist(metrics, current, (arr) => { B.items = arr; B.itemsRe = ''; changedData(); }));
      } else {
        const dims = (S.dimOptions[spec.metric] || []).filter((d) => d.values.length > 1);
        if (!dims.find((d) => d.name === B.dim) && dims.length) B.dim = dims[0].name;
        box.append(field('Dimensão', selectInput(B, 'dim', dims.map((d) => [d.name, d.name + ' (' + d.values.length + ')']), () => { B.items = []; changedData(); })));
        box.append(field('Cores (séries) por', selectInput(B, 'seriesDim', [['', B.xAxis === 'items' ? 'Casos' : 'Valores da dimensão']].concat(dims.filter((x) => x.name !== B.dim).map((x) => [x.name, x.name])), () => { if (B.seriesDim) B.xAxis = 'items'; changedData(); }), 'Ex.: Dimensão = Core e cores por Cause dá as causas de bloqueio em cada núcleo.'));
        const d = dims.find((x) => x.name === B.dim);
        if (d && d.values.length <= 40) {
          const vals = d.values.filter((v) => v !== 'all');
          box.append(metricChecklist(vals, B.items.length ? B.items : vals, (arr) => { B.items = arr.length === vals.length ? [] : arr; changedData(); }, true));
        }
      }
      box.append(field('Ponto de carga', lpSelect(B, 'lp')));
      box.append(field('No eixo x', selectInput(B, 'xAxis', [['cases', 'Casos (cores = itens)'], ['items', 'Itens (cores = casos)']], () => changedData())));
      const opts = el('div.checks');
      opts.append(checkInput(B, 'stacked', 'Empilhar', () => { if (B.stacked) spec.style.errorBars = 'none'; changedData(); }));
      opts.append(checkInput(B, 'hideZero', 'Ocultar itens sempre zero', () => changedData()));
      if (B.stacked) opts.append(checkInput(B, 'normalize', 'Normalizar em 100 %', () => changedData()));
      box.append(opts);
      if (B.xAxis === 'items') {
        box.append(field('Disposição', selectInput(B, 'layout', [['grouped', 'Agrupadas num só gráfico'], ['panels', 'Um painel por caso']], () => changedData())));
        box.append(field('Ordenar', selectInput(B, 'sort', [['none', 'Ordem do arquivo'], ['asc', 'Crescente'], ['desc', 'Decrescente']], () => changedData())));
        box.append(field('Mostrar só os N primeiros', numInput(B, 'top', () => changedStyleData(), { min: 0, step: 1, placeholder: '0 = todos' })));
      }
      box.append(filterFields(spec, B.itemsFrom === 'dim' ? [B.dim, B.seriesDim] : null));
    }
    return box;
  }
  const changedStyleData = App.debounce(() => { persistDraft(); refresh(); }, 250);

  function coresDataFields() {
    const C = S.spec.cores;
    const box = el('div.stack-s');
    box.append(field('Ponto de carga', lpSelect(C, 'lp')),
      field('Mostrar', selectInput(S.spec.style, 'coreShow', [['both', 'Bloqueio e uso'], ['block', 'Só bloqueio'], ['util', 'Só uso'], ['none', 'Só a fibra']], () => changedStyle())),
      el('div.checks', checkInput(C, 'normBlock', 'Bloqueio como fração do total (soma = 1)', () => changedData()),
        checkInput(S.spec.style, 'coreHideUnused', 'Núcleos sem uso e sem bloqueio tracejados (ex.: sem núcleo central)', () => changedData())),
      el('p.small.muted', 'Uso: ' + C.utilFile + '.csv → "' + C.utilMetric + '" (Link = all). Bloqueio: ' + C.blockFile + '.csv → "' + C.blockPrefix + ' N". A posição dos núcleos segue o arquivo network: as fibras de 2, 7, 12 e 19 núcleos usam os mesmos desenhos da aba Rede; nas demais, o núcleo vizinho de todos fica no centro e os outros em anel.'));
    return box;
  }

  function scatterDataFields() {
    const X = S.spec.scatter;
    const o = S.scatterOptions || { files: [], metrics: {}, dims: {} };
    const box = el('div.stack-s');
    const fileSel = (key, mkey) => {
      const s2 = el('select.input', (o.files.length ? o.files : [X[key]]).map((f) => el('option', { value: f }, f)));
      s2.value = X[key];
      s2.addEventListener('change', () => { X[key] = s2.value; X[mkey] = ''; changedData(); });
      return s2;
    };
    const metricSel = (fkey, mkey) => {
      const ms = o.metrics[X[fkey]] || (X[mkey] ? [X[mkey]] : []);
      const s2 = el('select.input', ms.map((m) => el('option', { value: m }, m + (autoLabel(m) !== m ? ' — ' + autoLabel(m) : ''))));
      if (!ms.includes(X[mkey]) && ms.length) X[mkey] = ms[0];
      s2.value = X[mkey];
      s2.addEventListener('change', () => { X[mkey] = s2.value; changedData(); });
      return s2;
    };
    const dimFilters = (fkey, mkey, fobj) => {
      const ds = ((o.dims[X[fkey]] || {})[X[mkey]] || []).filter((d) => d.values.length > 1 && d.name.toLowerCase() !== String(X.by).toLowerCase());
      return ds.map((d) => {
        const cur = fobj[d.name] !== undefined && d.values.includes(fobj[d.name]) ? fobj[d.name] : (d.values.includes('all') ? 'all' : d.values[0]);
        const s2 = el('select.input', d.values.slice(0, 500).map((v) => el('option', { value: v }, autoLabel(v) !== v ? autoLabel(v) : v)));
        s2.value = cur;
        s2.addEventListener('change', () => { fobj[d.name] = s2.value; changedData(); });
        return field('Filtro: ' + d.name, s2);
      });
    };
    box.append(field('Um ponto por', selectInput(X, 'by', [['Link', 'Enlace (cada sentido)'], ['Core', 'Núcleo'], ['LoadPoint', 'Ponto de carga']], () => changedData()),
      'Enlaces e núcleos: os dois valores no mesmo ponto de carga. Pontos de carga: um ponto por carga, as duas métricas gerais.'));
    box.append(el('div.fsub', 'Eixo x'), field('Arquivo', fileSel('xFile', 'xMetric')), field('Métrica', metricSel('xFile', 'xMetric')), ...dimFilters('xFile', 'xMetric', X.fx));
    box.append(el('div.fsub', 'Eixo y'), field('Arquivo', fileSel('yFile', 'yMetric')), field('Métrica', metricSel('yFile', 'yMetric')), ...dimFilters('yFile', 'yMetric', X.fy));
    if (X.by !== 'LoadPoint') box.append(field('Ponto de carga', lpSelect(X, 'lp')));
    box.append(el('div.checks', checkInput(X, 'trend', 'Reta de ajuste e r de Pearson', () => changedData()),
      checkInput(X, 'pooled', 'Um ajuste só, com os pontos de todos os casos', () => changedData())));
    box.append(el('p.small.muted', 'r perto de 1 (ou −1): as duas métricas crescem juntas (ou em sentidos opostos); perto de 0, sem relação linear. Correlação não é causa: use para levantar hipóteses e conferir com os casos.'));
    return box;
  }

  function slotsDataFields() {
    const T = S.spec.slots;
    const o = S.slotOptions || { links: [], cores: [], nSlots: 0 };
    const box = el('div.stack-s');
    const isBlock = S.spec.type === 'slotblock';
    if (!o.links.length) {
      box.append(el('p.small.muted', isBlock ? 'Nenhum caso selecionado tem SlotBlocking.csv.' + fileHint('SlotBlocking') : 'Nenhum caso selecionado tem SlotUtilization.csv. O arquivo é gravado pelo simulador da Parte 27 (métrica SlotUtilization, ligada por padrão junto com SpectrumUtilization).'));
      return box;
    }
    box.append(field('Ponto de carga', lpSelect(T, 'lp')));
    if (isBlock) {
      const causes = (o.causes || []).slice();
      if (T.blockCause && T.blockCause !== 'all' && !causes.includes(T.blockCause)) causes.push(T.blockCause);
      box.append(el('div.fields.two',
        field('O que cada slot mostra', selectInput(T, 'blockMode', [['ratio', 'Bloqueadas ÷ tentativas (escala de cores)'], ['count', 'Número de bloqueios (escala de cores)'], ['causes', 'Causa do bloqueio (uma cor por causa)']], () => {
          if (T.blockMode === 'count') S.spec.style.slotRange = 'auto';
          changedData();
        })),
        field('Causa', selectInput(T, 'blockCause', [['all', 'Todas as causas']].concat(causes.map((k) => [k, autoLabel(k) + (autoLabel(k) !== k ? ' (' + k + ')' : '')])), () => changedData()))));
      box.append(el('div.checks',
        checkInput(T, 'onlyBlocked', 'Só os núcleos de enlace com bloqueio', () => changedData()),
        checkInput(T, 'summary', 'Colunas à direita: bloqueadas/feitas e %', () => changedData())));
    }
    const links = o.links.slice();
    if (T.order === 'name') links.sort(linkCompare);
    const quick = (label, fn) => el('button.btn.small', { type: 'button', on: { click: fn } }, label);
    const linkBox = metricChecklist(links, T.links.length ? links.filter((l) => T.links.includes(l)) : links, (arr) => { T.links = arr.length === links.length ? [] : arr; changedData(); }, true);
    box.append(groupField('Enlaces (' + (T.links.length || links.length) + ' de ' + links.length + ')', el('div.stack-s',
      el('div.toolbar', { style: { margin: 0 } },
        quick('Todos', () => { T.links = []; changedData(); }),
        quick('Só um sentido (a < b)', () => { T.links = links.filter((l) => { const m = /^<([^<>]+?)-([^<>]+)>$/.exec(l); return m && linkCompare('<' + m[1] + '-' + m[2] + '>', '<' + m[2] + '-' + m[1] + '>') < 0; }); changedData(); }),
        quick('Primeiro', () => { T.links = [links[0]]; changedData(); })),
      linkBox)));
    const cores = o.cores;
    box.append(groupField('Núcleos', el('div.stack-s',
      el('div.toolbar', { style: { margin: 0 } }, quick('Todos', () => { T.cores = []; changedData(); })),
      metricChecklist(cores, T.cores.length ? cores.filter((c) => T.cores.includes(c)) : cores, (arr) => { T.cores = arr.length === cores.length ? [] : arr; changedData(); }, true))));
    box.append(el('div.fields.two',
      field('Do slot', numInput(T, 'from', () => changedStyleData(), { min: 1, max: o.nSlots, step: 1 })),
      field('Até o slot', numInput(T, 'to', () => changedStyleData(), { min: 0, max: o.nSlots, step: 1, placeholder: '0 = último (' + o.nSlots + ')' }))));
    box.append(field('Ordem dos enlaces', selectInput(T, 'order', [['name', 'Por origem e destino (1-2, 1-3, 2-1…)'], ['file', 'Ordem do arquivo (lista de enlaces da rede)']], () => changedData())));
    box.append(el('p.small.muted', isBlock
      ? 'Uma tentativa é a faixa (sem bandas de guarda) do candidato de cada requisição — o circuito aceito, ou o candidato bloqueado — em cada enlace da rota, no núcleo dele. As contagens somam as replicações. Bloqueios sem candidato (sem faixa livre, sem rota) não têm lugar no espectro: aparecem só no título do mapa.'
      : 'Cada célula é a fração do tempo (amostrada a cada chegada) em que o slot estava ocupado, média das replicações. Bandas de guarda contam como ocupadas; no núcleo reservado à QKD aparecem os QChs e PIChs.'));
    return box;
  }

  function lpSelect(obj, key) {
    const lps = S.lpOptions || [];
    const opts = [[-1, 'Último ponto']].concat(lps.map((l) => [l, 'Ponto ' + (l + 1) + (S.lpErl && S.lpErl[l] !== undefined ? ' · ' + App.fmtNum(S.lpErl[l], 0) + ' E' : '')]));
    return selectInput(obj, key, opts, () => changedData());
  }

  function metricChecklist(all, selected, onChange, plain) {
    const box = el('div.fig-checklist');
    const sel = new Set(selected);
    for (const m of all) {
      const cb = el('input', { type: 'checkbox' });
      cb.checked = sel.has(m);
      cb.addEventListener('change', () => {
        if (cb.checked) sel.add(m); else sel.delete(m);
        onChange(all.filter((x) => sel.has(x)));
      });
      box.append(el('label.fig-pick-row', cb, el('span.ell', { title: m }, m), plain || autoLabel(m) === m ? null : el('span.small.muted', autoLabel(m))));
    }
    return box;
  }

  function filterFields(spec, freeDim) {
    const free = [].concat(freeDim || []);
    const dims = (S.dimOptions[spec.metric] || S.dimOptions['*'] || []).filter((d) => d.values.length > 1 && !free.includes(d.name));
    if (!dims.length) return null;
    const box = el('div.stack-s');
    for (const d of dims) {
      if (!(d.name in spec.filters) || !d.values.includes(spec.filters[d.name])) spec.filters[d.name] = d.values.includes('all') ? 'all' : d.values[0];
      const s = el('select.input', d.values.slice(0, 2000).map((v) => el('option', { value: v }, v)));
      s.value = spec.filters[d.name];
      s.addEventListener('change', () => { spec.filters[d.name] = s.value; changedData(); });
      box.append(field('Filtro: ' + d.name, s));
    }
    return box;
  }

  /** Current style of the entry e (index i) of the series panel; panels with a series dimension are looked up by key. */
  function seriesStyleOf(m, e, i) {
    if (m.kind === 'line' || m.kind === 'bars') return m.series[i] ? m.series[i].style : null;
    if (m.kind === 'panels') {
      for (const p of m.panels) { const x = p.model.series.find((s) => s.key === e.key); if (x && x.style) return x.style; }
      const p = m.panels[i];
      return p && p.model.series[0] ? p.model.series[0].style : null;
    }
    return null;
  }

  /** Remembers which text field of the panel has the focus, so a redraw does not take it away while the user types. */
  function focusKeyIn(box) {
    const a = document.activeElement;
    if (!a || !box.contains(a) || !a.dataset || !a.dataset.fk) return null;
    let sel = null;
    try { sel = [a.selectionStart, a.selectionEnd]; } catch (err) { sel = null; }
    return { fk: a.dataset.fk, sel };
  }
  function restoreFocus(box, f) {
    if (!f) return;
    const i = [...box.querySelectorAll('[data-fk]')].find((x) => x.dataset.fk === f.fk);
    if (!i) return;
    i.focus();
    if (f.sel && f.sel[0] !== null) { try { i.setSelectionRange(f.sel[0], f.sel[1]); } catch (err) { /* not a text field */ } }
  }

  function drawSeriesPanel() {
    if (!seriesBox) return;
    const keepFocus = focusKeyIn(seriesBox);
    try { drawSeriesPanelInner(); } finally { restoreFocus(seriesBox, keepFocus); }
  }

  function drawSeriesPanelInner() {
    clear(seriesBox);
    const spec = S.spec;
    const m = S.model;
    if (!m || m.empty) { seriesBox.append(section('3. Séries e cores', true, el('p.small.muted', 'As séries aparecem aqui quando houver dados.'))); return; }
    let entries = [];
    if (m.kind === 'line') entries = m.series.map((s) => ({ key: s.key, name: s.name, kind: 'line' }));
    else if (m.kind === 'bars') entries = m.series.map((s) => ({ key: s.key, name: s.name, kind: 'bar' }));
    else if (m.kind === 'panels' && m.panels.some((p) => p.model.series.some((x) => x.key.startsWith('sd:')))) {
      const seen = new Map();
      for (const p of m.panels) for (const x of p.model.series) if (!seen.has(x.key)) seen.set(x.key, { key: x.key, name: x.name, kind: 'bar' });
      entries = [...seen.values()];
    }
    else if (m.kind === 'panels') entries = m.panels.map((p) => ({ key: p.model.series[0].key, name: p.title, kind: 'bar' }));
    const pal = R().PALETTES;
    const palSel = selectInput(spec.style, 'palette', Object.keys(pal).map((k) => [k, R().PALETTE_NAMES[k]]), () => changedStyle());
    const resetBtn = el('button.btn.small', { type: 'button', title: 'Remove as cores/traços escolhidos manualmente', on: { click: () => { spec.seriesStyle = {}; persistDraft(); drawSeriesPanel(); refresh(true); } } }, 'Restaurar padrão');
    const body = el('div.stack-s', el('div.toolbar', { style: { margin: 0 } }, el('span.small.muted', 'Paleta'), palSel, resetBtn));
    if (m.kind === 'topo') {
      const st = spec.style;
      clear(body);
      body.append(el('div.fields.two',
        field('Sentidos de cada enlace', selectInput(st, 'topoDir', [['both', 'Os dois (duas faixas paralelas)'], ['max', 'Um traço: maior dos dois'], ['mean', 'Um traço: média dos dois']], changedStyle)),
        field('Rótulos nos enlaces', selectInput(st, 'topoLabels', [['none', 'Nenhum'], ['km', 'Comprimento (km)'], ['value', 'Valor da métrica']].concat(spec.topo.block ? [['block', 'Bloqueio do enlace'], ['valueBlock', 'Utilização e bloqueio']] : []), changedStyle)),
        field('Formato dos valores', selectInput(st, 'topoFormat', [['percent', 'Porcentagem'], ['fixed', 'Decimal']], changedStyle)),
        field('Casas decimais', numInput(st, 'topoDigits', changedStyle, { min: 0, max: 8 })),
        field('Escala: mínimo (verde)', numInput(st, 'topoMin', changedStyleData, { step: 'any', placeholder: 'auto = menor valor' })),
        field('Escala: máximo (vermelho)', numInput(st, 'topoMax', changedStyleData, { step: 'any', placeholder: 'auto = maior valor' })),
        field('Legenda de cores', selectInput(st, 'topoLegend', [['right', 'À direita'], ['bottom', 'Embaixo'], ['none', 'Sem legenda']], changedStyle)),
        field('Título da legenda', labelInput('__heat', App.metricInfo.axisTitle(spec.metric) || spec.metric)),
        field('Imagens por linha', numInput(st, 'topoCols', changedStyle, { min: 1, max: 8 })),
        field('Raio dos nós (px)', numInput(st, 'topoNodeR', changedStyle, { min: 4, max: 40 })),
        field('Espessura dos enlaces (px)', numInput(st, 'topoLinkWidth', changedStyle, { min: 1, max: 20, step: 0.5 })),
        field('Cor dos rótulos', colorInput(st, 'topoLabelColor', changedStyle)),
        field('Preenchimento dos nós', colorInput(st, 'topoNodeFill', changedStyle)),
        field('Enlace sem dado', colorInput(st, 'topoNoData', changedStyle))),
        el('div.checks', checkInput(st, 'topoAutoHeight', 'Altura automática (proporção da topologia)', changedStyle),
          checkInput(st, 'topoPanelBorder', 'Moldura em cada imagem', changedStyle),
          checkInput(st, 'topoTrusted', 'Nós confiáveis (pontos a cada QTND km, casos com QKD)', changedStyle)),
        el('div.fields.two', field('Cor dos nós confiáveis', colorInput(st, 'topoTrustedFill', changedStyle)),
          field('Raio dos nós confiáveis (px)', numInput(st, 'topoTrustedR', changedStyle, { min: 0, max: 20, step: 0.5, placeholder: 'auto' }))),
        el('p.small.muted', 'Com "os dois", cada sentido é uma faixa: a faixa de a→b fica à direita de quem segue de a para b. Passe o mouse sobre um enlace para ver o valor.'));
      seriesBox.append(section('3. Cores e rótulos', true, body));
      return;
    }
    if (m.kind === 'slots' && m.block) {
      const st = spec.style;
      clear(body);
      const scale = m.block !== 'causes';
      body.append(el('div.fields.two',
        scale ? field('Escala de cores', selectInput(st, 'slotScale', R().SLOT_SCALES.map((x) => [x.id, x.name]), changedStyle)) : null,
        scale && m.block === 'ratio' ? field('Faixa da escala', selectInput(st, 'slotRange', [['fixed', '0 a 100 % (fixa)'], ['auto', 'Menor a maior valor dos dados']], changedStyleData)) : null,
        scale ? field('Mínimo da escala', numInput(st, 'slotMin', changedStyleData, { step: 'any', placeholder: 'auto' })) : null,
        scale ? field('Máximo da escala', numInput(st, 'slotMax', changedStyleData, { step: 'any', placeholder: 'auto' })) : null,
        field('Cor dos slots sem tentativa', colorInput(st, 'slotNoData', changedStyleData)),
        field('Altura de cada linha (px)', numInput(st, 'slotRowH', changedStyle, { min: 2, max: 60 })),
        field('Rótulos de slot a cada', numInput(st, 'slotTickStep', changedStyle, { min: 0, step: 1, placeholder: 'auto' })),
        field('Legenda de cores', selectInput(st, 'slotLegend', [['right', 'À direita'], ['bottom', 'Embaixo'], ['none', 'Sem legenda']], changedStyle)),
        field('Título da legenda', labelInput('__slot', slotBlockLegendTitle({ labels: {} }, m.block))),
        m.summaryHeads ? field('Título da coluna bloqueadas/feitas', labelInput('__sumA', 'Bloq./req.')) : null,
        m.summaryHeads ? field('Título da coluna da porcentagem', labelInput('__sumB', '%')) : null,
        scale && m.block === 'ratio' ? field('Casas decimais da escala', numInput(st, 'slotDigits', changedStyle, { min: 0, max: 6 })) : null,
        field('Cor das divisões', colorInput(st, 'slotGridColor', changedStyle))),
        el('div.checks',
          checkInput(st, 'slotCellGrid', 'Contorno em cada slot (quando couber)', changedStyle),
          checkInput(st, 'slotLinkSep', 'Linha grossa entre enlaces', changedStyle),
          checkInput(st, 'slotPanelTitle', 'Título de cada mapa (caso · carga)', changedStyle)),
        el('p.small.muted', 'Passe o mouse sobre um slot para ver tentativas, bloqueios e as causas. A coluna "bloqueadas/feitas" conta requisições (uma vez por núcleo de enlace); as cores contam slots.'));
      seriesBox.append(section('3. Cores e rótulos', true, body));
      return;
    }
    if (m.kind === 'slots') {
      const st = spec.style;
      clear(body);
      body.append(el('div.fields.two',
        field('Escala de cores', selectInput(st, 'slotScale', R().SLOT_SCALES.map((x) => [x.id, x.name]), changedStyle)),
        field('Faixa da escala', selectInput(st, 'slotRange', [['fixed', '0 a 100 % (fixa)'], ['auto', 'Menor a maior valor dos dados']], changedStyleData)),
        field('Mínimo da escala', numInput(st, 'slotMin', changedStyleData, { step: 'any', placeholder: 'auto' })),
        field('Máximo da escala', numInput(st, 'slotMax', changedStyleData, { step: 'any', placeholder: 'auto' })),
        field('Altura de cada linha (px)', numInput(st, 'slotRowH', changedStyle, { min: 2, max: 60 })),
        field('Rótulos de slot a cada', numInput(st, 'slotTickStep', changedStyle, { min: 0, step: 1, placeholder: 'auto' })),
        field('Legenda de cores', selectInput(st, 'slotLegend', [['right', 'À direita'], ['bottom', 'Embaixo'], ['none', 'Sem legenda']], changedStyle)),
        field('Título da legenda', labelInput('__slot', 'Ocupação do slot')),
        field('Formato dos valores', selectInput(st, 'slotFormat', [['percent', 'Porcentagem'], ['fixed', 'Decimal']], changedStyle)),
        field('Casas decimais', numInput(st, 'slotDigits', changedStyle, { min: 0, max: 6 })),
        field('Cor das divisões', colorInput(st, 'slotGridColor', changedStyle)),
        field('Slot nunca ocupado', selectInput(st, 'slotZero', [['scale', 'Cor da escala'], ['white', 'Branco'], ['gray', 'Cinza claro']], changedStyle))),
        el('div.checks',
          checkInput(st, 'slotCellGrid', 'Contorno em cada slot (quando couber)', changedStyle),
          checkInput(st, 'slotLinkSep', 'Linha grossa entre enlaces', changedStyle),
          checkInput(st, 'slotPanelTitle', 'Título de cada mapa (caso · carga)', changedStyle)),
        el('p.small.muted', 'Passe o mouse sobre o mapa para ver enlace, núcleo, slots e ocupação. Com muitos slots as células vizinhas de mesma cor viram um só retângulo, o que deixa o SVG leve.'));
      seriesBox.append(section('3. Cores e rótulos', true, body));
      return;
    }
    if (m.kind === 'cores') {
      const st = spec.style;
      clear(body);
      body.append(el('div.fields.two',
        field('Cor do bloqueio', colorInput(st, 'coreBlockColor', changedStyle)),
        field('Cor do uso', colorInput(st, 'coreUtilColor', changedStyle)),
        field('Rótulo do bloqueio', labelInput('__block', 'Bloqueio do núcleo')),
        field('Rótulo do uso', labelInput('__util', 'Uso do núcleo')),
        field('Fundo da fibra', colorInput(st, 'coreFiber', changedStyle)),
        field('Contorno da fibra', colorInput(st, 'coreFiberStroke', changedStyle)),
        field('Preenchimento do núcleo', colorInput(st, 'coreFill', changedStyle)),
        field('Contorno do núcleo', colorInput(st, 'coreStroke', changedStyle)),
        field('Colorir núcleos por', selectInput(st, 'coreHeat', [['none', 'Não'], ['util', 'Uso'], ['block', 'Bloqueio']], changedStyle)),
        field('Cor do mapa de calor', colorInput(st, 'coreHeatColor', changedStyle)),
        field('Casas decimais', numInput(st, 'coreDigits', changedStyle, { min: 0, max: 10 })),
        field('Diagramas por linha', numInput(st, 'coreCols', changedStyle, { min: 1, max: 6 })),
        field('Tamanho dos rótulos (px)', numInput(st, 'coreLabelSize', changedStyle, { min: 0, max: 30, placeholder: 'auto' }))),
        el('div.checks', checkInput(st, 'coreLegend', 'Mostrar legenda', changedStyle)));
      seriesBox.append(section('3. Cores e rótulos', true, body));
      return;
    }
    entries.forEach((e, i) => {
      const o = spec.seriesStyle[e.key] || (spec.seriesStyle[e.key] = {});
      const cur = seriesStyleOf(m, e, i);
      if (!cur) return;
      const color = el('input.input.color', { type: 'color', value: toHex(cur.color) });
      color.addEventListener('input', () => { o.color = color.value; changedStyle(); });
      const vis = el('input', { type: 'checkbox', title: 'Mostrar' });
      vis.checked = cur.visible;
      vis.addEventListener('change', () => { o.visible = vis.checked; changedStyle(); });
      const row = el('div.fig-series', el('div.fig-series-head', vis, color, labelInputFor(e)));
      if (e.kind === 'line') {
        const dash = el('select.input', Object.entries(R().DASHES).map(([k, l]) => el('option', { value: k }, l)));
        dash.value = cur.dash;
        dash.addEventListener('change', () => { o.dash = dash.value; changedStyle(); });
        const width = el('input.input.num', { type: 'number', min: 0, max: 10, step: 0.5, value: cur.width, title: 'Espessura da linha' });
        width.addEventListener('input', () => { o.width = width.value; changedStyle(); });
        const mk = el('select.input', R().MARKERS.map((k) => el('option', { value: k }, R().MARKER_NAMES[k])));
        mk.value = cur.marker;
        mk.addEventListener('change', () => { o.marker = mk.value; changedStyle(); });
        const ms = el('input.input.num', { type: 'number', min: 2, max: 30, step: 1, value: cur.markerSize, title: 'Tamanho do marcador' });
        ms.addEventListener('input', () => { o.markerSize = ms.value; changedStyle(); });
        const hol = el('input', { type: 'checkbox' }); hol.checked = cur.hollow;
        hol.addEventListener('change', () => { o.hollow = hol.checked; changedStyle(); });
        const ln = el('input', { type: 'checkbox' }); ln.checked = cur.line;
        ln.addEventListener('change', () => { o.line = ln.checked; changedStyle(); });
        row.append(el('div.fig-series-grid',
          el('label', el('span', 'Traço'), dash), el('label', el('span', 'Espessura'), width),
          el('label', el('span', 'Marcador'), mk), el('label', el('span', 'Tamanho'), ms)),
        el('div.checks.small', el('label.check', hol, ' marcador vazado'), el('label.check', ln, ' linha')));
      }
      body.append(row);
    });
    if (m.kind === 'bars' && spec.bars.xAxis === 'cases' && m.categories.length) body.append(el('p.small.muted', 'Os rótulos dos casos (eixo x) são editados na seção 1.'));
    if ((m.kind === 'bars' || m.kind === 'panels') && spec.bars.xAxis === 'items') {
      const cats = m.kind === 'bars' ? m.categories : m.panels[0].model.categories;
      if (cats.length <= 30) {
        body.append(el('div.small.muted', { style: { marginTop: '6px' } }, 'Rótulos do eixo x'));
        body.append(el('div.fields.two', cats.map((c) => field(c.key, labelInput(c.key, autoLabel(c.key))))));
      }
    }
    seriesBox.append(section('3. Séries e cores (' + entries.length + ')', true, body));
  }

  function labelInputFor(e) {
    const spec = S.spec;
    // line series named by case: edit the case label; item series: edit item label
    if (e.key.startsWith('i:')) return labelInput(e.key.slice(2), autoLabel(e.key.slice(2)));
    const c = spec.cases.find((x) => x.path === e.key);
    if (c) {
      const i = el('input.input', { type: 'text', value: c.label, 'data-fk': 'case:' + c.path });
      i.addEventListener('input', () => { c.label = i.value; changedStyle(); });
      return i;
    }
    const m = /\|m:(.*)$/.exec(e.key) || /\|[^|=]+=(.*)$/.exec(e.key) || /^param\|[dm]:(.*)$/.exec(e.key);
    if (m) return labelInput(m[1], autoLabel(m[1]), e.name);
    return el('span.small', e.name);
  }

  function labelInput(key, fallback) {
    const spec = S.spec;
    const i = el('input.input', { type: 'text', value: spec.labels[key] !== undefined ? spec.labels[key] : '', placeholder: fallback, 'data-fk': 'lbl:' + key });
    i.addEventListener('input', () => { spec.labels[key] = i.value; changedStyle(); });
    return i;
  }

  function appearancePanel() {
    const st = S.spec.style;
    const fonts = R().FONTS.map((f) => [f, f.split(',')[0].replace(/"/g, '')]);
    return el('div.stack-s',
      el('div.fields.two',
        field('Largura (px)', numInput(st, 'width', changedStyle, { min: 200, max: 4000, step: 10 })),
        field('Altura (px)', numInput(st, 'height', changedStyle, { min: 150, max: 4000, step: 10 })),
        field('Fundo da imagem', colorInput(st, 'bg', changedStyle)),
        field('Fundo do gráfico', colorInput(st, 'plotBg', changedStyle)),
        field('Fonte', selectInput(st, 'font', fonts, changedStyle)),
        field('Tamanho do texto', numInput(st, 'fontSize', changedStyle, { min: 6, max: 40, step: 0.5 })),
        field('Cor do texto', colorInput(st, 'textColor', changedStyle)),
        field('Espessura padrão das linhas', numInput(st, 'lineWidth', changedStyle, { min: 0.5, max: 10, step: 0.5 })),
        field('Tamanho padrão do marcador', numInput(st, 'markerSize', changedStyle, { min: 2, max: 30 })),
        field('Barras de erro (IC 95 %)', selectInput(st, 'errorBars', [['bars', 'Barras'], ['band', 'Faixa sombreada'], ['none', 'Não mostrar']], changedStyle)),
        field('Largura do "T" do erro', numInput(st, 'capWidth', changedStyle, { min: 0, max: 20 })),
        field('Espaço entre grupos de barras', numInput(st, 'barGap', changedStyle, { min: 0, max: 0.95, step: 0.05 })),
        field('Espaço entre barras do grupo', numInput(st, 'seriesGap', changedStyle, { min: 0, max: 0.5, step: 0.02 })),
        field('Painéis por linha', numInput(st, 'panelCols', changedStyle, { min: 1, max: 6 })),
        field('Altura de cada painel (px)', numInput(st, 'panelHeight', changedStyle, { min: 0, max: 2000, step: 10, placeholder: '0 = usa a altura total' })),
        field('Rótulo do painel', textInput(st, 'panelLabel', changedStyle, { placeholder: 'ex.: (a)' }))),
      el('div.checks',
        checkInput(st, 'barValues', 'Valores sobre as barras', changedStyle),
        checkInput(st, 'barOutline', 'Contorno nas barras', changedStyle),
        checkInput(st, 'border', 'Moldura no gráfico', changedStyle)),
      el('div.toolbar', { style: { margin: '4px 0 0' } },
        el('span.small.muted', 'Tamanhos rápidos:'),
        ...[['Coluna (≈8,5 cm)', 520, 340], ['Página (≈17 cm)', 1000, 460], ['Lado a lado', 600, 420], ['Slide 16:9', 1280, 720]].map(([l, w, h]) =>
          el('button.btn.small', { type: 'button', on: { click: () => { st.width = w; st.height = h; changedData(); } } }, l))));
  }

  function axesPanel() {
    const st = S.spec.style;
    return el('div.stack-s',
      el('div.fields.two',
        field('Título do eixo y', textInput(st, 'yTitle', () => { st.yAuto = false; changedStyle(); }, { placeholder: 'automático pela métrica' })),
        field('Título do eixo x', textInput(st, 'xTitle', changedStyle)),
        field('y mínimo', textInput(st, 'yMin', changedStyle, { placeholder: 'auto' })),
        field('y máximo', textInput(st, 'yMax', changedStyle, { placeholder: 'auto' })),
        field('Passo do eixo y', textInput(st, 'yStep', changedStyle, { placeholder: 'auto' })),
        field('Formato dos números (y)', selectInput(st, 'yFormat', [['auto', 'Automático'], ['fixed', 'Casas fixas'], ['sci', 'Científico (1,0E-03)'], ['percent', 'Percentual']], changedStyle)),
        field('Casas decimais (y)', numInput(st, 'yDigits', changedStyle, { min: 0, max: 10 })),
        field('Separador decimal', selectInput(st, 'decimal', [[',', 'Vírgula (0,002)'], ['.', 'Ponto (0.002)']], changedStyle)),
        field('Casas decimais (x)', numInput(st, 'xDigits', changedStyle, { min: 0, max: 10 })),
        field('Rotação dos rótulos x (°)', selectInput(st, 'xLabelRotate', [[0, '0'], [-30, '-30'], [-45, '-45'], [-60, '-60'], [-90, '-90']], changedStyle)),
        field('x mínimo', textInput(st, 'xMin', changedStyle, { placeholder: 'auto' })),
        field('x máximo', textInput(st, 'xMax', changedStyle, { placeholder: 'auto' })),
        field('Grade', selectInput(st, 'grid', [['y', 'Horizontal'], ['xy', 'Horizontal e vertical'], ['x', 'Vertical'], ['none', 'Sem grade']], changedStyle)),
        field('Traço da grade', selectInput(st, 'gridDash', [['solid', 'Contínuo'], ['dashed', 'Tracejado'], ['dotted', 'Pontilhado']], changedStyle)),
        field('Cor da grade', colorInput(st, 'gridColor', changedStyle)),
        field('Cor dos eixos', colorInput(st, 'axisColor', changedStyle))),
      el('div.checks',
        checkInput(st, 'yLog', 'Eixo y logarítmico', changedStyle),
        checkInput(st, 'xAsCategories', 'Eixo x com espaçamento uniforme (como no Excel)', changedStyle),
        checkInput(st, 'ticksOut', 'Marcas nos eixos', changedStyle)));
  }

  function legendPanel() {
    const st = S.spec.style;
    return el('div.stack-s',
      el('div.fields.two',
        field('Título do gráfico', textInput(st, 'title', changedStyle)),
        field('Tamanho do título', numInput(st, 'titleSize', changedStyle, { min: 6, max: 48 })),
        field('Cor do título', colorInput(st, 'titleColor', changedStyle)),
        field('Posição da legenda', selectInput(st, 'legend', [['bottom', 'Abaixo'], ['top', 'Acima'], ['right', 'À direita'], ['inside-tl', 'Dentro, sup. esquerdo'], ['inside-tr', 'Dentro, sup. direito'], ['inside-bl', 'Dentro, inf. esquerdo'], ['inside-br', 'Dentro, inf. direito'], ['none', 'Sem legenda']], changedStyle)),
        field('Colunas da legenda', numInput(st, 'legendCols', changedStyle, { min: 0, max: 12, placeholder: '0 = auto' })),
        field('Tamanho do texto da legenda', numInput(st, 'legendSize', changedStyle, { min: 0, max: 40, placeholder: 'auto' })),
        field('Fundo da legenda', colorInput(st, 'legendBg', changedStyle))),
      el('div.checks',
        checkInput(st, 'showTitle', 'Mostrar título', changedStyle),
        checkInput(st, 'legendBox', 'Caixa em volta da legenda', changedStyle)));
  }

  // ---------------------------------------------------------------- options + preview

  async function loadOptions() {
    const spec = S.spec;
    const counts = new Map();
    const firstWith = new Map();
    for (const c of spec.cases) {
      try {
        const cd = await caseData(c.path);
        for (const f of cd.csv) {
          counts.set(f.suffix, (counts.get(f.suffix) || 0) + 1);
          if (!firstWith.has(f.suffix)) firstWith.set(f.suffix, c.path);
        }
      } catch (e) { /* ignore */ }
    }
    S.fileOptions = [...counts.entries()].map(([suffix, count]) => ({ suffix, count })).sort((a, b) => b.count - a.count || a.suffix.localeCompare(b.suffix));
    if (spec.type !== 'cores' && spec.type !== 'slots' && spec.type !== 'slotblock' && S.fileOptions.length && !counts.has(spec.file)) {
      const want = spec.file;
      spec.file = (S.fileOptions.find((f) => f.suffix === want || f.suffix.endsWith('_' + want)) || S.fileOptions.find((f) => /BlockingProbability$/.test(f.suffix) && !/BitRate/.test(f.suffix)) || S.fileOptions[0]).suffix;
    }
    S.metricOptions = []; S.dimOptions = {}; S.lpOptions = []; S.lpErl = null;
    if (spec.type === 'scatter') {
      const X = spec.scatter;
      S.scatterOptions = { files: S.fileOptions.map((f) => f.suffix).filter((f) => f !== 'SlotUtilization' && f !== 'SlotBlocking'), metrics: {}, dims: {} };
      for (const f of [...new Set([X.xFile, X.yFile])]) {
        const first = spec.cases.find((c) => firstWith.get(f) === c.path);
        if (!first) continue;
        try {
          const p = await getCsv(first.path, f);
          if (!(p && p.ok)) continue;
          S.scatterOptions.metrics[f] = p.metrics;
          S.scatterOptions.dims[f] = {};
          for (const m of p.metrics) S.scatterOptions.dims[f][m] = p.dims.map((d, i) => ({ name: d, values: dimValues(p, m)[i] }));
          if (!S.lpOptions.length) {
            S.lpOptions = p.lps;
            const cd = await caseData(first.path);
            if (cd.loads) S.lpErl = Object.fromEntries(p.lps.map((l) => [l, cd.loads.base + l * cd.loads.inc]));
          }
        } catch (e) { /* ignore */ }
      }
      S.paramOptions = [];
      return;
    }
    if (spec.type === 'slots' || spec.type === 'slotline' || spec.type === 'slotblock') {
      const isBlock = spec.type === 'slotblock';
      S.slotOptions = { links: [], cores: [], nSlots: 0, causes: [] };
      for (const c of spec.cases) {
        try {
          const p = await getCsv(c.path, isBlock ? 'SlotBlocking' : 'SlotUtilization');
          if (!(p && p.ok && (isBlock ? p.slotblock : p.slot))) continue;
          if (isBlock) for (const k of p.causes) if (!S.slotOptions.causes.includes(k)) S.slotOptions.causes.push(k);
          for (const l of p.links) if (!S.slotOptions.links.includes(l)) S.slotOptions.links.push(l);
          for (const k of p.cores) if (!S.slotOptions.cores.includes(k)) S.slotOptions.cores.push(k);
          S.slotOptions.nSlots = Math.max(S.slotOptions.nSlots, p.nSlots);
          if (!S.lpOptions.length) {
            S.lpOptions = p.lps;
            const cd = await caseData(c.path);
            if (cd.loads) S.lpErl = Object.fromEntries(p.lps.map((l) => [l, cd.loads.base + l * cd.loads.inc]));
          }
        } catch (e) { /* ignore */ }
      }
      S.slotOptions.cores.sort((a, b) => Number(a) - Number(b));
      S.paramOptions = [];
      return;
    }
    const fileForOptions = spec.type === 'cores' ? spec.cores.utilFile : spec.file;
    const first = spec.cases.find((c) => firstWith.get(fileForOptions) === c.path) || spec.cases[0];
    if (first) {
      try {
        const p = await getCsv(first.path, fileForOptions);
        if (p && p.ok) {
          S.metricOptions = p.metrics;
          for (const m of p.metrics) S.dimOptions[m] = p.dims.map((d, i) => ({ name: d, values: dimValues(p, m)[i] }));
          S.lpOptions = p.lps;
          const cd = await caseData(first.path);
          if (cd.loads) S.lpErl = Object.fromEntries(p.lps.map((l) => [l, cd.loads.base + l * cd.loads.inc]));
          if (spec.type !== 'cores' && (!spec.metric || !p.byMetric.has(spec.metric))) {
            const t = TEMPLATES.find((x) => x.id === spec.template);
            spec.metric = t && t.metric && p.byMetric.has(t.metric) ? t.metric : p.metrics[0];
          }
          // automatic y-axis title follows the metric until the user types one
          if (spec.type === 'line' && spec.style.yAuto !== false && spec.metric && spec.style._yFor !== spec.metric) {
            if (spec.style._yFor !== undefined || !spec.style.yTitle) spec.style.yTitle = App.metricInfo.axisTitle(spec.metric);
            spec.style._yFor = spec.metric;
          }
        }
      } catch (e) { /* ignore */ }
    }
    S.paramOptions = spec.type === 'line' && spec.line.xMode === 'param' ? await differingParams(spec) : [];
    if (spec.type === 'line' && spec.line.xMode === 'param' && !S.paramOptions.includes(spec.line.paramPath)) spec.line.paramPath = S.paramOptions[0] || '';
  }

  let refreshSeq = 0;
  async function refresh(styleOnly) {
    if (!previewBox) return;
    const seq = ++refreshSeq;
    try {
      if (!styleOnly) {
        const before = JSON.stringify([S.fileOptions, S.metricOptions, S.lpOptions, S.paramOptions, S.spec.file, S.spec.metric, S.spec.line.paramPath, S.slotOptions, S.scatterOptions]);
        await loadOptions();
        if (seq !== refreshSeq) return;
        if (before !== JSON.stringify([S.fileOptions, S.metricOptions, S.lpOptions, S.paramOptions, S.spec.file, S.spec.metric, S.spec.line.paramPath, S.slotOptions, S.scatterOptions])) drawControls();
      }
      const model = await buildModel(S.spec);
      if (seq !== refreshSeq) return;
      const keys = model && !model.empty ? JSON.stringify(seriesSignature(model)) : '';
      S.model = model;
      if (keys !== S.seriesKeys) { S.seriesKeys = keys; drawSeriesPanel(); }
      drawPreview();
    } catch (e) {
      if (seq !== refreshSeq) return;
      clear(previewBox).append(el('div.notice.err', e.message || String(e)));
    }
  }

  function seriesSignature(m) {
    // keys only: the names follow the labels the user is typing, and a redraw would take the focus away
    if (m.kind === 'line' || m.kind === 'bars') return m.series.map((s) => s.key).concat(m.categories ? m.categories.map((c) => c.key) : []);
    if (m.kind === 'panels') return m.panels.map((p) => p.model.series.map((s) => s.key).join(',')).concat(m.items || []);
    if (m.kind === 'cores') return ['cores'];
    if (m.kind === 'slots') return ['slots'];
    if (m.kind === 'topo') return ['topo'];
    return [];
  }

  function drawPreview() {
    const m = S.model;
    clear(previewBox); clear(tableBox); clear(infoBox);
    if (!m || m.empty) {
      previewBox.append(el('div.notice.info', (m && m.empty) || 'Sem dados.'));
      S.svg = null; S.table = null;
      return;
    }
    // re-apply style (labels/colours may have changed without rebuilding data)
    applyStyle(m);
    const svgNode = R().render(m, S.spec.style);
    S.svg = svgNode;
    const frameEl = el('div.fig-canvas', { style: { maxWidth: S.spec.style.width + 'px' } });
    svgNode.setAttribute('style', 'width:100%;height:auto;display:block');
    frameEl.appendChild(svgNode);
    previewBox.append(frameEl, el('div.small.muted', { style: { marginTop: '6px' } },
      svgNode.getAttribute('width') + ' × ' + svgNode.getAttribute('height') + ' px' + (S.savedName ? ' · figura "' + S.savedName + '"' : ' · não salva')
      + (m.lpUsed && m.lpUsed.length ? ' · ponto de carga ' + [...new Set(m.lpUsed.map((l) => l + 1))].join('/') : '')));
    if (m.missing && m.missing.length) infoBox.append(el('div.notice.warn', 'Sem o arquivo nestes casos (ignorados): ' + m.missing.join(', ')));
    const t = tableRows(m, S.spec);
    S.table = t;
    if (t) {
      const fmt = (v) => (typeof v === 'number' ? R().fmtNumber(v, 'auto', 6, S.spec.style.decimal) : v);
      const rows = t.rows.slice(0, 300);
      const det = el('details.fsec', el('summary', 'Dados do gráfico (' + t.rows.length + ' linhas)'),
        el('div.table-wrap', el('table.grid.data', el('thead', el('tr', t.head.map((h) => el('th', h)))),
          el('tbody', rows.map((r) => el('tr', r.map((v) => el(typeof v === 'number' ? 'td.num' : 'td', fmt(v)))))))),
        t.rows.length > rows.length ? el('p.small.muted', { style: { margin: '6px 12px' } }, 'Mostrando ' + rows.length + ' de ' + t.rows.length + ' linhas; "Dados (CSV)" exporta todas.') : null,
        tableLegend(m, S.spec));
      const k = 'snets.fig.table.open.' + S.mode;
      let open = S.mode === 'case';
      try { const v = localStorage.getItem(k); if (v !== null) open = v === '1'; } catch (e) { /* ignore */ }
      det.open = open;
      det.addEventListener('toggle', () => { try { localStorage.setItem(k, det.open ? '1' : '0'); } catch (e) { /* ignore */ } });
      tableBox.append(det);
    }
    previewBox.append(aboutPanel(m, S.spec));
  }

  function applyStyle(m) {
    const spec = S.spec;
    const restyle = (list, kind, itemOf) => list.forEach((s, i) => { s.style = styleFor(spec, s.key, i, kind, itemOf ? itemOf(s) : undefined); });
    if (m.kind === 'line' && m.scatter) {
      const fmtR = (f) => (f && Number.isFinite(f.r) ? ' (r = ' + R().fmtNumber(f.r, 'fixed', 2, spec.style.decimal) + ')' : '');
      let k = 0;
      const colorOf = {};
      for (const s of m.series) {
        if (s.trendOf === undefined && s.key !== 'trend|all') {
          s.style = styleFor(spec, s.key, k++, 'scatter');
          colorOf[s.key] = s.style.color;
          const c = spec.cases.find((x) => x.path === s.key);
          s.name = (c ? caseLabel(c) : s.name) + (spec.scatter.trend && !spec.scatter.pooled ? fmtR(s.fit) : '');
        }
      }
      k = 0;
      for (const s of m.series) {
        if (s.trendOf !== undefined) {
          const o = spec.seriesStyle[s.key] || {};
          s.style = styleFor(spec, s.key, k++, 'trend');
          if (!o.color && colorOf[s.trendOf]) s.style.color = colorOf[s.trendOf];
          const c = spec.cases.find((x) => x.path === s.trendOf);
          s.name = 'Ajuste · ' + (c ? caseLabel(c) : '');
        } else if (s.key === 'trend|all') {
          s.style = styleFor(spec, s.key, 0, 'trend');
          if (!(spec.seriesStyle[s.key] || {}).color) s.style.color = '#404040';
          s.name = 'Ajuste (todos os casos)' + fmtR(m.pooledFit);
        }
      }
    } else if (m.kind === 'line' && m.styleKind === 'slotline') {
      restyle(m.series, 'slotline');
      for (const s of m.series) {
        const mm = /^(.*)\|core=(.*)$/.exec(s.key);
        if (!mm) continue;
        const cc = spec.cases.find((x) => x.path === mm[1]);
        const lab = spec.labels[mm[2]] !== undefined && spec.labels[mm[2]] !== '' ? spec.labels[mm[2]] : 'Núcleo ' + mm[2];
        s.name = (spec.cases.length > 1 && cc ? caseLabel(cc) + ' · ' : '') + lab;
      }
    } else if (m.kind === 'line') {
      restyle(m.series, 'line');
      // names may depend on labels
      const multiCase = spec.cases.length > 1;
      for (const s of m.series) {
        const c = spec.cases.find((x) => x.path === s.key);
        if (c) s.name = caseLabel(c);
        const mm = /^(.*)\|m:(.*)$/.exec(s.key) || /^(.*)\|[^|=]+=(.*)$/.exec(s.key);
        if (mm) { const cc = spec.cases.find((x) => x.path === mm[1]); s.name = (multiCase && cc ? caseLabel(cc) + ' · ' : '') + itemLabel(spec, mm[2]); }
        const pm = /^param\|[dm]:(.*)$/.exec(s.key);
        if (pm) s.name = itemLabel(spec, pm[1]);
      }
      if (spec.line.xMode === 'lp1') m.xLabel = null;
    } else if (m.kind === 'bars' || (m.kind === 'panels' && m.panels.some((p) => p.model.series.some((x) => x.key.startsWith('sd:'))))) {
      const styleBars = (mm) => {
        restyle(mm.series, 'bar', (x) => (x.key.startsWith('i:') ? x.key.slice(2) : x.key.startsWith('sd:') ? x.key.slice(3) : undefined));
        for (const x of mm.series) {
          const c = spec.cases.find((y) => y.path === x.key);
          x.name = c ? caseLabel(c) : itemLabel(spec, x.key.slice(x.key.startsWith('sd:') ? 3 : 2));
        }
        for (const c of mm.categories) {
          const cc = spec.cases.find((y) => y.path === c.key);
          c.label = cc ? caseLabel(cc) : spec.bars.seriesDim && spec.bars.dim === 'Core' && /^\d+$/.test(c.key) && !(spec.labels[c.key]) ? 'Núcleo ' + c.key : itemLabel(spec, c.key);
        }
      };
      if (m.kind === 'bars') styleBars(m); else m.panels.forEach((p) => styleBars(p.model));
    } else if (m.kind === 'panels') {
      m.panels.forEach((p, i) => {
        const s = p.model.series[0];
        s.style = styleFor(spec, s.key, i, 'bar');
        const c = spec.cases.find((x) => x.path === s.key);
        if (c) { p.title = caseLabel(c); s.name = p.title; }
        for (const cat of p.model.categories) cat.label = itemLabel(spec, cat.key);
      });
    } else if (m.kind === 'topo') {
      m.legendTitle = spec.labels.__heat || App.metricInfo.axisTitle(spec.metric) || spec.metric;
    } else if (m.kind === 'slots') {
      m.legendTitle = m.block ? slotBlockLegendTitle(spec, m.block) : spec.labels.__slot || 'Ocupação do slot';
      if (m.summaryHeads) m.summaryHeads = [spec.labels.__sumA || 'Bloq./req.', spec.labels.__sumB || '%'];
    } else if (m.kind === 'cores') {
      for (const d of m.diagrams) { const c = spec.cases.find((x) => x.path === d.path); if (c) d.title = caseLabel(c); }
      m.blockLabel = spec.labels.__block || 'Bloqueio do núcleo';
      m.utilLabel = spec.labels.__util || 'Uso do núcleo';
    }
  }

  // ================================================================ entry points

  /** Opens the studio with the given cases (e.g. from the Results tab). */
  function openWith(paths, opts) {
    opts = opts || {};
    S.mode = 'page'; S.casePath = null;
    const st = S.spec && S.spec.version ? S.spec : null;
    S.spec = newSpec(opts.template || 'custom', paths);
    if (opts.file) S.spec.file = opts.file;
    if (opts.metric) S.spec.metric = opts.metric;
    if (opts.xMode) S.spec.line.xMode = opts.xMode;
    if (opts.yTitle !== undefined) S.spec.style.yTitle = opts.yTitle;
    if (opts.xMode && opts.xMode !== 'erl') S.spec.style.xTitle = opts.xMode === 'param' ? '' : 'Ponto de carga';
    if (st && st.style) { S.spec.style.decimal = st.style.decimal; S.spec.style.palette = st.style.palette; S.spec.style.font = st.style.font; }
    S.savedName = '';
    persistDraft();
    App.showFigures();
  }

  function invalidate(path) {
    caseCache.delete(path);
    for (const k of [...csvCache.keys()]) if (k.startsWith(path + '|')) csvCache.delete(k);
  }

  App.figures = { render, renderInto, openWith, invalidate, parseCsv, buildModel, newSpec, TEMPLATES, _state: S };
})(window.App);
