package com.leafia.contents.network.ff_duct.uninos;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Anything that may sit on a Forge-fluid duct run: a duct, or a machine with a Forge tank.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/IFFConnector.java}. Its one
 * method asks whether a side accepts a connection, and its default answers "whatever the block
 * entity exposes a fluid handler on". In 1.12.2 that was the block entity's own
 * {@code hasCapability(FLUID_HANDLER_CAPABILITY, side)}; in 1.21.1 a capability is a registration
 * ({@code LeafiaCapabilities}), so the question goes to the level, which asks the registered
 * provider for that side. The answer is the same as long as the registration mirrors the old
 * override, which is what the GameTests check per side.
 *
 * <p>{@code ForgeDirection.UNKNOWN} is a null {@link Direction}, as everywhere in this port.
 */
public interface IFFConnector {

	/** Whether the given side can be connected to. */
	default boolean canConnect(@Nullable FluidStack stack, @Nullable Direction dir) {
		if(this instanceof BlockEntity te) {
			Level level = te.getLevel();
			if(level == null) return false;
			return level.getCapability(Capabilities.FluidHandler.BLOCK, te.getBlockPos(), te.getBlockState(), te, dir) != null;
		}
		return dir != null;
	}
}
