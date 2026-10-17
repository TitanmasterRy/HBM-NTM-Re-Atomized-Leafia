package com.leafia.contents.network.pipe_amat.uninos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.uninos.INetworkProvider;
import com.leafia.contents.fluids.traits.FT_Magnetic;

import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidProviderMK2;
import api.hbm.fluidmk2.IFluidReceiverMK2;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The antimatter pipe's network: a fluid network that has to be <em>held</em>.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/uninos/AmatNet.java}. Upstream's
 * own comment on the bulk of that file is "this entire shit below is copy-pasted" — it is
 * NTM-CE's {@code FluidNetMK2} duplicated with one clause added. **Here it is a subclass
 * instead**, which is not a liberty: the copied half is base-mod arithmetic, D-1000 says our
 * value wins where the two differ, and subclassing is the only way to guarantee it stays that
 * way as the base mod moves. What is left below is exactly the addon's own contribution.
 *
 * <p>Two consequences worth stating, because they are what the subclass buys:
 *
 * <ul>
 *   <li>{@code Tuple.ObjectLongPair}, which upstream's copy uses for its provider and receiver
 *       lists, <b>is not needed at all</b>. It is a CE-only nested type with no counterpart in
 *       1.7.10 {@code hbm-src} (D-1013), and inheriting {@link FluidNetMK2}'s lists means
 *       inheriting its {@code Tuple.Pair} instead.</li>
 *   <li>The fastutil {@code Object2LongMap.fastIterator()} loops go with it — our
 *       {@code providerEntries}/{@code receiverEntries} are plain {@code Map}s.</li>
 * </ul>
 *
 * <h2>What the addon actually adds</h2>
 *
 * A magnetic field, expressed as a power budget the net spends every tick. {@link #power}
 * starts full, drains by {@link #consumption} per tick, and is refilled by the amat duct
 * charger. While it holds, a {@link FT_Magnetic} fluid is contained; when it runs out, or if
 * the fluid was never magnetic to begin with, {@link #checkExplode} says so and the provider is
 * dropped from the network.
 *
 * <p><b>"Explode" does not explode.</b> Upstream's world-explosion call is commented out in the
 * source, with the comment "fuck off", and the method just returns true — which the provider
 * loop turns into {@code iterator.remove()}. So the live behaviour is <em>disconnection</em>,
 * not detonation. That is reproduced exactly (D-021: a live behaviour is preserved and flagged,
 * not corrected), and the commented-out line is kept below so whoever restores it knows the
 * radius upstream intended.
 *
 * <h2>One network per fluid, separately from the base mod's</h2>
 *
 * {@link #getProvider} is the addon's own provider cache, keyed by fluid id. It matters that it
 * is not {@link FluidType#getNetworkProvider()}: an amat pipe carrying, say, deuterium must not
 * join the same network as an ordinary fluid duct carrying deuterium, or the containment would
 * be bypassed by running a plain pipe alongside. A separate provider per fluid gives the amat
 * pipes their own graph over the same nodespace, which is the mechanism {@code FluidType}
 * already uses to give every fluid its own network.
 */
public class AmatNet extends FluidNetMK2 {

	/** Field strength spent per tick. Upstream writes it as {@code 1000/20}, integer division, so 50. */
	public static long consumption = 1000 / 20;
	/** A full charge: one second of containment. */
	public static long maxPower = consumption * 20;

	/** How much field is left. A net starts full, which is upstream's. */
	public long power = maxPower;

	/**
	 * One provider per fluid id, so every fluid gets its own amat network — and so that two
	 * nodes carrying the same fluid resolve to the <em>same</em> provider object, which is what
	 * {@code UniNodespace} keys its node map on.
	 */
	public static final Map<Integer, INetworkProvider<AmatNet>> PROVIDERS = new HashMap<>();

	public static INetworkProvider<AmatNet> getProvider(FluidType type) {

		if(PROVIDERS.containsKey(type.getID()))
			return PROVIDERS.get(type.getID());

		INetworkProvider<AmatNet> provider = () -> new AmatNet(type);
		PROVIDERS.put(type.getID(), provider);
		return provider;
	}

	public AmatNet(FluidType type) {
		super(type);
	}

	/**
	 * Whether anything <em>other</em> than this provider is subscribed.
	 *
	 * <p>Upstream compares a provider against the <em>receiver</em> entries by identity, which
	 * only ever matches for a block entity that is both — a transceiver. Kept as it is: it is
	 * the check that stops a lone charged pipe from dropping itself the moment it is placed.
	 */
	private boolean isConnectedToAnywhere(IFluidProviderMK2 provider) {

		for(IFluidReceiverMK2 receiver : receiverEntries.keySet()) {
			if(receiver != provider) return true;
		}

		return false;
	}

	/**
	 * Whether this provider has lost containment, in which case the provider loop drops it.
	 *
	 * <p>The order of the tests is upstream's and is load-bearing: a non-antimatter fluid is
	 * judged on magnetism alone and never reaches the connection test, so an uncharged pipe
	 * carrying a non-magnetic fluid fails immediately whether or not anything is attached.
	 */
	public boolean checkExplode(IFluidProviderMK2 provider) {

		if(!getType().hasTrait(FT_Amat.class)) {
			if(power <= 0 || !getType().hasTrait(FT_Magnetic.class)) return true;
		}

		if(!isConnectedToAnywhere(provider)) return false;
		if(power > 0 && getType().hasTrait(FT_Magnetic.class)) return false;

		if(provider instanceof BlockEntity) {
			// Upstream, commented out in upstream and left commented here:
			// te.getWorld().newExplosion(null, te.getPos().getX(), te.getPos().getY(), te.getPos().getZ(), 5, true, true);
			return true;
		}

		return false;
	}

	/** The field drains first, before the early-outs — upstream's ordering, and it matters
	 * because an idle net with nothing attached still runs down. */
	@Override
	public void update() {
		power = Math.max(power - consumption, 0);
		super.update();
	}

	/**
	 * {@link FluidNetMK2#setupFluidProviders()} with {@link #checkExplode} added to the
	 * drop condition.
	 *
	 * <p>The body is otherwise the base mod's, character for character, and is duplicated only
	 * because the extra clause sits inside the loop's {@code if}. If that method ever grows a
	 * hook for the removal test, this override should become a use of it.
	 */
	@Override
	public void setupFluidProviders() {

		Iterator<Map.Entry<IFluidProviderMK2, Long>> iterator = providerEntries.entrySet().iterator();

		while(iterator.hasNext()) {
			Map.Entry<IFluidProviderMK2, Long> entry = iterator.next();
			if(currentTime - entry.getValue() > timeout || isBadLink(entry.getKey()) || checkExplode(entry.getKey())) { iterator.remove(); continue; }
			IFluidProviderMK2 provider = entry.getKey();
			int[] pressureRange = provider.getProvidingPressureRange(type);
			for(int p = pressureRange[0]; p <= pressureRange[1]; p++) {
				long available = Math.min(provider.getFluidAvailable(type, p), provider.getProviderSpeed(type, p));
				providers[p].add(new com.hbm.util.Tuple.Pair<>(provider, available));
				fluidAvailable[p] += available;
			}
		}
	}
}
