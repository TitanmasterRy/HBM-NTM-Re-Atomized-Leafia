package com.leafia.contents.effects.nuke;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.entity.logic.EntityExplosionChunkloading;
import com.hbm.explosion.ExplosionNukeRayBatched;
import com.hbm.explosion.ExplosionNukeRayParallelized;
import com.hbm.interfaces.IExplosionRay;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.particle.helper.AshesCreator;
import com.hbm.saveddata.satellites.SatelliteDetector;
import com.hbm.saveddata.satellites.SatelliteDetector.BurstIntensity;
import com.hbm.sound.NtmSounds;
import com.hbm.util.EntityDamageUtil;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.init.LeafiaEntities;
import com.leafia.overwrite_contents.LeafiaFalloutVariants;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The addon's nuclear explosion, which only the absorption particle reactor and a destroyed absorption particle
 * capsule set off, and only ever as {@link ExplosionType#ENDOTHERMIC}: a crater, a cold blast that hurts
 * everything it can see within the radius, and fallout that freezes the ground and floods the crater with liquid
 * oxygen.
 *
 * <p>Upstream: {@code com.leafia.contents.effects.nuke.NukeLCA}, a copy of CE's {@code EntityNukeExplosionMK5}
 * with the radiation, the base blast damage and the glowing-one conversion all switched off by {@code if(false)},
 * and two additions: the type, and the lowest block the crater reached, which sets the flood height. Its
 * numbers, all upstream's:
 * <ul>
 * <li>{@link #statFac}: strength {@code 2r}, radius {@code r}, 25 for a radius of 0. (CE's
 *     {@code disableNuclear}, which drops the fallout, is not a base-mod option.)</li>
 * <li>Each tick, everything within {@code 2r} is a candidate for {@link #dealDamageEndothermic}: within
 *     {@code r}, in line of sight, {@code 250 x (r - d) / r} of {@code leafia:endothermic} through the base mod's
 *     {@code attackEntityFromNT} (invulnerability ignored, threshold 100 pierced), five seconds of fire, and a
 *     push of 0.2 away from the centre unless the hit was cancelled. A living thing it kills is pulverised:
 *     {@code clamp(w x h x w x 25, 5, 50)} ash particles at scale 0.25 and the disintegration sound.</li>
 * <li>The crater: an endothermic blast always runs the batched ray (upstream's choice, whatever the algorithm
 *     config says); any other runs the parallelised ray when {@code explosionAlgorithm} is 1 or 2. Both are
 *     driven with {@code BombConfig.mk5} milliseconds a tick.</li>
 * <li>The fallout: {@code (int) (2.5r + falloutAdd) x falloutRange / 100}; an endothermic one is the addon's
 *     endothermic fallout variant ({@code leafia:endothermic}, D-3009) with its flood height at
 *     {@code lowest + 1 + round(r^0.65 / 2.5)}.</li>
 * </ul>
 *
 * <h2>Upstream's own behaviour, kept (rule 4)</h2>
 * <ol>
 * <li><b>The type is saved</b> (D-3075). Upstream lost it on reload, so a saved-and-quit APR meltdown carried on as
 *     an ordinary blast without the cold damage or the flooding fallout.</li>
 * <li><b>The flood floods.</b> D-3009 asked whether an endothermic blast on the parallel ray should flood, since
 *     upstream's parallel ray never recorded a lowest height. It never comes up: upstream runs an endothermic
 *     blast on the batched ray, whose lowest height is recorded, so it floods - here as there.</li>
 * <li>A crater that removed nothing leaves the lowest height at {@code Integer.MAX_VALUE}, and the flood height
 *     overflows negative, so there is no flood.</li>
 * </ol>
 *
 * <p>Port differences: CE's ray progress is not saved here (the base mod's rays have no NBT, D-2196), so a
 * reloaded blast starts its crater again, which removes the same blocks; {@code CompatibilityConfig.isWarDim}
 * does not exist in the base mod; the detonator is kept and saved but no base ray takes one; entities are
 * gathered from a box of {@code 2r} (CE's {@code WorldUtil.getEntitiesInRadius}), which the damage then limits to
 * {@code r}; the exempt entities are ocelots and cats (1.12.2's ocelot was also the cat) - CE's bullets, beams and
 * grenades are not in the base mod.
 */
public class NukeLCA extends EntityExplosionChunkloading {

	public enum ExplosionType {
		ENDOTHERMIC,
	}

	/** Upstream's endothermic blast damage, {@code dealDamageEndothermic(..., radius, 250)}. */
	public static final float ENDOTHERMIC_MAX_DAMAGE = 250F;

	private int strength;
	private int radius;
	private boolean fallout = true;
	private IExplosionRay explosion;
	private boolean initialized = false;
	private int falloutAdd = 0;
	private int algorithm;
	private long explosionStart = 0;
	@Nullable public ExplosionType type;
	@Nullable public UUID detonator;
	public int lowestHeight = 0;

	public NukeLCA(EntityType<? extends NukeLCA> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public NukeLCA(Level level) {
		this(LeafiaEntities.NUKE_LCA.get(), level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) { }

	public NukeLCA setExplosionType(ExplosionType type) {
		this.type = type;
		return this;
	}

	public static NukeLCA statFac(Level world, int r, double x, double y, double z) {
		if(GeneralConfig.enableExtendedLogging && !world.isClientSide)
			MainRegistry.LOGGER.info("[NUKE] Initialized explosion at {} / {} / {} with radius {}!", x, y, z, r);

		if(r == 0) r = 25;

		NukeLCA mk5 = new NukeLCA(world);
		mk5.strength = 2 * r;
		mk5.radius = r;
		mk5.algorithm = BombConfig.explosionAlgorithm;
		mk5.setPos(x, y, z);
		// CE's BombConfig.disableNuclear (no fallout) is not a base-mod option
		return mk5;
	}

	public static NukeLCA statFacNoRad(Level world, int r, double x, double y, double z) {
		NukeLCA mk5 = statFac(world, r, x, y, z);
		mk5.fallout = false;
		return mk5;
	}

	public int getStrength() { return strength; }
	public int getRadius() { return radius; }
	public boolean hasFallout() { return fallout; }
	public int getAlgorithm() { return algorithm; }
	@Nullable public IExplosionRay getExplosion() { return explosion; }

	/** The fallout radius this blast would spawn: {@code (int) (2.5r + falloutAdd) x falloutRange / 100}. */
	public int falloutScale() {
		return (int) (this.radius * 2.5 + falloutAdd) * BombConfig.falloutRange / 100;
	}

	/** The flood height an endothermic fallout gets: {@code lowest + 1 + round(r^0.65 / 2.5)}. */
	public static int floodHeight(int lowestHeight, int radius) {
		return lowestHeight + 1 + (int) Math.round(Math.pow(radius, 0.65) / 2.5);
	}

	/** The speed argument the base rays take; the base MK5's {@code ceil(100000 / strength)}. */
	private int speed() {
		return (int) Math.ceil(100000D / Math.max(strength, 1));
	}

	private IExplosionRay makeRay(boolean endothermic, int algorithm) {
		int x = (int) this.getX(), y = (int) this.getY(), z = (int) this.getZ();
		if(!endothermic && (algorithm == 1 || algorithm == 2))
			return new ExplosionNukeRayParallelized(this.level(), this.getX(), this.getY(), this.getZ(), strength, speed(), radius);
		return new ExplosionNukeRayBatched(this.level(), x, y, z, strength, speed(), radius);
	}

	@Override
	public void tick() {
		if(this.level().isClientSide) return;

		if(strength == 0) {
			this.discardBlast();
			return;
		}
		loadChunk((int) Math.floor(this.getX() / 16D), (int) Math.floor(this.getZ() / 16D));

		if(type == ExplosionType.ENDOTHERMIC) {
			List<Entity> list = this.level().getEntities(this, new AABB(this.getX(), this.getY(), this.getZ(), this.getX(), this.getY(), this.getZ()).inflate(this.radius * 2.0D));
			dealDamageEndothermic(this.level(), list, this.getX(), this.getY(), this.getZ(), radius, ENDOTHERMIC_MAX_DAMAGE);
		}

		if(!initialized) {
			explosionStart = System.currentTimeMillis();
			explosion = makeRay(type == ExplosionType.ENDOTHERMIC, BombConfig.explosionAlgorithm);
			SatelliteDetector.reportEvent(this.level(), SatelliteDetector.DURATION_HIGH, BurstIntensity.HIGH, this.getX(), this.getZ());
			initialized = true;
		}

		if(!explosion.isComplete()) {
			explosion.cacheChunksTick(BombConfig.mk5);
			explosion.destructionTick(BombConfig.mk5);
			lowestHeight = explosion.getLowestHeight();
		} else {
			if(GeneralConfig.enableExtendedLogging && explosionStart != 0)
				MainRegistry.LOGGER.info("[NUKE] Explosion complete. Time elapsed: {}ms", System.currentTimeMillis() - explosionStart);
			if(fallout) {
				EntityFalloutRain rain = new EntityFalloutRain(this.level());
				rain.setPos(this.getX(), this.getY(), this.getZ());
				rain.setScale(falloutScale());
				if(type == ExplosionType.ENDOTHERMIC) {
					LeafiaFalloutVariants.setEndothermic(rain);
					LeafiaFalloutVariants.setFloodHeight(rain, floodHeight(lowestHeight, radius));
				}
				this.level().addFreshEntity(rain);
			}
			this.discardBlast();
		}
	}

	/** Upstream's {@code setDead}: cancel the ray, release the chunk, go. */
	private void discardBlast() {
		if(explosion != null) explosion.cancel();
		clearChunkLoader();
		this.discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		if(reason.shouldDestroy() && explosion != null) explosion.cancel();
		super.remove(reason);
	}

	public NukeLCA setDetonator(Entity detonator) {
		if(detonator instanceof ServerPlayer) this.detonator = detonator.getUUID();
		return this;
	}

	public NukeLCA moreFallout(int fallout) {
		falloutAdd = fallout;
		return this;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		radius = nbt.getInt("radius");
		strength = nbt.getInt("strength");
		falloutAdd = nbt.getInt("falloutAdd");
		fallout = nbt.getBoolean("fallout");
		algorithm = nbt.getInt("algorithm");
		if(nbt.hasUUID("detonator")) detonator = nbt.getUUID("detonator");
		// D-3075: the type and flood height are saved (upstream's reload lost both and carried on as a plain blast)
		if(nbt.contains("explosionType")) {
			try { type = ExplosionType.valueOf(nbt.getString("explosionType")); } catch(IllegalArgumentException ignored) { type = null; }
		}
		lowestHeight = nbt.getInt("lowestHeight");
		if(!initialized && strength > 0) {
			explosion = makeRay(type == ExplosionType.ENDOTHERMIC, algorithm);
			initialized = true;
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putInt("radius", radius);
		nbt.putInt("strength", strength);
		nbt.putInt("falloutAdd", falloutAdd);
		nbt.putBoolean("fallout", fallout);
		nbt.putInt("algorithm", algorithm);
		if(detonator != null) nbt.putUUID("detonator", detonator);
		if(type != null) nbt.putString("explosionType", type.name());
		nbt.putInt("lowestHeight", lowestHeight);
	}

	/** Upstream's exemptions that exist here: the ocelot (1.12.2's ocelot was also the cat). */
	public static boolean isExplosionExempt(Entity e) {
		return e instanceof Ocelot || e instanceof Cat;
	}

	public static DamageSource endothermicSource(Level level) {
		return LeafiaDamageSources.of(level, LeafiaDamageSources.endothermic);
	}

	public static void dealDamageEndothermic(Level world, List<Entity> list, double x, double y, double z, double radius, float maxDamage) {
		DamageSource source = endothermicSource(world);
		for(Entity e : list) {
			if(e instanceof EntityExplosionChunkloading) continue;
			double dist = Math.sqrt(e.distanceToSqr(x, y, z));

			if(dist <= radius) {
				double entX = e.getX();
				double entY = e.getY() + e.getEyeHeight();
				double entZ = e.getZ();

				if(!isExplosionExempt(e) && !Library.isObstructed(world, x, y, z, entX, entY, entZ)) {

					boolean doKnockback = true;
					double damage = maxDamage * (radius - dist) / radius;
					if(e instanceof LivingEntity living && e.isAlive()) {
						doKnockback = EntityDamageUtil.attackEntityFromNT(living, source, (float) damage, true, true, 0, 100F, 0);
						if(!e.isAlive()) pulverize(living);
					} else {
						e.hurt(source, (float) damage);
					}

					e.igniteForSeconds(5);

					if(doKnockback) {
						Vec3 knock = new Vec3(e.getX() - x, e.getY() + e.getEyeHeight() - y, e.getZ() - z).normalize();
						e.setDeltaMovement(e.getDeltaMovement().add(knock.x * 0.2D, knock.y * 0.2D, knock.z * 0.2D));
						e.hurtMarked = true;
					}
				}
			}
		}
	}

	/** Upstream's ash count: {@code clamp((int) (w x h x w x 25), 5, 50)}. */
	public static int ashCount(LivingEntity entity) {
		return Mth.clamp((int) (entity.getBbWidth() * entity.getBbHeight() * entity.getBbWidth() * 25), 5, 50);
	}

	public static void pulverize(LivingEntity entity) {
		AshesCreator.composeEffect(entity.level(), entity, ashCount(entity), 0.25F);
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), NtmSounds.WEAPON_FIRE_DISINTEGRATION.get(),
				SoundSource.PLAYERS, 2.0F, 0.9F + entity.getRandom().nextFloat() * 0.2F);
	}

}
