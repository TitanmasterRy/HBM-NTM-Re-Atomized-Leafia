package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphAdd;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRSender;

/**
 * Leafia's entries of the "Output" category: Play Sound and Transmit RoR (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's
 * {@code NCLeafiaOutput}. "Graph Add" (CCP-INSTRUMENTS-1, D-3071) is offered only in a graph instrument's own editor, as Leafia offered it.
 */
public class NCLeafiaOutput implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		return switch(s2) {
			case "Play Sound" -> new NodeSounder(x, y);
			case "Graph Add" -> new NodeGraphAdd(x, y, editor.currentSystem.parent);
			case "Transmit RoR" -> new NodeRoRSender(x, y, editor.currentSystem.parent);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Play Sound");
		if(editor.currentSystem.parent instanceof Graph)
			list.addItems("Graph Add");
		list.addItems("Transmit RoR");
	}
}
