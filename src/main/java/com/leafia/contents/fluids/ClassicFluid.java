package com.leafia.contents.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A fluid that flows the way Forge 1.12.2's {@code BlockFluidClassic} did, which is the way vanilla
 * water does: a source block, seven steps of flow that thin out one level at a time, a search four
 * blocks deep for the nearest edge to run toward, and no new sources made from two old ones.
 *
 * <p>That is what {@link BaseFlowingFluid} already is, so this class adds one thing: <b>who gets
 * pushed out of the way</b>. Forge's {@code canDisplace} compared the two fluids' densities - a
 * fluid replaces another only if it is strictly denser, from any side - while vanilla's
 * {@code canBeReplacedWith} lets any fluid replace any other one from above. This is the
 * density rule, on this side of the exchange: <em>this</em> fluid gives way only to something
 * denser than itself. The other side, an addon fluid flowing <em>into</em> water or lava, is
 * decided by water's and lava's own rule and is not changed here (D-1079).
 *
 * <p>The two nested classes are {@link BaseFlowingFluid}'s own source and flowing halves, which
 * cannot be reused because they extend it directly rather than this.
 */
public abstract class ClassicFluid extends BaseFlowingFluid {

	protected ClassicFluid(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
		return !isSame(fluidIn) && denser(fluidIn, this);
	}

	/** Whether {@code a} is strictly denser than {@code b} - Forge's whole displacement test. */
	public static boolean denser(Fluid a, Fluid b) {
		return a.getFluidType().getDensity() > b.getFluidType().getDensity();
	}

	public static class Source extends ClassicFluid {

		public Source(Properties properties) {
			super(properties);
		}

		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}

	public static class Flowing extends ClassicFluid {

		public Flowing(Properties properties) {
			super(properties);
			registerDefaultState(getStateDefinition().any().setValue(FlowingFluid.LEVEL, 7));
		}

		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(FlowingFluid.LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(FlowingFluid.LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}
}
