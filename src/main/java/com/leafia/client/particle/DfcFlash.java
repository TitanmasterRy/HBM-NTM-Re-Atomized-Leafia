package com.leafia.client.particle;

/**
 * The white-out a DFC blast puts over the screen, as a countdown the blast's shockwave starts.
 *
 * <p>Upstream keeps this as {@code HandlerClient.dfcFlashTicks}, a static int on an inner class of
 * {@code LeafiaClientListener}, which that class draws as a fading full-screen quad and which
 * {@code LeafiaPassiveLocal} counts down once a tick. {@code ParticleNuke} is its only writer: the
 * first frame its sphere reaches the player's eyes, it sets the counter to
 * {@code 100 + flashTime}.
 *
 * <p>The overlay and the countdown are not ported yet — they belong to the listener — so this is
 * only the counter, kept where {@code ParticleNuke} can write it without that class being ported
 * first. When the listener lands it should read and decrement this field rather than declare its
 * own, or move the field and update the one writer.
 */
public final class DfcFlash {

	private DfcFlash() {}

	/** Upstream's {@code 100}: the ticks the flash holds before it starts to fade. */
	public static final int HOLD_TICKS = 100;

	/** Ticks of flash left; the overlay fades it by {@code ticks / 100} when it is under 100. */
	public static volatile int dfcFlashTicks = 0;

	/** What {@code ParticleNuke} writes the first time its sphere reaches the player. */
	public static void trigger(int flashTime) {
		dfcFlashTicks = HOLD_TICKS + flashTime;
	}
}
