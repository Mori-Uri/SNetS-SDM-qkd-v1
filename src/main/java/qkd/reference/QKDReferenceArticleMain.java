package qkd.reference;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Command-line entry point for the article-base verification addon.
 *
 * <p>Refer\u00eancias: [DIBAJ24].</p>
 */
public final class QKDReferenceArticleMain {
    private QKDReferenceArticleMain() {}

    public static void main(String[] args) throws Exception {
        if(args.length<1 || args.length>2) {
            System.out.println("Usage: java -cp <jar> qkd.reference.QKDReferenceArticleMain <reference-scenario-root> [output-dir]");
            System.exit(2);
        }
        File root=new File(args[0]);
        File out=args.length==2?new File(args[1]):new File(root,"snets_results");
        if(!out.exists()&&!out.mkdirs())throw new IllegalStateException("Cannot create "+out);

        long start=System.currentTimeMillis();
        ReferenceMultifiberExperiment experiment=new ReferenceMultifiberExperiment(root);
        List<ReferenceResultRow> rows=experiment.run();
        File raw=new File(out,"snets_reference_raw.csv");
        File stats=new File(out,"snets_reference_statistics.csv");
        experiment.writeRaw(raw,rows);experiment.writeStatistics(stats,rows);

        File expected=new File(new File(root,"reference_python"),"replications_raw.csv");
        ReferenceMultifiberExperiment.VerificationSummary verification=null;
        if(expected.isFile())verification=experiment.compareWithPython(expected,rows);

        File report=new File(out,"verification_report.txt");
        BufferedWriter w=new BufferedWriter(new FileWriter(report));
        try {
            w.write("SNetS-QKD addon - Dibaj MF reference verification\n");
            w.write("Rows: "+rows.size()+"\n");
            if(verification!=null){w.write(verification.toString()+"\n");w.write("Exact reproduction: "+verification.isExact()+"\n");}
            w.write("Elapsed seconds: "+((System.currentTimeMillis()-start)/1000.0)+"\n");
        } finally {w.close();}

        System.out.println("SNetS-QKD article-base reference run finished");
        System.out.println("raw="+raw.getAbsolutePath());
        System.out.println("statistics="+stats.getAbsolutePath());
        if(verification!=null)System.out.println(verification.toString());
        System.out.println("report="+report.getAbsolutePath());
    }
}
