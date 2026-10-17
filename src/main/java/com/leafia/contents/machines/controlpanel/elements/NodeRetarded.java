package com.leafia.contents.machines.controlpanel.elements;

import org.jetbrains.annotations.NotNull;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;

/**
 * A socket drawn one row higher than its slot (Leafia's own name for it, kept so a search for upstream's class finds this one).
 * {@code NodeFormat}'s output uses it: the node has a text box in its first row and the output sits beside it (CCP-LEAFIA-NODES,
 * D-3070). Adapted from Leafia's {@code NodeRetarded}. CCP-IC10 ports Leafia's other {@code elements} class and finds this one
 * already here.
 */
public class NodeRetarded extends NodeConnection {

	public NodeRetarded(String name, Node p, int idx, boolean isInput, DataType type, @NotNull DataValue defaultVal) {
		super(name, p, idx, isInput, type, defaultVal);
	}

	@Override
	public void resetOffset() {
		super.resetOffset();
		offsetY -= 8;
	}
}
