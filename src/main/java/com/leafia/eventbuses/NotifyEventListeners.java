package com.leafia.eventbuses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.eventbuses.interfaces.INotifyEventListener;
import com.leafia.lib.LeafiaRef;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The two halves of upstream that keep {@link INotifyEventListener}s: {@code LeafiaServerListener.Unsorted}'s
 * {@code notifyEventListeners} map with its {@code NeighborNotifyEvent} dispatch, and the expiry in
 * {@code LeafiaPassiveServer.onTick}.
 *
 * <ul>
 * <li>A listener is held with the wall-clock time it last {@linkplain #subscribe subscribed}.</li>
 * <li>Every server-side neighbour notification, in any level, is handed to every listener (upstream's; a
 *     listener checks its own level - the APR core reads the position in its own world).</li>
 * <li>Each server tick drops a listener that has not renewed for {@link #EXPIRY_MS} (60 seconds) and a block
 *     entity that has been removed (upstream's {@code isInvalid()}).</li>
 * </ul>
 *
 * <p>One port difference, with no effect on an outcome: the dispatch walks a copy of the listeners, so a
 * listener whose answer changes blocks (and so notifies again, re-entrantly) cannot disturb the walk it is in.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NotifyEventListeners {

	private NotifyEventListeners() {}

	/** Upstream's {@code System.currentTimeMillis() - 60_000}. */
	public static final long EXPIRY_MS = 60_000L;

	private static final Map<INotifyEventListener, Long> LISTENERS = new HashMap<>();

	public static void subscribe(INotifyEventListener listener) {
		LISTENERS.put(listener, System.currentTimeMillis());
	}

	public static void unsubscribe(INotifyEventListener listener) {
		LISTENERS.remove(listener);
	}

	public static boolean isSubscribed(INotifyEventListener listener) {
		return LISTENERS.containsKey(listener);
	}

	/** For a test: when {@code listener} last subscribed, or null. */
	public static Long lastSubscribed(INotifyEventListener listener) {
		return LISTENERS.get(listener);
	}

	/** For a test: backdate a subscription. */
	public static void setLastSubscribed(INotifyEventListener listener, long time) {
		if(LISTENERS.containsKey(listener)) LISTENERS.put(listener, time);
	}

	@SubscribeEvent
	public static void onBlockNotify(BlockEvent.NeighborNotifyEvent evt) {
		if(evt.getLevel().isClientSide() || LISTENERS.isEmpty()) return;
		for(INotifyEventListener listener : new ArrayList<>(LISTENERS.keySet()))
			listener.onBlockNotify(evt);
	}

	/** Upstream's sweep in {@code LeafiaPassiveServer.onTick}. */
	public static void expire(long now) {
		List<INotifyEventListener> listeners = new ArrayList<>(LISTENERS.keySet());
		for(INotifyEventListener listener : listeners) {
			if(LISTENERS.get(listener) < now - EXPIRY_MS) {
				LISTENERS.remove(listener);
				continue;
			}
			if(listener instanceof BlockEntity be && be.isRemoved())
				LISTENERS.remove(listener);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if(!LISTENERS.isEmpty()) expire(System.currentTimeMillis());
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		LISTENERS.clear();
	}
}
