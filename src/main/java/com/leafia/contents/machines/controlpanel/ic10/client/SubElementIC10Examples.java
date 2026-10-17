package com.leafia.contents.machines.controlpanel.ic10.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.SubElement;
import com.leafia.contents.machines.controlpanel.ic10.IC10Examples;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The list of example programs (CCP-IC10, D-3073): one button per entry of {@link IC10Examples}, each leading to the warning that
 * the current code will be lost. Adapted from Leafia's {@code SubElementIC10Examples}; the programs are generated into
 * {@code IC10Examples} so a test can run each one.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementIC10Examples extends SubElement {

	public Button back;
	public List<Button> buttons = new ArrayList<>();
	private final List<String> names = new ArrayList<>();

	public SubElementIC10Examples(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		back = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 13, 30, 20, "Back");
		buttons.clear();
		names.clear();
		int i = 0;
		for(Map.Entry<String, ArrayList<ArrayList<Object>>> entry : IC10Examples.examples.entrySet()) {
			buttons.add(button(cX - 62, cY - 100 + i * 25, 170, 20, entry.getKey()));
			names.add(entry.getKey());
			i++;
		}
		super.init();
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(back, enable);
		for(Button button : buttons)
			GuiDraw.show(button, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == back) {
			gui.popElement();
			return;
		}
		int index = buttons.indexOf(button);
		if(index >= 0 && gui.ic10Warning != null) {
			gui.ic10Warning.instruction = IC10Examples.copyOf(IC10Examples.examples.get(names.get(index)));
			gui.pushElement(gui.ic10Warning);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = gui.font();
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		String text = "Example Codes";
		g.drawString(font, text, cX - font.width(text) / 2F + 22, cY - 112, 0xFF777777, false);
	}
}
