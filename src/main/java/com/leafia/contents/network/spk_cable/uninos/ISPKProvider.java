package com.leafia.contents.network.spk_cable.uninos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.Compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Something that gives SPK: a DFC emitter, a core's receiver passing it on.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/ISPKProvider.java}. The
 * transfer arithmetic below is upstream's line for line; the changes are the types
 * ({@code World}/{@code TileEntity}/{@code ForgeDirection} to {@code Level}/{@code BlockEntity}/
 * {@code Direction}), the plain {@link Map} iterators where upstream used fastutil's, and the
 * block entity lookup, which goes through {@link Compat#getTileStandard} so that asking about a
 * neighbour never loads a chunk.
 *
 * <h2>How SPK moves</h2>
 *
 * <ol>
 * <li>A provider calls {@link #tryProvideSPK(Level, int, int, int, Direction)} for a position
 *     next to it, usually once per side per tick.
 * <li>If that position is a cable, the call walks the cable's net's <em>receiver</em> entries:
 *     it drops the stale or removed ones, and sizes the rest by what each can take
 *     ({@code min(room, receiverSpeed)}).
 * <li>The most it will send is {@code min(getSendable(), getSPKProviderSpeed())}, capped at the
 *     total demand. Each receiver gets that total split in proportion to its demand, rounded
 *     down, so the rounding stays in the provider rather than being lost.
 * <li>The provider is charged for what receivers actually accepted
 *     ({@link #setTransferredSpk}), not for what it offered.
 * <li>If the position is a receiver rather than a cable it is fed directly, one-to-one, if
 *     it prefers input from that side.
 * </ol>
 *
 * There is <b>no loss</b> anywhere in this, and no priority: every receiver in a net shares one
 * pool in proportion to its demand. The net's providers are not consulted (see {@link SPKNet}).
 *
 * <h2>Two upstream quirks, kept</h2>
 *
 * <ul>
 * <li>{@code tryProvideSPK(Level, BlockPos, Direction, boolean simulate)} ignores
 *     {@code simulate}. Nothing upstream passes {@code true}.
 * <li>A cable whose net has not formed yet (a node's net is null for its first tick) is not
 *     skipped to the receiver branch; the method falls through to it, finds the cable is not an
 *     {@link ISPKReceiver}, and does nothing. A provider placed the same tick as its cable simply
 *     gives nothing for one tick.
 * </ul>
 */
public interface ISPKProvider extends ISPKHandler {

	/**
	 * Uses up SPK. The default has no sanity check: ensure {@code power} is at most what is there.
	 *
	 * <p>Upstream's two comments on this method disagree - "joules are supposed to be reset every
	 * tick, we do not need a buffer of them" and, dated 2026-02-08, "Seems like we needed this" -
	 * and the second won.
	 */
	default void setTransferredSpk(long power) {
		this.setSPK(this.getSPK() - power);
	}

	/** The most this provider can send in one go. The whole buffer, unless an implementor says less. */
	default long getSPKProviderSpeed() {
		return this.getMaxSPK();
	}

	/** What is available to send right now. */
	long getSendable();

	/**
	 * Gives SPK to whatever is at {@code (x, y, z)}: through a cable to every subscribed receiver
	 * of its net, or straight into a receiver.
	 *
	 * @param x   the position of the <b>target</b>, not of this provider
	 * @param dir the direction from this provider to the target
	 */
	default void tryProvideSPK(Level world, int x, int y, int z, Direction dir) {

		BlockPos targetPos = new BlockPos(x, y, z);
		BlockEntity targetTE = Compat.getTileStandard(world, targetPos);

		if(targetTE == null) return;

		Direction dirOpposite = dir.getOpposite();
		if(targetTE instanceof ISPKConductor con) {
			if(con.canConnectSPK(dirOpposite)) {
				SPKNode node = (SPKNode) UniNodespace.getNode(world, targetPos, SPKNet.THE_NETWORK_PROVIDER);
				if(node != null && node.net != null) {
					// node.net.addProvider(this); - upstream: "fuck uninos". See SPKNet: a provider pushes, it does not register.
					Iterator<Map.Entry<ISPKReceiver, Long>> recIt = node.net.receiverEntries.entrySet().iterator();

					final long timestamp = System.currentTimeMillis();
					final List<SPKNet.Share<ISPKReceiver>> receivers = new ArrayList<>();
					long totalDemand = 0;
					while(recIt.hasNext()) {
						Map.Entry<ISPKReceiver, Long> entry = recIt.next();
						ISPKReceiver rec = entry.getKey();

						if(timestamp - entry.getValue() > SPKNet.timeout || NodeNet.isBadLink(rec)) {
							recIt.remove();
							continue;
						}

						long need = Math.min(rec.getMaxSPK() - rec.getSPK(), rec.getSPKReceiverSpeed());
						if(need > 0) {
							receivers.add(new SPKNet.Share<>(rec, need));
							totalDemand += need;
						}
					}
					if(totalDemand <= 0) return;

					long powerAvailable = Math.min(this.getSendable(), this.getSPKProviderSpeed());
					final long toTransfer = Math.min(powerAvailable, totalDemand);
					long energyUsed = 0L;

					for(SPKNet.Share<ISPKReceiver> entry : receivers) {
						ISPKReceiver rec = entry.target();
						long want = entry.amount();
						long toSend = (long) Math.max(((double) toTransfer) * ((double) want / (double) totalDemand), 0D);

						long leftoverFromRecv = rec.transferSPK(toSend, false);
						energyUsed += (toSend - leftoverFromRecv);
					}
					this.setTransferredSpk(energyUsed);

					return;
				}
			}
		}

		if(targetTE instanceof ISPKReceiver rec && targetTE != this) {
			if(rec.canConnectSPK(dirOpposite) && rec.isInputPreferrable(dirOpposite)) {
				long canProvide = Math.min(this.getSendable(), this.getSPKProviderSpeed());
				long canReceive = Math.min(rec.getMaxSPK() - rec.getSPK(), rec.getSPKReceiverSpeed());
				long toTransfer = Math.min(canProvide, canReceive);

				if(toTransfer > 0L) {
					long rejected = rec.transferSPK(toTransfer, false);
					long accepted = toTransfer - rejected;
					if(accepted > 0L) {
						this.setTransferredSpk(accepted);
					}
				}
			}
		}
	}

	/**
	 * Gives up to {@code canProvide} to one specific block entity, never through a net.
	 *
	 * @param simulate true to answer how much would be taken without taking it or charging this provider
	 * @return the amount the target accepted
	 */
	default long tryProvideSPK(BlockEntity targetTE, Direction dir, long canProvide, boolean simulate) {
		if(targetTE == null) return 0L;
		Direction dirOpposite = dir.getOpposite();
		if(targetTE instanceof ISPKReceiver rec && targetTE != this) {
			if(rec.canConnectSPK(dirOpposite) && rec.isInputPreferrable(dirOpposite)) {
				long canReceive = Math.min(rec.getMaxSPK() - rec.getSPK(), rec.getSPKReceiverSpeed());
				long toTransfer = Math.min(canProvide, canReceive);
				if(toTransfer > 0L) {
					long rejected = rec.transferSPK(toTransfer, simulate);
					long accepted = toTransfer - rejected;
					if(accepted > 0L) {
						if(!simulate) this.setTransferredSpk(accepted);
						return accepted;
					}
				}
			}
		}
		return 0L;
	}

	/**
	 * Registers this provider with the net of the cable {@code te} - <b>do not call this</b>. It
	 * arms {@link SPKNet#update}'s throw as soon as the net also has a receiver. Upstream keeps
	 * it, and keeps a commented-out call to it in the one place that once used it.
	 */
	default void tryLinkSPK(Level world, BlockPos pos, BlockEntity te, Direction dir) {
		Direction dirOpposite = dir.getOpposite();
		if(te instanceof ISPKConductor con) {
			if(con.canConnectSPK(dirOpposite)) {
				SPKNode node = (SPKNode) UniNodespace.getNode(world, pos, SPKNet.THE_NETWORK_PROVIDER);
				if(node != null && node.net != null) {
					node.net.addProvider(this);
				}
			}
		}
	}

	/** {@code simulate} is ignored, as upstream's is. */
	default void tryProvideSPK(Level world, BlockPos pos, Direction dir, boolean simulate) {
		tryProvideSPK(world, pos.getX(), pos.getY(), pos.getZ(), dir);
	}
}
