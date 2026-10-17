package com.leafia.contents.effects.zomg;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.leafia.init.LeafiaEntities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code ExplosionChaos.zomg}: a volley of rainbow beams from one point, one at every living thing within range.
 *
 * <p>NTM-CE base code, in neither this base mod nor {@code hbm-src}; D-3030 #4 has the addon register it in its own
 * namespace, for the pink door's last effect (upstream's {@code ExplosionChaos.zomg(world, x, y, z, 30, null, null)}).
 * <b>A reconstruction, not a port</b> (D-3031, flagged): no NTM-CE source is in the reference, so the shape is what the
 * call and the earlier record of it (D-3028: "a rainbow-beam volley at every living thing within 30 blocks") say - the
 * {@code 30} is the reach, a box of that half-width round the point, the second-to-last argument the player to spare
 * and the last the grenade it came from (both null at the door's call, and the grenade does not exist here). Each beam
 * is an {@link EntityRainbow} aimed at its target's eyes at {@link #SPEED} a tick.
 */
public final class LeafiaZomg {

	private LeafiaZomg() {}

	/** Blocks a tick: the {@code 1F} the old ZOMG passed its rainbow as its speed. */
	public static final double SPEED = 1.0D;

	/** Every living thing the volley aims at, in the level's order: inside the box, alive, and not {@code spare}. */
	public static List<LivingEntity> targets(Level level, double x, double y, double z, double reach, @Nullable Entity spare) {
		AABB box = new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
		return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != spare && e.isAlive()));
	}

	/** Fires the volley. Server side; does nothing while the rainbow is not registered (the pink door's switch is off). */
	public static int zomg(Level level, double x, double y, double z, double reach, @Nullable Entity spare) {
		if(level.isClientSide || LeafiaEntities.RAINBOW == null) return 0;
		int fired = 0;
		for(LivingEntity target : targets(level, x, y, z, reach, spare)) {
			Vec3 aim = new Vec3(target.getX() - x, target.getEyeY() - y, target.getZ() - z);
			if(aim.lengthSqr() < 1.0E-8) aim = new Vec3(0, 1, 0);
			level.addFreshEntity(new EntityRainbow(level, x, y, z, aim.normalize().scale(SPEED), spare));
			fired++;
		}
		return fired;
	}
}
