package launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Console window of one execution (Windows). Started by the launcher with
 * {@code cmd /c start "title" java -cp <launcher> launcher.RunConsole <port> <runId>}.
 *
 * <p>Prints the simulator output and the progress of the run. Every request is
 * also a heartbeat: when this window is closed the heartbeats stop and the
 * launcher cancels the run. Closing the browser does not affect the run.</p>
 */
public final class RunConsole {

    private RunConsole() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("uso: RunConsole <porta> <id da execucao>");
            return;
        }
        String base = "http://127.0.0.1:" + Integer.parseInt(args[0]) + "/api/run/console?id=" + args[1];
        System.out.println("=====================================================================");
        System.out.println(" SNetS Launcher - execucao " + args[1]);
        System.out.println(" Fechar esta janela CANCELA a execucao.");
        System.out.println(" Fechar o navegador NAO interrompe a execucao.");
        System.out.println("=====================================================================");
        long next = 0;
        int failures = 0;
        String lastProgress = "";
        while (true) {
            List<String> body;
            try {
                body = get(base + "&from=" + next);
                failures = 0;
            } catch (Exception e) {
                if (++failures >= 10) {
                    System.out.println();
                    System.out.println("Sem contato com o launcher (a janela principal foi fechada?).");
                    pause(15);
                    return;
                }
                Thread.sleep(1000);
                continue;
            }
            if (body.isEmpty()) {
                Thread.sleep(1000);
                continue;
            }
            // first line: state <TAB> progress(0..1) <TAB> next <TAB> elapsed ms <TAB> label
            String[] h = body.get(0).split("\t", -1);
            String state = h[0];
            double progress = h.length > 1 ? parse(h[1]) : 0;
            next = h.length > 2 ? (long) parse(h[2]) : next;
            long elapsed = h.length > 3 ? (long) parse(h[3]) : 0;
            for (int i = 1; i < body.size(); i++) {
                System.out.println(body.get(i));
            }
            String p = String.format(java.util.Locale.ROOT, "%.1f", progress * 100);
            if (!p.equals(lastProgress) && body.size() == 1 && "running".equals(state)) {
                System.out.println("[" + bar(progress) + "] " + p + " %   tempo " + clock(elapsed));
                lastProgress = p;
            }
            if (!"running".equals(state) && !"queued".equals(state)) {
                System.out.println();
                System.out.println("[" + bar(progress) + "] " + p + " %   tempo " + clock(elapsed));
                System.out.println("Execucao " + label(state) + ".");
                pause(30);
                return;
            }
            Thread.sleep(1000);
        }
    }

    private static String label(String state) {
        if ("finished".equals(state)) return "concluida";
        if ("failed".equals(state)) return "terminou com erro";
        if ("cancelled".equals(state)) return "cancelada";
        return state;
    }

    private static void pause(int seconds) throws InterruptedException {
        System.out.println("Esta janela fecha sozinha em " + seconds + " s.");
        Thread.sleep(seconds * 1000L);
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String bar(double p) {
        int n = (int) Math.round(Math.max(0, Math.min(1, p)) * 30);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append(i < n ? '#' : '.');
        return sb.toString();
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static List<String> get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        List<String> out = new ArrayList<String>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        }
        return out;
    }
}
