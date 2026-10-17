package com.leafia.contents.machines.controlpanel.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Something a Custom Control Panel can link to and drive: a panel itself, a machine, a door. Adapted from NTM-CE's
 * {@code IControllable} (D-3061), with the 1.12.2 {@code World} replaced by {@link Level}.
 *
 * <p>An addon block entity implements it directly. A <b>base-mod</b> block entity cannot (the addon never edits {@code com.hbm},
 * D-1001), so the addon answers for it through {@link ControlEventSystem#CONTROLLABLE}, a block capability whose provider
 * returns an adapter implementing this interface over the base entity's public fields (D-3060 #4, batch 8).
 *
 * <p>Everything but the two position methods has a default that does nothing, so a read-only target overrides only
 * {@link #getQueryData} and a write-only one only {@link #getInEvents} and {@link #receiveEvent}.
 */
public interface IControllable {

	/** The event names this target accepts. {@code tick} here makes it a per-tick target of its level's {@link ControlEventSystem}. */
	default List<String> getInEvents() {
		return Collections.emptyList();
	}

	/** The event names this target may send back. A panel offers these when configuring a link. */
	default List<String> getOutEvents() {
		return Collections.emptyList();
	}

	/** What {@code NodeQueryBlock} can read: a key to its current value. A key absent here reads as "no value", not as 0. */
	default Map<String, DataValue> getQueryData() {
		return Collections.emptyMap();
	}

	/**
	 * Delivers an event. Most targets answer by sending their out event back — a lazy fix upstream, so that other controls
	 * sharing a panel also see the broadcast.
	 */
	default void receiveEvent(BlockPos from, ControlEvent e) {}

	BlockPos getControlPos();

	Level getControlWorld();
}
