package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.machines.controlpanel.AddonNodeLoader;
import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaInput;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaOutput;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSetup;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.modular.NodeEditContext;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.instruments.AddonControlType;
import com.leafia.contents.machines.controlpanel.instruments.AddonInstrumentRegister;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.drawing.Drawing;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph.GraphSkin;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverBase;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeButton;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeSwitch;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphAdd;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphBounds;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Leafia's first six instruments of the Custom Control Panel and the graph nodes (CCP-INSTRUMENTS-1, D-3071): the graph, the boxed
 * label, the two levers, and the large switch and button.
 *
 * <h2>What this cannot do, stated first</h2>
 * Nothing here draws. A GameTest server cannot load a client class, so the renderers, the two configuration panes and the editor's
 * preview are not reached; that is the client check in D-3071. What it can pin: every size, variable and configuration against
 * Leafia's constructors (typed from the source, not read back), the graph's sample buffer, its clamps and its saved form, the two graph
 * nodes, the default graphs of the lever, the switch and the button run through the real event path, the registry and the packet
 * table, the generated model assets against the part table, and the sync packets - round-tripped through the real framing, then
 * attacked: a client sending one, a target that is not an instrument, a count past the cap, a position nobody has loaded.
 *
 * <p>Everything is synchronous on the server thread. Logic tests run on a panel hosted by a recorder, which nothing ticks; the
 * packet tests place a real panel and put it back. Every static the tests touch (the sound hook) is restored in a {@code finally}
 * (D-952).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelInstrumentTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;
	private static final float EPS = 1E-4F;

	// ------------------------------------------------------------------ helpers

	private static void setup() {
		ControlPanelSetup.commonSetup();
	}

	private static ControlPanel hostedPanel(GameTestHelper helper) {
		return new ControlPanel(new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel(), "tick"));
	}

	/** What the editor hands {@code populateDefaultNodes}: the control's out-events then its in-events, as registered. */
	private static List<ControlEvent> editorEvents(Control c) {
		List<ControlEvent> events = new ArrayList<>();
		for(String name : c.getOutEvents())
			events.add(ControlEvent.getRegisteredEvent(name));
		for(String name : c.getInEvents()) {
			ControlEvent evt = ControlEvent.getRegisteredEvent(name);
			if(!events.contains(evt))
				events.add(evt);
		}
		return events;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Control> T add(ControlPanel panel, String key, float x, float y) {
		Control c = ControlRegistry.getNew(key, panel);
		c.posX = x;
		c.posY = y;
		c.populateDefaultNodes(editorEvents(c));
		panel.controls.add(c);
		return (T) c;
	}

	private static void press(ControlPanel panel, Control c, boolean sneaking) {
		panel.receiveDirectEvent(c, ControlEvent.newEvent("ctrl_press").setVar("isSneaking", sneaking));
	}

	private static void tick(ControlPanel panel, Control c) {
		panel.receiveDirectEvent(c, ControlEvent.newEvent("tick"));
	}

	private static float v(Control c, String var) {
		return c.getVar(var).getNumber();
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < EPS;
	}

	private static String describe(DataValue v) {
		if(v instanceof DataValueString s)
			return "s:" + s;
		return "f:" + v.getNumber();
	}

	private static List<String> describe(Map<String, DataValue> map) {
		List<String> out = new ArrayList<>();
		for(Map.Entry<String, DataValue> e : map.entrySet())
			out.add(e.getKey() + "=" + describe(e.getValue()));
		return out;
	}

	private static Map<String, DataValue> configs(Object... kv) {
		Map<String, DataValue> m = new LinkedHashMap<>();
		for(int i = 0; i < kv.length; i += 2)
			m.put((String) kv[i], kv[i + 1] instanceof String s ? new DataValueString(s) : new DataValueFloat(((Number) kv[i + 1]).floatValue()));
		return m;
	}

	/** Applies configuration entries over a control's defaults, the way the editor and a panel load do. */
	private static void apply(Control c, Object... kv) {
		Map<String, DataValue> m = new LinkedHashMap<>(c.getConfigs());
		m.putAll(configs(kv));
		c.applyConfigs(m);
	}

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

	// ------------------------------------------------------------------ the registry

	private record Inst(String key, String display, Class<? extends Control> type, ControlType family, float[] size, boolean pressable) {}

	/** This batch's six, in Leafia's registration order (batch 12's five sit between them, see the registry test). Sizes typed from Leafia's {@code getSize}. */
	private static final List<Inst> INSTRUMENTS = List.of(
			new Inst("leafia_sbswitch", "Large Switch", LargeSwitch.class, ControlType.SWITCH, new float[]{6, 6, 0.5F}, true),
			new Inst("leafia_sbbutton", "Large Button", LargeButton.class, ControlType.BUTTON, new float[]{6, 6, 0.5F}, true),
			new Inst("leafia_graph", "Graph", Graph.class, AddonControlType.GRAPH, new float[]{6.2F, 4.2F, 0.1F}, false),
			new Inst("leafia_drawing_box", "Boxed Label", Drawing.class, ControlType.LABEL, new float[]{1, 1, 0.01F}, false),
			new Inst("leafia_lever_h", "Horizontal Lever", LeverHorizontal.class, AddonControlType.LEVER, new float[]{2, 1, 1.25F}, true),
			new Inst("leafia_lever_v", "Vertical Lever", LeverVertical.class, AddonControlType.LEVER, new float[]{1, 2, 1.25F}, true));

	@GameTest(template = EMPTY)
	public static void leafiasSixInstrumentsAreRegisteredAfterTheStockFourteenInOrder(GameTestHelper helper) {
		setup();
		List<String> keys = new ArrayList<>(ControlRegistry.registry.keySet());
		List<String> have = new ArrayList<>();
		for(String k : keys)
			if(k.startsWith("leafia_"))
				have.add(k);
		// all eleven, in Leafia's AddonInstrumentRegister order: batch 12 (D-3072) put its five between this batch's six
		List<String> want = List.of("leafia_nixie", "leafia_sbswitch", "leafia_sbbutton", "leafia_textindicator", "leafia_rbmk", "leafia_graph",
				"leafia_meter_h", "leafia_meter_v", "leafia_drawing_box", "leafia_lever_h", "leafia_lever_v");
		helper.assertTrue(have.equals(want), "Leafia's instruments, in his order, got " + have);
		helper.assertTrue(keys.indexOf("leafia_sbswitch") > keys.indexOf("label"), "all after the stock fourteen");

		for(Inst i : INSTRUMENTS) {
			Control proto = ControlRegistry.registry.get(i.key);
			helper.assertTrue(proto.getClass() == i.type && proto.name.equals(i.display) && proto.registryName.equals(i.key), i.key + " is the " + i.display);
			helper.assertTrue(proto.panel == null, "a prototype belongs to no panel");
			helper.assertTrue(proto.getControlType() == i.family, i.key + " is in the " + i.family.name + " family");
			float[] size = proto.getSize();
			helper.assertTrue(size.length == 3 && near(size[0], i.size[0]) && near(size[1], i.size[1]) && near(size[2], i.size[2]),
					i.key + " is " + java.util.Arrays.toString(i.size) + ", got " + java.util.Arrays.toString(size));
			helper.assertTrue(proto.getOutEvents().equals(i.pressable ? List.of("ctrl_press") : List.of()), i.key + " out-events");
			helper.assertTrue(proto.newControl(null).getClass() == i.type && proto.newControl(null) != proto, i.key + " makes a new one of its own class");
		}

		// the three families and where each instrument sits in the placement list
		helper.assertTrue(AddonControlType.RBMK_DISPLAY.name.equals("RBMK Display") && AddonControlType.GRAPH.name.equals("Graph") && AddonControlType.LEVER.name.equals("Lever"),
				"the three families are Leafia's names");
		helper.assertTrue(ControlType.getByName("Graph") == AddonControlType.GRAPH && ControlType.getByName("Lever") == AddonControlType.LEVER
				&& ControlType.getByName("RBMK Display") == AddonControlType.RBMK_DISPLAY, "and registered once, by name");
		helper.assertTrue(ControlRegistry.getAllControlsOfType(AddonControlType.GRAPH).equals(List.of("leafia_graph")), "one graph");
		helper.assertTrue(ControlRegistry.getAllControlsOfType(AddonControlType.LEVER).equals(List.of("leafia_lever_h", "leafia_lever_v")), "two levers");
		helper.assertTrue(ControlRegistry.getAllControlsOfType(ControlType.LABEL).contains("leafia_drawing_box"), "the boxed label sits with the labels");
		helper.assertTrue(ControlType.ALL_VALUES.indexOf(ControlType.LABEL) == 8, "and the stock nine families keep their places");

		// registering again changes nothing
		int before = ControlRegistry.addonControls.size();
		AddonInstrumentRegister.register();
		ControlPanelSetup.commonSetup();
		helper.assertTrue(ControlRegistry.addonControls.size() == before, "a second registration adds nothing to the addon list");
		helper.assertTrue(new ArrayList<>(ControlRegistry.registry.keySet()).equals(keys), "nor to the registry");
		helper.succeed();
	}

	private static final Map<String, List<String>> VARS = new LinkedHashMap<>();
	private static final Map<String, List<String>> CONFIGS = new LinkedHashMap<>();

	static {
		VARS.put("leafia_sbswitch", List.of("position=f:0.0"));
		VARS.put("leafia_sbbutton", List.of("isPushed=f:0.0", "isLit=f:0.0"));
		VARS.put("leafia_graph", List.of("minValue=f:0.0", "maxValue=f:1.0"));
		VARS.put("leafia_drawing_box", List.of());
		VARS.put("leafia_lever_h", List.of("position=f:50.0"));
		VARS.put("leafia_lever_v", List.of("position=f:50.0"));
		for(String k : VARS.keySet())
			CONFIGS.put(k, List.of());
		CONFIGS.put("leafia_graph", List.of("red=f:0.0", "green=f:255.0", "blue=f:0.0", "skin=s:Console", "segments=f:40.0", "verticalLineSpan=f:1.0",
				"horizontalLineDivision=f:10.0", "decimalLength=f:2.0", "textOffset=f:0.5", "width=f:6.0", "height=f:4.0"));
		CONFIGS.put("leafia_drawing_box", List.of("red=f:100.0", "green=f:100.0", "blue=f:100.0", "width=f:0.2", "height=f:0.2", "scale=f:25.0", "text=s:", "white=f:0.0"));
	}

	@GameTest(template = EMPTY)
	public static void everyInstrumentsVariablesConfigsAndBoxesArePinned(GameTestHelper helper) {
		setup();
		for(Inst i : INSTRUMENTS) {
			Control c = ControlRegistry.getNew(i.key, null);
			helper.assertTrue(describe(c.vars).equals(VARS.get(i.key)), i.key + " variables, got " + describe(c.vars));
			helper.assertTrue(describe(c.getConfigs()).equals(CONFIGS.get(i.key)), i.key + " configuration, got " + describe(c.getConfigs()));
			helper.assertTrue(c.getInEvents().equals(List.of("tick", "initialize", "redstone_input")), i.key + " listens for CE's three");
		}
		// no collision box for the graph and the label, a real one for the other four
		helper.assertTrue(ControlRegistry.getNew("leafia_graph", null).getBoundingBox() == null && ControlRegistry.getNew("leafia_drawing_box", null).getBoundingBox() == null,
				"a click passes through a chart and a label");
		AABB sw = ControlRegistry.getNew("leafia_sbswitch", null).getBoundingBox();
		helper.assertTrue(sw != null && near((float) sw.minX, -0.5F) && near((float) sw.maxX, 5.5F) && near((float) sw.maxY, 0.5F) && near((float) sw.minZ, -0.5F) && near((float) sw.maxZ, 5.5F),
				"the large switch is a 6 x 6 x 0.5 box offset as CE offsets a big control, got " + sw);
		AABB lv = ControlRegistry.getNew("leafia_lever_h", null).getBoundingBox();
		helper.assertTrue(lv != null && near((float) lv.maxY, 1.25F) && near((float) (lv.maxX - lv.minX), 2F) && near((float) (lv.maxZ - lv.minZ), 1F), "the horizontal lever is 2 x 1 x 1.25");

		// the box the editor selects them by
		Drawing d = (Drawing) ControlRegistry.getNew("leafia_drawing_box", null);
		d.posX = 3;
		d.posY = 4;
		apply(d, "width", 2.5F, "height", 1.5F);
		float[] box = d.getBox();
		helper.assertTrue(near(box[0], 3) && near(box[1], 4) && near(box[2], 5.5F) && near(box[3], 5.5F), "the label's box is its own width and height, got " + java.util.Arrays.toString(box));
		Graph g = (Graph) ControlRegistry.getNew("leafia_graph", null);
		g.posX = 1;
		g.posY = 2;
		float[] gb = g.getBox();
		helper.assertTrue(near(gb[2], 1 + 6.2F) && near(gb[3], 2 + 4.2F), "the graph's box is its size from its position");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the graph: buffer, scale, clamps, saving

	@GameTest(template = EMPTY)
	public static void theGraphKeepsTheLastSegmentsSamplesOldestFirst(GameTestHelper helper) {
		setup();
		Graph g = (Graph) ControlRegistry.getNew("leafia_graph", null);
		helper.assertTrue(g.values.length == 40 && g.getSegments() == 40, "forty segments to start with");
		for(double x : g.values)
			helper.assertTrue(x == 0, "all zero");
		g.pushValue(1);
		g.pushValue(2);
		g.pushValue(3);
		helper.assertTrue(g.values[37] == 1 && g.values[38] == 2 && g.values[39] == 3 && g.values[36] == 0, "the newest is last");
		for(int i = 4; i <= 45; i++)
			g.pushValue(i);
		helper.assertTrue(g.values[0] == 6 && g.values[39] == 45 && g.values.length == 40, "after 45 pushes it holds 6..45");

		// a graph of three: the window is exactly three long
		apply(g, "segments", 3);
		helper.assertTrue(g.values.length == 3 && g.values[0] == 0 && g.values[2] == 0, "a new segment count is a new, empty window");
		g.pushValue(7);
		g.pushValue(8);
		g.pushValue(9);
		g.pushValue(10);
		helper.assertTrue(g.values[0] == 8 && g.values[1] == 9 && g.values[2] == 10, "of the last three");
		// the same count again keeps the samples
		apply(g, "segments", 3, "red", 10);
		helper.assertTrue(g.values[2] == 10, "an edit that leaves the count alone keeps the samples");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGraphScalesBetweenItsMinAndMaxVariables(GameTestHelper helper) {
		setup();
		Graph g = (Graph) ControlRegistry.getNew("leafia_graph", null);
		helper.assertTrue(near((float) g.scaleValue(0.25), 0.25F) && near((float) g.scaleValue(0), 0) && near((float) g.scaleValue(1), 1), "0..1 by default");
		helper.assertTrue(near((float) g.scaleValue(2), 2) && near((float) g.scaleValue(-1), -1), "outside the range is outside 0..1: the renderer clips");
		g.vars.put("minValue", new DataValueFloat(2));
		g.vars.put("maxValue", new DataValueFloat(6));
		helper.assertTrue(near((float) g.scaleValue(4), 0.5F) && near((float) g.scaleValue(2), 0) && near((float) g.scaleValue(6), 1), "2..6");
		helper.assertTrue(g.getMinValue() == 2 && g.getMaxValue() == 6, "and the accessors read the variables");
		g.vars.put("minValue", new DataValueFloat(5));
		g.vars.put("maxValue", new DataValueFloat(5));
		helper.assertTrue(g.scaleValue(1) == 5 && g.scaleValue(100) == 5, "an empty range gives the maximum itself, as Leafia's does");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGraphsConfigurationIsClampedToTheEditorsRangesAndSavedRaw(GameTestHelper helper) {
		setup();
		Graph g = (Graph) ControlRegistry.getNew("leafia_graph", null);
		apply(g, "segments", 2_000_000_000F, "verticalLineSpan", 0, "horizontalLineDivision", 1000, "decimalLength", 9, "width", 1000, "height", -4,
				"textOffset", 5, "red", 300, "green", -1, "blue", 7, "skin", "Dark");
		helper.assertTrue(g.getSegments() == 200 && g.values.length == 200, "two billion segments is the editor's 200 and an array of 200, got " + g.getSegments());
		helper.assertTrue(g.getVerticalLineSpan() == 1, "a line span of 0 is 1 (it was an endless loop in the renderer)");
		helper.assertTrue(g.getHorizontalLineDivision() == 20 && g.getDecimalLength() == 5, "20 divisions, 5 decimals");
		helper.assertTrue(near(g.getWidth(), 10) && near(g.getHeight(), 1) && near(g.getTextOffset(), 1), "10 wide, 1 high, offset 1");
		helper.assertTrue(near(g.getRed(), 255) && near(g.getGreen(), 0) && near(g.getBlue(), 7), "colour 0..255");
		helper.assertTrue(g.getSkin() == GraphSkin.DARK, "the Dark skin by name");
		helper.assertTrue(g.getConfigs().get("segments").getNumber() == 2_000_000_000F && g.getConfigs().get("width").getNumber() == 1000F,
				"the configuration map keeps what it was given, so a panel round-trips");

		apply(g, "segments", -5, "decimalLength", -1);
		helper.assertTrue(g.getSegments() == 1 && g.values.length == 1 && g.getDecimalLength() == 0, "the other end: one segment, no decimals");

		// NaN leaves a field as it was; an unknown skin does too
		apply(g, "segments", 12, "width", 3, "red", 40);
		apply(g, "segments", Float.NaN, "width", Float.NaN, "red", Float.NaN, "skin", "No Such Skin");
		helper.assertTrue(g.getSegments() == 12 && near(g.getWidth(), 3) && near(g.getRed(), 40) && g.getSkin() == GraphSkin.DARK, "NaN and an unknown skin change nothing");

		// the size follows the plot
		apply(g, "width", 3, "height", 2);
		float[] size = g.getSize();
		helper.assertTrue(near(size[0], 3.2F) && near(size[1], 2.2F) && near(size[2], 0.1F), "size is the plot plus a 0.1 frame each side, got " + java.util.Arrays.toString(size));

		// the three skins, as Leafia registers them
		List<GraphSkin> skins = GraphSkin.skins();
		helper.assertTrue(skins.size() >= 3 && skins.get(0) == GraphSkin.CONSOLE && skins.get(1) == GraphSkin.LIGHT && skins.get(2) == GraphSkin.DARK, "Console, Light, Dark");
		helper.assertTrue(GraphSkin.CONSOLE.emissive && !GraphSkin.CONSOLE.hasGrid && GraphSkin.CONSOLE.bgColor == 0, "Console is emissive and plain");
		helper.assertTrue(!GraphSkin.LIGHT.emissive && GraphSkin.LIGHT.hasGrid && GraphSkin.LIGHT.bgColor == 0xFFFFFF && GraphSkin.LIGHT.gridColor == 0xCCCCCC
				&& GraphSkin.LIGHT.textColor == 0xAAAAAA, "Light");
		helper.assertTrue(GraphSkin.DARK.emissive && GraphSkin.DARK.hasGrid && GraphSkin.DARK.bgColor == 0x1A1A1A && GraphSkin.DARK.gridColor == 0x3A3A3A
				&& GraphSkin.DARK.textColor == 0xCCCCCC, "Dark");
		helper.assertTrue(GraphSkin.registerOrGet("Light", true, true, 1, 2, 3) == GraphSkin.LIGHT && GraphSkin.byName("nope") == null, "registerOrGet returns the one there");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGraphsSamplesAreSavedAndOnlyReadBackAtTheSameCount(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		Graph g = add(panel, "leafia_graph", 1, 1);
		apply(g, "segments", 5, "width", 7, "skin", "Light");
		for(double x : new double[]{1, 2.5, -3, 4, 5.25})
			g.pushValue(x);
		CompoundTag tag = g.writeToNBT(new CompoundTag());
		helper.assertTrue(tag.getList("graphData", 5).size() == 5 && tag.getList("graphData", 5).getFloat(2) == -3F, "graphData is a list of five floats");

		Control back = panel.readControl(tag);
		helper.assertTrue(back instanceof Graph, "a saved graph loads as a graph");
		Graph g2 = (Graph) back;
		helper.assertTrue(g2.values.length == 5 && g2.values[0] == 1 && g2.values[1] == 2.5 && g2.values[2] == -3 && g2.values[4] == 5.25,
				"the samples come back: configuration first, then the data");
		helper.assertTrue(near(g2.getWidth(), 7) && g2.getSkin() == GraphSkin.LIGHT, "with the configuration");
		helper.assertTrue(panel.cloneControl(g) instanceof Graph c && c.values[4] == 5.25, "and a clone of it keeps them");

		// data for another count is ignored, not cut or stretched
		Graph other = (Graph) ControlRegistry.getNew("leafia_graph", panel);
		other.applyConfigs(new LinkedHashMap<>(other.getConfigs()));
		other.readFromNBT(tag);
		helper.assertTrue(other.values.length == 40 && other.values[0] == 0 && other.values[39] == 0, "five samples into a graph of forty change nothing");

		// a list of the wrong type is ignored, and so is no list at all
		CompoundTag wrong = g.writeToNBT(new CompoundTag());
		ListTag ints = new ListTag();
		for(int i = 0; i < 5; i++)
			ints.add(IntTag.valueOf(9));
		wrong.put("graphData", ints);
		Control read = panel.readControl(wrong);
		helper.assertTrue(read instanceof Graph w && w.values.length == 5 && w.values[0] == 0, "a list of ints reads as no samples");
		wrong.remove("graphData");
		helper.assertTrue(panel.readControl(wrong) instanceof Graph, "and a graph saved without any loads");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the two graph nodes

	@GameTest(template = EMPTY)
	public static void graphAddPushesIntoItsGraphOnlyAndNeverCancels(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		Graph g = add(panel, "leafia_graph", 0, 0);
		apply(g, "segments", 4);

		NodeGraphAdd node = new NodeGraphAdd(0, 0, g);
		helper.assertTrue(node.getDisplayName().equals("Graph Add") && node.inputs.size() == 1 && node.inputs.get(0).name.equals("Value")
				&& node.inputs.get(0).type == DataValue.DataType.NUMBER, "one number input called Value");
		helper.assertTrue(node.writeToNBT(new CompoundTag(), new NodeSystem(g)).getString("nodeType").equals("leafia_graph"), "saved as leafia_graph");
		helper.assertTrue(node.getColor() == com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister.colorOutput, "in the output colour");

		node.inputs.get(0).setDefault(new DataValueFloat(7));
		helper.assertTrue(node.doOutput(panel.parent, Map.of(), Map.of()), "it returns true: in a send graph false would cancel the event");
		helper.assertTrue(g.values[3] == 7 && g.values[2] == 0, "and pushed the 7");

		// run from the graph's own event, through the real path
		NodeSystem sys = LeafiaControlPanelKit.receiveSystem(g, "tick");
		NodeGraphAdd inSys = new NodeGraphAdd(0, 0, g);
		inSys.inputs.get(0).setDefault(new DataValueFloat(2.5F));
		sys.addNode(inSys);
		tick(panel, g);
		tick(panel, g);
		helper.assertTrue(g.values[3] == 2.5 && g.values[2] == 2.5 && g.values[1] == 7, "a tick event pushes once each: 7, 2.5, 2.5");

		// it survives a save and finds its graph again through the loader
		CompoundTag saved = node.writeToNBT(new CompoundTag(), sys);
		Node loaded = new AddonNodeLoader().nodeFromNBT(saved, sys);
		helper.assertTrue(loaded instanceof NodeGraphAdd a && a.ctrl == g, "the addon loader makes it again, bound to the system's control");
		helper.assertTrue(new AddonNodeLoader().nodeFromNBT(new CompoundTag(), sys) == null, "and no type is no node");

		// in the graph of anything else it does nothing
		TestControl other = new TestControl(panel);
		NodeGraphAdd stray = new NodeGraphAdd(0, 0, other);
		stray.inputs.get(0).setDefault(new DataValueFloat(99));
		helper.assertTrue(stray.doOutput(panel.parent, Map.of(), Map.of()) && g.values[3] == 2.5, "a node outside a graph pushes nothing and does not fail");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void graphBoundsReadsTheHighestAndLowestSample(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		Graph g = add(panel, "leafia_graph", 0, 0);
		apply(g, "segments", 3);

		NodeGraphBounds node = new NodeGraphBounds(0, 0, g);
		helper.assertTrue(node.getDisplayName().equals("Graph Bounds") && node.inputs.isEmpty() && node.outputs.size() == 2
				&& node.outputs.get(0).name.equals("Highest") && node.outputs.get(1).name.equals("Lowest"), "two outputs, Highest and Lowest");
		helper.assertTrue(node.writeToNBT(new CompoundTag(), new NodeSystem(g)).getString("nodeType").equals("leafia_graph_bounds"), "saved as leafia_graph_bounds");
		helper.assertTrue(node.getColor() == com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister.colorInput, "in the input colour");

		helper.assertTrue(node.evaluate(0).getNumber() == 0 && node.evaluate(1).getNumber() == 0, "an empty window is 0 and 0");
		g.pushValue(5);
		g.pushValue(2);
		g.pushValue(9);
		helper.assertTrue(node.evaluate(0).getNumber() == 9 && node.evaluate(1).getNumber() == 2, "9 and 2");
		g.pushValue(-1);
		g.pushValue(-4);
		g.pushValue(-2);
		helper.assertTrue(node.evaluate(0).getNumber() == -1 && node.evaluate(1).getNumber() == -4, "all-negative: -1 and -4, not clamped by the zeros that left");
		helper.assertTrue(node.evaluate(2).getNumber() == 0 && node.evaluate(-1).getNumber() == 0, "an output that is not there reads 0");

		Node loaded = new AddonNodeLoader().nodeFromNBT(node.writeToNBT(new CompoundTag(), new NodeSystem(g)), new NodeSystem(g));
		helper.assertTrue(loaded instanceof NodeGraphBounds b && b.ctrl == g, "the addon loader makes it again");
		NodeGraphBounds stray = new NodeGraphBounds(0, 0, new TestControl(panel));
		helper.assertTrue(stray.evaluate(0).getNumber() == 0 && stray.evaluate(1).getNumber() == 0, "in another control's graph it reads 0");

		// an autoscaling chart: bounds wired to the graph's own variables through the real graph
		NodeSystem sys = LeafiaControlPanelKit.receiveSystem(g, "tick");
		NodeGraphBounds b2 = new NodeGraphBounds(0, 0, g);
		com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar hi = new com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar(0, 0, g).setData("maxValue", false);
		com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar lo = new com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar(0, 0, g).setData("minValue", false);
		hi.inputs.get(0).setData(b2, 0, true);
		lo.inputs.get(0).setData(b2, 1, true);
		sys.addNode(b2);
		sys.addNode(hi);
		sys.addNode(lo);
		tick(panel, g);
		helper.assertTrue(g.getMaxValue() == -1 && g.getMinValue() == -4, "bounds into minValue and maxValue autoscale the chart, got " + g.getMinValue() + ".." + g.getMaxValue());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGraphNodesAreOfferedOnlyInAGraphsOwnEditor(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		Graph g = add(panel, "leafia_graph", 0, 0);
		TestControl other = new TestControl(panel);

		for(Control c : new Control[]{g, other}) {
			boolean isGraph = c == g;
			NodeEditContext ctx = new NodeEditContext(new NodeSystem(c), ControlEvent.getRegisteredEvent("tick"), List.of());
			ItemList in = new ItemList(0, 0, 32, s -> null);
			new NCLeafiaInput().addItems(in, 0, 0, ctx);
			ItemList out = new ItemList(0, 0, 32, s -> null);
			new NCLeafiaOutput().addItems(out, 0, 0, ctx);
			helper.assertTrue(in.itemNames.contains("Graph Bounds") == isGraph, "Graph Bounds is offered " + (isGraph ? "in" : "outside") + " a graph, got " + in.itemNames);
			helper.assertTrue(out.itemNames.contains("Graph Add") == isGraph, "Graph Add is offered " + (isGraph ? "in" : "outside") + " a graph, got " + out.itemNames);
			helper.assertTrue(in.itemNames.contains("Bulk Query Block") && in.itemNames.contains("Receive RoR") && out.itemNames.contains("Play Sound"), "and the rest of Leafia's entries are as before");
		}
		NodeEditContext ctx = new NodeEditContext(new NodeSystem(g), ControlEvent.getRegisteredEvent("tick"), List.of());
		Node a = new NCLeafiaOutput().selectItem("Graph Add", 3, 4, ctx);
		Node b = new NCLeafiaInput().selectItem("Graph Bounds", 5, 6, ctx);
		helper.assertTrue(a instanceof NodeGraphAdd na && na.ctrl == g && a.posX == 3 && a.posY == 4, "selecting Graph Add makes one bound to the graph at the click");
		helper.assertTrue(b instanceof NodeGraphBounds nb && nb.ctrl == g && b.posX == 5, "and Graph Bounds");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the boxed label

	@GameTest(template = EMPTY)
	public static void theBoxedLabelReadsItsConfigurationWithinTheEditorsRanges(GameTestHelper helper) {
		setup();
		Drawing d = (Drawing) ControlRegistry.getNew("leafia_drawing_box", null);
		helper.assertTrue(near(d.getWidth(), 0.2F) && near(d.getHeight(), 0.2F) && d.getScale() == 25 && d.getText().isEmpty() && !d.isWhiteText(), "the defaults");
		float[] c = d.getColor();
		helper.assertTrue(near(c[0], 1) && near(c[1], 1) && near(c[2], 1), "a 100% colour is white");

		apply(d, "red", 50, "green", 25, "blue", 0, "width", 3.5F, "height", 1.25F, "scale", 40, "text", "REACTOR 1", "white", 1);
		c = d.getColor();
		helper.assertTrue(near(c[0], 0.5F) && near(c[1], 0.25F) && near(c[2], 0), "50/25/0 per cent");
		helper.assertTrue(near(d.getWidth(), 3.5F) && near(d.getHeight(), 1.25F) && d.getScale() == 40 && d.getText().equals("REACTOR 1") && d.isWhiteText(), "size, scale, text, white");

		apply(d, "red", 500, "green", -5, "width", 5000, "height", 0, "scale", 1, "white", 0.99F);
		c = d.getColor();
		helper.assertTrue(near(c[0], 1) && near(c[1], 0), "colour clamps to 0..100");
		helper.assertTrue(near(d.getWidth(), 10) && near(d.getHeight(), 0.1F) && d.getScale() == 10 && !d.isWhiteText(), "10 wide, 0.1 high, scale 10, and white needs 1");
		apply(d, "width", Float.NaN, "scale", Float.NaN);
		helper.assertTrue(near(d.getWidth(), 10) && d.getScale() == 10, "NaN changes nothing");
		helper.assertTrue(Float.isNaN(d.getConfigs().get("width").getNumber()), "the raw value is kept");

		helper.assertTrue(d.receiveNodeMap.isEmpty() && d.getOutEvents().isEmpty(), "it listens for nothing and sends nothing");
		d.populateDefaultNodes(List.of());
		helper.assertTrue(d.receiveNodeMap.isEmpty(), "and has no default graph");

		// it saves and loads with its configuration (a NaN width is saved raw and reads as the fresh control's 0.2)
		apply(d, "width", 7, "scale", 10);
		ControlPanel panel = hostedPanel(helper);
		Control back = panel.readControl(d.writeToNBT(new CompoundTag()));
		helper.assertTrue(back instanceof Drawing b && b.getText().equals("REACTOR 1") && near(b.getWidth(), 7) && b.isWhiteText() == d.isWhiteText() && b.getScale() == 10, "round trip");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the levers

	@GameTest(template = EMPTY)
	public static void aLeversClickMovesItsPositionByTenAndTheHandleFollows(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		LeverHorizontal lever = add(panel, "leafia_lever_h", 0, 0);
		helper.assertTrue(v(lever, "position") == 50 && near(lever.getCurLevel(), 0.5F), "position 50, drawn at 0.5 (Leafia's mismatch, kept)");

		press(panel, lever, false);
		helper.assertTrue(v(lever, "position") == 60, "a click is +10");
		press(panel, lever, true);
		press(panel, lever, true);
		helper.assertTrue(v(lever, "position") == 40, "a sneaking click is -10, twice: 40");
		for(int i = 0; i < 6; i++)
			press(panel, lever, false);
		helper.assertTrue(v(lever, "position") == 100 + 0, "six clicks up from 40 is 100");
		press(panel, lever, false);
		helper.assertTrue(v(lever, "position") == 110, "the graph itself does not clamp: 110 until the tick");
		tick(panel, lever);
		helper.assertTrue(v(lever, "position") == 100, "the tick clamps it into 0..100");

		// the handle swings toward it by at most 200/7 a tick: from 0.5 to 100 in four ticks (28.57 each)
		float inc = LeverBase.INCREMENT;
		helper.assertTrue(near(inc, 200 / 7F), "increment is 200/7");
		helper.assertTrue(near(lever.getCurLevel(), 0.5F + inc), "one tick: " + lever.getCurLevel());
		tick(panel, lever);
		tick(panel, lever);
		helper.assertTrue(near(lever.getCurLevel(), 0.5F + 3 * inc), "three ticks");
		tick(panel, lever);
		helper.assertTrue(near(lever.getCurLevel(), 100), "and it arrives, without overshooting, on the fourth");

		// and back down
		lever.vars.put("position", new DataValueFloat(0));
		tick(panel, lever);
		helper.assertTrue(near(lever.getCurLevel(), 100 - inc), "down by one step");
		for(int i = 0; i < 5; i++)
			tick(panel, lever);
		helper.assertTrue(near(lever.getCurLevel(), 0) && v(lever, "position") == 0, "to the bottom stop");

		// below zero is clamped; NaN reads as 0
		lever.vars.put("position", new DataValueFloat(-30));
		tick(panel, lever);
		helper.assertTrue(v(lever, "position") == 0, "-30 is 0");
		lever.vars.put("position", new DataValueFloat(Float.NaN));
		tick(panel, lever);
		helper.assertTrue(v(lever, "position") == 0, "NaN is 0 (Leafia's left NaN there for ever)");

		// the vertical lever is the same machine
		LeverVertical up = add(panel, "leafia_lever_v", 4, 0);
		press(panel, up, false);
		helper.assertTrue(v(up, "position") == 60, "the vertical lever clicks the same way");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aLeversSetCurLevelIsClampedAndIgnoresNaN(GameTestHelper helper) {
		setup();
		LeverVertical lever = (LeverVertical) ControlRegistry.getNew("leafia_lever_v", null);
		lever.setCurLevel(70);
		helper.assertTrue(lever.getCurLevel() == 70, "set");
		lever.setCurLevel(Float.NaN);
		helper.assertTrue(lever.getCurLevel() == 70, "NaN ignored");
		lever.setCurLevel(1e9F);
		helper.assertTrue(lever.getCurLevel() == 100, "clamped high");
		lever.setCurLevel(-1e9F);
		helper.assertTrue(lever.getCurLevel() == 0, "and low");
		LargeSwitch sw = (LargeSwitch) ControlRegistry.getNew("leafia_sbswitch", null);
		sw.setCurLevel(Float.POSITIVE_INFINITY);
		helper.assertTrue(sw.getCurLevel() == 100, "the switch's too");
		sw.setCurLevel(Float.NaN);
		helper.assertTrue(sw.getCurLevel() == 100, "NaN ignored there too");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the large switch and button

	@GameTest(template = EMPTY)
	public static void theLargeSwitchFlipsBetweenItsStopsAndSoundsOnEveryClick(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		LargeSwitch sw = add(panel, "leafia_sbswitch", 0, 0);
		helper.assertTrue(v(sw, "position") == 0 && near(sw.getCurLevel(), 50), "position 0, drawn at the middle");
		BlockPos at = panel.parent.getControlPos();

		List<Played> played = capture(() -> press(panel, sw, false));
		helper.assertTrue(v(sw, "position") == 100, "a click flips 0 to 100");
		helper.assertTrue(played.size() == 1, "and plays one sound, got " + played);
		Played p = played.get(0);
		helper.assertTrue(p.id.toString().equals("leafia:external.sbwallswitch") && p.source == SoundSource.BLOCKS && near(p.volume, 0.5F) && near(p.pitch, 1)
				&& p.x == at.getX() + 0.5 && p.y == at.getY() + 0.5 && p.z == at.getZ() + 0.5, "the wall switch's sound at 0.5 volume at the panel, got " + p);
		press(panel, sw, true);
		helper.assertTrue(v(sw, "position") == 0, "a second click, sneaking or not, flips it back: 100 - 100");

		// the handle: from 50 up to 100 in two ticks (28.57 then the rest), without overshooting
		sw.vars.put("position", new DataValueFloat(100));
		tick(panel, sw);
		helper.assertTrue(near(sw.getCurLevel(), 50 + LargeSwitch.INCREMENT), "one step: " + sw.getCurLevel());
		tick(panel, sw);
		helper.assertTrue(near(sw.getCurLevel(), 100), "then there");
		sw.vars.put("position", new DataValueFloat(0));
		tick(panel, sw);
		tick(panel, sw);
		tick(panel, sw);
		tick(panel, sw);
		helper.assertTrue(near(sw.getCurLevel(), 0), "and back to 0 in four");
		sw.vars.put("position", new DataValueFloat(250));
		tick(panel, sw);
		helper.assertTrue(v(sw, "position") == 100, "a position past the stop is clamped to it");
		sw.vars.put("position", new DataValueFloat(Float.NaN));
		tick(panel, sw);
		helper.assertTrue(v(sw, "position") == 0, "NaN is 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLargeButtonLightsPushesAndLetsGoAfterFortyFiveTicks(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		LargeButton btn = add(panel, "leafia_sbbutton", 0, 0);
		helper.assertTrue(v(btn, "isPushed") == 0 && v(btn, "isLit") == 0, "up and dark");

		List<Played> played = capture(() -> press(panel, btn, false));
		helper.assertTrue(v(btn, "isPushed") == 1 && v(btn, "isLit") == 1, "a click pushes and lights it");
		helper.assertTrue(played.size() == 1 && played.get(0).id.toString().equals("leafia:external.sbwallbutton") && near(played.get(0).volume, 0.5F) && near(played.get(0).pitch, 1),
				"with the wall button's sound at 0.5, pitch 1, got " + played);

		int released = -1;
		for(int t = 1; t <= 60 && released < 0; t++) {
			tick(panel, btn);
			if(v(btn, "isPushed") == 0)
				released = t;
		}
		helper.assertTrue(released == LargeButton.HOLD_TICKS + 1, "it lets go on tick 46 (the buffer counts 45 and answers on the next), got " + released);
		helper.assertTrue(v(btn, "isLit") == 0, "and the lamp goes dark with it");

		// the stock encased button forgets its lamp across a reload (D-3064); this one does not
		press(panel, btn, false);
		Control reloaded = panel.cloneControl(btn);
		panel.controls.add(reloaded);
		press(panel, reloaded, false);
		helper.assertTrue(v(reloaded, "isPushed") == 1 && v(reloaded, "isLit") == 1, "after a save and load a click still lights it (the literal rides a Math node)");
		helper.assertTrue(LargeButton.HOLD_TICKS == 45, "the hold is 45");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void everyInstrumentsDefaultGraphsSurviveTheTrustBoundaryAndAReload(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		int n = 0;
		for(Inst i : INSTRUMENTS) {
			Control c = add(panel, i.key, 10 * n++, 0);
			helper.assertTrue(com.leafia.contents.machines.controlpanel.core.ControlPanelGuard.graphProblem(c) == null, i.key + " passes the cycle and chain guard");
			Control clone = panel.cloneControl(c);
			helper.assertTrue(clone.getClass() == c.getClass(), i.key + " survives a save and load as itself, not a placeholder");
			helper.assertTrue(clone.receiveNodeMap.keySet().equals(c.receiveNodeMap.keySet()), i.key + " keeps its graphs");
		}
		// only these have a graph at all
		helper.assertTrue(ControlRegistry.getNew("leafia_graph", panel).receiveNodeMap.isEmpty() && panel.controls.get(3).receiveNodeMap.isEmpty(), "the chart and the label have none");
		helper.assertTrue(panel.controls.get(0).receiveNodeMap.keySet().equals(java.util.Set.of("ctrl_press")), "the switch has a press graph");
		helper.assertTrue(panel.controls.get(1).receiveNodeMap.keySet().equals(java.util.Set.of("ctrl_press", "tick")), "the button a press and a tick graph");
		helper.assertTrue(panel.controls.get(4).receiveNodeMap.keySet().equals(java.util.Set.of("ctrl_press")), "and the lever a press graph");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the sync packets

	/** The packet as it travels: through the real framing, opened, and decoded by the registered prototype. */
	private static Consumer<IPayloadContext> viaWire(com.leafia.dev.custompacket.LeafiaCustomPacketEncoder packet) {
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
		var opened = LeafiaCustomPacket.open(wire);
		if(opened.prototype() == null)
			return null;
		return opened.prototype().decode(opened.buf());
	}

	@GameTest(template = EMPTY)
	public static void theSyncPacketsAreRegisteredServerToClientOnly(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_GRAPH_SYNC) instanceof Graph.CCPGraphSyncPacket, "the graph's");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_LEVER_SYNC) instanceof LeverBase.CCPLeverSyncPacket, "the lever's");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_LARGE_SWITCH_SYNC) instanceof LargeSwitch.CCPLargeSwitchSyncPacket, "the switch's");
		helper.assertTrue(LeafiaPacketTypes.CCP_GRAPH_SYNC != LeafiaPacketTypes.CCP_LEVER_SYNC && LeafiaPacketTypes.CCP_LEVER_SYNC != LeafiaPacketTypes.CCP_LARGE_SWITCH_SYNC, "three ids");
		for(int id : new int[]{LeafiaPacketTypes.CCP_GRAPH_SYNC, LeafiaPacketTypes.CCP_LEVER_SYNC, LeafiaPacketTypes.CCP_LARGE_SWITCH_SYNC})
			helper.assertTrue(!LeafiaPacketTypes.byId(id).acceptsServerbound(), "type " + id + " is not a packet a client may send");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anUnchangedSyncPacketIsResentOnlyEveryTwentyTicks(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		LeverHorizontal lever = add(te.panel, "leafia_lever_h", 10, 0);
		lever.setCurLevel(30F);
		Object sig = lever.syncPacket().signature();
		long t = 1000;
		helper.assertTrue(com.leafia.contents.machines.controlpanel.CCPSyncPacketBase.due(lever, sig, t), "the first packet goes");
		helper.assertTrue(!com.leafia.contents.machines.controlpanel.CCPSyncPacketBase.due(lever, lever.syncPacket().signature(), t + 1), "the same state next tick does not (D-3074)");
		helper.assertTrue(!com.leafia.contents.machines.controlpanel.CCPSyncPacketBase.due(lever, lever.syncPacket().signature(), t + 19), "nor 19 ticks on");
		helper.assertTrue(com.leafia.contents.machines.controlpanel.CCPSyncPacketBase.due(lever, lever.syncPacket().signature(), t + 20), "but 20 ticks on it does, for a player who has just walked into range");
		lever.setCurLevel(31F);
		helper.assertTrue(com.leafia.contents.machines.controlpanel.CCPSyncPacketBase.due(lever, lever.syncPacket().signature(), t + 21), "a change goes at once");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aGraphLeverAndSwitchStateTravelsToTheClientCopy(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		Graph g = add(te.panel, "leafia_graph", 0, 0);
		LeverHorizontal lever = add(te.panel, "leafia_lever_h", 10, 0);
		LargeSwitch sw = add(te.panel, "leafia_sbswitch", 20, 0);
		ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
		var client = LeafiaControlPanelKit.context(player, PacketFlow.CLIENTBOUND);

		// the graph
		for(int i = 0; i < 40; i++)
			g.pushValue(i * 0.5);
		var packet = g.syncPacket();
		helper.assertTrue(packet.pos.equals(te.getBlockPos()) && packet.idx == 0, "it names its panel and its place on it");
		double[] sent = g.values.clone();
		var cb = viaWire(packet); // the samples are written when the packet is built for the wire, so clear the copy afterwards
		helper.assertTrue(cb != null, "the graph's packet decodes");
		g.values = new double[40];
		cb.accept(client);
		helper.assertTrue(java.util.Arrays.equals(g.values, sent), "the client copy has the samples that were sent");

		// the lever, index 1
		lever.setCurLevel(63.5F);
		var lp = lever.syncPacket();
		helper.assertTrue(lp.idx == 1, "the lever is control 1");
		lever.setCurLevel(1);
		viaWire(lp).accept(client);
		helper.assertTrue(lever.getCurLevel() == 63.5F, "its drawn angle arrives");

		// the switch, index 2
		sw.setCurLevel(12.25F);
		var sp = sw.syncPacket();
		sw.setCurLevel(99);
		viaWire(sp).accept(client);
		helper.assertTrue(sw.getCurLevel() == 12.25F && lever.getCurLevel() == 63.5F, "and the switch's, touching nothing else");

		// a packet touches only the control it names
		g.pushValue(77);
		var again = g.syncPacket();
		LeverBase.CCPLeverSyncPacket wrongKind = new LeverBase.CCPLeverSyncPacket(te.getBlockPos(), 0, 5);
		viaWire(wrongKind).accept(client);
		helper.assertTrue(g.values[39] == 77 && lever.getCurLevel() == 63.5F, "a lever packet aimed at the graph's slot does nothing");
		helper.assertTrue(again.pos.equals(te.getBlockPos()), "sanity");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aHostileInstrumentSyncPacketIsRefused(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		Graph g = add(te.panel, "leafia_graph", 0, 0);
		LeverHorizontal lever = add(te.panel, "leafia_lever_h", 10, 0);
		g.pushValue(3);
		lever.setCurLevel(20);
		double[] gBefore = g.values.clone();
		ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
		var client = LeafiaControlPanelKit.context(player, PacketFlow.CLIENTBOUND);
		var server = LeafiaControlPanelKit.context(player, PacketFlow.SERVERBOUND);
		BlockPos abs = te.getBlockPos();

		// 1. a client sending one: dropped by the hub before it is decoded
		LeverBase.CCPLeverSyncPacket honest = new LeverBase.CCPLeverSyncPacket(abs, 1, 99);
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(honest), server);
		helper.assertTrue(lever.getCurLevel() == 20, "a lever packet arriving serverbound moves nothing");
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(g.syncPacket()), server);
		helper.assertTrue(java.util.Arrays.equals(g.values, gBefore), "nor a graph's");
		// the same bytes the other way round do apply, so the refusal above is the direction and not a broken packet
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(honest), client);
		helper.assertTrue(lever.getCurLevel() == 99, "arriving clientbound the same packet applies");
		lever.setCurLevel(20);

		// 2. even handed a serverbound context, the callback refuses (the gate is not the only gate)
		viaWire(honest).accept(server);
		helper.assertTrue(lever.getCurLevel() == 20, "a callback run with a serverbound context does nothing");

		// 3. targets that are not the instrument: a past-the-end, negative and huge index, another kind of control, no panel, an unloaded position
		BlockPos stone = abs.above(3);
		helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		BlockPos far = new BlockPos(2_000_000, 64, 2_000_000);
		for(BlockPos pos : new BlockPos[]{abs, stone, far, abs.west(20)}) {
			for(int idx : new int[]{2, 3, -1, 32767, -32768}) {
				viaWire(new LeverBase.CCPLeverSyncPacket(pos, idx, 77)).accept(client);
				viaWire(new Graph.CCPGraphSyncPacket(pos, idx, g)).accept(client);
			}
		}
		viaWire(new LeverBase.CCPLeverSyncPacket(abs, 0, 77)).accept(client); // index 0 is the graph, not a lever
		viaWire(new Graph.CCPGraphSyncPacket(abs, 1, g)).accept(client);      // index 1 is a lever, not a graph
		helper.assertTrue(lever.getCurLevel() == 20 && java.util.Arrays.equals(g.values, gBefore), "none of them changed anything");
		helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());

		// 4. a level that is not a number: NaN is ignored, the rest clamped
		viaWire(new LeverBase.CCPLeverSyncPacket(abs, 1, Float.NaN)).accept(client);
		helper.assertTrue(lever.getCurLevel() == 20, "a NaN angle is ignored");
		viaWire(new LeverBase.CCPLeverSyncPacket(abs, 1, 1e30F)).accept(client);
		helper.assertTrue(lever.getCurLevel() == 100, "and a huge one is the end stop");

		// 5. a graph packet whose count is outside 1..200 keeps nothing, and a count of 200 is the most it keeps
		for(int count : new int[]{0, 201, 32767, -1}) {
			LeafiaBuf buf = new LeafiaBuf(io.netty.buffer.Unpooled.buffer());
			buf.writeVec3i(abs);
			buf.writeShort(0);
			buf.writeShort(count);
			for(int i = 0; i < Math.min(Math.max(count, 0), 300); i++)
				buf.writeFloat(123F);
			buf.resetReaderIndex();
			Consumer<IPayloadContext> cb = new Graph.CCPGraphSyncPacket().decode(buf);
			if(cb != null)
				cb.accept(client);
			helper.assertTrue(cb == null && java.util.Arrays.equals(g.values, gBefore), "a count of " + count + " is dropped before it is read");
		}
		LeafiaBuf full = new LeafiaBuf(io.netty.buffer.Unpooled.buffer());
		full.writeVec3i(abs);
		full.writeShort(0);
		full.writeShort(CCPSyncPacketBase.MAX_SAMPLES);
		for(int i = 0; i < CCPSyncPacketBase.MAX_SAMPLES; i++)
			full.writeFloat(i);
		full.resetReaderIndex();
		new Graph.CCPGraphSyncPacket().decode(full).accept(client);
		helper.assertTrue(g.values.length == 200 && g.values[199] == 199, "exactly 200 is kept");

		// 6. the prototype is not written to by a decode (it is shared by every packet that arrives)
		LeverBase.CCPLeverSyncPacket proto = (LeverBase.CCPLeverSyncPacket) LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_LEVER_SYNC);
		BlockPos before = proto.pos;
		int idxBefore = proto.idx;
		viaWire(honest);
		helper.assertTrue(proto.pos == before && proto.idx == idxBefore, "decoding leaves the registered prototype untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSyncSendHelperNeedsARealPanelInARealLevel(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		Graph g = add(te.panel, "leafia_graph", 0, 0);
		helper.assertTrue(CCPSyncPacketBase.sendFor(g, g.syncPacket()), "a graph on a placed panel sends");
		helper.assertTrue(!CCPSyncPacketBase.sendFor(null, g.syncPacket()), "no control, nothing");
		Graph loose = (Graph) ControlRegistry.getNew("leafia_graph", null);
		helper.assertTrue(!CCPSyncPacketBase.sendFor(loose, new Graph.CCPGraphSyncPacket(BlockPos.ZERO, 0, loose)), "a control on no panel sends nothing");
		ControlPanel noParent = new ControlPanel(null);
		Graph orphan = add(noParent, "leafia_graph", 0, 0);
		helper.assertTrue(!CCPSyncPacketBase.sendFor(orphan, new Graph.CCPGraphSyncPacket(BlockPos.ZERO, 0, orphan)), "a panel with no host sends nothing");
		helper.assertTrue(!CCPSyncPacketBase.sendFor(g, null), "no packet, nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the trust boundary, with the new controls

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aHostilePanelOfInstrumentsIsClampedOrRefusedByTheServer(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

		// all six, honestly made, are taken
		CompoundTag honest = LeafiaControlPanelKit.edit(te, panel -> {
			int n = 0;
			for(Inst i : INSTRUMENTS) {
				Control c = ControlRegistry.getNew(i.key, panel);
				c.posX = 8 * n++;
				c.posY = 0;
				c.populateDefaultNodes(editorEvents(c));
				panel.controls.add(c);
			}
		});
		helper.assertTrue(te.applyFullSet(player, honest) == null, "a panel holding all six is accepted");
		helper.assertTrue(te.panel.controls.size() == 6 && te.panel.controls.get(2) instanceof Graph && te.panel.controls.get(5) instanceof LeverVertical, "and read back as six instruments");
		helper.assertTrue(te.panel.writeToNBT(new CompoundTag()).getCompound("controls").size() == 6, "all six saved");

		// a graph asking for two billion segments is taken and held to the editor's 200
		CompoundTag huge = LeafiaControlPanelKit.edit(te, panel -> {
			Graph c = (Graph) ControlRegistry.getNew("leafia_graph", panel);
			Map<String, DataValue> cfg = new LinkedHashMap<>(c.getConfigs());
			cfg.put("segments", new DataValueFloat(2_000_000_000F));
			cfg.put("verticalLineSpan", new DataValueFloat(0));
			cfg.put("width", new DataValueFloat(1e30F));
			c.applyConfigs(cfg);
			c.populateDefaultNodes(List.of());
			panel.controls.add(c);
		});
		long t0 = System.nanoTime();
		helper.assertTrue(te.applyFullSet(player, huge) == null, "the guard has no opinion on a number it cannot judge");
		Graph held = (Graph) te.panel.controls.get(0);
		helper.assertTrue(held.getSegments() == 200 && held.values.length == 200 && held.getVerticalLineSpan() == 1 && near(held.getWidth(), 10),
				"but the server's control is the editor's 200 segments, 10 wide, got " + held.getSegments());
		helper.assertTrue(System.nanoTime() - t0 < 2_000_000_000L, "and it did not allocate its way to a stall");

		// a graph carrying far more samples than it has segments is taken (the guard counts compounds and lists, not scalars; the
		// packet's own 2 MiB ceiling bounds it) and the server's control ignores them: what it saves and sends on is its own forty zeros
		CompoundTag flood = LeafiaControlPanelKit.edit(te, panel -> {
			Graph c = (Graph) ControlRegistry.getNew("leafia_graph", panel);
			panel.controls.add(c);
		});
		ListTag floats = new ListTag();
		for(int i = 0; i < 310_000; i++)
			floats.add(net.minecraft.nbt.FloatTag.valueOf(1F));
		flood.getCompound("controls").getCompound("control0").put("graphData", floats);
		helper.assertTrue(te.applyFullSet(player, flood) == null, "three hundred thousand samples for forty segments are not a reason to refuse an edit");
		Graph flooded = (Graph) te.panel.controls.get(0);
		helper.assertTrue(flooded.values.length == 40 && flooded.values[0] == 0 && flooded.values[39] == 0, "but the control holds forty zeros");
		helper.assertTrue(te.panel.writeToNBT(new CompoundTag()).getCompound("controls").getCompound("control0").getList("graphData", 5).size() == 40,
				"and what it saves and sends to the other players is forty samples, not the flood");

		CompoundTag before = te.panel.writeToNBT(new CompoundTag());
		ControlPanel panelBefore = te.panel;

		// a hostile body for a Graph Add node: a cycle is the guard's, and it applies to these nodes like any other
		CompoundTag cycle = LeafiaControlPanelKit.edit(te, panel -> {
			Graph c = (Graph) ControlRegistry.getNew("leafia_graph", panel);
			panel.controls.add(c);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
			NodeGraphBounds a = new NodeGraphBounds(0, 0, c);
			com.leafia.contents.machines.controlpanel.core.nodes.NodeMath m = new com.leafia.contents.machines.controlpanel.core.nodes.NodeMath(50, 0);
			sys.addNode(a);
			sys.addNode(m);
			m.inputs.get(0).setData(m, 0, true);
		});
		NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), cycle), LeafiaControlPanelKit.context(player));
		helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a node feeding itself in a graph's graph is refused as anywhere else");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the assets, and no client type on the server

	private static String resource(String path) throws IOException {
		try(InputStream in = LeafiaControlPanelInstrumentTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null)
				throw new IOException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static boolean exists(String path) {
		try(InputStream in = LeafiaControlPanelInstrumentTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			return in != null;
		} catch(IOException e) {
			return false;
		}
	}

	@GameTest(template = EMPTY)
	public static void theInstrumentModelsAreTheGeneratedPartsOfLeafiasObjs(GameTestHelper helper) throws Exception {
		setup();
		Map<String, List<String>> expected = new LinkedHashMap<>();
		expected.put(AddonInstrumentModels.LARGE_SWITCH, List.of("Plane", "Plane.001", "Plane.002", "Cube", "Switch"));
		expected.put(AddonInstrumentModels.LARGE_BUTTON, List.of("Plane", "Plane.001", "Plane.002", "Circle", "Circle.001"));
		expected.put(AddonInstrumentModels.LEVER, List.of("Base", "Lever", "Base.001"));
		Map<String, List<String>> table = ControlPanelParts.all();
		for(Map.Entry<String, List<String>> e : expected.entrySet()) {
			helper.assertTrue(table.containsKey(e.getKey()) && table.get(e.getKey()).equals(e.getValue()), e.getKey() + " is in the part table with Leafia's objects, got " + table.get(e.getKey()));
			List<String> objects = new ArrayList<>();
			for(String line : resource("models/blocks/ccp_" + e.getKey() + ".obj").split("\n"))
				if(line.startsWith("o "))
					objects.add(line.substring(2).trim());
			helper.assertTrue(new java.util.HashSet<>(objects).equals(new java.util.HashSet<>(e.getValue())), e.getKey() + ": the OBJ holds " + objects);
			helper.assertTrue(resource("models/blocks/ccp_" + e.getKey() + ".obj").contains("usemtl"), e.getKey() + " has a material, or it bakes to nothing");
			for(String part : e.getValue()) {
				String name = ControlPanelParts.modelName(e.getKey(), part);
				helper.assertTrue(name.equals(name.toLowerCase(java.util.Locale.ROOT)) && name.startsWith("ccp_" + e.getKey() + "_"), name + " is a lower-case resource name");
				JsonObject json = JsonParser.parseString(resource("models/block/" + name + ".json")).getAsJsonObject();
				helper.assertTrue(json.get("model").getAsString().equals("leafia:models/blocks/ccp_" + e.getKey() + ".obj"), name + " points at its OBJ");
				JsonObject hidden = json.getAsJsonObject("visibility");
				for(String o : objects)
					helper.assertTrue(hidden.has(o) == !o.equals(part), name + ": " + o + (o.equals(part) ? " is shown" : " is hidden"));
				String tex = json.getAsJsonObject("textures").get("0").getAsString();
				helper.assertTrue(tex.equals(e.getKey().equals("lever") ? "leafia:block/ccp/white" : "leafia:block/ccp/" + AddonInstrumentModels.PALETTE_TEXTURE), name + " wears " + tex);
				helper.assertTrue(exists("textures/" + tex.substring("leafia:".length()) + ".png"), "the texture " + tex + " ships");
			}
		}
		// Blender's capitals and dots survive only in the visibility keys
		helper.assertTrue(ControlPanelParts.modelName("lever", "Base.001").equals("ccp_lever_base.001") && exists("models/block/ccp_lever_base.001.json"), "Base.001 is ccp_lever_base.001");
		helper.assertTrue(ControlPanelParts.modelName("button_push", "btn_top").equals("ccp_button_push_btn_top"), "and the stock names are as they were");
		String manifest = resource("control_panel_models.txt");
		for(Map.Entry<String, List<String>> e : expected.entrySet())
			for(String part : e.getValue())
				helper.assertTrue(manifest.contains(ControlPanelParts.modelName(e.getKey(), part) + "\n"), "the generator lists " + ControlPanelParts.modelName(e.getKey(), part));
		helper.succeed();
	}

	private static final String ROOT = "com/leafia/contents/machines/controlpanel";

	private static byte[] classBytes(String internalName) throws Exception {
		try(InputStream in = LeafiaControlPanelInstrumentTests.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	}

	private static boolean mentionsClient(byte[] bytes) {
		String s = new String(bytes, StandardCharsets.ISO_8859_1);
		return s.contains("net/minecraft/client") || s.contains("com/mojang/blaze3d") || s.contains("org/lwjgl") || s.contains("com/mojang/realmsclient")
				|| s.contains(ROOT + "/core/client") || s.contains(ROOT + "/instruments/client");
	}

	@GameTest(template = EMPTY)
	public static void noInstrumentClassOnTheServerNamesAClientType(GameTestHelper helper) throws Exception {
		// positive controls first: the scan must flag a client class, and a client class of this batch
		byte[] known = classBytes("com/leafia/client/LeafiaClient");
		helper.assertTrue(known != null && mentionsClient(known), "the scanner flags LeafiaClient");
		byte[] draw = classBytes(ROOT + "/instruments/client/GraphDraw");
		helper.assertTrue(draw != null && mentionsClient(draw), "and this batch's own graph renderer");

		List<String> names = new ArrayList<>();
		names.add(ROOT + "/CCPSyncPacketBase");
		names.add(ROOT + "/nodes/NodeGraphAdd");
		names.add(ROOT + "/nodes/NodeGraphBounds");
		URL url = LeafiaControlPanelInstrumentTests.class.getClassLoader().getResource(ROOT + "/instruments");
		if(url != null) {
			Path root = Paths.get(url.toURI());
			try(Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					if(!rel.startsWith("client/"))
						names.add(ROOT + "/instruments/" + rel.substring(0, rel.length() - ".class".length()));
				});
			}
		}
		helper.assertTrue(names.size() >= 14, "the instrument classes were found, got " + names.size());
		for(String internal : names) {
			byte[] bytes = classBytes(internal);
			helper.assertTrue(bytes != null, "class file found: " + internal);
			helper.assertTrue(!mentionsClient(bytes), internal + " names a client class, a dedicated server would fail to load it");
			if(!internal.contains("$"))
				Class.forName(internal.replace('/', '.'), true, LeafiaControlPanelInstrumentTests.class.getClassLoader());
		}
		helper.succeed();
	}
}
