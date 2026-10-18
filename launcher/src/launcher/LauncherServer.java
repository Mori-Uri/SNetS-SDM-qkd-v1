package launcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * SNetS Launcher - local web interface for configuring and running SNetS
 * simulations. It is an add-on: it only reads/writes the simulation folders
 * and starts the simulator jar as an external process.
 *
 * Usage: java -jar snets-launcher.jar [--port 8765] [--project <dir>] [--no-browser] [--web <dir>]
 */
public final class LauncherServer {

    static final String VERSION = "1.10.0";

    private final Settings settings;
    private final Workspace workspace;
    private final Runner runner;
    private final Tools tools;
    private final Figures figures;
    private final File webDir;
    private int port;

    private LauncherServer(Settings settings, File webDir) {
        this.settings = settings;
        this.workspace = new Workspace(settings);
        this.runner = new Runner(settings, workspace);
        this.tools = new Tools(settings);
        this.figures = new Figures(settings);
        this.webDir = webDir;
    }

    public static void main(String[] args) throws Exception {
        int port = 8765;
        boolean openBrowser = true;
        String project = null;
        File webDir = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--port".equals(a) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--project".equals(a) && i + 1 < args.length) {
                project = args[++i];
            } else if ("--no-browser".equals(a)) {
                openBrowser = false;
            } else if ("--web".equals(a) && i + 1 < args.length) {
                webDir = new File(args[++i]);
            } else if ("--help".equals(a) || "-h".equals(a)) {
                System.out.println("java -jar snets-launcher.jar [--port 8765] [--project <pasta do SNetS>] "
                        + "[--no-browser]");
                return;
            }
        }
        Settings settings = new Settings(Settings.detectLauncherDir());
        if (project != null) {
            settings.set("projectRoot", new File(project).getAbsolutePath());
        }
        LauncherServer server = new LauncherServer(settings, webDir);
        server.start(port, openBrowser);
    }

    private void start(int requestedPort, boolean openBrowser) throws IOException {
        HttpServer http = null;
        IOException last = null;
        for (int p = requestedPort; p < requestedPort + 20; p++) {
            try {
                http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0);
                port = p;
                break;
            } catch (BindException e) {
                last = e;
            }
        }
        if (http == null) {
            throw last != null ? last : new IOException("Nenhuma porta livre");
        }
        http.createContext("/api/", new ApiHandler());
        http.createContext("/", new StaticHandler());
        http.setExecutor(Executors.newCachedThreadPool());
        http.start();
        runner.setPort(port);

        final String url = "http://localhost:" + port + "/";
        System.out.println("==============================================");
        System.out.println(" SNetS Launcher " + VERSION);
        System.out.println(" Projeto:     " + settings.projectRoot().getAbsolutePath());
        System.out.println(" Simulations: " + settings.simulationsDir().getAbsolutePath());
        System.out.println(" Interface:   " + url);
        System.out.println(" Fechar esta janela encerra o launcher e as execucoes em andamento.");
        System.out.println(" Fechar o navegador nao interrompe nada: reabra " + url);
        System.out.println("==============================================");

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                runner.stopAll();
            }
        }));

        if (openBrowser) {
            openBrowser(url);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception | Error ignored) {
            // try OS command below
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException ignored) {
            System.out.println("Abra no navegador: " + url);
        }
    }

    // ================================================================== API

    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if (!originAllowed(ex)) {
                    send(ex, 403, error("Origem não permitida"));
                    return;
                }
                String path = ex.getRequestURI().getPath();
                String method = ex.getRequestMethod();
                Map<String, String> q = query(ex.getRequestURI().getRawQuery());
                if ("/api/run/console".equals(path)) {
                    sendText(ex, consoleText(q));
                    return;
                }
                Object result = route(method, path, q, ex);
                send(ex, 200, Json.write(result));
            } catch (IllegalArgumentException | IllegalStateException e) {
                send(ex, 400, error(e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                send(ex, 500, error(e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private Object route(String method, String path, Map<String, String> q, HttpExchange ex) throws Exception {
        boolean post = "POST".equals(method) || "PUT".equals(method);
        switch (path) {
            case "/api/info": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("version", VERSION);
                m.put("settings", settings.toMap());
                m.put("jars", workspace.listJars());
                m.put("javaVersion", System.getProperty("java.version"));
                m.put("os", System.getProperty("os.name"));
                m.put("cpus", Runtime.getRuntime().availableProcessors());
                m.put("simulationsExists", workspace.simulationsDir().isDirectory());
                return m;
            }
            case "/api/settings": {
                requirePost(post);
                Map<String, String> body = query(readBody(ex));
                for (String key : new String[] {"projectRoot", "simulationsDir", "javaCommand", "defaultJar",
                    "jvmArgs", "runConsole", "javacCommand"}) {
                    if (body.containsKey(key)) {
                        String v = body.get(key);
                        if (("projectRoot".equals(key) || "simulationsDir".equals(key)) && v != null
                                && !v.trim().isEmpty() && !new File(v.trim()).isDirectory()) {
                            throw new IllegalArgumentException("Pasta não encontrada: " + v);
                        }
                        settings.set(key, v);
                    }
                }
                settings.save();
                return settings.toMap();
            }
            case "/api/tree":
                return workspace.tree();
            case "/api/catalog":
                return Catalog.build(settings.projectRoot());
            case "/api/case":
                return workspace.readCase(required(q, "path"));
            case "/api/file": {
                requirePost(post);
                long mtime = q.containsKey("mtime") ? Long.parseLong(q.get("mtime")) : 0L;
                workspace.writeCaseFile(required(q, "path"), required(q, "name"), readBody(ex), mtime);
                return ok();
            }
            case "/api/routes/delete": {
                requirePost(post);
                workspace.deleteRoutesFile(required(q, "path"));
                return ok();
            }
            case "/api/csv": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("text", workspace.readCsv(required(q, "path"), required(q, "name")));
                return m;
            }
            case "/api/case/create": {
                requirePost(post);
                String rel = workspace.createCase(q.get("parent"), required(q, "name"), q.get("from"),
                        "true".equals(q.get("copyRoutes")));
                Map<String, Object> m = ok();
                m.put("path", rel);
                return m;
            }
            case "/api/folder/create": {
                requirePost(post);
                Map<String, Object> m = ok();
                m.put("path", workspace.createFolder(q.get("parent"), required(q, "name")));
                return m;
            }
            case "/api/script": {
                requirePost(post);
                String name = required(q, "name");
                if (workspace.scriptExists(name) && !"true".equals(q.get("overwrite"))) {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("ok", false);
                    m.put("exists", true);
                    return m;
                }
                Map<String, Object> m = ok();
                m.put("file", workspace.writeScript(name, readBody(ex)));
                return m;
            }
            case "/api/run": {
                requirePost(post);
                String jvm = q.containsKey("jvmArgs") ? q.get("jvmArgs") : settings.get("jvmArgs", "");
                int threads = q.containsKey("threads") && !q.get("threads").isEmpty() ? Integer.parseInt(q.get("threads")) : 0;
                boolean console = q.containsKey("console") ? "true".equals(q.get("console")) : settings.runConsole();
                Runner.Run r = runner.start(required(q, "path"), required(q, "jar"), jvm, threads, console);
                return r.summary();
            }
            case "/api/run/batch": {
                requirePost(post);
                List<String> cases = new ArrayList<String>();
                for (String line : readBody(ex).split("\\r?\\n")) {
                    if (!line.trim().isEmpty()) {
                        cases.add(line.trim());
                    }
                }
                if (cases.isEmpty()) {
                    throw new IllegalArgumentException("Nenhum caso informado");
                }
                String jvm = q.containsKey("jvmArgs") ? q.get("jvmArgs") : settings.get("jvmArgs", "");
                int threads = q.containsKey("threads") && !q.get("threads").isEmpty() ? Integer.parseInt(q.get("threads")) : 0;
                boolean console = q.containsKey("console") ? "true".equals(q.get("console")) : settings.runConsole();
                if ("parallel".equals(q.get("mode"))) {
                    if (threads <= 0) {
                        threads = Runtime.getRuntime().availableProcessors();
                    }
                    return runner.startParallel(cases, required(q, "jar"), jvm, threads, console);
                }
                return runner.enqueueCases(cases, required(q, "jar"), jvm, threads, console);
            }
            case "/api/queue": {
                requirePost(post);
                List<String> cases = new ArrayList<String>();
                for (String line : readBody(ex).split("\\r?\\n")) {
                    if (!line.trim().isEmpty()) {
                        cases.add(line.trim());
                    }
                }
                if (cases.isEmpty()) {
                    throw new IllegalArgumentException("Nenhum caso informado para a fila");
                }
                String jvm = q.containsKey("jvmArgs") ? q.get("jvmArgs") : settings.get("jvmArgs", "");
                return runner.enqueueCases(cases, required(q, "jar"), jvm);
            }
            case "/api/queue/clear": {
                requirePost(post);
                Map<String, Object> m = ok();
                m.put("removed", runner.clearQueue());
                return m;
            }
            case "/api/tools": {
                return tools.describe(new File(required(q, "jar")));
            }
            case "/api/jar/info": {
                // Parte 28: build the simulator jar from the sources (launcher.JarBuilder)
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                File src = new File(settings.projectRoot(), "src" + File.separator + "main" + File.separator + "java");
                m.put("src", src.getAbsolutePath());
                m.put("srcExists", src.isDirectory());
                String jc = settings.get("javacCommand", "");
                boolean inJvm = javax.tools.ToolProvider.getSystemJavaCompiler() != null;
                String found = jc != null && !jc.trim().isEmpty() ? jc.trim() : JarBuilder.findJavac();
                m.put("javac", found);
                m.put("compilerInLauncher", inJvm);
                m.put("javaVersion", System.getProperty("java.version"));
                m.put("simulationsDir", settings.simulationsDir().getAbsolutePath());
                m.put("defaultOut", "SNetS-SDM-QKD.jar");
                m.put("jars", workspace.listJars());
                return m;
            }
            case "/api/jar/build": {
                requirePost(post);
                File src = new File(settings.projectRoot(), "src" + File.separator + "main" + File.separator + "java");
                File base = new File(required(q, "base"));
                String outName = required(q, "out").trim();
                if (!outName.matches("[A-Za-z0-9_.+\\-]+\\.jar")) throw new IllegalArgumentException("Nome de JAR inválido: " + outName);
                if (outName.equalsIgnoreCase("SNetS-SDM-SBRC26.jar")) {
                    throw new IllegalArgumentException("O SNetS-SDM-SBRC26.jar é o JAR clássico de referência (CLASSICAL_BASE_SHA256.txt) e não deve ser substituído. Escolha outro nome.");
                }
                File out = new File(settings.simulationsDir(), outName);
                List<String> args = new ArrayList<String>(java.util.Arrays.asList("--src", src.getAbsolutePath(),
                        "--base", base.getAbsolutePath(), "--out", out.getAbsolutePath(),
                        "--backup", new File(settings.simulationsDir(), "backup_jar").getAbsolutePath()));
                String jc = settings.get("javacCommand", "");
                if (jc != null && !jc.trim().isEmpty()) { args.add("--javac"); args.add(jc.trim()); }
                if ("true".equals(q.get("verify"))) args.add("--verify");
                File self;
                try {
                    self = new File(LauncherServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                } catch (Exception e) {
                    throw new IllegalStateException("Não foi possível localizar o snets-launcher.jar: " + e.getMessage());
                }
                Runner.Run r = runner.startTool("Gerar JAR: " + outName, self.getAbsolutePath(), "launcher.JarBuilder", args, "", false);
                return r.summary();
            }
            case "/api/tool/run": {
                requirePost(post);
                File jar = new File(required(q, "jar"));
                List<String> spec = tools.resolve(required(q, "id"), jar);
                String jvm = q.containsKey("jvmArgs") ? q.get("jvmArgs") : settings.get("jvmArgs", "");
                Runner.Run r = runner.startTool(spec.get(0), jar.getAbsolutePath(), spec.get(1),
                        spec.subList(2, spec.size()), jvm, "true".equals(q.get("queued")));
                return r.summary();
            }
            case "/api/text": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                String rel = required(q, "path");
                if ("true".equals(q.get("optional")) && !workspace.resolve(rel).isFile()) {
                    m.put("text", null);
                    return m;
                }
                m.put("text", workspace.readTextFile(rel));
                return m;
            }
            case "/api/files":
                return workspace.listFiles(q.get("path"));
            case "/api/runs":
                return runner.list();
            case "/api/run/log": {
                int from = q.containsKey("from") ? Integer.parseInt(q.get("from")) : 0;
                return runner.get(required(q, "id")).toMap(from);
            }
            case "/api/run/cancel": {
                requirePost(post);
                runner.cancel(required(q, "id"));
                return ok();
            }
            case "/api/open-folder": {
                requirePost(post);
                File dir = workspace.resolve(q.get("path"));
                openFolder(dir);
                return ok();
            }
            case "/api/figures": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("dir", figures.dir().getAbsolutePath());
                m.put("items", figures.list());
                return m;
            }
            case "/api/figure": {
                if (post) {
                    Map<String, Object> m = ok();
                    m.put("file", figures.save(required(q, "name"), readBody(ex)));
                    return m;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("text", figures.read(required(q, "name")));
                return m;
            }
            case "/api/figure/image": {
                requirePost(post);
                Map<String, Object> m = ok();
                m.put("file", figures.saveImage(required(q, "name"), required(q, "ext"), readBody(ex)));
                return m;
            }
            case "/api/figure/delete": {
                requirePost(post);
                figures.delete(required(q, "name"));
                return ok();
            }
            case "/api/figures/open-folder": {
                requirePost(post);
                java.io.File d = figures.dir();
                if (!d.isDirectory()) {
                    d.mkdirs();
                }
                openFolder(d);
                return ok();
            }
            default:
                throw new IllegalArgumentException("Endpoint desconhecido: " + method + " " + path);
        }
    }

    private static void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
                return;
            }
        } catch (Exception | Error ignored) {
            // fallback
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer", dir.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", dir.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
            }
        } catch (IOException ignored) {
            // nothing else to do
        }
    }

    private boolean originAllowed(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return true;
        }
        return origin.equals("http://localhost:" + port) || origin.equals("http://127.0.0.1:" + port);
    }

    private static void requirePost(boolean post) {
        if (!post) {
            throw new IllegalArgumentException("Use POST para esta operação");
        }
    }

    private static String required(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: " + key);
        }
        return v;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", true);
        return m;
    }

    private static String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", false);
        m.put("error", msg == null ? "erro" : msg);
        return Json.write(m);
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String part : raw.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int i = part.indexOf('=');
            try {
                String k = URLDecoder.decode(i < 0 ? part : part.substring(0, i), "UTF-8");
                String v = i < 0 ? "" : URLDecoder.decode(part.substring(i + 1), "UTF-8");
                m.put(k, v);
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return m;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        InputStream in = ex.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Plain-text feed of RunConsole; each request is also its heartbeat. */
    private String consoleText(Map<String, String> q) {
        Runner.Run run = runner.get(required(q, "id"));
        runner.heartbeat(run);
        int from = q.containsKey("from") ? Integer.parseInt(q.get("from")) : 0;
        Map<String, Object> m = run.toMap(from);
        StringBuilder sb = new StringBuilder();
        long start = run.start == 0 ? System.currentTimeMillis() : run.start;
        long end = run.end == 0 ? System.currentTimeMillis() : run.end;
        sb.append(run.state).append('\t').append(run.progress).append('\t').append(m.get("next"))
          .append('\t').append(end - start).append('\t').append(run.label).append('\n');
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) m.get("lines");
        for (String l : lines) {
            sb.append(l.replace('\r', ' ')).append('\n');
        }
        return sb.toString();
    }

    private static void sendText(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ================================================================== static files

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] bytes = null;
            if (webDir != null) {
                File f = new File(webDir, path.substring(1));
                if (f.isFile()) {
                    bytes = Files.readAllBytes(f.toPath());
                }
            } else {
                InputStream in = LauncherServer.class.getResourceAsStream("/web" + path);
                if (in != null) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            bos.write(buf, 0, n);
                        }
                        bytes = bos.toByteArray();
                    } finally {
                        in.close();
                    }
                }
            }
            if (bytes == null) {
                byte[] msg = "404".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, msg.length);
                ex.getResponseBody().write(msg);
                ex.close();
                return;
            }
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
