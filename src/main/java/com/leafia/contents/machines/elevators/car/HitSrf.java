package com.leafia.contents.machines.elevators.car;

import com.leafia.dev.math.FiaMatrix;

import net.minecraft.world.phys.Vec3;

/**
 * One flat, oriented rectangle an elevator car pushes entities off — a "hit surface".
 *
 * <p>The car is an entity, so it gets none of a block's collision for free: every wall, floor and
 * ceiling panel of the cabin is one of these, and the car walks them each tick, shoving anything
 * that has ended up inside back out. That is what lets a player stand in a moving cabin rather
 * than falling through it.
 *
 * <h2>Why this is a top-level class here, and upstream's is nested</h2>
 *
 * Upstream declares {@code HitSrf} and the two static functions below inside
 * {@code ElevatorEntity}, a 1,388-line class that reaches for eighteen other addon files. Nothing
 * about the geometry needs any of that — it needs {@link FiaMatrix} and {@link Vec3}, and that is
 * all. Nested where it is, it drags the entire elevator subsystem into the compile of anything
 * that wants a wall panel, which is why D-1033 measured the elevators as one 42-file unit with no
 * smaller entry.
 *
 * <p>Hoisting it out is the entry. {@link com.leafia.contents.machines.elevators.car.styles.EvWallBase}
 * and the three panel classes under it now compile, and are testable, with no car in the game. See
 * D-1034.
 *
 * <p><b>Nothing else changes.</b> Field names, the constructor's argument order, the two
 * functions' parameter lists and every line of their arithmetic are upstream's, so a reader can
 * still diff this against {@code ElevatorEntity} lines 924-995.
 */
public class HitSrf {

	/** Where the surface sits and which way it faces, relative to the car's own origin. */
	public FiaMatrix mat;

	/** The rectangle's extent in the surface's own plane. */
	public double x0;
	public double x1;
	public double y0;
	public double y1;

	/** How thick the surface is. Entities are pushed to {@code depth/2} clear of the plane. */
	double depth;

	/** Doors switch their panel off while open. */
	public boolean enabled = true;

	/** 0 = wall, -1 = floor, 1 = ceiling. Decides which point of an entity is tested. */
	public int type = 0;

	/**
	 * <b>The argument order is {@code x0, y0, x1, y1} — not {@code x0, x1, y0, y1}.</b> Upstream
	 * assigns the fields in the second order inside a constructor that takes them in the first,
	 * which reads like a bug and is not one. Swapping them builds surfaces that are inside-out on
	 * one axis: {@code x0 > x1} makes every bounds check below fail, so the panel silently stops
	 * colliding rather than colliding wrongly, and a player walks out through the cabin wall.
	 */
	public HitSrf(FiaMatrix mat,double x0,double y0,double x1,double y1,double depth) {
		this.mat = mat;
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
		this.depth = depth;
	}

	public HitSrf setType(int t) { this.type = t; return this; }

	/** Read-only access to the thickness, which upstream keeps package-private. */
	public double getDepth() { return depth; }

	/**
	 * Where a point at {@code pt} moving at {@code vel} should be moved to, to be clear of
	 * {@code srf} on a car at {@code x,y,z} facing {@code yaw} — or {@code null} if it is already
	 * clear.
	 *
	 * <p>{@code length} is how far past the plane the point is allowed to be before it counts as
	 * inside: the caller passes half an entity's width for a wall, and zero for a floor or a
	 * ceiling, because those are tested against the entity's feet and head rather than its middle.
	 */
	public static Vec3 collideVector(Vec3 pt,Vec3 vel,double length,HitSrf srf,double x,double y,double z,double yaw) {

		FiaMatrix mat = new FiaMatrix(new Vec3(x,y,z)).rotateY(-yaw).travel(srf.mat).translate(0,0,srf.depth/2);

		FiaMatrix relative = mat.toObjectSpace(new FiaMatrix(pt));
		if (relative.getX() < srf.x0) return null;
		if (relative.getX() > srf.x1) return null;
		if (relative.getY() < srf.y0) return null;
		if (relative.getY() > srf.y1) return null;

		if (relative.getZ() < 0 && relative.getZ() > -srf.depth/2-length) {
			return mat.toWorldSpace(new FiaMatrix(new Vec3(relative.getX(),relative.getY(),-srf.depth/2-length))).position;
		}
		else if (relative.getZ() > 0 && relative.getZ() < srf.depth/2+length) {
			return mat.toWorldSpace(new FiaMatrix(new Vec3(relative.getX(),relative.getY(),srf.depth/2+length))).position;
		}
		return null;
	}

	/**
	 * {@code velocity} with the component pointing into the surface removed, so an entity shoved
	 * out of a wall slides along it instead of jittering back in next tick.
	 */
	public static Vec3 killVelocity(Vec3 velocity,HitSrf srf,double yaw) {
		FiaMatrix mat = new FiaMatrix().rotateY(-yaw).rotateAlong(srf.mat);
		FiaMatrix relative = mat.toObjectSpace(new FiaMatrix(velocity));
		return mat.toWorldSpace(new FiaMatrix(new Vec3(relative.getX(),relative.getY(),0))).position;
	}
}
