package com.leafia.contents.machines.misc.modular_turbine.ports;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.misc.modular_turbine.separator.MTSeparator7x7;
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
 * `modular_turbine_7x7_port_inline`: a 7x7 separator that takes steam along the shaft itself, so two in a row can make two stages without a separator between them.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ports/MTPort7x7Inline.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Point a fluid identifier at it to choose the steam; the stage it belongs to then subscribes
 * through {@link #getConPos}, and the {@code EXTRA} dummies {@link #fillSpace} plants there are
 * fluid proxies onto its {@link MTComponentPortTE}.
 */
public class MTPort7x7Inline extends MTSeparator7x7 implements IMTPortBlock {

	public static final MapCodec<MTPort7x7Inline> CODEC = simpleCodec(MTPort7x7Inline::new);

	public MTPort7x7Inline(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTPort7x7Inline> codec() {
		return CODEC;
	}

	@Override
	public DirPos[] getConPos(BlockPos pos, Direction facing) {
		return new DirPos[] {
				new DirPos(pos.above(shaftHeight()).relative(facing), facing),
				new DirPos(pos.above(shaftHeight()).relative(facing, -1), facing.getOpposite())
		};
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.INLINE_PORT;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new MTComponentPortTE(LeafiaBlockEntities.MODULAR_TURBINE_PORT.get(), pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.MODULAR_TURBINE_PROXY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	/** Server only: upstream's {@code update()} does nothing on a client. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide || state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.MODULAR_TURBINE_PORT.get(), MTComponentPortTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		BlockPos core = pos.relative(dir, o);
		makeExtra(world, core.above(shaftHeight()));
	}
}
