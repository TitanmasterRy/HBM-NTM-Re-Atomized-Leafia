package com.leafia.contents.fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * A fluid that moves a fixed amount of itself around, the way Forge 1.12.2's {@code BlockFluidFinite}
 * did - and the way nothing in vanilla 1.21.1 does.
 *
 * <p>A vanilla fluid is a <em>shape</em>: a source feeds flow that thins with distance, and when the
 * source goes the flow drains away on its own. A finite fluid is a <em>quantity</em>. Each block holds
 * one to eight eighths ("quanta"), a bucket places eight, and every tick a block hands what it has to
 * the block below and then shares what is left with its lower neighbours. Nothing is created and
 * nothing drains: a puddle is exactly as much fluid as was poured, which is why concrete and the
 * core component both need their own way to end (see {@link ConcreteFluid} and
 * {@link OsmiridiumFluid}).
 *
 * <h2>Mapping</h2>
 *
 * The amount is carried in the one place 1.21.1 already keeps it: a source is eight quanta and a
 * flowing state is {@code LEVEL} one to seven, so {@code LiquidBlock}'s {@code LEVEL} property is
 * {@code 8 - quanta} and every vanilla renderer, bucket and height check works unchanged. Upstream's
 * {@code getStateForPlacement} - <em>always</em> a full block, whatever the player clicked - is a
 * bucket placing a source, which is vanilla's behaviour already.
 *
 * <h2>What is reconstructed, and from what</h2>
 *
 * Forge's {@code BlockFluidFinite} is not in the vendored reference (it lives in Forge, not NTM-CE), so
 * the algorithm below is written from its documented behaviour rather than copied: fall if you can,
 * fill the block below if it is the same fluid, then equalise sideways. The one place it is a
 * choice rather than a recollection is the sideways step, which here shares among the lower
 * neighbours until each would hold at least as much as the one it is filling, and gives the
 * remainder to the block it started from. A puddle therefore settles in a few ticks and never
 * oscillates. D-1079 records this as a reconstruction.
 *
 * <h2>Two consequences of overriding {@code tick}</h2>
 *
 * Vanilla's own flow never runs, so neither does its decay of flowing blocks without a source -
 * quantity is conserved. And {@link #isRandomlyTicking} is on, because {@code BlockFluidBase} called
 * {@code setTickRandomly(true)} on every fluid block: a settled puddle is woken by random ticks,
 * which is the <em>only</em> thing that ever ends a puddle of concrete.
 */
public abstract class FiniteFluid extends BaseFlowingFluid {

	/** {@code BlockFluidFinite}'s default {@code quantaPerBlock}, which neither subclass changes. */
	public static final int QUANTA = 8;

	protected FiniteFluid(Properties properties) {
		super(properties);
	}

	/** One to eight: how much of a block this state fills. */
	public static int quanta(FluidState state) {
		return state.isSource() ? QUANTA : state.getAmount();
	}

	/** The state holding {@code quanta} eighths - a source for all eight. */
	public FluidState withQuanta(int quanta) {
		return quanta >= QUANTA ? getSource(false) : getFlowing(quanta, false);
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
		return !isSame(fluidIn) && ClassicFluid.denser(fluidIn, this);
	}

	@Override
	protected boolean isRandomlyTicking() {
		return true;
	}

	/** Upstream's random tick is its {@code updateTick}: the same step, woken by chance. */
	@Override
	protected void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
		tick(level, pos, state);
	}

	/** Whether {@code state} is a block of this fluid. */
	protected final boolean isOwn(BlockState state) {
		return state.getBlock() instanceof LiquidBlock && isSame(state.getFluidState().getType());
	}

	@Override
	public void tick(Level level, BlockPos pos, FluidState scheduled) {
		if(flow(level, pos)) afterFlow(level, pos, level.random);
	}

	/**
	 * One step of the flow, without what follows it. False if there is no block of this fluid at
	 * {@code pos} to flow. Public so a test can take a step without also taking the chance a
	 * subclass adds after it.
	 */
	public final boolean flow(Level level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		if(!isOwn(here)) return false;
		int quanta = quanta(here.getFluidState());

		// Down first. A block of the same fluid below takes what it has room for; anything the fluid
		// can displace takes the lot, and this block empties.
		BlockPos below = pos.below();
		if(level.isInWorldBounds(below)) {
			BlockState under = level.getBlockState(below);
			if(isOwn(under)) {
				int held = quanta(under.getFluidState());
				int moved = Math.min(quanta, QUANTA - held);
				if(moved > 0) {
					put(level, below, held + moved);
					quanta -= moved;
				}
			} else if(canDisplace(level, below, under)) {
				displace(level, below, under);
				put(level, below, quanta);
				quanta = 0;
			}
		}

		if(quanta > 1) quanta = spreadSideways(level, pos, quanta);

		put(level, pos, quanta);
		return true;
	}

	/**
	 * Shares {@code quanta} with the lower horizontal neighbours and returns what this block keeps.
	 *
	 * <p>Candidates are the neighbours of the same fluid holding less than this block, and the ones
	 * it can displace (which hold nothing). They are taken lowest first, and one is admitted only
	 * if the share each would then hold exceeds what it holds now - so a neighbour that is already
	 * as full as the average is left alone rather than drained to feed another.
	 */
	private int spreadSideways(Level level, BlockPos pos, int quanta) {

		List<BlockPos> targets = new ArrayList<>();
		List<Integer> held = new ArrayList<>();
		for(Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos to = pos.relative(dir);
			if(!level.isInWorldBounds(to)) continue;
			BlockState state = level.getBlockState(to);
			int has;
			if(isOwn(state)) {
				has = quanta(state.getFluidState());
				if(has >= quanta) continue;
			} else if(canDisplace(level, to, state)) {
				has = 0;
			} else continue;
			targets.add(to);
			held.add(has);
		}
		if(targets.isEmpty()) return quanta;

		// Lowest first; the sort is stable, so ties keep the compass order above.
		List<Integer> order = new ArrayList<>();
		for(int i = 0; i < targets.size(); i++) order.add(i);
		order.sort(Comparator.comparingInt(held::get));

		int total = quanta, admitted = 0, share = 0;
		for(int n = 1; n <= order.size(); n++) {
			int sum = quanta;
			for(int i = 0; i < n; i++) sum += held.get(order.get(i));
			int each = sum / (n + 1);
			if(each > held.get(order.get(n - 1))) {
				admitted = n;
				total = sum;
				share = each;
			}
		}
		if(admitted == 0) return quanta;

		for(int i = 0; i < admitted; i++) {
			BlockPos to = targets.get(order.get(i));
			BlockState state = level.getBlockState(to);
			if(!isOwn(state)) displace(level, to, state);
			put(level, to, share);
		}
		return total - admitted * share;
	}

	/** Sets {@code pos} to hold {@code quanta}, or to air for none, and wakes it. */
	protected final void put(Level level, BlockPos pos, int quanta) {
		if(quanta <= 0) {
			if(!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			return;
		}
		FluidState fluid = withQuanta(quanta);
		BlockState state = fluid.createLegacyBlock();
		// Only a change wakes the block. Rescheduling an unchanged one would tick every puddle
		// forever, where upstream lets a settled one fall silent until a random tick finds it.
		if(level.getBlockState(pos) == state) return;
		level.setBlock(pos, state, 3);
		level.scheduleTick(pos, fluid.getType(), getTickDelay(level));
	}

	/**
	 * What happens after a block has flowed, at the position it started the tick on - which may
	 * by now be empty. Upstream's subclasses override {@code updateTick}, call {@code super} and
	 * then do this, whether or not anything is still there. Public so a test can hand it a
	 * random source that is not chance.
	 */
	public void afterFlow(Level level, BlockPos pos, RandomSource random) {}

	/**
	 * Whether this fluid can take the place of {@code state}. Forge's default, which is
	 * {@code BlockFluidBase.canDisplace}: air, a block with no body, or another fluid this one is
	 * denser than.
	 *
	 * <p>A block that merely <em>holds</em> water - a waterlogged slab - is not "another fluid":
	 * replacing it would delete the slab, which upstream's fluid cannot do because 1.12.2 has no
	 * waterlogging.
	 */
	protected boolean canDisplace(Level level, BlockPos pos, BlockState state) {
		if(state.isAir()) return true;
		if(state.getBlock() instanceof LiquidBlock) return ClassicFluid.denser(this, state.getFluidState().getType());
		if(!state.getFluidState().isEmpty()) return false;
		return isDisplaceableBlock(state);
	}

	/**
	 * A block with no body to stop a fluid: vanilla's own list of what cannot hold a fluid, then
	 * {@code Material.blocksMovement()} as {@code blocksMotion()}.
	 */
	protected static boolean isDisplaceableBlock(BlockState state) {
		Block block = state.getBlock();
		if(block instanceof DoorBlock || state.is(BlockTags.SIGNS) || state.is(Blocks.LADDER)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BUBBLE_COLUMN)
				|| state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
				|| state.is(Blocks.END_GATEWAY) || state.is(Blocks.STRUCTURE_VOID)) return false;
		return !state.blocksMotion();
	}

	/** Clears {@code state} out of the way, dropping what it would drop - Forge's {@code displaceIfPossible}. */
	protected void displace(Level level, BlockPos pos, BlockState state) {
		if(state.isAir() || state.getBlock() instanceof LiquidBlock) return;
		Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
	}

	public static class Source extends FiniteFluid {

		public Source(Properties properties) {
			super(properties);
		}

		@Override
		public int getAmount(FluidState state) {
			return QUANTA;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}

	public static class Flowing extends FiniteFluid {

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
