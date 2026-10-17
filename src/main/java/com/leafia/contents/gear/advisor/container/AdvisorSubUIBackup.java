package com.leafia.contents.gear.advisor.container;

import org.lwjgl.glfw.GLFW;

import com.leafia.contents.LeafiaItems;
import com.leafia.dev.gui.GuiScreenLeafia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * An empty advisor panel that goes back to the list: the skeleton upstream copied the Read screen
 * from. <b>Nothing opens it</b> - no code in upstream names it outside its own file - and it is
 * ported because it is part of the cluster and costs nothing, not because a player can reach it.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/advisor/container/AdvisorSubUIBackup.java}.
 * Kept as it was, including that it draws the list's own background rather than the message one.
 */
@OnlyIn(Dist.CLIENT)
public class AdvisorSubUIBackup extends GuiScreenLeafia implements IAdvisorUI {

	final int scr;

	public AdvisorSubUIBackup(int scroll) {
		AdvisorUI.instance = this;
		xSize = 195;
		ySize = 145;
		scr = scroll;
	}

	boolean screenSwitching = false;

	@Override
	public void removed() {
		if(screenSwitching) return;
		AdvisorUI.instance = null;
		AdvisorUI.sendClose();
		super.removed();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			screenSwitching = true;
			Minecraft.getInstance().setScreen(new AdvisorUI(scr));
			return true;
		}
		return false;
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
		graphics.blit(AdvisorUI.TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
	}
}
