package com.leafia.dev.gui;

import com.leafia.init.LeafiaSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon's base for a screen that has no inventory: a centred panel with a background layer
 * drawn first and a foreground layer drawn last, in the panel's own coordinates.
 *
 * <p>Upstream: {@code com/leafia/dev/gui/GuiScreenLeafia.java}, a {@code GuiScreen} with
 * {@code guiLeft}/{@code guiTop}/{@code xSize}/{@code ySize} bolted on so it reads like a
 * {@code GuiContainer} without being one. 1.21.1's {@link Screen} is the same idea and the
 * fields are kept under upstream's names, because every subclass computes with them.
 *
 * <h2>What the 1.21.1 base does that upstream's did not</h2>
 *
 * <b>{@code Screen.render} draws a (blurred) background itself.</b> 1.12.2's {@code drawScreen}
 * drew nothing but the buttons, and a subclass called {@code drawDefaultBackground()} from its
 * own background layer when it wanted the world dimmed. Here the base's {@code render} would draw
 * that dim <em>after</em> the subclass's panel and bury it. So {@link #renderBackground} is
 * suppressed for exactly the span of the {@code super.render} call, and
 * {@link #drawDefaultBackground} is the way a subclass asks for it - which keeps upstream's
 * order (dim, panel, widgets, foreground) and its call sites.
 *
 * <p><b>A texture is a parameter now.</b> {@code drawTexturedModalRect} blitted whatever was
 * last bound; {@code GuiGraphics.blit} takes the texture, so
 * {@link #drawTexturedModalByFiaRect} does too. The 256 by 256 sheet it assumes is the same
 * one upstream's {@code drawTexturedModalRect} assumed.
 *
 * <p><b>The inventory key.</b> Upstream closes on Escape or on the inventory key, unless a text
 * field consumed the keystroke first. {@code Screen.keyPressed} handles Escape; the inventory
 * key is handled here, and an editable focused {@link EditBox} swallows it exactly as
 * {@code textboxKeyTyped} did, so typing an "e" into a field does not close the screen.
 *
 * <p><b>Sounds.</b> {@code PositionedSoundRecord.getMasterRecord(event, pitch)} plays at volume
 * 0.25 on the master category, which is what {@link SimpleSoundInstance#forUI} does.
 */
@OnlyIn(Dist.CLIENT)
public class GuiScreenLeafia extends Screen {

	public int xSize = 176;
	public int ySize = 166;
	public int guiLeft;
	public int guiTop;

	/** True while {@code Screen.render} runs, so its own call to {@link #renderBackground} is skipped. */
	private boolean drawingWidgets = false;

	public GuiScreenLeafia() {
		super(Component.empty());
	}

	protected void drawTexturedModalByFiaRect(GuiGraphics graphics, ResourceLocation texture, FiaUIRect rect, int tx, int ty) {
		graphics.blit(texture, rect.guiLeft + rect.x, rect.guiTop + rect.y, tx, ty, rect.w, rect.h);
	}

	/** Upstream's {@code playClick}: the vanilla button click at the given pitch. */
	protected void playClick(float pitch) {
		click(pitch);
	}

	/** Upstream's {@code playDenied}: the addon's own refusal sound, {@code ui.button.invalid}. */
	protected void playDenied() {
		denied();
	}

	/** The same two sounds for a screen that is not a {@link GuiScreenLeafia} (a container screen). */
	public static void click(float pitch) {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}

	public static void denied() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(LeafiaSounds.UI_BUTTON_INVALID.get(), 1));
	}

	@Override
	protected void init() {
		super.init();
		this.guiLeft = (this.width - this.xSize) / 2;
		this.guiTop = (this.height - this.ySize) / 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		drawGuiScreenBackgroundLayer(graphics, partialTick, mouseX, mouseY);

		drawingWidgets = true;
		try {
			super.render(graphics, mouseX, mouseY, partialTick);
		} finally {
			drawingWidgets = false;
		}

		graphics.pose().pushPose();
		graphics.pose().translate(guiLeft, guiTop, 0);
		drawGuiScreenForegroundLayer(graphics, mouseX, mouseY);
		graphics.pose().popPose();
	}

	/**
	 * Upstream's {@code drawDefaultBackground}: dims the world behind the panel.
	 *
	 * <p>The plain dim, not {@code Screen.renderBackground}: in 1.21.1 that blurs the world as
	 * well, which a 1.12.2 screen never did and which would smear whatever the player opened the
	 * screen to look at.
	 */
	protected void drawDefaultBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if(!drawingWidgets) super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}

	/** Drawn in the panel's coordinates, after the widgets. Upstream's {@code drawGuiScreenForegroundLayer}. */
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) { }

	/** Drawn first, in screen coordinates. Upstream's {@code drawGuiScreenBackgroundLayer}. */
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) { }

	/**
	 * Upstream's {@code keyTyped}: the inventory key closes the screen.
	 *
	 * <p>Escape is {@code Screen}'s. A focused, editable text field takes the key first - the
	 * same ordering upstream got from {@code textboxKeyTyped} returning true.
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

		if(super.keyPressed(keyCode, scanCode, modifiers)) return true;

		if(getFocused() instanceof EditBox box && box.canConsumeInput()) return true;

		if(this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			this.onClose();
			return true;
		}

		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
