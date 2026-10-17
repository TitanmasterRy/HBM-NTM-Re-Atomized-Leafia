package com.leafia.contents.machines.controlpanel.core.modular.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;

/** The "Boolean" category of the node menu. Adapted from NTM-CE's {@code NCStockBoolean} (CCP-NODE-EDITOR, D-3067). */
public class NCStockBoolean implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		if(s2.equals("Boolean Node")) {
			return new NodeBoolean(x, y);
		}
		return null;
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Boolean Node");
	}
}
