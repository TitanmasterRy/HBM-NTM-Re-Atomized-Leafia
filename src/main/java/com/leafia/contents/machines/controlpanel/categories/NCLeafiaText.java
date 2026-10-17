package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeAddString;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeFormat;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSIPfx;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSubString;

/** Leafia's own "Text" category: Add String, Substring, SIPfx and Format (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NCLeafiaText}. */
public class NCLeafiaText implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		return switch(s2) {
			case "Add String" -> new NodeAddString(x, y);
			case "Substring" -> new NodeSubString(x, y);
			case "SIPfx" -> new NodeSIPfx(x, y);
			case "Format" -> new NodeFormat(x, y);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Add String");
		list.addItems("Substring");
		list.addItems("SIPfx");
		list.addItems("Format");
	}
}
