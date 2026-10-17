package com.leafia.contents.machines.misc.modular_turbine.ports;

import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A turbine component that steam can be piped into or out of.
 *
 * <p>Upstream: {@code .../modular_turbine/ports/IMTPortBlock.java}, three lines and one method.
 * {@code ModularTurbineBlockBase} tests {@code this instanceof IMTPortBlock} before it will let a
 * fluid identifier or a screwdriver do anything, so this marker is what separates a port from the
 * blades and separators either side of it.
 *
 * <p>Upstream's {@code EnumFacing} is {@link Direction} and its {@code com.hbm.lib.DirPos} is
 * {@code com.hbm.util.DirPos} here — the same rename the base mod's own machines carry.
 *
 * <p>Nothing implements this yet: its thirteen implementors are T3 of the campaign,
 * and they cannot land before the core cluster they attach to. An
 * interface with no implementor is what a dependency checker offers and D-1055 warns about —
 * the difference here is that this one has a campaign committed to it rather than a tree that is
 * not coming.
 */
public interface IMTPortBlock {

	/**
	 * Where this port reaches for pipes, given the core's position and the machine's facing.
	 *
	 * <p>A {@link DirPos} is a position <em>and</em> the side it is approached from, which is what
	 * the base mod's fluid network subscribes on.
	 */
	DirPos[] getConPos(BlockPos pos, Direction facing);
}
