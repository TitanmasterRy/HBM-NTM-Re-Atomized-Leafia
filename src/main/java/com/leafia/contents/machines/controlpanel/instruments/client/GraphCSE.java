package com.leafia.contents.machines.controlpanel.instruments.client;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.configs.SubElementBaseConfig;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph.GraphSkin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The graph's configuration pane (CCP-INSTRUMENTS-1, D-3071): colour sliders with a swatch, width and height, the segment count,
 * the vertical line span and horizontal division, the scale's decimals and its offset, and a button that cycles the skin.
 * Adapted from Leafia's {@code GraphCSE} on the same widgets and ranges as the stock panes (D-3066): the sliders read live where
 * Leafia's copied their values on mouse release, and the dimensions round to tenths when written.
 */
@OnlyIn(Dist.CLIENT)
public class GraphCSE extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {60, 167, 44, 44};
	private static final int SPACING = 95;
	private static final int START_X_OFFSET = 85;

	private final float red, green, blue;
	private final double width, height, textOffset;
	private final int segments, verticalLineSpan, horizontalLineDivision, decimalLength;
	private String skin;

	private ControlSlider slideR, slideG, slideB, slideWidth, slideHeight;
	private ControlSlider slideSegments, slideVSpan, slideHDiv, slideDecimals, slideTextOffset;
	private Button skinButton;

	public GraphCSE(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.red = num(map, "red", 0);
		this.green = num(map, "green", 255);
		this.blue = num(map, "blue", 0);
		this.skin = text(map, "skin", "Console");
		this.segments = (int) num(map, "segments", 40);
		this.verticalLineSpan = (int) num(map, "verticalLineSpan", 1);
		this.horizontalLineDivision = (int) num(map, "horizontalLineDivision", 10);
		this.decimalLength = (int) num(map, "decimalLength", 2);
		this.textOffset = Math.round(num(map, "textOffset", 0.5F) * 10) / 10d;
		this.width = Math.round(num(map, "width", 6) * 10) / 10d;
		this.height = Math.round(num(map, "height", 4) * 10) / 10d;
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
			putStringConfig(configs, "skin", skin);
			putFloatConfig(configs, "segments", segments);
			putFloatConfig(configs, "verticalLineSpan", verticalLineSpan);
			putFloatConfig(configs, "horizontalLineDivision", horizontalLineDivision);
			putFloatConfig(configs, "decimalLength", decimalLength);
			putFloatConfig(configs, "textOffset", (float) textOffset);
			putFloatConfig(configs, "width", (float) width);
			putFloatConfig(configs, "height", (float) height);
			return;
		}
		putFloatConfig(configs, "red", slideR.getValueInt());
		putFloatConfig(configs, "green", slideG.getValueInt());
		putFloatConfig(configs, "blue", slideB.getValueInt());
		putStringConfig(configs, "skin", skin);
		putFloatConfig(configs, "segments", slideSegments.getValueInt());
		putFloatConfig(configs, "verticalLineSpan", slideVSpan.getValueInt());
		putFloatConfig(configs, "horizontalLineDivision", slideHDiv.getValueInt());
		putFloatConfig(configs, "decimalLength", slideDecimals.getValueInt());
		putFloatConfig(configs, "textOffset", tenths(slideTextOffset));
		putFloatConfig(configs, "width", tenths(slideWidth));
		putFloatConfig(configs, "height", tenths(slideHeight));
	}

	@Override
	public void init() {
		int cx = gui.width / 2;
		int top = gui.getGuiTop();
		int left = cx - START_X_OFFSET;
		int right = cx - START_X_OFFSET + SPACING;
		slideR = slider(left, top + 70, 80, ChatFormatting.RED + "R ", 0, 255, red);
		slideG = slider(left, top + 70 + 20, 80, ChatFormatting.GREEN + "G ", 0, 255, green);
		slideB = slider(left, top + 70 + 40, 80, ChatFormatting.BLUE + "B ", 0, 255, blue);
		slideWidth = slider(left, top + 70 + 60, 80, "Width ", 1, 10, width, true);
		slideHeight = slider(left, top + 70 + 80, 80, "Height ", 1, 10, height, true);
		slideSegments = slider(right, top + 70, 80, "Segments ", 1, 200, segments);
		slideVSpan = slider(right, top + 70 + 20, 80, "X Line Span ", 1, 20, verticalLineSpan);
		slideHDiv = slider(right, top + 70 + 40, 80, "Y Line Div ", 0, 20, horizontalLineDivision);
		slideDecimals = slider(right, top + 70 + 60, 80, "Decimals ", 0, 5, decimalLength);
		slideTextOffset = slider(right, top + 70 + 80, 80, "Txt Offs. ", 0, 1, textOffset, true);
		skinButton = button(right, top + 70 + 100, 80, 20, "Theme: " + skin);
		super.init();
	}

	@Override
	public void enableButtons(boolean enable) {
		super.enableButtons(enable);
		if(skinButton != null)
			GuiDraw.show(skinButton, enable);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button != skinButton)
			return;
		List<GraphSkin> skins = GraphSkin.skins();
		int index = -1;
		for(int i = 0; i < skins.size(); i++) {
			if(skins.get(i).name.equals(skin)) {
				index = i;
				break;
			}
		}
		index++;
		if(index >= skins.size())
			index = 0;
		skin = skins.get(index).name;
		skinButton.setMessage(net.minecraft.network.chat.Component.literal("Theme: " + skin));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if(slideR != null)
			swatch(g, 20, 70, slideR.getValueInt() / 255F, slideG.getValueInt() / 255F, slideB.getValueInt() / 255F);
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
