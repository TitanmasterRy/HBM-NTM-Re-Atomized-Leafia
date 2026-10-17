package com.leafia.contents.network.ff_duct.uninos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.uninos.INetworkProvider;
import com.hbm.uninos.NodeNet;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.network.FFNBT;
import com.leafia.dev.LeafiaUtil;

import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Forge-fluid duct network: one graph over the nodespace, moving NeoForge stacks from the
 * providers' tanks into the receivers' tanks.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/FFNet.java}, whose own comment on
 * the algorithm is "i got tired and rushed, have this shitty algorithm". It is ported as it is,
 * because every quirk below is reachable by a player.
 *
 * <h2>How fluid moves</h2>
 *
 * A provider ({@link IFFProvider#tryProvide}) registers itself <em>and the tank it is sending
 * from</em> every tick; a receiver ({@link IFFReceiver#trySubscribe}) registers itself. On the
 * network's update, for every live provider and every tank it registered:
 *
 * <ol>
 * <li>every live receiver other than the provider is asked for its tank for this fluid; a tank that
 *     is the sending tank itself, holds another fluid, or carries an incompatible tag
 *     ({@link FFNBT#areTagsCompatible}) is skipped. Each remaining tank's demand is its free room;
 *     a receiver with no room is counted in the total demand (it adds 0) but not served;</li>
 * <li>each served receiver gets {@code min(demand, ceil(available x demand / totalDemand))}, in
 *     the order the receiver map iterates, through {@link LeafiaUtil#fillFF}.</li>
 * </ol>
 *
 * Then the tank registry is cleared; providers and receivers stay registered until they time out
 * ({@link #timeout}, 3,000 ms of not re-registering) or their block entity goes away.
 *
 * <h2>Two upstream quirks, kept and flagged (D-021)</h2>
 *
 * <ul>
 * <li><b>The rounding creates fluid.</b> The shares are rounded <em>up</em> - upstream's comment
 *     says it switched from floor to ceil "just to be safe" - so they can add up to more than the
 *     provider holds, and {@code fillFF} fills the receiver the full share while the provider can
 *     only drain what it has. Five millibuckets split between two receivers with equal room become
 *     three and three: one millibucket appears per tick. At most one per extra receiver per tank per
 *     tick; a GameTest pins the five-to-six case.</li>
 * <li><b>The transfer runs a tick late.</b> Upstream queues the body onto
 *     {@code LeafiaPassiveServer.queueFunction} ("bullshit threading, so here we are"), which runs
 *     it at the start of the next server tick. The port's copy of that queue is
 *     {@link PWRTicks#queueFunction}, where the PWR's share of the same upstream listener already
 *     lives; this uses it rather than a second queue so the ordering is upstream's single list.</li>
 * </ul>
 *
 * <p>The fastutil {@code Object2LongMap.fastIterator} loops are the base mod's plain
 * {@code Map<.., Long>} entries here; {@link NodeNet} in this port has three type parameters,
 * not CE's four.
 */
public class FFNet extends NodeNet<IFFReceiver, IFFProvider, FFNode> {

	public static final INetworkProvider<FFNet> PROVIDER = FFNet::new;

	/** What each provider registered this tick, cleared by {@link #transfer}. */
	final Map<IFFProvider, Set<FluidTank>> tankMap = new HashMap<>();

	public void addTank(IFFProvider prov, FluidTank tank) {
		tankMap.computeIfAbsent(prov, p -> new HashSet<>()).add(tank);
	}

	/** Milliseconds a provider or receiver stays registered without re-registering. */
	protected static int timeout = 3_000;

	/** Upstream's flag; its only assignment is commented out ("this made it buggier"), so it stays false. */
	public boolean scheduled = false;

	@Override
	public void update() {
		if(scheduled) return;
		PWRTicks.queueFunction(this::transfer);
	}

	/** The queued body of upstream's {@code update}. Public so tests can run it without waiting a tick. */
	public void transfer() {
		scheduled = false;
		if(providerEntries.isEmpty()) { tankMap.clear(); return; }
		if(receiverEntries.isEmpty()) { tankMap.clear(); return; }
		final long timestamp = System.currentTimeMillis();

		Iterator<Map.Entry<IFFProvider, Long>> provIt = providerEntries.entrySet().iterator();
		while(provIt.hasNext()) {
			Map.Entry<IFFProvider, Long> entry = provIt.next();
			IFFProvider prov = entry.getKey();
			if(timestamp - entry.getValue() > timeout || isBadLink(prov)) {
				provIt.remove();
				continue;
			}
			Set<FluidTank> tanks = tankMap.get(prov);
			if(tanks == null) continue;
			for(FluidTank tank : tanks) {
				if(tank == null) continue;
				if(tank.getFluid().isEmpty() || tank.getFluidAmount() <= 0) continue;
				final List<IFFReceiver> receivers = new ArrayList<>();
				Iterator<Map.Entry<IFFReceiver, Long>> recIt = receiverEntries.entrySet().iterator();
				int totalDemand = 0;
				while(recIt.hasNext()) {
					Map.Entry<IFFReceiver, Long> entry1 = recIt.next();
					IFFReceiver rec = entry1.getKey();
					if(timestamp - entry1.getValue() > timeout || isBadLink(rec)) {
						recIt.remove();
						continue;
					}
					if(rec.equals(prov)) continue;
					FluidTank receiving = rec.getCorrespondingTank(tank.getFluid());
					if(receiving == null) continue;
					if(receiving.equals(tank)) continue;
					if(!receiving.getFluid().isEmpty() && !receiving.getFluid().getFluid().equals(tank.getFluid().getFluid())) continue;
					if(FFNBT.areTagsCompatible(tank.getFluid(), receiving)) {
						int demand = receiving.getCapacity() - receiving.getFluidAmount();
						totalDemand += demand;
						if(demand > 0)
							receivers.add(rec);
					}
				}
				int totalAmt = tank.getFluidAmount();
				for(IFFReceiver rec : receivers) {
					FluidTank receiving = rec.getCorrespondingTank(tank.getFluid());
					int demand = receiving.getCapacity() - receiving.getFluidAmount();
					float ratio = demand / (float) totalDemand;
					int toTransfer = Math.min(demand, (int) Math.ceil(totalAmt * ratio)); // just to be safe
					if(toTransfer > 0) {
						// at this point, transferring is confirmed, no turning back
						LeafiaUtil.fillFF(tank, receiving, toTransfer);
					}
				}
			}
		}
		tankMap.clear();
	}
}
