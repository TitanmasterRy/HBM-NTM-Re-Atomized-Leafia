package com.leafia.contents.machines.controlpanel.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeOutput;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * "Graph Add": pushes its {@code Value} onto the graph instrument whose graph it sits in (CCP-INSTRUMENTS-1, D-3071). Adapted from
 * Leafia's {@code NodeGraphAdd}; the {@code nodeType} {@code leafia_graph} is upstream's. Offered only in a graph's own node
 * editor, as upstream offered it ({@code NCLeafiaOutput}).
 *
 * <p>A node placed in the graph of any other control, or one whose control is gone, does nothing. <b>Returns true</b> from
 * {@link #doOutput}, where Leafia's returned false: in a <i>send</i> graph false cancels the event for the receiver being asked
 * (D-3070 divergence 1, the same as the sounder's), and only {@code NodeCancelEvent} should cancel. In a receive graph the value
 * is ignored either way, and the graph instrument only has receive graphs.
 */
public class NodeGraphAdd extends NodeOutput {

	public Control ctrl;

	public NodeGraphAdd(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		inputs.add(new NodeConnection("Value", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		recalcSize();
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		if(inputs.isEmpty() || inputs.get(0) == null)
			return true;
		if(ctrl instanceof Graph graph) {
			DataValue value = inputs.get(0).evaluate();
			if(value != null)
				graph.pushValue(value.getNumber());
		}
		return true;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Graph Add";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_graph");
		return super.writeToNBT(tag, sys);
	}
}
