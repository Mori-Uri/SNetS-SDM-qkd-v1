# Parte 38 — launcher 1.16.0 (estúdio de gráficos)

Só o launcher mudou; o simulador e os resultados não mudam.

Arquivos:
- launcher/web/js/figures.js — seção "Como ler este gráfico", ocultar caso (c.hidden), pontos de carga das linhas
  (line.lps), ordem das séries (seriesOrder), painéis de linhas de referência, largura das barras, downloads numerados.
- launcher/web/js/figrender.js — xStep, marcas extras, linhas de referência, valores nos pontos das linhas, valueSize,
  barMaxPx.
- launcher/web/js/metricinfo.js — glossário dos itens da legenda (App.metricInfo.item).
- launcher/web/js/api.js — figureImageName.
- launcher/web/styles.css — estilos novos.
- launcher/src/launcher/Figures.java, LauncherServer.java — /api/figure/image-name (primeiro nome livre: n, n(1)…);
  VERSION 1.16.0.
- launcher/README.md, launcher/snets-launcher.jar.

Figuras salvas antes continuam abrindo (campos novos com valores padrão).
