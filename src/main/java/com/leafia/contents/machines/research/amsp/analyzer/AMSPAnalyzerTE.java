package com.leafia.contents.machines.research.amsp.analyzer;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The anti-mass spectrometer's analyzer: a block entity with nothing in it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/research/amsp/analyzer/AMSPAnalyzerTE.java}, an empty
 * {@code extends TileEntity}. It exists so the renderer has something to be bound to.
 */
public class AMSPAnalyzerTE extends BlockEntity {

	public AMSPAnalyzerTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.AMSP_ANALYZER.get(), pos, state);
	}

	public AMSPAnalyzerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
