/*
 * "Verificação QKD" page: runs the QKD verifiers of the simulator jar and the
 * three article-base (Dibaj et al. 2024, multi-fiber) checks:
 *   - physical layer (Table 5, Fig. 1(b), Fig. 5(b), SKR x mu);
 *   - network layer replaying the routed traces of the Python reconstruction;
 *   - network layer executed natively by SNetS (30 normal simulation cases).
 * Every tool is a main class of the simulator jar started as a separate
 * process; this page only reads the CSV/TXT files they write.
 */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtSci, fmtDuration } = App;

  const Q = { info: null, jar: '', verifierRuns: {}, pageRuns: {} };
  const TOPO_LABEL = { small_core: 'Small Core', butterfly: 'Butterfly', metro: 'Metro' };

  function jars() { return (App.state.info && App.state.info.jars) || []; }

  function defaultQkdJar() {
    const j = jars();
    const pref = App.state.info && App.state.info.settings.defaultJar;
    return (j.find((x) => x.path === pref && /qkd/i.test(x.name)) || j.find((x) => /qkd/i.test(x.name))
      || j.find((x) => x.path === pref) || j[0] || {}).path || '';
  }

  async function render() {
    const box = clear(document.getElementById('qkdView'));
    if (!Q.jar) Q.jar = defaultQkdJar();
    const jarSel = el('select.input', jars().map((j) => el('option', { value: j.path }, j.name + ' · ' + j.location)));
    jarSel.value = Q.jar;
    jarSel.addEventListener('change', () => { Q.jar = jarSel.value; render(); });
    box.append(
      el('div.case-head', el('div.case-title', el('div.crumbs', App.displayPath((Q.info && Q.info.dibajRel ? Q.info.dibajRel.split('/').slice(0, -1).join('/') : 'QKD_reference'))),
        el('h1', 'Verificação do QKD e do artigo-base')),
        el('div.case-actions', el('div', { style: { minWidth: '320px' } }, jarSel))),
      el('p.lead.muted', { style: { maxWidth: '80ch' } },
        'Executa os verificadores do addon QKD e reproduz o artigo-base (Dibaj et al., 2024, cenário multifibra) de três formas: ',
        'camada física, camada de rede sobre os traces da reconstrução em Python e camada de rede executada pelo próprio motor do SNetS.'));
    if (!Q.jar) {
      box.appendChild(el('div.notice.err', 'Nenhum JAR encontrado. Gere o SNetS-SDM-QKD.jar.'));
      return;
    }
    try { Q.info = await App.api.tools(Q.jar); } catch (e) { box.appendChild(el('div.notice.err', e.message)); return; }
    if (Q.info && Q.info.verificationDir) box.append(el('p.small.muted', { style: { maxWidth: '90ch' } }, 'Os resultados do plano de verificação e do artigo-base ficam na pasta ', el('code', Q.info.verificationDir),
      ' (QKD_ladder/<perfil>/<passo> e QKD_reference), separada de simulations; na árvore, aparecem na seção "Verificação". Os verificadores da lista só escrevem no console da execução (painel Execuções).'));
    if (!Q.info.dibajExists) {
      box.appendChild(el('div.notice.warn', { style: { marginBottom: '16px' } }, 'Pasta do artigo-base não encontrada: ' + Q.info.dibajRoot));
    }
    const stack = el('div.stack');
    box.appendChild(stack);
    stack.append(App.ladder ? App.ladder.panel() : null, verifiersPanel(), physicalPanel(), tracesPanel(), nativePanel());
  }

  // ------------------------------------------------------------------ shared tool runner

  /** Starts a tool and shows its progress inside `host`; resolves with the final run summary. */
  function runTool(id, host, queued) {
    return new Promise(async (resolve) => {
      let r;
      try { r = await App.api.runTool(id, Q.jar, queued); } catch (e) { App.toast(e.message, 'err'); resolve(null); return; }
      Q.pageRuns[id] = r.id;
      watch(r.id, host, resolve);
      App.run.pollList();
    });
  }

  function watch(runId, host, resolve) {
    const bar = el('div.progress', el('div'));
    const meta = el('div.run-meta');
    const out = el('div.console', { style: { height: '160px' } });
    const det = el('details', el('summary.small.muted', { style: { cursor: 'pointer' } }, 'Saída do processo'), out);
    if (host) clear(host).append(meta, bar, det);
    let next = 0;
    const tick = async () => {
      let d;
      try { d = await App.api.runLog(runId, next); } catch (e) { return; }
      next = d.next;
      if (host && document.body.contains(host)) {
        for (const line of d.lines) out.appendChild(document.createTextNode(line + '\n'));
        out.scrollTop = out.scrollHeight;
        const end = d.end || Date.now();
        clear(meta).append(el('span.state.' + d.state, stateLabel(d.state)),
          d.start ? el('span', 'Tempo ', el('b', fmtDuration(end - d.start))) : null,
          el('span', 'Progresso ', el('b', fmtNum(d.progress * 100, 0) + ' %')));
        bar.firstChild.style.width = (d.state === 'finished' ? 100 : d.progress * 100) + '%';
      }
      if (d.state === 'finished' || d.state === 'failed' || d.state === 'cancelled') {
        clearInterval(timer);
        if (resolve) resolve(d);
      }
    };
    const timer = setInterval(tick, 1000);
    tick();
  }

  function stateLabel(s) {
    return { queued: 'na fila', running: 'executando', finished: 'concluído', failed: 'falhou', cancelled: 'cancelado' }[s] || s;
  }

  async function readText(rel) {
    try { return (await App.api.text(rel, true)).text; } catch (e) { return null; }
  }

  function parseRows(text) {
    if (!text) return [];
    const lines = text.split(/\r?\n/).filter((l) => l.trim());
    const h = lines[0].split(',').map((x) => x.trim());
    return lines.slice(1).map((l) => { const c = l.split(','); const o = {}; h.forEach((k, i) => { o[k] = c[i]; }); return o; });
  }

  // ------------------------------------------------------------------ verifiers

  function verifiersPanel() {
    const list = Q.info.verifiers || [];
    const tbody = el('tbody');
    const rows = {};
    for (const v of list) {
      const status = el('span.muted', '—');
      const time = el('span.muted', '');
      const view = el('button.btn.small.ghost', { type: 'button', hidden: true }, 'saída');
      rows[v] = { status, time, view };
      tbody.appendChild(el('tr', el('td.mono', { style: { padding: '5px 10px' } }, v), el('td', { style: { padding: '5px 10px' } }, status),
        el('td', { style: { padding: '5px 10px' } }, time), el('td.rowctl', view)));
    }
    const summary = el('span.small.muted');
    const runAll = el('button.btn.primary', { type: 'button', disabled: !list.length }, 'Executar todos (em fila)');
    runAll.addEventListener('click', async () => {
      runAll.disabled = true;
      let pass = 0, fail = 0, done = 0;
      const update = () => { summary.textContent = `${done}/${list.length} concluídos · ${pass} OK · ${fail} falha(s)`; };
      update();
      await Promise.all(list.map(async (v) => {
        const row = rows[v];
        row.status.className = 'state queued'; row.status.textContent = 'na fila';
        let r;
        try { r = await App.api.runTool('verify:' + v, Q.jar, true); } catch (e) { row.status.textContent = e.message; return; }
        Q.verifierRuns[v] = r.id;
        await new Promise((resolve) => {
          const poll = setInterval(async () => {
            let d;
            try { d = await App.api.runLog(r.id, 0); } catch (e) { return; }
            if (d.state === 'running') { row.status.className = 'state running'; row.status.textContent = 'executando'; }
            if (d.state === 'finished' || d.state === 'failed' || d.state === 'cancelled') {
              clearInterval(poll);
              const ok = d.state === 'finished' && d.lines.some((l) => /ALL CHECKS PASSED/.test(l));
              row.status.className = 'state ' + (ok ? 'finished' : 'failed');
              row.status.textContent = ok ? 'OK' : 'falhou';
              row.time.textContent = fmtDuration((d.end || Date.now()) - d.start);
              row.view.hidden = false;
              row.view.onclick = () => App.modal(v, el('div.console', { style: { height: '420px' } }, d.lines.join('\n')), null);
              if (ok) pass++; else fail++;
              done++; update();
              resolve();
            }
          }, 1500);
        });
      }));
      runAll.disabled = false;
      App.toast(`Verificadores: ${pass} OK, ${fail} falha(s)`, fail ? 'err' : 'ok', 8000);
    });
    return el('section.panel',
      el('h2', 'Verificadores do addon QKD'),
      el('p.lead', 'Programas de verificação contidos no JAR (Partes 1 a 22: modelo BB84, crosstalk → ruído no receptor, QTND, núcleo reservado, ciclo dinâmico, TDM/Q-TRx, QChs por trecho, artigo-base…). Rodam em sequência na fila, a partir da pasta do projeto.'),
      el('div.toolbar', runAll, summary),
      el('div.table-wrap', { style: { maxHeight: '420px' } }, el('table.grid', el('thead', el('tr', el('th', 'Verificador'), el('th', 'Resultado'), el('th', 'Tempo'), el('th', ''))), tbody)));
  }

  // ------------------------------------------------------------------ physical layer

  function physicalPanel() {
    const host = el('div');
    const results = el('div');
    const btn = el('button.btn.primary', { type: 'button', disabled: !Q.info.hasPhysical }, 'Executar verificação física');
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      const d = await runTool('ref-physical', host);
      btn.disabled = false;
      if (d && d.state === 'finished') showPhysical(results);
    });
    showPhysical(results);
    return el('section.panel',
      el('h2', 'Artigo-base — camada física'),
      el('p.lead', 'Valores intermediários da Tabela 5 da qualificação (1550 nm, 25 km), SKR × comprimento de onda e distância (Fig. 1(b)), SKR/Q-TRx × QTND em um enlace de 40 km (Fig. 5(b)) e SKR × μ (Mehdizadeh et al., cenários de 20 e 70 km).'),
      el('div.toolbar', btn, Q.info.hasPhysical ? null : el('span.fwarn.small', 'Este JAR não contém a ferramenta.')),
      host, results);
  }

  async function showPhysical(box) {
    const base = Q.info.dibajRel + '/physical_results/';
    const [t5, f1, f5, mu] = await Promise.all([readText(base + 'table5_intermediate_values.csv'),
      readText(base + 'snets_fig1b_skr.csv'), readText(base + 'snets_fig5b_skr_per_qtrx.csv'), readText(base + 'mu_sweep_skr.csv')]);
    clear(box);
    if (!t5 && !f1) { box.appendChild(el('p.muted', 'Ainda não há resultados físicos. Clique em "Executar verificação física".')); return; }
    if (t5) {
      const rows = parseRows(t5);
      const bad = rows.filter((r) => r.status !== 'OK').length;
      box.append(el('div.notice.' + (bad ? 'err' : 'ok'), { style: { margin: '12px 0' } },
        bad ? bad + ' valor(es) diferente(s) da referência.' : 'Todos os ' + rows.length + ' valores de referência foram reproduzidos.'),
      el('div.table-wrap', el('table.grid.data',
        el('thead', el('tr', el('th', 'Grandeza'), el('th.num', 'SNetS'), el('th.num', 'Qualificação'), el('th.num', '|diferença|'), el('th', ''))),
        el('tbody', rows.map((r) => el('tr', el('td.mono', r.quantity), el('td.num', fmtSci(Number(r.snets_value))),
          el('td.num', fmtSci(Number(r.qualification_value))), el('td.num', r.abs_difference),
          el('td', el('span.tag.' + (r.status === 'OK' ? 'info' : 'err'), r.status))))))));
    }
    const charts = el('div.two-col', { style: { marginTop: '16px' } });
    box.appendChild(charts);
    if (f1) {
      const rows = parseRows(f1);
      const lengths = [1, 5, 10, 20, 30, 40];
      const series = lengths.map((L) => ({ name: L + ' km', points: rows.filter((r) => Number(r.length_km) === L)
        .map((r) => ({ x: Number(r.wavelength_nm), key: Number(r.wavelength_nm), mean: Number(r.skr_mbps), ci: NaN, n: 1 })) }));
      charts.appendChild(chartBox(series, 'SKR × λ (Fig. 1(b), MF)', 'Comprimento de onda (nm)', true, (p) => fmtNum(p.x, 0) + ' nm', 'Mbit/s'));
    }
    if (f5) {
      const rows = parseRows(f5);
      const wl = [1520, 1550, 1580, 1620];
      const series = wl.map((w) => ({ name: w + ' nm', points: rows.filter((r) => Number(r.wavelength_nm) === w)
        .map((r) => ({ x: Number(r.qtnd_km), key: Number(r.qtnd_km), mean: Number(r.skr_per_qtrx_mbps), ci: NaN, n: 1 })) }));
      charts.appendChild(chartBox(series, 'SKR/Q-TRx × QTND, enlace de 40 km (Fig. 5(b))', 'QTND (km)', false, (p) => 'QTND ' + p.x + ' km', 'Mbit/s'));
    }
    if (mu) {
      const rows = parseRows(mu);
      const series = [['skr_20km_bps', '20 km'], ['skr_70km_bps', '70 km']].map(([c, n]) => ({ name: n,
        points: rows.map((r) => ({ x: Number(r.mu), key: Number(r.mu), mean: Number(r[c]), ci: NaN, n: 1 })).filter((p) => p.mean > 0) }));
      charts.appendChild(chartBox(series, 'SKR × μ (Mehdizadeh et al., fibras separadas)', 'μ (fótons por pulso)', true, (p) => 'μ = ' + p.x, 'bit/s'));
    }
  }

  function chartBox(series, title, xTitle, log, header, unit) {
    const css = getComputedStyle(document.documentElement);
    const tok = (n) => css.getPropertyValue(n).trim();
    series.forEach((s, i) => { if (!s.color) s.color = tok('--series-' + ((s.slot || i) + 1)); });
    const colors = { surface: tok('--chart-surface'), text: tok('--fg-2'), title: tok('--fg'), grid: tok('--grid'), axis: tok('--border-strong') };
    const wrap = el('div.chart-box');
    const legend = el('div.chart-legend', series.length > 1 ? series.map((s) => el('span',
      el('span.sw', { style: { background: s.color, opacity: s.markersOnly ? 0.6 : 1 } }), s.name)) : null);
    const host = el('div', { style: { position: 'relative' } });
    wrap.append(legend, host);
    requestAnimationFrame(() => {
      const width = Math.max(320, host.clientWidth || 520);
      const chart = App.charts.buildSvg(series, { width, height: 320, xTitle, title, log, colors });
      host.appendChild(chart.svg);
      App.charts.attachHover(host, chart, series, { header, footer: unit ? () => unit : null });
    });
    return wrap;
  }

  // ------------------------------------------------------------------ traces

  function tracesPanel() {
    const host = el('div');
    const results = el('div');
    const btn = el('button.btn.primary', { type: 'button', disabled: !Q.info.hasTraces }, 'Executar sobre os traces');
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      const d = await runTool('ref-traces', host);
      btn.disabled = false;
      if (d && d.state === 'finished') showTraces(results);
    });
    showTraces(results);
    return el('section.panel',
      el('h2', 'Artigo-base — rede sobre os traces da reconstrução em Python'),
      el('p.lead', 'Reaplica as requisições já roteadas pela reconstrução em Python (3 topologias × 10 replicações × 10 QTNDs) à camada QKD/TDM/Q-TRx em Java e confere as 300 observações. Isola a camada QKD dos desempates de roteamento que o artigo não especifica.'),
      el('div.toolbar', btn), host, results);
  }

  async function showTraces(box) {
    const txt = await readText(Q.info.dibajRel + '/snets_results/verification_report.txt');
    clear(box);
    if (!txt) { box.appendChild(el('p.muted', 'Ainda não executado.')); return; }
    const exact = /Exact reproduction: true/.test(txt);
    box.append(el('div.notice.' + (exact ? 'ok' : 'warn'), { style: { margin: '12px 0' } },
      exact ? 'Reprodução exata: as 300 observações de Q-TRx são iguais às da reconstrução em Python.' : 'Ver relatório abaixo.'),
    el('div.console', { style: { height: 'auto', maxHeight: '200px' } }, txt));
  }

  // ------------------------------------------------------------------ native SNetS run

  function nativePanel() {
    const host = el('div');
    const results = el('div');
    const mk = (id, label, primary) => {
      const b = el('button.btn' + (primary ? '.primary' : ''), { type: 'button', disabled: !Q.info.hasNative }, label);
      b.addEventListener('click', async () => {
        b.disabled = true;
        const d = await runTool(id, host);
        b.disabled = false;
        if (d && d.state !== 'cancelled') { App.loadTree(); showNative(results); }
      });
      return b;
    };
    showNative(results);
    return el('section.panel',
      el('h2', 'Artigo-base — rede executada pelo motor do SNetS'),
      el('p.lead', 'Gera 30 casos comuns do SNetS em ', el('code', App.displayPath(Q.info.nativeRel)),
        ' (3 topologias × QTND 5–50 km, 10 replicações cada). O próprio simulador gera as requisições, roteia (K = 2, menor índice de canal final), aloca o espectro clássico (10 fibras × 160 canais de 75 GHz), segmenta por QTND, compartilha os QChs por TDM (256 slots) e conta os pares Q-TRx. ',
        'O resultado é comparado com a reconstrução em Python e com os pontos digitalizados da Fig. 7 do artigo.'),
      el('div.toolbar', mk('ref-native-all', 'Gerar, executar e analisar', true), mk('ref-native-prepare', 'Só gerar os casos'),
        mk('ref-native-run', 'Executar os casos existentes'), mk('ref-native-analyze', 'Só analisar')),
      host, results);
  }

  async function showNative(box) {
    const base = Q.info.nativeRel + '/results/';
    const [sum, stats, report] = await Promise.all([readText(base + 'snets_native_summary.csv'),
      readText(base + 'snets_native_statistics.csv'), readText(base + 'snets_native_report.txt')]);
    clear(box);
    if (!sum || !stats) { box.appendChild(el('p.muted', 'Ainda sem resultados da execução nativa.')); return; }
    const srows = parseRows(sum);
    const ok = report && /Status geral: OK/.test(report);
    box.append(el('div.notice.' + (ok ? 'ok' : 'warn'), { style: { margin: '12px 0' } },
      ok ? 'Critério da qualificação atendido nas três topologias: curva em U e QTND ótimo a no máximo 5 km de 25 km em todas as replicações.'
        : 'Algum critério não foi atendido — veja o relatório.'),
    el('div.table-wrap', el('table.grid.data',
      el('thead', el('tr', el('th', 'Topologia'), el('th.num', 'Ótimo SNetS'), el('th.num', 'Ótimo Python'), el('th.num', 'Ótimo artigo'),
        el('th.num', 'Replicações c/ ótimo em 20–30 km'), el('th.num', 'MAE vs Python'), el('th.num', 'MAE vs artigo'), el('th.num', 'RMSE vs artigo'),
        el('th.num', 'Bloqueio QKD'), el('th.num', 'Bloqueio clássico'))),
      el('tbody', srows.map((r) => el('tr', el('td', TOPO_LABEL[r.topology] || r.topology),
        el('td.num', r.optimum_qtnd_snets + ' km'), el('td.num', r.optimum_qtnd_python + ' km'), el('td.num', r.optimum_qtnd_article + ' km'),
        el('td.num', r.replications_optimum_within_5km_of_25), el('td.num', fmtNum(Number(r.mae_vs_python), 2)),
        el('td.num', fmtNum(Number(r.mae_vs_article), 2)), el('td.num', fmtNum(Number(r.rmse_vs_article), 2)),
        el('td.num', fmtNum(100 * Number(r.qkd_blocking_max), 2) + ' %'), el('td.num', fmtNum(100 * Number(r.classical_blocking_max), 2) + ' %')))))));

    const rows = parseRows(stats);
    const normSeg = el('div.seg');
    let normalized = false;
    const charts = el('div.stack', { style: { marginTop: '16px' } });
    const drawCharts = () => {
      clear(charts);
      for (const topo of [...new Set(rows.map((r) => r.topology))]) {
        const t = rows.filter((r) => r.topology === topo);
        const pyMax = Math.max(...t.map((r) => Number(r.python_mean_qtrx) || 0));
        const pt = (r, field, ciField, scale) => ({ x: Number(r.qtnd_km), key: Number(r.qtnd_km),
          mean: Number(r[field]) * (scale || 1), ci: ciField ? Number(r[ciField]) : NaN, n: Number(r.n) });
        const series = normalized ? [
          { name: 'SNetS (média ± IC95%)', slot: 0, points: t.map((r) => pt(r, 'normalized_mean_pct', 'normalized_ci95_margin_pct')) },
          { name: 'Reconstrução Python (média)', slot: 1, dash: true, points: t.filter((r) => r.python_mean_qtrx).map((r) => ({ ...pt(r, 'python_mean_qtrx'), mean: 100 * Number(r.python_mean_qtrx) / pyMax })) },
          { name: 'Artigo (Fig. 7 digitalizada)', slot: 2, markersOnly: true, hollow: true, points: t.filter((r) => r.article_normalized_pct).map((r) => pt(r, 'article_normalized_pct')) },
        ] : [
          { name: 'SNetS (média ± IC95%)', slot: 0, points: t.map((r) => pt(r, 'mean_qtrx', 'ci95_margin')) },
          { name: 'Reconstrução Python (média)', slot: 1, dash: true, points: t.filter((r) => r.python_mean_qtrx).map((r) => pt(r, 'python_mean_qtrx')) },
          { name: 'Artigo (Fig. 7 digitalizada)', slot: 2, markersOnly: true, hollow: true, points: t.filter((r) => r.article_qtrx).map((r) => pt(r, 'article_qtrx')) },
        ];
        charts.appendChild(chartBox(series, (TOPO_LABEL[topo] || topo) + (normalized ? ' — Q-TRx normalizado (%)' : ' — pares Q-TRx'),
          'QTND (km)', false, (p) => 'QTND ' + p.x + ' km', normalized ? '% do máximo de cada replicação' : 'pares Q-TRx'));
      }
    };
    const setNorm = (v) => { normalized = v; [...normSeg.children].forEach((b, i) => b.setAttribute('aria-pressed', String((i === 1) === v))); drawCharts(); };
    normSeg.append(el('button', { type: 'button', 'aria-pressed': 'true', on: { click: () => setNorm(false) } }, 'Q-TRx absoluto'),
      el('button', { type: 'button', 'aria-pressed': 'false', on: { click: () => setNorm(true) } }, 'Normalizado'));
    box.append(el('div.toolbar', { style: { marginTop: '16px' } }, normSeg, el('span.spacer'),
      el('button.btn.small', { type: 'button', on: { click: () => App.modal('Relatório da execução nativa', el('div.console', { style: { height: '420px' } }, report || ''), null) } }, 'Ver relatório')),
    charts);
    drawCharts();
  }

  App.qkd = { render };
  App.qkdTools = { runTool, watch, chartBox, readText, info: () => Q.info, jar: () => Q.jar };
})(window.App);
