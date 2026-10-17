package com.leafia.contents.machines.controlpanel.core.modular.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeConditional;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;

/** The "Logic" category of the node menu. Adapted from NTM-CE's {@code NCStockLogic} (CCP-NODE-EDITOR, D-3067). */
public class NCStockLogic implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		if(s2.equals("Function")) {
			return new NodeFunction(x, y);
		} else if(s2.equals("Buffer")) {
			return new NodeBuffer(x, y);
		} else if(s2.equals("Conditional")) {
			return new NodeConditional(x, y);
		}
		return null;
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Function");
		list.addItems("Buffer");
		list.addItems("Conditional");
	}
}
