package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * Holds a signal back for a number of evaluations. Adapted from NTM-CE's {@code NodeBuffer} (D-3062).
 *
 * <p>A true input (or a count already running) starts counting one per evaluation; when the count equals the delay
 * (<code>|round(delay)|</code>) the node answers the input itself and resets, and every other evaluation answers 0. A delay of 0
 * therefore passes a true input straight through. The count is saved, so a reload part-way through a delay resumes it.
 */
public class NodeBuffer extends Node {

	private int internalCount;

	public NodeBuffer(float x, float y) {
		super(x, y);
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Delay", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	/** The running count, for tests and the editor. */
	public int getInternalCount() {
		return internalCount;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorLogic;
	}

	@Override
	public String getDisplayName() {
		return "Buffer";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "buffer");
		tag.putInt("internalCount", this.internalCount);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		this.internalCount = tag.getInt("internalCount");
		super.readFromNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int inx) {
		if(cacheValid)
			return evalCache[0];
		cacheValid = true;

		DataValue in = inputs.get(0).evaluate();
		DataValue delay = inputs.get(1).evaluate();
		if(in == null || delay == null)
			return null;
		int delayInt = Math.abs(Math.round(delay.getNumber()));

		if(in.getBoolean() || (internalCount != 0)) {
			if(internalCount == delayInt) {
				internalCount = 0;
				return evalCache[0] = in;
			}
			internalCount++;
		}

		return evalCache[0] = new DataValueFloat(0);
	}
}
