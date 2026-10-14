/*
 * Consistency checks run in the browser before saving / running.
 * They are deliberately conservative: errors only for things that would
 * certainly break the simulation; everything else is a warning. The
 * simulator still runs its own NetworkConfigValidator at start-up.
 */
(function (App) {
  'use strict';
  const { isPlainObject } = App;

  function trafficLoad(traffic) {
    const gens = (traffic && Array.isArray(traffic.requestGenerators)) ? traffic.requestGenerators : [];
    let base = 0, inc = 0;
    for (const g of gens) {
      const mu = Number(g.holdRate) || 1;
      base += (Number(g.arrivalRate) || 0) / mu;
      inc += (Number(g.arrivalRateIncrease) || 0) / mu;
    }
    return { base, inc, generators: gens.length };
  }

  function validateAll(model, catalog, info) {
    const out = [];
    const add = (level, file, msg) => out.push({ level, file, msg });
    const net = model.network, sim = model.simulation, phy = model.physicalLayer, tr = model.traffic;

    for (const f of ['network', 'physicalLayer', 'simulation', 'traffic', 'others']) {
      if (App.state.parseErrors[f]) add('err', f, 'JSON inválido: ' + App.state.parseErrors[f]);
      else if (model[f] === null || model[f] === undefined) add(f === 'others' ? 'warn' : 'err', f, 'Arquivo ausente no caso.');
    }

    // ---------------- network
    const nodeNames = new Set();
    if (isPlainObject(net)) {
      const nodes = Array.isArray(net.nodes) ? net.nodes : [];
      if (!nodes.length) add('err', 'network', 'Nenhum nó definido.');
      for (const n of nodes) {
        const name = String(n && n.name);
        if (nodeNames.has(name)) add('err', 'network', 'Nó duplicado: ' + name);
        nodeNames.add(name);
      }
      const links = Array.isArray(net.links) ? net.links : [];
      if (!links.length) add('err', 'network', 'Nenhum enlace definido.');
      const linkSet = new Set();
      links.forEach((l, i) => {
        const s = String(l.source), d = String(l.destination);
        if (!nodeNames.has(s) || !nodeNames.has(d)) add('err', 'network', `Enlace #${i} (${s}→${d}) usa nó inexistente.`);
        if (s === d) add('err', 'network', `Enlace #${i} liga o nó ${s} a ele mesmo.`);
        const key = s + '→' + d;
        if (linkSet.has(key)) add('warn', 'network', 'Enlace duplicado: ' + key);
        linkSet.add(key);
        if (!(Number(l.slots) > 0) || !Number.isInteger(Number(l.slots))) add('err', 'network', `Enlace ${key}: slots deve ser inteiro > 0.`);
        if (!(Number(l.size) > 0)) add('err', 'network', `Enlace ${key}: size (km) deve ser > 0.`);
      });
      const oneWay = [...linkSet].filter((k) => { const [s, d] = k.split('→'); return !linkSet.has(d + '→' + s); });
      if (oneWay.length) add('warn', 'network', `${oneWay.length} enlace(s) sem o sentido inverso (ex.: ${oneWay.slice(0, 3).join(', ')}).`);
      const cores = Array.isArray(net.cores) ? net.cores : [];
      const ids = cores.map((c) => Number(c.id));
      const idSet = new Set(ids);
      if (cores.length && idSet.size !== cores.length) add('err', 'network', 'IDs de núcleo repetidos.');
      for (const c of cores) {
        for (const a of (c.adjacentCores || [])) {
          if (!idSet.has(Number(a))) add('err', 'network', `Núcleo ${c.id}: vizinho ${a} não existe.`);
          else {
            const other = cores.find((x) => Number(x.id) === Number(a));
            if (other && !(other.adjacentCores || []).map(Number).includes(Number(c.id))) {
              add('warn', 'network', `Adjacência não simétrica: ${c.id}→${a} existe, mas ${a}→${c.id} não.`);
            }
          }
        }
      }
      if (!Array.isArray(net.modulations) || !net.modulations.length) add('err', 'network', 'Nenhum formato de modulação definido.');
      if (isPlainObject(net.qkd) && net.qkd.enabled) {
        if (net.qkd.quantumCoreId !== undefined && cores.length && !idSet.has(Number(net.qkd.quantumCoreId))) {
          add('err', 'network', `qkd.quantumCoreId = ${net.qkd.quantumCoreId} não corresponde a nenhum núcleo.`);
        }
        if (!(Number(net.qkd.qtndKm) > 0)) add('err', 'network', 'qkd.qtndKm deve ser > 0.');
        for (const k of ['alternativeRoutes', 'pichSlots', 'tdmSlots']) {
          if (net.qkd[k] !== undefined && !(Number.isInteger(Number(net.qkd[k])) && Number(net.qkd[k]) >= 0)) add('err', 'network', `qkd.${k} deve ser um inteiro ≥ 0.`);
        }
        if (net.qkd.qchGranularity !== undefined && !['route', 'link', 'span'].includes(String(net.qkd.qchGranularity).toLowerCase())) add('err', 'network', 'qkd.qchGranularity deve ser route, link ou span.');
        if (net.qkd.qchSlotPolicy !== undefined && !['firstfit', 'lowestloss'].includes(String(net.qkd.qchSlotPolicy).toLowerCase())) add('err', 'network', 'qkd.qchSlotPolicy deve ser firstfit ou lowestloss.');
        if (Number(net.qkd.pichSlots) > 0 && String(net.qkd.qchGranularity || 'link').toLowerCase() !== 'route') add('info', 'network', 'O PICh é reservado em qualquer modo de QCh, mas o cenário da qualificação usa qchGranularity = route.');
      }
    }

    // ---------------- traffic
    if (isPlainObject(tr)) {
      const gens = Array.isArray(tr.requestGenerators) ? tr.requestGenerators : [];
      if (!gens.length) add('err', 'traffic', 'Nenhum gerador de requisições.');
      let badNode = 0, badRate = 0;
      const missing = new Set();
      for (const g of gens) {
        if (nodeNames.size && (!nodeNames.has(String(g.source)) || !nodeNames.has(String(g.destination)))) {
          badNode++; missing.add(nodeNames.has(String(g.source)) ? String(g.destination) : String(g.source));
        }
        if (!(Number(g.holdRate) > 0) || !(Number(g.arrivalRate) >= 0) || !(Number(g.bitRate) > 0)) badRate++;
      }
      if (badNode) add('err', 'traffic', `${badNode} gerador(es) usam nós que não existem na rede (ex.: ${[...missing].slice(0, 4).join(', ')}).`);
      if (badRate) add('err', 'traffic', `${badRate} gerador(es) com arrivalRate/holdRate/bitRate inválidos.`);
    }

    // ---------------- simulation
    if (isPlainObject(sim)) {
      for (const k of ['requests', 'loadPoints', 'replications', 'threads']) {
        if (k in sim && (!Number.isInteger(sim[k]) || sim[k] < 1)) add('err', 'simulation', `${k} deve ser inteiro ≥ 1.`);
      }
      if (sim.replications === 1) add('warn', 'simulation', 'Com 1 replicação não é possível calcular intervalo de confiança.');
      if (info && sim.threads > info.cpus) add('info', 'simulation', `threads = ${sim.threads}, mas esta máquina tem ${info.cpus} processadores lógicos.`);
      const opts = (catalog && catalog.options) || {};
      const schema = App.schema.simulation.fields;
      for (const [k, h] of Object.entries(schema)) {
        if (!h.catalog || !(k in sim)) continue;
        const list = opts[h.catalog];
        if (list && list.length && !list.includes(sim[k])) add('warn', 'simulation', `${k} = "${sim[k]}" não aparece nas constantes do código-fonte.`);
      }
      if ('qkdProtectedRequestProbability' in sim) {
        const p = sim.qkdProtectedRequestProbability;
        if (!(p >= 0 && p <= 1)) add('err', 'simulation', 'qkdProtectedRequestProbability deve estar em [0, 1].');
        const qkdOn = isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled;
        if (p > 0 && !qkdOn) add('warn', 'simulation', 'Há requisições protegidas por QKD, mas network.qkd.enabled não está ativo.');
      }
      if (isPlainObject(sim.activeMetrics) && !Object.values(sim.activeMetrics).some(Boolean)) {
        add('warn', 'simulation', 'Nenhuma métrica ativa: a simulação não vai gerar CSVs.');
      }
      if (sim.kRouting === 'kfixedroutes' || sim.routing === 'fixedroutes') {
        add('info', 'simulation', 'Roteamento por rotas fixas: o arquivo kRoutesByPar.txt/routesByPar.txt é lido como entrada e deve corresponder à topologia.');
      }
    }

    // ---------------- request count vs generators (tail of already scheduled arrivals)
    if (isPlainObject(sim) && isPlainObject(tr) && Array.isArray(tr.requestGenerators)) {
      const gens = tr.requestGenerators.length;
      const qkdOn = isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled;
      const exact = qkdOn && sim.qkdExactRequestCount === true;
      if (!exact && gens > 0 && sim.requests < 2 * gens) {
        add('warn', 'simulation', `requests = ${sim.requests}, mas o tráfego tem ${gens} geradores: cada gerador já tem uma chegada agendada quando a geração para, `
          + `então cada replicação processa cerca de ${sim.requests + gens} chegadas (${Math.round(100 * gens / (sim.requests + gens))}% vindas dessa cauda). `
          + 'Aumente requests' + (qkdOn ? ' ou ative qkdExactRequestCount.' : '.'));
      }
    }

    // ---------------- QKD addon algorithms
    if (isPlainObject(sim) && isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled) {
      const gens = isPlainObject(tr) && Array.isArray(tr.requestGenerators) ? tr.requestGenerators : [];
      const anyGen = gens.some((g) => Number(g.qkdProtectedProbability) > 0);
      if (!(Number(sim.qkdProtectedRequestProbability) > 0) && !anyGen) add('warn', 'traffic', 'Modo QKD sem requisições protegidas: defina qkdProtectedProbability nos geradores (aba Tráfego) ou a fração padrão na aba QKD.');
      for (const g of gens) {
        if (g.qkdProtectedProbability !== undefined && !(Number(g.qkdProtectedProbability) >= 0 && Number(g.qkdProtectedProbability) <= 1)) { add('err', 'traffic', 'qkdProtectedProbability deve estar em [0, 1] (gerador ' + g.source + '→' + g.destination + ').'); break; }
        if (g.keyRateMbps !== undefined && !(Number(g.keyRateMbps) > 0)) { add('err', 'traffic', 'keyRateMbps deve ser > 0 (gerador ' + g.source + '→' + g.destination + ').'); break; }
      }
      if (isPlainObject(sim.activeMetrics) && sim.activeMetrics.QKDMetrics === false) add('info', 'simulation', 'QKDMetrics desativada: só o QKDStatistics.csv (formato antigo) será gravado.');
    }
    if (isPlainObject(sim) && isPlainObject(net) && isPlainObject(net.qkd)) {
      const qkdOn = !!net.qkd.enabled;
      const qkdCsa = ['qabne', 'qfirstfit'];
      if (qkdOn && sim.coreAndSpectrumAssignment && !qkdCsa.includes(String(sim.coreAndSpectrumAssignment).toLowerCase())) {
        add('err', 'simulation', `Com QKD habilitado a alocação de núcleo e espectro precisa ser um algoritmo do addon (${qkdCsa.join(', ')}); "${sim.coreAndSpectrumAssignment}" não é aceito.`);
      }
      if (!qkdOn && qkdCsa.includes(String(sim.coreAndSpectrumAssignment).toLowerCase())) {
        add('err', 'simulation', `"${sim.coreAndSpectrumAssignment}" só existe no addon QKD; habilite network.qkd ou use um algoritmo clássico.`);
      }
      if (String(sim.integratedRmlsa).toLowerCase() === 'qrmsta' && (!qkdOn || sim.rmlsaType !== 1)) {
        add('err', 'simulation', '"qrmsta" é um RMLSA integrado do addon QKD: exige network.qkd.enabled = true e rmlsaType = 1.');
      }
      if (qkdOn && net.qkd.qchGranularity && !['link', 'span'].includes(net.qkd.qchGranularity)) add('err', 'network', 'qkd.qchGranularity deve ser "link" ou "span".');
      if (qkdOn && net.qkd.wavelengthPolicy && !['fixed', 'grid'].includes(net.qkd.wavelengthPolicy)) add('err', 'network', 'qkd.wavelengthPolicy deve ser "fixed" ou "grid".');
      if (qkdOn && net.qkd.tdmSlots !== undefined && !(Number.isInteger(net.qkd.tdmSlots) && net.qkd.tdmSlots >= 0)) add('err', 'network', 'qkd.tdmSlots deve ser inteiro ≥ 0.');
    }

    // ---------------- physical layer
    if (isPlainObject(phy)) {
      if ('spanLength' in phy && !(phy.spanLength > 0)) add('err', 'physicalLayer', 'spanLength deve ser > 0.');
      if ('fiberLoss' in phy && !(phy.fiberLoss >= 0)) add('err', 'physicalLayer', 'fiberLoss deve ser ≥ 0.');
      const r = phy.qkdReceiver;
      if (isPlainObject(r) && 'receiverOpticalEfficiency' in r && !(r.receiverOpticalEfficiency >= 0 && r.receiverOpticalEfficiency <= 1)) {
        add('err', 'physicalLayer', 'qkdReceiver.receiverOpticalEfficiency deve estar em [0, 1].');
      }
    }
    const qp = model.qkdPhysicalLayer;
    if (isPlainObject(qp) && isPlainObject(qp.detector)) {
      for (const k of ['receiverOpticalEfficiency', 'detectorEfficiency', 'darkCountProbability']) {
        const v = qp.detector[k];
        if (v !== undefined && !(v >= 0 && v <= 1)) add('err', 'qkdPhysicalLayer', 'detector.' + k + ' deve estar em [0, 1].');
      }
    }
    if (isPlainObject(phy) && isPlainObject(qp) && (phy.qkdReceiver !== undefined || phy.qkdPhysical !== undefined)) {
      add('warn', 'physicalLayer', 'Há blocos QKD antigos no physicalLayer e também o arquivo qkdPhysicalLayer: o simulador usa só o qkdPhysicalLayer.');
    }
    return out;
  }

  function issuesList(issues, emptyText) {
    const { el } = App;
    if (!issues.length) return el('div.notice.ok', emptyText || 'Nenhum problema encontrado.');
    const order = { err: 0, warn: 1, info: 2 };
    const label = { err: 'erro', warn: 'aviso', info: 'info' };
    return el('ul.issues', issues.slice().sort((a, b) => order[a.level] - order[b.level]).map((i) =>
      el('li', el('span.tag.' + i.level, label[i.level]), el('span.tag.file', i.file), el('span', i.msg))));
  }

  App.validate = { validateAll, trafficLoad, issuesList };
})(window.App);
