package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.HashMap;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Which configuration pane a control gets (CCP-EDITOR, D-3066). In CE this was {@code Control.getConfigSubElement}, an override
 * on six of the fourteen controls; a control class has no client types in this port, so
 * the choice is a table by registry name, here. The Leafia instruments of batches 11 and 12 add theirs with {@link #register}.
 * A control with no entry gets the empty {@link SubElementBaseConfig}, which is CE's default.
 */
@OnlyIn(Dist.CLIENT)
public final class ConfigPanes {

	private ConfigPanes() {}

	@FunctionalInterface
	public interface Factory {
		SubElementBaseConfig create(ControlEditScreen gui, Map<String, DataValue> configs);
	}

	private static final Map<String, Factory> FACTORIES = new HashMap<>();

	static {
		register("dial_square", SubElementDialSquare::new);
		register("dial_large", SubElementDialSquare::new);
		register("display_7seg", SubElementDisplaySevenSeg::new);
		register("display_text", SubElementDisplayText::new);
		register("knob_control", SubElementKnobControl::new);
		register("label", SubElementLabel::new);
		// Leafia's instruments (CCP-INSTRUMENTS-1, D-3071).
		com.leafia.contents.machines.controlpanel.instruments.client.InstrumentPanes.register();
	}

	public static synchronized void register(String registryName, Factory factory) {
		FACTORIES.put(registryName, factory);
	}

	public static synchronized void unregister(String registryName) {
		FACTORIES.remove(registryName);
	}

	/** The pane for this control, built from the configuration to start from. */
	public static synchronized SubElementBaseConfig create(Control control, ControlEditScreen gui, Map<String, DataValue> configs) {
		Factory f = FACTORIES.get(control.registryName);
		return f == null ? new SubElementBaseConfig(gui, configs) : f.create(gui, configs);
	}

	public static synchronized boolean hasPane(String registryName) {
		return FACTORIES.containsKey(registryName);
	}
}
