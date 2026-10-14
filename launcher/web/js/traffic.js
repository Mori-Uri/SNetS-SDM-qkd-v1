/*
 * Traffic tab: summary of the current traffic file, reuse of another case's
 * traffic and generation of a new one. The generator reproduces the formula
 * of util.tools.TrafficFileCreator.createProportionalTrafficFile, generalised
 * to any subset of nodes taken from the case's network file.
 */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtBitRate, parseNumber, isPlainObject } = App;

  function summarize(traffic) {
    const gens = isPlainObject(traffic) && Array.isArray(traffic.requestGenerators) ? traffic.requestGenerators : [];
    const pairs = new Set();
    const nodes = new Set();
    const byRate = new Map();
    let base = 0, inc = 0;
    for (const g of gens) {
      pairs.add(g.source + '→' + g.destination);
      nodes.add(String(g.source)); nodes.add(String(g.destination));
      const mu = Number(g.holdRate) || 1;
      const l = (Number(g.arrivalRate) || 0) / mu;
      const li = (Number(g.arrivalRateIncrease) || 0) / mu;
      base += l; inc += li;
      const r = Number(g.bitRate);
      const e = byRate.get(r) || { rate: r, count: 0, load: 0, inc: 0, lambda: 0 };
      e.count++; e.load += l; e.inc += li; e.lambda += Number(g.arrivalRate) || 0;
      byRate.set(r, e);
    }
    const rates = [...byRate.values()].sort((a, b) => a.rate - b.rate);
    const holdRates = [...new Set(gens.map((g) => Number(g.holdRate)))];
    return { gens, pairs: pairs.size, nodes: [...nodes], rates, base, inc, holdRates };
  }

  /** Same arithmetic as TrafficFileCreator.createProportionalTrafficFile. */
  function generate(nodeNames, rates, rho, increment, mu) {
    const n = nodeNames.length;
    const nPairs = n * (n - 1);
    const sumProp = rates.reduce((s, r) => s + r.prop, 0);
    const lambdaPair = (rho / nPairs) * mu;
    const incPair = (increment / nPairs) * mu;
    const gens = [];
    for (const s of nodeNames) {
      for (const d of nodeNames) {
        if (s === d) continue;
        for (const r of rates) {
          gens.push({
            source: s,
            destination: d,
            bitRate: r.gbps * 1e9,
            arrivalRate: (lambdaPair / sumProp) * r.prop,
            holdRate: mu,
            arrivalRateIncrease: (incPair / sumProp) * r.prop,
          });
        }
      }
    }
    return { requestGenerators: gens };
  }

  function loadsPerPoint(base, inc, points) {
    const out = [];
    for (let i = 0; i < Math.max(1, points); i++) out.push(base + i * inc);
    return out;
  }

  function render() {
    const root = el('div.stack');
    const model = App.state.model;
    const sum = summarize(model.traffic);
    const points = isPlainObject(model.simulation) ? Number(model.simulation.loadPoints) || 1 : 1;

    // ------------------------------------------------ current traffic
    const loads = loadsPerPoint(sum.base, sum.inc, points);
    const cur = el('section.panel',
      el('h2', 'Tráfego atual do caso'),
      el('p.lead', App.state.dirty.traffic ? 'Alterado nesta sessão (ainda não salvo).' : 'Conteúdo do arquivo traffic deste caso.'),
      el('div.kv',
        kv('Geradores', fmtNum(sum.gens.length)),
        kv('Pares origem→destino', fmtNum(sum.pairs)),
        kv('Nós envolvidos', fmtNum(sum.nodes.length)),
        kv('Carga inicial', fmtNum(sum.base, 1), 'Erlang'),
        kv('Incremento', fmtNum(sum.inc, 1), 'Erlang/ponto'),
        kv('holdRate', sum.holdRates.length === 1 ? fmtNum(sum.holdRates[0]) : sum.holdRates.length ? 'vários' : '—')),
      el('h3', { style: { marginTop: '16px' } }, 'Carga por ponto (loadPoints = ' + points + ')'),
      el('div.table-wrap', el('table.grid.data',
        el('thead', el('tr', el('th', 'Ponto'), loads.map((_, i) => el('th.num', String(i))))),
        el('tbody', el('tr', el('td', 'Erlang'), loads.map((l) => el('td.num', fmtNum(l, 1))))))),
      sum.rates.length ? el('div', { style: { marginTop: '14px' } },
        el('h3', 'Composição por taxa de bit'),
        el('div.table-wrap', el('table.grid.data',
          el('thead', el('tr', el('th', 'Taxa'), el('th.num', 'Geradores'), el('th.num', 'Carga inicial (E)'), el('th.num', 'Participação'))),
          el('tbody', sum.rates.map((r) => el('tr',
            el('td', fmtBitRate(r.rate)), el('td.num', fmtNum(r.count)), el('td.num', fmtNum(r.load, 2)),
            el('td.num', sum.base ? fmtNum(100 * r.load / sum.base, 1) + ' %' : '—'))))))) : null);
    root.appendChild(cur);
    root.appendChild(el('section.panel', glossary('O que significa cada variável do tráfego', TRAFFIC_TERMS, 'traffic', true)));

    const grid = el('div.two-col');
    grid.appendChild(reusePanel());
    grid.appendChild(generatorPanel(sum, points));
    root.appendChild(grid);
    if (App.qkdcase && App.qkdcase.isQkd()) root.appendChild(App.qkdcase.trafficPanel(sum));

    // ------------------------------------------------ preview
    const hasQkd = sum.gens.some((g) => g.qkdProtectedProbability !== undefined || g.keyRateMbps !== undefined);
    const preview = el('section.panel',
      el('h2', 'Geradores de requisição'),
      el('p.lead', 'Primeiras linhas do arquivo. Para editar linha a linha use o modo JSON abaixo.'),
      el('div.table-wrap', el('table.grid.data',
        el('thead', el('tr', ['source', 'destination', 'bitRate', 'arrivalRate', 'holdRate', 'arrivalRateIncrease'].concat(hasQkd ? ['qkdProtectedProbability', 'keyRateMbps'] : []).map((h) => el('th', h)))),
        el('tbody', sum.gens.slice(0, 40).map((g) => el('tr',
          el('td', String(g.source)), el('td', String(g.destination)), el('td.num', fmtBitRate(Number(g.bitRate))),
          el('td.num', fmtNum(Number(g.arrivalRate))), el('td.num', fmtNum(Number(g.holdRate))),
          el('td.num', fmtNum(Number(g.arrivalRateIncrease))),
          hasQkd ? el('td.num', g.qkdProtectedProbability === undefined ? '—' : fmtNum(Number(g.qkdProtectedProbability))) : null,
          hasQkd ? el('td.num', g.keyRateMbps === undefined ? '—' : fmtNum(Number(g.keyRateMbps))) : null))))),
      sum.gens.length > 40 ? el('p.muted.small', '… mais ' + fmtNum(sum.gens.length - 40) + ' linhas.') : null);
    const det = el('details.group', el('summary', 'Editar o arquivo traffic como JSON'),
      el('div.group-body', App.forms.renderJsonEditor('traffic')));
    root.append(preview, det);
    return root;
  }

  // ------------------------------------------------ glossary of the traffic variables

  /** Collapsible list of term → explanation (remembers open/closed). */
  function glossary(title, items, key, openDefault) {
    const d = el('details.gloss', el('summary', title),
      el('dl.gloss-list', items.flatMap(([t, desc, ex]) => [el('dt', t), el('dd', desc, ex ? el('div.gloss-ex', ex) : null)])));
    d.open = !!openDefault;
    try { const v = localStorage.getItem('snets.gloss.' + key); if (v !== null) d.open = v === '1'; } catch (e) { /* ignore */ }
    d.addEventListener('toggle', () => { try { localStorage.setItem('snets.gloss.' + key, d.open ? '1' : '0'); } catch (e) { /* ignore */ } });
    return d;
  }

  const TRAFFIC_TERMS = [
    ['Gerador de requisições', 'Cada linha do arquivo traffic. Produz requisições de circuito de um nó de origem (source) para um nó de destino (destination), todas com a mesma taxa de bits. O tráfego "todos para todos" tem um gerador por par ordenado de nós e por taxa.'],
    ['bitRate (taxa de bits)', 'Banda pedida por cada requisição, em bit/s. Com a modulação escolhida, define quantos slots o circuito ocupa.', 'Ex.: 1E11 = 100 Gb/s.'],
    ['arrivalRate (λ, taxa de chegada)', 'Chegadas por unidade de tempo deste gerador no primeiro ponto de carga. As chegadas são um processo de Poisson: o intervalo médio entre duas requisições do gerador é 1/λ.'],
    ['holdRate (μ, taxa de serviço)', 'Inverso da duração média de uma conexão. A duração é exponencial com média 1/μ. Com μ = 1, o tempo da simulação é medido em "durações médias de conexão".'],
    ['Carga (Erlang)', 'λ/μ: número médio de conexões que estariam ativas ao mesmo tempo se nada fosse bloqueado. A carga da rede é a soma das cargas de todos os geradores.', 'Ex.: λ = 2 e μ = 1 → 2 Erlangs neste gerador.'],
    ['arrivalRateIncrease (incremento de λ)', 'Quanto λ cresce a cada ponto de carga. O ponto i usa λ + i × incremento; assim a mesma lista de geradores gera todas as cargas da curva.'],
    ['Carga inicial / Incremento por ponto (gerador abaixo)', 'Carga total da rede no ponto 0 e o acréscimo por ponto, em Erlang. São divididos igualmente entre os pares origem→destino e, dentro de cada par, entre as taxas de bits pela proporção.', 'Ex.: 500 E e incremento 250 E com 3 pontos → 500, 750 e 1000 E.'],
    ['Proporção (das taxas)', 'Peso relativo de cada taxa de bits na carga de cada par. Não é uma porcentagem: só a razão entre os números importa.', 'Ex.: proporções 5, 4, 3, 2, 1 → a taxa com peso 5 recebe 5/15 = 33 % das requisições.'],
    ['Pontos de carga, requisições e replicações', 'Definidos no arquivo simulation (aba Simulação): loadPoints = quantas cargas simular; requests = chegadas simuladas em cada replicação; replications = repetições com sementes diferentes, usadas para média e intervalo de confiança.'],
  ];

  function kv(k, v, unit) {
    return el('div', el('div.k', k), el('div.v', v, unit ? el('small', ' ' + unit) : null));
  }

  function reusePanel() {
    const cases = App.allCases().filter((c) => c.path !== App.state.casePath);
    const sel = el('select.input', cases.map((c) => el('option', { value: c.path }, c.path)));
    const status = el('div.small.muted');
    const btn = el('button.btn', {
      type: 'button',
      on: { click: async () => {
        if (!sel.value) return;
        btn.disabled = true;
        try {
          const data = await App.api.readCase(sel.value);
          const text = data.files.traffic;
          if (!text) throw new Error('O caso selecionado não tem arquivo traffic.');
          const obj = JSON.parse(text);
          App.setValue('traffic', [], obj);
          App.toast('Tráfego de ' + sel.value + ' aplicado (não salvo).', 'ok');
          App.renderTab();
        } catch (e) {
          status.textContent = e.message; status.className = 'small fwarn';
        } finally { btn.disabled = false; }
      } },
    }, 'Usar este tráfego');
    return el('section.panel',
      el('h2', 'Reutilizar tráfego existente'),
      el('p.lead', 'Copia o arquivo traffic de outro caso para este. Os nós precisam existir na rede deste caso (a revisão verifica isso).'),
      cases.length ? el('div.form-grid', sel, el('div', btn), status) : el('p.muted', 'Não há outros casos.'));
  }

  function generatorPanel(sum, points) {
    const net = App.state.model.network;
    const allNodes = isPlainObject(net) && Array.isArray(net.nodes) ? net.nodes.map((n) => String(n.name)) : [];
    const used = new Set(sum.nodes);
    const picked = new Set(allNodes.filter((n) => !sum.nodes.length || used.has(n)));

    // defaults inferred from the current file, so "generate" with no edits reproduces it
    const minLambda = Math.min(...sum.rates.map((r) => r.lambda).filter((x) => x > 0));
    let rates = sum.rates.length ? sum.rates.map((r) => ({
      gbps: r.rate / 1e9,
      prop: Number.isFinite(minLambda) && minLambda > 0 ? Math.round((r.lambda / minLambda) * 100) / 100 : 1,
    })) : [100, 200, 300, 400, 500].map((g, i) => ({ gbps: g, prop: 5 - i }));
    const st = {
      rho: sum.base ? Math.round(sum.base * 1000) / 1000 : 500,
      inc: sum.inc ? Math.round(sum.inc * 1000) / 1000 : 250,
      mu: sum.holdRates.length === 1 ? sum.holdRates[0] : 1,
    };

    const nodeBox = el('div.node-picks');
    const drawNodes = () => {
      clear(nodeBox);
      for (const n of allNodes) {
        const cb = el('input', { type: 'checkbox', checked: picked.has(n) });
        cb.addEventListener('change', () => { if (cb.checked) picked.add(n); else picked.delete(n); update(); });
        nodeBox.appendChild(el('label.check', cb, n));
      }
    };
    const rateBox = el('div.rates');
    const drawRates = () => {
      clear(rateBox);
      rateBox.appendChild(el('div.rate-row.small.muted', el('span', 'Taxa (Gb/s)'), el('span', 'Proporção'), el('span', '')));
      rates.forEach((r, i) => {
        const g = el('input.input.num', { type: 'text', value: String(r.gbps) });
        const p = el('input.input.num', { type: 'text', value: String(r.prop) });
        g.addEventListener('input', () => { r.gbps = parseNumber(g.value); g.classList.toggle('invalid', !(r.gbps > 0)); update(); });
        p.addEventListener('input', () => { r.prop = parseNumber(p.value); p.classList.toggle('invalid', !(r.prop > 0)); update(); });
        rateBox.appendChild(el('div.rate-row', g, p, el('button.btn.small.ghost', {
          type: 'button', title: 'Remover', on: { click: () => { rates.splice(i, 1); drawRates(); update(); } },
        }, '✕')));
      });
      rateBox.appendChild(el('div', el('button.btn.small', {
        type: 'button', on: { click: () => { rates.push({ gbps: 100, prop: 1 }); drawRates(); update(); } },
      }, '+ Taxa')));
    };
    const num = (key, label, unit, help) => {
      const inp = el('input.input.num', { type: 'text', value: String(st[key]) });
      inp.addEventListener('input', () => { st[key] = parseNumber(inp.value); inp.classList.toggle('invalid', Number.isNaN(st[key]) || st[key] < 0); update(); });
      return el('div.field', el('div.flabel', el('span', label), unit ? el('span.unit', '(' + unit + ')') : null), inp, help ? el('div.help', help) : null);
    };
    const previewBox = el('div.small');
    const applyBtn = el('button.btn.primary', { type: 'button' }, 'Gerar e aplicar ao caso');

    function valid() {
      return picked.size >= 2 && rates.length > 0 && rates.every((r) => r.gbps > 0 && r.prop > 0)
        && st.rho >= 0 && st.inc >= 0 && st.mu > 0;
    }
    function update() {
      const n = picked.size;
      const ok = valid();
      applyBtn.disabled = !ok;
      clear(previewBox);
      if (!ok) { previewBox.appendChild(el('span.fwarn', 'Selecione ao menos 2 nós e informe taxas, proporções e cargas válidas.')); return; }
      const loads = loadsPerPoint(st.rho, st.inc, points);
      previewBox.append(
        el('div', fmtNum(n * (n - 1)) + ' pares × ' + rates.length + ' taxas = ', el('b', fmtNum(n * (n - 1) * rates.length) + ' geradores')),
        el('div.muted', 'Carga por ponto: ' + loads.map((l) => fmtNum(l, 1)).join(' · ') + ' Erlang'));
    }
    applyBtn.addEventListener('click', () => {
      const order = allNodes.filter((x) => picked.has(x));
      const t = generate(order, rates, st.rho, st.inc, st.mu);
      App.setValue('traffic', [], t);
      App.toast('Novo tráfego gerado: ' + fmtNum(t.requestGenerators.length) + ' geradores (não salvo).', 'ok');
      App.renderTab();
    });

    drawNodes(); drawRates(); update();
    return el('section.panel',
      el('h2', 'Gerar novo tráfego'),
      el('p.lead', 'Tráfego uniforme entre todos os pares dos nós selecionados, dividido entre as taxas na proporção indicada (mesma fórmula do TrafficFileCreator). Os valores iniciais foram inferidos do arquivo atual.'),
      el('div.form-grid',
        el('div.field.wide', el('div.flabel', 'Nós (da rede deste caso)'),
          allNodes.length ? nodeBox : el('span.fwarn', 'A rede deste caso não tem nós.'),
          el('div.toolbar', { style: { margin: '4px 0 0' } },
            el('button.btn.small', { type: 'button', on: { click: () => { allNodes.forEach((x) => picked.add(x)); drawNodes(); update(); } } }, 'Todos'),
            el('button.btn.small', { type: 'button', on: { click: () => { picked.clear(); drawNodes(); update(); } } }, 'Nenhum'))),
        el('div.field.wide', el('div.flabel', 'Taxas de bit e proporções'), rateBox,
          el('div.help', 'Proporção = peso relativo da taxa na carga de cada par (ex.: 5, 4, 3, 2, 1 → a primeira taxa recebe 5/15 das requisições).')),
        el('div.fields', num('rho', 'Carga inicial', 'Erlang', 'Carga total da rede no ponto 0.'),
          num('inc', 'Incremento por ponto', 'Erlang', 'Somado à carga a cada ponto.'),
          num('mu', 'holdRate (μ)', '1/duração média', 'Duração média da conexão = 1/μ.')),
        previewBox,
        el('div', applyBtn)));
  }

  App.traffic = { render, summarize, generate, glossary };
})(window.App);
