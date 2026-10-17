package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The seven-segment display's pane: three colour sliders with a swatch, {@code digitCount} 0 to 8 and a {@code isDecimal} box.
 * Adapted from NTM-CE's {@code SubElementDisplaySevenSeg} (CCP-EDITOR, D-3066).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementDisplaySevenSeg extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {132, 112, 88, 88};

	private final float colorR, colorG, colorB;
	private final int digitCount;
	private final boolean isDecimal;

	private ControlSlider slideR, slideG, slideB, slideDigits;
	private Checkbox checkDecimal;

	public SubElementDisplaySevenSeg(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.colorR = num(map, "colorR", 1);
		this.colorG = num(map, "colorG", 0);
		this.colorB = num(map, "colorB", 0);
		this.digitCount = (int) num(map, "digitCount", 4);
		this.isDecimal = flag(map, "isDecimal", false);
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		if(slideR == null) {
			putFloatConfig(configs, "colorR", colorR);
			putFloatConfig(configs, "colorG", colorG);
			putFloatConfig(configs, "colorB", colorB);
			putFloatConfig(configs, "digitCount", digitCount);
			putFloatConfig(configs, "isDecimal", isDecimal ? 1F : 0F);
			return;
		}
		putFloatConfig(configs, "colorR", (float) slideR.fraction());
		putFloatConfig(configs, "colorG", (float) slideG.fraction());
		putFloatConfig(configs, "colorB", (float) slideB.fraction());
		putFloatConfig(configs, "digitCount", slideDigits.getValueInt());
		putFloatConfig(configs, "isDecimal", checkDecimal.selected() ? 1F : 0F);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int top = gui.getGuiTop();
		slideR = slider(cX - 85, top + 70, 80, ChatFormatting.RED + "R ", 0, 100, colorR * 100);
		slideG = slider(cX - 85, top + 90, 80, ChatFormatting.GREEN + "G ", 0, 100, colorG * 100);
		slideB = slider(cX - 85, top + 110, 80, ChatFormatting.BLUE + "B ", 0, 100, colorB * 100);
		slideDigits = slider(cX + 10, top + 70, 75, "Digits ", 0, 8, digitCount);
		checkDecimal = checkbox(cX + 10, top + 92, "Decimal", isDecimal);
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
