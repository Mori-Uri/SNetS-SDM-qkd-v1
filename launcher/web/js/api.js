/* Thin client for the launcher's local HTTP API. */
(function (App) {
  'use strict';

  async function call(method, path, params, body) {
    const qs = params ? '?' + new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    ).toString() : '';
    let res;
    try {
      res = await fetch('/api/' + path + qs, {
        method,
        headers: body !== undefined ? { 'Content-Type': 'text/plain; charset=utf-8' } : undefined,
        body,
      });
    } catch (e) {
      throw new Error('Sem conexão com o launcher. A janela do servidor foi fechada?');
    }
    let data;
    try { data = await res.json(); } catch (e) { throw new Error('Resposta inválida do servidor (' + res.status + ')'); }
    if (!res.ok || (data && data.ok === false && data.error)) {
      throw new Error((data && data.error) || ('Erro HTTP ' + res.status));
    }
    return data;
  }

  App.api = {
    info: () => call('GET', 'info'),
    saveSettings: (s) => call('POST', 'settings', null, new URLSearchParams(s).toString()),
    tree: () => call('GET', 'tree'),
    catalog: () => call('GET', 'catalog'),
    readCase: (path) => call('GET', 'case', { path }),
    writeFile: (path, name, content, mtime) => call('POST', 'file', { path, name, mtime }, content),
    deleteRoutes: (path) => call('POST', 'routes/delete', { path }),
    csv: (path, name) => call('GET', 'csv', { path, name }),
    createCase: (parent, name, from, copyRoutes) => call('POST', 'case/create', { parent, name, from, copyRoutes }),
    createFolder: (parent, name) => call('POST', 'folder/create', { parent, name }),
    writeScript: (name, content, overwrite) => call('POST', 'script', { name, overwrite }, content),
    run: (path, jar, jvmArgs, threads, consoleWin) => call('POST', 'run', { path, jar, jvmArgs, threads: threads || null, console: consoleWin === undefined ? null : String(!!consoleWin) }),
    batch: (cases, mode, jar, jvmArgs, threads, consoleWin) => call('POST', 'run/batch', { mode, jar, jvmArgs, threads: threads || null, console: consoleWin === undefined ? null : String(!!consoleWin) }, cases.join('\n')),
    runs: () => call('GET', 'runs'),
    runLog: (id, from) => call('GET', 'run/log', { id, from }),
    cancelRun: (id) => call('POST', 'run/cancel', { id }),
    openFolder: (path) => call('POST', 'open-folder', { path }),
    queue: (cases, jar, jvmArgs) => call('POST', 'queue', { jar, jvmArgs }, cases.join('\n')),
    clearQueue: () => call('POST', 'queue/clear'),
    tools: (jar) => call('GET', 'tools', { jar }),
    runTool: (id, jar, queued, jvmArgs) => call('POST', 'tool/run', { id, jar, queued: queued ? 'true' : null, jvmArgs }),
    text: (path, optional) => call('GET', 'text', { path, optional: optional ? 'true' : null }),
    files: (path) => call('GET', 'files', { path }),
    figures: () => call('GET', 'figures'),
    figure: (name) => call('GET', 'figure', { name }),
    saveFigure: (name, json) => call('POST', 'figure', { name }, json),
    saveFigureImage: (name, ext, body) => call('POST', 'figure/image', { name, ext }, body),
    deleteFigure: (name) => call('POST', 'figure/delete', { name }),
    openFiguresFolder: () => call('POST', 'figures/open-folder'),
  };
})(window.App);
