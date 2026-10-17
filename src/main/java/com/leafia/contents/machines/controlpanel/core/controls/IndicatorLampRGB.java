package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.phys.AABB;

/**
 * The RGB indicator lamp: three variables, {@code red}, {@code green} and {@code blue}, each 0 to 255. The common half of
 * NTM-CE's {@code IndicatorLampRGB} (CCP-CONTROLS, D-3064). No default graphs, no collision box.
 */
public class IndicatorLampRGB extends Control {

	public IndicatorLampRGB(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("red", new DataValueFloat(0));
		vars.put("green", new DataValueFloat(0));
		vars.put("blue", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.INDICATOR;
	}

	@Override
	public float[] getSize() {
		return new float[]{.5F, .5F, .18F};
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}

	@Override
	public Control newControl(ControlPanel panel) {
		return new IndicatorLampRGB(name, registryName, panel);
	}
}
