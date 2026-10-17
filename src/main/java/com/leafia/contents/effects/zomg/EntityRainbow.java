package com.leafia.contents.effects.zomg;

import javax.annotation.Nullable;

import com.hbm.items.weapon.sedna.SednaDamageTypes;
import com.hbm.util.DamageResistanceHandler.DamageClass;
import com.leafia.init.LeafiaEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rainbow beam {@link LeafiaZomg#zomg} fires: a projectile that flies straight, at a constant speed and with no
 * gravity, and hits the first entity on its path for {@link #DAMAGE_MIN} to {@link #DAMAGE_MAX} subatomic damage.
 *
 * <p>NTM-CE's {@code EntityRainbow}, which is not in {@code hbm-src} or anywhere in the reference: D-3030 #4 has the
 * addon register its own. <b>Its numbers are a reconstruction, not a port</b> (D-3031, flagged for the owner): the
 * damage range is the {@code 10000, 100000} the old 1.7.10 ZOMG passed its rainbow, as remembered rather than read; the
 * damage class is the base mod's {@code SUBATOMIC} (1.7.10's {@code subAtomic} source, the ZOMG's own, armour-piercing);
 * a block stops it and nothing else happens, because no remembered behaviour for that was certain enough to carry; and
 * it is discarded after {@link #LIFETIME} ticks so a beam that misses does not fly for ever.
 */
public class EntityRainbow extends Projectile {

	public static final float DAMAGE_MIN = 10000F;
	public static final float DAMAGE_MAX = 100000F;
	public static final int LIFETIME = 200;

	public EntityRainbow(EntityType<? extends EntityRainbow> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public EntityRainbow(Level level, double x, double y, double z, Vec3 velocity, @Nullable Entity owner) {
		this(LeafiaEntities.RAINBOW.get(), level);
		this.setPos(x, y, z);
		this.setDeltaMovement(velocity);
		if(owner != null) this.setOwner(owner);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) { }

	@Override
	public void tick() {
		super.tick();
		if(!level().isClientSide) {
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if(hit.getType() != HitResult.Type.MISS) {
				this.hitTargetOrDeflectSelf(hit);
				if(isRemoved()) return;
			}
			if(tickCount > LIFETIME) {
				discard();
				return;
			}
		}
		Vec3 v = getDeltaMovement();
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
	}

	/** The damage one hit deals: a uniform draw in {@code [DAMAGE_MIN, DAMAGE_MAX)}. */
	public float rollDamage() {
		return DAMAGE_MIN + random.nextFloat() * (DAMAGE_MAX - DAMAGE_MIN);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		LivingEntity shooter = getOwner() instanceof LivingEntity living ? living : null;
		result.getEntity().hurt(SednaDamageTypes.getDamage(level(), this, shooter, DamageClass.SUBATOMIC), rollDamage());
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		discard();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}
}
