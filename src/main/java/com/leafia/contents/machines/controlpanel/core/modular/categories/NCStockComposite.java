package com.leafia.contents.machines.controlpanel.core.modular.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeRead;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeWrite;

/** The "Pack" category of the node menu. Adapted from NTM-CE's {@code NCStockComposite} (CCP-NODE-EDITOR, D-3067). */
public class NCStockComposite implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		if(s2.equals("Pack Read")) {
			return new NodeCompositeRead(x, y);
		} else if(s2.equals("Pack Write")) {
			return new NodeCompositeWrite(x, y);
		}
		return null;
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Pack Read");
		list.addItems("Pack Write");
	}
}
