package com.leafia.contents.machines.controlpanel.ic10.client;

import java.util.ArrayList;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.SubElement;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * "The current code will be lost" (CCP-IC10, D-3073): the question between choosing an example and replacing the program with it.
 * Adapted from Leafia's {@code SubElementIC10Warning}. Either button leaves both this page and the example list; "Proceed" puts
 * the example into the editor first.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementIC10Warning extends SubElement {

	/** The program the Examples page chose, waiting for an answer. */
	public ArrayList<ArrayList<Object>> instruction;
	public Button back;
	public Button proceed;

	public SubElementIC10Warning(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		proceed = button(cX - 62, cY - 15, 170, 20, "Proceed");
		back = button(cX - 62, cY + 15, 170, 20, "Wait no");
		super.init();
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(proceed, enable);
		GuiDraw.show(back, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}

	@Override
	public void actionPerformed(Button button) {
		gui.popElement();
		gui.popElement();
		if(button == proceed && instruction != null && gui.ic10Editor != null)
			gui.ic10Editor.replaceProgram(instruction);
		instruction = null;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = gui.font();
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		String text = "The current code will be lost!";
		g.drawString(font, text, cX - font.width(text) / 2F + 22, cY - 40, 0xFF777777, false);
		text = "Are you sure want to continue?";
		g.drawString(font, text, cX - font.width(text) / 2F + 22, cY - 40 - 10, 0xFF777777, false);
	}
}
