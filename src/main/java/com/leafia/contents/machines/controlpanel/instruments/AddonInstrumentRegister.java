package com.leafia.contents.machines.controlpanel.instruments;

import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.drawing.Drawing;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.horizontal.MeterHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical.MeterVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.nixie.NixieDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.rbmk.RBMKDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeButton;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeSwitch;
import com.leafia.contents.machines.controlpanel.instruments.types.textindicator.TextIndicator;

/**
 * Puts Leafia's instruments on the control registry's addon list (CCP-INSTRUMENTS-1, D-3071; the last five in CCP-INSTRUMENTS-2, D-3072). Adapted from Leafia's
 * {@code AddonInstrumentRegister}: the prototypes are added to {@link ControlRegistry#addonControls}, which
 * {@link ControlRegistry#init} folds in after the stock fourteen, so the placement list shows them after the stock controls in
 * Leafia's order.
 *
 * <p>Leafia's list is eleven long and all eleven are here, in his order: nixie tube, large switch, large button, text indicator, RBMK
 * display, graph, horizontal and vertical meter, boxed label, horizontal and vertical lever. Idempotent: a name already on the list is
 * left where it is.
 */
public final class AddonInstrumentRegister {

	private AddonInstrumentRegister() {}

	private static final List<Control> reg = ControlRegistry.addonControls;

	private static void add(Control prototype) {
		for(Control have : reg) {
			if(have.registryName.equals(prototype.registryName))
				return;
		}
		reg.add(prototype);
	}

	public static synchronized void register() {
		AddonInstrumentModels.register();
		add(new NixieDisplay("Nixie Tube", "leafia_nixie", null));
		add(new LargeSwitch("Large Switch", "leafia_sbswitch", null));
		add(new LargeButton("Large Button", "leafia_sbbutton", null));
		add(new TextIndicator("Text Indicator", "leafia_textindicator", null));
		add(new RBMKDisplay("RBMK Display", "leafia_rbmk", null));
		add(new Graph("Graph", "leafia_graph", null));
		add(new MeterHorizontal("Horizontal Meter", "leafia_meter_h", null));
		add(new MeterVertical("Vertical Meter", "leafia_meter_v", null));
		add(new Drawing("Boxed Label", "leafia_drawing_box", null));
		add(new LeverHorizontal("Horizontal Lever", "leafia_lever_h", null));
		add(new LeverVertical("Vertical Lever", "leafia_lever_v", null));
	}
}
