package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodePulse;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodeSRLatch;

/** Leafia's entries of the "Boolean" category: Pulse and SR Latch (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NCLeafiaBoolean}. */
public class NCLeafiaBoolean implements INodeMenuCreator {

	@Override
	public Node selectItem(String s, float x, float y, NodeEditContext editor) {
		return switch(s) {
			case "Pulse" -> new NodePulse(x, y);
			case "SR Latch" -> new NodeSRLatch(x, y);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Pulse");
		list.addItems("SR Latch");
	}
}
