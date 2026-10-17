package com.leafia.dev.gui;

import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.gui.GuiInfoContainer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon's base for a screen over a menu: the base mod's {@link GuiInfoContainer} plus the half
 * dozen helpers upstream's screens call that it does not have.
 *
 * <p>Upstream: {@code com/leafia/dev/gui/LCEGuiInfoContainer.java} ("ntmleafia: I want to explode"),
 * the base of every addon container screen, over {@code GuiContainerLeafia}.
 *
 * <h2>The decision this class records - for the twelve screens behind it</h2>
 *
 * <b>{@code GuiContainerLeafia} dissolves and this class is a thin adapter.</b> Upstream's two files
 * are 728 + 233 lines, and nearly all of it is one thing: a transcription of 1.12.2's
 * {@code GuiContainer} (slot drawing, drag-splitting, double-click gathering, hotbar keys) made so
 * that {@code hoveredSlot}, {@code drawSlot} and the click bookkeeping could be reached and hooked,
 * because the originals were private. In 1.21.1 {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}
 * exposes what that was for: {@code hoveredSlot} is protected, and {@code renderSlot} and
 * {@code renderSlotHighlight} are protected overridable methods. A copy of the class would be a
 * second implementation of drag-splitting that disagrees with vanilla's on every update, and the
 * base mod's {@link GuiInfoContainer} has already decided the same thing for its own screens
 * (the boiler's and the cabin's screens needed none of the rest, D-1054, D-1077).
 *
 * <p>What survives is what screens actually <em>call</em>, measured against the thirteen screens upstream that
 * extend it rather than against what the class defines:
 *
 * <ul>
 * <li>{@link #clickDown}: whether the mouse button is held, which only the fuzzy identifier's click
 *     on a fluid tank reads ({@code LeafiaClientUtil}). Set on press, cleared on release, as upstream.</li>
 * <li>{@link #drawTexturedModalByFiaRect}: a {@link FiaUIRect} blitted from a texture sheet.</li>
 * <li>{@link #drawCustomInfo} and {@link #drawFluidInfo}: a tooltip at the mouse, over a rectangle
 *     or unconditionally. The first is the AMS and DFC
 *     screens' idiom, and the base mod's {@code GuiInfoContainer} has no such method.</li>
 * <li>{@link #playClick} and {@link #playDenied}: the two sounds, as on {@link GuiScreenLeafia}.</li>
 * </ul>
 *
 * <p>What does <b>not</b> survive, each with the reason:
 *
 * <ul>
 * <li>{@code drawElectricityInfo(gui, ...)}, {@code drawCustomInfoStat}, {@code drawInfoPanel},
 *     {@code isMouseOverSlot}: all on {@link GuiInfoContainer} already. The {@code gui} argument
 *     upstream passes is the {@code BullshitMixinGuiInfoContainer} artefact (the "corn" unit
 *     swap, WIP and off by default upstream); a screen drops it, and the unit swap is the cosmetic
 *     seam M6 names, not an override here. The base's tooltip is {@code getShortNumber} + "HE".</li>
 * <li>{@code drawStackText}: 90 lines of immediate-mode GL for a tooltip that mixes strings and item
 *     stacks, and <b>no screen anywhere calls it</b>.</li>
 * <li>{@code preDrawSlot}, {@code postDrawSlot}, {@code detectSlotHover}, {@code drawSlots},
 *     {@code drawSlotHighlight}, {@code superDrawScreen}, {@code superMouseClicked}: the hooks into
 *     the transcription. Only the PWR terminal overrides any, to draw its scaled remote slots, and
 *     it does so with {@code renderSlot}/{@code renderSlotHighlight} on a pose scale and a second
 *     pass that sets {@code hoveredSlot} (1.21.1's {@code isHovering(Slot,...)} is private).
 *     That is the PWR batch's, with the shape recorded in D-3000.</li>
 * </ul>
 *
 * <h2>Differences a porting screen meets</h2>
 *
 * <ul>
 * <li>Every drawing call takes a {@link GuiGraphics} first, as everywhere in 1.21.1.</li>
 * <li>The tooltips are the base mod's, drawn as strings through {@code GUIElements.drawHoveringText}
 *     rather than through vanilla's component tooltip, for the reason {@link GuiInfoContainer} states.</li>
 * <li>{@code xSize}/{@code ySize}/{@code guiLeft}/{@code guiTop} are {@code imageWidth},
 *     {@code imageHeight}, {@code leftPos}, {@code topPos}. {@link FiaUIRect} already follows them.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public abstract class LCEGuiInfoContainer<T extends AbstractContainerMenu> extends GuiInfoContainer<T> {

	/** True while a mouse button is held over this screen: set in {@code mouseClicked}, cleared in {@code mouseReleased}. */
	public boolean clickDown = false;

	public LCEGuiInfoContainer(T menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	/** Upstream's {@code playClick}: the vanilla button click at the given pitch. */
	protected void playClick(float pitch) {
		GuiScreenLeafia.click(pitch);
	}

	/** Upstream's {@code playDenied}: the addon's own refusal sound. */
	protected void playDenied() {
		GuiScreenLeafia.denied();
	}

	/** Upstream's {@code drawTexturedModalByFiaRect}; the sheet is a parameter now, as on {@link GuiScreenLeafia}. */
	protected void drawTexturedModalByFiaRect(GuiGraphics graphics, ResourceLocation texture, FiaUIRect rect, int tx, int ty) {
		graphics.blit(texture, rect.guiLeft + rect.x, rect.guiTop + rect.y, tx, ty, rect.w, rect.h);
	}

	/** Upstream's {@code drawFluidInfo}: a tooltip box at a point, whatever the text is about. */
	public void drawFluidInfo(GuiGraphics graphics, String[] text, int x, int y) {
		drawHoveringText(graphics, Arrays.asList(text), x, y);
	}

	public void drawFluidInfo(GuiGraphics graphics, List<String> text, int x, int y) {
		drawHoveringText(graphics, text, x, y);
	}

	/**
	 * Upstream's {@code drawCustomInfo}: a tooltip at the mouse while it is over a rectangle. The same
	 * half-open test as {@link GuiInfoContainer#drawCustomInfoStat} - on x the left edge is in and the
	 * right is out, on y the top is out and the bottom is in - because it is that method with the
	 * tooltip pinned to the cursor.
	 */
	public void drawCustomInfo(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, String... text) {
		drawCustomInfoStat(graphics, mouseX, mouseY, x, y, width, height, mouseX, mouseY, text);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		clickDown = true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		clickDown = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
