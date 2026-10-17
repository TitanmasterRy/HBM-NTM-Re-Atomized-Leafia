package com.custom_hbm.contents.torex;

import net.minecraft.util.Mth;

/**
 * The 1.7.10-style mutable vector the torex's motion is written in: upstream's
 * {@code com/custom_hbm/render/amlfrom1710/Vec3} (the vendored model-loader library, which D-1007
 * dissolved), cut to the six operations {@code LCETorex} calls.
 *
 * <p>It is not vanilla's {@code Vec3} because the torex <b>rotates in place</b>
 * ({@code rotateAroundY}, {@code rotateAroundZ} mutate and return nothing) and does it several times
 * per puff per tick. And it keeps the original's arithmetic exactly: {@link #length} is a
 * {@code float} square root, as 1.12.2's {@code MathHelper.sqrt(double)} returned one, and the
 * rotations use the sine table, whose 1.21.1 successor {@code Mth.sin(float)} is the same
 * 65,536-entry table indexed the same way. A {@code Math.sin} here would differ in the fifth
 * decimal and the cloud would drift from upstream's over 4,500 ticks.
 */
final class TorexVec {

	double x;
	double y;
	double z;

	TorexVec(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** A float-precision square root, as 1.12.2's {@code MathHelper.sqrt(double)}. */
	static double sqrt(double v) {
		return (float) Math.sqrt(v);
	}

	double length() {
		return sqrt(x * x + y * y + z * z);
	}

	/** Unit length, or the zero vector below a length of 1e-4. A new vector, as upstream's. */
	TorexVec normalize() {
		double d0 = sqrt(x * x + y * y + z * z);
		return d0 < 1.0E-4D ? new TorexVec(0, 0, 0) : new TorexVec(x / d0, y / d0, z / d0);
	}

	void rotateAroundY(float angle) {
		float f1 = Mth.cos(angle);
		float f2 = Mth.sin(angle);
		double d0 = x * (double) f1 + z * (double) f2;
		double d2 = z * (double) f1 - x * (double) f2;
		x = d0;
		z = d2;
	}

	void rotateAroundZ(float angle) {
		float f1 = Mth.cos(angle);
		float f2 = Mth.sin(angle);
		double d0 = x * (double) f1 + y * (double) f2;
		double d1 = y * (double) f1 - x * (double) f2;
		x = d0;
		y = d1;
	}
}
