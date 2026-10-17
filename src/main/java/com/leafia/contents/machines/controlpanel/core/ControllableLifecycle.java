package com.leafia.contents.machines.controlpanel.core;

import java.util.LinkedHashMap;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What every addon machine that implements {@link IControllable} does on load and on removal (batch 9, D-3069): join its level's
 * {@link ControlEventSystem} on the server, leave it when removed or when its chunk unloads. NTM-CE's {@code validate} and
 * {@code invalidate} became {@code onLoad} and {@code setRemoved}/{@code onChunkUnloaded}, as {@code ControlPanelTE} has them
 *. Registration matters only for a {@code tick} in-event and for targets that are not block
 * entities; dispatch to a machine is positional and finds the block entity itself first, so a machine that forgot to call this
 * still answers, which is why the call is cheap insurance rather than load-bearing.
 *
 * <p>Both calls are no-ops on the client and on a block entity with no level, so a machine calls them unconditionally.
 */
public final class ControllableLifecycle {

	private ControllableLifecycle() {}

	/** Call from {@code onLoad}, after {@code super.onLoad()}. */
	public static <T extends BlockEntity & IControllable> void join(T be) {
		if(be.getLevel() == null || be.getLevel().isClientSide)
			return;
		ControlEventSystem.get(be.getLevel()).addControllable(be);
	}

	/** Call from {@code setRemoved} and {@code onChunkUnloaded}. Never creates a system that does not exist. */
	public static <T extends BlockEntity & IControllable> void leave(T be) {
		if(be.getLevel() == null || be.getLevel().isClientSide)
			return;
		ControlEventSystem system = ControlEventSystem.getIfPresent(be.getLevel());
		if(system != null)
			system.removeControllable(be);
	}

	/** A query map from alternating key and number, in order: {@code values("heat", heat, "maxHeat", max)}. */
	public static Map<String, DataValue> values(Object... keysAndNumbers) {
		Map<String, DataValue> map = new LinkedHashMap<>();
		for(int i = 0; i + 1 < keysAndNumbers.length; i += 2)
			map.put((String) keysAndNumbers[i], new DataValueFloat(((Number) keysAndNumbers[i + 1]).floatValue()));
		return map;
	}

	/** A float map (the shape the machines kept their panel values in before the retrofit) as query data. */
	public static Map<String, DataValue> floats(Map<String, Float> floats) {
		Map<String, DataValue> map = new LinkedHashMap<>();
		for(Map.Entry<String, Float> e : floats.entrySet())
			map.put(e.getKey(), new DataValueFloat(e.getValue()));
		return map;
	}

	/** An event variable as a number, or {@code NaN} when it is absent or not a number; callers ignore the event on NaN. */
	public static double number(ControlEvent e, String key) {
		DataValue v = e.vars.get(key);
		return v == null ? Double.NaN : v.getNumber();
	}

	/**
	 * An event variable in percent as a fraction clamped to 0..1, or {@code NaN} (to be ignored). {@code Mth.clamp} lets NaN
	 * through, and a NaN rod position would never arrive anywhere.
	 */
	public static double fraction(ControlEvent e, String key) {
		double n = number(e, key) / 100d;
		return Double.isNaN(n) ? Double.NaN : Math.max(0d, Math.min(1d, n));
	}

	/** An event variable as an integer clamped to {@code min..max}, or {@code Integer.MIN_VALUE} when it is absent or NaN. */
	public static int clampedInt(ControlEvent e, String key, int min, int max) {
		double n = number(e, key);
		if(Double.isNaN(n))
			return Integer.MIN_VALUE;
		return (int) Math.max(min, Math.min(max, Math.round(n)));
	}
}
