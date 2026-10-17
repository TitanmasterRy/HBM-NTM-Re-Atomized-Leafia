package com.leafia.gametest;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.machines.controlpanel.AddonNodeLoader;
import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.NodeTextBox;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.UnknownNode;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;
import com.leafia.contents.machines.controlpanel.nodes.NodeBulkQuery;
import com.leafia.contents.machines.controlpanel.nodes.NodeBulkQuery.CombineMethod;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder.NodeSnd;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodePulse;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodeSRLatch;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRReceiver;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRSender;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeAddString;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeFormat;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSIPfx;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSubString;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeCache;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeClock;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeParseFloat;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNG;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNGDecimal;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeSummarizer;
import com.leafia.gametest.LeafiaControlPanelFixtures.AdaptedHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.DirectHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.lib.LeafiaRef;

import com.hbm.gametest.TickWindow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Leafia's own nodes of the Custom Control Panel (CCP-LEAFIA-NODES, D-3070): every node's sockets and size, its {@code evaluate}
 * against a table written from Leafia's source, its NBT, the loader and menu that offer it, the radio nodes through the real
 * {@code RTTYSystem} bus, the sounder against the level's sound hook, the bulk query against live and unloaded blocks, and a
 * hostile graph of them through the real update packet.
 *
 * <p>Everything but the radio test is synchronous on the server thread. Tests that read nodes back from NBT use the loaders the
 * mod's setup registered (the stock one, then Leafia's), the same list the game runs; no test leaves a block entity ticking or a
 * channel published (D-952, D-963).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelLeafiaNodeTests {

	private static final String EMPTY = "empty";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	/** A position far outside anything a test loads. */
	private static final BlockPos FAR = new BlockPos(2_000_000, 64, 2_000_000);

	// ------------------------------------------------------------------ helpers

	private static TestControl control(GameTestHelper helper) {
		return new TestControl(new ControlPanel(new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel(), "tick")));
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

	private static String text(DataValue v) {
		return v == null ? "<null>" : v.toString();
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

	private static void wire(Node to, int input, Node from, int output) {
		to.inputs.get(input).setData(from, output, true);
	}

	private static void registered() {
		StockNodesRegister.register();
		AddonNodesRegister.register();
	}

	private static NodeEditContext ctx(Control c) {
		return new NodeEditContext(new NodeSystem(c), com.leafia.contents.machines.controlpanel.core.ControlEvent.getRegisteredEvent("tick"), List.of());
	}

	private static List<String> leafiaItems(NodeEditContext ctx, String category) {
		ItemList list = new ItemList(0, 0, 32, s -> null);
		for(INodeMenuCreator c : NTMControlPanelRegistry.addMenuControl.get(category)) {
			if(c.getClass().getSimpleName().startsWith("NCLeafia"))
				c.addItems(list, 0, 0, ctx);
		}
		return list.itemNames;
	}

	private static Node pick(NodeEditContext ctx, String category, String item) {
		for(INodeMenuCreator c : NTMControlPanelRegistry.addMenuControl.get(category)) {
			Node n = c.selectItem(item, 12, 34, ctx);
			if(n != null)
				return n;
		}
		return null;
	}

	private static DirectHost host(GameTestHelper helper, int x, int z) {
		BlockPos rel = new BlockPos(x, 1, z);
		helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
		DirectHost h = (DirectHost) helper.getBlockEntity(rel);
		h.rec.in = new ArrayList<>();
		return h;
	}

	/** The node written into a one-node graph and read back through the loaders, as a saved panel does it. */
	private static Node reload(Node n, Control c) {
		NodeSystem sys = new NodeSystem(c);
		sys.addNode(n);
		NodeSystem back = new NodeSystem(c);
		back.readFromNBT(sys.writeToNBT(new CompoundTag()));
		return back.nodes.get(0);
	}

	// ------------------------------------------------------------------ registration and menu

	@GameTest(template = EMPTY)
	public static void theLoaderFollowsTheStockOneAndTheMenuAddsToItsCategories(GameTestHelper helper) {
		registered();
		int loaders = NTMControlPanelRegistry.nbtNodeLoaders.size();
		int categories = NTMControlPanelRegistry.addMenuCategories.size();
		int input = NTMControlPanelRegistry.addMenuControl.get("Input").size();
		AddonNodesRegister.register();
		helper.assertTrue(NTMControlPanelRegistry.nbtNodeLoaders.size() == loaders && NTMControlPanelRegistry.addMenuCategories.size() == categories
				&& NTMControlPanelRegistry.addMenuControl.get("Input").size() == input, "a second register changes nothing");

		int stock = -1;
		int addon = -1;
		long addons = 0;
		for(int i = 0; i < NTMControlPanelRegistry.nbtNodeLoaders.size(); i++) {
			INodeLoader l = NTMControlPanelRegistry.nbtNodeLoaders.get(i);
			if(l instanceof StockNodeLoader && stock < 0)
				stock = i;
			if(l instanceof AddonNodeLoader) {
				addon = i;
				addons++;
			}
		}
		helper.assertTrue(addons == 1 && stock >= 0 && addon > stock, "one Leafia loader, after the stock one (" + stock + ", " + addon + ")");

		List<String> cats = NTMControlPanelRegistry.addMenuCategories;
		helper.assertTrue(cats.indexOf("Text") > cats.indexOf("Pack") && cats.indexOf("Utility") > cats.indexOf("Text"), "Text and Utility follow the stock six, in Leafia's order: " + cats);
		for(String c : List.of("Input", "Output", "Boolean")) {
			List<INodeMenuCreator> list = NTMControlPanelRegistry.addMenuControl.get(c);
			helper.assertTrue(list.get(0).getClass().getSimpleName().startsWith("NCStock") && list.get(list.size() - 1).getClass().getSimpleName().startsWith("NCLeafia"),
					c + ": the stock creator is first and Leafia's follows it");
		}

		NodeEditContext ctx = ctx(control(helper));
		helper.assertTrue(leafiaItems(ctx, "Input").equals(List.of("Bulk Query Block", "Receive RoR")), "input: " + leafiaItems(ctx, "Input"));
		helper.assertTrue(leafiaItems(ctx, "Output").equals(List.of("Play Sound", "Transmit RoR")), "output: " + leafiaItems(ctx, "Output"));
		helper.assertTrue(leafiaItems(ctx, "Boolean").equals(List.of("Pulse", "SR Latch")), "boolean: " + leafiaItems(ctx, "Boolean"));
		helper.assertTrue(leafiaItems(ctx, "Text").equals(List.of("Add String", "Substring", "SIPfx", "Format")), "text: " + leafiaItems(ctx, "Text"));
		helper.assertTrue(leafiaItems(ctx, "Utility").equals(List.of("Cache", "Clock", "RNG", "Decimal RNG", "Summarizer", "Text to Number")), "utility: " + leafiaItems(ctx, "Utility"));

		List<Class<?>> made = new ArrayList<>();
		for(String cat : List.of("Input", "Output", "Boolean", "Text", "Utility")) {
			for(String item : leafiaItems(ctx, cat)) {
				Node n = pick(ctx, cat, item);
				helper.assertTrue(n != null && n.posX == 12 && n.posY == 34, cat + "/" + item + " makes a node where it is asked for");
				made.add(n.getClass());
			}
		}
		List<Class<?>> all = List.of(NodeBulkQuery.class, NodeRoRReceiver.class, NodeSounder.class, NodeRoRSender.class, NodePulse.class, NodeSRLatch.class,
				NodeAddString.class, NodeSubString.class, NodeSIPfx.class, NodeFormat.class, NodeCache.class, NodeClock.class, NodeRNG.class, NodeRNGDecimal.class,
				NodeSummarizer.class, NodeParseFloat.class);
		helper.assertTrue(made.size() == 16 && made.containsAll(all), "the menu makes all sixteen of Leafia's nodes, got " + made.size());
		helper.assertTrue(pick(ctx, "Input", "Event Data") != null && pick(ctx, "Text", "Event Data") == null, "stock entries still answer, and a name that is not the category's makes nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void everyNodeHasUpstreamsSocketsSizeColourAndName(GameTestHelper helper) {
		registered();
		TestControl c = control(helper);
		record Row(Node n, String sockets, float size, float[] colour, String name) {}
		float[] input = StockNodesRegister.colorInput;
		float[] output = StockNodesRegister.colorOutput;
		float[] bool = StockNodesRegister.colorBoolean;
		float[] str = AddonNodesRegister.colorString;
		float[] util = AddonNodesRegister.colorUtility;
		List<Row> rows = List.of(
				new Row(new NodePulse(0, 0), "Input:NUMBER,>Pulse:NUMBER,", 26, bool, "Pulse"),
				new Row(new NodeSRLatch(0, 0), "Set:NUMBER,Reset:NUMBER,>Output:NUMBER,", 26, bool, "SR Latch"),
				new Row(new NodeAddString(0, 0), "Input 1:STRING,Input 2:STRING,>Output:STRING,", 26, str, "String Add"),
				new Row(new NodeSubString(0, 0), "Input:STRING,Start:NUMBER,End:NUMBER,>Output:STRING,", 34, str, "Substring"),
				new Row(new NodeSIPfx(0, 0), "Input:NUMBER,Show Full:NUMBER,>Shortened:STRING,", 26, str, "SIPfx"),
				new Row(new NodeFormat(0, 0), "Input 1:STRING,>Output:STRING,", 26, str, "Format"),
				new Row(new NodeCache(0, 0), "Input:NUMBER,>Output:NUMBER,", 18, util, "Cache"),
				new Row(new NodeClock(0, 0), "Uptime:NUMBER,Downtime:NUMBER,>Clock Signal:NUMBER,", 26, util, "Clock"),
				new Row(new NodeParseFloat(0, 0), "Input:STRING,>Output:NUMBER,Is Error:NUMBER,", 26, util, "Text to Number"),
				new Row(new NodeRNG(0, 0, c), "Min:NUMBER,Max:NUMBER,>Random:NUMBER,", 26, util, "RNG"),
				new Row(new NodeRNGDecimal(0, 0, c), "Min:NUMBER,Max:NUMBER,>Random:NUMBER,", 26, util, "Decimal RNG"),
				new Row(new NodeSummarizer(0, 0), "Enable:NUMBER,New Value:NUMBER,Clear:NUMBER,Trim Count:NUMBER,>Summary:NUMBER,", 50, util, "Summarizer"),
				new Row(new NodeBulkQuery(0, 0, c), ">Output:GENERIC,", 26, input, "Bulk Query"),
				new Row(new NodeSounder(0, 0), "Volume:NUMBER,Pitch:NUMBER,>", 26, output, "Play Sound"),
				new Row(new NodeRoRReceiver(0, 0, c), "Frequency:STRING,>Signal:STRING,", 18, input, "Receive RoR"),
				new Row(new NodeRoRSender(0, 0, c), "Frequency:STRING,Signal:STRING,>", 18, output, "Transmit RoR"));
		for(Row r : rows) {
			String k = r.n().getClass().getSimpleName();
			helper.assertTrue(sockets(r.n()).equals(r.sockets()), k + " sockets: " + sockets(r.n()));
			helper.assertTrue(same(r.n().size, r.size()), k + " size " + r.n().size);
			helper.assertTrue(r.n().getColor() == r.colour() && r.n().getDisplayName().equals(r.name()), k + " colour and name");
		}
		helper.assertTrue(java.util.Arrays.equals(str, new float[]{0.2F, 0.8F, 1}) && java.util.Arrays.equals(util, new float[]{1, 0.614F, 0.189F}), "the two colours are Leafia's");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void everyNodeKeepsItsTypeAndStateThroughASaveAndALoad(GameTestHelper helper) {
		registered();
		TestControl c = control(helper);
		NodePulse pulse = new NodePulse(1, 1);
		((NodeDropdown) pulse.otherElements.get(0)).choose("ON -> OFF");
		pulse.wasOn = true;
		NodeSRLatch latch = new NodeSRLatch(2, 2);
		latch.state = true;
		NodeFormat format = new NodeFormat(3, 3);
		format.format = "%s and %.2f";
		format.updateFormat();
		NodeClock clock = new NodeClock(4, 4);
		clock.internalCounter = 7;
		NodeSummarizer sum = new NodeSummarizer(5, 5);
		((NodeDropdown) sum.otherElements.get(0)).choose("HIGHEST");
		sum.internalList = new float[]{1, 2, 3};
		sum.needle = 2;
		NodeSounder sounder = new NodeSounder(6, 6);
		((NodeDropdown) sounder.otherElements.get(0)).choose("AZ_5");
		NodeRoRReceiver recv = new NodeRoRReceiver(7, 7, c);
		recv.lastValue = "41";
		NodeBulkQuery bulk = new NodeBulkQuery(8, 8, c);
		bulk.combineMethod = CombineMethod.LOWEST;
		bulk.dataName = "heat";
		List<Node> made = List.of(pulse, latch, format, clock, sum, sounder, recv, bulk, new NodeAddString(9, 9), new NodeSubString(10, 10), new NodeSIPfx(11, 11),
				new NodeCache(12, 12), new NodeParseFloat(13, 13), new NodeRNG(14, 14, c), new NodeRNGDecimal(15, 15, c), new NodeRoRSender(16, 16, c));
		NodeSystem sys = new NodeSystem(c);
		for(Node n : made)
			sys.addNode(n);
		CompoundTag first = sys.writeToNBT(new CompoundTag());
		NodeSystem back = new NodeSystem(c);
		back.readFromNBT(first);
		helper.assertTrue(back.nodes.size() == made.size(), "every node came back");
		for(int i = 0; i < made.size(); i++) {
			helper.assertTrue(!(back.nodes.get(i) instanceof UnknownNode) && back.nodes.get(i).getClass() == made.get(i).getClass(),
					"node " + i + " is a " + made.get(i).getClass().getSimpleName() + ", got " + back.nodes.get(i).getClass().getSimpleName());
		}
		helper.assertTrue(back.writeToNBT(new CompoundTag()).equals(first), "and writing it again gives the same tree");
		helper.assertTrue(((NodePulse) back.nodes.get(0)).mode == NodePulse.PulseMode.ON_OFF && ((NodePulse) back.nodes.get(0)).wasOn, "pulse: mode and memory");
		helper.assertTrue(((NodeSRLatch) back.nodes.get(1)).state, "latch: state");
		NodeFormat f = (NodeFormat) back.nodes.get(2);
		helper.assertTrue(f.format.equals("%s and %.2f") && sockets(f).equals("Input 1:STRING,Input 2:NUMBER,>Output:STRING,") && ((NodeTextBox) f.otherElements.get(0)).value.equals("%s and %.2f"),
				"format: the text, its two inputs and the box showing it: " + sockets(f));
		helper.assertTrue(((NodeClock) back.nodes.get(3)).internalCounter == 7, "clock: counter");
		NodeSummarizer s = (NodeSummarizer) back.nodes.get(4);
		helper.assertTrue(s.combineMethod == CombineMethod.HIGHEST && java.util.Arrays.equals(s.internalList, new float[]{1, 2, 3}) && s.needle == 2, "summarizer: method, window and fill");
		helper.assertTrue(((NodeSounder) back.nodes.get(5)).snd == NodeSnd.AZ_5, "sounder: the sound");
		helper.assertTrue(((NodeRoRReceiver) back.nodes.get(6)).lastValue.equals("41"), "receiver: the last reading");
		NodeBulkQuery b = (NodeBulkQuery) back.nodes.get(7);
		helper.assertTrue(b.combineMethod == CombineMethod.LOWEST && b.dataName.equals("heat"), "bulk query: method and key");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSavedNodeTypesAreUpstreamsExactly(GameTestHelper helper) {
		registered();
		TestControl c = control(helper);
		Map<String, Node> expected = new java.util.LinkedHashMap<>();
		expected.put("sounder", new NodeSounder(0, 0));
		expected.put("addString", new NodeAddString(0, 0));
		expected.put("subString", new NodeSubString(0, 0));
		expected.put("sipfx", new NodeSIPfx(0, 0));
		expected.put("format", new NodeFormat(0, 0));
		expected.put("bulkQuery", new NodeBulkQuery(0, 0, c));
		expected.put("leafia_clock", new NodeClock(0, 0));
		expected.put("leafia_pulse", new NodePulse(0, 0));
		expected.put("leafia_sr_latch", new NodeSRLatch(0, 0));
		expected.put("leafia_summarizer", new NodeSummarizer(0, 0));
		expected.put("leafia_cache", new NodeCache(0, 0));
		expected.put("leafia_parse_float", new NodeParseFloat(0, 0));
		expected.put("leafia_ror_receiver", new NodeRoRReceiver(0, 0, c));
		expected.put("leafia_ror_sender", new NodeRoRSender(0, 0, c));
		expected.put("leafia_rng", new NodeRNG(0, 0, c));
		expected.put("leafia_rng_d", new NodeRNGDecimal(0, 0, c));
		NodeSystem sys = new NodeSystem(c);
		for(Map.Entry<String, Node> e : expected.entrySet()) {
			String type = e.getValue().writeToNBT(new CompoundTag(), sys).getString("nodeType");
			helper.assertTrue(type.equals(e.getKey()), e.getValue().getClass().getSimpleName() + " saves as '" + type + "', upstream's is '" + e.getKey() + "'");
			CompoundTag tag = new CompoundTag();
			tag.putString("nodeType", e.getKey());
			Node made = new AddonNodeLoader().nodeFromNBT(tag, sys);
			helper.assertTrue(made != null && made.getClass() == e.getValue().getClass(), "the loader makes a " + e.getValue().getClass().getSimpleName() + " from '" + e.getKey() + "'");
		}
		CompoundTag other = new CompoundTag();
		other.putString("nodeType", "leafia_not_a_node");
		helper.assertTrue(new AddonNodeLoader().nodeFromNBT(other, sys) == null && new AddonNodeLoader().nodeFromNBT(new CompoundTag(), sys) == null,
				"a type this batch does not own (the IC10 node came with CCP-IC10, D-3073) is not recognised, so the graph keeps it as a placeholder");
		helper.succeed();
	}

	// ------------------------------------------------------------------ boolean

	@GameTest(template = EMPTY)
	public static void aPulseFiresOnTheEdgeItsModeNames(GameTestHelper helper) {
		float[] in = {0, 1, 1, 0, 1};
		Object[][] table = {
				{"OFF -> ON", new float[]{0, 1, 0, 0, 1}},
				{"ON -> OFF", new float[]{0, 0, 0, 1, 0}},
				{"ALWAYS", new float[]{0, 1, 0, 1, 1}},
		};
		for(Object[] row : table) {
			NodePulse p = new NodePulse(0, 0);
			((NodeDropdown) p.otherElements.get(0)).choose((String) row[0]);
			float[] want = (float[]) row[1];
			for(int i = 0; i < in.length; i++) {
				set(p, 0, in[i]);
				helper.assertTrue(same(eval(p).getNumber(), want[i]), row[0] + " step " + i + ": input " + in[i] + " gives " + eval(p).getNumber() + ", want " + want[i]);
			}
		}
		NodePulse p = new NodePulse(0, 0);
		helper.assertTrue(p.mode == NodePulse.PulseMode.OFF_ON && !p.wasOn && same(p.inputs.get(0).evaluate().getNumber(), 1), "it starts off, on rising edges, with its input high");
		helper.assertTrue(same(eval(p).getNumber(), 1), "so a fresh pulse fires on its first read");
		p.cacheValid = true;
		set(p, 0, 0);
		helper.assertTrue(same(p.evaluate(0).getNumber(), 1), "and the read is cached for the event: a second consumer sees the same pulse");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aLatchHoldsAndResetWins(GameTestHelper helper) {
		float[][] table = {{1, 0, 1}, {0, 0, 1}, {0, 1, 0}, {0, 0, 0}, {1, 1, 0}, {1, 0, 1}, {1, 1, 0}};
		NodeSRLatch l = new NodeSRLatch(0, 0);
		for(int i = 0; i < table.length; i++) {
			set(l, 0, table[i][0]);
			set(l, 1, table[i][1]);
			helper.assertTrue(same(l.evaluate(0).getNumber(), table[i][2]), "step " + i + " (set " + table[i][0] + ", reset " + table[i][1] + ") gives " + table[i][2]);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ text

	@GameTest(template = EMPTY)
	public static void addStringJoinsTheTextOfItsInputs(GameTestHelper helper) {
		NodeAddString n = new NodeAddString(0, 0);
		set(n, 0, "foo");
		set(n, 1, "bar");
		helper.assertTrue(eval(n).toString().equals("foobar"), "text and text");
		n.inputs.get(1).setDefault(new DataValueFloat(2.5F));
		helper.assertTrue(eval(n).toString().equals("foo2.5"), "a number joins as its text form, got " + eval(n));
		n.inputs.get(0).setDefault(new DataValueString(""));
		helper.assertTrue(eval(n).toString().equals("2.5"), "an empty side adds nothing");
		set(n, 0, "x".repeat(AddonNodeSupport.MAX_TEXT));
		set(n, 1, "y".repeat(10));
		helper.assertTrue(eval(n).toString().length() == AddonNodeSupport.MAX_TEXT, "the result is cut at the text ceiling");
		set(n, 0, "a");
		n.inputs.get(0).setData(new NodeCache(0, 0), 0, true);
		n.inputs.get(0).connection.inputs.clear();
		helper.assertTrue(eval(n) == null, "a source that gives nothing gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aDoublingChainOfAddStringsIsBoundedNotAMemoryBomb(GameTestHelper helper) {
		NodeAddString first = new NodeAddString(0, 0);
		set(first, 0, "abcdefgh");
		set(first, 1, "abcdefgh");
		Node prev = first;
		for(int i = 0; i < 100; i++) {
			NodeAddString next = new NodeAddString(0, 0);
			wire(next, 0, prev, 0);
			wire(next, 1, prev, 0);
			prev = next;
		}
		helper.assertTrue(eval(prev).toString().length() == AddonNodeSupport.MAX_TEXT, "a hundred links that each double their text give " + AddonNodeSupport.MAX_TEXT + ", not 2^100");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void subStringCountsFromOneAndFromTheEnd(GameTestHelper helper) {
		Object[][] table = {
				{1, -1, "hello"}, {2, 4, "ell"}, {-1, -1, "o"}, {-3, -1, "llo"}, {0, 3, "hel"}, {3, 3, "l"},
				{1, -6, ""}, {1, 6, "ERROR"}, {4, 2, "ERROR"}, {-9, -1, "ERROR"}, {1, 5, "hello"}, {6, -1, ""},
		};
		NodeSubString n = new NodeSubString(0, 0);
		set(n, 0, "hello");
		for(Object[] row : table) {
			set(n, 1, ((Integer) row[0]).floatValue());
			set(n, 2, ((Integer) row[1]).floatValue());
			String got = text(eval(n));
			helper.assertTrue(got.equals(row[2]), "(" + row[0] + ", " + row[1] + ") of 'hello' is '" + row[2] + "', got '" + got + "'");
		}
		set(n, 1, 2.9F);
		set(n, 2, 3.9F);
		helper.assertTrue(text(eval(n)).equals("el"), "the positions are truncated: 2.9 and 3.9 are 2 and 3");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void siPrefixShortensANumber(GameTestHelper helper) {
		Object[][] table = {
				{12500F, 0F, "12.5 k"}, {12500F, 1F, "12.5 Kilo"}, {999F, 0F, "999.0 "}, {2_500_000F, 0F, "2.5 M"},
				{2_500_000F, 1F, "2.5 Mega"}, {1000F, 0F, "1.0 k"}, {0F, 0F, "0.0 "}, {-5F, 0F, "-5.0 "},
		};
		NodeSIPfx n = new NodeSIPfx(0, 0);
		for(Object[] row : table) {
			set(n, 0, (Float) row[0]);
			set(n, 1, (Float) row[1]);
			String got = text(eval(n));
			helper.assertTrue(got.equals(row[2]), row[0] + " (full " + row[1] + ") is '" + row[2] + "', got '" + got + "'");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void formatBuildsItsInputsFromTheFormatAndFormats(GameTestHelper helper) {
		NodeFormat n = new NodeFormat(0, 0);
		helper.assertTrue(n.format.equals("%s") && n.inputs.size() == 1, "it starts as %s with one text input");
		set(n, 0, "x");
		helper.assertTrue(text(eval(n)).equals("x"), "and passes the text through");

		Object[][] table = {
				// format, expected sockets, inputs (String or Float), expected output
				{"%s=%.2f", "Input 1:STRING,Input 2:NUMBER,>Output:STRING,", new Object[]{"a", 3.14159F}, "a=3.14"},
				{"%5s|", "Input 1:STRING,>Output:STRING,", new Object[]{"ab"}, "   ab|"},
				{"%-5s|", "Input 1:STRING,>Output:STRING,", new Object[]{"ab"}, "ab   |"},
				{"100%%", ">Output:STRING,", new Object[]{}, "100%"},
				{"%e", "Input 1:NUMBER,>Output:STRING,", new Object[]{1500F}, "1.500000e+03"},
				{"%1$s-%1$s", "Input 1:STRING,Input 2:STRING,>Output:STRING,", new Object[]{"q"}, "q-q"},
				{"%d", "Input 1:NUMBER,>Output:STRING,", new Object[]{5F}, "ERROR"},
				{"%x", "Input 1:NUMBER,>Output:STRING,", new Object[]{255F}, "ERROR"},
				{"%q", ">Output:STRING,", new Object[]{}, "ERROR"},
				{"plain", ">Output:STRING,", new Object[]{}, "plain"},
				{"", ">Output:STRING,", new Object[]{}, ""},
				{"%", ">Output:STRING,", new Object[]{}, "ERROR"},
				{"%5", ">Output:STRING,", new Object[]{}, "ERROR"},
		};
		for(Object[] row : table) {
			n.format = (String) row[0];
			n.updateFormat();
			helper.assertTrue(sockets(n).equals(row[1]), "'" + row[0] + "' sockets: " + sockets(n));
			Object[] args = (Object[]) row[2];
			for(int i = 0; i < args.length; i++) {
				if(args[i] instanceof Float f)
					set(n, i, f);
				else
					set(n, i, (String) args[i]);
			}
			String got = text(eval(n));
			helper.assertTrue(got.equals(row[3]), "'" + row[0] + "' gives '" + row[3] + "', got '" + got + "'");
		}

		// committing the text box rebuilds the node, as typing in the editor does
		NodeTextBox box = (NodeTextBox) n.otherElements.get(0);
		box.startTyping();
		for(int i = 0; i < 8; i++)
			box.backspace();
		for(char ch : "%s %s".toCharArray())
			box.typeChar(ch);
		box.stopTyping();
		helper.assertTrue(n.format.equals("%s %s") && n.inputs.size() == 2 && sockets(n).equals("Input 1:STRING,Input 2:STRING,>Output:STRING,"), "typing a format rebuilds the inputs: " + sockets(n));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void formatRefusesWhatWouldBuildAHugeStringOrManyInputs(GameTestHelper helper) {
		NodeFormat n = new NodeFormat(0, 0);
		for(String bad : new String[]{"%2000000000s", "%.9999f", "%10000d", "%1000s", "ok %0100000x", "%-99999s"}) {
			n.format = bad;
			n.updateFormat();
			for(int i = 0; i < n.inputs.size(); i++)
				set(n, i, n.inputs.get(i).type == DataType.NUMBER ? "0" : "z");
			helper.assertTrue(text(eval(n)).equals("ERROR"), "'" + bad + "' is refused");
		}
		n.format = "%999s|";
		n.updateFormat();
		set(n, 0, "a");
		helper.assertTrue(text(eval(n)).length() == 1000, "a width of 999 is still allowed");
		n.format = "x".repeat(NodeFormat.MAX_FORMAT + 1);
		n.updateFormat();
		helper.assertTrue(text(eval(n)).equals("ERROR"), "a format past the length ceiling is refused");

		n.format = "%s".repeat(100);
		n.updateFormat();
		helper.assertTrue(n.inputs.size() == NodeFormat.MAX_ARGS, "a hundred conversions give the node " + NodeFormat.MAX_ARGS + " inputs, got " + n.inputs.size());
		helper.assertTrue(text(eval(n)).equals("ERROR"), "and the format that wants more than that reads ERROR rather than throwing");

		// a hand-made tag: a width bomb, and a node saved with no inputs at all
		TestControl c = control(helper);
		registered();
		NodeSystem sys = new NodeSystem(c);
		NodeFormat bomb = new NodeFormat(0, 0);
		sys.addNode(bomb);
		CompoundTag tag = sys.writeToNBT(new CompoundTag());
		tag.getCompound("N").getCompound("n0").putString("format", "%2000000000s");
		NodeSystem back = new NodeSystem(c);
		back.readFromNBT(tag);
		helper.assertTrue(text(eval(back.nodes.get(0))).equals("ERROR"), "a saved width bomb reads ERROR");
		helper.succeed();
	}

	// ------------------------------------------------------------------ utility

	@GameTest(template = EMPTY)
	public static void parseFloatReadsTextAndFlagsWhatIsNotANumber(GameTestHelper helper) {
		Object[][] table = {
				{"12.5", 12.5F, 0F}, {"abc", 0F, 1F}, {"", 0F, 1F}, {"1e3", 1000F, 0F}, {" 7 ", 7F, 0F}, {"-0.25", -0.25F, 0F},
				{"1,5", 0F, 1F}, {"NaN", Float.NaN, 0F}, {"3f", 3F, 0F},
		};
		NodeParseFloat n = new NodeParseFloat(0, 0);
		for(Object[] row : table) {
			set(n, 0, (String) row[0]);
			n.cacheValid = false;
			DataValue v = n.evaluate(0);
			DataValue e = n.evaluate(1);
			helper.assertTrue(same(v.getNumber(), (Float) row[1]) && same(e.getNumber(), (Float) row[2]), "'" + row[0] + "' gives " + v + ", error " + e);
		}
		helper.assertTrue(n.evaluate(2) == null && n.evaluate(-1) == null, "a socket it does not have gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCacheReadsItsInputOncePerEvent(GameTestHelper helper) {
		NodeCache n = new NodeCache(0, 0);
		set(n, 0, 5F);
		helper.assertTrue(same(eval(n).getNumber(), 5F), "it passes its input through");
		set(n, 0, 9F);
		helper.assertTrue(same(n.evaluate(0).getNumber(), 5F), "and keeps it until the event is over");
		n.cacheValid = false;
		helper.assertTrue(same(n.evaluate(0).getNumber(), 9F), "then reads again");
		n.inputs.clear();
		helper.assertTrue(n.evaluate(0) == null, "a node saved with no input gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aClockCountsOncePerTickAndKeepsItsPhase(GameTestHelper helper) {
		NodeClock n = new NodeClock(0, 0);
		set(n, 0, 2F);
		set(n, 1, 3F);
		float[] want = {1, 0, 0, 0, 1, 1, 0, 0, 0, 1};
		for(int tick = 0; tick < want.length; tick++) {
			AddonNodeSupport.newTick();
			float first = eval(n).getNumber();
			float second = eval(n).getNumber();
			helper.assertTrue(same(first, want[tick]) && same(second, first), "tick " + tick + ": up 2, down 3 gives " + want[tick] + ", got " + first + " then " + second);
		}
		helper.assertTrue(n.internalCounter == 0, "ten ticks is two full periods, got " + n.internalCounter);

		AddonNodeSupport.newTick();
		set(n, 0, 0F);
		helper.assertTrue(same(eval(n).getNumber(), 0F) && n.internalCounter == 0, "an uptime of 0 is a constant 0 and does not count");
		set(n, 0, 2F);
		set(n, 1, 0F);
		helper.assertTrue(same(eval(n).getNumber(), 1F) && n.internalCounter == 0, "a downtime of 0 is a constant 1 and does not count");
		set(n, 0, -4F);
		helper.assertTrue(same(eval(n).getNumber(), 0F), "a negative uptime is 0");

		// values past an int are saturated, and their sum is taken wide: nothing throws or goes negative
		NodeClock big = new NodeClock(0, 0);
		set(big, 0, 3e9F);
		set(big, 1, 3e9F);
		AddonNodeSupport.newTick();
		helper.assertTrue(same(eval(big).getNumber(), 1F) && big.internalCounter == 1, "uptime and downtime of 3e9 read as saturated ints");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRandomNodesStayInRangeAndAreNotCached(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeRNG n = new NodeRNG(0, 0, c);
		set(n, 0, 3F);
		set(n, 1, 7F);
		Set<Integer> seen = new java.util.HashSet<>();
		for(int i = 0; i < 300; i++) {
			float v = eval(n).getNumber();
			helper.assertTrue(v == Math.floor(v) && v >= 3 && v <= 7, "a whole number from 3 to 7, got " + v);
			seen.add((int) v);
		}
		helper.assertTrue(seen.size() == 5, "all five values turn up in 300 draws, got " + seen);
		set(n, 0, 4F);
		set(n, 1, 4F);
		helper.assertTrue(same(eval(n).getNumber(), 4F), "Min equal to Max is that number");
		set(n, 0, 2.9F);
		set(n, 1, 3.9F);
		for(int i = 0; i < 40; i++) {
			float v = eval(n).getNumber();
			helper.assertTrue(v == 2 || v == 3, "fractional bounds are truncated to 2 and 3, got " + v);
		}
		set(n, 0, 5F);
		set(n, 1, 1F);
		helper.assertTrue(Float.isNaN(eval(n).getNumber()), "an empty range reads NaN");
		set(n, 0, -2e9F);
		set(n, 1, 2e9F);
		helper.assertTrue(Float.isNaN(eval(n).getNumber()), "a range wider than an int reads NaN");
		set(n, 0, 0F);
		set(n, 1, 1_000_000F);
		Set<Float> distinct = new java.util.HashSet<>();
		for(int i = 0; i < 20; i++)
			distinct.add(n.evaluate(0).getNumber());
		helper.assertTrue(distinct.size() > 1, "reading it again in one event draws again: it is not cached");
		helper.assertTrue(Float.isNaN(eval(new NodeRNG(0, 0, new TestControl(new ControlPanel(null)))).getNumber()), "a node with no level reads NaN");

		NodeRNGDecimal d = new NodeRNGDecimal(0, 0, c);
		set(d, 0, -1.5F);
		set(d, 1, 2.5F);
		boolean fractional = false;
		for(int i = 0; i < 300; i++) {
			float v = eval(d).getNumber();
			helper.assertTrue(v >= -1.5F && v < 2.5F, "from -1.5 up to 2.5, got " + v);
			fractional |= v != Math.floor(v);
		}
		helper.assertTrue(fractional, "and it has decimals");
		for(float[] bad : new float[][]{{3, 3}, {4, 1}, {0, Float.POSITIVE_INFINITY}, {Float.NaN, 1}, {-3.4e38F, 3.4e38F}}) {
			set(d, 0, bad[0]);
			set(d, 1, bad[1]);
			helper.assertTrue(Float.isNaN(eval(d).getNumber()), "a width that is not positive and finite reads NaN: " + bad[0] + " to " + bad[1]);
		}
		helper.assertTrue(Float.isNaN(eval(new NodeRNGDecimal(0, 0, new TestControl(new ControlPanel(null)))).getNumber()), "no level, NaN");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSummarizerKeepsAWindowAndCombinesIt(GameTestHelper helper) {
		NodeSummarizer n = new NodeSummarizer(0, 0);
		NodeDropdown method = (NodeDropdown) n.otherElements.get(0);
		set(n, 3, 4F);
		set(n, 2, 0F);
		helper.assertTrue(n.combineMethod == CombineMethod.AVERAGE && n.internalList.length == 40, "it starts averaging a 40-wide window");

		// push 1, 2, 3, then 4, then 5 (which pushes the oldest out), reading each combination at each fill
		float[] pushes = {1, 2, 3, 4, 5};
		method.choose("ADD");
		float[] add = {1, 3, 6, 10, 14};
		for(int i = 0; i < pushes.length; i++) {
			set(n, 0, 1F);
			set(n, 1, pushes[i]);
			helper.assertTrue(same(n.evaluate(0).getNumber(), add[i]), "ADD after pushing " + pushes[i] + " is " + add[i]);
		}
		helper.assertTrue(java.util.Arrays.equals(n.internalList, new float[]{2, 3, 4, 5}) && n.needle == 4, "the full window dropped its oldest: " + java.util.Arrays.toString(n.internalList));

		set(n, 0, 0F);
		method.choose("HIGHEST");
		helper.assertTrue(same(n.evaluate(0).getNumber(), 5F), "HIGHEST");
		method.choose("LOWEST");
		helper.assertTrue(same(n.evaluate(0).getNumber(), 2F), "LOWEST");
		method.choose("MULTIPLY");
		helper.assertTrue(same(n.evaluate(0).getNumber(), 0F), "MULTIPLY starts from 0 as Leafia's does, so it is always 0 (upstream quirk, kept and flagged)");
		method.choose("AVERAGE");
		helper.assertTrue(same(n.evaluate(0).getNumber(), 3F), "AVERAGE of a full window of [2,3,4,5] sums needle-1 = 3 values: (2+3+4)/3 (upstream quirk, kept and flagged)");

		// the same quirk at a partly full window
		n.internalList = new float[]{1, 2, 3, 0};
		n.needle = 3;
		helper.assertTrue(same(n.evaluate(0).getNumber(), 1.5F), "[1,2,3] filled 3 averages the first two");
		n.internalList = new float[]{7, 0, 0, 0};
		n.needle = 1;
		helper.assertTrue(same(n.evaluate(0).getNumber(), 7F), "one value is itself");
		n.needle = 0;
		helper.assertTrue(same(n.evaluate(0).getNumber(), 7F), "and an empty fill still reads the first slot");

		// clear
		method.choose("ADD");
		set(n, 2, 1F);
		helper.assertTrue(same(n.evaluate(0).getNumber(), 0F) && n.needle == 0 && n.internalList.length == 4, "Clear empties the window, and the sum is 0");
		set(n, 2, 0F);

		// trim count changes the window and empties it
		set(n, 3, 2F);
		n.evaluate(0);
		helper.assertTrue(n.internalList.length == 2, "a trim count of 2 makes a 2-wide window");
		set(n, 3, 0F);
		n.evaluate(0);
		helper.assertTrue(n.internalList.length == 1, "0 is raised to 1");
		set(n, 3, 99999F);
		n.evaluate(0);
		helper.assertTrue(n.internalList.length == NodeSummarizer.MAX_LENGTH, "and a large count is cut to " + NodeSummarizer.MAX_LENGTH);
		set(n, 3, Float.NaN);
		set(n, 0, 1F);
		set(n, 1, 6F);
		helper.assertTrue(same(n.evaluate(0).getNumber(), 6F) && n.internalList.length == 1, "a NaN count is a 1-wide window and does not throw (Leafia's made an empty one)");
		n.inputs.remove(3);
		helper.assertTrue(n.evaluate(0) == null, "a node saved with a missing input gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSummarizerReadsAHandMadeTagWithoutBreaking(GameTestHelper helper) {
		registered();
		TestControl c = control(helper);
		NodeSystem sys = new NodeSystem(c);
		sys.addNode(new NodeSummarizer(0, 0));
		for(int len : new int[]{0, 100_000}) {
			CompoundTag tag = sys.writeToNBT(new CompoundTag());
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			for(int i = 0; i < len; i++)
				list.add(net.minecraft.nbt.FloatTag.valueOf(1));
			tag.getCompound("N").getCompound("n0").put("list", list);
			tag.getCompound("N").getCompound("n0").putInt("needle", 999_999);
			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(tag);
			NodeSummarizer s = (NodeSummarizer) back.nodes.get(0);
			helper.assertTrue(s.internalList.length == Math.max(1, Math.min(len, NodeSummarizer.MAX_LENGTH)) && s.needle <= s.internalList.length,
					"a saved window of " + len + " is " + s.internalList.length + " wide with its fill within it (" + s.needle + ")");
			set(s, 0, 1F);
			helper.assertTrue(s.evaluate(0) != null, "and it runs");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ bulk query

	@GameTest(template = EMPTY)
	public static void aBulkQueryCombinesTheNumbersOfEveryLinkedBlock(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		DirectHost a = host(helper, 2, 2);
		DirectHost b = host(helper, 3, 2);
		DirectHost d = host(helper, 4, 2);
		DirectHost text = host(helper, 5, 2);
		DirectHost none = host(helper, 6, 2);
		a.rec.query.put("heat", new DataValueFloat(10));
		b.rec.query.put("heat", new DataValueFloat(20));
		d.rec.query.put("heat", new DataValueFloat(60));
		d.rec.query.put("name", new DataValueString("core"));
		text.rec.query.put("heat", new DataValueString("hot"));
		none.rec.query.put("other", new DataValueFloat(1000));
		for(DirectHost h : List.of(a, b, d, text, none))
			c.taggedLinks.put(h.getBlockPos().toShortString(), h.getBlockPos());

		NodeBulkQuery q = new NodeBulkQuery(0, 0, c);
		helper.assertTrue(q.dataSelector.list.itemNames.equals(List.of("heat", "other")), "the dropdown lists the keys that carry a number, once each, got " + q.dataSelector.list.itemNames);
		helper.assertTrue(same(q.evaluate(0).getNumber(), 0F), "with no key chosen it reads 0");
		q.dataSelector.choose("heat");
		helper.assertTrue(q.dataName.equals("heat") && q.outputs.get(0).type == DataType.NUMBER, "choosing a key types the output as a number");

		Object[][] table = {
				{CombineMethod.ADD, 90F}, {CombineMethod.MULTIPLY, 12000F}, {CombineMethod.AVERAGE, 30F},
				{CombineMethod.HIGHEST, 60F}, {CombineMethod.LOWEST, 10F},
		};
		for(Object[] row : table) {
			q.combineMethod = (CombineMethod) row[0];
			float got = q.evaluate(0).getNumber();
			helper.assertTrue(same(got, (Float) row[1]), row[0] + " of 10, 20 and 60 (a text value and a missing key are skipped) is " + row[1] + ", got " + got);
		}
		q.dataName = "nothing";
		for(CombineMethod m : CombineMethod.values()) {
			q.combineMethod = m;
			float want = m == CombineMethod.MULTIPLY ? 1F : 0F;
			helper.assertTrue(same(q.evaluate(0).getNumber(), want), m + " of no values is " + want);
		}
		helper.assertTrue(q.evaluate(0).getType() == DataType.NUMBER, "and it always answers a number");

		a.rec.query.put("heat", new DataValueFloat(40));
		q.dataName = "heat";
		q.combineMethod = CombineMethod.ADD;
		helper.assertTrue(same(q.evaluate(0).getNumber(), 120F), "it reads live");
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
		helper.assertTrue(same(q.evaluate(0).getNumber(), 100F), "a block that is gone drops out");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBulkQueryRefusesAnUnloadedPositionAndReachesBaseMachinesThroughTheAdapter(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		TestControl c = control(helper);
		DirectHost a = host(helper, 2, 2);
		a.rec.query.put("fluidAmount", new DataValueFloat(500));
		BlockPos adaptedRel = new BlockPos(3, 1, 2);
		helper.setBlock(adaptedRel, LeafiaControlPanelFixtures.adapted);
		AdaptedHost adapted = (AdaptedHost) helper.getBlockEntity(adaptedRel);
		adapted.adapter.query.put("fluidAmount", new DataValueFloat(8000));
		c.taggedLinks.put("direct", a.getBlockPos());
		c.taggedLinks.put("adapted", helper.absolutePos(adaptedRel));
		c.taggedLinks.put("far", FAR);

		NodeBulkQuery q = new NodeBulkQuery(0, 0, c);
		helper.assertTrue(q.dataSelector.list.itemNames.equals(List.of("fluidAmount")), "keys are offered from the block entity and from the adapter, not from the far link: " + q.dataSelector.list.itemNames);
		q.dataName = "fluidAmount";
		q.combineMethod = CombineMethod.ADD;
		helper.assertTrue(same(q.evaluate(0).getNumber(), 8500F), "the direct block and the base machine's adapter are summed, the unloaded link adds nothing, got " + q.evaluate(0).getNumber());
		helper.assertTrue(!helper.getLevel().isLoaded(FAR) && helper.getLevel().getChunkSource().getChunkNow(FAR.getX() >> 4, FAR.getZ() >> 4) == null, "and no chunk was loaded to look");

		c.taggedLinks.clear();
		c.taggedLinks.put("far", FAR);
		q.setDataSelector();
		helper.assertTrue(q.dataSelector.list.itemNames.isEmpty() && same(q.evaluate(0).getNumber(), 0F), "a control linked only to an unloaded block offers nothing and reads 0");
		NodeBulkQuery noPanel = new NodeBulkQuery(0, 0, new TestControl(new ControlPanel(null)));
		noPanel.dataName = "x";
		helper.assertTrue(same(noPanel.evaluate(0).getNumber(), 0F), "a node with no panel reads 0");
		helper.succeed();
	}

	// ------------------------------------------------------------------ sounds

	/** The sound events the level was asked to play while the body ran. */
	private record Played(ResourceLocation id, SoundSource source, float volume, float pitch, double x, double y, double z) {}

	private static List<Played> capture(Runnable body) {
		List<Played> played = new ArrayList<>();
		Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
			if(e.getSound() != null)
				played.add(new Played(BuiltInRegistries.SOUND_EVENT.getKey(e.getSound().value()), e.getSource(), e.getNewVolume(), e.getNewPitch(),
						e.getPosition().x, e.getPosition().y, e.getPosition().z));
		};
		NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, listener);
		try {
			body.run();
		} finally {
			NeoForge.EVENT_BUS.unregister(listener);
		}
		return played;
	}

	@GameTest(template = EMPTY)
	public static void theSounderPlaysAChosenRegisteredEventAtThePanel(GameTestHelper helper) {
		NodeSounder s = new NodeSounder(0, 0);
		NodeDropdown dd = (NodeDropdown) s.otherElements.get(0);
		List<String> names = new ArrayList<>();
		for(NodeSnd v : NodeSnd.values())
			names.add(v.name());
		java.util.Collections.sort(names);
		helper.assertTrue(dd.list.itemNames.equals(names) && names.size() == 12, "twelve sounds, listed alphabetically: " + dd.list.itemNames);
		helper.assertTrue(s.snd == NodeSnd.BUTTON_ACCEPT, "the default is BUTTON_ACCEPT");

		Map<String, String> ids = new java.util.LinkedHashMap<>();
		ids.put("BUTTON_ACCEPT", "hbm:item.techboop");
		ids.put("BUTTON_DENY", "minecraft:entity.villager.no");
		ids.put("BUTTON", "leafia:external.sbwallbutton");
		ids.put("CLICK_ACCEPT", "minecraft:ui.button.click");
		ids.put("CLICK_INVALID", "leafia:ui.button.invalid");
		ids.put("KEYPAD", "leafia:ui.button.keypad");
		ids.put("ARMED", "hbm:weapon.fstbmbstart");
		ids.put("TIMER", "hbm:weapon.fstbmbping");
		ids.put("BLEEP", "hbm:item.techbleep");
		ids.put("POP", "hbm:item.techboop");
		ids.put("SHUTDOWN", "hbm:block.shutdown");
		ids.put("AZ_5", "leafia:block.az5");
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		Recorder owner = new Recorder(pos, helper.getLevel());
		for(Map.Entry<String, String> e : ids.entrySet()) {
			dd.choose(e.getKey());
			helper.assertTrue(s.snd.name().equals(e.getKey()), "the dropdown picks " + e.getKey());
			ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(s.snd.event());
			helper.assertTrue(id != null && id.toString().equals(e.getValue()), e.getKey() + " is the registered event " + e.getValue() + ", got " + id);
			set(s, 0, 1F);
			set(s, 1, 1F);
			List<Played> played = capture(() -> s.doOutput(owner, Map.of(), Map.of()));
			helper.assertTrue(played.size() == 1 && played.get(0).id().equals(id), e.getKey() + " plays exactly that event, got " + played);
			helper.assertTrue(played.get(0).source() == SoundSource.BLOCKS && played.get(0).x() == pos.getX() + 0.5 && played.get(0).y() == pos.getY() + 0.5 && played.get(0).z() == pos.getZ() + 0.5,
					"in the blocks channel, at the middle of the panel's block");
		}

		dd.choose("AZ_5");
		Object[][] vp = {{0.5F, 1F, 0.5F, 1F}, {11F, 3F, 10F, 2F}, {-1F, 0.1F, 0F, 0.5F}, {3F, 1.25F, 3F, 1.25F}};
		for(Object[] row : vp) {
			set(s, 0, (Float) row[0]);
			set(s, 1, (Float) row[1]);
			List<Played> played = capture(() -> helper.assertTrue(s.doOutput(owner, Map.of(), Map.of()), "a sound never cancels the event"));
			helper.assertTrue(played.size() == 1 && same(played.get(0).volume(), (Float) row[2]) && same(played.get(0).pitch(), (Float) row[3]),
					"volume " + row[0] + " and pitch " + row[1] + " play at " + row[2] + " and " + row[3] + ", got " + played);
		}
		set(s, 0, Float.NaN);
		helper.assertTrue(capture(() -> s.doOutput(owner, Map.of(), Map.of())).isEmpty(), "a NaN volume plays nothing");
		set(s, 0, 1F);
		s.inputs.clear();
		helper.assertTrue(capture(() -> helper.assertTrue(s.doOutput(owner, Map.of(), Map.of()), "and continues")).isEmpty(), "a node saved with no inputs plays nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ radio

	private static void forget(String... channels) {
		for(String ch : channels) {
			RTTYSystem.broadcast.keySet().removeIf(k -> k.getValue().equals(ch));
			RTTYSystem.newMessages.keySet().removeIf(k -> k.getValue().equals(ch));
		}
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void theRadioNodesRoundTripThroughTheBus(GameTestHelper helper) {
		TestControl c = control(helper);
		NodeRoRSender text = new NodeRoRSender(0, 0, c);
		NodeRoRSender number = new NodeRoRSender(0, 0, c);
		NodeRoRSender first = new NodeRoRSender(0, 0, c);
		NodeRoRSender second = new NodeRoRSender(0, 0, c);
		NodeRoRSender hostile = new NodeRoRSender(0, 0, c);
		NodeRoRSender hostile2 = new NodeRoRSender(0, 0, c);
		NodeRoRReceiver rx = new NodeRoRReceiver(0, 0, c);
		Recorder owner = new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel());
		String long128 = "f".repeat(NodeRoRSender.MAX_FREQUENCY + 1);

		TickWindow.sequence(helper).thenExecute(() -> {
			set(text, 0, "ccp10.text");
			set(text, 1, "hello");
			helper.assertTrue(text.doOutput(owner, Map.of(), Map.of()), "a transmitter never cancels");
			set(number, 0, "ccp10.number");
			number.inputs.get(1).setDefault(new DataValueFloat(7.9F));
			number.doOutput(owner, Map.of(), Map.of());
			set(first, 0, "ccp10.sum");
			set(first, 1, "3");
			first.doOutput(owner, Map.of(), Map.of());
			set(second, 0, "ccp10.sum");
			set(second, 1, "4");
			second.doOutput(owner, Map.of(), Map.of());
			set(hostile, 0, long128);
			set(hostile, 1, "x");
			hostile.doOutput(owner, Map.of(), Map.of());
			set(hostile2, 0, "");
			set(hostile2, 1, "x");
			hostile2.doOutput(owner, Map.of(), Map.of());
			helper.assertTrue(RTTYSystem.listen(helper.getLevel(), "ccp10.text") == null, "nothing is readable in the tick it was sent: the bus delays by one");
		}).thenIdle(3).thenExecute(() -> {
			try {
				set(rx, 0, "ccp10.text");
				helper.assertTrue(text(eval(rx)).equals("hello"), "the receiver reads what the transmitter sent, got " + text(eval(rx)));
				set(rx, 0, "ccp10.number");
				helper.assertTrue(text(eval(rx)).equals("7"), "a number goes out as its integer part, got " + text(eval(rx)));
				set(rx, 0, "ccp10.sum");
				helper.assertTrue(text(eval(rx)).equals("7"), "two numbers sent to one channel in one tick add, got " + text(eval(rx)));
				helper.assertTrue(RTTYSystem.listen(helper.getLevel(), long128) == null && RTTYSystem.listen(helper.getLevel(), "") == null, "a frequency past the ceiling, and an empty one, were not sent");

				set(rx, 0, "ccp10.quiet");
				helper.assertTrue(text(eval(rx)).equals("7"), "a channel nobody published on holds the last reading (it read 7 from the sum)");
				rx.lastValue = "0";
				helper.assertTrue(text(eval(rx)).equals("0"), "and a fresh node starts at 0");
				rx.cacheValid = true;
				set(rx, 0, "ccp10.text");
				helper.assertTrue(text(rx.evaluate(0)).equals("0"), "the read is cached for the event");

				Node back = reload(rx, c);
				helper.assertTrue(back instanceof NodeRoRReceiver r && r.lastValue.equals("0"), "and the receiver saves and loads");
				helper.assertTrue(text(new NodeRoRReceiver(0, 0, new TestControl(new ControlPanel(null))).evaluate(0)).equals("0"), "a receiver with no level reads its last value and does not throw");
			} finally {
				forget("ccp10.text", "ccp10.number", "ccp10.sum", "ccp10.quiet");
			}
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ a graph, end to end, and hostile graphs

	@GameTest(template = EMPTY)
	public static void aGraphOfLeafiaNodesRunsFromAnEventToAVariable(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			registered();
			TestControl c = control(helper);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
			NodeFormat fmt = new NodeFormat(0, 0);
			fmt.format = "%s/%s";
			fmt.updateFormat();
			NodeSIPfx si = new NodeSIPfx(0, 0);
			set(si, 0, 12500F);
			NodeSubString sub = new NodeSubString(0, 0);
			set(sub, 0, "heat");
			set(sub, 1, 1F);
			set(sub, 2, 2F);
			wire(fmt, 0, si, 0);
			wire(fmt, 1, sub, 0);
			com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar set = new com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar(0, 0, c);
			c.vars.put("label", new DataValueString(""));
			set.setData("label", false);
			wire(set, 0, fmt, 0);
			for(Node n : List.of(si, sub, fmt, set))
				sys.addNode(n);

			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(sys.writeToNBT(new CompoundTag()));
			back.receiveEvent(c.panel, c, com.leafia.contents.machines.controlpanel.core.ControlEvent.newEvent("tick"));
			helper.assertTrue(c.vars.get("label").toString().equals("12.5 k/he"), "the saved graph runs: SIPfx and Substring into Format into a variable, got " + c.vars.get("label"));
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aHostileGraphOfLeafiaNodesIsStillRefused(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			registered();
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			CompoundTag before = te.panel.writeToNBT(new CompoundTag());
			ControlPanel panelBefore = te.panel;

			// a cycle of Leafia nodes, written by hand: a depends on b, b on a
			CompoundTag cycle = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeCache a = new NodeCache(0, 0);
				NodeAddString b = new NodeAddString(100, 0);
				sys.addNode(a);
				sys.addNode(b);
				wire(a, 0, b, 0);
				wire(b, 0, a, 0);
			});
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), cycle), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a cycle of Leafia nodes is refused and the panel is untouched");

			// a chain one longer than the guard allows
			CompoundTag chain = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				Node prev = new NodeCache(0, 0);
				sys.addNode(prev);
				for(int i = 0; i <= ControlPanelGuard.MAX_CHAIN; i++) {
					NodeCache next = new NodeCache(i, 0);
					wire(next, 0, prev, 0);
					sys.addNode(next);
					prev = next;
				}
			});
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), chain), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a chain of Leafia nodes past the ceiling is refused");

			// too many nodes for one panel
			CompoundTag huge = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				for(int i = 0; i <= ControlPanelGuard.MAX_NODES; i++)
					sys.addNode(new NodeSRLatch(i, 0));
			});
			helper.assertTrue(ControlPanelGuard.scan(huge) != null, "the scan names a graph past the node ceiling");
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), huge), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a graph of Leafia nodes past the node ceiling is refused");

			// a sound-and-radio graph that is within the limits is accepted, and a saved cycle loads as a placeholder that keeps its tag
			CompoundTag ok = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeClock clock = new NodeClock(0, 0);
				NodeRoRSender tx = new NodeRoRSender(100, 0, c);
				NodeSounder snd = new NodeSounder(200, 0);
				sys.addNode(clock);
				sys.addNode(tx);
				sys.addNode(snd);
				wire(tx, 1, clock, 0);
				wire(snd, 0, clock, 0);
			});
			helper.assertTrue(ControlPanelGuard.scan(ok) == null, "a modest graph of the same nodes passes the scan");
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), ok), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel != panelBefore && te.panel.controls.size() == 1, "and is accepted");
			Control saved = te.panel.controls.get(0);
			helper.assertTrue(saved.receiveNodeMap.get("tick").nodes.stream().noneMatch(n -> n instanceof UnknownNode) && saved.receiveNodeMap.get("tick").nodes.size() == 3,
					"read back through Leafia's loader, not as placeholders");

			// a saved cycle of Leafia nodes loads as a placeholder that keeps its tag, and never runs
			ControlPanelTE cyclic = LeafiaControlPanelKit.place(helper, AT.east(4));
			TestControl cc = LeafiaControlPanelKit.control(cyclic.panel, 1, 1);
			NodeSystem csys = LeafiaControlPanelKit.receiveSystem(cc, "tick");
			NodeCache ca = new NodeCache(0, 0);
			NodeCache cb = new NodeCache(0, 0);
			csys.addNode(ca);
			csys.addNode(cb);
			wire(ca, 0, cb, 0);
			wire(cb, 0, ca, 0);
			CompoundTag cyclicTag = cyclic.saveWithoutMetadata(helper.getLevel().registryAccess());
			ControlPanelTE reloaded = LeafiaControlPanelKit.place(helper, AT.east(8));
			reloaded.loadWithComponents(cyclicTag, helper.getLevel().registryAccess());
			helper.assertTrue(reloaded.panel.controls.size() == 1 && reloaded.panel.controls.get(0) instanceof UnknownControl u && u.problem != null && u.problem.contains("cycle"),
					"the cyclic control is an inert placeholder on load");
			reloaded.panel.receiveEvent(reloaded.getBlockPos(), com.leafia.contents.machines.controlpanel.core.ControlEvent.newEvent("tick")); // would overflow the stack if it ran
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ the class-load sweep (§2.2)

	@GameTest(template = EMPTY)
	public static void leafiasNodesNameNoClientClass(GameTestHelper helper) throws Exception {
		String base = "com/leafia/contents/machines/controlpanel";
		List<String> found = new ArrayList<>();
		URL url = LeafiaControlPanelLeafiaNodeTests.class.getClassLoader().getResource(base);
		if(url != null) {
			Path root = Paths.get(url.toURI());
			try(Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					if(rel.startsWith("nodes/") || rel.startsWith("categories/") || rel.startsWith("elements/") || rel.startsWith("Addon"))
						found.add(base + "/" + rel.substring(0, rel.length() - ".class".length()));
				});
			}
		}
		helper.assertTrue(found.size() >= 25, "the sweep found Leafia's node classes on the classpath (" + found.size() + ")");
		for(String name : found) {
			byte[] bytes;
			try(var in = LeafiaControlPanelLeafiaNodeTests.class.getClassLoader().getResourceAsStream(name + ".class")) {
				bytes = in.readAllBytes();
			}
			String s = new String(bytes, StandardCharsets.ISO_8859_1);
			helper.assertTrue(!(s.contains("net/minecraft/client") || s.contains("com/mojang/blaze3d") || s.contains("org/lwjgl") || s.contains(base + "/core/client")),
					name + " names a client class");
		}
		helper.succeed();
	}
}
