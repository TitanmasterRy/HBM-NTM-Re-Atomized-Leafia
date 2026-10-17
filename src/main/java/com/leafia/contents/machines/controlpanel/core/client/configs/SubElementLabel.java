package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The label's pane: three colour sliders with a swatch, the {@code text}, and {@code scale} 10 to 100. Adapted from NTM-CE's
 * {@code SubElementLabel} (CCP-EDITOR, D-3066).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementLabel extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {137, 116, 8, 88};

	private final float colorR, colorG, colorB;
	private final String text;
	private final int scale;

	private ControlSlider slideR, slideG, slideB, slideScale;
	private EditBox textField;

	public SubElementLabel(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.colorR = num(map, "colorR", 1);
		this.colorG = num(map, "colorG", 1);
		this.colorB = num(map, "colorB", 1);
		this.text = text(map, "text", "");
		this.scale = (int) num(map, "scale", 25);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideR == null) {
			putFloatConfig(configs, "colorR", colorR);
			putFloatConfig(configs, "colorG", colorG);
			putFloatConfig(configs, "colorB", colorB);
			putStringConfig(configs, "text", text);
			putFloatConfig(configs, "scale", scale);
			return;
		}
		putFloatConfig(configs, "colorR", (float) slideR.fraction());
		putFloatConfig(configs, "colorG", (float) slideG.fraction());
		putFloatConfig(configs, "colorB", (float) slideB.fraction());
		putStringConfig(configs, "text", textField.getValue());
		putFloatConfig(configs, "scale", slideScale.getValueInt());
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int top = gui.getGuiTop();
		slideR = slider(cX - 85, top + 70, 80, ChatFormatting.RED + "R ", 0, 100, colorR * 100);
		slideG = slider(cX - 85, top + 90, 80, ChatFormatting.GREEN + "G ", 0, 100, colorG * 100);
		slideB = slider(cX - 85, top + 110, 80, ChatFormatting.BLUE + "B ", 0, 100, colorB * 100);
		slideScale = slider(cX - 85, top + 140, 80, "Scale ", 10, 100, scale);
		textField = editBox(cX + 10, top + 70, 100, 30, text);
		super.init();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if(slideR != null)
			swatch(g, 20, 70, (float) slideR.fraction(), (float) slideG.fraction(), (float) slideB.fraction());
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
