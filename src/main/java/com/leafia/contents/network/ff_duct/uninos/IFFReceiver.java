package com.leafia.contents.network.ff_duct.uninos;

import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.Compat;
import com.llib.exceptions.LeafiaDevFlaw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A machine that takes Forge fluid off a duct run.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/IFFReceiver.java}, line for line.
 * {@link #trySubscribe} registers this machine on the network of the duct at {@code pos} if that
 * duct will take the fluid; the network then fills {@link #getCorrespondingTank} directly. It is
 * called every tick, because the network forgets a receiver it has not heard from for three
 * seconds ({@code FFNet.timeout}).
 *
 * <p><b>The empty tank's stand-in.</b> Upstream asks with the tank's fluid, or with
 * {@code defaultStack} when the tank is empty, and its callers pass {@code new FluidStack(fluid, 0)}.
 * A NeoForge stack of amount 0 <em>is</em> empty and answers {@code Fluids.EMPTY} for its fluid,
 * which no duct accepts, so callers here pass an amount of 1: the stack is only ever compared by
 * fluid, never drained.
 */
public interface IFFReceiver extends IFFHandler {

	default void trySubscribe(FluidTank tank, FluidStack defaultStack, Level world, BlockPos pos, Direction dir) {
		if(world.isClientSide) throw new LeafiaDevFlaw("screw you don't fucking trySubscribe on remote");
		FluidStack stack = tank.getFluid().isEmpty() ? defaultStack : tank.getFluid();
		if(!this.canConnect(stack, dir)) return;
		BlockEntity te = Compat.getTileStandard(world, pos);
		if(te instanceof IFFConductor conductor) {
			if(!conductor.canConnect(stack, dir.getOpposite())) return;
			GenNode node = UniNodespace.getNode(world, pos, FFNet.PROVIDER);
			if(node != null && node.net instanceof FFNet net)
				net.addReceiver(this);
		}
	}

	/** The tank a stack of this fluid goes into. */
	FluidTank getCorrespondingTank(FluidStack stack);
}
