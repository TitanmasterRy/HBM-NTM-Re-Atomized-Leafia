package com.leafia.contents.machines.powercores.ams.emitter.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The AMC emitter's screen: a coolant tank, three gauges (heat, power in, efficiency) and a
 * warning icon.
 *
 * <p>Upstream: {@code .../ams/emitter/container/AMSEmitterUI.java}, 176 by 166, and {@code
 * gui_emitter.png}. The tank is at (26, 17) and 16 by 52, the heat gauge at (8, 17), the power
 * gauge at (134, 17) and the efficiency gauge at (152, 17), each drawn up from y = 69 from a strip
 * 52 tall at x = 176, 192 and 208; the warning icon is at (80, 17) from (176, 36 + 16 warning).
 * The efficiency gauge's tooltip says "Power:", which is upstream's word for it.
 */
@OnlyIn(Dist.CLIENT)
public class AMSEmitterScreen extends GuiInfoContainer<AMSEmitterMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/ams_emitter.png");

	private final AMSEmitterTE emitter;

	public AMSEmitterScreen(AMSEmitterMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.emitter = menu.emitter;
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(emitter != null) {
			GUIElements.drawFluidTankInfo(graphics, this, emitter.tank, mouseX, mouseY, leftPos + 26, topPos + 69 - 52, 16, 52, hasShiftDown());
			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 134, topPos + 69 - 52, 16, 52, emitter.power, AMSEmitterTE.maxPower);
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 152, topPos + 69 - 52, 16, 52, mouseX, mouseY,
					"Power:", emitter.efficiency + "%");
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 8, topPos + 69 - 52, 16, 52, mouseX, mouseY,
					"Heat:", emitter.heat + "/" + AMSEmitterTE.maxHeat);
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

		if(emitter == null) return;

		int i = (int) emitter.getPowerScaled(52);
		graphics.blit(TEXTURE, leftPos + 134, topPos + 69 - i, 192, 52 - i, 16, i);

		int j = emitter.getEfficiencyScaled(52);
		graphics.blit(TEXTURE, leftPos + 152, topPos + 69 - j, 208, 52 - j, 16, j);

		int k = emitter.getHeatScaled(52);
		graphics.blit(TEXTURE, leftPos + 8, topPos + 69 - k, 176, 52 - k, 16, k);

		int m = emitter.warning;
		if(m > 0)
			graphics.blit(TEXTURE, leftPos + 80, topPos + 17, 176, Math.floorMod(36 + 16 * m, 256), 16, 16);

		GUIElements.drawFluidTank(graphics, emitter.tank, leftPos + 26, topPos + 97 - 28, 16, 52);
	}
}
