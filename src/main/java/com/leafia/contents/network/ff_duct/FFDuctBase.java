package com.leafia.contents.network.ff_duct;

import javax.annotation.Nullable;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What both Forge-fluid ducts are: a block whose block entity is an {@link FFDuctTE}.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/FFDuctBase.java}, a {@code BlockContainer}
 * whose whole body is {@code createNewTileEntity}. Both ducts share the one block entity type.
 *
 * <p>{@link #updateConnections} is the one addition: a duct's drawn connections are blockstate here
 * rather than upstream's render-time {@code getActualState}, so a duct whose fluid changes has to be
 * told to recompute them. The shielded duct is a plain cube and has nothing to recompute.
 */
public class FFDuctBase extends Block implements EntityBlock {

	public FFDuctBase(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FFDuctTE(LeafiaBlockEntities.FF_DUCT.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide || type != LeafiaBlockEntities.FF_DUCT.get() ? null
				: (l, p, s, be) -> FFDuctTE.tick(l, p, s, (FFDuctTE) be);
	}

	/** This duct's fluid changed. Nothing to do for a duct whose look does not depend on it. */
	public void updateConnections(Level level, BlockPos pos) {}
}
