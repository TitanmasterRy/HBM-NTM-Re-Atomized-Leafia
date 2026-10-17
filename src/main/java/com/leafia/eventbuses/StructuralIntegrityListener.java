package com.leafia.eventbuses;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.StructuralIntegrityHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What drives structural integrity: the two hooks upstream carries inside {@code LeafiaServerListener}
 * (the neighbour-notify handler) and {@code LeafiaPassiveServer.priorTick} (the per-tick reset and the
 * queue), pulled out into a class of their own because neither of those files is ported.
 *
 * <p>The sequence, which is upstream's:
 * <ol>
 *   <li>A block changes and the world notifies its neighbours. Nothing is calculated now - the
 *       change is queued.
 *   <li>The next server tick opens by resetting the budget ({@code calculations = 0}, no position
 *       blocked), then drains the queue: for each entry the changed position, then each side that was
 *       notified, goes to {@link StructuralIntegrityHandler#handleBlock}.
 *   <li>A collapse changes the world and so queues the next wave, which is the next tick's.
 * </ol>
 *
 * <p><b>With the switch off this does nothing</b>: the notify handler returns on its first line,
 * before anything is allocated or queued, so a world without the feature pays one field read per
 * block update. (Upstream queues a closure per notification and checks the switch inside it.)
 *
 * <p>Two things differ from upstream's queue, and neither changes an outcome. Entries are
 * <b>de-duplicated</b> - the same level, position and sides twice in a tick is a second call for a
 * position already blocked, a certain no-op - and the queue is <b>capped</b> at {@link #MAX_QUEUED}
 * so that a world-edit of millions of blocks cannot grow it without limit. Upstream's list is
 * unbounded and drops everything past 200 calculations anyway.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StructuralIntegrityListener {

	private StructuralIntegrityListener() {}

	/** Memory guard, about 3 MB. Far above anything 200 calculations can use. */
	public static final int MAX_QUEUED = 65_536;

	private record Entry(ServerLevel level, long pos, int sides) {}

	private static Set<Entry> queue = new LinkedHashSet<>();

	/** How many changes are waiting for the next tick. */
	public static int pending() {
		return queue.size();
	}

	public static void clearQueue() {
		queue = new LinkedHashSet<>();
	}

	private static int mask(Iterable<Direction> sides) {
		int m = 0;
		for (Direction side : sides)
			m |= 1 << side.ordinal();
		return m;
	}

	@SubscribeEvent
	public static void onBlockNotify(BlockEvent.NeighborNotifyEvent event) {
		if (!LeafiaFeatures.structuralIntegrity) return;
		if (!(event.getLevel() instanceof ServerLevel level)) return;
		if (StructuralIntegrityHandler.blacklistedDimensions.contains(level.dimension())) return;
		if (queue.size() >= MAX_QUEUED) return;
		queue.add(new Entry(level, event.getPos().asLong(), mask(event.getNotifiedSides())));
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Pre event) {
		StructuralIntegrityHandler.blockedPoses.clear();
		StructuralIntegrityHandler.calculations = 0;
		if (queue.isEmpty()) return;
		Set<Entry> running = queue;
		queue = new LinkedHashSet<>();
		if (!LeafiaFeatures.structuralIntegrity) return;
		for (Entry entry : running) {
			BlockPos pos = BlockPos.of(entry.pos());
			StructuralIntegrityHandler.handleBlock(entry.level(), pos);
			for (Direction side : Direction.values()) {
				if ((entry.sides() & (1 << side.ordinal())) != 0)
					StructuralIntegrityHandler.handleBlock(entry.level(), pos.relative(side));
			}
		}
	}

	/** A level that unloads must not be kept alive by, or calculated through, a queued change. */
	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (queue.isEmpty()) return;
		Iterator<Entry> it = queue.iterator();
		while (it.hasNext()) {
			if (it.next().level() == event.getLevel())
				it.remove();
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		clearQueue();
	}
}
