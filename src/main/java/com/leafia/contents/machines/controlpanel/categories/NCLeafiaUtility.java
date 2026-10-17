package com.leafia.contents.machines.controlpanel.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeCache;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeClock;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeParseFloat;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNG;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNGDecimal;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeSummarizer;

/**
 * Leafia's own "Utility" category (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NCLeafiaUtility}; the menu order is
 * Leafia's, which is not the order the entries are matched in. The two random nodes take the control, where Leafia's took the
 * editor's panel's world.
 */
public class NCLeafiaUtility implements INodeMenuCreator {

	@Override
	public Node selectItem(String s, float x, float y, NodeEditContext editor) {
		return switch(s) {
			case "Cache" -> new NodeCache(x, y);
			case "Clock" -> new NodeClock(x, y);
			case "Summarizer" -> new NodeSummarizer(x, y);
			case "Text to Number" -> new NodeParseFloat(x, y);
			case "RNG" -> new NodeRNG(x, y, editor.currentSystem.parent);
			case "Decimal RNG" -> new NodeRNGDecimal(x, y, editor.currentSystem.parent);
			default -> null;
		};
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		list.addItems("Cache");
		list.addItems("Clock");
		list.addItems("RNG");
		list.addItems("Decimal RNG");
		list.addItems("Summarizer");
		list.addItems("Text to Number");
	}
}
