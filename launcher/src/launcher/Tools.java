package launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * QKD verification and article-base reference tools exposed by the
 * simulator jar. Only these known entry points can be started from the
 * interface (no arbitrary class names are accepted from the browser).
 */
final class Tools {

    static final String DIBAJ_REL = "QKD_reference/Dibaj_MF";
    static final String NATIVE_REL = "QKD_reference/Dibaj_MF_SNetS";
    static final String LADDER_REL = "QKD_ladder";
    static final java.util.Set<String> LADDER_STEPS = new java.util.HashSet<String>(Arrays.asList(
            "A1", "A2", "A3", "B1", "B2", "B2L", "B3", "C1", "C2", "D1", "D2", "E1", "E2", "E3", "E4",
            "F1", "F2", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8", "M1", "M2", "M3", "Q1", "Q2", "Q3"));

    private final Settings settings;

    Tools(Settings settings) {
        this.settings = settings;
    }

    /** Part 27: the reference lives in the verification folder; old projects still have it under simulations. */
    private boolean refInSimulations() {
        File n = new File(settings.verificationDir(), DIBAJ_REL.replace('/', File.separatorChar));
        File o = new File(settings.simulationsDir(), DIBAJ_REL.replace('/', File.separatorChar));
        return !new File(n, "config.json").isFile() && new File(o, "config.json").isFile();
    }

    File dibajRoot() {
        return new File(refInSimulations() ? settings.simulationsDir() : settings.verificationDir(), DIBAJ_REL.replace('/', File.separatorChar));
    }

    File nativeRoot() {
        return new File(refInSimulations() ? settings.simulationsDir() : settings.verificationDir(), NATIVE_REL.replace('/', File.separatorChar));
    }

    /** Paths as the browser sees them ("@verificacao/..." inside the verification folder). */
    private String relOf(String rel) {
        return refInSimulations() ? rel : Workspace.VERIF + "/" + rel;
    }

    /** Verification classes contained in the jar, in natural order. */
    static List<String> verifiers(File jar) {
        List<String> out = new ArrayList<String>();
        if (jar == null || !jar.isFile()) {
            return out;
        }
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> e = zip.entries();
            while (e.hasMoreElements()) {
                String n = e.nextElement().getName();
                if (n.startsWith("qkd/verification/") && n.endsWith("Verification.class") && !n.contains("$")) {
                    out.add(n.substring("qkd/verification/".length(), n.length() - ".class".length()));
                }
            }
        } catch (IOException ignored) {
            // not a readable jar
        }
        Collections.sort(out);
        return out;
    }

    static boolean hasClass(File jar, String className) {
        if (jar == null || !jar.isFile()) {
            return false;
        }
        try (ZipFile zip = new ZipFile(jar)) {
            return zip.getEntry(className.replace('.', '/') + ".class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    Map<String, Object> describe(File jar) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("jar", jar == null ? null : jar.getAbsolutePath());
        m.put("verifiers", verifiers(jar));
        m.put("hasPhysical", hasClass(jar, "qkd.reference.QKDReferencePhysicalMain"));
        m.put("hasTraces", hasClass(jar, "qkd.reference.QKDReferenceArticleMain"));
        m.put("hasNative", hasClass(jar, "qkd.reference.QKDNativeReferenceMain"));
        m.put("dibajRoot", dibajRoot().getAbsolutePath());
        m.put("dibajExists", new File(dibajRoot(), "config.json").isFile());
        m.put("dibajRel", relOf(DIBAJ_REL));
        m.put("nativeRel", relOf(NATIVE_REL));
        m.put("verificationDir", settings.verificationDir().getAbsolutePath());
        m.put("nativeExists", nativeRoot().isDirectory());
        m.put("hasLadder", hasClass(jar, "qkd.ladder.QKDLadderMain"));
        m.put("ladderRel", Workspace.VERIF + "/" + LADDER_REL);
        return m;
    }

    /** Returns {label, mainClass, args...} for a tool id, or throws. */
    List<String> resolve(String id, File jar) {
        if (id == null) {
            throw new IllegalArgumentException("ferramenta não informada");
        }
        String dibaj = dibajRoot().getAbsolutePath();
        String nat = nativeRoot().getAbsolutePath();
        if (id.startsWith("verify:")) {
            String name = id.substring("verify:".length());
            if (!verifiers(jar).contains(name)) {
                throw new IllegalArgumentException("Verificador desconhecido neste JAR: " + name);
            }
            List<String> l = new ArrayList<String>(Arrays.asList(name, "qkd.verification." + name));
            if ("QKDNativeArticleBaseVerification".equals(name)) {
                l.add(dibaj);
                l.add(new File(System.getProperty("java.io.tmpdir"), "snets_native_article_verification").getAbsolutePath());
            }
            return l;
        }
        if (id.startsWith("ladder:")) {
            // ladder:<step>:<profile>[:<threads>][:force]
            String[] p = id.split(":");
            String step = p.length > 1 ? p[1].toUpperCase(java.util.Locale.ROOT) : "";
            String profile = p.length > 2 ? p[2] : "rapido";
            if (!LADDER_STEPS.contains(step)) throw new IllegalArgumentException("Passo desconhecido: " + step);
            if (!"rapido".equals(profile) && !"completo".equals(profile)) throw new IllegalArgumentException("Perfil desconhecido: " + profile);
            if (!hasClass(jar, "qkd.ladder.QKDLadderMain")) throw new IllegalArgumentException("Este JAR não contém o plano de verificação (qkd.ladder.QKDLadderMain)");
            List<String> l = new ArrayList<String>(Arrays.asList("Plano de verificação " + step + " (" + profile + ")",
                    "qkd.ladder.QKDLadderMain", step, settings.simulationsDir().getAbsolutePath(), "--profile", profile,
                    "--out", settings.verificationDir().getAbsolutePath()));
            for (int i = 3; i < p.length; i++) {
                if ("force".equals(p[i])) l.add("--force");
                else if (p[i].matches("\\d{1,3}") && Integer.parseInt(p[i]) > 0) { l.add("--threads"); l.add(p[i]); }
            }
            return l;
        }
        switch (id) {
            case "ref-physical":
                return new ArrayList<String>(Arrays.asList("Artigo-base: camada física",
                        "qkd.reference.QKDReferencePhysicalMain", new File(dibaj, "physical_results").getAbsolutePath()));
            case "ref-traces":
                return new ArrayList<String>(Arrays.asList("Artigo-base: rede (traces Python)",
                        "qkd.reference.QKDReferenceArticleMain", dibaj, new File(dibaj, "snets_results").getAbsolutePath()));
            case "ref-native-prepare":
            case "ref-native-run":
            case "ref-native-analyze":
            case "ref-native-all": {
                String mode = id.substring("ref-native-".length());
                return new ArrayList<String>(Arrays.asList("Artigo-base no SNetS (" + mode + ")",
                        "qkd.reference.QKDNativeReferenceMain", mode, dibaj, nat));
            }
            default:
                throw new IllegalArgumentException("Ferramenta desconhecida: " + id);
        }
    }
}
