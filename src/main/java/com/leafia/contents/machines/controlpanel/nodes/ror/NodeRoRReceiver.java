package com.leafia.contents.machines.controlpanel.nodes.ror;

import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.blockentity.network.RTTYSystem.RTTYChannel;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Reads a redstone-over-radio channel (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeRoRReceiver}.
 *
 * <p>Input: the frequency, as text. Output: the signal last published on it, as text. The read goes through the base mod's
 * {@link RTTYSystem#listen} for the control's own dimension, which is the only path (no position, no block). If the channel has
 * published nothing the output is the <b>last value this node read</b> (saved with the node, "0" at first), so a quiet channel
 * holds the reading rather than dropping to empty. The result is cached for the event.
 */
public class NodeRoRReceiver extends Node {

	public final Control ctrl;
	public String lastValue = "0";

	public NodeRoRReceiver(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		outputs.add(new NodeConnection("Signal", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		inputs.add(new NodeConnection("Frequency", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		recalcSize();
		evalCache = new DataValue[1];
	}

	@Override
	public DataValue evaluate(int i) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.isEmpty())
			return null;
		String value = lastValue;
		DataValue in = inputs.get(0).evaluate();
		Level world = AddonNodeSupport.levelOf(ctrl);
		if(in != null && world != null) {
			RTTYChannel chan = RTTYSystem.listen(world, in.toString());
			if(chan != null)
				value = AddonNodeSupport.clip("" + chan.signal);
		}
		lastValue = value;
		evalCache[0] = new DataValueString(value);
		cacheValid = true;
		return evalCache[0];
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Receive RoR";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		lastValue = AddonNodeSupport.clip(tag.getString("lastValue"));
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_ror_receiver");
		tag.putString("lastValue", lastValue);
		return super.writeToNBT(tag, sys);
	}
}
