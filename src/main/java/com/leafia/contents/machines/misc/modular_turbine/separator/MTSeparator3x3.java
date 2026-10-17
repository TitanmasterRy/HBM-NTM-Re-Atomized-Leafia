package com.leafia.contents.machines.misc.modular_turbine.separator;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `modular_turbine_3x3_separator`: ends a steam stage of a 3x3 turbine.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/separator/MTSeparator3x3.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>A 3x3 disc with its core block at the bottom centre, so the shaft runs 1 up.
 * Weight 0.1. Drawn by the component renderer (T6); until then it is invisible and hidden
 * from the creative tab.
 */
public class MTSeparator3x3 extends ModularTurbineBlockBase {

	public static final MapCodec<MTSeparator3x3> CODEC = simpleCodec(MTSeparator3x3::new);

	public MTSeparator3x3(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTSeparator3x3> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 1;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.SEPARATOR;
	}

	@Override
	public int[] canConnectTo() {
		return new int[] {};
	}

	@Override
	public int size() {
		return 3;
	}

	@Override
	public double weight() {
		return 0.1;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 2, 0, 0, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** Upstream's {@code meta >= 12} is the core and {@code meta >= extra} a fluid proxy. */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new ModularTurbineComponentTE(LeafiaBlockEntities.MODULAR_TURBINE_COMPONENT.get(), pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.MODULAR_TURBINE_PROXY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}
}
