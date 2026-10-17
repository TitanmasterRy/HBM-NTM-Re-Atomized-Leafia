package com.leafia.contents.network.pipe_amat.uninos;

import com.hbm.uninos.GenNode;
import com.hbm.uninos.INetworkProvider;
import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;

/**
 * An antimatter pipe's node in the Unified Nodespace.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/uninos/AmatNode.java}. It carries
 * nothing of its own — the provider it is constructed with is what puts it on an
 * {@link AmatNet} rather than an ordinary fluid net, and that provider comes from
 * {@link AmatNet#getProvider}.
 *
 * <p>The {@code setConnections} override exists only to narrow the return type so a caller can
 * chain without casting, which is upstream's reason too.
 */
public class AmatNode extends GenNode<AmatNet> {

	public AmatNode(INetworkProvider<AmatNet> provider, BlockPos... positions) {
		super(provider, positions);
	}

	@Override
	public AmatNode setConnections(DirPos... connections) {
		super.setConnections(connections);
		return this;
	}
}
