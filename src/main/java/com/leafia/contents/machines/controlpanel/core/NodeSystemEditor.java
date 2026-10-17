package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;

import net.minecraft.nbt.CompoundTag;

/**
 * The editor's state and rules for one {@link NodeSystem}: what is selected, what is being dragged or wired or typed into, and
 * what a click, a release and a key do to the graph (CCP-NODE-EDITOR, D-3067). It is the <b>editor half of NTM-CE's
 * {@code NodeSystem}</b> (its {@code @SideOnly(CLIENT)} fields, {@code onClick}, {@code clickReleased}, {@code keyTyped},
 * {@code removeNode} and the clipboard calls), moved out of the graph class and kept free of any client type.
 *
 * <p>That is why it is in {@code core} and not {@code core.client}: nothing here draws or names a screen. Positions arrive as
 * numbers (a click is given in <i>grid</i> space and in <i>screen</i> space, and whether Shift is down), so a GameTest can drive
 * every editing operation a player has, and the screen {@code SubElementNodeEditor} only turns mouse and keys into these calls.
 * Where CE read {@code Keyboard.isKeyDown(KEY_LSHIFT)} the caller passes {@code shift}.
 *
 * <h2>Divergences from CE (each a hardening; a graph CE could build, this can build)</h2>
 * <ul>
 * <li>A wire that would make a node depend on itself is refused at the moment it is made ({@link #wouldCycle}); CE let it be
 * drawn, and the server (D-3063) then refused the whole panel when it was saved.</li>
 * <li>{@link #removeNode} also unwires every socket that was wired to the node, and drops the focus if it was in the node; CE
 * left the other nodes pointing at a node that was no longer in the graph.</li>
 * <li>Text typed into a box is committed when the editor closes ({@link #stopTyping}) instead of being lost.</li>
 * </ul>
 */
public class NodeSystemEditor {

	public final NodeSystem system;

	public Node activeNode;
	public final List<Node> selectedNodes = new ArrayList<>();
	public NodeConnection connectionInProgress;
	public ITypableNode currentTypingBox;
	public boolean drag;
	public float dragDist;
	public float lastMouseX;
	public float lastMouseY;

	public NodeSystemEditor(NodeSystem system) {
		this.system = system;
	}

	/** CE's {@code removeClientData}: forget the selection and the focus. Typed text is committed first. */
	public void reset() {
		stopTyping();
		activeNode = null;
		selectedNodes.clear();
		connectionInProgress = null;
		drag = false;
		dragDist = 0;
	}

	// ------------------------------------------------------------------ the graph

	/** Puts a node in the graph and makes it the active one, as the add menu does. */
	public void addNode(Node node) {
		system.addNode(node);
		activeNode = node;
	}

	public boolean hasSelectedNodes() {
		return !selectedNodes.isEmpty();
	}

	/** Takes a node out, unwires everything that pointed at it, and drops any focus that was inside it. */
	public void removeNode(Node n) {
		if(activeNode == n)
			activeNode = null;
		selectedNodes.remove(n);
		if(connectionInProgress != null && connectionInProgress.parent == n)
			connectionInProgress = null;
		if(currentTypingBox instanceof NodeElement e && e.parent == n) {
			currentTypingBox.stopTyping();
			currentTypingBox = null;
		}
		for(Node other : system.nodes) {
			for(NodeConnection in : other.inputs) {
				if(in.connection == n) {
					in.connection = null;
					in.connectionIndex = -1;
					in.drawsLine = false;
				}
			}
		}
		system.removeNode(n);
	}

	/** Deletes the selected nodes (CE's Delete and X). */
	public void removeSelected() {
		for(Node n : new ArrayList<>(selectedNodes))
			removeNode(n);
	}

	// ------------------------------------------------------------------ the clipboard

	public CompoundTag copySelection() {
		CompoundTag tag = NodeClipboard.writeSelected(system, selectedNodes);
		return tag == null ? null : tag.copy();
	}

	/** Pastes at a grid position and selects what was pasted, as CE did. Returns the nodes, empty if nothing was. */
	public List<Node> paste(CompoundTag clipboard, float x, float y) {
		List<Node> pasted = NodeClipboard.paste(system, clipboard.copy(), x, y);
		if(pasted.isEmpty())
			return pasted;
		selectedNodes.clear();
		selectedNodes.addAll(pasted);
		activeNode = pasted.get(pasted.size() - 1);
		connectionInProgress = null;
		stopTyping();
		return pasted;
	}

	// ------------------------------------------------------------------ the mouse

	/** CE's {@code render} first block: while the mouse is down on a node, the selection follows it. */
	public void dragTo(float screenX, float screenY, float gridScale, boolean shift) {
		if(drag && !shift) {
			float distX = screenX - lastMouseX;
			float distY = screenY - lastMouseY;
			dragDist += Math.sqrt(distX * distX + distY * distY);
			for(Node n : selectedNodes)
				n.setPosition(n.posX + (screenX - lastMouseX) * gridScale, n.posY + (screenY - lastMouseY) * gridScale);
			for(Node n : system.nodes)
				n.setPosition(n.posX, n.posY); // to fix elements not rendering in edit mode
			lastMouseX = screenX;
			lastMouseY = screenY;
		}
	}

	/**
	 * While a wire is being dragged, where it should end: the centre of the opposite-kind port under the mouse, or null if the
	 * mouse is over none (the wire then ends at the mouse).
	 */
	public float[] wireSnapPoint(float gridMX, float gridMY) {
		if(connectionInProgress == null)
			return null;
		for(int i = system.nodes.size() - 1; i >= 0; i--) {
			Node n = system.nodes.get(i);
			if(ControlGeometry.intersects2DBox(gridMX, gridMY, n.getExtendedBoundingBox())) {
				for(NodeConnection c : (connectionInProgress.isInput ? n.outputs : n.inputs)) {
					if(connectionInProgress.parent != c.parent && ControlGeometry.intersects2DBox(gridMX, gridMY, c.getPortBox())) {
						float[] box = c.getPortBox();
						return new float[]{(box[0] + box[2]) / 2F, (box[1] + box[3]) / 2F};
					}
				}
			}
		}
		return null;
	}

	/** The button a click at this grid position lands on, or null. Also remembers where the mouse is, as CE did. */
	public NodeElement getNodeElementPressed(float gridMX, float gridMY, float screenX, float screenY) {
		lastMouseX = screenX;
		lastMouseY = screenY;
		for(int i = system.nodes.size() - 1; i >= 0; i--) {
			for(NodeElement e : system.nodes.get(i).otherElements) {
				if(e instanceof NodeButton) {
					if(e.onClick(gridMX, gridMY))
						return e;
				}
			}
		}
		return null;
	}

	/** CE's {@code NodeSystem.onClick}: element clicks first, then wires, then selection and the start of a drag. */
	public void onClick(float gridMX, float gridMY, float screenX, float screenY, boolean shift) {
		lastMouseX = screenX;
		lastMouseY = screenY;
		// Click handling (dropdowns, buttons)
		for(int i = system.nodes.size() - 1; i >= 0; i--) {
			if(system.nodes.get(i).onClick(gridMX, gridMY))
				return;
		}
		// Do line connection handling
		for(int i = system.nodes.size() - 1; i >= 0; i--) {
			Node n = system.nodes.get(i);
			if(ControlGeometry.intersects2DBox(gridMX, gridMY, n.getExtendedBoundingBox())) {
				List<NodeConnection> union = new ArrayList<>();
				union.addAll(n.inputs);
				union.addAll(n.outputs);
				for(NodeConnection c : union) {
					if(ControlGeometry.intersects2DBox(gridMX, gridMY, c.getPortBox())) {
						if(c.connection != null) {
							connectionInProgress = c.removeConnection();
							connectionInProgress.drawsLine = true;
						} else {
							connectionInProgress = c;
							c.drawsLine = true;
						}
						return;
					}
				}
			}
		}
		drag = true;
		dragDist = 0;
		if(!shift && selectedNodes.size() <= 1) {
			selectedNodes.clear();
			activeNode = null;
		}
		boolean clear = true;
		for(int i = system.nodes.size() - 1; i >= 0; i--) {
			Node n = system.nodes.get(i);
			boolean intersectsBox = ControlGeometry.intersects2DBox(gridMX, gridMY, n.getBoundingBox());
			for(NodeConnection c : n.inputs) {
				if(c.enumSelector != null) {
					stopTyping();
					if(c.enumSelector.onClick(gridMX, gridMY))
						return;
				}
				if(intersectsBox && ControlGeometry.intersects2DBox(gridMX, gridMY, c.getValueBox())) {
					if(currentTypingBox != c && c.connection == null && c.type != DataType.COMPOSITE) {
						c.isTyping = true;
						c.startTyping();
						if(currentTypingBox != null)
							currentTypingBox.stopTyping();
						currentTypingBox = c;
					}
				}
			}
			for(NodeElement o : n.otherElements) {
				if(o instanceof ITypableNode b) { // CE's NodeTextBox, and any element an addon makes typable
					if(intersectsBox && ControlGeometry.intersects2DBox(gridMX, gridMY, b.getValueBox())) {
						if(currentTypingBox != b) {
							b.startTyping();
							if(currentTypingBox != null)
								currentTypingBox.stopTyping();
							currentTypingBox = b;
						}
					}
				}
			}
			if(intersectsBox) {
				clear = false;
				if(activeNode == n && selectedNodes.size() <= 1) {
					selectedNodes.remove(n);
					activeNode = null;
				} else {
					if(!selectedNodes.contains(n))
						selectedNodes.add(n);
					activeNode = n;
				}
				break;
			}
		}
		if(currentTypingBox != null && !ControlGeometry.intersects2DBox(gridMX, gridMY, currentTypingBox.getValueBox())) {
			currentTypingBox.stopTyping();
			currentTypingBox = null;
		}
		if(clear) {
			selectedNodes.clear();
			activeNode = null;
		}
	}

	/** CE's {@code clickReleased}: a wire let go over a port joins them; anywhere else it is dropped. */
	public void clickReleased(float gridMX, float gridMY, boolean shift) {
		if(connectionInProgress != null) {
			for(int i = system.nodes.size() - 1; i >= 0; i--) {
				Node n = system.nodes.get(i);
				if(ControlGeometry.intersects2DBox(gridMX, gridMY, n.getExtendedBoundingBox())) {
					for(NodeConnection c : (connectionInProgress.isInput ? n.outputs : n.inputs)) {
						if(connectionInProgress.parent != c.parent && ControlGeometry.intersects2DBox(gridMX, gridMY, c.getPortBox())) {
							if(!connect(connectionInProgress, c, n)) {
								connectionInProgress.drawsLine = false;
							}
							connectionInProgress = null;
							return;
						}
					}
				}
			}
			connectionInProgress.drawsLine = false;
			connectionInProgress = null;
		}
		if(!shift && dragDist == 0) {
			for(int i = system.nodes.size() - 1; i >= 0; i--) {
				Node n = system.nodes.get(i);
				if(ControlGeometry.intersects2DBox(gridMX, gridMY, n.getBoundingBox())) {
					selectedNodes.clear();
					selectedNodes.add(n);
					activeNode = n;
					break;
				}
			}
		}
		drag = false;
	}

	/**
	 * Joins a wire that was dragged from {@code held} to the port {@code target} of node {@code targetNode}. Only an input keeps
	 * the wire (CE: "only input nodes draw lines"), so whichever of the two is the input records the other. Returns false, and
	 * changes nothing, if the wire would make the graph depend on itself.
	 */
	public boolean connect(NodeConnection held, NodeConnection target, Node targetNode) {
		NodeConnection input = held.isInput ? held : target;
		NodeConnection output = held.isInput ? target : held;
		if(wouldCycle(input.parent, output.parent))
			return false;
		target.removeConnection();
		if(target.isInput) {
			held.drawsLine = false;
			target.drawsLine = true;
			target.connection = held.parent;
			target.connectionIndex = held.index;
		} else {
			held.connection = targetNode;
			held.connectionIndex = target.index;
		}
		return true;
	}

	/**
	 * True if wiring an output of {@code producer} into an input of {@code consumer} would make {@code consumer} depend on
	 * itself: that is, if the producer already (transitively) reads from the consumer.
	 */
	public static boolean wouldCycle(Node consumer, Node producer) {
		if(consumer == producer)
			return true;
		Deque<Node> todo = new ArrayDeque<>();
		Map<Node, Boolean> seen = new IdentityHashMap<>();
		todo.push(producer);
		seen.put(producer, true);
		while(!todo.isEmpty()) {
			Node at = todo.pop();
			for(NodeConnection in : at.inputs) {
				Node dep = in.connection;
				if(dep == null)
					continue;
				if(dep == consumer)
					return true;
				if(seen.put(dep, true) == null)
					todo.push(dep);
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the keyboard

	public boolean isTyping() {
		return currentTypingBox != null;
	}

	/** Commits what is being typed and leaves typing. */
	public void stopTyping() {
		if(currentTypingBox != null) {
			currentTypingBox.stopTyping();
			currentTypingBox = null;
		}
	}

	/** A printable character. True if a box took it (typing is on). */
	public boolean typeChar(char c) {
		if(currentTypingBox == null)
			return false;
		currentTypingBox.typeChar(c);
		return true;
	}

	/** Backspace. True if a box was typing (the key is then used up). */
	public boolean backspace() {
		if(currentTypingBox == null)
			return false;
		currentTypingBox.backspace();
		return true;
	}

	/** Enter commits the text. True if a box was typing. */
	public boolean enter() {
		if(currentTypingBox == null)
			return false;
		stopTyping();
		return true;
	}

	// ------------------------------------------------------------------ pruning

	/**
	 * Takes the empty event graphs out of a control. Opening an event in the editor creates its graph, and saving writes it, so
	 * a player who clicked through sixty-five events would send the server a panel it refuses for having too many graphs on one
	 * control (D-3063) - and every one of them is a graph that does nothing. An absent graph and an empty one behave the same.
	 *
	 * @return how many graphs were taken out
	 */
	public static int pruneEmptySystems(Control control) {
		return prune(control.receiveNodeMap) + prune(control.sendNodeMap);
	}

	private static int prune(Map<String, NodeSystem> map) {
		int removed = 0;
		for(Iterator<Map.Entry<String, NodeSystem>> it = map.entrySet().iterator(); it.hasNext();) {
			NodeSystem sys = it.next().getValue();
			if(sys.nodes.isEmpty() && sys.varsView().isEmpty()) {
				it.remove();
				removed++;
			}
		}
		return removed;
	}
}
