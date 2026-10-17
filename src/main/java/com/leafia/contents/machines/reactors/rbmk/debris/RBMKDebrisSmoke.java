package com.leafia.contents.machines.reactors.rbmk.debris;

import com.hbm.blocks.machine.rbmk.RBMKDebris;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The centre of a melted RBMK: the base mod's debris, which smokes.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/debris/RBMKDebrisSmoke.java}, an
 * {@code RBMKDebris} that is an {@code ITileEntityProvider} for {@link RBMKDebrisSmokeTE}. The constructor's
 * move from {@code ModBlocks.ALL_BLOCKS} to {@code AddonBlocks.ALL_BLOCKS} is 1.12.2 registration
 * bookkeeping and dissolves into {@code LeafiaBlocks.pribris_smoke}. Because it is an {@link RBMKDebris},
 * the base mod's digamma debris may eat it as it eats the rest of the pile, exactly as upstream's.
 *
 * <p>Nothing places it but the RBMK overhaul's meltdown hook, which also gives it its size; one placed by
 * hand has size 0 and does not smoke (D-3018).
 */
public class RBMKDebrisSmoke extends RBMKDebris implements EntityBlock {

	public RBMKDebrisSmoke(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RBMKDebrisSmokeTE(LeafiaBlockEntities.PRIBRIS_SMOKE.get(), pos, state);
	}

	/** Only the client ticks: the smoke is all it does, and upstream's server tick only re-sent the size. */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(!level.isClientSide || type != LeafiaBlockEntities.PRIBRIS_SMOKE.get()) return null;
		return (BlockEntityTicker<T>) (BlockEntityTicker<RBMKDebrisSmokeTE>) (l, p, s, be) -> be.clientTick();
	}
}
