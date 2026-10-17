package launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Builds the simulator jar from the project sources, without Maven (launcher "Gerar JAR").
 *
 * <p>It compiles every {@code .java} under the source folder (Java 8 bytecode, ISO-8859-1, as in
 * the pom) against an existing simulator jar, which provides the dependencies (Gson, OpenCSV,
 * Firebase…) and the classes whose sources do not compile without Maven (simulationControl/Main,
 * which needs a newer Firebase). The new jar is the base jar with the compiled classes replacing
 * the old ones; classes of the project packages whose source file no longer exists are dropped.
 * The previous output jar is copied to a backup folder first.</p>
 *
 * <p>Usage: {@code java -cp snets-launcher.jar launcher.JarBuilder --src <dir> --base <jar> --out <jar>
 * [--exclude rel/path/A.java]... [--javac <javac>] [--backup <dir>] [--verify]}</p>
 */
public final class JarBuilder {

    private JarBuilder() {
    }

    static final String[] DEFAULT_EXCLUDES = {"simulationControl/Main.java"};

    public static void main(String[] args) throws Exception {
        // the launcher reads this process as UTF-8 (accents in the messages)
        System.setOut(new java.io.PrintStream(new FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        System.setErr(new java.io.PrintStream(new FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"));
        File src = null, base = null, out = null, backup = null;
        String javac = null;
        boolean verify = false;
        Set<String> excludes = new HashSet<String>(Arrays.asList(DEFAULT_EXCLUDES));
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--src".equals(a)) src = new File(args[++i]);
            else if ("--base".equals(a)) base = new File(args[++i]);
            else if ("--out".equals(a)) out = new File(args[++i]);
            else if ("--backup".equals(a)) backup = new File(args[++i]);
            else if ("--javac".equals(a)) javac = args[++i];
            else if ("--exclude".equals(a)) excludes.add(args[++i].replace('\\', '/'));
            else if ("--verify".equals(a)) verify = true;
        }
        if (src == null || base == null || out == null) {
            fail("uso: JarBuilder --src <pasta> --base <jar> --out <jar> [--backup <pasta>] [--javac <javac>] [--verify]");
        }
        if (!src.isDirectory()) fail("Pasta do código não encontrada: " + src.getAbsolutePath());
        if (!base.isFile()) fail("JAR base não encontrado: " + base.getAbsolutePath()
                + "\nO JAR base fornece as bibliotecas (Gson, OpenCSV, Firebase) e a classe Main; use o JAR atual do simulador.");

        long t0 = System.currentTimeMillis();
        File work = new File(out.getAbsoluteFile().getParentFile(), ".jarbuild");
        deleteTree(work);
        File classes = new File(work, "classes");
        if (!classes.mkdirs()) fail("Não foi possível criar " + classes);

        // 1. sources
        List<File> sources = new ArrayList<File>();
        Set<String> sourceTops = new HashSet<String>();
        Set<String> sourcePkgs = new HashSet<String>();
        collect(src, src, sources, sourceTops, sourcePkgs, excludes);
        System.out.println("[1/4] Código: " + src.getAbsolutePath());
        System.out.println("      " + sources.size() + " arquivos .java (fora: " + String.join(", ", excludes) + ")");
        if (sources.isEmpty()) fail("Nenhum arquivo .java em " + src);

        // 2. compile
        System.out.println("[2/4] Compilando (Java 8, ISO-8859-1) contra " + base.getName() + " …");
        File argFile = new File(work, "sources.txt");
        try (PrintWriter w = new PrintWriter(argFile, "UTF-8")) {
            for (File f : sources) w.println(quote(f.getAbsolutePath()));
        }
        int rc = compile(javac, base, classes, argFile);
        if (rc != 0) fail("A compilação falhou (código " + rc + "). Veja os erros acima; nenhum JAR foi alterado.");
        int compiled = countClasses(classes);
        System.out.println("      " + compiled + " classes compiladas");

        // 3. package
        System.out.println("[3/4] Montando o JAR …");
        File tmp = new File(work, out.getName());
        int[] counts = pack(base, classes, tmp, sourceTops, sourcePkgs);
        System.out.println("      " + counts[0] + " classes novas, " + counts[1] + " arquivos do JAR base mantidos, "
                + counts[2] + " classes antigas sem código-fonte removidas");

        // 4. backup and replace
        System.out.println("[4/4] Gravando " + out.getAbsolutePath());
        if (out.isFile()) {
            File bdir = backup != null ? backup : new File(out.getAbsoluteFile().getParentFile(), "backup_jar");
            if (!bdir.isDirectory() && !bdir.mkdirs()) fail("Não foi possível criar a pasta de backup " + bdir);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(out.lastModified()));
            String n = out.getName().replaceAll("\\.jar$", "");
            File b = new File(bdir, n + "_" + stamp + ".jar");
            Files.copy(out.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("      JAR anterior guardado em " + b.getAbsolutePath());
        }
        try {
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            fail("Não foi possível substituir " + out + " (" + e.getMessage() + ").\n"
                    + "Se uma simulação estiver usando este JAR, espere ela terminar ou cancele e tente de novo. O JAR novo ficou em " + tmp);
        }
        deleteTree(work);
        System.out.println(String.format(Locale.ROOT, "JAR gerado em %.1f s (%.1f MB).",
                (System.currentTimeMillis() - t0) / 1000.0, out.length() / 1048576.0));

        if (verify) verify(out);
        System.out.println("Concluído.");
    }

    // ------------------------------------------------------------------ compile

    private static int compile(String javacPath, File base, File classes, File argFile) throws Exception {
        boolean modern = !System.getProperty("java.specification.version", "1.8").startsWith("1.");
        List<String> opts = new ArrayList<String>(Arrays.asList("-nowarn", "-encoding", "ISO-8859-1",
                "-cp", base.getAbsolutePath(), "-d", classes.getAbsolutePath(), "-proc:none"));
        JavaCompiler jc = javacPath == null ? ToolProvider.getSystemJavaCompiler() : null;
        if (jc != null) {
            System.out.println("      compilador: o do Java atual (" + System.getProperty("java.version") + ")");
            List<String> all = new ArrayList<String>(opts);
            all.addAll(modern ? Arrays.asList("--release", "8") : Arrays.asList("-source", "1.8", "-target", "1.8"));
            if (modern) all.add("-Xlint:-options");
            all.add("@" + argFile.getAbsolutePath());
            return jc.run(null, System.out, System.err, all.toArray(new String[0]));
        }
        String exe = javacPath != null ? javacPath : findJavac();
        if (exe == null) {
            fail("Nenhum compilador Java (javac) encontrado: o launcher está rodando num JRE.\n"
                    + "Instale um JDK 8 ou mais novo (por exemplo, o Temurin) ou informe o caminho do javac em Configurações > Comando javac.");
        }
        System.out.println("      compilador: " + exe);
        int v = javacMajor(exe);
        List<String> cmd = new ArrayList<String>();
        cmd.add(exe);
        cmd.addAll(opts);
        cmd.addAll(v >= 9 ? Arrays.asList("--release", "8", "-Xlint:-options") : Arrays.asList("-source", "1.8", "-target", "1.8"));
        cmd.add("@" + argFile.getAbsolutePath());
        return run(cmd);
    }

    /** javac next to the running Java, in JAVA_HOME or on the PATH. */
    static String findJavac() {
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String name = win ? "javac.exe" : "javac";
        List<File> cands = new ArrayList<File>();
        File jh = new File(System.getProperty("java.home"));
        cands.add(new File(new File(jh, "bin"), name));
        if (jh.getParentFile() != null) cands.add(new File(new File(jh.getParentFile(), "bin"), name)); // JRE inside a JDK 8
        String env = System.getenv("JAVA_HOME");
        if (env != null) cands.add(new File(new File(env, "bin"), name));
        String path = System.getenv("PATH");
        if (path != null) for (String p : path.split(File.pathSeparator)) if (!p.isEmpty()) cands.add(new File(p, name));
        // JDKs installed by IntelliJ (~/.jdks) or in the usual Windows folders
        List<File> roots = new ArrayList<File>();
        roots.add(new File(System.getProperty("user.home"), ".jdks"));
        for (String pf : new String[] {System.getenv("ProgramFiles"), System.getenv("ProgramW6432"), System.getenv("ProgramFiles(x86)")}) {
            if (pf == null) continue;
            for (String vendor : new String[] {"Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft"}) {
                roots.add(new File(pf, vendor));
            }
        }
        for (File r : roots) {
            File[] kids = r.listFiles();
            if (kids == null) continue;
            Arrays.sort(kids, java.util.Collections.reverseOrder()); // newest names first
            for (File k : kids) cands.add(new File(new File(k, "bin"), name));
        }
        for (File f : cands) if (f.isFile()) return f.getAbsolutePath();
        return null;
    }

    private static int javacMajor(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version").redirectErrorStream(true).start();
            String s = readAll(p.getInputStream()).trim();
            p.waitFor();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("javac (\\d+)(?:\\.(\\d+))?").matcher(s);
            if (m.find()) {
                int a = Integer.parseInt(m.group(1));
                return a == 1 && m.group(2) != null ? Integer.parseInt(m.group(2)) : a;
            }
        } catch (Exception ignored) {
            // assume a modern javac
        }
        return 11;
    }

    // ------------------------------------------------------------------ package

    private static int[] pack(File base, File classes, File out, Set<String> sourceTops, Set<String> sourcePkgs) throws IOException {
        int added = 0, kept = 0, dropped = 0;
        Set<String> written = new HashSet<String>();
        Manifest mf = new Manifest();
        try (JarFile bj = new JarFile(base)) {
            if (bj.getManifest() != null) mf = new Manifest(bj.getManifest());
        }
        Attributes at = mf.getMainAttributes();
        at.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (at.getValue(Attributes.Name.MAIN_CLASS) == null) at.put(Attributes.Name.MAIN_CLASS, "simulationControl.Main");
        mf.getEntries().clear(); // per-entry digests of signed jars no longer apply
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(out), mf)) {
            written.add("META-INF/MANIFEST.MF");
            List<File> files = new ArrayList<File>();
            listFiles(classes, files);
            for (File f : files) {
                String name = classes.toURI().relativize(f.toURI()).getPath();
                jo.putNextEntry(new ZipEntry(name));
                Files.copy(f.toPath(), jo);
                jo.closeEntry();
                written.add(name);
                added++;
            }
            try (ZipFile zb = new ZipFile(base)) {
                Enumeration<? extends ZipEntry> en = zb.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || written.contains(n)) continue;
                    String up = n.toUpperCase(Locale.ROOT);
                    if (up.equals("META-INF/MANIFEST.MF") || (up.startsWith("META-INF/") && (up.endsWith(".SF") || up.endsWith(".RSA") || up.endsWith(".DSA")))) continue;
                    if (n.endsWith(".class") && isStale(n, sourceTops, sourcePkgs)) { dropped++; continue; }
                    jo.putNextEntry(new ZipEntry(n));
                    try (InputStream in = zb.getInputStream(e)) { copy(in, jo); }
                    jo.closeEntry();
                    written.add(n);
                    kept++;
                }
            }
        }
        return new int[] {added, kept, dropped};
    }

    /** A class of a project package whose top-level source file no longer exists. */
    static boolean isStale(String entry, Set<String> sourceTops, Set<String> sourcePkgs) {
        int slash = entry.lastIndexOf('/');
        String pkg = slash < 0 ? "" : entry.substring(0, slash);
        if (!sourcePkgs.contains(pkg)) return false;
        String simple = entry.substring(slash + 1, entry.length() - ".class".length());
        int dollar = simple.indexOf('$');
        String top = dollar < 0 ? simple : simple.substring(0, dollar);
        return !sourceTops.contains((pkg.isEmpty() ? "" : pkg + "/") + top);
    }

    // ------------------------------------------------------------------ verify

    private static void verify(File jar) throws Exception {
        List<String> names = new ArrayList<String>();
        try (ZipFile z = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("qkd/verification/") && n.endsWith("Verification.class") && !n.contains("$")) {
                    names.add(n.substring("qkd/verification/".length(), n.length() - ".class".length()));
                }
            }
        }
        java.util.Collections.sort(names);
        System.out.println("Verificações QKD (" + names.size() + "):");
        String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
        int ok = 0;
        List<String> bad = new ArrayList<String>();
        File cwd = jar.getAbsoluteFile().getParentFile().getParentFile(); // project root (simulations/..)
        for (String n : names) {
            Process p = new ProcessBuilder(java, "-cp", jar.getAbsolutePath(), "qkd.verification." + n)
                    .directory(cwd).redirectErrorStream(true).start();
            String outText = readAll(p.getInputStream());
            p.waitFor();
            boolean pass = outText.contains("ALL CHECKS PASSED");
            if (pass) ok++; else bad.add(n);
            System.out.println("  " + (pass ? "OK    " : "FALHA ") + n);
            if (!pass) {
                String[] lines = outText.split("\\r?\\n");
                for (int i = Math.max(0, lines.length - 6); i < lines.length; i++) System.out.println("        " + lines[i]);
            }
        }
        System.out.println(ok + " de " + names.size() + " verificações passaram" + (bad.isEmpty() ? "." : "; falharam: " + String.join(", ", bad)));
        if (!bad.isEmpty()) System.exit(2);
    }

    // ------------------------------------------------------------------ helpers

    private static void collect(File root, File dir, List<File> out, Set<String> tops, Set<String> pkgs, Set<String> excludes) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list);
        for (File f : list) {
            if (f.isDirectory()) { collect(root, f, out, tops, pkgs, excludes); continue; }
            if (!f.getName().endsWith(".java")) continue;
            String rel = root.toURI().relativize(f.toURI()).getPath();
            String noExt = rel.substring(0, rel.length() - ".java".length());
            tops.add(noExt);
            int slash = rel.lastIndexOf('/');
            pkgs.add(slash < 0 ? "" : rel.substring(0, slash));
            if (!excludes.contains(rel)) out.add(f);
        }
    }

    private static void listFiles(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list);
        for (File f : list) {
            if (f.isDirectory()) listFiles(f, out); else out.add(f);
        }
    }

    private static int countClasses(File dir) {
        List<File> l = new ArrayList<File>();
        listFiles(dir, l);
        int n = 0;
        for (File f : l) if (f.getName().endsWith(".class")) n++;
        return n;
    }

    private static int run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) System.out.println(line);
        }
        return p.waitFor();
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        copy(in, b);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static String quote(String p) {
        return "\"" + p.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        if (!f.delete()) f.deleteOnExit();
    }

    private static void fail(String msg) {
        System.err.println("ERRO: " + msg);
        System.exit(1);
    }
}
