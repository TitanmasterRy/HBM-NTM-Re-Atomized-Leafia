package com.leafia.contents.miscellanous.slop.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.leafia.contents.miscellanous.slop.SlopTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The slop reactor's screen: a switch, and four bars - fuel, power, the layer burning, and damage.
 *
 * <p>Upstream: {@code .../slop/container/SlopGUI.java}, on {@code LCEGuiInfoContainer}. As with the
 * boiler's (D-1054) nothing is needed from that base: the one thing it was asked for beyond the
 * base mod's {@link GuiInfoContainer} is a button click, and vanilla's own UI click is what a
 * 1.21.1 player expects.
 *
 * <p>Every coordinate is upstream's. The switch is the 18x18 at (7, 52), lit from (208, 0) while
 * the reactor is on; the fuel and power bars are 16x52 and fill from the bottom, the layer is
 * 52x16 filling from the left, and damage is 34x16 filling from the left.
 */
@OnlyIn(Dist.CLIENT)
public class SlopScreen extends GuiInfoContainer<SlopMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/reactors/slop.png");

	private final SlopTE slop;

	public SlopScreen(SlopMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.slop = menu.slop;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(slop != null)
			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 152, topPos + 17, 16, 52, slop.power, slop.getMaxPower());

		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {

		if(button == 0 && slop != null && x >= leftPos + 7 && y >= topPos + 52
				&& x < leftPos + 7 + 18 && y < topPos + 52 + 18) {
			LeafiaPacket._start(slop).__write(0, !slop.on).__sendToServer();
			Minecraft.getInstance().getSoundManager()
					.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
		}

		return super.mouseClicked(x, y, button);
	}

	/** Upstream centres the name at y = 6 and puts the inventory label 96 above the bottom. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	private void verticalBar(GuiGraphics graphics, int x, int y, int u, int v, int level) {
		graphics.blit(TEXTURE, leftPos + x, topPos + y + 52 - level, u, v + 52 - level, 16, level);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(slop == null) return;

		if(slop.on) graphics.blit(TEXTURE, leftPos + 7, topPos + 52, 208, 0, 18, 18);

		verticalBar(graphics, 44, 17, 192, 0, slop.fuel * 52 / SlopTE.MAX_FUEL);
		verticalBar(graphics, 152, 17, 176, 0, (int) (slop.power * 52 / slop.getMaxPower()));

		if(slop.dynaMaxPartialConsumption > 0)
			graphics.blit(TEXTURE, leftPos + 80, topPos + 17, 176, 52,
					52 * slop.partialConsumption / slop.dynaMaxPartialConsumption, 16);

		graphics.blit(TEXTURE, leftPos + 80 + 18, topPos + 53, 176, 68, 34 * slop.damage / SlopTE.maxDamage, 16);
	}
}
