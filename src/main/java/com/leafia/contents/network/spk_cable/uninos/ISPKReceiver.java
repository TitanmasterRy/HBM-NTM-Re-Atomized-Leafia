package com.leafia.contents.network.spk_cable.uninos;

import com.hbm.uninos.UniNodespace;
import com.hbm.util.Compat;
import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Something that takes SPK: a DFC receiver, a crate that wants it.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/ISPKReceiver.java}. The
 * mirror of the base mod's {@code IEnergyReceiverMK2}, with the one difference that matters:
 * there is no {@code ConnectionPriority}. SPK has **no priorities** - every subscribed receiver
 * of a net takes a share in proportion to its demand (see
 * {@link ISPKProvider#tryProvideSPK(Level, int, int, int, Direction)}).
 *
 * <p>An implementor calls {@link #trySubscribeSPK} every tick it wants power, once per side
 * (upstream's receivers do it from their update, one call per side they accept); the net forgets a
 * receiver that stops asking after three seconds.
 */
public interface ISPKReceiver extends ISPKHandler {

	/**
	 * Takes SPK, up to the buffer's ceiling, and returns what did not fit.
	 *
	 * @param power    the amount offered
	 * @param simulate true to answer without changing anything
	 * @return the overshoot: 0 if it all went in
	 */
	default long transferSPK(long power, boolean simulate) {
		if(power + this.getSPK() <= this.getMaxSPK()) {
			if(!simulate) this.setSPK(power + this.getSPK());
			return 0;
		}
		long capacity = this.getMaxSPK() - this.getSPK();
		long overshoot = power - capacity;
		if(!simulate) this.setSPK(this.getMaxSPK());
		return overshoot;
	}

	/** Whether a provider touching this receiver directly (no cable) may put SPK in through {@code dir}. */
	default boolean isInputPreferrable(Direction dir) {
		return true;
	}

	/** How much this receiver can take in one go. The whole buffer, unless an implementor says less. */
	default long getSPKReceiverSpeed() {
		return this.getMaxSPK();
	}

	default void trySubscribeSPK(Level world, DirPos pos) {
		trySubscribeSPK(world, pos, pos.getDir());
	}

	default void trySubscribeSPK(Level world, BlockPos pos, Direction dir) {
		trySubscribeSPK(world, pos.getX(), pos.getY(), pos.getZ(), dir);
	}

	/**
	 * Subscribes to the net of the cable at {@code (x, y, z)}, if there is one and it accepts a
	 * connection arriving from {@code dir}'s opposite.
	 *
	 * <p>Does nothing if the cable's node has no net yet - nodes get theirs a tick after they are
	 * created - so a receiver must keep asking, which is what the every-tick call does.
	 */
	default void trySubscribeSPK(Level world, int x, int y, int z, Direction dir) {

		BlockPos pos = new BlockPos(x, y, z);
		BlockEntity te = Compat.getTileStandard(world, pos);

		if(te instanceof ISPKConductor con) {
			if(!con.canConnectSPK(dir.getOpposite())) return;

			SPKNode node = (SPKNode) UniNodespace.getNode(world, pos, SPKNet.THE_NETWORK_PROVIDER);

			if(node != null && node.net != null) {
				node.net.addReceiver(this);
			}
		}
	}
}
