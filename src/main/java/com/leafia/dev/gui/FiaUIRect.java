package com.leafia.dev.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A rectangle on a screen, remembered relative to the panel it belongs to.
 *
 * <p>Upstream: {@code com/leafia/dev/gui/FiaUIRect.java}. It is a rectangle with an
 * {@code isMouseIn}, and the only things that change on the way to 1.21.1 are the two screen
 * types it knows how to follow: 1.12.2's {@code GuiContainer} becomes
 * {@link AbstractContainerScreen} (its {@code guiLeft}/{@code guiTop} are
 * {@code getGuiLeft()}/{@code getGuiTop()}), and {@link GuiScreenLeafia} keeps upstream's own
 * fields. Every coordinate and every comparison is upstream's, <b>including the half-open
 * bounds</b> of {@link #isMouseIn}: the left and top edges are inside, the right and bottom are
 * not. (Not every screen that uses a rectangle agrees with it — {@code EvFloorGUI} tests its
 * button with inclusive bounds on both ends, and keeps doing so rather than reading this class.)
 *
 * <p>Not annotated client-only, although two overloads name client screen types. The JVM
 * resolves those lazily - nothing here loads {@link AbstractContainerScreen} or
 * {@link GuiScreenLeafia} until one of the two is actually called - so a dedicated server can
 * load this class and the arithmetic can be pinned by a GameTest, which a screen's cannot.
 */
public class FiaUIRect {

	public int guiLeft;
	public int guiTop;
	public int x;
	public int y;
	public int w;
	public int h;

	public FiaUIRect(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public FiaUIRect(AbstractContainerScreen<?> gui, int x, int y, int w, int h) {
		this(x, y, w, h);
		updateGuiPosition(gui);
	}

	public FiaUIRect updateGuiPosition(AbstractContainerScreen<?> gui) {
		guiLeft = gui.getGuiLeft();
		guiTop = gui.getGuiTop();
		return this;
	}

	public FiaUIRect(GuiScreenLeafia gui, int x, int y, int w, int h) {
		this(x, y, w, h);
		updateGuiPosition(gui);
	}

	public FiaUIRect updateGuiPosition(GuiScreenLeafia gui) {
		guiLeft = gui.guiLeft;
		guiTop = gui.guiTop;
		return this;
	}

	public FiaUIRect updateGuiPosition(int guiLeft, int guiTop) {
		this.guiLeft = guiLeft;
		this.guiTop = guiTop;
		return this;
	}

	public boolean isMouseIn(int mouseX, int mouseY) {
		return guiLeft + x <= mouseX && mouseX < guiLeft + x + w && guiTop + y <= mouseY && mouseY < guiTop + y + h;
	}

	public int absX() {
		return guiLeft + x;
	}

	public int absY() {
		return guiTop + y;
	}
}
