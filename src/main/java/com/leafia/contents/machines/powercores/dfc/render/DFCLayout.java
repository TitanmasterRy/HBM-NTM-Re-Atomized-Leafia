package com.leafia.contents.machines.powercores.dfc.render;

import java.util.ArrayList;
import java.util.List;

import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

/**
 * The arithmetic of the DFC's renderers, kept out of the renderers: where a part turns, which of its
 * frames and arms are drawn, and the sizes of the core's balls. Nothing here names a client class, so a
 * GameTest on a dedicated server can pin every number a screenshot could only look at.
 *
 * <p>Upstream: the bodies of {@code DFCComponentRender.render} and {@code .isFace} and of
 * {@code DFCCoreRender.renderOrb} and {@code .renderStandby}, with the drawing taken out.
 */
public final class DFCLayout {

	private DFCLayout() {}

	/** How much of a direction must point along a face for that face to count as a front (upstream's literal). */
	public static final double FRONT_THRESHOLD = 0.15;

	/** Upstream's {@code isFace}: does {@code direction} point at least 0.15 of its length along this face? */
	public static boolean isFace(net.minecraft.core.Direction face, Vec3 direction) {
		double component = switch(face.getAxis()) {
			case X -> direction.x;
			case Y -> direction.y;
			case Z -> direction.z;
		};
		if(face.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE) component *= -1;
		return component > FRONT_THRESHOLD;
	}

	/** The yaw, in degrees, that turns a part whose front is -Z to face {@code relative}: {@code atan2(-dx, -dz)}. */
	public static double yaw(Vec3i relative) {
		return Math.toDegrees(Math.atan2(-relative.getX(), -relative.getZ()));
	}

	/** The pitch, in degrees, after the yaw: {@code atan2(dy, hypot(dx, dz))}. */
	public static double pitch(Vec3i relative) {
		return Math.toDegrees(Math.atan2(relative.getY(), Math.sqrt(relative.getX() * relative.getX() + relative.getZ() * relative.getZ())));
	}

	/** The part's unit vector toward its target (zero if the target is its own cell). */
	public static Vec3 unit(Vec3i relative) {
		return new Vec3(relative.getX(), relative.getY(), relative.getZ()).normalize();
	}

	/** Does a frame stand on this face? Upstream draws one unless the face opposite it is a front face. */
	public static boolean hasFrame(net.minecraft.core.Direction face, Vec3 unit) {
		return !isFace(face.getOpposite(), unit);
	}

	/**
	 * Is this face the one the part looks out of? Upstream's test is {@code unit.component == maxAbs x
	 * sign} by exact equality, so two faces are fronts when two components tie to the last bit.
	 */
	public static boolean isFront(net.minecraft.core.Direction face, Vec3 unit) {
		double maxAbs = Math.max(Math.abs(unit.x), Math.max(Math.abs(unit.y), Math.abs(unit.z)));
		double expected = maxAbs * face.getAxisDirection().getStep();
		return switch(face.getAxis()) {
			case X -> unit.x == expected;
			case Y -> unit.y == expected;
			case Z -> unit.z == expected;
		};
	}

	/**
	 * The four edges of a front face are tried in turn (each followed by a quarter turn about the part's
	 * front): the face looks at the neighbouring face it would meet, and draws that edge's arm unless that
	 * neighbour is also a front. Returns, in order, whether the arm at {@code i} = 0..3 is drawn.
	 */
	public static boolean[] arms(net.minecraft.core.Direction face, Vec3 unit) {
		boolean[] drawn = new boolean[4];
		for(int i = 0; i < 4; i++) {
			net.minecraft.core.Direction check;
			if(face == net.minecraft.core.Direction.UP) check = net.minecraft.core.Direction.from2DDataValue(Math.floorMod(-i, 4));
			else if(face == net.minecraft.core.Direction.DOWN) check = net.minecraft.core.Direction.from2DDataValue(Math.floorMod(2 - i, 4));
			else if(i == 0) check = net.minecraft.core.Direction.UP;
			else if(i == 1) check = face.getClockWise().getOpposite();
			else if(i == 2) check = net.minecraft.core.Direction.DOWN;
			else check = face.getClockWise();
			drawn[i] = !isFace(check, unit);
		}
		return drawn;
	}

	/** The faces that carry a frame, in {@code Direction.values()} order. */
	public static List<net.minecraft.core.Direction> frames(Vec3 unit) {
		List<net.minecraft.core.Direction> out = new ArrayList<>();
		for(net.minecraft.core.Direction face : net.minecraft.core.Direction.values())
			if(hasFrame(face, unit)) out.add(face);
		return out;
	}

	// ---- the core ----------------------------------------------------------------------------

	/** The standby ball's brightness: {@code (T / 100)^1.5}, as a float. */
	public static float standbyBrightness(double temperature) {
		return (float) Math.pow(temperature / 100d, 1.5);
	}

	/**
	 * The orb's scale: {@code ln(T / 50 + 1) + 0.5}; in the last 3% of a collapse (past 0.97) it shrinks to
	 * nothing on an exponential ease of how far through that last 3% the collapse is.
	 */
	public static float orbScale(double temperature, double collapsing) {
		float scale = (float) Math.log(temperature / 50 + 1) + 0.5F;
		if(collapsing > 0.97) {
			double percent = (collapsing - 0.97) / 0.03;
			LeafiaEase ease = new LeafiaEase(Ease.EXPO, Direction.I);
			scale *= (float) ease.get(ease.get(percent), 1, 0);
		}
		return scale;
	}

	/** The orb's turn, in degrees about (1, 1, 1): nothing until the last 3% of a collapse, then up to 1,000. */
	public static double orbSpin(double collapsing) {
		if(collapsing > 0.97) {
			double percent = (collapsing - 0.97) / 0.03;
			return new LeafiaEase(Ease.EXPO, Direction.I).get(percent) * 1000;
		}
		return 0;
	}
}
