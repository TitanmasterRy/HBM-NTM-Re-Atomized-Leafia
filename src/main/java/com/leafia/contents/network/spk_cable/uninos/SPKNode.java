package com.leafia.contents.network.spk_cable.uninos;

import com.hbm.uninos.GenNode;
import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;

/**
 * A cable's node in the Unified Nodespace, on the SPK network.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/SPKNode.java}. Like
 * {@code AmatNode} it carries nothing of its own: the provider it is built with is what puts it
 * on an {@link SPKNet}, and {@code setConnections} is overridden only to return the narrower
 * type so a caller can chain.
 */
public class SPKNode extends GenNode<SPKNet> {

	public SPKNode(BlockPos... positions) {
		super(SPKNet.THE_NETWORK_PROVIDER, positions);
		this.positions = positions;
	}

	@Override
	public SPKNode setConnections(DirPos... connections) {
		super.setConnections(connections);
		return this;
	}
}
