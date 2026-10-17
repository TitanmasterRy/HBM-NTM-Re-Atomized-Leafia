package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import com.hbm.handler.MultiblockHandlerXR;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * `modular_turbine_5x5_flywheel`: 320 of rotor inertia in a 5x5 disc, and a grinder - see {@link MTFlywheelTE}.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/flywheel/MTFlywheel5x5.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Shaft 2 up from the core block, weight 320, joins only its own size.
 * 1 narrower cap row on top round the disc off.
 * Drawn by the component renderer (T6); until then it is invisible and hidden from the creative tab.
 */
public class MTFlywheel5x5 extends MTFlywheelBase {

	public static final MapCodec<MTFlywheel5x5> CODEC = simpleCodec(MTFlywheel5x5::new);

	public MTFlywheel5x5(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTFlywheel5x5> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 2;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.FLYWHEEL;
	}

	@Override
	public int[] canConnectTo() {
		return new int[] { 5 };
	}

	@Override
	public int size() {
		return 5;
	}

	@Override
	public double weight() {
		return 320;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 3, 0, 0, 0, 2, 2 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** The narrower rows that round the disc off, in upstream's fill order. */
	public static final int[][] CAPS = { { 4, -4, 0, 0, 1, 1 } };

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		for(int[] cap : CAPS)
			MultiblockHandlerXR.fillSpace(world, pos.relative(dir, o), cap, this, dir);
	}

	/** Room for every cap row as well as the disc, all measured from the core as upstream does. */
	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {
		BlockPos core = pos.relative(dir, o);
		for(int[] cap : CAPS) {
			if(!MultiblockHandlerXR.checkSpace(world, core, cap, core, dir))
				return false;
		}
		return MultiblockHandlerXR.checkSpace(world, core, getDimensions(), core, dir);
	}
}
