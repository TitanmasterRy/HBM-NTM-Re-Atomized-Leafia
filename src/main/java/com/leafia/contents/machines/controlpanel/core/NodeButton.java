package com.leafia.contents.machines.controlpanel.core;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;

/**
 * A button row inside a node: the <b>common half</b> of NTM-CE's {@code NodeButton} (D-3062), reduced to what a server can have,
 * its label, its place in the node and its hit test.
 *
 * <p>CE's {@code render} and the {@code onClicked(SubElement)} override its only user, {@code NodeFunction}, supplied
 * ("Edit Body" descends into the function's sub-graph) are the client's (§2.2). The button exists on a server because it takes
 * a row: {@link Node#recalcSize} counts a node's {@code otherElements}, so a function node without one would be eight units
 * short and every socket under it would sit a row too high in a saved graph.
 */
public class NodeButton extends NodeElement {

	public NodeButton(String name, Node parent, int idx) {
		super(parent, idx);
		this.name = name;
		resetOffset();
	}

	/** True if a click at this node-space position lands on the button (it consumes the click, as CE's did). Strict, as CE's box test is. */
	@Override
	public boolean onClick(float x, float y) {
		float[] box = getBox();
		return x > box[0] && x < box[2] && y > box[1] && y < box[3];
	}
}
