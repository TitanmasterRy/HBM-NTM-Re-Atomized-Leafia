package com.leafia.dev.math;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from `com.leafia.dev.math.FiaBB`.
 *
 * <p>A rectangle in a {@link FiaMatrix}'s local space, plus a depth, which
 * {@link #toAABB()} flattens into a world-aligned box by transforming two opposite corners and
 * taking the min and max of each axis. That is how the addon gives a rotated elevator car a
 * collision box vanilla can use.
 *
 * <p>{@code AxisAlignedBB} is {@link AABB} and {@code Vec3d} is {@link Vec3}; nothing else
 * changed.
 */
public class FiaBB {
	public FiaMatrix mat;
	public double x0;
	public double y0;
	public double x1;
	public double y1;
	double depth;
	public FiaBB(FiaMatrix mat,double x0,double y0,double x1,double y1,double depth) {
		this.mat = mat;
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.depth = depth;
	}
	public FiaBB(FiaBB bb) {
		this.mat = bb.mat;
		this.x0 = bb.x0;
		this.x1 = bb.x1;
		this.y0 = bb.y0;
		this.y1 = bb.y1;
		this.depth = bb.depth;
	}
	public AABB toAABB() {
		Vec3 a = mat.translate(x0,y0,0).position;
		Vec3 b = mat.translate(x1,y1,depth).position;
		return new AABB(
				Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),
				Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)
		);
	};
}
