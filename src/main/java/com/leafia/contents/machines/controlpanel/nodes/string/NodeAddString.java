package com.leafia.contents.machines.controlpanel.nodes.string;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;

/**
 * Joins two values as text (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeAddString}: the output is
 * {@code input1.toString() + input2.toString()}, so a number joins as its text form, and an input with no value gives no output. The result is cut at {@link AddonNodeSupport#MAX_TEXT} characters (a hardening Leafia's had no need of: its graphs were not hostile).
 */
public class NodeAddString extends Node {

	public NodeAddString(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		this.inputs.add(new NodeConnection("Input 1", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		this.inputs.add(new NodeConnection("Input 2", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.size() < 2)
			return null;
		cacheValid = true;

		DataValue in1 = inputs.get(0).evaluate();
		DataValue in2 = inputs.get(1).evaluate();
		if(in1 == null || in2 == null)
			return null;

		return evalCache[0] = new DataValueString(AddonNodeSupport.clip(in1.toString() + in2.toString()));
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorString;
	}

	@Override
	public String getDisplayName() {
		return "String Add";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "addString");
		return super.writeToNBT(tag, sys);
	}
}
