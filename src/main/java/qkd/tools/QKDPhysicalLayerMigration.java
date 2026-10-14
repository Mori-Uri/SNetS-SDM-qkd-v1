package qkd.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Part 26: moves the QKD parameters of older cases from {@code physicalLayer}
 * (blocks {@code qkdPhysical} and {@code qkdReceiver}) to the case file
 * {@code qkdPhysicalLayer}. The values do not change, so the results do not change.
 *
 * <pre>
 * java -cp SNetS-SDM-QKD.jar qkd.tools.QKDPhysicalLayerMigration &lt;case or folder&gt; [--dry-run]
 * </pre>
 *
 * A copy of the original file is kept as {@code physicalLayer.before_p26}. In cases with QKD
 * disabled the blocks (defaults written by older versions) are only removed.
 * Cases that already have {@code qkdPhysicalLayer} are left untouched.
 *
 * <p>Refer\u00eancias: [MORI26] (Parte 26: arquivo qkdPhysicalLayer).</p>
 */
public final class QKDPhysicalLayerMigration {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static int migrated, skipped;

    private QKDPhysicalLayerMigration() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: QKDPhysicalLayerMigration <case or folder> [--dry-run]");
            return;
        }
        boolean dry = args.length > 1 && "--dry-run".equals(args[1]);
        walk(new File(args[0]), dry, 0);
        System.out.println("migrated=" + migrated + " skipped=" + skipped + (dry ? " (dry run)" : ""));
    }

    private static void walk(File dir, boolean dry, int depth) throws IOException {
        if (!dir.isDirectory() || depth > 8) return;
        File phy = new File(dir, "physicalLayer");
        if (phy.isFile()) migrate(dir, dry);
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) if (k.isDirectory() && !k.getName().startsWith(".")) walk(k, dry, depth + 1);
    }

    /** Migrates one case folder; returns true when something was written. */
    public static boolean migrate(File caseDir, boolean dry) throws IOException {
        File phy = new File(caseDir, "physicalLayer");
        File target = new File(caseDir, "qkdPhysicalLayer");
        String text = new String(Files.readAllBytes(phy.toPath()), StandardCharsets.UTF_8);
        JsonElement el;
        try {
            el = new JsonParser().parse(text);
        } catch (RuntimeException e) {
            skipped++;
            return false;
        }
        if (!el.isJsonObject()) { skipped++; return false; }
        JsonObject root = el.getAsJsonObject();
        boolean has = root.has("qkdPhysical") || root.has("qkdReceiver");
        if (!has || target.isFile()) { skipped++; return false; }
        JsonObject out = split(root);
        boolean qkd = qkdEnabled(caseDir);
        System.out.println((dry ? "[dry] " : "") + (qkd ? "migrating " : "removing unused QKD blocks (QKD disabled) ") + caseDir.getPath());
        if (!dry) {
            Files.write(new File(caseDir, "physicalLayer.before_p26").toPath(), text.getBytes(StandardCharsets.UTF_8));
            if (qkd) Files.write(target.toPath(), PRETTY.toJson(out).getBytes(StandardCharsets.UTF_8));
            Files.write(phy.toPath(), PRETTY.toJson(root).getBytes(StandardCharsets.UTF_8));
        }
        migrated++;
        return true;
    }

    /** True when the network file of the case has qkd.enabled = true. */
    static boolean qkdEnabled(File caseDir) {
        try {
            File net = new File(caseDir, "network");
            if (!net.isFile()) return false;
            JsonElement el = new JsonParser().parse(new String(Files.readAllBytes(net.toPath()), StandardCharsets.UTF_8));
            if (!el.isJsonObject() || !el.getAsJsonObject().has("qkd")) return false;
            JsonElement q = el.getAsJsonObject().get("qkd");
            return q.isJsonObject() && q.getAsJsonObject().has("enabled") && q.getAsJsonObject().get("enabled").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes the legacy blocks from {@code physicalLayer} (in place) and returns
     * the grouped {@code qkdPhysicalLayer} object.
     */
    public static JsonObject split(JsonObject physicalLayer) {
        JsonObject q = physicalLayer.has("qkdPhysical") && physicalLayer.get("qkdPhysical").isJsonObject()
                ? physicalLayer.getAsJsonObject("qkdPhysical") : new JsonObject();
        JsonObject r = physicalLayer.has("qkdReceiver") && physicalLayer.get("qkdReceiver").isJsonObject()
                ? physicalLayer.getAsJsonObject("qkdReceiver") : new JsonObject();
        physicalLayer.remove("qkdPhysical");
        physicalLayer.remove("qkdReceiver");
        JsonObject out = new JsonObject();
        out.add("source", pick(q, "meanPhotonNumber", "pulseIntervalPs", "qchPowerDbm", "qchBandwidthGHz"));
        out.add("protocol", pick(q, "errorCorrectionInefficiency", "misalignmentError"));
        JsonObject det = pick(q, "detectorEfficiency", "darkCountProbability");
        for (Map.Entry<String, JsonElement> e : pick(r, "detectorGateWindowPs", "receiverOpticalEfficiency").entrySet())
            det.add(e.getKey(), e.getValue());
        out.add("detector", det);
        out.add("fiber", pick(q, "attenuationFloorDbPerKm", "attenuationQuadraticDbPerKmNm2", "attenuationCenterNm"));
        out.add("crosstalk", pick(r, "intercoreCrosstalkEnabled", "receiverTransferModel"));
        return out;
    }

    private static JsonObject pick(JsonObject from, String... keys) {
        JsonObject o = new JsonObject();
        for (String k : keys) if (from.has(k)) o.add(k, from.get(k));
        return o;
    }
}
