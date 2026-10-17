package com.leafia.contents.effects.folkvangr;

import net.minecraft.world.phys.Vec3;

/**
 * Every number the two Folkvangr particles and the cloud's particle rings compute, as pure functions, so a
 * server-side GameTest can pin them: a particle's superclass is client-only and cannot be linked there.
 * Not an upstream file; the arithmetic is {@code ParticleFleijaCloud}'s, {@code ParticleFleijaVacuum}'s and
 * {@code MixinEntityCloudFleija.spawnParticle}'s.
 */
public final class FolkvangrParticleMath {

	private FolkvangrParticleMath() {}

	/** {@code ParticleFleijaCloud}: its quad's half-size. */
	public static final float CLOUD_SCALE = 3;
	/** It fades in over this many ticks... */
	public static final int CLOUD_FADE_IN = 10;
	/** ...holds until this age, then fades out over {@link #CLOUD_FADE_OUT}... */
	public static final int CLOUD_HOLD_UNTIL = 20 * 2;
	public static final int CLOUD_FADE_OUT = 20;
	/** ...and is gone at this age. */
	public static final int CLOUD_LIFETIME = 20 * 3;
	/** It sits on the cloud's surface pushed out by this much. */
	public static final double CLOUD_SURFACE = 1.4;

	/**
	 * The cloud particle's alpha at {@code age} (before the age is advanced), from {@code previous}:
	 * {@code age / 10} up to 10, held, then {@code (1 - min((age - 40) / 20, 1))^1.5} from 40.
	 */
	public static float cloudAlpha(int age, float previous) {
		float alpha = previous;
		if(age <= CLOUD_FADE_IN) alpha = age / (float) CLOUD_FADE_IN;
		if(age >= CLOUD_HOLD_UNTIL) alpha = (float) Math.pow(1 - Math.min((age - CLOUD_HOLD_UNTIL) / (float) CLOUD_FADE_OUT, 1), 1.5);
		return alpha;
	}

	/** Where a cloud particle sits: the cloud's centre plus {@code (forward + right) x scale x 1.4}. */
	public static Vec3 cloudPosition(Vec3 centre, Vec3 forward, Vec3 right, double scale) {
		double s = scale * CLOUD_SURFACE;
		return new Vec3(centre.x + forward.x * s + right.x * s, centre.y + forward.y * s + right.y * s, centre.z + forward.z * s + right.z * s);
	}

	/**
	 * How many particles one ring of {@code radius} sheds: the ring walks one radian at a time up to
	 * {@code 0.8 x 2 pi r^2 / (2 pi r)} steps, and the first step throws three.
	 */
	public static int ringCount(double radius) {
		double length = radius * 0.8;
		double theta = 1 / (2 * radius * Math.PI);
		int count = 0;
		for(double offs = 0; offs < length / theta; offs += 1 / theta) {
			for(int i = -1; i <= ((offs == 0) ? 1 : -1); i++) count++;
		}
		return count;
	}

	/** {@code ParticleFleijaVacuum}: how far through its flight it is, 1 at its start and 0 at half the vacuum's inner radius. */
	public static double vacuumRatio(double distance, double startDistance, double vacuumStart) {
		return Math.max(0, (distance - vacuumStart / 2) / (startDistance - vacuumStart / 2));
	}

	/** Its size shrinks as {@code ratio^0.1}... */
	public static float vacuumScale(float baseScale, double ratio) {
		return baseScale * (float) Math.pow(ratio, 0.1);
	}

	/** ...and its alpha grows as {@code (1 - ratio)^0.5}. */
	public static float vacuumAlpha(float baseAlpha, double ratio) {
		return baseAlpha * (float) Math.pow(1 - ratio, 0.5);
	}

	/** It lives at most this long, and less if it reaches half the inner radius. */
	public static final int VACUUM_LIFETIME = 20 * 10;
}
