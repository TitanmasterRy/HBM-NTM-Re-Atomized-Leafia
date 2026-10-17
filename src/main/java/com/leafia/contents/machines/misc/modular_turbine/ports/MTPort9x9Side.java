package com.leafia.contents.machines.misc.modular_turbine.ports;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.misc.modular_turbine.separator.MTSeparator9x9;
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
 * `modular_turbine_9x9_port_side`: a 9x9 separator that takes steam from either side, 5 blocks out at its bottom row.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ports/MTPort9x9Side.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Point a fluid identifier at it to choose the steam; the stage it belongs to then subscribes
 * through {@link #getConPos}, and the {@code EXTRA} dummies {@link #fillSpace} plants there are
 * fluid proxies onto its {@link MTComponentPortTE}.
 */
public class MTPort9x9Side extends MTSeparator9x9 implements IMTPortBlock {

	public static final MapCodec<MTPort9x9Side> CODEC = simpleCodec(MTPort9x9Side::new);

	public MTPort9x9Side(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTPort9x9Side> codec() {
		return CODEC;
	}

	@Override
	public DirPos[] getConPos(BlockPos pos, Direction facing) {
		return new DirPos[] {
				new DirPos(pos.relative(facing.getClockWise(), 5), facing.getClockWise()),
				new DirPos(pos.relative(facing.getClockWise(), -5), facing.getClockWise().getOpposite())
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
		Direction rot = dir.getClockWise();
		makeExtra(world, core.relative(rot, 4));
		makeExtra(world, core.relative(rot, -4));
	}
}
