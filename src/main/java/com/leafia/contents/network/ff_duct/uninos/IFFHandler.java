package com.leafia.contents.network.ff_duct.uninos;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A machine end of a Forge-fluid duct run: a connector that is also a NeoForge fluid handler.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/IFFHandler.java}, empty there as
 * here. The duct's connection test asks for it ({@code LeafiaUtil.canConnectFF}): a block entity
 * that merely exposes a fluid handler is not enough, it has to declare itself one of these.
 */
public interface IFFHandler extends IFFConnector, IFluidHandler {
}
