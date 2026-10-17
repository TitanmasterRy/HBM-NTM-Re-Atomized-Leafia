package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import com.mojang.serialization.MapCodec;

/**
 * `modular_turbine_3x3_flywheel`: 160 of rotor inertia in a 3x3 disc, and a grinder - see {@link MTFlywheelTE}.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/flywheel/MTFlywheel3x3.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Shaft 1 up from the core block, weight 160, joins only its own size.
 * Drawn by the component renderer (T6); until then it is invisible and hidden from the creative tab.
 */
public class MTFlywheel3x3 extends MTFlywheelBase {

	public static final MapCodec<MTFlywheel3x3> CODEC = simpleCodec(MTFlywheel3x3::new);

	public MTFlywheel3x3(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTFlywheel3x3> codec() {
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
		return new int[] { 3 };
	}

	@Override
	public int size() {
		return 3;
	}

	@Override
	public double weight() {
		return 160;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 2, 0, 0, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}
}
