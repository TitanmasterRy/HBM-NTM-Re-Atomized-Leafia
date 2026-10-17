package com.leafia.contents.machines.controlpanel.nodes.ror;

import java.util.Map;

import com.hbm.blockentity.network.RTTYSystem;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeOutput;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Publishes a redstone-over-radio signal (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeRoRSender}.
 *
 * <p>When its graph runs it broadcasts {@code Signal} on {@code Frequency} through the base mod's {@link RTTYSystem#broadcast}
 * for the control's own dimension, to be read one tick later; that is its only reach. A number is sent as the integer part of
 * its value ({@code 7.9} is "7"), anything else as its text. Two numbers sent to one channel in one tick add, which is the bus's
 * rule, not this node's.
 *
 * <p><b>Hardening, and a hand-off.</b> A frequency or a signal longer than {@link #MAX_FREQUENCY} / {@link AddonNodeSupport#MAX_TEXT}
 * characters, and a frequency that is empty, are not sent. The bus never forgets a channel it has published on (it is cleared
 * only with its level), so a graph that builds a new frequency every tick, with a clock and a format node, grows it without
 * limit; this node cannot bound that from outside the base mod, and it is recorded as a hand-off in D-3070.
 *
 * <p>{@link #doOutput} returns true, where Leafia's returned false; see {@code NodeSounder} for why that mattered.
 */
public class NodeRoRSender extends NodeOutput {

	public static final int MAX_FREQUENCY = 128;

	public final Control ctrl;

	public NodeRoRSender(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		inputs.add(new NodeConnection("Frequency", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		inputs.add(new NodeConnection("Signal", this, inputs.size(), true, DataType.STRING, new DataValueString("")));
		recalcSize();
		evalCache = new DataValue[1];
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Transmit RoR";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_ror_sender");
		return super.writeToNBT(tag, sys);
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		if(inputs.size() < 2)
			return true;
		DataValue freq = inputs.get(0).evaluate();
		DataValue signal = inputs.get(1).evaluate();
		Level world = AddonNodeSupport.levelOf(ctrl);
		if(signal != null && freq != null && world != null && !world.isClientSide()) {
			String channel = freq.toString();
			String s = signal.toString();
			if(signal instanceof DataValueFloat f)
				s = Integer.toString((int) f.getNumber());
			if(!channel.isEmpty() && channel.length() <= MAX_FREQUENCY && s.length() <= AddonNodeSupport.MAX_TEXT)
				RTTYSystem.broadcast(world, channel, s);
		}
		return true;
	}
}
