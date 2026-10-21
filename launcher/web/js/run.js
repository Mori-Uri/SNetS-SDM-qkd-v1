/* Execution tab (run now + .bat generation) and the global runs drawer. */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtDuration, fmtDate, fmtBytes } = App;

  const R = { list: [], logPoll: null, listPoll: null, consoleRunId: null, consoleNext: 0 };

  function defaultJar() {
    const jars = (App.state.info && App.state.info.jars) || [];
    const pref = App.state.info && App.state.info.settings.defaultJar;
    return (jars.find((j) => j.path === pref) || jars.find((j) => /qkd/i.test(j.name) && j.location === 'simulations')
      || jars.find((j) => j.location === 'simulations') || jars[0] || {}).path || '';
  }

  function jarSelect(value) {
    const jars = (App.state.info && App.state.info.jars) || [];
    const sel = el('select.input', jars.map((j) => el('option', { value: j.path },
      `${j.name}  ·  ${j.location}  ·  ${fmtBytes(j.size)}  ·  ${fmtDate(j.mtime)}`)));
    sel.value = value || defaultJar();
    return sel;
  }

  // ------------------------------------------------------------------ tab

  function render() {
    const st = App.state;
    const root = el('div.stack');
    const sim = st.model.simulation || {};
    const dirty = App.dirtyFiles();
    const jars = (st.info && st.info.jars) || [];

    const jarSel = jarSelect();
    const jvm = el('input.input.mono', { type: 'text', value: (st.info && st.info.settings.jvmArgs) || '', placeholder: 'ex.: -Xmx8g' });
    const runBtn = el('button.btn.primary', { type: 'button', disabled: !!dirty.length || !jars.length }, 'Executar simulação');
    const nSims = (Number(sim.loadPoints) || 0) * (Number(sim.replications) || 0);
    const cpus = (st.info && st.info.cpus) || 1;
    const threadsIn = el('input.input.num', { type: 'number', min: 1, max: 512, placeholder: String(sim.threads || '') });
    const settings = (st.info && st.info.settings) || {};
    const consoleCb = el('input', { type: 'checkbox', checked: !!settings.runConsole, disabled: !settings.consoleSupported });
    runBtn.addEventListener('click', async () => {
      runBtn.disabled = true;
      try {
        const r = await App.api.run(st.casePath, jarSel.value, jvm.value, Number(threadsIn.value) || 0, consoleCb.checked);
        App.toast('Simulação iniciada (' + r.id + ')', 'ok');
        attachConsole(r.id);
        pollList();
      } catch (e) { App.toast(e.message, 'err'); }
      finally { runBtn.disabled = !!App.dirtyFiles().length; }
    });

    const runPanel = el('section.panel',
      el('h2', 'Executar agora'),
      el('p.lead', 'Roda o simulador como processo separado, do mesmo jeito que os .bat existentes: ',
        el('code', 'java -jar <jar> ' + st.casePath.replace(/\//g, '\\')), ', a partir da pasta simulations.'),
      dirty.length ? el('div.notice.warn', { style: { marginBottom: '12px' } },
        el('span', 'Há alterações não salvas (' + dirty.join(', ') + '). O simulador lê os arquivos do disco — salve antes de executar. '),
        el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('review') } }, 'Revisar e salvar')) : null,
      !jars.length ? el('div.notice.err', { style: { marginBottom: '12px' } }, 'Nenhum .jar encontrado em simulations/ nem em target/. Gere o jar do simulador ou ajuste as configurações.') : null,
      el('div.fields',
        el('div.field.wide', el('div.flabel', 'JAR do simulador'), jarSel),
        el('div.field', el('div.flabel', 'Argumentos da JVM', el('span.unit', '(opcional)')), jvm),
        el('div.field', el('div.flabel', 'Threads desta execução', el('span.unit', '(vazio = ' + (sim.threads || '?') + ', do arquivo simulation; máquina: ' + cpus + ')')), threadsIn),
        el('div.field', el('div.flabel', 'Tamanho da execução'),
          el('div', el('b', fmtNum(nSims) + ' simulações'), el('span.muted', ` (${sim.loadPoints} pontos × ${sim.replications} replicações, ${fmtNum(sim.requests)} requisições cada, ${sim.threads} threads)`)))),
      el('label.check', { style: { marginTop: '10px' } }, consoleCb, ' Abrir janela de console com o progresso (fechar a janela cancela a execução)'),
      el('div.notice.info', { style: { marginTop: '10px' } },
        'A execução roda no processo do launcher: fechar ou recarregar o navegador NÃO a interrompe — reabra http://localhost e acompanhe em "Execuções". Ela termina se você cancelar aqui, fechar a janela de console dela (se aberta) ou fechar a janela principal do launcher.'
        + (settings.consoleSupported ? '' : ' (Janelas de console por execução só existem no Windows.)')),
      el('div.toolbar', { style: { marginTop: '14px', marginBottom: 0 } }, runBtn));
    root.appendChild(runPanel);

    const consolePanel = el('section.panel#consolePanel', el('h2', 'Saída da execução'));
    root.appendChild(consolePanel);
    root.appendChild(batPanel());

    // attach to the latest run of this case, if any
    const last = R.list.filter((r) => r.case === st.casePath && r.state !== 'queued').slice(-1)[0];
    setTimeout(() => {
      if (last) attachConsole(last.id);
      else clear(consolePanel).append(el('h2', 'Saída da execução'), el('p.muted', 'Nenhuma execução deste caso nesta sessão.'));
    }, 0);
    return root;
  }

  function attachConsole(id) {
    R.consoleRunId = id;
    R.consoleNext = 0;
    const panel = document.getElementById('consolePanel');
    if (!panel) return;
    const meta = el('div.run-meta');
    const bar = el('div.progress', el('div'));
    const pre = el('div.console');
    const cancel = el('button.btn.small.danger', { type: 'button' }, 'Cancelar');
    cancel.addEventListener('click', async () => {
      if (!window.confirm('Cancelar esta simulação? Os resultados parciais não serão salvos.')) return;
      try { await App.api.cancelRun(id); } catch (e) { App.toast(e.message, 'err'); }
    });
    clear(panel).append(el('div.toolbar', { style: { marginBottom: '4px' } }, el('h2', 'Saída da execução'), el('span.spacer'), cancel), meta, bar, pre);
    clearInterval(R.logPoll);
    let finishedHandled = false;
    const tick = async () => {
      if (R.consoleRunId !== id || !document.body.contains(pre)) { clearInterval(R.logPoll); return; }
      let d;
      try { d = await App.api.runLog(id, R.consoleNext); } catch (e) { return; }
      const atBottom = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 30;
      for (const line of d.lines) {
        const cls = line.startsWith('> ') ? 'cmd' : /exception|error|erro/i.test(line) ? 'er' : line.startsWith('[launcher]') ? 'lw' : null;
        pre.appendChild(cls ? el('span.' + cls, line + '\n') : document.createTextNode(line + '\n'));
      }
      R.consoleNext = d.next;
      if (atBottom) pre.scrollTop = pre.scrollHeight;
      const end = d.end || Date.now();
      clear(meta).append(
        el('span.state.' + d.state, stateLabel(d.state)),
        el('span', 'Progresso ', el('b', fmtNum(d.progress * 100, 1) + ' %')),
        el('span', 'Tempo ', el('b', fmtDuration(end - d.start))),
        el('span', 'JAR ', el('b', d.jar)),
        d.exitCode !== null ? el('span', 'Código de saída ', el('b', String(d.exitCode))) : null);
      bar.firstChild.style.width = (d.progress * 100).toFixed(1) + '%';
      cancel.hidden = d.state !== 'running';
      if (d.state !== 'running') {
        clearInterval(R.logPoll);
        if (!finishedHandled) {
          finishedHandled = true;
          if (d.state === 'finished' && d.case === App.state.casePath) {
            App.refreshCaseFiles();
            panel.appendChild(el('div.notice.ok', { style: { marginTop: '10px' } },
              el('span', 'Simulação concluída. '),
              el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('results') } }, 'Ver resultados')));
          }
        }
      }
    };
    tick();
    R.logPoll = setInterval(tick, 1000);
  }

  function stateLabel(s) {
    return { queued: 'na fila', running: 'executando', finished: 'concluída', failed: 'falhou', cancelled: 'cancelada' }[s] || s;
  }

  // ------------------------------------------------------------------ .bat

  function batPanel() {
    const st = App.state;
    const cases = App.allCases();
    const picked = new Set([st.casePath]);
    const filter = el('input.input', { type: 'search', placeholder: 'Filtrar casos…' });
    const list = el('div.table-wrap', { style: { maxHeight: '220px' } });
    const jarSel = jarSelect();
    const jvm = el('input.input.mono', { type: 'text', value: (st.info && st.info.settings.jvmArgs) || '', placeholder: 'ex.: -Xmx8g' });
    const pause = el('input', { type: 'checkbox', checked: true });
    const kind = el('select.input', el('option', { value: 'bat' }, '.bat (Windows)'), el('option', { value: 'sh' }, '.sh (Linux/macOS)'));
    const name = el('input.input.mono', { type: 'text', value: 'run_' + App.caseNameOf(st.casePath) + '.bat' });
    const ext = () => '.' + kind.value;
    kind.addEventListener('change', () => { name.value = name.value.replace(/\.(bat|sh)$/i, '') + ext(); regen(); });
    const preview = el('textarea.input', { rows: 18, spellcheck: false, wrap: 'off' });
    let edited = false;
    preview.addEventListener('input', () => { edited = true; });

    const drawList = () => {
      clear(list);
      const q = filter.value.trim().toLowerCase();
      const tbody = el('tbody');
      for (const c of cases) {
        if (q && !c.path.toLowerCase().includes(q)) continue;
        const cb = el('input', { type: 'checkbox', checked: picked.has(c.path) });
        cb.addEventListener('change', () => {
          if (cb.checked) picked.add(c.path); else picked.delete(c.path);
          updatePlan();
          if (!name.dataset.touched) name.value = (picked.size === 1 ? 'run_' + App.caseNameOf([...picked][0]) : 'run_lote_' + new Date().toISOString().slice(0, 10)) + ext();
          regen();
        });
        tbody.appendChild(el('tr', el('td', { style: { width: '1%' } }, el('label.check', { style: { padding: '4px 8px' } }, cb)), el('td', { style: { padding: '4px 8px' } }, el('span.mono', c.path))));
      }
      list.appendChild(el('table.grid', tbody));
    };
    name.addEventListener('input', () => { name.dataset.touched = '1'; });

    const regen = () => {
      if (edited && !window.confirm('Descartar as edições manuais do script?')) return;
      edited = false;
      const ordered = cases.map((c) => c.path).filter((p) => picked.has(p));
      preview.value = kind.value === 'sh' ? buildSh(ordered, jarSel.value, jvm.value)
        : buildBat(ordered, jarSel.value, jvm.value, pause.checked);
    };
    filter.addEventListener('input', drawList);
    jarSel.addEventListener('change', regen);
    jvm.addEventListener('input', App.debounce(regen, 300));
    pause.addEventListener('change', regen);

    const save = el('button.btn', { type: 'button' }, 'Salvar script em simulations/');
    const cpus = (st.info && st.info.cpus) || 1;
    const settings = (st.info && st.info.settings) || {};
    const mode = el('select.input',
      el('option', { value: 'sequential' }, 'Uma após a outra (fila)'),
      el('option', { value: 'parallel' }, 'Em paralelo (threads divididas igualmente)'));
    const totalThreads = el('input.input.num', { type: 'number', min: 1, max: 512 });
    const consoleCb = el('input', { type: 'checkbox', checked: !!settings.runConsole, disabled: !settings.consoleSupported });
    const plan = el('div.small.muted');
    const updatePlan = () => {
      const n = picked.size;
      const par = mode.value === 'parallel';
      totalThreads.placeholder = par ? String(cpus) : 'do arquivo de cada caso';
      const T = Number(totalThreads.value) || (par ? cpus : 0);
      if (!n) { plan.textContent = 'Selecione os casos.'; return; }
      if (par) {
        const total = Math.max(n, T);
        const per = [...Array(n)].map((_, i) => Math.floor(total / n) + (i < total % n ? 1 : 0));
        plan.textContent = n + ' caso(s) ao mesmo tempo; threads por caso: ' + [...new Set(per)].join(' ou ') + ' (total ' + total + ', máquina ' + cpus + ').'
          + (n > cpus ? ' Atenção: mais casos que processadores — cada um terá 1 thread e todos disputam a CPU e a memória.' : '');
      } else {
        plan.textContent = n + ' caso(s) em sequência; cada um usa ' + (T ? T + ' threads' : 'as threads do seu arquivo simulation') + '.';
      }
    };
    mode.addEventListener('change', updatePlan);
    totalThreads.addEventListener('input', updatePlan);
    const runBatch = el('button.btn.primary', { type: 'button' }, 'Executar os casos selecionados');
    runBatch.addEventListener('click', async () => {
      if (!picked.size) { App.toast('Selecione ao menos um caso.', 'err'); return; }
      if (App.dirtyFiles().length && picked.has(st.casePath)) { App.toast('Salve as alterações deste caso antes de executar.', 'err'); return; }
      const ordered = cases.map((c) => c.path).filter((p) => picked.has(p));
      try {
        const out = await App.api.batch(ordered, mode.value, jarSel.value, jvm.value, Number(totalThreads.value) || 0, consoleCb.checked);
        App.toast(out.length + ' caso(s) ' + (mode.value === 'parallel' ? 'iniciados em paralelo' : 'na fila de execução'), 'ok');
        pollList(); openDrawer();
      } catch (e) { App.toast(e.message, 'err'); }
    });
    const origDraw = () => updatePlan();
    save.addEventListener('click', async () => {
      if (!picked.size) { App.toast('Selecione ao menos um caso.', 'err'); return; }
      try {
        let r = await App.api.writeScript(name.value.trim(), preview.value, false);
        if (r.exists) {
          if (!window.confirm(name.value + ' já existe. Sobrescrever?')) return;
          r = await App.api.writeScript(name.value.trim(), preview.value, true);
        }
        App.toast('Script salvo: ' + r.file, 'ok', 6000);
      } catch (e) { App.toast(e.message, 'err'); }
    });

    drawList();
    regen();
    origDraw();
    return el('section.panel',
      el('h2', 'Vários casos'),
      el('p.lead', 'Executa vários casos direto no launcher — em fila (um após o outro) ou em paralelo, dividindo as threads entre eles — ou gera um script .bat/.sh no padrão dos run_*.bat.'),
      el('div.two-col',
        el('div.stack-s', { style: { alignSelf: 'start' } }, el('div.flabel', 'Casos'), filter,
          App.allNoneButtons(() => {
            const q = filter.value.trim().toLowerCase();
            for (const c of cases) if (!q || c.path.toLowerCase().includes(q)) picked.add(c.path);
            drawList(); updatePlan(); regen();
          }, () => { picked.clear(); drawList(); updatePlan(); regen(); }),
          list,
          el('div.muted.small', 'A ordem de execução segue a ordem da lista.')),
        el('div.form-grid',
          el('div.field', el('div.flabel', 'JAR'), jarSel),
          el('div.field', el('div.flabel', 'Argumentos da JVM'), jvm),
          el('div.field', el('div.flabel', 'Modo de execução'), mode),
          el('div.field', el('div.flabel', 'Threads', el('span.unit', '(fila: por caso; paralelo: total a dividir)')), totalThreads),
          el('label.check', consoleCb, ' Uma janela de console por caso (fechá-la cancela aquele caso)'),
          plan,
          el('div.toolbar', { style: { margin: 0 } }, runBatch))),
      el('details.group', { style: { marginTop: '14px' } }, el('summary', 'Gerar script .bat / .sh'),
        el('div.group-body',
          el('div.fields',
            el('div.field', el('div.flabel', 'Tipo de script'), kind),
            el('div.field', el('div.flabel', 'Nome do arquivo'), name)),
          el('label.check', pause, 'Pausar no final (.bat: manter a janela aberta)'),
          el('div.field', { style: { marginTop: '10px' } }, el('div.flabel', 'Conteúdo (pode ser editado antes de salvar)'), preview),
          el('div.toolbar', { style: { marginTop: '12px', marginBottom: 0 } }, save,
            el('button.btn', { type: 'button', on: { click: () => App.copyText(preview.value) } }, 'Copiar')))));
  }

  function jarRef(jarPath) {
    const j = ((App.state.info && App.state.info.jars) || []).find((x) => x.path === jarPath);
    if (!j) return jarPath;
    return j.location === 'simulations' ? '%ROOT%' + j.name : '%ROOT%..\\target\\' + j.name;
  }

  function buildBat(cases, jarPath, jvmArgs, pause) {
    const lines = [
      '@echo off',
      'setlocal',
      'REM Gerado pelo SNetS Launcher em ' + new Date().toLocaleString('pt-BR'),
      'REM Casos: ' + cases.length,
      'set "ROOT=%~dp0"',
      'set "JAR=' + jarRef(jarPath) + '"',
      'set "JAVA_OPTS=' + (jvmArgs || '').trim() + '"',
      'set "FAILED=0"',
      '',
      'if not exist "%JAR%" (',
      '  echo ERRO: %JAR% nao encontrado.',
      pause ? '  pause' : null,
      '  exit /b 1',
      ')',
      '',
      'pushd "%ROOT%"',
    ].filter((x) => x !== null);
    for (const c of cases) lines.push('call :run "' + c.replace(/\//g, '\\') + '"');
    lines.push(
      'popd',
      '',
      'echo ============================================',
      'if "%FAILED%"=="0" (',
      '  echo Todas as simulacoes terminaram com sucesso.',
      ') else (',
      '  echo Simulacoes com falha: %FAILED%',
      ')',
    );
    if (pause) lines.push('pause');
    lines.push(
      'exit /b %FAILED%',
      '',
      ':run',
      'echo ============================================',
      'echo Simulacao: %~1',
      'echo Inicio: %DATE% %TIME%',
      'if not exist "%~1\\simulation" (',
      '  echo ERRO: caso "%~1" nao encontrado.',
      '  set /a FAILED+=1',
      '  exit /b 0',
      ')',
      'java %JAVA_OPTS% -jar "%JAR%" "%~1"',
      'if errorlevel 1 (',
      '  echo ERRO: a simulacao "%~1" terminou com codigo %ERRORLEVEL%.',
      '  set /a FAILED+=1',
      ')',
      'echo Fim: %DATE% %TIME%',
      'exit /b 0',
      '');
    return lines.join('\n');
  }

  function buildSh(cases, jarPath, jvmArgs) {
    const j = ((App.state.info && App.state.info.jars) || []).find((x) => x.path === jarPath);
    const jarRel = !j ? jarPath : j.location === 'simulations' ? '$ROOT/' + j.name : '$ROOT/../target/' + j.name;
    const lines = [
      '#!/usr/bin/env bash',
      '# Gerado pelo SNetS Launcher em ' + new Date().toLocaleString('pt-BR'),
      'ROOT="$(cd "$(dirname "$0")" && pwd)"',
      'JAR="' + jarRel + '"',
      'JAVA_OPTS="' + (jvmArgs || '').trim() + '"',
      'FAILED=0',
      'cd "$ROOT" || exit 1',
      '[ -f "$JAR" ] || { echo "ERRO: $JAR nao encontrado"; exit 1; }',
      'run_case() {',
      '  echo "============================================"',
      '  echo "Simulacao: $1  ($(date))"',
      '  if [ ! -f "$1/simulation" ]; then echo "ERRO: caso $1 nao encontrado"; FAILED=$((FAILED+1)); return; fi',
      '  java $JAVA_OPTS -jar "$JAR" "$1" || { echo "ERRO: $1 terminou com codigo $?"; FAILED=$((FAILED+1)); }',
      '}',
    ];
    for (const c of cases) lines.push('run_case "' + c + '"');
    lines.push('echo "============================================"',
      'if [ "$FAILED" -eq 0 ]; then echo "Todas as simulacoes terminaram com sucesso."; else echo "Simulacoes com falha: $FAILED"; fi',
      'exit $FAILED', '');
    return lines.join('\n');
  }

  // ------------------------------------------------------------------ drawer / header indicator

  async function pollList() {
    try { R.list = await App.api.runs(); } catch (e) { return; }
    const running = R.list.filter((r) => r.state === 'running');
    const queued = R.list.filter((r) => r.state === 'queued');
    document.getElementById('runsDot').className = 'dot' + (running.length ? ' running' : '');
    document.getElementById('runsLabel').textContent = running.length || queued.length
      ? `Executando (${running.length}${queued.length ? ' + ' + queued.length + ' na fila' : ''})` : 'Execuções';
    const prev = R.prevRunning || new Set();
    for (const r of R.list) {
      if (prev.has(r.id) && r.state !== 'running') {
        if (r.kind !== 'tool') {
          App.toast(`${App.caseNameOf(r.case)}: ${stateLabel(r.state)}`, r.state === 'finished' ? 'ok' : 'err', 6000);
          if (r.state === 'finished') { App.loadTree(); if (r.case === App.state.casePath) App.refreshCaseFiles(); }
        }
      }
    }
    R.prevRunning = new Set(running.map((r) => r.id));
    if (!document.getElementById('runsDrawer').hidden) drawDrawer();
    clearTimeout(R.listPoll);
    R.listPoll = setTimeout(pollList, running.length || queued.length ? 2000 : 8000);
  }

  function drawDrawer() {
    const box = clear(document.getElementById('runsList'));
    if (!R.list.length) { box.appendChild(el('p.muted', 'Nenhuma execução iniciada nesta sessão do launcher.')); return; }
    const queued = R.list.filter((r) => r.state === 'queued').length;
    if (queued) {
      box.appendChild(el('div.toolbar', el('span.small', queued + ' na fila'), el('span.spacer'),
        el('button.btn.small.danger', { type: 'button', on: { click: async () => { await App.api.clearQueue(); pollList(); } } }, 'Esvaziar fila')));
    }
    for (const r of R.list.slice().reverse()) {
      const end = r.end || Date.now();
      const isTool = r.kind === 'tool';
      box.appendChild(el('div.run-card',
        el('div.top', el('span.path', isTool ? '⚙ ' + r.label : r.case), el('span.state.' + r.state, stateLabel(r.state))),
        el('div.progress', el('div', { style: { width: (r.progress * 100).toFixed(1) + '%' } })),
        el('div.sub', (r.state === 'queued' ? r.jar + ' · aguardando' : `${r.jar} · início ${fmtDate(r.start)} · ${fmtDuration(end - r.start)} · ${fmtNum(r.progress * 100, 1)} %`)
          + (r.threads ? ' · ' + r.threads + ' threads' : '') + (r.group ? ' · lote ' + r.group : '') + (r.console && r.state === 'running' ? (r.consoleAttached ? ' · janela de console aberta' : ' · abrindo console') : '')),
        el('div.toolbar', { style: { margin: 0 } },
          isTool ? el('button.btn.small', { type: 'button', on: { click: () => showToolLog(r) } }, 'Ver saída')
            : r.state === 'queued' ? null
              : el('button.btn.small', { type: 'button', on: { click: () => { closeDrawer(); App.openCase(r.case, 'run').then(() => attachConsole(r.id)); } } }, 'Abrir saída'),
          r.state === 'queued' || r.state === 'running'
            ? el('button.btn.small.ghost', { type: 'button', on: { click: async () => { await App.api.cancelRun(r.id); pollList(); } } }, r.state === 'queued' ? 'Remover da fila' : 'Cancelar') : null)));
    }
  }
  async function showToolLog(r) {
    try {
      const d = await App.api.runLog(r.id, 0);
      App.modal(r.label, el('div.console', { style: { height: '440px' } }, d.lines.join('\n')), null);
    } catch (e) { App.toast(e.message, 'err'); }
  }

  function openDrawer() { document.getElementById('runsDrawer').hidden = false; drawDrawer(); }
  function closeDrawer() { document.getElementById('runsDrawer').hidden = true; }

  App.run = { render, pollList, openDrawer, closeDrawer, attachConsole, buildBat, buildSh, defaultJar };
})(window.App);
