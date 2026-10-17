package com.leafia.overwrite_contents;

import org.jetbrains.annotations.Nullable;

import com.hbm.blockentity.bomb.BlockEntityLandmine;
import com.hbm.blockentity.bomb.BlockEntityLaunchPadBase;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.bomb.Landmine;
import com.hbm.config.WorldConfig;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.projectile.EntityMeteor;
import com.hbm.entity.projectile.EntityThrowableNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.hbm.handler.BossSpawnHandler;
import com.hbm.handler.ImpactWorldHandler;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem;
import com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntity;
import com.leafia.contents.miscellanous.diverter.DiverterBlock;
import com.leafia.database.ImpactSeismic;
import com.leafia.overwrite_contents.interfaces.IMixinEntityMissileBaseNT;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityLandmine;
import com.leafia.passive.effects.LeafiaShakecam;
import com.leafia.unsorted.explosion_vnt.EntityProcessorLandmine;
import com.leafia.unsorted.explosion_vnt.EntityProcessorPlayerLandmine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the addon plugs into the base mod's meteor, missile, launch-pad, landmine and impact seams (D-2197), all at
 * once; installed by {@link LeafiaExplosionSeams#install()}. Not an upstream file: upstream reached each of these
 * with a mixin into {@code com.hbm}, and D-1001 makes every one a seam consumer instead (D-3022).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code MixinEntityMeteor} (HEAD of {@code onUpdate}: divert)</td><td>{@link #METEOR} {@code tick}</td><td>content.meteorDiverter</td></tr>
 * <tr><td>{@code MixinEntityMeteor} (HEAD of {@code onUpdate}: the falling shake; the tails redirect; the burial redirect)</td><td>{@link #METEOR} {@code tick}, {@code replaceTails}, {@code burialPos}</td><td>meteorOverhaul (off), and balance.meteor.customCraters</td></tr>
 * <tr><td>{@code MixinBossSpawnHandler}</td><td>{@link #STRIKE_FILTER}</td><td>meteorStrikeRebalance (off)</td></tr>
 * <tr><td>{@code MixinEntityMissileBaseNT} (the airburst inject; the {@code killMissile} overwrite)</td><td>{@link #MISSILE}</td><td>missileOverhaul (off) for base missiles; always for the addon's</td></tr>
 * <tr><td>{@code MixinEntityThrowableNT} (the {@code onImpact} redirect)</td><td>{@link #AIRBURST_IMPACT}</td><td>the same</td></tr>
 * <tr><td>{@code MixinTileEntityLaunchPadBase}</td><td>{@link #CUSTOM_NUKE_PROVIDER}</td><td>content.addonBombs (the item exists)</td></tr>
 * <tr><td>{@code MixinLandmine} (the processor redirect), {@code MixinTileEntityLandmine} (the click volume)</td><td>{@link #LANDMINE_BLAST}, {@link #syncLandmineVolume()}</td><td>landmineOverhaul (off)</td></tr>
 * <tr><td>{@code MixinModBlocks} (the AP mine's range / 4)</td><td>{@link #LANDMINE_RANGE}</td><td>landmineOverhaul (off)</td></tr>
 * <tr><td>{@code MixinImpactWorldHandler}</td><td>{@link #SEISMIC_CHUNK}</td><td>impactSeismic (off)</td></tr>
 * </table>
 *
 * <p>{@code MixinTomSaveData}, {@code MixinLandmine}'s placement override and {@code MixinTileEntityLandmine}'s field
 * are state, not behaviour: {@code IMixinTomSaveData} and {@code IMixinTileEntityLandmine}, with
 * {@code LeafiaStrikeListener} writing both. Each handler is registered whatever its switch says and reads the switch
 * when it runs, so an off switch hands the base mod its own behaviour back.
 */
public final class LeafiaStrikeSeams {

	private LeafiaStrikeSeams() {}

	// ------------------------------------------------------------------ MixinEntityMeteor

	/** The falling shake is sent every this many ticks. */
	public static final int SHAKE_INTERVAL = 5;
	/** ...to players within this many blocks. */
	public static final double SHAKE_SEND_RANGE = 250;
	/** A crater meteor's meteorite is buried this much deeper than the base mod's. */
	public static final int CRATER_BURY = 2;
	/** The crater is centred this many motions along the meteor's (upstream: z) drift. */
	public static final double CRATER_DRIFT = 4;

	/** Per-meteor random divert heading, upstream's {@code @Unique leafia$divertAngle}, drawn once per meteor. */
	private static final java.util.Map<EntityMeteor, Double> DIVERT_ANGLE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	public static double divertAngle(EntityMeteor meteor) {
		return DIVERT_ANGLE.computeIfAbsent(meteor, m -> m.level().random.nextDouble() * Math.PI * 2);
	}

	public static final EntityMeteor.MeteorHook METEOR = new EntityMeteor.MeteorHook() {

		@Override
		public boolean tick(EntityMeteor meteor) {
			if(meteor.level().isClientSide) return false;
			if(LeafiaFeatures.meteorDiverter && divert(meteor)) return true;
			if(LeafiaFeatures.meteorOverhaul && meteor.level().getGameTime() % SHAKE_INTERVAL == 0 && !meteor.safe) {
				LeafiaShakecam.sendNear((ServerLevel) meteor.level(), BlockPos.containing(meteor.position()), SHAKE_SEND_RANGE,
						"type=smooth", "preset=RUPTURE", "blurDulling*2", "duration/4", "speed/2", "intensity/4", "range=200");
			}
			return false;
		}

		@Override
		public boolean replaceTails(EntityMeteor meteor) {
			if(!LeafiaFeatures.meteorOverhaul || !LeafiaConfig.meteorCraters) return false;
			if(WorldConfig.enableMeteorTails && !meteor.safe) {
				// upstream: "for some reason its both Z axis in original code" - and kept both Z itself
				Vec3 motion = meteor.getDeltaMovement();
				double x = meteor.getX() + motion.z * CRATER_DRIFT, y = meteor.getY(), z = meteor.getZ() + motion.z * CRATER_DRIFT;
				LeafiaMeteorCrater.createMeteorExplosionEffect(meteor.level(), x, y, z);
				LeafiaMeteorCrater.createMeteorCrater(meteor.level(), BlockPos.containing(x, y, z));
			}
			return true;
		}

		@Override
		public BlockPos burialPos(EntityMeteor meteor, BlockPos upstream) {
			if(LeafiaFeatures.meteorOverhaul && LeafiaConfig.meteorCraters && !meteor.safe) return upstream.below(CRATER_BURY);
			return upstream;
		}
	};

	/**
	 * Upstream's divert: while the meteor's chunk is protected, move it {@code radius x 16} blocks along its own random
	 * heading; if it moved, it is replaced by a fresh meteor there with the same {@code safe} and motion, "because the
	 * entity rendering keeps making shit go back and forth", and the tick ends. The chunk is upstream's
	 * {@code (int)(pos / 16)} - truncated toward zero, so a negative coordinate's chunk is one off; kept. A radius of 0
	 * moves nothing and would loop forever upstream; here it stops, which is the one change (D-3022).
	 */
	public static boolean divert(EntityMeteor meteor) {
		Level world = meteor.level();
		int radius = LeafiaConfig.meteorDiverterProtectionRadius;
		double angle = divertAngle(meteor);
		boolean moved = false;
		while(true) {
			int cx = (int) (meteor.getX() / 16);
			int cz = (int) (meteor.getZ() / 16);
			if(DiverterBlock.isProtected(world, cx, cz)) {
				if(radius <= 0) break;
				double divertX = Math.cos(angle);
				double divertZ = Math.sin(angle);
				moved = true;
				meteor.setPos(meteor.getX() + divertX * radius * 16, meteor.getY(), meteor.getZ() + divertZ * radius * 16);
			} else {
				break;
			}
		}
		if(moved) {
			meteor.discard();
			EntityMeteor fresh = new EntityMeteor(world);
			fresh.safe = meteor.safe;
			fresh.setPos(meteor.getX(), meteor.getY(), meteor.getZ());
			fresh.setDeltaMovement(meteor.getDeltaMovement());
			world.addFreshEntity(fresh);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ MixinBossSpawnHandler

	/** One strike in this many goes ahead with the rebalance on. */
	public static final int STRIKE_ODDS = 6;

	/** Upstream's {@code world.rand.nextInt(6) == 0}, drawn from the strike's level, only with the rebalance on. */
	public static boolean strikeGoesAhead(net.minecraft.util.RandomSource random) {
		return !LeafiaFeatures.meteorStrikeRebalance || random.nextInt(STRIKE_ODDS) == 0;
	}

	public static final BossSpawnHandler.MeteorStrikeFilter STRIKE_FILTER = (target, repell) -> strikeGoesAhead(target.level().random);

	// ------------------------------------------------------------------ MixinEntityMissileBaseNT, MixinEntityThrowableNT

	/** The airburst window: within this of the target on each horizontal axis... */
	public static final double AIRBURST_REACH = 20;
	/** ...and less than this above the ground. */
	public static final double AIRBURST_HEIGHT = 50;

	/**
	 * Whether an air-bursting missile is over its target: within 20 of it on x and z and under 50 above 1.12.2's
	 * {@code getHeight(x, z)} (the motion-blocking height here, D-843) at {@code (int)} of its position.
	 */
	public static boolean inAirburstWindow(EntityMissileBaseNT missile) {
		double x = missile.getX(), z = missile.getZ();
		return Math.abs(missile.targetX - x) < AIRBURST_REACH && Math.abs(missile.targetZ - z) < AIRBURST_REACH
				&& missile.getY() - missile.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) < AIRBURST_HEIGHT;
	}

	/**
	 * The airburst and the shoot-down. Upstream's inject called {@code onMissileImpact} and left the missile flying, so
	 * it went off again every tick until it reached the ground; here it goes off once, through the seam's
	 * {@code detonateHere()}, which removes it (D-3022 flags the difference).
	 */
	public static final EntityMissileBaseNT.MissileHook MISSILE = new EntityMissileBaseNT.MissileHook() {

		@Override
		public void onFlightTick(EntityMissileBaseNT missile) {
			if(!IMixinEntityMissileBaseNT.governs(missile)) return;
			if(!IMixinEntityMissileBaseNT.shouldDetonateInAir(missile)) return;
			if(inAirburstWindow(missile)) missile.detonateHere();
		}

		@Override
		public boolean onShotDown(EntityMissileBaseNT missile) {
			if(!IMixinEntityMissileBaseNT.governs(missile)) return false;
			return !IMixinEntityMissileBaseNT.interactABMissile(missile);
		}
	};

	/** An air-bursting missile that hits something before its window breaks up as if shot down. */
	public static final EntityThrowableNT.ImpactHook AIRBURST_IMPACT = new EntityThrowableNT.ImpactHook() {
		@Override
		public boolean replace(EntityThrowableNT projectile, HitResult hit) {
			if(!(projectile instanceof EntityMissileBaseNT missile) || missile.level().isClientSide) return false;
			if(!IMixinEntityMissileBaseNT.governs(missile) || !IMixinEntityMissileBaseNT.shouldDetonateInAir(missile)) return false;
			missile.killMissile();
			return true;
		}
	};

	// ------------------------------------------------------------------ MixinTileEntityLaunchPadBase

	/**
	 * Every launch pad fires a custom nuke missile carrying some TNT, with the stages the item saved; one with no
	 * TNT (or never opened) launches nothing.
	 */
	public static final BlockEntityLaunchPadBase.MissileProvider CUSTOM_NUKE_PROVIDER = new BlockEntityLaunchPadBase.MissileProvider() {

		@Override
		public boolean accepts(BlockEntityLaunchPadBase pad, ItemStack missile) {
			return missile.getItem() instanceof CustomNukeMissileItem;
		}

		@Override
		public @Nullable Entity create(BlockEntityLaunchPadBase pad, ItemStack missile, int targetX, int targetZ) {
			return launch(pad.getLevel(), pad.getBlockPos(), pad.getLaunchOffset(), missile, targetX, targetZ);
		}
	};

	public static @Nullable CustomNukeMissileEntity launch(Level level, BlockPos pos, double launchOffset, ItemStack missile, int targetX, int targetZ) {
		CompoundTag nbt = CustomNukeMissileItem.inventoryTag(missile);
		if(nbt.getFloat("tnt") <= 0) return null;
		return CustomNukeMissileEntity.launch(level, pos.getX() + 0.5F, pos.getY() + (float) launchOffset, pos.getZ() + 0.5F, targetX, targetZ,
				nbt.getFloat("tnt"), nbt.getFloat("nuke"), nbt.getFloat("hydro"), nbt.getFloat("bale"),
				nbt.getFloat("dirty"), nbt.getFloat("schrab"), nbt.getFloat("sol"), nbt.getFloat("euph"));
	}

	// ------------------------------------------------------------------ MixinLandmine, MixinTileEntityLandmine, MixinModBlocks

	/** The priming click's volume with the overhaul on (upstream: 0.5; the base mod's is 3). */
	public static final float PRIME_VOLUME = 0.5F;
	/** The AP mine's range is divided by this. */
	public static final double AP_RANGE_DIVISOR = 4;

	public static final Landmine.RangeModifier LANDMINE_RANGE =
			(mine, range) -> LeafiaFeatures.landmineOverhaul && mine == ModBlocks.mine_ap.get() ? range / AP_RANGE_DIVISOR : range;

	/** Swap each mine's entity processor for the addon's, keyed on who placed it. */
	public static final Landmine.DetonationHook LANDMINE_BLAST = (mine, level, pos, blockEntity, explosion) -> {
		if(!LeafiaFeatures.landmineOverhaul) return;
		boolean byPlayer = blockEntity != null && IMixinTileEntityLandmine.getPlayerPlaced(blockEntity);
		if(explosion.getEntityProcessor() instanceof EntityProcessorCrossSmooth base) {
			EntityProcessorCrossSmooth processor = processor(mine, byPlayer, base);
			if(processor != null) explosion.setEntityProcessor(processor);
		}
	};

	/** The fat mine's range mod, which upstream's redirect restated at its call site; the base's processor has no getter for it. */
	public static final float FAT_RANGE_MOD = 1.5F;

	/**
	 * The processor upstream's redirect made in place of each mine's {@code new EntityProcessorCrossSmooth(nodeDist, damage)}: the
	 * addon's, carrying the call site's own node distance, damage and piercing - read back off the processor the base mod built
	 * ({@code base}, through {@code ExplosionVNT.getEntityProcessor} and the processor's getters, D-2207) instead of restating the
	 * base mod's table - and the fat mine's range mod. Null for a block that is none of the five mines upstream redirected.
	 */
	public static @Nullable EntityProcessorCrossSmooth processor(Landmine mine, boolean byPlayer, EntityProcessorCrossSmooth base) {
		boolean fat = mine == ModBlocks.mine_fat.get();
		if(!fat && mine != ModBlocks.mine_ap.get() && mine != ModBlocks.mine_he.get() && mine != ModBlocks.mine_shrap.get() && mine != ModBlocks.mine_naval.get())
			return null;
		EntityProcessorCrossSmooth processor = of(base.getNodeDist(), base.getFixedDamage(), byPlayer).setupPiercing(base.getPierceDT(), base.getPierceDR());
		if(fat) processor.withRangeMod(FAT_RANGE_MOD);
		return processor;
	}

	private static EntityProcessorCrossSmooth of(double nodeDist, double damage, boolean byPlayer) {
		return byPlayer ? new EntityProcessorPlayerLandmine(nodeDist, (float) damage) : new EntityProcessorLandmine(nodeDist, (float) damage);
	}

	/** The base mod's click volume is a process-wide static: set it from the switch. Called at install and by tests. */
	public static void syncLandmineVolume() {
		BlockEntityLandmine.setPrimeVolume(LeafiaFeatures.landmineOverhaul ? PRIME_VOLUME : BlockEntityLandmine.PRIME_VOLUME);
	}

	// ------------------------------------------------------------------ MixinImpactWorldHandler

	public static final ImpactWorldHandler.ChunkListener SEISMIC_CHUNK = (level, chunk) -> {
		if(LeafiaFeatures.impactSeismic) ImpactSeismic.collapseChunk(level, chunk);
	};

	// ------------------------------------------------------------------ install

	private static boolean installed = false;

	public static void install() {
		if(installed) return;
		installed = true;
		EntityMeteor.addHook(METEOR);
		BossSpawnHandler.addMeteorStrikeFilter(STRIKE_FILTER);
		EntityMissileBaseNT.addHook(MISSILE);
		EntityThrowableNT.addImpactHook(AIRBURST_IMPACT);
		BlockEntityLaunchPadBase.addMissileProvider(CUSTOM_NUKE_PROVIDER);
		Landmine.addRangeModifier(LANDMINE_RANGE);
		Landmine.addDetonationHook(LANDMINE_BLAST);
		ImpactWorldHandler.addChunkListener(SEISMIC_CHUNK);
		syncLandmineVolume();
	}
}
