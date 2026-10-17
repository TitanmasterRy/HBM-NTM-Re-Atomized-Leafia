package com.leafia.eventbuses;

import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Three handlers of upstream's {@code LeafiaServerListener} that no feature batch had a reason to take (D-3040).
 *
 * <p>Upstream's listener is a bag of inner classes each registered on the event bus; its other handlers went to the
 * features they serve - the advisor on login ({@code AdvisorItem}), the seismic tick and the death times
 * ({@link LeafiaStrikeListener}), the picked-up flag ({@code LeafiaItemDrops}), the server ticks ({@code PWRTicks},
 * {@code Wind}), the neighbour notifications ({@link NotifyEventListeners}, {@link StructuralIntegrityListener}), the shield's
 * block ({@code AddonShieldItem}) and the sharp edges ({@link LeafiaSharpEdges}). These are the three left over:
 *
 * <ul>
 * <li><b>Digamma rain</b> ({@code Unsorted.handleRains}, its digamma half): a living thing under open sky in the digamma
 *     crater takes {@code 0.01 + rand x 0.02} digamma once every {@value #DIGAMMA_RAIN_PERIOD} ticks - the ticks on which
 *     the server's counter ({@code Unsorted.digammaRainCounter}, advanced by {@code LeafiaPassiveServer.onTick}) is zero.
 *     The position is upstream's: the block half a block east and south of the entity, at the top of its box. It reads the
 *     sky, not the weather, so it falls in sunshine too. Upstream also skipped the crater's watching figures, which here are
 *     not entities. Its acid-rain half belongs to {@code HasAcidicRain} biomes, which are Sellacity's (D-3038). Behind
 *     {@code features.digammaCrater}; schizo mode, which makes every biome the crater, makes it fall everywhere, as upstream.</li>
 * <li><b>Burnt skin</b> ({@code Unsorted.onEntityHurt}): fire damage to anything living gives it the addon's damaged-skin
 *     effect, up to level III, with chance {@code 1 / (5 + 5 x level)} - already-burnt skin burns less often. It changes
 *     what the base mod's fire does, so it rides {@code features.hazardOverhaul}, off.</li>
 * <li><b>Stale anti-tamper protections</b> ({@code Unsorted.worldInit}): when a level loads, the field disturber's
 *     protections recorded for that dimension ({@code EntityNukeExplosionMK3.at}, a process-wide map) are forgotten, so a
 *     protection from a world closed earlier in the same game does not hold in the next one. Not gated: it only ever drops
 *     state a running level cannot have written.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaServerHandlers {

	private LeafiaServerHandlers() {}

	/** Digamma rain falls on one server tick in this many. */
	public static final int DIGAMMA_RAIN_PERIOD = 70;
	/** The least digamma one fall gives... */
	public static final float DIGAMMA_RAIN_MIN = 0.01F;
	/** ...and how much more it can give. */
	public static final float DIGAMMA_RAIN_SPREAD = 0.02F;
	/** Fire burns skin with chance {@code 1 / (BASE + BASE x level)}. */
	public static final int SKIN_BURN_BASE = 5;
	/** ...up to this level. */
	public static final int SKIN_BURN_MAX = 3;

	/** Upstream's {@code Unsorted.digammaRainCounter}: zero on one server tick in {@link #DIGAMMA_RAIN_PERIOD}. */
	public static int digammaRainCounter = 0;

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		digammaRainCounter = (digammaRainCounter + 1) % DIGAMMA_RAIN_PERIOD;
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		if(event.getEntity() instanceof LivingEntity living && !living.level().isClientSide) digammaRain(living);
	}

	/**
	 * One tick of digamma rain on {@code entity}.
	 *
	 * @return whether it fell on it
	 */
	public static boolean digammaRain(LivingEntity entity) {
		if(!LeafiaFeatures.digammaCrater || digammaRainCounter != 0) return false;
		Level level = entity.level();
		BlockPos pos = new BlockPos(Mth.floor(entity.getX() + 0.5), Mth.floor(entity.getY() + entity.getBbHeight() - 0.1), Mth.floor(entity.getZ() + 0.5));
		if(!DigammaCrater.isDigammaBiome(level.getBiome(pos)) || !level.canSeeSky(pos)) return false;
		ContaminationUtil.contaminate(entity, HazardType.DIGAMMA, ContaminationType.CREATIVE,
				DIGAMMA_RAIN_MIN + level.random.nextFloat() * DIGAMMA_RAIN_SPREAD);
		return true;
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		burnSkin(event.getEntity(), event.getSource(), event.getEntity().getRandom());
	}

	/**
	 * Fire damage may burn the skin.
	 *
	 * @return whether it did
	 */
	public static boolean burnSkin(LivingEntity entity, DamageSource source, RandomSource rng) {
		if(!LeafiaFeatures.hazardOverhaul || !source.is(DamageTypeTags.IS_FIRE)) return false;
		if(rng.nextInt(SKIN_BURN_BASE + SKIN_BURN_BASE * LeafiaPotion.getSkinDamage(entity)) != 0) return false;
		LeafiaPotion.hurtSkin(entity, SKIN_BURN_MAX);
		return true;
	}

	@SubscribeEvent
	public static void onLevelLoad(LevelEvent.Load event) {
		if(event.getLevel() instanceof ServerLevel level) forgetProtections(level.dimension().location());
	}

	/**
	 * Drops every field-disturber protection recorded for {@code dimension}.
	 *
	 * @return how many there were
	 */
	public static int forgetProtections(ResourceLocation dimension) {
		int before = EntityNukeExplosionMK3.at.size();
		EntityNukeExplosionMK3.at.keySet().removeIf(entry -> entry.dim().equals(dimension));
		return before - EntityNukeExplosionMK3.at.size();
	}
}
