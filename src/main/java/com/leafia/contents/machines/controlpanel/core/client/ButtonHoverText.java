package com.leafia.contents.machines.controlpanel.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A button that reads something else while the mouse is over it ("&lt;Click to edit&gt;" on the event lists). Adapted from
 * NTM-CE's {@code ButtonHoverText} (CCP-EDITOR, D-3066); the event editor of batch 7 is its only user.
 */
@OnlyIn(Dist.CLIENT)
public class ButtonHoverText extends Button {

	public String hoverText;
	private final Component normal;

	public ButtonHoverText(int x, int y, int width, int height, String text, String hoverText, OnPress onPress) {
		super(x, y, width, height, Component.literal(text), onPress, DEFAULT_NARRATION);
		this.normal = Component.literal(text);
		this.hoverText = hoverText;
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		setMessage(isHoveredOrFocused() && isMouseOver(mouseX, mouseY) ? Component.literal(hoverText) : normal);
		super.renderWidget(g, mouseX, mouseY, partialTick);
		setMessage(normal);
	}
}
