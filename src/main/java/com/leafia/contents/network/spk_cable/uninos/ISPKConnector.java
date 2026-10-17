package com.leafia.contents.network.spk_cable.uninos;

import net.minecraft.core.Direction;

/**
 * Anything an SPK cable will draw an arm towards: the cable itself, and every provider and
 * receiver (through {@link ISPKHandler}).
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/ISPKConnector.java}.
 * {@code ForgeDirection.UNKNOWN} was upstream's "no side"; a null {@link Direction} is that
 * here, the same translation the base mod's {@code IEnergyConnectorMK2} makes.
 *
 * <p>The cable's <em>drawn</em> connection ignores this method - it asks only whether the
 * neighbour is an {@code ISPKConnector} at all - while the network's subscription checks ask
 * it. So a machine that refuses a side still gets an arm drawn at it, and just does not
 * receive. Upstream's behaviour, kept.
 */
public interface ISPKConnector {

	/**
	 * @param dir the side of <em>this</em> block the connection arrives at
	 */
	default boolean canConnectSPK(Direction dir) {
		return dir != null;
	}
}
