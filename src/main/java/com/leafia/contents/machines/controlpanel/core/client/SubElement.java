package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One page of the panel editor: the placement grid, the control chooser, the configuration, the linker. Adapted from NTM-CE's
 * {@code SubElement} (CCP-EDITOR, D-3066). The screen keeps a stack of them and forwards everything to the top one; each owns
 * widgets it adds to the screen and shows or hides them in {@link #enableButtons}.
 *
 * <h2>How CE's hooks map</h2>
 * <ul>
 * <li>{@code initGui} is {@link #init}; {@code drawScreen} is {@link #render}; {@code updateScreen} is {@link #update} (called
 * once a tick); {@code keyTyped} is split into {@link #keyPressed} and {@link #charTyped}, because 1.21.1 delivers a key and the
 * character it typed separately.</li>
 * <li>A mouse button number is the same as before (0 left, 1 right, 2 middle). The wheel arrives as {@link #mouseScrolled}.</li>
 * <li>Methods were {@code protected} in CE and are {@code public} here: the five configuration panes live in another package
 * (they were in {@code controls.configs}) and the Leafia editor pages of batch 13 are in a third.</li>
 * </ul>
 *
 * <p>{@link #lock} is CE's: set on a page when it is pushed, cleared by the next click. A button press the click itself began
 * is not forwarded to a page that opened during it.
 */
@OnlyIn(Dist.CLIENT)
public class SubElement {

	public final ControlEditScreen gui;

	public boolean lock = false;

	/** The widgets this page added, so it can take them off the screen when it is thrown away. */
	private final List<AbstractWidget> owned = new ArrayList<>();

	public SubElement(ControlEditScreen gui) {
		this.gui = gui;
	}

	/** Called by a button this page made; only the top page hears it, as in CE. */
	public void actionPerformed(Button button) {}

	/** Builds this page's widgets. They start hidden. */
	public void init() {
		enableButtons(false);
	}

	/** Draws over the widgets and the slots, every frame, for the top page only. */
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

	/** Draws under the widgets, on top of the screen's base texture. */
	public void renderBackground(GuiGraphics g) {}

	public void enableButtons(boolean enable) {}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	public boolean charTyped(char codePoint, int modifiers) {
		return false;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	/** One notch is 1 up, -1 down. */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return false;
	}

	/** Once a tick. */
	public void update() {}

	/** The whole editor is closing. */
	public void onClose() {}

	public void onElementOpen() {}

	public void onElementClose() {}

	// ------------------------------------------------------------------ widgets

	/** Adds a widget to the screen and remembers it as this page's. */
	protected <T extends AbstractWidget> T add(T widget) {
		owned.add(widget);
		return gui.addWidget(widget);
	}

	/** A button whose press goes to the top page's {@link #actionPerformed}. */
	protected Button button(int x, int y, int width, int height, String label) {
		return add(gui.button(x, y, width, height, label));
	}

	/** Takes one widget this page added off the screen. */
	protected void remove(AbstractWidget widget) {
		owned.remove(widget);
		gui.removeWidgetFromScreen(widget);
	}

	/** Takes everything this page added off the screen. */
	public void removeWidgets() {
		for(AbstractWidget w : owned)
			gui.removeWidgetFromScreen(w);
		owned.clear();
	}
}
