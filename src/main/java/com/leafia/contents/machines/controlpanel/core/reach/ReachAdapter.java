package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * An {@link IControllable} over a block entity of the base mod, which never learns the panel exists (D-1001, D-3060 #4, batch 8).
 * The addon hands one out through {@link ControlEventSystem#CONTROLLABLE} wherever {@link BaseReach} registered a provider.
 *
 * <p>An adapter is <b>stateless and cheap</b>: the capability builds a fresh one on every lookup, so nothing it holds survives a
 * tick. Whatever has to outlive a lookup (the previous value of something a machine changes without telling anyone) is kept by
 * {@link BaseReachPoller}, which asks {@link #snapshot()} and {@link #diff}.
 *
 * <h2>Reading and writing the base machine</h2>
 *
 * Only the base's <b>public</b> fields and methods, so a rename on master breaks this module's compile, which is the loud
 * failure D-3060 wants. A <b>meaning</b> change does not, and each adapter has a GameTest against a live block entity for that.
 *
 * <h2>In-events respect the machine's own bounds</h2>
 *
 * A panel event is a packet a modified client could author, so every in-event is clamped to what the machine's own GUI and
 * packet path accepts (a mode to its mode count, a rod level to 0..1, a pump buffer to 0..10,000). Where NTM-CE's body would
 * have stored an out-of-range value, the adapter stores the nearest legal one, and the adapter's class note says so.
 */
public abstract class ReachAdapter<T extends BlockEntity> implements IControllable {

	/** The base machine. Public so a test can compare an adapter with the block entity it came from. */
	public final T be;

	protected ReachAdapter(T be) {
		this.be = be;
	}

	@Override
	public BlockPos getControlPos() {
		return be.getBlockPos();
	}

	@Override
	public Level getControlWorld() {
		return be.getLevel();
	}

	// ------------------------------------------------------------------ the out-event poller's half

	/**
	 * Whether this machine has out-events at all. When true, {@link BaseReachPoller} watches it while anything is subscribed to
	 * its position; when false it is never polled.
	 */
	public boolean polled() {
		return false;
	}

	/**
	 * A value that is {@code equals} to a later one exactly when nothing a panel can see has changed: a record, an {@link Integer}
	 * or a {@link List}. Null for an adapter that is not {@link #polled()}.
	 */
	public Object snapshot() {
		return null;
	}

	/** The events to send when {@code prev} became {@code now}; called only when they differ. */
	public List<ControlEvent> diff(Object prev, Object now) {
		return Collections.emptyList();
	}

	/**
	 * Sends an event to everything subscribed to this machine immediately and tells the poller the machine is now at its current
	 * snapshot, so the same change is not announced a second time at the end of the tick. For the two echoes NTM-CE sends from
	 * inside {@code receiveEvent} (the tank's mode, the crane's move).
	 */
	protected final void echo(ControlEvent evt) {
		Level level = be.getLevel();
		if(level == null || level.isClientSide)
			return;
		ControlEventSystem.get(level).broadcastToSubscribed(this, evt);
		BaseReachPoller.rebase(this);
	}

	// ------------------------------------------------------------------ small helpers the families share

	/** A mutable, ordered query map; the order is the order CE's {@code put} calls ran in. */
	protected static Map<String, DataValue> map() {
		return new LinkedHashMap<>();
	}

	protected static DataValue num(float f) {
		return new DataValueFloat(f);
	}

	protected static DataValue str(String s) {
		return new DataValueString(s);
	}

	/** An event variable's number, or {@code fallback} when the event does not carry the variable. */
	protected static float number(ControlEvent e, String key, float fallback) {
		DataValue v = e.vars.get(key);
		return v == null ? fallback : v.getNumber();
	}

	/** A variable's number truncated like Java's {@code (int)} cast of a float (NaN is 0, out of range saturates). */
	protected static int whole(ControlEvent e, String key) {
		return (int) number(e, key, 0F);
	}

	protected static boolean flag(ControlEvent e, String key, boolean fallback) {
		DataValue v = e.vars.get(key);
		return v == null ? fallback : v.getBoolean();
	}
}
