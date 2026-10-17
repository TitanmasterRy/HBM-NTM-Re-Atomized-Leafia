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
 * The horizontal meter's configuration pane (CCP-INSTRUMENTS-2, D-3072): needle colour sliders with a swatch, the scale's two ends typed
 * into text boxes (red while not a number, and the last good number kept, as Leafia's did), and sliders for the length, decimals,
 * divisions and subdivisions. Adapted from Leafia's {@code MeterHorizontalCSE} on the same widgets and ranges as the stock panes
 * (D-3066): the sliders read live where Leafia's copied their values on mouse release.
 */
@OnlyIn(Dist.CLIENT)
public class MeterHorizontalCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {50, 134, 66, 66};
	private static final int SPACING = 95;
	private static final int START_X_OFFSET = 85;

	private final int length, r, g, b, divisions, subdivisions, decimals;
	private float min, max;

	private ControlSlider slideLength, slideR, slideG, slideB, slideDiv, slideSubd, slideDecimals;
	private EditBox fieldMax, fieldMin;

	public MeterHorizontalCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.length = (int) num(map, "length", 5);
		this.r = (int) num(map, "r", 255);
		this.g = (int) num(map, "g", 153);
		this.b = (int) num(map, "b", 0);
		this.min = num(map, "minValue", 0);
		this.max = num(map, "maxValue", 1);
		this.decimals = (int) num(map, "decimals", 0);
		this.divisions = (int) num(map, "divisions", 5);
		this.subdivisions = (int) num(map, "subdivisions", 3);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideR == null) {
			putFloatConfig(configs, "length", length);
			putFloatConfig(configs, "r", r);
			putFloatConfig(configs, "g", g);
			putFloatConfig(configs, "b", b);
			putFloatConfig(configs, "minValue", min);
			putFloatConfig(configs, "maxValue", max);
			putFloatConfig(configs, "decimals", decimals);
			putFloatConfig(configs, "divisions", divisions);
			putFloatConfig(configs, "subdivisions", subdivisions);
			return;
		}
		min = InstrumentPanes.parse(fieldMin, min);
		max = InstrumentPanes.parse(fieldMax, max);
		putFloatConfig(configs, "length", slideLength.getValueInt());
		putFloatConfig(configs, "r", slideR.getValueInt());
		putFloatConfig(configs, "g", slideG.getValueInt());
		putFloatConfig(configs, "b", slideB.getValueInt());
		putFloatConfig(configs, "minValue", min);
		putFloatConfig(configs, "maxValue", max);
		putFloatConfig(configs, "decimals", slideDecimals.getValueInt());
		putFloatConfig(configs, "divisions", slideDiv.getValueInt());
		putFloatConfig(configs, "subdivisions", slideSubd.getValueInt());
	}

	@Override
	public void init() {
		int cx = gui.width / 2;
		int top = gui.getGuiTop();
		int left = cx - START_X_OFFSET;
		int right = left + SPACING;
		slideR = slider(left, top + 70, 80, ChatFormatting.RED + "R ", 0, 255, r);
		slideG = slider(left, top + 70 + 20, 80, ChatFormatting.GREEN + "G ", 0, 255, g);
		slideB = slider(left, top + 70 + 40, 80, ChatFormatting.BLUE + "B ", 0, 255, b);
		fieldMax = editBox(right, top + 70, 80, 15, Float.toString(max));
		fieldMin = editBox(right, top + 70 + 20, 80, 15, Float.toString(min));
		slideLength = slider(right, top + 70 + 40, 80, "Length ", 3, 10, length);
		slideDecimals = slider(right, top + 70 + 60, 80, "Decimals ", 0, 3, decimals);
		slideDiv = slider(right, top + 70 + 80, 80, "Division ", 2, 20, divisions);
		slideSubd = slider(right, top + 70 + 100, 80, "Subdivision ", 0, 10, subdivisions);
		super.init();
	}

	@Override
	public void update() {
		super.update();
		if(fieldMax != null) {
			min = InstrumentPanes.parseColoured(fieldMin, min);
			max = InstrumentPanes.parseColoured(fieldMax, max);
		}
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
