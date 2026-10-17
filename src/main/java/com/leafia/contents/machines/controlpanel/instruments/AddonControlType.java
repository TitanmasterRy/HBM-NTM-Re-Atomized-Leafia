package com.leafia.contents.machines.controlpanel.instruments;

import com.leafia.contents.machines.controlpanel.core.controls.ControlType;

/**
 * The control families Leafia's instruments add to the placement list (CCP-INSTRUMENTS-1, D-3071). Leafia's
 * {@code MixinControlType} existed to force this class to load before the placement list was built; {@code ControlType.registerOrGet}
 * is already the class-standing-in-for-an-enum of NTM-CE, so the three families are plain static fields here and are created the
 * first time any instrument names them. The Graph, Lever and RBMK families are new; the other instruments sit in the stock
 * Switch, Button, Display, Meter and Label families.
 */
public final class AddonControlType {

	private AddonControlType() {}

	public static final ControlType RBMK_DISPLAY = ControlType.registerOrGet("RBMK Display");
	public static final ControlType GRAPH = ControlType.registerOrGet("Graph");
	public static final ControlType LEVER = ControlType.registerOrGet("Lever");
}
