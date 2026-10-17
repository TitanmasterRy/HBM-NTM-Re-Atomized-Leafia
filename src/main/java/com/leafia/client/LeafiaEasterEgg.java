package com.leafia.client;

import java.util.Random;

/**
 * The title screen's one-in-512 surprise: the state, with nothing in it that needs a client (D-3026).
 *
 * <p>Upstream keeps it in {@code LeafiaClientListener.HandlerClient} ({@code seenEasterEgg}, {@code actuallySeenEasterEgg}) and rolls it
 * in {@code onGuiInit}. The screen is {@code com.custom_hbm.GuiEasterEgg}, the roll {@link LeafiaEasterEggClient#onTitleScreen}. The flag is
 * read by {@code LeafiaGeneralLocal.injectWackySplashes}, which on seeing it throws away every other splash.
 */
public final class LeafiaEasterEgg {

	private LeafiaEasterEgg() {}

	/** One title screen in this many shows it, the first time only. */
	public static final int ODDS = 512;

	/** The title screen has been shown once this session, whether or not it rolled the screen. */
	public static boolean seenEasterEgg = false;
	/** The screen was shown this session. */
	public static boolean actuallySeenEasterEgg = false;

	/** Upstream's `new Random().nextInt(512) == 0`. */
	public static boolean roll(Random random) {
		return random.nextInt(ODDS) == 0;
	}
}
