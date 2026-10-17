package com.leafia.contents.machines.controlpanel.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which named objects each control OBJ holds, by control model. The <b>common</b> half of the control panel's models
 * (D-3065): strings only, so a GameTest on a dedicated server can read it and compare it with the generated assets, which
 * the client renderers cannot.
 *
 * <p>NTM-CE draws a control with {@code model.renderPart("name")}; here every object of every OBJ is its own baked model,
 * written by a generator script as {@code models/block/ccp_<model>_<part>.json} and registered
 * for baking by {@code ControlPanelModels}. This table is what that registration walks, and what the renderers' part names
 * must be drawn from: a part that is drawn but not in a model JSON bakes to the missing-model cube, and nothing logs it.
 *
 * <p>Batches 11 and 12 add the instruments' models through {@link #register}; the generator for those writes the JSONs.
 */
public final class ControlPanelParts {

	private ControlPanelParts() {}

	private static final Map<String, List<String>> PARTS = new LinkedHashMap<>();

	static {
		register("button_push", "base", "btn_base", "btn_top", "btn_top_top");
		register("button_emergency_push", "base", "top");
		register("button_encased_push", "cover", "cover2", "base", "btn_top", "btn_top_top");
		register("switch_toggle", "base", "lever", "lamp_off", "lamp_on");
		register("switch_rotary_toggle", "base", "lever");
		register("display_seven_seg", "base", "seg_0", "seg_1", "seg_2", "seg_3", "seg_4", "seg_5", "seg_6", "border");
		register("indicator_lamp", "base", "lamp");
		register("slider_vertical", "base", "slider", "light0", "light1", "light2", "light3", "light4", "light5");
		register("knob_control", "base", "knob");
		register("dial_square", "dial", "base");
		register("dial_large", "dial", "base");
	}

	/** Adds (or replaces) one control model's parts. For the addon's own instruments. */
	public static synchronized void register(String model, String... parts) {
		PARTS.put(model, List.of(parts));
	}

	/** Every control model and its parts, in registration order. */
	public static synchronized Map<String, List<String>> all() {
		return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(PARTS));
	}

	public static synchronized List<String> partsOf(String model) {
		return PARTS.getOrDefault(model, List.of());
	}

	/** The model JSON's name (without namespace or folder) for one part: {@code ccp_button_push_btn_top}, in lower case. */
	public static String modelName(String model, String part) {
		// A resource path cannot hold a capital, and Blender names objects "Base.001" (D-3071). The model JSON's "visibility" keys
		// stay in the OBJ's own case; only the file name is lowered.
		return "ccp_" + model + "_" + part.toLowerCase(java.util.Locale.ROOT);
	}

	/** The upright front's slab, drawn whole. */
	public static final String PANEL_FRONT = "ccp_panel_front";
}
