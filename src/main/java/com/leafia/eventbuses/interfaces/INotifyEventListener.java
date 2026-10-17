package com.leafia.eventbuses.interfaces;

import com.leafia.eventbuses.NotifyEventListeners;

import net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent;

/**
 * Something on the server that wants to hear about every block change, for a minute after it last asked.
 *
 * <p>Upstream: {@code com.leafia.eventbuses.interfaces.INotifyEventListener}, which put the listener in
 * {@code LeafiaServerListener.Unsorted.notifyEventListeners} with the time it asked. That listener is not
 * ported whole (it is 400 lines of unrelated handlers); its two halves that serve this interface - the
 * dispatch on {@code NeighborNotifyEvent} and the one-minute expiry in {@code LeafiaPassiveServer.onTick} -
 * are {@link NotifyEventListeners}. A subscriber renews itself by calling {@link #subscribeToBlockNotification}
 * every tick, which is what the APR core does.
 */
public interface INotifyEventListener {

	void onBlockNotify(NeighborNotifyEvent evt);

	default void subscribeToBlockNotification() {
		NotifyEventListeners.subscribe(this);
	}
}
