/*
 * Rendering engine of the chart studio (Estúdio de gráficos).
 * Pure functions: model + style -> standalone SVG element, with every visual
 * property taken from the style object (no CSS), so the exported SVG/PNG looks
 * exactly like the preview.
 *
 * Model kinds:
 *   line  : { series:[{key,name,style,points:[{x,mean,ci,n,label}]}], xCategories?:[...] }
 *   bars  : { categories:[{key,label}], series:[{key,name,style,values:{catKey:{mean,ci,n}}}], stacked, normalize }
 *   panels: { panels:[{title, model}] }    (small multiples of bars/line)
 *   cores : { diagrams:[{title, cores:[{id,x,y,util,block,unused}]}] }
 *   slots : { panels:[{title, from, to, rows:[{link, core, values:Float64Array}]}], range:{min,max}, legendTitle }
 */
(function (App) {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  function S(tag, attrs, text) {
    const n = document.createElementNS(NS, tag);
    if (attrs) for (const k in attrs) { const v = attrs[k]; if (v !== undefined && v !== null && v !== '') n.setAttribute(k, v); }
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }

  // ------------------------------------------------------------ palettes & presets

  const PALETTES = {
    office: ['#4472C4', '#ED7D31', '#A5A5A5', '#FFC000', '#5B9BD5', '#70AD47', '#264478', '#9E480E', '#636363', '#997300', '#255E91', '#43682B', '#698ED0', '#F1975A', '#B7B7B7', '#FFCD33', '#7CAFDD', '#8CC168', '#C00000', '#7030A0'],
    artigo: ['#E31A1C', '#33A02C', '#6A3D9A', '#1F78B4', '#FF7F00', '#B15928', '#A6CEE3', '#B2DF8A', '#FB9A99', '#FDBF6F', '#CAB2D6', '#FFD92F', '#000000', '#666666', '#1B9E77', '#D95F02', '#7570B3', '#E7298A', '#66A61E', '#E6AB02'],
    tableau: ['#4E79A7', '#F28E2B', '#E15759', '#76B7B2', '#59A14F', '#EDC948', '#B07AA1', '#FF9DA7', '#9C755F', '#BAB0AC', '#86BCB6', '#FABFD2', '#8CD17D', '#B6992D', '#499894', '#D37295', '#79706E', '#D4A6C8', '#A0CBE8', '#FFBE7D'],
    okabe: ['#0072B2', '#E69F00', '#009E73', '#CC79A7', '#56B4E9', '#D55E00', '#F0E442', '#000000', '#999999', '#004C7A', '#9A6B00', '#006B4E', '#8A527A', '#3B7AA0', '#8F3F00', '#A39B2F', '#555555', '#7FB8D8', '#F2CF80', '#7FCEB9'],
    cinza: ['#000000', '#555555', '#888888', '#AAAAAA', '#333333', '#777777', '#999999', '#BBBBBB', '#222222', '#666666', '#444444', '#CCCCCC'],
  };
  const PALETTE_NAMES = { office: 'Excel (Office)', artigo: 'Artigo (cores fortes)', tableau: 'Tableau', okabe: 'Okabe-Ito (daltonismo)', cinza: 'Tons de cinza (impressão)' };
  const MARKERS = ['circle', 'square', 'diamond', 'triangle', 'triangleDown', 'plus', 'x', 'star', 'none'];
  const MARKER_NAMES = { circle: 'Círculo', square: 'Quadrado', diamond: 'Losango', triangle: 'Triângulo', triangleDown: 'Triângulo invertido', plus: 'Mais (+)', x: 'Xis (×)', star: 'Estrela', none: 'Sem marcador' };
  const DASHES = { solid: 'Contínua', dashed: 'Tracejada', dotted: 'Pontilhada', dashdot: 'Traço-ponto', longdash: 'Traço longo' };
  const FONTS = ['Calibri, Carlito, Arial, sans-serif', 'Arial, Helvetica, sans-serif', '"Times New Roman", Times, serif', '"Segoe UI", system-ui, sans-serif', 'Helvetica, Arial, sans-serif', 'Georgia, serif', '"Courier New", monospace'];

  function defaultStyle() {
    return {
      width: 760, height: 440, bg: '#ffffff', plotBg: '#ffffff', border: false, borderColor: '#d9d9d9',
      font: FONTS[0], fontSize: 12, titleSize: 15, textColor: '#404040', titleColor: '#262626',
      title: '', showTitle: true, xTitle: '', yTitle: '', panelLabel: '', panelLabelSize: 16,
      grid: 'y', gridColor: '#d9d9d9', gridDash: 'solid', axisColor: '#808080', ticksOut: false,
      legend: 'bottom', legendCols: 0, legendBox: true, legendSize: 0, legendBg: '#ffffff',
      yMin: '', yMax: '', yLog: false, yFormat: 'auto', yDigits: 3, yStep: '', decimal: ',',
      xMin: '', xMax: '', xFormat: 'auto', xDigits: 0, xLabelRotate: 0, xAsCategories: true,
      errorBars: 'bars', capWidth: 4, errorWidth: 1.2,
      lineWidth: 2, markerSize: 7, palette: 'office',
      barGap: 0.35, seriesGap: 0.04, barValues: false, barValueDigits: 3, barOutline: false,
      panelCols: 1, panelGap: 18, panelHeight: 0,
      // cores diagram
      coreCols: 2, coreFiber: '#ededed', coreFiberStroke: '#9a9a9a', coreFill: '#ffffff', coreStroke: '#000000',
      coreBlockColor: '#f4b183', coreUtilColor: '#9dc3e6', coreShow: 'both', coreDigits: 6, coreHideUnused: true,
      coreHeat: 'none', coreHeatColor: '#2f75b5', coreLegend: true, coreLabelSize: 0,
      // topology / link heat map
      topoCols: 3, topoNodeR: 11, topoLinkWidth: 5, topoDir: 'both', topoLabels: 'none', topoLegend: 'right',
      topoFormat: 'percent', topoDigits: 1, topoMin: '', topoMax: '', topoNodeFill: '#ffffff', topoNodeStroke: '#000000',
      topoNoData: '#bdbdbd', topoLabelColor: '#c00000', topoAutoHeight: true, topoPanelBorder: false,
      topoTrusted: true, topoTrustedFill: '#7030a0', topoTrustedR: 0,
      // spectrum (slot) heat map
      slotScale: 'traffic', slotRange: 'fixed', slotMin: '', slotMax: '', slotRowH: 12, slotTickStep: '', slotLegend: 'right',
      slotFormat: 'percent', slotDigits: 1, slotGridColor: '#000000', slotZero: 'scale', slotCellGrid: true, slotLinkSep: true, slotPanelTitle: true,
    };
  }

  // ------------------------------------------------------------ number formatting

  function fmtNumber(v, mode, digits, dec, step) {
    if (v === null || v === undefined || !Number.isFinite(v)) return '';
    let t;
    if (mode === 'percent') {
      const d = Number.isFinite(digits) ? digits : 1;
      t = (v * 100).toFixed(d) + '%';
    } else if (mode === 'sci') {
      t = v === 0 ? '0' : v.toExponential(Number.isFinite(digits) ? digits : 2).replace('e', 'E').replace('E+', 'E+');
    } else if (mode === 'fixed') {
      t = v.toFixed(Number.isFinite(digits) ? digits : 3);
    } else {
      // auto: decimals taken from the tick step when available
      const a = Math.abs(v);
      if (a !== 0 && (a < 1e-4 || a >= 1e7)) t = v.toExponential(2).replace('e', 'E');
      else if (step) {
        t = v.toFixed(Math.min(12, stepDecimals(step)));
      } else {
        t = String(+v.toPrecision(Number.isFinite(digits) && digits > 0 ? digits : 4));
        if (/e/.test(t)) t = v.toExponential(2).replace('e', 'E');
      }
    }
    return dec === ',' ? t.replace('.', ',') : t;
  }

  function niceStep(span, count) {
    const raw = span / Math.max(1, count);
    const mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const n = raw / mag;
    return (n < 1.5 ? 1 : n < 2.25 ? 2 : n < 3.5 ? 2.5 : n < 7.5 ? 5 : 10) * mag;
  }
  function stepDecimals(step) {
    const t = String(+step.toPrecision(6));
    if (/e-/.test(t)) return Math.min(12, Number(t.split('e-')[1]) + ((t.split('e')[0].split('.')[1] || '').length));
    return (t.split('.')[1] || '').length;
  }
  function linearTicks(min, max, count, fixedStep) {
    if (!(max > min)) { max = min + 1; }
    let step = fixedStep > 0 ? fixedStep : niceStep(max - min, count);
    if ((max - min) / step > 200) step = niceStep(max - min, count);
    const out = [];
    const start = Math.ceil(min / step - 1e-9) * step;
    for (let v = start; v <= max + step * 1e-6; v += step) out.push(+v.toPrecision(12));
    return { ticks: out, step };
  }

  // ------------------------------------------------------------ text measurement

  let measureCtx = null;
  function textWidth(text, size, font, weight) {
    if (!measureCtx) measureCtx = document.createElement('canvas').getContext('2d');
    measureCtx.font = (weight || 'normal') + ' ' + size + 'px ' + font;
    return measureCtx.measureText(String(text)).width;
  }

  // ------------------------------------------------------------ marks

  function dashArray(kind, w) {
    const k = Math.max(1, w);
    switch (kind) {
      case 'dashed': return (4 * k) + ' ' + (2.5 * k);
      case 'dotted': return '0.1 ' + (2.2 * k);
      case 'dashdot': return (4.5 * k) + ' ' + (2 * k) + ' 0.1 ' + (2 * k);
      case 'longdash': return (8 * k) + ' ' + (3 * k);
      default: return null;
    }
  }

  function marker(shape, x, y, size, color, hollow, bg, strokeW) {
    const r = size / 2;
    const fill = hollow ? bg : color;
    const sw = strokeW || Math.max(1.2, size / 6);
    const base = { fill, stroke: color, 'stroke-width': sw, 'stroke-linejoin': 'round' };
    const poly = (pts) => S('polygon', Object.assign({ points: pts.map((p) => (x + p[0]).toFixed(2) + ',' + (y + p[1]).toFixed(2)).join(' ') }, base));
    switch (shape) {
      case 'none': return null;
      case 'square': return S('rect', Object.assign({ x: (x - r * 0.88).toFixed(2), y: (y - r * 0.88).toFixed(2), width: (r * 1.76).toFixed(2), height: (r * 1.76).toFixed(2) }, base));
      case 'diamond': return poly([[0, -r * 1.15], [r * 1.15, 0], [0, r * 1.15], [-r * 1.15, 0]]);
      case 'triangle': return poly([[0, -r * 1.15], [r * 1.1, r * 0.8], [-r * 1.1, r * 0.8]]);
      case 'triangleDown': return poly([[0, r * 1.15], [r * 1.1, -r * 0.8], [-r * 1.1, -r * 0.8]]);
      case 'star': {
        const pts = [];
        for (let i = 0; i < 10; i++) {
          const a = -Math.PI / 2 + i * Math.PI / 5;
          const rr = i % 2 ? r * 0.5 : r * 1.2;
          pts.push([Math.cos(a) * rr, Math.sin(a) * rr]);
        }
        return poly(pts);
      }
      case 'plus': case 'x': {
        const g = S('g', { stroke: color, 'stroke-width': Math.max(1.6, size / 4.5), 'stroke-linecap': 'round' });
        const d = r * 1.05;
        if (shape === 'plus') {
          g.appendChild(S('line', { x1: x - d, x2: x + d, y1: y, y2: y }));
          g.appendChild(S('line', { x1: x, x2: x, y1: y - d, y2: y + d }));
        } else {
          const e = d * 0.8;
          g.appendChild(S('line', { x1: x - e, x2: x + e, y1: y - e, y2: y + e }));
          g.appendChild(S('line', { x1: x - e, x2: x + e, y1: y + e, y2: y - e }));
        }
        return g;
      }
      default: return S('circle', Object.assign({ cx: x.toFixed(2), cy: y.toFixed(2), r: r.toFixed(2) }, base));
    }
  }

  /** Native tooltip (shown on hover in the launcher). */
  function tip(name, p, st, share) {
    const f = (v) => fmtNumber(v, 'auto', 6, st.decimal);
    let t = name + ': ' + f(p.mean);
    if (Number.isFinite(p.ci)) t += ' ± ' + f(p.ci) + ' (IC 95 %)';
    if (p.n) t += ' · n = ' + p.n;
    if (share !== null && share !== undefined) t += ' · ' + (share * 100).toFixed(1) + ' %';
    if (p.x !== undefined && p.label) t += ' · ' + p.label;
    return S('title', null, t);
  }

  function textEl(x, y, str, o) {
    o = o || {};
    const t = S('text', {
      x: x.toFixed ? x.toFixed(1) : x, y: y.toFixed ? y.toFixed(1) : y,
      'font-size': o.size, 'font-family': o.font, fill: o.color, 'text-anchor': o.anchor || 'start',
      'font-weight': o.weight, 'dominant-baseline': o.baseline, transform: o.transform, 'font-style': o.italic ? 'italic' : null,
    }, str);
    return t;
  }

  // ------------------------------------------------------------ legend

  function legendItemSwatch(item, st, x, y) {
    const g = S('g');
    if (item.kind === 'bar') {
      g.appendChild(S('rect', { x, y: y - 5.5, width: 11, height: 11, fill: item.color, stroke: st.barOutline ? '#000' : null, 'stroke-width': st.barOutline ? 0.6 : null }));
      return { node: g, w: 15 };
    }
    if (item.kind === 'box') {
      g.appendChild(S('rect', { x, y: y - 6, width: 22, height: 12, fill: item.color, stroke: '#000', 'stroke-width': 0.4 }));
      return { node: g, w: 26 };
    }
    const w = 30;
    if (item.line !== false) {
      g.appendChild(S('line', { x1: x, x2: x + w, y1: y, y2: y, stroke: item.color, 'stroke-width': item.width, 'stroke-dasharray': dashArray(item.dash, item.width), 'stroke-linecap': item.dash === 'dotted' ? 'round' : 'butt' }));
    }
    const m = marker(item.marker, x + w / 2, y, Math.min(item.markerSize, 10), item.color, item.hollow, st.legendBg || st.bg);
    if (m) g.appendChild(m);
    return { node: g, w: w + 5 };
  }

  /** Measures and builds a legend block; returns {w,h,draw(x,y)}. maxW limits the row width. */
  function buildLegend(items, st, maxW, forcedCols) {
    const fs = st.legendSize > 0 ? st.legendSize : Math.max(9, st.fontSize - 1);
    const rowH = Math.max(fs * 1.45, 15);
    const widths = items.map((it) => (it.kind === 'bar' ? 15 : it.kind === 'box' ? 26 : 35) + textWidth(it.name, fs, st.font) + 14);
    const colW = Math.max(...widths, 40);
    let cols = forcedCols || st.legendCols;
    if (!cols) cols = Math.max(1, Math.min(items.length, Math.floor((maxW - 12) / colW)));
    cols = Math.max(1, Math.min(cols, items.length));
    const rows = Math.ceil(items.length / cols);
    // column widths: max per column
    const cw = [];
    for (let c = 0; c < cols; c++) {
      let m = 0;
      for (let r = 0; r < rows; r++) { const i = r * cols + c; if (i < items.length) m = Math.max(m, widths[i]); }
      cw.push(m);
    }
    const pad = st.legendBox ? 8 : 2;
    const w = cw.reduce((a, b) => a + b, 0) + pad * 2 - 8;
    const h = rows * rowH + pad * 2 - (rowH - fs * 1.2) * 0.3;
    return {
      w, h,
      draw(x, y) {
        const g = S('g', { class: 'legend' });
        if (st.legendBox) g.appendChild(S('rect', { x, y, width: w, height: h, fill: st.legendBg || st.bg, stroke: '#bfbfbf', 'stroke-width': 0.8 }));
        else if (st.legendBg && st.legendBg !== st.bg) g.appendChild(S('rect', { x, y, width: w, height: h, fill: st.legendBg }));
        items.forEach((it, i) => {
          const r = Math.floor(i / cols), c = i % cols;
          const ix = x + pad + cw.slice(0, c).reduce((a, b) => a + b, 0);
          const iy = y + pad + r * rowH + rowH / 2;
          const sw = legendItemSwatch(it, st, ix, iy);
          g.appendChild(sw.node);
          g.appendChild(textEl(ix + sw.w, iy + fs * 0.35, it.name, { size: fs, font: st.font, color: st.textColor }));
        });
        return g;
      },
    };
  }

  // ------------------------------------------------------------ cartesian frame

  /**
   * Draws title, legend, axes and grid; returns the plotting rectangle and scales.
   * xSpec: {type:'num', min,max, ticks:[...]} | {type:'band', labels:[...]}
   */
  function frame(root, st, box, ySpec, xSpec, legendItems, opts) {
    opts = opts || {};
    const fs = st.fontSize;
    const font = st.font;
    let top = box.y + 8, bottom = box.y + box.h - 8, left = box.x + 8, right = box.x + box.w - 10;

    // panel label "(a)"
    if (st.panelLabel && !opts.noPanelLabel) {
      root.appendChild(textEl(box.x + 6, box.y + st.panelLabelSize + 2, st.panelLabel, { size: st.panelLabelSize, font, color: st.titleColor, weight: 700 }));
    }
    const title = opts.title !== undefined ? opts.title : st.title;
    if (st.showTitle && title) {
      const tfs = opts.titleSize || st.titleSize;
      root.appendChild(textEl(box.x + box.w / 2, top + tfs * 0.85, title, { size: tfs, font, color: st.titleColor, anchor: 'middle', weight: 700 }));
      top += tfs * 1.5;
    } else if (st.panelLabel && !opts.noPanelLabel) top += st.panelLabelSize * 0.6;

    // legend
    let legend = null;
    const lpos = opts.legend !== undefined ? opts.legend : st.legend;
    if (legendItems && legendItems.length && lpos !== 'none') {
      if (lpos === 'right') {
        legend = buildLegend(legendItems, st, box.w * 0.4, st.legendCols || 1);
        legend.x = right - legend.w; legend.y = top + Math.max(0, (bottom - top - legend.h) / 2);
        right -= legend.w + 12;
      } else if (lpos === 'top') {
        legend = buildLegend(legendItems, st, box.w - 20);
        legend.x = box.x + (box.w - legend.w) / 2; legend.y = top;
        top += legend.h + 8;
      } else if (lpos === 'bottom') {
        legend = buildLegend(legendItems, st, box.w - 20);
        legend.x = box.x + (box.w - legend.w) / 2; legend.y = bottom - legend.h;
        bottom -= legend.h + 10;
      } else {
        legend = buildLegend(legendItems, st, box.w * 0.5);
        legend.inside = lpos;
      }
    }

    // y axis
    const yTitle = opts.yTitle !== undefined ? opts.yTitle : st.yTitle;
    if (yTitle) left += fs * 1.6;
    const yInfo = ySpec;
    const yLabels = yInfo.ticks.map((t) => yInfo.fmt(t));
    const yLabW = Math.max(0, ...yLabels.map((l) => textWidth(l, fs, font)));
    left += yLabW + 8;

    // x axis labels height
    const xTitle = opts.xTitle !== undefined ? opts.xTitle : st.xTitle;
    if (xTitle) bottom -= fs * 1.7;
    const rot = Number(st.xLabelRotate) || 0;
    const xLabels = xSpec.type === 'band' ? xSpec.labels : xSpec.ticks.map((t) => xSpec.fmt(t));
    const xLabMax = Math.max(0, ...xLabels.map((l) => textWidth(l, fs, font)));
    const xLabH = rot === 0 ? fs * 1.35 : Math.abs(Math.sin(rot * Math.PI / 180)) * xLabMax + fs * 1.1;
    bottom -= xLabH + 4;
    if (rot === 0 && xLabels.length) right -= Math.min(20, textWidth(xLabels[xLabels.length - 1], fs, font) / 2);

    const plot = { x: left, y: top + 4, w: Math.max(30, right - left), h: Math.max(30, bottom - top - 4) };
    if (st.plotBg && st.plotBg !== st.bg) root.appendChild(S('rect', { x: plot.x, y: plot.y, width: plot.w, height: plot.h, fill: st.plotBg }));

    // scales
    const ys = yInfo.log
      ? (v) => plot.y + plot.h - (Math.log10(Math.max(v, yInfo.min)) - Math.log10(yInfo.min)) / (Math.log10(yInfo.max) - Math.log10(yInfo.min)) * plot.h
      : (v) => plot.y + plot.h - (v - yInfo.min) / (yInfo.max - yInfo.min || 1) * plot.h;
    let xs, band = null;
    if (xSpec.type === 'band') {
      const n = Math.max(1, xSpec.labels.length);
      const bw = plot.w / n;
      band = { bw, n };
      xs = (i) => plot.x + bw * (i + 0.5);
    } else {
      xs = (v) => plot.x + (v - xSpec.min) / (xSpec.max - xSpec.min || 1) * plot.w;
    }

    // grid
    const dashGrid = st.gridDash === 'solid' ? null : dashArray(st.gridDash, 0.8);
    const gridG = S('g');
    if (st.grid === 'y' || st.grid === 'xy') {
      for (const t of yInfo.ticks) {
        const y = ys(t);
        if (y < plot.y - 0.5 || y > plot.y + plot.h + 0.5) continue;
        gridG.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: y.toFixed(1), y2: y.toFixed(1), stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid }));
      }
      if (yInfo.log && yInfo.minor) {
        for (const t of yInfo.minor) {
          const y = ys(t);
          gridG.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: y.toFixed(1), y2: y.toFixed(1), stroke: st.gridColor, 'stroke-width': 0.4, 'stroke-dasharray': '2 3' }));
        }
      }
    }
    if (st.grid === 'x' || st.grid === 'xy') {
      if (xSpec.type === 'band') {
        for (let i = 0; i <= band.n; i++) { const x = plot.x + i * band.bw; gridG.appendChild(S('line', { x1: x, x2: x, y1: plot.y, y2: plot.y + plot.h, stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid })); }
      } else {
        for (const t of xSpec.ticks) { const x = xs(t); gridG.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: plot.y, y2: plot.y + plot.h, stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid })); }
      }
    }
    root.appendChild(gridG);

    // axes
    const ax = S('g');
    ax.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: plot.y + plot.h, y2: plot.y + plot.h, stroke: st.axisColor, 'stroke-width': 1 }));
    if (st.border) ax.appendChild(S('rect', { x: plot.x, y: plot.y, width: plot.w, height: plot.h, fill: 'none', stroke: st.axisColor, 'stroke-width': 1 }));
    else if (st.grid === 'none' || st.grid === 'x') ax.appendChild(S('line', { x1: plot.x, x2: plot.x, y1: plot.y, y2: plot.y + plot.h, stroke: st.axisColor, 'stroke-width': 1 }));
    yInfo.ticks.forEach((t, i) => {
      const y = ys(t);
      if (y < plot.y - 0.5 || y > plot.y + plot.h + 0.5) return;
      if (st.ticksOut) ax.appendChild(S('line', { x1: plot.x - 4, x2: plot.x, y1: y, y2: y, stroke: st.axisColor }));
      ax.appendChild(textEl(plot.x - 6, y + fs * 0.35, yLabels[i], { size: fs, font, color: st.textColor, anchor: 'end' }));
    });
    const xly = plot.y + plot.h + fs * 1.15 + 2;
    const xlab = (x, label) => {
      if (st.ticksOut) ax.appendChild(S('line', { x1: x, x2: x, y1: plot.y + plot.h, y2: plot.y + plot.h + 4, stroke: st.axisColor }));
      if (rot === 0) ax.appendChild(textEl(x, xly, label, { size: fs, font, color: st.textColor, anchor: 'middle' }));
      else ax.appendChild(textEl(x, plot.y + plot.h + 6, label, { size: fs, font, color: st.textColor, anchor: rot < 0 ? 'end' : 'start', transform: `rotate(${rot} ${x.toFixed(1)} ${(plot.y + plot.h + 6).toFixed(1)})`, baseline: 'middle' }));
    };
    if (xSpec.type === 'band') xSpec.labels.forEach((l, i) => xlab(xs(i), l));
    else xSpec.ticks.forEach((t, i) => { const x = xs(t); if (x >= plot.x - 0.5 && x <= plot.x + plot.w + 0.5) xlab(x, xLabels[i]); });
    if (xTitle) ax.appendChild(textEl(plot.x + plot.w / 2, plot.y + plot.h + xLabH + fs * 1.6 + 2, xTitle, { size: fs + 0.5, font, color: st.textColor, anchor: 'middle', weight: 600 }));
    if (yTitle) {
      const cx = box.x + 8 + fs * 0.9, cy = plot.y + plot.h / 2;
      ax.appendChild(textEl(cx, cy, yTitle, { size: fs + 0.5, font, color: st.textColor, anchor: 'middle', weight: 600, transform: `rotate(-90 ${cx.toFixed(1)} ${cy.toFixed(1)})` }));
    }
    root.appendChild(ax);

    return {
      plot, xs, ys, band,
      finish() {
        if (!legend) return;
        if (legend.inside) {
          const lx = legend.inside === 'inside-tl' || legend.inside === 'inside-bl' ? plot.x + 8 : plot.x + plot.w - legend.w - 8;
          const ly = legend.inside === 'inside-bl' || legend.inside === 'inside-br' ? plot.y + plot.h - legend.h - 8 : plot.y + 8;
          root.appendChild(legend.draw(lx, ly));
        } else root.appendChild(legend.draw(legend.x, legend.y));
      },
    };
  }

  function yScaleSpec(values, st, opts) {
    opts = opts || {};
    const fmtMode = st.yFormat, digits = Number(st.yDigits), dec = st.decimal;
    const userMin = App.parseNumber(String(st.yMin)), userMax = App.parseNumber(String(st.yMax));
    const finite = values.filter(Number.isFinite);
    if (st.yLog) {
      const pos = finite.filter((v) => v > 0);
      let min = Number.isFinite(userMin) && userMin > 0 ? userMin : pos.length ? Math.pow(10, Math.floor(Math.log10(Math.min(...pos)))) : 1e-4;
      let max = Number.isFinite(userMax) && userMax > min ? userMax : pos.length ? Math.pow(10, Math.ceil(Math.log10(Math.max(...pos)))) : 1;
      if (max <= min) max = min * 10;
      const ticks = [], minor = [];
      for (let e = Math.ceil(Math.log10(min) - 1e-9); e <= Math.log10(max) + 1e-9; e++) {
        ticks.push(Math.pow(10, e));
        for (let k = 2; k < 10; k++) { const v = k * Math.pow(10, e); if (v > min && v < max) minor.push(v); }
      }
      const autoLog = (v) => (v >= 1e-4 && v < 1e5 ? fmtNumber(v, 'fixed', Math.max(0, -Math.floor(Math.log10(v) + 1e-9)), dec) : fmtNumber(v, 'sci', 0, dec));
      return { log: true, min, max, ticks, minor, fmt: (v) => (fmtMode === 'auto' ? autoLog(v) : fmtNumber(v, fmtMode, digits, dec)) };
    }
    let min = Number.isFinite(userMin) ? userMin : Math.min(0, ...finite);
    let max = Number.isFinite(userMax) ? userMax : (finite.length ? Math.max(...finite) : 1);
    if (opts.forceMax !== undefined && !Number.isFinite(userMax)) max = opts.forceMax;
    if (!(max > min)) max = min + (Math.abs(min) || 1);
    const stepUser = App.parseNumber(String(st.yStep));
    const t = linearTicks(min, max, opts.tickCount || 6, stepUser);
    if (!Number.isFinite(userMax) && opts.forceMax === undefined) {
      const last = t.ticks[t.ticks.length - 1];
      if (last < max - 1e-12) { t.ticks.push(+(last + t.step).toPrecision(12)); }
      max = t.ticks[t.ticks.length - 1];
    }
    if (!Number.isFinite(userMin) && t.ticks[0] > min) min = t.ticks[0];
    return { log: false, min, max, ticks: t.ticks.filter((v) => v >= min - 1e-12 && v <= max + 1e-12), step: t.step, fmt: (v) => fmtNumber(v, fmtMode, digits, dec, t.step) };
  }

  // ------------------------------------------------------------ line chart

  function drawLine(root, model, st, box, opts) {
    opts = opts || {};
    const series = model.series.filter((s) => s.style.visible !== false);
    const pts = series.flatMap((s) => s.points.filter((p) => Number.isFinite(p.mean)));
    const showErr = st.errorBars !== 'none';
    const yvals = pts.flatMap((p) => showErr && Number.isFinite(p.ci) ? [p.mean - p.ci, p.mean + p.ci] : [p.mean]);
    const ySpec = yScaleSpec(yvals, st);

    // x axis
    let xSpec;
    const xsAll = [...new Set(pts.map((p) => p.x))].sort((a, b) => a - b);
    const catMode = st.xAsCategories && xsAll.length <= 40 && !model.xNumericOnly;
    if (catMode) {
      const labels = xsAll.map((v) => (model.xLabel ? model.xLabel(v) : fmtNumber(v, st.xFormat, Number(st.xDigits), st.decimal)));
      xSpec = { type: 'num', min: -0.5, max: xsAll.length - 0.5, ticks: xsAll.map((_, i) => i), fmt: (i) => labels[i] };
    } else {
      const uMin = App.parseNumber(String(st.xMin)), uMax = App.parseNumber(String(st.xMax));
      let mn = Number.isFinite(uMin) ? uMin : Math.min(...xsAll), mx = Number.isFinite(uMax) ? uMax : Math.max(...xsAll);
      if (!(mx > mn)) { mn -= 1; mx += 1; }
      const t = xsAll.length <= 12 && !model.scatter && !Number.isFinite(uMin) && !Number.isFinite(uMax) ? { ticks: xsAll, step: 0 } : linearTicks(mn, mx, 8);
      const pad = Number.isFinite(uMin) || Number.isFinite(uMax) ? 0 : (mx - mn) * 0.03;
      xSpec = { type: 'num', min: mn - pad, max: mx + pad, ticks: t.ticks, fmt: (v) => (model.xLabel ? model.xLabel(v) : fmtNumber(v, st.xFormat, Number(st.xDigits), st.decimal, t.step || undefined)) };
    }
    const xOf = (p) => (catMode ? xsAll.indexOf(p.x) : p.x);

    const legendItems = series.map((s) => ({ name: s.name, color: s.style.color, width: s.style.width, dash: s.style.dash, marker: s.style.marker, markerSize: s.style.markerSize, hollow: s.style.hollow, line: s.style.line !== false }));
    const f = frame(root, st, box, ySpec, xSpec, series.length > (opts.minLegend || 1) || st.legend !== 'auto' ? legendItems : null, opts);
    const clipId = 'clip' + Math.random().toString(36).slice(2, 8);
    const defs = S('defs');
    const cp = S('clipPath', { id: clipId });
    cp.appendChild(S('rect', { x: f.plot.x - 6, y: f.plot.y - 6, width: f.plot.w + 12, height: f.plot.h + 12 }));
    defs.appendChild(cp); root.appendChild(defs);
    const g = S('g', { 'clip-path': `url(#${clipId})` });

    for (const s of series) {
      const sty = s.style;
      const valid = s.points.filter((p) => Number.isFinite(p.mean) && (!ySpec.log || p.mean > 0)).sort((a, b) => xOf(a) - xOf(b));
      const sg = S('g');
      if (showErr && st.errorBars === 'band' && valid.length > 1) {
        const up = valid.map((p) => [f.xs(xOf(p)), f.ys(p.mean + (Number.isFinite(p.ci) ? p.ci : 0))]);
        const dn = valid.slice().reverse().map((p) => [f.xs(xOf(p)), f.ys(Math.max(ySpec.log ? ySpec.min : -Infinity, p.mean - (Number.isFinite(p.ci) ? p.ci : 0)))]);
        sg.appendChild(S('path', { d: 'M' + up.concat(dn).map((q) => q[0].toFixed(1) + ' ' + q[1].toFixed(1)).join(' L') + ' Z', fill: sty.color, 'fill-opacity': 0.15, stroke: 'none' }));
      }
      if (sty.line !== false && valid.length > 1) {
        sg.appendChild(S('path', {
          d: valid.map((p, i) => (i ? 'L' : 'M') + f.xs(xOf(p)).toFixed(1) + ' ' + f.ys(p.mean).toFixed(1)).join(' '),
          fill: 'none', stroke: sty.color, 'stroke-width': sty.width, 'stroke-linejoin': 'round',
          'stroke-linecap': sty.dash === 'dotted' ? 'round' : 'butt', 'stroke-dasharray': dashArray(sty.dash, sty.width),
        }));
      }
      if (showErr && st.errorBars === 'bars') {
        for (const p of valid) {
          if (!Number.isFinite(p.ci) || p.ci <= 0) continue;
          const x = f.xs(xOf(p));
          const y1 = f.ys(ySpec.log ? Math.max(p.mean - p.ci, ySpec.min) : p.mean - p.ci), y2 = f.ys(p.mean + p.ci);
          const cw = st.capWidth;
          const eg = S('g', { stroke: sty.errColor || sty.color, 'stroke-width': st.errorWidth });
          eg.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: y1.toFixed(1), y2: y2.toFixed(1) }));
          if (cw > 0) {
            eg.appendChild(S('line', { x1: (x - cw).toFixed(1), x2: (x + cw).toFixed(1), y1: y1.toFixed(1), y2: y1.toFixed(1) }));
            eg.appendChild(S('line', { x1: (x - cw).toFixed(1), x2: (x + cw).toFixed(1), y1: y2.toFixed(1), y2: y2.toFixed(1) }));
          }
          sg.appendChild(eg);
        }
      }
      for (const p of valid) {
        const m = marker(sty.marker, f.xs(xOf(p)), f.ys(p.mean), sty.markerSize, sty.color, sty.hollow, st.plotBg || st.bg);
        if (m) { m.appendChild(tip(s.name, p, st)); sg.appendChild(m); }
      }
      g.appendChild(sg);
    }
    root.appendChild(g);
    f.finish();
    return f;
  }

  // ------------------------------------------------------------ bar chart

  function drawBars(root, model, st, box, opts) {
    opts = opts || {};
    const series = model.series.filter((s) => s.style.visible !== false);
    const cats = model.categories;
    const showErr = st.errorBars !== 'none' && !model.stacked;
    const val = (s, c) => { const v = s.values[c.key]; return v && Number.isFinite(v.mean) ? v : null; };
    let yvals;
    let totals = null;
    if (model.stacked) {
      totals = cats.map((c) => series.reduce((a, s) => a + (val(s, c) ? Math.max(0, val(s, c).mean) : 0), 0));
      yvals = model.normalize ? [1] : totals;
    } else {
      yvals = series.flatMap((s) => cats.map((c) => val(s, c)).filter(Boolean).map((v) => (showErr && Number.isFinite(v.ci) ? v.mean + v.ci : v.mean)));
    }
    const stLocal = model.normalize && st.yFormat === 'auto' ? Object.assign({}, st, { yFormat: 'percent', yDigits: 0 }) : st;
    const ySpec = yScaleSpec(yvals, stLocal, model.normalize ? { forceMax: 1 } : {});
    const xSpec = { type: 'band', labels: cats.map((c) => c.label) };
    const legendItems = series.map((s) => ({ name: s.name, color: s.style.color, kind: 'bar' }));
    const f = frame(root, stLocal, box, ySpec, xSpec, (series.length > 1 || model.forceLegend) ? legendItems : null, opts);
    const bw = f.band.bw;
    const inner = bw * (1 - st.barGap);
    const g = S('g');
    const y0 = f.ys(ySpec.log ? ySpec.min : Math.max(ySpec.min, 0));
    const fs = st.fontSize;
    cats.forEach((c, ci) => {
      const cx = f.xs(ci);
      if (model.stacked) {
        let acc = 0;
        const tot = totals[ci] || 1;
        for (const s of series) {
          const v = val(s, c);
          if (!v || v.mean <= 0) continue;
          const a = model.normalize ? acc / tot : acc, b = model.normalize ? (acc + v.mean) / tot : acc + v.mean;
          acc += v.mean;
          const ya = f.ys(a), yb = f.ys(b);
          const seg = S('rect', { x: (cx - inner / 2).toFixed(1), y: yb.toFixed(1), width: inner.toFixed(1), height: Math.max(0, ya - yb).toFixed(2), fill: s.style.color, stroke: st.barOutline ? '#000' : null, 'stroke-width': st.barOutline ? 0.6 : null });
          seg.appendChild(tip(c.label + ' · ' + s.name, v, st, model.normalize ? v.mean / tot : null));
          g.appendChild(seg);
          if (st.barValues && ya - yb > fs * 1.1) {
            g.appendChild(textEl(cx, (ya + yb) / 2 + fs * 0.33, fmtNumber(model.normalize ? (v.mean / tot) : v.mean, model.normalize ? 'percent' : st.yFormat === 'auto' ? 'auto' : st.yFormat, model.normalize ? 0 : Number(st.barValueDigits), st.decimal), { size: fs * 0.85, font: st.font, color: st.textColor, anchor: 'middle' }));
          }
        }
        if (st.barValues && !model.normalize) {
          g.appendChild(textEl(cx, f.ys(totals[ci]) - 4, fmtNumber(totals[ci], st.yFormat === 'auto' ? 'auto' : st.yFormat, Number(st.barValueDigits), st.decimal), { size: fs * 0.85, font: st.font, color: st.textColor, anchor: 'middle' }));
        }
      } else {
        const n = series.length;
        const gap = inner * st.seriesGap;
        const w = n ? (inner - gap * (n - 1)) / n : inner;
        series.forEach((s, si) => {
          const v = val(s, c);
          if (!v) return;
          const x = cx - inner / 2 + si * (w + gap);
          const y = f.ys(ySpec.log ? Math.max(v.mean, ySpec.min) : v.mean);
          const rect = S('rect', { x: x.toFixed(2), y: Math.min(y, y0).toFixed(2), width: Math.max(0.5, w).toFixed(2), height: Math.abs(y0 - y).toFixed(2), fill: s.style.color, stroke: st.barOutline ? '#000' : null, 'stroke-width': st.barOutline ? 0.6 : null });
          rect.appendChild(tip(c.label + ' · ' + s.name, v, st));
          g.appendChild(rect);
          if (showErr && Number.isFinite(v.ci) && v.ci > 0) {
            const xm = x + w / 2, y1 = f.ys(Math.max(v.mean - v.ci, ySpec.log ? ySpec.min : -Infinity)), y2 = f.ys(v.mean + v.ci);
            const cw = Math.min(st.capWidth, w / 2);
            const eg = S('g', { stroke: '#000000', 'stroke-width': st.errorWidth });
            eg.appendChild(S('line', { x1: xm, x2: xm, y1, y2 }));
            eg.appendChild(S('line', { x1: xm - cw, x2: xm + cw, y1, y2: y1 }));
            eg.appendChild(S('line', { x1: xm - cw, x2: xm + cw, y1: y2, y2 }));
            g.appendChild(eg);
          }
          if (st.barValues) {
            const lbl = fmtNumber(v.mean, st.yFormat === 'auto' ? 'auto' : st.yFormat, Number(st.barValueDigits), st.decimal);
            if (w >= fs * 0.9) {
              const tx = x + w / 2, ty = Math.min(y, y0) - 4;
              g.appendChild(textEl(tx, ty, lbl, { size: fs * 0.8, font: st.font, color: st.textColor, anchor: w < textWidth(lbl, fs * 0.8, st.font) ? 'start' : 'middle', transform: w < textWidth(lbl, fs * 0.8, st.font) ? `rotate(-90 ${tx.toFixed(1)} ${ty.toFixed(1)})` : null }));
            }
          }
        });
      }
    });
    root.appendChild(g);
    f.finish();
    return f;
  }

  // ------------------------------------------------------------ fiber cores diagram

  function drawCores(root, model, st, box) {
    const fs = st.coreLabelSize > 0 ? st.coreLabelSize : Math.max(8, st.fontSize - 2);
    let top = box.y + 6;
    if (st.panelLabel) root.appendChild(textEl(box.x + 6, box.y + st.panelLabelSize + 2, st.panelLabel, { size: st.panelLabelSize, font: st.font, color: st.titleColor, weight: 700 }));
    if (st.showTitle && st.title) {
      root.appendChild(textEl(box.x + box.w / 2, top + st.titleSize * 0.9, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
      top += st.titleSize * 1.5;
    }
    const show = st.coreShow;
    if (st.coreLegend && show !== 'none') {
      const items = [];
      if (show === 'both' || show === 'block') items.push({ name: model.blockLabel || 'Bloqueio do núcleo', color: st.coreBlockColor, kind: 'box' });
      if (show === 'both' || show === 'util') items.push({ name: model.utilLabel || 'Uso do núcleo', color: st.coreUtilColor, kind: 'box' });
      const lg = buildLegend(items, Object.assign({}, st, { legendBox: false }), box.w, 1);
      root.appendChild(lg.draw(box.x + (box.w - lg.w) / 2, top));
      top += lg.h + 4;
    }
    const diagrams = model.diagrams;
    const cols = Math.max(1, Math.min(Number(st.coreCols) || 2, diagrams.length || 1));
    const rows = Math.ceil(diagrams.length / cols);
    const cellW = box.w / cols, cellH = (box.y + box.h - top) / Math.max(1, rows);
    const nBoxes = show === 'both' ? 2 : show === 'none' ? 0 : 1;
    const boxW = nBoxes ? textWidth(fmtNumber(0.123456789, 'fixed', Number(st.coreDigits), st.decimal), fs, st.font) + 8 : -20;
    const boxH = nBoxes ? fs * 1.35 : 0;

    // heat scale
    let heatMax = 0;
    if (st.coreHeat !== 'none') for (const d of diagrams) for (const c of d.cores) { const v = st.coreHeat === 'util' ? c.util : c.block; if (Number.isFinite(v)) heatMax = Math.max(heatMax, v); }
    const mix = (hex, t) => {
      const h = hex.replace('#', '');
      const r = parseInt(h.slice(0, 2), 16), g = parseInt(h.slice(2, 4), 16), b = parseInt(h.slice(4, 6), 16);
      const m = (a) => Math.round(255 + (a - 255) * t);
      return `rgb(${m(r)},${m(g)},${m(b)})`;
    };

    diagrams.forEach((d, i) => {
      const cx0 = box.x + (i % cols) * cellW + cellW / 2;
      const cy0 = top + Math.floor(i / cols) * cellH;
      const capH = fs * 1.6;
      const avail = Math.min(cellW - 2 * (boxW + 26), cellH - capH - 2 * (nBoxes * boxH + 18));
      const R = Math.max(30, avail / 2);
      const cy = cy0 + (cellH - capH) / 2;
      const g = S('g');
      g.appendChild(S('circle', { cx: cx0, cy, r: R, fill: st.coreFiber, stroke: st.coreFiberStroke, 'stroke-width': 1.2 }));
      const ext = Math.max(...d.cores.map((c) => Math.hypot(c.x, c.y)), 0.01);
      let dmin = Infinity;
      for (let a = 0; a < d.cores.length; a++) for (let b = a + 1; b < d.cores.length; b++) dmin = Math.min(dmin, Math.hypot(d.cores[a].x - d.cores[b].x, d.cores[a].y - d.cores[b].y));
      if (!Number.isFinite(dmin) || dmin <= 0) dmin = 1;
      const scale = d.cores.length < 2 ? 0 : R / (ext + 0.42 * dmin);
      const coreR = d.cores.length < 2 ? R * 0.3 : Math.max(5, 0.25 * dmin * scale);
      for (const c of d.cores) {
        const x = cx0 + c.x * scale, y = cy + c.y * scale;
        let fill = st.coreFill;
        if (st.coreHeat !== 'none' && heatMax > 0 && !c.unused) {
          const v = st.coreHeat === 'util' ? c.util : c.block;
          if (Number.isFinite(v)) fill = mix(st.coreHeatColor, Math.min(1, v / heatMax));
        }
        if (c.highlight) fill = c.highlight;
        g.appendChild(S('circle', { cx: x, cy: y, r: coreR, fill: c.unused ? 'none' : fill, stroke: c.unused ? '#9a9a9a' : st.coreStroke, 'stroke-width': 1.3, 'stroke-dasharray': c.unused ? '3 2' : null }));
        g.appendChild(textEl(x, y + Math.min(coreR * 1.05, Math.max(fs * 1.7, coreR * 0.6)) * 0.36, String(c.id), { size: Math.min(coreR * 1.05, Math.max(fs * 1.7, coreR * 0.6)), font: st.font, color: c.unused ? '#9a9a9a' : '#000000', anchor: 'middle', weight: 700 }));
        if (c.unused || !nBoxes) continue;
        // label position: radially outside the fiber; the centre core goes to the right
        let ang = Math.atan2(c.y, c.x);
        if (Math.hypot(c.x, c.y) < 1e-6) ang = 0;
        const lr = R + 10;
        let lx = cx0 + Math.cos(ang) * lr, ly = cy + Math.sin(ang) * lr;
        const right = Math.cos(ang) > 0.25, left = Math.cos(ang) < -0.25;
        const bx = right ? lx + 4 : left ? lx - boxW - 4 : lx - boxW / 2;
        const totalH = nBoxes * boxH;
        const by = Math.sin(ang) < -0.25 ? ly - totalH - 2 : Math.sin(ang) > 0.25 ? ly + 2 : ly - totalH / 2;
        // leader line from core edge to box
        const ex = x + Math.cos(ang) * coreR, ey = y + Math.sin(ang) * coreR;
        const tx = right ? bx : left ? bx + boxW : bx + boxW / 2;
        const ty = Math.sin(ang) < -0.25 ? by + totalH : Math.sin(ang) > 0.25 ? by : by + totalH / 2;
        g.appendChild(S('polyline', { points: `${ex.toFixed(1)},${ey.toFixed(1)} ${lx.toFixed(1)},${ly.toFixed(1)} ${tx.toFixed(1)},${ty.toFixed(1)}`, fill: 'none', stroke: '#000000', 'stroke-width': 0.7 }));
        const vals = [];
        if (show === 'both' || show === 'block') vals.push([c.block, st.coreBlockColor]);
        if (show === 'both' || show === 'util') vals.push([c.util, st.coreUtilColor]);
        vals.forEach(([v, col], k) => {
          const yy = by + k * boxH;
          g.appendChild(S('rect', { x: bx.toFixed(1), y: yy.toFixed(1), width: boxW.toFixed(1), height: boxH.toFixed(1), fill: col, stroke: '#ffffff', 'stroke-width': 0.6 }));
          g.appendChild(textEl(bx + boxW / 2, yy + boxH * 0.72, Number.isFinite(v) ? fmtNumber(v, 'fixed', Number(st.coreDigits), st.decimal) : '—', { size: fs, font: st.font, color: '#000000', anchor: 'middle' }));
        });
      }
      g.appendChild(textEl(cx0, cy0 + cellH - fs * 0.5, d.title, { size: Math.max(fs, st.fontSize - 1), font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(g);
    });
  }

  // ------------------------------------------------------------ topology (link heat map / network preview)

  let gradSeq = 0;

  /** Value of an undirected link according to the direction mode ('max' | 'mean' | 'ab' | 'ba'). */
  function linkValue(l, mode) {
    const v = [l.ab, l.ba].filter(Number.isFinite);
    if (!v.length) return NaN;
    if (mode === 'mean') return v.reduce((a, b) => a + b, 0) / v.length;
    return Math.max(...v);
  }

  function topoLayout(model, st, W) {
    const n = Math.max(1, model.panels.length);
    const cols = Math.max(1, Math.min(Number(st.topoCols) || 3, n));
    const rows = Math.ceil(n / cols);
    const legendW = model.range && st.topoLegend === 'right' ? 92 : 0;
    const legendH = model.range && st.topoLegend === 'bottom' ? 58 : 0;
    const titleH = st.showTitle && st.title ? st.titleSize * 1.6 : 4;
    return { n, cols, rows, legendW, legendH, titleH, cellW: (W - legendW) / cols };
  }

  /** Natural height of the drawing for a given width (keeps the aspect of the topology). */
  function topoHeight(model, st, W) {
    const L = topoLayout(model, st, W);
    const p = model.panels[0];
    let asp = 0.7;
    if (p && p.nodes.length > 1) {
      const xs = p.nodes.map((q) => q.x), ys = p.nodes.map((q) => q.y);
      const w = Math.max(1, Math.max(...xs) - Math.min(...xs)), h = Math.max(1, Math.max(...ys) - Math.min(...ys));
      asp = Math.min(1.6, Math.max(0.35, h / w));
    }
    const fs = Math.max(9, st.fontSize);
    const pad = (Number(st.topoNodeR) || 11) * 2 + 8;
    const cellH = (L.cellW - pad) * asp + pad + fs * 2;
    return Math.round(L.titleH + L.rows * cellH + L.legendH + 6);
  }

  function heatLegend(root, model, st, x, y, w, h, vertical) {
    const id = 'heat' + (++gradSeq);
    const defs = S('defs');
    const grad = S('linearGradient', vertical ? { id, x1: 0, y1: 1, x2: 0, y2: 0 } : { id, x1: 0, y1: 0, x2: 1, y2: 0 });
    for (const [t] of App.presets.HEAT_STOPS) grad.appendChild(S('stop', { offset: (t * 100).toFixed(1) + '%', 'stop-color': App.presets.heatColor(t) }));
    defs.appendChild(grad);
    root.appendChild(defs);
    const fs = Math.max(8, st.fontSize - 1);
    const { min, max } = model.range;
    const fmt = (v) => fmtNumber(v, st.topoFormat === 'percent' ? 'percent' : 'fixed', Number(st.topoDigits), st.decimal);
    const ticks = 5;
    if (vertical) {
      const lines = [];
      for (const word of String(model.legendTitle || '').split(/\s+/).filter(Boolean)) {
        const last = lines[lines.length - 1];
        if (last && textWidth(last + ' ' + word, fs, st.font, 600) <= w) lines[lines.length - 1] = last + ' ' + word;
        else lines.push(word);
      }
      lines.forEach((ln, k) => root.appendChild(textEl(x, y + fs * (1 + 1.2 * k), ln, { size: fs, font: st.font, color: st.textColor, weight: 600 })));
      const bw = 16, top = y + fs * (1.2 * lines.length + 1), bh = Math.max(40, h - fs * (1.2 * lines.length + 2));
      root.appendChild(S('rect', { x, y: top, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const v = min + (max - min) * i / ticks;
        const yy = top + bh - bh * i / ticks;
        root.appendChild(S('line', { x1: x + bw, x2: x + bw + 4, y1: yy, y2: yy, stroke: st.axisColor }));
        root.appendChild(textEl(x + bw + 6, yy + fs * 0.35, fmt(v), { size: fs, font: st.font, color: st.textColor }));
      }
    } else {
      const bh = 12, bw = Math.min(w, 420), bx = x + (w - bw) / 2;
      root.appendChild(textEl(x + w / 2, y + fs, model.legendTitle || '', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(S('rect', { x: bx, y: y + fs * 1.5, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const v = min + (max - min) * i / ticks;
        const xx = bx + bw * i / ticks;
        root.appendChild(S('line', { x1: xx, x2: xx, y1: y + fs * 1.5 + bh, y2: y + fs * 1.5 + bh + 4, stroke: st.axisColor }));
        root.appendChild(textEl(xx, y + fs * 1.5 + bh + 5 + fs, fmt(v), { size: fs, font: st.font, color: st.textColor, anchor: 'middle' }));
      }
    }
  }

  function drawTopo(root, model, st, box) {
    const L = topoLayout(model, st, box.w);
    if (st.showTitle && st.title) root.appendChild(textEl(box.x + (box.w - L.legendW) / 2, box.y + st.titleSize * 1.1, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
    const fs = Math.max(8, st.fontSize);
    const nodeR = Number(st.topoNodeR) || 11;
    const lw = Number(st.topoLinkWidth) || 4;
    const dir = st.topoDir || 'both';
    const range = model.range;
    const cellH = (box.h - L.titleH - L.legendH) / L.rows;
    const color = (v) => {
      if (!range) return st.topoLinkColor || '#5b6573';
      if (!Number.isFinite(v)) return st.topoNoData || '#bdbdbd';
      return App.presets.heatColor(range.max > range.min ? (v - range.min) / (range.max - range.min) : 0);
    };
    const fmtV = (v) => fmtNumber(v, st.topoFormat === 'percent' ? 'percent' : 'fixed', Number(st.topoDigits), st.decimal);
    const fmtP = (o) => (o && Number.isFinite(o.mean) ? fmtNumber(o.mean, 'percent', Number(st.topoDigits), st.decimal) : '—');
    const blkText = (o, long) => {
      if (!o) return '—';
      const has = (x) => x && Number.isFinite(x.mean);
      if (model.block && model.block.qkd && (has(o.qkd) || has(o.classical))) {
        const parts = [];
        if (has(o.classical)) parts.push((long ? 'clássicas ' : 'C ') + fmtP(o.classical));
        if (has(o.qkd)) parts.push((long ? 'protegidas QKD ' : 'Q ') + fmtP(o.qkd));
        if (long) parts.push('todas ' + fmtP(o.all));
        return parts.join(' · ');
      }
      return (long ? 'todas ' : 'B ') + fmtP(o.all);
    };
    model.panels.forEach((p, i) => {
      const cx = box.x + (i % L.cols) * L.cellW, cy = box.y + L.titleH + Math.floor(i / L.cols) * cellH;
      const capH = p.title ? fs * 1.8 : 0;
      const pad = nodeR + 6;
      const xs = p.nodes.map((q) => q.x), ys = p.nodes.map((q) => q.y);
      const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
      const aw = L.cellW - 2 * pad, ah = cellH - capH - 2 * pad;
      const sc = Math.min(aw / Math.max(1, x1 - x0), ah / Math.max(1, y1 - y0));
      const ox = cx + pad + (aw - (x1 - x0) * sc) / 2, oy = cy + pad + (ah - (y1 - y0) * sc) / 2;
      const P = new Map(p.nodes.map((q) => [String(q.id), { x: ox + (q.x - x0) * sc, y: oy + (q.y - y0) * sc }]));
      const g = S('g');
      if (st.topoPanelBorder) g.appendChild(S('rect', { x: cx + 2, y: cy + 2, width: L.cellW - 4, height: cellH - 4, fill: 'none', stroke: st.borderColor }));
      for (const l of p.links) {
        const A = P.get(String(l.a)), B = P.get(String(l.b));
        if (!A || !B) continue;
        const dx = B.x - A.x, dy = B.y - A.y, d = Math.max(1e-6, Math.hypot(dx, dy));
        const nx = -dy / d, ny = dx / d;
        const lines = [];
        if (range && dir === 'both') {
          const off = lw / 2 + 0.4;
          lines.push({ x1: A.x + nx * off, y1: A.y + ny * off, x2: B.x + nx * off, y2: B.y + ny * off, v: l.ab, t: l.a + '→' + l.b, blk: l.blkAB });
          lines.push({ x1: A.x - nx * off, y1: A.y - ny * off, x2: B.x - nx * off, y2: B.y - ny * off, v: l.ba, t: l.b + '→' + l.a, blk: l.blkBA });
        } else {
          const v = range ? linkValue(l, dir) : NaN;
          lines.push({ x1: A.x, y1: A.y, x2: B.x, y2: B.y, v, t: l.a + '–' + l.b });
        }
        for (const s of lines) {
          const ln = S('line', { x1: s.x1.toFixed(1), y1: s.y1.toFixed(1), x2: s.x2.toFixed(1), y2: s.y2.toFixed(1), stroke: color(s.v), 'stroke-width': range ? lw : Math.max(1.5, lw * 0.6), 'stroke-linecap': 'butt' });
          let tt = s.t + (Number.isFinite(l.km) ? ' · ' + fmtNumber(l.km, 'auto', 0, st.decimal) + ' km' : '') + (range ? ': ' + (Number.isFinite(s.v) ? fmtV(s.v) : 'sem dado') : '');
          if (model.block) {
            const parts = dir === 'both' ? [[s.t, s.blk]] : [[l.a + '→' + l.b, l.blkAB], [l.b + '→' + l.a, l.blkBA]];
            for (const [nm, o] of parts) if (o) tt += '\nBloqueio ' + nm + ': ' + blkText(o, true);
          }
          ln.appendChild(S('title', null, tt));
          g.appendChild(ln);
        }
        if (model.block && (st.topoLabels === 'block' || st.topoLabels === 'valueBlock')) {
          const fsl = Math.max(8, fs - 2);
          const txt = (v, o) => (st.topoLabels === 'valueBlock' ? 'U ' + (Number.isFinite(v) ? fmtV(v) : '—') + ' · ' : '') + (o ? blkText(o, false) : '—');
          const put = (sgn, text) => {
            // a→b label 40 % along the link on its own side, b→a label at 60 % on the other side
            const tpos = dir === 'both' ? (sgn > 0 ? 0.4 : 0.6) : 0.5;
            const mxc = A.x + (B.x - A.x) * tpos, myc = A.y + (B.y - A.y) * tpos;
            const dd = lw + fsl * 0.7 + Math.abs(nx) * text.length * fsl * 0.29;
            g.appendChild(textEl(mxc + sgn * nx * dd, myc + sgn * ny * dd + fsl * 0.35, text, { size: fsl, font: st.font, color: st.topoLabelColor || '#c00000', anchor: 'middle', weight: 600 }));
          };
          if (dir === 'both') { put(1, txt(l.ab, l.blkAB)); put(-1, txt(l.ba, l.blkBA)); }
          else put(1, txt(linkValue(l, dir), l.blkAB) + '  |  ' + (l.blkBA ? blkText(l.blkBA, false) : '—'));
          continue;
        }
        const lab = st.topoLabels === 'km' ? (Number.isFinite(l.km) ? fmtNumber(l.km, 'auto', 0, st.decimal) : '')
          : st.topoLabels === 'value' && range ? (dir === 'both' ? [l.ab, l.ba].map((v) => (Number.isFinite(v) ? fmtV(v) : '—')).join(' / ') : fmtV(linkValue(l, dir))) : '';
        if (lab) {
          const mx = (A.x + B.x) / 2 + nx * (lw + fs * 0.5), my = (A.y + B.y) / 2 + ny * (lw + fs * 0.5) + fs * 0.35;
          g.appendChild(textEl(mx, my, lab, { size: Math.max(7, fs - 2), font: st.font, color: st.topoLabelColor || '#c00000', anchor: 'middle', weight: 600 }));
        }
      }
      // Parte 27: trusted nodes every QTND km (from the source of each direction, as the QKD planner does)
      if (p.qtnd > 0 && st.topoTrusted !== false) {
        const tr = Number(st.topoTrustedR) > 0 ? Number(st.topoTrustedR) : Math.max(2.5, nodeR * 0.36);
        const tg = S('g');
        for (const l of p.links) {
          const A = P.get(String(l.a)), B = P.get(String(l.b));
          if (!A || !B || !Number.isFinite(l.km) || l.km <= 0) continue;
          const n = Math.max(1, Math.ceil(l.km / p.qtnd - 1e-12));
          if (n < 2) continue;
          const dx = B.x - A.x, dy = B.y - A.y, d = Math.max(1e-6, Math.hypot(dx, dy));
          const nx = -dy / d, ny = dx / d;
          const both = range && dir === 'both';
          const off = both ? lw / 2 + 0.4 : 0;
          const dirs = both ? [[A, B, 1, l.a + '→' + l.b], [B, A, -1, l.b + '→' + l.a]] : [[A, B, 0, l.a + '–' + l.b]];
          for (const [P0, P1, sg, name] of dirs) {
            for (let k = 1; k < n; k++) {
              const f = Math.min(0.999, (k * p.qtnd) / l.km);
              const x = P0.x + (P1.x - P0.x) * f + nx * off * sg, y = P0.y + (P1.y - P0.y) * f + ny * off * sg;
              const c = S('circle', { cx: x.toFixed(1), cy: y.toFixed(1), r: tr.toFixed(1), fill: st.topoTrustedFill || '#7030a0', stroke: '#ffffff', 'stroke-width': 0.8 });
              c.appendChild(S('title', null, 'Nó confiável ' + k + ' de ' + (n - 1) + ' em ' + name + ' · ' + fmtNumber(k * p.qtnd, 'auto', 1, st.decimal) + ' km da origem (QTND ' + fmtNumber(p.qtnd, 'auto', 1, st.decimal) + ' km)'));
              tg.appendChild(c);
            }
          }
        }
        g.appendChild(tg);
      }
      for (const q of p.nodes) {
        const c = P.get(String(q.id));
        const circ = S('circle', { cx: c.x.toFixed(1), cy: c.y.toFixed(1), r: nodeR, fill: q.fill || st.topoNodeFill || '#ffffff', stroke: st.topoNodeStroke || '#000000', 'stroke-width': 1.3 });
        circ.appendChild(S('title', null, 'Nó ' + q.id));
        g.appendChild(circ);
        g.appendChild(textEl(c.x, c.y + nodeR * 0.36, String(q.id), { size: Math.max(7, nodeR * (String(q.id).length > 2 ? 0.8 : 1.0)), font: st.font, color: '#000000', anchor: 'middle', weight: 700 }));
      }
      if (p.title) g.appendChild(textEl(cx + L.cellW / 2, cy + cellH - fs * 0.6, p.title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(g);
    });
    if (range && st.topoLegend === 'right') heatLegend(root, model, st, box.x + box.w - L.legendW + 8, box.y + L.titleH, L.legendW - 8, Math.min(320, box.h - L.titleH - 10), true);
    if (range && st.topoLegend === 'bottom') heatLegend(root, model, st, box.x, box.y + box.h - L.legendH, box.w, L.legendH, false);
  }

  // ------------------------------------------------------------ spectrum heat map (slots × cores × links)

  const SLOT_SCALES = [
    { id: 'traffic', name: 'Verde → amarelo → laranja → vermelho', stops: [[0, '#00B050'], [1 / 3, '#FFFF00'], [2 / 3, '#FFC000'], [1, '#FF0000']] },
    { id: 'bins5', name: '5 faixas: verde, amarelo, laranja, marrom, vermelho', bins: ['#00B050', '#FFFF00', '#FFC000', '#C55A11', '#FF0000'] },
    { id: 'heat', name: 'Igual ao mapa da topologia', stops: [[0, '#00A651'], [1 / 3, '#FFDD00'], [2 / 3, '#FF8C00'], [1, '#D7191C']] },
    { id: 'viridis', name: 'Viridis (daltonismo e impressão)', stops: [[0, '#440154'], [0.25, '#3B528B'], [0.5, '#21918C'], [0.75, '#5EC962'], [1, '#FDE725']] },
    { id: 'blues', name: 'Branco → azul', stops: [[0, '#F7FBFF'], [0.5, '#6BAED6'], [1, '#08306B']] },
    { id: 'gray', name: 'Branco → preto', stops: [[0, '#FFFFFF'], [1, '#000000']] },
  ];

  function hexRgb(h) { const m = /^#?([0-9a-f]{6})$/i.exec(h); const n = m ? parseInt(m[1], 16) : 0; return [n >> 16 & 255, n >> 8 & 255, n & 255]; }

  /** Returns color(v) for the scale and range (continuous scales are quantized in 1 % steps). */
  function slotColorFn(st, range) {
    const sc = SLOT_SCALES.find((x) => x.id === st.slotScale) || SLOT_SCALES[0];
    const span = range.max - range.min;
    const cache = new Map();
    const zero = st.slotZero === 'white' ? '#FFFFFF' : st.slotZero === 'gray' ? '#E7E6E6' : null;
    const stops = sc.stops ? sc.stops.map(([t, c]) => [t, hexRgb(c)]) : null;
    return (v) => {
      if (!Number.isFinite(v)) return '#BDBDBD';
      if (zero && v <= 0) return zero;
      let t = span > 0 ? (v - range.min) / span : 0;
      t = Math.max(0, Math.min(1, t));
      if (sc.bins) return sc.bins[Math.min(sc.bins.length - 1, Math.floor(t * sc.bins.length))];
      const q = Math.round(t * 100);
      if (cache.has(q)) return cache.get(q);
      const tq = q / 100;
      let col = stops[stops.length - 1][1];
      for (let i = 1; i < stops.length; i++) {
        if (tq <= stops[i][0]) {
          const [t0, c0] = stops[i - 1], [t1, c1] = stops[i];
          const f = t1 > t0 ? (tq - t0) / (t1 - t0) : 0;
          col = c0.map((x, k) => Math.round(x + (c1[k] - x) * f));
          break;
        }
      }
      const out = '#' + col.map((x) => x.toString(16).padStart(2, '0')).join('');
      cache.set(q, out);
      return out;
    };
  }

  function slotGeometry(model, st, W) {
    const fs = Math.max(7, Number(st.fontSize) || 12);
    const rowH = Math.max(2, Number(st.slotRowH) || 12);
    const legendW = st.slotLegend === 'right' ? 110 : 0;
    const legendH = st.slotLegend === 'bottom' ? fs * 4 + 12 : 0;
    const titleH = st.showTitle && st.title ? st.titleSize * 1.7 : 4;
    const ptH = st.slotPanelTitle !== false ? fs * 1.9 : 0;
    const headH = fs * 2.4;
    const labels = [];
    for (const p of model.panels) for (const r of p.rows) labels.push(r.link);
    const linkColW = Math.max(textWidth('Enlace', fs, st.font, 700), ...labels.map((t) => textWidth(t, fs, st.font, 700))) + 18;
    const coreColW = textWidth('Núcleo', fs, st.font, 700) + 16;
    const panelH = model.panels.map((p) => ptH + headH + p.rows.length * rowH + 14);
    const H = Math.round(titleH + panelH.reduce((a, b) => a + b, 0) + legendH + 6);
    return { fs, rowH, legendW, legendH, titleH, ptH, headH, linkColW, coreColW, panelH, H };
  }

  function slotLegend(root, model, st, color, x, y, w, h, vertical) {
    const sc = SLOT_SCALES.find((q) => q.id === st.slotScale) || SLOT_SCALES[0];
    const fs = Math.max(8, (Number(st.fontSize) || 12) - 1);
    const { min, max } = model.range;
    const fmt = (v) => fmtNumber(v, st.slotFormat === 'fixed' ? 'fixed' : 'percent', Number(st.slotDigits) || 0, st.decimal);
    const title = model.legendTitle || '';
    if (sc.bins) {
      const n = sc.bins.length;
      const items = sc.bins.map((c, i) => [c, fmt(min + (max - min) * i / n) + ' – ' + fmt(min + (max - min) * (i + 1) / n)]).reverse();
      if (vertical) {
        root.appendChild(textEl(x, y + fs, title, { size: fs, font: st.font, color: st.textColor, weight: 600 }));
        items.forEach(([c, t], i) => {
          const yy = y + fs * 1.8 + i * (fs * 1.6);
          root.appendChild(S('rect', { x, y: yy, width: 14, height: fs * 1.1, fill: c, stroke: st.axisColor, 'stroke-width': 0.6 }));
          root.appendChild(textEl(x + 19, yy + fs * 0.9, t, { size: fs, font: st.font, color: st.textColor }));
        });
      } else {
        root.appendChild(textEl(x + w / 2, y + fs, title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
        const iw = Math.min(170, w / n);
        const x0 = x + (w - iw * n) / 2;
        items.slice().reverse().forEach(([c, t], i) => {
          root.appendChild(S('rect', { x: x0 + i * iw, y: y + fs * 1.6, width: 14, height: fs * 1.1, fill: c, stroke: st.axisColor, 'stroke-width': 0.6 }));
          root.appendChild(textEl(x0 + i * iw + 19, y + fs * 2.5, t, { size: fs, font: st.font, color: st.textColor }));
        });
      }
      return;
    }
    const id = 'slotheat' + (++gradSeq);
    const defs = S('defs');
    const grad = S('linearGradient', vertical ? { id, x1: 0, y1: 1, x2: 0, y2: 0 } : { id, x1: 0, y1: 0, x2: 1, y2: 0 });
    for (let i = 0; i <= 20; i++) grad.appendChild(S('stop', { offset: (i * 5) + '%', 'stop-color': color(min + (max - min) * i / 20) }));
    defs.appendChild(grad);
    root.appendChild(defs);
    const ticks = 5;
    if (vertical) {
      root.appendChild(textEl(x, y + fs, title, { size: fs, font: st.font, color: st.textColor, weight: 600 }));
      const bw = 16, top = y + fs * 1.8, bh = Math.max(60, Math.min(280, h - fs * 2.5));
      root.appendChild(S('rect', { x, y: top, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const yy = top + bh - bh * i / ticks;
        root.appendChild(S('line', { x1: x + bw, x2: x + bw + 4, y1: yy, y2: yy, stroke: st.axisColor }));
        root.appendChild(textEl(x + bw + 6, yy + fs * 0.35, fmt(min + (max - min) * i / ticks), { size: fs, font: st.font, color: st.textColor }));
      }
    } else {
      const bh = 12, bw = Math.min(w, 420), bx = x + (w - bw) / 2;
      root.appendChild(textEl(x + w / 2, y + fs, title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(S('rect', { x: bx, y: y + fs * 1.5, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const xx = bx + bw * i / ticks;
        root.appendChild(S('line', { x1: xx, x2: xx, y1: y + fs * 1.5 + bh, y2: y + fs * 1.5 + bh + 4, stroke: st.axisColor }));
        root.appendChild(textEl(xx, y + fs * 1.5 + bh + 5 + fs, fmt(min + (max - min) * i / ticks), { size: fs, font: st.font, color: st.textColor, anchor: 'middle' }));
      }
    }
  }

  function drawSlots(root, model, st, box) {
    const G = slotGeometry(model, st, box.w);
    const { fs, rowH } = G;
    const color = slotColorFn(st, model.range);
    const fmt = (v) => fmtNumber(v, st.slotFormat === 'fixed' ? 'fixed' : 'percent', Number(st.slotDigits) || 0, st.decimal);
    const gridC = st.slotGridColor || '#000000';
    if (st.showTitle && st.title) root.appendChild(textEl(box.x + (box.w - G.legendW) / 2, box.y + st.titleSize * 1.15, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
    let y = box.y + G.titleH;
    const x0 = box.x + 6;
    const gx = x0 + G.linkColW + G.coreColW;
    const gw = Math.max(40, box.w - 6 - G.linkColW - G.coreColW - G.legendW - 10);
    model.panels.forEach((p, pi) => {
      const n = p.to - p.from + 1;
      const cw = gw / n;
      const g = S('g');
      if (G.ptH) g.appendChild(textEl(x0, y + fs * 1.25, p.title, { size: fs, font: st.font, color: st.titleColor, weight: 700 }));
      let yy = y + G.ptH;
      // header: column titles and slot numbers
      const hb = yy + G.headH;
      g.appendChild(textEl(x0 + G.linkColW / 2, hb - fs * 0.55, 'Enlace', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
      g.appendChild(textEl(x0 + G.linkColW + G.coreColW / 2, hb - fs * 0.55, 'Núcleo', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
      let step = Number(st.slotTickStep) || 0;
      if (!(step > 0)) {
        const need = (textWidth(String(p.to), fs - 1, st.font) + 8) / cw;
        step = [1, 2, 5, 10, 16, 20, 25, 32, 40, 50, 64, 80, 100, 160, 200].find((k) => k >= need) || Math.ceil(need);
      }
      const tfs = Math.max(7, fs - 1);
      for (let k = 0; k < n; k++) {
        const sn = p.from + k;
        if (sn !== p.from && sn % step !== 0) continue;
        const cx = gx + (k + 0.5) * cw;
        g.appendChild(textEl(cx, hb - fs * 0.55, String(sn), { size: tfs, font: st.font, color: st.textColor, anchor: 'middle' }));
        g.appendChild(S('line', { x1: cx.toFixed(2), x2: cx.toFixed(2), y1: hb - 3, y2: hb, stroke: st.axisColor, 'stroke-width': 0.8 }));
      }
      yy = hb;
      const cells = S('g', { 'shape-rendering': 'crispEdges' });
      const lines = S('g');
      const rows = p.rows;
      const top = yy;
      let groupStart = 0;
      for (let ri = 0; ri < rows.length; ri++) {
        const r = rows[ri];
        const ry = top + ri * rowH;
        // runs of equal colour
        let k = 0;
        while (k < n) {
          const c = color(r.values[k]);
          let e = k + 1, lo = r.values[k], hi = r.values[k];
          while (e < n && color(r.values[e]) === c) { const v = r.values[e]; if (v < lo) lo = v; if (v > hi) hi = v; e++; }
          const rect = S('rect', { x: (gx + k * cw).toFixed(2), y: ry.toFixed(2), width: ((e - k) * cw + 0.35).toFixed(2), height: (rowH + 0.35).toFixed(2), fill: c });
          const sl = e - k > 1 ? 'slots ' + (p.from + k) + '–' + (p.from + e - 1) : 'slot ' + (p.from + k);
          rect.appendChild(S('title', null, 'Enlace ' + r.link + ' · núcleo ' + r.core + ' · ' + sl + ': ' + (Math.abs(hi - lo) > 1e-9 ? fmt(lo) + ' a ' + fmt(hi) : fmt(lo))));
          cells.appendChild(rect);
          k = e;
        }
        g.appendChild(textEl(x0 + G.linkColW + G.coreColW / 2, ry + rowH / 2 + Math.min(fs, rowH) * 0.36, rowH >= 7 ? String(r.core) : '', { size: Math.min(fs, Math.max(6, rowH - 1)), font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
        lines.appendChild(S('line', { x1: x0 + G.linkColW, x2: gx + gw, y1: (ry + rowH).toFixed(2), y2: (ry + rowH).toFixed(2), stroke: gridC, 'stroke-width': rowH >= 6 ? 0.5 : 0, 'stroke-opacity': 0.7 }));
        const last = ri === rows.length - 1 || rows[ri + 1].link !== r.link;
        if (last) {
          const gy0 = top + groupStart * rowH, gy1 = ry + rowH;
          g.appendChild(textEl(x0 + G.linkColW / 2, (gy0 + gy1) / 2 + fs * 0.36, r.link, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
          lines.appendChild(S('line', { x1: x0, x2: gx + gw, y1: gy1.toFixed(2), y2: gy1.toFixed(2), stroke: gridC, 'stroke-width': st.slotLinkSep !== false ? 1.6 : 0.6 }));
          groupStart = ri + 1;
        }
      }
      const bottom = top + rows.length * rowH;
      if (st.slotCellGrid !== false && cw >= 5) {
        for (let k = 1; k < n; k++) { const xx = (gx + k * cw).toFixed(2); lines.appendChild(S('line', { x1: xx, x2: xx, y1: top, y2: bottom, stroke: gridC, 'stroke-width': 0.5, 'stroke-opacity': 0.8 })); }
      }
      // frame and column separators
      lines.appendChild(S('rect', { x: x0, y: top, width: (gx + gw - x0).toFixed(2), height: (bottom - top).toFixed(2), fill: 'none', stroke: gridC, 'stroke-width': 1.2 }));
      for (const xx of [x0 + G.linkColW, gx]) lines.appendChild(S('line', { x1: xx, x2: xx, y1: top, y2: bottom, stroke: gridC, 'stroke-width': 1 }));
      g.appendChild(cells);
      g.appendChild(lines);
      root.appendChild(g);
      y += G.panelH[pi];
    });
    if (st.slotLegend === 'right') slotLegend(root, model, st, color, box.x + box.w - G.legendW + 8, box.y + G.titleH + G.ptH + G.headH, G.legendW - 10, 320, true);
    if (st.slotLegend === 'bottom') slotLegend(root, model, st, color, box.x, box.y + G.H - G.legendH, box.w, G.legendH, false);
  }

  // ------------------------------------------------------------ entry point

  function render(model, st) {
    const W = Math.max(200, Number(st.width) || 760);
    let H = Math.max(150, Number(st.height) || 440);
    if (model && model.kind === 'panels' && Number(st.panelHeight) > 0) {
      const n = model.panels.length, cols = Math.max(1, Math.min(Number(st.panelCols) || 1, n));
      const rows = Math.ceil(n / cols);
      H = Math.round((st.showTitle && st.title ? st.titleSize * 1.6 : 0) + rows * Number(st.panelHeight) + (rows - 1) * (Number(st.panelGap) || 0));
    }
    if (model && model.kind === 'topo' && st.topoAutoHeight !== false) H = Math.max(150, topoHeight(model, st, W));
    if (model && model.kind === 'slots') H = Math.max(150, slotGeometry(model, st, W).H);
    const root = S('svg', { xmlns: NS, width: W, height: H, viewBox: `0 0 ${W} ${H}`, 'font-family': st.font });
    root.appendChild(S('rect', { x: 0, y: 0, width: W, height: H, fill: st.bg }));
    if (st.border === 'outer') root.appendChild(S('rect', { x: 0.5, y: 0.5, width: W - 1, height: H - 1, fill: 'none', stroke: st.borderColor }));
    const box = { x: 0, y: 0, w: W, h: H };
    if (!model) return root;
    if (model.kind === 'line') drawLine(root, model, st, box);
    else if (model.kind === 'bars') drawBars(root, model, st, box);
    else if (model.kind === 'cores') drawCores(root, model, st, box);
    else if (model.kind === 'topo') drawTopo(root, model, st, box);
    else if (model.kind === 'slots') drawSlots(root, model, st, box);
    else if (model.kind === 'panels') {
      const n = model.panels.length;
      const cols = Math.max(1, Math.min(Number(st.panelCols) || 1, n));
      const rows = Math.ceil(n / cols);
      let top = 0;
      if (st.showTitle && st.title) {
        root.appendChild(textEl(W / 2, st.titleSize * 1.1, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
        top = st.titleSize * 1.6;
      }
      const gap = Number(st.panelGap) || 0;
      const pw = (W - gap * (cols - 1)) / cols, ph = (H - top - gap * (rows - 1)) / rows;
      model.panels.forEach((p, i) => {
        const b = { x: (i % cols) * (pw + gap), y: top + Math.floor(i / cols) * (ph + gap), w: pw, h: ph };
        const sub = Object.assign({}, st, { panelLabel: p.label || '' });
        const opts = { title: p.title, titleSize: Math.max(10, st.titleSize - 2), noPanelLabel: !p.label };
        if (p.model.kind === 'bars') drawBars(root, p.model, sub, b, opts);
        else drawLine(root, p.model, sub, b, opts);
      });
    }
    return root;
  }

  function serialize(svgNode) {
    const clone = svgNode.cloneNode(true);
    clone.setAttribute('xmlns', NS);
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(clone);
  }

  /** Renders the SVG into a PNG data URL at the given scale (2 = double resolution). */
  function toPng(svgNode, scale) {
    return new Promise((resolve, reject) => {
      const text = serialize(svgNode);
      const img = new Image();
      const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml;charset=utf-8' }));
      img.onload = () => {
        const w = Number(svgNode.getAttribute('width')), h = Number(svgNode.getAttribute('height'));
        const cv = document.createElement('canvas');
        cv.width = Math.round(w * scale); cv.height = Math.round(h * scale);
        const ctx = cv.getContext('2d');
        ctx.scale(scale, scale); ctx.drawImage(img, 0, 0, w, h);
        URL.revokeObjectURL(url);
        resolve(cv.toDataURL('image/png'));
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Falha ao gerar PNG')); };
      img.src = url;
    });
  }

  App.figRender = { render, serialize, toPng, defaultStyle, fmtNumber, marker, dashArray, PALETTES, PALETTE_NAMES, MARKERS, MARKER_NAMES, DASHES, FONTS, SLOT_SCALES };
})(window.App);
