/*
 * Predefined topologies and multi-core fibre layouts.
 *
 * - TOPOLOGIES: node coordinates (screen units, y downwards) and undirected links
 *   with their length in km. Applying one writes both directions of every link
 *   (same slots and slot width on all links) and stores the coordinates as
 *   "x"/"y" fields of each node; the simulator ignores these extra fields.
 * - CORES: positions (fibre radius ~ 1) and adjacency of the 2, 7, 12 and 19-core
 *   MCF layouts. The same positions are used in the Rede preview and in the
 *   per-core diagram of the results.
 * - coordsFor(network): node coordinates of any network (x/y fields, a known
 *   topology with the same links, or an automatic force layout).
 */
(function (App) {
  'use strict';

  const T = (id, name, desc, xy, links) => ({
    id, name, desc,
    nodes: Object.entries(xy).map(([n, p]) => ({ id: n, x: p[0], y: p[1] })),
    links: links.trim().split(/\s+/).map((s) => {
      const m = /^(\d+)-(\d+):(\d+(?:\.\d+)?)$/.exec(s);
      return [m[1], m[2], Number(m[3])];
    }),
  });

  const TOPOLOGIES = [
    T('nsfnet', 'NSFNet (14 nós, 21 enlaces)', 'Rede acadêmica dos EUA', {
      1: [160, 137], 2: [28, 265], 3: [158, 398], 4: [258, 325], 5: [402, 390], 6: [530, 530], 7: [535, 280],
      8: [617, 200], 9: [746, 200], 10: [705, 512], 11: [712, 66], 12: [887, 117], 13: [952, 257], 14: [853, 340],
    }, `1-2:1050 1-3:1500 1-8:1800 2-3:600 2-4:600 3-6:1500 4-5:600 4-11:1500 5-6:1300 5-7:600 6-10:1050
        6-14:1500 7-8:750 7-10:1350 8-9:750 9-10:800 9-12:300 9-13:300 11-12:500 11-13:750 12-14:800 13-14:300`),
    T('usa', 'USA (24 nós, 43 enlaces)', 'Backbone continental dos EUA', {
      1: [128, 52], 2: [86, 152], 3: [228, 146], 4: [66, 272], 5: [208, 275], 6: [104, 328], 7: [88, 406], 8: [245, 433],
      9: [342, 268], 10: [363, 403], 11: [490, 140], 12: [499, 258], 13: [500, 378], 14: [472, 460], 15: [855, 45],
      16: [661, 162], 17: [824, 170], 18: [641, 257], 19: [787, 245], 20: [746, 306], 21: [643, 383], 22: [793, 383],
      23: [633, 481], 24: [740, 481],
    }, `1-2:400 1-3:500 2-3:475 2-4:550 3-5:500 3-9:600 3-11:950 4-5:500 4-6:125 4-7:500 5-6:425 5-8:550 5-9:500
        6-7:400 7-8:600 8-10:450 9-10:500 9-11:700 9-12:500 10-13:475 10-14:400 11-12:450 11-15:1300 11-16:650
        12-13:450 12-18:500 13-14:325 13-21:550 14-23:600 15-17:600 16-17:650 16-18:300 17-19:350 18-19:500
        18-20:400 18-21:500 19-20:150 20-21:425 20-22:300 21-22:500 21-23:400 22-24:450 23-24:450`),
    T('cost239', 'COST239 (11 nós, 26 enlaces)', 'Rede pan-europeia', {
      1: [337, 62], 2: [102, 165], 3: [220, 227], 4: [395, 188], 5: [158, 334], 6: [313, 338], 7: [520, 268],
      8: [88, 437], 9: [453, 380], 10: [677, 226], 11: [500, 465],
    }, `1-2:1510 1-3:960 1-4:590 1-7:940 2-3:750 2-5:590 2-8:650 3-4:860 3-5:510 3-6:590 4-7:540 4-8:1290
        4-10:860 5-6:520 5-8:500 5-11:1130 6-7:930 6-8:600 6-9:550 7-9:765 7-10:520 8-9:800 8-11:1020 9-10:930
        9-11:520 10-11:1020`),
    T('net28', 'Rede de 28 nós (44 enlaces)', 'Topologia continental de 28 nós', {
      1: [158, 115], 2: [57, 212], 3: [182, 302], 4: [287, 272], 5: [420, 40], 6: [512, 85], 7: [437, 184],
      8: [365, 276], 9: [446, 292], 10: [618, 290], 11: [216, 382], 12: [276, 343], 13: [373, 356], 14: [326, 416],
      15: [412, 418], 16: [531, 389], 17: [141, 512], 18: [270, 522], 19: [333, 481], 20: [400, 511], 21: [497, 447],
      22: [586, 447], 23: [30, 662], 24: [194, 628], 25: [459, 650], 26: [522, 523], 27: [623, 540], 28: [716, 766],
    }, `1-2:500 1-4:1300 2-3:700 3-4:700 3-12:700 3-11:500 4-8:700 4-12:200 5-6:700 5-7:700 6-10:1500 7-9:700
        8-9:500 8-13:700 9-10:1000 9-13:700 9-15:1000 9-16:500 10-22:1000 11-12:500 11-14:700 11-17:1000
        11-18:700 12-13:500 13-14:200 13-15:500 14-15:500 14-19:200 15-20:500 15-21:700 16-21:500 16-22:700
        17-23:1000 18-19:500 18-24:1000 19-20:500 20-25:700 21-26:500 22-27:500 23-24:1000 25-26:1000 25-28:1800
        26-27:700 27-28:1500`),
    T('net12', 'Rede de 12 nós (17 enlaces)', 'Topologia regional de 12 nós', {
      1: [482, 20], 2: [477, 115], 3: [440, 213], 4: [390, 170], 5: [306, 144], 6: [204, 108], 7: [294, 224],
      8: [173, 176], 9: [113, 173], 10: [138, 232], 11: [51, 218], 12: [35, 342],
    }, `1-2:594 1-4:1257 2-3:352 3-4:48 3-7:366 4-5:251 5-6:251 5-7:251 6-8:253 7-8:187 7-10:491 8-9:342
        9-10:67 9-11:281 10-11:365 10-12:1159 11-12:912`),
    T('core5', 'Núcleo pequeno (5 nós, 6 enlaces)', 'Rede de 5 nós da qualificação', {
      1: [180, 58], 2: [370, 58], 3: [180, 480], 4: [352, 508], 5: [270, 270],
    }, '1-2:76 1-3:86 2-5:90 5-3:80 2-4:100 3-4:70'),
    T('butterfly', 'Borboleta (6 nós, 8 enlaces)', 'Topologia em borboleta', {
      1: [520, 62], 2: [440, 186], 3: [597, 186], 4: [440, 380], 5: [597, 380], 6: [520, 512],
    }, '1-2:30 1-3:40 2-3:50 2-4:55 3-5:55 4-5:50 4-6:40 5-6:30'),
    T('metro', 'Metropolitana (8 nós, 11 enlaces)', 'Rede metropolitana', {
      1: [777, 68], 2: [670, 215], 3: [898, 215], 4: [670, 370], 5: [777, 370], 6: [898, 370], 7: [717, 515], 8: [843, 515],
    }, '1-2:30 1-3:25 2-3:40 2-4:50 3-6:20 4-7:40 5-6:40 5-7:45 5-8:30 6-8:35 7-8:20'),
  ];

  // ------------------------------------------------------------ multi-core fibres

  function ringPos(order, radius, startDeg, clockwise) {
    const out = {};
    order.forEach((id, k) => {
      const th = (startDeg + (clockwise ? -1 : 1) * k * 360 / order.length) * Math.PI / 180;
      out[id] = { x: Math.cos(th) * radius, y: -Math.sin(th) * radius };
    });
    return out;
  }

  function coreSet(name, desc, pos, adj) {
    const ids = Object.keys(pos).map(Number).sort((a, b) => a - b);
    return { n: ids.length, name, desc, pos, adj, ids };
  }

  const CORES = {};
  CORES[2] = coreSet('2 núcleos', 'Núcleo 0 à esquerda e 1 à direita, vizinhos entre si.',
    { 0: { x: -0.5, y: 0 }, 1: { x: 0.5, y: 0 } }, { 0: [1], 1: [0] });
  (function () {
    const pos = Object.assign({ 0: { x: 0, y: 0 } }, ringPos([1, 2, 3, 4, 5, 6], 1, 90, false));
    const adj = { 0: [1, 2, 3, 4, 5, 6] };
    for (let i = 1; i <= 6; i++) adj[i] = [0, i === 1 ? 6 : i - 1, i === 6 ? 1 : i + 1].sort((a, b) => a - b);
    CORES[7] = coreSet('7 núcleos', 'Núcleo 0 no centro (vizinho de todos) e 1–6 em anel hexagonal.', pos, adj);
  })();
  (function () {
    const order = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0];
    const pos = ringPos(order, 1, 90, true);
    const adj = {};
    for (let i = 0; i < 12; i++) adj[i] = [(i + 11) % 12, (i + 1) % 12].sort((a, b) => a - b);
    CORES[12] = coreSet('12 núcleos', 'Anel de 12 núcleos (1 no topo, sentido horário, 0 à esquerda do 1); cada núcleo tem 2 vizinhos.', pos, adj);
  })();
  (function () {
    const px = {
      0: [272, 72], 1: [362, 48], 2: [455, 72], 3: [200, 130], 4: [307, 150], 5: [412, 150], 6: [522, 135],
      7: [172, 238], 8: [267, 238], 9: [456, 238], 10: [552, 238], 11: [200, 338], 12: [307, 325], 13: [412, 325],
      14: [522, 338], 15: [272, 410], 16: [362, 440], 17: [455, 410], 18: [362, 240],
    };
    const pos = {};
    for (const [id, p] of Object.entries(px)) pos[id] = { x: (p[0] - 362) / 100, y: (p[1] - 240) / 100 };
    const adj = {
      0: [1, 3, 4], 1: [0, 2, 4, 5], 2: [1, 5, 6], 3: [0, 4, 7, 8], 4: [0, 1, 3, 5, 8, 18], 5: [1, 2, 4, 6, 9, 18],
      6: [2, 5, 9, 10], 7: [3, 8, 11], 8: [3, 4, 7, 11, 12, 18], 9: [5, 6, 10, 13, 14, 18], 10: [6, 9, 14],
      11: [7, 8, 12, 15], 12: [8, 11, 13, 15, 16, 18], 13: [9, 12, 14, 16, 17, 18], 14: [9, 10, 13, 17],
      15: [11, 12, 16], 16: [12, 13, 15, 17], 17: [13, 14, 16], 18: [4, 5, 8, 9, 12, 13],
    };
    CORES[19] = coreSet('19 núcleos', 'Rede hexagonal: 18 no centro, anel interno 4, 5, 9, 13, 12, 8 e anel externo com 12 núcleos.', pos, adj);
  })();

  /** network.cores array for a preset. */
  function coresConfig(n) {
    const c = CORES[n];
    return c.ids.map((id) => ({ id, adjacentCores: c.adj[id].slice() }));
  }

  /** Which preset (2/7/12/19) a network.cores array is, or null. */
  function corePresetOf(cores) {
    if (!Array.isArray(cores)) return null;
    const c = CORES[cores.length];
    if (!c) return null;
    for (const core of cores) {
      const want = c.adj[core.id];
      if (!want) return null;
      const got = [...new Set((core.adjacentCores || []).map(Number))].sort((a, b) => a - b);
      if (got.length !== want.length || got.some((v, i) => v !== want[i])) return null;
    }
    return cores.length;
  }

  /** Positions (radius ~1, y down) of the cores of a network.cores array. */
  function coreLayout(cores) {
    const pos = new Map();
    const preset = corePresetOf(cores);
    if (preset) {
      for (const core of cores) pos.set(core.id, Object.assign({}, CORES[preset].pos[core.id]));
      return pos;
    }
    const n = cores.length;
    const ids = cores.map((c) => c.id).sort((a, b) => a - b);
    const ring = (list, radius, startDeg) => Object.entries(ringPos(list, radius, startDeg, false)).forEach(([id, p]) => pos.set(Number(id), p));
    const center = n > 4 ? cores.find((c) => (c.adjacentCores || []).length === n - 1) : null;
    if (n === 2) { pos.set(ids[0], { x: -0.5, y: 0 }); pos.set(ids[1], { x: 0.5, y: 0 }); } else if (center) {
      pos.set(center.id, { x: 0, y: 0 });
      ring(ids.filter((i) => i !== center.id), 1, 90);
    } else ring(ids, 1, 90);
    return pos;
  }

  // ------------------------------------------------------------ topology helpers

  const pairKey = (a, b) => (String(a) < String(b) ? a + '|' + b : b + '|' + a);

  /** Undirected links of a network: [{a, b, km, dirs:Set('a>b')}] in file order. */
  function undirectedLinks(network) {
    const map = new Map();
    for (const l of (network && network.links) || []) {
      const a = String(l.source), b = String(l.destination);
      const k = pairKey(a, b);
      if (!map.has(k)) map.set(k, { a, b, km: Number(l.size), dirs: new Set() });
      map.get(k).dirs.add(a + '>' + b);
    }
    return [...map.values()];
  }

  function matchTopology(network) {
    const names = ((network && network.nodes) || []).map((n) => String(n.name));
    const links = undirectedLinks(network);
    for (const t of TOPOLOGIES) {
      if (t.nodes.length !== names.length || t.links.length !== links.length) continue;
      const set = new Set(t.links.map(([a, b]) => pairKey(a, b)));
      if (names.every((n) => t.nodes.find((x) => x.id === n)) && links.every((l) => set.has(pairKey(l.a, l.b)))) return t;
    }
    return null;
  }

  /** Simple deterministic force layout (Fruchterman–Reingold) for networks without coordinates. */
  function forceLayout(names, links) {
    const n = names.length;
    const P = names.map((_, i) => ({ x: Math.cos(2 * Math.PI * i / n) * 300, y: Math.sin(2 * Math.PI * i / n) * 300 }));
    if (n < 3) return P;
    const idx = new Map(names.map((nm, i) => [nm, i]));
    const E = links.map((l) => [idx.get(l.a), idx.get(l.b)]).filter(([a, b]) => a !== undefined && b !== undefined);
    const k = Math.sqrt((600 * 600) / n);
    let temp = 60;
    for (let it = 0; it < 400; it++) {
      const D = P.map(() => ({ x: 0, y: 0 }));
      for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
        const dx = P[i].x - P[j].x, dy = P[i].y - P[j].y;
        const d = Math.max(0.01, Math.hypot(dx, dy));
        const f = (k * k) / d;
        D[i].x += dx / d * f; D[i].y += dy / d * f; D[j].x -= dx / d * f; D[j].y -= dy / d * f;
      }
      for (const [a, b] of E) {
        const dx = P[a].x - P[b].x, dy = P[a].y - P[b].y;
        const d = Math.max(0.01, Math.hypot(dx, dy));
        const f = (d * d) / k;
        D[a].x -= dx / d * f; D[a].y -= dy / d * f; D[b].x += dx / d * f; D[b].y += dy / d * f;
      }
      for (let i = 0; i < n; i++) {
        const d = Math.max(0.01, Math.hypot(D[i].x, D[i].y));
        P[i].x += D[i].x / d * Math.min(d, temp); P[i].y += D[i].y / d * Math.min(d, temp);
      }
      temp = Math.max(1, temp * 0.985);
    }
    return P;
  }

  /**
   * Coordinates of the nodes of a network: Map(name -> {x, y}) plus where they came
   * from ('file' = x/y fields, 'preset' = known topology, 'auto' = force layout).
   */
  function coordsFor(network) {
    const nodes = ((network && network.nodes) || []).map((n) => ({ name: String(n.name), x: Number(n.x), y: Number(n.y) }));
    const pos = new Map();
    if (nodes.length && nodes.every((n) => Number.isFinite(n.x) && Number.isFinite(n.y))) {
      for (const n of nodes) pos.set(n.name, { x: n.x, y: n.y });
      return { pos, source: 'file' };
    }
    const t = matchTopology(network);
    if (t) {
      for (const n of t.nodes) pos.set(n.id, { x: n.x, y: n.y });
      return { pos, source: 'preset', topology: t };
    }
    const names = nodes.map((n) => n.name);
    const P = forceLayout(names, undirectedLinks(network));
    names.forEach((nm, i) => pos.set(nm, P[i]));
    return { pos, source: 'auto' };
  }

  /** Model for figRender ('topo') drawing a network (no values): used by the Rede preview. */
  function topoPanel(network, title, extra) {
    const { pos } = coordsFor(network);
    const q = network && network.qkd && network.qkd.enabled ? Number(network.qkd.qtndKm) : 0;
    return Object.assign({
      title: title || '',
      nodes: [...pos.entries()].map(([id, p]) => ({ id, x: p.x, y: p.y })),
      links: undirectedLinks(network).map((l) => ({ a: l.a, b: l.b, km: l.km, both: l.dirs.size > 1 })),
      qtnd: Number.isFinite(q) && q > 0 ? q : 0, // Parte 27: trusted nodes every qtnd km
    }, extra || {});
  }

  /** nodes/links arrays for a predefined topology (both directions, same slots/spectrum). */
  function buildTopology(t, opts) {
    const tpl = Object.assign({ transmitters: 1000, receivers: 1000, regenerators: 0 }, opts.nodeTemplate || {});
    delete tpl.name; delete tpl.x; delete tpl.y;
    const nodes = t.nodes.map((n) => Object.assign({ name: n.id }, tpl, { x: n.x, y: n.y }));
    const links = [];
    for (const [a, b, km] of t.links) {
      for (const [s, d] of [[a, b], [b, a]]) links.push({ source: s, destination: d, slots: opts.slots, spectrum: opts.spectrum, size: km });
    }
    return { nodes, links };
  }

  // ------------------------------------------------------------ heat colours (green → yellow → orange → red)

  const HEAT_STOPS = [[0, [0, 166, 81]], [1 / 3, [255, 221, 0]], [2 / 3, [255, 140, 0]], [1, [215, 25, 28]]];
  function heatColor(t) {
    if (!Number.isFinite(t)) return '#9a9a9a';
    t = Math.max(0, Math.min(1, t));
    for (let i = 1; i < HEAT_STOPS.length; i++) {
      const [t1, c1] = HEAT_STOPS[i];
      if (t <= t1) {
        const [t0, c0] = HEAT_STOPS[i - 1];
        const f = (t - t0) / (t1 - t0);
        return 'rgb(' + c0.map((v, k) => Math.round(v + (c1[k] - v) * f)).join(',') + ')';
      }
    }
    return 'rgb(215,25,28)';
  }

  App.presets = {
    TOPOLOGIES, CORES, HEAT_STOPS, coresConfig, corePresetOf, coreLayout, undirectedLinks, matchTopology,
    coordsFor, topoPanel, buildTopology, heatColor, pairKey,
  };
})(window.App);
