package com.leafia.init.hazards.types;

import com.leafia.config.LeafiaFeatures;

public final class HazardTypeHelper {

	private HazardTypeHelper() {}

	/**
	 * Upstream's {@code enableHealthMod}, which here is {@code features.radiationClassification}:
	 * the classified types take their health-mod factor, or the plain {@code 1.0} when the system
	 * is off. Reached only by an entry registered while it was on and read after it was turned
	 * off, since {@code ItemRads} registers nothing otherwise - see D-1002.
	 */
	public static double accountConfig(double value) {
		return LeafiaFeatures.radiationClassification ? value : 1.0;
	}
}
