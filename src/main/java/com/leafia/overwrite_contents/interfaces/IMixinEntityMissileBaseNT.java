package com.leafia.overwrite_contents.interfaces;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.leafia.config.LeafiaFeatures;
import com.leafia.database.AirbustMissiles;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

/**
 * How a missile ends (D-3022): whether it bursts in the air over its target, and what a shoot-down does to it.
 *
 * <p>Upstream's interface, which {@code MixinEntityMissileBaseNT} implemented on every NTM missile. Here a mixin
 * cannot add it to the base mod's missiles (D-1001), so it is two things:
 * <ul>
 *   <li>an interface the addon's own missiles implement and override - the custom nuke missile does - whose
 *       defaults are upstream's;</li>
 *   <li>statics, {@link #shouldDetonateInAir(EntityMissileBaseNT)} and {@link #interactABMissile(EntityMissileBaseNT)},
 *       that answer for any missile: the interface's own answer when it implements it, and otherwise upstream's
 *       default for a base-mod missile. {@code LeafiaStrikeSeams}' missile hooks call these.</li>
 * </ul>
 *
 * <p>{@link #governs} is where the switch is: a base-mod missile is only the addon's to end when
 * {@code overhauls.missileOverhaul} is on, an addon missile always.
 */
public interface IMixinEntityMissileBaseNT {

	/** Nuclear missiles should use this: upstream's default reads {@link AirbustMissiles}. */
	default boolean shouldDetonateInAir() {
		return AirbustMissiles.defaultAirDetonationMissiles.contains(((EntityMissileBaseNT) this).getMissileItemForInfo().getItem());
	}

	/**
	 * Usually shouldn't be overridden. {@code true} if a shot-down missile should drop debris (and the base mod's
	 * small blast and shrapnel), {@code false} if it set its warhead off instead.
	 */
	default boolean interactABMissile() {
		if(shouldDetonateInAir()) return true;
		explodeAB();
		return false;
	}

	/** Override this for custom interaction: by default, the warhead goes off where the missile is. */
	default void explodeAB() {
		EntityMissileBaseNT missile = (EntityMissileBaseNT) this;
		missile.onMissileImpact(generateFakeRayTraceResult(missile));
	}

	/** A hit on the block the missile is in, from above, as upstream built it. */
	static BlockHitResult generateFakeRayTraceResult(EntityMissileBaseNT missile) {
		return new BlockHitResult(missile.position(), Direction.UP, missile.blockPosition(), false);
	}

	/** Whether the addon decides how this missile ends: its own missiles always, the base mod's with the switch. */
	static boolean governs(EntityMissileBaseNT missile) {
		return missile instanceof IMixinEntityMissileBaseNT || LeafiaFeatures.missileOverhaul;
	}

	static boolean shouldDetonateInAir(EntityMissileBaseNT missile) {
		if(missile instanceof IMixinEntityMissileBaseNT own) return own.shouldDetonateInAir();
		return AirbustMissiles.defaultAirDetonationMissiles.contains(missile.getMissileItemForInfo().getItem());
	}

	static boolean interactABMissile(EntityMissileBaseNT missile) {
		if(missile instanceof IMixinEntityMissileBaseNT own) return own.interactABMissile();
		if(shouldDetonateInAir(missile)) return true;
		missile.onMissileImpact(generateFakeRayTraceResult(missile));
		return false;
	}
}
