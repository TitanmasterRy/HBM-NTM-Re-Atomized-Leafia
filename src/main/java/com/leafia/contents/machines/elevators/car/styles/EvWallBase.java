package com.leafia.contents.machines.elevators.car.styles;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.elevators.car.HitSrf;
import com.leafia.dev.math.FiaMatrix;

/**
 * One of the four walls of an elevator cabin: how far round the car it sits, and what a player
 * bumps into.
 *
 * <p>The plain wall is a single flat panel 15/16 of a block out from the cabin's centre, the full
 * width of the cabin and 36/16 tall — taller than the 32/16 doorway, because the wall carries on
 * past the top of the door frame.
 *
 * <p>{@link #rotation} is degrees, not a {@link net.minecraft.core.Direction}: the car is an
 * entity and turns to any yaw, so its walls are placed by angle. Kept as upstream's raw
 * {@code int}.
 */
public class EvWallBase {

	public final int rotation;

	public EvWallBase(int rotation) {
		this.rotation = rotation;
	}

	/**
	 * The surfaces this wall pushes entities off, in the car's own space.
	 *
	 * <p>A fresh list each call, as upstream: the car mutates {@link HitSrf#enabled} on the
	 * surfaces it is handed, so handing out a shared list would leave one door's open state stuck
	 * on every other cabin using the same style.
	 */
	public List<HitSrf> getHitSurfaces() {
		List<HitSrf> surfaces = new ArrayList<>();
		surfaces.add(new HitSrf(new FiaMatrix().translate(0,0,-15/16d).rotateY(180),-15/16d,0,15/16d,36/16d,4d/16d).setType(0));
		return surfaces;
	}
}
