package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Upstream's {@code @SideOnly(Side.CLIENT) local$grindEntity}, in a class of its own.
 *
 * <p>Not an upstream file. The method names {@link Minecraft}, which a dedicated server does not
 * have; kept on {@link MTFlywheelTE} it would sit in a class the server loads. Here it is only
 * linked when a client-side flywheel calls it, which is the same shape {@code ParticleUtil}
 * uses for the base mod's client particle dispatch.
 */
public final class MTFlywheelClient {

	private MTFlywheelClient() {}

	/** Moves or grinds {@code entity} only if it is this client's own player. */
	public static void grindIfLocalPlayer(MTFlywheelTE flywheel, Entity entity, double damage) {
		if(entity.equals(Minecraft.getInstance().player))
			flywheel.grindEntity(entity, damage);
	}
}
