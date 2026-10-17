package com.leafia.contents.machines.elevators.car;

import net.minecraft.world.level.block.AirBlock;

/**
 * The invisible block an elevator car leaves under itself so the shaft is lit as it moves.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/car/ElevatorLight.java}.
 *
 * <h2>Nine overrides, and vanilla is eight of them</h2>
 *
 * Upstream extends its own block base and then overrides {@code getRenderType} to
 * {@code INVISIBLE}, {@code isOpaqueCube}, {@code canCollideCheck}, {@code isCollidable},
 * {@code getCollisionBoundingBox} to {@code NULL_AABB}, {@code getItemDropped} to
 * {@code Items.AIR}, {@code shouldSideBeRendered}, {@code isBlockNormalCube} and
 * {@code isReplaceable} — nine methods whose combined meaning is "be air, but glow". Vanilla's
 * {@link AirBlock} plus four {@code Properties} calls is all of it, which is D-019's shape for
 * the third time in this addon and the second time for a light source: {@code LightEmitter} is
 * the same class with the same nine overrides and the same answer (D-1017).
 *
 * <p>{@code setLightLevel(1)} is 1.12.2's 0..1 float, so it is light level <b>15</b> here, not 1.
 * That unit change is the one thing in this file that could be got wrong silently — a rail lit at
 * level 1 looks broken rather than absent — and it is what the test checks.
 *
 * <h2>It is inert, and knowingly so</h2>
 *
 * Nothing places one: {@code ElevatorEntity} does, and the car is the middle of a seven-file
 * cycle that this batch scopes rather than ports (D-1032). Upstream gives it
 * {@code setCreativeTab(null)}, so a player cannot obtain one either — it is a marker block the
 * car owns. It lands now because it is the only node of that cycle with <b>no unported
 * dependency at all</b>, so it is the one piece that can be correct before the campaign runs.
 */
public class ElevatorLight extends AirBlock {

	public ElevatorLight(Properties properties) {
		super(properties);
	}
}
