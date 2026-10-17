package com.leafia.contents.network.pipe_amat.uninos;

/**
 * A marker for anything that may sit on an antimatter pipe run.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/uninos/IAmatConnector.java}, and it
 * is empty there too. It exists so {@link IAmatConductor} has something to extend and so the
 * duct's connection test has a type to ask about, the same way the base mod's
 * {@code IBlockFluidDuct} does.
 */
public interface IAmatConnector {
}
