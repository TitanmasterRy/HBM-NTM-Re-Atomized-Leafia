package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import com.hbm.handler.MultiblockHandlerXR;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * `modular_turbine_2x2_flywheel`: 120 of rotor inertia in a 2x2 disc, and a grinder - see {@link MTFlywheelTE}.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/flywheel/MTFlywheel2x2.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Shaft 1 up from the core block, weight 120, joins only its own size.
 * 1 narrower cap row on top round the disc off.
 * Drawn by the component renderer (T6); until then it is invisible and hidden from the creative tab.
 */
public class MTFlywheel2x2 extends MTFlywheelBase {

	public static final MapCodec<MTFlywheel2x2> CODEC = simpleCodec(MTFlywheel2x2::new);

	public MTFlywheel2x2(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTFlywheel2x2> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 1;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.FLYWHEEL;
	}

	@Override
	public int[] canConnectTo() {
		return new int[] { 2 };
	}

	@Override
	public int size() {
		return 2;
	}

	@Override
	public double weight() {
		return 120;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 0, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** The narrower rows that round the disc off, in upstream's fill order. */
	public static final int[][] CAPS = { { 2, -2, 0, 0, 0, 0 } };

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
