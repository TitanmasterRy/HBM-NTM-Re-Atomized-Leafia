package com.leafia.contents.machines.controlpanel.instruments.types.lever;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;

/**
 * The lever standing along the panel's z axis, one unit by two (CCP-INSTRUMENTS-1, D-3071). Leafia's {@code LeverVertical}: the
 * behaviour is {@link LeverBase}'s and the model is drawn as it is.
 */
public class LeverVertical extends LeverBase {

	public LeverVertical(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
	}

	@Override
	public float[] getSize() {
		return new float[]{1, 2, 1.25f};
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new LeverVertical(name, registryName, controlPanel);
	}
}
