package qkd.ladder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File helpers of the verification ladder: SNetS result CSV parsing, plain
 * CSV reading, text writing and small statistics.
 */
final class LadderIO {

    private LadderIO() {
    }

    // ------------------------------------------------------------------ text files

    static String read(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    static String readOrNull(File f) {
        try {
            return f.isFile() ? read(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void write(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        try (Writer w = new OutputStreamWriter(new java.io.FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(text);
        }
    }

    // ------------------------------------------------------------------ SNetS metric CSV

    /**
     * Parsed SNetS result file ({@code Metrics,LoadPoint,<dims>, ,rep0,...}).
     * Values are looked up by metric name, load point and the dimension values.
     */
    static final class Metrics {
        final List<String> dims = new ArrayList<String>();
        final Map<String, double[]> values = new LinkedHashMap<String, double[]>();
        final List<String[]> keys = new ArrayList<String[]>(); // {metric, lp, dim1, dim2...}

        double[] get(String metric, int lp, String... dimValues) {
            StringBuilder k = new StringBuilder(metric).append('|').append(lp);
            for (int i = 0; i < dims.size(); i++) k.append('|').append(i < dimValues.length ? dimValues[i] : "all");
            return values.get(k.toString());
        }

        /** Mean of the replications (NaN ignored); NaN when absent. */
        double mean(String metric, int lp, String... dimValues) {
            return LadderIO.mean(get(metric, lp, dimValues));
        }

        /** Every row of a metric: {lp, dims...} -> values. */
        List<Object[]> rows(String metric) {
            List<Object[]> out = new ArrayList<Object[]>();
            for (String[] k : keys) {
                if (!k[0].equals(metric)) continue;
                StringBuilder key = new StringBuilder(k[0]);
                for (int i = 1; i < k.length; i++) key.append('|').append(k[i]);
                out.add(new Object[] {k, values.get(key.toString())});
            }
            return out;
        }

        boolean has(String metric) {
            for (String[] k : keys) if (k[0].equals(metric)) return true;
            return false;
        }
    }

    static Metrics metrics(File f) throws IOException {
        if (f == null || !f.isFile()) return null;
        Metrics m = new Metrics();
        List<String> lines = lines(f);
        if (lines.isEmpty()) return m;
        String[] h = lines.get(0).split(",", -1);
        int lp = -1, blank = -1;
        for (int i = 0; i < h.length; i++) {
            if (lp < 0 && h[i].trim().equalsIgnoreCase("LoadPoint")) lp = i;
            else if (lp >= 0 && blank < 0 && h[i].trim().isEmpty()) blank = i;
        }
        if (lp < 0 || blank < 0) return m;
        for (int i = lp + 1; i < blank; i++) m.dims.add(h[i].trim());
        for (int li = 1; li < lines.size(); li++) {
            String line = lines.get(li);
            if (line.trim().isEmpty()) continue;
            String[] p = line.split(",", -1);
            if (p.length <= blank) continue;
            String[] key = new String[2 + m.dims.size()];
            key[0] = p[0].trim();
            key[1] = p[lp].trim();
            for (int i = 0; i < m.dims.size(); i++) key[2 + i] = p[lp + 1 + i].trim();
            List<Double> v = new ArrayList<Double>();
            for (int i = blank + 1; i < p.length; i++) {
                String s = p[i].trim();
                if (s.isEmpty()) continue;
                try {
                    v.add(Double.valueOf(s));
                } catch (NumberFormatException e) {
                    v.add(Double.NaN);
                }
            }
            double[] arr = new double[v.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = v.get(i);
            StringBuilder k = new StringBuilder(key[0]);
            for (int i = 1; i < key.length; i++) k.append('|').append(key[i]);
            if (!m.values.containsKey(k.toString())) m.keys.add(key);
            m.values.put(k.toString(), arr);
        }
        return m;
    }

    // ------------------------------------------------------------------ plain CSV

    static List<Map<String, String>> table(File f) throws IOException {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        if (f == null || !f.isFile()) return rows;
        List<String> lines = lines(f);
        if (lines.isEmpty()) return rows;
        String[] h = lines.get(0).split(",", -1);
        for (int li = 1; li < lines.size(); li++) {
            String line = lines.get(li);
            if (line.trim().isEmpty()) continue;
            String[] p = line.split(",", -1);
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < h.length && i < p.length; i++) m.put(h[i].trim(), p[i].trim());
            rows.add(m);
        }
        return rows;
    }

    static List<String> lines(File f) throws IOException {
        List<String> out = new ArrayList<String>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line);
        }
        return out;
    }

    static double num(Map<String, String> row, String col) {
        String v = row.get(col);
        if (v == null || v.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ statistics

    static double mean(double[] v) {
        if (v == null) return Double.NaN;
        double s = 0;
        int n = 0;
        for (double x : v) if (!Double.isNaN(x)) { s += x; n++; }
        return n == 0 ? Double.NaN : s / n;
    }

    static double mean(List<Double> v) {
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        return mean(a);
    }

    /**
     * {mean, sample std, 95 % half-width (Student t), n}.
     *
     * <p>Refer\u00eancias: intervalo de confian\u00e7a t de Student: [JAIN91], cap. 13.</p>
     */
    static double[] meanCi(double[] v) {
        List<Double> ok = new ArrayList<Double>();
        if (v != null) for (double x : v) if (!Double.isNaN(x)) ok.add(x);
        int n = ok.size();
        if (n == 0) return new double[] {Double.NaN, Double.NaN, Double.NaN, 0};
        double s = 0;
        for (double x : ok) s += x;
        double mean = s / n, ss = 0;
        for (double x : ok) ss += (x - mean) * (x - mean);
        double sd = n > 1 ? Math.sqrt(ss / (n - 1)) : 0;
        return new double[] {mean, sd, n > 1 ? tQuantile(n - 1) * sd / Math.sqrt(n) : 0, n};
    }

    static double[] meanCi(List<Double> v) {
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        return meanCi(a);
    }

    static double tQuantile(int df) {
        double[] t = {0, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179, 2.160,
                2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052,
                2.048, 2.045, 2.042};
        return df >= 1 && df < t.length ? t[df] : 1.96;
    }

    /**
     * Erlang-B blocking for c servers and offered load a (stable recursion).
     *
     * <p>Refer\u00eancias: Erlang-B: [JAIN91] R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991.</p>
     */
    static double erlangB(int c, double a) {
        double b = 1.0;
        for (int k = 1; k <= c; k++) b = a * b / (k + a * b);
        return b;
    }

    static String f(double v) {
        if (Double.isNaN(v)) return "";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format(Locale.US, "%.6g", v);
    }

    static String pct(double v) {
        return Double.isNaN(v) ? "\u2014" : String.format(Locale.US, "%.3f %%", 100 * v);
    }
}
