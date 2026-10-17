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
 * The vertical meter's configuration pane (CCP-INSTRUMENTS-2, D-3072): the scale's two ends typed into text boxes (red while not a
 * number, the last good number kept), and sliders for the needle's offset (-3 to 3), decimals, divisions (5 to 30) and subdivisions, and
 * a tick box for clamping. Adapted from Leafia's {@code MeterVerticalCSE}.
 */
@OnlyIn(Dist.CLIENT)
public class MeterVerticalCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {40, 112, 88, 88};
	private static final int SPACING = 95;
	private static final int START_X_OFFSET = 85;

	private final int needleOffset, divisions, subdivisions, decimals;
	private final boolean clamp;
	private float min, max;

	private ControlSlider slideNeedle, slideDiv, slideSubd, slideDecimals;
	private Checkbox checkClamp;
	private EditBox fieldMax, fieldMin;

	public MeterVerticalCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.needleOffset = (int) num(map, "needleOffset", 0);
		this.max = num(map, "maxValue", 1);
		this.min = num(map, "minValue", -1);
		this.divisions = (int) num(map, "divisions", 20);
		this.subdivisions = (int) num(map, "subdivisions", 3);
		this.decimals = (int) num(map, "decimals", 1);
		this.clamp = flag(map, "clamp", false);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideNeedle == null) {
			putFloatConfig(configs, "needleOffset", needleOffset);
			putFloatConfig(configs, "minValue", min);
			putFloatConfig(configs, "maxValue", max);
			putFloatConfig(configs, "decimals", decimals);
			putFloatConfig(configs, "divisions", divisions);
			putFloatConfig(configs, "subdivisions", subdivisions);
			putFloatConfig(configs, "clamp", clamp ? 1 : 0);
			return;
		}
		min = InstrumentPanes.parse(fieldMin, min);
		max = InstrumentPanes.parse(fieldMax, max);
		putFloatConfig(configs, "needleOffset", slideNeedle.getValueInt());
		putFloatConfig(configs, "minValue", min);
		putFloatConfig(configs, "maxValue", max);
		putFloatConfig(configs, "decimals", slideDecimals.getValueInt());
		putFloatConfig(configs, "divisions", slideDiv.getValueInt());
		putFloatConfig(configs, "subdivisions", slideSubd.getValueInt());
		putFloatConfig(configs, "clamp", checkClamp.selected() ? 1 : 0);
	}

	@Override
	public void init() {
		int cx = gui.width / 2;
		int top = gui.getGuiTop();
		int left = cx - START_X_OFFSET;
		int right = left + SPACING;
		fieldMax = editBox(left, top + 70, 80, 15, Float.toString(max));
		fieldMin = editBox(left, top + 70 + 20, 80, 15, Float.toString(min));
		slideNeedle = slider(right, top + 70, 80, "Offset ", -3, 3, needleOffset);
		slideDecimals = slider(right, top + 70 + 20, 80, "Decimals ", 0, 3, decimals);
		slideDiv = slider(right, top + 70 + 40, 80, "Division ", 5, 30, divisions);
		slideSubd = slider(right, top + 70 + 60, 80, "Subdivision ", 0, 10, subdivisions);
		checkClamp = checkbox(right, top + 70 + 80 + 2, "Clamp", clamp);
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
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
