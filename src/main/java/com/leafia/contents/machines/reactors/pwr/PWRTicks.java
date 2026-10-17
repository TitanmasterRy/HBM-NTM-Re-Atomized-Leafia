package com.leafia.contents.machines.reactors.pwr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.lib.LeafiaRef;
import com.llib.group.LeafiaSet;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The PWR's share of upstream's per-tick server listeners, which are not coming as a unit
 * ({@code LeafiaPassiveServer} and {@code LeafiaServerListener} are accumulators, D-1056).
 *
 * <ul>
 * <li>{@code LeafiaPassiveServer.queueFunction}: a component placed in the world starts its diagnosis on
 * the <b>next</b> server tick, because the block entity does not exist yet while the block is placed. The
 * queue runs at the start of the server tick, where upstream's {@code priorTick} ran it. It is a queue
 * rather than {@code server.execute}, which on the server thread runs at once (D-1056's finding, MTCoreTE).</li>
 * <li>{@code LeafiaPassiveServer.onTick}: {@link PWRDiagnosis#preventScan} is cleared at the end of every
 * server tick.</li>
 * <li>{@code LeafiaServerListener.worldTick}: {@link PWRDiagnosis#cleanup} when a level's game time is
 * 97 modulo 100, on both phases as upstream's (whose check sits outside its phase tests).</li>
 * <li>{@code LeafiaServerListener.onBlockNotify}: any block change at a position a fuel column traced
 * its neutrons through makes that column re-trace ({@link PWRElementTE#updateObstacleMappings}).
 * Upstream removes dead entries from the map while iterating it; this iterates a copy.</li>
 * </ul>
 *
 * <p>Nothing here registers anything, so it needs no feature gate: with the PWR off the queue and the
 * maps stay empty.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PWRTicks {

	private PWRTicks() {}

	static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

	public static void queueFunction(Runnable callback) {
		queue.add(callback);
	}

	/** Runs and clears the queue: what the start of a server tick does. Public for tests. */
	public static void runQueue() {
		List<Runnable> running = new ArrayList<>(queue);
		queue.clear();
		for(Runnable callback : running) {
			if(callback != null) callback.run();
		}
	}

	@SubscribeEvent
	public static void serverTickPre(ServerTickEvent.Pre event) {
		runQueue();
	}

	@SubscribeEvent
	public static void serverTickPost(ServerTickEvent.Post event) {
		PWRDiagnosis.preventScan.clear();
	}

	@SubscribeEvent
	public static void levelTickPre(LevelTickEvent.Pre event) {
		if(!event.getLevel().isClientSide && event.getLevel().getGameTime() % 100 == 97) PWRDiagnosis.cleanup();
	}

	@SubscribeEvent
	public static void levelTickPost(LevelTickEvent.Post event) {
		if(!event.getLevel().isClientSide && event.getLevel().getGameTime() % 100 == 97) PWRDiagnosis.cleanup();
	}

	@SubscribeEvent
	public static void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
		if(event.getLevel().isClientSide()) return;
		notifyChange(event.getPos());
	}

	/** The listener body, for tests: re-traces every fuel column that traced through {@code pos}. */
	public static void notifyChange(BlockPos pos) {
		List<Entry<PWRElementTE, LeafiaSet<BlockPos>>> entries = new ArrayList<>(PWRElementTE.listeners.entrySet());
		for(Entry<PWRElementTE, LeafiaSet<BlockPos>> entry : entries) {
			if(entry.getKey().isRemoved()) {
				PWRElementTE.listeners.remove(entry.getKey());
				continue;
			}
			if(entry.getValue().contains(pos)) entry.getKey().updateObstacleMappings();
		}
	}
}
