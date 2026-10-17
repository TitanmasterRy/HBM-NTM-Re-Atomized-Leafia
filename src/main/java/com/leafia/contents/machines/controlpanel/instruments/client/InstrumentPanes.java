package com.leafia.contents.machines.controlpanel.instruments.client;

import com.leafia.contents.machines.controlpanel.core.client.configs.ConfigPanes;

import net.minecraft.client.gui.components.EditBox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Which of Leafia's instruments get a configuration pane of their own (CCP-INSTRUMENTS-1, D-3071), by registry name; {@link ConfigPanes}
 * calls {@link #register} when it loads. Leafia overrode {@code getConfigSubElement} on the graph and the boxed label; the other
 * four of batch 11 (the two large controls and the two levers) have nothing to configure and get the empty pane, as in CE. Batch 12's
 * meters, nixie tube, text indicator and RBMK display (CCP-INSTRUMENTS-2, D-3072) are the other five panes here.
 */
@OnlyIn(Dist.CLIENT)
public final class InstrumentPanes {

	private InstrumentPanes() {}

	public static void register() {
		ConfigPanes.register("leafia_graph", GraphCSE::new);
		ConfigPanes.register("leafia_drawing_box", DrawingCSE::new);
		// CCP-INSTRUMENTS-2 (D-3072)
		ConfigPanes.register("leafia_meter_h", MeterHorizontalCSE::new);
		ConfigPanes.register("leafia_meter_v", MeterVerticalCSE::new);
		ConfigPanes.register("leafia_nixie", NixieCSE::new);
		ConfigPanes.register("leafia_rbmk", RBMKDisplayCSE::new);
		ConfigPanes.register("leafia_textindicator", TextIndicatorCSE::new);
	}

	/** The number in a text box, or {@code fallback} when it is not a finite one. */
	static float parse(EditBox box, float fallback) {
		try {
			float v = Float.parseFloat(box.getValue().trim());
			return Float.isFinite(v) ? v : fallback;
		} catch(NumberFormatException e) {
			return fallback;
		}
	}

	/** {@link #parse}, and the box's text goes red while it is not a number (Leafia's {@code 0xFF0000} against white). */
	static float parseColoured(EditBox box, float fallback) {
		try {
			float v = Float.parseFloat(box.getValue().trim());
			if(!Float.isFinite(v))
				throw new NumberFormatException();
			box.setTextColor(0xFFFFFF);
			return v;
		} catch(NumberFormatException e) {
			box.setTextColor(0xFF0000);
			return fallback;
		}
	}
}
