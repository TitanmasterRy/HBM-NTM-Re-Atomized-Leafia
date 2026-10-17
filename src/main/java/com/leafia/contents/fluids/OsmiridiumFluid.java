package com.leafia.contents.fluids;

import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.contents.fluids.LeafiaLiquids.Liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The molten DFC component: a heavy, burning, digamma-bright puddle that eats through almost
 * anything and is gone a tenth of the time it is looked at.
 *
 * <p>Upstream: {@code com.leafia.contents.fluids.OsmiridiumFluid}, a Forge {@code Fluid} named
 * {@code corecomponent} (unlocalised {@code hbmfluid.corecomponent}) whose block is a
 * {@code BlockFluidFinite} of {@code Material.LAVA}. Nothing in the vendored source places it - it
 * is for the DFC's meltdown, which is unported - so it is reachable by bucket alone for now.
 *
 * <h2>The numbers, and what each became</h2>
 *
 * <table>
 * <tr><td>{@code setDensity(31200)}, {@code setViscosity(2000)}</td><td>{@link #DENSITY},
 *   {@link #VISCOSITY}</td></tr>
 * <tr><td>{@code setTemperature(3200+273)}</td><td>{@link #TEMPERATURE} 3,473 K - hot enough that its
 *   bucket makes lava's sound, as Forge chose by temperature</td></tr>
 * <tr><td>{@code tickRate} 30</td><td>{@link #TICK_RATE}</td></tr>
 * <tr><td>{@code Material.LAVA}</td><td>as {@link FluorideFluid}: burns, cannot be swum in</td></tr>
 * <tr><td>{@code IFluidFog}: 2.0, {@code 0x343435}</td><td>the client extension; 2.0 is lava's fog</td></tr>
 * <tr><td>{@code radiate(world, x, y, z, 60, 0, 85, 0)}</td><td>{@link #RADIATE_RANGE} and
 *   {@link #DIGAMMA}: no radiation, 85 digamma, no fire, within 60 blocks, every tick</td></tr>
 * <tr><td>{@code random.nextInt(10) == 0 -> air}</td><td>{@link #VANISH_ONE_IN}</td></tr>
 * <tr><td>{@code contaminate(DIGAMMA, CREATIVE, 1F/200)}</td><td>{@link #DIGAMMA_TOUCH}</td></tr>
 * </table>
 *
 * <h2>What it displaces</h2>
 *
 * Forge's {@code canDisplace} is overridden to ignore the usual rule: <em>any</em> non-liquid
 * block, whatever its material, gives way with odds of one in {@code (int)(sqrt(resistance) x 1.2)}
 * per attempt, and anything softer than that always does. {@code getExplosionResistance(null)} in
 * 1.12.2 is the stored resistance over five, which is the figure 1.21.1's block holds directly
 * (D-012), so the formula is carried over unchanged. Stone (resistance 6) gives way one try in two;
 * obsidian one in twenty-four. {@code displaceIfPossible} refuses liquids outright, and since
 * 1.12.2's flow only moved into what it had displaced, the fluid cannot flow into water or lava
 * here either.
 *
 * <p>Upstream rolls this twice per attempt - once to decide, once in {@code displaceIfPossible}.
 * Forge's flow code is not in the vendored reference, so this rolls once: a reconstruction, in
 * D-1079.
 */
public final class OsmiridiumFluid {

	private OsmiridiumFluid() {}

	public static final int DENSITY = 31200;
	public static final int VISCOSITY = 2000;
	/** {@code Fluid.setTemperature(3200+273)}. */
	public static final int TEMPERATURE = 3200 + 273;
	/** {@code OsmiridiumFluidBlock.tickRate}. */
	public static final int TICK_RATE = 30;
	/** {@code radiate(..., 60, 0, 85, 0)}: the range, and the digamma. */
	public static final double RADIATE_RANGE = 60;
	public static final float DIGAMMA = 85F;
	/** {@code random.nextInt(10) == 0}. */
	public static final int VANISH_ONE_IN = 10;
	/** {@code 1F/200}. */
	public static final float DIGAMMA_TOUCH = 1F / 200;
	/** {@code entity.setFire(3)}, in seconds. */
	public static final float FIRE_SECONDS = 3F;
	/** {@code attackEntityFrom(DamageSource.IN_FIRE, 4F)}. */
	public static final float FIRE_DAMAGE = 4F;
	/** {@code getFogColor}. */
	public static final int FOG_COLOR = 0x343435;
	/** {@code Math.sqrt(resistance) * 1.2}. */
	public static final double RESISTANCE_FACTOR = 1.2;

	static Liquid define() {
		return LeafiaLiquids.define("corecomponent", "osmiridium_fluid", OsmiridiumFluid::type,
				OsmiridiumSource::new, OsmiridiumFlowing::new, OsmiridiumFluidBlock::new,
				LeafiaLiquids.liquidProps(MapColor.FIRE),
				p -> p.tickRate(TICK_RATE));
	}

	static FluidType type() {
		return new FluidType(FluidType.Properties.create()
				.descriptionId("hbmfluid.corecomponent")
				.density(DENSITY)
				.viscosity(VISCOSITY)
				.temperature(TEMPERATURE)
				// Material.LAVA; the push is lava's, scaled down for the viscosity by vanilla's own
				// ratio (water 1000 -> 0.014, lava 6000 -> 0.014 / 6).
				.motionScale(0.014D * 1000 / VISCOSITY)
				.canSwim(false)
				.canDrown(false)
				.pathType(PathType.LAVA)
				.adjacentPathType(null)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA));
	}

	/** Upstream's tail of {@code updateTick}: radiate, then one time in ten, vanish. */
	public static void burnOff(Level level, BlockPos pos, RandomSource random) {
		if(level.isClientSide) return;
		ContaminationUtil.radiate(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RADIATE_RANGE, 0F, DIGAMMA);
		if(random.nextInt(VANISH_ONE_IN) == 0) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	/**
	 * The odds, as {@code nextInt}'s bound, that one attempt displaces a block of this explosion
	 * resistance: one in {@code max(1, (int)(sqrt(resistance) x 1.2))}, so a bound of 1 is a block
	 * that always gives way. Upstream tests {@code scaled < 1} first and returns true without
	 * rolling, which is the same answer.
	 */
	public static int oddsOneIn(float resistance) {
		return Math.max(1, (int) (float) (Math.sqrt(resistance) * RESISTANCE_FACTOR));
	}

	/**
	 * Upstream's {@code canDisplace}: air, anything soft enough, and otherwise one chance in
	 * {@link #oddsOneIn}. Liquids refuse.
	 */
	public static boolean canDisplace(RandomSource random, BlockState state) {
		if(state.isAir()) return true;
		if(state.getBlock() instanceof LiquidBlock) return false;
		return random.nextInt(oddsOneIn(state.getBlock().getExplosionResistance())) == 0;
	}

	public static class OsmiridiumSource extends FiniteFluid.Source {

		public OsmiridiumSource(BaseFlowingFluid.Properties properties) {
			super(properties);
		}

		@Override
		protected boolean canDisplace(Level level, BlockPos pos, BlockState state) {
			return OsmiridiumFluid.canDisplace(level.random, state);
		}

		@Override
		public void afterFlow(Level level, BlockPos pos, RandomSource random) {
			burnOff(level, pos, random);
		}
	}

	public static class OsmiridiumFlowing extends FiniteFluid.Flowing {

		public OsmiridiumFlowing(BaseFlowingFluid.Properties properties) {
			super(properties);
		}

		@Override
		protected boolean canDisplace(Level level, BlockPos pos, BlockState state) {
			return OsmiridiumFluid.canDisplace(level.random, state);
		}

		@Override
		public void afterFlow(Level level, BlockPos pos, RandomSource random) {
			burnOff(level, pos, random);
		}
	}

	/** Upstream's {@code OsmiridiumFluidBlock}. */
	public static class OsmiridiumFluidBlock extends LiquidBlock {

		public OsmiridiumFluidBlock(FlowingFluid fluid, Properties properties) {
			super(fluid, properties);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
			LeafiaLiquids.lavaBurn(entity, pos);
			if(entity instanceof ServerPlayer player && (player.isSpectator() || player.isCreative())) return;
			entity.makeStuckInBlock(state, ConcreteFluid.WEB);
			entity.igniteForSeconds(FIRE_SECONDS);
			entity.hurt(level.damageSources().inFire(), FIRE_DAMAGE);
			if(!level.isClientSide && entity instanceof LivingEntity living)
				ContaminationUtil.contaminate(living, HazardType.DIGAMMA, ContaminationType.CREATIVE, DIGAMMA_TOUCH);
		}
	}
}
