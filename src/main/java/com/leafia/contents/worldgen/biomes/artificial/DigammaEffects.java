package com.leafia.contents.worldgen.biomes.artificial;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * What the digamma crater and a digamma dose do to the player's senses: the numbers of upstream's
 * {@code LeafiaClientListener.Digamma}, without the drawing (D-3040).
 *
 * <p>Upstream ticks this once per client tick ({@code Digamma.update}, from {@code LeafiaPassiveLocal.onTick}):
 *
 * <ul>
 * <li><b>The crater's ambience and heartbeat.</b> Inside the crater the ambience ({@code external.sbmoon_surface}, looped)
 *     fades in by {@value #AMBIENCE_STEP} a tick to full, and every {@value #BEAT_PERIOD} ticks a beat plays
 *     ({@code external.eversionsong7cut}) and the screen's edges darken for {@value #VIGNETTE_TICKS} ticks. Outside it the
 *     ambience fades out at the same rate and stops at silence.</li>
 * <li><b>The dose.</b> {@code (digamma / 10) ^ 0.666}: 0 at none, 1 at the lethal 10. It narrows the field of view by up to
 *     {@code 3/14} ({@link #FOV_PER_DOSE}), shakes the camera sideways above {@value #SHAKE_FROM}, and puts words on the
 *     screen: every one to four seconds, with chance {@code dose - 0.1}, one of ten lines ("WHY DO I EXIST?") appears at a
 *     random height for five to nine seconds.</li>
 * </ul>
 *
 * <p>{@code LeafiaDigammaClient} plays, draws and listens; this class holds the state so a test can drive it without a client.
 */
public final class DigammaEffects {

	/** {@code vgMaxTicks}: how long the vignette lasts after a beat. */
	public static final int VIGNETTE_TICKS = 10;
	/** A beat every this many ticks in the crater. */
	public static final int BEAT_PERIOD = 17;
	/** The ambience's fade, each way, per tick. */
	public static final double AMBIENCE_STEP = 0.01;
	/** {@code gui.digamma_message.0} to {@code .9}. */
	public static final int MESSAGE_VARIANTS = 10;
	/** The field of view at dose 1 is {@code 1 - FOV_PER_DOSE} of normal. */
	public static final float FOV_PER_DOSE = 0.21428571428F;
	/** The camera starts shaking above this dose... */
	public static final float SHAKE_FROM = 0.25F;
	/** ...by up to this many blocks sideways, times a gaussian, at dose 1. */
	public static final double SHAKE_SCALE = 0.065;
	/** Upstream counts the words' time in seconds, a twentieth a tick. */
	public static final float TICK_SECONDS = 0.05F;

	/** One line of words on the screen. Positions are fractions of the screen; times are seconds. */
	public static final class Text {
		public final String message;
		public final float x;
		public final float y;
		public final float scale;
		public final float lifetime;
		public float timeElapsed = 0;

		public Text(String message, float x, float y, float scale, float lifetime) {
			this.message = message;
			this.x = x;
			this.y = y;
			this.scale = scale;
			this.lifetime = lifetime;
		}
	}

	public int vgTicks = 0;
	public int beatTimer = 0;
	public double ambienceVolume = 0;
	/** Upstream starts it at 1; the first tick sets it. */
	public float digammaDose = 1;
	public float timer = 0;
	public float timerMax = 5;
	public final List<Text> texts = new ArrayList<>();

	/** Upstream's dose curve. */
	public static float dose(double digamma) {
		return (float) Math.pow(digamma / 10, 0.666);
	}

	/**
	 * One client tick.
	 *
	 * @param inCrater whether the player stands in the digamma crater
	 * @param digamma the player's digamma
	 * @param message resolves message {@code n} (0 to 9) to its words; their length places them
	 * @return whether a beat falls on this tick
	 */
	public boolean tick(boolean inCrater, double digamma, Random rand, IntFunction<String> message) {
		boolean beat = false;
		if(vgTicks > 0) vgTicks--;
		if(inCrater) {
			ambienceVolume = Math.min(ambienceVolume + AMBIENCE_STEP, 1);
			beatTimer++;
			if(beatTimer >= BEAT_PERIOD) {
				beatTimer = 0;
				vgTicks = VIGNETTE_TICKS;
				beat = true;
			}
		} else {
			ambienceVolume = Math.max(ambienceVolume - AMBIENCE_STEP, 0);
		}
		digammaDose = dose(digamma);
		texts.removeIf(text -> (text.timeElapsed += TICK_SECONDS) > text.lifetime);
		timer += TICK_SECONDS;
		if(timer >= timerMax) {
			timer = 0;
			timerMax = rand.nextFloat() * 3F + 1;
			if(rand.nextFloat() + 0.1F < digammaDose) {
				String msg = message.apply(rand.nextInt(MESSAGE_VARIANTS));
				float offset = rand.nextFloat() - 0.5F;
				texts.add(new Text(msg,
						0.5F + offset * Math.max(0, 1 - msg.length() / 60F),
						0.1F + rand.nextFloat() * 0.8F,
						1F + rand.nextFloat(),
						5 + rand.nextFloat() * 4));
			}
		}
		return beat;
	}

	/** The vignette's opacity, {@code sin(ratio x pi) / 2.5}, part way through a tick. */
	public float vignetteAlpha(float partialTicks) {
		if(vgTicks <= 0) return 0;
		float ratio = (vgTicks - partialTicks) / VIGNETTE_TICKS;
		return (float) Math.sin(ratio * Math.PI) / 2.5F;
	}

	/** What the field of view is multiplied by. */
	public float fovMultiplier() {
		return 1 - digammaDose * FOV_PER_DOSE;
	}

	/** The camera's sideways shake for one frame, in blocks, given a standard gaussian. */
	public double shake(double gaussian) {
		if(digammaDose <= SHAKE_FROM) return 0;
		float ratio = (digammaDose - SHAKE_FROM) / (1 - SHAKE_FROM);
		return gaussian * ratio * SHAKE_SCALE;
	}

	/** Forgets everything: the player left the world. */
	public void reset() {
		vgTicks = 0;
		beatTimer = 0;
		ambienceVolume = 0;
		digammaDose = 1;
		timer = 0;
		timerMax = 5;
		texts.clear();
	}
}
