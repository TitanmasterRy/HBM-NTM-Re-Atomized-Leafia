package com.leafia.contents.network.ff_duct.uninos;

import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.leafia.contents.network.FFNBT;
import com.leafia.dev.LeafiaUtil;
import com.llib.exceptions.LeafiaDevFlaw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A machine that puts Forge fluid onto a duct run, or straight into the machine beside it.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/IFFProvider.java}, line for line.
 * {@link #tryProvide} does two independent things toward the block at {@code pos}:
 *
 * <ul>
 * <li>If it is a duct that takes this fluid, the tank is handed to that duct's network, which moves
 *     it on its next update ({@link FFNet#addTank}) - the network carries the fluid, this only
 *     registers.</li>
 * <li>If it is a receiver itself (and not this machine), the fluid goes across directly, as much as
 *     the receiver has room for, <b>in this call</b>.</li>
 * </ul>
 *
 * Both checks run, so a block that is both a duct and a receiver would get both; none is.
 *
 * <p>The direct half throws if the amount it confirmed differs from the amount that moved. With
 * {@code fillFF} as it is that cannot happen - the confirmed amount is never more than the room -
 * and the throw is upstream's own assertion, kept.
 */
public interface IFFProvider extends IFFHandler {

	default void tryProvide(FluidTank sending, Level world, BlockPos pos, Direction dir) {
		if(world.isClientSide) throw new LeafiaDevFlaw("screw you don't fucking tryProvide on remote");
		if(sending.getFluid().isEmpty()) return;
		if(!this.canConnect(sending.getFluid(), dir)) return;
		BlockEntity targetTE = world.getBlockEntity(pos);
		if(targetTE == null) return;
		Direction dirOpposite = dir.getOpposite();
		if(targetTE instanceof IFFConductor conductor) {
			if(conductor.canConnect(sending.getFluid(), dirOpposite)) {
				GenNode node = UniNodespace.getNode(world, pos, FFNet.PROVIDER);
				if(node != null && node.net instanceof FFNet net) {
					net.addTank(this, sending);
					net.addProvider(this);
				}
			}
		}
		if(targetTE instanceof IFFReceiver rec && targetTE != this) {
			if(rec.canConnect(sending.getFluid(), dirOpposite)) {
				FluidTank receiving = rec.getCorrespondingTank(sending.getFluid());
				if(receiving != null && FFNBT.areTagsCompatible(sending.getFluid(), receiving)) {
					int demand = receiving.getCapacity() - receiving.getFluidAmount();
					int toTransfer = Math.min(demand, sending.getFluidAmount());
					if(toTransfer > 0) {
						// at this point, transferring is confirmed, no turning back
						int sent = LeafiaUtil.fillFF(sending, receiving, toTransfer);
						if(sent != toTransfer)
							throw new LeafiaDevFlaw("prov: confirmed transfer amount (" + toTransfer + "mB) and actual amount transferred (" + sent + "mB) doesn't match, wtf");
					}
				}
			}
		}
	}

	/** The tank a stack of this fluid is sent from. */
	FluidTank getSendingTank(FluidStack stack);
}
