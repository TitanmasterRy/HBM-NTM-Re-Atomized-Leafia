package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The knob's pane: one slider, {@code positions}, 1 to 11. Adapted from NTM-CE's {@code SubElementKnobControl} (CCP-EDITOR,
 * D-3066). The setting changes nothing about the knob's behaviour in CE or here (D-3064 records that), but it is saved.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementKnobControl extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {149, 64, 88, 88};

	private final int positions;
	private ControlSlider slidePositions;

	public SubElementKnobControl(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.positions = (int) num(map, "positions", 11);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		slidePositions = slider(cX - 85, gui.getGuiTop() + 70, 80, "Positions ", 1, 11, positions);
		super.init();
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		putFloatConfig(configs, "positions", slidePositions == null ? positions : slidePositions.getValueInt());
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
