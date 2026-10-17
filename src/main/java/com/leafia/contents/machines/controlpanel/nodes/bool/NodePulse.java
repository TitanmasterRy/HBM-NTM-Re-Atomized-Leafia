package com.leafia.contents.machines.controlpanel.nodes.bool;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * Turns an edge of its input into a one-event pulse (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodePulse}.
 *
 * <p>The input is remembered between events ({@link #wasOn}, saved), and the output is 1 for the event on which the input
 * differs from what it was: rising for {@code OFF -> ON}, falling for {@code ON -> OFF}, either for {@code ALWAYS}. The input is
 * read once per event (the result is cached), so two consumers see the same pulse. An input with no value changes nothing.
 */
public class NodePulse extends Node {

	public enum PulseMode { OFF_ON, ON_OFF, ALWAYS }

	public boolean wasOn = false;
	public PulseMode mode = PulseMode.OFF_ON;

	public NodePulse(float x, float y) {
		super(x, y);
		NodeDropdown modeSelector = new NodeDropdown(this, otherElements.size(), s -> {
			try {
				mode = PulseMode.valueOf(s.replace(" -> ", "_"));
			} catch(IllegalArgumentException ignored) {}
			return null;
		}, () -> mode.name().replace("_", " -> "));
		for(PulseMode value : PulseMode.values())
			modeSelector.list.addItems(value.name().replace("_", " -> "));
		otherElements.add(modeSelector);
		this.outputs.add(new NodeConnection("Pulse", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		this.inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public DataValue evaluate(int i) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.isEmpty())
			return null;
		DataValue d0 = inputs.get(0).evaluate();
		if(d0 != null) {
			cacheValid = true;
			boolean value = d0.getBoolean();
			float output = 0;
			if(value && !wasOn) {
				if(mode == PulseMode.ALWAYS || mode == PulseMode.OFF_ON)
					output = 1;
			} else if(!value && wasOn) {
				if(mode == PulseMode.ALWAYS || mode == PulseMode.ON_OFF)
					output = 1;
			}
			wasOn = value;
			return evalCache[0] = new DataValueFloat(output);
		}
		return null;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorBoolean;
	}

	@Override
	public String getDisplayName() {
		return "Pulse";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		wasOn = tag.getBoolean("wasOn");
		try {
			mode = PulseMode.valueOf(tag.getString("mode"));
		} catch(IllegalArgumentException ignored) {}
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_pulse");
		tag.putBoolean("wasOn", wasOn);
		tag.putString("mode", mode.name());
		return super.writeToNBT(tag, sys);
	}
}
