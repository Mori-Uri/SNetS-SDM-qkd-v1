package launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * File-system access to the simulator's "simulations" folder.
 * Every path coming from the browser is relative to that folder and is
 * checked so that nothing outside it can be read or written.
 */
final class Workspace {

    /** Files that make up a SNetS simulation case. */
    static final List<String> CASE_FILES = Collections.unmodifiableList(Arrays.asList(
            "network", "physicalLayer", "simulation", "traffic", "others", "qkdPhysicalLayer"));
    static final String ROUTES_FILE = "kRoutesByPar.txt";

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.\\-+=()\\[\\] ]{1,120}");

    private final Settings settings;

    Workspace(Settings settings) {
        this.settings = settings;
    }

    File simulationsDir() {
        return settings.simulationsDir();
    }

    /** Resolves a "/"-separated path relative to the simulations folder, refusing escapes. */
    File resolve(String rel) throws IOException {
        File base = simulationsDir().getCanonicalFile();
        if (rel == null || rel.trim().isEmpty() || rel.equals("/") || rel.equals(".")) {
            return base;
        }
        String cleaned = rel.replace('\\', '/');
        File f = new File(base, cleaned.replace('/', File.separatorChar)).getCanonicalFile();
        String b = base.getPath();
        if (!f.getPath().equals(b) && !f.getPath().startsWith(b + File.separator)) {
            throw new IllegalArgumentException("Caminho fora da pasta simulations: " + rel);
        }
        return f;
    }

    static void checkName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches() || name.startsWith(".")
                || name.contains("..")) {
            throw new IllegalArgumentException("Nome inválido: '" + name
                    + "'. Use letras, números, espaço e _ - . + = ( ) [ ]");
        }
    }

    static boolean isCase(File dir) {
        return new File(dir, "simulation").isFile() && new File(dir, "network").isFile();
    }

    // ------------------------------------------------------------------ tree

    Map<String, Object> tree() throws IOException {
        File base = simulationsDir();
        Map<String, Object> root = node(base, "", 0);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("root", root);
        out.put("simulationsDir", base.getAbsolutePath());
        out.put("exists", base.isDirectory());
        return out;
    }

    private Map<String, Object> node(File dir, String rel, int depth) {
        Map<String, Object> n = new LinkedHashMap<String, Object>();
        n.put("name", rel.isEmpty() ? dir.getName() : dir.getName());
        n.put("path", rel);
        boolean isCase = isCase(dir);
        n.put("isCase", isCase);
        if (isCase) {
            int csv = 0;
            long last = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".csv")) {
                        csv++;
                        last = Math.max(last, f.lastModified());
                    }
                }
            }
            n.put("csvCount", csv);
            n.put("lastResult", last);
            n.put("modified", new File(dir, "simulation").lastModified());
        }
        List<Object> children = new ArrayList<Object>();
        if (depth < 7) {
            File[] list = dir.listFiles();
            if (list != null) {
                Arrays.sort(list, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                for (File f : list) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        String childRel = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
                        Map<String, Object> c = node(f, childRel, depth + 1);
                        // keep folders that are cases or contain cases somewhere below
                        if (Boolean.TRUE.equals(c.get("isCase")) || Boolean.TRUE.equals(c.get("hasCases"))
                                || depth == 0) {
                            children.add(c);
                        }
                    }
                }
            }
        }
        n.put("children", children);
        boolean hasCases = false;
        for (Object o : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) o;
            if (Boolean.TRUE.equals(c.get("isCase")) || Boolean.TRUE.equals(c.get("hasCases"))) {
                hasCases = true;
            }
        }
        n.put("hasCases", hasCases);
        return n;
    }

    // ------------------------------------------------------------------ case content

    Map<String, Object> readCase(String rel) throws IOException {
        File dir = resolve(rel);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Caso não encontrado: " + rel);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("path", rel);
        out.put("name", dir.getName());
        out.put("absolutePath", dir.getAbsolutePath());
        Map<String, Object> files = new LinkedHashMap<String, Object>();
        Map<String, Object> mtimes = new LinkedHashMap<String, Object>();
        for (String name : CASE_FILES) {
            File f = new File(dir, name);
            files.put(name, f.isFile() ? readText(f) : null);
            mtimes.put(name, f.isFile() ? f.lastModified() : 0L);
        }
        out.put("files", files);
        out.put("mtimes", mtimes);
        File routes = new File(dir, ROUTES_FILE);
        out.put("hasRoutesFile", routes.isFile());
        out.put("routesFileSize", routes.isFile() ? routes.length() : 0L);

        List<Object> csv = new ArrayList<Object>();
        List<Object> other = new ArrayList<Object>();
        File[] list = dir.listFiles();
        if (list != null) {
            Arrays.sort(list);
            for (File f : list) {
                if (!f.isFile()) {
                    continue;
                }
                Map<String, Object> info = new LinkedHashMap<String, Object>();
                info.put("name", f.getName());
                info.put("size", f.length());
                info.put("mtime", f.lastModified());
                if (f.getName().toLowerCase().endsWith(".csv")) {
                    csv.add(info);
                } else if (!CASE_FILES.contains(f.getName()) && !ROUTES_FILE.equals(f.getName())) {
                    other.add(info);
                }
            }
        }
        out.put("csv", csv);
        out.put("otherFiles", other);
        return out;
    }

    void writeCaseFile(String rel, String name, String content, long expectedMtime) throws IOException {
        if (!CASE_FILES.contains(name)) {
            throw new IllegalArgumentException("Arquivo não editável pelo launcher: " + name);
        }
        File dir = resolve(rel);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Caso não encontrado: " + rel);
        }
        File target = new File(dir, name);
        if (expectedMtime > 0 && target.isFile() && target.lastModified() != expectedMtime) {
            throw new IllegalStateException("O arquivo '" + name + "' foi alterado fora do launcher desde que "
                    + "foi aberto. Recarregue o caso antes de salvar para não sobrescrever essa alteração.");
        }
        writeAtomically(target, content);
    }

    void deleteRoutesFile(String rel) throws IOException {
        File f = new File(resolve(rel), ROUTES_FILE);
        if (f.isFile() && !f.delete()) {
            throw new IOException("Não foi possível apagar " + f);
        }
    }

    String readCsv(String rel, String name) throws IOException {
        if (name == null || !name.toLowerCase().endsWith(".csv") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Arquivo CSV inválido: " + name);
        }
        File f = new File(resolve(rel), name);
        if (!f.isFile()) {
            throw new IllegalArgumentException("CSV não encontrado: " + name);
        }
        return readText(f);
    }

    /** Reads a text result file (csv/txt/json/md) anywhere under the simulations folder. */
    String readTextFile(String rel) throws IOException {
        String lower = rel == null ? "" : rel.toLowerCase();
        if (!(lower.endsWith(".csv") || lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".md"))) {
            throw new IllegalArgumentException("Apenas arquivos .csv, .txt, .json ou .md podem ser lidos: " + rel);
        }
        File f = resolve(rel);
        if (!f.isFile()) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + rel);
        }
        if (f.length() > 16L * 1024 * 1024) {
            throw new IllegalArgumentException("Arquivo grande demais para a interface: " + rel);
        }
        return readText(f);
    }

    /** Lists the files of a folder under simulations (non-recursive). */
    List<Object> listFiles(String rel) throws IOException {
        File dir = resolve(rel);
        List<Object> out = new ArrayList<Object>();
        File[] list = dir.listFiles();
        if (list == null) {
            return out;
        }
        Arrays.sort(list);
        for (File f : list) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", f.getName());
            m.put("dir", f.isDirectory());
            m.put("size", f.isFile() ? f.length() : 0L);
            m.put("mtime", f.lastModified());
            out.add(m);
        }
        return out;
    }

    /** Creates a new case folder by copying the configuration (not the results) of another case. */
    String createCase(String parentRel, String name, String fromRel, boolean copyRoutes) throws IOException {
        checkName(name);
        File parent = resolve(parentRel);
        if (!parent.isDirectory()) {
            throw new IllegalArgumentException("Pasta de destino não existe: " + parentRel);
        }
        File dir = new File(parent, name);
        if (dir.exists()) {
            throw new IllegalArgumentException("Já existe uma pasta com esse nome: " + name);
        }
        if (!dir.mkdirs()) {
            throw new IOException("Não foi possível criar " + dir);
        }
        if (fromRel != null && !fromRel.isEmpty()) {
            File src = resolve(fromRel);
            if (!isCase(src)) {
                throw new IllegalArgumentException("Caso de origem inválido: " + fromRel);
            }
            for (String f : CASE_FILES) {
                File s = new File(src, f);
                if (s.isFile()) {
                    Files.copy(s.toPath(), new File(dir, f).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            File routes = new File(src, ROUTES_FILE);
            if (copyRoutes && routes.isFile()) {
                Files.copy(routes.toPath(), new File(dir, ROUTES_FILE).toPath());
            }
        } else {
            for (String f : CASE_FILES) {
                InputStream in = Workspace.class.getResourceAsStream("/templates/" + f);
                if (in == null) {
                    continue;
                }
                try {
                    Files.copy(in, new File(dir, f).toPath());
                } finally {
                    in.close();
                }
            }
        }
        String parentPath = parentRel == null ? "" : parentRel.replace('\\', '/');
        while (parentPath.endsWith("/")) {
            parentPath = parentPath.substring(0, parentPath.length() - 1);
        }
        return parentPath.isEmpty() ? name : parentPath + "/" + name;
    }

    String createFolder(String parentRel, String name) throws IOException {
        checkName(name);
        File parent = resolve(parentRel);
        File dir = new File(parent, name);
        if (dir.exists()) {
            throw new IllegalArgumentException("Já existe uma pasta com esse nome: " + name);
        }
        if (!dir.mkdirs()) {
            throw new IOException("Não foi possível criar " + dir);
        }
        String parentPath = parentRel == null ? "" : parentRel;
        return parentPath.isEmpty() ? name : parentPath + "/" + name;
    }

    /** Writes a .bat file into the simulations folder (next to the jars, like the existing scripts). */
    String writeScript(String name, String content) throws IOException {
        checkName(name);
        boolean bat = name.toLowerCase().endsWith(".bat");
        boolean sh = name.toLowerCase().endsWith(".sh");
        if (!bat && !sh) {
            throw new IllegalArgumentException("O script deve ter extensão .bat ou .sh");
        }
        File target = new File(simulationsDir(), name);
        String lf = content.replace("\r\n", "\n");
        // Windows cmd expects CRLF line endings; shells expect LF
        writeAtomically(target, bat ? lf.replace("\n", "\r\n") : lf);
        if (sh) {
            target.setExecutable(true, false);
        }
        return target.getAbsolutePath();
    }

    boolean scriptExists(String name) {
        return new File(simulationsDir(), name).exists();
    }

    List<Object> listJars() {
        List<Object> out = new ArrayList<Object>();
        addJars(simulationsDir(), "simulations", out);
        addJars(new File(settings.projectRoot(), "target"), "target", out);
        return out;
    }

    private static void addJars(File dir, String label, List<Object> out) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        Arrays.sort(list);
        for (File f : list) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                Map<String, Object> j = new LinkedHashMap<String, Object>();
                j.put("name", f.getName());
                j.put("location", label);
                j.put("path", f.getAbsolutePath());
                j.put("size", f.length());
                j.put("mtime", f.lastModified());
                out.add(j);
            }
        }
    }

    // ------------------------------------------------------------------ io helpers

    static String readText(File f) throws IOException {
        byte[] bytes = Files.readAllBytes(f.toPath());
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.startsWith("﻿")) {
            s = s.substring(1);
        }
        return s;
    }

    static void writeAtomically(File target, String content) throws IOException {
        File tmp = new File(target.getParentFile(), "." + target.getName() + ".launcher-tmp");
        OutputStream out = new FileOutputStream(tmp);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
