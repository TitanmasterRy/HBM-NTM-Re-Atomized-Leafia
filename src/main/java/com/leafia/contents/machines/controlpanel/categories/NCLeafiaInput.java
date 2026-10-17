package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.nodes.NodeBulkQuery;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphBounds;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRReceiver;

/**
 * Leafia's entries of the "Input" category: Bulk Query Block and Receive RoR (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's
 * {@code NCLeafiaInput}. "Graph Bounds" (CCP-INSTRUMENTS-1, D-3071) is offered only in a graph instrument's own editor, as Leafia offered it.
 */
public class NCLeafiaInput implements INodeMenuCreator {

	@Override
	public Node selectItem(String s, float x, float y, NodeEditContext editor) {
		return switch(s) {
			case "Bulk Query Block" -> new NodeBulkQuery(x, y, editor.currentSystem.parent);
			case "Graph Bounds" -> new NodeGraphBounds(x, y, editor.currentSystem.parent);
			case "Receive RoR" -> new NodeRoRReceiver(x, y, editor.currentSystem.parent);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Bulk Query Block");
		if(editor.currentSystem.parent instanceof Graph)
			list.addItems("Graph Bounds");
		list.addItems("Receive RoR");
	}
}
