package util;

import java.util.List;

/**
 * This class computes external fragmentation and relative fragmentation.
 * Articles:
 *     - (2012) Spectrum allocation policy modeling for elastic optical networks
 *     - (2023) Impairment- and fragmentation-aware, energy-efficient dynamic RMSCA for SDM-EONs
 *
 * @author Iallen
 */
public class ComputesFragmentation {

	/**
	 * This method calculates the external fragmentation
	 *
	 * @param freeSpectrumBands List<int[]> free spectrum band list
	 * @return double
	 */
    public double externalFragmentation(List<int[]> freeSpectrumBands) {
        int aux[] = {0, 0};
        double totalFree = 0.0;

        for (int[] faixa : freeSpectrumBands) {
            if (faixa[1] - faixa[0] > aux[1] - aux[0]) {
                aux = faixa;
            }
            totalFree = totalFree + (faixa[1] - faixa[0]);

        }
        double maior = aux[1] - aux[0];

        double fe = 1 - (maior / totalFree);

        if (totalFree == 0.0) fe = 0.0; // If the spectrum is completely filled it is not fragmented

        return fe;
    }

    /**
     * This method calculates the relative fragmentation
     *
     * @param freeSpectrumBands free spectrum band list
     * @param c                 Number of slots to allocate (relative value)
     * @return					double
     */
    public double relativeFragmentation(List<int[]> freeSpectrumBands, int c) {
        int freeC = 0;
        int totalFree = 0;

        for (int[] faixa : freeSpectrumBands) {
            int auxT = (faixa[1] - faixa[0] + 1);
            int auxF = auxT / c;
            freeC += auxF;
            totalFree += auxT;
        }
        double f_c = 1 - ((double) (c * freeC)) / ((double) totalFree);

        if (totalFree == 0) f_c = 0.0;

        return f_c;
    }

    /**
	 * This method calculates the entropy external fragmentation
	 *
	 * @param freeSpectrumBands List<int[]> free spectrum band list
	 * @return double
	 */
    public double entropyExternalFragmentation(List<int[]> freeSpectrumBands, int totalNumberOfSlots) {
        double sumFrag = 0.0;

        for (int[] faixa : freeSpectrumBands) {
            int tamFaixa = (faixa[1] - faixa[0] + 1);
            double logv = (double)totalNumberOfSlots / (double)tamFaixa;
            if (logv < 1.000001) { //Avoid negative or zero logs
            	logv = 1.000001;
			}
            sumFrag += ((double)tamFaixa / (double)totalNumberOfSlots) * Math.log(logv);
        }

        return sumFrag;
    }

}
