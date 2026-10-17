package com.leafia.contents.bomb.chud;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fat Chud's block entity, which only exists to be drawn (D-3022). Upstream's
 * {@code com/leafia/contents/bomb/chud/NukeChudTE.java} gives it an infinite render box and a 256-block view
 * distance; both are on {@link NukeChudRender}, where 1.21.1 asks for them.
 */
public class NukeChudTE extends BlockEntity {

	public NukeChudTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.NUKE_CHUD.get(), pos, state);
	}
}
