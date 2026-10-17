package com.leafia.contents.machines.controlpanel.nodes.string;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.llib.math.SIPfx;

import net.minecraft.nbt.CompoundTag;

/**
 * A number with its SI prefix (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeSIPfx}: {@code SIPfx.auto(input,
 * showFull)}, so 12500 reads {@code "12.5 k"} and, with Show Full set, {@code "12.5 Kilo"}.
 */
public class NodeSIPfx extends Node {

	public NodeSIPfx(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Shortened", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Show Full", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
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

		DataValue in = inputs.get(0).evaluate();
		DataValue full = inputs.get(1).evaluate();
		if(in == null || full == null)
			return null;

		return evalCache[0] = new DataValueString(SIPfx.auto(in.getNumber(), full.getBoolean()));
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorString;
	}

	@Override
	public String getDisplayName() {
		return "SIPfx";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "sipfx");
		return super.writeToNBT(tag, sys);
	}
}
