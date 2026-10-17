package com.leafia.contents.machines.controlpanel.core.client;

import org.lwjgl.glfw.GLFW;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlGeometry;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The editor's home page: the panel's grid with its controls on it, and the buttons that start every other page. Adapted from
 * NTM-CE's {@code SubElementPlacement} (CCP-EDITOR, D-3066).
 *
 * <p>Left click selects a control and, if you move the mouse with it held, picks it up; click again to put it down. Middle
 * click drags the grid and the wheel zooms about the mouse. Controls snap to a tenth of a grid unit unless Control is held. A
 * control goes down only where it is inside the slab and overlaps no other, and (not in CE) only while the panel has room for
 * it: the server refuses a panel of more than {@link ControlPanelGuard#MAX_CONTROLS}, and an edit it refuses is lost whole.
 *
 * <p>Keys: Control+C and Control+X copy and cut the selected control, Control+V pastes it under the mouse; Shift+A starts a new
 * control and Shift+D duplicates the selected one. The clipboard is the one static field and survives the screen, as in CE.
 *
 * <p>Differences from CE: the controls are drawn by {@link ControlPreview}, which batch 5 will make real; the "Security" and
 * "Connections" buttons stay dead as in CE, and "Variables" opens the variables page (D-3067). The grid texture is
 * drawn tiled, which needs the texture's default repeat wrap.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementPlacement extends SubElement {

	public static final ResourceLocation texture = GuiDraw.texture("gui_placement_front");
	public static final ResourceLocation grid = GuiDraw.texture("placement_grid");

	public Control selectedControl;
	public boolean controlGrabbed = false;

	public Button btn_panelResize;
	public Button btn_security;
	public Button btn_connections;
	public Button btn_variables;
	public Button btn_newControl;
	public Button btn_editControl;
	public Button btn_deleteControl;

	private boolean gridGrabbed = false;
	protected float gridX = 0;
	protected float gridY = 0;
	protected float gridScale = 0.1F;
	private float prevMouseX;
	private float prevMouseY;
	private final float[] renderBox = new float[4];

	private int ctrl_index = 0;
	/** Where a control I picked up stood, so closing the editor mid-drag puts it back instead of losing it. */
	private int grabbedIndex = -1;
	private float grabbedX, grabbedY;

	public static Control clipboard = null;

	public SubElementPlacement(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		int left = gui.getGuiLeft();
		int top = gui.getGuiTop();
		btn_panelResize = button(left + 7, top + 13, 43, 20, "Resize");
		btn_variables = button(left + 54, top + 13, 58, 20, "Variables");
		btn_security = button(left + 117, top + 13, 58, 20, "Security");
		btn_connections = button(left + 180, top + 13, 70, 20, "Connections");
		btn_newControl = button(left + 7, top + 47, 43, 20, "New");
		btn_editControl = button(left + 7, top + 69, 43, 20, "Edit");
		btn_deleteControl = button(left + 7, top + 91, 43, 20, "Delete");
		float[] cGrid = convertToGridSpace(cX + 10, cY + 20);
		gridX = -cGrid[0];
		gridY = cGrid[1];
		super.init();
	}

	public void resetPrevPos() {
		prevMouseX = gui.mouseX;
		prevMouseY = gui.mouseY;
	}

	/** If a control is in the hand, mid-drag from the panel, puts it back where it stood. Called as the editor closes. */
	public void restoreGrabbed() {
		if(gui.currentEditControl != null && !gui.isEditMode && grabbedIndex >= 0) {
			Control c = gui.currentEditControl;
			c.posX = grabbedX;
			c.posY = grabbedY;
			gui.panel.controls.add(Math.min(grabbedIndex, gui.panel.controls.size()), c);
			gui.currentEditControl = null;
			grabbedIndex = -1;
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dWheel = (float) gui.takeWheel();
		float dScale = dWheel * gridScale * 0.00075F;

		btn_editControl.active = isEditableControl(selectedControl);
		btn_deleteControl.active = selectedControl != null;

		// Correction so we scale around mouse position
		float prevX = (gui.mouseX - gui.getGuiLeft()) * gridScale;
		float prevY = (gui.mouseY - gui.getGuiTop()) * gridScale;
		gridScale = Mth.clamp(gridScale - dScale, 0.025F, 0.15F);
		float currentX = (gui.mouseX - gui.getGuiLeft()) * gridScale;
		float currentY = (gui.mouseY - gui.getGuiTop()) * gridScale;
		gridX += prevX - currentX;
		gridY -= prevY - currentY;

		if(gridGrabbed || gui.currentEditControl != null) {
			float dX = (gui.mouseX - prevMouseX) * gridScale;
			float dY = (gui.mouseY - prevMouseY) * gridScale;
			if(gridGrabbed) {
				gridX -= dX;
				gridY += dY;
			} else if(!gui.isEditMode) {
				gui.currentEditControl.posX += dX;
				gui.currentEditControl.posY += dY;
			}
		}
		if((prevMouseX != gui.mouseX || prevMouseY != gui.mouseY) && controlGrabbed && selectedControl != null) {
			ctrl_index = gui.panel.controls.indexOf(selectedControl);
			grabbedIndex = ctrl_index;
			grabbedX = selectedControl.posX;
			grabbedY = selectedControl.posY;
			gui.panel.controls.remove(selectedControl);
			gui.currentEditControl = selectedControl;
			selectedControl = null;
			controlGrabbed = false;
		}
		prevMouseX = gui.mouseX;
		prevMouseY = gui.mouseY;

		int cX = gui.width / 2;
		int cY = gui.height / 2;
		// CE's glScissor measured the y of this window from the bottom of the screen: this is the same window from the top.
		g.enableScissor(cX - 72, cY - 78, cX + 120, cY + 114);

		float baseSizeX = 1 - (gui.panel.b_off + gui.panel.d_off);
		float baseSizeY = 1 - (gui.panel.a_off + gui.panel.c_off);
		float panel_hyp = (float) (baseSizeY / Math.cos(Math.abs(gui.panel.angle)));

		float[] box = ControlGeometry.panelBox(gui.panel);
		float[] pos1 = convertFromGridSpace(box[0] - 0.03125F, box[1] - 0.03125F);
		float[] pos2 = convertFromGridSpace(box[2] + 0.03125F, box[3] + 0.03125F);

		float uScale = (baseSizeX * 10) / 4;
		float vScale = (panel_hyp * 10) / 4;
		GuiDraw.tiled(g, grid, pos1[0], pos1[1], pos2[0] - pos1[0], pos2[1] - pos1[1], uScale + 0.015625F, vScale + 0.015625F);

		// bottom,right lines to close the grid, cus somehow scaling the grid looks worse
		float line = 1 / (gridScale * 15);
		GuiDraw.rect(g, pos1[0], pos2[1] - line, pos2[0], pos2[1], 0xFF666666);
		GuiDraw.rect(g, pos2[0] - line, pos1[1], pos2[0], pos2[1], 0xFF666666);

		g.pose().pushPose();
		float spX = gui.getGuiLeft();
		float spY = gui.getGuiTop();
		g.pose().translate(spX, spY, 0);
		g.pose().scale(1 / gridScale, 1 / gridScale, 1 / gridScale);
		g.pose().translate(-spX, -spY, 0);
		g.pose().translate(-gridX, gridY, 0);
		renderItems(g);
		g.pose().popPose();
		g.disableScissor();

		if(selectedControl != null) {
			Font font = gui.font();
			g.drawString(font, "ID: " + gui.panel.controls.indexOf(selectedControl), gui.getGuiLeft() + 58, gui.getGuiTop() + 230, 0xFF333333, false);
		}
	}

	public void renderItems(GuiGraphics g) {
		for(Control c : gui.panel.controls)
			renderControl(g, c, false);
		if(gui.currentEditControl != null) {
			boolean ctrl = Screen.hasControlDown();
			float prevX = gui.currentEditControl.posX;
			float prevY = gui.currentEditControl.posY;

			if(!ctrl) {
				gui.currentEditControl.posX = (float) Math.round(prevX * 10) * .1F;
				gui.currentEditControl.posY = (float) Math.round(prevY * 10) * .1F;
			}

			boolean canPlace = canPlace();
			renderControl(g, gui.currentEditControl, !canPlace);
			if(!ctrl) {
				gui.currentEditControl.posX = prevX;
				gui.currentEditControl.posY = prevY;
			}
		}
	}

	/** One control in the current pose's space (the grid's, or the preview's). */
	public void renderControl(GuiGraphics g, Control c, boolean invalid) {
		ControlPreview.draw(g, c, renderBox, selectedControl, invalid);
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(texture, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	// ------------------------------------------------------------------ actions

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_panelResize) {
			gui.pushElement(gui.panelResize);
		} else if(button == btn_variables) {
			gui.currentEditControl = isEditableControl(selectedControl) ? selectedControl : null; // access to a selected control's local vars
			gui.variables.isGlobalScope = true;
			gui.pushElement(gui.variables);
		} else if(button == btn_newControl) {
			gui.isEditMode = false;
			gui.pushElement(gui.choice);
		} else if(button == btn_editControl) {
			if(isEditableControl(selectedControl)) {
				gui.currentEditControl = selectedControl;
				gui.itemConfig.last_control = null;
				gui.itemConfig.variants = ControlRegistry.getAllControlsOfType(gui.currentEditControl.getControlType());
				gui.pushElement(gui.itemConfig);
				gui.isEditMode = true;
				selectedControl = null;
			}
		} else if(button == btn_deleteControl) {
			gui.isEditMode = false;
			gui.panel.controls.remove(selectedControl);
			selectedControl = null;
		}
	}

	@Override
	public boolean keyPressed(int code, int scanCode, int modifiers) {
		if(Screen.hasControlDown()) {
			if(code == GLFW.GLFW_KEY_C || code == GLFW.GLFW_KEY_X) { // copy, cut
				if(isEditableControl(selectedControl)) {
					clipboard = duplicateControl(selectedControl);
					if(clipboard == null)
						return true;
					if(code == GLFW.GLFW_KEY_X) {
						gui.isEditMode = false;
						gui.panel.controls.remove(selectedControl);
						selectedControl = null;
					}
					return true;
				}
			} else if(code == GLFW.GLFW_KEY_V) {
				if(clipboard != null) {
					Control duplicate = duplicateControl(clipboard);
					if(duplicate == null)
						return true;
					holdUnderMouse(duplicate);
					return true;
				}
			}
		}
		if(Screen.hasShiftDown()) {
			if(gui.currentEditControl != null)
				return false;
			if(code == GLFW.GLFW_KEY_A) { // add new
				gui.isEditMode = false;
				gui.pushElement(gui.choice);
				return true;
			} else if(code == GLFW.GLFW_KEY_D && isEditableControl(selectedControl)) { // duplicate
				Control duplicate = duplicateControl(selectedControl);
				if(duplicate == null)
					return true;
				holdUnderMouse(duplicate);
				return true;
			}
		}
		return false;
	}

	private void holdUnderMouse(Control duplicate) {
		gui.currentEditControl = duplicate;
		float[] gridMouse = convertToGridSpace(gui.mouseX, gui.mouseY);
		duplicate.posX = gridMouse[0];
		duplicate.posY = gridMouse[1];
		resetPrevPos();
		controlGrabbed = false;
		selectedControl = null;
		grabbedIndex = -1;
	}

	private Control duplicateControl(Control source) {
		Control duplicate = gui.panel.cloneControl(source);
		return isEditableControl(duplicate) ? duplicate : null;
	}

	protected boolean canPlace() {
		if(gui.currentEditControl == null)
			return false;
		// A panel past the server's ceiling would be refused whole.
		if(!gui.panel.controls.contains(gui.currentEditControl) && gui.panel.controls.size() >= ControlPanelGuard.MAX_CONTROLS)
			return false;
		for(Control c : gui.panel.controls) {
			if(ControlGeometry.boxesOverlap(c.getBox(), gui.currentEditControl.getBox()))
				return false;
		}
		return ControlGeometry.boxContainsOther(ControlGeometry.panelBox(gui.panel), gui.currentEditControl.getBox());
	}

	// ------------------------------------------------------------------ the mouse

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float gridMX = (gui.mouseX - gui.getGuiLeft()) * gridScale + gui.getGuiLeft() + gridX;
		float gridMY = (gui.mouseY - gui.getGuiTop()) * gridScale + gui.getGuiTop() - gridY;
		if(button == 0) {
			if(gui.currentEditControl != null) {
				float prevX = gui.currentEditControl.posX;
				float prevY = gui.currentEditControl.posY;
				if(!Screen.hasControlDown()) {
					gui.currentEditControl.posX = (float) Math.round(prevX * 10) * .1F;
					gui.currentEditControl.posY = (float) Math.round(prevY * 10) * .1F;
				}
				if(canPlace()) {
					gui.panel.controls.add(gui.currentEditControl);
					gui.currentEditControl = null;
					grabbedIndex = -1;
				} else {
					gui.currentEditControl.posX = prevX;
					gui.currentEditControl.posY = prevY;
				}
			} else {
				for(Control c : gui.panel.controls) {
					if(ControlGeometry.intersects2DBox(gridMX, gridMY, c.getBox())) {
						selectedControl = c;
						controlGrabbed = isEditableControl(c);
						return true;
					}
				}
				selectedControl = null;
				controlGrabbed = false;
			}
		}
		if(button == 2) {
			gridGrabbed = true;
			prevMouseX = gui.mouseX;
			prevMouseY = gui.mouseY;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		if(state == 2)
			gridGrabbed = false;
		if(state == 0) {
			if(canPlace()) {
				if(!Screen.hasControlDown()) {
					gui.currentEditControl.posX = (float) Math.round(gui.currentEditControl.posX * 10) * .1F;
					gui.currentEditControl.posY = (float) Math.round(gui.currentEditControl.posY * 10) * .1F;
				}
				gui.panel.controls.add(Math.max(0, Math.min(ctrl_index, gui.panel.controls.size())), gui.currentEditControl);
				gui.currentEditControl = null;
				grabbedIndex = -1;
			}
			controlGrabbed = false;
		}
		return false;
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(btn_panelResize, enable);
		GuiDraw.show(btn_variables, enable);
		GuiDraw.show(btn_security, enable);
		btn_security.active = false;
		GuiDraw.show(btn_connections, enable);
		btn_connections.active = false;
		GuiDraw.show(btn_newControl, enable);
		GuiDraw.show(btn_editControl, enable);
		GuiDraw.show(btn_deleteControl, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}

	// ------------------------------------------------------------------ the grid's space

	/** A point on the screen to a point on the grid. */
	public float[] convertToGridSpace(float x, float y) {
		float gridMX = (x - gui.getGuiLeft()) * gridScale + gui.getGuiLeft() + gridX;
		float gridMY = (y - gui.getGuiTop()) * gridScale + gui.getGuiTop() - gridY;
		return new float[]{gridMX, gridMY};
	}

	/** A point on the grid to a point on the screen. */
	public float[] convertFromGridSpace(float gridMX, float gridMY) {
		float x = ((gridMX - gridX) - gui.getGuiLeft()) / gridScale + gui.getGuiLeft();
		float y = ((gridMY + gridY) - gui.getGuiTop()) / gridScale + gui.getGuiTop();
		return new float[]{x, y};
	}

	private static boolean isEditableControl(Control control) {
		return control != null && ControlRegistry.isRegistered(control.registryName) && !(control instanceof UnknownControl);
	}
}
