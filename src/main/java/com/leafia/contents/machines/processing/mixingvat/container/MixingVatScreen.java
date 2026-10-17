package com.leafia.contents.machines.processing.mixingvat.container;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.processing.mixingvat.MixingVatTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The mixing vat's screen: the salt in and the salt out, a power bar, the batch's progress arrow and a small button that
 * voids the output.
 *
 * <p>Upstream: {@code .../mixingvat/container/MixingVatNclrGUI.java} - "Nclr" is its nuclear mode, the only one that ever
 * had a screen - on {@code LCEGuiInfoContainer}, 210 by 203 on {@code mixingvat_nuclear.png}. Every coordinate is upstream's.
 *
 * <ul>
 * <li>The tanks are 16 by 52, drawn up from y = 69 at x = 26 (input) and x = 134 (output).</li>
 * <li>The power bar is at (186, 18), 16 by 35, lit from the sheet's x = 210, with the lamp at (190, 4) once there is
 *     power for a tick of a batch.</li>
 * <li>The arrow grows from (70, 54), 16 tall, to 54 wide as the batch runs.</li>
 * <li>The void button is at (156, 39), 8 by 8; its tooltip reads "Void Contents", and a left click sends key 0.</li>
 * </ul>
 *
 * <h2>An upstream slip, kept: both tank tooltips show the output tank</h2>
 *
 * Upstream calls {@code renderTankInfo} twice with {@code vat.tankNc1} - once at the input's rectangle (offset by one
 * pixel to the right, 27 rather than 26) and once at the output's. So hovering the input tank names the <b>output</b>'s
 * contents and amount. Nothing is lost - the tank itself is drawn right, and a hover over a full input and an empty
 * output reads empty - but it misleads, and it is kept ({@link MixingVatTE#tooltipTank}) and flagged, not corrected (D-021).
 */
@OnlyIn(Dist.CLIENT)
public class MixingVatScreen extends LCEGuiInfoContainer<MixingVatMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/mixingvat.png");

	private final MixingVatTE vat;

	private FiaUIRect clear;

	public MixingVatScreen(MixingVatMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.vat = menu.vat;
		this.imageWidth = 210;
		this.imageHeight = 203;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	protected void init() {
		super.init();
		clear = new FiaUIRect(this, 156, 39, 8, 8);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(vat != null) {
			var ff = LeafiaFF.getFF(vat.inputTypeNc);
			FluidStack fallback = ff == null ? FluidStack.EMPTY : new FluidStack(ff, 1);

			FluidTank leftTank = vat.tooltipTank(true);
			FluidTank rightTank = vat.tooltipTank(false);
			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 45 - 18, topPos + 69 - 52, 16, 52,
					leftTank.getFluid(), leftTank.getCapacity(), fallback);
			LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 45 - 18 + 108, topPos + 69 - 52, 16, 52,
					rightTank.getFluid(), rightTank.getCapacity(), fallback);

			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 186, topPos + 18, 16, 35, vat.power, MixingVatTE.maxPower);

			if(clear.isMouseIn(mouseX, mouseY))
				drawFluidInfo(graphics, new String[] { I18nUtil.resolveKey("gui.desc.voidcontents") }, mouseX, mouseY);
		}

		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if(vat != null && button == 0 && clear.isMouseIn((int) mouseX, (int) mouseY)) {
			playClick(1);
			LeafiaPacket._start(vat).__write(0, true).__sendToServer();
			return true;
		}
		return handled;
	}

	/** Upstream's name centred on x = 88 (176 / 2) at y = 6, and the inventory label. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 176 / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(vat == null) return;

		int i = (int) vat.getPowerRemainingScaled(35);
		graphics.blit(TEXTURE, leftPos + 186, topPos + 53 - i, 210, 35 - i, 16, i);
		if(vat.power >= MixingVatTE.POWER_PER_TICK)
			graphics.blit(TEXTURE, leftPos + 190, topPos + 4, 210, 35, 9, 12);

		int j = (int) vat.getProgressScaled(54);
		graphics.blit(TEXTURE, leftPos + 70, topPos + 54, 0, 203, j, 16);

		LeafiaFFClient.drawTank(graphics, vat.tankNc0, leftPos + 44 - 18, topPos + 69, 16, 52);
		LeafiaFFClient.drawTank(graphics, vat.tankNc1, leftPos + 44 - 18 + 108, topPos + 69, 16, 52);
	}
}
