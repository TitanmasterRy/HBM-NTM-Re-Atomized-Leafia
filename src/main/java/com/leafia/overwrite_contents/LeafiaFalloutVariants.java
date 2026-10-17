package com.leafia.overwrite_contents;

import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.dev.LeafiaUtil;
import com.leafia.init.FalloutConfigInit;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The digamma and endothermic fallout, as the base mod's fallout variants, and the flags upstream kept on
 * the rain and the MK5 by mixin.
 *
 * <p>Upstream: {@code overwrite_contents/mixin/mod/hbm/MixinEntityFalloutRain} (two {@code @Unique}
 * column passes behind a {@code stompColumnToUpdates} head injection, a redirect of the crater biome and
 * three flags saved as {@code digammaFallout}/{@code endothermic}/{@code floodHeight}),
 * {@code MixinEntityNukeExplosionMK5} (one flag, and a redirect of the {@code spawnEntity} that hands it
 * to the rain with the rain's scale times five) and the two interfaces that exposed them,
 * {@code IMixinEntityFalloutRain} and {@code IMixinEntityNukeExploisonMK5}. D-1001: none of it is a mixin
 * here. The base mod's seams (D-2196) carry each piece:
 *
 * <table>
 * <tr><th>upstream</th><th>here</th></tr>
 * <tr><td>{@code leafia$setDigammaFallout()} on the rain</td><td>{@code rain.setVariant(}{@link #DIGAMMA}{@code )}</td></tr>
 * <tr><td>{@code leafia$setEndothermic()}</td><td>{@code rain.setVariant(}{@link #ENDOTHERMIC}{@code )}</td></tr>
 * <tr><td>{@code leafia$setFloodHeight(h)}</td><td>{@link #TAG_FLOOD_HEIGHT} in {@code rain.getVariantData()}</td></tr>
 * <tr><td>{@code setDigammaFallout()} on the MK5</td><td>{@code mk5.setFalloutVariant(}{@link #DIGAMMA}{@code )}</td></tr>
 * <tr><td>the MK5's {@code spawnEntity} redirect</td><td>{@link #beforeSpawn}, an {@code EntityNukeExplosionMK5.FalloutHook}</td></tr>
 * <tr><td>the {@code stompColumnToUpdates} injection</td><td>each variant's {@code stomp}</td></tr>
 * <tr><td>the {@code getBiomeChange} redirect</td><td>each variant's {@code biomeChange}</td></tr>
 * </table>
 *
 * The save keys move with them: a rain saves {@code variant} and {@code variantData}, an MK5
 * {@code falloutVariant}. Nothing persisted under upstream's names needs reading - this is a new world.
 *
 * <h2>The column passes</h2>
 *
 * Upstream's are NTM-CE's batched {@code stompColumnToUpdates}, which reads a chunk's sections and
 * collects writes into maps applied after the chunk. The base mod's sweep is 1.7.10's, column by column
 * in the world (D-815), and a variant's {@code stomp} runs in it, so the two passes are written in its
 * terms: from the {@code WORLD_SURFACE} heightmap down, through {@link EntityFalloutRain#convert} and
 * {@link EntityFalloutRain#dropRubble}, writing as they go. Every rule and number is upstream's; the
 * immediate writes change nothing a column reads back (each pass reads only blocks it has not yet
 * written, or reads a block whose conversion is solid either way), and the falling rubble is the base
 * mod's, which leaves block entities and multiblocks standing (D-815).
 */
public final class LeafiaFalloutVariants {

	private LeafiaFalloutVariants() {}

	public static final ResourceLocation DIGAMMA = LeafiaRef.rl("digamma");
	public static final ResourceLocation ENDOTHERMIC = LeafiaRef.rl("endothermic");

	/** Where the endothermic rain keeps its flood height: upstream's NBT key. */
	public static final String TAG_FLOOD_HEIGHT = "floodHeight";

	/** The MK5 hands a digamma rain {@code scale x 5}. */
	public static final int DIGAMMA_SCALE_MUL = 5;
	/** ...and the digamma column pass multiplies the distance by 5 back, so it stomps the inner fifth. */
	public static final double DIGAMMA_DIST_MUL = 5;
	/** Digamma fire within 65 percent (of the multiplied distance), as upstream's rubble radius. */
	public static final double DIGAMMA_FIRE_RADIUS = 65;
	/** {@code rand.nextInt(3) == 0 || flammable}, then {@code rand.nextInt(5) == 0} on air. */
	public static final int DIGAMMA_FIRE_ODDS = 3;
	public static final int DIGAMMA_FIRE_PLACE_ODDS = 5;
	/** Ash where {@code rand.nextInt(80) + 20 >= (dist / 100)^4 x 100}. */
	public static final int DIGAMMA_ASH_SPREAD = 80;
	public static final int DIGAMMA_ASH_FLOOR = 20;
	public static final double DIGAMMA_ASH_EXPONENT = 4.0;

	public static final EntityFalloutRain.Variant DIGAMMA_VARIANT = new EntityFalloutRain.Variant() {
		@Override
		public void stomp(EntityFalloutRain rain, ServerLevel level, int x, int z, double dist) {
			stompDigamma(level, x, z, dist, rain.getRandom());
		}

		@Override
		public ResourceKey<Biome> biomeChange(EntityFalloutRain rain, double dist, int scale, ResourceKey<Biome> original) {
			return DigammaCrater.KEY;
		}
	};

	public static final EntityFalloutRain.Variant ENDOTHERMIC_VARIANT = new EntityFalloutRain.Variant() {
		@Override
		public void stomp(EntityFalloutRain rain, ServerLevel level, int x, int z, double dist) {
			stompEndothermic(level, x, z, dist, getFloodHeight(rain));
		}

		@Override
		public ResourceKey<Biome> biomeChange(EntityFalloutRain rain, double dist, int scale, ResourceKey<Biome> original) {
			return null;
		}
	};

	/** The MK5 half: a blast marked digamma hands its rain {@code scale x 5}. The variant itself the MK5 already handed on. */
	public static final EntityNukeExplosionMK5.FalloutHook DIGAMMA_HOOK = LeafiaFalloutVariants::beforeSpawn;

	/** Registers both variants and the MK5 hook, with {@code features.digammaCrater} on. Idempotent. */
	public static void register() {
		if(!LeafiaFeatures.digammaCrater) return;
		EntityFalloutRain.registerVariant(DIGAMMA, DIGAMMA_VARIANT);
		EntityFalloutRain.registerVariant(ENDOTHERMIC, ENDOTHERMIC_VARIANT);
		EntityNukeExplosionMK5.removeFalloutHook(DIGAMMA_HOOK);
		EntityNukeExplosionMK5.addFalloutHook(DIGAMMA_HOOK);
	}

	// ---------------------------------------------------------------- the interfaces, as statics

	/** {@code IMixinEntityNukeExploisonMK5.setDigammaFallout}. */
	public static EntityNukeExplosionMK5 setDigammaFallout(EntityNukeExplosionMK5 mk5) {
		return mk5.setFalloutVariant(DIGAMMA);
	}

	/** {@code IMixinEntityFalloutRain.leafia$setDigammaFallout}: the rain only, not its scale. */
	public static void setDigammaFallout(EntityFalloutRain rain) {
		rain.setVariant(DIGAMMA);
	}

	/** {@code IMixinEntityFalloutRain.leafia$setEndothermic}. */
	public static void setEndothermic(EntityFalloutRain rain) {
		rain.setVariant(ENDOTHERMIC);
	}

	/** {@code IMixinEntityFalloutRain.leafia$setFloodHeight}: an absolute Y; nothing below it but solid ground is left dry. */
	public static void setFloodHeight(EntityFalloutRain rain, int height) {
		rain.getVariantData().putInt(TAG_FLOOD_HEIGHT, height);
	}

	public static int getFloodHeight(EntityFalloutRain rain) {
		return rain.getVariantData().getInt(TAG_FLOOD_HEIGHT);
	}

	static void beforeSpawn(EntityNukeExplosionMK5 nuke, EntityFalloutRain rain) {
		if(DIGAMMA.equals(nuke.getFalloutVariant())) rain.setScale(rain.getScale() * DIGAMMA_SCALE_MUL);
	}

	// ---------------------------------------------------------------- the column passes

	/**
	 * Upstream's {@code leafia$stompColumnToUpdatesDigamma}. {@code dist} is percent of the (five times
	 * larger) radius and is multiplied by five first, so past a fifth of it nothing happens. Down the
	 * column until three solid blocks have been passed: within 65 percent, one block in three - or any
	 * flammable one - lights digamma fire on the air above it one time in five; then digammitite by
	 * {@link FalloutConfigInit#digammaEntries}; failing that, a visible solid cube turns to digamma ash where
	 * {@code nextInt(80) + 20 >= (dist / 100)^4 x 100} (always out to 66 percent of the stomped radius,
	 * never certain at the edge); failing that, the rubble falls within 65 percent.
	 */
	public static void stompDigamma(ServerLevel level, int x, int z, double dist, RandomSource rand) {

		dist *= DIGAMMA_DIST_MUL;
		if(dist > 100) return;

		List<FalloutEntry> entries = FalloutConfigInit.digammaEntries;
		int solidDepth = 0;
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int floor = level.getMinBuildHeight();
		int ceiling = level.getMaxBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for(int y = top; y >= floor; y--) {

			if(solidDepth >= EntityFalloutRain.MAX_SOLID_DEPTH) return;

			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if(state.isAir() || state.is(ModBlocks.fallout.get())) continue;

			int upY = y + 1;
			if(dist < DIGAMMA_FIRE_RADIUS && (rand.nextInt(DIGAMMA_FIRE_ODDS) == 0 || state.isFlammable(level, pos, Direction.UP))) {
				if(upY < ceiling) {
					BlockPos up = new BlockPos(x, upY, z);
					if(level.getBlockState(up).isAir() && rand.nextInt(DIGAMMA_FIRE_PLACE_ODDS) == 0)
						level.setBlock(up, ModBlocks.fire_digamma.get().defaultBlockState(), 3);
				}
			}

			boolean transformed = false;
			FalloutEntry entry = EntityFalloutRain.convert(level, pos.immutable(), state, dist, entries);
			if(entry != null) {
				if(entry.isSolid()) solidDepth++;
				transformed = true;
			}

			if(!transformed && LeafiaUtil.isSolidVisibleCube(state, level, pos)
					&& rand.nextInt(DIGAMMA_ASH_SPREAD) + DIGAMMA_ASH_FLOOR >= Math.pow(dist / 100.0, DIGAMMA_ASH_EXPONENT) * 100.0) {
				if(y > floor) {
					level.setBlock(pos, ModBlocks.ash_digamma.get().defaultBlockState(), 3);
					solidDepth++;
				}
				transformed = true;
			}

			if(!transformed) EntityFalloutRain.dropRubble(level, pos.immutable(), state, solidDepth, dist);

			if(!transformed && state.isSolidRender(level, pos) && state.isCollisionShapeFullBlock(level, pos)) solidDepth++;
		}
	}

	/**
	 * Upstream's {@code leafia$stompColumnToUpdatesEndothermic}. {@code dist} is used as it comes. Down the
	 * column until three solid blocks have been passed: every non-solid block from above the current one up
	 * to (not including) {@code floodHeight} becomes liquid oxygen; then the frozen blocks by
	 * {@link FalloutConfigInit#endoEntries}; failing that, the rubble falls within 65 percent. No fire, no
	 * ash and no crater biome. With {@code features.worldFluids} off there is no liquid-oxygen block and the
	 * crater stays dry.
	 */
	public static void stompEndothermic(ServerLevel level, int x, int z, double dist, int floodHeight) {

		List<FalloutEntry> entries = FalloutConfigInit.endoEntries;
		BlockState lox = LeafiaLiquids.lox == null ? null : LeafiaLiquids.lox.block.get().defaultBlockState();
		int solidDepth = 0;
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int floor = level.getMinBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos flood = new BlockPos.MutableBlockPos();

		for(int y = top; y >= floor; y--) {

			if(solidDepth >= EntityFalloutRain.MAX_SOLID_DEPTH) return;

			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if(state.isAir() || state.is(ModBlocks.fallout.get())) continue;

			if(lox != null) {
				for(int i = y + 1; i < floodHeight; i++) {
					flood.set(x, i, z);
					if(!level.getBlockState(flood).isSolid()) level.setBlock(flood, lox, 3);
				}
			}

			boolean transformed = false;
			FalloutEntry entry = EntityFalloutRain.convert(level, pos.immutable(), state, dist, entries);
			if(entry != null) {
				if(entry.isSolid()) solidDepth++;
				transformed = true;
			}

			if(!transformed) EntityFalloutRain.dropRubble(level, pos.immutable(), state, solidDepth, dist);

			if(!transformed && state.isSolidRender(level, pos) && state.isCollisionShapeFullBlock(level, pos)) solidDepth++;
		}
	}
}
