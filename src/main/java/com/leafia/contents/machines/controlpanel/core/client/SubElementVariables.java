package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlVariables;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The variable list: the panel's <b>global</b> variables and, when a control is being edited, that control's <b>local</b> ones,
 * six to a page, each editable in place, with buttons to make a number, a string or a pack and to delete one or clear the scope.
 * Adapted from NTM-CE's {@code SubElementVariables} (CCP-NODE-EDITOR, D-3067); the rules (what may be made, deleted, typed) are
 * {@link ControlVariables}, which a test runs.
 *
 * <h2>How it differs from CE</h2>
 * CE drew the list every frame and rebuilt its widgets inside the draw call, polling each text box for a change. Here the rows
 * are built when the list changes ({@link #rebuild}: a page, a scope, a make, a delete, the page opening), and a box reports
 * its text when it changes. The local variables are the control being edited, <b>on the scratch panel</b> the editor works on,
 * so a variable made here is saved with the rest of the edit. Opened from the grid with no control selected it shows only the
 * global scope, and the scope button is dead, as in CE.
 *
 * <p>Number boxes take digits, a sign and a point only, and a number that does not parse leaves the old value (CE). A pack and
 * an enum variable are listed but not typed into. Other changes are in {@link ControlVariables}.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementVariables extends SubElement {

	public static final ResourceLocation list_bg = GuiDraw.texture("gui_variable_list_front");

	private Button btn_clearAll;
	private Button btn_back;
	private Button btn_newNumber;
	private Button btn_newString;
	private Button btn_newComposite;
	private Button btn_local;
	private Button btn_global;
	private Button btn_confirmNewVar;
	private Button btn_nextPage;
	private Button btn_prevPage;

	private EditBox txt_newVarName;
	private EditBox txt_newVarData;

	/** What kind of variable the "new" row will make. */
	DataType newVarType = DataType.NUMBER;

	public int numPages = 1;
	public int currentPage = 1;

	/** True for the panel's variables, false for the control's. Set true by the grid's Variables button. */
	public boolean isGlobalScope = true;
	boolean isCreatingNewVar = false;
	private boolean enabled = false;

	/** The names on the current page, in order, and the widgets of each row. */
	private final List<String> rowNames = new ArrayList<>();
	private final List<Button> rowDelete = new ArrayList<>();
	private final List<EditBox> rowBoxes = new ArrayList<>();

	public SubElementVariables(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cY = gui.height / 2;
		int gLeft = gui.getGuiLeft();
		int gTop = gui.getGuiTop();

		btn_back = button(gLeft + 7, gTop + 13, 30, 20, "Back");
		btn_newString = button(gLeft + 7, gTop + 45, 43, 20, "String");
		btn_newNumber = button(gLeft + 7, gTop + 68, 43, 20, "Number");
		btn_newComposite = button(gLeft + 7, gTop + 91, 43, 20, "Pack");
		btn_local = button(gLeft + 59, gTop + 17, 65, 20, "Local");
		btn_global = button(gLeft + 59, gTop + 17, 65, 20, "Global");
		btn_clearAll = button(gLeft + 132, gTop + 17, 43, 20, "Clear");
		btn_prevPage = button(gLeft + 186, gTop + 17, 15, 20, "<");
		btn_nextPage = button(gLeft + 231, gTop + 17, 15, 20, ">");
		btn_confirmNewVar = add(Button.builder(Component.literal("+").withStyle(s -> s.withColor(0x5dac5d)), gui.pressToTop())
				.bounds(gLeft + 223, cY - 80, 20, 20).build());
		txt_newVarName = add(new EditBox(gui.font(), gLeft + 77, cY - 80, 60, 20, Component.literal("name")));
		txt_newVarName.setValue("name");
		txt_newVarName.setMaxLength(ControlVariables.MAX_NAME);
		txt_newVarData = add(new EditBox(gui.font(), gLeft + 152, cY - 80, 65, 20, Component.literal("value")));
		txt_newVarData.setMaxLength(256);

		super.init();
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(list_bg, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
		int cY = gui.height / 2;
		for(int j = 0; j < rowNames.size(); j++)
			GuiDraw.rect(g, gui.getGuiLeft() + 55, (cY - 54.5F) + j * 28, gui.getGuiLeft() + 55 + 193, (cY - 54.5F) + j * 28 + 25, GuiDraw.argb(.15F, .19F, .14F, .2F));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int cY = gui.height / 2;
		String text = currentPage + "/" + numPages;
		g.drawString(gui.font(), text, (gui.getGuiLeft() + 216) - gui.font().width(text) / 2F, cY - 103, 0xFF777777, false);

		for(int j = 0; j < rowNames.size(); j++) {
			String name = rowNames.get(j);
			g.drawString(gui.font(), gui.font().width(name) > 70 ? name.substring(0, Math.min(10, name.length())) + "..." : name, gui.getGuiLeft() + 87, (cY - 46) + (j * 28), 0xFFFFFFFF, false);
			g.drawString(gui.font(), " = ", gui.getGuiLeft() + 154, (cY - 46) + (j * 28), 0xFFFFFFFF, false);
		}

		if(isCreatingNewVar) {
			String label = switch(newVarType) {
				case NUMBER -> "N : ";
				case STRING -> "S : ";
				case COMPOSITE -> "P : ";
				default -> "";
			};
			g.drawString(gui.font(), label, gui.getGuiLeft() + 60, cY - 74, 0xFFFFFFFF, false);
			g.drawString(gui.font(), " = ", gui.getGuiLeft() + 138, cY - 74, 0xFFFFFFFF, false);
		}
	}

	// ------------------------------------------------------------------ the scope

	private Control control() {
		return gui.currentEditControl;
	}

	private Map<String, DataValue> vars() {
		if(isGlobalScope || control() == null)
			return gui.panel.globalVars;
		return control().vars;
	}

	/** The control's custom-name set for a local scope, or null for the global one (what {@link ControlVariables} wants). */
	private java.util.Set<String> custom() {
		return isGlobalScope || control() == null ? null : control().customVarNames;
	}

	// ------------------------------------------------------------------ rows

	/** Builds the rows of the current page from the current scope, and shows or hides every widget accordingly. */
	void rebuild() {
		if(control() == null)
			isGlobalScope = true;
		for(Button b : rowDelete)
			remove(b);
		for(EditBox b : rowBoxes)
			remove(b);
		rowDelete.clear();
		rowBoxes.clear();
		rowNames.clear();

		Map<String, DataValue> map = vars();
		numPages = (int) Math.ceil(Math.max(1, map.size()) / 6F);
		currentPage = Mth.clamp(currentPage, 1, numPages);

		int cY = gui.height / 2;
		int i = 0;
		// a snapshot, so a responder that replaces a value cannot disturb the walk
		for(Map.Entry<String, DataValue> e : new LinkedHashMap<>(map).entrySet()) {
			if(i >= (currentPage - 1) * 6 && i < currentPage * 6) {
				int j = i % 6;
				String key = e.getKey();
				rowNames.add(key);

				Button del = add(Button.builder(Component.literal("X"), gui.pressToTop()).bounds(gui.getGuiLeft() + 60, (cY - 52) + (j * 28), 20, 20).build());
				del.active = ControlVariables.canDelete(custom(), key);
				rowDelete.add(del);

				EditBox box = add(new EditBox(gui.font(), gui.getGuiLeft() + 167, (cY - 52) + (j * 28), 75, 20, Component.literal(key)));
				box.setMaxLength(256);
				DataValue v = e.getValue();
				if(v.getType() == DataType.NUMBER)
					box.setFilter(ControlVariables::isNumericText);
				box.setValue(v.toString());
				box.setTextColor(v.getType() == DataType.NUMBER ? 0x99CCFF : 0xFFFF99);
				if(ControlVariables.isEditable(v)) {
					box.setResponder(text -> ControlVariables.setFromText(vars(), key, text));
				} else {
					box.setEditable(false); // a pack or an enum is listed, not typed into
				}
				rowBoxes.add(box);
			}
			i++;
		}
		refresh();
	}

	/** Shows what this page shows now: the rows, the scope button, the paging, and the "new" row while one is being made. */
	private void refresh() {
		GuiDraw.show(btn_back, enabled);
		GuiDraw.show(btn_clearAll, enabled);
		GuiDraw.show(btn_newNumber, enabled);
		GuiDraw.show(btn_newString, enabled);
		GuiDraw.show(btn_newComposite, enabled);
		boolean hasControl = control() != null;
		GuiDraw.show(btn_global, enabled && isGlobalScope);
		GuiDraw.show(btn_local, enabled && !isGlobalScope);
		btn_global.active = enabled && hasControl;
		btn_local.active = enabled && hasControl;
		GuiDraw.show(btn_prevPage, enabled && numPages > 1 && currentPage > 1);
		GuiDraw.show(btn_nextPage, enabled && numPages > 1 && currentPage < numPages);
		GuiDraw.show(btn_confirmNewVar, enabled);
		btn_confirmNewVar.active = enabled && isCreatingNewVar;
		GuiDraw.show(txt_newVarName, enabled && isCreatingNewVar);
		boolean hideData = newVarType == DataType.COMPOSITE;
		GuiDraw.show(txt_newVarData, enabled && isCreatingNewVar && !hideData);
		for(Button b : rowDelete)
			GuiDraw.show(b, enabled);
		for(EditBox b : rowBoxes)
			GuiDraw.show(b, enabled);
		if(enabled) {
			int k = 0;
			for(Button b : rowDelete)
				b.active = ControlVariables.canDelete(custom(), rowNames.get(k++));
		}
	}

	// ------------------------------------------------------------------ actions

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_newNumber || button == btn_newString || button == btn_newComposite) {
			if(button == btn_newNumber) {
				newVarType = DataType.NUMBER;
				txt_newVarData.setFilter(ControlVariables::isNumericText);
				txt_newVarData.setValue("0");
			} else if(button == btn_newString) {
				newVarType = DataType.STRING;
				txt_newVarData.setFilter(s -> true);
				txt_newVarData.setValue("");
			} else {
				newVarType = DataType.COMPOSITE;
			}
			isCreatingNewVar = true;
			refresh();
		} else if(button == btn_prevPage) {
			currentPage = Math.max(1, currentPage - 1);
			rebuild();
		} else if(button == btn_nextPage) {
			currentPage = Math.min(numPages, currentPage + 1);
			rebuild();
		} else if(button == btn_clearAll) {
			ControlVariables.clear(vars(), custom());
			rebuild();
		} else if(button == btn_local || button == btn_global) {
			isGlobalScope = !isGlobalScope;
			currentPage = 1;
			rebuild();
		} else if(button == btn_back) {
			if(previousElement() == gui.placement)
				gui.currentEditControl = null;
			gui.popElement();
		} else if(button == btn_confirmNewVar) {
			String name = txt_newVarName.getValue();
			if(name.isEmpty())
				return;
			// CE needed a value for a number or a string; a pack has none.
			if(newVarType != DataType.COMPOSITE && txt_newVarData.getValue().isEmpty())
				return;
			if(ControlVariables.create(vars(), custom(), name, newVarType, txt_newVarData.getValue())) {
				isCreatingNewVar = false;
				txt_newVarName.setValue("name");
				rebuild();
			}
			// a number that does not parse keeps the row open, as CE did
		} else {
			int i = rowDelete.indexOf(button);
			if(i != -1) {
				ControlVariables.delete(vars(), custom(), rowNames.get(i));
				rebuild();
			}
		}
	}

	/** The page under this one in the stack, or null (CE's {@code getPreviousElement}). */
	private SubElement previousElement() {
		Iterator<SubElement> it = gui.subElementStack.iterator();
		if(it.hasNext())
			it.next();
		return it.hasNext() ? it.next() : null;
	}

	@Override
	public void enableButtons(boolean enable) {
		enabled = enable;
		if(enable)
			gui.setSlotsEnabled(false);
		refresh();
	}

	@Override
	public void onElementOpen() {
		rebuild();
	}

	@Override
	public void onElementClose() {
		// leaves nothing focused behind a hidden box
		refresh();
	}
}
