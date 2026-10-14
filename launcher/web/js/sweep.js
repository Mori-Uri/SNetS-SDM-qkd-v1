/*
 * "Varredura" tab: creates a set of cases from the current one by varying one
 * or two configuration values (e.g. network qkd.qtndKm = 5..50, or
 * physicalLayer qkdReceiver.receiverOpticalEfficiency x receiverTransferModel).
 * Each generated case is a normal SNetS folder (copy of the current case with
 * the changed values), so it can be run in the queue, from a .bat/.sh or
 * compared in the Resultados tab ("Parâmetro dos casos").
 */
(function (App) {
  'use strict';
  const { el, clear, isPlainObject, deepClone, parseNumber } = App;

  const FILES = ['network', 'physicalLayer', 'qkdPhysicalLayer', 'simulation', 'others'];
  const PRESETS = [
    { label: 'QTND (km)', file: 'network', path: 'qkd.qtndKm', values: '5,10,15,20,25,30,35,40,45,50' },
    { label: 'η_f do receptor QKD', file: 'qkdPhysicalLayer', path: 'detector.receiverOpticalEfficiency', values: '0.1,0.25,0.5,0.75,1.0' },
    { label: 'Modelo de transferência do XT', file: 'qkdPhysicalLayer', path: 'crosstalk.receiverTransferModel', values: 'receiver_referenced,distributed,attenuated' },
    { label: 'Janela de detecção T_g (ps)', file: 'qkdPhysicalLayer', path: 'detector.detectorGateWindowPs', values: '50,100,200,500' },
    { label: 'Fração protegida por QKD', file: 'simulation', path: 'qkdProtectedRequestProbability', values: '0.05,0.1,0.2,0.5' },
    { label: 'Coeficiente de acoplamento h', file: 'physicalLayer', path: 'couplingCoefficient', values: '0.006,0.012,0.024' },
    { label: 'Algoritmo de núcleo/espectro', file: 'simulation', path: 'coreAndSpectrumAssignment', values: 'qabne,qfirstfit' },
  ];

  /** Scalar leaves (numbers, strings, booleans) of a JSON object as dotted paths. */
  function leaves(obj, prefix, out) {
    if (!isPlainObject(obj)) return out;
    for (const k of Object.keys(obj)) {
      const v = obj[k];
      const p = prefix ? prefix + '.' + k : k;
      if (isPlainObject(v)) leaves(v, p, out);
      else if (!Array.isArray(v)) out.push(p);
    }
    return out;
  }

  function getAt(obj, path) {
    return path.split('.').reduce((o, k) => (o === null || o === undefined ? undefined : o[k]), obj);
  }

  function setAt(obj, path, value) {
    const parts = path.split('.');
    let o = obj;
    for (let i = 0; i < parts.length - 1; i++) {
      if (!isPlainObject(o[parts[i]])) o[parts[i]] = {};
      o = o[parts[i]];
    }
    o[parts[parts.length - 1]] = value;
  }

  /** Parses "1,2,3", "5:50:5" (start:stop:step) or text values, typed like the current value. */
  function parseValues(text, current) {
    const t = String(text || '').trim();
    if (!t) return [];
    const range = /^\s*([-+0-9.eE]+)\s*:\s*([-+0-9.eE]+)\s*:\s*([-+0-9.eE]+)\s*$/.exec(t);
    if (range) {
      const a = Number(range[1]), b = Number(range[2]), st = Number(range[3]);
      if (!(st > 0) || !Number.isFinite(a) || !Number.isFinite(b)) return null;
      const out = [];
      for (let v = a; v <= b + st * 1e-9; v += st) out.push(+v.toPrecision(12));
      return out.length <= 500 ? out : null;
    }
    return t.split(/[;,]/).map((x) => x.trim()).filter((x) => x !== '').map((x) => {
      if (typeof current === 'number') { const n = parseNumber(x); return Number.isNaN(n) ? null : n; }
      if (typeof current === 'boolean') return x === 'true' || x === '1' || x === 'sim';
      return x;
    });
  }

  function slug(v) {
    return String(v).replace(/\./g, 'p').replace(/[^A-Za-z0-9_+-]+/g, '_').slice(0, 40);
  }

  function render() {
    const st = App.state;
    const root = el('div.stack');
    if (App.dirtyFiles().length) {
      root.appendChild(el('div.notice.warn', 'Há alterações não salvas: a varredura usa a configuração atual da tela (inclusive o que não foi salvo) como base.'));
    }
    const caseName = App.caseNameOf(st.casePath);
    const parent = st.casePath.split('/').slice(0, -1).join('/');
    const dims = [makeDim(0), makeDim(1)];
    const target = el('input.input.mono', { type: 'text', value: (parent ? parent + '/' : '') + caseName + '_sweep' });
    const preview = el('div');
    const createBtn = el('button.btn.primary', { type: 'button' }, 'Criar casos');
    const queueBtn = el('button.btn', { type: 'button', disabled: true }, 'Executar os casos criados (fila)');
    let created = [];

    function makeDim(i) {
      const fileSel = el('select.input', FILES.map((f) => el('option', { value: f }, f)));
      const pathSel = el('select.input');
      const values = el('input.input.mono', { type: 'text', placeholder: 'ex.: 5,10,15  ou  5:50:5' });
      const enabled = el('input', { type: 'checkbox', checked: i === 0 });
      const cur = el('div.help');
      const fillPaths = () => {
        clear(pathSel);
        const obj = st.model[fileSel.value];
        for (const p of leaves(obj, '', [])) pathSel.appendChild(el('option', { value: p }, p));
      };
      const showCurrent = () => {
        const v = getAt(st.model[fileSel.value], pathSel.value);
        cur.textContent = 'Valor atual: ' + JSON.stringify(v);
      };
      fileSel.addEventListener('change', () => { fillPaths(); showCurrent(); update(); });
      pathSel.addEventListener('change', () => { showCurrent(); update(); });
      values.addEventListener('input', App.debounce(update, 200));
      enabled.addEventListener('change', update);
      const presets = el('select.input', el('option', { value: '' }, 'Atalhos…'),
        PRESETS.filter((p) => getAt(st.model[p.file], p.path) !== undefined).map((p, k) => el('option', { value: String(PRESETS.indexOf(p)) }, p.label)));
      presets.addEventListener('change', () => {
        const p = PRESETS[Number(presets.value)];
        if (!p) return;
        fileSel.value = p.file; fillPaths(); pathSel.value = p.path; values.value = p.values;
        showCurrent(); enabled.checked = true; presets.value = ''; update();
      });
      fillPaths();
      if (i === 0) {
        const qtnd = PRESETS[0];
        if (getAt(st.model.network, qtnd.path) !== undefined) { fileSel.value = 'network'; fillPaths(); pathSel.value = qtnd.path; values.value = qtnd.values; }
      }
      showCurrent();
      const node = el('div.panel', { style: { padding: '12px' } },
        el('div.toolbar', { style: { marginBottom: '8px' } }, el('label.check', enabled, el('b', i === 0 ? 'Parâmetro 1' : 'Parâmetro 2 (combinação cartesiana)')),
          el('span.spacer'), el('div', { style: { minWidth: '220px' } }, presets)),
        el('div.fields',
          el('div.field', el('div.flabel', 'Arquivo'), fileSel),
          el('div.field', el('div.flabel', 'Campo'), pathSel, cur),
          el('div.field', el('div.flabel', 'Valores'), values, el('div.help', 'Lista separada por vírgulas ou início:fim:passo.'))));
      return { node, fileSel, pathSel, values, enabled };
    }

    function combos() {
      const active = dims.filter((d) => d.enabled.checked && d.pathSel.value);
      const lists = [];
      for (const d of active) {
        const current = getAt(st.model[d.fileSel.value], d.pathSel.value);
        const vals = parseValues(d.values.value, current);
        if (!vals || !vals.length || vals.some((v) => v === null)) return { error: 'Valores inválidos para ' + d.pathSel.value };
        lists.push(vals.map((v) => ({ file: d.fileSel.value, path: d.pathSel.value, value: v })));
      }
      if (!lists.length) return { error: 'Escolha ao menos um parâmetro.' };
      let out = [[]];
      for (const l of lists) out = out.flatMap((c) => l.map((x) => c.concat([x])));
      if (out.length > 200) return { error: out.length + ' casos é demais (limite 200).' };
      return { list: out };
    }

    function nameFor(c) {
      return caseName + '__' + c.map((x) => x.path.split('.').slice(-1)[0] + '_' + slug(x.value)).join('__');
    }

    function update() {
      clear(preview);
      const r = combos();
      createBtn.disabled = !!r.error;
      if (r.error) { preview.appendChild(el('div.fwarn.small', r.error)); return; }
      preview.append(el('p.small', el('b', r.list.length + ' caso(s)'), ' serão criados em ', el('code', 'simulations/' + target.value.trim()), ':'),
        el('div.table-wrap', { style: { maxHeight: '220px' } }, el('table.grid.data',
          el('thead', el('tr', el('th', 'Caso'), r.list[0].map((x) => el('th', x.file + ': ' + x.path)))),
          el('tbody', r.list.map((c) => el('tr', el('td.mono', nameFor(c)), c.map((x) => el('td.mono', String(x.value)))))))));
    }
    target.addEventListener('input', App.debounce(update, 200));

    createBtn.addEventListener('click', async () => {
      const r = combos();
      if (r.error) return;
      const dest = target.value.trim().replace(/\\/g, '/').replace(/\/+$/, '');
      createBtn.disabled = true;
      created = [];
      try {
        // ensure the destination folder exists (create nested parts)
        const parts = dest.split('/').filter(Boolean);
        let acc = '';
        const known = new Set(App.allFolders().map((f) => f.path));
        for (const p of parts) {
          const next = acc ? acc + '/' + p : p;
          if (!known.has(next)) { try { await App.api.createFolder(acc, p); } catch (e) { if (!/existe/.test(e.message)) throw e; } }
          acc = next;
        }
        for (const c of r.list) {
          const name = nameFor(c);
          const res = await App.api.createCase(dest, name, st.casePath, false);
          const changed = {};
          for (const x of c) {
            if (!changed[x.file]) changed[x.file] = deepClone(st.model[x.file]);
            setAt(changed[x.file], x.path, x.value);
          }
          // the base case may have unsaved edits: write every config file from the current model
          for (const f of ['network', 'physicalLayer', 'simulation', 'traffic', 'others']) {
            const obj = changed[f] || st.model[f];
            if (obj === undefined) continue;
            await App.api.writeFile(res.path, f, App.serialize(f, obj), 0);
          }
          created.push(res.path);
        }
        App.toast(created.length + ' casos criados em simulations/' + dest, 'ok', 6000);
        queueBtn.disabled = false;
        await App.loadTree();
      } catch (e) {
        App.toast('Falha após ' + created.length + ' caso(s): ' + e.message, 'err');
      } finally { createBtn.disabled = false; }
    });

    queueBtn.addEventListener('click', async () => {
      if (!created.length) return;
      const jar = App.run.defaultJar();
      try {
        await App.api.queue(created, jar, (st.info && st.info.settings.jvmArgs) || '');
        App.toast(created.length + ' casos na fila de execução', 'ok');
        App.run.pollList();
        App.run.openDrawer();
      } catch (e) { App.toast(e.message, 'err'); }
    });

    root.appendChild(el('section.panel',
      el('h2', 'Varredura de parâmetros'),
      el('p.lead', 'Cria cópias deste caso variando um ou dois campos de configuração. Cada cópia é um caso comum do SNetS: pode ser executada na fila, por .bat/.sh, e comparada na aba Resultados com o eixo x "Parâmetro dos casos".'),
      el('div.stack', dims[0].node, dims[1].node),
      el('div.field', { style: { marginTop: '14px' } }, el('div.flabel', 'Pasta de destino (dentro de simulations)'), target),
      el('div', { style: { marginTop: '12px' } }, preview),
      el('div.toolbar', { style: { marginTop: '12px', marginBottom: 0 } }, createBtn, queueBtn)));
    update();
    return root;
  }

  App.sweep = { render, parseValues };
})(window.App);
