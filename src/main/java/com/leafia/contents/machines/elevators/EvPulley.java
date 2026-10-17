package com.leafia.contents.machines.elevators;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The winch block at the head of a shaft — a 1×1×3 machine whose core drives the cabin and the
 * counterweight.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvPulley.java}. The behaviour is all
 * in {@link EvPulleyTE}; this is placement, shape and the proxy.
 *
 * <h2>The extra block, and why power reaches the core through it</h2>
 *
 * {@code fillSpace} places one <em>extra</em> block one step anticlockwise of the facing, on top of
 * the dummies the base class already lays down. That extra carries a
 * {@link BlockEntityProxyCombo} rather than nothing, which is what lets a cable touch the side of
 * the pulley and have the power arrive at the core — the proxy forwards it.
 *
 * <p>Upstream builds that proxy as {@code new TileEntityProxyCombo(false, true, false)}: power
 * only, no fluid and no inventory. This port has the same class as
 * {@link BlockEntityProxyCombo}, partial but with power forwarding done (base D-068).
 *
 * <p>It carries the addon's own {@link LeafiaBlockEntities#EV_PULLEY_PROXY} type and not the base
 * mod's shared {@code MULTIBLOCK_DUMMY} (D-3076). Sharing that type is how upstream does it and it
 * cannot work here: a 1.21.1 {@code BlockEntityType} validates the block it is placed on
 * ({@code validBlocks.contains(state.getBlock())}), and the base mod's type lists the base mod's
 * blocks, so placing a pulley threw and then crashed the server on the next chunk broadcast.
 */
public class EvPulley extends BlockDummyable {

	public static final MapCodec<EvPulley> CODEC = simpleCodec(EvPulley::new);

	public EvPulley(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends EvPulley> codec() {
		return CODEC;
	}

	/** Nothing up or down, one block each way horizontally: a flat 3×3 head. */
	@Override public int[] getDimensions() { return new int[] { 0, 0, 1, 1, 1, 1 }; }

	@Override public int getOffset() { return 0; }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new EvPulleyTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.EV_PULLEY_PROXY.get(), pos, state).power();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.EV_PULLEY.get(), EvPulleyTE::tick);
	}

	/**
	 * The dummies, plus one extra block one step anticlockwise of the facing.
	 *
	 * <p>Upstream's {@code dir.getRotation(ForgeDirection.UP)} is a quarter turn about Y, which is
	 * {@link Direction#getClockWise()} here — and the offset is <em>subtracted</em>, so the extra
	 * lands on the anticlockwise side. {@link EvPulleyTE} subscribes to power on that same side,
	 * which is why the two have to agree.
	 */
	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		Direction d = dir.getClockWise();
		this.makeExtra(world, pos.relative(dir, o).relative(d.getOpposite()));
	}

	/*
	 * Upstream's isOpaqueCube/isFullCube false are noOcclusion() at registration, as with every
	 * other addon machine.
	 */
}
