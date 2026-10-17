package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The trust boundary of the Custom Control Panel (D-3063).
 *
 * <p>NTM-CE hands the <b>whole panel tree, authored by the client</b>, to the server and applies it with {@code hasPermission
 * -> true}. Ported as it stood, that is remote control of anything in the world, a class-name lookup on a client string, and
 * unbounded graphs run on the server tick. Everything the port refuses is decided here, in plain functions over tags and over a
 * parsed panel, so that a test can feed each one a hostile input and read the reason back. None of it evaluates a graph.
 *
 * <h2>Three layers</h2>
 * <ol>
 * <li>{@link #scan}: the <b>raw tag</b>, before anything is built from it. Size ceilings (controls, event systems, nodes, links,
 * how deep function nodes nest, how deep the tag is) and the closed enum registry applied to <i>every</i> enum-shaped value
 * wherever it sits, so a bad id in a socket default is refused as well as one in a variable.</li>
 * <li>{@link #graphProblem}: a <b>parsed control</b>: no node depends on itself through its inputs (an evaluate along a cycle is
 * a stack overflow on the server tick), no dependency chain is longer than {@link #MAX_CHAIN}, and no event is broadcast into
 * itself - neither through a send graph that broadcasts an event whose send graph broadcasts it back, nor through a receive graph
 * that re-sends its own event to a link that is the panel itself.</li>
 * <li>{@link #linkProblem}: where the links point. Within the link radius of the panel, and any link that is new was presented by
 * a linker the editing player holds.</li>
 * </ol>
 *
 * <p>The same {@link #scan} and {@link #graphProblem} run on <b>load</b> (a saved panel, a dropped panel's item): a control that
 * fails is kept as an inert {@link UnknownControl} with its tag intact, so nothing is lost and nothing runs.
 *
 * <p>What none of this can stop is an echo through <i>other</i> machines (a panel sends to a machine that answers by sending the
 * same event back); {@link ControlEventSystem} bounds that at run time with a delivery depth.
 */
public final class ControlPanelGuard {

	private ControlPanelGuard() {}

	/** Controls on one panel. A panel's grid is 10 by 10 units and the smallest control takes one. */
	public static final int MAX_CONTROLS = 256;
	/** Nodes on one panel, summed over every control, event and function body. */
	public static final int MAX_NODES = 2048;
	/** Event graphs (send and receive) on one control. */
	public static final int MAX_SYSTEMS_PER_CONTROL = 64;
	/** Links on one control, and on the whole panel. */
	public static final int MAX_LINKS_PER_CONTROL = 64;
	public static final int MAX_LINKS = 512;
	/** Function nodes inside function nodes. */
	public static final int MAX_SYSTEM_DEPTH = 8;
	/** The longest chain of nodes one evaluation may recurse through. */
	public static final int MAX_CHAIN = 128;
	/** How deep any tag in a panel may nest. A legal panel is under thirty. */
	public static final int MAX_TAG_DEPTH = 64;
	/** How many tags a panel may hold. The packet's own 2 MiB ceiling is the harder bound; this keeps the walk short. */
	public static final int MAX_TAG_COUNT = 300_000;

	/**
	 * Checks an addon registers for a node type, run on the raw tag of every node of that {@code nodeType} the scan meets: it
	 * returns why the node's tag must not be read, or null. The IC10 node's program and state are measured through this
	 * (CCP-IC10, D-3073), so the common guard names no addon class.
	 */
	public static final Map<String, Function<CompoundTag, String>> NODE_SCANNERS = new ConcurrentHashMap<>();

	// ------------------------------------------------------------------ the raw tag

	/** Why this panel tag must not be read, or null when it may. Builds nothing and evaluates nothing. */
	public static String scan(CompoundTag panelTag) {
		if(panelTag == null)
			return "no panel tag";

		CompoundTag controls = panelTag.getCompound("controls");
		if(controls.size() > MAX_CONTROLS)
			return "more than " + MAX_CONTROLS + " controls";

		int[] nodes = {0};
		int links = 0;
		for(String key : controls.getAllKeys()) {
			CompoundTag ct = controls.getCompound(key);
			for(String which : new String[]{"SN", "RN"}) {
				CompoundTag systems = ct.getCompound(which);
				if(systems.size() > MAX_SYSTEMS_PER_CONTROL)
					return "more than " + MAX_SYSTEMS_PER_CONTROL + " event graphs on a control";
				for(String event : systems.getAllKeys()) {
					String why = scanSystem(systems.getCompound(event), 0, nodes);
					if(why != null)
						return why;
				}
			}
			int here = ct.getCompound("taglnk").size();
			if(here > MAX_LINKS_PER_CONTROL)
				return "more than " + MAX_LINKS_PER_CONTROL + " links on a control";
			links += here;
		}
		if(links > MAX_LINKS)
			return "more than " + MAX_LINKS + " links on a panel";

		return scanTags(panelTag);
	}

	private static String scanSystem(CompoundTag system, int depth, int[] nodes) {
		CompoundTag n = system.getCompound("N");
		nodes[0] += n.size();
		if(nodes[0] > MAX_NODES)
			return "more than " + MAX_NODES + " nodes";
		for(String key : n.getAllKeys()) {
			CompoundTag node = n.getCompound(key);
			Function<CompoundTag, String> scanner = NODE_SCANNERS.get(node.getString("nodeType"));
			if(scanner != null) {
				String problem = scanner.apply(node);
				if(problem != null)
					return problem;
			}
			if(node.contains("SS", Tag.TAG_COMPOUND)) {
				if(depth + 1 > MAX_SYSTEM_DEPTH)
					return "function nodes nested deeper than " + MAX_SYSTEM_DEPTH;
				String why = scanSystem(node.getCompound("SS"), depth + 1, nodes);
				if(why != null)
					return why;
			}
		}
		return null;
	}

	/** One walk over every tag: depth, count, and the closed enum registry wherever an enum-shaped value sits. */
	private static String scanTags(CompoundTag root) {
		Deque<Tag> stack = new ArrayDeque<>();
		Deque<Integer> depths = new ArrayDeque<>();
		stack.push(root);
		depths.push(1);
		int count = 0;
		while(!stack.isEmpty()) {
			Tag t = stack.pop();
			int depth = depths.pop();
			if(++count > MAX_TAG_COUNT)
				return "more than " + MAX_TAG_COUNT + " tags";
			if(depth > MAX_TAG_DEPTH)
				return "nested deeper than " + MAX_TAG_DEPTH + " tags";
			if(t instanceof CompoundTag c) {
				if(c.contains(DataValueEnum.KEY_ORDINAL, Tag.TAG_INT)) {
					String why = enumProblem(c);
					if(why != null)
						return why;
				}
				for(String k : c.getAllKeys()) {
					Tag child = c.get(k);
					if(child instanceof CompoundTag || child instanceof ListTag) {
						stack.push(child);
						depths.push(depth + 1);
					}
				}
			} else if(t instanceof ListTag l) {
				for(Tag child : l) {
					if(child instanceof CompoundTag || child instanceof ListTag) {
						stack.push(child);
						depths.push(depth + 1);
					}
				}
			}
		}
		return null;
	}

	private static String enumProblem(CompoundTag c) {
		String id = c.getString(DataValueEnum.KEY_ENUM);
		Class<? extends Enum<?>> clazz = DataValueEnum.classOf(id);
		if(clazz == null)
			return "enum id '" + trim(id) + "' is not in the registry";
		int ordinal = c.getInt(DataValueEnum.KEY_ORDINAL);
		if(ordinal < 0 || ordinal >= clazz.getEnumConstants().length)
			return "ordinal " + ordinal + " is out of range for enum '" + id + "'";
		return null;
	}

	private static String trim(String s) {
		return s.length() <= 40 ? s : s.substring(0, 40) + "...";
	}

	// ------------------------------------------------------------------ the parsed control

	/** Why this parsed control must not run, or null when it may. Walks its graphs and evaluates none. */
	public static String graphProblem(Control control) {
		for(Map.Entry<String, NodeSystem> e : control.receiveNodeMap.entrySet()) {
			String why = systemProblem(e.getValue());
			if(why != null)
				return "receive graph '" + e.getKey() + "': " + why;
		}
		for(Map.Entry<String, NodeSystem> e : control.sendNodeMap.entrySet()) {
			String why = systemProblem(e.getValue());
			if(why != null)
				return "send graph '" + e.getKey() + "': " + why;
		}

		// A send graph runs once per receiver and may itself broadcast; follow the event names it broadcasts to see if one comes back.
		Map<String, Set<String>> sendEdges = new HashMap<>();
		for(Map.Entry<String, NodeSystem> e : control.sendNodeMap.entrySet()) {
			Set<String> out = new HashSet<>();
			broadcasts(e.getValue(), out);
			sendEdges.put(e.getKey(), out);
		}
		for(String start : sendEdges.keySet()) {
			if(reaches(sendEdges, start, start))
				return "send graph '" + start + "' broadcasts an event whose send graph leads back to it";
		}

		// A receive graph that re-sends its own event to a link that is the panel itself feeds itself on the same stack.
		BlockPos own = control.panel != null && control.panel.parent != null ? control.panel.parent.getControlPos() : null;
		if(own != null && control.taggedLinks.containsValue(own)) {
			for(Map.Entry<String, NodeSystem> e : control.receiveNodeMap.entrySet()) {
				Set<String> out = new HashSet<>();
				broadcasts(e.getValue(), out);
				if(out.contains(e.getKey()))
					return "receive graph '" + e.getKey() + "' broadcasts its own event to a link that is this panel";
			}
		}
		return null;
	}

	private static void broadcasts(NodeSystem sys, Set<String> into) {
		for(Node n : sys.nodes) {
			if(n instanceof NodeEventBroadcast b && b.eventName != null)
				into.add(b.eventName);
		}
		for(NodeSystem sub : sys.subSystems.values())
			broadcasts(sub, into);
	}

	private static boolean reaches(Map<String, Set<String>> edges, String from, String target) {
		Deque<String> todo = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		todo.push(from);
		while(!todo.isEmpty()) {
			Set<String> next = edges.get(todo.pop());
			if(next == null)
				continue;
			for(String n : next) {
				if(n.equals(target))
					return true;
				if(edges.containsKey(n) && seen.add(n))
					todo.push(n);
			}
		}
		return false;
	}

	/** Cycles and over-long chains in one system, and in every function body under it. */
	private static String systemProblem(NodeSystem sys) {
		String why = dependencyProblem(sys);
		if(why != null)
			return why;
		for(NodeSystem sub : sys.subSystems.values()) {
			why = systemProblem(sub);
			if(why != null)
				return why;
		}
		return null;
	}

	/**
	 * Iterative depth-first search along each node's input connections. A node met again while it is still open is a cycle;
	 * the height of a finished node is one more than the tallest thing it depends on.
	 */
	private static String dependencyProblem(NodeSystem sys) {
		IdentityHashMap<Node, Integer> state = new IdentityHashMap<>(); // 1 open, 2 done
		IdentityHashMap<Node, Integer> height = new IdentityHashMap<>();
		for(Node start : sys.nodes) {
			if(state.containsKey(start))
				continue;
			Deque<Node> path = new ArrayDeque<>();
			Deque<Integer> next = new ArrayDeque<>();
			path.push(start);
			next.push(0);
			state.put(start, 1);
			while(!path.isEmpty()) {
				Node at = path.peek();
				int i = next.pop();
				if(i < at.inputs.size()) {
					next.push(i + 1);
					NodeConnection in = at.inputs.get(i);
					Node dep = in.connection;
					if(dep == null)
						continue;
					if(in.connectionIndex < 0 || in.connectionIndex >= dep.outputs.size())
						return "a connection names socket " + in.connectionIndex + " of a node that has " + dep.outputs.size();
					Integer s = state.get(dep);
					if(s == null) {
						state.put(dep, 1);
						path.push(dep);
						next.push(0);
					} else if(s == 1) {
						return "nodes depend on themselves (a cycle)";
					}
				} else {
					int h = 0;
					for(NodeConnection in : at.inputs) {
						if(in.connection != null)
							h = Math.max(h, height.getOrDefault(in.connection, 0) + 1);
					}
					if(h > MAX_CHAIN)
						return "a chain of more than " + MAX_CHAIN + " nodes";
					height.put(at, h);
					state.put(at, 2);
					path.pop();
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ links

	/** The link radius, in blocks, from the config (D-3063). */
	public static int linkRadius() {
		return LeafiaConfig.controlPanelLinkRadius;
	}

	/**
	 * Why a link to {@code target} must be refused, or null. {@code known} is every position the panel is already linked to (a
	 * link already there is not new); {@code presented} is every position a linker the player holds has stored.
	 */
	public static String linkProblem(BlockPos panelPos, BlockPos target, Collection<BlockPos> known, Collection<BlockPos> presented) {
		long r = linkRadius();
		if(panelPos.distSqr(target) > r * r)
			return "link " + target.toShortString() + " is farther than " + r + " blocks from the panel";
		if(!known.contains(target) && !presented.contains(target))
			return "link " + target.toShortString() + " was not presented by a linker the player holds";
		return null;
	}

	/** Every link of every control of a parsed panel. */
	public static List<BlockPos> linksOf(ControlPanel panel) {
		List<BlockPos> all = new ArrayList<>();
		for(Control c : panel.controls)
			all.addAll(c.taggedLinks.values());
		return all;
	}
}
