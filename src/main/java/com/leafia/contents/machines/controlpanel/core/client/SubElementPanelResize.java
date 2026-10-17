package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.ControlPanel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The slab's shape: four edge insets, a height and an angle, with a top view and a side view that redraw as the sliders move.
 * Adapted from NTM-CE's {@code SubElementPanelResize} (CCP-EDITOR, D-3066).
 *
 * <p>A shape is taken only when it is one the server will accept ({@link ControlPanel#geometryValid}, the rule CE's "lazy" test
 * was and which D-3063 made the server's too); otherwise every slider snaps back. CE read the sliders on <i>every</i> mouse
 * release anywhere on the page, which rounded the angle to a whole degree even when nobody touched it (the front panel's 11.31
 * degrees became 11 on clicking Back). Here the angle is taken from its slider only when the slider's whole-degree value
 * differs from the panel's.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementPanelResize extends SubElement {

	public static final ResourceLocation bg_tex = GuiDraw.texture("gui_base");
	public static final ResourceLocation black_shit = GuiDraw.texture("gui_resize_grid");
	public static final ResourceLocation smol_gridz = GuiDraw.texture("resize_grids");

	private static final int RED = GuiDraw.argb(1F, .2F, .2F, 1F);
	private static final int GREEN = GuiDraw.argb(.2F, 1F, .2F, 1F);
	private static final int WHITE = 0xFFFFFFFF;

	Button back;
	ControlSlider a_offset;
	ControlSlider b_offset;
	ControlSlider c_offset;
	ControlSlider d_offset;
	ControlSlider panel_height;
	ControlSlider panel_angle;

	public SubElementPanelResize(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		ControlPanel p = gui.panel;
		int top = gui.getGuiTop();

		back = button(gui.getGuiLeft() + 7, top + 13, 30, 20, "Back");
		a_offset = add(new ControlSlider(cX + 18, top + 47, 80, 15, "A ", 0, 100, p.a_off * 100));
		b_offset = add(new ControlSlider(cX + 18, top + 47 + 20, 80, 15, "B ", 0, 100, p.b_off * 100));
		c_offset = add(new ControlSlider(cX + 18, top + 47 + 40, 80, 15, "C ", 0, 100, p.c_off * 100));
		d_offset = add(new ControlSlider(cX + 18, top + 47 + 60, 80, 15, "D ", 0, 100, p.d_off * 100));
		panel_height = add(new ControlSlider(cX + 18, 194, 80, 15, "Height ", 0, 100, p.height * 100));
		panel_angle = add(new ControlSlider(cX + 18, 214, 80, 15, "Angle ", -45, 45, Math.toDegrees(p.angle)));

		super.init();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		ControlPanel p = gui.panel;
		int minX = gui.getGuiLeft() + 38;
		int maxX = gui.getGuiLeft() + 111;
		int minY = gui.getGuiTop() + 56;
		int maxY = gui.getGuiTop() + 129;
		int minY_2 = gui.getGuiTop() + 146 - 1;
		int maxY_2 = gui.getGuiTop() + 219;

		int box_len = maxX - minX;
		int height0 = (int) (box_len * ControlPanel.getSlopeHeightFromZ(p.a_off, p.height, -p.angle) - 1);
		int height1 = (int) (box_len * ControlPanel.getSlopeHeightFromZ(1 - p.c_off, p.height, -p.angle) - 1);

		// top view
		GuiDraw.rect(g, minX + p.d_off * box_len, minY + p.a_off * box_len, maxX - p.b_off * box_len, minY + p.a_off * box_len + 1, RED);
		GuiDraw.rect(g, maxX - p.b_off * box_len - 1, minY + p.a_off * box_len, maxX - p.b_off * box_len, maxY - p.c_off * box_len, WHITE);
		GuiDraw.rect(g, minX + p.d_off * box_len, maxY - p.c_off * box_len - 1, maxX - p.b_off * box_len, maxY - p.c_off * box_len, GREEN);
		GuiDraw.rect(g, minX + p.d_off * box_len, minY + p.a_off * box_len, minX + p.d_off * box_len + 1, maxY - p.c_off * box_len, WHITE);
		// side view
		GuiDraw.rect(g, maxX - p.c_off * box_len - 1, minY_2 + (box_len - height1), maxX - p.c_off * box_len, minY_2 + (box_len - height1) + height1, GREEN);
		GuiDraw.rect(g, minX + p.a_off * box_len, maxY_2 - 1, maxX - p.c_off * box_len, maxY_2, WHITE);
		GuiDraw.rect(g, minX + p.a_off * box_len, minY_2 + (box_len - height0), minX + p.a_off * box_len + 1, minY_2 + (box_len - height0) + height0, RED);
		// side view slope
		GuiDraw.line(g, minX + p.a_off * box_len, minY_2 + (box_len - height0), maxX - p.c_off * box_len, minY_2 + (box_len - height1), 3, WHITE);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == back)
			gui.popElement();
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		ControlPanel p = gui.panel;
		float a_off = (float) a_offset.fraction();
		float b_off = (float) b_offset.fraction();
		float c_off = (float) c_offset.fraction();
		float d_off = (float) d_offset.fraction();
		float height = (float) panel_height.fraction();
		boolean angleMoved = panel_angle.getValueInt() != Math.round(Math.toDegrees(p.angle));
		float angle = angleMoved ? (float) Math.toRadians(panel_angle.getValueInt()) : p.angle;

		boolean changed = Math.abs(a_off - p.a_off) > 1e-6F || Math.abs(b_off - p.b_off) > 1e-6F || Math.abs(c_off - p.c_off) > 1e-6F
				|| Math.abs(d_off - p.d_off) > 1e-6F || Math.abs(height - p.height) > 1e-6F || angleMoved;
		if(!changed)
			return false;

		if(ControlPanel.geometryValid(height, angle, a_off, b_off, c_off, d_off)) {
			p.a_off = a_off;
			p.b_off = b_off;
			p.c_off = c_off;
			p.d_off = d_off;
			p.height = height;
			p.angle = angle;
		} else {
			a_offset.setValue(p.a_off * 100);
			b_offset.setValue(p.b_off * 100);
			c_offset.setValue(p.c_off * 100);
			d_offset.setValue(p.d_off * 100);
			panel_height.setValue(p.height * 100);
			panel_angle.setValue(Math.toDegrees(p.angle));
		}
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(bg_tex, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
		g.blit(black_shit, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, 120, gui.getYSize());
		g.blit(smol_gridz, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(back, enable);
		GuiDraw.show(a_offset, enable);
		GuiDraw.show(b_offset, enable);
		GuiDraw.show(c_offset, enable);
		GuiDraw.show(d_offset, enable);
		GuiDraw.show(panel_height, enable);
		GuiDraw.show(panel_angle, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}
}
