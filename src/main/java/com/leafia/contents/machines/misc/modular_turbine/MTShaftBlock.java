package com.leafia.contents.machines.misc.modular_turbine;

import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `modular_turbine_shaft`: a bare length of shaft, one block, for spacing a turbine out.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/MTShaftBlock.java}.
 *
 * <p>It is a separator to the assembly walk ({@code SHAFT_ONLY} is not one of the two types that
 * join a stage), size 1 and so compatible with every size, and it weighs 0.001 — a thousandth of
 * a separator, so a long run of shaft adds almost nothing to the rotor's inertia. Drawn by the
 * component renderer (T6), as a spinning rod and nothing else.
 */
public class MTShaftBlock extends ModularTurbineBlockBase {

	public static final MapCodec<MTShaftBlock> CODEC = simpleCodec(MTShaftBlock::new);

	public MTShaftBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTShaftBlock> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 0;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.SHAFT_ONLY;
	}

	@Override
	public int[] canConnectTo() {
		return new int[0];
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public double weight() {
		return 0.001;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 0, 0, 0, 0, 0, 0 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new ModularTurbineComponentTE(LeafiaBlockEntities.MODULAR_TURBINE_COMPONENT.get(), pos, state);
			case EXTRA, DUMMY -> null;
		};
	}
}
