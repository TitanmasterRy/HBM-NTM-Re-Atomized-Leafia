package com.leafia.contents.machines.powercores.ams.stabilizer.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The AMC stabilizer's screen: the emitter's, with a second icon for which focus it holds.
 *
 * <p>Upstream: {@code .../ams/stabilizer/container/AMSStabilizerUI.java}, 176 by 166, and
 * {@code gui_stabilizer.png}. Same tank, three gauges and warning icon in the same places; the
 * focus icon is at (98, 17) from (176, 84 + 16 mode) when there is a focus, and the second gauge
 * is labelled "Efficiency:".
 */
@OnlyIn(Dist.CLIENT)
public class AMSStabilizerScreen extends GuiInfoContainer<AMSStabilizerMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/ams_stabilizer.png");

	private final AMSStabilizerTE limiter;

	public AMSStabilizerScreen(AMSStabilizerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.limiter = menu.stabilizer;
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(limiter != null) {
			GUIElements.drawFluidTankInfo(graphics, this, limiter.tank, mouseX, mouseY, leftPos + 26, topPos + 69 - 52, 16, 52, hasShiftDown());
			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 134, topPos + 69 - 52, 16, 52, limiter.power, AMSStabilizerTE.maxPower);
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 152, topPos + 69 - 52, 16, 52, mouseX, mouseY,
					"Efficiency:", limiter.efficiency + "%");
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 8, topPos + 69 - 52, 16, 52, mouseX, mouseY,
					"Heat:", limiter.heat + "/" + AMSStabilizerTE.maxHeat);
		}

		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(limiter == null) return;

		int i = (int) limiter.getPowerScaled(52);
		graphics.blit(TEXTURE, leftPos + 134, topPos + 69 - i, 192, 52 - i, 16, i);

		int j = limiter.getEfficiencyScaled(52);
		graphics.blit(TEXTURE, leftPos + 152, topPos + 69 - j, 208, 52 - j, 16, j);

		int k = limiter.getHeatScaled(52);
		graphics.blit(TEXTURE, leftPos + 8, topPos + 69 - k, 176, 52 - k, 16, k);

		int l = limiter.mode;
		if(l > 0)
			graphics.blit(TEXTURE, leftPos + 98, topPos + 17, 176, 84 + 16 * l, 16, 16);

		int m = limiter.warning;
		if(m > 0)
			graphics.blit(TEXTURE, leftPos + 80, topPos + 17, 176, Math.floorMod(36 + 16 * m, 256), 16, 16);

		GUIElements.drawFluidTank(graphics, limiter.tank, leftPos + 26, topPos + 97 - 28, 16, 52);
	}
}
