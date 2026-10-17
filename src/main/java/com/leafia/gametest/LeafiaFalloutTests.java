package com.leafia.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.custom_hbm.contents.torex.LCETorex;
import com.custom_hbm.contents.torex.TorexMath;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.extprop.HbmLivingProps;
import com.leafia.config.LeafiaFeatureCondition;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.worldgen.AddonBiome;
import com.leafia.contents.worldgen.AddonBiomes;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.DigammaBackstabPacket;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.ForceSchizoPacket;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.NullEntity;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaFalloutVariants;
import com.leafia.overwrite_contents.LeafiaNukeSeams;
import com.leafia.savedata.FalloutSavedData;
import com.leafia.savedata.FalloutSavedData.FalloutData;
import com.leafia.savedata.FalloutSavedData.FalloutSyncPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.Music;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EXPL-A (D-3009): the digamma and endothermic fallout, the MK5's digamma mark, the digamma crater
 * biome, its nulls and its packets, fallout weather and the torex delegate.
 *
 * <p>Every static the tests flip ({@code DigammaCrater.schizoMode}, {@code LeafiaFeatures.falloutRain},
 * {@code LeafiaFeatures.leafiaTorex}) is flipped and restored inside one synchronous body (D-952). Nothing
 * is left in the world: the blasts run at a random far spot so a previous run cannot have prepared it
 * (D-2196), and fallout records are kept in detached lists, never the level's saved one.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFalloutTests {

	private static final String EMPTY = "empty";

	/** Answers {@code nextInt} and {@code nextDouble} from scripts and records each {@code nextInt} bound. */
	static final class Script extends LegacyRandomSource {
		private final int[] ints;
		private final double[] doubles;
		private int nextI, nextD;
		final List<Integer> bounds = new ArrayList<>();

		Script(int... ints) {
			this(ints, new double[0]);
		}

		Script(int[] ints, double[] doubles) {
			super(0L);
			this.ints = ints;
			this.doubles = doubles;
		}

		@Override
		public int nextInt(int bound) {
			bounds.add(bound);
			return nextI < ints.length ? ints[nextI++] : bound - 1;
		}

		@Override
		public double nextDouble() {
			return nextD < doubles.length ? doubles[nextD++] : 0.5;
		}
	}

	private static boolean on() {
		return LeafiaFeatures.digammaCrater;
	}

	/** A column of {@code n} stone at relative (x, 1..n, z) with air above; returns the absolute top. */
	private static BlockPos stoneColumn(GameTestHelper helper, int x, int z, int n) {
		for(int y = 1; y <= n; y++) helper.setBlock(x, y, z, Blocks.STONE);
		for(int y = n + 1; y <= n + 4; y++) helper.setBlock(x, y, z, Blocks.AIR);
		return helper.absolutePos(new BlockPos(x, n, z));
	}

	private static BlockState digammitite(int meta) {
		return LeafiaBlocks.digammitite.get().withMeta(meta);
	}

	/**
	 * Holds the chunks a far blast needs. Lapses by itself 6,000 ticks after it is taken, so a test that fails before
	 * {@link #release} leaves nothing loaded for long.
	 */
	private static final TicketType<ChunkPos> PRELOAD = TicketType.create("leafia_gametest_preload", Comparator.comparingLong(ChunkPos::toLong), 6000);

	/**
	 * Generates, on the server thread, every chunk within {@code radius} blocks of {@code centre} plus a ring, and holds
	 * them at full status.
	 *
	 * <p>Why (D-3042): the fallout rain only works a chunk once it and its four neighbours have arrived from the chunk
	 * system's worker threads, so its pace is set by world generation in <em>wall-clock</em> time. The test's timeout is
	 * counted in <em>ticks</em>, and the GameTest server does not wait between ticks ({@code waitUntilNextTick} only
	 * runs queued tasks), so 3,000 ticks is no fixed amount of generation time, and on a box that is also compiling it
	 * can be too little. The one red of this shape was "rain still falling, 34 chunks left" of the rain's 43 - nine
	 * done. Generated up front, the rain's every poll finds its chunk and only ticks remain.
	 */
	private static List<ChunkPos> preload(ServerLevel level, BlockPos centre, int radius) {
		ChunkPos c = new ChunkPos(centre);
		int r = (radius >> 4) + 2;
		List<ChunkPos> held = new ArrayList<>();
		for(int dx = -r; dx <= r; dx++) {
			for(int dz = -r; dz <= r; dz++) {
				ChunkPos cp = new ChunkPos(c.x + dx, c.z + dz);
				level.getChunkSource().addRegionTicket(PRELOAD, cp, 0, cp);
				held.add(cp);
			}
		}
		for(ChunkPos cp : held) level.getChunk(cp.x, cp.z);
		return held;
	}

	private static void release(ServerLevel level, List<ChunkPos> held) {
		for(ChunkPos cp : held) level.getChunkSource().removeRegionTicket(PRELOAD, cp, 0, cp);
		held.clear();
	}

	/** A far, chunk-aligned spot no earlier run can have touched. */
	private static BlockPos farSpot(GameTestHelper helper, int sign) {
		RandomSource r = helper.getLevel().random;
		int x = sign * (60_000 + r.nextInt(4000) * 16);
		int z = -sign * (60_000 + r.nextInt(4000) * 16);
		return new BlockPos(x + 8, 0, z + 8);
	}

	// --------------------------------------------------------------------------- the biome

	/**
	 * The biome is registered (its {@code leafia:feature} condition is met) with every number upstream's
	 * {@code BiomeProperties} and overrides gave, read back from the registry rather than from the JSON.
	 */
	@GameTest(template = EMPTY)
	public static void theCraterBiomeIsRegisteredWithUpstreamsNumbers(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatureCondition.isOn("digammaCrater") == LeafiaFeatures.digammaCrater, "the condition reads the switch");
		helper.assertTrue(!LeafiaFeatureCondition.isOn("noSuchFeature"), "an unknown switch is off");
		var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		var holder = registry.getHolder(DigammaCrater.KEY);
		if(!on()) {
			helper.assertTrue(holder.isEmpty(), "with the switch off the biome is not loaded");
			helper.succeed();
			return;
		}
		helper.assertTrue(holder.isPresent(), "the digamma crater is in the biome registry");
		Biome biome = holder.get().value();
		var fx = biome.getSpecialEffects();
		helper.assertTrue(fx.getSkyColor() == DigammaCrater.SKY_COLOR && fx.getSkyColor() == 0, "black sky, got " + fx.getSkyColor());
		helper.assertTrue(fx.getFogColor() == DigammaCrater.FOG_COLOR && fx.getFogColor() == 0, "black fog, got " + fx.getFogColor());
		helper.assertTrue(fx.getWaterColor() == 0xFF0000, "red water, got " + Integer.toHexString(fx.getWaterColor()));
		helper.assertTrue(fx.getGrassColorOverride().orElse(-1) == 0x666666, "grey grass");
		helper.assertTrue(fx.getFoliageColorOverride().orElse(-1) == 0x666666, "grey leaves");
		helper.assertTrue(!biome.hasPrecipitation(), "rain disabled");
		helper.assertTrue(biome.getBaseTemperature() == 0.5F, "1.12.2's default temperature, got " + biome.getBaseTemperature());
		helper.assertTrue(biome.getModifiedClimateSettings().downfall() == 0F, "rainfall 0");
		Music music = fx.getBackgroundMusic().orElse(null);
		helper.assertTrue(music != null && music.getMinDelay() == 12000 && music.getMaxDelay() == 24000 && music.replaceCurrentMusic(),
				"the silence: 12,000 to 24,000 ticks, replacing what plays");
		helper.assertTrue(music.getEvent().is(ResourceLocation.withDefaultNamespace("intentionally_empty")), "and it plays nothing");

		var spawns = biome.getMobSettings();
		helper.assertTrue(spawns.getMobs(MobCategory.CREATURE).isEmpty(), "spawnableCreatureList.clear()");
		int monsterWeight = 0;
		for(SpawnerData data : spawns.getMobs(MobCategory.MONSTER).unwrap()) monsterWeight += data.getWeight().asInt();
		helper.assertTrue(spawns.getMobs(MobCategory.MONSTER).unwrap().size() == 8 && monsterWeight == 515,
				"1.12.2's eight default monsters, weight 515, got " + monsterWeight);
		helper.assertTrue(spawns.getMobs(MobCategory.WATER_CREATURE).unwrap().size() == 1
				&& spawns.getMobs(MobCategory.AMBIENT).unwrap().size() == 1, "the squid and the bat stay");

		TagKey<Biome> swamp = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_swamp"));
		TagKey<Biome> wet = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_wet"));
		helper.assertTrue(holder.get().is(swamp) && holder.get().is(wet), "BiomeDictionary SWAMP and WET");
		helper.assertTrue(AddonBiomes.digamma != null && AddonBiomes.digamma.key.equals(DigammaCrater.KEY)
				&& AddonBiomes.of(holder.get()) == AddonBiomes.digamma, "the descriptor names it");
		helper.succeed();
	}

	/** Upstream's fog: blended over the 49 samples, start to 0 and end to 0.4, colour to opaque black. */
	@GameTest(template = EMPTY)
	public static void theCraterFogClosesInByHowMuchOfTheViewIsCrater(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		Map<AddonBiome, Float> half = Map.of(AddonBiomes.digamma, 0.5F);
		helper.assertTrue(AddonBiome.blendFog(half, 100F, 1) == 50F, "half the view: start 100 to 50");
		helper.assertTrue(AddonBiome.blendFog(half, 100F, 2) == 70F, "end 100 to 70");
		helper.assertTrue(AddonBiome.blendFog(half, 100F, 0) == 100F, "density unchanged");
		double[] col = AddonBiome.blendFogColor(half, 1, 0.5, 0.25);
		helper.assertTrue(col[0] == 0.5 && col[1] == 0.25 && col[2] == 0.125, "colour halfway to black");

		BlockPos p = helper.absolutePos(new BlockPos(2, 2, 2));
		Map<AddonBiome, Float> outside = AddonBiome.getBiomeRatios(helper.getLevel(), p);
		helper.assertTrue(outside.isEmpty(), "the test grid is not crater: " + outside);
		DigammaCrater.schizoMode = true;
		try {
			Map<AddonBiome, Float> everywhere = AddonBiome.getBiomeRatios(helper.getLevel(), p);
			helper.assertTrue(everywhere.size() == 1 && everywhere.get(AddonBiomes.digamma) == 1F, "schizo: all 49 are crater, got " + everywhere);
			helper.assertTrue(AddonBiome.blendFog(everywhere, 100F, 1) == 0F && AddonBiome.blendFog(everywhere, 100F, 2) == 40F,
					"start x 0, end x 0.4");
		} finally {
			DigammaCrater.schizoMode = false;
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------- the variants

	@GameTest(template = EMPTY)
	public static void theTwoFalloutKindsAreVariantsWithTheirBiomes(GameTestHelper helper) {

		if(!on()) {
			helper.assertTrue(EntityFalloutRain.variant(LeafiaFalloutVariants.DIGAMMA) == null, "off: no digamma variant");
			helper.succeed();
			return;
		}
		helper.assertTrue(EntityFalloutRain.variant(LeafiaFalloutVariants.DIGAMMA) == LeafiaFalloutVariants.DIGAMMA_VARIANT, "digamma registered");
		helper.assertTrue(EntityFalloutRain.variant(LeafiaFalloutVariants.ENDOTHERMIC) == LeafiaFalloutVariants.ENDOTHERMIC_VARIANT, "endothermic registered");

		ServerLevel level = helper.getLevel();
		EntityFalloutRain rain = new EntityFalloutRain(level);
		for(double dist : new double[] {0, 50, 99, 400}) {
			helper.assertTrue(LeafiaFalloutVariants.DIGAMMA_VARIANT.biomeChange(rain, dist, 10, null) == DigammaCrater.KEY,
					"digamma paints the crater at any distance and any scale, " + dist);
			helper.assertTrue(LeafiaFalloutVariants.ENDOTHERMIC_VARIANT.biomeChange(rain, dist, 500, null) == null,
					"endothermic paints nothing, " + dist);
		}

		LeafiaFalloutVariants.setEndothermic(rain);
		LeafiaFalloutVariants.setFloodHeight(rain, 71);
		CompoundTag tag = rain.saveWithoutId(new CompoundTag());
		EntityFalloutRain loaded = new EntityFalloutRain(level);
		loaded.load(tag);
		helper.assertTrue(loaded.getVariant() == LeafiaFalloutVariants.ENDOTHERMIC_VARIANT && LeafiaFalloutVariants.getFloodHeight(loaded) == 71,
				"the kind and the flood height are saved with the rain");

		EntityNukeExplosionMK5 mk5 = EntityNukeExplosionMK5.statFac(level, 10, 0, 0, 0);
		LeafiaFalloutVariants.setDigammaFallout(mk5);
		helper.assertTrue(LeafiaFalloutVariants.DIGAMMA.equals(mk5.getFalloutVariant()), "the MK5 carries the mark");
		mk5.clearChunkLoader();
		helper.succeed();
	}

	/**
	 * Digammitite by distance: the pass multiplies by five, so a column 1 percent out is 5 percent (level
	 * 5, the first rung, 7.5) and one 2.5 out is 12.5 (level 4, the second, 15). Three solid blocks deep
	 * and no further. A roll of 1 on {@code nextInt(3)} lights no fire and draws no {@code nextInt(5)}.
	 */
	@GameTest(template = EMPTY)
	public static void theDigammaPassTurnsRockToDigammititeFiveTimesCloser(GameTestHelper helper) {

		if(!on() || LeafiaBlocks.digammitite == null) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos top = stoneColumn(helper, 1, 1, 4);
		Script rand = new Script(1, 1, 1);
		LeafiaFalloutVariants.stompDigamma(level, top.getX(), top.getZ(), 1.0, rand);
		for(int i = 0; i < 3; i++)
			helper.assertTrue(level.getBlockState(top.below(i)) == digammitite(5), "level 5 digammitite at depth " + i + ", got " + level.getBlockState(top.below(i)));
		helper.assertTrue(level.getBlockState(top.below(3)).is(Blocks.STONE), "the fourth is left: three solid blocks deep");
		helper.assertTrue(level.getBlockState(top.above()).isAir(), "no fire");
		helper.assertTrue(rand.bounds.equals(List.of(3, 3, 3)), "one fire roll per block, no placement roll, got " + rand.bounds);

		BlockPos top2 = stoneColumn(helper, 3, 1, 4);
		LeafiaFalloutVariants.stompDigamma(level, top2.getX(), top2.getZ(), 2.5, new Script(1, 1, 1));
		helper.assertTrue(level.getBlockState(top2) == digammitite(4), "12.5 percent is level 4, got " + level.getBlockState(top2));
		helper.succeed();
	}

	/** Digamma fire: one block in three (or any flammable one) within 65 percent, then one time in five on air. */
	@GameTest(template = EMPTY)
	public static void theDigammaPassLightsDigammaFireOneBlockInThree(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos lit = stoneColumn(helper, 1, 1, 4);
		Script yes = new Script(0, 0);
		LeafiaFalloutVariants.stompDigamma(level, lit.getX(), lit.getZ(), 1.0, yes);
		helper.assertTrue(level.getBlockState(lit.above()).is(ModBlocks.fire_digamma.get()), "0 then 0 lights it, got " + level.getBlockState(lit.above()));
		helper.assertTrue(yes.bounds.subList(0, 2).equals(List.of(3, 5)), "nextInt(3) then nextInt(5), got " + yes.bounds);
		level.setBlock(lit.above(), Blocks.AIR.defaultBlockState(), 3);

		BlockPos unlit = stoneColumn(helper, 3, 1, 4);
		LeafiaFalloutVariants.stompDigamma(level, unlit.getX(), unlit.getZ(), 1.0, new Script(0, 1));
		helper.assertTrue(level.getBlockState(unlit.above()).isAir(), "0 then 1 does not");

		// past 65 percent (13.1 x 5 = 65.5) there is no fire roll at all; the rock turns to ash instead
		BlockPos far = stoneColumn(helper, 1, 3, 4);
		Script none = new Script(0, 0, 0);
		LeafiaFalloutVariants.stompDigamma(level, far.getX(), far.getZ(), 13.1, none);
		helper.assertTrue(!none.bounds.contains(3) && !none.bounds.contains(5), "no fire roll past 65 percent, got " + none.bounds);
		helper.assertTrue(level.getBlockState(far).is(ModBlocks.ash_digamma.get()), "65.5 percent: ash, got " + level.getBlockState(far));
		helper.succeed();
	}

	/**
	 * Past the digammitite, ash where {@code nextInt(80) + 20 >= (dist / 100)^4 x 100}: at 95 percent (19 x 5)
	 * that is 81.45, so a roll of 61 keeps the rock and 62 turns it.
	 */
	@GameTest(template = EMPTY)
	public static void pastTheDigammititeRockTurnsToAshByTheFourthPower(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos top = stoneColumn(helper, 1, 1, 4);
		Script rand = new Script(61, 62);
		LeafiaFalloutVariants.stompDigamma(level, top.getX(), top.getZ(), 19, rand);
		helper.assertTrue(level.getBlockState(top).is(Blocks.STONE), "61 + 20 < 81.45 keeps the rock");
		helper.assertTrue(level.getBlockState(top.below()).is(ModBlocks.ash_digamma.get()), "62 + 20 >= 81.45 turns it, got " + level.getBlockState(top.below()));
		helper.assertTrue(rand.bounds.get(0) == 80 && rand.bounds.get(1) == 80, "the roll is nextInt(80), got " + rand.bounds);
		helper.succeed();
	}

	/** Past a fifth of its (five times larger) radius the digamma pass does nothing and draws nothing. */
	@GameTest(template = EMPTY)
	public static void pastAFifthOfItsRadiusTheDigammaPassDoesNothing(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos top = stoneColumn(helper, 1, 1, 4);
		Script rand = new Script();
		LeafiaFalloutVariants.stompDigamma(level, top.getX(), top.getZ(), 20.01, rand);
		helper.assertTrue(level.getBlockState(top).is(Blocks.STONE) && rand.bounds.isEmpty(), "20.01 x 5 > 100: untouched");
		LeafiaFalloutVariants.stompDigamma(level, top.getX(), top.getZ(), 20.0, rand);
		helper.assertTrue(!rand.bounds.isEmpty(), "20 x 5 = 100 is still inside");
		helper.succeed();
	}

	/**
	 * The endothermic pass: every non-solid block between the surface and the flood height becomes liquid
	 * oxygen (a solid one is left), and grass within 45 percent freezes. Cleared in the same tick, before the
	 * oxygen can flow into the next test.
	 */
	@GameTest(template = EMPTY)
	public static void theEndothermicPassFloodsToItsHeightAndFreezes(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		helper.setBlock(1, 0, 1, Blocks.OBSIDIAN);
		helper.setBlock(1, 1, 1, Blocks.DIRT);
		helper.setBlock(1, 2, 1, Blocks.GRASS_BLOCK);
		for(int y = 3; y <= 8; y++) helper.setBlock(1, y, 1, Blocks.AIR);
		BlockPos top = helper.absolutePos(new BlockPos(1, 2, 1));
		int flood = top.getY() + 4;
		try {
			helper.setBlock(1, 4, 1, Blocks.OBSIDIAN); // a solid block in the way, two above, too hard to fall
			LeafiaFalloutVariants.stompEndothermic(level, top.getX(), top.getZ(), 10, flood);
			// the obsidian is now the column's top: the flood runs from above each block passed
			helper.assertTrue(level.getBlockState(top).is(ModBlocks.frozen_grass.get()), "grass freezes within 45 percent, got " + level.getBlockState(top));
			helper.assertTrue(level.getBlockState(top.above(2)).is(Blocks.OBSIDIAN), "a solid block is not flooded");
			if(LeafiaLiquids.lox != null) {
				BlockState lox = LeafiaLiquids.lox.block.get().defaultBlockState();
				helper.assertTrue(level.getBlockState(top.above(1)) == lox, "liquid oxygen above the surface, got " + level.getBlockState(top.above(1)));
				helper.assertTrue(level.getBlockState(top.above(3)) == lox, "and on up to the flood height, got " + level.getBlockState(top.above(3)));
				helper.assertTrue(level.getBlockState(top.above(4)).isAir(), "not at it");
			}
		} finally {
			for(int y = 3; y <= 8; y++) helper.setBlock(1, y, 1, Blocks.AIR);
		}

		BlockPos past = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.setBlock(3, 0, 3, Blocks.OBSIDIAN);
		helper.setBlock(3, 1, 3, Blocks.DIRT);
		helper.setBlock(3, 2, 3, Blocks.GRASS_BLOCK);
		for(int y = 3; y <= 5; y++) helper.setBlock(3, y, 3, Blocks.AIR);
		LeafiaFalloutVariants.stompEndothermic(level, past.getX(), past.getZ(), 50, 0);
		helper.assertTrue(level.getBlockState(past).is(Blocks.GRASS_BLOCK), "past 45 percent the grass stays");
		helper.succeed();
	}

	/**
	 * A small digamma blast, end to end, at a far spot: the rod's MK5 marked digamma hands its fallout the
	 * variant at five times upstream's scale; the rain then paints the crater over that whole radius and
	 * stomps the inner fifth.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 3000, batch = "leafia_fallout_isolated")
	public static void aDigammaBlastPaintsACraterFiveTimesItsFallout(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 1);

		EntityFalloutRain[] seen = new EntityFalloutRain[1];
		EntityNukeExplosionMK5.FalloutHook watch = (nuke, rain) -> {
			if(nuke.getExtraData().getBoolean("leafia_gametest")) seen[0] = rain;
		};
		EntityNukeExplosionMK5.addFalloutHook(watch);

		EntityNukeExplosionMK5[] mk5 = new EntityNukeExplosionMK5[1];
		int[] surface = new int[1];
		List<ChunkPos> held = new ArrayList<>();
		helper.onEachTick(() -> {
			if(mk5[0] != null) return;
			level.getChunk(far);
			surface[0] = level.getHeight(Heightmap.Types.WORLD_SURFACE, far.getX(), far.getZ());
			mk5[0] = LeafiaFalloutVariants.setDigammaFallout(EntityNukeExplosionMK5.statFac(level, 4, far.getX() + 0.5, surface[0], far.getZ() + 0.5));
			held.addAll(preload(level, far, mk5[0].falloutScale() * LeafiaFalloutVariants.DIGAMMA_SCALE_MUL));
			mk5[0].getExtraData().putBoolean("leafia_gametest", true);
			level.addFreshEntity(mk5[0]);
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(mk5[0] != null && mk5[0].isRemoved() && seen[0] != null, "blast still running");
			helper.assertTrue(seen[0].isRemoved(), "rain still falling, " + seen[0].remainingChunks() + " chunks left");
			EntityNukeExplosionMK5.removeFalloutHook(watch);
			release(level, held);

			int upstream = mk5[0].falloutScale();
			helper.assertTrue(seen[0].getScale() == upstream * 5, "scale " + upstream + " x 5, got " + seen[0].getScale());
			helper.assertTrue(LeafiaFalloutVariants.DIGAMMA.equals(seen[0].getVariantId()), "the rain is digamma");

			int scale = seen[0].getScale();
			int inside = (int) (scale * 0.8);
			Holder<Biome> painted = level.getNoiseBiome(QuartPos.fromBlock(far.getX() + inside), QuartPos.fromBlock(surface[0]), QuartPos.fromBlock(far.getZ()));
			helper.assertTrue(painted.is(DigammaCrater.KEY), "the crater reaches " + inside + " blocks, got " + painted.unwrapKey());
			Holder<Biome> outside = level.getNoiseBiome(QuartPos.fromBlock(far.getX() + scale + 12), QuartPos.fromBlock(surface[0]), QuartPos.fromBlock(far.getZ()));
			helper.assertTrue(!outside.is(DigammaCrater.KEY), "and stops at " + scale);

			// Six blocks out, well clear of the blast's own hole, is 12 percent of 50: 60 after the times-five,
			// where nextInt(80) + 20 >= 0.6^4 x 100 = 12.96 always holds. Twelve to fourteen out is past a fifth.
			int ash = 0, grassBeyond = 0;
			StringBuilder seenTops = new StringBuilder();
			for(int[] d : new int[][] {{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
				int x = far.getX() + d[0], z = far.getZ() + d[1];
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1; // under any digamma fire
				BlockState s = level.getBlockState(new BlockPos(x, y, z));
				seenTops.append(' ').append(s.getBlock().builtInRegistryHolder().key().location().getPath()).append('@').append(y);
				if(s.is(ModBlocks.ash_digamma.get())) ash++;
			}
			for(int dx = 12; dx <= 14; dx++) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX() + dx, far.getZ()) - 1;
				if(level.getBlockState(new BlockPos(far.getX() + dx, y, far.getZ())).is(Blocks.GRASS_BLOCK)) grassBeyond++;
			}
			helper.assertTrue(ash == 4, "the ground six blocks out (60 percent) is digamma ash, " + ash + " of 4:" + seenTops);
			helper.assertTrue(grassBeyond == 3, "past a fifth of the radius it is untouched, " + grassBeyond + " of 3");
		});
	}

	// --------------------------------------------------------------------------- fallout weather

	/** The finish listener: a plain rain leaves weather with the switch on, never a digamma or endothermic one. */
	@GameTest(template = EMPTY)
	public static void aFinishedRainLeavesFalloutWeatherOnlyWithTheSwitchOn(GameTestHelper helper) {

		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, -1);
		FalloutSavedData saved = FalloutSavedData.forLevel(level);
		int before = saved.falloutMap.size();
		boolean was = LeafiaFeatures.falloutRain;
		List<FalloutData> added = new ArrayList<>();
		try {
			EntityFalloutRain rain = new EntityFalloutRain(level);
			rain.setPos(far.getX() + 0.5, 70, far.getZ() + 0.5);
			rain.setScale(42);

			LeafiaFeatures.falloutRain = false;
			FalloutSavedData.FINISH_LISTENER.onFinished(rain, level);
			helper.assertTrue(saved.falloutMap.size() == before, "off: no weather");

			LeafiaFeatures.falloutRain = true;
			EntityFalloutRain digamma = new EntityFalloutRain(level);
			LeafiaFalloutVariants.setDigammaFallout(digamma);
			FalloutSavedData.FINISH_LISTENER.onFinished(digamma, level);
			EntityFalloutRain endo = new EntityFalloutRain(level);
			LeafiaFalloutVariants.setEndothermic(endo);
			FalloutSavedData.FINISH_LISTENER.onFinished(endo, level);
			helper.assertTrue(saved.falloutMap.size() == before, "digamma and endothermic rains leave none");

			FalloutSavedData.FINISH_LISTENER.onFinished(rain, level);
			helper.assertTrue(saved.falloutMap.size() == before + 1, "on: one record");
			FalloutData data = saved.falloutMap.get(saved.falloutMap.size() - 1);
			added.add(data);
			helper.assertTrue(data.radius == 42 && data.pos.x == far.getX() + 0.5 && data.pos.z == far.getZ() + 0.5 && data.pos.y == 0,
					"the rain's centre and scale, got " + data.pos + " r " + data.radius);
			helper.assertTrue(saved.syncTimer == 0 && saved.isDirty(), "a sync is due and the save is dirty");
		} finally {
			LeafiaFeatures.falloutRain = was;
			saved.falloutMap.removeAll(added);
		}
		helper.succeed();
	}

	/** {@code 600 + r / 100 x 120,000} ticks; density {@code (1 - age/life) x min(sqrt(2 (1 - d/r)), 1)}; the heaviest wins. */
	@GameTest(template = EMPTY)
	public static void falloutWeatherThinsWithTimeAndDistance(GameTestHelper helper) {

		FalloutSavedData saved = FalloutSavedData.detached(null);
		FalloutData a = new FalloutData(new Vec3(0, 0, 0), 100);
		FalloutData b = new FalloutData(new Vec3(150, 0, 0), 100);
		helper.assertTrue(a.getLifetime() == 120_600, "a 100-block rain lives 120,600 ticks, got " + a.getLifetime());
		helper.assertTrue(FalloutData.getLifetimeFromRadius(10) == 12_600, "a 10-block one 12,600");
		helper.assertTrue(a.getDistanceMultiplier(new Vec3(0, 0, 0)) == 1 && a.getDistanceMultiplier(new Vec3(50, 0, 0)) == 1, "full to half the radius");
		helper.assertTrue(Math.abs(a.getDistanceMultiplier(new Vec3(75, 0, 0)) - Math.sqrt(0.5)) < 1e-12, "sqrt(2 x 0.25) at three quarters");
		a.setTimeElapsed(60_300);
		helper.assertTrue(a.getDensity() == 0.5, "half its life: half as dense");
		saved.falloutMap.add(a);
		saved.falloutMap.add(b);
		helper.assertTrue(saved.getDensest(new Vec3(10, 0, 0)) == a, "nearer a");
		helper.assertTrue(saved.getDensest(new Vec3(140, 0, 0)) == b, "nearer b");
		helper.assertTrue(saved.getDensest(new Vec3(400, 0, 0)) == null, "outside both");

		CompoundTag tag = saved.save(new CompoundTag(), helper.getLevel().registryAccess());
		FalloutSavedData loaded = FalloutSavedData.load(tag, helper.getLevel().registryAccess());
		helper.assertTrue(loaded.falloutMap.size() == 2 && loaded.falloutMap.get(0).getTimeElapsed() == 60_300
				&& loaded.falloutMap.get(1).pos.x == 150 && loaded.falloutMap.get(0).getLifetime() == 120_600, "saved and loaded");

		b.setTimeElapsed((int) b.getLifetime());
		saved.tick(RandomSource.create(1));
		helper.assertTrue(saved.falloutMap.size() == 1 && saved.falloutMap.get(0) == a, "a record past its life is dropped");
		helper.assertTrue(a.getTimeElapsed() == 60_301, "and each tick ages the rest");
		helper.succeed();
	}

	/** It dusts the ground inside its radius with fallout layers, and nowhere outside it. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void falloutWeatherDustsTheGroundInsideItsRadius(GameTestHelper helper) {

		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 1).offset(0, 0, -200_000);
		int radius = 40;
		for(int cx = -4; cx <= 4; cx++)
			for(int cz = -4; cz <= 4; cz++) level.getChunk((far.getX() >> 4) + cx, (far.getZ() >> 4) + cz);

		FalloutSavedData saved = FalloutSavedData.detached(level);
		FalloutData data = new FalloutData(new Vec3(far.getX(), 0, far.getZ()), radius);
		data.setTimeElapsed((int) data.getLifetime() - 1000); // old: the odds are near 11 in 50
		saved.falloutMap.add(data);
		RandomSource rand = RandomSource.create(42);
		for(int i = 0; i < 400; i++) saved.tick(rand);

		int inside = 0, outside = 0;
		List<BlockPos> found = new ArrayList<>();
		for(int dx = -radius - 24; dx <= radius + 24; dx++) {
			for(int dz = -radius - 24; dz <= radius + 24; dz++) {
				int x = far.getX() + dx, z = far.getZ() + dz;
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
				if(!level.getBlockState(top).is(ModBlocks.fallout.get())) continue;
				found.add(top);
				if(Math.hypot(x + 0.5 - far.getX(), z + 0.5 - far.getZ()) <= radius) inside++;
				else outside++;
			}
		}
		for(BlockPos p : found) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
		helper.assertTrue(inside > 0, "fallout lands inside the radius");
		helper.assertTrue(outside == 0, outside + " layers outside it");
		helper.succeed();
	}

	/** The sync carries every record's centre, radius and age. */
	@GameTest(template = EMPTY)
	public static void theFalloutSyncCarriesEveryRecord(GameTestHelper helper) {

		FalloutSavedData saved = FalloutSavedData.detached(null);
		FalloutData a = new FalloutData(new Vec3(-12.5, 0, 7.25), 31);
		a.setTimeElapsed(1234);
		saved.falloutMap.add(a);
		saved.falloutMap.add(new FalloutData(new Vec3(1e6, 0, -3), 0.5));
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new FalloutSyncPacket(saved));
		LeafiaCustomPacket.Opened opened = LeafiaCustomPacket.open(packet);
		helper.assertTrue(opened.prototype() instanceof FalloutSyncPacket, "the id is FALLOUT_SYNC's");
		List<FalloutData> read = FalloutSyncPacket.read(opened.buf());
		helper.assertTrue(read.size() == 2, "both records");
		helper.assertTrue(read.get(0).pos.x == -12.5 && read.get(0).pos.z == 7.25 && read.get(0).radius == 31 && read.get(0).getTimeElapsed() == 1234,
				"the first exactly");
		helper.assertTrue(read.get(1).pos.x == 1e6 && read.get(1).radius == 0.5, "the second exactly");
		helper.assertTrue(LeafiaPacketTypes.idOf(new DigammaBackstabPacket()) == LeafiaPacketTypes.FALLOUT_SYNC + 1
				&& LeafiaPacketTypes.idOf(new ForceSchizoPacket()) == LeafiaPacketTypes.FALLOUT_SYNC + 2, "upstream's order: sync, backstab, schizo");
		helper.succeed();
	}

	// --------------------------------------------------------------------------- the torex

	/**
	 * Off by default, the base cloud stands. With the switch on, {@code statFacStandard} and
	 * {@code statFacBale} raise the addon's torex in the nuke and balefire palettes instead, and any other
	 * type is left to the base mod.
	 */
	@GameTest(template = EMPTY)
	public static void theTorexDelegateRaisesLeafiasCloudWithTheSwitchOn(GameTestHelper helper) {

		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(new BlockPos(2, 2, 2));
		AABB box = new AABB(p).inflate(3);
		boolean was = LeafiaFeatures.leafiaTorex;
		List<LCETorex> before = new ArrayList<>(LCETorex.serverTorexes(level));
		try {
			LeafiaFeatures.leafiaTorex = false;
			EntityNukeTorex.statFacStandard(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 100F);
			List<EntityNukeTorex> base = level.getEntitiesOfClass(EntityNukeTorex.class, box);
			helper.assertTrue(base.size() == 1, "off: the base cloud, found " + base.size());
			base.forEach(EntityNukeTorex::discard);

			LeafiaFeatures.leafiaTorex = true;
			helper.assertTrue(!LeafiaNukeSeams.spawnTorex(level, 0, 0, 0, 100F, 7), "another type is the base mod's");
			if(LeafiaFeatures.torex) {
				EntityNukeTorex.statFacStandard(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 100F);
				EntityNukeTorex.statFacBale(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 100F);
				helper.assertTrue(level.getEntitiesOfClass(EntityNukeTorex.class, box).isEmpty(), "on: no base cloud");
				List<LCETorex> made = new ArrayList<>(LCETorex.serverTorexes(level));
				made.removeAll(before);
				helper.assertTrue(made.size() == 2, "two torexes, got " + made.size());
				helper.assertTrue(made.get(0).getType() == TorexMath.TYPE_NUKE && made.get(1).getType() == TorexMath.TYPE_BALEFIRE,
						"in the nuke and balefire palettes");
				helper.assertTrue(made.get(0).getScale() == TorexMath.scaleOf(TorexMath.nukeInput(100F)), "with the caller's scale");
				made.forEach(LCETorex::forget);
			}
		} finally {
			LeafiaFeatures.leafiaTorex = was;
			level.getEntitiesOfClass(EntityNukeTorex.class, box).forEach(EntityNukeTorex::discard);
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------- null

	/**
	 * A null on a client counts itself, watches, and is gone when the viewer is nearer than 20 or further
	 * than 200 (counted that tick), or has left the crater (not counted). On a server it lives 20 ticks.
	 * Schizo mode makes the test grid crater, and is put back.
	 */
	@GameTest(template = EMPTY)
	public static void aNullWatchesFromItsRingAndLeavesWhenTooCloseOrFar(GameTestHelper helper) {

		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(new BlockPos(2, 1, 2));
		DigammaCrater.schizoMode = true;
		try {
			NullEntity n = new NullEntity(level);
			n.clientSide = true;
			n.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			helper.assertTrue(n.tick(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 30.5), 1.62) && !n.dead, "30 out: counted and kept");
			helper.assertTrue(n.tick(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 19.5), 1.62) && n.dead, "19 out: counted, gone");
			NullEntity f = new NullEntity(level);
			f.clientSide = true;
			f.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			helper.assertTrue(f.tick(new Vec3(p.getX() + 200.5, p.getY(), p.getZ() + 0.5), 1.62) && !f.dead, "200 is kept");
			helper.assertTrue(f.tick(new Vec3(p.getX() + 201.5, p.getY(), p.getZ() + 0.5), 1.62) && f.dead, "201 is gone");

			NullEntity s = new NullEntity(level);
			s.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			for(int i = 0; i < 19; i++) helper.assertTrue(!s.tick(Vec3.ZERO, 1.62) && !s.dead, "a server null lives, tick " + i);
			s.tick(Vec3.ZERO, 1.62);
			helper.assertTrue(s.dead, "and dies on its 20th tick");
		} finally {
			DigammaCrater.schizoMode = false;
		}
		NullEntity out = new NullEntity(level);
		out.clientSide = true;
		out.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		helper.assertTrue(!out.tick(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 30.5), 1.62) && out.dead, "outside the crater: gone, uncounted");
		helper.succeed();
	}

	/** The head turns 10 degrees a tick and its pitch at most 40; the body lags the head by 75, and closes once it is still. */
	@GameTest(template = EMPTY)
	public static void aNullsHeadTurnsTenDegreesATickAndItsBodyFollows(GameTestHelper helper) {

		NullEntity n = new NullEntity(helper.getLevel());
		n.setPos(0, 0, 0);
		n.lookAt(-10, NullEntity.EYE_HEIGHT, 0); // due west of it: yaw 90
		helper.assertTrue(n.yHeadRot == 10F && n.xRot == 0F, "10 degrees toward 90, level, got " + n.yHeadRot + " / " + n.xRot);
		for(int i = 0; i < 8; i++) n.lookAt(-10, NullEntity.EYE_HEIGHT, 0);
		helper.assertTrue(n.yHeadRot == 90F, "there in nine ticks, got " + n.yHeadRot);
		n.lookAt(-10, 1000, 0);
		helper.assertTrue(n.xRot == -40F, "the pitch is reset and turned at most 40, got " + n.xRot);
		helper.assertTrue(NullEntity.turn(0, 350, 10) == -10F, "the short way round");

		NullEntity b = new NullEntity(helper.getLevel());
		b.yHeadRot = 100;
		b.tick(Vec3.ZERO, 0); // a server null: the body helper and the failsafe only
		helper.assertTrue(b.yBodyRot == 25F, "a head 100 degrees round pulls the body to within 75, got " + b.yBodyRot);
		for(int i = 0; i < 19; i++) b.tick(Vec3.ZERO, 0);
		helper.assertTrue(Math.abs(b.yBodyRot - 92.5F) < 1e-3, "still for 19 ticks: within 7.5, got " + b.yBodyRot);
		b.tick(Vec3.ZERO, 0);
		helper.assertTrue(b.yBodyRot == 100F, "still for 20 ticks: the body has caught up, got " + b.yBodyRot);
		helper.assertTrue(NullEntity.brightness(20) == 0F && NullEntity.brightness(30) == 0.25F && NullEntity.brightness(40) == 1F
				&& NullEntity.brightness(100) == 1F, "red-eyed at 20, white past 40");
		helper.succeed();
	}

	/** A spawn point is 60 + rand x 80 out, at the top block, on solid ground in the crater; water refuses. */
	@GameTest(template = EMPTY)
	public static void aNullSpawnsOnSolidGroundSixtyToAHundredAndFortyOut(GameTestHelper helper) {

		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertTrue(DigammaCrater.trySpawnNull(level, p.getX(), p.getZ(), new Script(new int[0], new double[] {0, 0})) == null,
				"outside the crater: none");
		DigammaCrater.schizoMode = true;
		BlockPos water = null;
		BlockState was = null;
		try {
			double vx = p.getX() + 0.5, vz = p.getZ() + 0.5;
			level.getChunk(BlockPos.containing(vx + 60, 0, vz));
			NullEntity near = DigammaCrater.trySpawnNull(level, vx, vz, new Script(new int[0], new double[] {0, 0}));
			int x = (int) Math.floor(vx + 60);
			helper.assertTrue(near != null && near.x == x + 0.5 && near.z == Math.floor(vz) + 0.5
					&& near.y == level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, (int) Math.floor(vz)), "radius 60 due east, on top");
			level.getChunk(BlockPos.containing(vx - 140, 0, vz));
			NullEntity farOne = DigammaCrater.trySpawnNull(level, vx, vz, new Script(new int[0], new double[] {1, 0.5}));
			helper.assertTrue(farOne != null && Math.abs(farOne.x - (Math.floor(vx - 140) + 0.5)) < 1e-9, "radius 140 due west, got " + (farOne == null ? null : farOne.x));

			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, (int) Math.floor(vz));
			water = new BlockPos(x, top, (int) Math.floor(vz));
			was = level.getBlockState(water);
			level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
			helper.assertTrue(DigammaCrater.trySpawnNull(level, vx, vz, new Script(new int[0], new double[] {0, 0})) == null, "not on water");
		} finally {
			DigammaCrater.schizoMode = false;
			if(water != null) level.setBlock(water, was, 2);
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------- the packets

	/** The backstab: four health by setHealth, and a shove from within 45 degrees of behind, 0.4 up on the ground. */
	@GameTest(template = EMPTY)
	public static void theBackstabShovesFromBehindAndTakesFourHealth(GameTestHelper helper) {

		Vec3 m = DigammaCrater.backstabMotion(new Vec3(0.2, 0.1, -0.2), 0, 0.5F, true);
		helper.assertTrue(Math.abs(m.x - 0.1) < 1e-6 && Math.abs(m.z - 0.3) < 1e-6, "facing south, shoved south: halved then 0.4, got " + m);
		helper.assertTrue(m.y == 0.4000000059604645D, "0.05 + 0.4 capped, got " + m.y);
		Vec3 air = DigammaCrater.backstabMotion(new Vec3(0, -0.3, 0), 90, 0F, false);
		double yaw = Math.toRadians(90 - 45);
		helper.assertTrue(Math.abs(air.x - (-Math.sin(yaw) * 0.4)) < 1e-6 && Math.abs(air.z - Math.cos(yaw) * 0.4) < 1e-6 && air.y == -0.3,
				"45 degrees off, no lift in the air, got " + air);

		Cow cow = EntityType.COW.create(helper.getLevel());
		cow.setHealth(10);
		DigammaBackstabPacket.hurt(cow);
		helper.assertTrue(cow.getHealth() == 6F, "four health, got " + cow.getHealth());
		ForceSchizoPacket.serve(cow);
		helper.assertTrue(Math.abs(HbmLivingProps.getDigamma(cow) - 0.2F) < 1e-6, "schizo's answer gives 0.2 digamma, got " + HbmLivingProps.getDigamma(cow));
		cow.discard();
		helper.succeed();
	}
}
