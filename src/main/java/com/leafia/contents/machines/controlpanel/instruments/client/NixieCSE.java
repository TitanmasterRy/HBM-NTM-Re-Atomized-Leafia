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

/** The nixie display's pane (CCP-INSTRUMENTS-2, D-3072): one slider, the number of tubes, 0 to 8. Adapted from Leafia's {@code NixieCSE}. */
@OnlyIn(Dist.CLIENT)
public class NixieCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {132, 112, 88, 88};

	private final int length;
	private ControlSlider slideLength;

	public NixieCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.length = (int) num(map, "length", 1);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		putFloatConfig(configs, "length", slideLength == null ? length : slideLength.getValueInt());
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		slideLength = slider(cX + 10, gui.getGuiTop() + 70, 75, "Digits ", 0, 8, length);
		super.init();
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
