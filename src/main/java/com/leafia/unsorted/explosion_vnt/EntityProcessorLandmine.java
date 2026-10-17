package com.leafia.unsorted.explosion_vnt;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.leafia.init.LeafiaDamageSources;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A worldgen landmine's blast (D-3022): the base mod's smooth cross processor at <b>4.5 times</b> the mine's damage,
 * every hit then <b>halved</b> - upstream left the "only the exploder takes half" test commented out, so everyone
 * does - and a halved hit of 22 or less <b>capped at 11</b> (upstream: "nerfed since player-placed mines deals
 * 600000"; the 17 it replaced is a comment). So a mine hits for {@code 2.25 x damage x (1 - distance)}, and at most
 * 11 wherever that comes to 22 or less.
 *
 * <p>Upstream: {@code com/leafia/unsorted/explosion_vnt/EntityProcessorLandmine.java}, which {@code MixinLandmine}
 * swapped in for every mine's {@code new EntityProcessorCrossSmooth(nodeDist, damage)}. Here
 * {@code LeafiaStrikeSeams.LANDMINE_BLAST} does it through the base mod's {@code Landmine.DetonationHook}.
 *
 * <p>The source is {@code leafia:mine} (an explosion that bypasses armour). Upstream hurt living things through
 * {@code EntityDamageUtil.attackEntityFromNT(..., true, false, 0, pierceDT, pierceDR)}, which ignores the
 * invulnerability frames; the base mod has no damage-threshold system to pierce, so - as its own
 * {@link EntityProcessorCrossSmooth} does for the same call - the frames are cleared and the hit lands. The
 * confetti on a kill is cosmetic gibbing the base mod does not port either.
 */
public class EntityProcessorLandmine extends EntityProcessorCrossSmooth {

	/** The mine's damage is multiplied by this at construction. */
	public static final float DAMAGE_MULT = 4.5F;
	/** Every hit is multiplied by this. */
	public static final float HIT_MULT = 0.5F;
	/** A hit at or under this... */
	public static final float CAP_BELOW = 22F;
	/** ...is capped at this. */
	public static final float CAP = 11F;

	public EntityProcessorLandmine(double nodeDist, float fixedDamage) {
		super(nodeDist, fixedDamage * DAMAGE_MULT);
	}

	/** What a hit of {@code amount} becomes: halved, then capped at 11 if 22 or less. */
	public static float hit(float amount) {
		amount *= HIT_MULT;
		if(amount <= CAP_BELOW) amount = Math.min(amount, CAP);
		return amount;
	}

	@Override
	public void attackEntity(Entity entity, ExplosionVNT source, float amount) {
		if(!entity.isAlive()) return;
		amount = hit(amount);
		DamageSource dmg = LeafiaDamageSources.of(source.world, LeafiaDamageSources.mine);
		if(entity instanceof LivingEntity living) living.invulnerableTime = 0;
		entity.hurt(dmg, amount);
	}
}
