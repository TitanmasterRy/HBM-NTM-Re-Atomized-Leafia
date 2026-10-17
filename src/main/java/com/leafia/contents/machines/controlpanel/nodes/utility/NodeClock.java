package com.leafia.contents.machines.controlpanel.nodes.utility;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;

/**
 * A square wave counted in evaluations-per-tick (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeClock}.
 *
 * <p>Output is 1 for {@code Uptime} counts and 0 for {@code Downtime} counts, repeating. The counter advances <b>once per server
 * tick</b> however many times its graph is evaluated in that tick ({@link AddonNodeSupport#firstThisTick}), and only when the
 * graph is evaluated at all: a clock in a {@code tick} receive graph counts game ticks, one in a button's graph counts presses.
 * An Uptime of 0 or less is a constant 0; a Downtime of 0 or less is a constant 1 (the counter does not move for either).
 * Both are read as integers (truncated). The counter is saved.
 */
public class NodeClock extends Node {

	public int internalCounter = 0;

	public NodeClock(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Clock Signal", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Uptime", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		this.inputs.add(new NodeConnection("Downtime", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public DataValue evaluate(int i) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.size() < 2)
			return null;
		float value = 0;
		DataValue d0 = inputs.get(0).evaluate();
		DataValue d1 = inputs.get(1).evaluate();
		if(d0 == null || d1 == null)
			return null;
		cacheValid = true;
		int uptime = (int) (d0.getNumber());
		int downtime = (int) (d1.getNumber());
		if(uptime <= 0)
			return evalCache[0] = new DataValueFloat(0);
		if(downtime <= 0)
			return evalCache[0] = new DataValueFloat(1);
		// uptime + downtime can overflow for a hostile pair of huge values; a negative total would make floorMod throw or go wrong
		long total = (long) uptime + downtime;
		if(AddonNodeSupport.firstThisTick(this))
			internalCounter++;
		internalCounter = (int) Math.floorMod((long) internalCounter, total);
		if(internalCounter < uptime)
			value = 1;
		return evalCache[0] = new DataValueFloat(value);
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorUtility;
	}

	@Override
	public String getDisplayName() {
		return "Clock";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		internalCounter = tag.getInt("counter");
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_clock");
		tag.putInt("counter", internalCounter);
		return super.writeToNBT(tag, sys);
	}
}
