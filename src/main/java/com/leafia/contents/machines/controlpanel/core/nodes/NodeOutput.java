package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.types.DataValue;

/**
 * Base of the nodes that act when their graph runs. Adapted from NTM-CE's {@code NodeOutput} (D-3062); implements
 * {@link IOutputNode}, whose {@code doOutput} carries CE's contract (true to continue, false only for an explicit cancel).
 *
 * <p>An output has no value to hand to a later node, so {@link #evaluate} is null.
 */
public abstract class NodeOutput extends Node implements IOutputNode {

	public NodeOutput(float x, float y) {
		super(x, y);
	}

	@Override
	public DataValue evaluate(int idx) {
		//Output nodes don't need this
		return null;
	}
}
