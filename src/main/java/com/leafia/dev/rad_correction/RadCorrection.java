package com.leafia.dev.rad_correction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ported from `com.leafia.dev.rad_correction.RadCorrection`.
 *
 * <p>Whether an entity counts as underground, which the addon's radiation classification uses
 * to decide how much of the sky's fallout reaches it. "Underground" is eight blocks or more
 * below the surface — and the surface is measured from the top of the water, not the bottom,
 * so standing on a lake bed does not shelter you.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 *   <li>{@code Material} was removed from the game in 1.20, so
 *       {@code getMaterial() instanceof MaterialLiquid} becomes a check on the block's fluid
 *       state. That is the same question in 1.21.1 terms: a block is a liquid exactly when it
 *       carries a non-empty {@code FluidState}.</li>
 *   <li>1.12.2's {@code World#getHeight(x, z)} is the motion-blocking heightmap, which counts
 *       fluids — {@link Heightmap.Types#MOTION_BLOCKING} is its successor. Picking
 *       {@code WORLD_SURFACE} instead would ignore leaves and change the answer under a tree.</li>
 *   <li>{@code new BlockPos(entity.posX, ...)} is {@code entity.blockPosition()}, which floors
 *       the same way; {@code pos.up()} is {@code pos.above()}; {@code entity.world} is
 *       {@code entity.level()}.</li>
 * </ul>
 *
 * <p><b>No caller yet.</b> Upstream's only consumer is a mixin into {@code com.hbm}'s
 * {@code EntityEffectHandler}, which D-1001 makes a base-mod hook rather than a mixin. The
 * radiation classification feature (L6) is where that hook and this method meet.
 */
public class RadCorrection {

	public static boolean isUnderground(Entity entity) {
		BlockPos pos = entity.blockPosition();
		int height = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		// Climb out of any water the entity is standing in, so depth is measured from the
		// surface of the lake rather than from its bed.
		while(!entity.level().getBlockState(pos).getFluidState().isEmpty())
			pos = pos.above();
		if(pos.getY() <= height - 8)
			return true;
		return false;
	}
}
