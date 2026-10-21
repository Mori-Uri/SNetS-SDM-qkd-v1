/* Small DOM + formatting helpers shared by every module. */
window.App = window.App || {};

(function (App) {
  'use strict';

  // Node.append() would print "null"/"false" for optional children; skip them instead.
  const nativeAppend = Element.prototype.append;
  Element.prototype.append = function (...nodes) {
    return nativeAppend.apply(this, nodes.flat().filter((n) => n !== null && n !== undefined && n !== false));
  };

  /** el('div.cls#id', {attrs, on:{click}}, children...) */
  function el(spec, attrs, ...children) {
    const m = /^([a-z0-9]+)?((?:[.#][\w-]+)*)$/i.exec(spec) || [];
    const node = document.createElement(m[1] || 'div');
    (m[2] || '').replace(/([.#])([\w-]+)/g, (_, t, v) => {
      if (t === '.') node.classList.add(v); else node.id = v;
    });
    if (attrs && (typeof attrs !== 'object' || attrs instanceof Node || Array.isArray(attrs))) {
      children.unshift(attrs);
      attrs = null;
    }
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        if (v === undefined || v === null || v === false) continue;
        if (k === 'on') { for (const [ev, fn] of Object.entries(v)) node.addEventListener(ev, fn); }
        else if (k === 'class') node.className += ' ' + v;
        else if (k === 'style' && typeof v === 'object') Object.assign(node.style, v);
        else if (k === 'html') node.innerHTML = v;
        else if (k in node && typeof v !== 'string') node[k] = v;
        else node.setAttribute(k, v === true ? '' : v);
      }
    }
    append(node, children);
    return node;
  }

  function append(node, children) {
    for (const c of children) {
      if (c === null || c === undefined || c === false) continue;
      if (Array.isArray(c)) append(node, c);
      else node.appendChild(c instanceof Node ? c : document.createTextNode(String(c)));
    }
  }

  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); return node; }

  function svg(tag, attrs, ...children) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
    if (attrs) for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === null) continue;
      if (k === 'text') node.textContent = v; else node.setAttribute(k, v);
    }
    for (const c of children) if (c) node.appendChild(c);
    return node;
  }

  function toast(msg, kind, ms) {
    const t = el('div.toast' + (kind ? '.' + kind : ''), msg);
    document.getElementById('toasts').appendChild(t);
    setTimeout(() => t.remove(), ms || (kind === 'err' ? 7000 : 3500));
  }

  /**
   * Opens the shared <dialog>. body: Node. onOk(form) may return a Promise;
   * throw/reject to show the error and keep the dialog open.
   */
  function modal(title, body, onOk, okLabel) {
    const dlg = document.getElementById('modal');
    document.getElementById('modalTitle').textContent = title;
    clear(document.getElementById('modalBody')).appendChild(body);
    const err = document.getElementById('modalError');
    err.hidden = true;
    const ok = document.getElementById('modalOk');
    ok.textContent = okLabel || 'OK';
    ok.hidden = !onOk;
    ok.disabled = false;
    const handler = async (ev) => {
      if (ev.submitter && ev.submitter.value === 'cancel') return;
      ev.preventDefault();
      if (!onOk) { dlg.close(); return; }
      ok.disabled = true;
      try {
        await onOk();
        dlg.close();
      } catch (e) {
        err.textContent = e.message || String(e);
        err.hidden = false;
      } finally {
        ok.disabled = false;
      }
    };
    const form = document.getElementById('modalForm');
    form.onsubmit = handler;
    dlg.showModal();
    const first = body.querySelector('input, select, textarea');
    if (first) setTimeout(() => first.focus(), 30);
  }

  const nf = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
  function fmtNum(v, digits) {
    if (v === null || v === undefined || Number.isNaN(v)) return '—';
    if (digits !== undefined) return Number(v).toLocaleString('pt-BR', { maximumFractionDigits: digits, minimumFractionDigits: 0 });
    const a = Math.abs(v);
    if (a !== 0 && (a < 1e-3 || a >= 1e7)) return Number(v).toExponential(3).replace('e', 'E');
    return nf.format(v);
  }
  function fmtSci(v) {
    if (v === null || v === undefined || Number.isNaN(v)) return '—';
    const a = Math.abs(v);
    if (a !== 0 && (a < 1e-3 || a >= 1e5)) return Number(v).toExponential(3).replace('e', 'E');
    const t = Number(v).toPrecision(4);
    return t.indexOf('.') >= 0 && t.indexOf('e') < 0 ? t.replace(/\.?0+$/, '') : t;
  }
  function fmtBytes(n) {
    if (n < 1024) return n + ' B';
    if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1048576).toFixed(1) + ' MB';
  }
  function fmtDate(ms) {
    if (!ms) return '—';
    return new Date(ms).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }
  function fmtDuration(ms) {
    const s = Math.max(0, Math.round(ms / 1000));
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
    return (h ? h + 'h ' : '') + (h || m ? m + 'min ' : '') + r + 's';
  }
  function fmtBitRate(bps) {
    if (bps >= 1e12) return fmtNum(bps / 1e12) + ' Tb/s';
    if (bps >= 1e9) return fmtNum(bps / 1e9) + ' Gb/s';
    if (bps >= 1e6) return fmtNum(bps / 1e6) + ' Mb/s';
    return fmtNum(bps) + ' b/s';
  }

  function deepClone(o) { return o === undefined ? undefined : JSON.parse(JSON.stringify(o)); }
  function deepEqual(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
  function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

  function getPath(obj, path) {
    let o = obj;
    for (const k of path) { if (o === null || o === undefined) return undefined; o = o[k]; }
    return o;
  }
  function setPath(obj, path, value) {
    let o = obj;
    for (let i = 0; i < path.length - 1; i++) o = o[path[i]];
    o[path[path.length - 1]] = value;
  }

  function downloadText(name, text, mime) {
    const blob = new Blob([text], { type: mime || 'text/plain;charset=utf-8' });
    const a = el('a', { href: URL.createObjectURL(blob), download: name });
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }

  async function copyText(text) {
    try { await navigator.clipboard.writeText(text); toast('Copiado para a área de transferência', 'ok'); }
    catch (e) {
      const ta = el('textarea', { style: { position: 'fixed', opacity: '0' } }, text);
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove();
      toast('Copiado', 'ok');
    }
  }

  function debounce(fn, ms) {
    let t;
    return function (...a) { clearTimeout(t); t = setTimeout(() => fn.apply(this, a), ms); };
  }

  /** Parses numbers typed by the user; accepts "1e11", "1,5" (pt-BR comma), "100E+9". */
  function parseNumber(s) {
    if (typeof s === 'number') return s;
    const t = String(s).trim().replace(/\s/g, '');
    if (t === '') return NaN;
    const norm = /^-?\d+,\d+([eE][+-]?\d+)?$/.test(t) ? t.replace(',', '.') : t;
    if (!/^[-+]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(norm)) return NaN;
    return Number(norm);
  }

  function caseNameOf(path) { const p = String(path || '').split('/'); return p[p.length - 1]; }

  // ------------------------------------------------------------------ scroll position across redraws (1.11.0)

  /**
   * Runs fn (which rebuilds part of the page) and puts back the scroll positions: the page, the main panel,
   * the figure controls column and every inner list marked with data-sk="key". Without this, a redraw after a
   * click (a checkbox, a select) sent the view back to the top.
   */
  function keepScroll(fn) {
    const roots = [document.scrollingElement, document.getElementById('main')].concat([...document.querySelectorAll('.fig-controls')]);
    const saved = roots.filter(Boolean).map((r) => [r, r.scrollTop, r.classList && r.classList.contains('fig-controls')]);
    const inner = new Map();
    document.querySelectorAll('[data-sk]').forEach((n) => { if (n.scrollTop) inner.set(n.getAttribute('data-sk'), n.scrollTop); });
    const out = fn();
    for (const [r, top, isControls] of saved) {
      // the figure column is rebuilt in place (same element) or replaced by a new one
      const target = isControls && !r.isConnected ? document.querySelector('.fig-controls') : r;
      if (target && target.isConnected) target.scrollTop = top;
    }
    if (inner.size) document.querySelectorAll('[data-sk]').forEach((n) => { const t = inner.get(n.getAttribute('data-sk')); if (t) n.scrollTop = t; });
    return out;
  }

  /** Two small buttons, "Marcar todos" / "Desmarcar todos", for a group of checkboxes. */
  function allNoneButtons(onAll, onNone, labels) {
    const l = labels || ['Marcar todos', 'Desmarcar todos'];
    return el('div.allnone',
      el('button.btn.small', { type: 'button', on: { click: (e) => { e.preventDefault(); onAll(); } } }, l[0]),
      el('button.btn.small', { type: 'button', on: { click: (e) => { e.preventDefault(); onNone(); } } }, l[1]));
  }

  /**
   * A dropdown list that replaces a long <select>: options in groups, a filter box, the current option marked,
   * and the list keeps its scroll position between openings (a native select always reopens at the top of
   * long lists in some browsers). groups: [{label, items:[{value, label, title?}]}].
   */
  const pickerScroll = new Map();
  function picker(key, groups, value, onChange) {
    const flat = groups.flatMap((g) => g.items);
    const cur = flat.find((o) => o.value === value);
    const wrap = el('div.picker');
    const btn = el('button.input.picker-btn', { type: 'button', title: cur ? cur.label : '' }, el('span.ell', cur ? cur.label : '—'), el('span.picker-caret', '▾'));
    const panel = el('div.picker-panel', { hidden: true });
    const filter = el('input.input.picker-filter', { type: 'search', placeholder: 'Filtrar…' });
    const list = el('div.picker-list');
    panel.append(filter, list);
    wrap.append(btn, panel);
    const fill = () => {
      clear(list);
      const q = filter.value.trim().toLowerCase();
      for (const g of groups) {
        const items = g.items.filter((o) => !q || o.label.toLowerCase().includes(q) || (g.label || '').toLowerCase().includes(q));
        if (!items.length) continue;
        if (g.label) list.append(el('div.picker-group', g.label));
        for (const o of items) {
          const row = el('div.picker-item' + (o.value === value ? '.selected' : ''), { title: o.title || o.label, role: 'option', tabindex: '-1' }, o.label);
          row.addEventListener('click', () => { close(); if (o.value !== value) onChange(o.value); });
          list.append(row);
        }
      }
      if (!list.childNodes.length) list.append(el('div.picker-empty', 'Nada corresponde ao filtro.'));
    };
    const onDoc = (e) => { if (!wrap.contains(e.target)) close(); };
    const onKey = (e) => { if (e.key === 'Escape') { close(); btn.focus(); } };
    function open() {
      fill();
      panel.hidden = false;
      const saved = pickerScroll.get(key);
      if (saved !== undefined) list.scrollTop = saved;
      else { const s = list.querySelector('.selected'); if (s) list.scrollTop = Math.max(0, s.offsetTop - list.clientHeight / 2); }
      document.addEventListener('mousedown', onDoc, true);
      document.addEventListener('keydown', onKey, true);
      setTimeout(() => filter.focus(), 0);
    }
    function close() {
      if (panel.hidden) return;
      pickerScroll.set(key, list.scrollTop);
      panel.hidden = true;
      document.removeEventListener('mousedown', onDoc, true);
      document.removeEventListener('keydown', onKey, true);
    }
    list.addEventListener('scroll', () => pickerScroll.set(key, list.scrollTop));
    filter.addEventListener('input', () => { fill(); list.scrollTop = 0; });
    filter.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { const first = list.querySelector('.picker-item'); if (first) first.click(); }
    });
    btn.addEventListener('click', (e) => { e.preventDefault(); if (panel.hidden) open(); else close(); });
    return wrap;
  }

  Object.assign(App, {
    el, clear, svg, toast, modal, fmtNum, fmtSci, fmtBytes, fmtDate, fmtDuration, fmtBitRate,
    deepClone, deepEqual, isPlainObject, getPath, setPath, downloadText, copyText, debounce,
    parseNumber, caseNameOf, keepScroll, allNoneButtons, picker,
  });
  // 1.14.0: hooks run by App.openCase (defined here because util.js loads before the panels)
  App.caseHooks = App.caseHooks || [];
  App.onCaseOpened = function (fn) { App.caseHooks.push(fn); };
})(window.App);
