package com.leafia.contents.machines.controlpanel.instruments.types;

import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;

/**
 * Which objects each of Leafia's instrument OBJs holds (CCP-INSTRUMENTS-1, D-3071). Leafia's {@code AddonInstrumentModels} was six
 * {@code WaveFrontObjectVAO} fields; a 1.21.1 renderer draws baked models by name instead (see {@link ControlPanelParts}), so this
 * class is the table of what those baked models are, as strings a dedicated server can read, and the names the renderers draw.
 *
 * <p>{@link #register} adds them to {@link ControlPanelParts}, which {@code ControlPanelModels} walks to register the baked parts and
 * which the model tests compare with the generator's output. The object names are
 * the OBJ's own, in Blender's case ({@code Base.001}); the model JSONs are written in lower case because a resource path cannot hold a
 * capital, and {@link ControlPanelParts#modelName} does the same.
 *
 * <p>CCP-INSTRUMENTS-2 (D-3072) adds the nixie tube, the vertical meter and the text indicator. The nixie and the meter wear the plain
 * white texture and are tinted; the text indicator's lens ({@code Plane.001}) wears {@link #TEXT_INDICATOR_LIGHT}.
 */
public final class AddonInstrumentModels {

	private AddonInstrumentModels() {}

	public static final String LARGE_SWITCH = "large_switch";
	public static final String LARGE_BUTTON = "large_button";
	public static final String LEVER = "lever";
	public static final String NIXIE = "nixie";
	public static final String METER_V = "meter_v";
	public static final String TEXT_INDICATOR = "text_indicator";

	/** The large switch's and button's shared 3 x 3 colour palette. The lever's parts wear the plain white texture and are tinted. */
	public static final String PALETTE_TEXTURE = "largeswitch_palette";

	/** The text indicator's lens texture. */
	public static final String TEXT_INDICATOR_LIGHT = "textindicator_light";

	/** The RBMK display's 2 x 2 checkerboard, one pixel a column. */
	public static final String RBMK_TEXTURE = "rbmk";

	/** Registers the parts once; calling it again replaces them with the same lists. */
	public static void register() {
		ControlPanelParts.register(LARGE_SWITCH, "Plane", "Plane.001", "Plane.002", "Cube", "Switch");
		ControlPanelParts.register(LARGE_BUTTON, "Plane", "Plane.001", "Plane.002", "Circle", "Circle.001");
		ControlPanelParts.register(LEVER, "Base", "Lever", "Base.001");
		ControlPanelParts.register(NIXIE, "Border", "BorderLeft", "InsideLeft", "Inside", "Tube", "Screws", "ClipPlane");
		ControlPanelParts.register(METER_V, "Base", "Meter", "Needle");
		ControlPanelParts.register(TEXT_INDICATOR, "Plane", "Plane.001");
	}
}
