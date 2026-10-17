package com.leafia.contents.machines.controlpanel.nodes.bool;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * A set/reset latch (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeSRLatch}. Set turns it on, then Reset turns it
 * off, in that order, so <b>reset wins when both are high</b>. It holds its state (saved) between events and is not cached: every
 * read applies the inputs again, which is idempotent for a latch.
 */
public class NodeSRLatch extends Node {

	public boolean state = false;

	public NodeSRLatch(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Set", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Reset", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		recalcSize();
	}

	@Override
	public DataValue evaluate(int i) {
		if(inputs.size() < 2)
			return null;
		DataValue set = inputs.get(0).evaluate();
		DataValue reset = inputs.get(1).evaluate();
		if(set != null && set.getBoolean())
			state = true;
		if(reset != null && reset.getBoolean())
			state = false;
		return new DataValueFloat(state ? 1 : 0);
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorBoolean;
	}

	@Override
	public String getDisplayName() {
		return "SR Latch";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		state = tag.getBoolean("state");
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_sr_latch");
		tag.putBoolean("state", state);
		return super.writeToNBT(tag, sys);
	}
}
