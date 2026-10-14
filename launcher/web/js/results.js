/*
 * Results tab: reads the CSVs written by SNetS
 *   Metrics, LoadPoint, <dimension columns...>, " ", rep0, rep1, ...
 * and plots metric × load point with the mean over replications and a 95 %
 * confidence interval (Student t). Several cases can be overlaid.
 */
(function (App) {
  'use strict';
  const { el, clear, svg, fmtNum, fmtSci } = App;

  const T95 = [12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179, 2.160, 2.145,
    2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042];
  const tCrit = (df) => (df < 1 ? NaN : df <= 30 ? T95[df - 1] : 1.96);
  const MAX_SERIES = 8;

  const cache = new Map();
  const ui = {
    csv: null, metric: null, filters: {}, seriesBy: '', xMode: 'lp', yLog: false, compare: [],
    paramPath: '', paramLp: 0,
  };

  /** "LoadPoint,Replication,<metric columns>" files such as QKDStatistics.csv. */
  function parseWideCsv(lines, header) {
    const metrics = header.slice(2).map((h) => h.trim()).filter(Boolean);
    const by = new Map();
    for (let i = 1; i < lines.length; i++) {
      if (!lines[i] || !lines[i].trim()) continue;
      const c = lines[i].split(',');
      const lp = Number(c[0]), rep = Number(c[1]);
      if (!Number.isFinite(lp) || !Number.isFinite(rep)) continue;
      metrics.forEach((m, j) => {
        const v = Number(c[j + 2]);
        const key = m + '|' + lp;
        if (!by.has(key)) by.set(key, { metric: m, lp, dims: [], reps: [] });
        if (Number.isFinite(v)) by.get(key).reps[rep] = v;
      });
    }
    const rows = [...by.values()].map((r) => ({ ...r, reps: r.reps.filter((v) => v !== undefined) }));
    return { ok: true, dims: [], rows, metrics, wide: true };
  }

  function parseCsv(text) {
    const lines = text.split(/\r?\n/);
    const header = (lines[0] || '').split(',');
    if ((header[0] || '').trim().toLowerCase() === 'loadpoint' && (header[1] || '').trim().toLowerCase() === 'replication') {
      return parseWideCsv(lines, header);
    }
    const lpIdx = header.findIndex((h) => h.trim().toLowerCase() === 'loadpoint');
    const blank = header.findIndex((h, i) => i > lpIdx && h.trim() === '');
    if (header[0].trim().toLowerCase() !== 'metrics' || lpIdx < 0) return { ok: false, header, lines };
    const repStart = blank >= 0 ? blank + 1 : header.findIndex((h) => /^rep\d+/i.test(h.trim()));
    const dimEnd = blank >= 0 ? blank : repStart;
    const dims = header.slice(lpIdx + 1, dimEnd).map((h) => h.trim());
    const rows = [];
    const metrics = [];
    const seen = new Set();
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line || !line.trim()) continue;
      const c = line.split(',');
      if (c[0].trim().toLowerCase() === 'metrics') continue;
      const lp = Number(c[lpIdx]);
      if (!Number.isFinite(lp)) continue;
      const metric = c[0].trim();
      if (!seen.has(metric)) { seen.add(metric); metrics.push(metric); }
      rows.push({
        metric, lp,
        dims: c.slice(lpIdx + 1, dimEnd).map((x) => x.trim()),
        reps: c.slice(repStart).map((x) => Number(x)).filter((x) => Number.isFinite(x)),
      });
    }
    return { ok: true, dims, rows, metrics, nReps: header.length - repStart };
  }

  function stats(values) {
    const n = values.length;
    if (!n) return { mean: NaN, ci: NaN, n };
    const mean = values.reduce((a, b) => a + b, 0) / n;
    if (n < 2) return { mean, ci: NaN, n };
    const sd = Math.sqrt(values.reduce((a, b) => a + (b - mean) * (b - mean), 0) / (n - 1));
    return { mean, ci: tCrit(n - 1) * sd / Math.sqrt(n), n, sd };
  }

  async function loadCsv(casePath, name) {
    const key = casePath + '|' + name;
    if (cache.has(key)) return cache.get(key);
    const d = await App.api.csv(casePath, name);
    const parsed = App.figures ? App.figures.parseCsv(d.text) : parseCsv(d.text);
    cache.set(key, parsed);
    return parsed;
  }

  function suffixOf(caseName, csvName) {
    return csvName.startsWith(caseName + '_') ? csvName.slice(caseName.length + 1) : csvName;
  }

  async function loadsOf(casePath) {
    let traffic;
    if (casePath === App.state.casePath) traffic = App.state.base.traffic;
    else {
      const c = ui.compare.find((x) => x.path === casePath);
      traffic = c && c.traffic;
    }
    const l = App.validate.trafficLoad(traffic);
    return l.generators ? l : null;
  }

  // ------------------------------------------------------------------ render

  function render() {
    const st = App.state;
    const root = el('div.stack');
    const csvs = (st.caseData && st.caseData.csv) || [];
    if (!csvs.length) {
      root.appendChild(el('section.panel', el('h2', 'Resultados'),
        el('p.lead', 'Este caso ainda não tem arquivos CSV. Execute a simulação na aba Executar; os CSVs são gravados na pasta do caso, um por métrica ativa.')));
      return root;
    }
    // Parte 24: the Results tab is the chart studio, opened on this case.
    const studio = el('div.results-studio');
    root.appendChild(studio);
    App.figures.renderInto(studio, 'case', st.casePath).catch((e) => App.toast(e.message, 'err'));
    return root;
    // eslint-disable-next-line no-unreachable
    if (!ui.csv || !csvs.find((c) => c.name === ui.csv) || ui.casePath !== st.casePath) {
      const pref = csvs.find((c) => /_BlockingProbability\.csv$/.test(c.name)) || csvs[0];
      Object.assign(ui, { csv: pref.name, metric: null, filters: {}, seriesBy: '', compare: [], casePath: st.casePath });
    }
    const controls = el('section.panel');
    const chartPanel = el('section.panel');
    root.append(controls, chartPanel);
    draw(controls, chartPanel).catch((e) => App.toast(e.message, 'err'));
    return root;
  }

  async function draw(controls, chartPanel) {
    const st = App.state;
    const csvs = st.caseData.csv;
    const caseName = App.caseNameOf(st.casePath);
    clear(controls).appendChild(el('p.muted', 'Carregando ' + ui.csv + '…'));
    const data = await loadCsv(st.casePath, ui.csv);
    clear(controls);

    const fileSel = el('select.input', csvs.map((c) => el('option', { value: c.name }, suffixOf(caseName, c.name) + '  (' + App.fmtBytes(c.size) + ')')));
    fileSel.value = ui.csv;
    fileSel.addEventListener('change', () => { Object.assign(ui, { csv: fileSel.value, metric: null, filters: {}, seriesBy: '' }); draw(controls, chartPanel); });
    controls.append(el('h2', 'Resultados'),
      el('p.lead', 'Média das replicações com intervalo de confiança de 95 % (t de Student). Arquivos gerados em ' + App.fmtDate(Math.max(...csvs.map((c) => c.mtime))) + '.'));

    if (!data.ok) {
      controls.append(el('div.fields', el('div.field', el('div.flabel', 'Arquivo'), fileSel)),
        el('div.notice.warn', { style: { marginTop: '12px' } }, 'Formato de CSV não reconhecido para gráfico (sem colunas Metrics/LoadPoint). Primeiras linhas:'),
        el('div.diff', { style: { marginTop: '8px' } }, data.lines.slice(0, 40).map((l) => el('div.l', l))));
      clear(chartPanel);
      return;
    }

    if (!ui.metric || !data.metrics.includes(ui.metric)) { ui.metric = data.metrics[0]; ui.filters = {}; }
    const metricSel = el('select.input', data.metrics.map((m) => el('option', { value: m }, m)));
    metricSel.value = ui.metric;
    metricSel.addEventListener('change', () => { ui.metric = metricSel.value; ui.filters = {}; ui.seriesBy = ''; draw(controls, chartPanel); });

    const mrows = data.rows.filter((r) => r.metric === ui.metric);
    const dimValues = data.dims.map((_, i) => [...new Set(mrows.map((r) => r.dims[i]))]);
    data.dims.forEach((d, i) => {
      const vals = dimValues[i];
      if (!(d in ui.filters) || !vals.includes(ui.filters[d])) ui.filters[d] = vals.includes('all') ? 'all' : vals[0];
    });
    const variableDims = data.dims.filter((_, i) => dimValues[i].length > 1);
    if (ui.seriesBy && !variableDims.includes(ui.seriesBy)) ui.seriesBy = '';

    const filterFields = data.dims.map((d, i) => {
      if (dimValues[i].length <= 1) return null;
      const sel = el('select.input', dimValues[i].map((v) => el('option', { value: v }, v)));
      sel.value = ui.filters[d];
      sel.disabled = ui.seriesBy === d;
      sel.addEventListener('change', () => { ui.filters[d] = sel.value; drawChart(chartPanel, data); });
      return el('div.field', el('div.flabel', d), sel);
    });
    const seriesSel = el('select.input', el('option', { value: '' }, 'Caso'), variableDims.map((d) => el('option', { value: d }, d)));
    seriesSel.value = ui.seriesBy;
    seriesSel.addEventListener('change', () => { ui.seriesBy = seriesSel.value; draw(controls, chartPanel); });

    const seg = (opts, cur, onPick) => el('div.seg', opts.map(([v, label]) => el('button', {
      type: 'button', 'aria-pressed': String(cur === v), on: { click: () => onPick(v) },
    }, label)));
    const xSeg = seg([['lp', 'Ponto de carga'], ['erl', 'Carga (Erlang)'], ['param', 'Parâmetro dos casos']], ui.xMode, (v) => { ui.xMode = v; draw(controls, chartPanel); });
    const ySeg = seg([[false, 'Linear'], [true, 'Log']], ui.yLog, (v) => { ui.yLog = v; draw(controls, chartPanel); });

    // comparison
    const others = App.allCases().filter((c) => c.path !== st.casePath && c.csvCount > 0 && !ui.compare.find((x) => x.path === c.path));
    const cmpSel = el('select.input', el('option', { value: '' }, 'Comparar com outro caso…'), others.map((c) => el('option', { value: c.path }, c.path)));
    cmpSel.addEventListener('change', async () => {
      const p = cmpSel.value;
      if (!p) return;
      try {
        await addCompare(p, caseName, false);
        draw(controls, chartPanel);
      } catch (e) { App.toast(e.message, 'err'); cmpSel.value = ''; }
    });
    const parent = st.casePath.split('/').slice(0, -1).join('/');
    const siblings = App.allCases().filter((c) => c.path !== st.casePath && c.csvCount > 0
      && c.path.split('/').slice(0, -1).join('/') === parent && !ui.compare.find((x) => x.path === c.path));
    const addSiblings = siblings.length ? el('button.btn.small', {
      type: 'button', title: 'Adiciona todos os casos da mesma pasta (ex.: uma varredura)',
      on: { click: async () => {
        let n = 0;
        for (const c of siblings) { try { if (await addCompare(c.path, caseName, true)) n++; } catch (e) { /* skip */ } }
        App.toast(n + ' caso(s) da pasta adicionados', 'ok');
        draw(controls, chartPanel);
      } },
    }, '+ Casos da mesma pasta (' + siblings.length + ')') : null;

    // parameter axis (sweeps): config values that differ between the compared cases
    let paramFields = null;
    if (ui.xMode === 'param') {
      const opts = differingParams();
      if (!opts.includes(ui.paramPath)) ui.paramPath = opts[0] || '';
      const pSel = el('select.input', opts.length ? opts.map((o) => el('option', { value: o }, o))
        : el('option', { value: '' }, 'adicione casos com configurações diferentes'));
      pSel.value = ui.paramPath;
      pSel.addEventListener('change', () => { ui.paramPath = pSel.value; drawChart(chartPanel, data); });
      const lps = [...new Set(mrows.map((r) => r.lp))].sort((a, b) => a - b);
      if (!lps.includes(ui.paramLp)) ui.paramLp = lps[0] || 0;
      const lSel = el('select.input', lps.map((l) => el('option', { value: String(l) }, 'ponto ' + l)));
      lSel.value = String(ui.paramLp);
      lSel.addEventListener('change', () => { ui.paramLp = Number(lSel.value); drawChart(chartPanel, data); });
      paramFields = [el('div.field', el('div.flabel', 'Parâmetro no eixo x'), pSel),
        el('div.field', el('div.flabel', 'Ponto de carga'), lSel)];
    }

    controls.append(
      el('div.filters',
        el('div.field', el('div.flabel', 'Arquivo'), fileSel),
        el('div.field', el('div.flabel', 'Métrica'), metricSel),
        el('div.field', el('div.flabel', 'Uma linha por'), seriesSel),
        ...filterFields.filter(Boolean), ...(paramFields || [])),
      el('div.toolbar', { style: { marginBottom: 0 } }, el('span.small.muted', 'Eixo x'), xSeg, el('span.small.muted', 'Eixo y'), ySeg,
        el('span.spacer'), addSiblings, el('div', { style: { minWidth: '260px' } }, cmpSel)),
      ui.compare.length ? el('div.chart-legend', { style: { padding: '10px 0 0' } }, el('span.muted', 'Casos comparados:'),
        ui.compare.map((c) => el('span.tag.file', App.caseNameOf(c.path), el('button.rm', {
          type: 'button', title: 'Remover', on: { click: () => { ui.compare = ui.compare.filter((x) => x.path !== c.path); draw(controls, chartPanel); } },
        }, ' ✕')))) : null);

    await drawChart(chartPanel, data);
  }

  async function addCompare(p, caseName, quiet) {
    if (ui.compare.find((x) => x.path === p) || p === App.state.casePath) return false;
    const cd = await App.api.readCase(p);
    const name = App.caseNameOf(p) + '_' + suffixOf(caseName, ui.csv);
    if (!cd.csv.find((c) => c.name === name)) {
      if (quiet) return false;
      throw new Error(p + ' não tem o arquivo ' + name);
    }
    let traffic = null;
    try { traffic = JSON.parse(cd.files.traffic); } catch (e) { /* ignore */ }
    const files = {};
    for (const f of ['network', 'physicalLayer', 'qkdPhysicalLayer', 'simulation', 'others']) {
      try { files[f] = JSON.parse(cd.files[f]); } catch (e) { /* ignore */ }
    }
    ui.compare.push({ path: p, csv: name, traffic, files });
    return true;
  }

  function flatten(obj, prefix, out) {
    if (obj === null || obj === undefined) return out;
    if (typeof obj !== 'object') { out[prefix] = obj; return out; }
    if (Array.isArray(obj)) {
      if (obj.length > 12 || obj.some((v) => v !== null && typeof v === 'object')) return out;
      out[prefix] = obj.join(',');
      return out;
    }
    for (const k of Object.keys(obj)) flatten(obj[k], prefix ? prefix + '.' + k : k, out);
    return out;
  }

  function configOf(path) {
    if (path === App.state.casePath) return App.state.base;
    const c = ui.compare.find((x) => x.path === path);
    return c ? c.files || {} : {};
  }

  function paramValue(path, param) {
    const i = param.indexOf(':');
    const file = param.slice(0, i), key = param.slice(i + 1);
    const flat = flatten(configOf(path)[file], '', {});
    return flat[key];
  }

  /** "file:dotted.path" of numeric configuration values that differ among the cases being compared. */
  function differingParams() {
    const paths = [App.state.casePath].concat(ui.compare.map((c) => c.path));
    if (paths.length < 2) return [];
    const out = [];
    for (const f of ['network', 'physicalLayer', 'qkdPhysicalLayer', 'simulation', 'others']) {
      const flats = paths.map((p) => flatten(configOf(p)[f], '', {}));
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

  async function collectSeries(data) {
    if (ui.xMode === 'param') return collectParamSeries(data);
    const st = App.state;
    const out = [];
    const sources = [{ path: st.casePath, csv: ui.csv }].concat(ui.compare);
    for (const src of sources) {
      const d = src.path === st.casePath ? data : await loadCsv(src.path, src.csv);
      if (!d.ok) continue;
      const loads = await loadsOf(src.path);
      const rows = d.rows.filter((r) => r.metric === ui.metric && d.dims.every((dim, i) =>
        dim === ui.seriesBy || r.dims[i] === ui.filters[dim]));
      const groups = new Map();
      for (const r of rows) {
        const k = ui.seriesBy ? r.dims[d.dims.indexOf(ui.seriesBy)] : '';
        if (!groups.has(k)) groups.set(k, new Map());
        const g = groups.get(k);
        if (!g.has(r.lp)) g.set(r.lp, []);
        g.get(r.lp).push(...r.reps);
      }
      for (const [k, g] of groups) {
        const pts = [...g.entries()].sort((a, b) => a[0] - b[0]).map(([lp, vals]) => {
          const s = stats(vals);
          const x = ui.xMode === 'erl' && loads ? loads.base + lp * loads.inc : lp;
          return { lp, key: lp, x, ...s };
        });
        const caseLabel = App.caseNameOf(src.path);
        const name = ui.seriesBy ? (sources.length > 1 ? caseLabel + ' · ' : '') + ui.seriesBy + ' ' + k : caseLabel;
        out.push({ name, path: src.path, points: pts, removable: src.path !== st.casePath });
      }
    }
    return out;
  }

  /** One point per case: x = configuration value, y = metric at the chosen load point. */
  async function collectParamSeries(data) {
    const st = App.state;
    const sources = [{ path: st.casePath, csv: ui.csv }].concat(ui.compare);
    const groups = new Map();
    if (!ui.paramPath) return [];
    for (const src of sources) {
      const d = src.path === st.casePath ? data : await loadCsv(src.path, src.csv);
      if (!d.ok) continue;
      const xv = paramValue(src.path, ui.paramPath);
      const x = typeof xv === 'number' ? xv : App.parseNumber(String(xv));
      if (!Number.isFinite(x)) continue;
      const rows = d.rows.filter((r) => r.metric === ui.metric && r.lp === ui.paramLp && d.dims.every((dim, i) =>
        dim === ui.seriesBy || r.dims[i] === ui.filters[dim]));
      for (const r of rows) {
        const k = ui.seriesBy ? r.dims[d.dims.indexOf(ui.seriesBy)] : '';
        if (!groups.has(k)) groups.set(k, []);
        groups.get(k).push({ x, key: x, label: App.caseNameOf(src.path), reps: r.reps });
      }
    }
    const out = [];
    for (const [k, list] of groups) {
      const byX = new Map();
      for (const p of list) {
        if (!byX.has(p.x)) byX.set(p.x, { x: p.x, key: p.key, labels: [], reps: [] });
        byX.get(p.x).labels.push(p.label);
        byX.get(p.x).reps.push(...p.reps);
      }
      const points = [...byX.values()].sort((a, b) => a.x - b.x).map((p) => ({ x: p.x, key: p.key, lp: p.key, label: p.labels.join(', '), ...stats(p.reps) }));
      out.push({ name: ui.seriesBy ? ui.seriesBy + ' ' + k : ui.metric, points });
    }
    return out;
  }

  async function drawChart(panel, data) {
    clear(panel);
    let series = await collectSeries(data);
    let note = null;
    if (series.length > MAX_SERIES) {
      note = el('div.notice.warn', { style: { marginBottom: '8px' } }, `${series.length} séries — mostrando as ${MAX_SERIES} primeiras. Filtre mais ou escolha outra divisão.`);
      series = series.slice(0, MAX_SERIES);
    }
    const css = getComputedStyle(document.documentElement);
    const tok = (n) => css.getPropertyValue(n).trim();
    series.forEach((s, i) => { s.color = tok('--series-' + (i + 1)); });
    const colors = { surface: tok('--chart-surface'), text: tok('--fg-2'), title: tok('--fg'), grid: tok('--grid'), axis: tok('--border-strong') };

    const box = el('div.chart-box');
    const legend = el('div.chart-legend');
    if (series.length >= 2) {
      for (const s of series) {
        legend.appendChild(el('span', el('span.sw', { style: { background: s.color } }), s.name,
          s.removable && !ui.seriesBy ? el('button.rm', {
            type: 'button', title: 'Remover da comparação',
            on: { click: () => { ui.compare = ui.compare.filter((c) => c.path !== s.path); App.renderTab(); } },
          }, '✕') : null));
      }
    }
    const loadsMissing = ui.xMode === 'erl' && !(await loadsOf(App.state.casePath));
    const paramMode = ui.xMode === 'param';
    const xTitle = paramMode ? (ui.paramPath || 'parâmetro') : ui.xMode === 'erl' && !loadsMissing ? 'Carga (Erlang)' : 'Ponto de carga';
    const title = ui.metric + filterSummary(data) + (paramMode ? ' — ponto de carga ' + ui.paramLp : '');
    if (!series.some((s) => s.points.length)) {
      panel.append(el('div.notice.info', paramMode
        ? 'Escolha casos com valores diferentes de algum parâmetro (botão "+ Casos da mesma pasta" ou "Comparar com outro caso").'
        : 'Nenhum valor para os filtros escolhidos.'));
      return;
    }
    const host = el('div', { style: { position: 'relative' } });
    box.append(legend, host);
    panel.append(note, box);
    const width = Math.max(340, host.clientWidth || panel.clientWidth - 40 || 800);
    const chart = buildSvg(series, { width, height: 400, xTitle, title, log: ui.yLog, colors });
    host.appendChild(chart.svg);
    attachHover(host, chart, series);

    // table + exports
    const lps = [...new Set(series.flatMap((s) => s.points.map((p) => p.key)))].sort((a, b) => a - b);
    const firstCol = paramMode ? 'Casos' : 'Ponto';
    const table = el('table.grid.data',
      el('thead', el('tr', el('th', firstCol), el('th.num', xTitle === 'Ponto de carga' ? '' : xTitle),
        series.map((s) => el('th.num', s.name)))),
      el('tbody', lps.map((lp) => {
        const xs = series.map((s) => s.points.find((p) => p.key === lp));
        const x0 = xs.find(Boolean);
        return el('tr', el('td', paramMode ? (x0 && x0.label) || '' : String(lp)),
          el('td.num', xTitle === 'Ponto de carga' ? '' : fmtNum(x0 && x0.x, 3)),
          xs.map((p) => el('td.num', p ? fmtSci(p.mean) + (Number.isFinite(p.ci) ? ' ± ' + fmtSci(p.ci) : '') : '—')));
      })));
    const csvOut = () => {
      const head = [paramMode ? 'cases' : 'LoadPoint', paramMode ? ui.paramPath : 'x'].concat(series.flatMap((s) => [s.name + ' mean', s.name + ' ci95', s.name + ' n']));
      const rows = lps.map((lp) => {
        const xs = series.map((s) => s.points.find((p) => p.key === lp));
        const x0 = xs.find(Boolean);
        return [paramMode ? (x0 && x0.label) || '' : lp, x0 ? x0.x : ''].concat(xs.flatMap((p) => p ? [p.mean, Number.isFinite(p.ci) ? p.ci : '', p.n] : ['', '', '']));
      });
      return [head].concat(rows).map((r) => r.map((v) => (/[",]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : v)).join(',')).join('\n');
    };
    const fname = (ui.metric || 'grafico').replace(/[^\w-]+/g, '_');
    panel.append(
      loadsMissing ? el('div.notice.warn', { style: { marginTop: '8px' } }, 'Não foi possível calcular a carga em Erlang a partir do arquivo traffic; usando o índice do ponto.') : null,
      el('div.toolbar', { style: { margin: '12px 0' } },
        el('button.btn.small', { type: 'button', on: { click: () => App.downloadText(fname + '.svg', serializeSvg(chart.svg, colors.surface), 'image/svg+xml') } }, 'Baixar SVG'),
        el('button.btn.small', { type: 'button', on: { click: () => downloadPng(chart.svg, fname + '.png', colors.surface) } }, 'Baixar PNG'),
        el('button.btn.small', { type: 'button', on: { click: () => App.copyText(csvOut()) } }, 'Copiar tabela (CSV)'),
        el('button.btn.small', { type: 'button', on: { click: () => App.downloadText(fname + '.csv', csvOut(), 'text/csv') } }, 'Baixar tabela'),
        el('span.spacer'),
        el('button.btn.small.primary', { type: 'button', title: 'Abre estes casos no Estúdio de gráficos (cores, traços, marcadores, tamanho, exportação)', on: { click: () => {
          const paths = [App.state.casePath].concat(ui.compare.map((c) => c.path));
          const suffix = suffixOf(App.caseNameOf(App.state.casePath), ui.csv).replace(/\.csv$/i, '');
          App.figures.openWith(paths, { file: suffix, metric: ui.metric, xMode: ui.xMode === 'erl' ? 'erl' : ui.xMode === 'param' ? 'param' : 'lp1', yTitle: ui.metric });
        } } }, 'Abrir no Estúdio de gráficos')),
      el('div.table-wrap', table));
  }

  function filterSummary(data) {
    const parts = data.dims.filter((d) => d !== ui.seriesBy && ui.filters[d] && ui.filters[d] !== 'all' && ui.filters[d] !== '-')
      .map((d) => d + ' = ' + ui.filters[d]);
    return parts.length ? ' (' + parts.join(', ') + ')' : '';
  }

  // ------------------------------------------------------------------ svg chart

  function niceTicks(min, max, count) {
    if (min === max) { min -= 1; max += 1; }
    const span = max - min;
    const step0 = span / Math.max(1, count);
    const mag = Math.pow(10, Math.floor(Math.log10(step0)));
    const norm = step0 / mag;
    const step = (norm >= 5 ? 10 : norm >= 2 ? 5 : norm >= 1 ? 2 : 1) * mag;
    const start = Math.floor(min / step) * step;
    const ticks = [];
    for (let v = start; v <= max + step * 0.5; v += step) ticks.push(+v.toPrecision(12));
    return ticks;
  }

  function buildSvg(series, o) {
    const m = { l: 70, r: 18, t: 30, b: 48 };
    const W = o.width, H = o.height, iw = W - m.l - m.r, ih = H - m.t - m.b;
    const pts = series.flatMap((s) => s.points);
    const xs = pts.map((p) => p.x);
    let xmin = Math.min(...xs), xmax = Math.max(...xs);
    if (xmin === xmax) { xmin -= 1; xmax += 1; }
    const xpad = (xmax - xmin) * 0.04;
    xmin -= xpad; xmax += xpad;
    const lo = pts.map((p) => p.mean - (Number.isFinite(p.ci) ? p.ci : 0));
    const hi = pts.map((p) => p.mean + (Number.isFinite(p.ci) ? p.ci : 0));
    let ymin, ymax, yticks, ys;
    if (o.log) {
      const pos = lo.concat(pts.map((p) => p.mean)).filter((v) => v > 0);
      const posHi = hi.filter((v) => v > 0);
      ymin = pos.length ? Math.pow(10, Math.floor(Math.log10(Math.min(...pos)))) : 1e-3;
      ymax = posHi.length ? Math.pow(10, Math.ceil(Math.log10(Math.max(...posHi)))) : 1;
      if (ymin === ymax) ymax = ymin * 10;
      yticks = [];
      for (let e = Math.log10(ymin); e <= Math.log10(ymax) + 1e-9; e++) yticks.push(Math.pow(10, e));
      ys = (v) => m.t + ih - (Math.log10(Math.max(v, ymin)) - Math.log10(ymin)) / (Math.log10(ymax) - Math.log10(ymin)) * ih;
    } else {
      ymin = Math.min(0, ...lo.filter(Number.isFinite));
      ymax = Math.max(...hi.filter(Number.isFinite));
      if (!Number.isFinite(ymax)) ymax = 1;
      yticks = niceTicks(ymin, ymax, 5);
      ymin = Math.min(ymin, yticks[0]); ymax = Math.max(ymax, yticks[yticks.length - 1]);
      ys = (v) => m.t + ih - (v - ymin) / (ymax - ymin || 1) * ih;
    }
    const xsF = (v) => m.l + (v - xmin) / (xmax - xmin) * iw;
    const c = o.colors;
    const root = svg('svg', { class: 'chart', viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img', 'aria-label': o.title, 'font-family': 'Segoe UI, system-ui, sans-serif' });
    root.appendChild(svg('text', { x: m.l, y: 16, class: 'title', fill: c.title, 'font-size': 13, 'font-weight': 600, text: o.title }));
    const grid = svg('g', { class: 'grid' });
    for (const t of yticks) {
      const y = ys(t);
      grid.appendChild(svg('line', { x1: m.l, x2: m.l + iw, y1: y, y2: y, stroke: c.grid, 'stroke-width': 1 }));
      root.appendChild(svg('text', { x: m.l - 8, y: y + 4, 'text-anchor': 'end', fill: c.text, 'font-size': 11.5, text: fmtSci(t) }));
    }
    root.insertBefore(grid, root.firstChild.nextSibling);
    const xticks = [...new Set(xs)].sort((a, b) => a - b);
    const xt = xticks.length <= 12 ? xticks : niceTicks(Math.min(...xs), Math.max(...xs), 8);
    for (const t of xt) {
      root.appendChild(svg('text', { x: xsF(t), y: m.t + ih + 18, 'text-anchor': 'middle', fill: c.text, 'font-size': 11.5, text: fmtNum(t, 1) }));
    }
    root.appendChild(svg('line', { x1: m.l, x2: m.l + iw, y1: m.t + ih, y2: m.t + ih, stroke: c.axis, 'stroke-width': 1 }));
    root.appendChild(svg('text', { x: m.l + iw / 2, y: H - 8, 'text-anchor': 'middle', fill: c.text, 'font-size': 12, text: o.xTitle }));

    for (const s of series) {
      const g = svg('g');
      const valid = s.points.filter((p) => Number.isFinite(p.mean) && (!o.log || p.mean > 0));
      // CI whiskers
      for (const p of valid) {
        if (!Number.isFinite(p.ci) || p.ci === 0) continue;
        const x = xsF(p.x), y1 = ys(p.mean - p.ci), y2 = ys(p.mean + p.ci);
        g.appendChild(svg('line', { x1: x, x2: x, y1, y2, stroke: s.color, 'stroke-width': 1.5, opacity: 0.75 }));
        g.appendChild(svg('line', { x1: x - 4, x2: x + 4, y1, y2: y1, stroke: s.color, 'stroke-width': 1.5, opacity: 0.75 }));
        g.appendChild(svg('line', { x1: x - 4, x2: x + 4, y1: y2, y2, stroke: s.color, 'stroke-width': 1.5, opacity: 0.75 }));
      }
      if (valid.length > 1 && !s.markersOnly) {
        g.appendChild(svg('path', {
          d: valid.map((p, i) => (i ? 'L' : 'M') + xsF(p.x).toFixed(1) + ' ' + ys(p.mean).toFixed(1)).join(' '),
          fill: 'none', stroke: s.color, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round',
          'stroke-dasharray': s.dash ? '6 4' : null,
        }));
      }
      for (const p of (valid.length > 30 && !s.markersOnly ? [] : valid)) {
        if (s.hollow) {
          g.appendChild(svg('rect', { x: xsF(p.x) - 4.5, y: ys(p.mean) - 4.5, width: 9, height: 9, fill: c.surface, stroke: s.color, 'stroke-width': 2, transform: 'rotate(45 ' + xsF(p.x) + ' ' + ys(p.mean) + ')' }));
        } else {
          g.appendChild(svg('circle', { cx: xsF(p.x), cy: ys(p.mean), r: 4.5, fill: s.color, stroke: c.surface, 'stroke-width': 2 }));
        }
      }
      root.appendChild(g);
    }
    const cross = svg('line', { x1: 0, x2: 0, y1: m.t, y2: m.t + ih, stroke: c.axis, 'stroke-width': 1, 'stroke-dasharray': '3 3', visibility: 'hidden' });
    root.appendChild(cross);
    const hit = svg('rect', { x: m.l, y: m.t, width: iw, height: ih, fill: 'transparent' });
    root.appendChild(hit);
    return { svg: root, xsF, ys, m, iw, ih, hit, cross, W, H };
  }

  function attachHover(host, chart, series, opts) {
    opts = opts || {};
    const tip = el('div.chart-tip', { hidden: true });
    host.appendChild(tip);
    const all = series.flatMap((s) => s.points.map((p) => ({ s, p })));
    const move = (ev) => {
      const rect = chart.svg.getBoundingClientRect();
      const scale = chart.W / rect.width;
      const mx = (ev.clientX - rect.left) * scale;
      let best = null, bd = Infinity;
      for (const a of all) { const d = Math.abs(chart.xsF(a.p.x) - mx); if (d < bd) { bd = d; best = a; } }
      if (!best) return;
      const lp = best.p.key !== undefined ? best.p.key : best.p.lp;
      const x = chart.xsF(best.p.x);
      chart.cross.setAttribute('x1', x); chart.cross.setAttribute('x2', x); chart.cross.setAttribute('visibility', 'visible');
      const header = opts.header ? opts.header(best.p)
        : ui.xMode === 'param' ? (ui.paramPath + ' = ' + fmtNum(best.p.x, 4) + (best.p.label ? ' (' + best.p.label + ')' : ''))
          : 'Ponto ' + lp + (ui.xMode === 'erl' ? ' · ' + fmtNum(best.p.x, 1) + ' E' : '');
      clear(tip).appendChild(el('div.tt', header));
      for (const s of series) {
        const p = s.points.find((q) => (q.key !== undefined ? q.key : q.lp) === lp);
        if (!p) continue;
        tip.appendChild(el('div.row', el('span', el('span.sw', { style: { background: s.color } }), s.name),
          el('span', fmtSci(p.mean) + (Number.isFinite(p.ci) ? ' ± ' + fmtSci(p.ci) : ''))));
      }
      if (opts.footer !== null) tip.appendChild(el('div.muted.small', { style: { marginTop: '4px' } },
        opts.footer ? opts.footer(best.p) : 'n = ' + best.p.n + ' replicações'));
      tip.hidden = false;
      const px = x / scale;
      const left = px + 16 + tip.offsetWidth > rect.width ? px - tip.offsetWidth - 16 : px + 16;
      tip.style.left = Math.max(0, left) + 'px';
      tip.style.top = (chart.m.t / scale + 8) + 'px';
    };
    chart.hit.addEventListener('mousemove', move);
    chart.hit.addEventListener('mouseleave', () => { tip.hidden = true; chart.cross.setAttribute('visibility', 'hidden'); });
  }

  function serializeSvg(node, bg) {
    const clone = node.cloneNode(true);
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    clone.removeAttribute('class');
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('width', '100%'); rect.setAttribute('height', '100%'); rect.setAttribute('fill', bg);
    clone.insertBefore(rect, clone.firstChild);
    clone.querySelectorAll('[visibility="hidden"]').forEach((n) => n.remove());
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(clone);
  }

  function downloadPng(node, name, bg) {
    const text = serializeSvg(node, bg);
    const img = new Image();
    const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml' }));
    img.onload = () => {
      const w = Number(node.getAttribute('width')), h = Number(node.getAttribute('height'));
      const cv = document.createElement('canvas');
      cv.width = w * 2; cv.height = h * 2;
      const ctx = cv.getContext('2d');
      ctx.scale(2, 2); ctx.drawImage(img, 0, 0, w, h);
      URL.revokeObjectURL(url);
      cv.toBlob((b) => {
        const a = el('a', { href: URL.createObjectURL(b), download: name });
        document.body.appendChild(a); a.click(); a.remove();
      });
    };
    img.src = url;
  }

  function invalidate(casePath) {
    for (const k of [...cache.keys()]) if (k.startsWith(casePath + '|')) cache.delete(k);
  }

  App.results = { render, parseCsv, stats, invalidate };
  App.charts = { buildSvg, attachHover, serializeSvg, downloadPng, stats, tCrit };
})(window.App);
