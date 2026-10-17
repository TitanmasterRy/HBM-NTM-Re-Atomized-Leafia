package com.leafia.contents.network.ff_duct.uninos;

import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A connector that also carries: the duct itself, as opposed to a machine on the end of one.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/uninos/IFFConductor.java}. Its node has
 * a connection on each of the six faces, written out by hand upstream with {@code Library}'s
 * {@code POS_X}...{@code NEG_Z} aliases in the order east, west, up, down, south, north; that
 * order is kept here, though nothing reads it.
 */
public interface IFFConductor extends IFFConnector {

	Direction[] ORDER = { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH };

	default FFNode createNode() {
		BlockPos pos = ((BlockEntity) this).getBlockPos();
		DirPos[] sides = new DirPos[ORDER.length];
		for(int i = 0; i < ORDER.length; i++) sides[i] = new DirPos(pos.relative(ORDER[i]), ORDER[i]);
		return new FFNode(pos).setConnections(sides);
	}
}
