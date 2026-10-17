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
 * The boxed label's configuration pane (CCP-INSTRUMENTS-1, D-3071): colour sliders (0 to 100) with a swatch, width and height in
 * tenths of a unit, the text's scale, a white-text tick box and the text. Adapted from Leafia's {@code DrawingCSE} on the same
 * widgets and ranges as the stock panes (D-3066).
 */
@OnlyIn(Dist.CLIENT)
public class DrawingCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {132, 112, 88, 88};
	private static final int SPACING = 95;
	private static final int START_X_OFFSET = 85;

	private final double width, height;
	private final int red, green, blue, scale;
	private final boolean whiteText;
	private final String text;

	private ControlSlider slideR, slideG, slideB, slideWidth, slideHeight, slideScale;
	private Checkbox checkWhite;
	private EditBox textField;

	public DrawingCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.red = (int) num(map, "red", 100);
		this.green = (int) num(map, "green", 100);
		this.blue = (int) num(map, "blue", 100);
		this.width = Math.round(num(map, "width", 0.2F) * 10) / 10d;
		this.height = Math.round(num(map, "height", 0.2F) * 10) / 10d;
		this.scale = (int) num(map, "scale", 25);
		this.whiteText = flag(map, "white", false);
		this.text = text(map, "text", "");
	}

	private static float tenths(ControlSlider s) {
		return Math.round((float) s.getValue() * 10) / 10f;
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideR == null) {
			putFloatConfig(configs, "red", red);
			putFloatConfig(configs, "green", green);
			putFloatConfig(configs, "blue", blue);
			putFloatConfig(configs, "width", (float) width);
			putFloatConfig(configs, "height", (float) height);
			putFloatConfig(configs, "white", whiteText ? 1 : 0);
			putFloatConfig(configs, "scale", scale);
			putStringConfig(configs, "text", text);
			return;
		}
		putFloatConfig(configs, "red", slideR.getValueInt());
		putFloatConfig(configs, "green", slideG.getValueInt());
		putFloatConfig(configs, "blue", slideB.getValueInt());
		putFloatConfig(configs, "width", tenths(slideWidth));
		putFloatConfig(configs, "height", tenths(slideHeight));
		putFloatConfig(configs, "white", checkWhite.selected() ? 1 : 0);
		putFloatConfig(configs, "scale", slideScale.getValueInt());
		putStringConfig(configs, "text", textField.getValue());
	}

	@Override
	public void init() {
		int cx = gui.width / 2;
		int top = gui.getGuiTop();
		int left = cx - START_X_OFFSET;
		slideR = slider(left, top + 70, 80, ChatFormatting.RED + "R ", 0, 100, red);
		slideG = slider(left, top + 70 + 20, 80, ChatFormatting.GREEN + "G ", 0, 100, green);
		slideB = slider(left, top + 70 + 40, 80, ChatFormatting.BLUE + "B ", 0, 100, blue);
		slideWidth = slider(left + SPACING, top + 70, 80, "Width ", 0.1, 10, width, true);
		slideHeight = slider(left + SPACING, top + 70 + 20, 80, "Height ", 0.1, 10, height, true);
		slideScale = slider(left, top + 140, 80, "Text Scale ", 10, 100, scale);
		checkWhite = checkbox(left, top + 140 + 20 + 35 + 2, "White Text", whiteText);
		textField = editBox(left - 20, top + 140 + 20, 100, 30, text);
		super.init();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if(slideR != null)
			swatch(g, 20, 70, slideR.getValueInt() / 100F, slideG.getValueInt() / 100F, slideB.getValueInt() / 100F);
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
