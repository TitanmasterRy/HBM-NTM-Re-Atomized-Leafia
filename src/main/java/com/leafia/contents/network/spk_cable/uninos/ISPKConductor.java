package com.leafia.contents.network.spk_cable.uninos;

import com.hbm.util.DirPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that is a length of SPK network: it owns a node and joins it to its neighbours.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/uninos/ISPKConductor.java}.
 * {@code createNode} is the same six connection points in the same order (+X, -X, +Y, -Y, +Z,
 * -Z), now built from the {@link Direction} each one faces rather than by writing out the offsets
 * and the {@code Library.POS_X ... NEG_Z} aliases.
 */
public interface ISPKConductor extends ISPKConnector {

	default SPKNode createNode() {

		BlockPos pos = ((BlockEntity) this).getBlockPos();

		return new SPKNode(pos).setConnections(
				new DirPos(pos.relative(Direction.EAST), Direction.EAST),
				new DirPos(pos.relative(Direction.WEST), Direction.WEST),
				new DirPos(pos.relative(Direction.UP), Direction.UP),
				new DirPos(pos.relative(Direction.DOWN), Direction.DOWN),
				new DirPos(pos.relative(Direction.SOUTH), Direction.SOUTH),
				new DirPos(pos.relative(Direction.NORTH), Direction.NORTH));
	}
}
