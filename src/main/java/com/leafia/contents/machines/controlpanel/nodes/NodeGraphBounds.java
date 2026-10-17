package com.leafia.contents.machines.controlpanel.nodes;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;

import net.minecraft.nbt.CompoundTag;

/**
 * "Graph Bounds": the highest and the lowest sample the graph instrument holds right now (CCP-INSTRUMENTS-1, D-3071). Adapted from
 * Leafia's {@code NodeGraphBounds}; the {@code nodeType} {@code leafia_graph_bounds} is upstream's. Wired to the graph's own
 * {@code minValue}/{@code maxValue} it makes a chart that autoscales. Offered only in a graph's own node editor.
 *
 * <p>An empty or all-zero window reads 0 and 0; a node in any other control's graph reads 0. A NaN sample is skipped by the
 * comparisons exactly as the {@code Double} loop upstream skipped it (NaN is neither above nor below anything), unless it is the
 * first, which then stays the extreme until something compares true against it - kept as upstream has it.
 */
public class NodeGraphBounds extends Node {

	public Control ctrl;

	public NodeGraphBounds(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		outputs.add(new NodeConnection("Highest", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(1)));
		outputs.add(new NodeConnection("Lowest", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		recalcSize();
	}

	@Override
	public DataValue evaluate(int i) {
		if(ctrl instanceof Graph graph) {
			Double max = null;
			Double min = null;
			for(double value : graph.values) {
				if(max == null || value > max)
					max = value;
				if(min == null || value < min)
					min = value;
			}
			if(max == null)
				max = 0d;
			if(min == null)
				min = 0d;
			if(i == 0)
				return new DataValueFloat((float) (double) max);
			else if(i == 1)
				return new DataValueFloat((float) (double) min);
		}
		return new DataValueFloat(0);
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Graph Bounds";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_graph_bounds");
		return super.writeToNBT(tag, sys);
	}
}
