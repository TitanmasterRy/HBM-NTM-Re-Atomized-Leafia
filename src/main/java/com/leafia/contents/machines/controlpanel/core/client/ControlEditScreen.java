package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.machines.controlpanel.ic10.client.IC10Client;
import com.leafia.contents.machines.controlpanel.ic10.client.SubElementIC10Editor;
import com.leafia.contents.machines.controlpanel.ic10.client.SubElementIC10Examples;
import com.leafia.contents.machines.controlpanel.ic10.client.SubElementIC10Funcs;
import com.leafia.contents.machines.controlpanel.ic10.client.SubElementIC10Warning;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEditMenu;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The control panel editor: a 256 by 256 screen hosting a stack of {@link SubElement} pages. Adapted from NTM-CE's
 * {@code GuiControlEdit} (CCP-EDITOR, D-3066) as an {@link AbstractContainerScreen}
 * over {@link ControlEditMenu}, drawing through {@link GuiGraphics}.
 *
 * <h2>It edits a copy</h2>
 * CE edited the live panel and sent the whole tree on close. Here the screen reads the block entity's panel into {@link #panel},
 * a scratch {@link ControlPanel}, edits that, and on close sends it as {@code full_set} <b>only if it changed</b>. The live panel
 * changes when the server accepts the edit and sends the result back ({@code ControlPanelUpdatePacket}); a refused edit leaves
 * the client's panel as the server has it, and the server sends it again (D-3066). The edit is sent <b>before</b> the container
 * is closed: the server accepts it only while the editor's menu is open (D-3063), and the close packet would arrive first
 * otherwise.
 *
 * <h2>The node pages</h2>
 * The variables page, the event editor and the node editor are CCP-NODE-EDITOR's (D-3067): {@link #variables},
 * {@link #eventEditor} and {@link #nodeEditor}. The linker's "Continue" goes to the event editor, whose "Done" is
 * {@link #finishEdit}. The pictures of the controls are batch 5's ({@link ControlPreview#drawer}); until then each control is a
 * labelled box.
 */
@OnlyIn(Dist.CLIENT)
public class ControlEditScreen extends AbstractContainerScreen<ControlEditMenu> {

	public static final ResourceLocation BACK = GuiDraw.texture("gui_placement_back");

	public final ControlPanelTE control;
	/** The panel being edited: a copy of the block entity's, sent back on close if it changed. */
	public final ControlPanel panel;
	private final CompoundTag original;

	public final Deque<SubElement> subElementStack = new ArrayDeque<>();
	public SubElementPlacement placement;
	public SubElementItemChoice choice;
	public SubElementLinker linker;
	public SubElementPanelResize panelResize;
	public SubElementItemConfig itemConfig;
	/** The node graph pages (CCP-NODE-EDITOR, D-3067). Each is pushed by name from the pages above. */
	public SubElementEventEditor eventEditor;
	public SubElementNodeEditor nodeEditor;
	public SubElementVariables variables;
	/**
	 * Leafia's four IC10 pages (CCP-IC10, D-3073; the pages {@code MixinGuiControlEdit.initGui} added): the code editor, pushed by
	 * a node's "Edit Code" button, and the instruction reference, the example list and the "code will be lost" question it leads to.
	 * Null until {@link #init}, and always built, whether or not IC10 is on: nothing pushes them unless an IC10 node is pressed.
	 */
	public SubElementIC10Editor ic10Editor;
	public SubElementIC10Funcs ic10Funcs;
	public SubElementIC10Examples ic10Examples;
	public SubElementIC10Warning ic10Warning;

	/** The control being placed or edited. */
	public Control currentEditControl;
	/** True when editing a control already on the panel, false when making a new one. */
	public boolean isEditMode = false;

	/** The mouse in GUI coordinates, to the fraction. */
	public float mouseX, mouseY;
	private double wheel = 0;

	public ControlEditScreen(ControlEditMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.control = menu.control;
		this.imageWidth = 256;
		this.imageHeight = 256;
		this.panel = new ControlPanel(control);
		this.panel.readFromNBT(control.panel.writeToNBT(new CompoundTag()));
		this.original = this.panel.writeToNBT(new CompoundTag());
	}

	public Font font() {
		return font;
	}

	public Minecraft mc() {
		return minecraft;
	}

	// ------------------------------------------------------------------ pages

	@Override
	protected void init() {
		super.init();
		subElementStack.clear();
		placement = new SubElementPlacement(this);
		choice = new SubElementItemChoice(this);
		linker = new SubElementLinker(this);
		panelResize = new SubElementPanelResize(this);
		itemConfig = new SubElementItemConfig(this);
		eventEditor = new SubElementEventEditor(this);
		nodeEditor = new SubElementNodeEditor(this);
		variables = new SubElementVariables(this);
		ic10Editor = new SubElementIC10Editor(this);
		ic10Funcs = new SubElementIC10Funcs(this);
		ic10Examples = new SubElementIC10Examples(this);
		ic10Warning = new SubElementIC10Warning(this);
		IC10Client.register();
		placement.init();
		choice.init();
		linker.init();
		panelResize.init();
		itemConfig.init();
		eventEditor.init();
		nodeEditor.init();
		variables.init();
		ic10Editor.init();
		ic10Funcs.init();
		ic10Examples.init();
		ic10Warning.init();

		subElementStack.addFirst(placement);
		placement.enableButtons(true);
	}

	public SubElement top() {
		return subElementStack.getFirst();
	}

	public void pushElement(SubElement e) {
		top().enableButtons(false);
		top().onElementClose();
		e.lock = true;
		e.enableButtons(true);
		e.onElementOpen();
		subElementStack.addFirst(e);
	}

	public void popElement() {
		SubElement e = subElementStack.removeFirst();
		e.enableButtons(false);
		e.onElementClose();
		top().enableButtons(true);
		top().onElementOpen();
	}

	public void resetStack() {
		top().enableButtons(false);
		top().onElementClose();
		subElementStack.clear();
		subElementStack.addFirst(placement);
		placement.enableButtons(true);
	}

	/** Switches the linker slot and the inventory slots on or off as a page needs them. */
	public void setSlotsEnabled(boolean enabled) {
		menu.input.enabled = enabled;
		for(ControlEditMenu.SlotDisableable slot : menu.invSlots)
			slot.enabled = enabled;
	}

	/** CE's {@code returnControlInputToPlayerInventory}: shift-clicks the linker slot's item out. */
	public void returnControlInputToPlayerInventory() {
		if(menu.input.hasItem())
			slotClicked(menu.input, ControlEditMenu.LINKER_SLOT, 0, ClickType.QUICK_MOVE);
	}

	// ------------------------------------------------------------------ the end of an edit

	/** See {@link ControlLinks#accumulateReceiveEvents}. */
	public static List<ControlEvent> accumulateReceiveEvents(Control control, List<IControllable> linked) {
		return ControlLinks.accumulateReceiveEvents(control, linked);
	}

	/**
	 * Done with the control: the event editor's "Done" (CE's {@code SubElementEventEditor}), and the way out of the linker when a
	 * test or a caller has no event editor.
	 * Gives a new control its default graphs, keeps the links the linker holds, then hands a new control to the grid to be placed
	 * under the mouse, or lets go of an edited one. CE also sent the control {@code initialize} here; the server does that for
	 * every control when it accepts the edit, and the client's copy has no business running a graph, so it is not done here.
	 */
	public void finishEdit(List<IControllable> linked) {
		Control c = currentEditControl;
		if(c == null)
			return;
		linker.syncCurrentEditControlConnections();
		if(!isEditMode && c.receiveNodeMap.isEmpty() && c.sendNodeMap.isEmpty())
			c.populateDefaultNodes(accumulateReceiveEvents(c, linked));
		if(!isEditMode) {
			float[] gridMouse = placement.convertToGridSpace(mouseX, mouseY);
			c.posX = gridMouse[0];
			c.posY = gridMouse[1];
			placement.resetPrevPos();
		} else {
			currentEditControl = null;
			isEditMode = false;
		}
		resetStack();
	}

	// ------------------------------------------------------------------ widgets

	public <T extends AbstractWidget> T addWidget(T widget) {
		return addRenderableWidget(widget);
	}

	public void removeWidgetFromScreen(AbstractWidget widget) {
		removeWidget(widget);
	}

	/** What every button of the editor does when pressed: tell the top page, unless it was only just pushed by this same click. */
	public Button.OnPress pressToTop() {
		return b -> {
			SubElement top = top();
			if(!top.lock)
				top.actionPerformed(b);
		};
	}

	/** A button whose press goes to the top page, unless that page was only just pushed by this same click. */
	public Button button(int x, int y, int width, int height, String label) {
		return Button.builder(Component.literal(label), pressToTop()).bounds(x, y, width, height).build();
	}

	public double takeWheel() {
		double w = wheel;
		wheel = 0;
		return w;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Window window = minecraft.getWindow();
		this.mouseX = (float) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
		this.mouseY = (float) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
		super.render(g, mouseX, mouseY, partialTick);
		top().render(g, mouseX, mouseY, partialTick);
		wheel = 0;
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(BACK, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		top().renderBackground(g);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

	// ------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
			return super.keyPressed(keyCode, scanCode, modifiers);
		// A text box in focus takes everything, and a digit typed in it must not swap the hovered inventory slot.
		if(getFocused() instanceof EditBox box && box.isFocused()) {
			box.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if(top().keyPressed(keyCode, scanCode, modifiers))
			return true;
		// CE swallowed the inventory key rather than closing the editor with it.
		if(minecraft.options.keyInventory.matches(keyCode, scanCode))
			return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if(super.charTyped(codePoint, modifiers))
			return true;
		return top().charTyped(codePoint, modifiers);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		boolean handled = super.mouseClicked(x, y, button);
		top().lock = false;
		return top().mouseClicked(x, y, button) || handled;
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		boolean handled = super.mouseReleased(x, y, button);
		return top().mouseReleased(x, y, button) || handled;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		wheel += scrollY * 120;
		return top().mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	protected void containerTick() {
		top().update();
	}

	// ------------------------------------------------------------------ closing

	/** The user closed the editor (Escape, or any other way that is theirs). Sends the edit first, then closes the container. */
	@Override
	public void onClose() {
		placement.restoreGrabbed();
		placement.onClose();
		choice.onClose();
		linker.onClose();
		panelResize.onClose();
		itemConfig.onClose();
		eventEditor.onClose();
		nodeEditor.onClose();
		variables.onClose();
		ic10Editor.onClose();
		ic10Funcs.onClose();
		ic10Examples.onClose();
		ic10Warning.onClose();
		sendEdit();
		isEditMode = false;
		super.onClose();
	}

	private void sendEdit() {
		CompoundTag tag = panel.writeToNBT(new CompoundTag());
		if(tag.equals(original))
			return;
		tag.putString("full_set", "");
		PacketDistributor.sendToServer(new NBTControlPacket(control.getBlockPos(), tag));
	}
}
