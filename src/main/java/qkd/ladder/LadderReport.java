package qkd.ladder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of one ladder step: checks (OK / FALHA / AVISO / INFO), chart data and
 * the list of SNetS cases, written to {@code <step folder>}:
 * checks.csv, data.csv, charts.csv, cases.txt, report.txt and status.txt.
 */
final class LadderReport {

    static final String OK = "OK", FAIL = "FALHA", WARN = "AVISO", INFO = "INFO";

    final String step;
    final String title;
    final File dir;
    final File simulationsDir;
    final List<String[]> checks = new ArrayList<String[]>();
    final List<String[]> data = new ArrayList<String[]>();
    final Map<String, String[]> charts = new LinkedHashMap<String, String[]>();
    final List<String> cases = new ArrayList<String>();
    final StringBuilder notes = new StringBuilder();
    final long start = System.currentTimeMillis();

    LadderReport(String step, String title, File dir, File simulationsDir) {
        this.step = step;
        this.title = title;
        this.dir = dir;
        this.simulationsDir = simulationsDir;
    }

    /** Adds a check; returns the status for convenience. */
    String check(String id, String status, String what, String measured, String expected, String detail) {
        checks.add(new String[] {id, status, what, measured == null ? "" : measured, expected == null ? "" : expected, detail == null ? "" : detail});
        System.out.println("[" + status + "] " + what + (measured == null || measured.isEmpty() ? "" : " | medido: " + measured)
                + (expected == null || expected.isEmpty() ? "" : " | esperado: " + expected) + (detail == null || detail.isEmpty() ? "" : " | " + detail));
        return status;
    }

    String check(String id, boolean ok, String failStatus, String what, String measured, String expected, String detail) {
        return check(id, ok ? OK : failStatus, what, measured, expected, detail);
    }

    void chart(String id, String title, String xLabel, String yLabel, boolean logY) {
        charts.put(id, new String[] {id, title, xLabel, yLabel, String.valueOf(logY)});
    }

    void point(String chart, String series, double x, double y, double ci, double n) {
        data.add(new String[] {chart, series, LadderIO.f(x), LadderIO.f(y), LadderIO.f(ci), LadderIO.f(n)});
    }

    void point(String chart, String series, double x, double[] meanCi) {
        point(chart, series, x, meanCi[0], meanCi[2], meanCi[3]);
    }

    void addCase(File caseDir) {
        String rel = simulationsDir.toURI().relativize(caseDir.toURI()).getPath();
        if (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
        if (!cases.contains(rel)) cases.add(rel);
    }

    void note(String s) {
        notes.append(s).append('\n');
        System.out.println(s);
    }

    String status() {
        boolean warn = false;
        for (String[] c : checks) {
            if (FAIL.equals(c[1])) return FAIL;
            if (WARN.equals(c[1])) warn = true;
        }
        return warn ? WARN : OK;
    }

    void write(String profile, int ran, int reused) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        StringBuilder c = new StringBuilder("id,status,check,measured,expected,detail\n");
        for (String[] r : checks) c.append(csv(r)).append('\n');
        LadderIO.write(new File(dir, "checks.csv"), c.toString());
        StringBuilder d = new StringBuilder("chart,series,x,y,ci95,n\n");
        for (String[] r : data) d.append(csv(r)).append('\n');
        LadderIO.write(new File(dir, "data.csv"), d.toString());
        StringBuilder ch = new StringBuilder("chart,title,xlabel,ylabel,log\n");
        for (String[] r : charts.values()) ch.append(csv(r)).append('\n');
        LadderIO.write(new File(dir, "charts.csv"), ch.toString());
        StringBuilder cs = new StringBuilder();
        for (String s : cases) cs.append(s).append('\n');
        LadderIO.write(new File(dir, "cases.txt"), cs.toString());

        String status = status();
        long secs = (System.currentTimeMillis() - start) / 1000;
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        LadderIO.write(new File(dir, "status.txt"), status + "\n" + when + "\n" + profile + "\n" + secs + "\n" + ran + "\n" + reused + "\n");

        StringBuilder rep = new StringBuilder();
        rep.append("Plano de verifica\u00e7\u00e3o QKD \u2014 ").append(step).append(": ").append(title).append('\n');
        rep.append("Perfil: ").append(profile).append(" \u00b7 ").append(when).append(" \u00b7 ").append(secs).append(" s \u00b7 casos simulados: ")
                .append(ran).append(", reaproveitados: ").append(reused).append("\n\n");
        int[] n = new int[4];
        for (String[] r : checks) {
            int k = OK.equals(r[1]) ? 0 : FAIL.equals(r[1]) ? 1 : WARN.equals(r[1]) ? 2 : 3;
            n[k]++;
            rep.append(String.format("[%-5s] %s", r[1], r[2]));
            if (!r[3].isEmpty()) rep.append(" | medido: ").append(r[3]);
            if (!r[4].isEmpty()) rep.append(" | esperado: ").append(r[4]);
            if (!r[5].isEmpty()) rep.append("\n         ").append(r[5]);
            rep.append('\n');
        }
        rep.append('\n').append(n[0]).append(" OK, ").append(n[1]).append(" falha(s), ").append(n[2]).append(" aviso(s), ")
                .append(n[3]).append(" informativo(s)\n");
        if (notes.length() > 0) rep.append("\nObserva\u00e7\u00f5es:\n").append(notes);
        rep.append("\nCasos SNetS: ").append(cases.size()).append(" (abra-os no launcher para gr\u00e1ficos, mapas de calor e tabelas)\n");
        rep.append("\nStatus: ").append(status).append('\n');
        LadderIO.write(new File(dir, "report.txt"), rep.toString());
        System.out.println();
        System.out.println(rep);
        System.out.println("LADDER STEP " + step + ": " + ("FALHA".equals(status) ? "FAILED" : "PASSED") + " (" + status + ")");
    }

    private static String csv(String[] r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(',');
            String v = r[i] == null ? "" : r[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\"";
            sb.append(v);
        }
        return sb.toString();
    }
}
