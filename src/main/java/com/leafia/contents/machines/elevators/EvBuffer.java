package com.leafia.contents.machines.elevators;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * The shock absorber at the bottom of a shaft: a two-block spring that a falling cabin lands on
 * instead of exploding.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvBuffer.java}.
 *
 * <h2>The cushioning lives in the cabin, not here</h2>
 *
 * Nothing in this class or in {@link EvBufferTE} slows anything down. {@link ElevatorEntity}
 * scans the 3×3 column up to four blocks beneath itself for an {@code EvBuffer}, finds its core,
 * and scales its own downward motion by the distance to it — and separately declines to explode
 * on impact if a buffer was found. This block's whole contribution is <em>being findable</em>,
 * which is why it is two blocks tall and has a core.
 *
 * <p>So a buffer placed anywhere else in the world does nothing at all, and that is upstream's
 * behaviour rather than a gap.
 */
public class EvBuffer extends BlockDummyable {

	public static final MapCodec<EvBuffer> CODEC = simpleCodec(EvBuffer::new);

	/**
	 * Upstream's bounding box: a 0.6-wide post through the middle of the block, full height. The
	 * spring is drawn much bigger than this — see {@link EvBufferTE#getRenderBoundingBox()} — but
	 * what a player bumps into is the post.
	 */
	private static final VoxelShape SHAPE =
			net.minecraft.world.phys.shapes.Shapes.box(0.2, 0, 0.2, 0.8, 1, 0.8);

	public EvBuffer(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends EvBuffer> codec() {
		return CODEC;
	}

	/** Two up, nothing sideways. */
	@Override public int[] getDimensions() { return new int[] { 2, 0, 0, 0, 0, 0 }; }

	@Override public int getOffset() { return 0; }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new EvBufferTE(pos, state);
			case EXTRA, DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.EV_BUFFER.get(), EvBufferTE::tick);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/*
	 * Upstream's isOpaqueCube/isFullCube false are BlockBehaviour.Properties.noOcclusion() here,
	 * applied at registration in LeafiaBlocks like every other addon machine.
	 */
}
