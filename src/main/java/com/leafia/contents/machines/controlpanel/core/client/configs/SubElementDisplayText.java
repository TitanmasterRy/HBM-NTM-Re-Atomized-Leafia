package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The text display's pane: {@code scale} and {@code width}, each 10 to 100. Adapted from NTM-CE's {@code SubElementDisplayText} (CCP-EDITOR, D-3066). */
@OnlyIn(Dist.CLIENT)
public class SubElementDisplayText extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {149, 64, 88, 88};

	private final int scale;
	private final int width;
	private ControlSlider slideScale;
	private ControlSlider slideWidth;

	public SubElementDisplayText(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.scale = (int) num(map, "scale", 25);
		this.width = (int) num(map, "width", 20);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		slideScale = slider(cX - 85, gui.getGuiTop() + 70, 80, "Scale ", 10, 100, scale);
		slideWidth = slider(cX - 85, gui.getGuiTop() + 90, 80, "Width ", 10, 100, width);
		super.init();
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		putFloatConfig(configs, "scale", slideScale == null ? scale : slideScale.getValueInt());
		putFloatConfig(configs, "width", slideWidth == null ? width : slideWidth.getValueInt());
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
