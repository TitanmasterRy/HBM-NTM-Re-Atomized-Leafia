package com.leafia.contents.machines.controlpanel.core.modular.categories;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCancelEvent;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneOutput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;

/**
 * The "Output" category of the node menu. Adapted from NTM-CE's {@code NCStockOutput} (CCP-NODE-EDITOR, D-3067).
 *
 * <p>A receive graph may broadcast (when the control has an event to send) and a send graph may cancel; the two are never both
 * on offer, which is also what the paste check enforces.
 */
public class NCStockOutput implements INodeMenuCreator {

	@Override
	public Node selectItem(String s2, float x, float y, NodeEditContext editor) {
		if(s2.equals("Broadcast")) {
			if(editor.sendEvents == null || editor.sendEvents.isEmpty())
				return null; // not offered then; a broadcast node cannot be made without an event
			return new NodeEventBroadcast(x, y, editor.sendEvents);
		} else if(s2.equals("Cancel")) {
			return new NodeCancelEvent(x, y);
		} else if(s2.equals("Set Variable")) {
			return new NodeSetVar(x, y, editor.currentSystem.parent);
		} else if(s2.equals("Redstone Output")) {
			return new NodeRedstoneOutput(x, y);
		}
		return null;
	}

	@Override
	public void addItems(ItemList list, float x, float y, NodeEditContext editor) {
		if(editor.sendEvents != null) {
			if(!editor.sendEvents.isEmpty())
				list.addItems("Broadcast");
		} else {
			list.addItems("Cancel");
		}
		list.addItems("Set Variable");
		list.addItems("Redstone Output");
	}
}
