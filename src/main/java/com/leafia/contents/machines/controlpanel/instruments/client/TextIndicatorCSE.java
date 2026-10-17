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
 * The text indicator's pane (CCP-INSTRUMENTS-2, D-3072): lens colour sliders (0 to 255) with a swatch, and the line of text. Adapted
 * from Leafia's {@code TextIndicatorCSE}.
 */
@OnlyIn(Dist.CLIENT)
public class TextIndicatorCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {137, 116, 8, 88};

	private final float colorR, colorG, colorB;
	private final String text;
	private ControlSlider slideR, slideG, slideB;
	private EditBox textField;

	public TextIndicatorCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.colorR = num(map, "red", 255);
		this.colorG = num(map, "green", 0);
		this.colorB = num(map, "blue", 0);
		this.text = text(map, "text", "A3-5");
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideR == null) {
			putFloatConfig(configs, "red", colorR);
			putFloatConfig(configs, "green", colorG);
			putFloatConfig(configs, "blue", colorB);
			putStringConfig(configs, "text", text);
			return;
		}
		putFloatConfig(configs, "red", slideR.getValueInt());
		putFloatConfig(configs, "green", slideG.getValueInt());
		putFloatConfig(configs, "blue", slideB.getValueInt());
		putStringConfig(configs, "text", textField.getValue());
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int top = gui.getGuiTop();
		slideR = slider(cX - 85, top + 70, 80, ChatFormatting.RED + "R ", 0, 255, colorR);
		slideG = slider(cX - 85, top + 90, 80, ChatFormatting.GREEN + "G ", 0, 255, colorG);
		slideB = slider(cX - 85, top + 110, 80, ChatFormatting.BLUE + "B ", 0, 255, colorB);
		textField = editBox(cX + 10, top + 70, 100, 30, text);
		super.init();
	}

	@Override
	public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
		if(slideR != null)
			swatch(gg, 20, 70, slideR.getValueInt() / 255F, slideG.getValueInt() / 255F, slideB.getValueInt() / 255F);
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
