/*
 * "Topologia e fibra" panel of the Rede tab: predefined topologies and MCF core
 * layouts with a drawing generated from the network file. Nodes, links and cores
 * remain editable in the tables below the panel.
 */
(function (App) {
  'use strict';
  const { el } = App;
  const P = () => App.presets;
  const ui = { topo: '', cores: '', showKm: true };

  function responsive(svg, maxW) {
    const w = svg.getAttribute('width'), h = svg.getAttribute('height');
    svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
    svg.setAttribute('width', '100%');
    svg.removeAttribute('height');
    svg.style.maxWidth = maxW + 'px';
    svg.style.display = 'block';
    svg.style.margin = '0 auto';
    return svg;
  }

  function commonValue(links, key) {
    const v = [...new Set(links.map((l) => l[key]).filter((x) => x !== undefined))];
    return v.length === 1 ? v[0] : undefined;
  }

  function topoPreview(net, preset) {
    const R = App.figRender;
    const panel = preset
      ? { title: '', nodes: preset.nodes, links: preset.links.map(([a, b, km]) => ({ a, b, km })), qtnd: P().topoPanel(net).qtnd }
      : P().topoPanel(net);
    if (!panel.nodes.length) return el('div.muted.small', 'A rede não tem nós.');
    const st = Object.assign(R.defaultStyle(), {
      width: 680, showTitle: false, topoCols: 1, topoLabels: ui.showKm ? 'km' : 'none', topoNodeR: 13,
      topoLinkWidth: 3, topoLegend: 'none', fontSize: 11, topoLinkColor: '#5b6573',
    });
    return responsive(R.render({ kind: 'topo', panels: [panel], range: null }, st), 680);
  }

  function coresPreview(coresCfg, quantumCore) {
    const R = App.figRender;
    const pos = P().coreLayout(coresCfg);
    const cores = coresCfg.map((c) => {
      const p = pos.get(c.id) || { x: 0, y: 0 };
      return { id: c.id, x: p.x, y: p.y, util: NaN, block: NaN, unused: false, highlight: quantumCore === c.id ? '#c9a3ff' : null };
    });
    const st = Object.assign(R.defaultStyle(), { width: 300, height: 300, coreShow: 'none', coreLegend: false, coreCols: 1, showTitle: false, fontSize: 11 });
    return responsive(R.render({ kind: 'cores', diagrams: [{ title: coresCfg.length + ' núcleos', cores }] }, st), 300);
  }

  function panel() {
    const st = App.state;
    const net = st.model.network;
    if (!App.isPlainObject(net)) return null;
    const links = Array.isArray(net.links) ? net.links : [];
    const qkdCore = App.isPlainObject(net.qkd) && net.qkd.enabled ? Number(net.qkd.quantumCoreId) : null;

    // ---------------- topology
    const info = P().coordsFor(net);
    const opts = { slots: commonValue(links, 'slots') || 320, spectrum: commonValue(links, 'spectrum') || 12.5e9 };
    const topoSel = el('select.input',
      el('option', { value: '' }, 'Topologia atual do arquivo'),
      P().TOPOLOGIES.map((t) => el('option', { value: t.id }, t.name)));
    topoSel.value = ui.topo;
    const preset = P().TOPOLOGIES.find((t) => t.id === ui.topo) || null;
    const slots = el('input.input.num', { type: 'number', min: 1, step: 1, value: opts.slots });
    const ghz = el('input.input.num', { type: 'number', min: 0.1, step: 'any', value: opts.spectrum / 1e9 });
    const km = el('input', { type: 'checkbox', checked: ui.showKm });
    const topoBox = el('div.net-preview');
    const caption = el('div.small.muted');
    const draw = () => {
      App.clear(topoBox).append(topoPreview(net, preset));
      const q = P().topoPanel(net).qtnd;
      if (q > 0) topoBox.append(el('div.small.muted', { style: { marginTop: '4px' } }, 'Pontos roxos: nós confiáveis a cada ' + App.fmtNum(q, 1) + ' km (QTND), contados a partir do nó de origem do enlace. Passe o mouse para ver a posição.'));
      const u = P().undirectedLinks(net);
      if (preset) caption.textContent = 'Pré-visualização de ' + preset.name + ' (' + preset.desc + '). Ainda não aplicada: clique em "Aplicar topologia".';
      else {
        const src = info.source === 'file' ? 'posições dos campos x/y dos nós'
          : info.source === 'preset' ? 'reconhecida como ' + info.topology.name + ' (posições da topologia pronta)'
            : 'posições automáticas (os nós não têm x/y)';
        const oneWay = u.filter((l) => l.dirs.size < 2).length;
        caption.textContent = (net.nodes || []).length + ' nós · ' + u.length + ' enlaces bidirecionais (' + links.length + ' direcionais) · ' + src + '.'
          + (oneWay ? ' Atenção: ' + oneWay + ' enlace(s) só têm um sentido.' : '');
      }
    };
    draw();
    topoSel.addEventListener('change', () => { ui.topo = topoSel.value; App.renderTab(); });
    km.addEventListener('change', () => { ui.showKm = km.checked; draw(); });

    const readOpts = () => {
      const s = Math.round(Number(slots.value)), g = Number(ghz.value);
      if (!(s > 0) || !(g > 0)) throw new Error('Informe slots e largura do slot válidos.');
      return { slots: s, spectrum: g * 1e9 };
    };
    const apply = () => {
      if (!preset) return;
      let o;
      try { o = readOpts(); } catch (e) { App.toast(e.message, 'err'); return; }
      const body = el('div.stack-s',
        el('p', 'Substituir os nós e enlaces deste caso por ', el('b', preset.name), '?'),
        el('ul.small',
          el('li', preset.nodes.length + ' nós e ' + preset.links.length * 2 + ' enlaces direcionais (cada enlace nos dois sentidos).'),
          el('li', 'Todos os enlaces com ' + o.slots + ' slots de ' + App.fmtNum(o.spectrum / 1e9, 3) + ' GHz; comprimentos da topologia (km).'),
          el('li', 'Transmissores/receptores/regeneradores copiados do primeiro nó atual; posições gravadas em x/y (ignoradas pelo simulador).'),
          el('li', 'O arquivo traffic não muda: confira se os pares origem–destino existem na nova rede.')),
        el('p.small.muted', 'A alteração só vai para o disco ao salvar. "Restaurar original" desfaz.'));
      App.modal('Aplicar topologia', body, () => {
        const built = P().buildTopology(preset, Object.assign(o, { nodeTemplate: (net.nodes || [])[0] }));
        App.setValue('network', ['nodes'], built.nodes);
        App.setValue('network', ['links'], built.links);
        ui.topo = '';
        App.renderTab();
        const names = new Set(built.nodes.map((n) => n.name));
        const gens = (App.isPlainObject(st.model.traffic) && Array.isArray(st.model.traffic.requestGenerators)) ? st.model.traffic.requestGenerators : [];
        const bad = gens.filter((g) => !names.has(String(g.source)) || !names.has(String(g.destination))).length;
        App.toast(preset.name + ' aplicada' + (bad ? ' — ' + bad + ' gerador(es) de tráfego usam nós que não existem' : ''), bad ? 'warn' : 'ok', bad ? 7000 : 3000);
      }, 'Aplicar');
    };
    const uniform = () => {
      let o;
      try { o = readOpts(); } catch (e) { App.toast(e.message, 'err'); return; }
      App.setValue('network', ['links'], links.map((l) => Object.assign({}, l, { slots: o.slots, spectrum: o.spectrum })));
      App.renderTab();
      App.toast('Todos os enlaces com ' + o.slots + ' slots de ' + App.fmtNum(o.spectrum / 1e9, 3) + ' GHz', 'ok');
    };
    const saveXY = () => {
      const nodes = (net.nodes || []).map((n) => {
        const p = info.pos.get(String(n.name));
        return p ? Object.assign({}, n, { x: Math.round(p.x), y: Math.round(p.y) }) : n;
      });
      App.setValue('network', ['nodes'], nodes);
      App.renderTab();
    };

    const topoCard = el('div.net-card',
      el('h3', 'Topologia'),
      el('div.fields.two',
        el('label.field', el('div.flabel', 'Topologia pronta'), topoSel),
        el('label.field', el('div.flabel', 'Slots por enlace'), slots),
        el('label.field', el('div.flabel', 'Largura do slot (GHz)'), ghz)),
      el('div.toolbar',
        el('button.btn.primary.small', { type: 'button', disabled: !preset, on: { click: apply } }, 'Aplicar topologia'),
        el('button.btn.small', { type: 'button', title: 'Mesmo número de slots e mesma largura em todos os enlaces atuais', on: { click: uniform } }, 'Uniformizar enlaces atuais'),
        !preset && info.source !== 'file' && (net.nodes || []).length ? el('button.btn.small.ghost', { type: 'button', title: 'Grava as posições do desenho nos campos x/y dos nós (para usar e ajustar no mapa de calor)', on: { click: saveXY } }, 'Gravar posições x/y') : null,
        el('label.check.small', km, ' comprimentos (km)')),
      topoBox, caption,
      el('p.small.muted', 'Todos os enlaces são bidirecionais (dois enlaces direcionais com o mesmo comprimento). Nós e enlaces continuam editáveis nas tabelas abaixo; o desenho acompanha as alterações. Para mover um nó, edite x/y na tabela de nós.'));

    // ---------------- cores
    const cores = Array.isArray(net.cores) ? net.cores : [];
    const curPreset = P().corePresetOf(cores);
    if (ui.cores && !P().CORES[ui.cores]) ui.cores = '';
    const coreSel = el('select.input',
      el('option', { value: '' }, 'Atual do arquivo (' + cores.length + ' núcleos' + (curPreset ? '' : cores.length ? ', layout próprio' : '') + ')'),
      [2, 7, 12, 19].map((n) => el('option', { value: String(n) }, P().CORES[n].name)));
    coreSel.value = ui.cores;
    coreSel.addEventListener('change', () => { ui.cores = coreSel.value; App.renderTab(); });
    const showN = ui.cores ? Number(ui.cores) : null;
    const showCfg = showN ? P().coresConfig(showN) : cores;
    const coreBox = el('div.net-preview.cores');
    if (showCfg.length) coreBox.append(coresPreview(showCfg, qkdCore));
    else coreBox.append(el('div.muted.small', 'O arquivo não tem núcleos.'));
    const desc = showN ? P().CORES[showN].desc : curPreset ? P().CORES[curPreset].desc : cores.length ? 'Layout lido de network.cores (não corresponde a 2, 7, 12 ou 19 núcleos padrão).' : '';
    const adj = el('details.small', el('summary', 'Vizinhos de cada núcleo (fonte de crosstalk)'),
      el('div.mono.small', showCfg.map((c) => el('div', c.id + ': ' + (c.adjacentCores || []).join(', ')))));
    const applyCores = () => {
      if (!showN) return;
      App.setValue('network', ['cores'], P().coresConfig(showN));
      ui.cores = '';
      App.renderTab();
      const warn = qkdCore !== null && qkdCore >= showN;
      App.toast(P().CORES[showN].name + ' aplicada' + (warn ? ' — o núcleo QKD (' + qkdCore + ') não existe nesta fibra' : ''), warn ? 'warn' : 'ok', warn ? 7000 : 3000);
    };
    const coreCard = el('div.net-card',
      el('h3', 'Fibra multinúcleo'),
      el('label.field', el('div.flabel', 'Arquitetura de núcleos'), coreSel),
      el('div.toolbar', el('button.btn.primary.small', { type: 'button', disabled: !showN, on: { click: applyCores } }, 'Aplicar núcleos'),
        showN ? el('span.small.muted', 'pré-visualização, ainda não aplicada') : null),
      coreBox,
      el('p.small.muted', desc + (qkdCore !== null ? ' Em lilás: núcleo reservado para QKD (' + qkdCore + ').' : '')),
      adj,
      el('p.small.muted', 'O mesmo desenho é usado no diagrama de uso e bloqueio por núcleo dos resultados.'));

    return el('section.panel.net-editor', el('h2', 'Topologia e fibra'), el('div.net-grid', topoCard, coreCard));
  }

  App.netEditor = { panel };
})(window.App);
