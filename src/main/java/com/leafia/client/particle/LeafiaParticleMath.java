package com.leafia.client.particle;

import com.leafia.dev.math.FiaMatrix;
import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

/**
 * The arithmetic inside the addon's particles, pulled out of them so a GameTest can run it.
 *
 * <p>Every particle class extends a client-only vanilla type, and a dedicated server cannot even
 * link one — the same trap the base mod's {@code ParticleGeometry} was split off to avoid. The
 * numbers a particle is made of (how long a spark lives, how a frame is picked, how a fade runs)
 * are plain functions of an age and a random draw, so they live here and the particle calls them.
 * Each one is upstream's expression, copied rather than rederived, with the random draw passed in
 * so a test can pin both ends of its range instead of hoping to see them.
 *
 * <p>Nothing here may import a client class.
 */
public final class LeafiaParticleMath {

	private LeafiaParticleMath() {}

	/**
	 * {@code (int)(8.0D / (Math.random() * 0.8D + 0.2D)) + 4}: the life of {@code ParticleBalefire},
	 * {@code ParticleFireK} and {@code ParticleDFC}. {@code random} is a draw in {@code [0, 1)}, so the
	 * result runs from 12 ({@code random} near 1) up to 44 ({@code random} 0).
	 */
	public static int fireLifetime(double random) {
		return (int) (8.0D / (random * 0.8D + 0.2D)) + 4;
	}

	/**
	 * {@code (int)(16.0D / (Math.random() * 0.8D + 0.2D))}: the life of {@code ParticleBalefireLava}
	 * and {@code ParticleFireLavaK}, 16 to 80.
	 */
	public static int lavaLifetime(double random) {
		return (int) (16.0D / (random * 0.8D + 0.2D));
	}

	/**
	 * The shrink every fire particle shares: its size is {@code base × (1 − f² / 2)} where {@code f}
	 * is the fraction of its life gone, so it ends at half size.
	 */
	public static float fireShrink(float lifeFraction) {
		return 1.0F - lifeFraction * lifeFraction * 0.5F;
	}

	/**
	 * {@code ParticleCloud}'s growth: a puff grows from nothing over the first 1/32 of its life.
	 * Shared by {@code ParticleCloudSmall} and {@code ParticleArcWelder}.
	 */
	public static float cloudGrowth(int age, float partialTicks, int lifetime) {
		float f = ((float) age + partialTicks) / (float) lifetime * 32.0F;
		return Math.max(0.0F, Math.min(1.0F, f));
	}

	/**
	 * {@code ParticleRainBlood}'s column in its four-frame strip: the strip is 32 wide, a frame is a
	 * quarter of it, and the frame steps with {@code age × 4 / lifetime}, in integers.
	 */
	public static double bloodFrameU(int age, int lifetime) {
		return Math.floorDiv(age * 4, lifetime) / 4d;
	}

	/** {@code ParticleRBMKJet}'s column in its twenty-frame strip, {@code age × 20 / lifetime}. */
	public static int jetFrame(int age, int lifetime) {
		return age * 20 / lifetime;
	}

	/**
	 * {@code ParticleRBMKJet}'s alpha: a twenty-tick fade in, a twenty-tick fade out, and then
	 * halved. When the life is shorter than forty ticks the second rule wins where they overlap,
	 * because upstream's {@code if}s run in that order and the later one overwrites.
	 */
	public static float jetAlpha(int age, int lifetime) {
		float alpha = 1F;
		if(age < 20) alpha = age / 20F;
		if(age > lifetime - 20) alpha = (lifetime - age) / 20F;
		return alpha * 0.5F;
	}

	/**
	 * The fade both Folkvangr antischrab particles use: nothing for ten ticks, then
	 * {@code (1 − (age − 10) / 40)²} down to zero at fifty.
	 */
	public static float fleijaAlpha(int age) {
		return (float) Math.pow(1 - Math.min((age - 10) / 40f, 1), 2);
	}

	/** One step outward of {@code ParticleFleijaAntischrabA}'s spiral radius. */
	public static double fleijaNextPosition(double position) {
		return position + 2 / Math.pow(position, 0.35);
	}

	/**
	 * One step of {@code ParticleFleijaAntischrabA}'s angle in degrees: it slows as the particle
	 * ages, and the {@code (int)} truncation means it stops dead once {@code 10 / √(age + 1)} falls
	 * below one, which is from age 100 and so never inside its fifty-tick life.
	 */
	public static int fleijaNextAngle(int angle, int age) {
		return angle + (int) (10 / Math.pow(age + 1, 0.5)) * 2;
	}

	/** {@code ParticleArcWelder}'s alpha, {@code 0.5 − 0.5 × (age / life)²}. */
	public static float arcWelderAlpha(int age, int lifetime) {
		return 0.5f - 0.5f * (float) Math.pow((double) age / lifetime, 2);
	}

	/**
	 * {@code ParticleFlash}'s envelope: it grows linearly over {@code ticksIn}, then falls linearly
	 * over {@code ticksOut}. {@code age} already has the partial tick added.
	 */
	public static float flashScale(float age, int ticksIn, int ticksOut) {
		if(age < ticksIn) return age / (float) ticksIn;
		return 1 - (age - ticksIn) / (float) ticksOut;
	}

	/** {@code ParticleNuke}'s diameter: a quadratic ease-out to {@code radius × 2}. */
	public static float nukeDiameter(float age, int lifetime, int radius) {
		LeafiaEase ease = new LeafiaEase(Ease.QUAD, Direction.O);
		return 0 + (float) (ease.get(age / lifetime) * radius * 2);
	}

	/**
	 * {@code ParticleNuke}'s alpha: opaque until twenty ticks before the end, then linear to zero.
	 * The result is {@code 1 − max(a, 0)}.
	 */
	public static float nukeAlpha(int age, int lifetime) {
		float a = (age - (lifetime - 20)) / 20f;
		return 1 - Math.max(a, 0);
	}

	/**
	 * Where {@code ParticlePulserCharge} is along its path. {@code ratio} runs 1 down to 0 as the
	 * spark is drawn in; the distance and the size both follow a sine ease-out of it.
	 */
	public static double pulserEase(double ratio) {
		return new LeafiaEase(Ease.SINE, Direction.O).get(ratio);
	}

	/**
	 * Where {@code ParticleEyeOfHarmony} is along its arc: the frame {@code mat} turned
	 * {@code progress × flyAngle} degrees about its vertical, then pushed back along its own axis by
	 * {@code flyHeight × sin(progress × π)}. At progress 0 and 1 that is the frame's own position, and
	 * at one half it is {@code flyHeight} away from it.
	 */
	public static FiaMatrix eyeOfHarmony(FiaMatrix mat, float progress, float flyAngle, float flyHeight) {
		return mat.rotateY(progress * flyAngle).translate(0, 0, -flyHeight * Math.sin(progress * Math.PI));
	}

	/**
	 * {@code ParticleBasicSmoke}'s life, {@code 100 + rand.nextInt(40)}: 100 to 139 ticks. {@code draw} is
	 * the draw out of forty.
	 */
	public static int smokeMaxAge(int draw) {
		return 100 + draw;
	}

	/**
	 * {@code ParticleBasicSmoke}'s alpha for the tick about to be counted, {@code (1 − age / maxAge) ×
	 * multiplier}. It is worked out <em>before</em> the age is incremented, so a puff is at full alpha
	 * on its first tick and never reaches nothing: the last value it takes is {@code 1 / maxAge}.
	 */
	public static float smokeAlpha(int age, int maxAge, float multiplier) {
		return (1 - ((float) age / (float) maxAge)) * multiplier;
	}

	/** {@code ParticleSmokeShockwave}'s ring radius at an age: {@code age × increment}. */
	public static double shockwaveRadius(int age, double increment) {
		return age * increment;
	}

	/**
	 * The fade factor {@code ParticleSmokeShockwave} calls {@code sc}, {@code √(1 − radius / maxRadius)}.
	 * It is 1 at the centre and 0 at the rim; past the rim the root of a negative is NaN, which upstream
	 * never reaches because it expires at the first radius at or beyond the rim.
	 */
	public static float shockwaveFade(double radius, double maxRadius) {
		return (float) Math.pow(1 - radius / maxRadius, 0.5);
	}

	/** A ring puff's half-size: {@code particleScale × (0.3 + sc × 0.7)}. */
	public static float shockwaveScale(float particleScale, float fade) {
		return particleScale * (0.3f + fade * 0.7f);
	}

	/** A ring puff's alpha multiplier: {@code sc × 0.8}. */
	public static float shockwaveAlpha(float fade) {
		return fade * 0.8f;
	}

	/** How many puffs a ring of this radius gets: {@code 8 + (int)(radius^0.4 × 20)}. */
	public static int shockwaveAmount(double radius) {
		return 8 + (int) (Math.pow(radius, 0.4) * 20);
	}

	/**
	 * The angle of the {@code i}th of {@code amount} puffs round a ring, {@code (2π / amount) × i}. The
	 * increment is worked out first and multiplied, as upstream does, so the last one is just short of
	 * a full turn.
	 */
	public static double shockwaveAngle(int amount, int i) {
		double angleIncrement = Math.PI * 2 / amount;
		return angleIncrement * i;
	}
}
