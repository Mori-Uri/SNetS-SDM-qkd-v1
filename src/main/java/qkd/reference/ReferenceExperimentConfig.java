package qkd.reference;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

/** Configuration of the Dibaj et al. multi-fiber reference reconstruction. */
public class ReferenceExperimentConfig {

    private int kPaths = 2;
    private int classicalChannelsPerFiber = 160;
    private int classicalFibersPerLink = 10;
    private int classicalGuardChannels = 0;
    private boolean reservePichChannel = true;
    private int quantumChannels = 960;
    private int tdmSlots = 256;
    private double groomingCapacityGbps = 800.0;
    private boolean useFixedReferencePaths = true;
    private boolean verifyClassicalRecomputation = true;
    private int[] qtndValuesKm = new int[] {5,10,15,20,25,30,35,40,45,50};
    private List<Replication> replications = new ArrayList<Replication>();
    private List<String> topologies = new ArrayList<String>();

    public static ReferenceExperimentConfig load(File file) throws IOException {
        FileReader reader = new FileReader(file);
        try {
            ReferenceExperimentConfig config =
                    new Gson().fromJson(reader, ReferenceExperimentConfig.class);
            config.validate();
            return config;
        } finally {
            reader.close();
        }
    }

    public void validate() {
        if (kPaths <= 0) throw new IllegalArgumentException("kPaths must be > 0");
        if (classicalChannelsPerFiber <= 0) throw new IllegalArgumentException("classicalChannelsPerFiber must be > 0");
        if (classicalFibersPerLink <= 0) throw new IllegalArgumentException("classicalFibersPerLink must be > 0");
        if (classicalGuardChannels < 0) throw new IllegalArgumentException("classicalGuardChannels must be >= 0");
        if (quantumChannels <= 0) throw new IllegalArgumentException("quantumChannels must be > 0");
        if (tdmSlots <= 0) throw new IllegalArgumentException("tdmSlots must be > 0");
        if (!(groomingCapacityGbps > 0.0)) throw new IllegalArgumentException("groomingCapacityGbps must be > 0");
        if (qtndValuesKm == null || qtndValuesKm.length == 0) throw new IllegalArgumentException("qtndValuesKm cannot be empty");
        if (replications == null || replications.isEmpty()) throw new IllegalArgumentException("replications cannot be empty");
        if (topologies == null || topologies.isEmpty()) throw new IllegalArgumentException("topologies cannot be empty");
    }

    public int getKPaths(){return kPaths;}
    public int getClassicalChannelsPerFiber(){return classicalChannelsPerFiber;}
    public int getClassicalFibersPerLink(){return classicalFibersPerLink;}
    public int getClassicalGuardChannels(){return classicalGuardChannels;}
    public boolean isReservePichChannel(){return reservePichChannel;}
    public int getQuantumChannels(){return quantumChannels;}
    public int getTdmSlots(){return tdmSlots;}
    public double getGroomingCapacityGbps(){return groomingCapacityGbps;}
    public boolean isUseFixedReferencePaths(){return useFixedReferencePaths;}
    public boolean isVerifyClassicalRecomputation(){return verifyClassicalRecomputation;}
    public int[] getQtndValuesKm(){return qtndValuesKm.clone();}
    public List<Replication> getReplications(){return new ArrayList<Replication>(replications);}
    public List<String> getTopologies(){return new ArrayList<String>(topologies);}

    public static class Replication {
        private int replication;
        private long seed;
        private String directory;
        public int getReplication(){return replication;}
        public long getSeed(){return seed;}
        public String getDirectory(){return directory;}
    }
}
