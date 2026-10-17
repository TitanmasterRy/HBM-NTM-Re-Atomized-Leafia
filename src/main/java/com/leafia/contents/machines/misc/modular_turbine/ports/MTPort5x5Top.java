package com.leafia.contents.machines.misc.modular_turbine.ports;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.misc.modular_turbine.separator.MTSeparator5x5;
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
 * `modular_turbine_5x5_port_top`: a 5x5 separator that takes steam from directly above its top row.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ports/MTPort5x5Top.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Point a fluid identifier at it to choose the steam; the stage it belongs to then subscribes
 * through {@link #getConPos}, and the {@code EXTRA} dummies {@link #fillSpace} plants there are
 * fluid proxies onto its {@link MTComponentPortTE}.
 */
public class MTPort5x5Top extends MTSeparator5x5 implements IMTPortBlock {

	public static final MapCodec<MTPort5x5Top> CODEC = simpleCodec(MTPort5x5Top::new);

	public MTPort5x5Top(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTPort5x5Top> codec() {
		return CODEC;
	}

	@Override
	public DirPos[] getConPos(BlockPos pos, Direction facing) {
		return new DirPos[] {
				new DirPos(pos.above(5), Direction.UP)
		};
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.PORT;
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
		makeExtra(world, core.above(4));
	}
}
