package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.phys.AABB;

/**
 * The large dial, 4.5 by 2.25: a needle that sweeps 180 degrees over {@code value} 0 to 100, under a free-text {@code label}.
 * The common half of NTM-CE's {@code DialLarge} (CCP-CONTROLS, D-3064). No default graphs, no collision box. CE's editor pane for
 * it is {@code SubElementDialSquare}, the same one the square dial uses (batch 6).
 */
public class DialLarge extends Control {

	private String label = "    POWER            (RS)";

	public DialLarge(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
		configMap.put("label", new DataValueString(label));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.DIAL;
	}

	@Override
	public float[] getSize() {
		return new float[]{4.5F, 2.25F, .4F};
	}

	public String getLabel() {
		return label;
	}

	@Override
	protected void onConfigMapChanged() {
		for(Map.Entry<String, DataValue> e : configMap.entrySet()) {
			switch(e.getKey()) {
			case "label": {
				label = e.getValue().toString();
				break;
			}
			}
		}
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new DialLarge(name, registryName, panel);
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}
}
