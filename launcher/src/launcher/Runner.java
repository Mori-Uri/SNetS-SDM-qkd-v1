package launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the simulator (or one of its QKD verification/reference tools) as a
 * separate process, exactly as the .bat scripts do. The launcher never loads
 * simulator classes, so changes in the simulator cannot break it and a crash
 * in a simulation cannot take the interface down.
 *
 * <p>Jobs can be started immediately or appended to a sequential queue.</p>
 */
final class Runner {

    private static final int MAX_LINES = 50000;
    private static final Pattern PROGRESS = Pattern.compile("progress:\\s*([0-9.Ee+-]+)\\s*%");

    private final Settings settings;
    private final Workspace workspace;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<String, Run> runs = new LinkedHashMap<String, Run>();
    private final LinkedList<Run> queue = new LinkedList<Run>();
    private Thread queueThread;

    Runner(Settings settings, Workspace workspace) {
        this.settings = settings;
        this.workspace = workspace;
    }

    static final class Run {
        final String id;
        final String casePath;
        final String label;
        final String kind;
        final String jar;
        final List<String> command;
        final File workDir;
        final boolean queued;
        volatile long start = 0;
        final List<String> lines = new ArrayList<String>();
        int droppedLines = 0;
        volatile long end = 0;
        volatile String state;
        volatile Integer exitCode = null;
        volatile double progress = 0.0;
        volatile Process process;
        volatile boolean cancelled = false;
        /** Threads override (-Dsnets.threads), 0 = value of the simulation file. */
        int threads = 0;
        /** Console window: open one for this run; closing it cancels the run. */
        boolean console = false;
        volatile boolean consoleSeen = false;
        volatile long lastBeat = 0;
        String group = null;

        Run(String id, String kind, String casePath, String label, String jar, List<String> command,
                File workDir, boolean queued) {
            this.id = id;
            this.kind = kind;
            this.casePath = casePath;
            this.label = label;
            this.jar = jar;
            this.command = command;
            this.workDir = workDir;
            this.queued = queued;
            this.state = queued ? "queued" : "running";
        }

        synchronized void add(String line) {
            lines.add(line);
            if (lines.size() > MAX_LINES) {
                lines.remove(0);
                droppedLines++;
            }
        }

        synchronized Map<String, Object> toMap(int from) {
            Map<String, Object> m = summary();
            int base = droppedLines;
            int startIdx = Math.max(0, from - base);
            List<String> out = new ArrayList<String>();
            for (int i = startIdx; i < lines.size(); i++) {
                out.add(lines.get(i));
            }
            m.put("lines", out);
            m.put("from", startIdx + base);
            m.put("next", base + lines.size());
            return m;
        }

        synchronized Map<String, Object> summary() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("kind", kind);
            m.put("case", casePath);
            m.put("label", label);
            m.put("jar", jar);
            m.put("command", command);
            m.put("state", state);
            m.put("queued", queued);
            m.put("exitCode", exitCode);
            m.put("progress", progress);
            m.put("start", start);
            m.put("end", end);
            m.put("lineCount", droppedLines + lines.size());
            m.put("threads", threads);
            m.put("console", console);
            m.put("consoleAttached", consoleSeen);
            m.put("group", group);
            return m;
        }
    }

    // ------------------------------------------------------------------ job construction

    private List<String> javaPrefix(String jvmArgs) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(settings.javaCommand());
        if (jvmArgs != null) {
            for (String a : jvmArgs.trim().split("\\s+")) {
                if (!a.isEmpty()) {
                    cmd.add(a);
                }
            }
        }
        return cmd;
    }

    private Run caseJob(String casePath, String jarPath, String jvmArgs, boolean queued) throws IOException {
        return caseJob(casePath, jarPath, jvmArgs, queued, 0, false);
    }

    private Run caseJob(String casePath, String jarPath, String jvmArgs, boolean queued, int threads,
            boolean console) throws IOException {
        File caseDir = workspace.resolve(casePath);
        if (!Workspace.isCase(caseDir)) {
            throw new IllegalArgumentException("A pasta não é um caso de simulação válido: " + casePath);
        }
        File jar = checkJar(jarPath);
        List<String> cmd = javaPrefix(jvmArgs);
        if (threads > 0) {
            cmd.add("-Dsnets.threads=" + threads);
        }
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        // relative path, like the .bat scripts (Main splits it into parent folder + name)
        // a case of the verification folder ("@verificacao/...") goes as an absolute path
        cmd.add(Workspace.isVerification(casePath) ? caseDir.getAbsolutePath() : casePath.replace('/', File.separatorChar));
        Run run = new Run("r" + ids.incrementAndGet(), "case", casePath, casePath, jar.getName(), cmd,
                workspace.simulationsDir(), queued);
        run.threads = Math.max(0, threads);
        run.console = console && (Settings.isWindows() || Boolean.getBoolean("snets.console.test"));
        return run;
    }

    private static File checkJar(String jarPath) {
        File jar = new File(jarPath == null ? "" : jarPath);
        if (!jar.isFile() && !jar.isDirectory()) {
            throw new IllegalArgumentException("JAR não encontrado: " + jarPath);
        }
        return jar;
    }

    /** A Java main class of the simulator jar (QKD verifiers and reference tools). */
    private Run toolJob(String label, String jarPath, String mainClass, List<String> args,
            String jvmArgs, boolean queued) {
        File jar = checkJar(jarPath);
        List<String> cmd = javaPrefix(jvmArgs);
        cmd.add("-cp");
        cmd.add(jar.getAbsolutePath());
        cmd.add(mainClass);
        cmd.addAll(args);
        return new Run("r" + ids.incrementAndGet(), "tool", null, label, jar.getName(), cmd,
                settings.projectRoot(), queued);
    }

    // ------------------------------------------------------------------ start / queue

    synchronized Run start(String casePath, String jarPath, String jvmArgs) throws IOException {
        return start(casePath, jarPath, jvmArgs, 0, settings.runConsole());
    }

    private void checkNotActive(String casePath) {
        for (Run r : runs.values()) {
            if (("running".equals(r.state) || "queued".equals(r.state)) && casePath.equals(r.casePath)) {
                throw new IllegalStateException("O caso " + casePath + " já está em execução ou na fila (" + r.id + ").");
            }
        }
    }

    synchronized Run start(String casePath, String jarPath, String jvmArgs, int threads, boolean console)
            throws IOException {
        checkNotActive(casePath);
        Run run = caseJob(casePath, jarPath, jvmArgs, false, threads, console);
        runs.put(run.id, run);
        launch(run, false);
        return run;
    }

    synchronized Run startTool(String label, String jarPath, String mainClass, List<String> args,
            String jvmArgs, boolean queued) {
        Run run = toolJob(label, jarPath, mainClass, args, jvmArgs, queued);
        runs.put(run.id, run);
        if (queued) {
            queue.add(run);
            ensureQueueThread();
        } else {
            launch(run, false);
        }
        return run;
    }

    synchronized List<Object> enqueueCases(List<String> cases, String jarPath, String jvmArgs) throws IOException {
        return enqueueCases(cases, jarPath, jvmArgs, 0, settings.runConsole());
    }

    /** Sequential batch: one after the other, each with the given threads (0 = its file). */
    synchronized List<Object> enqueueCases(List<String> cases, String jarPath, String jvmArgs, int threads,
            boolean console) throws IOException {
        List<Run> created = new ArrayList<Run>();
        String group = "g" + ids.incrementAndGet();
        for (String c : cases) {
            checkNotActive(c);
            Run r = caseJob(c, jarPath, jvmArgs, true, threads, console);
            r.group = group;
            created.add(r);
        }
        List<Object> out = new ArrayList<Object>();
        for (Run r : created) {
            runs.put(r.id, r);
            queue.add(r);
            out.add(r.summary());
        }
        ensureQueueThread();
        return out;
    }

    /**
     * Parallel batch: every case starts now and the total number of threads is
     * divided as equally as possible among them (at least 1 each).
     */
    synchronized List<Object> startParallel(List<String> cases, String jarPath, String jvmArgs, int totalThreads,
            boolean console) throws IOException {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Nenhum caso informado");
        }
        for (String c : cases) {
            checkNotActive(c);
        }
        int k = cases.size();
        int total = Math.max(k, totalThreads);
        String group = "g" + ids.incrementAndGet();
        List<Run> created = new ArrayList<Run>();
        for (int i = 0; i < k; i++) {
            int t = total / k + (i < total % k ? 1 : 0);
            Run r = caseJob(cases.get(i), jarPath, jvmArgs, false, Math.max(1, t), console);
            r.group = group;
            created.add(r);
        }
        List<Object> out = new ArrayList<Object>();
        for (Run r : created) {
            runs.put(r.id, r);
            r.add("[launcher] execução paralela (" + group + "): " + r.threads + " de " + total + " threads");
            launch(r, false);
            out.add(r.summary());
        }
        return out;
    }

    private void ensureQueueThread() {
        if (queueThread != null && queueThread.isAlive()) {
            return;
        }
        queueThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Run next;
                    synchronized (Runner.this) {
                        next = queue.poll();
                        if (next == null) {
                            queueThread = null;
                            return;
                        }
                        if (next.cancelled) {
                            continue;
                        }
                    }
                    launch(next, true);
                }
            }
        }, "snets-queue");
        queueThread.setDaemon(true);
        queueThread.start();
    }

    /** Starts the process; when {@code wait} is true blocks until it ends (queue). */
    private void launch(final Run run, boolean wait) {
        ProcessBuilder pb = new ProcessBuilder(run.command);
        pb.directory(run.workDir);
        pb.redirectErrorStream(true);
        run.start = System.currentTimeMillis();
        run.state = "running";
        run.add("> " + join(run.command));
        run.add("> pasta de trabalho: " + run.workDir.getAbsolutePath());
        final Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            run.state = "failed";
            run.end = System.currentTimeMillis();
            run.add("Falha ao iniciar o processo: " + e.getMessage());
            return;
        }
        run.process = p;
        if (run.threads > 0) {
            run.add("[launcher] threads desta execução: " + run.threads + " (o arquivo simulation não foi alterado)");
        }
        if (run.console) {
            openConsole(run);
        }
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                pump(run, p);
            }
        }, "snets-run-" + run.id);
        reader.setDaemon(true);
        reader.start();
        if (wait) {
            try {
                reader.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void pump(Run run, Process p) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), (run.command.contains("qkd.ladder.QKDLadderMain") || run.command.contains("launcher.JarBuilder"))
                        ? java.nio.charset.StandardCharsets.UTF_8 : Charset.defaultCharset()))) {
            String line;
            while ((line = br.readLine()) != null) {
                run.add(line);
                Matcher m = PROGRESS.matcher(line);
                if (m.find()) {
                    try {
                        run.progress = Math.min(1.0, Double.parseDouble(m.group(1)) / 100.0);
                    } catch (NumberFormatException ignored) {
                        // ignore
                    }
                }
            }
        } catch (IOException e) {
            run.add("[launcher] leitura da saída interrompida: " + e.getMessage());
        }
        try {
            int code = p.waitFor();
            run.exitCode = code;
            run.end = System.currentTimeMillis();
            if (run.cancelled) {
                run.state = "cancelled";
            } else {
                run.state = code == 0 ? "finished" : "failed";
                if (code == 0) {
                    run.progress = 1.0;
                }
            }
            run.add("[launcher] processo terminou com código " + code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ console window (Windows)

    private volatile int port;
    private Thread watchdog;
    /** Internal test hook (snets.console.test): RunConsole processes started without a window. */
    final Map<String, Process> consoleTestProcesses = new java.util.concurrent.ConcurrentHashMap<String, Process>();

    void setPort(int port) {
        this.port = port;
        startWatchdog();
    }

    /** Class path of the launcher itself (jar or classes folder), for RunConsole. */
    private static String launcherClassPath() {
        try {
            return new File(Runner.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Opens a console window running {@link RunConsole}, which prints the output
     * and progress of the run and sends a heartbeat. When the window is closed the
     * heartbeat stops and the watchdog cancels the run.
     */
    private void openConsole(Run run) {
        String cp = launcherClassPath();
        boolean test = Boolean.getBoolean("snets.console.test"); // internal: runs RunConsole without a window
        if (cp == null || port <= 0 || (!Settings.isWindows() && !test)) {
            run.add("[launcher] janela de console não aberta (disponível apenas no Windows)");
            run.console = false;
            return;
        }
        String title = ("SNetS " + run.id + " " + (run.casePath == null ? run.label : run.casePath))
                .replaceAll("[\\\"&|<>^%]", "_");
        List<String> cmd = new ArrayList<String>();
        if (!test) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("\"" + title + "\"");
        }
        cmd.add(settings.javaCommand());
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add("launcher.RunConsole");
        cmd.add(String.valueOf(port));
        cmd.add(run.id);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(run.workDir);
            if (test) {
                pb.redirectOutput(new File(System.getProperty("java.io.tmpdir"), "snets-console-" + run.id + ".log"));
                pb.redirectErrorStream(true);
            }
            Process cp2 = pb.start();
            if (test) {
                consoleTestProcesses.put(run.id, cp2);
            }
            run.lastBeat = System.currentTimeMillis();
            run.add("[launcher] janela de console aberta; fechá-la cancela esta execução");
        } catch (IOException e) {
            run.console = false;
            run.add("[launcher] não foi possível abrir a janela de console: " + e.getMessage());
        }
    }

    /** Called by RunConsole about once per second. */
    void heartbeat(Run run) {
        run.consoleSeen = true;
        run.lastBeat = System.currentTimeMillis();
    }

    private synchronized void startWatchdog() {
        if (watchdog != null) {
            return;
        }
        watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    List<Run> lost = new ArrayList<Run>();
                    long now = System.currentTimeMillis();
                    synchronized (Runner.this) {
                        for (Run r : runs.values()) {
                            if (r.console && r.consoleSeen && "running".equals(r.state) && !r.cancelled
                                    && now - r.lastBeat > 8000) {
                                lost.add(r);
                            }
                        }
                    }
                    for (Run r : lost) {
                        r.add("[launcher] a janela de console foi fechada: execução cancelada");
                        try {
                            cancel(r.id);
                        } catch (Exception ignored) {
                            // already finished
                        }
                    }
                }
            }
        }, "snets-console-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    synchronized Run get(String id) {
        Run r = runs.get(id);
        if (r == null) {
            throw new IllegalArgumentException("Execução não encontrada: " + id);
        }
        return r;
    }

    synchronized List<Object> list() {
        List<Object> out = new ArrayList<Object>();
        for (Run r : runs.values()) {
            out.add(r.summary());
        }
        return out;
    }

    void cancel(String id) {
        Run r = get(id);
        synchronized (this) {
            if ("queued".equals(r.state)) {
                r.cancelled = true;
                r.state = "cancelled";
                r.end = System.currentTimeMillis();
                queue.remove(r);
                r.add("[launcher] removido da fila");
                return;
            }
        }
        Process p = r.process;
        if (p != null && "running".equals(r.state)) {
            r.cancelled = true;
            r.add("[launcher] cancelamento solicitado");
            p.destroy();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    /** Cancels every queued job (running jobs continue). */
    synchronized int clearQueue() {
        int n = 0;
        for (Run r : queue) {
            r.cancelled = true;
            r.state = "cancelled";
            r.end = System.currentTimeMillis();
            n++;
        }
        queue.clear();
        return n;
    }

    synchronized void stopAll() {
        clearQueue();
        for (Run r : runs.values()) {
            Process p = r.process;
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    private static String join(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String c : cmd) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(c.contains(" ") ? "\"" + c + "\"" : c);
        }
        return sb.toString();
    }
}
