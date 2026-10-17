package com.leafia.contents.machines.misc.modular_turbine.separator;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.handler.MultiblockHandlerXR;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `modular_turbine_7x7_separator`: ends a steam stage of a 7x7 turbine.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/separator/MTSeparator7x7.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>A 7x7 disc with its core block at the bottom centre, so the shaft runs 3 up.
 * On top of that sits a narrower cap row, 5 wide at height 6,
 * which is what makes the disc round rather than square.
 * Weight 0.1. Drawn by the component renderer (T6); until then it is invisible and hidden
 * from the creative tab.
 */
public class MTSeparator7x7 extends ModularTurbineBlockBase {

	public static final MapCodec<MTSeparator7x7> CODEC = simpleCodec(MTSeparator7x7::new);

	public MTSeparator7x7(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTSeparator7x7> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 3;
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
		return 7;
	}

	@Override
	public double weight() {
		return 0.1;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 5, 0, 0, 0, 3, 3 };
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

	/** The narrower top row that rounds the disc off. Upstream's second {@code fillSpace}. */
	public static final int[] CAP = { 6, -6, 0, 0, 2, 2 };

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		MultiblockHandlerXR.fillSpace(world, pos.relative(dir, o), CAP, this, dir);
	}

	/** Room for the cap row as well as the disc, both measured from the core as upstream does. */
	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {
		BlockPos core = pos.relative(dir, o);
		if(!MultiblockHandlerXR.checkSpace(world, core, CAP, core, dir))
			return false;
		return MultiblockHandlerXR.checkSpace(world, core, getDimensions(), core, dir);
	}
}
