package com.leafia.contents.gear.combat.guns.am_rifle;

import javax.annotation.Nullable;

import com.hbm.entity.projectile.EntityBulletBeamBase;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.slices.GunSystem;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.leafia.dev.math.FiaMatrix.TupleRotation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The anti-mass rifle's beam: the base mod's sedna beam, aimed either where the shooter looks or, with a lock, from the shooter's
 * eyes at the locked entity's eyes, with no spread either way (D-3028).
 *
 * <p>Ported from {@code com.leafia.contents.gear.combat.guns.am_rifle.AMRifleBeam}: upstream's constructor, line for line. It is
 * <b>the base mod's beam entity type</b> ({@code hbm:entity_beam_mk4}) rather than one of its own, which upstream registered only so
 * that 1.12.2 could construct the class: everything that differs happens in this constructor, on the server, before the beam is
 * sent, and a client draws it from its config id and its length like any other beam. So a client builds a plain
 * {@code EntityBulletBeamBase} for it, and nothing about it is saved (the base beam is never saved).
 */
public class AMRifleBeam extends EntityBulletBeamBase {

	/** The reach of the hit-scan: upstream's {@code range = 250D}. */
	public static final double RANGE = 250D;

	public AMRifleBeam(LivingEntity entity, @Nullable Entity lockTarget, BulletConfig config, float baseDamage, double sideOffset, double heightOffset, double frontOffset) {
		this(entity.level());

		this.thrower = entity;
		this.setBulletConfig(config);

		this.damage = baseDamage * this.config.damageMult;

		if(lockTarget == null)
			this.moveTo(thrower.getX(), thrower.getEyeY(), thrower.getZ(), thrower.getYRot(), thrower.getXRot());
		else {
			FiaMatrix mat = new FiaMatrix(new Vec3(thrower.getX(), thrower.getEyeY(), thrower.getZ()), new Vec3(lockTarget.getX(), lockTarget.getEyeY(), lockTarget.getZ()));
			TupleRotation rot = mat.getRotation(RotationOrder.YXZ);
			this.moveTo(thrower.getX(), thrower.getEyeY(), thrower.getZ(),
					(float) -rot.angleY + 180, (float) -rot.angleX
			);
		}

		Vec3 offset = new Vec3(sideOffset, heightOffset, frontOffset)
				.xRot(-this.getXRot() / 180F * (float) Math.PI)
				.yRot(-this.getYRot() / 180F * (float) Math.PI);

		this.setPos(this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z);

		this.headingX = -Mth.sin(this.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(this.getXRot() / 180.0F * (float) Math.PI);
		this.headingZ = Mth.cos(this.getYRot() / 180.0F * (float) Math.PI) * Mth.cos(this.getXRot() / 180.0F * (float) Math.PI);
		this.headingY = -Mth.sin((this.getXRot()) / 180.0F * (float) Math.PI);

		double range = RANGE;
		this.headingX *= range;
		this.headingY *= range;
		this.headingZ *= range;

		performHitscan();
	}

	public AMRifleBeam(Level world) {
		this(GunSystem.BEAM_MK4.get(), world);
	}

	public AMRifleBeam(EntityType<? extends EntityBulletBeamBase> type, Level world) {
		super(type, world);
	}
}
