package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlGeometry;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.NodeClipboard;
import com.leafia.contents.machines.controlpanel.core.NodeElement;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.NodeSystemEditor;
import com.leafia.contents.machines.controlpanel.core.modular.NodeAddMenu;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The node canvas: one event's graph (or a function node's body under it), panned with the middle mouse button, zoomed with the
 * wheel about the cursor, edited with the mouse and a few keys. Adapted from NTM-CE's {@code SubElementNodeEditor}
 * (CCP-NODE-EDITOR, D-3067) with Leafia's {@code MixinSubElementNodeEditor} folded in.
 *
 * <h2>Where the work is</h2>
 * What a click, a release and a key do to the graph is {@link NodeSystemEditor} and {@link NodeClipboard} (common, and run by
 * GameTests); the menu of nodes is {@link NodeAddMenu}; the picture is {@link NodeDraw}. This page turns the mouse and keyboard
 * into those calls and keeps the pan, the zoom and the menu. It edits the graphs of {@code gui.currentEditControl}, a control of
 * the editor's scratch panel (D-3066), so the edit is saved by the same path as every other.
 *
 * <h2>Keys</h2>
 * Shift+A opens the add menu; Delete or X deletes the selection; Ctrl+C or Ctrl+X copies it (CE's cut does not remove it, and
 * that is kept); Ctrl+V pastes at the mouse. While a box is being typed into, every key is the box's: characters are typed,
 * Backspace deletes, Enter commits, and a click elsewhere commits too.
 *
 * <h2>The mixin</h2>
 * Leafia's mixin redirected the click on a node's button so that a button called "Edit Code" pushes the IC10 editor page. That is
 * the "Edit Code" entry of {@link #BUTTON_ACTIONS}, which {@code IC10Client} registers (CCP-IC10, D-3073) together with "Reset State";
 * a button with no registered action does nothing. "Edit Body", a function node's button, descends into its body.
 *
 * <h2>Differences from CE</h2>
 * A click outside the canvas' window is not a click on the canvas (CE let a click on the buttons beside it reach nodes hidden
 * behind them); the add menu is drawn over the canvas and is not cut off by its edge; the page names a graph the server would
 * refuse (a cycle, a loop of broadcasts: {@link ControlPanelGuard#graphProblem}) in red under the canvas; a wire that would
 * make a cycle is not made ({@link NodeSystemEditor}); empty event graphs are not saved ({@link NodeSystemEditor#pruneEmptySystems}).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementNodeEditor extends SubElement {

	public static final ResourceLocation texture = GuiDraw.texture("gui_placement_front");
	public static final ResourceLocation grid = GuiDraw.texture("grid");

	/** What pressing a node's button does, by the button's name. A node's own sub-pages register here (CCP-IC10, batch 10). */
	@FunctionalInterface
	public interface ButtonAction {
		void run(SubElementNodeEditor editor, NodeElement button);
	}

	public static final Map<String, ButtonAction> BUTTON_ACTIONS = new HashMap<>();

	static {
		BUTTON_ACTIONS.put("Edit Body", (editor, button) -> {
			if(button.parent instanceof NodeFunction f && f.editBody == button)
				editor.descendSubsystem(button.parent);
		});
		// Leafia's MixinSubElementNodeEditor: "Edit Code" and "Reset State" belong to the IC10 node, and IC10Client registers them
		// (CCP-IC10, D-3073); this page names no addon node.
	}

	/** The clipboard outlives the page and the screen, as CE's did (it was static). Client memory only. */
	private static CompoundTag clipboardNodes;

	/** The canvas' window in the screen, from the centre: x from -72 to 120, y from -78 to 114. */
	private static final int WIN_L = -72, WIN_T = -78, WIN_R = 120, WIN_B = 114;

	public Button btn_back;
	public Button btn_variables;

	public ItemList addMenu;

	/** What the add menu's creators read (CE: the page itself). */
	public final NodeEditContext context = new NodeEditContext();
	public NodeSystemEditor current;
	public final Deque<NodeSystemEditor> systemHistoryStack = new ArrayDeque<>();
	private Control editedControl;

	public boolean gridGrabbed = false;
	public float gridX = 0;
	public float gridY = 0;
	public float gridScale = 1;
	public float prevMouseX;
	public float prevMouseY;

	/** Why the server would refuse the control as it stands, or null; refreshed every ten ticks. */
	private String problem;
	private int problemTicks = 0;

	public SubElementNodeEditor(ControlEditScreen gui) {
		super(gui);
	}

	/** Opens the graph of one event of the control being edited, making it if the control has none yet. */
	public void setData(Map<String, NodeSystem> map, ControlEvent c, List<ControlEvent> sendEvents) {
		editedControl = gui.currentEditControl;
		NodeSystem sys = map.computeIfAbsent(c.name, e -> new NodeSystem(editedControl));
		systemHistoryStack.clear();
		current = new NodeSystemEditor(sys);
		context.currentSystem = sys;
		context.currentEvent = c;
		context.sendEvents = sendEvents;
		addMenu = null;
		gridX = 0;
		gridY = 0;
		gridScale = 1;
		gridGrabbed = false;
		problem = null;
		problemTicks = 0;
	}

	/** Goes into a function node's body. */
	public void descendSubsystem(Node node) {
		NodeSystem sub = current.system.subSystems.get(node);
		if(sub == null) { // a function node that arrived without a body (a hand-made panel): give it one
			sub = new NodeSystem(current.system.parent);
			current.system.subSystems.put(node, sub);
		}
		systemHistoryStack.push(current);
		current = new NodeSystemEditor(sub);
		context.currentSystem = sub;
		addMenu = null;
	}

	@Override
	public void init() {
		btn_back = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 13, 30, 20, "Back");
		btn_variables = button(gui.getGuiLeft() + 54, gui.getGuiTop() + 13, 58, 20, "Variables");
		super.init();
	}

	// ------------------------------------------------------------------ the grid's space

	/** A point on the screen to a point in node space. */
	public float[] toGrid(float x, float y) {
		return new float[]{(x - gui.getGuiLeft()) * gridScale + gui.getGuiLeft() + gridX, (y - gui.getGuiTop()) * gridScale + gui.getGuiTop() - gridY};
	}

	private boolean inCanvas(float x, float y) {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		return x >= cX + WIN_L && x <= cX + WIN_R && y >= cY + WIN_T && y <= cY + WIN_B;
	}

	// ------------------------------------------------------------------ keys

	@Override
	public boolean keyPressed(int code, int scanCode, int modifiers) {
		if(current == null)
			return false;
		if(current.isTyping()) {
			if(code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER)
				current.enter();
			else if(code == GLFW.GLFW_KEY_BACKSPACE)
				current.backspace();
			return true; // every key is the box's while it types
		}
		boolean ctrl = Screen.hasControlDown();
		if(ctrl) {
			if(code == GLFW.GLFW_KEY_C || code == GLFW.GLFW_KEY_X) {
				copySelectionToClipboard();
				return true;
			}
			if(code == GLFW.GLFW_KEY_V) {
				if(canPasteClipboard()) {
					float[] at = toGrid(gui.mouseX, gui.mouseY);
					pasteClipboard(at[0], at[1]);
				}
				return true;
			}
		}
		if(code == GLFW.GLFW_KEY_A && Screen.hasShiftDown()) {
			openAddMenu();
			return true;
		}
		if(code == GLFW.GLFW_KEY_DELETE || code == GLFW.GLFW_KEY_X) {
			current.removeSelected();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		return current != null && current.typeChar(c);
	}

	private void openAddMenu() {
		if(addMenu != null)
			addMenu.close();
		addMenu = NodeAddMenu.build(context, gui.mouseX, gui.mouseY, () -> toGrid(gui.mouseX, gui.mouseY), canPasteClipboard(),
				at -> pasteClipboard(at[0], at[1]),
				node -> {
					current.addNode(node);
				},
				() -> {
					if(addMenu != null)
						addMenu.close();
					addMenu = null;
				});
	}

	private static boolean hasClipboardData() {
		return clipboardNodes != null && !clipboardNodes.isEmpty();
	}

	private boolean canPasteClipboard() {
		return current != null && context.currentEvent != null && hasClipboardData()
				&& NodeClipboard.canPaste(current.system, clipboardNodes, context.currentEvent, context.sendEvents == null);
	}

	private void copySelectionToClipboard() {
		if(current == null || !current.hasSelectedNodes())
			return;
		CompoundTag tag = current.copySelection();
		clipboardNodes = tag;
	}

	private void pasteClipboard(float x, float y) {
		if(!canPasteClipboard())
			return;
		current.paste(clipboardNodes, x, y);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(texture, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if(current == null)
			return;
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		String hint = "Shift+A to add node";
		g.drawString(gui.font(), hint, cX - gui.font().width(hint) / 2F + 45, cY - 108, 0xFF777777, false);

		float dWheel = (float) gui.takeWheel();
		float dScale = dWheel * gridScale * 0.00075F;

		// Correction so we scale around mouse position
		float prevX = (gui.mouseX - gui.getGuiLeft()) * gridScale;
		float prevY = (gui.mouseY - gui.getGuiTop()) * gridScale;
		gridScale = Mth.clamp(gridScale - dScale, 0.25F, 2.5F);
		float currentX = (gui.mouseX - gui.getGuiLeft()) * gridScale;
		float currentY = (gui.mouseY - gui.getGuiTop()) * gridScale;
		gridX += prevX - currentX;
		gridY -= prevY - currentY;

		if(gridGrabbed) {
			float dX = gui.mouseX - prevMouseX;
			float dY = gui.mouseY - prevMouseY;
			gridX -= dX * gridScale;
			gridY += dY * gridScale;
			prevMouseX = gui.mouseX;
			prevMouseY = gui.mouseY;
		}
		current.dragTo(gui.mouseX, gui.mouseY, gridScale, Screen.hasShiftDown());

		g.enableScissor(cX + WIN_L, cY + WIN_T, cX + WIN_R, cY + WIN_B);

		float u = gridX / gui.getXSize();
		float v = -gridY / gui.getYSize();
		GuiDraw.texQuad(g, grid, gui.getGuiLeft(), gui.getGuiTop(), gui.getXSize(), gui.getYSize(), u, v, gridScale + u, gridScale + v,
				GuiDraw.argb(0.2F, 0.2F, 0.2F, 1));

		g.pose().pushPose();
		float spX = gui.getGuiLeft();
		float spY = gui.getGuiTop();
		g.pose().translate(spX, spY, 0);
		g.pose().scale(1 / gridScale, 1 / gridScale, 1 / gridScale);
		g.pose().translate(-spX, -spY, 0);
		g.pose().translate(-gridX, gridY, 0);
		float[] gridMouse = toGrid(gui.mouseX, gui.mouseY);
		// CE's GL line width was 3 pixels whatever the zoom; a node unit is 1/gridScale of a GUI pixel
		float thickness = 3F / (float) gui.mc().getWindow().getGuiScale() * gridScale;
		NodeDraw.system(g, current, gridMouse[0], gridMouse[1], thickness);
		g.pose().popPose();

		if(problem != null) {
			String shown = gui.font().plainSubstrByWidth("Would be refused: " + problem, WIN_R - WIN_L - 8);
			g.drawString(gui.font(), shown, cX + WIN_L + 4, cY + WIN_B - 10, 0xFFFF5555, false);
		}
		g.disableScissor();

		if(addMenu != null)
			ItemListDraw.render(g, addMenu, gui.mouseX, gui.mouseY);
	}

	@Override
	public void update() {
		if(--problemTicks > 0)
			return;
		problemTicks = 10;
		problem = null;
		if(current != null && editedControl != null) {
			try {
				problem = ControlPanelGuard.graphProblem(editedControl);
			} catch(RuntimeException e) {
				problem = e.toString();
			}
		}
	}

	// ------------------------------------------------------------------ the mouse

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(current == null)
			return false;
		float mx = gui.mouseX;
		float my = gui.mouseY;
		if(addMenu != null && button == 0) {
			if(ControlGeometry.intersects2DBox(mx, my, addMenu.getBoundingBox())) {
				ItemListDraw.mouseClicked(addMenu, mx, my);
			} else {
				addMenu.close();
				addMenu = null;
			}
			return true;
		}
		boolean used = false;
		if(button == 0 && inCanvas(mx, my)) {
			float[] at = toGrid(mx, my);
			// doing this here for now cus i want buttons to be able to make gui changes
			NodeElement pressed = current.getNodeElementPressed(at[0], at[1], mx, my);
			if(pressed != null) {
				NodeSystemEditor before = current;
				pressNodeButton(pressed);
				if(current != before)
					return true; // the button took us into another graph; the click is not meant for it
			}
			current.onClick(at[0], at[1], mx, my, Screen.hasShiftDown());
			used = true;
		}
		if(button == 2) {
			gridGrabbed = true;
			prevMouseX = gui.mouseX;
			prevMouseY = gui.mouseY;
			used = true;
		}
		return used;
	}

	private void pressNodeButton(NodeElement pressed) {
		ButtonAction action = BUTTON_ACTIONS.get(pressed.name);
		if(action != null)
			action.run(this, pressed);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		if(current == null)
			return false;
		if(state == 2)
			gridGrabbed = false;
		if(addMenu == null && state == 0) {
			float[] at = toGrid(gui.mouseX, gui.mouseY);
			current.clickReleased(at[0], at[1], Screen.hasShiftDown());
		}
		return false;
	}

	// ------------------------------------------------------------------ buttons

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_back) {
			if(current != null) {
				current.reset();
				if(!systemHistoryStack.isEmpty()) {
					current = systemHistoryStack.pop();
					context.currentSystem = current.system;
					addMenu = null;
					return;
				}
				prune();
				current = null;
			}
			gui.popElement();
		} else if(button == btn_variables) {
			if(current != null) {
				gui.currentEditControl = current.system.parent;
				gui.pushElement(gui.variables);
			}
		}
	}

	/** Takes the empty event graphs out of the control, so the server is not sent graphs that do nothing. */
	private void prune() {
		pruneEmpty(editedControl);
	}

	/** See {@link NodeSystemEditor#pruneEmptySystems}. Safe with null. */
	public static void pruneEmpty(Control control) {
		if(control != null)
			NodeSystemEditor.pruneEmptySystems(control);
	}

	@Override
	public void onElementOpen() {
		// back from the variables page: the variable dropdowns should list what is there now
		if(current != null)
			refreshVariableSelectors(current.system);
		for(NodeSystemEditor e : systemHistoryStack)
			refreshVariableSelectors(e.system);
	}

	private static void refreshVariableSelectors(NodeSystem sys) {
		for(Node n : sys.nodes) {
			if(n instanceof NodeGetVar v)
				v.setVarSelector();
			else if(n instanceof NodeSetVar v)
				v.setVarSelector();
		}
	}

	@Override
	public void onClose() {
		if(current != null)
			current.reset();
		for(NodeSystemEditor e : systemHistoryStack)
			e.reset();
		prune();
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(btn_back, enable);
		GuiDraw.show(btn_variables, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}
}
