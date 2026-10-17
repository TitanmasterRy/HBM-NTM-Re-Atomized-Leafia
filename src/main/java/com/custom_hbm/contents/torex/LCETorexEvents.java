package com.custom_hbm.contents.torex;

import com.leafia.lib.LeafiaRef;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What ticks a torex: 1.12.2's {@code World.updateEntities} did it for the weather-effects list, and
 * this is that loop. One handler for both sides, because {@code LevelTickEvent} fires for a server
 * level and for the client's, and {@link LCETorex#tickLevel} picks the list. Nothing in here
 * names a client class, so it is the same code on a dedicated server.
 *
 * <p>Each of the switch's two effects is in {@code tickLevel} and the clients' handlers, not here:
 * with {@code features.torex} off the lists are never filled, so there is nothing to tick, and
 * {@code tickLevel} returns on its first line anyway.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LCETorexEvents {

	private LCETorexEvents() {}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		LCETorex.tickLevel(event.getLevel());
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if(event.getLevel() instanceof Level level) LCETorex.forgetLevel(level);
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		LCETorex.forgetAll();
	}
}
