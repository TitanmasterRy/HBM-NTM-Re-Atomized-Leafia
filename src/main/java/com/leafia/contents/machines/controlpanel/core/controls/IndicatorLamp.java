package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;

/**
 * The indicator lamp, half a block square: lit or not ({@code isLit}), in a dye colour ({@code color}, red by default). The
 * common half of NTM-CE's {@code IndicatorLamp} (CCP-CONTROLS, D-3064). No default graphs, no collision box.
 */
public class IndicatorLamp extends Control {

	public IndicatorLamp(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isLit", new DataValueFloat(0));
		vars.put("color", new DataValueEnum<>(DyeColor.RED));
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
		return new IndicatorLamp(name, registryName, panel);
	}
}
