package com.leafia.contents.network.spk_cable.uninos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.hbm.uninos.INetworkProvider;
import com.hbm.uninos.NodeNet;
import com.llib.exceptions.LeafiaDevFlaw;

/**
 * The SPK (spark) network: a graph of cables over the base mod's nodespace that carries the DFC's
 * energy.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/SPKNet.java}, whose own
 * header is "Copy-pasted PowerNetMk2". Unlike {@code AmatNet} it cannot be a subclass of the
 * base mod's net (a power net moves {@code IEnergy*MK2}, not {@code ISPK*}), so it is a
 * {@link NodeNet} of its own - three type arguments here where NTM-CE's takes four, because this
 * port's {@code NodeNet} has no self-type parameter (D-1013).
 *
 * <h2>The network does not move anything</h2>
 *
 * This is the thing worth knowing before writing a provider. Upstream's {@link #update} is
 * {@code @Deprecated} and **throws** the moment a net has both a provider and a receiver
 * ("Deprecated update method of SPKNet called"); the author replaced the net-driven transfer with
 * a provider-driven one in {@link ISPKProvider#tryProvideSPK(net.minecraft.world.level.Level, int, int, int, net.minecraft.core.Direction)}:
 * the provider walks the <em>receiver</em> entries of the net its cable belongs to, sizes up the
 * demand, and pushes into them itself, every time it is called. The net is therefore only a
 * directory - a set of cables, and the receivers that have subscribed to it. Providers must
 * <b>not</b> call {@link ISPKProvider#tryLinkSPK} (which registers them as provider entries and
 * so arms the throw) or {@link #addProvider}.
 *
 * <p>That is upstream's design and it is kept, including the throw, which is
 * unreachable from any call path upstream has. The body below the throw is dead and is ported
 * anyway, so that whoever revives net-driven transfer starts from the author's algorithm and
 * not from a blank file.
 *
 * <h2>Why a timestamp</h2>
 *
 * A receiver re-subscribes every tick it wants power ({@link ISPKReceiver#trySubscribeSPK}); the
 * net stamps the time and drops any entry older than {@link #timeout} milliseconds, or one whose
 * block entity has been removed or unloaded. That is how a receiver that is broken, or just stops
 * asking, leaves the network without anyone telling the net. The clock is wall time
 * ({@link System#currentTimeMillis}), as in the base mod's {@code PowerNetMK2}.
 */
public class SPKNet extends NodeNet<ISPKReceiver, ISPKProvider, SPKNode> {

	public static final INetworkProvider<SPKNet> THE_NETWORK_PROVIDER = SPKNet::new;

	/** A target and an amount: upstream's {@code Tuple.ObjectLongPair}, which is NTM-CE's and not in the base (D-1013). */
	public record Share<T>(T target, long amount) {}

	public long energyTracker = 0L;

	/** How long a subscription lives without being refreshed, in milliseconds. */
	protected static int timeout = 3_000;

	@Override
	public void resetTrackers() {
		this.energyTracker = 0;
	}

	@Deprecated
	@Override
	public void update() {

		if(providerEntries.isEmpty()) return;
		if(receiverEntries.isEmpty()) return;

		if(true)
			throw new LeafiaDevFlaw("Deprecated update method of SPKNet called");

		final long timestamp = System.currentTimeMillis();

		final List<Share<ISPKProvider>> providers = new ArrayList<>();
		long powerAvailable = 0;

		Iterator<Map.Entry<ISPKProvider, Long>> provIt = providerEntries.entrySet().iterator();

		while(provIt.hasNext()) {
			Map.Entry<ISPKProvider, Long> entry = provIt.next();
			ISPKProvider prov = entry.getKey();

			if(timestamp - entry.getValue() > timeout || isBadLink(prov)) {
				provIt.remove();
				continue;
			}

			long src = Math.min(prov.getSendable(), prov.getSPKProviderSpeed());
			if(src > 0) {
				providers.add(new Share<>(prov, src));
				powerAvailable += src;
			}
		}

		if(powerAvailable <= 0) return;

		final List<Share<ISPKReceiver>> receivers = new ArrayList<>();
		long totalDemand = 0;

		Iterator<Map.Entry<ISPKReceiver, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Map.Entry<ISPKReceiver, Long> entry = recIt.next();
			ISPKReceiver rec = entry.getKey();

			if(timestamp - entry.getValue() > timeout || isBadLink(rec)) {
				recIt.remove();
				continue;
			}

			long need = Math.min(rec.getMaxSPK() - rec.getSPK(), rec.getSPKReceiverSpeed());
			if(need > 0) {
				receivers.add(new Share<>(rec, need));
				totalDemand += need;
			}
		}

		if(totalDemand <= 0) return;

		final long toTransfer = Math.min(powerAvailable, totalDemand);
		long energyUsed = 0L;

		for(Share<ISPKReceiver> entry : receivers) {
			ISPKReceiver rec = entry.target();
			long want = entry.amount();
			long toSend = (long) Math.max(((double) toTransfer) * ((double) want / (double) totalDemand), 0D);

			long leftoverFromRecv = rec.transferSPK(toSend, false);
			energyUsed += (toSend - leftoverFromRecv);
		}

		this.energyTracker += energyUsed;

		if(energyUsed <= 0) return;

		long leftover = energyUsed;

		for(Share<ISPKProvider> entry : providers) {
			ISPKProvider prov = entry.target();
			long canProvide = entry.amount();

			double weight = (double) canProvide / (double) powerAvailable;
			long toUse = (long) Math.max(energyUsed * weight, 0.0D);

			prov.setTransferredSpk(toUse);
			leftover -= toUse;
		}

		int iterationsLeft = 100;
		while(iterationsLeft > 0 && leftover > 0 && !providers.isEmpty()) {
			iterationsLeft--;

			Share<ISPKProvider> selected = providers.get(rand.nextInt(providers.size()));
			ISPKProvider scapegoat = selected.target();

			long toUse = Math.min(leftover, scapegoat.getSendable());
			scapegoat.setTransferredSpk(toUse);
			leftover -= toUse;
		}
	}
}
