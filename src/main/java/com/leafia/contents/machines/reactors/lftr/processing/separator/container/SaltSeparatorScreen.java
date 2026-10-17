package com.leafia.contents.machines.reactors.lftr.processing.separator.container;

import com.hbm.inventory.gui.GUIScreenRecipeSelector;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipes;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The salt separator's screen: five NTM tanks, three Forge ones, a power bar, a recipe selector and its
 * progress bar.
 *
 * <p>Upstream: {@code .../separator/container/SaltSeparatorGUI.java}, on {@code LCEGuiInfoContainer}
 * and the chemical plant's layout (D-3000). Every coordinate is upstream's:
 *
 * <ul>
 * <li>the two input tanks at {@code 26 + i*18, 18} and the three output tanks at {@code 80 + i*18, 18},
 *     each 16 by 34 (NTM tanks, drawn by {@code GUIElements});</li>
 * <li>the <b>salt tank</b>, a Forge tank, 16 by 70 at {@code 8, 18};</li>
 * <li>the two <b>buffers</b>, Forge tanks drawn as 52 by 7 bars filling rightward, at {@code 8, 99} (in)
 *     and {@code 80, 99} (out) - upstream rotates a vertical tank 90 degrees to get them;</li>
 * <li>the two concentrate tanks, NTM, 52 by 7 at {@code 8, 108} and {@code 80, 108};</li>
 * <li>the power bar 16 by 61 at {@code 152, 18}; the recipe selector 18 by 18 at {@code 7, 125} with its
 *     icon at {@code 8, 126}; the progress bar up to 70 wide at {@code 62, 126}; the two LEDs at
 *     {@code 51, 121} and {@code 56, 121}.</li>
 * </ul>
 *
 * <h2>The first screen to draw a Forge tank</h2>
 *
 * The three Forge tanks go through {@link LeafiaFFClient#drawTank} (the fluid's still texture, tinted,
 * tiled from the bottom or the left of the bar) and their tooltips through
 * {@link LeafiaFFClient#renderTankInfo}; the NTM ones through the base mod's {@code GUIElements} for the
 * picture and {@link LeafiaFFClient#renderTankInfo} for the tooltip, which adds the amount line and the
 * fuzzy identifier's click on an addon screen (D-3003). The tooltip of an empty Forge tank names the
 * salt the recipe uses, as upstream's {@code Fluid} argument does.
 *
 * <p>The slot background and every tooltip are drawn by the base; the foreground labels are upstream's
 * ({@code 70 - width/2, 6} for the title, {@code ySize - 96 + 2} for the inventory).
 */
@OnlyIn(Dist.CLIENT)
public class SaltSeparatorScreen extends LCEGuiInfoContainer<SaltSeparatorMenu> {

	static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/lftr/gui_separator.png");

	private final SaltSeparatorTE separator;

	public SaltSeparatorScreen(SaltSeparatorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.separator = menu.separator;
		this.imageWidth = 176;
		this.imageHeight = 256;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 70 - this.font.width(this.title) / 2;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		if(separator != null) {
			boolean showHidden = hasShiftDown();
			FluidStack saltLabel = LeafiaFF.toFF(separator.saltType, 1);

			for(int i = 0; i < 2; i++)
				LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 26 + i * 18, topPos + 18, 16, 34, separator.inputTanks[i], false);
			for(int i = 0; i < 3; i++)
				LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 80 + i * 18, topPos + 18, 16, 34, separator.outputTanks[i], false);

			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 8, topPos + 18, 16, 70,
					separator.saltTank.getFluid(), separator.saltTank.getCapacity(), saltLabel);
			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 8, topPos + 99, 52, 7,
					separator.bufferIn.getFluid(), separator.bufferIn.getCapacity(), saltLabel);
			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 80, topPos + 99, 52, 7,
					separator.bufferOut.getFluid(), separator.bufferOut.getCapacity(), saltLabel);

			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 8, topPos + 108, 52, 7, separator.concIn, false);
			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 80, topPos + 108, 52, 7, separator.concOut, false);

			this.drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 152, topPos + 18, 16, 61, separator.power, separator.maxPower);

			if(overSelector(mouseX, mouseY)) {
				GenericRecipe recipe = SaltSeparatorRecipes.INSTANCE.recipeNameMap.get(separator.module.getRecipeName());
				if(recipe != null) {
					GUIElements.drawHoveringTextRecipe(graphics, recipe.print(false), mouseX, mouseY, this.font, this.width, this.height);
				} else {
					drawInfo(graphics, mouseX, mouseY, ChatFormatting.YELLOW + I18nUtil.resolveKey("gui.recipe.setRecipe"));
				}
			}
		}

		this.renderTooltip(graphics, mouseX, mouseY);
	}

	/** Upstream's {@code checkClick(x, y, 7, 125, 18, 18)}, which is also its hover rectangle. */
	private boolean overSelector(double x, double y) {
		return checkClick(x, y, 7, 125, 18, 18);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		boolean handled = super.mouseClicked(x, y, button);

		if(separator != null && overSelector(x, y)) {
			GUIScreenRecipeSelector.openSelector(SaltSeparatorRecipes.INSTANCE, separator, separator.module.getRecipeName(), 0,
					ItemBlueprints.grabPool(menu.getSlot(1).getItem()), this);
			return true;
		}

		return handled;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(separator == null) return;

		int p = (int) (separator.power * 61 / separator.maxPower);
		graphics.blit(TEXTURE, leftPos + 152, topPos + 79 - p, 176, 61 - p, 16, p);

		if(separator.module.progress > 0) {
			int j = (int) Math.ceil(70 * separator.module.progress);
			graphics.blit(TEXTURE, leftPos + 62, topPos + 126, 176, 61, j, 16);
		}

		GenericRecipe recipe = SaltSeparatorRecipes.INSTANCE.recipeNameMap.get(separator.module.getRecipeName());

		/// LEFT LED
		if(separator.didProcess) {
			graphics.blit(TEXTURE, leftPos + 51, topPos + 121, 195, 0, 3, 6);
		} else if(recipe != null) {
			graphics.blit(TEXTURE, leftPos + 51, topPos + 121, 192, 0, 3, 6);
		}

		/// RIGHT LED
		if(separator.didProcess) {
			graphics.blit(TEXTURE, leftPos + 56, topPos + 121, 195, 0, 3, 6);
		} else if(recipe != null && separator.power >= recipe.power) {
			graphics.blit(TEXTURE, leftPos + 56, topPos + 121, 192, 0, 3, 6);
		}

		graphics.renderItem(recipe != null ? recipe.getIcon() : new ItemStack(ModItems.blueprints.get()), leftPos + 8, topPos + 126);

		for(int i = 0; i < 2; i++)
			GUIElements.drawFluidTank(graphics, separator.inputTanks[i], leftPos + 26 + i * 18, topPos + 52, 16, 34);
		for(int i = 0; i < 3; i++)
			GUIElements.drawFluidTank(graphics, separator.outputTanks[i], leftPos + 80 + i * 18, topPos + 52, 16, 34);

		// The Forge tanks: bottom edge, then size. The buffers are bars that fill rightward.
		LeafiaFFClient.drawTank(graphics, separator.saltTank, leftPos + 8, topPos + 88, 16, 70);
		LeafiaFFClient.drawTank(graphics, separator.bufferIn.getFluid(), separator.bufferIn.getCapacity(),
				leftPos + 8, topPos + 99 + 7, 52, 7, 1, false);
		LeafiaFFClient.drawTank(graphics, separator.bufferOut.getFluid(), separator.bufferOut.getCapacity(),
				leftPos + 80, topPos + 99 + 7, 52, 7, 1, false);

		GUIElements.drawFluidTank(graphics, separator.concIn, leftPos + 8, topPos + 108 + 7, 52, 7, 1);
		GUIElements.drawFluidTank(graphics, separator.concOut, leftPos + 80, topPos + 108 + 7, 52, 7, 1);
	}
}
