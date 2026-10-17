package launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Launcher settings, stored in launcher/launcher-settings.properties
 * (inside the launcher folder, never inside the simulator code).
 */
final class Settings {

    private final File launcherDir;
    private final File file;
    private final Properties props = new Properties();

    Settings(File launcherDir) {
        this.launcherDir = launcherDir;
        this.file = new File(launcherDir, "launcher-settings.properties");
        load();
    }

    static File detectLauncherDir() {
        try {
            File code = new File(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = code.isFile() ? code.getParentFile() : code;
            // when running from compiled classes (launcher/build/classes) climb to launcher/
            while (dir != null && !new File(dir, "web").isDirectory() && !code.isFile()) {
                dir = dir.getParentFile();
            }
            if (dir != null) {
                return dir.getAbsoluteFile();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // fall through
        }
        return new File(".").getAbsoluteFile();
    }

    private void load() {
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // defaults
            }
        }
    }

    synchronized void save() throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "SNetS Launcher settings");
        }
    }

    synchronized String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    synchronized void set(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            props.remove(key);
        } else {
            props.setProperty(key, value.trim());
        }
    }

    File launcherDir() {
        return launcherDir;
    }

    File projectRoot() {
        String p = get("projectRoot", null);
        if (p != null) {
            return new File(p).getAbsoluteFile();
        }
        File parent = launcherDir.getParentFile();
        if (parent != null && new File(parent, "simulations").isDirectory()) {
            return parent;
        }
        if (new File(launcherDir, "simulations").isDirectory()) {
            return launcherDir;
        }
        return new File(".").getAbsoluteFile();
    }

    File simulationsDir() {
        String p = get("simulationsDir", null);
        if (p != null) {
            return new File(p).getAbsoluteFile();
        }
        return new File(projectRoot(), "simulations");
    }

    /**
     * Folder of the verification outputs (QKD_ladder, QKD_reference): "verificacao" next to the
     * simulations folder, or the "verificationDir" setting. The browser sees it as "@verificacao".
     */
    File verificationDir() {
        String p = get("verificationDir", null);
        if (p != null && !p.trim().isEmpty()) {
            return new File(p).getAbsoluteFile();
        }
        File sims = simulationsDir();
        File parent = sims.getParentFile();
        return new File(parent == null ? projectRoot() : parent, "verificacao");
    }

    String javaCommand() {
        String def = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            File exe = new File(def + ".exe");
            if (exe.isFile()) {
                def = exe.getPath();
            }
        }
        return get("javaCommand", def);
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Open a console window for each execution (Windows only; default on). */
    boolean runConsole() {
        return isWindows() && !"false".equalsIgnoreCase(get("runConsole", "true"));
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("launcherDir", launcherDir.getAbsolutePath());
        m.put("projectRoot", projectRoot().getAbsolutePath());
        m.put("simulationsDir", simulationsDir().getAbsolutePath());
        m.put("verificationDir", verificationDir().getAbsolutePath());
        m.put("javaCommand", javaCommand());
        m.put("javacCommandSetting", get("javacCommand", ""));
        m.put("defaultJar", get("defaultJar", ""));
        m.put("jvmArgs", get("jvmArgs", ""));
        m.put("runConsole", runConsole());
        m.put("consoleSupported", isWindows());
        m.put("projectRootSetting", get("projectRoot", ""));
        m.put("simulationsDirSetting", get("simulationsDir", ""));
        m.put("javaCommandSetting", get("javaCommand", ""));
        return m;
    }
}
