package launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the list of valid algorithm names for the "simulation" file by
 * reading the simulator's own source code (read-only). Nothing in the
 * simulator needs to change: when a new constant such as
 * {@code POWER_ASSIGNMENT_XYZ = "xyz"} is added to GPRMCSA.java, it shows up
 * in the launcher automatically.
 */
final class Catalog {

    /** Constant-name prefix -> key of the "simulation" file. Longest prefixes first. */
    private static final String[][] PREFIXES = {
        {"GROOMING_OPT_", "grooming"},
        {"ROUTING_K_", "kRouting"},
        {"ROUTING_", "routing"},
        {"SPECTRUM_ASSIGNMENT_", "spectrumAssignment"},
        {"INTEGRATED_", "integratedRmlsa"},
        {"ALL_ASSIGNMENT_OF_REGENERATOR", "regeneratorAssignment"},
        {"MODULATION_", "modulationSelection"},
        {"CORE_SPECTRUM_ASSIGNMENT_", "coreAndSpectrumAssignment"},
        {"CORE_SPECTRUM_", "coreAndSpectrumAssignment"},
        {"REALLOCATION_", "reallocation"},
        {"POWER_ASSIGNMENT_", "powerAssignment"},
        {"GBS_", "guardBandSelection"},
    };

    private static final Pattern STRING_CONST = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z0-9_]+)\\s*=\\s*\"([^\"]*)\"\\s*;(.*)");
    private static final Pattern METRIC_FIELD = Pattern.compile(
            "public\\s+boolean\\s+([A-Za-z0-9_]+)\\s*;");

    private Catalog() {
    }

    static Map<String, Object> build(File projectRoot) {
        Map<String, Set<String>> options = new LinkedHashMap<String, Set<String>>();
        Map<String, String> notes = new LinkedHashMap<String, String>();
        List<String> sources = new ArrayList<String>();
        List<String> metrics = new ArrayList<String>();

        File javaRoot = new File(projectRoot, "src" + File.separator + "main" + File.separator + "java");
        File gprmcsa = new File(javaRoot, "gprmcsa" + File.separator + "GPRMCSA.java");
        List<File> files = new ArrayList<File>();
        if (gprmcsa.isFile()) {
            files.add(gprmcsa);
        }
        collectFactories(javaRoot, files, 0);

        for (File f : files) {
            try {
                scanConstants(f, options, notes);
                sources.add(relative(projectRoot, f));
            } catch (IOException ignored) {
                // unreadable file: skip, the UI falls back to free text
            }
        }

        File simConfig = new File(javaRoot, "simulationControl" + File.separator + "parsers"
                + File.separator + "SimulationConfig.java");
        if (simConfig.isFile()) {
            try {
                String text = new String(Files.readAllBytes(simConfig.toPath()), StandardCharsets.UTF_8);
                int idx = text.indexOf("class Metrics");
                if (idx >= 0) {
                    int end = text.indexOf('}', idx);
                    Matcher m = METRIC_FIELD.matcher(text.substring(idx, end < 0 ? text.length() : end));
                    while (m.find()) {
                        metrics.add(m.group(1));
                    }
                }
                sources.add(relative(projectRoot, simConfig));
            } catch (IOException ignored) {
                // ignore
            }
        }

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> opts = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Set<String>> e : options.entrySet()) {
            opts.put(e.getKey(), new ArrayList<String>(e.getValue()));
        }
        out.put("options", opts);
        out.put("notes", notes);
        out.put("metrics", metrics);
        out.put("sources", sources);
        out.put("sourceAvailable", !sources.isEmpty());
        return out;
    }

    private static void collectFactories(File dir, List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 8) {
            return;
        }
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                collectFactories(f, out, depth + 1);
            } else if (f.getName().endsWith("AlgorithmFactory.java")) {
                out.add(f);
            }
        }
    }

    private static void scanConstants(File f, Map<String, Set<String>> options, Map<String, String> notes)
            throws IOException {
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            Matcher m = STRING_CONST.matcher(line);
            if (!m.find()) {
                continue;
            }
            String name = m.group(1);
            String value = m.group(2);
            String rest = m.group(3);
            String key = null;
            for (String[] p : PREFIXES) {
                if (name.startsWith(p[0])) {
                    key = p[1];
                    break;
                }
            }
            if (key == null || value.isEmpty()) {
                continue;
            }
            Set<String> set = options.get(key);
            if (set == null) {
                set = new LinkedHashSet<String>();
                options.put(key, set);
            }
            set.add(value);
            int c = rest.indexOf("//");
            if (c >= 0) {
                String comment = rest.substring(c + 2).trim();
                if (!comment.isEmpty()) {
                    notes.put(key + ":" + value, comment);
                }
            }
        }
    }

    private static String relative(File root, File f) {
        String r = root.getAbsoluteFile().toURI().relativize(f.getAbsoluteFile().toURI()).getPath();
        return r;
    }
}
