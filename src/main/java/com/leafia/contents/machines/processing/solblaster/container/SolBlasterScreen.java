package com.leafia.contents.machines.processing.solblaster.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.items.ModItems;
import com.leafia.contents.machines.processing.solblaster.SolBlasterTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The Neutron Blaster's screen: a lit square behind each lens that is loaded, and a lamp once it is
 * armed.
 *
 * <p>Upstream: {@code .../solblaster/container/SolBlasterGUI.java}, on {@code LCEGuiInfoContainer},
 * of which it uses only {@code renderHoveredToolTip} - the item tooltip over a slot, which
 * {@link #renderTooltip} is. Everything else is upstream's: a 176 by 227 sheet, the name centred at
 * x = 124 and y = 47, and three overlays read straight from the slots.
 *
 * <ul>
 * <li>Each of the four lens squares is 23 by 23 at (8 + 23 * (i % 2), 8 + 23 * (i / 2)), lit from
 *     the sheet at (176 + 23 * (i % 2), 23 * (i / 2)) when slot {@code i + 1} holds a lens of
 *     either kind.</li>
 * <li>The armed lamp is 16 by 16 at (53, 86), lit from (176, 46), when there is a core <em>and</em>
 *     all four lenses.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class SolBlasterScreen extends GuiInfoContainer<SolBlasterMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/sol_blaster.png");

	private final SolBlasterTE blaster;

	public SolBlasterScreen(SolBlasterMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.blaster = menu.blaster;
		this.imageWidth = 176;
		this.imageHeight = 227;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	/** Upstream: the name centred on x = 124 at y = 47, and the inventory label 96 above the bottom. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 124 - font.width(title) / 2, 47, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
	}

	private static boolean isLens(ItemStack stack) {
		return stack.is(ModItems.early_explosive_lenses.get()) || stack.is(ModItems.explosive_lenses.get());
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(blaster == null) return;

		boolean core = blaster.slots[SolBlasterTE.SLOT_CORE].is(ModItems.solinium_core.get());
		boolean all = true;

		for(int i = 0; i < 4; i++) {
			boolean lens = isLens(blaster.slots[i + 1]);
			all &= lens;
			int x = i % 2 * 23;
			int y = i / 2 * 23;
			if(lens) graphics.blit(TEXTURE, leftPos + 8 + x, topPos + 8 + y, 176 + x, y, 23, 23);
		}

		if(core && all) graphics.blit(TEXTURE, leftPos + 53, topPos + 86, 176, 46, 16, 16);
	}
}
