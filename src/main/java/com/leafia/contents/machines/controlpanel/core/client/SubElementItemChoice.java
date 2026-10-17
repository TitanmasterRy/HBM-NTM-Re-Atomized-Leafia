package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * "Select Control Type": one button per control family that has a registered control, seven to a page. Adapted from NTM-CE's
 * {@code SubElementItemChoice} (CCP-EDITOR, D-3066). CE recovered the type from the button's id (1000 + the type's index, with
 * ids skipped for empty families); a button here is looked up in a map instead.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementItemChoice extends SubElement {

	public static final ResourceLocation bg_tex = GuiDraw.texture("gui_base");

	public Button pageLeft;
	public Button pageRight;
	public int currentPage = 1;
	public int numPages = 1;
	public List<Button> buttons = new ArrayList<>();
	private final Map<Button, ControlType> types = new HashMap<>();

	public SubElementItemChoice(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		pageLeft = button(cX - 80, cY + 92, 15, 20, "<");
		pageRight = button(cX + 65, cY + 92, 15, 20, ">");

		int pos = 0;
		for(ControlType control : ControlType.ALL_VALUES) {
			if(!ControlRegistry.getAllControlsOfType(control).isEmpty()) {
				Button b = button(cX - 80, (cY - 90) + ((pos++) % 7) * 25, 160, 20, control.name);
				buttons.add(b);
				types.put(b, control);
			}
		}

		numPages = Math.max(1, (buttons.size() + 6) / 7);
		super.init();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		String text = currentPage + "/" + numPages;
		g.drawString(gui.font(), text, cX - gui.font().width(text) / 2F, cY + 98, 0xFF777777, false);
		text = "Select Control Type";
		g.drawString(gui.font(), text, cX - gui.font().width(text) / 2F, cY - 110, 0xFF777777, false);
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(bg_tex, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	private void recalculateVisibleButtons() {
		for(Button b : buttons)
			GuiDraw.show(b, false);
		int idx = (currentPage - 1) * 7;
		for(int i = idx; i < idx + 7; i++) {
			if(i >= buttons.size())
				break;
			GuiDraw.show(buttons.get(i), true);
		}
		boolean showPaging = numPages > 1;
		GuiDraw.show(pageLeft, showPaging && currentPage > 1);
		GuiDraw.show(pageRight, showPaging && currentPage < numPages);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == pageLeft) {
			currentPage = Math.max(1, currentPage - 1);
			recalculateVisibleButtons();
		} else if(button == pageRight) {
			currentPage = Math.min(numPages, currentPage + 1);
			recalculateVisibleButtons();
		} else {
			ControlType type = types.get(button);
			if(type == null)
				return;
			gui.itemConfig.variants = ControlRegistry.getAllControlsOfType(type);
			if(gui.itemConfig.variants.isEmpty())
				return;

			gui.currentEditControl = ControlRegistry.getNew(gui.itemConfig.variants.get(0), gui.panel);
			gui.pushElement(gui.itemConfig);
		}
	}

	@Override
	public void enableButtons(boolean enable) {
		if(enable) {
			recalculateVisibleButtons();
			gui.setSlotsEnabled(false);
		} else {
			for(Button b : buttons)
				GuiDraw.show(b, false);
			GuiDraw.show(pageLeft, false);
			GuiDraw.show(pageRight, false);
		}
	}
}
