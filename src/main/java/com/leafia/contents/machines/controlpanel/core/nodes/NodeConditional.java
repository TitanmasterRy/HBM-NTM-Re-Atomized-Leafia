package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/** Answers Input when Enable is true, else Default. Adapted from NTM-CE's {@code NodeConditional} (D-3062). */
public class NodeConditional extends Node {

	public NodeConditional(float x, float y) {
		super(x, y);

		this.inputs.add(new NodeConnection("Enable", this, inputs.size(), true, DataValue.DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataValue.DataType.GENERIC, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Default", this, inputs.size(), true, DataValue.DataType.GENERIC, new DataValueFloat(0)));
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataValue.DataType.GENERIC, new DataValueFloat(0)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorLogic;
	}

	@Override
	public String getDisplayName() {
		return "Conditional";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "conditional");
		return super.writeToNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		cacheValid = true;

		DataValue enable = inputs.get(0).evaluate();
		DataValue in = inputs.get(1).evaluate();
		DataValue defaul = inputs.get(2).evaluate();
		if(enable == null || in == null || defaul == null)
			return null;

		if(enable.getBoolean()) {
			return evalCache[0] = in;
		}

		return evalCache[0] = defaul;
	}
}
