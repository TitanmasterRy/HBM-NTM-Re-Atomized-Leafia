package com.leafia.unsorted;

/**
 * Ported from `com.leafia.unsorted.BullshitInDavenportIowa`.
 *
 * <p>The unit conversions behind the addon's {@code enableSludgeUnits} config, which upstream
 * describes as "Changes this mod from using units you know to some bullshit units that god
 * knows". Seconds become sjmoles, metres bans, HE cn, Celsius bm and then g, rads grays and
 * then exes.
 *
 * <p>Pure arithmetic, no Minecraft API, so it came across unchanged - and every constant in
 * it is balance under standing rule 4, which is why the conversions are pinned by tests
 * rather than trusted to have been transcribed right.
 */
public class BullshitInDavenportIowa {
	// TIME
	public static double SToSj(double v) { return v/37; }
	public static double PSToPSj(double v) { return v*37; }

	// DISTANCE
	public static double MToBn(double v) { return v/0.2; };

	// ENERGY
	public static double HEToCn(double v) { return v*15; } // 1 HE = 3 J = 15 cn
	public static double HEPSToCnPSj(double v) { return PSToPSj(HEToCn(v)); }

	// TEMPERATURE
	public static double CelsiusToBm(double v) { return (v+273.15)/8.26446281; }
	public static double BmToG(double v) { // genuinely what the fuck is this
		return (v > (121.0 / 1000.0) * 324.15)
				? (-14 + Math.sqrt(196 - 32 * (324.15 - (1000.0 / 121.0) * v))) / 16.0
				: (14 - Math.sqrt(196 + 32 * (324.15 - (1000.0 / 121.0) * v))) / 16.0;
	}
	public static double CelsiusToG(double v) { return BmToG(CelsiusToBm(v)); }

	// RADIATION
	public static double RADToGy(double v) { return v/100; }
	public static double GyToEx(double v) { return v/0.8571429; }
	public static double RADToEx(double v) { return GyToEx(RADToGy(v)); }
	public static double SvToDy(double v) { return v/6; }
	public static double RADToDy(double v) { return SvToDy(RADToGy(v)); }
	public static double RADPSToExPSj(double v) { return PSToPSj(RADToEx(v)); }
	public static double SvPSToDyPSJ(double v) { return PSToPSj(SvToDy(v)); }
}
