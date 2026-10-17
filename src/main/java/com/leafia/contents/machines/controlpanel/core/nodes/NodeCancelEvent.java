package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Cancels the receiver the event broadcast is currently serving when "do cancel" is true: the only node whose
 * {@link #doOutput} returns false. Adapted from NTM-CE (D-3062). An input that evaluates to nothing cancels nothing, where CE
 * threw a {@code NullPointerException}.
 */
public class NodeCancelEvent extends NodeOutput {

	public NodeCancelEvent(float x, float y) {
		super(x, y);
		this.inputs.add(new NodeConnection("do cancel", this, 0, true, DataType.NUMBER, new DataValueFloat(0)));
		recalcSize();
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		DataValue cancel = inputs.get(0).evaluate();
		return cancel == null || !cancel.getBoolean();
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Cancel Event";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "cancelEvent");
		return super.writeToNBT(tag, sys);
	}
}
