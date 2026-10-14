package gprmcsa.reallocation.util;

import java.io.Serializable;

import network.Core;

/**
 * Equation of the Spectrum Compactness fragmentation measure extracted from the paper <Crosstalk-Aware Spectrum Defragmentation Based on Spectrum Compactness in Space Division Multiplexing Enabled Elastic Optical Networks With Multicore Fiber >
 * @author gustavo
 *
 */

public class SpectrumCompactness implements Serializable {

	private int indexSlotMaxOcup; // highest occupied slot index
	private int indexSlotMinOcup; // lowest occupied slot index
	private double amountSlotsOcup; // number of occupied slots
	private double amountSlotsFree; // number of free slots
	private int amountOfFreeFragments; // number of free-slot fragments (blocks)
	private double sc; // resultado do calculo do spectrum compactness


	/**
	 * Creates a new instance of SpectrumCompactness.
	 */
	public SpectrumCompactness(){
		this.indexSlotMaxOcup = -1;
		this.indexSlotMinOcup = 99999;
		this.amountOfFreeFragments = 0;
		this.amountSlotsFree = 0;
		this.amountSlotsOcup = 0;
	}

	/**
	 * calculates the spectrum compactness of a core
	 * returns -1 if the core has no occupancy
	 * @param c
	 * @return
	 */
	public double compute(Core c) {
		// the calculation is performed only if the core has some occupancy
		if (!isCoreOccupied(c)) {
			return -1;
		}
		// to be safe, reset all variables

		resetAttributes();

		// resetting the spectrum compactness attribute value
		this.sc = 0;

		// numero de blocos de slots livres - fragmentos
		this.amountOfFreeFragments = c.getSpectrum().getFreeSpectrumBands().size();

		// somatorio de slots livres
		for (int[] freeBand : c.getSpectrum().getFreeSpectrumBands()) {
			this.amountSlotsFree = this.amountSlotsFree + (freeBand[1] - freeBand[0] + 1);
		}

		// somatorio de slots ocupados
		this.amountSlotsOcup = c.getSpectrum().getNumOfSlots() - this.amountSlotsFree;

		// lowest occupied slot index
		int indexSlotMinFree = c.getSpectrum().getFreeSpectrumBands().get(0)[0];
		if (indexSlotMinFree == 1) {
			this.indexSlotMinOcup = c.getSpectrum().getFreeSpectrumBands().get(0)[1] + 1;
		}else {
			this.indexSlotMinOcup = 1;
		}


		// highest occupied slot index
		int sizeList = c.getSpectrum().getFreeSpectrumBands().size();
		int indexSlotMaxFree = c.getSpectrum().getFreeSpectrumBands().get(sizeList-1)[1];
		if (indexSlotMaxFree == c.getNumOfSlots()) {
			this.indexSlotMaxOcup = c.getSpectrum().getFreeSpectrumBands().get(sizeList-1)[0] - 1;
		}else {
			this.indexSlotMaxOcup = c.getNumOfSlots();
		}



		sc = ((indexSlotMaxOcup - indexSlotMinOcup +1)/amountSlotsOcup)*(amountSlotsFree/amountOfFreeFragments);

		return sc;
	}

	/**
	 * Resets the attributes.
	 */
	public void resetAttributes() {
		// TODO Auto-generated method stub
		this.indexSlotMaxOcup = -1;
		this.indexSlotMinOcup = 99999;
		this.amountOfFreeFragments = 0;
		this.amountSlotsFree = 0;
		this.amountSlotsOcup = 0;
	}

	/**
	 * checks whether the core has occupied slots
	 * @param c
	 * @return
	 */
	private boolean isCoreOccupied(Core c) {
		// TODO Auto-generated method stub
		if (c.getUsedSlots() > 0) {
			return true;
		}

		return false;

	}


}
