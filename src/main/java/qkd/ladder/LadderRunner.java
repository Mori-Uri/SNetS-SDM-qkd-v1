package qkd.ladder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes ladder cases and runs them through the normal SNetS entry point
 * ({@code simulationControl.Main.localSimulation}). A case whose five
 * configuration files did not change and that already has results is not
 * simulated again, so a step can reuse the cases of an earlier step.
 */
final class LadderRunner {

    static final String[] CONFIG_FILES = {"network", "physicalLayer", "simulation", "traffic", "others"};

    private final ProgressFilter progress;
    boolean force;
    int ran, reused;

    LadderRunner() {
        PrintStream utf8;
        try {
            utf8 = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            utf8 = System.out;
        }
        this.progress = new ProgressFilter(utf8);
        try {
            System.setOut(new PrintStream(progress, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            System.setOut(new PrintStream(progress, true));
        }
    }

    void setProgress(double base, double span) {
        progress.base = base;
        progress.span = span;
        progress.overall(base);
    }

    /** Ensures the case exists with these files and has results; returns true if it was simulated now. */
    boolean ensure(File dir, Map<String, String> files) throws Exception {
        // SNetS rewrites the configuration files after a run, so the generated files are
        // identified by a hash stored next to the results.
        String key = key(files);
        File keyFile = new File(dir, ".ladder_key");
        String old0 = LadderIO.readOrNull(keyFile);
        boolean same = old0 != null && old0.trim().equals(key);
        if (same && !force && hasResults(dir) && !new File(dir, ".ladder_incomplete").isFile()) {
            reused++;
            progress.raw("[ladder] reutilizando resultados de " + dir.getName());
            return false;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (f.getName().endsWith(".csv")) f.delete();
        for (Map.Entry<String, String> e : files.entrySet()) LadderIO.write(new File(dir, e.getKey()), e.getValue());
        File marker = new File(dir, ".ladder_incomplete");
        LadderIO.write(marker, "running");
        progress.raw("[ladder] simulando " + dir.getAbsolutePath());
        Class<?> main = Class.forName("simulationControl.Main");
        Method local = main.getMethod("localSimulation", String.class);
        try {
            local.invoke(null, dir.getAbsolutePath());
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Falha ao simular " + dir.getName() + ": " + c, c);
        }
        marker.delete();
        LadderIO.write(keyFile, key + "\n");
        ran++;
        return true;
    }

    static String key(Map<String, String> files) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> e : files.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(e.getValue().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasResults(File dir) {
        String[] n = dir.list();
        if (n == null) return false;
        for (String s : n) if (s.endsWith("_BlockingProbability.csv") || s.endsWith("_QKDStatistics.csv")) return true;
        return false;
    }

    static File result(File dir, String metric) {
        return new File(dir, dir.getName() + "_" + metric + ".csv");
    }

    /**
     * Rewrites the "progress: x%" lines of each simulation as the overall
     * progress of the ladder step, so the launcher progress bar is monotone.
     */
    static final class ProgressFilter extends OutputStream {
        private static final Pattern P = Pattern.compile("^progress:\\s*([0-9.Ee+-]+)\\s*%\\s*$");
        private final PrintStream out;
        private final java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
        double base = 0, span = 1;

        ProgressFilter(PrintStream out) {
            this.out = out;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') flushLine();
            else if (b != '\r') line.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) write(b[i]);
        }

        private void flushLine() {
            String s = new String(line.toByteArray(), StandardCharsets.UTF_8);
            line.reset();
            Matcher m = P.matcher(s.trim());
            if (m.matches()) {
                double p = Double.parseDouble(m.group(1)) / 100.0;
                overall(base + span * Math.max(0, Math.min(1, p)));
                return;
            }
            out.println(s);
        }

        void overall(double p) {
            out.println(String.format(Locale.US, "progress: %.2f%%", 100 * Math.max(0, Math.min(1, p))));
        }

        void raw(String s) {
            out.println(s);
        }
    }
}
