package com.leafia.contents.machines.controlpanel.nodes.utility;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.nbt.CompoundTag;

/**
 * Text to number (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeParseFloat}: {@code Float.parseFloat} of the
 * input's text, with a second output, {@code Is Error}, that is 1 when the text was not a number (and the first output 0). Java's
 * parser also accepts a trailing {@code f} or {@code d}, {@code NaN}, {@code Infinity} and hex floats, and trims whitespace.
 */
public class NodeParseFloat extends Node {

	public NodeParseFloat(float x, float y) {
		super(x, y);
		outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		outputs.add(new NodeConnection("Is Error", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.STRING, new DataValueString("0")));
		recalcSize();
		evalCache = new DataValue[2];
	}

	@Override
	public DataValue evaluate(int i) {
		if(i < 0 || i > 1 || inputs.isEmpty())
			return null;
		if(cacheValid)
			return evalCache[i];
		DataValue in = inputs.get(0).evaluate();
		if(in == null)
			return null;
		cacheValid = true;
		float value = 0;
		float error = 0;
		try {
			value = Float.parseFloat(in.toString());
		} catch(NumberFormatException ignored) {
			error = 1;
		}
		evalCache[0] = new DataValueFloat(value);
		evalCache[1] = new DataValueFloat(error);
		return evalCache[i];
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorUtility;
	}

	@Override
	public String getDisplayName() {
		return "Text to Number";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_parse_float");
		return super.writeToNBT(tag, sys);
	}
}
