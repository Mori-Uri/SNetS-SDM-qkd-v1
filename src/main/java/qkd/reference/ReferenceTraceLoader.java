package qkd.reference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Loads the exact request traces exported by the verified Python reconstruction. */
public final class ReferenceTraceLoader {
    private ReferenceTraceLoader() {}

    public static List<ReferenceRequest> load(File file) throws IOException {
        return load(file, true);
    }

    public static List<ReferenceRequest> load(File file, boolean sortByRequestId) throws IOException {
        List<ReferenceRequest> requests = new ArrayList<ReferenceRequest>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String header = reader.readLine();
            if (header == null) throw new IOException("Empty trace: " + file);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 6) throw new IOException("Invalid trace row: " + line);
                int requestId = Integer.parseInt(p[0].trim());
                int source = Integer.parseInt(p[1].trim());
                int destination = Integer.parseInt(p[2].trim());
                double classicalRateGbps = Double.parseDouble(p[3].trim());
                int updatePeriodUs = Integer.parseInt(p[4].trim());
                double skrDemandMbps = Double.parseDouble(p[5].trim());
                List<Integer> expectedPath = p.length >= 7
                        ? parsePath(p[6].trim()) : null;
                requests.add(new ReferenceRequest(requestId, source, destination,
                        classicalRateGbps, updatePeriodUs, skrDemandMbps,
                        expectedPath));
            }
        } finally {
            reader.close();
        }
        if (sortByRequestId) {
            // Reconstruct original arrival order before repeating grooming.
            Collections.sort(requests, new Comparator<ReferenceRequest>() {
                @Override public int compare(ReferenceRequest a, ReferenceRequest b) {
                    return Integer.compare(a.requestId, b.requestId);
                }
            });
        }
        return requests;
    }

    private static List<Integer> parsePath(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] p = value.split("-");
        List<Integer> path = new ArrayList<Integer>();
        for (String s : p) path.add(Integer.valueOf(Integer.parseInt(s)));
        return path;
    }
}
