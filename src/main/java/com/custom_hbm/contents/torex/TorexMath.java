package com.custom_hbm.contents.torex;

import java.util.Random;

import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

import net.minecraft.util.Mth;

/**
 * Every number the torex computes, as a pure function. <b>Not an upstream file</b>: upstream's
 * {@code LCETorex} writes each of these inline, in the middle of a 1,000-line class whose
 * superclass cannot be loaded on a server. Pulled out here so a GameTest can run the whole growth
 * curve, the lifetimes, the spawn counts and the palettes without a client, in the manner of
 * {@code LeafiaParticleMath} (D-1075).
 *
 * <p>Nothing in here is new arithmetic. Each method is a block of upstream's {@code onUpdate},
 * {@code getSimulationSpeed}, {@code getAlpha}, {@code getInterpColor}, {@code setScale} or
 * {@code statFac}, moved without being touched, and the expression keeps its original types because
 * several of them are integer or {@code float} arithmetic on purpose (the heat formula divides
 * <em>integers</em>; the fade divides <em>floats</em>). Where upstream's integer division looks like
 * a mistake it is kept, and says so.
 *
 * <p>The constants are upstream's {@code public static final} fields. {@code MAX_CLOUDLETS} is
 * among them and is <b>read by nothing, upstream included</b>: the one line that used it is
 * commented out ("this is just stupid"), so the cloud's size is bounded only by how fast it
 * spawns and how long a puff lives (D-1089).
 */
public final class TorexMath {

	private TorexMath() {}

	/** The shockwave's speed multiplier. Upstream's {@code animationSpeedShk}. */
	public static final float ANIMATION_SPEED_SHK = 6;
	/** The cloud's speed multiplier, and the ratio of animation ticks to real ones. */
	public static final float ANIMATION_SPEED_GENERAL = 3f;

	public static final int FIRST_CONDENSE_HEIGHT = 130;
	public static final int SECOND_CONDENSE_HEIGHT = 170;
	/** Unused upstream. */
	public static final int BLAST_WAVE_HEADSTART = 5;
	/** Unused upstream: see the class note. */
	public static final int MAX_CLOUDLETS = 20_000;

	public static final int FLASH_BASE_DURATION = 15;
	public static final int FLARE_BASE_DURATION = 600;

	/** The palettes, as upstream's {@code TYPE} byte. {@code BALEFIRE} is also what any other value means. */
	public static final int TYPE_NUKE = 0;
	public static final int TYPE_BALEFIRE = 1;
	public static final int TYPE_DIGAMMA = 2;
	public static final int TYPE_ENDOTHERMIC = 3;

	// Nuke colours
	public static final double NR1 = 2.5;
	public static final double NG1 = 1.3;
	public static final double NB1 = 0.4;
	public static final double NR2 = 0.1;
	public static final double NG2 = 0.075;
	public static final double NB2 = 0.05;

	// Balefire colours
	public static final double BR1 = 1;
	public static final double BG1 = 2;
	public static final double BB1 = 0.5;
	public static final double BR2 = 0.1;
	public static final double BG2 = 0.1;
	public static final double BB2 = 0.1;

	/** {@code startBoostEase}: exponential out, which {@code LeafiaEase.get(t, min, max, true)} discards (D-1004). */
	private static final LeafiaEase START_BOOST_EASE = new LeafiaEase(Ease.EXPO, Direction.O);

	// ----- scale ------------------------------------------------------------------------------

	/** {@code setScale}'s {@code maxAge}: forty-five seconds per unit of scale. */
	public static int maxAge(float scale) {
		return (int) (45 * 20 * scale);
	}

	/**
	 * The scale a command's {@code scale} argument becomes: a hundredth of it, held between a quarter
	 * and five. Upstream's {@code MathHelper.clamp(scale * 0.01F, 0.25F, 5F)}, written in every one
	 * of its four {@code statFac} variants.
	 */
	public static float scaleOf(float scale) {
		return Mth.clamp(scale * 0.01F, 0.25F, 5F);
	}

	/**
	 * What {@code statFac} (the nuke palette only) does to the argument before {@link #scaleOf}: a
	 * soft cap that bends anything above 10 toward a 0.9 power. The other three palettes skip it.
	 */
	public static float nukeInput(float scale) {
		return (float) Math.min(Math.pow(scale / 10, 0.9) * 10, scale);
	}

	/** The packet's range: {@code 200 + amp + amp^0.8 * 16} with {@code amp = scale * 100}. */
	public static double packetRange(double scale) {
		double amp = scale * 100;
		return 200 + amp + Math.pow(amp, 0.8) * 16;
	}

	// ----- the simulation clock ---------------------------------------------------------------

	/** The tick a cloud with a living bound explosion stops ageing at: a quarter of its life. */
	public static int pauseTick(int maxAge) {
		return (int) (maxAge * 0.25);
	}

	/** The tick the cloud starts to wind down at: three quarters of its life. */
	public static int slowTick(int maxAge) {
		return (int) (maxAge * 0.75);
	}

	/**
	 * {@code getSimulationSpeed} past the part that touches {@code subt}.
	 *
	 * @param ticksExisted ticks since the cloud appeared
	 * @param life         {@code ticksExisted - subt}, after any pause has been applied
	 * @param maxAge       {@link #maxAge}
	 * @param scale        the cloud's scale
	 */
	public static double simulationSpeed(int ticksExisted, int life, int maxAge, double scale) {
		int slowTick = slowTick(maxAge);
		double out = 1;
		if(life >= slowTick)
			out *= Mth.clampedLerp(1, 0, (life - slowTick) / (float) (maxAge - slowTick));
		double startBoostDuration = 20 * 10;
		int slowStart = 20 * 30;
		double slowDuration = 20 * 180;
		if(ticksExisted <= startBoostDuration) {
			out *= START_BOOST_EASE.get(ticksExisted / startBoostDuration, Mth.clamp((scale - 0.5) * 2 / 3 + 1, 1, 2), 1, true);
		} else if(ticksExisted >= slowStart) {
			out *= Mth.clampedLerp(1, 0.25, (ticksExisted - slowStart) / slowDuration);
		}
		return out;
	}

	/** The cloud's alpha: one until three quarters through its life, then fading linearly to zero. */
	public static float alpha(int maxAge, int ticksExisted, int subt) {
		int fadeOut = maxAge * 3 / 4;
		int life = ticksExisted - subt;
		if(life > fadeOut) {
			float fac = (float) (life - fadeOut) / (float) (maxAge - fadeOut);
			return 1F - fac;
		}
		return 1.0F;
	}

	/** How long a standard puff lives: its square-law age, capped by what the cloud has left. */
	public static int cloudletLifetime(int ticksExisted, int maxAge, int subt) {
		return Math.min((int) Math.pow(Math.min(ticksExisted, 1200), 2) + 200, (maxAge + subt) - ticksExisted + 200);
	}

	/** The bound of {@code rand.nextInt(...)}: a spawn happens when it draws zero. */
	public static int spawnBound(double simSpeed) {
		return Math.max(0, (int) Math.floor(1 / Math.max(simSpeed, 0.001) - 0.25)) + 1;
	}

	/**
	 * How many puffs the loop spawns this tick, as the (fractional) limit {@code i} counts up to.
	 * {@code multiplier} is 5 for the endothermic palette's first loop and 1 otherwise; its
	 * pillar loop uses 6.
	 */
	public static double spawnLimit(boolean doSpawn, double simSpeed, int multiplier) {
		return doSpawn ? Math.ceil(simSpeed - 0.1) * 2 * multiplier : 0;
	}

	// ----- the shockwave ----------------------------------------------------------------------

	/** The tick the shockwave ring starts to be made: {@code scale * 15 * 3.65}. */
	public static double tickOffset(double scale) {
		return scale * 15 * 3.65;
	}

	/** True while the shock ring is still being spawned. */
	public static boolean shockActive(int ticksExisted, double tickOffset) {
		return ticksExisted * ANIMATION_SPEED_SHK < 150 + tickOffset * ANIMATION_SPEED_SHK && ticksExisted > tickOffset;
	}

	public static int shockCount(double ticksExistedO) {
		return Math.min((int) (ticksExistedO * ANIMATION_SPEED_SHK) * 2, 300);
	}

	public static int shockLife(double ticksExistedO) {
		return Math.max(400 - (int) (ticksExistedO * ANIMATION_SPEED_SHK) * 20, 50);
	}

	public static double shockMotion(double ticksExistedO) {
		return Mth.clamp(0.25 * (int) (ticksExistedO * ANIMATION_SPEED_SHK) - 5, 0, 1);
	}

	// ----- the torus --------------------------------------------------------------------------

	/** What one tick adds to {@code coreHeight} (not for the endothermic palette). */
	public static double coreHeightStep(int ticksExisted, double scale) {
		return 0.15 / (Math.max(ticksExisted - 240, 0) / (Math.pow(scale, 0.6) * 1.6) * 1.75 + 1);
	}

	/** What one tick adds to {@code torusWidth}. */
	public static double torusWidthStep(int ticksExisted, double scale) {
		return 0.05 / (Math.max(ticksExisted - 300, 0) / (Math.pow(scale, 0.6) * 1.75) * 1.75 + 1);
	}

	/** What one tick adds to {@code scaleCurrent}. */
	public static double scaleCurrentStep(int ticksExisted, double scale, double simSpeed) {
		return (0.0025 * simSpeed) / (Math.max(ticksExisted - 300, 0) / (Math.pow(scale, 0.6) * 1.45) * 1.75 + 1);
	}

	/** What one tick adds to {@code scaleGrow}. */
	public static double scaleGrowStep(int ticksExisted) {
		return (0.0025 * 6) / (Math.max(ticksExisted - 600, 0) / 350d + 1);
	}

	/** {@code maxHeat}: {@code 50 * scale^2}, truncated, doubled for the endothermic palette. */
	public static int maxHeat(double scale, int type) {
		int maxHeat = (int) (50 * scale * scale);
		if(type == TYPE_ENDOTHERMIC) maxHeat *= 2;
		return maxHeat;
	}

	/**
	 * The cloud's heat. <b>Integer division inside the power</b>: {@code maxHeat * life / maxAge} is
	 * {@code int / int}, so the heat falls in steps. Upstream's; kept.
	 */
	public static double heat(int maxHeat, int ticksExisted, int subt, int maxAge) {
		return maxHeat - Math.pow((maxHeat * (ticksExisted - subt)) / maxAge, 0.6);
	}

	/** {@link #heat} as a fraction of {@code maxHeat}, with no {@code subt}. */
	public static double heatScaled(int maxHeat, int ticksExisted, int maxAge) {
		return (maxHeat - Math.pow((maxHeat * ticksExisted) / maxAge, 0.6)) / maxHeat;
	}

	// ----- colour -----------------------------------------------------------------------------

	/**
	 * The palette: {@code out} becomes the colour at {@code interp} (0 is the cool end, 2 the hot).
	 * Palette 0 is the nuke; 2 swaps in the balefire's green and blue channels for red and green, so
	 * it reads red; 3 is the nuke with red and blue swapped, so it reads blue; anything else is the
	 * balefire.
	 */
	public static void color(int type, double interp, double[] out) {
		if(type == TYPE_NUKE) {
			out[0] = NR2 + (NR1 - NR2) * interp;
			out[1] = NG2 + (NG1 - NG2) * interp;
			out[2] = NB2 + (NB1 - NB2) * interp;
		} else if(type == TYPE_DIGAMMA) {
			out[0] = BG2 + (BG1 - BG2) * interp;
			out[1] = BB2 + (BB1 - BB2) * interp;
			out[2] = BB2 + (BB1 - BB2) * interp;
		} else if(type == TYPE_ENDOTHERMIC) {
			out[0] = NB2 + (NB1 - NB2) * interp;
			out[1] = NG2 + (NG1 - NG2) * interp;
			out[2] = NR2 + (NR1 - NR2) * interp;
		} else {
			out[0] = BR2 + (BR1 - BR2) * interp;
			out[1] = BG2 + (BG1 - BG2) * interp;
			out[2] = BB2 + (BB1 - BB2) * interp;
		}
	}

	/**
	 * How hot a puff is: {@code 2 / max(distSq / div, 1)} with {@code distSq} the squared distance
	 * from the cloud's core, divided by the cloud's heat ({@code heat * 3} for a shock puff).
	 */
	public static double heatInterp(double distSq, double heat, boolean shock, double scale) {
		distSq /= shock ? heat * 3 : heat;
		double div = 2.5 - Math.min(1, scale / 5) * 1.5;
		return 2D / Math.max(distSq / div, 1);
	}

	// ----- the shock-in-the-player ------------------------------------------------------------

	/** How far the shockwave still has to travel to the player; the effect starts at 10 or less. */
	public static double shockwaveDistance(double dist, int ticksExisted, double scale) {
		return dist - ticksExisted * 1.5 * ANIMATION_SPEED_SHK + scale * FLASH_BASE_DURATION * 6 * ANIMATION_SPEED_SHK;
	}

	/** Which sound the blast plays: smol, plain, near or far, by amplitude and distance. Index into {@link #SOUND_NAMES}. */
	public static int blastSound(double amplitude, double dist) {
		if(amplitude > 48) {
			if(amplitude > 128) {
				if(dist <= 100 + Math.pow(amplitude, 0.95)) return 2;
				else if(dist > 300 + amplitude + Math.pow(amplitude, 0.8) * 2) return 3;
				return 0;
			}
			return 1;
		}
		return 4;
	}

	/** {@code nuke}, {@code nuke_smol}, {@code nuke_near}, {@code nuke_far}, {@code mukeExplosion}. */
	public static final String[] SOUND_NAMES = {"nuke", "nuke_smol", "nuke_near", "nuke_far", "mukeExplosion"};

	/** The hurt-time wobble: {@code duration = amp * min(1, amp^2 / dist^2)}, the swing twice that. */
	public static int hurtDuration(double amplitude, double dist) {
		return (int) (amplitude * Math.min(1, (amplitude * amplitude) / (dist * dist)));
	}

	// ----- the flash --------------------------------------------------------------------------

	/**
	 * The flash's size and centre alpha at {@code age} ticks (including the partial tick), as
	 * {@code {size, alpha}}, or {@code null} once it is over ({@code age > flashDuration * 6}).
	 * {@code size} multiplies the cloud's scale; below one it eases in on a quadratic out.
	 */
	public static double[] flash(double age, float flashDuration) {
		if(age > flashDuration * 12 / 2) return null;
		double intensity = age / flashDuration * 2;
		if(intensity < 1) {
			LeafiaEase ease = new LeafiaEase(Ease.QUAD, Direction.O);
			intensity = ease.get(intensity);
			return new double[] {intensity, 1};
		}
		double size = Math.pow(Math.min(intensity, 20), 0.5);
		return new double[] {size, Math.max(1 - Math.pow((intensity - 1) / 8, 0.5), 0)};
	}

	// ----- the flash's spikes --------------------------------------------------------------

	/** Spikes in the flash, and the seed of their random walk: upstream's 300 and {@code 432L}. */
	public static final int FLASH_FANS = 300;
	public static final long FLASH_SEED = 432L;
	/** {@code renderFlash}'s {@code intensity} argument, which {@code flashWrapper} always passes as 50. */
	public static final float FLASH_INTENSITY = 50F;

	/** Each fan's accumulated rotation (row-major 3x3) and its two raw size draws, built once. */
	public static final float[][][] FLASH_ROT = new float[FLASH_FANS][][];
	public static final float[] FLASH_LEN = new float[FLASH_FANS];
	public static final float[] FLASH_WID = new float[FLASH_FANS];

	static {
		buildFlashTable(FLASH_ROT, FLASH_LEN, FLASH_WID);
	}

	/**
	 * The flash's 300 orientations: {@code glRotatef} five times <em>onto whatever the last spike
	 * left</em>, from {@code Random(432L)}, then the two size draws. Public so a test can run it.
	 *
	 * @param lengthRaw {@code random * 20 + 5 + 10}, before it is multiplied by intensity and scale
	 * @param widthRaw  {@code random * 2 + 1 + 2}
	 */
	public static void buildFlashTable(float[][][] rotations, float[] lengthRaw, float[] widthRaw) {
		Random random = new Random(FLASH_SEED);
		float[][] m = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		for(int i = 0; i < rotations.length; i++) {
			m = mul(m, rot(0, random.nextFloat() * 360.0F));
			m = mul(m, rot(1, random.nextFloat() * 360.0F));
			m = mul(m, rot(2, random.nextFloat() * 360.0F));
			m = mul(m, rot(0, random.nextFloat() * 360.0F));
			m = mul(m, rot(1, random.nextFloat() * 360.0F));
			rotations[i] = m;
			lengthRaw[i] = random.nextFloat() * 20.0F + 5.0F + 1 * 10.0F;
			widthRaw[i] = random.nextFloat() * 2.0F + 1.0F + 1 * 2.0F;
		}
	}

	private static float[][] mul(float[][] a, float[][] b) {
		float[][] r = new float[3][3];
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				for(int k = 0; k < 3; k++)
					r[i][j] += a[i][k] * b[k][j];
		return r;
	}

	private static float[][] rot(int axis, float degrees) {
		double rad = Math.toRadians(degrees);
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		return switch(axis) {
		case 0 -> new float[][] {{1, 0, 0}, {0, c, -s}, {0, s, c}};
		case 1 -> new float[][] {{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
		default -> new float[][] {{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
		};
	}

}
