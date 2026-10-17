package com.leafia.contents.fluids;

import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.blocks.fluid.GenericFiniteFluid;
import com.leafia.client.ResidueClient;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Balefire corium: the molten core of a balefire rod, a pool that creeps through the ground
 * faster than ordinary corium and is far hotter.
 *
 * <p>Upstream: {@code com.leafia.contents.fluids.BaleCoriumFluid}, a Forge {@code Fluid} named
 * {@code balecorium} (unlocalised {@code hbmfluid.balecorium}) and, nested in the same file,
 * {@code BaleCoriumFluidBlock}, which {@code extends CoriumFinite} - the base mod's corium block,
 * ported as {@code com.hbm.blocks.fluid.CoriumFinite} since the file was first deferred (D-1079).
 * Everything corium does - the spread, the pool setting into corium and corium cobblestone, the
 * radiation, the catch-and-ignite - is the base class's; this retunes three things, as upstream's
 * did.
 *
 * <h2>The numbers, and what each became</h2>
 *
 * <table>
 * <tr><td>{@code setDensity(31200)}, {@code setViscosity(500)}</td><td>{@link #DENSITY}, which
 *   the block reads for the swap with a lighter liquid below, as {@code BlockFluidBase} read its
 *   fluid's; {@link #VISCOSITY}, which nothing reads ({@code tickRate} is set outright)</td></tr>
 * <tr><td>{@code setTemperature(5000+273)}</td><td>{@link #TEMPERATURE} 5,273 K, which nothing
 *   reads either</td></tr>
 * <tr><td>{@code tickRate = 20} (the base's is 30)</td><td>{@link #TICK_RATE}: the block's tick
 *   delay - it spreads half as fast again</td></tr>
 * <tr><td>{@code canDisplace}</td><td>{@link BaleCoriumFluidBlock#canDisplace}</td></tr>
 * <tr><td>{@code randomDisplayTick}</td><td>{@link ResidueClient#baleCoriumDisplayTick}</td></tr>
 * </table>
 *
 * <p><b>A block, not a fluid (D-3052).</b> The base mod's corium is upstream's
 * {@code BlockFluidFinite} again (D-2173): a plain block whose quanta are a {@code level}
 * property, with no NeoForge {@code Fluid} behind it. Balefire corium extends it, so it is one
 * too - five quanta, a placed block full, what a rod leaves one quantum - and like corium it has
 * no bucket. Its tick rate and density reach the base class through D-2251's constructor.
 *
 * <p>The block is registered as {@code balecorium_fluid} - upstream's registry name, which its
 * field {@code fluid_balecorium} is not. The fuel rods name it by that field
 * ({@code setCorium("fluid_balecorium")}), and {@code LeafiaRodItem#corium} resolves that name to
 * this block (D-3004).
 *
 * <h2>Where it differs from the base mod's corium, and where it does not</h2>
 *
 * <ul>
 * <li><b>It sets like corium.</b> Upstream's {@code MixinCoriumFinite} rewrites the base class's
 *     tick for <em>every</em> corium pool, with a branch for this one (500 RAD instead of 50, balefire
 *     instead of fire, zetalite instead of chernobylite, baleonite in the rock beside it, and an
 *     explosion on meeting water). That mixin is {@code com.leafia.overwrite_contents.LeafiaCoriumBehavior}
 *     now, a base-mod corium behaviour behind {@code overhauls.explosionVfx} (D-3013); with it off a
 *     balecorium pool behaves as the base class does, which is corium's.
 * <li><b>The light.</b> Neither the fluid nor the block sets a luminosity upstream, so there is none;
 *     the base mod's corium glows at 10 because its 1.7.10 fluid said so. The emissive overlay
 *     textures ({@code corium_bale_*_e}) belong to a renderer the addon does not have.
 * <li><b>No fog, and no bucket.</b> Upstream's {@code IFluidFog} (density 2.0, {@link #FOG_COLOR})
 *     was the fluid's; with no fluid there is no camera fog, as there is none in the base mod's
 *     corium since D-2173. The bucket stood in for 1.12.2's universal one, and corium has none.
 * </ul>
 */
public final class BaleCoriumFluid {

	private BaleCoriumFluid() {}

	public static final int DENSITY = 31200;
	public static final int VISCOSITY = 500;
	/** {@code setTemperature(5000+273)}. */
	public static final int TEMPERATURE = 5000 + 273;
	/** {@code BaleCoriumFluidBlock.tickRate = 20}. */
	public static final int TICK_RATE = 20;
	/** Neither the fluid nor the block sets a luminosity upstream. */
	public static final int LUMINOSITY = 0;
	/** {@code CoriumFinite.getFogColor()}: 3421237, the colour the fog takes inside the pool. */
	public static final int FOG_COLOR = 3421237;
	/** {@code Math.pow(resistance, 0.35)}: the exponent that replaces corium's square root. */
	public static final double RESISTANCE_EXPONENT = 0.35;
	/** The base corium's blast resistance (upstream's {@code setResistance(500F)}, D-1230). */
	public static final float RESISTANCE = 300F;

	static DeferredBlock<BaleCoriumFluidBlock> define() {
		return LeafiaLiquids.BLOCKS.register("balecorium_fluid", () -> new BaleCoriumFluidBlock(properties()));
	}

	/** The base mod's {@code corium_block} properties, less its light. */
	static BlockBehaviour.Properties properties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.FIRE)
				.sound(SoundType.STONE)
				.strength(0.0F, RESISTANCE)
				.noCollission()
				.noOcclusion()
				.forceSolidOn()
				.liquid()
				.randomTicks()
				.pushReaction(PushReaction.BLOCK)
				.lightLevel(s -> GenericFiniteFluid.lightFor(s.getValue(GenericFiniteFluid.LEVEL), CoriumFinite.QUANTA, LUMINOSITY))
				.noLootTable();
	}

	/**
	 * Upstream's {@code canDisplace}, as a pure function of the roll and the block: one time in three
	 * it refuses outright; otherwise it flows into anything softer than {@code resistance^0.35 < 1}
	 * always, and into harder blocks one time in {@code (int)(resistance^0.35)}.
	 *
	 * <p>Upstream returns {@code true} for a liquid before testing resistance, but the base class's
	 * {@code displaceIfPossible} refuses a liquid before it ever asks, so a pool never flows into
	 * water or lava, or into another liquid block, and the answer for a liquid is no.
	 */
	public static boolean canDisplace(RandomSource random, BlockState state) {
		if(random.nextInt(3) == 0) return false;
		if(!state.getFluidState().isEmpty() || state.liquid()) return false;
		float scaled = (float) Math.pow(state.getBlock().getExplosionResistance(), RESISTANCE_EXPONENT);
		if(scaled < 1F) return true;
		return random.nextInt(Math.max(1, (int) scaled)) == 0;
	}

	/** Upstream's {@code BaleCoriumFluidBlock}. */
	public static class BaleCoriumFluidBlock extends CoriumFinite {

		/** Upstream's own {@code Random rand} - a field of the block, not the level's. */
		private RandomSource rand = RandomSource.create();

		/** For a test: swaps the block's own source of chance and returns the one it had. */
		public RandomSource swapRandom(RandomSource replacement) {
			RandomSource old = rand;
			rand = replacement;
			return old;
		}

		public BaleCoriumFluidBlock(Properties properties) {
			// qualified: inside this class the bare names are CoriumFinite's own inherited 30, 600,000 and 10
			super(properties, BaleCoriumFluid.TICK_RATE, BaleCoriumFluid.DENSITY, BaleCoriumFluid.LUMINOSITY);
		}

		@Override
		public boolean canDisplace(Level level, BlockPos pos, BlockState state, RandomSource random) {
			return BaleCoriumFluid.canDisplace(rand, state);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			ResidueClient.baleCoriumDisplayTick(level, pos, random);
		}
	}
}
