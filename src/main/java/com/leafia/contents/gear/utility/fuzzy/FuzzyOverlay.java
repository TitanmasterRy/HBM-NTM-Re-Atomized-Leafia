package com.leafia.contents.gear.utility.fuzzy;

/**
 * The geometry of the fuzzy identifier's readout: where the four fluid patches and the four
 * hazard pips sit on the item's face, and how the flicker is timed.
 *
 * <h2>Why this is not inside the renderer</h2>
 *
 * These are upstream's magic numbers, forty of them, written out longhand across a hundred lines
 * of {@code addVertexColorWithUV} calls — and they are the part of that renderer most able to be
 * wrong without anything looking broken. A rectangle a pixel off still draws.
 *
 * <p>They are pulled out here because <b>{@link FuzzyIdentifierRender} is client-only</b>, and a
 * GameTest runs on a dedicated server. A test that reached into the renderer for these would
 * have to load a class whose fields are {@code RenderType}s; this one holds no client type at
 * all, so {@code LeafiaFuzzyTests} can check the arithmetic on the server where the tests
 * actually run.
 *
 * <p>The numbers are grouped into tables rather than repeated, which is the port's own change:
 * upstream writes each patch's four UVs and eight vertices out in full, four times over, and
 * computes each pip's position with a {@code switch} of {@code x++}/{@code y--}. Same numbers,
 * counted once.
 */
public final class FuzzyOverlay {

	private FuzzyOverlay() {}

	/** A sixteenth of a block: upstream's {@code PIX}, one texture pixel. */
	public static final double PIX = 0.0625D;

	/** Upstream's {@code HALF_A_PIXEL}. */
	public static final double HALF_A_PIXEL = 0.03125D;

	/**
	 * The four fluid patches, {@code {x0, y0, x1, y1}} in texture pixels, bottom-left origin.
	 *
	 * <p>Every one of them lands on a fully transparent part of {@code fuzzy_identifier.png} —
	 * see {@code LeafiaFuzzyTests.overlayRectanglesLandOnTransparentTexels}, which reads the
	 * sprite and proves it. That is what makes the readout visible at all: the overlay is drawn
	 * inside the sprite's own slab of depth and shows through its gaps.
	 */
	public static final int[][] PATCHES = {
			{ 9, 14, 13, 15 },
			{ 5, 13, 13, 14 },
			{ 7, 12, 13, 13 },
			{ 8, 11, 10, 12 },
	};

	/** The millisecond offsets upstream gives those four, so they do not flicker in step. */
	public static final long[] PATCH_OFFSETS = { 0L, 15L, 30L, 45L };

	/**
	 * The four hazard pips, {@code {x, y}} in texture pixels, in upstream's own switch order:
	 * flammability, poison, reactivity, and "has a hazard symbol at all".
	 *
	 * <p>Upstream starts each case from a shared {@code x = 3, y = 10} and adjusts — case 0
	 * {@code x++}, case 1 {@code y--}, case 2 {@code x += 2; y--}, case 3 {@code x++; y -= 2}.
	 * The results of that arithmetic are what is written here.
	 */
	public static final int[][] PIPS = {
			{ 4, 10 },
			{ 3, 9 },
			{ 5, 9 },
			{ 4, 8 },
	};

	/** How long one flicker cycle lasts, in milliseconds. Upstream's {@code floorMod(..., 100)}. */
	public static final long FLICKER_PERIOD = 100L;

	/**
	 * Upstream's flicker: a sawtooth over {@link #FLICKER_PERIOD}, offset per patch.
	 *
	 * <p>{@code 1 - (t/200)^2 * 2} over {@code t} in {@code [0, 100)} runs from 1 down to 0.5 and
	 * snaps back, so the readout pulses ten times a second and never fades past half. The
	 * division is by <b>200</b> and the period is <b>100</b> — the patch uses only the first half
	 * of the curve, which is why it never reaches zero, and a "tidy-up" to matching numbers would
	 * make it blink instead of pulse.
	 */
	public static float getDisplayAlpha(long offset) {
		return 1 - (float) Math.pow(Math.floorMod(System.currentTimeMillis() - offset, FLICKER_PERIOD) / 200F, 2) * 2;
	}

	/**
	 * Upstream's {@code getInterpolatedU/V(n)}, on a standalone texture rather than an atlas
	 * sprite — see {@link FuzzyIdentifierRender}'s class doc.
	 */
	public static float uv(double texel) {
		return (float) (texel / 16D);
	}

	/**
	 * Upstream's own shear of the sampled coordinates: {@code n * 1.5 - 8}.
	 *
	 * <p>A patch does not sample the part of the fluid texture it covers, it samples a stretched
	 * and shifted part of it, which is what makes the readout look like a smear of the fluid
	 * rather than a swatch. Kept exactly, including the second patch's {@code -0.5}.
	 */
	public static float shearedUv(int texel) {
		return uv(texel * 1.5D - 8D);
	}
}
