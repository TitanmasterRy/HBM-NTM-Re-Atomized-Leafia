package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.IControllable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The last step of making or editing a control: a list of the events it can <b>receive</b> and a list of the events it can
 * <b>send</b>, three to a page, each opening that event's graph in the node editor. "Done" puts the control on the grid (a new
 * one) or lets go of it (an edited one). Adapted from NTM-CE's {@code SubElementEventEditor} (CCP-NODE-EDITOR, D-3067).
 *
 * <p>The events come from {@link ControlLinks}: receivable are the control's own out-events and in-events, then every linked
 * machine's out-events; sendable are every linked machine's in-events. The control's own come first because a default graph
 * indexes this list ({@code receiveEvents.get(0)} is {@code ctrl_press} on a button, D-3064). A new control is given its default
 * graphs when the linker's "Continue" calls {@link #populateDefaultNodes}, so there is something to open.
 *
 * <p>Differences from CE: "Done" does not send {@code initialize} from the client (D-3066: the server does it when it accepts
 * the edit) and goes through {@link ControlEditScreen#finishEdit}; the event lists are read from the machines the linker has
 * <i>now</i> ({@code getLinked}), which is what CE passed in too.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementEventEditor extends SubElement {

	public List<ControlEvent> receiveEvents = new ArrayList<>();
	public List<ControlEvent> sendEvents = new ArrayList<>();
	public List<Button> receiveButtons = new ArrayList<>();
	public List<Button> sendButtons = new ArrayList<>();
	public int currentReceivePage = 1;
	public int numReceivePages = 1;
	public int currentSendPage = 1;
	public int numSendPages = 1;
	public Button receivePageLeft;
	public Button receivePageRight;
	public Button sendPageLeft;
	public Button sendPageRight;
	public Button done;
	public Button back;

	/** Whether this page is the one showing, so a rebuild of the lists does not show buttons of a page that is hidden. */
	private boolean enabled = false;

	public SubElementEventEditor(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		receivePageLeft = button(cX - 62, cY - 29, 20, 20, "<");
		receivePageRight = button(cX + 88, cY - 29, 20, 20, ">");
		sendPageLeft = button(cX - 62, cY + 70, 20, 20, "<");
		sendPageRight = button(cX + 88, cY + 70, 20, 20, ">");
		done = button(cX - 62, cY + 92, 170, 20, "Done");
		back = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 13, 30, 20, "Back");
		super.init();
	}

	/** Reads the events the control and the machines it is linked to can send and receive, and makes a button for each. */
	public void accumulateEventTypes(List<IControllable> linked) {
		for(Button b : receiveButtons)
			remove(b);
		for(Button b : sendButtons)
			remove(b);
		receiveButtons.clear();
		sendButtons.clear();
		Control control = gui.currentEditControl;
		receiveEvents = ControlLinks.accumulateReceiveEvents(control, linked);
		sendEvents = ControlLinks.accumulateSendEvents(linked);

		int cX = gui.width / 2;
		int cY = gui.height / 2;

		numReceivePages = (receiveEvents.size() + 2) / 3;
		for(int i = 0; i < receiveEvents.size(); i++) {
			int offset = (i % 3) * 25;
			receiveButtons.add(add(new ButtonHoverText(cX - 62, cY - 100 + offset, 170, 20, receiveEvents.get(i).name, "<Click to edit>", gui.pressToTop())));
		}
		currentReceivePage = Mth.clamp(currentReceivePage, 1, Math.max(1, numReceivePages));

		numSendPages = (sendEvents.size() + 2) / 3;
		for(int i = 0; i < sendEvents.size(); i++) {
			int offset = (i % 3) * 25;
			sendButtons.add(add(new ButtonHoverText(cX - 62, cY + 5 + offset, 170, 20, sendEvents.get(i).name, "<Click to edit>", gui.pressToTop())));
		}
		currentSendPage = Mth.clamp(currentSendPage, 1, Math.max(1, numSendPages));
		enableButtons(enabled);
	}

	/** A new control gets its default graphs, once, from the receivable events (CE's {@code populateDefaultNodes}). */
	public void populateDefaultNodes() {
		Control c = gui.currentEditControl;
		if(!gui.isEditMode && c != null && c.receiveNodeMap.isEmpty() && c.sendNodeMap.isEmpty())
			c.populateDefaultNodes(receiveEvents);
	}

	private void recalculateVisibleButtons() {
		for(Button b : receiveButtons)
			GuiDraw.show(b, false);
		for(Button b : sendButtons)
			GuiDraw.show(b, false);
		int idx = (currentReceivePage - 1) * 3;
		for(int i = idx; i < idx + 3 && i < receiveButtons.size(); i++)
			GuiDraw.show(receiveButtons.get(i), true);
		idx = (currentSendPage - 1) * 3;
		for(int i = idx; i < idx + 3 && i < sendButtons.size(); i++)
			GuiDraw.show(sendButtons.get(i), true);

		GuiDraw.show(receivePageLeft, numReceivePages > 1 && currentReceivePage > 1);
		GuiDraw.show(receivePageRight, numReceivePages > 1 && currentReceivePage < numReceivePages);
		GuiDraw.show(sendPageLeft, numSendPages > 1 && currentSendPage > 1);
		GuiDraw.show(sendPageRight, numSendPages > 1 && currentSendPage < numSendPages);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		String text = currentReceivePage + "/" + Math.max(1, numReceivePages);
		g.drawString(gui.font(), text, cX + 12, cY - 21, 0xFF777777, false);
		text = currentSendPage + "/" + Math.max(1, numSendPages);
		g.drawString(gui.font(), text, cX + 12, cY + 76, 0xFF777777, false);
		text = "Receivable Events";
		g.drawString(gui.font(), text, cX - gui.font().width(text) / 2F + 22, cY - 112, 0xFF777777, false);
		text = "Sendable Events";
		g.drawString(gui.font(), text, cX - gui.font().width(text) / 2F + 22, cY - 5, 0xFF777777, false);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == back) {
			gui.popElement();
		} else if(button == receivePageLeft) {
			currentReceivePage = Math.max(1, currentReceivePage - 1);
			recalculateVisibleButtons();
		} else if(button == receivePageRight) {
			currentReceivePage = Math.min(numReceivePages, currentReceivePage + 1);
			recalculateVisibleButtons();
		} else if(button == sendPageLeft) {
			currentSendPage = Math.max(1, currentSendPage - 1);
			recalculateVisibleButtons();
		} else if(button == sendPageRight) {
			currentSendPage = Math.min(numSendPages, currentSendPage + 1);
			recalculateVisibleButtons();
		} else if(button == done) {
			SubElementNodeEditor.pruneEmpty(gui.currentEditControl);
			gui.finishEdit(gui.linker.getLinked());
		} else {
			int i = receiveButtons.indexOf(button);
			if(i != -1) {
				gui.nodeEditor.setData(gui.currentEditControl.receiveNodeMap, receiveEvents.get(i), sendEvents);
			} else {
				i = sendButtons.indexOf(button);
				if(i != -1)
					gui.nodeEditor.setData(gui.currentEditControl.sendNodeMap, sendEvents.get(i), null);
			}
			if(i != -1)
				gui.pushElement(gui.nodeEditor);
		}
	}

	@Override
	public void enableButtons(boolean enable) {
		enabled = enable;
		if(enable) {
			recalculateVisibleButtons();
		} else {
			for(Button b : receiveButtons)
				GuiDraw.show(b, false);
			for(Button b : sendButtons)
				GuiDraw.show(b, false);
			GuiDraw.show(receivePageLeft, false);
			GuiDraw.show(receivePageRight, false);
			GuiDraw.show(sendPageLeft, false);
			GuiDraw.show(sendPageRight, false);
		}
		GuiDraw.show(done, enable);
		GuiDraw.show(back, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}
}
