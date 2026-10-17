package com.leafia.overwrite_contents.interfaces;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;
import com.leafia.contents.network.spk_cable.uninos.ISPKProvider;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;

import net.minecraft.world.entity.player.Player;

/**
 * The overhauled DFC receiver ("absorber"): it registers itself with the core it points at, takes a
 * share of the core's contained energy in proportion to its {@link #leafia$getLevel() level}, and
 * passes it on over the SPK cable - so it is both ends of the network (D-3008).
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityCoreReceiver.java}. The
 * implementor is DFC-3's subclass of the base receiver; the core reads {@code getLevel} and calls
 * {@code explode} in its final phase.
 */
public interface IMixinTileEntityCoreReceiver extends IDFCBase, ISPKProvider, ISPKReceiver {
	double leafia$getLevel();
	void leafia$setLevel(double value);
	BlockEntityCore leafia$getCore();
	void leafia$explode();
	int leafia$fanAngle();
	double leafia$joulesPerSec();
	void leafia$sendToPlayer(Player player);
	long leafia$syncJoules();
	int leafia$destructionLevel();
	long leafia$syncSpk();
	FluidTank leafia$getOutputTank();
}
