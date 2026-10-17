package com.leafia.contents.machines.controlpanel.core;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.nbt.CompoundTag;

/**
 * Stands in for a saved node no loader recognised, and writes the saved tag back unchanged so that a panel saved without the
 * addon that supplied the node does not lose it. Adapted from NTM-CE's {@code UnknownNode} (D-3061); it is {@code NodeSystem}'s
 * fallback, so it lands with the evaluation half rather than with the stock nodes (the ledger row is ticked in batch 1).
 *
 * <p>Evaluating one is an error for an enum socket (there is no constant to invent), an empty pack for a composite, NaN for a
 * number and the text {@code ERROR} for a string.
 */
public class UnknownNode extends Node {

	CompoundTag tagBase;

	public UnknownNode(float x, float y, CompoundTag tag) {
		super(x, y);
		tagBase = tag;
		CompoundTag inputs = tag.getCompound("in");
		for(int i = 0; i < inputs.getAllKeys().size(); i++) {
			NodeConnection c = new NodeConnection(null, this, 0, false, null, new DataValueFloat(0));
			c.buildFromNBT(inputs.getCompound("con" + i));
			this.inputs.add(c);
		}
		CompoundTag outputs = tag.getCompound("out");
		for(int i = 0; i < outputs.getAllKeys().size(); i++) {
			NodeConnection c = new NodeConnection(null, this, 0, false, null, new DataValueFloat(0));
			c.buildFromNBT(outputs.getCompound("con" + i));
			this.outputs.add(c);
		}
		recalcSize();
	}

	@Override
	public DataValue evaluate(int idx) {
		return switch(outputs.get(idx).type) {
			case ENUM -> throw new UnsupportedOperationException("Control panel contains unresolved node type!");
			case COMPOSITE -> new DataValueComposite();
			case NUMBER -> new DataValueFloat(Float.NaN);
			case STRING, GENERIC -> new DataValueString("ERROR");
		};
	}

	@Override
	public float[] getColor() {
		return new float[]{1, 1, 1};
	}

	@Override
	public String getDisplayName() {
		return "Unknown";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		for(String s : tagBase.getAllKeys())
			tag.put(s, tagBase.get(s).copy());
		return super.writeToNBT(tag, sys);
	}
}
