package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.nbt.CompoundTag;

/** Reads one key out of a pack ({@code DataValueComposite}) as a string; the text {@code ERROR} if either input is missing. Adapted from NTM-CE (D-3062). */
public class NodeCompositeRead extends Node {

	public NodeCompositeRead(float x, float y) {
		super(x, y);
		outputs.add(new NodeConnection("Value", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		inputs.add(new NodeConnection("Signal", this, inputs.size(), true, DataType.COMPOSITE, new DataValueComposite()));
		inputs.add(new NodeConnection("Key", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		recalcSize();
		evalCache = new DataValue[1];
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		DataValue signal = inputs.get(0).evaluate();
		DataValue key = inputs.get(1).evaluate();
		if(key != null && signal != null) {
			evalCache[0] = new DataValueString(signal.getValueOf(key.toString()));
		} else
			evalCache[0] = new DataValueString("ERROR");
		cacheValid = true;
		return evalCache[0];
	}

	@Override
	public float[] getColor() {
		return DataType.COMPOSITE.getColor();
	}

	@Override
	public String getDisplayName() {
		return "Pack Read";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "composite_read");
		return super.writeToNBT(tag, sys);
	}
}
