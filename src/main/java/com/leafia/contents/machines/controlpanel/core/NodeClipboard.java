package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.nodes.IFunctionNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IOutputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCancelEvent;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;

/**
 * Copy and paste for the node editor: the editor half of NTM-CE's {@code NodeSystem} that has no client type in it
 * (CCP-NODE-EDITOR, D-3067), as static functions over a {@link NodeSystem} so a test can run them.
 *
 * <p>A copy is the selected nodes written as a small {@code NodeSystem} of their own (so a wire between two selected nodes
 * survives and a wire to a node left behind does not) plus the corner they hang from. A paste reads that back into a fresh
 * system and moves its nodes into the target, offset so the corner lands where the player asked; each pasted node keeps its
 * function body. Whether a paste is allowed ({@link #canPaste}) is CE's three checks: no node the other kind of graph cannot hold
 * (a cancel in a receive graph, a broadcast in a send graph - CE's test had these two swapped, see {@code hasCompatibleGraphNodes}), no event-data output the target event does not carry (or carries
 * as another type), and no variable reference the target control does not have (or has as another type).
 *
 * <p>CE named the clipboard anchor keys {@code clipboardAnchorX/Y}; kept, so a clipboard tag reads the same. The clipboard is
 * client memory only and never leaves the editor: what a paste adds is saved, and checked by the server, like any other edit.
 */
public final class NodeClipboard {

	private NodeClipboard() {}

	public static final String ANCHOR_X = "clipboardAnchorX";
	public static final String ANCHOR_Y = "clipboardAnchorY";

	/** The selected nodes of {@code from}, in graph order, as a clipboard tag; null if none of them is in the graph. */
	public static CompoundTag writeSelected(NodeSystem from, Collection<Node> selected) {
		if(selected == null || selected.isEmpty())
			return null;

		List<Node> ordered = new ArrayList<>();
		for(Node node : from.nodes) {
			if(selected.contains(node))
				ordered.add(node);
		}
		if(ordered.isEmpty())
			return null;

		NodeSystem subset = new NodeSystem(from.parent);
		subset.nodes.addAll(ordered);
		for(Map.Entry<String, DataValue> e : from.varsView().entrySet())
			subset.setVar(e.getKey(), e.getValue());
		for(Node node : ordered) {
			if(node instanceof IOutputNode out)
				subset.outputNodes.add(out);
			if(node instanceof IFunctionNode && from.subSystems.containsKey(node))
				subset.subSystems.put(node, from.subSystems.get(node));
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		for(Node node : ordered) {
			minX = Math.min(minX, node.posX);
			minY = Math.min(minY, node.posY);
		}

		CompoundTag tag = subset.writeToNBT(new CompoundTag());
		tag.putFloat(ANCHOR_X, minX);
		tag.putFloat(ANCHOR_Y, minY);
		return tag;
	}

	/**
	 * Adds the clipboard's nodes to {@code into}, their top left corner at ({@code x}, {@code y}), and returns them in order. A
	 * socket that was wired to something not copied is left unwired.
	 */
	public static List<Node> paste(NodeSystem into, CompoundTag tag, float x, float y) {
		if(tag == null || tag.isEmpty())
			return new ArrayList<>();

		NodeSystem pasted = new NodeSystem(into.parent);
		pasted.readFromNBT(tag);

		float anchorX = tag.contains(ANCHOR_X) ? tag.getFloat(ANCHOR_X) : 0;
		float anchorY = tag.contains(ANCHOR_Y) ? tag.getFloat(ANCHOR_Y) : 0;
		float dX = x - anchorX;
		float dY = y - anchorY;

		List<Node> nodes = new ArrayList<>(pasted.nodes.size());
		for(Node node : pasted.nodes) {
			for(NodeConnection input : node.inputs) {
				if(input.connection == null) {
					input.connectionIndex = -1;
					input.drawsLine = false;
				}
			}
			// Output sockets keep no wire, but CE reset them as well "just to be safe".
			for(NodeConnection output : node.outputs) {
				if(output.connection == null) {
					output.connectionIndex = -1;
					output.drawsLine = false;
				}
			}
			node.setPosition(node.posX + dX, node.posY + dY);
			into.addNode(node);
			if(node instanceof IFunctionNode) {
				NodeSystem sub = pasted.subSystems.get(node);
				if(sub != null)
					into.subSystems.put(node, sub);
			}
			nodes.add(node);
		}
		return nodes;
	}

	/**
	 * Whether this clipboard may be pasted into a graph for {@code evt}: {@code sendGraph} is true for a send graph, whose event
	 * data adds {@code to index} where a receive graph's adds {@code tag} and {@code from index}.
	 */
	public static boolean canPaste(NodeSystem into, CompoundTag tag, ControlEvent evt, boolean sendGraph) {
		if(tag == null || tag.isEmpty() || evt == null)
			return false;

		NodeSystem pasted = new NodeSystem(into.parent);
		try {
			pasted.readFromNBT(tag);
		} catch(RuntimeException e) {
			return false;
		}
		Map<String, DataValue> allowed = new Object2ObjectOpenHashMap<>(evt.vars);
		if(sendGraph) {
			allowed.put("to index", new DataValueFloat(0));
		} else {
			allowed.put("tag", new DataValueString(""));
			allowed.put("from index", new DataValueFloat(0));
		}
		return hasCompatibleGraphNodes(pasted, sendGraph)
				&& hasCompatibleEventInputs(pasted, allowed, sendGraph ? "to index" : "from index")
				&& hasCompatibleVariableReferences(pasted, into.parent);
	}

	private static boolean hasCompatibleGraphNodes(NodeSystem sys, boolean sendGraph) {
		for(Node node : sys.nodes) {
			// The graph the menu offers a node to is the graph it may be pasted into (NCStockOutput): a send graph cancels and does
			// not broadcast, a receive graph broadcasts and does not cancel. CE's check had the two the other way round, which
			// refused to paste a node the menu would make and allowed one the menu would not (D-3067).
			if(sendGraph) {
				if(node instanceof NodeEventBroadcast)
					return false;
			} else if(node instanceof NodeCancelEvent) {
				return false;
			}
			if(node instanceof IFunctionNode) {
				NodeSystem sub = sys.subSystems.get(node);
				if(sub != null && !hasCompatibleGraphNodes(sub, sendGraph))
					return false;
			}
		}
		return true;
	}

	private static boolean hasCompatibleEventInputs(NodeSystem sys, Map<String, DataValue> allowed, String indexName) {
		for(Node node : sys.nodes) {
			if(node instanceof NodeInput input && !isCompatibleEventInput(input, allowed, indexName))
				return false;
			if(node instanceof IFunctionNode) {
				NodeSystem sub = sys.subSystems.get(node);
				if(sub != null && !hasCompatibleEventInputs(sub, allowed, indexName))
					return false;
			}
		}
		return true;
	}

	private static boolean isCompatibleEventInput(NodeInput input, Map<String, DataValue> allowed, String indexName) {
		if(!"Event Data".equals(input.name))
			return true;
		for(NodeConnection output : input.outputs) {
			if(indexName.equals(output.name)) {
				if(output.type != DataValue.DataType.NUMBER)
					return false;
				continue;
			}
			DataValue ok = allowed.get(output.name);
			if(ok == null || ok.getType() != output.type)
				return false;
		}
		return true;
	}

	private static boolean hasCompatibleVariableReferences(NodeSystem sys, Control target) {
		for(Node node : sys.nodes) {
			if(node instanceof NodeGetVar getVar && !hasCompatibleVariableReference(target, getVar.global, getVar.varName, getVar.outputs))
				return false;
			if(node instanceof NodeSetVar setVar && !hasCompatibleVariableReference(target, setVar.global, setVar.varName, setVar.inputs))
				return false;
			if(node instanceof IFunctionNode) {
				NodeSystem sub = sys.subSystems.get(node);
				if(sub != null && !hasCompatibleVariableReferences(sub, target))
					return false;
			}
		}
		return true;
	}

	private static boolean hasCompatibleVariableReference(Control target, boolean global, String varName, List<NodeConnection> ports) {
		if(varName == null || varName.isEmpty())
			return true;
		DataValue value = global ? (target.panel == null ? null : target.panel.globalVars.get(varName)) : target.vars.get(varName);
		if(value == null || ports.isEmpty())
			return false;
		DataValue.DataType expected = ports.get(0).type;
		return expected == DataValue.DataType.GENERIC || expected == value.getType();
	}
}
