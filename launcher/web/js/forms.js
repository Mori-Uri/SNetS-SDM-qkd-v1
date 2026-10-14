/*
 * Schema-tolerant editor for the JSON configuration files.
 * Every field present in the file is rendered (known ones with friendly
 * labels, unknown ones under "Campos adicionais") and every edit is applied
 * to the in-memory model, so unknown fields are never lost on save.
 */
(function (App) {
  'use strict';
  const { el, clear, isPlainObject, getPath, deepEqual, parseNumber, fmtNum } = App;

  function hintFor(schema, path) {
    if (!schema) return {};
    return schema.fields[path.join('.')] || {};
  }

  function labelNode(key, hint, path) {
    return el('div.flabel',
      el('span', hint.label || key, hint.unit ? el('span.unit', ' (' + hint.unit + ')') : null),
      el('span.key', hint.label ? path.join('.') : '\u00a0'));
  }

  function markChanged(fileKey, path, node) {
    const base = App.state.base[fileKey];
    const cur = App.state.model[fileKey];
    const changed = base !== undefined && !deepEqual(getPath(base, path), getPath(cur, path));
    node.classList.toggle('changed', changed);
  }

  function set(fileKey, path, value, fieldNode) {
    App.setValue(fileKey, path, value);
    if (fieldNode) markChanged(fileKey, path, fieldNode);
  }

  // ---------------------------------------------------------------- scalar inputs

  function numberInput(fileKey, path, value, hint, fieldNode) {
    const inp = el('input.input.num', { type: 'text', inputmode: 'decimal', value: formatForInput(value), spellcheck: false });
    const warn = el('div.fwarn', { hidden: true });
    inp.addEventListener('input', () => {
      const v = parseNumber(inp.value);
      let msg = '';
      if (Number.isNaN(v)) msg = 'Número inválido';
      else if (hint.type === 'int' && !Number.isInteger(v)) msg = 'Deve ser inteiro';
      else if (hint.type === 'prob' && (v < 0 || v > 1)) msg = 'Deve estar entre 0 e 1';
      else if (hint.min !== undefined && v < hint.min) msg = 'Mínimo: ' + hint.min;
      inp.classList.toggle('invalid', !!msg);
      warn.hidden = !msg; warn.textContent = msg;
      if (!Number.isNaN(v)) set(fileKey, path, v, fieldNode);
    });
    return [inp, warn];
  }

  function formatForInput(v) {
    if (typeof v !== 'number') return v === null || v === undefined ? '' : String(v);
    const a = Math.abs(v);
    if (Number.isInteger(v) && a < 1e15 && a % 1e6 !== 0) return String(v);
    if (a !== 0 && (a >= 1e7 || a < 1e-4)) return v.toExponential().replace('e+', 'E').replace('e', 'E');
    return String(v);
  }

  function selectInput(fileKey, path, value, options, labels, fieldNode, allowOther, notes) {
    const wrap = el('div.with-other');
    const sel = el('select.input');
    const known = options.map(String);
    for (const o of options) {
      const lab = labels && labels[o] !== undefined ? labels[o] : String(o);
      const note = notes && notes[o] ? ' — ' + notes[o] : '';
      sel.appendChild(el('option', { value: String(o) }, lab + note));
    }
    const isKnown = known.includes(String(value));
    if (!isKnown && value !== undefined && value !== null && value !== '') {
      sel.appendChild(el('option', { value: String(value) }, String(value) + ' (valor atual — não encontrado no código)'));
    }
    if (allowOther) sel.appendChild(el('option', { value: '__other__' }, 'Outro valor…'));
    sel.value = String(value);
    const other = el('input.input', { type: 'text', value: String(value === undefined ? '' : value), hidden: true, placeholder: 'nome do algoritmo' });
    const warn = el('div.fwarn', { hidden: isKnown || !options.length }, options.length ? 'Valor não consta nas constantes do código-fonte. Confirme o nome.' : '');
    const numeric = typeof value === 'number';
    sel.addEventListener('change', () => {
      if (sel.value === '__other__') { other.hidden = false; other.focus(); return; }
      other.hidden = true;
      const v = numeric ? Number(sel.value) : sel.value;
      warn.hidden = known.includes(String(v));
      set(fileKey, path, v, fieldNode);
    });
    other.addEventListener('input', () => {
      const v = numeric ? parseNumber(other.value) : other.value.trim();
      warn.hidden = known.includes(String(v));
      set(fileKey, path, v, fieldNode);
    });
    wrap.append(sel, other);
    return [wrap, warn];
  }

  function boolInput(fileKey, path, value, fieldNode, text) {
    const cb = el('input', { type: 'checkbox', checked: !!value });
    cb.addEventListener('change', () => set(fileKey, path, cb.checked, fieldNode));
    return el('label.check', cb, text || (value ? 'ativado' : 'desativado'));
  }

  function textInput(fileKey, path, value, fieldNode) {
    const inp = el('input.input', { type: 'text', value: value === null ? '' : String(value), spellcheck: false });
    inp.addEventListener('input', () => set(fileKey, path, inp.value, fieldNode));
    return inp;
  }

  function listInput(fileKey, path, value, fieldNode) {
    const allNum = value.every((x) => typeof x === 'number');
    const inp = el('input.input' + (allNum ? '.num' : ''), { type: 'text', value: value.map(String).join(', '), spellcheck: false });
    const warn = el('div.fwarn', { hidden: true });
    inp.addEventListener('input', () => {
      const parts = inp.value.split(/[,;]\s*|\s+/).map((s) => s.trim()).filter((s) => s !== '');
      let arr = parts;
      if (allNum) {
        arr = parts.map(parseNumber);
        const bad = arr.some(Number.isNaN);
        inp.classList.toggle('invalid', bad);
        warn.hidden = !bad; warn.textContent = 'Lista de números inválida';
        if (bad) return;
      }
      set(fileKey, path, arr, fieldNode);
    });
    return [inp, warn];
  }

  function jsonInput(fileKey, path, value, fieldNode) {
    const ta = el('textarea.input', { rows: 3, spellcheck: false }, JSON.stringify(value));
    const warn = el('div.fwarn', { hidden: true });
    ta.addEventListener('input', () => {
      try { const v = JSON.parse(ta.value); ta.classList.remove('invalid'); warn.hidden = true; set(fileKey, path, v, fieldNode); }
      catch (e) { ta.classList.add('invalid'); warn.hidden = false; warn.textContent = 'JSON inválido'; }
    });
    return [ta, warn];
  }

  // ---------------------------------------------------------------- tables (arrays of objects)

  function tableEditor(fileKey, path, rows, fieldNode) {
    const box = el('div');
    const draw = () => {
      clear(box);
      const arr = getPath(App.state.model[fileKey], path) || [];
      const cols = [];
      for (const r of arr) if (isPlainObject(r)) for (const k of Object.keys(r)) if (!cols.includes(k)) cols.push(k);
      const kinds = {};
      for (const c of cols) {
        const sample = arr.map((r) => r && r[c]).find((v) => v !== undefined && v !== null);
        kinds[c] = Array.isArray(sample) ? 'list' : isPlainObject(sample) ? 'json' : typeof sample;
      }
      const limit = 400;
      const thead = el('thead', el('tr', el('th', '#'), cols.map((c) => el('th', c)), el('th', '')));
      const tbody = el('tbody');
      arr.slice(0, limit).forEach((row, i) => {
        const tr = el('tr', el('td.idx', String(i)));
        for (const c of cols) tr.appendChild(el('td', cellInput(fileKey, path.concat([i, c]), row[c], kinds[c], fieldNode)));
        tr.appendChild(el('td.rowctl', el('button.btn.small.ghost', {
          type: 'button', title: 'Remover linha',
          on: { click: () => { const a = getPath(App.state.model[fileKey], path); a.splice(i, 1); App.setValue(fileKey, path, a); markChanged(fileKey, path, fieldNode); draw(); } },
        }, '✕')));
        tbody.appendChild(tr);
      });
      box.appendChild(el('div.table-wrap', el('table.grid', thead, tbody)));
      const ctl = el('div.toolbar', { style: { marginTop: '8px', marginBottom: '0' } },
        el('button.btn.small', {
          type: 'button',
          on: { click: () => {
            const a = getPath(App.state.model[fileKey], path);
            const last = a[a.length - 1];
            a.push(last ? JSON.parse(JSON.stringify(last)) : {});
            App.setValue(fileKey, path, a); markChanged(fileKey, path, fieldNode); draw();
          } },
        }, '+ Linha (cópia da última)'),
        el('span.muted.small', arr.length + ' linha(s)' + (arr.length > limit ? ' — mostrando ' + limit + '; use o modo JSON para as demais' : '')));
      box.appendChild(ctl);
    };
    draw();
    return box;
  }

  function cellInput(fileKey, path, value, kind, fieldNode) {
    if (kind === 'boolean') {
      const cb = el('input', { type: 'checkbox', checked: !!value, style: { margin: '6px 8px' } });
      cb.addEventListener('change', () => set(fileKey, path, cb.checked, fieldNode));
      return cb;
    }
    const text = value === undefined || value === null ? '' : kind === 'list' ? value.join(', ')
      : kind === 'json' ? JSON.stringify(value) : kind === 'number' ? formatForInput(value) : String(value);
    const inp = el('input', { type: 'text', value: text, spellcheck: false });
    inp.addEventListener('input', () => {
      let v; let bad = false;
      if (kind === 'number') { v = parseNumber(inp.value); bad = Number.isNaN(v); }
      else if (kind === 'list') {
        const parts = inp.value.split(/[,;\s]+/).filter((s) => s !== '');
        const nums = parts.map(parseNumber);
        v = nums.every((n) => !Number.isNaN(n)) ? nums : parts;
      } else if (kind === 'json') { try { v = JSON.parse(inp.value); } catch (e) { bad = true; } }
      else v = inp.value;
      inp.classList.toggle('invalid', bad);
      if (!bad) set(fileKey, path, v, fieldNode);
    });
    return inp;
  }

  // ---------------------------------------------------------------- open objects (key → value)

  function openObjectEditor(fileKey, path, fieldNode) {
    const box = el('div');
    const draw = () => {
      clear(box);
      const obj = getPath(App.state.model[fileKey], path) || {};
      const tbody = el('tbody');
      for (const k of Object.keys(obj)) {
        const v = obj[k];
        const keyInp = el('input', { type: 'text', value: k, spellcheck: false });
        keyInp.addEventListener('change', () => {
          const nk = keyInp.value.trim();
          if (!nk || nk === k) { keyInp.value = k; return; }
          const o = getPath(App.state.model[fileKey], path);
          const rebuilt = {};
          for (const [kk, vv] of Object.entries(o)) rebuilt[kk === k ? nk : kk] = vv;
          App.setValue(fileKey, path, rebuilt); markChanged(fileKey, path, fieldNode); draw();
        });
        const kind = Array.isArray(v) ? 'list' : isPlainObject(v) ? 'json' : typeof v;
        tbody.appendChild(el('tr',
          el('td', keyInp),
          el('td', cellInput(fileKey, path.concat([k]), v, kind, fieldNode)),
          el('td.rowctl', el('button.btn.small.ghost', {
            type: 'button', title: 'Remover',
            on: { click: () => { const o = getPath(App.state.model[fileKey], path); delete o[k]; App.setValue(fileKey, path, o); markChanged(fileKey, path, fieldNode); draw(); } },
          }, '✕'))));
      }
      box.appendChild(el('div.table-wrap', el('table.grid',
        el('thead', el('tr', el('th', 'chave'), el('th', 'valor'), el('th', ''))), tbody)));
      box.appendChild(el('div.toolbar', { style: { marginTop: '8px', marginBottom: '0' } },
        el('button.btn.small', {
          type: 'button',
          on: { click: () => {
            const o = getPath(App.state.model[fileKey], path) || {};
            let n = 'novaVariavel', i = 1;
            while (n in o) n = 'novaVariavel' + (++i);
            o[n] = '';
            App.setValue(fileKey, path, o); markChanged(fileKey, path, fieldNode); draw();
          } },
        }, '+ Entrada')));
    };
    draw();
    return box;
  }

  // ---------------------------------------------------------------- field dispatcher

  function field(fileKey, path, value, schema) {
    const hint = hintFor(schema, path);
    const key = path[path.length - 1];
    const node = el('div.field');
    const catalog = App.state.catalog || { options: {}, notes: {} };

    if (hint.openObject && isPlainObject(value)) {
      node.classList.add('wide');
      node.append(labelNode(key, hint, path), hint.help ? el('div.help', hint.help) : null,
        openObjectEditor(fileKey, path, node));
    } else if (isPlainObject(value)) {
      node.classList.add('wide');
      const inner = el('div.fields.subobj');
      for (const k of Object.keys(value)) inner.appendChild(field(fileKey, path.concat([k]), value[k], schema));
      node.append(labelNode(key, hint, path), hint.help ? el('div.help', hint.help) : null, inner,
        missingKnown(fileKey, path, value, schema));
    } else if (Array.isArray(value) && (value.length === 0 ? hint.table : value.every(isPlainObject))) {
      node.classList.add('wide');
      node.append(labelNode(key, hint, path), hint.help ? el('div.help', hint.help) : null,
        tableEditor(fileKey, path, value, node));
    } else if (Array.isArray(value) && value.every((x) => x === null || typeof x !== 'object')) {
      node.append(labelNode(key, hint, path), ...listInput(fileKey, path, value, node));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    } else if (Array.isArray(value)) {
      node.classList.add('wide');
      node.append(labelNode(key, hint, path), ...jsonInput(fileKey, path, value, node));
    } else if (typeof value === 'boolean') {
      node.append(labelNode(key, hint, path), boolInput(fileKey, path, value, node));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    } else if (hint.enum) {
      const opts = Object.keys(hint.enum).map((k) => (typeof value === 'number' ? Number(k) : k));
      node.append(labelNode(key, hint, path), ...selectInput(fileKey, path, value, opts, hint.enum, node, false));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    } else if (hint.catalog && typeof value === 'string') {
      const opts = (catalog.options && catalog.options[hint.catalog]) || [];
      const notes = {};
      for (const o of opts) { const n = catalog.notes && catalog.notes[hint.catalog + ':' + o]; if (n) notes[o] = n; }
      if (opts.length) node.append(labelNode(key, hint, path), ...selectInput(fileKey, path, value, opts, null, node, true, notes));
      else node.append(labelNode(key, hint, path), textInput(fileKey, path, value, node));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    } else if (typeof value === 'number') {
      node.append(labelNode(key, hint, path), ...numberInput(fileKey, path, value, hint, node));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    } else if (value === null) {
      node.append(labelNode(key, hint, path), ...jsonInput(fileKey, path, value, node));
    } else {
      node.append(labelNode(key, hint, path), textInput(fileKey, path, value, node));
      if (hint.help) node.appendChild(el('div.help', hint.help));
    }
    markChanged(fileKey, path, node);
    return node;
  }

  /** Buttons to add known fields (with a default in the schema) that are absent from an object. */
  function missingKnown(fileKey, path, obj, schema) {
    if (!schema) return null;
    const prefix = path.length ? path.join('.') + '.' : '';
    const keys = Object.keys(schema.fields).filter((k) => k.startsWith(prefix)
      && k.slice(prefix.length).indexOf('.') < 0 && !(k.slice(prefix.length) in obj)
      && schema.fields[k].default !== undefined && !(schema.exclude || []).includes(k));
    if (!keys.length) return null;
    return el('div.toolbar', { style: { margin: '8px 0 0' } },
      el('span.small.muted', 'Campos conhecidos ausentes no arquivo:'),
      keys.map((k) => el('button.btn.small', {
        type: 'button', title: 'Adiciona com o valor padrão do simulador',
        on: { click: () => {
          const target = path.length ? getPath(App.state.model[fileKey], path) : App.state.model[fileKey];
          target[k.slice(prefix.length)] = JSON.parse(JSON.stringify(schema.fields[k].default));
          App.refreshDirty(); App.renderTab();
        } },
      }, '+ ' + (schema.fields[k].label || k))));
  }

  /** Renders the grouped form for one configuration file. */
  function renderForm(fileKey) {
    const schema = App.schema[fileKey];
    const obj = App.state.model[fileKey];
    const box = el('div');
    if (!isPlainObject(obj)) {
      box.appendChild(el('div.notice.warn', 'O conteúdo deste arquivo não é um objeto JSON; use o modo JSON.'));
      return box;
    }
    const used = new Set(schema ? (schema.exclude || []) : []);
    for (const g of (schema ? schema.groups : [])) {
      const present = g.keys.filter((k) => k in obj);
      present.forEach((k) => used.add(k));
      const addable = g.keys.filter((k) => !(k in obj) && schema.fields[k] && schema.fields[k].default !== undefined);
      if (!present.length && !addable.length) continue;
      const det = el('details.group', { open: g.open !== false });
      const body = el('div.group-body');
      if (g.desc) body.appendChild(el('p.help.muted.small', { style: { margin: '0 0 10px' } }, g.desc));
      const grid = el('div.fields');
      for (const k of present) grid.appendChild(field(fileKey, [k], obj[k], schema));
      body.appendChild(grid);
      if (addable.length) {
        const sub = {};
        for (const k of g.keys) if (k in obj) sub[k] = true;
        const fake = { fields: {}, exclude: [] };
        for (const k of addable) fake.fields[k] = schema.fields[k];
        body.appendChild(missingKnown(fileKey, [], sub, fake));
      }
      const count = present.reduce((n, k) => n + (Array.isArray(obj[k]) ? obj[k].length : 0), 0);
      det.append(el('summary', g.title, count ? el('span.count', '· ' + fmtNum(count) + ' itens') : null), body);
      box.appendChild(det);
    }
    const unknown = Object.keys(obj).filter((k) => !used.has(k));
    if (unknown.length) {
      const det = el('details.group.unknown', { open: true });
      const grid = el('div.fields');
      for (const k of unknown) grid.appendChild(field(fileKey, [k], obj[k], schema));
      det.append(el('summary', 'Campos adicionais', el('span.count', '· ' + unknown.length)),
        el('div.group-body',
          el('p.help.muted.small', { style: { margin: '0 0 10px' } },
            'Campos presentes no arquivo que o launcher não conhece (por exemplo, adicionados em versões novas do simulador). ',
            'Eles são editáveis e sempre preservados ao salvar.'),
          grid));
      box.appendChild(det);
    }
    return box;
  }

  /** Raw JSON editor bound to the model. */
  function renderJsonEditor(fileKey) {
    const text = App.state.rawText[fileKey] !== undefined ? App.state.rawText[fileKey] : App.serialize(fileKey, App.state.model[fileKey]);
    const lines = text.split('\n').length;
    const ta = el('textarea.input', { rows: Math.min(40, Math.max(12, lines + 1)), spellcheck: false, wrap: 'off' }, text);
    const status = el('div.small', { style: { marginTop: '6px' } });
    const showStatus = () => {
      const err = App.state.parseErrors[fileKey];
      status.className = 'small ' + (err ? 'fwarn' : 'muted');
      status.textContent = err ? 'JSON inválido: ' + err + ' — corrija antes de salvar ou voltar ao formulário.' : 'JSON válido.';
    };
    ta.addEventListener('input', App.debounce(() => {
      App.state.rawText[fileKey] = ta.value;
      try {
        const v = JSON.parse(ta.value);
        delete App.state.parseErrors[fileKey];
        App.setValue(fileKey, [], v, true);
      } catch (e) {
        App.state.parseErrors[fileKey] = e.message;
        App.refreshDirty();
      }
      showStatus();
    }, 250));
    showStatus();
    return el('div', ta, status);
  }

  App.forms = { renderForm, renderJsonEditor, formatForInput };
})(window.App);
