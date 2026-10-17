package com.leafia.gametest;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.gametest.TickWindow;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownNode;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.ic10.IC10;
import com.leafia.contents.machines.controlpanel.ic10.IC10Examples;
import com.leafia.contents.machines.controlpanel.ic10.IC10Limits;
import com.leafia.contents.machines.controlpanel.ic10.IC10Program;
import com.leafia.contents.machines.controlpanel.ic10.IC10State;
import com.leafia.contents.machines.controlpanel.nodes.pack.NodeIC10;
import com.leafia.gametest.LeafiaControlPanelFixtures.DirectHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The IC10 interpreter, node and limits of the Custom Control Panel (CCP-IC10, D-3073): reference programs for the arithmetic, the
 * branches, the pack in and out, variables, linked blocks and the radio; the stack at {@code ic10MaxStack} (512) and the
 * registers at {@code ic10MaxRegisters} (64); the instruction ceiling that stops {@code j 0}; the program format and what a
 * hostile program, state or gzip does to the guard; the seven upstream bugs fixed on the owner's decision (D-3074); the switch; and the class-load
 * sweep.
 *
 * <p>Every test runs the node on the server thread by hand. The tick pool ({@link IC10Limits}) is a static, so a test that runs a
 * node calls {@link IC10Limits#newTick} before each evaluation (the real tick does it) and leaves it full; the one switch a test
 * flips ({@code controlPanelIC10}) is put back in a {@code finally} (D-952). Nothing leaves a block entity ticking
 * or a radio channel published.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelIC10Tests {

	private static final String EMPTY = "empty";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	// ------------------------------------------------------------------ helpers

	private static TestControl control(GameTestHelper helper) {
		return new TestControl(new ControlPanel(new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel(), "tick")));
	}

	private static void registered() {
		StockNodesRegister.register();
		AddonNodesRegister.register();
	}

	/** A program from plain lines: words separated by single spaces, an empty string for a blank line. */
	private static ArrayList<ArrayList<Object>> program(String... lines) {
		ArrayList<ArrayList<Object>> out = new ArrayList<>();
		for(String line : lines) {
			ArrayList<Object> tokens = new ArrayList<>();
			String t = line.strip();
			if(!t.isEmpty()) {
				String[] words = t.split("\\s+");
				for(int i = 0; i < words.length; i++) {
					if(i > 0)
						tokens.add(1);
					tokens.add(words[i]);
				}
			}
			out.add(tokens);
		}
		return out;
	}

	private static NodeIC10 node(TestControl c, String... lines) {
		NodeIC10 n = new NodeIC10(0, 0, c);
		n.instructions = program(lines);
		n.resetState();
		return n;
	}

	/** One evaluation in a fresh tick. */
	private static void run(NodeIC10 n) {
		IC10Limits.newTick();
		n.cacheValid = false;
		n.evaluate(0);
	}

	private static void run(NodeIC10 n, int times) {
		for(int i = 0; i < times; i++)
			run(n);
	}

	private static double num(NodeIC10 n, int register) {
		Object o = n.state.register.get(register);
		return o instanceof Number x ? x.doubleValue() : Double.NaN;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-9;
	}

	private static String out(NodeIC10 n, String key) {
		return n.state.output.getValueOf(key);
	}

	private static DirectHost host(GameTestHelper helper, int x, int z) {
		BlockPos rel = new BlockPos(x, 1, z);
		helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
		DirectHost h = (DirectHost) helper.getBlockEntity(rel);
		h.rec.in = new ArrayList<>();
		return h;
	}

	/** A single-node graph written and read back through the loaders, as a saved panel does it. */
	private static Node reload(Node n, Control c) {
		NodeSystem sys = new NodeSystem(c);
		sys.addNode(n);
		NodeSystem back = new NodeSystem(c);
		back.readFromNBT(sys.writeToNBT(new CompoundTag()));
		return back.nodes.get(0);
	}

	// ------------------------------------------------------------------ the table and the defaults

	@GameTest(template = EMPTY)
	public static void theLimitsAreLeafiasAndTheSwitchIsOn(GameTestHelper helper) {
		helper.assertTrue(LeafiaConfig.ic10MaxStack == 512 && LeafiaConfig.ic10MaxRegisters == 64, "512 stack entries and 64 registers, Leafia's values");
		helper.assertTrue(LeafiaFeatures.controlPanelIC10, "controlPanelIC10 defaults on: the node is additive content (D-1002, D-3073)");
		helper.assertTrue(IC10Limits.STEPS_PER_UPDATE == 128 && IC10Limits.TICK_BUDGET == 4096 && IC10Limits.MAX_LINES == 512, "the ceilings are the ones D-3073 records");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theInstructionTableIsLeafiasNinetyOne(GameTestHelper helper) {
		String names = "alias l ln s pulse rand move add sub mul div mod pow sqrt exp log max min floor ceil round sin asin cos acos tan atan atan2 concat substr "
				+ "seq seqz sne snez sgt sgtz sge sgez slt sltz sle slez beq beqz bne bnez bgt bgtz bge bgez blt bltz ble blez "
				+ "breq breqz brne brnez brgt brgtz brge brgez brlt brltz brle brlez push pop peek j jr jal yield sleep lr lnr sr snr "
				+ "llv lgv slv snlv sgv sngv print query queryadd queryavg querymul queryh queryl";
		List<String> want = List.of(names.split(" "));
		helper.assertTrue(want.size() == 91 && IC10.instructions.size() == 91, "91 instructions, got " + IC10.instructions.size());
		for(String n : want)
			helper.assertTrue(IC10.instructions.containsKey(n), "instruction " + n + " exists");
		helper.assertTrue(IC10.instructions.get("add").args.size() == 3 && IC10.instructions.get("add").args.get(0).name.equals("register")
				&& IC10.instructions.get("add").args.get(1).type == IC10.IC10Type.NUMBER, "add takes register, a, b");
		helper.assertTrue(IC10.category.get(IC10.instructions.get("sin")).equals("Math/Trigonometry") && IC10.category.get(IC10.instructions.get("beq")).equals("Conditionals/Branch"),
				"and keeps Leafia's categories, which the Functions page lists");
		helper.succeed();
	}

	// ------------------------------------------------------------------ reference programs

	@GameTest(template = EMPTY)
	public static void arithmeticMatchesTheReference(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = node(c,
				"move r0 7", "add r1 r0 5", "sub r2 r1 2", "mul r3 r2 3", "div r4 r3 4", "mod r5 r0 4", "pow r6 r0 2", "sqrt r7 r6",
				"max r8 r0 r1", "min r9 r0 r1", "floor r10 3.7", "round r11 3.5", "mod r12 -1 4", "sub r13 0 r0", "abs_is_not_an_instruction");
		run(n);
		helper.assertTrue(near(num(n, 0), 7) && near(num(n, 1), 12) && near(num(n, 2), 10) && near(num(n, 3), 30), "add, sub and mul chain: " + n.state.register);
		helper.assertTrue(near(num(n, 4), 7.5) && near(num(n, 5), 3) && near(num(n, 6), 49) && near(num(n, 7), 7), "div, mod, pow and sqrt");
		helper.assertTrue(near(num(n, 8), 12) && near(num(n, 9), 7) && near(num(n, 10), 3) && near(num(n, 11), 4), "max, min, floor and round");
		helper.assertTrue(near(num(n, 12), 3), "mod is a positive modulo: -1 mod 4 is 3");
		helper.assertTrue(near(num(n, 13), -7), "and a register can go negative");
		helper.assertTrue("UnrecognizedInstruction".equals(n.state.error), "an unknown instruction stops the program with its name: " + n.state.error);
		helper.assertTrue(n.state.line == 15, "at the line after it, got " + n.state.line);

		NodeIC10 trig = node(c, "move r0 0", "sin r1 r0", "cos r2 r0", "atan2 r3 1 1", "exp r4 0", "log r5 1", "yield");
		run(trig);
		helper.assertTrue(near(num(trig, 1), 0) && near(num(trig, 2), 1) && near(num(trig, 3), Math.PI / 4) && near(num(trig, 4), 1) && near(num(trig, 5), 0), "trigonometry, exp and log");
		helper.assertTrue(trig.state.error == null, "and the program ended without an error");

		NodeIC10 cmp = node(c, "move r0 5", "seq r1 r0 5", "sne r2 r0 5", "sgt r3 r0 4", "slt r4 r0 4", "sge r5 r0 5", "sle r6 r0 4", "seqz r7 0", "snez r8 r0", "sgtz r9 r0", "yield");
		run(cmp);
		double[] want = {1, 0, 1, 0, 1, 0, 1, 1, 1};
		for(int i = 0; i < want.length; i++)
			helper.assertTrue(near(num(cmp, i + 1), want[i]), "comparison r" + (i + 1) + " is " + want[i] + ", got " + num(cmp, i + 1));

		NodeIC10 str = node(c, "concat r0 \"ab\" 12", "substr r1 \"hello\" 2 4", "yield");
		run(str);
		helper.assertTrue("ab12.0".equals(String.valueOf(str.state.register.get(0))), "concat joins as text, a number as its double, got " + str.state.register.get(0));
		helper.assertTrue("ell".equals(String.valueOf(str.state.register.get(1))), "substr is one-based and inclusive of the end, got " + str.state.register.get(1));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void branchesAndLoopsRunLikeTheReference(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 sum = node(c, "move r0 0", "move r1 1", "Loop:", "add r0 r0 r1", "add r1 r1 1", "ble r1 10 Loop", "yield");
		run(sum);
		helper.assertTrue(near(num(sum, 0), 55) && near(num(sum, 1), 11), "the sum of 1 to 10 is 55, got " + num(sum, 0));
		helper.assertTrue(sum.state.error == null, "and the program waits at its yield, not in error");

		NodeIC10 branch = node(c,
				"move r0 3",
				"beq r0 3 Eq", "move r1 99", "Eq:", "move r2 1",
				"bne r0 3 Ne", "move r3 1", "Ne:",
				"bgt r0 5 Gt", "move r4 1", "Gt:",
				"beqz r0 Z", "move r5 1", "Z:",
				"bnez r0 NZ", "move r6 99", "NZ:",
				"breq r0 3 1", "move r7 99",
				"jr 1", "move r8 99",
				"yield");
		run(branch);
		helper.assertTrue(branch.state.register.get(1) == null, "beq jumped over the line it should");
		helper.assertTrue(near(num(branch, 2), 1) && near(num(branch, 3), 1) && near(num(branch, 4), 1) && near(num(branch, 5), 1), "bne, bgt and beqz fell through");
		helper.assertTrue(branch.state.register.get(6) == null && branch.state.register.get(7) == null && branch.state.register.get(8) == null, "bnez, breq and jr skipped their lines");
		helper.assertTrue(branch.state.error == null, "no error: " + branch.state.error);

		// jal stores the line after itself in ra (upstream stored line + 2; fixed on the owner's decision, D-3074)
		NodeIC10 jal = node(c, "jal Fn", "yield", "move r5 1", "yield", "Fn:", "move r0 4", "yield");
		run(jal);
		helper.assertTrue(near(num(jal, 0), 4), "jal reached the function");
		helper.assertTrue(near(jal.state.register.get(IC10State.register_ra) instanceof Number x ? x.doubleValue() : Double.NaN, 1), "ra is the line after the jal: " + jal.state.register.get(IC10State.register_ra));
		NodeIC10 ret = node(c, "jal Fn", "move r5 1", "yield", "Fn:", "move r0 4", "j ra");
		run(ret);
		helper.assertTrue(near(num(ret, 0), 4) && near(num(ret, 5), 1), "j ra returns to the line after the jal, skipping nothing");

		// jumps out of the program end it with an error, they do not throw
		for(String bad : new String[]{"j -5", "j 9999", "jr -100", "jr 100"}) {
			NodeIC10 n = node(c, bad, "yield");
			run(n);
			helper.assertTrue("OutOfInstructions".equals(n.state.error), bad + " ends the program with OutOfInstructions, got " + n.state.error);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aDeviceIsReadAndWrittenThroughThePacks(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = node(c, "l r0 \"k\"", "ln r1 \"n\"", "ln r2 \"word\"", "add r3 r1 1", "s \"out\" r3", "s \"greeting\" r0",
				"pulse \"p\" \"1\" \"0\"", "yield");
		DataValueComposite in = new DataValueComposite();
		in.setValueOf("k", "hello");
		in.setValueOf("n", "42");
		in.setValueOf("word", "abc");
		n.inputs.get(0).setDefault(in);
		run(n);
		helper.assertTrue("hello".equals(n.state.register.get(0)), "l reads a key as text");
		helper.assertTrue(near(num(n, 1), 42) && near(num(n, 2), -1), "ln reads a number, and -1 for text");
		helper.assertTrue(out(n, "out").equals("43.0") && out(n, "greeting").equals("hello"), "s writes the output pack, got " + out(n, "out"));
		helper.assertTrue(out(n, "p").equals("1"), "a pulse is its first value for one evaluation");
		run(n);
		helper.assertTrue(out(n, "p").equals("0"), "then its second");
		helper.assertTrue(n.evaluate(0) == n.state.output && n.evaluate(1).toString().isEmpty(), "outputs are the pack, and an empty error");

		// the Line output is the line the program stopped at
		NodeIC10 line = node(c, "move r0 1", "bogus");
		run(line);
		helper.assertTrue(line.evaluate(1).toString().equals("UnrecognizedInstruction") && line.evaluate(2).getNumber() == 1, "Error and Line say what stopped it and where: " + line.evaluate(1) + " " + line.evaluate(2).getNumber());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void variablesAreReadAndWritten(GameTestHelper helper) {
		TestControl c = control(helper);
		c.panel.globalVars.put("g", new DataValueFloat(9));
		NodeIC10 n = node(c, "snlv \"v\" 5", "slv \"s\" \"txt\"", "sngv \"gn\" 7", "sgv \"gs\" \"gt\"", "llv r0 \"v\"", "llv r1 \"s\"", "lgv r2 \"g\"", "lgv r3 \"gn\"", "yield");
		run(n);
		helper.assertTrue(c.vars.get("v").getNumber() == 5 && c.vars.get("s").toString().equals("txt"), "local variables written");
		helper.assertTrue(c.panel.globalVars.get("gn").getNumber() == 7 && c.panel.globalVars.get("gs").toString().equals("gt"), "global variables written");
		helper.assertTrue(near(num(n, 0), 5) && "txt".equals(n.state.register.get(1)) && near(num(n, 2), 9) && near(num(n, 3), 7), "and read back into registers");

		// a program cannot grow the tables without bound or make a name past the ceiling
		NodeIC10 flood = node(c, "move r1 0", "Loop:", "add r1 r1 1", "concat r0 \"v\" r1", "slv r0 \"1\"", "j Loop");
		c.vars.clear();
		run(flood, 40);
		helper.assertTrue(c.vars.size() == IC10Limits.MAX_VARS, "variables stop at " + IC10Limits.MAX_VARS + ", got " + c.vars.size());
		NodeIC10 longName = node(c, "concat r0 \"" + "x".repeat(60) + "\" \"" + "y".repeat(20) + "\"", "slv r0 \"1\"", "yield");
		run(longName);
		helper.assertTrue(c.vars.keySet().stream().noneMatch(k -> k.length() > IC10Limits.MAX_VAR_NAME), "and a name past " + IC10Limits.MAX_VAR_NAME + " is not made");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void linkedBlocksAreQueriedThroughTheNode(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		DirectHost a = host(helper, 2, 2);
		DirectHost b = host(helper, 3, 2);
		a.rec.query.put("heat", new DataValueFloat(10));
		b.rec.query.put("heat", new DataValueFloat(20));
		a.rec.query.put("name", new DataValueString("core"));
		c.taggedLinks.put("a", a.getBlockPos());
		c.taggedLinks.put("b", b.getBlockPos());
		NodeIC10 n = node(c, "query r0 \"a\" \"heat\"", "query r1 \"b\" \"heat\"", "query r2 \"a\" \"name\"", "query r3 \"a\" \"nothing\"", "query r4 \"zzz\" \"heat\"",
				"queryadd r5 \"heat\"", "queryh r6 \"heat\"", "queryl r7 \"heat\"", "queryavg r8 \"heat\"", "querymul r9 \"heat\"", "queryadd r10 \"name\"", "yield");
		run(n);
		helper.assertTrue(near(num(n, 0), 10) && near(num(n, 1), 20), "query reads one linked block's number");
		helper.assertTrue("core".equals(n.state.register.get(2)), "and its text");
		helper.assertTrue("< ERROR >".equals(n.state.register.get(3)) && "< ERROR >".equals(n.state.register.get(4)), "a missing key and an unknown link read < ERROR >");
		helper.assertTrue(near(num(n, 5), 30) && near(num(n, 6), 20) && near(num(n, 7), 10), "queryadd, queryh and queryl combine every linked block");
		helper.assertTrue(near(num(n, 8), 15), "queryavg divides by the number of blocks, got " + num(n, 8));
		helper.assertTrue(near(num(n, 9), 200), "querymul multiplies from one, got " + num(n, 9));
		helper.assertTrue("< ERROR >".equals(n.state.register.get(10)), "a bulk query over a text value reads < ERROR >");

		// an unloaded link is refused: no chunk is loaded for it
		c.taggedLinks.put("far", new BlockPos(2_000_000, 64, 2_000_000));
		NodeIC10 far = node(c, "query r0 \"far\" \"heat\"", "yield");
		run(far);
		helper.assertTrue("< ERROR >".equals(far.state.register.get(0)), "an unloaded position reads < ERROR > and loads nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void theRadioInstructionsGoThroughTheBus(GameTestHelper helper) {
		TestControl c = control(helper);
		String long129 = "f".repeat(IC10Limits.MAX_FREQUENCY + 1);
		NodeIC10 tx = node(c, "sr \"ccp13.text\" \"hello\"", "sr \"ccp13.num\" \"7\"", "snr \"ccp13.int\" 7", "sr \"" + long129 + "\" \"x\"", "sr \"ccp13.txt\" \"x\"", "yield");
		NodeIC10 rx = node(c, "lr r0 \"ccp13.text\"", "lnr r1 \"ccp13.num\"", "lr r2 \"ccp13.none\"", "lnr r3 \"ccp13.none\"", "lnr r5 \"ccp13.int\"", "lr r4 \"" + long129 + "\"", "yield");
		TickWindow.sequence(helper).thenExecute(() -> run(tx)).thenIdle(3).thenExecute(() -> {
			try {
				run(rx);
				helper.assertTrue("hello".equals(String.valueOf(rx.state.register.get(0))), "lr reads what sr sent: " + rx.state.register.get(0));
				helper.assertTrue(near(num(rx, 1), 7), "lnr reads a number: " + rx.state.register.get(1));
				helper.assertTrue("".equals(rx.state.register.get(2)) && near(num(rx, 3), 0), "a quiet channel reads empty, and 0 as a number");
				helper.assertTrue("".equals(rx.state.register.get(4)), "a frequency past the ceiling reads nothing");
				helper.assertTrue(near(num(rx, 5), 7), "lnr reads a number sent by snr");
				helper.assertTrue(RTTYSystem.listen(helper.getLevel(), long129) == null, "and was never sent");
			} finally {
				for(String ch : List.of("ccp13.text", "ccp13.num", "ccp13.int", "ccp13.txt")) {
					RTTYSystem.broadcast.keySet().removeIf(k -> k.getValue().equals(ch));
					RTTYSystem.newMessages.keySet().removeIf(k -> k.getValue().equals(ch));
				}
				IC10Limits.newTick();
			}
		}).thenSucceed();
	}

	@GameTest(template = EMPTY)
	public static void leafiasExamplesAllRunWithoutAnError(GameTestHelper helper) {
		TestControl c = control(helper);
		helper.assertTrue(IC10Examples.examples.size() == 4, "four examples");
		for(var e : IC10Examples.examples.entrySet()) {
			NodeIC10 n = new NodeIC10(0, 0, c);
			n.instructions = IC10Examples.copyOf(e.getValue());
			n.resetState();
			run(n, 400);
			helper.assertTrue(n.state.error == null, "example '" + e.getKey() + "' ran 400 evaluations without an error, got " + n.state.error);
			helper.assertTrue(IC10Program.lengthProblem(IC10Program.encode(n.instructions)) == null, "and fits the program limits");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDefaultProgramIsAThreeSecondClock(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = new NodeIC10(0, 0, c);
		helper.assertTrue(n.instructions.size() == 21 && n.instructions.equals(IC10Examples.examples.get("3 Second Clock")), "the default is the first example, 21 lines");
		int seen = -1;
		int evals = 0;
		for(; evals < 200 && seen < 0; evals++) {
			run(n);
			if("1".equals(out(n, "signal")))
				seen = evals;
		}
		helper.assertTrue(seen >= 40 && seen <= 100, "the signal goes high on the fourth pass of the loop (about 20 evaluations a pass), at " + seen);
		for(int i = 0; i < 60; i++)
			run(n);
		helper.assertTrue(n.state.error == null, "and it keeps running");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the limits

	@GameTest(template = EMPTY)
	public static void theStackHoldsFiveHundredAndTwelve(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = node(c, "Loop:", "push 7", "j Loop");
		for(int i = 0; i < 40 && n.state.error == null; i++)
			run(n);
		helper.assertTrue("StackOverflow".equals(n.state.error), "the 513th push overflows, got " + n.state.error);
		helper.assertTrue(near(num(n, IC10State.register_sp), 512) && n.state.stack[511] != null, "with sp at 512 and the last slot filled");
		helper.assertTrue(n.state.stack.length == 512, "the stack is 512 deep");

		NodeIC10 lifo = node(c, "push 5", "push \"abc\"", "push r0", "pop r1", "pop r2", "pop r3", "yield");
		lifo.state.register.put(0, 9d);
		run(lifo);
		helper.assertTrue(near(num(lifo, 1), 9) && "abc".equals(lifo.state.register.get(2)) && near(num(lifo, 3), 5), "pop returns last in, first out");
		helper.assertTrue(near(num(lifo, IC10State.register_sp), 0), "and sp is back to 0");

		NodeIC10 under = node(c, "pop r0", "yield");
		run(under);
		helper.assertTrue("StackUnderflow".equals(under.state.error), "a pop from an empty stack underflows, got " + under.state.error);

		NodeIC10 peek = node(c, "push 5", "peek r0", "yield");
		run(peek);
		helper.assertTrue(near(num(peek, 0), 5) && near(num(peek, IC10State.register_sp), 1), "peek reads the top of the stack and leaves sp");
		NodeIC10 peekEmpty = node(c, "peek r0", "yield");
		run(peekEmpty);
		helper.assertTrue("StackUnderflow".equals(peekEmpty.state.error), "peek on an empty stack underflows, got " + peekEmpty.state.error);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void registersStopAtSixtyFour(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 ok = node(c, "move r0 1", "move r64 2", "yield");
		run(ok);
		helper.assertTrue(near(num(ok, 64), 2) && ok.state.error == null, "r64 is the last register");
		for(String bad : new String[]{"move r65 1", "move r-1 1", "move rx 1", "move r999999999999 1"}) {
			NodeIC10 n = node(c, bad, "yield");
			run(n);
			helper.assertTrue(n.state.error != null && n.state.error.startsWith("OutOfRegisterBounds") || "IncorrectVariable".equals(n.state.error), bad + " is refused, got " + n.state.error);
			helper.assertTrue(n.state.register.size() == 0, "and writes nothing, got " + n.state.register);
		}
		IC10State s = new NodeIC10(0, 0, c).state;
		s.setRegister(66, 1);
		helper.assertTrue("OutOfRegisterBounds".equals(s.error), "setRegister past the bound is an error");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCeilingStopsAJumpToZeroWithinOneTick(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = node(c, "j 0");
		IC10Limits.newTick();
		long t0 = System.nanoTime();
		n.cacheValid = false;
		n.evaluate(0);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		helper.assertTrue(n.state.lastSteps == IC10Limits.STEPS_PER_UPDATE, "one evaluation runs 128 steps and stops, ran " + n.state.lastSteps);
		helper.assertTrue(n.state.error == null && n.state.line == 0, "the loop is still intact");
		helper.assertTrue(ms < 200, "and returned at once (" + ms + " ms)");

		// the same node asked again and again in one tick draws on the shared pool of 4096
		int total = n.state.lastSteps;
		for(int i = 0; i < 1000; i++) {
			n.cacheValid = false;
			n.evaluate(0);
			total += n.state.lastSteps;
		}
		helper.assertTrue(total == IC10Limits.TICK_BUDGET, "a thousand reads in one tick run " + IC10Limits.TICK_BUDGET + " steps in all, ran " + total);
		helper.assertTrue(n.state.throttled && n.state.lastSteps == 0, "after that the node does nothing and says it was throttled");
		helper.assertTrue(IC10Limits.remaining() == 0, "the pool is empty");
		run(n);
		helper.assertTrue(n.state.lastSteps == IC10Limits.STEPS_PER_UPDATE, "and the next tick gives it 128 again");

		// a hundred nodes together cost no more than the pool
		List<NodeIC10> nodes = new ArrayList<>();
		for(int i = 0; i < 100; i++)
			nodes.add(node(c, "j 0"));
		IC10Limits.newTick();
		int sum = 0;
		for(NodeIC10 x : nodes) {
			x.cacheValid = false;
			x.evaluate(0);
			sum += x.state.lastSteps;
		}
		helper.assertTrue(sum == IC10Limits.TICK_BUDGET, "a hundred looping nodes run 4096 steps between them, ran " + sum);
		IC10Limits.newTick();
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void whatAProgramBuildsIsBounded(GameTestHelper helper) {
		TestControl c = control(helper);
		// text that doubles
		NodeIC10 dbl = node(c, "move r0 \"ab\"", "Loop:", "concat r0 r0 r0", "j Loop");
		run(dbl, 5);
		Object r0 = dbl.state.register.get(0);
		helper.assertTrue(r0 instanceof String s && s.length() == IC10Limits.MAX_TEXT, "a doubling concat stops at " + IC10Limits.MAX_TEXT + " characters");

		// output keys
		NodeIC10 keys = node(c, "move r1 0", "Loop:", "add r1 r1 1", "concat r0 \"k\" r1", "s r0 \"1\"", "j Loop");
		run(keys, 40);
		helper.assertTrue(keys.state.output.values().size() == IC10Limits.MAX_OUTPUT_KEYS, "the output pack holds " + IC10Limits.MAX_OUTPUT_KEYS + " keys at most, held " + keys.state.output.values().size());

		// a long key
		NodeIC10 longKey = node(c, "move r0 \"" + "k".repeat(30) + "\"", "concat r0 r0 r0", "concat r0 r0 r0", "s r0 \"1\"", "yield");
		run(longKey);
		helper.assertTrue(longKey.state.output.values().isEmpty(), "and a key past " + IC10Limits.MAX_KEY + " characters is not written");

		// print with nobody near sends nothing and does not throw
		NodeIC10 print = node(c, "print \"a\" 1", "print \"b\" r0", "yield");
		run(print);
		helper.assertTrue(print.state.error == null, "print with nobody in range is harmless");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void errorsStopTheProgramAndAreReported(GameTestHelper helper) {
		TestControl c = control(helper);
		String[][] table = {
				{"IncorrectArgumentCount", "add r0 1"},
				{"IncorrectArgumentCount", "add r0 1 2 3"},
				{"IncorrectVariable", "add r0 \"x\" 1"},
				{"IncorrectVariable", "j 1.5"},
				{"UnrecognizedInstruction", "nope r0"},
				{"CompileError", "define x abc"},
				{"CompileError", "alias y zz"},
		};
		for(String[] row : table) {
			NodeIC10 n = node(c, row[1], "yield");
			run(n);
			helper.assertTrue(row[0].equals(n.state.error), "'" + row[1] + "' is " + row[0] + ", got " + n.state.error);
			int steps = n.state.lastSteps;
			run(n);
			helper.assertTrue(n.state.lastSteps == 0 && steps <= 1, "and an errored node does nothing afterwards");
		}
		NodeIC10 brgtz = node(c, "move r0 1", "brgtz r0 1", "move r1 9", "move r2 3", "yield");
		run(brgtz);
		helper.assertTrue(brgtz.state.error == null && brgtz.state.register.get(1) == null && near(num(brgtz, 2), 3), "brgtz skips b lines when a > 0: " + brgtz.state.error);
		NodeIC10 ceil = node(c, "ceil r0 3.2", "yield");
		run(ceil);
		helper.assertTrue(near(num(ceil, 0), 4), "ceil rounds up, got " + num(ceil, 0));

		NodeIC10 unset = node(c, "add r0 r1 r2", "yield");
		run(unset);
		helper.assertTrue(near(num(unset, 0), 0) && unset.state.error == null, "an unset register reads as 0");
		NodeIC10 defs = node(c, "alias x r3", "define k 10", "add x k 1", "yield");
		run(defs);
		helper.assertTrue(near(num(defs, 3), 11), "aliases and defines resolve, got " + num(defs, 3));
		helper.succeed();
	}

	// ------------------------------------------------------------------ saving

	@GameTest(template = EMPTY)
	public static void aProgramAndItsStateSurviveASaveAndLoad(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			registered();
			TestControl c = control(helper);
			NodeIC10 n = node(c, "push 5", "push \"abc\"", "move r3 12", "move r4 \"txt\"", "pulse \"p\" \"1\" \"0\"", "s \"o\" 4", "sleep 50", "yield");
			run(n);
			NodeIC10 fresh = new NodeIC10(0, 0, c);
			helper.assertTrue(fresh.writeToNBT(new CompoundTag(), new NodeSystem(c)).getString("nodeType").equals("leafia_ic10"), "the saved type is Leafia's leafia_ic10");

			Node back = reload(n, c);
			helper.assertTrue(back instanceof NodeIC10, "the loader rebuilds it");
			NodeIC10 b = (NodeIC10) back;
			helper.assertTrue(IC10Program.text(b.instructions).equals(IC10Program.text(n.instructions)), "the program is the same text");
			helper.assertTrue(b.instructions.equals(n.instructions), "and the same tokens");
			helper.assertTrue(b.state.line == n.state.line && b.state.yield == n.state.yield, "the line and the sleep are kept");
			helper.assertTrue(near(num(b, 3), 12) && "txt".equals(b.state.register.get(4)), "registers are kept");
			helper.assertTrue(near(num(b, IC10State.register_sp), 2), "so is sp");
			helper.assertTrue(near(num(b, 0), Double.NaN) || b.state.register.get(0) == null, "and the stack was NOT written over r0 and r1 (Leafia wrote it into the registers)");
			helper.assertTrue(near(((Number) b.state.stack[0]).doubleValue(), 5) && "abc".equals(b.state.stack[1]), "the stack is kept, got " + java.util.Arrays.toString(java.util.Arrays.copyOf(b.state.stack, 3)));
			helper.assertTrue(b.state.pulseQueue.equals(n.state.pulseQueue) && b.state.pulseQueue.get("p").equals("0"), "the queued pulse is kept");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theProgramFormatKeepsWhatItCannotCorrupt(GameTestHelper helper) {
		// more than 126 spaces, a zero-space token, a non-ASCII character
		ArrayList<ArrayList<Object>> p = new ArrayList<>();
		ArrayList<Object> wide = new ArrayList<>();
		wide.add("a");
		wide.add(200);
		wide.add("b");
		wide.add(0);
		wide.add("c");
		wide.add("é中");
		p.add(wide);
		p.add(new ArrayList<>());
		ArrayList<Object> two = new ArrayList<>();
		two.add("x");
		two.add("y");
		p.add(two);
		ArrayList<ArrayList<Object>> back = IC10Program.decode(IC10Program.encode(p));
		helper.assertTrue(back.size() == 3, "three lines come back");
		helper.assertTrue(IC10Program.text(back).split("\n", -1)[0].equals("a" + " ".repeat(200) + "bc??"), "200 spaces survive, a zero run is dropped and non-ASCII is a question mark, got " + IC10Program.text(back).split("\n", -1)[0].length());
		helper.assertTrue(back.get(1).isEmpty() && back.get(2).equals(List.of("x", "y")), "a blank line and two touching words come back as they were");

		CompoundTag tag = new CompoundTag();
		IC10Program.write(tag, p);
		helper.assertTrue(tag.contains("compressed") && IC10Program.problem(tag) == null, "it is written gzipped");
		CompoundTag raw = new CompoundTag();
		raw.putByteArray("instructions", IC10Program.encode(p));
		helper.assertTrue(IC10Program.decode(IC10Program.bytes(raw)).size() == 3, "and the uncompressed form is read too");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void withTheSwitchOffTheNodeIsInertAndKept(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			registered();
			TestControl c = control(helper);
			NodeIC10 n = node(c, "move r0 5", "yield");
			NodeSystem sys = new NodeSystem(c);
			sys.addNode(n);
			CompoundTag saved = sys.writeToNBT(new CompoundTag());
			boolean was = LeafiaFeatures.controlPanelIC10;
			try {
				LeafiaFeatures.controlPanelIC10 = false;
				run(n);
				helper.assertTrue(n.state.register.isEmpty() && n.state.lastSteps == 0, "with IC10 off a node runs nothing");
				NodeSystem back = new NodeSystem(c);
				back.readFromNBT(saved);
				helper.assertTrue(back.nodes.size() == 1 && back.nodes.get(0) instanceof UnknownNode, "and a saved one loads as an inert placeholder");
				helper.assertTrue(back.writeToNBT(new CompoundTag()).toString().equals(saved.toString()), "that keeps its tag, byte for byte");
				var ctx = new com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext(new NodeSystem(c),
						com.leafia.contents.machines.controlpanel.core.ControlEvent.getRegisteredEvent("tick"), List.of());
				helper.assertTrue(menu(ctx).isEmpty(), "and the menu does not offer it");

				LeafiaFeatures.controlPanelIC10 = true;
				back = new NodeSystem(c);
				back.readFromNBT(saved);
				helper.assertTrue(back.nodes.get(0) instanceof NodeIC10, "switched back on it is a real node again");
				helper.assertTrue(menu(ctx).equals(List.of("IC10 Sequence")), "and the Pack category offers it");
				helper.assertTrue(com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry.addMenuCategories.contains("Pack"), "in the stock Pack category");
			} finally {
				LeafiaFeatures.controlPanelIC10 = was;
			}
		});
		helper.succeed();
	}

	private static List<String> menu(com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext ctx) {
		com.leafia.contents.machines.controlpanel.core.ItemList list = new com.leafia.contents.machines.controlpanel.core.ItemList(0, 0, 32, s -> null);
		for(var creator : com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry.addMenuControl.get("Pack")) {
			if(creator.getClass().getSimpleName().equals("NCLeafiaPack"))
				creator.addItems(list, 0, 0, ctx);
		}
		return new ArrayList<>(list.itemNames);
	}

	// ------------------------------------------------------------------ the trust boundary

	private static byte[] gzip(byte[] data) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try(GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(data);
		}
		return bos.toByteArray();
	}

	/** Sends a panel with one IC10 node, made hostile by {@code corrupt}, through the real packet and says whether the panel was left alone. */
	private static boolean refused(GameTestHelper helper, ControlPanelTE te, ServerPlayer player, java.util.function.Consumer<NodeIC10> corrupt, java.util.function.Consumer<CompoundTag> tagEdit) {
		CompoundTag before = te.panel.writeToNBT(new CompoundTag());
		ControlPanel panelBefore = te.panel;
		CompoundTag tag = LeafiaControlPanelKit.edit(te, panel -> {
			TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
			NodeIC10 n = new NodeIC10(0, 0, c);
			corrupt.accept(n);
			sys.addNode(n);
		});
		tagEdit.accept(tag);
		boolean scanned = ControlPanelGuard.scan(tag) != null;
		NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), tag), LeafiaControlPanelKit.context(player));
		boolean untouched = te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before);
		helper.assertTrue(scanned == untouched, "the scan and the packet agree (scan refuses: " + scanned + ", panel untouched: " + untouched + ")");
		return untouched;
	}

	/** The IC10 node's tag inside a panel tag. */
	private static CompoundTag nodeTag(CompoundTag panel) {
		CompoundTag controls = panel.getCompound("controls");
		for(String ck : controls.getAllKeys()) {
			CompoundTag rn = controls.getCompound(ck).getCompound("RN");
			for(String ek : rn.getAllKeys()) {
				CompoundTag nodes = rn.getCompound(ek).getCompound("N");
				for(String nk : nodes.getAllKeys()) {
					if(nodes.getCompound(nk).getString("nodeType").equals("leafia_ic10"))
						return nodes.getCompound(nk);
				}
			}
		}
		throw new IllegalStateException("no IC10 node in the tag");
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aHostileProgramIsRefusedByTheGuard(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			registered();
			helper.assertTrue(ControlPanelGuard.NODE_SCANNERS.containsKey("leafia_ic10"), "the addon installs the guard's check");
			ControlPanelTE hostileTe = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer hostile = LeafiaControlPanelKit.editor(helper, hostileTe);

			// too many lines
			ArrayList<ArrayList<Object>> many = new ArrayList<>();
			for(int i = 0; i <= IC10Limits.MAX_LINES; i++)
				many.add(new ArrayList<>(List.of("yield")));
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> n.instructions = many, t -> {}), "513 lines are refused");

			// a line too wide
			ArrayList<ArrayList<Object>> wide = new ArrayList<>(List.of(new ArrayList<>(List.of("#", "x".repeat(IC10Limits.MAX_LINE_CHARS)))));
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> n.instructions = wide, t -> {}), "a line of 257 characters is refused");

			// a gzip that inflates to 10 MB
			byte[] bomb = gzip(new byte[10_000_000]);
			helper.assertTrue(bomb.length < IC10Limits.MAX_COMPRESSED, "the bomb is small on the wire (" + bomb.length + " bytes)");
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> nodeTag(t).putByteArray("compressed", bomb)), "a gzip that inflates past 64 KiB is refused, and was not inflated in full");

			// a compressed program past the wire ceiling (incompressible data)
			byte[] noise = new byte[IC10Limits.MAX_COMPRESSED + 1];
			new Random(13).nextBytes(noise);
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> nodeTag(t).putByteArray("compressed", noise)), "more than 16 KiB compressed is refused");

			// not a gzip at all
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> nodeTag(t).putByteArray("compressed", new byte[]{1, 2, 3})), "bytes that are not a gzip are refused");

			// a raw program past the decoded ceiling
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> {
				nodeTag(t).remove("compressed");
				nodeTag(t).putByteArray("instructions", new byte[IC10Limits.MAX_PROGRAM_BYTES + 1]);
			}), "an uncompressed program past 64 KiB is refused");

			// a state with too many registers
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> {
				CompoundTag regs = nodeTag(t).getCompound("state").getCompound("registry");
				for(int i = 0; i < IC10State.MAX_SAVED_REGISTERS + 1; i++)
					regs.putDouble(Integer.toString(i), i);
			}), "a saved state with 129 registers is refused");
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> {
				CompoundTag st = nodeTag(t).getCompound("state").getCompound("stack");
				for(int i = 0; i < IC10State.MAX_SAVED_STACK + 1; i++)
					st.putDouble(Integer.toString(i), i);
			}), "and 1025 stack entries");
			helper.assertTrue(refused(helper, hostileTe, hostile, n -> {}, t -> nodeTag(t).getCompound("state").getCompound("registry").putString("0", "z".repeat(IC10Limits.MAX_TEXT + 1))),
					"and a register holding more than 4096 characters");

			// an IC10 node in a function body is measured too
			helper.assertTrue(ControlPanelGuard.scan(wrapInFunction(nodeTagOf(many))) != null, "an oversize program hidden in a function body is refused");

			// a modest program is accepted, and reads back as a real node
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT.east(4));
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			ControlPanel before = te.panel;
			ArrayList<ArrayList<Object>> fine = program("move r0 1", "Loop:", "add r0 r0 1", "yield", "j Loop");
			CompoundTag ok = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeIC10 n = new NodeIC10(0, 0, c);
				n.instructions = fine;
				sys.addNode(n);
			});
			helper.assertTrue(ControlPanelGuard.scan(ok) == null, "a five-line program passes the scan");
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), ok), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel != before && te.panel.controls.size() == 1, "and is accepted");
			Node saved = te.panel.controls.get(0).receiveNodeMap.get("tick").nodes.get(0);
			helper.assertTrue(saved instanceof NodeIC10 n && n.instructions.equals(fine), "and read back as the same program, not a placeholder");

			// a program that was written past the limits straight into a node is cut on load
			NodeIC10 over = new NodeIC10(0, 0, control(helper));
			over.instructions = many;
			NodeSystem sys = new NodeSystem(over.ctrl);
			sys.addNode(over);
			CompoundTag over2 = sys.writeToNBT(new CompoundTag());
			helper.assertTrue(((NodeIC10) reload(over, over.ctrl)).instructions.size() == IC10Limits.MAX_LINES, "a node read from an oversize tag keeps 512 lines");
			helper.assertTrue(over2.size() > 0, "(written)");
		});
		helper.succeed();
	}

	private static CompoundTag nodeTagOf(ArrayList<ArrayList<Object>> program) {
		TestControl c = new TestControl(new ControlPanel(null));
		NodeIC10 n = new NodeIC10(0, 0, c);
		n.instructions = program;
		NodeSystem sys = new NodeSystem(c);
		sys.addNode(n);
		return sys.writeToNBT(new CompoundTag()).getCompound("N").getCompound("n0");
	}

	/** A panel tag holding {@code node} one function body down. */
	private static CompoundTag wrapInFunction(CompoundTag node) {
		CompoundTag inner = new CompoundTag();
		CompoundTag innerNodes = new CompoundTag();
		innerNodes.put("0", node);
		inner.put("N", innerNodes);
		CompoundTag fn = new CompoundTag();
		fn.putString("nodeType", "function");
		fn.put("SS", inner);
		CompoundTag outer = new CompoundTag();
		CompoundTag outerNodes = new CompoundTag();
		outerNodes.put("0", fn);
		outer.put("N", outerNodes);
		CompoundTag rn = new CompoundTag();
		rn.put("tick", outer);
		CompoundTag control = new CompoundTag();
		control.put("RN", rn);
		CompoundTag controls = new CompoundTag();
		controls.put("0", control);
		CompoundTag panel = new CompoundTag();
		panel.put("controls", controls);
		return panel;
	}

	@GameTest(template = EMPTY)
	public static void aHostileStateIsReadSafely(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeIC10 n = new NodeIC10(0, 0, c);
		CompoundTag st = new CompoundTag();
		st.putInt("line", -50);
		st.putInt("yield", Integer.MAX_VALUE);
		CompoundTag regs = new CompoundTag();
		regs.putString("notanumber", "x");
		regs.putDouble("999", 1);
		regs.putDouble("-9", 1);
		regs.putDouble("3", 2);
		st.put("registry", regs);
		CompoundTag stack = new CompoundTag();
		stack.putDouble("100000", 1);
		stack.putDouble("2", 4);
		st.put("stack", stack);
		IC10State s = new IC10State(n);
		s.deserialize(st);
		helper.assertTrue(s.line == 0 && s.yield == 1_000_000, "a negative line is zero and a sleep is bounded");
		helper.assertTrue(s.register.size() == 1 && s.register.get(3).equals(2d), "registers out of range or with no number are dropped, got " + s.register);
		helper.assertTrue(s.stack[2].equals(4d), "and stack slots past the stack are ignored");

		NodeIC10 neg = node(c, "move r0 1", "yield");
		neg.state.line = -7;
		run(neg);
		helper.assertTrue("OutOfInstructions".equals(neg.state.error), "a saved line before the program is an error, not an exception on the server thread");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the sweep

	@GameTest(template = EMPTY)
	public static void theInterpreterNamesNoClientClass(GameTestHelper helper) throws Exception {
		String base = "com/leafia/contents/machines/controlpanel";
		List<String> found = new ArrayList<>();
		URL url = LeafiaControlPanelIC10Tests.class.getClassLoader().getResource(base);
		if(url != null) {
			Path root = Paths.get(url.toURI());
			try(Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					if((rel.startsWith("ic10/") && !rel.startsWith("ic10/client/")) || rel.startsWith("nodes/pack/") || rel.startsWith("categories/NCLeafiaPack") || rel.startsWith("elements/NodeTextBox"))
						found.add(base + "/" + rel.substring(0, rel.length() - ".class".length()));
				});
			}
		}
		helper.assertTrue(found.size() >= 8, "the sweep found the IC10 classes (" + found.size() + ")");
		for(String name : found) {
			byte[] bytes;
			try(var in = LeafiaControlPanelIC10Tests.class.getClassLoader().getResourceAsStream(name + ".class")) {
				bytes = in.readAllBytes();
			}
			String s = new String(bytes, StandardCharsets.ISO_8859_1);
			helper.assertTrue(!(s.contains("net/minecraft/client") || s.contains("com/mojang/blaze3d") || s.contains("org/lwjgl") || s.contains(base + "/core/client") || s.contains(base + "/ic10/client")),
					name + " names a client class");
		}
		helper.succeed();
	}
}
