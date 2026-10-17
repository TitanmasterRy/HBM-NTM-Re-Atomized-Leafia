package com.leafia.contents.machines.powercores.dfc.core;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.config.LeafiaConfig;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.unsorted.BullshitInDavenportIowa;
import com.llib.math.SIPfx;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon core's screen: the two fuel tanks at the sides, four bars along the bottom (temperature,
 * stabilization, contained energy, expelling energy) and a dial for the potential gain.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/core/CoreGUI.java}, on its own sheet
 * {@code leafia:textures/gui/dfc/gui_core.png} (176 x 204). Every rectangle is upstream's:
 *
 * <ul>
 * <li>tanks at x = 8 and 152, 16 by 80, bottom at y = 88;</li>
 * <li>temperature at (7, 98), 70 by 4, from (0, 204), drawn as {@code temperature / meltingPoint} of its
 *     width; at or past the melting point the whole bar flashes between the strips at y = 224 and 228,
 *     four ticks each;</li>
 * <li>stabilization at (7, 102) from (0, 208), {@code (1 + sqrt(10 s) - 1) / 10} of 70;</li>
 * <li>contained and expelling energy at (84, 98) and (84, 102) from (0, 216) and (0, 220), each
 *     {@code energy / 100,000} of 70;</li>
 * <li>the dial's needle, a 5 by 12 strip at (176, 0), turned about (165, 102) at 2/5 scale by
 *     {@code min((potential - 1) / 9, maxDial)} of a turn, where {@code maxDial} jitters between 0.9
 *     and 0.98 on the client each tick, so a pegged dial shivers;</li>
 * <li>the core's glow at (70, 29), 36 square, from (220, 0) while both catalysts are in.</li>
 * </ul>
 *
 * <p>The title is right-aligned and the inventory label left, both on the line 96 above the bottom.
 * With {@code bullshitUnits} on the temperature is in "grille" and the expelling energy per "stevejob",
 * upstream's own joke units ({@code BullshitInDavenportIowa}).
 */
@OnlyIn(Dist.CLIENT)
public class CoreScreen extends GuiInfoContainer<CoreMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/dfc/gui_core.png");
	private final LeafiaCoreTE core;

	public CoreScreen(CoreMenu menu, Inventory invPlayer, Component title) {
		super(menu, invPlayer, title);
		this.core = menu.core;
		this.imageWidth = 176;
		this.imageHeight = 204;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		IMixinTileEntityCore mixin = core;
		boolean shift = hasShiftDown();
		GUIElements.drawFluidTankInfo(graphics, this, core.tanks[0], mouseX, mouseY, leftPos + 8, topPos + 7, 16, 80, shift);
		GUIElements.drawFluidTankInfo(graphics, this, core.tanks[1], mouseX, mouseY, leftPos + 152, topPos + 7, 16, 80, shift);

		String txt = String.format("%01.1f", mixin.getDFCTemperature()) + "°C" + "§8 / " + mixin.getDFCMeltingPoint();
		if(LeafiaConfig.bullshitUnits)
			txt = String.format("%01.1f", BullshitInDavenportIowa.CelsiusToG(mixin.getDFCTemperature())) + "grille" + "§8 / " + (int) BullshitInDavenportIowa.CelsiusToG(mixin.getDFCMeltingPoint());
		drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 7, topPos + 98, 70, 4, mouseX, mouseY,
				"Temperature: " + ((mixin.getDFCTemperature() >= IMixinTileEntityCore.failsafeLevel) ? "ERROR" : txt));
		drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 7, topPos + 102, 70, 4, mouseX, mouseY,
				"Stabilization: " + Math.round(mixin.getDFCStabilization() * 100) + "%");
		drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 84, topPos + 98, 70, 4, mouseX, mouseY,
				"Contained Energy: " + ((mixin.getDFCContainedEnergy() >= IMixinTileEntityCore.failsafeLevel) ? "ERROR" : SIPfx.formatNoSpace("%01.3f", mixin.getDFCContainedEnergy() * 1000, false) + "SPK"));
		if(!LeafiaConfig.bullshitUnits) {
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 84, topPos + 102, 70, 4, mouseX, mouseY,
					"Expelling Energy: " + String.format("%01.3f", mixin.getDFCExpellingEnergy() / 1000) + "MSPK/s");
		} else {
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 84, topPos + 102, 70, 4, mouseX, mouseY,
					"Expelling Energy: " + String.format("%01.3f", BullshitInDavenportIowa.PSToPSj(mixin.getDFCExpellingEnergy() / 1000)) + "MSPK/stevejob");
		}
		drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 161, topPos + 98, 8, 8, mouseX, mouseY,
				"Potential: " + Math.round(mixin.getDFCPotentialGain() * 100) + "%");

		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth - 8 - font.width(title), imageHeight - 96 + 2, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	/** The temperature bar's width in pixels, or -1 when it is at or past the melting point and flashes. */
	public static int temperatureWidth(double temperature, int meltingPoint) {
		if(temperature < meltingPoint) return (int) Mth.clampedLerp(0, 70, temperature / meltingPoint);
		return -1;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		IMixinTileEntityCore mixin = core;
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		int t = temperatureWidth(mixin.getDFCTemperature(), mixin.getDFCMeltingPoint());
		if(t >= 0)
			graphics.blit(TEXTURE, leftPos + 7, topPos + 98, 0, 204, t, 4);
		else
			graphics.blit(TEXTURE, leftPos + 7, topPos + 98, 0, 224 + 4 * Math.floorDiv(Math.floorMod(mixin.getDFCTicks(), 8), 4), 70, 4);
		graphics.blit(TEXTURE, leftPos + 7, topPos + 102, 0, 208,
				(int) Mth.clampedLerp(0, 70, (IMixinTileEntityCore.getStabilizationDiv(mixin.getDFCStabilization()) - 1) / 10), 4);
		graphics.blit(TEXTURE, leftPos + 84, topPos + 98, 0, 216,
				(int) Mth.clampedLerp(0, 70, mixin.getDFCContainedEnergy() / 100_000), 4); // 1MSPK ~ 1PSPK (= 5EHE)
		graphics.blit(TEXTURE, leftPos + 84, topPos + 102, 0, 220,
				(int) Mth.clampedLerp(0, 70, mixin.getDFCExpellingEnergy() / (100_000)), 4);

		graphics.pose().pushPose();
		graphics.pose().translate(leftPos + 165, topPos + 102, 0);
		graphics.pose().scale(2 / 5F, 2 / 5F, 2 / 5F);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.min((mixin.getDFCPotentialGain() - 1) / 9, mixin.getDFCClientMaxDial()) * 360));
		graphics.pose().translate(-2.5, -9.5, 0);
		graphics.blit(TEXTURE, 0, 0, 176, 0, 5, 12);
		graphics.pose().popPose();

		if(mixin.isDFCHasCore())
			graphics.blit(TEXTURE, leftPos + 70, topPos + 29, 220, 0, 36, 36);

		GUIElements.drawFluidTank(graphics, core.tanks[0], leftPos + 8, topPos + 88, 16, 80);
		GUIElements.drawFluidTank(graphics, core.tanks[1], leftPos + 152, topPos + 88, 16, 80);
	}
}
