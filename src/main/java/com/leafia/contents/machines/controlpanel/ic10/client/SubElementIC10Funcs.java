package com.leafia.contents.machines.controlpanel.ic10.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.SubElement;
import com.leafia.contents.machines.controlpanel.ic10.IC10;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Argument;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Instruction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The instruction reference (CCP-IC10, D-3073): every instruction in {@link IC10#insn_def}, in its categories, each with its
 * arguments and its description, in a list that scrolls and whose categories fold. Adapted from Leafia's
 * {@code SubElementIC10Funcs}; the list is built from the instruction table itself, so it cannot disagree with the interpreter.
 * "Source" opens Leafia's IC10 source in the browser, after the game's own link confirmation.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementIC10Funcs extends SubElement {

	public static final ResourceLocation texture = GuiDraw.texture("gui_ic10_funcs");
	public static final ResourceLocation extra = GuiDraw.texture("gui_ic10_funcs_extra");
	public static final String SOURCE = "https://github.com/ntmleafia/NTM-Cursed-Addon/blob/main/src/main/java/com/leafia/contents/machines/controlpanel/ic10/IC10.java";

	public Button btn_back;
	public Button btn_src;

	public static class ListObject {
		int parenthesis = 0;
	}

	public static class ListInstruction extends ListObject {
		public List<String> list = new ArrayList<>();
	}

	public static class ListCategory extends ListObject {
		public String name;
		public List<ListObject> contents = new ArrayList<>();
		/** The fold arrow's box, in the page's pixels. */
		public int x, y, w, h;
		public boolean unfolded = false;
		public boolean visible = false;
	}

	int parenthesisOffset = 6;
	public List<ListObject> contents = new ArrayList<>();
	public List<ListCategory> cats = new ArrayList<>();

	public int scroll = 0;
	boolean scrollin = false;
	int lastY = 0;

	private static final int LIST_X = 55, LIST_Y = 13, LIST_H = 229;

	public SubElementIC10Funcs(ControlEditScreen gui) {
		super(gui);
	}

	public void addContents(List<ListObject> into, Map<String, Object> map, int parenthesis) {
		for(Entry<String, Object> entry : map.entrySet()) {
			if(entry.getValue() instanceof Map<?, ?> category) {
				ListCategory item = new ListCategory();
				item.parenthesis = parenthesis;
				item.x = LIST_X + (2 + parenthesis * parenthesisOffset) / 2;
				item.y = 15;
				item.w = 9 / 2;
				item.h = 9 / 2;
				item.name = entry.getKey();
				@SuppressWarnings("unchecked")
				Map<String, Object> inner = (Map<String, Object>) category;
				addContents(item.contents, inner, parenthesis + 1);
				into.add(item);
				cats.add(item);
			} else if(entry.getValue() instanceof IC10Instruction insn) {
				ListInstruction item = new ListInstruction();
				item.parenthesis = parenthesis;
				StringBuilder title = new StringBuilder("§e" + entry.getKey() + "§7");
				for(IC10Argument arg : insn.args)
					title.append(" ").append(arg.name).append("<").append(arg.type.name().toLowerCase()).append(">");
				item.list.add(title.toString());
				item.list.addAll(java.util.Arrays.asList(insn.desc));
				into.add(item);
			}
		}
	}

	@Override
	public void init() {
		btn_back = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 13, 30, 20, "Back");
		btn_src = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 45, 40, 20, "Source");
		super.init();
		contents.clear();
		cats.clear();
		addContents(contents, IC10.insn_def, 0);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_back)
			gui.popElement();
		else if(button == btn_src)
			ConfirmLinkScreen.confirmLinkNow(gui, SOURCE);
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(btn_back, enable);
		GuiDraw.show(btn_src, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}

	@Override
	public void onElementClose() {
		scrollin = false;
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(texture, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize() - 5, gui.getYSize() - 5);
	}

	// ------------------------------------------------------------------ geometry

	public int getContentHeight() {
		return lastY;
	}

	public int getMaxScroll() {
		return getContentHeight() - LIST_H;
	}

	float relMouseX() {
		return gui.mouseX - gui.getGuiLeft();
	}

	float relMouseY() {
		return gui.mouseY - gui.getGuiTop();
	}

	private boolean inBox(double rx, double ry, int x, int y, int w, int h) {
		return rx >= x && ry >= y && rx < x + w && ry < y + h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		double delta = -Mth.clamp(scrollY, -1, 1);
		if(delta != 0)
			scroll = Mth.clamp(scroll + (int) (delta * 10), 0, Math.max(getMaxScroll(), 0));
		return delta != 0;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(button != 0)
			return false;
		double rx = mouseX - gui.getGuiLeft();
		double ry = mouseY - gui.getGuiTop();
		if(inBox(rx, ry, 236, 13, 12, 229)) {
			scrollin = true;
			return true;
		}
		for(ListCategory cat : cats) {
			if(cat.visible && inBox(rx, ry, cat.x, cat.y, cat.w, cat.h)) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2F));
				cat.unfolded = !cat.unfolded;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0)
			scrollin = false;
		return false;
	}

	// ------------------------------------------------------------------ drawing

	/** One piece of the panel sheet stretched over a box. */
	private void stretched(GuiGraphics g, int x, int y, int tx, int ty, int w, int h) {
		if(w > 0 && h > 0)
			g.blit(extra, x, y, w, h, tx, ty, 1, 1, 256, 256);
	}

	/** A panel with a one-pixel border from the sheet's 3 by 3 corner block (Leafia's {@code drawPanel}). */
	private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
		stretched(g, x, y, 24, 0, 1, 1);
		stretched(g, x + w - 1, y, 26, 0, 1, 1);
		stretched(g, x + w - 1, y + h - 1, 26, 2, 1, 1);
		stretched(g, x, y + h - 1, 24, 2, 1, 1);

		stretched(g, x + 1, y, 25, 0, w - 2, 1);
		stretched(g, x + 1, y + h - 1, 25, 2, w - 2, 1);
		stretched(g, x, y + 1, 24, 1, 1, h - 2);
		stretched(g, x + w - 1, y + 1, 26, 1, 1, h - 2);

		stretched(g, x + 1, y + 1, 25, 1, w - 2, h - 2);
	}

	/** Draws the list in the scaled space and measures it; {@code lastY} ends as the list's height in that space. */
	private void iterate(GuiGraphics g, List<ListObject> list, Font font) {
		for(ListObject obj : list) {
			if(obj instanceof ListCategory cat) {
				int o = parenthesisOffset * cat.parenthesis;
				drawPanel(g, o, lastY, 175 * 2 - o, 13);
				boolean hot = inBox(relMouseX(), relMouseY(), cat.x, cat.y, cat.w, cat.h);
				if(hot)
					g.setColor(0.9F, 0.9F, 0.9F, 1F);
				g.blit(extra, 2 + o, lastY + 2, cat.unfolded ? 9 : 0, 15, 9, 9);
				g.setColor(1F, 1F, 1F, 1F);
				g.drawString(font, cat.name, 12 + o, lastY + 3, 0xFFFFFFFF, false);
				cat.y = 13 + (lastY + 2) / 2 - scroll;
				lastY += 13;
				cat.visible = true;
				if(cat.unfolded)
					iterate(g, cat.contents, font);
			} else if(obj instanceof ListInstruction insn) {
				int o = parenthesisOffset * insn.parenthesis;
				int space = 10;
				int h = insn.list.size() * space + 3;
				drawPanel(g, o, lastY, 175 * 2 - o, h);
				for(int i = 0; i < insn.list.size(); i++)
					g.drawString(font, insn.list.get(i), 2 + o, lastY + 3 + space * i, 0xFFFFFFFF, false);
				lastY += h;
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int left = gui.getGuiLeft();
		int top = gui.getGuiTop();
		int maxScroll = Math.max(getMaxScroll(), 0);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		int scrollBarPos = 0;
		if(maxScroll > 0) {
			if(scrollin) {
				double ratio = Mth.clamp((relMouseY() - 13 - 15 / 2d) / (229 - 15), 0, 1);
				scroll = Mth.clamp((int) (ratio * maxScroll + 0.5), 0, maxScroll);
			}
			scrollBarPos = (int) ((maxScroll == 0 ? 0 : (double) scroll / maxScroll) * (229 - 15));
		}
		g.blit(extra, left + 236, top + 13 + scrollBarPos, (maxScroll > 0) ? 0 : 12, 0, 12, 15);

		for(ListCategory cat : cats)
			cat.visible = false;
		lastY = 0;
		g.enableScissor(left + LIST_X, top + LIST_Y, left + LIST_X + 176, top + LIST_Y + LIST_H);
		g.pose().pushPose();
		g.pose().translate(left + LIST_X, top + LIST_Y - scroll, 0);
		g.pose().scale(0.5F, 0.5F, 1F);
		iterate(g, contents, gui.font());
		g.pose().popPose();
		g.disableScissor();
		lastY /= 2;
	}
}
