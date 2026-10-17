package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.NodeButton;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownNode;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.IFunctionNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IInputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IOutputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean.BoolOperation;
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
import com.leafia.contents.machines.controlpanel.core.nodes.NodeOutput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeQueryBlock;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.gametest.LeafiaControlPanelCoreTests.TestControl;
import com.leafia.gametest.LeafiaControlPanelFixtures.DirectHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.AdaptedHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The stock node set of the Custom Control Panel (CCP-NODES, D-3062): every node's {@code evaluate} against a table written
 * from NTM-CE's source, its sockets and size, its NBT, and a graph run end to end from an event to a variable, a broadcast and a
 * block query.
 *
 * <p>Everything is synchronous on the server thread. The tests that read nodes back from NBT register the stock loader for
 * the length of the body and take it out in a {@code finally} (D-952); no test registers a controllable with
 * the level's event system, so nothing is left ticking (D-963).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelNodeTests {

	private static final String EMPTY = "empty";

	/** A position far outside anything a test loads. */
	private static final BlockPos FAR = new BlockPos(2_000_000, 64, 2_000_000);

	// ------------------------------------------------------------------ helpers

	private static Recorder owner(GameTestHelper helper) {
		return new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel(), "tick");
	}

	private static TestControl control(GameTestHelper helper) {
		return new TestControl(new ControlPanel(owner(helper)), 1, 1, 1);
	}

	private static void set(Node n, int input, float v) {
		n.inputs.get(input).setDefault(new DataValueFloat(v));
	}

	private static void set(Node n, int input, String s) {
		n.inputs.get(input).setDefault(new DataValueString(s));
	}

	private static DataValue eval(Node n) {
		n.cacheValid = false;
		return n.evaluate(0);
	}

	private static boolean same(float a, float b) {
		return Float.compare(a, b) == 0;
	}

	private static String sockets(Node n) {
		StringBuilder sb = new StringBuilder();
		for(NodeConnection c : n.inputs)
			sb.append(c.name).append(':').append(c.type).append(',');
		sb.append('>');
		for(NodeConnection c : n.outputs)
			sb.append(c.name).append(':').append(c.type).append(',');
		return sb.toString();
	}

	private interface Body {
		void run() throws Exception;
	}

	/** Runs with the stock loader registered, and takes it out whatever happens. */
	private static void withStock(Body body) {
		INodeLoader loader = new StockNodeLoader();
		NTMControlPanelRegistry.nbtNodeLoaders.add(loader);
		try {
			body.run();
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		} finally {
			NTMControlPanelRegistry.nbtNodeLoaders.remove(loader);
		}
	}

	/** Writes a system and reads it into a fresh one for the same control. */
	private static NodeSystem reload(NodeSystem sys, Control c) {
		NodeSystem back = new NodeSystem(c);
		back.readFromNBT(sys.writeToNBT(new CompoundTag()));
		return back;
	}

	private static void wire(Node to, int input, Node from, int output) {
		to.inputs.get(input).setData(from, output, true);
	}

	// ------------------------------------------------------------------ the stock register

	@GameTest(template = EMPTY)
	public static void theStockColoursAreCEs(GameTestHelper helper) {
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorInput, new float[]{0, 1, 0}), "input is green");
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorMath, new float[]{1, 1, 0}), "math is yellow");
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorBoolean, new float[]{1, 0, 1}), "boolean is magenta");
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorLogic, new float[]{.4F, .4F, 1}), "logic is blue");
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorOutput, new float[]{1, 0, 0}), "output is red");
		helper.assertTrue(java.util.Arrays.equals(StockNodesRegister.colorComposite, new float[]{1F, 0.399F, 0.842F}), "composite is pink");

		Control c = control(helper);
		helper.assertTrue(new NodeInput(0, 0, "x").getColor() == StockNodesRegister.colorInput, "input node");
		helper.assertTrue(new NodeGetVar(0, 0, c).getColor() == StockNodesRegister.colorInput, "get variable");
		helper.assertTrue(new NodeQueryBlock(0, 0, c).getColor() == StockNodesRegister.colorInput, "query block");
		helper.assertTrue(new NodeMath(0, 0).getColor() == StockNodesRegister.colorMath, "math");
		helper.assertTrue(new NodeBoolean(0, 0).getColor() == StockNodesRegister.colorBoolean, "boolean");
		helper.assertTrue(new NodeFunction(0, 0).getColor() == StockNodesRegister.colorLogic, "function");
		helper.assertTrue(new NodeBuffer(0, 0).getColor() == StockNodesRegister.colorLogic, "buffer");
		helper.assertTrue(new NodeConditional(0, 0).getColor() == StockNodesRegister.colorLogic, "conditional");
		helper.assertTrue(new NodeCancelEvent(0, 0).getColor() == StockNodesRegister.colorOutput, "cancel");
		helper.assertTrue(new NodeSetVar(0, 0, c).getColor() == StockNodesRegister.colorOutput, "set variable");
		helper.assertTrue(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("tick"))).getColor() == StockNodesRegister.colorOutput, "broadcast");
		helper.assertTrue(new NodeCompositeRead(0, 0).getColor() == DataType.COMPOSITE.getColor(), "pack read takes the type's colour");
		helper.assertTrue(new NodeCompositeWrite(0, 0).getColor() == DataType.COMPOSITE.getColor(), "pack write too");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void registerAddsTheStockLoaderOnce(GameTestHelper helper) {
		// Since CCP-PANEL (D-3063) the mod's own setup registers the stock loader, so the list is saved and put back whole: removing
		// "the" stock loader here would leave every later test in the run, in any class, without one.
		java.util.List<INodeLoader> saved = new java.util.ArrayList<>(NTMControlPanelRegistry.nbtNodeLoaders);
		try {
			NTMControlPanelRegistry.nbtNodeLoaders.removeIf(l -> l instanceof StockNodeLoader);
			StockNodesRegister.register();
			StockNodesRegister.register();
			long stock = NTMControlPanelRegistry.nbtNodeLoaders.stream().filter(l -> l instanceof StockNodeLoader).count();
			helper.assertTrue(stock == 1, "one stock loader after two calls, got " + stock);
		} finally {
			NTMControlPanelRegistry.nbtNodeLoaders.clear();
			NTMControlPanelRegistry.nbtNodeLoaders.addAll(saved);
		}
		helper.assertTrue(NTMControlPanelRegistry.nbtNodeLoaders.equals(saved), "and the list is put back");
		helper.succeed();
	}

	// ------------------------------------------------------------------ sockets and sizes

	@GameTest(template = EMPTY)
	public static void everyNodeHasCEsSocketsAndSize(GameTestHelper helper) {
		Control c = control(helper);
		check(helper, new NodeInput(0, 0, "evt"), ">", 0, "Input node starts with no sockets and has not been sized");
		check(helper, new NodeMath(0, 0), "Input 1:NUMBER,Input 2:NUMBER,>Output:NUMBER,", 34, "Math (Add): two inputs, one output, one dropdown");
		check(helper, new NodeBoolean(0, 0), "Input 1:NUMBER,Input 2:NUMBER,>Output:NUMBER,", 34, "Boolean (And)");
		check(helper, new NodeFunction(0, 0), "Enable:NUMBER,>", 18, "Function: Enable and an Edit Body row");
		check(helper, new NodeBuffer(0, 0), "Input:NUMBER,Delay:NUMBER,>Output:NUMBER,", 26, "Buffer");
		check(helper, new NodeConditional(0, 0), "Enable:NUMBER,Input:GENERIC,Default:GENERIC,>Output:GENERIC,", 34, "Conditional");
		check(helper, new NodeCompositeRead(0, 0), "Signal:COMPOSITE,Key:STRING,>Value:STRING,", 26, "Pack Read");
		check(helper, new NodeCompositeWrite(0, 0), "Enable:NUMBER,Key:STRING,Value:STRING,Base Signal:COMPOSITE,>Modified Signal:COMPOSITE,", 42, "Pack Write");
		check(helper, new NodeCancelEvent(0, 0), "do cancel:NUMBER,>", 10, "Cancel Event");
		check(helper, new NodeGetVar(0, 0, c), ">Output:GENERIC,", 26, "Get Variable: two dropdowns and an output");
		check(helper, new NodeSetVar(0, 0, c), "Input:GENERIC,>", 26, "Set Variable: two dropdowns and an input");
		check(helper, new NodeQueryBlock(0, 0, c), ">Output:GENERIC,", 26, "Query Block: two dropdowns and an output");
		NodeEventBroadcast b = new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("lever_toggle")));
		check(helper, b, "isOn:NUMBER,Cancel:GENERIC,>", 26, "Broadcast of lever_toggle: its variable and Cancel");

		helper.assertTrue(((Node) new NodeCompositeWrite(0, 0)).inputs.get(0).defaultValue.getNumber() == 1F, "Pack Write's Enable defaults to 1, the only input that does");
		helper.assertTrue(new NodeFunction(0, 0).evalCache.length == 1 && new NodeMath(0, 0).evalCache.length == 1, "evaluating nodes keep a one-slot cache");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Node n, String sockets, float size, String what) {
		helper.assertTrue(sockets(n).equals(sockets), what + ": sockets " + sockets + ", got " + sockets(n));
		helper.assertTrue(n.size == size, what + ": size " + size + ", got " + n.size);
	}

	@GameTest(template = EMPTY)
	public static void displayNamesAreCEs(GameTestHelper helper) {
		Control c = control(helper);
		helper.assertTrue(new NodeInput(0, 0, "lever_toggle").getDisplayName().equals("lever_toggle"), "an input node is named after its event");
		helper.assertTrue(new NodeMath(0, 0).getDisplayName().equals("Add"), "math shows its operation");
		helper.assertTrue(new NodeBoolean(0, 0).getDisplayName().equals("AND"), "boolean shows its operation");
		helper.assertTrue(new NodeFunction(0, 0).getDisplayName().equals("Function"), "function");
		helper.assertTrue(new NodeBuffer(0, 0).getDisplayName().equals("Buffer"), "buffer");
		helper.assertTrue(new NodeConditional(0, 0).getDisplayName().equals("Conditional"), "conditional");
		helper.assertTrue(new NodeCompositeRead(0, 0).getDisplayName().equals("Pack Read"), "pack read");
		helper.assertTrue(new NodeCompositeWrite(0, 0).getDisplayName().equals("Pack Write"), "pack write");
		helper.assertTrue(new NodeCancelEvent(0, 0).getDisplayName().equals("Cancel Event"), "cancel");
		helper.assertTrue(new NodeGetVar(0, 0, c).getDisplayName().equals("Get Variable"), "get variable");
		helper.assertTrue(new NodeSetVar(0, 0, c).getDisplayName().equals("Set Variable"), "set variable");
		helper.assertTrue(new NodeQueryBlock(0, 0, c).getDisplayName().equals("Query Block"), "query block");
		helper.assertTrue(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("tick"))).getDisplayName().equals("Broadcast"), "broadcast");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBatch1SeamsAreImplemented(GameTestHelper helper) {
		Control c = control(helper);
		helper.assertTrue(((Object) new NodeInput(0, 0, "x")) instanceof IInputNode, "NodeInput is an IInputNode");
		helper.assertTrue(((Object) new NodeFunction(0, 0)) instanceof IFunctionNode, "NodeFunction is an IFunctionNode");
		for(Node o : List.<Node>of(new NodeCancelEvent(0, 0), new NodeSetVar(0, 0, c), new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("tick"))))) {
			helper.assertTrue(((Object) o) instanceof IOutputNode && o instanceof NodeOutput, o.getDisplayName() + " is an output node");
			helper.assertTrue(o.evaluate(0) == null, o.getDisplayName() + " has no value to hand on");
		}
		helper.assertTrue(!(((Object) new NodeMath(0, 0)) instanceof IOutputNode) && !(((Object) new NodeMath(0, 0)) instanceof IFunctionNode), "a math node is neither");
		helper.succeed();
	}

	// ------------------------------------------------------------------ math

	private static final float INF = Float.POSITIVE_INFINITY;
	private static final float NAN = Float.NaN;

	/** op, a, b, c, expected. Written from CE's {@code evaluate}, not from the port. */
	private static final Object[][] MATH = {
			{Operation.ADD, 2F, 3F, 0F, 5F},
			{Operation.ADD, -1.5F, 0.25F, 0F, -1.25F},
			{Operation.SUB, 2F, 3F, 0F, -1F},
			{Operation.MULT, 4F, 2.5F, 0F, 10F},
			{Operation.DIV, 1F, 4F, 0F, 0.25F},
			{Operation.DIV, 1F, 0F, 0F, INF},
			{Operation.DIV, -1F, 0F, 0F, -INF},
			{Operation.DIV, 0F, 0F, 0F, NAN},
			{Operation.MOD, 7F, 3F, 0F, 1F},
			{Operation.MOD, -7F, 3F, 0F, -1F},
			{Operation.MOD, 5.5F, 2F, 0F, 1.5F},
			{Operation.MOD, 1F, 0F, 0F, NAN},
			{Operation.POW, 2F, 10F, 0F, 1024F},
			{Operation.POW, 9F, 0.5F, 0F, 3F},
			{Operation.POW, 0F, 0F, 0F, 1F},
			{Operation.LOG, 8F, 2F, 0F, 3F},
			{Operation.LOG, 100F, 10F, 0F, 2F},
			{Operation.LOG, 1F, 10F, 0F, 0F},
			{Operation.EXP, 0F, 0F, 0F, 1F},
			{Operation.EXP, 1F, 0F, 0F, 2.7182817F},
			{Operation.SQRT, 16F, 0F, 0F, 4F},
			{Operation.SQRT, -1F, 0F, 0F, NAN},
			{Operation.ABS, -3.5F, 0F, 0F, 3.5F},
			{Operation.ABS, 3.5F, 0F, 0F, 3.5F},
			{Operation.EQUAL, 2F, 2F, 0F, 1F},
			{Operation.EQUAL, 2F, 3F, 0F, 0F},
			{Operation.EQUAL, NAN, NAN, 0F, 0F},
			{Operation.GREATER, 3F, 2F, 0F, 1F},
			{Operation.GREATER, 2F, 2F, 0F, 0F},
			{Operation.LESS, 2F, 3F, 0F, 1F},
			{Operation.LESS, 3F, 3F, 0F, 0F},
			{Operation.GEQUAL, 3F, 3F, 0F, 1F},
			{Operation.GEQUAL, 2F, 3F, 0F, 0F},
			{Operation.LEQUAL, 3F, 3F, 0F, 1F},
			{Operation.LEQUAL, 4F, 3F, 0F, 0F},
			{Operation.CLAMP, 5F, 0F, 3F, 3F},
			{Operation.CLAMP, -1F, 0F, 3F, 0F},
			{Operation.CLAMP, 2F, 0F, 3F, 2F},
			{Operation.CLAMP, 5F, 3F, 0F, 0F},
			{Operation.MAX, 2F, 3F, 0F, 3F},
			{Operation.MAX, -2F, -3F, 0F, -2F},
			{Operation.MIN, 2F, 3F, 0F, 2F},
			{Operation.MIN, -2F, -3F, 0F, -3F},
			{Operation.FLOOR, 2.7F, 0F, 0F, 2F},
			{Operation.FLOOR, -2.5F, 0F, 0F, -3F},
			{Operation.CEIL, 2.1F, 0F, 0F, 3F},
			{Operation.CEIL, -2.1F, 0F, 0F, -2F},
			{Operation.ROUND, 2.5F, 0F, 0F, 3F},
			{Operation.ROUND, 2.4F, 0F, 0F, 2F},
			{Operation.ROUND, -2.5F, 0F, 0F, -2F},
			{Operation.ROUND, -2.6F, 0F, 0F, -3F},
	};

	@GameTest(template = EMPTY)
	public static void mathEvaluatesCEsTable(GameTestHelper helper) {
		for(Object[] row : MATH) {
			Operation op = (Operation) row[0];
			NodeMath m = new NodeMath(0, 0).setData(op);
			for(int i = 0; i < m.inputs.size(); i++)
				set(m, i, (Float) row[1 + i]);
			DataValue v = eval(m);
			float want = (Float) row[4];
			helper.assertTrue(v != null && same(v.getNumber(), want),
					op.name + "(" + row[1] + ", " + row[2] + ", " + row[3] + ") = " + want + ", got " + (v == null ? "nothing" : v.getNumber()));
			helper.assertTrue(v.getType() == DataType.NUMBER, op.name + " answers a number");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void everyMathOperationIsInTheTable(GameTestHelper helper) {
		for(Operation op : Operation.VALUES) {
			boolean found = false;
			for(Object[] row : MATH)
				found |= row[0] == op;
			helper.assertTrue(found, op.name + " has a row");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void mathOperationsAreInCEsOrderWithTheirSockets(GameTestHelper helper) {
		String names = "Add,Subtract,Multiply,Divide,Modulo,Power,Logarithm,Exponent,Square root,Absolute,Equal,Greater,Less,Greater/equal,Less/equal,Clamp,Max,Min,Floor,Ceil,Round";
		StringBuilder got = new StringBuilder();
		for(Operation op : Operation.VALUES)
			got.append(got.length() == 0 ? "" : ",").append(op.name);
		helper.assertTrue(got.toString().equals(names), "the 21 operations, in order: " + got);
		helper.assertTrue(Operation.VALUES[0] == Operation.ADD && Operation.VALUES[15] == Operation.CLAMP && Operation.VALUES[20] == Operation.ROUND, "ordinals are the saved index");
		helper.assertTrue(Operation.getByName("Logarithm") == Operation.LOG && Operation.getByName("log") == null, "lookup is by display name, exactly");

		NodeMath m = new NodeMath(0, 0);
		m.setOperation(Operation.POW);
		helper.assertTrue(sockets(m).equals("Base:NUMBER,Exponent:NUMBER,>Output:NUMBER,"), "Power: Base, Exponent");
		m.setOperation(Operation.LOG);
		helper.assertTrue(sockets(m).equals("Value:NUMBER,Base:NUMBER,>Output:NUMBER,"), "Logarithm: Value, Base");
		m.setOperation(Operation.CLAMP);
		helper.assertTrue(sockets(m).equals("Value:NUMBER,Min:NUMBER,Max:NUMBER,>Output:NUMBER,") && m.size == 42, "Clamp: Value, Min, Max");
		for(Operation op : List.of(Operation.EXP, Operation.SQRT, Operation.ABS, Operation.FLOOR, Operation.CEIL, Operation.ROUND)) {
			m.setOperation(op);
			helper.assertTrue(sockets(m).equals("Input:NUMBER,>Output:NUMBER,"), op.name + " has the one input");
		}
		for(Operation op : List.of(Operation.ADD, Operation.EQUAL, Operation.MAX, Operation.MOD)) {
			m.setOperation(op);
			helper.assertTrue(sockets(m).equals("Input 1:NUMBER,Input 2:NUMBER,>Output:NUMBER,"), op.name + " has Input 1 and Input 2");
		}
		for(Operation op : Operation.VALUES) {
			m.setOperation(op);
			helper.assertTrue(m.inputs.size() == op.arity, op.name + " reads " + op.arity + " inputs");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void changingAMathOperationCutsItsWires(GameTestHelper helper) {
		NodeMath src = new NodeMath(0, 0);
		NodeMath m = new NodeMath(50, 0);
		wire(m, 0, src, 0);
		helper.assertTrue(m.inputs.get(0).connection == src, "wired");
		m.setOperation(Operation.SUB);
		helper.assertTrue(m.inputs.get(0).connection == null && m.inputs.get(0).connectionIndex == -1, "a new operation rebuilds the sockets unwired");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aMathNodeReadsItsInputsThroughWires(GameTestHelper helper) {
		NodeMath a = new NodeMath(0, 0);
		set(a, 0, 6);
		set(a, 1, 7);
		NodeMath b = new NodeMath(50, 0).setData(Operation.MULT);
		wire(b, 0, a, 0);
		set(b, 1, 2);
		helper.assertTrue(same(eval(b).getNumber(), 26F), "(6+7)*2");
		// the cache: a changed default is not seen until the caches are reset
		set(a, 0, 10);
		helper.assertTrue(same(b.evaluate(0).getNumber(), 26F), "a cached node answers its cache");
		a.cacheValid = false;
		b.cacheValid = false;
		helper.assertTrue(same(b.evaluate(0).getNumber(), 34F), "and the new value once reset");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anInputThatEvaluatesToNothingMakesTheMathNodeAnswerNothing(GameTestHelper helper) {
		Control c = control(helper);
		NodeSetVar nothing = new NodeSetVar(0, 0, c);
		NodeMath m = new NodeMath(0, 0);
		wire(m, 0, nothing, 0);
		helper.assertTrue(eval(m) == null, "a connected output node has no value, so neither does the sum");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theMathDropdownSelectsAndSavesTheOperation(GameTestHelper helper) {
		NodeMath m = new NodeMath(0, 0);
		helper.assertTrue(m.otherElements.size() == 1 && m.otherElements.get(0) instanceof NodeDropdown, "one dropdown");
		NodeDropdown d = (NodeDropdown) m.otherElements.get(0);
		helper.assertTrue(d.list.itemNames.size() == 21 && d.list.itemNames.get(0).equals("Add") && d.list.itemNames.get(20).equals("Round"), "it lists the 21 operations");
		helper.assertTrue(d.currentName().equals("Add"), "showing the current one");
		helper.assertTrue(d.choose("Divide") == null, "choosing is final (no sub-list)");
		helper.assertTrue(m.op == Operation.DIV && d.currentName().equals("Divide"), "and sets the operation");
		d.choose("no such operation");
		helper.assertTrue(m.op == Operation.DIV, "an unknown name changes nothing");
		boolean threw = false;
		try {
			d.writeToNBT(new CompoundTag(), new NodeSystem(null));
		} catch(IllegalStateException e) {
			threw = true;
		}
		helper.assertTrue(threw, "a dropdown is never serialised");
		helper.succeed();
	}

	// ------------------------------------------------------------------ boolean

	@GameTest(template = EMPTY)
	public static void booleanEvaluatesItsTruthTables(GameTestHelper helper) {
		// op: results for (a,b) = (0,0) (0,1) (1,0) (1,1)
		Object[][] table = {
				{BoolOperation.AND, new int[]{0, 0, 0, 1}},
				{BoolOperation.OR, new int[]{0, 1, 1, 1}},
				{BoolOperation.XOR, new int[]{0, 1, 1, 0}},
				{BoolOperation.NAND, new int[]{1, 1, 1, 0}},
				{BoolOperation.NOR, new int[]{1, 0, 0, 0}},
				{BoolOperation.XNOR, new int[]{1, 0, 0, 1}},
		};
		for(Object[] row : table) {
			BoolOperation op = (BoolOperation) row[0];
			int[] want = (int[]) row[1];
			NodeBoolean n = new NodeBoolean(0, 0).setData(op);
			for(int i = 0; i < 4; i++) {
				set(n, 0, i / 2);
				set(n, 1, i % 2);
				DataValue v = eval(n);
				helper.assertTrue(v != null && same(v.getNumber(), want[i]), op + "(" + (i / 2) + "," + (i % 2) + ") = " + want[i] + ", got " + (v == null ? "nothing" : v.getNumber()));
			}
		}
		NodeBoolean not = new NodeBoolean(0, 0).setData(BoolOperation.NOT);
		set(not, 0, 0);
		helper.assertTrue(same(eval(not).getNumber(), 1F), "NOT 0 = 1");
		set(not, 0, 1);
		helper.assertTrue(same(eval(not).getNumber(), 0F), "NOT 1 = 0");
		helper.assertTrue(sockets(not).equals("Input:NUMBER,>Output:NUMBER,"), "NOT has the single Input");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void booleanReadsAnyNonZeroAsTrue(GameTestHelper helper) {
		NodeBoolean n = new NodeBoolean(0, 0);
		set(n, 0, 0.001F);
		set(n, 1, -5F);
		helper.assertTrue(same(eval(n).getNumber(), 1F), "0.001 AND -5 is true: both non-zero");
		set(n, 1, 0F);
		helper.assertTrue(same(eval(n).getNumber(), 0F), "and a zero is false");
		helper.assertTrue(BoolOperation.VALUES.length == 7 && BoolOperation.VALUES[2] == BoolOperation.NOT && BoolOperation.VALUES[6] == BoolOperation.XNOR, "seven operations in CE's order");
		helper.assertTrue(BoolOperation.getByName("XOR") == BoolOperation.XOR && BoolOperation.getByName("xor") == null, "lookup is by exact name");
		NodeDropdown d = (NodeDropdown) n.otherElements.get(0);
		helper.assertTrue(d.list.itemNames.equals(List.of("AND", "OR", "NOT", "XOR", "NAND", "NOR", "XNOR")), "the dropdown lists them");
		d.choose("NOT");
		helper.assertTrue(n.op == BoolOperation.NOT && n.inputs.size() == 1 && n.getDisplayName().equals("NOT"), "choosing NOT drops the second input");
		helper.succeed();
	}

	// ------------------------------------------------------------------ logic: function, buffer, conditional

	@GameTest(template = EMPTY)
	public static void aFunctionNodeAnswersItsEnableAsABit(GameTestHelper helper) {
		NodeFunction f = new NodeFunction(0, 0);
		set(f, 0, 0);
		helper.assertTrue(same(eval(f).getNumber(), 0F), "disabled");
		set(f, 0, 7.5F);
		helper.assertTrue(same(eval(f).getNumber(), 1F), "any non-zero enables, as exactly 1");
		helper.assertTrue(f.editBody instanceof NodeButton && f.otherElements.size() == 1 && f.otherElements.get(0) == f.editBody && f.editBody.name.equals("Edit Body"), "the Edit Body row");
		float[] box = f.editBody.getBox();
		helper.assertTrue(f.editBody.onClick(box[0] + 1, box[1] + 1) && !f.editBody.onClick(box[0], box[1]), "its click test is strict, as CE's box test is");

		Control c = control(helper);
		NodeSystem sys = new NodeSystem(c);
		sys.addNode(f);
		helper.assertTrue(sys.subSystems.containsKey(f), "adding a function node gives it a sub-graph");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBufferDelaysByItsCount(GameTestHelper helper) {
		NodeBuffer b = new NodeBuffer(0, 0);
		set(b, 0, 1);
		set(b, 1, 2);
		float[] got = new float[7];
		for(int i = 0; i < got.length; i++)
			got[i] = eval(b).getNumber();
		helper.assertTrue(java.util.Arrays.equals(got, new float[]{0, 0, 1, 0, 0, 1, 0}), "a held 1 with delay 2 passes on every third evaluation, got " + java.util.Arrays.toString(got));

		NodeBuffer idle = new NodeBuffer(0, 0);
		set(idle, 1, 3);
		helper.assertTrue(same(eval(idle).getNumber(), 0F) && idle.getInternalCount() == 0, "a false input with nothing counting stays at 0");

		NodeBuffer instant = new NodeBuffer(0, 0);
		set(instant, 0, 5);
		helper.assertTrue(same(eval(instant).getNumber(), 5F), "delay 0 passes the input itself, here 5, not 1");

		NodeBuffer neg = new NodeBuffer(0, 0);
		set(neg, 0, 1);
		set(neg, 1, -2);
		eval(neg);
		eval(neg);
		helper.assertTrue(same(eval(neg).getNumber(), 1F), "a negative delay counts as its magnitude");

		NodeBuffer frac = new NodeBuffer(0, 0);
		set(frac, 0, 1);
		set(frac, 1, 1.6F);
		eval(frac);
		eval(frac);
		helper.assertTrue(same(eval(frac).getNumber(), 1F), "and a fractional one is rounded (1.6 is 2)");

		NodeBuffer run = new NodeBuffer(0, 0);
		set(run, 0, 1);
		set(run, 1, 3);
		eval(run);
		set(run, 0, 0);
		eval(run);
		eval(run);
		helper.assertTrue(run.getInternalCount() == 3, "a count already running continues after the input drops, got " + run.getInternalCount());
		helper.assertTrue(same(eval(run).getNumber(), 0F), "the delayed value is the input at the moment it arrives: 0 now");
		helper.assertTrue(run.getInternalCount() == 0, "and the count resets");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBuffersCountIsSaved(GameTestHelper helper) {
		withStock(() -> {
			Control c = control(helper);
			NodeSystem sys = new NodeSystem(c);
			NodeBuffer b = new NodeBuffer(3, 4);
			sys.addNode(b);
			set(b, 0, 1);
			set(b, 1, 5);
			eval(b);
			eval(b);
			helper.assertTrue(b.getInternalCount() == 2, "counting");
			NodeSystem back = reload(sys, c);
			helper.assertTrue(back.nodes.get(0) instanceof NodeBuffer nb && nb.getInternalCount() == 2, "a reload resumes the count");
			NodeBuffer nb = (NodeBuffer) back.nodes.get(0);
			helper.assertTrue(nb.posX == 3 && nb.posY == 4, "at the same place");
			helper.assertTrue(same(nb.inputs.get(1).defaultValue.getNumber(), 5F), "with the same delay");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aConditionalPicksInputOrDefault(GameTestHelper helper) {
		NodeConditional n = new NodeConditional(0, 0);
		set(n, 1, 11);
		set(n, 2, 22);
		set(n, 0, 0);
		helper.assertTrue(same(eval(n).getNumber(), 22F), "disabled gives Default");
		set(n, 0, 1);
		helper.assertTrue(same(eval(n).getNumber(), 11F), "enabled gives Input");
		n.inputs.get(1).setDefault(new DataValueString("yes"));
		helper.assertTrue(eval(n).toString().equals("yes"), "and the value is passed as it is, here a string");
		helper.succeed();
	}

	// ------------------------------------------------------------------ packs

	@GameTest(template = EMPTY)
	public static void aPackReadFindsAKeyOrAnEmptyStringOrAnError(GameTestHelper helper) {
		DataValueComposite pack = new DataValueComposite();
		pack.setValueOf("name", "reactor");
		NodeCompositeRead r = new NodeCompositeRead(0, 0);
		r.inputs.get(0).setDefault(pack);
		set(r, 1, "name");
		helper.assertTrue(eval(r).toString().equals("reactor"), "reads the key");
		set(r, 1, "missing");
		helper.assertTrue(eval(r).toString().equals(""), "a key the pack lacks reads as the empty string");
		helper.assertTrue(eval(r).getType() == DataType.STRING, "always a string");

		NodeSetVar nothing = new NodeSetVar(0, 0, control(helper));
		NodeCompositeRead broken = new NodeCompositeRead(0, 0);
		wire(broken, 0, nothing, 0);
		helper.assertTrue(eval(broken).toString().equals("ERROR"), "an input with no value reads as ERROR");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aPackWriteWritesACopy(GameTestHelper helper) {
		DataValueComposite base = new DataValueComposite();
		base.setValueOf("a", "1");
		NodeCompositeWrite w = new NodeCompositeWrite(0, 0);
		w.inputs.get(3).setDefault(base);
		set(w, 1, "b");
		set(w, 2, "2");
		DataValue out = eval(w);
		helper.assertTrue(out instanceof DataValueComposite && out.getValueOf("a").equals("1") && out.getValueOf("b").equals("2"), "keeps the base's keys and adds its own");
		helper.assertTrue(base.getValueOf("b").isEmpty(), "the base pack itself is untouched");
		helper.assertTrue(out != base, "the answer is a copy");

		set(w, 0, 0);
		out = eval(w);
		helper.assertTrue(out.getValueOf("a").equals("1") && out.getValueOf("b").isEmpty(), "with Enable off it passes the base through unchanged");

		set(w, 0, 1);
		set(w, 2, "");
		w.inputs.get(3).setDefault(out);
		set(w, 1, "a");
		out = eval(w);
		helper.assertTrue(out.getValueOf("a").isEmpty(), "writing a blank value removes the key");

		NodeCompositeWrite fresh = new NodeCompositeWrite(0, 0);
		set(fresh, 1, "k");
		set(fresh, 2, "v");
		helper.assertTrue(eval(fresh).getValueOf("k").equals("v"), "with the default empty base it starts a pack");
		NodeCompositeWrite blankKey = new NodeCompositeWrite(0, 0);
		set(blankKey, 2, "v");
		helper.assertTrue(((DataValueComposite) eval(blankKey)).snapshot().isEmpty(), "a blank key writes nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ input, cancel

	@GameTest(template = EMPTY)
	public static void anInputNodeTakesTheEventsVariables(GameTestHelper helper) {
		ControlEvent proto = ControlEvent.getRegisteredEvent("pwr_computer_set_level");
		NodeInput in = new NodeInput(0, 0, "pwr_computer_set_level").setVars(proto.vars);
		helper.assertTrue(sockets(in).equals(">name:STRING,level:NUMBER,"), "one output per variable, typed by it, got " + sockets(in));
		helper.assertTrue(in.size == 18, "and sized for them: two rows plus 2");

		ControlEvent evt = ControlEvent.newEvent("pwr_computer_set_level").setVar("name", "rod 3").setVar("level", 40);
		in.setOutputFromVars(evt.vars);
		helper.assertTrue(in.evaluate(0).toString().equals("rod 3") && same(in.evaluate(1).getNumber(), 40F), "the event's values are the outputs");

		in.setOutputFromVars(new ControlEvent("x").setVar("level", 41).vars);
		helper.assertTrue(in.evaluate(0).toString().equals("rod 3") && same(in.evaluate(1).getNumber(), 41F), "a variable the event lacks keeps its last value (CE threw)");

		NodeInput idx = new NodeInput(0, 0, "x").setVars(new ControlEvent("x").setVar("from index", 0).vars);
		idx.setOutputFromVars(new ControlEvent("x").setVar("from index", 9).vars);
		helper.assertTrue(same(idx.evaluate(0).getNumber(), 0F), "an output named 'from index' is left alone, as in CE");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCancelNodeReturnsFalseOnlyWhenAskedTo(GameTestHelper helper) {
		NodeCancelEvent n = new NodeCancelEvent(0, 0);
		Recorder o = owner(helper);
		set(n, 0, 0);
		helper.assertTrue(n.doOutput(o, null, Map.of()), "do cancel 0: processing continues");
		set(n, 0, 1);
		helper.assertTrue(!n.doOutput(o, null, Map.of()), "do cancel 1: cancelled");
		set(n, 0, -3);
		helper.assertTrue(!n.doOutput(o, null, Map.of()), "any non-zero cancels");
		wire(n, 0, new NodeSetVar(0, 0, control(helper)), 0);
		helper.assertTrue(n.doOutput(o, null, Map.of()), "an input with no value cancels nothing (CE threw)");
		helper.succeed();
	}

	// ------------------------------------------------------------------ variables

	@GameTest(template = EMPTY)
	public static void getVarReadsLocalAndGlobalVariables(GameTestHelper helper) {
		TestControl c = control(helper);
		c.vars.put("speed", new DataValueFloat(4));
		c.vars.put("label", new DataValueString("hi"));
		c.panel.globalVars.put("shared", new DataValueFloat(9));

		NodeGetVar g = new NodeGetVar(0, 0, c);
		helper.assertTrue(same(g.evaluate(0).getNumber(), 0F), "no name reads 0");
		NodeDropdown scope = (NodeDropdown) g.otherElements.get(0);
		NodeDropdown which = g.varSelector;
		helper.assertTrue(scope.list.itemNames.equals(List.of("Global", "Local")) && scope.currentName().equals("Local"), "scope starts Local");
		helper.assertTrue(which.list.itemNames.equals(List.of("speed", "label")), "the variable list is the control's, got " + which.list.itemNames);

		which.choose("speed");
		helper.assertTrue(g.varName.equals("speed") && same(g.evaluate(0).getNumber(), 4F), "reads the local");
		helper.assertTrue(g.outputs.get(0).type == DataType.NUMBER, "and types its output by it");
		which.choose("label");
		helper.assertTrue(g.evaluate(0).toString().equals("hi") && g.outputs.get(0).type == DataType.STRING, "a string variable gives a string output");

		scope.choose("Global");
		helper.assertTrue(g.global && g.varName.isEmpty() && which.list.itemNames.equals(List.of("shared")), "switching scope clears the name and lists the globals");
		which.choose("shared");
		helper.assertTrue(same(g.evaluate(0).getNumber(), 9F), "reads the global");
		scope.choose("Global");
		helper.assertTrue(g.varName.equals("shared"), "choosing the scope it already has changes nothing");

		g.setData("nobody", false);
		helper.assertTrue(same(g.evaluate(0).getNumber(), 0F), "a variable nobody set reads 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void setVarWritesLocalAndGlobalVariables(GameTestHelper helper) {
		TestControl c = control(helper);
		Recorder o = (Recorder) c.panel.parent;
		NodeSetVar s = new NodeSetVar(0, 0, c);
		set(s, 0, 3);
		helper.assertTrue(s.doOutput(o, null, Map.of()) && c.vars.isEmpty(), "with no name it writes nothing and continues");

		s.setData("rpm", false);
		helper.assertTrue(s.doOutput(o, null, Map.of()) && same(c.vars.get("rpm").getNumber(), 3F), "writes the local");
		s.setData("total", true);
		set(s, 0, 8);
		helper.assertTrue(s.doOutput(o, null, Map.of()) && same(c.panel.globalVars.get("total").getNumber(), 8F), "writes the global");
		helper.assertTrue(!c.vars.containsKey("total") && !c.panel.globalVars.containsKey("rpm"), "each in its own scope");

		NodeSetVar nothing = new NodeSetVar(0, 0, c);
		wire(s, 0, nothing, 0);
		helper.assertTrue(s.doOutput(o, null, Map.of()) && same(c.panel.globalVars.get("total").getNumber(), 8F), "an input with no value leaves the variable as it was");

		// picking an existing variable adopts its type and value as the input's default
		c.vars.put("name", new DataValueString("x"));
		NodeSetVar t = new NodeSetVar(0, 0, c);
		t.varSelector.choose("name");
		helper.assertTrue(t.inputs.get(0).type == DataType.STRING && t.inputs.get(0).defaultValue.toString().equals("x"), "choosing a variable adopts its type and value");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theVariableNodesSaveAndReload(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			c.vars.put("speed", new DataValueFloat(4));
			c.vars.put("label", new DataValueString("hi"));
			NodeSystem sys = new NodeSystem(c);
			NodeGetVar g = new NodeGetVar(1, 2, c).setData("label", false);
			NodeSetVar s = new NodeSetVar(3, 4, c).setData("speed", false);
			sys.addNode(g);
			sys.addNode(s);
			NodeSystem back = reload(sys, c);
			NodeGetVar g2 = (NodeGetVar) back.nodes.get(0);
			NodeSetVar s2 = (NodeSetVar) back.nodes.get(1);
			helper.assertTrue(g2.varName.equals("label") && !g2.global && g2.outputs.get(0).type == DataType.STRING, "get variable keeps its name and refreshes its output type");
			helper.assertTrue(s2.varName.equals("speed") && s2.inputs.get(0).type == DataType.NUMBER && same(s2.inputs.get(0).defaultValue.getNumber(), 4F), "set variable keeps its name and adopts the type and value");
			helper.assertTrue(g2.varSelector.list.itemNames.equals(List.of("speed", "label")), "and both refill their dropdowns");
			helper.assertTrue(back.outputNodes.size() == 1 && back.outputNodes.get(0) == s2, "the set node is the output node");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ the dropdown and its list

	@GameTest(template = EMPTY)
	public static void theItemListKeepsItsGeometryAndItems(GameTestHelper helper) {
		ItemList l = new ItemList(10, 20, 32, s -> null);
		helper.assertTrue(!l.isClosed, "starts open");
		l.addItems("a").addItems("b", "c").addItems("d", "e", "f").addItems(List.of("g"));
		helper.assertTrue(l.itemNames.equals(List.of("a", "b", "c", "d", "e", "f", "g")), "every overload appends in order");
		float[] box = l.getBoundingBox();
		helper.assertTrue(box[0] == 10 && box[1] == 20 && box[2] == 42 && box[3] == 20 + 7 * 6 + 4, "6 per row plus 4: " + java.util.Arrays.toString(box));
		helper.assertTrue(l.getMouseoverIndex(15, 22.5F) == 0 && l.getMouseoverIndex(15, 28.5F) == 1, "rows start 2 below the top and are 6 apart");
		helper.assertTrue(l.getMouseoverIndex(15, 27.5F) == -1 && l.getMouseoverIndex(10, 22.5F) == -1 && l.getMouseoverIndex(15, 20F) == -1, "the gaps and the left edge are outside, as in CE");
		ItemList child = new ItemList(42, 30, 40, s -> null);
		child.addItems("x", "y");
		l.child = child;
		box = l.getBoundingBox();
		helper.assertTrue(box[0] == 10 && box[1] == 20 && box[2] == 82 && box[3] == 66, "a child widens the box to the right, and the taller list sets the bottom: " + java.util.Arrays.toString(box));
		l.close();
		helper.assertTrue(l.isClosed, "close");
		l.open();
		helper.assertTrue(!l.isClosed, "open");

		Control c = control(helper);
		NodeMath m = new NodeMath(0, 0);
		NodeDropdown d = (NodeDropdown) m.otherElements.get(0);
		helper.assertTrue(d.list.isClosed && d.list.width == 32, "a node's list is created closed and 32 wide");
		d.setOffset(5, 6);
		helper.assertTrue(d.list.posX == 9 && d.list.posY == 20, "and sits 4 right and 14 below its row");
		m.setPosition(100, 200);
		d.resetOffset();
		helper.assertTrue(d.offsetX == 100 && d.list.posX == 104 && d.list.posY == 214, "moving the node moves the list");
		helper.assertTrue(c != null, "control built");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the broadcast

	private static DirectHost host(GameTestHelper helper, int x, int z, String... in) {
		BlockPos rel = new BlockPos(x, 1, z);
		helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
		DirectHost h = (DirectHost) helper.getBlockEntity(rel);
		h.rec.in = new ArrayList<>(List.of(in));
		return h;
	}

	private static NodeEventBroadcast leverBroadcast() {
		return new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("lever_toggle"), ControlEvent.getRegisteredEvent("door_toggle")));
	}

	@GameTest(template = EMPTY)
	public static void aBroadcastSendsItsEventToEveryLink(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		DirectHost a = host(helper, 1, 3, "lever_toggle");
		DirectHost b = host(helper, 3, 3, "lever_toggle");
		DirectHost deaf = host(helper, 3, 1, "door_toggle");
		c.taggedLinks.put("a", a.getBlockPos());
		c.taggedLinks.put("b", b.getBlockPos());
		c.taggedLinks.put("deaf", deaf.getBlockPos());
		c.taggedLinks.put("nowhere", FAR);

		NodeEventBroadcast n = leverBroadcast();
		helper.assertTrue(n.eventName.equals("lever_toggle"), "starts on the first event it was offered");
		set(n, 0, 1);
		boolean cont = n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks);
		helper.assertTrue(cont, "a broadcast never cancels");
		helper.assertTrue(a.rec.received.size() == 1 && b.rec.received.size() == 1, "both listeners got it, once each");
		var r = a.rec.received.get(0);
		helper.assertTrue(r.from().equals(c.panel.parent.getControlPos()), "from the panel's owner");
		helper.assertTrue(r.event().name.equals("lever_toggle") && same(r.event().vars.get("isOn").getNumber(), 1F), "with the wired variable");
		helper.assertTrue(!r.event().vars.containsKey("Cancel"), "and without the Cancel input");
		helper.assertTrue(deaf.rec.received.isEmpty(), "a target that does not list the event gets nothing");
		helper.assertTrue(!helper.getLevel().isLoaded(FAR), "the unloaded link was refused, not loaded");

		// the node can be re-pointed at another event: sockets follow it
		((NodeDropdown) n.otherElements.get(0)).choose("door_toggle");
		helper.assertTrue(n.eventName.equals("door_toggle") && sockets(n).equals("passcode:NUMBER,Cancel:GENERIC,>"), "choosing an event rebuilds the sockets, got " + sockets(n));
		set(n, 0, 1234);
		n.doOutput(c.panel.parent, null, c.taggedLinks);
		helper.assertTrue(deaf.rec.received.size() == 1 && same(deaf.rec.received.get(0).event().vars.get("passcode").getNumber(), 1234F), "with no send graphs map at all it still sends");
		helper.assertTrue(a.rec.received.size() == 1, "and only to those who listen");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aTrueCancelInputSendsNothing(GameTestHelper helper) {
		TestControl c = control(helper);
		DirectHost a = host(helper, 1, 3, "lever_toggle");
		c.taggedLinks.put("a", a.getBlockPos());
		NodeEventBroadcast n = leverBroadcast();
		n.inputs.get(1).setDefault(new DataValueFloat(1));
		helper.assertTrue(n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks) && a.rec.received.isEmpty(), "Cancel = 1: nothing sent, processing continues");
		n.inputs.get(1).setDefault(new DataValueFloat(0));
		n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks);
		helper.assertTrue(a.rec.received.size() == 1, "Cancel = 0: sent");
		helper.succeed();
	}

	/** An output that cancels its first call and records the receiver id it was run for. */
	static final class CancelFirst extends NodeOutput {
		final NodeSystem sys;
		final boolean cancelFirst;
		final List<Float> ids = new ArrayList<>();

		CancelFirst(NodeSystem sys, boolean cancelFirst) {
			super(0, 0);
			this.sys = sys;
			this.cancelFirst = cancelFirst;
		}

		@Override
		public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> links) {
			ids.add(sys.getVar("receiver_id").getNumber());
			return !(cancelFirst && ids.size() == 1);
		}

		@Override public float[] getColor() { return new float[]{1, 0, 0}; }
		@Override public String getDisplayName() { return "cancel first"; }
	}

	@GameTest(template = EMPTY)
	public static void aSendGraphRunsOncePerReceiverAndCanSkipOne(GameTestHelper helper) {
		TestControl c = control(helper);
		DirectHost a = host(helper, 1, 3, "lever_toggle");
		DirectHost b = host(helper, 3, 3, "lever_toggle");
		c.taggedLinks.put("a", a.getBlockPos());
		c.taggedLinks.put("b", b.getBlockPos());
		NodeEventBroadcast n = leverBroadcast();
		set(n, 0, 1);

		NodeSystem sys = new NodeSystem(c);
		CancelFirst cancel = new CancelFirst(sys, true);
		sys.addNode(cancel);
		c.sendNodeMap.put("lever_toggle", sys);
		n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks);
		helper.assertTrue(cancel.ids.equals(List.of(0F, 0F)), "the graph ran for each link, and a cancelled receiver does not use up an id, got " + cancel.ids);
		helper.assertTrue(a.rec.received.isEmpty() && b.rec.received.size() == 1, "the first receiver was skipped, the second sent");

		NodeSystem all = new NodeSystem(c);
		CancelFirst pass = new CancelFirst(all, false);
		all.addNode(pass);
		c.sendNodeMap.put("lever_toggle", all);
		n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks);
		helper.assertTrue(pass.ids.equals(List.of(0F, 1F)), "receiver_id counts 0, 1, got " + pass.ids);
		helper.assertTrue(a.rec.received.size() == 1 && b.rec.received.size() == 2, "and each is then sent the event");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBroadcastOfAnUnregisteredEventSendsNothing(GameTestHelper helper) {
		TestControl c = control(helper);
		DirectHost a = host(helper, 1, 3, "lever_toggle");
		c.taggedLinks.put("a", a.getBlockPos());
		NodeEventBroadcast n = leverBroadcast();
		n.eventName = "not_an_event";
		helper.assertTrue(n.doOutput(c.panel.parent, c.sendNodeMap, c.taggedLinks) && a.rec.received.isEmpty(), "no throw, no send");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the block query

	@GameTest(template = EMPTY)
	public static void aQueryReadsTheLinkedBlocksValue(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		DirectHost h = host(helper, 2, 2);
		h.rec.query.put("heat", new DataValueFloat(250));
		h.rec.query.put("label", new DataValueString("core"));
		c.taggedLinks.put("reactor", h.getBlockPos());

		NodeQueryBlock q = new NodeQueryBlock(0, 0, c);
		helper.assertTrue(q.tagSelector.list.itemNames.equals(List.of("reactor")), "the first dropdown lists the control's link tags");
		helper.assertTrue(q.dataSelector.list.itemNames.isEmpty() && same(q.evaluate(0).getNumber(), 0F), "nothing chosen reads 0");

		q.tagSelector.choose("reactor");
		helper.assertTrue(q.tag.equals("reactor") && q.dataName.isEmpty(), "choosing a tag clears the data name");
		helper.assertTrue(q.dataSelector.list.itemNames.equals(List.of("heat", "label")), "the second lists the block's query keys, got " + q.dataSelector.list.itemNames);
		q.dataSelector.choose("heat");
		helper.assertTrue(same(q.evaluate(0).getNumber(), 250F) && q.outputs.get(0).type == DataType.NUMBER, "reads the number and types its output by it");
		q.dataSelector.choose("label");
		helper.assertTrue(q.evaluate(0).toString().equals("core") && q.outputs.get(0).type == DataType.STRING, "or a string");

		h.rec.query.put("label", new DataValueString("changed"));
		helper.assertTrue(q.evaluate(0).toString().equals("changed"), "it reads live, not at selection time");
		h.rec.query.remove("label");
		helper.assertTrue(same(q.evaluate(0).getNumber(), 0F), "a key the block no longer has reads 0");
		helper.assertTrue(q.dataSelector.list.itemNames.equals(List.of("heat")), "and the key list is refreshed");

		q.setData("elsewhere");
		q.dataName = "heat";
		helper.assertTrue(same(q.evaluate(0).getNumber(), 0F), "a tag that is not linked reads 0");
		helper.assertTrue(q.linkedPos() == null, "and has no position");

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		q.setData("reactor");
		helper.assertTrue(same(q.evaluate(0).getNumber(), 0F), "a block that is gone reads 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aQueryRefusesAnUnloadedPosition(GameTestHelper helper) {
		TestControl c = control(helper);
		c.taggedLinks.put("far", FAR);
		NodeQueryBlock q = new NodeQueryBlock(0, 0, c);
		q.tagSelector.choose("far");
		helper.assertTrue(q.dataSelector.list.itemNames.isEmpty(), "no keys are offered for a block that is not loaded");
		q.dataName = "heat";
		helper.assertTrue(same(q.evaluate(0).getNumber(), 0F), "the value reads 0");
		helper.assertTrue(!helper.getLevel().isLoaded(FAR) && helper.getLevel().getChunkSource().getChunkNow(FAR.getX() >> 4, FAR.getZ() >> 4) == null,
				"and the chunk was not loaded to look");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aQueryReachesABaseMachineThroughTheCapability(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, LeafiaControlPanelFixtures.adapted);
		AdaptedHost h = (AdaptedHost) helper.getBlockEntity(rel);
		h.adapter.query.put("fluidAmount", new DataValueFloat(8000));
		c.taggedLinks.put("tank", helper.absolutePos(rel));

		NodeQueryBlock q = new NodeQueryBlock(0, 0, c);
		q.tagSelector.choose("tank");
		helper.assertTrue(q.dataSelector.list.itemNames.equals(List.of("fluidAmount")), "the adapter's keys are offered");
		q.dataSelector.choose("fluidAmount");
		helper.assertTrue(same(q.evaluate(0).getNumber(), 8000F), "and its value read");
		helper.succeed();
	}

	// ------------------------------------------------------------------ NBT

	@GameTest(template = EMPTY)
	public static void everyStockNodeRoundTripsThroughTheLoader(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			c.vars.put("v", new DataValueFloat(1));
			NodeSystem sys = new NodeSystem(c);
			List<Node> made = new ArrayList<>(List.of(
					new NodeInput(1, 1, "lever_toggle").setVars(ControlEvent.getRegisteredEvent("lever_toggle").vars),
					new NodeMath(2, 2).setData(Operation.CLAMP),
					new NodeBoolean(3, 3).setData(BoolOperation.XNOR),
					new NodeFunction(4, 4), new NodeBuffer(5, 5), new NodeConditional(6, 6),
					new NodeCompositeRead(7, 7), new NodeCompositeWrite(8, 8), new NodeCancelEvent(9, 9),
					new NodeGetVar(10, 10, c).setData("v", false), new NodeSetVar(11, 11, c).setData("v", false),
					new NodeQueryBlock(12, 12, c).setData("somewhere"),
					new NodeEventBroadcast(13, 13, List.of(ControlEvent.getRegisteredEvent("door_toggle"), ControlEvent.getRegisteredEvent("lever_toggle")))));
			for(Node n : made)
				sys.addNode(n);
			wire(made.get(2), 0, made.get(1), 0);

			CompoundTag first = sys.writeToNBT(new CompoundTag());
			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(first);
			helper.assertTrue(back.nodes.size() == made.size(), "every node came back");
			for(int i = 0; i < made.size(); i++) {
				helper.assertTrue(back.nodes.get(i).getClass() == made.get(i).getClass(), "node " + i + " is a " + made.get(i).getClass().getSimpleName() + ", got " + back.nodes.get(i).getClass().getSimpleName());
				helper.assertTrue(back.nodes.get(i).posX == i + 1 && back.nodes.get(i).posY == i + 1, "node " + i + " at its place");
			}
			helper.assertTrue(back.writeToNBT(new CompoundTag()).equals(first), "and writing it again gives the same tree");
			helper.assertTrue(back.nodes.get(2).inputs.get(0).connection == back.nodes.get(1), "wires are rebuilt to the new nodes");
			helper.assertTrue(((NodeMath) back.nodes.get(1)).op == Operation.CLAMP && ((NodeBoolean) back.nodes.get(2)).op == BoolOperation.XNOR, "operations kept");
			helper.assertTrue(((NodeInput) back.nodes.get(0)).name.equals("lever_toggle") && sockets(back.nodes.get(0)).equals(">isOn:NUMBER,"), "the input keeps its name and sockets");
			helper.assertTrue(back.subSystems.containsKey(back.nodes.get(3)), "the function node has its sub-graph");
			helper.assertTrue(back.outputNodes.size() == 3, "three output nodes: cancel, set variable, broadcast");
			NodeEventBroadcast bb = (NodeEventBroadcast) back.nodes.get(12);
			helper.assertTrue(bb.eventName.equals("door_toggle") && ((NodeDropdown) bb.otherElements.get(0)).list.itemNames.equals(List.of("door_toggle", "lever_toggle")), "the broadcast keeps its event and its list of events");
			helper.assertTrue(((NodeQueryBlock) back.nodes.get(11)).tag.equals("somewhere"), "the query keeps its tag");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFunctionNodeKeepsItsSubGraph(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			NodeSystem sys = new NodeSystem(c);
			NodeFunction f = new NodeFunction(0, 0);
			sys.addNode(f);
			NodeSystem body = sys.subSystems.get(f);
			body.addNode(new NodeMath(5, 5).setData(Operation.ABS));
			body.addNode(new NodeSetVar(9, 9, c));
			NodeSystem back = reload(sys, c);
			NodeSystem body2 = back.subSystems.get(back.nodes.get(0));
			helper.assertTrue(body2 != null && body2.nodes.size() == 2 && body2.nodes.get(0) instanceof NodeMath, "the body came back");
			helper.assertTrue(body2.outputNodes.size() == 1, "with its output node");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void hostileOperationIndicesAreReducedNotTrusted(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			for(int bad : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 21, 7}) {
				NodeSystem sys = new NodeSystem(c);
				sys.addNode(new NodeMath(0, 0));
				sys.addNode(new NodeBoolean(0, 0));
				CompoundTag tag = sys.writeToNBT(new CompoundTag());
				tag.getCompound("N").getCompound("n0").putInt("op", bad);
				tag.getCompound("N").getCompound("n1").putInt("op", bad);
				NodeSystem back = new NodeSystem(c);
				back.readFromNBT(tag);
				NodeMath m = (NodeMath) back.nodes.get(0);
				NodeBoolean b = (NodeBoolean) back.nodes.get(1);
				helper.assertTrue(m.op == Operation.VALUES[Math.floorMod(bad, 21)] && b.op == BoolOperation.VALUES[Math.floorMod(bad, 7)], "index " + bad + " reads as its residue");
			}
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNodeWithTooFewSavedSocketsAnswersNothing(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			NodeSystem sys = new NodeSystem(c);
			sys.addNode(new NodeMath(0, 0));
			sys.addNode(new NodeBoolean(0, 0));
			CompoundTag tag = sys.writeToNBT(new CompoundTag());
			tag.getCompound("N").getCompound("n0").getCompound("in").remove("con1");
			tag.getCompound("N").getCompound("n1").getCompound("in").remove("con1");
			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(tag);
			helper.assertTrue(back.nodes.get(0) instanceof NodeMath && back.nodes.get(0).inputs.size() == 1, "the truncated tag read");
			helper.assertTrue(eval(back.nodes.get(0)) == null && eval(back.nodes.get(1)) == null, "evaluating answers nothing instead of reading past the sockets");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBroadcastNamingAnUnknownEventIsKeptAsAPlaceholder(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			NodeSystem sys = new NodeSystem(c);
			sys.addNode(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("lever_toggle"))));
			CompoundTag tag = sys.writeToNBT(new CompoundTag());
			CompoundTag node = tag.getCompound("N").getCompound("n0");
			node.getCompound("itemList").putString("item0", "event_from_a_missing_addon");
			CompoundTag want = node.copy();

			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(tag);
			helper.assertTrue(back.nodes.get(0) instanceof UnknownNode, "an event nobody registered makes a placeholder");
			CompoundTag again = back.writeToNBT(new CompoundTag()).getCompound("N").getCompound("n0");
			helper.assertTrue(again.getString("nodeType").equals("eventBroadcast") && again.getCompound("itemList").equals(want.getCompound("itemList")), "which writes the original back");

			node.put("itemList", new CompoundTag());
			NodeSystem empty = new NodeSystem(c);
			empty.readFromNBT(tag);
			helper.assertTrue(empty.nodes.get(0) instanceof UnknownNode, "an empty event list is a placeholder, not a crash");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLoaderIgnoresTypesItDoesNotKnow(GameTestHelper helper) {
		TestControl c = control(helper);
		CompoundTag tag = new CompoundTag();
		tag.putString("nodeType", "redstoneInput");
		helper.assertTrue(new StockNodeLoader().nodeFromNBT(tag, new NodeSystem(c)) instanceof com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneInput,
				"the redstone nodes arrived with batch 3 (D-3063), and the loader knows both");
		tag.putString("nodeType", "redstoneOutput");
		helper.assertTrue(new StockNodeLoader().nodeFromNBT(tag, new NodeSystem(c)) instanceof com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneOutput,
				"redstoneOutput too");
		tag.putString("nodeType", "something_else");
		helper.assertTrue(new StockNodeLoader().nodeFromNBT(tag, new NodeSystem(c)) == null, "an unknown type is null");
		helper.succeed();
	}

	// ------------------------------------------------------------------ a graph, end to end

	@GameTest(template = EMPTY)
	public static void anEventDrivesAGraphToAVariableAndABroadcast(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		withStock(() -> {
			TestControl c = control(helper);
			c.vars.put("out", new DataValueFloat(0));
			DirectHost target = host(helper, 1, 3, "lever_toggle");
			c.taggedLinks.put("lamp", target.getBlockPos());

			// lever_toggle(isOn) -> isOn * 10 -> + 5 -> out
			//                    -> NOT isOn -> broadcast lever_toggle (isOn), guarded by a Conditional
			NodeSystem sys = new NodeSystem(c);
			NodeInput in = new NodeInput(0, 0, "lever_toggle").setVars(ControlEvent.getRegisteredEvent("lever_toggle").vars);
			NodeMath times = new NodeMath(1, 0).setData(Operation.MULT);
			NodeMath plus = new NodeMath(2, 0).setData(Operation.ADD);
			NodeSetVar setOut = new NodeSetVar(3, 0, c).setData("out", false);
			NodeBoolean not = new NodeBoolean(1, 1).setData(BoolOperation.NOT);
			NodeConditional gate = new NodeConditional(2, 1);
			NodeEventBroadcast send = new NodeEventBroadcast(3, 1, List.of(ControlEvent.getRegisteredEvent("lever_toggle")));
			for(Node n : List.of(in, times, plus, setOut, not, gate, send))
				sys.addNode(n);
			wire(times, 0, in, 0);
			set(times, 1, 10);
			wire(plus, 0, times, 0);
			set(plus, 1, 5);
			wire(setOut, 0, plus, 0);
			wire(not, 0, in, 0);
			set(gate, 0, 1);
			wire(gate, 1, not, 0);
			set(gate, 2, 0);
			wire(send, 0, gate, 0);
			c.receiveNodeMap.put("lever_toggle", sys);

			c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", true));
			helper.assertTrue(same(c.vars.get("out").getNumber(), 15F), "isOn=1: 1*10+5 = 15, got " + c.vars.get("out").getNumber());
			helper.assertTrue(target.rec.received.size() == 1 && same(target.rec.received.get(0).event().vars.get("isOn").getNumber(), 0F), "and NOT 1 = 0 was broadcast to the link");

			// the whole graph survives a save
			NodeSystem back = reload(sys, c);
			c.receiveNodeMap.put("lever_toggle", back);
			c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 3));
			helper.assertTrue(same(c.vars.get("out").getNumber(), 35F), "after a save and reload isOn=3 gives 35, got " + c.vars.get("out").getNumber());
			helper.assertTrue(target.rec.received.size() == 2 && same(target.rec.received.get(1).event().vars.get("isOn").getNumber(), 0F), "and broadcasts again");

			c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 0));
			helper.assertTrue(same(c.vars.get("out").getNumber(), 5F), "isOn=0 gives 5");
			helper.assertTrue(target.rec.received.size() == 3 && same(target.rec.received.get(2).event().vars.get("isOn").getNumber(), 1F), "and NOT 0 = 1");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFunctionGatesItsBodyInAGraph(GameTestHelper helper) {
		withStock(() -> {
			TestControl c = control(helper);
			c.vars.put("n", new DataValueFloat(0));
			NodeSystem sys = new NodeSystem(c);
			NodeInput in = new NodeInput(0, 0, "lever_toggle").setVars(ControlEvent.getRegisteredEvent("lever_toggle").vars);
			NodeFunction fn = new NodeFunction(1, 0);
			sys.addNode(in);
			sys.addNode(fn);
			wire(fn, 0, in, 0);
			NodeSystem body = sys.subSystems.get(fn);
			NodeSetVar mark = new NodeSetVar(0, 0, c).setData("n", false);
			set(mark, 0, 7);
			body.addNode(mark);
			c.receiveNodeMap.put("lever_toggle", sys);

			c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", false));
			helper.assertTrue(same(c.vars.get("n").getNumber(), 0F), "disabled: the body did not run");
			c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", true));
			helper.assertTrue(same(c.vars.get("n").getNumber(), 7F), "enabled: the body ran");
		});
		helper.succeed();
	}
}
