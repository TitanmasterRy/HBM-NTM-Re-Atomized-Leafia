package com.leafia.contents.machines.controlpanel.core.modular.categories;

import java.util.LinkedHashMap;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeQueryBlock;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneInput;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

/**
 * The "Input" category of the node menu. Adapted from NTM-CE's {@code NCStockInput} (CCP-NODE-EDITOR, D-3067).
 *
 * <p>"Event Data" is one output per variable of the event the graph is for, plus the two the editor adds: {@code to index} on a
 * send graph, {@code tag} and {@code from index} on a receive graph (what the paste check, {@code NodeClipboard}, allows).
 */
public class NCStockInput implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		if(s2.equals("Event Data")) {
			Map<String, DataValue> vars = new LinkedHashMap<>(editor.currentEvent.vars);
			if(editor.sendEvents == null) {
				vars.put("to index", new DataValueFloat(0));
			} else {
				vars.put("tag", new DataValueString(""));
				vars.put("from index", new DataValueFloat(0));
			}
			return new NodeInput(x, y, "Event Data").setVars(vars);
		} else if(s2.equals("Get Variable")) {
			return new NodeGetVar(x, y, editor.currentSystem.parent);
		} else if(s2.equals("Query Block")) {
			return new NodeQueryBlock(x, y, editor.currentSystem.parent);
		} else if(s2.equals("Redstone Input")) {
			return new NodeRedstoneInput(x, y, editor.currentSystem.parent);
		}
		return null;
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Event Data");
		list.addItems("Get Variable");
		list.addItems("Query Block");
		list.addItems("Redstone Input");
	}
}
