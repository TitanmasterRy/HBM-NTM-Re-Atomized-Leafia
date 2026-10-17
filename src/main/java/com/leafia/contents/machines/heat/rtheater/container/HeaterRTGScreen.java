package com.leafia.contents.machines.heat.rtheater.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.leafia.contents.machines.heat.rtheater.HeaterRTGTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The RT heater's screen: the pellet grid, the rod slot, and two bars - the heat rate on a log scale and
 * the store.
 *
 * <p>Upstream: {@code .../rtheater/container/HeaterRTGUI.java}, coordinate for coordinate. The title is
 * centred and the inventory label sits at {@code ySize - 94}, as upstream draws them; the rate bar is
 * logarithmic and full at 90,000 TU a tick ({@link HeaterRTGTE#getHeatGenScaled}), the store linear.
 */
@OnlyIn(Dist.CLIENT)
public class HeaterRTGScreen extends GuiInfoContainer<HeaterRTGMenu> {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/heat/gui_heater_rtg.png");

	private final HeaterRTGTE heater;

	public HeaterRTGScreen(HeaterRTGMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.heater = menu.heater;
		this.imageWidth = 176;
		this.imageHeight = 176;
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = this.imageWidth / 2 - this.font.width(this.title) / 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(heater != null) {
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 152, topPos + 69 + 4 - 52, 16, 52, mouseX, mouseY,
					String.format("%,d", Math.min(heater.heatEnergy, HeaterRTGTE.MAX_HEAT)) + " / "
							+ String.format("%,d", HeaterRTGTE.MAX_HEAT) + " TU");
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 134, topPos + 69 + 4 - 52, 16, 52, mouseX, mouseY,
					String.format("%,d", heater.heatGen) + " TU/t");
		}

		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(heater == null) return;

		if(heater.hasHeatGen()) {
			int i = heater.getHeatGenScaled(52);
			graphics.blit(TEXTURE, leftPos + 134, topPos + 74 - i, 176, 52 - i, 16, i);
		}
		if(heater.hasHeat()) {
			int i = heater.getHeatScaled(52);
			graphics.blit(TEXTURE, leftPos + 152, topPos + 74 - i, 192, 52 - i, 16, i);
		}
	}
}
