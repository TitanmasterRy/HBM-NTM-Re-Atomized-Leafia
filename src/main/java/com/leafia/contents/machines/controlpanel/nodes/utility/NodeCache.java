package com.leafia.contents.machines.controlpanel.nodes.utility;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads its input once per event and hands the same value to everything that asks (CCP-LEAFIA-NODES, D-3070). Adapted from
 * Leafia's {@code NodeCache}. It exists for the nodes that are not cached themselves (the random numbers, the summarizer, the
 * latch, a query): put a cache after one and its consumers all see one read.
 */
public class NodeCache extends Node {

	public NodeCache(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public DataValue evaluate(int i) {
		if(inputs.isEmpty())
			return null;
		if(cacheValid)
			return evalCache[0];
		DataValue value = inputs.get(0).evaluate();
		evalCache[0] = value;
		cacheValid = true;
		return value;
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorUtility;
	}

	@Override
	public String getDisplayName() {
		return "Cache";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_cache");
		return super.writeToNBT(tag, sys);
	}
}
