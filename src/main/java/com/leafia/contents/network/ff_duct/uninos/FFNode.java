package com.leafia.contents.network.ff_duct.uninos;

import com.hbm.uninos.GenNode;
import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;

/**
 * A Forge-fluid duct's node in the Unified Nodespace.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/FFNode.java}. It carries nothing
 * of its own: the provider it is built with, {@link FFNet#PROVIDER}, is what puts it on an
 * {@link FFNet}. There is <b>one</b> provider for every fluid, unlike the base mod's and the amat
 * pipe's per-fluid ones, so ducts carrying two different fluids that touch share a graph; what
 * keeps the fluids apart is the duct's own {@code canConnect}, which refuses a stack of another
 * fluid. That is upstream's arrangement.
 *
 * <p>The {@code setConnections} override only narrows the return type, as upstream's does.
 */
public class FFNode extends GenNode<FFNet> {

	public FFNode(BlockPos... positions) {
		super(FFNet.PROVIDER, positions);
	}

	@Override
	public FFNode setConnections(DirPos... connections) {
		super.setConnections(connections);
		return this;
	}
}
