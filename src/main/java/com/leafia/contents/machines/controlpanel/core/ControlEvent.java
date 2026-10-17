package com.leafia.contents.machines.controlpanel.core;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * A named bundle of variables sent between a panel and the machines linked to it, and the registry of every name that may
 * be sent. Adapted from NTM-CE's {@code ControlEvent} (D-3061).
 *
 * <p>The registered event is a <b>template</b>: {@link #newEvent} copies it, so the defaults a receiver sees for a field the
 * sender never set are the ones registered here. {@link #init} is CE's table followed by the nineteen events Leafia's
 * {@code MixinControlEvent} appended at its tail (folded here because a Leafia build always had them; the mixin row is
 * re-ticked with this batch, D-3061). It runs once, on first use of the registry, and is safe to call again.
 *
 * <p>An event name that is not registered throws {@link #getRegisteredEvent}; that is how an unknown name in a saved panel
 * surfaces, and the loaders turn it into an unresolved-node placeholder rather than a crash.
 */
public class ControlEvent {

	private static final Map<String, ControlEvent> REGISTRY = new Object2ObjectOpenHashMap<>();
	private static boolean initialized = false;

	public String name;
	public Map<String, DataValue> vars = new Object2ObjectLinkedOpenHashMap<>();

	public ControlEvent(String name) {
		this.name = name;
	}

	public ControlEvent setVar(String key, DataValue val) {
		vars.put(key, val);
		return this;
	}

	public ControlEvent setVar(String key, float f) {
		vars.put(key, new DataValueFloat(f));
		return this;
	}

	public ControlEvent setVar(String key, boolean b) {
		vars.put(key, new DataValueFloat(b ? 1 : 0));
		return this;
	}

	public ControlEvent setVar(String key, String str) {
		vars.put(key, new DataValueString(str));
		return this;
	}

	public <E extends Enum<E>> ControlEvent setVar(String key, E enm) {
		vars.put(key, new DataValueEnum<E>(enm));
		return this;
	}

	public ControlEvent copy() {
		ControlEvent evt = new ControlEvent(name);
		// Set default values
		for(Entry<String, DataValue> def : vars.entrySet()) {
			evt.vars.put(def.getKey(), def.getValue().copy());
		}
		return evt;
	}

	public CompoundTag writeToNBT(CompoundTag tag) {
		tag.putString("name", this.name);
		CompoundTag vars = new CompoundTag();
		for(Entry<String, DataValue> e : this.vars.entrySet()) {
			vars.put(e.getKey(), e.getValue().writeToNBT());
		}
		tag.put("vars", vars);
		return tag;
	}

	public static ControlEvent readFromNBT(CompoundTag tag) {
		ControlEvent evt = ControlEvent.newEvent(tag.getString("name"));
		CompoundTag vars = tag.getCompound("vars");
		for(String k : vars.getAllKeys()) {
			DataValue val = DataValue.newFromNBT(vars.get(k));
			if(val != null)
				evt.vars.put(k, val);
		}
		return evt;
	}

	public static ControlEvent newEvent(String name) {
		return getRegisteredEvent(name).copy();
	}

	public static synchronized ControlEvent getRegisteredEvent(String name) {
		init();
		ControlEvent e = REGISTRY.get(name);
		if(e == null)
			throw new RuntimeException("Unregistered control event: " + name);
		return e;
	}

	/** Whether this name is registered (and registers the stock table first if nothing has). */
	public static synchronized boolean isRegistered(String name) {
		init();
		return REGISTRY.containsKey(name);
	}

	/** Every registered name. */
	public static synchronized Set<String> registeredNames() {
		init();
		return new java.util.LinkedHashSet<>(REGISTRY.keySet());
	}

	public static synchronized void register(ControlEvent c) {
		REGISTRY.put(c.name, c);
	}

	/**
	 * Registers CE's events and then Leafia's. Idempotent: the second call does nothing, so a caller may use it as a "make sure"
	 * without re-registering over an event an addon has since replaced.
	 */
	public static synchronized void init() {
		if(initialized)
			return;
		initialized = true;

		register(new ControlEvent("tick").setVar("time", 0));
		register(new ControlEvent("door_open_state").setVar("state", 0));
		register(new ControlEvent("door_toggle").setVar("passcode", 0));
		register(new ControlEvent("turret_set_target").setVar("players", false).setVar("passive", false).setVar("hostile", true).setVar("machines", true));
		register(new ControlEvent("turret_switch").setVar("isOn", true).setVar("isBlacklistFilter", true));
		register(new ControlEvent("lever_toggle").setVar("isOn", false));
		register(new ControlEvent("spinny_light_power").setVar("isOn", false));
		register(new ControlEvent("siren_set_state").setVar("isOn", false));
		register(new ControlEvent("tank_set_mode").setVar("mode", 0));
		register(new ControlEvent("rbmk_ctrl_set_color").setVar("color", 0));
		register(new ControlEvent("rbmk_ctrl_set_level").setVar("level", 0));
		register(new ControlEvent("rbmk_crane_move").setVar("up", 0).setVar("down", 0).setVar("left", 0).setVar("right", 0));
		register(new ControlEvent("rbmk_crane_load"));
		register(new ControlEvent("ctrl_press").setVar("isSneaking", false));
		register(new ControlEvent("redstone_input")
				.setVar("facing", Direction.NORTH)
				.setVar("power", 0)
				.setVar("weak", 0)
				.setVar("strong", 0)
				.setVar("isPowered", false)
				.setVar("isWeaklyPowered", false)
				.setVar("isStronglyPowered", false));
		register(new ControlEvent("initialize"));

		// Leafia's MixinControlEvent.onInit, which injected at the TAIL of init() in the same order.
		register(new ControlEvent("set_booster_level").setVar("level", 0));
		register(new ControlEvent("set_booster_active").setVar("active", 0));
		register(new ControlEvent("set_absorber_level").setVar("level", 0));
		register(new ControlEvent("set_stabilizer_level").setVar("level", 0));
		register(new ControlEvent("torch_set_state").setVar("isOn", 0));
		register(new ControlEvent("pwr_ctrl_set_level").setVar("level", 0));
		register(new ControlEvent("lftr_ctrl_set_level").setVar("level", 0));
		register(new ControlEvent("pwr_computer_set_master").setVar("level", 0));
		register(new ControlEvent("pwr_computer_set_level").setVar("name", "").setVar("level", 0));
		register(new ControlEvent("set_pump_buffer").setVar("size", 0));
		register(new ControlEvent("set_pump_pressure").setVar("pu", 0));
		register(new ControlEvent("set_pump_priority").setVar("priority", 2));
		register(new ControlEvent("zirnox_ctrl_set_level").setVar("level", 0));
		register(new ControlEvent("setGlobalVar").setVar("name", "").setVar("value", ""));
		register(new ControlEvent("setIOMode").setVar("mode", 0));
		register(new ControlEvent("heater_set").setVar("heatPerTick", 0));
		register(new ControlEvent("burner_set").setVar("isOn", 0));
		register(new ControlEvent("set_pulser_active").setVar("active", 0).setVar("code", ""));
		register(new ControlEvent("set_pulser_detonation").setVar("charge", 0).setVar("code", ""));
	}
}
