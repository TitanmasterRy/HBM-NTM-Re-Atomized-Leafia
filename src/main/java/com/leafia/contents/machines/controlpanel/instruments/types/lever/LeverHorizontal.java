package com.leafia.contents.machines.controlpanel.instruments.types.lever;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;

/**
 * The lever lying along the panel's x axis, two units by one (CCP-INSTRUMENTS-1, D-3071). Leafia's {@code LeverHorizontal}: the
 * behaviour is {@link LeverBase}'s and the model is drawn turned a quarter about the panel's normal.
 */
public class LeverHorizontal extends LeverBase {

	public LeverHorizontal(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
	}

	@Override
	public float[] getSize() {
		return new float[]{2, 1, 1.25f};
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new LeverHorizontal(name, registryName, controlPanel);
	}
}
