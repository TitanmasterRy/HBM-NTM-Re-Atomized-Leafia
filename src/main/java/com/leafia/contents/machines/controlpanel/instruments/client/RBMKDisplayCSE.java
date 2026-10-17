package com.leafia.contents.machines.controlpanel.instruments.client;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.client.configs.SubElementBaseConfig;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The RBMK display's pane (CCP-INSTRUMENTS-2, D-3072): width and height sliders (1 to 27 columns) and a tick box each for shifting the
 * background's checkerboard by a column. Adapted from Leafia's {@code RBMKDisplayCSE}, <b>with one fix</b>: his height slider started at
 * the <i>width</i>, so saving a map that was not square made it square.
 */
@OnlyIn(Dist.CLIENT)
public class RBMKDisplayCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {132, 112, 88, 88};

	private final int width, height;
	private final boolean offsetX, offsetY;
	private ControlSlider slideWidth, slideHeight;
	private Checkbox checkX, checkY;

	public RBMKDisplayCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.width = (int) num(map, "width", 1);
		this.height = (int) num(map, "height", 1);
		this.offsetX = flag(map, "offsetU", false);
		this.offsetY = flag(map, "offsetV", false);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		boolean live = slideWidth != null;
		putFloatConfig(configs, "width", live ? slideWidth.getValueInt() : width);
		putFloatConfig(configs, "height", live ? slideHeight.getValueInt() : height);
		putFloatConfig(configs, "offsetU", (live ? checkX.selected() : offsetX) ? 1 : 0);
		putFloatConfig(configs, "offsetV", (live ? checkY.selected() : offsetY) ? 1 : 0);
	}

	@Override
	public void init() {
		int cx = gui.width / 2;
		int top = gui.getGuiTop();
		slideWidth = slider(cx - 85, top + 70, 80, "Width ", 1, 27, width);
		slideHeight = slider(cx - 85, top + 90, 80, "Height ", 1, 27, height);
		checkX = checkbox(cx + 10, top + 72, "Offset Tile X", offsetX);
		checkY = checkbox(cx + 10, top + 92, "Offset Tile Y", offsetY);
		super.init();
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
