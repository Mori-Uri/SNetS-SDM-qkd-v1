/* Review tab: validation + per-file diff + save. */
(function (App) {
  'use strict';
  const { el } = App;

  /** Line diff (common prefix/suffix + LCS on the middle when small enough). */
  function diffLines(a, b) {
    let s = 0;
    while (s < a.length && s < b.length && a[s] === b[s]) s++;
    let ea = a.length, eb = b.length;
    while (ea > s && eb > s && a[ea - 1] === b[eb - 1]) { ea--; eb--; }
    const ops = [];
    for (let i = 0; i < s; i++) ops.push(['=', a[i]]);
    const A = a.slice(s, ea), B = b.slice(s, eb);
    if (A.length * B.length <= 4e6) {
      const n = A.length, m = B.length;
      const dp = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
      for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) {
        dp[i][j] = A[i] === B[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
      let i = 0, j = 0;
      while (i < n && j < m) {
        if (A[i] === B[j]) { ops.push(['=', A[i]]); i++; j++; }
        else if (dp[i + 1][j] >= dp[i][j + 1]) { ops.push(['-', A[i++]]); }
        else ops.push(['+', B[j++]]);
      }
      while (i < n) ops.push(['-', A[i++]]);
      while (j < m) ops.push(['+', B[j++]]);
    } else {
      for (const x of A) ops.push(['-', x]);
      for (const x of B) ops.push(['+', x]);
    }
    for (let i = ea; i < a.length; i++) ops.push(['=', a[i]]);
    return ops;
  }

  function renderDiff(ops, maxLines) {
    const ctx = 3;
    const keep = new Array(ops.length).fill(false);
    ops.forEach((o, i) => { if (o[0] !== '=') for (let k = Math.max(0, i - ctx); k <= Math.min(ops.length - 1, i + ctx); k++) keep[k] = true; });
    const box = el('div.diff');
    let shown = 0, skipped = 0, adds = 0, dels = 0;
    for (const o of ops) { if (o[0] === '+') adds++; else if (o[0] === '-') dels++; }
    for (let i = 0; i < ops.length; i++) {
      if (!keep[i]) { skipped++; continue; }
      if (skipped) { box.appendChild(el('div.l.gap', '… ' + skipped + ' linha(s) iguais')); skipped = 0; }
      if (shown++ > maxLines) { box.appendChild(el('div.l.gap', '… diferença muito longa, truncada')); break; }
      const [t, line] = ops[i];
      box.appendChild(el('div.l.' + (t === '+' ? 'add' : t === '-' ? 'del' : 'ctx'), (t === '=' ? '  ' : t + ' ') + line));
    }
    return { box, adds, dels };
  }

  function render() {
    const st = App.state;
    const root = el('div.stack');
    const issues = App.validate.validateAll(st.model, st.catalog, st.info);
    const errors = issues.filter((i) => i.level === 'err').length;
    const dirty = App.dirtyFiles();

    root.appendChild(el('section.panel',
      el('h2', 'Validação'),
      el('p.lead', 'Verificações de consistência feitas pelo launcher. Ao iniciar, o simulador também executa o próprio NetworkConfigValidator.'),
      App.validate.issuesList(issues, 'Nenhum problema encontrado nos arquivos deste caso.')));

    const changes = el('section.panel', el('h2', 'Alterações não salvas'));
    if (!dirty.length) {
      changes.appendChild(el('p.lead', 'Nenhum arquivo foi alterado desde que o caso foi aberto.'));
    } else {
      changes.appendChild(el('p.lead', 'Comparação entre o arquivo no disco e o que será gravado. Apenas os arquivos listados serão escritos; os demais ficam intactos.'));
      for (const f of dirty) {
        const before = st.base[f] === undefined ? [] : App.serialize(f, st.base[f]).split('\n');
        const after = st.parseErrors[f] ? [] : App.serialize(f, st.model[f]).split('\n');
        const d = renderDiff(diffLines(before, after), 400);
        changes.appendChild(el('details.group', { open: dirty.length <= 3 },
          el('summary', f, el('span.count', `· +${d.adds} −${d.dels} linhas`)),
          el('div.group-body', st.parseErrors[f] ? el('div.notice.err', 'JSON inválido — corrija antes de salvar.') : d.box)));
      }
      const btn = el('button.btn.primary', { type: 'button', disabled: dirty.some((f) => st.parseErrors[f]) }, `Salvar ${dirty.length} arquivo(s)`);
      btn.addEventListener('click', async () => {
        if (errors && !window.confirm('Há ' + errors + ' erro(s) de validação. Salvar mesmo assim?')) return;
        btn.disabled = true;
        try {
          await App.saveAll();
        } catch (e) {
          App.toast(e.message, 'err');
          btn.disabled = false;
        }
      });
      const discard = el('button.btn', { type: 'button', on: { click: () => App.reloadCase(true) } }, 'Descartar alterações');
      changes.appendChild(el('div.toolbar', { style: { marginTop: '12px', marginBottom: 0 } }, btn, discard,
        errors ? el('span.fwarn.small', errors + ' erro(s) de validação acima') : null));
    }
    root.appendChild(changes);
    return root;
  }

  App.review = { render, diffLines };
})(window.App);
