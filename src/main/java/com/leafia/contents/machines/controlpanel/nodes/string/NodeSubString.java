package com.leafia.contents.machines.controlpanel.nodes.string;

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
 * Cuts a piece out of a text (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeSubString}, arithmetic for arithmetic.
 *
 * <p>Positions are 1-based and both ends are inclusive of the character they name: Start 1 is the first character. A negative
 * Start counts from the end ({@code length - |start|}, so -1 is the last character); a Start of 0 reads as 1. A negative End
 * counts from the end too ({@code length - |end| + 1}, so End -1 is the whole rest of the text, which is the default). Anything
 * the text cannot give (End past its length, End before Start) reads {@code "ERROR"}.
 */
public class NodeSubString extends Node {

	public NodeSubString(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		this.inputs.add(new NodeConnection("Start", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		this.inputs.add(new NodeConnection("End", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(-1)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.size() < 3)
			return null;
		cacheValid = true;

		DataValue in = inputs.get(0).evaluate();
		DataValue start0 = inputs.get(1).evaluate();
		DataValue end0 = inputs.get(2).evaluate();
		if(in == null || start0 == null || end0 == null)
			return null;

		String s = in.toString();
		int start = (int) start0.getNumber();
		int end = (int) end0.getNumber();

		if(start < 0)
			start = s.length() - Math.abs(start);
		else
			start = Math.max(0, start - 1);
		if(end < 0)
			end = s.length() - Math.abs(end) + 1;
		try {
			s = s.substring(start, end);
		} catch(IndexOutOfBoundsException e) {
			s = "ERROR";
		}
		return evalCache[0] = new DataValueString(s);
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorString;
	}

	@Override
	public String getDisplayName() {
		return "Substring";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "subString");
		return super.writeToNBT(tag, sys);
	}
}
