package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeButton;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * A node that owns a sub-graph, run when its Enable input is true. Adapted from NTM-CE's {@code NodeFunction} (D-3062);
 * implements {@link IFunctionNode}, which {@code NodeSystem} tests instead of the class.
 *
 * <p>Its "Edit Body" button is kept as {@link #editBody}, a {@link NodeButton} that takes its row. What the button does when
 * clicked, descending into the sub-graph in the node editor, is the client's (§2.2); batch 7's editor recognises a function
 * node's button through this field.
 */
public class NodeFunction extends Node implements IFunctionNode {

	public final NodeButton editBody;

	public NodeFunction(float x, float y) {
		super(x, y);

		this.inputs.add(new NodeConnection("Enable", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.editBody = new NodeButton("Edit Body", this, otherElements.size());
		this.otherElements.add(editBody);
		this.recalcSize();

		evalCache = new DataValue[1];
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorLogic;
	}

	@Override
	public String getDisplayName() {
		return "Function";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "function");
		return super.writeToNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		cacheValid = true;

		DataValue enable = inputs.get(0).evaluate();
		if(enable == null)
			return null;

		return evalCache[0] = new DataValueFloat(enable.getBoolean());
	}
}
