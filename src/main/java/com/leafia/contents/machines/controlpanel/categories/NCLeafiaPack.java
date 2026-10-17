package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.nodes.pack.NodeIC10;

/**
 * Leafia's entry in the "Pack" category: the IC10 node (CCP-IC10, D-3073). Adapted from Leafia's {@code NCLeafiaPack}. It is
 * offered only while {@link LeafiaFeatures#controlPanelIC10} is on; a graph that already holds one is read either way (the loader
 * decides, see {@code AddonNodeLoader}).
 */
public class NCLeafiaPack implements INodeMenuCreator {

	@Override
	public Node selectItem(String s, float x, float y, NodeEditContext editor) {
		if(!LeafiaFeatures.controlPanelIC10)
			return null;
		return switch(s) {
			case "IC10 Sequence" -> new NodeIC10(x, y, editor.currentSystem.parent);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		if(LeafiaFeatures.controlPanelIC10)
			list.addItems("IC10 Sequence");
	}
}
