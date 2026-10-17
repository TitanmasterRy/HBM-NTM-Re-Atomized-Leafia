package com.leafia.contents.building.storage.broof.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The broof's screen — <b>this addon's first</b>.
 *
 * <p>Upstream: {@code .../broof/container/BroofGUI.java}, which is a double chest with a
 * different title. It binds <b>vanilla's own</b> {@code generic_54.png} rather than shipping a
 * texture, so this addon ships none either; the slot coordinates in {@link BroofMenu} are
 * upstream's precisely because they have to land on it.
 *
 * <h2>What 1.21.1 already does that upstream wrote out</h2>
 *
 * Three of upstream's four methods are gone. {@code drawScreen} exists only to call
 * {@code renderHoveredToolTip}, which {@code AbstractContainerScreen.render} does itself;
 * {@code drawDefaultBackground} is {@code renderBackground}, called for us; and the two
 * {@code fontRenderer.drawString} calls in {@code drawGuiContainerForegroundLayer} are exactly
 * what {@code renderLabels} draws by default, from {@link #title} and
 * {@link #playerInventoryTitle}.
 *
 * <p>So what is left is the texture, the size, and <b>one</b> deviation from the default, below.
 */
@OnlyIn(Dist.CLIENT)
public class BroofScreen extends AbstractContainerScreen<BroofMenu> {

	/** Vanilla's double-chest background. Upstream binds this and so does this. */
	private static final ResourceLocation TEXTURE =
			ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

	public BroofScreen(BroofMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 222;
		// Vanilla measures the inventory label from the bottom; upstream hardcodes
		// `ySize - 96 + 2`, which is the same arithmetic. Set explicitly because the default is
		// imageHeight - 94, two pixels higher.
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	/**
	 * Upstream centres the title; vanilla left-aligns it at x=8.
	 *
	 * <p>The one visible difference between this screen and the vanilla default, and it is
	 * upstream's: {@code xSize / 2 - getStringWidth(name) / 2}, at y=6 rather than vanilla's 6
	 * — the y agrees, the x does not.
	 */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}
}
