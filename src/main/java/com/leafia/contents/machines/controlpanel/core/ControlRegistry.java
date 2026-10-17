package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.controls.ButtonEmergencyPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonEncasedPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonPush;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.controls.DialLarge;
import com.leafia.contents.machines.controlpanel.core.controls.DialSquare;
import com.leafia.contents.machines.controlpanel.core.controls.DisplaySevenSeg;
import com.leafia.contents.machines.controlpanel.core.controls.DisplayText;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLamp;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLampRGB;
import com.leafia.contents.machines.controlpanel.core.controls.KnobControl;
import com.leafia.contents.machines.controlpanel.core.controls.Label;
import com.leafia.contents.machines.controlpanel.core.controls.SliderVertical;
import com.leafia.contents.machines.controlpanel.core.controls.SwitchRotaryToggle;
import com.leafia.contents.machines.controlpanel.core.controls.SwitchToggle;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

/**
 * The prototype of every control a panel can hold, by registry name. Adapted from NTM-CE's {@code ControlRegistry} (D-3063,
 * completed by CCP-CONTROLS, D-3064): {@link #init} registers the fourteen stock controls, in CE's order, then folds in the
 * addon list.
 *
 * <p>A prototype is a control with no panel; {@link Control#newControl} makes a live one. Registration order is the order the
 * placement list shows, so the map keeps it. The registry is process-wide and never cleared: {@link #init} is idempotent, and a
 * test that registers its own prototype removes it with {@link #unregister}.
 */
public class ControlRegistry {

	public static final Map<String, Control> registry = new Object2ObjectLinkedOpenHashMap<>();

	/** Controls an addon registers before {@link #init} runs (Leafia's instruments, batch 11), as CE's {@code addonControls}. */
	public static final List<Control> addonControls = new ArrayList<>();

	private ControlRegistry() {}

	/**
	 * Registers the fourteen stock controls in CE's order, then every control in {@link #addonControls}. Safe to call again: a name
	 * that is already registered keeps its place in the order.
	 */
	public static synchronized void init() {
		registry.put("button_push", new ButtonPush("Push Button", "button_push", null));
		registry.put("button_emergency_push", new ButtonEmergencyPush("Emergency Push Button", "button_emergency_push", null));
		registry.put("button_encased_push", new ButtonEncasedPush("Encased Push Button", "button_encased_push", null));

		registry.put("switch_toggle", new SwitchToggle("Toggle Switch", "switch_toggle", null));
		registry.put("switch_rotary_toggle", new SwitchRotaryToggle("Rotary Toggle Switch", "switch_rotary_toggle", null));

		registry.put("slider_vertical", new SliderVertical("Vertical Slider", "slider_vertical", null));

		registry.put("knob_control", new KnobControl("Control Knob", "knob_control", null));

		registry.put("display_7seg", new DisplaySevenSeg("7-seg Display", "display_7seg", null));
		registry.put("display_text", new DisplayText("Text Display", "display_text", null));

		registry.put("dial_square", new DialSquare("Square Dial", "dial_square", null));
		registry.put("dial_large", new DialLarge("Large Dial", "dial_large", null));

		registry.put("indicator_lamp", new IndicatorLamp("Indicator Lamp", "indicator_lamp", null));

		registry.put("indicator_lamp_rgb", new IndicatorLampRGB("Indicator Lamp (RGB)", "indicator_lamp_rgb", null));

		registry.put("label", new Label("Label", "label", null));

		for(Control control : addonControls)
			registry.put(control.registryName, control);
	}

	public static synchronized void register(Control prototype) {
		registry.put(prototype.registryName, prototype);
	}

	public static synchronized void unregister(String name) {
		registry.remove(name);
	}

	public static synchronized List<Control> getAllControls() {
		return new ArrayList<>(registry.values());
	}

	public static synchronized List<String> getAllControlsOfType(ControlType type) {
		List<String> l = new ArrayList<>();
		for(Map.Entry<String, Control> c : registry.entrySet()) {
			if(c.getValue().getControlType() == type)
				l.add(c.getKey());
		}
		return l;
	}

	/** A fresh control of this registry name on this panel, or null when nobody registered the name. */
	public static synchronized Control getNew(String name, ControlPanel panel) {
		Control control = registry.get(name);
		return control != null ? control.newControl(panel) : null;
	}

	public static synchronized boolean isRegistered(String name) {
		return registry.containsKey(name);
	}
}
