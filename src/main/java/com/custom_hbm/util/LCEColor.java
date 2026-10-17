package com.custom_hbm.util;

/**
 * The one piece of colour arithmetic {@code LCEBeamPronter} needs, kept out of that class because
 * the pronter is client-only and a dedicated server cannot load it - which would leave the
 * arithmetic untestable.
 *
 * <p>Upstream calls NTM-CE's {@code BobMathUtil.interpolateColor(from, to, t)}. That file is not
 * in the reference clone, so this is the standard per-channel linear blend and its rounding is an
 * assumption (D-3008): channels truncate toward zero, and alpha is opaque.
 */
public final class LCEColor {

	private LCEColor() { }

	/**
	 * The colour of shell {@code j} (1 the thinnest) of a beam of {@code layers}: a one-layer beam is simply
	 * its outer colour, otherwise the shells blend from the inner colour at the first to the outer at the
	 * last. Always opaque - callers pass {@code 0xRRGGBB}, and a vertex colour with an alpha byte of 0 is
	 * invisible under the beam's {@code SRC_ALPHA, ONE} blend (DFC-4, D-3017: the one-layer branch handed
	 * the bare colour through and no beam had used it until the DFC's jammer and the sparks).
	 */
	public static int layerColor(int innerColor, int outerColor, int layers, int j) {
		if(layers == 1) return 0xFF000000 | outerColor;
		return interpolateColor(innerColor, outerColor, (float) (j - 1) / (layers - 1));
	}

	/** {@code t = 0} gives {@code from}, {@code t = 1} gives {@code to}, linear in between. */
	public static int interpolateColor(int from, int to, float t) {
		int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
		int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
		int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
