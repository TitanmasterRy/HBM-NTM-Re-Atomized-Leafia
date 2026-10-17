package com.leafia.contents.gear.advisor.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.LeafiaItems;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One warning, full text: the screen the list's Read button opens.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/advisor/container/AdvisorUIRead.java}. The same
 * 195 by 145 panel with a different background, the message word-wrapped to 152 pixels inside it
 * (and split again on every {@code $}, the lang files' own line break), and one 48 by 17 Back
 * button at (8, 123). Escape and the inventory key both mean Back, not close: they return to the
 * list scrolled where it was, which is why this screen carries the list's scroll position.
 *
 * <p>Leaving this screen any other way sends {@code CLOSE}, exactly as the list does.
 */
@OnlyIn(Dist.CLIENT)
public class AdvisorUIRead extends GuiScreenLeafia implements IAdvisorUI {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/advisor/message.png");

	final String str;
	final int scr;

	public AdvisorUIRead(String message, int scroll) {
		AdvisorUI.instance = this;
		xSize = 195;
		ySize = 145;
		str = message;
		scr = scroll;
	}

	FiaUIRect closeRect;

	@Override
	protected void init() {
		super.init();
		closeRect = new FiaUIRect(this, 8, 123, 48, 17);
	}

	private void drawButton(GuiGraphics graphics, String s, FiaUIRect rect) {
		int width = font.width(s);
		graphics.drawString(font, s, rect.guiLeft + rect.x + rect.w / 2 - width / 2, rect.guiTop + rect.y + 5, 4210752, false);
	}

	boolean screenSwitching = false;

	@Override
	public void removed() {
		if(screenSwitching) return;
		AdvisorUI.instance = null;
		AdvisorUI.sendClose();
		super.removed();
	}

	private void back() {
		screenSwitching = true;
		Minecraft.getInstance().setScreen(new AdvisorUI(scr));
	}

	/**
	 * Upstream's {@code keyTyped}, which replaces the base behaviour outright: Escape or the
	 * inventory key goes back, and no other key does anything.
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			back();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if(mouseButton == 0 && closeRect.isMouseIn((int) mouseX, (int) mouseY)) {
			playClick(1);
			back();
		}
		return true;
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.setColor(1, 1, 1, 1);
		String name = LeafiaItems.advisor.get().getDescription().getString();
		graphics.drawString(font, name, xSize / 2 - font.width(name) / 2, 5, 4210752, false);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {

		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);

		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
		if(closeRect.isMouseIn(mouseX, mouseY)) {
			graphics.setColor(0.9f, 0.9f, 0.9f, 1f);
			drawTexturedModalByFiaRect(graphics, TEXTURE, closeRect, 8, 123);
			graphics.setColor(1, 1, 1, 1);
		}

		List<String> text = new ArrayList<>();
		for(String s : I18nUtil.autoBreak(font, str, 162 - 10))
			text.addAll(Arrays.asList(s.split("\\$")));
		int i = 0;
		for(String s : text)
			graphics.drawString(font, s, guiLeft + 8 + 5, guiTop + 17 + 5 + 11 * (i++), 0xa0a0a0, true);
		drawButton(graphics, I18nUtil.resolveKey("item.advisor.gui.message.close"), closeRect);
	}
}
