package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.controls.DisplayText;
import com.leafia.contents.machines.controlpanel.core.controls.Label;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * How the editor draws one control on its flat grid, and the seam batch 5 filled in (CCP-EDITOR, D-3066; CCP-DRAW, D-3065). CE's
 * {@code Control.renderControl(renderBox, selected, gui)} drew each control's own picture; those belong to the drawing batch
 * (CCP-DRAW, D-3065), so this batch draws <b>a labelled box</b> and leaves {@link #drawer} for batch 5 to replace.
 *
 * <p>The drawer is called with the pose already in the grid's space (a control's box is in grid units, ten to a block), the
 * box from {@link Control#fillBox}, the currently selected control (to highlight) and whether the control is being dragged
 * over a place it may not go (CE tinted it red). It must not keep anything: it is called every frame, for every control.
 *
 * <p>{@link #measure} is the other half of the hand-off in D-3064: {@code DisplayText} and {@code Label} cannot know their text's
 * width and height on a server, and their selection box depends on them, so whoever draws them has to report it. It is done here,
 * before the box is asked for, so a text control is the right size to grab and to place whatever the drawer is.
 */
@OnlyIn(Dist.CLIENT)
public final class ControlPreview {

	private ControlPreview() {}

	@FunctionalInterface
	public interface Drawer {
		void draw(GuiGraphics g, Control control, float[] box, Control selected, boolean invalid);
	}

	/** Draws the real control through batch 5's renderers (D-3065); {@link #placeholder} stays as the fallback for a control with none. */
	public static Drawer drawer = ControlPreview::real;

	/** Reports a text control's measured size from the font, as CE's {@code render} stored it. */
	public static void measure(Control c) {
		Font font = Minecraft.getInstance().font;
		if(c instanceof DisplayText t)
			t.setMeasured(font.width(t.getVar("text").toString()), font.lineHeight);
		else if(c instanceof Label l)
			l.setMeasured(font.width(l.getText()), font.lineHeight);
	}

	/** Draws a control in the current pose's space, measuring it first. {@code box} is filled with its box. */
	public static void draw(GuiGraphics g, Control c, float[] box, Control selected, boolean invalid) {
		measure(c);
		c.fillBox(box);
		drawer.draw(g, c, box, selected, invalid);
	}

	/**
	 * The real picture: the control's own world renderer, laid flat into the grid. The panel's frame is x across, y out of the
	 * panel, z down it; the grid's is x across, y down, z toward the viewer, so y and z swap (a reflection, as the grid's y-down
	 * already is) and the depth is squashed so a tall control cannot leave the GUI's depth range. A control with no renderer, or
	 * one that threw, falls back to the labelled box; the outline marks the selection and an invalid placement tints red.
	 */
	public static void real(GuiGraphics g, Control c, float[] box, Control selected, boolean invalid) {
		if(ControlRenderers.find(c) == null) {
			placeholder(g, c, box, selected, invalid);
			return;
		}
		g.pose().pushPose();
		g.pose().mulPose(new Matrix4f(
			1, 0, 0, 0,
			0, 0, 0.01F, 0,
			0, 1, 0, 0,
			0, 0, 0, 1));
		ControlPanelRender.drawControl(c, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT, true);
		g.pose().popPose();
		g.flush();
		float t = 0.04F;
		if(invalid)
			GuiDraw.outline(g, box[0], box[1], box[2], box[3], t, 0xFFFF3030);
		else if(c == selected)
			GuiDraw.outline(g, box[0], box[1], box[2], box[3], t, 0xFFFFFFFF);
	}

	/** The stand-in: a dark box in the control's colour family, outlined, with its name written small inside. */
	public static void placeholder(GuiGraphics g, Control c, float[] box, Control selected, boolean invalid) {
		float w = box[2] - box[0];
		float h = box[3] - box[1];
		float t = Math.min(0.06F, Math.min(w, h) / 4F);

		int fill = c instanceof UnknownControl ? 0xFF553333 : 0xFF2E3338;
		if(invalid)
			fill = 0xFF8A2A2A;
		GuiDraw.rect(g, box[0], box[1], box[2], box[3], fill);
		GuiDraw.outline(g, box[0], box[1], box[2], box[3], t, c == selected ? 0xFFFFFFFF : 0xFF7A8088);

		Font font = Minecraft.getInstance().font;
		String name = c.name;
		float tw = Math.max(1, font.width(name));
		float s = Math.min((w - 2 * t) / tw, (h - 2 * t) / font.lineHeight) * 0.9F;
		if(s <= 0)
			return;
		g.pose().pushPose();
		g.pose().translate(box[0] + w / 2F, box[1] + h / 2F, 0);
		g.pose().scale(s, s, 1);
		g.drawString(font, name, -font.width(name) / 2F, -font.lineHeight / 2F, 0xFFCCCCCC, false);
		g.pose().popPose();
	}
}
