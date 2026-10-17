package com.leafia.unsorted.explosion_vnt;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.leafia.init.LeafiaDamageSources;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A player-placed landmine's blast (D-3022): the base mod's smooth cross processor at a flat <b>600,000</b> -
 * the mine's own damage argument is ignored - through {@code leafia:mine_player}, which is absolute but does not
 * bypass armour. Nothing is halved or capped; {@code 600,000 x (1 - distance)} kills anything in reach.
 *
 * <p>Upstream: {@code com/leafia/unsorted/explosion_vnt/EntityProcessorPlayerLandmine.java}; see
 * {@link EntityProcessorLandmine} for how it is swapped in and what became of {@code attackEntityFromNT}.
 */
public class EntityProcessorPlayerLandmine extends EntityProcessorCrossSmooth {

	/** The damage every player-placed mine deals, whatever it was configured with. */
	public static final float DAMAGE = 600000F;

	public EntityProcessorPlayerLandmine(double nodeDist, float fixedDamage) {
		super(nodeDist, DAMAGE);
	}

	@Override
	public void attackEntity(Entity entity, ExplosionVNT source, float amount) {
		if(!entity.isAlive()) return;
		DamageSource dmg = LeafiaDamageSources.of(source.world, LeafiaDamageSources.mine_player);
		if(entity instanceof LivingEntity living) living.invulnerableTime = 0;
		entity.hurt(dmg, amount);
	}
}
