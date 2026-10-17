package com.leafia.contents.machines.controlpanel.elements;

import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;

/**
 * Leafia's {@code elements/NodeTextBox} (CCP-IC10, D-3073): a one-line text field inside a node.
 *
 * <p>Leafia's class is a copy of NTM-CE's {@code NodeTextBox} with {@code implements ITypableNode} commented out, so it could not
 * be typed into; nothing in Leafia's source constructs it ({@code NodeFormat} uses CE's). The port keeps the name, as it does for
 * {@code NodeRetarded} (D-002), and makes it a subclass of the core {@link com.leafia.contents.machines.controlpanel.core.NodeTextBox},
 * which is the port of the same code with the typing protocol Leafia's left out: it saves and loads under the same keys, the node
 * editor draws it as its parent (the drawer lookup walks up the superclasses), and a click on its value box types into it.
 */
public class NodeTextBox extends com.leafia.contents.machines.controlpanel.core.NodeTextBox {

	public NodeTextBox(String name, Node p, int idx, String defaultVal, Consumer<String> func) {
		super(name, p, idx, defaultVal, func);
	}
}
