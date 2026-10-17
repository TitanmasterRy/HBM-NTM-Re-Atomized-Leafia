package com.leafia.contents.machines.controlpanel.nodes.utility;

import java.util.Arrays;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.nodes.NodeBulkQuery.CombineMethod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/**
 * A rolling window of values, combined (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeSummarizer}, line for line,
 * including what looks like its off-by-one (below).
 *
 * <p>Inputs: {@code Enable} (while true, every evaluation pushes {@code New Value}), {@code Clear} (empties the window), and
 * {@code Trim Count} (the window's length, clamped to 1..500; changing it empties the window). A window that is full drops its
 * oldest value. The output is the window combined by the dropdown's {@link CombineMethod}. The window and its fill count are
 * saved.
 *
 * <p><b>Two upstream behaviours, kept and flagged.</b> (1) The node is not cached, so every consumer that reads it pushes
 * the value again; one consumer per event is the intended use. (2) {@code AVERAGE} stops summing when it has counted
 * {@code needle - 1} values (and after the first when {@code needle} is 0 or 1), so it averages the <b>oldest {@code needle - 1}</b>
 * of the window's {@code needle} filled slots, not all of them. Whether that is intended is the owner's to say; the table in the
 * tests pins it as it is.
 */
public class NodeSummarizer extends Node {

	public static final int MAX_LENGTH = 500;

	public CombineMethod combineMethod = CombineMethod.AVERAGE;
	public float[] internalList = new float[40];
	public int needle = 0;

	public NodeSummarizer(float x, float y) {
		super(x, y);
		NodeDropdown combineSelector = new NodeDropdown(this, otherElements.size(), s -> {
			try {
				combineMethod = CombineMethod.valueOf(s);
			} catch(IllegalArgumentException ignored) {}
			return null;
		}, () -> combineMethod.name());
		this.otherElements.add(combineSelector);
		for(CombineMethod value : CombineMethod.values())
			combineSelector.list.addItems(value.name());
		this.outputs.add(new NodeConnection("Summary", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Enable", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("New Value", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Clear", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Trim Count", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(20)));
		recalcSize();
	}

	@Override
	public DataValue evaluate(int unused) {
		if(inputs.size() < 4)
			return null;
		DataValue enable = inputs.get(0).evaluate();
		DataValue newValue = inputs.get(1).evaluate();
		DataValue clear = inputs.get(2).evaluate();
		DataValue trim = inputs.get(3).evaluate();
		if(enable == null || newValue == null || clear == null || trim == null)
			return null;
		// Leafia's cast of a NaN trim count was 0, and an empty window then threw; a window is at least one long
		int length = Math.max(1, (int) Mth.clamp(trim.getNumber(), 1, MAX_LENGTH));
		if(length != internalList.length)
			internalList = new float[length];
		if(enable.getBoolean()) {
			if(needle >= internalList.length) {
				for(int i = 0; i < internalList.length - 1; i++)
					internalList[i] = internalList[i + 1];
				internalList[internalList.length - 1] = newValue.getNumber();
			} else {
				internalList[needle] = newValue.getNumber();
				needle++;
			}
		}
		if(clear.getBoolean()) {
			Arrays.fill(internalList, 0);
			needle = 0;
		}
		float value = 0;
		int total = 0;
		boolean firstTime = true;
		for(float v : internalList) {
			boolean stopLoop = false;
			switch(combineMethod) {
				case ADD -> value += v;
				case MULTIPLY -> value *= v;
				case AVERAGE -> {
					value += v;
					total++;
					if(total >= needle - 1)
						stopLoop = true;
				}
				case HIGHEST -> {
					if(firstTime)
						value = v;
					else
						value = Math.max(v, value);
					firstTime = false;
				}
				case LOWEST -> {
					if(firstTime)
						value = v;
					else
						value = Math.min(v, value);
					firstTime = false;
				}
			}
			if(stopLoop)
				break;
		}
		if(total > 0 && combineMethod == CombineMethod.AVERAGE)
			value /= total;
		return new DataValueFloat(value);
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorUtility;
	}

	@Override
	public String getDisplayName() {
		return "Summarizer";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_summarizer");
		tag.putString("combineMethod", combineMethod.name());
		ListTag list = new ListTag();
		for(float v : internalList)
			list.add(FloatTag.valueOf(v));
		tag.put("list", list);
		tag.putInt("needle", needle);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		try {
			combineMethod = CombineMethod.valueOf(tag.getString("combineMethod"));
		} catch(IllegalArgumentException ignored) {}
		if(tag.contains("list")) {
			ListTag list = tag.getList("list", Tag.TAG_FLOAT);
			// a window is 1..500 long; a saved list outside that is a hand-made tag
			internalList = new float[Mth.clamp(list.size(), 1, MAX_LENGTH)];
			for(int i = 0; i < internalList.length && i < list.size(); i++)
				internalList[i] = list.getFloat(i);
		}
		needle = Mth.clamp(tag.getInt("needle"), 0, internalList.length);
		super.readFromNBT(tag, sys);
	}
}
