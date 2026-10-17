package com.leafia.contents.machines.powercores.dfc.components.injector;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The overhauled injector's screen: its two tanks, and between them a picture of the core it feeds.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/injector/CoreInjectorGUI.java}, on
 * {@code leafia:textures/gui/dfc/gui_injector.png}, 176 by 166. Tanks at (35, 16) and (125, 16), 16 by 52.
 * The picture is the first of these that holds, upstream's order: the core at or above 1,500 degrees,
 * (53, 15) from (176, 87), 70 square; at or above 100, (53, 15) from (176, 29), 70 by 57; both of the core's
 * tanks holding fuel, (72, 36) from (176, 0), 32 by 28; otherwise nothing. Nothing is drawn while the
 * injector sees no core.
 */
@OnlyIn(Dist.CLIENT)
public class CoreInjectorScreen extends GuiInfoContainer<CoreInjectorMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/dfc/gui_injector.png");
	private final LeafiaCoreInjectorTE injector;

	public CoreInjectorScreen(CoreInjectorMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.injector = menu.injector;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		GUIElements.drawFluidTankInfo(graphics, this, injector.tanks[0], mouseX, mouseY, leftPos + 35, topPos + 16, 16, 52, hasShiftDown());
		GUIElements.drawFluidTankInfo(graphics, this, injector.tanks[1], mouseX, mouseY, leftPos + 125, topPos + 16, 16, 52, hasShiftDown());
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		BlockEntityCore core = injector.leafia$lastGetCore();
		if(core != null) {
			switch(LeafiaCoreInjectorTE.screenPicture(core)) {
				case 2 -> graphics.blit(TEXTURE, leftPos + 53, topPos + 15, 176, 87, 70, 70);
				case 1 -> graphics.blit(TEXTURE, leftPos + 53, topPos + 15, 176, 29, 70, 57);
				case 0 -> graphics.blit(TEXTURE, leftPos + 72, topPos + 36, 176, 0, 32, 28);
				default -> { }
			}
		}

		GUIElements.drawFluidTank(graphics, injector.tanks[0], leftPos + 35, topPos + 69, 16, 52);
		GUIElements.drawFluidTank(graphics, injector.tanks[1], leftPos + 125, topPos + 69, 16, 52);
	}
}
