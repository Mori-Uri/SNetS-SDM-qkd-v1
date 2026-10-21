package launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved figures of the chart studio. Everything lives in {@code launcher/figures/}:
 * {@code <name>.json} holds the figure definition (cases, data selection and style) and
 * {@code <name>.svg|.png} the exported images. Nothing is written inside {@code simulations/}.
 */
final class Figures {

    private final Settings settings;

    Figures(Settings settings) {
        this.settings = settings;
    }

    File dir() {
        return new File(settings.launcherDir(), "figures");
    }

    private static String clean(String name) {
        String n = name == null ? "" : name.trim();
        if (n.toLowerCase().endsWith(".json")) {
            n = n.substring(0, n.length() - 5);
        }
        if (n.isEmpty() || n.length() > 120 || !n.matches("[\\p{L}\\p{N} ._()\\-+]+") || n.startsWith(".")
                || n.contains("..")) {
            throw new IllegalArgumentException("Nome de figura inválido: use letras, números, espaço, . _ - ( ) +");
        }
        return n;
    }

    List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        File[] files = dir().listFiles();
        if (files == null) {
            return out;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            String n = f.getName();
            if (!f.isFile() || !n.endsWith(".json")) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            String base = n.substring(0, n.length() - 5);
            m.put("name", base);
            m.put("mtime", f.lastModified());
            m.put("svg", new File(dir(), base + ".svg").isFile());
            m.put("png", new File(dir(), base + ".png").isFile());
            out.add(m);
        }
        return out;
    }

    String read(String name) throws IOException {
        File f = new File(dir(), clean(name) + ".json");
        if (!f.isFile()) {
            throw new IllegalArgumentException("Figura não encontrada: " + name);
        }
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    String save(String name, String json) throws IOException {
        String n = clean(name);
        String t = json == null ? "" : json.trim();
        if (!t.startsWith("{") || t.length() > 5_000_000) {
            throw new IllegalArgumentException("Definição de figura inválida");
        }
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs()) {
            throw new IOException("Não foi possível criar " + d);
        }
        File f = new File(d, n + ".json");
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return f.getAbsolutePath();
    }

    /** Writes an exported image; {@code png} bodies are base64 (optionally a data: URL). */
    String saveImage(String name, String ext, String body) throws IOException {
        String n = clean(name);
        String e = ext == null ? "" : ext.toLowerCase();
        byte[] bytes;
        if ("svg".equals(e)) {
            bytes = body.getBytes(StandardCharsets.UTF_8);
        } else if ("png".equals(e) || "pdf".equals(e)) {
            String b = body.trim();
            int comma = b.indexOf(',');
            if (b.startsWith("data:") && comma > 0) {
                b = b.substring(comma + 1);
            }
            bytes = Base64.getDecoder().decode(b);
        } else {
            throw new IllegalArgumentException("Formato de imagem não suportado: " + ext);
        }
        if (bytes.length > 60_000_000) {
            throw new IllegalArgumentException("Imagem grande demais");
        }
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs()) {
            throw new IOException("Não foi possível criar " + d);
        }
        File f = new File(d, n + "." + e);
        Files.write(f.toPath(), bytes);
        return f.getAbsolutePath();
    }

    void delete(String name) throws IOException {
        String n = clean(name);
        for (String e : new String[] {".json", ".svg", ".png", ".pdf"}) {
            Files.deleteIfExists(new File(dir(), n + e).toPath());
        }
    }
}
