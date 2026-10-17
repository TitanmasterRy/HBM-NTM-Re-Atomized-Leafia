package com.leafia.contents.machines.misc.modular_turbine.generator;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `modular_turbine_3x3_generator`: the turbine's only generator.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/generator/MTGenerator3x3.java}.
 *
 * <p>A 3x3 disc three blocks deep along the shaft — the only turbine part longer than one block, so
 * the assembly walk meets it three times and counts it once. Its core block is the middle of the
 * three ({@code getOffset() = 1}). It separates stages, weighs 0.1, joins any size, and adds 5 to
 * the shaft's load. Drawn by the component renderer (T6) from its own model; until then it is
 * invisible and hidden from the creative tab.
 */
public class MTGenerator3x3 extends MTGeneratorBase {

	public static final MapCodec<MTGenerator3x3> CODEC = simpleCodec(MTGenerator3x3::new);

	public MTGenerator3x3(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTGenerator3x3> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 1;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.GENERATOR;
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
		return new int[] { 2, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new ModularTurbineComponentTE(LeafiaBlockEntities.MODULAR_TURBINE_COMPONENT.get(), pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.MODULAR_TURBINE_PROXY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public double generatorPower() {
		return 5;
	}
}
