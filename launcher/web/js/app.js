/* Application shell: state, workspace tree, case loading/saving and tabs. */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtDate, fmtBytes, isPlainObject, deepEqual, deepClone, setPath } = App;

  const FILES = ['network', 'physicalLayer', 'simulation', 'traffic', 'others', 'qkdPhysicalLayer'];

  const st = App.state = {
    info: null, catalog: null, tree: null,
    casePath: null, caseData: null,
    base: {}, model: {}, dirty: {}, parseErrors: {}, rawText: {}, viewMode: {},
    tab: 'overview', expanded: new Set(),
  };

  const TABS = [
    { id: 'overview', label: 'Visão geral' },
    { id: 'traffic', label: 'Tráfego', file: 'traffic' },
    { id: 'network', label: 'Rede', file: 'network' },
    { id: 'physicalLayer', label: 'Camada física', file: 'physicalLayer' },
    { id: 'qkdPhysicalLayer', label: 'Camada física QKD', file: 'qkdPhysicalLayer', qkdOnly: true },
    { id: 'simulation', label: 'Simulação', file: 'simulation' },
    { id: 'metrics', label: 'Métricas', file: 'simulation' },
    { id: 'qkd', label: 'QKD', qkdOnly: true },
    { id: 'others', label: 'Others', file: 'others' },
    { sep: true },
    { id: 'review', label: 'Revisar e salvar' },
    { id: 'run', label: 'Executar' },
    { id: 'sweep', label: 'Varredura' },
    { id: 'results', label: 'Resultados' },
  ];

  // ================================================================== serialization

  /** Writes each file in the same style the simulator's own files use. */
  App.serialize = function (name, obj) {
    if (name === 'traffic' && isPlainObject(obj) && Array.isArray(obj.requestGenerators)
      && Object.keys(obj).length === 1) {
      return '{"requestGenerators":[\n' + obj.requestGenerators.map((g) => JSON.stringify(g)).join(',\n') + '\n]}';
    }
    if (name === 'others') {
      const orig = st.caseData && st.caseData.files.others;
      if (!orig || orig.trim().indexOf('\n') < 0) return JSON.stringify(obj);
    }
    return JSON.stringify(obj, null, 2);
  };

  // ================================================================== model & dirty tracking

  App.setValue = function (file, path, value, fromRaw) {
    if (!path.length) st.model[file] = value;
    else setPath(st.model[file], path, value);
    if (!fromRaw) { delete st.rawText[file]; delete st.parseErrors[file]; }
    App.refreshDirty();
  };

  App.refreshDirty = function () {
    for (const f of FILES) {
      st.dirty[f] = !!st.parseErrors[f] || !deepEqual(st.model[f], st.base[f]);
    }
    const d = App.dirtyFiles();
    const badge = document.getElementById('dirtyBadge');
    badge.hidden = !d.length;
    badge.textContent = d.length === 1 ? '1 arquivo alterado' : d.length + ' arquivos alterados';
    document.querySelectorAll('.tab[data-file]').forEach((t) => {
      const f = t.dataset.file;
      t.querySelector('.mod').hidden = !(st.dirty[f] && !st.parseErrors[f]);
      t.querySelector('.errmark').hidden = !st.parseErrors[f];
    });
  };

  App.dirtyFiles = () => FILES.filter((f) => st.dirty[f]);

  window.addEventListener('beforeunload', (e) => {
    if (App.dirtyFiles().length) { e.preventDefault(); e.returnValue = ''; }
  });

  // ================================================================== tree

  App.loadTree = async function () {
    try {
      st.tree = await App.api.tree();
    } catch (e) { App.toast(e.message, 'err'); return; }
    drawTree();
  };

  App.allCases = function () {
    const out = [];
    const walk = (n) => { if (n.isCase) out.push(n); (n.children || []).forEach(walk); };
    if (st.tree) walk(st.tree.root);
    return out;
  };

  App.allFolders = function () {
    const out = [];
    const walk = (n) => { if (!n.isCase) { out.push(n); (n.children || []).forEach(walk); } };
    if (st.tree) walk(st.tree.root);
    return out;
  };

  function drawTree() {
    const box = clear(document.getElementById('tree'));
    if (!st.tree || !st.tree.exists) {
      box.appendChild(el('div.tree-empty', 'Pasta simulations não encontrada: ', el('code', st.tree ? st.tree.simulationsDir : '?'),
        '. Ajuste em Configurações.'));
      return;
    }
    const q = document.getElementById('treeFilter').value.trim().toLowerCase();
    const matches = (n) => !q || n.path.toLowerCase().includes(q) || (n.children || []).some(matches);
    const nodeEl = (n) => {
      if (!matches(n)) return null;
      if (n.isCase) {
        const item = el('div.tree-item' + (n.path === st.casePath ? '.active' : ''), {
          title: n.path + (n.csvCount ? '\nResultados: ' + fmtDate(n.lastResult) : ''),
          on: { click: () => App.openCase(n.path) },
        },
        el('span.tw', ''),
        caseIcon(),
        el('span.label', n.name),
        n.csvCount ? el('span.badge', n.csvCount + ' csv') : null);
        return item;
      }
      const open = q || st.expanded.has(n.path);
      const wrap = el('div');
      wrap.appendChild(el('div.tree-item', {
        on: { click: () => { if (st.expanded.has(n.path)) st.expanded.delete(n.path); else st.expanded.add(n.path); drawTree(); } },
      }, el('span.tw', open ? '▾' : '▸'), folderIcon(), el('span.label', n.name),
      el('span.badge', String(countCases(n)))));
      if (open) {
        const kids = el('div.tree-children');
        (n.children || []).forEach((c) => { const x = nodeEl(c); if (x) kids.appendChild(x); });
        wrap.appendChild(kids);
      }
      return wrap;
    };
    const root = st.tree.root;
    const items = (root.children || []).map(nodeEl).filter(Boolean);
    if (!items.length) box.appendChild(el('div.tree-empty', q ? 'Nenhum caso corresponde ao filtro.' : 'Nenhum caso encontrado. Crie um com “+ Caso”.'));
    items.forEach((i) => box.appendChild(i));
  }

  function countCases(n) { return n.isCase ? 1 : (n.children || []).reduce((s, c) => s + countCases(c), 0); }

  function folderIcon() {
    return App.svg('svg', { class: 'ico', viewBox: '0 0 16 16' },
      App.svg('path', { d: 'M1.5 3.5h4.2l1.4 1.5h7.4v8H1.5z', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.2', 'stroke-linejoin': 'round' }));
  }
  function caseIcon() {
    return App.svg('svg', { class: 'ico', viewBox: '0 0 16 16' },
      App.svg('circle', { cx: 8, cy: 8, r: 6.2, fill: 'none', stroke: 'currentColor', 'stroke-width': '1.2' }),
      App.svg('circle', { cx: 8, cy: 8, r: 1.6, fill: 'currentColor' }));
  }

  function expandTo(path) {
    const parts = path.split('/');
    for (let i = 1; i < parts.length; i++) st.expanded.add(parts.slice(0, i).join('/'));
  }

  // ================================================================== case loading

  App.openCase = async function (path, tab) {
    if (st.casePath && path !== st.casePath && App.dirtyFiles().length
      && !window.confirm('Há alterações não salvas em ' + st.casePath + '. Descartá-las e abrir outro caso?')) return;
    let data;
    try { data = await App.api.readCase(path); } catch (e) { App.toast(e.message, 'err'); return; }
    st.casePath = path;
    st.caseData = data;
    st.base = {}; st.model = {}; st.dirty = {}; st.parseErrors = {}; st.rawText = {};
    for (const f of FILES) {
      const text = data.files[f];
      if (text === null || text === undefined) { st.base[f] = undefined; st.model[f] = undefined; continue; }
      try {
        st.base[f] = JSON.parse(text);
        st.model[f] = deepClone(st.base[f]);
      } catch (e) {
        st.base[f] = undefined; st.model[f] = undefined;
        st.parseErrors[f] = e.message; st.rawText[f] = text;
        st.viewMode[f] = 'json';
      }
    }
    expandTo(path);
    drawTree();
    document.getElementById('welcome').hidden = true;
    document.getElementById('qkdView').hidden = true;
    document.getElementById('figView').hidden = true;
    document.getElementById('caseView').hidden = false;
    const parts = path.split('/');
    document.getElementById('caseName').textContent = parts[parts.length - 1];
    document.getElementById('caseCrumbs').textContent = 'simulations/' + parts.slice(0, -1).map((p) => p + '/').join('');
    if (tab) st.tab = tab;
    if (st.tab === 'qkd' && !App.qkdcase.isQkd()) st.tab = 'overview';
    drawTabs();
    App.qkdcase.drawModeBar();
    App.refreshDirty();
    App.renderTab();
    try { localStorage.setItem('snets.lastCase', path); } catch (e) { /* ignore */ }
  };

  App.reloadCase = async function (force) {
    if (!st.casePath) return;
    if (!force && App.dirtyFiles().length && !window.confirm('Descartar as alterações não salvas e recarregar do disco?')) return;
    const p = st.casePath;
    st.dirty = {};
    st.casePath = null;
    await App.openCase(p);
  };

  /** Refreshes only the list of result files (after a run) without touching edits. */
  App.refreshCaseFiles = async function () {
    if (!st.casePath) return;
    App.results.invalidate(st.casePath);
    if (App.figures) App.figures.invalidate(st.casePath);
    if (!App.dirtyFiles().length) {
      // the simulator rewrites its configuration files when it saves results:
      // reload everything so the next save does not hit a false conflict
      const p = st.casePath, tab = st.tab;
      st.casePath = null;
      await App.openCase(p, tab);
      App.loadTree();
      return;
    }
    try {
      const data = await App.api.readCase(st.casePath);
      st.caseData.csv = data.csv;
      st.caseData.otherFiles = data.otherFiles;
      st.caseData.hasRoutesFile = data.hasRoutesFile;
      App.results.invalidate(st.casePath);
      App.loadTree();
      if (st.tab === 'overview' || st.tab === 'results') App.renderTab();
    } catch (e) { /* ignore */ }
  };

  App.saveAll = async function () {
    const files = App.dirtyFiles();
    for (const f of files) {
      if (st.parseErrors[f]) throw new Error(f + ': JSON inválido');
    }
    const saved = [];
    for (const f of files) {
      try {
        await App.api.writeFile(st.casePath, f, App.serialize(f, st.model[f]), st.caseData.mtimes[f] || 0);
        saved.push(f);
      } catch (e) {
        throw new Error((saved.length ? 'Salvos: ' + saved.join(', ') + '. ' : '') + 'Falha em ' + f + ': ' + e.message);
      }
    }
    App.toast('Salvo: ' + saved.join(', '), 'ok');
    const tab = st.tab;
    st.dirty = {};
    const p = st.casePath; st.casePath = null;
    await App.openCase(p, tab);
  };

  // ================================================================== tabs

  function drawTabs() {
    const box = clear(document.getElementById('tabs'));
    for (const t of TABS) {
      if (t.sep) { box.appendChild(el('span.tab-sep')); continue; }
      if (t.qkdOnly && !App.qkdcase.isQkd()) continue;
      const b = el('button.tab', {
        type: 'button', role: 'tab', 'aria-selected': String(st.tab === t.id),
        'data-file': t.file && t.id !== 'metrics' ? t.file : null, 'data-tab': t.id,
        on: { click: () => App.selectTab(t.id) },
      }, t.label, el('span.mod', { hidden: true, title: 'alterado' }), el('span.errmark', { hidden: true, title: 'JSON inválido' }));
      box.appendChild(b);
    }
  }

  App.drawTabs = drawTabs;

  App.selectTab = function (id) {
    st.tab = id;
    document.querySelectorAll('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.tab === id)));
    App.renderTab();
    App.refreshDirty();
    document.getElementById('main').scrollTop = 0;
  };

  App.renderTab = function () {
    const body = clear(document.getElementById('tabBody'));
    let node;
    try {
      switch (st.tab) {
        case 'overview': node = renderOverview(); break;
        case 'traffic': node = st.parseErrors.traffic ? configTab('traffic') : App.traffic.render(); break;
        case 'network': case 'physicalLayer': case 'simulation': case 'others': case 'qkdPhysicalLayer': node = configTab(st.tab); break;
        case 'metrics': node = renderMetrics(); break;
        case 'qkd': node = App.qkdcase.renderTab(); break;
        case 'review': node = App.review.render(); break;
        case 'run': node = App.run.render(); break;
        case 'sweep': node = App.sweep.render(); break;
        case 'results': node = App.results.render(); break;
        default: node = el('div');
      }
    } catch (e) {
      console.error(e);
      node = el('div.notice.err', 'Erro ao montar esta aba: ' + e.message);
    }
    body.appendChild(node);
  };

  // ------------------------------------------------------------------ config tabs (form/json)

  function configTab(file) {
    const schema = App.schema[file];
    const root = el('div');
    const mode = st.parseErrors[file] ? 'json' : (st.viewMode[file] || 'form');
    const exists = st.model[file] !== undefined || st.parseErrors[file];
    const seg = el('div.seg',
      el('button', { type: 'button', 'aria-pressed': String(mode === 'form'), disabled: !!st.parseErrors[file], on: { click: () => { st.viewMode[file] = 'form'; App.renderTab(); } } }, 'Formulário'),
      el('button', { type: 'button', 'aria-pressed': String(mode === 'json'), on: { click: () => { st.viewMode[file] = 'json'; App.renderTab(); } } }, 'JSON'));
    const restore = el('button.btn.small', {
      type: 'button', disabled: !st.dirty[file],
      on: { click: () => {
        st.model[file] = deepClone(st.base[file]); delete st.parseErrors[file]; delete st.rawText[file];
        App.refreshDirty(); App.renderTab();
      } },
    }, 'Restaurar original');
    root.appendChild(el('div.toolbar', seg, el('span.spacer'),
      el('span.small.muted.mono', 'arquivo: ' + file + (st.caseData.mtimes[file] ? ' · ' + fmtDate(st.caseData.mtimes[file]) : '')), restore));

    if (file === 'qkdPhysicalLayer' || file === 'physicalLayer') { const n = qkdPhysicalNotice(file, exists); if (n) root.appendChild(n); }
    if (!exists) {
      if (file !== 'qkdPhysicalLayer') root.appendChild(el('div.notice.warn', 'Este caso não tem o arquivo ' + file + '.'));
      return root;
    }
    if (file === 'network' && mode === 'form') { root.appendChild(networkSummary()); const ne = App.netEditor.panel(); if (ne) root.appendChild(ne); }
    root.appendChild(mode === 'json' ? el('section.panel', App.forms.renderJsonEditor(file)) : App.forms.renderForm(file));
    return root;
  }

  // ------------------------------------------------------------------ Part 26: qkdPhysicalLayer file

  /** Groups of the qkdPhysicalLayer file and the legacy keys they come from. */
  const QKD_PHY_GROUPS = {
    source: [['meanPhotonNumber', 'qkdPhysical'], ['pulseIntervalPs', 'qkdPhysical'], ['qchPowerDbm', 'qkdPhysical'], ['qchBandwidthGHz', 'qkdPhysical']],
    protocol: [['errorCorrectionInefficiency', 'qkdPhysical'], ['misalignmentError', 'qkdPhysical']],
    detector: [['detectorEfficiency', 'qkdPhysical'], ['darkCountProbability', 'qkdPhysical'], ['detectorGateWindowPs', 'qkdReceiver'], ['receiverOpticalEfficiency', 'qkdReceiver']],
    fiber: [['attenuationFloorDbPerKm', 'qkdPhysical'], ['attenuationQuadraticDbPerKmNm2', 'qkdPhysical'], ['attenuationCenterNm', 'qkdPhysical']],
    crosstalk: [['intercoreCrosstalkEnabled', 'qkdReceiver'], ['receiverTransferModel', 'qkdReceiver']],
  };

  App.qkdPhysicalDefaults = function () {
    const out = {};
    for (const [g, keys] of Object.entries(QKD_PHY_GROUPS)) {
      out[g] = {};
      for (const [k] of keys) out[g][k] = App.schema.qkdPhysicalLayer.fields[g + '.' + k].default;
    }
    return out;
  };

  /** Moves the legacy qkdPhysical / qkdReceiver blocks of physicalLayer to a new qkdPhysicalLayer model. */
  function migrateQkdPhysical() {
    const phy = st.model.physicalLayer || {};
    const out = App.qkdPhysicalDefaults();
    for (const [g, keys] of Object.entries(QKD_PHY_GROUPS)) {
      for (const [k, from] of keys) if (isPlainObject(phy[from]) && phy[from][k] !== undefined) out[g][k] = phy[from][k];
    }
    st.model.qkdPhysicalLayer = out;
    if (isPlainObject(st.model.physicalLayer)) { delete st.model.physicalLayer.qkdPhysical; delete st.model.physicalLayer.qkdReceiver; }
    App.refreshDirty();
    App.toast('Parâmetros QKD movidos para o arquivo qkdPhysicalLayer. Salve o caso para gravar os dois arquivos.', 'ok');
    App.selectTab('qkdPhysicalLayer');
  }

  function qkdPhysicalNotice(file, exists) {
    const phy = st.model.physicalLayer;
    const legacy = isPlainObject(phy) && (phy.qkdPhysical !== undefined || phy.qkdReceiver !== undefined);
    const hasNew = st.model.qkdPhysicalLayer !== undefined;
    const box = (cls, text, ...buttons) => el('div.notice.' + cls, el('div', text), buttons.length ? el('div.toolbar', { style: { marginTop: '8px' } }, ...buttons) : null);
    if (file === 'physicalLayer') {
      if (!legacy) return null;
      return box('warn', hasNew
        ? 'Este caso tem os blocos QKD antigos (qkdPhysical/qkdReceiver) e também o arquivo qkdPhysicalLayer. O simulador usa o qkdPhysicalLayer e ignora estes blocos.'
        : 'Os parâmetros QKD deste caso ainda estão dentro do physicalLayer (formato antigo). Eles agora ficam no arquivo próprio qkdPhysicalLayer (aba Camada física QKD); os valores não mudam.',
      el('button.btn.small', { type: 'button', on: { click: migrateQkdPhysical } }, hasNew ? 'Remover os blocos antigos (mantém o qkdPhysicalLayer)' : 'Mover para a Camada física QKD'));
    }
    if (exists) return null;
    if (legacy) return box('info', 'Os parâmetros QKD deste caso estão no physicalLayer (formato antigo). Mova-os para cá: os valores são os mesmos e o physicalLayer fica só com a camada física clássica.',
      el('button.btn.small.primary', { type: 'button', on: { click: migrateQkdPhysical } }, 'Mover do physicalLayer'));
    return box('info', 'Este caso não tem o arquivo qkdPhysicalLayer. Sem ele o simulador usa os valores de referência (Dibaj et al., 2024).',
      el('button.btn.small.primary', { type: 'button', on: { click: () => { st.model.qkdPhysicalLayer = App.qkdPhysicalDefaults(); App.refreshDirty(); App.renderTab(); } } }, 'Criar com os valores de referência'));
  }

  function networkSummary() {
    const net = st.model.network;
    if (!isPlainObject(net)) return null;
    const links = Array.isArray(net.links) ? net.links : [];
    const slots = [...new Set(links.map((l) => l.slots))];
    const km = links.map((l) => Number(l.size) || 0);
    const issues = App.validate.validateAll(st.model, st.catalog, st.info).filter((i) => i.file === 'network');
    return el('section.panel', { style: { marginBottom: '16px' } },
      el('div.kv',
        kvBox('Nós', fmtNum((net.nodes || []).length)),
        kvBox('Enlaces direcionais', fmtNum(links.length)),
        kvBox('Núcleos', fmtNum((net.cores || []).length)),
        kvBox('Slots por enlace', slots.length === 1 ? fmtNum(slots[0]) : slots.join(', ') || '—'),
        kvBox('Enlace mais longo', km.length ? fmtNum(Math.max(...km)) : '—', 'km'),
        kvBox('Modulações', fmtNum((net.modulations || []).length))),
      issues.length ? el('div', { style: { marginTop: '12px' } }, App.validate.issuesList(issues)) : null);
  }

  function kvBox(k, v, unit) { return el('div', el('div.k', k), el('div.v', v, unit ? el('small', ' ' + unit) : null)); }

  // ------------------------------------------------------------------ metrics tab

  function renderMetrics() {
    const sim = st.model.simulation;
    const root = el('div.stack');
    if (!isPlainObject(sim)) return el('div.notice.err', 'Arquivo simulation ausente ou inválido.');
    if (!isPlainObject(sim.activeMetrics)) {
      root.appendChild(el('div.notice.warn', 'O arquivo simulation não tem o bloco activeMetrics.'));
      root.appendChild(el('button.btn', { type: 'button', on: { click: () => { App.setValue('simulation', ['activeMetrics'], {}); App.renderTab(); } } }, 'Criar activeMetrics'));
      return root;
    }
    const known = (st.catalog && st.catalog.metrics) || [];
    const keys = [...new Set(known.concat(Object.keys(sim.activeMetrics)))];
    const caseName = App.caseNameOf(st.casePath);
    const existing = new Set(((st.caseData && st.caseData.csv) || []).map((c) => c.name));
    const tbody = el('tbody');
    for (const k of keys) {
      const inFile = k in sim.activeMetrics;
      const cb = el('input', { type: 'checkbox', checked: !!sim.activeMetrics[k] });
      cb.addEventListener('change', () => { App.setValue('simulation', ['activeMetrics', k], cb.checked); App.renderTab(); });
      const csv = caseName + '_' + k + '.csv';
      const unknown = known.length && !known.includes(k);
      tbody.appendChild(el('tr',
        el('td', { style: { padding: '6px 10px', width: '1%' } }, cb),
        el('td', { style: { padding: '6px 10px' } }, el('b', k),
          unknown ? el('div.fwarn.small', 'Não existe na classe Metrics do código atual — provavelmente será ignorada.') : null,
          !inFile ? el('div.muted.small', 'Ausente no arquivo (o simulador assume desativada).') : null),
        el('td.mono', { style: { padding: '6px 10px', fontSize: '12px' } }, csv),
        el('td', { style: { padding: '6px 10px' } }, existing.has(csv) ? el('span.tag.info', 'já existe') : '')));
    }
    const on = keys.filter((k) => sim.activeMetrics[k]).length;
    root.appendChild(el('section.panel',
      el('h2', 'Resultados que serão gerados'),
      el('p.lead', 'Cada métrica ativa produz um CSV na pasta do caso ao final da execução, com uma coluna por replicação. ',
        known.length ? 'A lista vem da classe Metrics em SimulationConfig.java.' : 'Código-fonte não encontrado; mostrando apenas as chaves do arquivo.'),
      el('div.toolbar',
        el('button.btn.small', { type: 'button', on: { click: () => { keys.forEach((k) => { st.model.simulation.activeMetrics[k] = true; }); App.refreshDirty(); App.renderTab(); } } }, 'Marcar todas'),
        el('button.btn.small', { type: 'button', on: { click: () => { keys.forEach((k) => { if (k in st.model.simulation.activeMetrics) st.model.simulation.activeMetrics[k] = false; }); App.refreshDirty(); App.renderTab(); } } }, 'Desmarcar todas'),
        el('span.small.muted', on + ' de ' + keys.length + ' ativas')),
      el('div.table-wrap', el('table.grid', el('thead', el('tr', el('th', ''), el('th', 'Métrica'), el('th', 'Arquivo gerado'), el('th', ''))), tbody))));
    return root;
  }

  // ------------------------------------------------------------------ overview

  function renderOverview() {
    const sim = st.model.simulation || {};
    const net = st.model.network || {};
    const tr = App.traffic.summarize(st.model.traffic);
    const issues = App.validate.validateAll(st.model, st.catalog, st.info);
    const errs = issues.filter((i) => i.level === 'err').length;
    const warns = issues.filter((i) => i.level === 'warn').length;
    const d = st.caseData;
    const step = (n, title, status, tab, text) => el('div.step', {
      style: { cursor: 'pointer' }, on: { click: () => App.selectTab(tab) },
    }, el('div.n', n), el('b', title), el('span', text), status ? el('div', { style: { marginTop: '6px' } }, status) : null);
    const qkd = isPlainObject(net.qkd) && net.qkd.enabled;
    const root = el('div.stack');
    root.appendChild(el('div.steps',
      step('1', 'Tráfego', null, 'traffic', fmtNum(tr.gens.length) + ' geradores · ' + fmtNum(tr.base, 0) + ' → ' + fmtNum(tr.base + tr.inc * Math.max(0, (sim.loadPoints || 1) - 1), 0) + ' Erlang'),
      step('2', 'Configuração', null, 'simulation', `${(net.nodes || []).length} nós · ${(net.cores || []).length} núcleos · ${sim.routing || '?'} / ${sim.coreAndSpectrumAssignment || '?'} / ${sim.powerAssignment || '?'}` + (qkd ? ' · QKD ativo' : '')),
      step('3', 'Métricas', null, 'metrics', Object.entries(sim.activeMetrics || {}).filter(([, v]) => v).map(([k]) => k).join(', ') || 'nenhuma'),
      step('4', 'Revisar e salvar',
        el('span.tag.' + (errs ? 'err' : warns ? 'warn' : 'info'), errs ? errs + ' erro(s)' : warns ? warns + ' aviso(s)' : 'ok'),
        'review', App.dirtyFiles().length ? App.dirtyFiles().length + ' arquivo(s) alterado(s)' : 'sem alterações pendentes'),
      step('5', 'Executar', null, 'run', `${sim.loadPoints || '?'} pontos × ${sim.replications || '?'} replicações · ${fmtNum(sim.requests)} req.`),
      step('6', 'Resultados', null, 'results', d.csv.length ? d.csv.length + ' CSV(s) · ' + fmtDate(Math.max(...d.csv.map((c) => c.mtime))) : 'ainda sem resultados')));

    const files = el('table.grid.data',
      el('thead', el('tr', el('th', 'Arquivo'), el('th', 'Modificado'), el('th', 'Estado'))),
      el('tbody', FILES.map((f) => el('tr',
        el('td.mono', f),
        el('td', d.mtimes[f] ? fmtDate(d.mtimes[f]) : '—'),
        el('td', st.parseErrors[f] ? el('span.tag.err', 'JSON inválido') : st.model[f] === undefined ? el('span.tag.warn', 'ausente')
          : st.dirty[f] ? el('span.tag.warn', 'alterado') : el('span.muted', 'ok')))),
      el('tr', el('td.mono', 'kRoutesByPar.txt'), el('td', d.hasRoutesFile ? fmtBytes(d.routesFileSize) : '—'),
        el('td.muted', d.hasRoutesFile ? 'rotas (geradas pelo KSP; lidas como entrada em kfixedroutes)' : 'ausente'))));
    root.appendChild(el('section.panel', el('h2', 'Arquivos do caso'),
      el('p.lead.mono.small', d.absolutePath), el('div.table-wrap', files),
      d.otherFiles.length ? el('p.muted.small', { style: { marginTop: '8px' } }, 'Outros arquivos na pasta: ' + d.otherFiles.map((f) => f.name).join(', ')) : null));
    if (issues.length) root.appendChild(el('section.panel', el('h2', 'Validação'), App.validate.issuesList(issues)));
    return root;
  }

  // ================================================================== dialogs

  function newCaseDialog() {
    const folders = App.allFolders();
    const cases = App.allCases();
    const curParent = st.casePath ? st.casePath.split('/').slice(0, -1).join('/') : '';
    const parent = el('select.input', folders.map((f) => el('option', { value: f.path }, f.path ? 'simulations/' + f.path : 'simulations/ (raiz)')));
    const wanted = st.pendingParent !== undefined ? st.pendingParent : curParent;
    if (st.pendingParent && !folders.find((f) => f.path === st.pendingParent)) {
      parent.appendChild(el('option', { value: st.pendingParent }, 'simulations/' + st.pendingParent));
    }
    delete st.pendingParent;
    parent.value = [...parent.options].some((o) => o.value === wanted) ? wanted : '';
    const name = el('input.input.mono', { type: 'text', placeholder: 'ex.: NSFNet_C4_QKD_XT_qtnd50', required: true });
    const fromSel = el('select.input', el('option', { value: '' }, 'Modelo padrão do launcher (NSFNet clássico, 7 núcleos)'),
      cases.map((c) => el('option', { value: c.path }, c.path)));
    if (st.casePath) fromSel.value = st.casePath;
    const routes = el('input', { type: 'checkbox' });
    const body = el('div.form-grid',
      el('div.field', el('div.flabel', 'Pasta de destino'), parent),
      el('div.field', el('div.flabel', 'Nome do novo caso'), name, el('div.help', 'Os CSVs de resultado serão nomeados <nome>_<Métrica>.csv.')),
      el('div.field', el('div.flabel', 'Copiar configuração de'), fromSel,
        el('div.help', 'Copia network, physicalLayer, simulation, traffic e others. Resultados (CSV) não são copiados.')),
      el('label.check', routes, 'Copiar também kRoutesByPar.txt'));
    App.modal('Novo caso de simulação', body, async () => {
      const r = await App.api.createCase(parent.value, name.value.trim(), fromSel.value, routes.checked);
      await App.loadTree();
      await App.openCase(r.path, 'overview');
      App.toast('Caso criado: ' + r.path, 'ok');
    }, 'Criar caso');
  }

  function newFolderDialog() {
    const folders = App.allFolders();
    const parent = el('select.input', folders.map((f) => el('option', { value: f.path }, f.path ? 'simulations/' + f.path : 'simulations/ (raiz)')));
    const name = el('input.input.mono', { type: 'text', placeholder: 'ex.: QKD_campanha_out26' });
    App.modal('Nova pasta (partição)', el('div.form-grid',
      el('p.muted.small', { style: { margin: 0 } }, 'Pastas agrupam casos (como NSFNet_sims/APAmen). Uma pasta vazia só aparece na lista depois que tiver um caso.'),
      el('div.field', el('div.flabel', 'Dentro de'), parent),
      el('div.field', el('div.flabel', 'Nome'), name)), async () => {
      const r = await App.api.createFolder(parent.value, name.value.trim());
      await App.loadTree();
      App.toast('Pasta criada: ' + r.path + '. Agora crie um caso dentro dela.', 'ok', 6000);
      st.pendingParent = r.path;
      setTimeout(newCaseDialog, 50);
    }, 'Criar pasta');
  }

  function settingsDialog() {
    const s = st.info.settings;
    const proj = el('input.input.mono', { type: 'text', value: s.projectRootSetting, placeholder: s.projectRoot });
    const sims = el('input.input.mono', { type: 'text', value: s.simulationsDirSetting, placeholder: s.simulationsDir });
    const java = el('input.input.mono', { type: 'text', value: s.javaCommandSetting, placeholder: s.javaCommand });
    const jvm = el('input.input.mono', { type: 'text', value: s.jvmArgs, placeholder: 'ex.: -Xmx8g' });
    const jar = el('select.input', el('option', { value: '' }, '(automático: SNetS-SDM-QKD.jar, se existir)'),
      (st.info.jars || []).map((j) => el('option', { value: j.path }, j.name + ' · ' + j.location)));
    jar.value = s.defaultJar || '';
    const consoleCb = el('input', { type: 'checkbox', checked: !!s.runConsole, disabled: !s.consoleSupported });
    App.modal('Configurações', el('div.form-grid',
      el('div.field', el('div.flabel', 'Pasta do projeto SNetS'), proj, el('div.help', 'Vazio = pasta acima do launcher. Usada para achar o código-fonte (catálogo de algoritmos) e target/.')),
      el('div.field', el('div.flabel', 'Pasta simulations'), sims, el('div.help', 'Vazio = <projeto>/simulations.')),
      el('div.field', el('div.flabel', 'JAR padrão'), jar),
      el('div.field', el('div.flabel', 'Comando java'), java, el('div.help', 'Vazio = o mesmo Java que está rodando o launcher (' + st.info.javaVersion + ').')),
      el('div.field', el('div.flabel', 'Argumentos da JVM padrão'), jvm),
      el('label.check', consoleCb, ' Abrir uma janela de console para cada execução (fechar a janela cancela a execução)'),
      !s.consoleSupported ? el('div.help', 'Janelas de console por execução só existem no Windows.') : null,
      el('p.muted.small', { style: { margin: 0 } }, 'Salvo em ', el('code', s.launcherDir + (s.launcherDir.includes('\\') ? '\\' : '/') + 'launcher-settings.properties'))), async () => {
      await App.api.saveSettings({ projectRoot: proj.value, simulationsDir: sims.value, javaCommand: java.value, jvmArgs: jvm.value, defaultJar: jar.value, runConsole: consoleCb.checked ? 'true' : 'false' });
      await init(true);
      App.toast('Configurações salvas', 'ok');
    }, 'Salvar');
  }

  // ================================================================== welcome

  function renderWelcome() {
    const box = clear(document.getElementById('welcome'));
    const cases = App.allCases();
    const recent = cases.filter((c) => c.csvCount).sort((a, b) => b.lastResult - a.lastResult).slice(0, 6);
    box.append(
      el('h1', 'Configurar e executar simulações do SNetS'),
      el('p', 'Escolha um caso na lista à esquerda ou crie um novo. O launcher só lê e grava os arquivos de configuração da pasta simulations e executa o .jar do simulador — o código do simulador não é alterado.'),
      el('div.steps',
        el('div.step', el('div.n', '1'), el('b', 'Caso'), el('span', 'Abra um caso existente ou crie um copiando a configuração de outro.')),
        el('div.step', el('div.n', '2'), el('b', 'Tráfego'), el('span', 'Reaproveite o tráfego de outro caso ou gere um novo a partir da rede.')),
        el('div.step', el('div.n', '3'), el('b', 'Configuração'), el('span', 'Edite network, physicalLayer, simulation e others em formulário ou JSON.')),
        el('div.step', el('div.n', '4'), el('b', 'Métricas'), el('span', 'Escolha quais CSVs de resultado serão gerados.')),
        el('div.step', el('div.n', '5'), el('b', 'Executar'), el('span', 'Rode direto daqui com log ao vivo ou gere um .bat.')),
        el('div.step', el('div.n', '6'), el('b', 'Resultados'), el('span', 'Gráficos com média e IC 95 %, comparando casos ou varreduras.')),
        el('div.step', { style: { cursor: 'pointer' }, on: { click: () => document.getElementById('btnFigures').click() } },
          el('div.n', '▦'), el('b', 'Gráficos'), el('span', 'Figuras com vários casos (linhas, causas de bloqueio, enlaces, núcleos), estilo editável e exportação SVG/PNG.')),
        el('div.step', { style: { cursor: 'pointer' }, on: { click: () => document.getElementById('btnQkd').click() } },
          el('div.n', 'QKD'), el('b', 'Verificação QKD'), el('span', 'Verificadores do addon e reprodução do artigo-base (física, traces e SNetS nativo).'))),
      el('section.panel',
        el('h2', 'Ambiente'),
        el('div.kv',
          kvBox('Casos encontrados', fmtNum(cases.length)),
          kvBox('JARs', fmtNum((st.info.jars || []).length)),
          kvBox('Algoritmos no catálogo', fmtNum(Object.values((st.catalog && st.catalog.options) || {}).reduce((s, a) => s + a.length, 0))),
          kvBox('Java', st.info.javaVersion),
          kvBox('Processadores', fmtNum(st.info.cpus))),
        st.catalog && !st.catalog.sourceAvailable ? el('div.notice.warn', { style: { marginTop: '12px' } },
          'Código-fonte do simulador não encontrado em ' + st.info.settings.projectRoot + '/src/main/java. As listas de algoritmos ficarão como texto livre.') : null),
      recent.length ? el('section.panel', el('h2', 'Resultados recentes'),
        el('div.table-wrap', el('table.grid.data',
          el('thead', el('tr', el('th', 'Caso'), el('th', 'CSVs'), el('th', 'Gerados em'))),
          el('tbody', recent.map((c) => el('tr', { style: { cursor: 'pointer' }, on: { click: () => App.openCase(c.path, 'results') } },
            el('td.mono', c.path), el('td.num', String(c.csvCount)), el('td', fmtDate(c.lastResult)))))))) : null);
  }

  // ================================================================== init

  async function init(keepCase) {
    try {
      const [info, catalog] = await Promise.all([App.api.info(), App.api.catalog()]);
      st.info = info; st.catalog = catalog;
    } catch (e) {
      App.toast(e.message, 'err');
      return;
    }
    document.getElementById('projectPath').textContent = st.info.settings.simulationsDir;
    document.getElementById('projectPath').title = st.info.settings.simulationsDir;
    await App.loadTree();
    renderWelcome();
    if (keepCase && st.casePath) { App.renderTab(); return; }
    let last = null;
    try { last = localStorage.getItem('snets.lastCase'); } catch (e) { /* ignore */ }
    if (last && App.allCases().find((c) => c.path === last)) await App.openCase(last);
  }

  // retractable case sidebar
  const applySidebar = (collapsed) => {
    document.body.classList.toggle('sidebar-collapsed', collapsed);
    const b = document.getElementById('btnSidebar');
    b.textContent = collapsed ? '»' : '«';
    b.title = collapsed ? 'Mostrar a pasta simulations' : 'Recolher a pasta simulations';
    try { localStorage.setItem('snets.sidebarCollapsed', collapsed ? '1' : '0'); } catch (e) { /* ignore */ }
    if (st.casePath && (st.tab === 'results')) App.renderTab();
  };
  document.getElementById('btnSidebar').addEventListener('click', () => applySidebar(!document.body.classList.contains('sidebar-collapsed')));
  try { if (localStorage.getItem('snets.sidebarCollapsed') === '1') applySidebar(true); } catch (e) { /* ignore */ }

  document.getElementById('btnNewCase').addEventListener('click', newCaseDialog);
  document.getElementById('btnNewFolder').addEventListener('click', newFolderDialog);
  document.getElementById('btnRefreshTree').addEventListener('click', () => App.loadTree());
  document.getElementById('treeFilter').addEventListener('input', App.debounce(drawTree, 120));
  document.getElementById('btnSettings').addEventListener('click', settingsDialog);
  document.getElementById('btnRuns').addEventListener('click', App.run.openDrawer);
  function leaveCase() {
    if (st.casePath && App.dirtyFiles().length && !window.confirm('Há alterações não salvas em ' + st.casePath + '. Sair do caso mesmo assim?')) return false;
    if (st.casePath) { st.dirty = {}; }
    st.casePath = null;
    drawTree();
    return true;
  }
  App.showFigures = function () {
    if (!leaveCase()) return;
    document.getElementById('welcome').hidden = true;
    document.getElementById('caseView').hidden = true;
    document.getElementById('qkdView').hidden = true;
    document.getElementById('figView').hidden = false;
    document.getElementById('main').scrollTop = 0;
    App.figures.render();
  };
  document.getElementById('btnFigures').addEventListener('click', () => App.showFigures());
  document.getElementById('btnQkd').addEventListener('click', () => {
    if (!leaveCase()) return;
    document.getElementById('welcome').hidden = true;
    document.getElementById('caseView').hidden = true;
    document.getElementById('figView').hidden = true;
    document.getElementById('qkdView').hidden = false;
    document.getElementById('main').scrollTop = 0;
    App.qkd.render();
  });
  document.getElementById('btnCloseRuns').addEventListener('click', App.run.closeDrawer);
  // Clicking outside the runs drawer (or pressing Esc) closes it; dialogs opened from it are ignored.
  document.addEventListener('mousedown', (ev) => {
    const d = document.getElementById('runsDrawer');
    if (!d || d.hidden) return;
    const t = ev.target;
    if (!(t instanceof Element) || !document.body.contains(t)) return;
    if (d.contains(t) || t.closest('#btnRuns') || t.closest('dialog[open], .modal, .toast')) return;
    App.run.closeDrawer();
  }, true);
  document.addEventListener('keydown', (ev) => {
    const d = document.getElementById('runsDrawer');
    if (ev.key === 'Escape' && d && !d.hidden && !document.querySelector('dialog[open]')) App.run.closeDrawer();
  });
  document.getElementById('btnGoReview').addEventListener('click', () => App.selectTab('review'));
  document.getElementById('btnReload').addEventListener('click', () => App.reloadCase(false));
  document.getElementById('btnOpenFolder').addEventListener('click', () => App.api.openFolder(st.casePath).catch((e) => App.toast(e.message, 'err')));
  window.addEventListener('resize', App.debounce(() => { if (st.tab === 'results' && st.casePath) App.renderTab(); }, 250));

  init().then(() => App.run.pollList());
})(window.App);
