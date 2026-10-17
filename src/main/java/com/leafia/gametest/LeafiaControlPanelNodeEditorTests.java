package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.ControlVariables;
import com.leafia.contents.machines.controlpanel.core.ITypableNode;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.NodeButton;
import com.leafia.contents.machines.controlpanel.core.NodeClipboard;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeElement;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.NodeSystemEditor;
import com.leafia.contents.machines.controlpanel.core.NodeTextBox;
import com.leafia.contents.machines.controlpanel.core.UnknownNode;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.modular.NodeAddMenu;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCancelEvent;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeRead;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeWrite;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeConditional;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath.Operation;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeQueryBlock;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneOutput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The node editor's logic (CCP-NODE-EDITOR, D-3067): the menu of nodes, the editing operations a player has on a graph (add,
 * wire, type a value, select, drag, delete, copy and paste), the variable list's rules, the event lists, and that a graph built
 * with those operations is saved by the same path as every other edit and passes the server's guard. The screens that call all
 * this ({@code SubElementNodeEditor}, {@code SubElementEventEditor}, {@code SubElementVariables}) and the picture of a graph
 * ({@code NodeDraw}) are client code and are checked in a running client (D-3067's checklist); their logic is common, so it is
 * run here, by the same calls the screen makes.
 *
 * <p>Whatever a test changes that is process-wide it puts back: the menu's creator lists and the enum
 * selector factory.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelNodeEditorTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	// ------------------------------------------------------------------ helpers

	private static Control button(ControlPanel panel) {
		return ControlRegistry.getNew("button_push", panel);
	}

	private static ControlEvent evt(String name) {
		return ControlEvent.getRegisteredEvent(name);
	}

	/** What the editor tells the menu while a receive graph of a button's press is open (the control can send a tick). */
	private static NodeEditContext receiveCtx(NodeSystem sys) {
		return new NodeEditContext(sys, evt("ctrl_press"), List.of(evt("tick")));
	}

	private static NodeEditContext sendCtx(NodeSystem sys) {
		return new NodeEditContext(sys, evt("tick"), null);
	}

	/** What a click on this entry of this category makes, as the menu asks its creators. */
	private static Node pick(NodeEditContext ctx, String category, String item, float x, float y) {
		for(INodeMenuCreator c : NTMControlPanelRegistry.addMenuControl.get(category)) {
			Node n = c.selectItem(item, x, y, ctx);
			if(n != null)
				return n;
		}
		return null;
	}

	private static List<String> items(NodeEditContext ctx, String category) {
		ItemList list = new ItemList(0, 0, 32, s -> null);
		for(INodeMenuCreator c : NTMControlPanelRegistry.addMenuControl.get(category)) {
			if(c.getClass().getSimpleName().startsWith("NCStock"))
				c.addItems(list, 0, 0, ctx);
		}
		return list.itemNames;
	}

	private static float[] centre(float[] box) {
		return new float[]{(box[0] + box[2]) / 2F, (box[1] + box[3]) / 2F};
	}

	/** Drags a wire from one port to another the way the mouse does: press on the first, release on the second. */
	private static void wire(NodeSystemEditor ed, NodeConnection from, NodeConnection to) {
		float[] a = centre(from.getPortBox());
		ed.onClick(a[0], a[1], a[0], a[1], false);
		float[] b = centre(to.getPortBox());
		ed.clickReleased(b[0], b[1], false);
	}

	/** Types a number into an unwired input the way a player does: click it, clear what is there, type, press Enter. */
	private static void typeInto(NodeSystemEditor ed, NodeConnection c, String text) {
		float[] v = centre(c.getValueBox());
		ed.onClick(v[0], v[1], v[0], v[1], false);
		for(int i = 0; i < 12; i++)
			ed.backspace();
		for(char ch : text.toCharArray())
			ed.typeChar(ch);
		ed.enter();
		ed.clickReleased(v[0], v[1], false);
	}

	// ------------------------------------------------------------------ the menu

	@GameTest(template = EMPTY)
	public static void theMenuListsEveryStockNodeUnderItsCategory(GameTestHelper helper) {
		StockNodesRegister.register();
		List<String> cats = NTMControlPanelRegistry.addMenuCategories;
		int last = -1;
		for(String s : List.of("Input", "Output", "Math", "Boolean", "Logic", "Pack")) {
			int at = cats.indexOf(s);
			helper.assertTrue(at > last, "the category " + s + " is there, after the one before it (CE's order), at " + at);
			last = at;
		}

		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeEditContext receive = receiveCtx(sys);
		helper.assertTrue(items(receive, "Input").equals(List.of("Event Data", "Get Variable", "Query Block", "Redstone Input")), "input: " + items(receive, "Input"));
		helper.assertTrue(items(receive, "Output").equals(List.of("Broadcast", "Set Variable", "Redstone Output")), "output of a receive graph: " + items(receive, "Output"));
		helper.assertTrue(items(receive, "Math").equals(List.of("Math Node")), "math");
		helper.assertTrue(items(receive, "Boolean").equals(List.of("Boolean Node")), "boolean");
		helper.assertTrue(items(receive, "Logic").equals(List.of("Function", "Buffer", "Conditional")), "logic");
		helper.assertTrue(items(receive, "Pack").equals(List.of("Pack Read", "Pack Write")), "pack");

		NodeEditContext send = sendCtx(sys);
		helper.assertTrue(items(send, "Output").equals(List.of("Cancel", "Set Variable", "Redstone Output")), "a send graph may cancel and cannot broadcast: " + items(send, "Output"));
		NodeEditContext noEvents = new NodeEditContext(sys, evt("ctrl_press"), new ArrayList<>());
		helper.assertTrue(items(noEvents, "Output").equals(List.of("Set Variable", "Redstone Output")), "a control with nothing to send is offered neither: " + items(noEvents, "Output"));
		helper.assertTrue(pick(noEvents, "Output", "Broadcast", 0, 0) == null, "and a broadcast cannot be made for it by name either");

		// Every entry makes a node, and between them they make all fifteen stock nodes.
		List<Class<?>> made = new ArrayList<>();
		for(NodeEditContext ctx : List.of(receive, send)) {
			for(String cat : List.of("Input", "Output", "Math", "Boolean", "Logic", "Pack")) {
				for(String item : items(ctx, cat)) {
					Node n = pick(ctx, cat, item, 12, 34);
					helper.assertTrue(n != null, cat + "/" + item + " makes a node");
					helper.assertTrue(n.posX == 12 && n.posY == 34, item + " is made where it is asked for");
					made.add(n.getClass());
				}
			}
		}
		List<Class<?>> stock = List.of(NodeInput.class, NodeGetVar.class, NodeQueryBlock.class, NodeRedstoneInput.class, NodeMath.class, NodeBoolean.class,
				NodeFunction.class, NodeBuffer.class, NodeConditional.class, NodeCancelEvent.class, NodeEventBroadcast.class, NodeSetVar.class,
				NodeRedstoneOutput.class, NodeCompositeRead.class, NodeCompositeWrite.class);
		for(Class<?> k : stock)
			helper.assertTrue(made.contains(k), "the menu can make a " + k.getSimpleName());
		helper.assertTrue(pick(receive, "Input", "Math Node", 0, 0) == null && pick(receive, "Math", "no such node", 0, 0) == null, "a name that is not the category's makes nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void everyNodeTheMenuMakesSurvivesASaveAndALoad(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Control c = button(new ControlPanel(null));
			NodeSystem sys = new NodeSystem(c);
			for(NodeEditContext ctx : List.of(receiveCtx(sys), sendCtx(sys))) {
				for(String cat : List.of("Input", "Output", "Math", "Boolean", "Logic", "Pack")) {
					for(String item : items(ctx, cat)) {
						NodeSystem one = new NodeSystem(c);
						one.addNode(pick(ctx, cat, item, 5, 6));
						NodeSystem back = new NodeSystem(c);
						back.readFromNBT(one.writeToNBT(new CompoundTag()));
						helper.assertTrue(back.nodes.size() == 1 && !(back.nodes.get(0) instanceof UnknownNode)
								&& back.nodes.get(0).getClass() == one.nodes.get(0).getClass(), item + " is read back as itself, not as a placeholder");
					}
				}
			}
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void registeringTwiceChangesNothingAndAnAddonsCreatorFollowsTheStockOne(GameTestHelper helper) {
		StockNodesRegister.register();
		int categories = NTMControlPanelRegistry.addMenuCategories.size();
		List<INodeMenuCreator> input = NTMControlPanelRegistry.addMenuControl.get("Input");
		int creators = input.size();
		StockNodesRegister.register();
		helper.assertTrue(NTMControlPanelRegistry.addMenuCategories.size() == categories && NTMControlPanelRegistry.addMenuControl.get("Input").size() == creators,
				"a second register adds no category and no creator");
		helper.assertTrue(NTMControlPanelRegistry.nbtNodeLoaders.stream().filter(l -> l instanceof com.leafia.contents.machines.controlpanel.core.modular.StockNodeLoader).count() == 1,
				"and the stock loader once");

		INodeMenuCreator addon = new INodeMenuCreator() {
			@Override public Node selectItem(String s2, float x, float y, NodeEditContext editor) { return null; }
			@Override public void addItems(ItemList list, float x, float y, NodeEditContext editor) { list.addItems("Addon Item"); }
		};
		try {
			input.add(addon); // as Leafia's batch 13 does: putIfAbsent, get, add
			StockNodesRegister.register();
			List<INodeMenuCreator> now = NTMControlPanelRegistry.addMenuControl.get("Input");
			helper.assertTrue(now.size() == creators + 1 && now.get(0).getClass().getSimpleName().equals("NCStockInput") && now.get(now.size() - 1) == addon,
					"the stock creator is first and the addon's stays after it");
			ItemList all = new ItemList(0, 0, 32, s -> null);
			for(INodeMenuCreator c : now)
				c.addItems(all, 0, 0, receiveCtx(new NodeSystem(button(new ControlPanel(null)))));
			helper.assertTrue(all.itemNames.get(0).equals("Event Data") && all.itemNames.get(all.itemNames.size() - 1).equals("Addon Item"), "and both show on the menu, stock first");
		} finally {
			input.remove(addon);
		}
		helper.assertTrue(NTMControlPanelRegistry.addMenuControl.get("Input").size() == creators, "put back");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theAddMenuIsDrivenByClicksOnItsRows(GameTestHelper helper) {
		StockNodesRegister.register();
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		List<Node> added = new ArrayList<>();
		List<float[]> pasted = new ArrayList<>();
		int[] done = {0};
		float[] at = {55, 66};

		ItemList menu = NodeAddMenu.build(receiveCtx(sys), 10, 10, () -> at, false, pasted::add, added::add, () -> done[0]++);
		helper.assertTrue(menu.itemNames.get(0).equals("{expandable}Input") && menu.itemNames.size() >= 6, "the top level is the categories, expandable: " + menu.itemNames);
		// Row i of a list is 6 tall starting 2 below its top; the third row is Math.
		helper.assertTrue(!menu.mouseClicked(15, 10 + 2 + 6 * 2 + 3), "clicking a category opens its list and is not a final pick");
		helper.assertTrue(menu.child != null && menu.child.itemNames.equals(List.of("Math Node")), "the category's list is the Math node");
		helper.assertTrue(menu.child.posX == 10 + 32 && menu.child.posY == 10 + 2 + 12, "placed beside the row");
		helper.assertTrue(menu.mouseClicked(menu.child.posX + 5, menu.child.posY + 2 + 3), "clicking the node is a final pick");
		helper.assertTrue(added.size() == 1 && added.get(0) instanceof NodeMath && added.get(0).posX == 55 && added.get(0).posY == 66 && done[0] == 1,
				"the node is made at the position read when it was picked, and the menu is told it is done");

		at[0] = 70;
		ItemList withPaste = NodeAddMenu.build(receiveCtx(sys), 10, 10, () -> at, true, pasted::add, added::add, () -> done[0]++);
		helper.assertTrue(withPaste.itemNames.get(0).equals("Paste"), "Paste is the first row when the clipboard can be pasted");
		withPaste.mouseClicked(15, 10 + 2 + 3);
		helper.assertTrue(pasted.size() == 1 && pasted.get(0)[0] == 70 && done[0] == 2 && added.size() == 1, "and it pastes at the picked position");
		helper.succeed();
	}

	// ------------------------------------------------------------------ building a graph

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aGraphBuiltThroughTheEditorRunsSavesThroughTheRealPathAndPassesTheGuard(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			AtomicReference<Control> built = new AtomicReference<>();

			CompoundTag edit = LeafiaControlPanelKit.edit(te, panel -> {
				Control c = button(panel);
				c.posX = 2;
				c.posY = 3;
				panel.controls.add(c);
				built.set(c);
				NodeSystem sys = new NodeSystem(c);
				c.receiveNodeMap.put("ctrl_press", sys);
				NodeSystemEditor ed = new NodeSystemEditor(sys);
				NodeEditContext ctx = receiveCtx(sys);

				NodeInput in = (NodeInput) pick(ctx, "Input", "Event Data", 0, 0);
				ed.addNode(in);
				NodeMath math = (NodeMath) pick(ctx, "Math", "Math Node", 100, 0);
				math.setData(Operation.ADD);
				ed.addNode(math);
				NodeSetVar set = (NodeSetVar) pick(ctx, "Output", "Set Variable", 200, 0);
				set.setData("isLit", false);
				ed.addNode(set);
				helper.assertTrue(ed.activeNode == set && sys.nodes.size() == 3 && sys.outputNodes.size() == 1, "added nodes are active and in the graph");

				helper.assertTrue(in.outputs.get(0).name.equals("isSneaking") && in.outputs.size() == 3, "the event data node lists the event's variable and a receive graph's tag and index");
				wire(ed, in.outputs.get(0), math.inputs.get(0));
				wire(ed, math.outputs.get(0), set.inputs.get(0));
				helper.assertTrue(math.inputs.get(0).connection == in && math.inputs.get(0).connectionIndex == 0, "the event variable is wired to the first input");
				helper.assertTrue(set.inputs.get(0).connection == math && set.inputs.get(0).drawsLine, "and the sum to the variable");
				typeInto(ed, math.inputs.get(1), "2.5");
				helper.assertTrue(math.inputs.get(1).defaultValue.getNumber() == 2.5F && !ed.isTyping(), "a typed 2.5 is the second input, and typing is over");

				helper.assertTrue(ControlPanelGuard.graphProblem(c) == null, "the graph the editor built is one the guard accepts");
			});
			helper.assertTrue(ControlPanelGuard.scan(edit) == null, "and so is the tag it writes");
			Control c = built.get();
			c.receiveEvent(ControlEvent.newEvent("ctrl_press").setVar("isSneaking", true));
			helper.assertTrue(c.vars.get("isLit").getNumber() == 3.5F, "it runs: sneaking (1) plus 2.5 is 3.5, got " + c.vars.get("isLit"));

			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), edit), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel.controls.size() == 1, "the server took the edit through the real packet path");
			Control saved = te.panel.controls.get(0);
			NodeSystem sys = saved.receiveNodeMap.get("ctrl_press");
			helper.assertTrue(sys != null && sys.nodes.size() == 3 && sys.nodes.get(0) instanceof NodeInput && sys.nodes.get(1) instanceof NodeMath && sys.nodes.get(2) instanceof NodeSetVar,
					"with its three nodes");
			helper.assertTrue(sys.nodes.get(1).inputs.get(0).connection == sys.nodes.get(0) && sys.nodes.get(2).inputs.get(0).connection == sys.nodes.get(1)
					&& sys.nodes.get(1).inputs.get(1).defaultValue.getNumber() == 2.5F, "wired, with the typed literal, after the load");
			saved.receiveEvent(ControlEvent.newEvent("ctrl_press").setVar("isSneaking", true));
			helper.assertTrue(saved.vars.get("isLit").getNumber() == 3.5F, "and the saved copy runs the same");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aWireThatWouldMakeAGraphDependOnItselfIsNotMade(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeMath a = new NodeMath(0, 0);
		NodeMath b = new NodeMath(100, 0);
		NodeMath d = new NodeMath(200, 0);
		ed.addNode(a);
		ed.addNode(b);
		ed.addNode(d);
		wire(ed, a.outputs.get(0), b.inputs.get(0));
		wire(ed, b.outputs.get(0), d.inputs.get(0));
		helper.assertTrue(d.inputs.get(0).connection == b && b.inputs.get(0).connection == a, "a chain a, b, d is made");
		wire(ed, d.outputs.get(0), a.inputs.get(0));
		helper.assertTrue(a.inputs.get(0).connection == null && !a.inputs.get(0).drawsLine && ed.connectionInProgress == null, "closing it into a loop is refused, and nothing is left hanging");
		wire(ed, a.outputs.get(0), a.inputs.get(1));
		helper.assertTrue(a.inputs.get(1).connection == null, "a node cannot feed itself (the editor's own rule: the same node's ports never join)");
		helper.assertTrue(NodeSystemEditor.wouldCycle(a, d) && NodeSystemEditor.wouldCycle(a, a) && !NodeSystemEditor.wouldCycle(d, a), "the rule: a producer that already reads from the consumer");
		helper.assertTrue(ControlPanelGuard.graphProblem(c) == null, "the graph is still one the guard accepts");

		// dragging an existing wire away and onto another input moves it
		NodeMath e = new NodeMath(300, 0);
		ed.addNode(e);
		wire(ed, d.inputs.get(0), e.inputs.get(0));
		helper.assertTrue(d.inputs.get(0).connection == null && e.inputs.get(0).connection == b, "pressing a wired input picks the wire up from its source, and releasing on another input moves it there");
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aHostileGraphIsStillRefusedWhateverTheEditorWouldHaveLetThrough(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			CompoundTag before = te.panel.writeToNBT(new CompoundTag());
			ControlPanel panelBefore = te.panel;

			// A cycle written by hand, past the editor: a depends on b, b on a.
			CompoundTag cycle = LeafiaControlPanelKit.edit(te, panel -> {
				Control c = button(panel);
				panel.controls.add(c);
				NodeSystem sys = new NodeSystem(c);
				c.receiveNodeMap.put("ctrl_press", sys);
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(100, 0);
				sys.addNode(a);
				sys.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
				b.inputs.get(0).setData(a, 0, true);
			});
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), cycle), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a cycle is refused and the panel is untouched");

			// Too many nodes for one panel.
			CompoundTag huge = LeafiaControlPanelKit.edit(te, panel -> {
				Control c = button(panel);
				panel.controls.add(c);
				NodeSystem sys = new NodeSystem(c);
				c.receiveNodeMap.put("ctrl_press", sys);
				for(int i = 0; i <= ControlPanelGuard.MAX_NODES; i++)
					sys.addNode(new NodeMath(i, 0));
			});
			helper.assertTrue(ControlPanelGuard.scan(huge) != null, "the guard's scan names it");
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), huge), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a graph past the node ceiling is refused");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aControlThatBroadcastsItsOwnEventToThePanelIsOneTheEditorCanBuildAndThePageWarnsOf(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ControlPanel panel = new ControlPanel(te);
			Control c = button(panel);
			panel.controls.add(c);
			c.taggedLinks.put("me", te.getControlPos());
			NodeSystem sys = new NodeSystem(c);
			c.receiveNodeMap.put("tick", sys);
			NodeSystemEditor ed = new NodeSystemEditor(sys);
			NodeEditContext ctx = new NodeEditContext(sys, evt("tick"), List.of(evt("tick")));
			ed.addNode(pick(ctx, "Output", "Broadcast", 0, 0));
			helper.assertTrue(sys.nodes.size() == 1 && ((NodeEventBroadcast) sys.nodes.get(0)).eventName.equals("tick"), "the menu offers the broadcast, so the editor can build it");
			String why = ControlPanelGuard.graphProblem(c);
			helper.assertTrue(why != null && why.contains("broadcasts its own event"), "and the guard that the page asks names it: " + why);
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void emptyEventGraphsAreNotSavedSoOpeningManyEventsCannotMakeTheServerRefuseThePanel(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanel panel = new ControlPanel(null);
			Control c = button(panel);
			panel.controls.add(c);
			for(int i = 0; i < ControlPanelGuard.MAX_SYSTEMS_PER_CONTROL + 6; i++)
				c.receiveNodeMap.computeIfAbsent("event" + i, k -> new NodeSystem(c)); // what opening each event in the editor does
			NodeSystem real = new NodeSystem(c);
			real.addNode(new NodeMath(0, 0));
			c.sendNodeMap.put("tick", real);
			CompoundTag tag = panel.writeToNBT(new CompoundTag());
			helper.assertTrue(ControlPanelGuard.scan(tag) != null, "as opened, the panel is more graphs than the guard allows on a control");
			int removed = NodeSystemEditor.pruneEmptySystems(c);
			helper.assertTrue(removed == ControlPanelGuard.MAX_SYSTEMS_PER_CONTROL + 6 && c.receiveNodeMap.isEmpty() && c.sendNodeMap.containsKey("tick"),
					"pruned, only the graph with a node in it is left (removed " + removed + ")");
			helper.assertTrue(ControlPanelGuard.scan(panel.writeToNBT(new CompoundTag())) == null, "and the panel passes");
			NodeSystem vars = new NodeSystem(c);
			vars.setVar("x", new DataValueFloat(1));
			c.receiveNodeMap.put("keeps", vars);
			helper.assertTrue(NodeSystemEditor.pruneEmptySystems(c) == 0, "a graph with only a variable is not empty");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ editing operations

	@GameTest(template = EMPTY)
	public static void deletingNodesUnwiresTheRestAndForgetsTheSelection(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeMath a = new NodeMath(0, 0);
		NodeMath b = new NodeMath(100, 0);
		NodeFunction fn = new NodeFunction(200, 0);
		NodeSetVar set = new NodeSetVar(300, 0, c);
		for(Node n : List.of(a, b, fn, set))
			ed.addNode(n);
		wire(ed, a.outputs.get(0), b.inputs.get(0));
		wire(ed, b.outputs.get(0), set.inputs.get(0));
		helper.assertTrue(sys.subSystems.containsKey(fn) && sys.outputNodes.size() == 1, "a function node has a body and a set-variable is an output");

		// select a and the set node, delete them
		ed.onClick(20, 3, 20, 3, false);
		ed.clickReleased(20, 3, false);
		ed.onClick(320, 3, 320, 3, true);
		ed.clickReleased(320, 3, true);
		helper.assertTrue(ed.selectedNodes.size() == 2 && ed.activeNode == set, "two nodes are selected, the last clicked active");
		ed.removeSelected();
		helper.assertTrue(sys.nodes.size() == 2 && !sys.nodes.contains(a) && !sys.nodes.contains(set) && sys.outputNodes.isEmpty(), "they are gone, and the output list too");
		helper.assertTrue(b.inputs.get(0).connection == null && b.inputs.get(0).connectionIndex == -1 && !b.inputs.get(0).drawsLine,
				"b's input, which read from a, is unwired (CE left it pointing at a node no longer in the graph)");
		helper.assertTrue(ed.selectedNodes.isEmpty() && ed.activeNode == null, "nothing stays selected");

		ed.removeNode(fn);
		helper.assertTrue(!sys.subSystems.containsKey(fn) && sys.nodes.size() == 1, "a function node takes its body with it");
		helper.assertTrue(ControlPanelGuard.graphProblem(c) == null, "what is left is a graph the guard accepts");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void clickingSelectsShiftAddsAndDraggingMovesTheSelection(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeMath a = new NodeMath(0, 0);
		NodeMath b = new NodeMath(100, 0);
		ed.addNode(a);
		ed.addNode(b);
		ed.activeNode = null;

		ed.onClick(20, 3, 20, 3, false); // the title strip of a: no port, no field
		helper.assertTrue(ed.activeNode == a && ed.selectedNodes.equals(List.of(a)) && ed.drag, "a click on a node selects it and starts a drag");
		ed.dragTo(30, 8, 2F, false);
		helper.assertTrue(a.posX == 20 && a.posY == 10 && b.posX == 100, "dragging moves the selection by the mouse's distance times the zoom (10 x 2, 5 x 2)");
		helper.assertTrue(a.inputs.get(0).offsetX == a.posX, "and the node's sockets move with it");
		ed.dragTo(30, 8, 2F, true);
		helper.assertTrue(a.posX == 20, "with Shift down it does not drag");
		ed.clickReleased(30, 8, false);
		helper.assertTrue(!ed.drag && ed.selectedNodes.equals(List.of(a)), "releasing ends the drag, and a drag does not reselect");

		ed.onClick(120, 3, 120, 3, true);
		ed.clickReleased(120, 3, true);
		helper.assertTrue(ed.selectedNodes.size() == 2 && ed.activeNode == b, "Shift adds a second node");
		ed.onClick(110, 3, 110, 3, false);
		ed.dragTo(115, 3, 1F, false);
		helper.assertTrue(a.posX == 25 && b.posX == 105, "dragging one of two selected moves both");
		ed.clickReleased(115, 3, false);

		ed.onClick(1000, 1000, 1000, 1000, false);
		ed.clickReleased(1000, 1000, false);
		helper.assertTrue(ed.selectedNodes.isEmpty() && ed.activeNode == null, "a click on nothing clears the selection");
		helper.succeed();
	}

	// ------------------------------------------------------------------ copy and paste

	@GameTest(template = EMPTY)
	public static void aCopyKeepsTheWiresBetweenCopiedNodesAndDropsTheOthers(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Control c = button(new ControlPanel(null));
			NodeSystem sys = new NodeSystem(c);
			NodeSystemEditor ed = new NodeSystemEditor(sys);
			NodeMath a = new NodeMath(10, 20);
			NodeMath b = new NodeMath(110, 50);
			NodeMath other = new NodeMath(500, 500);
			NodeFunction fn = new NodeFunction(300, 300);
			for(Node n : List.of(a, b, other, fn))
				ed.addNode(n);
			wire(ed, a.outputs.get(0), b.inputs.get(0));
			wire(ed, other.outputs.get(0), b.inputs.get(1));
			NodeSetVar inBody = new NodeSetVar(0, 0, c);
			sys.subSystems.get(fn).addNode(inBody);

			ed.selectedNodes.addAll(List.of(a, b));
			CompoundTag clip = ed.copySelection();
			helper.assertTrue(clip != null && clip.getFloat(NodeClipboard.ANCHOR_X) == 10 && clip.getFloat(NodeClipboard.ANCHOR_Y) == 20, "the copy remembers its top left corner");
			int before = sys.nodes.size();
			List<Node> pasted = ed.paste(clip, 200, 100);
			helper.assertTrue(pasted.size() == 2 && sys.nodes.size() == before + 2, "two nodes are pasted");
			Node pa = pasted.get(0);
			Node pb = pasted.get(1);
			helper.assertTrue(pa.posX == 200 && pa.posY == 100 && pb.posX == 300 && pb.posY == 130, "the corner lands where asked, the shape is kept");
			helper.assertTrue(pb.inputs.get(0).connection == pa, "the wire between the two copied nodes survives, to the pasted node");
			helper.assertTrue(pb.inputs.get(1).connection == null && pb.inputs.get(1).connectionIndex == -1 && !pb.inputs.get(1).drawsLine, "the wire to a node left behind does not");
			helper.assertTrue(b.inputs.get(0).connection == a && b.inputs.get(1).connection == other, "the originals are untouched");
			helper.assertTrue(ed.selectedNodes.equals(pasted) && ed.activeNode == pb, "the pasted nodes are the selection");

			// A function node takes a copy of its body.
			ed.selectedNodes.clear();
			ed.selectedNodes.add(fn);
			List<Node> fns = ed.paste(ed.copySelection(), 0, 0);
			NodeSystem body = sys.subSystems.get(fns.get(0));
			helper.assertTrue(fns.get(0) != fn && body != null && body != sys.subSystems.get(fn) && body.nodes.size() == 1 && body.nodes.get(0) instanceof NodeSetVar,
					"a pasted function has its own copy of the body");
			helper.assertTrue(ControlPanelGuard.graphProblem(c) == null, "after all that the graph is one the guard accepts");

			ed.selectedNodes.clear();
			helper.assertTrue(ed.copySelection() == null && NodeClipboard.writeSelected(sys, List.of(new NodeMath(0, 0))) == null, "nothing selected, or selected but not in the graph, copies nothing");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aPasteIsOnlyAllowedWhereItsNodesBelong(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanel panel = new ControlPanel(null);
			Control c = button(panel);
			NodeSystem target = new NodeSystem(c);
			ControlEvent press = evt("ctrl_press");

			NodeSystem src = new NodeSystem(c);
			NodeCancelEvent cancel = new NodeCancelEvent(0, 0);
			NodeEventBroadcast broadcast = new NodeEventBroadcast(0, 0, List.of(evt("tick")));
			CompoundTag withCancel = NodeClipboard.writeSelected(addAll(src, cancel), List.of(cancel));
			src = new NodeSystem(c);
			CompoundTag withBroadcast = NodeClipboard.writeSelected(addAll(src, broadcast), List.of(broadcast));
			helper.assertTrue(!NodeClipboard.canPaste(target, withCancel, press, false) && NodeClipboard.canPaste(target, withCancel, press, true),
					"a cancel belongs in a send graph and not a receive graph (the menu offers it only there)");
			helper.assertTrue(NodeClipboard.canPaste(target, withBroadcast, press, false) && !NodeClipboard.canPaste(target, withBroadcast, press, true),
					"a broadcast belongs in a receive graph and not a send graph");
			helper.assertTrue(!NodeClipboard.canPaste(target, null, press, false) && !NodeClipboard.canPaste(target, new CompoundTag(), press, false) && !NodeClipboard.canPaste(target, withBroadcast, null, false),
					"no clipboard, an empty one, or no event: no");

			// Event data: its outputs must be variables the target's event carries, with the type it has.
			NodeEditContext ctx = receiveCtx(target);
			NodeInput data = (NodeInput) pick(ctx, "Input", "Event Data", 0, 0);
			CompoundTag eventData = NodeClipboard.writeSelected(addAll(new NodeSystem(c), data), List.of(data));
			helper.assertTrue(NodeClipboard.canPaste(target, eventData, press, false), "event data of the same event can be pasted back");
			helper.assertTrue(!NodeClipboard.canPaste(target, eventData, evt("tick"), false), "but not into the graph of an event that has no isSneaking");
			helper.assertTrue(!NodeClipboard.canPaste(target, eventData, press, true), "nor into a send graph, which has to index (to index) where a receive graph has tag and from index");
			NodeInput odd = new NodeInput(0, 0, "Event Data").setVars(Map.<String, DataValue>of("isSneaking", new DataValueString("x")));
			helper.assertTrue(!NodeClipboard.canPaste(target, NodeClipboard.writeSelected(addAll(new NodeSystem(c), odd), List.of(odd)), press, false), "a variable of the right name and another type is refused");

			// Variables: the target control must have the variable, as the type the port expects.
			NodeGetVar get = new NodeGetVar(0, 0, c).setData("isLit", false);
			CompoundTag getVar = NodeClipboard.writeSelected(addAll(new NodeSystem(c), get), List.of(get));
			helper.assertTrue(NodeClipboard.canPaste(target, getVar, press, false), "a variable the control has can be pasted");
			Control bare = ControlRegistry.getNew("button_push", panel);
			bare.vars.clear();
			helper.assertTrue(!NodeClipboard.canPaste(new NodeSystem(bare), getVar, press, false), "into a control without it, not");
			NodeGetVar glob = new NodeGetVar(0, 0, c).setData("nothing_here", true);
			helper.assertTrue(!NodeClipboard.canPaste(target, NodeClipboard.writeSelected(addAll(new NodeSystem(c), glob), List.of(glob)), press, false), "a global variable that does not exist, not");
			panel.globalVars.put("nothing_here", new DataValueFloat(1));
			helper.assertTrue(NodeClipboard.canPaste(target, NodeClipboard.writeSelected(addAll(new NodeSystem(c), glob), List.of(glob)), press, false), "and once it exists, yes");

			// A function's body is checked as well.
			NodeFunction fn = new NodeFunction(0, 0);
			NodeSystem withFn = new NodeSystem(c);
			withFn.addNode(fn);
			withFn.subSystems.get(fn).addNode(new NodeCancelEvent(0, 0));
			helper.assertTrue(!NodeClipboard.canPaste(target, NodeClipboard.writeSelected(withFn, List.of(fn)), press, false), "a cancel hidden in a function body is found");
		});
		helper.succeed();
	}

	private static NodeSystem addAll(NodeSystem sys, Node... nodes) {
		for(Node n : nodes)
			sys.addNode(n);
		return sys;
	}

	// ------------------------------------------------------------------ typing

	@GameTest(template = EMPTY)
	public static void typingIntoASocketAndATextBoxCommitsOnEnterOrWhenTheEditorLetsGo(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeMath m = new NodeMath(0, 0);
		ed.addNode(m);
		NodeConnection in = m.inputs.get(0);

		helper.assertTrue(in instanceof ITypableNode && !ed.typeChar('1') && !ed.backspace() && !ed.enter(), "nothing types while nothing is being typed into");
		float[] v = centre(in.getValueBox());
		ed.onClick(v[0], v[1], v[0], v[1], false);
		helper.assertTrue(ed.isTyping() && ed.currentTypingBox == in && in.isTyping(), "a click on an unwired input's field starts typing into it");
		helper.assertTrue(in.builder.toString().equals(in.defaultValue.toString()), "what is there is the start of the text, as in CE");
		for(int i = 0; i < 10; i++)
			ed.backspace();
		helper.assertTrue(in.builder.length() == 0, "Backspace deletes, and past the start does nothing");
		ed.typeChar('1');
		ed.typeChar('\n');
		ed.typeChar((char) 7);
		ed.typeChar((char) 127);
		ed.typeChar('2');
		helper.assertTrue(in.builder.toString().equals("12"), "only printable characters are typed, got '" + in.builder + "'");
		for(int i = 0; i < ITypableNode.MAX_TYPED + 10; i++)
			ed.typeChar('9');
		helper.assertTrue(in.builder.length() == ITypableNode.MAX_TYPED, "and no more than " + ITypableNode.MAX_TYPED);
		for(int i = 0; i < ITypableNode.MAX_TYPED + 10; i++)
			ed.backspace();
		ed.typeChar('7');
		ed.typeChar('.');
		ed.typeChar('5');
		helper.assertTrue(ed.enter() && !ed.isTyping() && in.defaultValue.getNumber() == 7.5F && in.defaultValue.getType() == DataType.NUMBER, "Enter commits it as the socket's type");

		// clicking elsewhere commits too
		ed.onClick(v[0], v[1], v[0], v[1], false);
		ed.typeChar('x'); // "7.5x" is not a number; a number socket reads it as 0 (CE)
		ed.onClick(1000, 1000, 1000, 1000, false);
		helper.assertTrue(!ed.isTyping() && in.builder == null, "a click away from the field commits and leaves typing");

		// the editor closing commits what is being typed (CE lost it)
		ed.onClick(v[0], v[1], v[0], v[1], false);
		for(int i = 0; i < 10; i++)
			ed.backspace();
		ed.typeChar('4');
		ed.reset();
		helper.assertTrue(in.defaultValue.getNumber() == 4F && !ed.isTyping(), "reset commits the typed text instead of losing it");

		// a string socket takes any text; a text box reports its change once
		NodeCompositeWrite write = new NodeCompositeWrite(300, 0);
		ed.addNode(write);
		NodeConnection key = null;
		for(NodeConnection k : write.inputs)
			if(k.type == DataType.STRING)
				key = k;
		helper.assertTrue(key != null, "the pack-write node has a string input");
		float[] kv = centre(key.getValueBox());
		ed.onClick(kv[0], kv[1], kv[0], kv[1], false);
		for(int i = 0; i < 10; i++)
			ed.backspace();
		for(char ch : "hello world".toCharArray())
			ed.typeChar(ch);
		ed.enter();
		helper.assertTrue(key.defaultValue.toString().equals("hello world"), "a string socket keeps what was typed");

		List<String> changes = new ArrayList<>();
		Node host = new Node(600, 0) {
			@Override public DataValue evaluate(int idx) { return null; }
			@Override public float[] getColor() { return new float[]{1, 1, 1}; }
			@Override public String getDisplayName() { return "Box"; }
		};
		NodeTextBox box = new NodeTextBox("Format", host, 0, "ab", changes::add);
		host.otherElements.add(box);
		host.recalcSize();
		host.size = 100; // tall enough that the box row, which CE places like a socket row, is inside the node
		ed.addNode(host);
		float[] bv = centre(box.getValueBox());
		ed.onClick(bv[0], bv[1], bv[0], bv[1], false);
		helper.assertTrue(ed.currentTypingBox == box && box.isTyping(), "a click on a text box in a node starts it typing (any element that is ITypableNode)");
		ed.typeChar('c');
		helper.assertTrue(changes.isEmpty(), "nothing is reported while typing");
		ed.enter();
		helper.assertTrue(changes.equals(List.of("abc")) && box.value.equals("abc"), "committing reports the text once");
		NodeTextBox quiet = new NodeTextBox("Q", host, 5, "x", null);
		quiet.startTyping();
		quiet.typeChar('y');
		quiet.stopTyping();
		helper.assertTrue(quiet.value.equals("xy"), "a box with no callback commits quietly");

		CompoundTag tag = box.writeToNBT(new CompoundTag(), sys);
		NodeTextBox back = new NodeTextBox("?", host, 0, "", null);
		back.readFromNBT(tag, sys);
		helper.assertTrue(back.name.equals("Format") && back.value.equals("abc") && !back.isTyping() && back.parent == host, "a text box saves its name and text");
		helper.succeed();
	}

	// ------------------------------------------------------------------ dropdowns and buttons

	@GameTest(template = EMPTY)
	public static void aDropdownOpensOnItsRowPicksAnEntryAndClosesAgain(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeMath m = new NodeMath(0, 0);
		ed.addNode(m);
		NodeDropdown dd = (NodeDropdown) m.otherElements.get(0);

		helper.assertTrue(dd.list.isClosed && dd.list.alpha == 0.95F && dd.list.textColor == 0xFFDFDFDF && dd.list.r == 0.3F, "closed, in the dropdown's own colours (CE's 0.95 alpha, 0.3 grey, 0xDFDFDF text)");
		helper.assertTrue(dd.list.posX == m.posX + 4 + 0 && dd.list.posY == dd.offsetY + 14, "its list hangs 4 right and 14 below the row");
		float[] row = centre(dd.getBox());
		ed.onClick(row[0], row[1], row[0], row[1], false);
		helper.assertTrue(!dd.list.isClosed && ed.selectedNodes.isEmpty(), "a click on the row opens the list, and is not a click on the node");
		ed.clickReleased(row[0], row[1], false);

		String third = dd.list.itemNames.get(2);
		float px = dd.list.posX + 5;
		float py = dd.list.posY + 2 + 6 * 2 + 3;
		ed.onClick(px, py, px, py, false);
		helper.assertTrue(dd.list.isClosed && dd.currentName().equals(third), "a click on an entry picks it and closes the list: " + third);
		ed.clickReleased(px, py, false);

		ed.onClick(row[0], row[1], row[0], row[1], false);
		ed.onClick(row[0], row[1], row[0], row[1], false);
		helper.assertTrue(dd.list.isClosed, "a second click on the row closes it");

		// an open list's click outside both the list and the row does nothing to it
		dd.list.open();
		helper.assertTrue(!dd.onClick(900, 900) && !dd.list.isClosed, "a click that is neither on the list nor on the row is not the dropdown's");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anEnumSocketGetsASelectorOnlyWhereTheClientInstallsOne(GameTestHelper helper) {
		var saved = NodeConnection.enumSelectorFactory;
		try {
			Control c = button(new ControlPanel(null));
			NodeMath host = new NodeMath(0, 0);
			NodeConnection.enumSelectorFactory = null;
			NodeConnection bare = new NodeConnection("dir", host, 0, true, DataType.ENUM, new DataValueEnum<>(Direction.NORTH));
			helper.assertTrue(bare.enumSelector == null, "with no factory, as on a server, there is no selector");

			NodeConnection.enumSelectorFactory = NodeConnection::newEnumSelector;
			NodeConnection sel = new NodeConnection("dir", host, 0, true, DataType.ENUM, new DataValueEnum<>(Direction.NORTH));
			helper.assertTrue(sel.enumSelector instanceof NodeDropdown, "with the client's factory there is a dropdown");
			NodeDropdown dd = (NodeDropdown) sel.enumSelector;
			helper.assertTrue(dd.list.itemNames.size() == 6 && dd.list.itemNames.get(0).equals("DOWN") && dd.currentName().equals("north"), "listing the constants by name, showing the current one: " + dd.list.itemNames + " / " + dd.currentName());
			helper.assertTrue(dd.list.posX == sel.offsetX + 4 && dd.list.posY == sel.offsetY + 14 && sel.getValueBox()[0] == dd.getBox()[0], "placed at the socket, and the socket's value box is the dropdown's");

			dd.list.open();
			dd.onClick(dd.list.posX + 5, dd.list.posY + 2 + 6 * 5 + 3); // the sixth entry
			helper.assertTrue(dd.list.isClosed && sel.defaultValue.getEnum(Direction.class) == Direction.EAST && sel.type == DataType.ENUM, "picking EAST makes it the socket's value");
			helper.assertTrue(dd.choose("nonsense") == null && sel.defaultValue.getEnum(Direction.class) == Direction.EAST, "a name that is no constant changes nothing");

			NodeConnection.enumSelectorFactory = null;
			sel.setDefault(new DataValueFloat(1));
			helper.assertTrue(sel.enumSelector == null, "a socket that stops being an enum loses its selector");
		} finally {
			NodeConnection.enumSelectorFactory = saved;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFunctionNodesButtonIsFoundByAClickOnItAndOnlyIt(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		NodeSystem sys = new NodeSystem(c);
		NodeSystemEditor ed = new NodeSystemEditor(sys);
		NodeFunction fn = new NodeFunction(50, 50);
		NodeMath m = new NodeMath(300, 50);
		ed.addNode(fn);
		ed.addNode(m);
		float[] box = centre(fn.editBody.getBox());
		NodeElement hit = ed.getNodeElementPressed(box[0], box[1], 7, 9);
		helper.assertTrue(hit == fn.editBody && hit instanceof NodeButton && hit.name.equals("Edit Body"), "the click on the button finds it");
		helper.assertTrue(ed.lastMouseX == 7 && ed.lastMouseY == 9, "and the mouse is remembered, as CE did");
		float[] other = centre(((NodeDropdown) m.otherElements.get(0)).getBox());
		helper.assertTrue(ed.getNodeElementPressed(other[0], other[1], 0, 0) == null && ed.getNodeElementPressed(5000, 5000, 0, 0) == null, "a dropdown is not a button, and empty space is nothing");
		ed.onClick(box[0], box[1], box[0], box[1], false);
		helper.assertTrue(ed.selectedNodes.isEmpty() && !ed.drag, "the click that pressed a button does not also select or drag the node");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the variable list

	@GameTest(template = EMPTY)
	public static void theVariableListsRulesForNumbersStringsPacksAndScopes(GameTestHelper helper) {
		helper.assertTrue(ControlVariables.parseNumber("2.5") == 2.5F && ControlVariables.parseNumber(" -3 ") == -3F && ControlVariables.parseNumber("1e2") == 100F, "numbers parse");
		for(String bad : new String[]{null, "", " ", "-", ".", "-.", "abc", "NaN", "Infinity", "1.2.3"})
			helper.assertTrue(ControlVariables.parseNumber(bad) == null, "'" + bad + "' is not a number");
		helper.assertTrue(ControlVariables.isNumericText("-12.5") && ControlVariables.isNumericText("") && ControlVariables.isNumericText("1.0E-5") && !ControlVariables.isNumericText("12a") && !ControlVariables.isNumericText("a"),
				"the number box takes digits, sign, point and the exponent a float prints with");

		Map<String, DataValue> global = new java.util.LinkedHashMap<>();
		helper.assertTrue(ControlVariables.create(global, null, " speed ", DataType.NUMBER, "3") && global.get("speed").getNumber() == 3F, "a global number, its name trimmed");
		helper.assertTrue(ControlVariables.create(global, null, "label", DataType.STRING, "hi") && global.get("label").toString().equals("hi"), "a string");
		helper.assertTrue(ControlVariables.create(global, null, "bundle", DataType.COMPOSITE, null) && global.get("bundle").getType() == DataType.COMPOSITE, "a pack needs no value");
		helper.assertTrue(!ControlVariables.create(global, null, "", DataType.NUMBER, "1") && !ControlVariables.create(global, null, "  ", DataType.STRING, "x")
				&& !ControlVariables.create(global, null, "x".repeat(ControlVariables.MAX_NAME + 1), DataType.STRING, "x") && !ControlVariables.create(global, null, "n", DataType.NUMBER, "abc")
				&& !ControlVariables.create(global, null, "e", DataType.ENUM, "1") && global.size() == 3, "an empty or over-long name, a number that does not parse, an enum: nothing made");

		Control c = button(new ControlPanel(null));
		Map<String, DataValue> vars = c.vars;
		int declared = vars.size();
		helper.assertTrue(declared > 0, "a button declares its own variables");
		helper.assertTrue(ControlVariables.create(vars, c.customVarNames, "mine", DataType.NUMBER, "9") && c.customVarNames.contains("mine"), "a local variable is recorded as the player's");
		helper.assertTrue(!ControlVariables.canDelete(c.customVarNames, "isLit") && ControlVariables.canDelete(c.customVarNames, "mine") && ControlVariables.canDelete(null, "anything"),
				"only the player's local variables can be deleted; any global one can");
		helper.assertTrue(!ControlVariables.delete(vars, c.customVarNames, "isLit") && vars.containsKey("isLit"), "a variable the control declares is not deleted");
		helper.assertTrue(ControlVariables.setFromText(vars, "mine", "12.5") && vars.get("mine").getNumber() == 12.5F, "typing a number updates it");
		helper.assertTrue(!ControlVariables.setFromText(vars, "mine", "-") && vars.get("mine").getNumber() == 12.5F, "half-typed text leaves the old value");
		ControlVariables.create(vars, c.customVarNames, "text", DataType.STRING, "a");
		helper.assertTrue(ControlVariables.setFromText(vars, "text", "abc") && vars.get("text").toString().equals("abc"), "a string takes the whole text");
		helper.assertTrue(!ControlVariables.setFromText(vars, "color", "x") && !ControlVariables.isEditable(vars.get("color")), "an enum variable is listed, not typed into");
		helper.assertTrue(!ControlVariables.setFromText(vars, "missing", "1"), "a variable that is gone is not made by typing");
		ControlVariables.clear(vars, c.customVarNames);
		helper.assertTrue(vars.size() == declared && !vars.containsKey("mine") && !vars.containsKey("text"), "Clear on a control removes the player's variables and keeps its own");
		helper.assertTrue(ControlVariables.delete(global, null, "label") && !global.containsKey("label"), "a global variable is deleted");
		ControlVariables.clear(global, null);
		helper.assertTrue(global.isEmpty(), "and Clear on the panel empties it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the event lists

	@GameTest(template = EMPTY)
	public static void theEventListsAreTheControlsOwnThenWhatTheLinkedMachinesCanSendAndReceive(GameTestHelper helper) {
		Control c = button(new ControlPanel(null));
		Recorder one = new Recorder(BlockPos.ZERO, null, "tick", "ctrl_press", "no_such_event") {
			@Override public List<String> getOutEvents() { return List.of("initialize", "ctrl_press", "also_unregistered"); }
		};
		Recorder two = new Recorder(BlockPos.ZERO.north(), null, "tick", "initialize");
		List<IControllable> linked = List.of(one, two);

		List<String> receive = new ArrayList<>();
		for(ControlEvent e : ControlLinks.accumulateReceiveEvents(c, linked))
			receive.add(e.name);
		helper.assertTrue(receive.get(0).equals("ctrl_press") && receive.contains("initialize") && receive.stream().distinct().count() == receive.size() && !receive.contains("also_unregistered"),
				"receivable: the control's own first (a default graph indexes it), then the machines' out-events, once each, unregistered names left out: " + receive);
		List<String> send = new ArrayList<>();
		for(ControlEvent e : ControlLinks.accumulateSendEvents(linked))
			send.add(e.name);
		helper.assertTrue(send.equals(List.of("tick", "ctrl_press", "initialize")), "sendable: every machine's in-events, in order, once each, unregistered left out: " + send);
		helper.assertTrue(ControlLinks.accumulateSendEvents(List.of()).isEmpty(), "with no links there is nothing to send");
		helper.succeed();
	}
}
