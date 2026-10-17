package com.leafia.contents.machines.powercores.ams.base.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The AMC base's screen: four tanks, four gauges (power, field, efficiency, heat), the core's colour,
 * and two icons for what is wrong and which stabilizers are in play.
 *
 * <p>Upstream: {@code .../ams/base/container/AMSBaseUI.java}. Every coordinate and every source
 * rectangle on the sheet is upstream's, and the sheet is the same {@code gui_base.png}:
 *
 * <ul>
 * <li>power at (116, 20) and 7 by 106, field at (44, 18), efficiency at (53, 18) and heat at
 *     (125, 18), each drawn up from y = 124 from a strip of the sheet 106 tall at x = 206, 192, 199
 *     and 213;</li>
 * <li>tanks at x = 26 and x = 134, 16 by 52, coolants with their bottom at y = 70 and fuels at 124;</li>
 * <li>the mode icon at (89, 18) from (176, 32 + 16 mode) when there is one, the warning icon at
 *     (71, 18) from (176, 16 warning - 16), and the core's glow at (61, 44), 54 by 54, tinted by
 *     {@link AMSBaseTE#color} then drawn plain;</li>
 * <li>with no resonators, a red badge at (-16, 36) outside the frame whose hover text explains it.</li>
 * </ul>
 *
 * <p>The locked warning is 33, which is a source row of 512 on a sheet 256 tall. Upstream's GL
 * texture wraps and so draws the row it wraps to, the first icon; here the row is reduced modulo
 * 256 so it does the same on a sampler that clamps.
 */
@OnlyIn(Dist.CLIENT)
public class AMSBaseScreen extends GuiInfoContainer<AMSBaseMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/ams_base.png");

	private final AMSBaseTE base;

	public AMSBaseScreen(AMSBaseMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.base = menu.base;
		this.imageWidth = 176;
		this.imageHeight = 222;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(base != null) {
			boolean shift = hasShiftDown();
			GUIElements.drawFluidTankInfo(graphics, this, base.tanks[0], mouseX, mouseY, leftPos + 26, topPos + 70 - 52, 16, 52, shift);
			GUIElements.drawFluidTankInfo(graphics, this, base.tanks[1], mouseX, mouseY, leftPos + 134, topPos + 70 - 52, 16, 52, shift);
			GUIElements.drawFluidTankInfo(graphics, this, base.tanks[2], mouseX, mouseY, leftPos + 26, topPos + 124 - 52, 16, 52, shift);
			GUIElements.drawFluidTankInfo(graphics, this, base.tanks[3], mouseX, mouseY, leftPos + 134, topPos + 124 - 52, 16, 52, shift);

			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 116, topPos + 124 - 104, 7, 104, base.power, AMSBaseTE.maxPower);
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 44, topPos + 124 - 106, 7, 106, mouseX, mouseY,
					"Restriction Field:", base.field + "%");
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 53, topPos + 124 - 106, 7, 106, mouseX, mouseY,
					"Efficiency:", base.efficiency + "%");
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 125, topPos + 124 - 106, 7, 106, mouseX, mouseY,
					"Heat:", base.heat + "/" + AMSBaseTE.maxHeat);

			if(!base.hasResonators()) {
				drawCustomInfoStat(graphics, mouseX, mouseY, leftPos - 16, topPos + 36, 16, 16, leftPos - 8, topPos + 36 + 16,
						"Error: Three satellite ID-chips linked",
						"to xenium resonators are required",
						"for this machine to work!");
			}
		}

		renderTooltip(graphics, mouseX, mouseY);
	}

	/** Upstream: the name centred at y = 6 and the inventory label 96 above the bottom. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(base == null) return;

		int i = (int) base.getPowerScaled(106);
		graphics.blit(TEXTURE, leftPos + 116, topPos + 124 - i, 206, 106 - i, 7, i);

		int j = base.getFieldScaled(106);
		graphics.blit(TEXTURE, leftPos + 44, topPos + 124 - j, 192, 106 - j, 7, j);

		int k = base.getEfficiencyScaled(106);
		graphics.blit(TEXTURE, leftPos + 53, topPos + 124 - k, 199, 106 - k, 7, k);

		int l = base.getHeatScaled(106);
		graphics.blit(TEXTURE, leftPos + 125, topPos + 124 - l, 213, 106 - l, 7, l);

		int m = base.mode;
		if(m > 0)
			graphics.blit(TEXTURE, leftPos + 89, topPos + 18, 176, 32 + 16 * m, 16, 16);

		int n = base.warning;
		if(n > 0)
			graphics.blit(TEXTURE, leftPos + 71, topPos + 18, 176, Math.floorMod(-16 + 16 * n, 256), 16, 16);

		if(base.color > -1) {
			graphics.setColor(((base.color & 0xFF0000) >> 16) / 255F, ((base.color & 0x00FF00) >> 8) / 255F,
					(base.color & 0x0000FF) / 255F, 1F);
			graphics.blit(TEXTURE, leftPos + 61, topPos + 44, 176, 160, 54, 54);
			graphics.setColor(1F, 1F, 1F, 1F);
			graphics.blit(TEXTURE, leftPos + 61, topPos + 44, 176, 106, 54, 54);
		}

		if(!base.hasResonators())
			drawInfoPanel(graphics, leftPos - 16, topPos + 36, 6);

		GUIElements.drawFluidTank(graphics, base.tanks[0], leftPos + 26, topPos + 98 - 28, 16, 52);
		GUIElements.drawFluidTank(graphics, base.tanks[1], leftPos + 134, topPos + 98 - 28, 16, 52);
		GUIElements.drawFluidTank(graphics, base.tanks[2], leftPos + 26, topPos + 152 - 28, 16, 52);
		GUIElements.drawFluidTank(graphics, base.tanks[3], leftPos + 134, topPos + 152 - 28, 16, 52);
	}
}
