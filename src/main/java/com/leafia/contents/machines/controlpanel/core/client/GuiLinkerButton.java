package com.leafia.contents.machines.controlpanel.core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One row of the linker page: a text box holding the link's tag and, at its right, the "X" that removes the link. Adapted from
 * NTM-CE's {@code GuiLinkerButton} (CCP-EDITOR, D-3066), which was a button that also drew a text field; here the button is the
 * "X" (20 wide) and {@link #field} is its own widget beside it, because the screen routes focus and typing to a text box that is
 * a child of the screen. The screen adds both.
 */
@OnlyIn(Dist.CLIENT)
public class GuiLinkerButton extends Button {

	public final int actualX;
	public final int actualWidth;
	public final EditBox field;

	public GuiLinkerButton(Font font, int x, int y, int width, int height, String tag, OnPress onPress) {
		super(x + width - 20, y, 20, height, Component.literal("X"), onPress, DEFAULT_NARRATION);
		this.actualX = x;
		this.actualWidth = width;
		this.field = new EditBox(font, x + 1, y + 1, width - 20 - 2, height - 2, Component.literal("Tag"));
		this.field.setMaxLength(32); // GuiTextField's default
		this.field.setValue(tag);
	}

	public void setShown(boolean shown) {
		GuiDraw.show(this, shown);
		GuiDraw.show(field, shown);
	}
}
