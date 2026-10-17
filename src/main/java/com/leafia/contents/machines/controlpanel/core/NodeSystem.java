package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.leafia.contents.machines.controlpanel.core.nodes.IFunctionNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IInputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IOutputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.lib.LeafiaRef;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A control's node graph for one event: its nodes, its output nodes, its local variables, and the sub-graphs of its function
 * nodes. The <b>evaluation half</b> of NTM-CE's {@code NodeSystem} (D-3061): NBT, node bookkeeping and running an event through
 * the graph.
 *
 * <p>The editor half (selection, dragging, connection-in-progress, typing focus, the line and node drawing, clipboard copy and
 * paste, and the graph-compatibility checks that paste uses) is the client's and belongs to batch 7's {@code NodeSystemEditor}
 * in {@code core.client}; it reads this class's public {@link #nodes} and {@link #subSystems} and needs nothing added here.
 * The checks do not belong in batch 1 because they test for batch 2's node classes.
 *
 * <h2>Running an event</h2>
 * {@link #receiveEvent}: every input node takes the event's variables, every function node whose enable socket is true runs its
 * own sub-graph, and then each output node does its output against the panel's owner, the control's send graphs and its linked
 * positions. Cached socket values are reset first, since nodes cache until told otherwise.
 *
 * <p>Two guards over CE, which would have thrown a {@code NullPointerException} through the sender's tick: a function node whose
 * enable evaluates to nothing is treated as disabled, and one with no saved sub-graph is skipped.
 */
public class NodeSystem {

	public static final ResourceLocation node_tex = LeafiaRef.rl("textures/gui/control_panel/node.png");

	public Control parent;
	public List<Node> nodes = new ArrayList<>();
	public List<IOutputNode> outputNodes = new ArrayList<>();
	private final Map<String, DataValue> vars = new Object2ObjectOpenHashMap<>();

	// an array of subsystems owned by the various nodes sharing a system layer (sublayering is then done recursively)
	public Map<Node, NodeSystem> subSystems = new Object2ObjectOpenHashMap<>();

	public NodeSystem(Control parent) {
		this.parent = parent;
	}

	public void setVar(String name, DataValue val) {
		vars.put(name, val);
	}

	public DataValue getVar(String name) {
		DataValue val = vars.get(name);
		if(val == null)
			return new DataValueFloat(0);
		return val;
	}

	/** A copy of the local variables, for the clipboard and for tests. */
	public Map<String, DataValue> varsView() {
		return new Object2ObjectOpenHashMap<>(vars);
	}

	public CompoundTag writeToNBT(CompoundTag tag) {
		CompoundTag nodes = new CompoundTag();

		for(int i = 0; i < this.nodes.size(); i++) {
			Node node = this.nodes.get(i);
			CompoundTag nodeTag = node.writeToNBT(new CompoundTag(), this);
			if(node instanceof IFunctionNode) {
				NodeSystem sub = subSystems.get(node);
				if(sub != null)
					nodeTag.put("SS", sub.writeToNBT(new CompoundTag()));
			}
			nodes.put("n" + i, nodeTag);
		}
		tag.put("N", nodes);

		CompoundTag vars = new CompoundTag();
		for(Entry<String, DataValue> e : this.vars.entrySet()) {
			vars.put(e.getKey(), e.getValue().writeToNBT());
		}
		tag.put("V", vars);

		return tag;
	}

	public void readFromNBT(CompoundTag tag) {
		this.nodes.clear();
		this.outputNodes.clear();
		this.subSystems.clear();

		CompoundTag nodes = tag.getCompound("N");
		for(int i = 0; i < nodes.getAllKeys().size(); i++) {
			CompoundTag nodeTag = nodes.getCompound("n" + i);
			Node node = Node.nodeFromNBT(nodeTag, this);

			if(node == null)
				node = new UnknownNode(0, 0, nodeTag);

			if(node instanceof IOutputNode out) {
				outputNodes.add(out);
			}
			if(node instanceof IFunctionNode && nodeTag.contains("SS")) {
				NodeSystem subsystem = new NodeSystem(parent);
				subsystem.readFromNBT(nodeTag.getCompound("SS"));
				subSystems.put(node, subsystem);
			}
			this.nodes.add(node);
		}
		for(int i = 0; i < this.nodes.size(); i++) {
			this.nodes.get(i).readFromNBT(nodes.getCompound("n" + i), this);
		}

		CompoundTag vars = tag.getCompound("V");
		for(String k : vars.getAllKeys()) {
			Tag base = vars.get(k);
			DataValue val = DataValue.newFromNBT(base);
			if(val != null) {
				this.vars.put(k, val);
			}
		}
	}

	public void addNode(Node n) {
		nodes.add(n);

		if(n instanceof IFunctionNode && !subSystems.containsKey(n)) {
			subSystems.put(n, new NodeSystem(parent));
		}
		if(n instanceof IOutputNode out) {
			outputNodes.add(out);
		}
	}

	/** Takes a node out of the graph. The connections other nodes hold to it are the caller's to clear, as in CE. */
	public void removeNode(Node n) {
		if(n instanceof IOutputNode out)
			outputNodes.remove(out);
		subSystems.remove(n);
		nodes.remove(n);
	}

	public void resetCachedValues() {
		for(Node n : nodes) {
			n.cacheValid = false;
		}
	}

	public void receiveEvent(ControlPanel panel, Control ctrl, ControlEvent evt) {
		resetCachedValues();
		for(Node n : nodes) {
			if(n instanceof IInputNode in) {
				in.setOutputFromVars(evt.vars);
			}
			if(n instanceof IFunctionNode) {
				DataValue enable = n.evaluate(0);
				if(enable != null && enable.getBoolean()) {
					NodeSystem sub = subSystems.get(n);
					if(sub != null)
						sub.receiveEvent(panel, ctrl, evt);
				}
			}
		}
		for(IOutputNode o : outputNodes) {
			o.doOutput(panel.parent, ctrl.sendNodeMap, ctrl.taggedLinks);
		}
	}
}
