package com.leafia.overwrite_contents.interfaces;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;

import net.minecraft.world.phys.HitResult;

/**
 * The overhauled DFC emitter, as the core and the renderer see it: an SPK receiver (it is fed over the
 * cable, D-3008) that fires a raycast beam and runs a second, output tank for its spent coolant.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityCoreEmitter.java}. The
 * implementor is DFC-3's subclass of the base emitter. {@code RayTraceResult} is {@link HitResult} and
 * {@code FluidTankNTM} is the base mod's {@link FluidTank}.
 */
public interface IMixinTileEntityCoreEmitter extends IDFCBase, ISPKReceiver {

	HitResult leafia$raycast(long out);

	boolean leafia$isActive();

	void leafia$isActive(boolean active);

	HitResult leafia$lastRaycast();

	FluidTank leafia$getOutputTank();
}
