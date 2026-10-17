package com.leafia.contents.network.spk_cable.uninos;

/**
 * The common half of a provider and a receiver: a buffer of SPK with a ceiling.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/ISPKHandler.java}. The same
 * three accessors the base mod's {@code IEnergyHandlerMK2} has, under SPK's names. "SPK" is the
 * DFC's unit - upstream calls it spark - and is a plain {@code long}.
 */
public interface ISPKHandler extends ISPKConnector {

	long getSPK();

	void setSPK(long power);

	long getMaxSPK();
}
