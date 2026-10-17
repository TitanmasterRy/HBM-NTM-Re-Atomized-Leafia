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
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.hbm.blocks.ModBlocks;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSetup;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.instruments.AddonControlType;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.horizontal.MeterHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical.MeterVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.nixie.NixieDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.rbmk.RBMKDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.textindicator.TextIndicator;
import com.leafia.contents.machines.panel.controltorch.ControlTorchBlock;
import com.leafia.contents.machines.panel.controltorch.ControlTorchTE;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.eventbuses.LeafiaComponentTooltips;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Leafia's last five instruments of the Custom Control Panel and the control panel torch (CCP-INSTRUMENTS-2, D-3072): the horizontal and
 * vertical meters, the nixie tube display, the text indicator and the RBMK display, and the torch a panel switches.
 *
 * <h2>What this cannot do, stated first</h2>
 * Nothing here draws (a GameTest server cannot load a client class): the five renderers, the five panes, the nixie's hole in the slab and
 * the RBMK map are the client check in D-3072. What it can pin is everything they are made from: every size, variable and configuration
 * against Leafia's constructors (typed from the source, not read back), the value-to-picture mappings the renderers call
 * ({@code fraction}, {@code needleX}, {@code label}, {@code getAngleForRatio}, {@code tubeTexts}, {@code glow}, {@code columnPos},
 * {@code columnColor}, {@code dotColor}), the two eased instruments' tick, the two sync packets round-tripped through the real framing and
 * then attacked, the clamps a crafted panel meets, the torch through the real event path and a reload, and the sledge, tooltip,
 * assembler and asset clauses of section 1.G.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelInstrumentTwoTests {

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

	@SuppressWarnings("unchecked")
	private static <T extends Control> T add(ControlPanel panel, String key, float x, float y) {
		Control c = ControlRegistry.getNew(key, panel);
		c.posX = x;
		c.posY = y;
		c.populateDefaultNodes(new ArrayList<>());
		panel.controls.add(c);
		return (T) c;
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

	/** Applies configuration entries over a control's defaults, the way the editor and a panel load do. */
	private static void apply(Control c, Object... kv) {
		Map<String, DataValue> m = new LinkedHashMap<>(c.getConfigs());
		for(int i = 0; i < kv.length; i += 2)
			m.put((String) kv[i], kv[i + 1] instanceof String s ? new DataValueString(s) : new DataValueFloat(((Number) kv[i + 1]).floatValue()));
		c.applyConfigs(m);
	}

	private static void setValue(Control c, float v) {
		c.vars.put("value", new DataValueFloat(v));
	}

	private static void tick(ControlPanel panel, Control c) {
		panel.receiveDirectEvent(c, ControlEvent.newEvent("tick"));
	}

	/** The packet as it travels: through the real framing, opened, and decoded by the registered prototype. */
	private static Consumer<IPayloadContext> viaWire(com.leafia.dev.custompacket.LeafiaCustomPacketEncoder packet) {
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
		var opened = LeafiaCustomPacket.open(wire);
		if(opened.prototype() == null)
			return null;
		return opened.prototype().decode(opened.buf());
	}

	// ------------------------------------------------------------------ the registry

	private record Inst(String key, String display, Class<? extends Control> type, ControlType family, float[] size) {}

	private static final List<Inst> INSTRUMENTS = List.of(
			new Inst("leafia_nixie", "Nixie Tube", NixieDisplay.class, ControlType.DISPLAY, new float[]{1.75F, 2.5F, 0.0625F}),
			new Inst("leafia_textindicator", "Text Indicator", TextIndicator.class, ControlType.INDICATOR, new float[]{4.5F, 1.8F, 0.25F}),
			new Inst("leafia_rbmk", "RBMK Display", RBMKDisplay.class, AddonControlType.RBMK_DISPLAY, new float[]{1, 1, 0.1F}),
			new Inst("leafia_meter_h", "Horizontal Meter", MeterHorizontal.class, ControlType.METER, new float[]{5, 1, 0.01F}),
			new Inst("leafia_meter_v", "Vertical Meter", MeterVertical.class, ControlType.METER, new float[]{1.7F, 5, 1.25F}));

	private static final Map<String, List<String>> VARS = new LinkedHashMap<>();
	private static final Map<String, List<String>> CONFIGS = new LinkedHashMap<>();

	static {
		VARS.put("leafia_nixie", List.of("value=s:"));
		VARS.put("leafia_textindicator", List.of("isLit=f:0.0"));
		VARS.put("leafia_rbmk", List.of());
		VARS.put("leafia_meter_h", List.of("value=f:0.0"));
		VARS.put("leafia_meter_v", List.of("value=f:0.0"));
		CONFIGS.put("leafia_nixie", List.of("length=f:1.0"));
		CONFIGS.put("leafia_textindicator", List.of("red=f:255.0", "green=f:0.0", "blue=f:0.0", "text=s:A3-5"));
		CONFIGS.put("leafia_rbmk", List.of("width=f:1.0", "height=f:1.0", "offsetU=f:0.0", "offsetV=f:0.0"));
		CONFIGS.put("leafia_meter_h", List.of("length=f:5.0", "r=f:255.0", "g=f:153.0", "b=f:0.0", "minValue=f:0.0", "maxValue=f:5.0", "divisions=f:6.0",
				"subdivisions=f:5.0", "decimals=f:0.0"));
		CONFIGS.put("leafia_meter_v", List.of("needleOffset=f:0.0", "minValue=f:-1.0", "maxValue=f:1.0", "decimals=f:1.0", "divisions=f:20.0",
				"subdivisions=f:3.0", "clamp=f:0.0"));
	}

	@GameTest(template = EMPTY)
	public static void allElevenInstrumentsAreRegisteredAndTheFiveNewOnesArePinned(GameTestHelper helper) {
		setup();
		List<String> keys = new ArrayList<>(ControlRegistry.registry.keySet());
		List<String> have = new ArrayList<>();
		for(String k : keys)
			if(k.startsWith("leafia_"))
				have.add(k);
		helper.assertTrue(have.equals(List.of("leafia_nixie", "leafia_sbswitch", "leafia_sbbutton", "leafia_textindicator", "leafia_rbmk", "leafia_graph",
				"leafia_meter_h", "leafia_meter_v", "leafia_drawing_box", "leafia_lever_h", "leafia_lever_v")), "Leafia's eleven, in his order, got " + have);

		for(Inst i : INSTRUMENTS) {
			Control proto = ControlRegistry.registry.get(i.key);
			helper.assertTrue(proto.getClass() == i.type && proto.name.equals(i.display) && proto.registryName.equals(i.key), i.key + " is the " + i.display);
			helper.assertTrue(proto.panel == null, "a prototype belongs to no panel");
			helper.assertTrue(proto.getControlType() == i.family, i.key + " is in the " + i.family.name + " family");
			float[] size = proto.getSize();
			helper.assertTrue(size.length == 3 && near(size[0], i.size[0]) && near(size[1], i.size[1]) && near(size[2], i.size[2]),
					i.key + " is " + java.util.Arrays.toString(i.size) + ", got " + java.util.Arrays.toString(size));
			helper.assertTrue(proto.getOutEvents().isEmpty(), i.key + " sends nothing");
			helper.assertTrue(proto.getInEvents().equals(List.of("tick", "initialize", "redstone_input")), i.key + " listens for CE's three");
			helper.assertTrue(proto.getBoundingBox() == null, i.key + " has no collision box");
			helper.assertTrue(proto.newControl(null).getClass() == i.type && proto.newControl(null) != proto, i.key + " makes a new one of its own class");

			Control c = ControlRegistry.getNew(i.key, null);
			helper.assertTrue(describe(c.vars).equals(VARS.get(i.key)), i.key + " variables, got " + describe(c.vars));
			helper.assertTrue(describe(c.getConfigs()).equals(CONFIGS.get(i.key)), i.key + " configuration, got " + describe(c.getConfigs()));
			helper.assertTrue(c.receiveNodeMap.isEmpty(), i.key + " has no default graph");
		}
		helper.assertTrue(ControlRegistry.getAllControlsOfType(ControlType.METER).equals(List.of("leafia_meter_h", "leafia_meter_v")), "the Meter family holds the two meters");
		helper.assertTrue(ControlRegistry.getAllControlsOfType(AddonControlType.RBMK_DISPLAY).equals(List.of("leafia_rbmk")), "and the RBMK family its display");
		helper.assertTrue(ControlRegistry.getAllControlsOfType(ControlType.DISPLAY).contains("leafia_nixie") && ControlRegistry.getAllControlsOfType(ControlType.INDICATOR).contains("leafia_textindicator"),
				"the nixie sits with the displays, the text indicator with the indicators");

		// a second registration changes nothing
		int before = ControlRegistry.addonControls.size();
		com.leafia.contents.machines.controlpanel.instruments.AddonInstrumentRegister.register();
		ControlPanelSetup.commonSetup();
		helper.assertTrue(ControlRegistry.addonControls.size() == before && before >= 11, "a second registration adds nothing, and eleven are there");
		helper.assertTrue(new ArrayList<>(ControlRegistry.registry.keySet()).equals(keys), "nor to the registry");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the horizontal meter

	@GameTest(template = EMPTY)
	public static void theHorizontalMeterMapsAValueToTheNeedleAndTheScale(GameTestHelper helper) {
		setup();
		MeterHorizontal m = (MeterHorizontal) ControlRegistry.getNew("leafia_meter_h", null);
		// 0 to 5, five units long: the needle runs from 0.4 to 4.6
		helper.assertTrue(near(m.fraction(2.5F), 0.5F) && near(m.fraction(0), 0) && near(m.fraction(5), 1), "the middle of the scale is half");
		helper.assertTrue(near(m.fraction(-100), 0) && near(m.fraction(100), 1), "a value off either end is held at the end");
		helper.assertTrue(near(m.fraction(Float.NaN), 0) && near(m.fraction(Float.POSITIVE_INFINITY), 1), "a NaN is the start, infinity the end");
		setValue(m, 1);
		helper.assertTrue(near(m.fraction(), 0.2F), "the variable is what the picture reads");
		helper.assertTrue(near(m.needleX(0), 0.4F) && near(m.needleX(1), 4.6F) && near(m.needleX(0.5F), 2.5F), "the needle's x runs 0.4 to length - 0.4");
		apply(m, "length", 10);
		helper.assertTrue(near(m.needleX(1), 9.6F) && near(m.needleX(0.5F), 5F), "and follows the length");

		// a reversed range
		apply(m, "minValue", 5, "maxValue", 0);
		helper.assertTrue(near(m.fraction(5), 0) && near(m.fraction(0), 1) && near(m.fraction(1), 0.8F), "a reversed scale runs backwards");
		// Leafia's quirk, kept: an empty range answers the bound itself where a ratio belongs
		apply(m, "minValue", 3, "maxValue", 3);
		helper.assertTrue(MeterHorizontal.ratio(3, 3, 99) == 3 && near(m.fraction(99), 1), "ratio of an empty range is the bound: 3, which is held at 1");
		apply(m, "minValue", -3, "maxValue", -3);
		helper.assertTrue(near(m.fraction(99), 0), "and a negative bound is held at 0");

		// the labels, one under each labelled mark
		apply(m, "minValue", 0, "maxValue", 5, "divisions", 6, "decimals", 0);
		List<String> labels = new ArrayList<>();
		for(int i = 0; i < m.getDivisions(); i++)
			labels.add(m.label(i));
		helper.assertTrue(labels.equals(List.of("0", "1", "2", "3", "4", "5")), "six labels 0 to 5, got " + labels);
		apply(m, "decimals", 2, "divisions", 3, "minValue", -1, "maxValue", 1);
		helper.assertTrue(m.label(0).equals("-1.00") && m.label(1).equals("0.00") && m.label(2).equals("1.00"), "decimals and a negative start, got " + m.label(0) + " " + m.label(1) + " " + m.label(2));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHorizontalMetersConfigurationIsClampedToTheEditorsRangesAndKeepsTheRaw(GameTestHelper helper) {
		setup();
		MeterHorizontal m = (MeterHorizontal) ControlRegistry.getNew("leafia_meter_h", null);
		apply(m, "length", 99, "r", 999, "g", -5, "b", 12.7F, "divisions", 1, "subdivisions", 500, "decimals", 99999);
		helper.assertTrue(m.getLength() == 10 && m.getRed() == 255 && m.getGreen() == 0 && m.getBlue() == 12, "length 3..10, colour 0..255, got " + m.getLength() + " " + m.getRed() + " " + m.getGreen() + " " + m.getBlue());
		helper.assertTrue(m.getDivisions() == 2 && m.getSubdivisions() == 10 && m.getDecimals() == 3, "divisions 2..20 (1 divided the scale by zero), subdivisions 0..10, decimals 0..3");
		helper.assertTrue(m.label(0).equals("0.000"), "a huge decimals reaches String.format as 3, got " + m.label(0));
		apply(m, "length", 1, "divisions", -7);
		helper.assertTrue(m.getLength() == 3 && m.getDivisions() == 2, "and the other end");
		apply(m, "length", Float.NaN, "minValue", Float.NaN, "maxValue", Float.POSITIVE_INFINITY);
		helper.assertTrue(m.getLength() == 3 && m.getMin() == 0 && m.getMax() == 5, "NaN and infinity change nothing");
		helper.assertTrue(m.getConfigs().get("divisions").getNumber() == -7 && m.getConfigs().get("decimals").getNumber() == 99999F, "the map keeps what was written, so a panel round-trips");
		m.posX = 3;
		m.posY = 4;
		apply(m, "length", 8);
		float[] box = m.getBox();
		helper.assertTrue(near(box[0], 3) && near(box[1], 4) && near(box[2], 11) && near(box[3], 5), "the box is as long as the meter, one unit tall, got " + java.util.Arrays.toString(box));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the vertical meter

	@GameTest(template = EMPTY)
	public static void theVerticalMetersDrumEasesTowardTheValueOnTheTickAndArrives(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		MeterVertical m = add(panel, "leafia_meter_v", 0, 0);
		// 21 marks (20 divisions) over -1..1: the middle of the scale is the drum's rest, the ends are -/+ 360 / 21 x 10
		helper.assertTrue(near(m.getAngleForRatio(0.5F), 0) && near(m.getAngleForRatio(1), -360 / 21f * 0.5F * 20) && near(m.getAngleForRatio(0), 360 / 21f * 0.5F * 20),
				"the drum's angle for the middle and the two ends");
		helper.assertTrue(near(m.desiredAngle(), 0), "a value of 0 on -1..1 asks for the rest");
		setValue(m, 1);
		float want = 360 / 21f * 0.5F * 20;
		helper.assertTrue(near(m.desiredAngle(), want), "a value at the top asks for " + want + ", got " + m.desiredAngle());
		tick(panel, m);
		helper.assertTrue(near(m.getCurAngle(), want / 2.5F), "the first tick moves a 2.5th of the way, got " + m.getCurAngle());
		float last = m.getCurAngle();
		boolean arrived = false;
		for(int i = 0; i < 60 && !arrived; i++) {
			tick(panel, m);
			helper.assertTrue(m.getCurAngle() > last || m.getCurAngle() == want, "it only ever moves toward the target");
			last = m.getCurAngle();
			arrived = m.getCurAngle() == want;
		}
		helper.assertTrue(arrived, "it arrives exactly within sixty ticks, at " + last);
		// the last stretch is a steady two degrees a tick, and within two it lands
		m.setCurAngle(want - 4F);
		tick(panel, m);
		helper.assertTrue(near(m.getCurAngle(), want - 2F), "four out: the threshold's two degrees a tick");
		m.setCurAngle(want - 1.5F);
		tick(panel, m);
		helper.assertTrue(m.getCurAngle() == want, "inside the threshold it lands");
		// the other way
		setValue(m, -1);
		tick(panel, m);
		helper.assertTrue(m.getCurAngle() < want && m.getCurAngle() > -want, "turned back toward the other end");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theVerticalMetersClampNeedleOffsetAndBadValuesAreAllHandled(GameTestHelper helper) {
		setup();
		MeterVertical m = (MeterVertical) ControlRegistry.getNew("leafia_meter_v", null);
		setValue(m, 5);
		float unclamped = m.desiredAngle();
		apply(m, "clamp", 1);
		helper.assertTrue(m.isClamp() && near(m.desiredAngle(), -m.getAngleForRatio(1)) && Math.abs(unclamped) > Math.abs(m.desiredAngle()), "clamp holds a value at the scale's end, unclamped it asks for more");
		apply(m, "minValue", 1, "maxValue", -1);
		setValue(m, 9);
		helper.assertTrue(near(m.effectiveValue(), 1), "clamp works with the bounds either way round");
		setValue(m, Float.NaN);
		helper.assertTrue(m.effectiveValue() == m.getMin() && !Float.isNaN(m.desiredAngle()), "a value that is not a number is the scale's start (Leafia's turned the drum to NaN for good)");
		apply(m, "needleOffset", 77, "divisions", 0, "subdivisions", -3, "decimals", 42);
		helper.assertTrue(m.getNeedleOffset() == 3 && m.getDivisions() == 5 && m.getSubdivisions() == 0 && m.getDecimals() == 3, "needle -3..3, divisions 5..30, subdivisions 0..10, decimals 0..3");
		apply(m, "needleOffset", -77, "divisions", 999);
		helper.assertTrue(m.getNeedleOffset() == -3 && m.getDivisions() == 30, "and the other ends");
		helper.assertTrue(near(MeterVertical.NEEDLE_STEP, 11.25F), "a needle step is 360 / 32");
		// the angle a packet sets
		m.setCurAngle(Float.NaN);
		helper.assertTrue(m.getCurAngle() == 0, "a NaN angle is ignored");
		m.setCurAngle(1e30F);
		helper.assertTrue(m.getCurAngle() == MeterVertical.MAX_ANGLE, "a huge one is the stop");
		m.setCurAngle(-1e30F);
		helper.assertTrue(m.getCurAngle() == -MeterVertical.MAX_ANGLE, "either way");
		// the labels run round the drum, one per division
		apply(m, "minValue", -1, "maxValue", 1, "divisions", 10, "decimals", 1);
		helper.assertTrue(m.label(0).equals("-1.0") && m.label(5).equals("0.0") && m.label(10).equals("1.0"), "eleven labels over a drum of ten divisions");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the nixie display

	@GameTest(template = EMPTY)
	public static void theNixieShowsOneCharacterPerTubeAndItsBoxFollowsItsLength(GameTestHelper helper) {
		setup();
		NixieDisplay n = (NixieDisplay) ControlRegistry.getNew("leafia_nixie", null);
		helper.assertTrue(n.getLength() == 1 && java.util.Arrays.equals(n.getTubeTexts(), new String[]{""}), "one dark tube to start with");
		apply(n, "length", 3);
		n.vars.put("value", new DataValueString("12345"));
		helper.assertTrue(java.util.Arrays.equals(n.getTubeTexts(), new String[]{"1", "2", "3"}), "three tubes show the first three characters");
		apply(n, "length", 5);
		n.vars.put("value", new DataValueString("12"));
		helper.assertTrue(java.util.Arrays.equals(n.getTubeTexts(), new String[]{"1", "2", "", "", ""}), "a tube past the end of the text is dark");
		n.vars.put("value", new DataValueFloat(7));
		helper.assertTrue(n.getTubeTexts()[0].equals("7") && n.getTubeTexts()[1].equals(".") && n.getTubeTexts()[2].equals("0"), "a number shows as its own text, '7.0'");
		helper.assertTrue(java.util.Arrays.equals(NixieDisplay.tubeTexts("AB", 0), new String[0]) && NixieDisplay.tubeTexts("AB", -4).length == 0, "no tubes, no characters");

		// the box: 1.75 for the first tube, a unit for each more
		n.posX = 2;
		n.posY = 3;
		for(int length = 0; length <= 8; length++) {
			apply(n, "length", length);
			float[] box = n.getBox();
			helper.assertTrue(near(box[0], 2) && near(box[1], 3) && near(box[2], 2 + 1.75F + length - 1) && near(box[3], 5.5F), "length " + length + " box, got " + java.util.Arrays.toString(box));
		}
		// the editor's 0..8, and the raw kept
		apply(n, "length", 100);
		helper.assertTrue(n.getLength() == 8 && n.getTubeTexts().length == 8, "a crafted 100 tubes is 8");
		apply(n, "length", -3);
		helper.assertTrue(n.getLength() == 0, "-3 is none");
		apply(n, "length", Float.NaN);
		helper.assertTrue(n.getLength() == 0 && Float.isNaN(n.getConfigs().get("length").getNumber()), "NaN changes nothing, and the map keeps it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the text indicator

	@GameTest(template = EMPTY)
	public static void theTextIndicatorsGlowSwellsAndFadesOverThreeTicks(GameTestHelper helper) {
		setup();
		ControlPanel panel = hostedPanel(helper);
		TextIndicator t = add(panel, "leafia_textindicator", 0, 0);
		helper.assertTrue(t.getCurrent() == 0 && t.glow() == 0, "dark to start with");
		t.vars.put("isLit", new DataValueFloat(1));
		int[] want = {1, 2, 3, 3, 3};
		for(int w : want) {
			tick(panel, t);
			helper.assertTrue(t.getCurrent() == w, "lit: the glow step is " + w + ", got " + t.getCurrent());
		}
		helper.assertTrue(near(t.glow(), 1), "fully lit glows at 1");
		t.setCurrent(1);
		helper.assertTrue(near(t.glow(), (float) Math.sqrt(1 / 3.0)) && near((float) Math.pow(2 / 3.0, 0.5), (float) (t.glow() * Math.sqrt(2))), "the glow is the square root of the fraction");
		t.setCurrent(3);
		t.vars.put("isLit", new DataValueFloat(0));
		for(int w : new int[]{2, 1, 0, 0}) {
			tick(panel, t);
			helper.assertTrue(t.getCurrent() == w, "unlit: the step is " + w + ", got " + t.getCurrent());
		}
		helper.assertTrue(t.glow() == 0, "and out");
		t.setCurrent(50);
		helper.assertTrue(t.getCurrent() == TextIndicator.DURATION, "a step past the top is the top");
		t.setCurrent(-9);
		helper.assertTrue(t.getCurrent() == 0, "and below zero is zero");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theTextIndicatorsColourIsClampedAndItsTextIsKept(GameTestHelper helper) {
		setup();
		TextIndicator t = (TextIndicator) ControlRegistry.getNew("leafia_textindicator", null);
		helper.assertTrue(t.getRed() == 255 && t.getGreen() == 0 && t.getBlue() == 0 && t.getText().equals("A3-5"), "red, A3-5 to start with");
		apply(t, "red", 300, "green", -5, "blue", 128.5F, "text", "HIGH TEMP");
		helper.assertTrue(t.getRed() == 255 && t.getGreen() == 0 && t.getBlue() == 128.5F && t.getText().equals("HIGH TEMP"), "colour 0..255, the text as given");
		apply(t, "red", Float.NaN);
		helper.assertTrue(t.getRed() == 255, "a NaN leaves the colour");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the RBMK display

	@GameTest(template = EMPTY)
	public static void theRbmkDisplaysConfigurationAndLinkErrorsArePinned(GameTestHelper helper) {
		setup();
		RBMKDisplay d = (RBMKDisplay) ControlRegistry.getNew("leafia_rbmk", null);
		helper.assertTrue(d.getMapWidth() == 1 && d.getMapHeight() == 1 && d.getOffsetU() == 0 && d.getOffsetV() == 0, "1 x 1, no offset");
		apply(d, "width", 15, "height", 9, "offsetU", 1, "offsetV", 0);
		helper.assertTrue(d.getMapWidth() == 15 && d.getMapHeight() == 9 && d.getOffsetU() == 1 && d.getOffsetV() == 0, "15 x 9, shifted in u");
		apply(d, "width", 0, "height", 9999);
		helper.assertTrue(d.getMapWidth() == 1 && d.getMapHeight() == 27, "1..27: the drawing visits width x height columns a frame");
		apply(d, "width", Float.NaN);
		helper.assertTrue(d.getMapWidth() == 1, "a NaN changes nothing");
		helper.assertTrue(d.getBox()[2] - d.getBox()[0] == 1, "the box is one unit whatever the map's size");

		// the link: none, one, two
		helper.assertTrue("RBMK$not linked".equals(d.linkError()) && d.center() == null, "no link reads 'not linked'");
		d.taggedLinks.put("a", new BlockPos(4, 5, 6));
		helper.assertTrue(d.linkError() == null && d.center().equals(new BlockPos(4, 5, 6)), "one link is the map's centre");
		d.taggedLinks.put("b", new BlockPos(7, 8, 9));
		helper.assertTrue("Must have only$1 position$linked".equals(d.linkError()) && d.center() == null, "two links are an error");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRbmkDisplaysGridAsksTheWorldForTheRightColumns(GameTestHelper helper) {
		BlockPos c = new BlockPos(10, 20, 30);
		// a 1 x 1 map is the linked column itself, whichever way the panel faces
		for(Direction f : Direction.Plane.HORIZONTAL)
			helper.assertTrue(RBMKDisplay.columnPos(c, f, 1, 1, 0, 0).equals(c), "1 x 1 is the centre facing " + f);
		// 3 x 3 facing north (the panel looks north): the top row (y = 0) is one block south of the centre... Leafia's offset(facing, -startY - y)
		// with startY = -1: rows run from facing * +1 (y = 0) to facing * -1 (y = 2); columns along the clockwise neighbour from -1 to +1
		Direction n = Direction.NORTH;
		helper.assertTrue(RBMKDisplay.columnPos(c, n, 3, 3, 1, 1).equals(c), "the middle cell is the centre");
		helper.assertTrue(RBMKDisplay.columnPos(c, n, 3, 3, 0, 0).equals(c.relative(n, 1).relative(n.getClockWise(), -1)), "cell (0, 0)");
		helper.assertTrue(RBMKDisplay.columnPos(c, n, 3, 3, 2, 2).equals(c.relative(n, -1).relative(n.getClockWise(), 1)), "cell (2, 2)");
		helper.assertTrue(RBMKDisplay.columnPos(c, n, 3, 3, 2, 0).equals(c.relative(n, 1).relative(n.getClockWise(), 1)), "cell (2, 0)");
		// an even size reaches a block further one way (floor of -(size-1)/2): 2 x 2 spans -1..0
		helper.assertTrue(RBMKDisplay.columnPos(c, n, 2, 2, 0, 0).equals(c.relative(n, 1).relative(n.getClockWise(), -1)) && RBMKDisplay.columnPos(c, n, 2, 2, 1, 1).equals(c),
				"a 2 x 2 map has the centre in its lower right cell");
		// every cell of a 5 x 5 is a different block, and they fill a 5 x 5 square
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		for(Direction f : Direction.Plane.HORIZONTAL) {
			seen.clear();
			for(int x = 0; x < 5; x++)
				for(int y = 0; y < 5; y++)
					seen.add(RBMKDisplay.columnPos(c, f, 5, 5, x, y));
			helper.assertTrue(seen.size() == 25, "25 distinct columns facing " + f);
			for(BlockPos p : seen)
				helper.assertTrue(Math.abs(p.getX() - c.getX()) <= 2 && Math.abs(p.getZ() - c.getZ()) <= 2 && p.getY() == c.getY(), "inside the square facing " + f);
		}
		// where the cell's picture is on the panel
		helper.assertTrue(RBMKDisplay.cellX(1, 0) == 0 && RBMKDisplay.cellX(3, 0) == -1 && RBMKDisplay.cellX(3, 2) == 1 && RBMKDisplay.cellX(4, 0) == -1.5F && RBMKDisplay.cellY(4, 3) == 1.5F,
				"a cell is a unit apart and the map is centred on the control");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRbmkDisplaysColoursAndDotsAreUpstreams(GameTestHelper helper) {
		CompoundTag none = new CompoundTag();
		// heat: a grey that reddens
		float[] cold = RBMKDisplay.columnColor(ColumnType.BLANK, none, 0, 1000);
		helper.assertTrue(near(cold[0], 0.65F) && near(cold[1], 0.65F) && near(cold[2], 0.65F), "cold is 0.65 grey");
		float[] hot = RBMKDisplay.columnColor(ColumnType.FUEL, none, 1000, 1000);
		helper.assertTrue(near(hot[0], 1) && near(hot[1], 0.65F) && near(hot[2], 0.65F), "at the limit it is red over 0.65");
		float[] half = RBMKDisplay.columnColor(ColumnType.MODERATOR, none, 500, 1000);
		helper.assertTrue(near(half[0], 0.65F + 0.35F * 0.5F), "half heat is half the red");
		helper.assertTrue(near(RBMKDisplay.columnColor(ColumnType.BLANK, none, 5000, 1000)[0], 1), "hotter than the limit is held at 1");
		helper.assertTrue(near(RBMKDisplay.columnColor(ColumnType.BLANK, none, 500, 0)[0], 0.65F) && near(RBMKDisplay.columnColor(ColumnType.BLANK, none, Double.NaN, 1000)[0], 0.65F),
				"no limit, or a NaN heat, is the cold grey");
		// the control rods' colour groups
		float[][] groups = {{1, 0, 0}, {1, 1, 0}, {0, 0.5F, 0}, {0, 0, 1}, {0.5F, 0, 1}};
		for(int g = 0; g < 5; g++) {
			CompoundTag t = new CompoundTag();
			t.putShort("color", (short) g);
			float[] col = RBMKDisplay.columnColor(ColumnType.CONTROL, t, 900, 1000);
			helper.assertTrue(near(col[0], groups[g][0]) && near(col[1], groups[g][1]) && near(col[2], groups[g][2]), "colour group " + g);
		}
		CompoundTag past = new CompoundTag();
		past.putShort("color", (short) 9);
		float[] white = RBMKDisplay.columnColor(ColumnType.CONTROL, past, 0, 1000);
		helper.assertTrue(white[0] == 1 && white[1] == 1 && white[2] == 1, "a group past the five is white, as Leafia's chain of ifs left it");
		CompoundTag unset = new CompoundTag();
		unset.putShort("color", (short) -1);
		helper.assertTrue(near(RBMKDisplay.columnColor(ColumnType.CONTROL, unset, 0, 1000)[1], 0.65F), "an unset colour is the heat grey");
		helper.assertTrue(near(RBMKDisplay.columnColor(ColumnType.CONTROL_AUTO, none, 0, 1000)[1], 0.65F), "an automatic rod has no group");
		// a colour on a column that is not a control rod does not count
		helper.assertTrue(near(RBMKDisplay.columnColor(ColumnType.FUEL, past, 0, 1000)[1], 0.65F) && near(RBMKDisplay.columnColor(ColumnType.FUEL, unset, 0, 1000)[1], 0.65F), "only a control rod has a group");

		// the dots
		CompoundTag fuel = new CompoundTag();
		fuel.putDouble("enrichment", 0.5);
		float[] dot = RBMKDisplay.dotColor(ColumnType.FUEL, fuel);
		helper.assertTrue(dot[0] == 0 && near(dot[1], 0.25F + 0.375F) && dot[2] == 0, "fuel: green, 0.25 + 0.75 x enrichment");
		helper.assertTrue(near(RBMKDisplay.dotColor(ColumnType.FUEL_SIM, none)[1], 0.25F), "a fuel channel with no enrichment known is the dimmest dot");
		CompoundTag level = new CompoundTag();
		level.putDouble("level", 0.75);
		float[] manual = RBMKDisplay.dotColor(ColumnType.CONTROL, level);
		float[] auto = RBMKDisplay.dotColor(ColumnType.CONTROL_AUTO, level);
		helper.assertTrue(near(manual[0], 0.75F) && near(manual[1], 0.75F) && manual[2] == 0, "a manual rod: (level, level, 0)");
		helper.assertTrue(near(auto[0], 0.75F) && auto[1] == 0 && near(auto[2], 0.75F), "an automatic one: (level, 0, level)");
		for(ColumnType type : ColumnType.values()) {
			boolean has = type == ColumnType.FUEL || type == ColumnType.FUEL_SIM || type == ColumnType.CONTROL || type == ColumnType.CONTROL_AUTO;
			helper.assertTrue((RBMKDisplay.dotColor(type, level) != null) == has, type + (has ? " has a dot" : " has none"));
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the two sync packets

	@GameTest(template = EMPTY)
	public static void theTwoNewSyncPacketsAreRegisteredServerToClientOnly(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_METER_V_SYNC) instanceof MeterVertical.CCPMeterVerticalSyncPacket, "the vertical meter's");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_INDICATOR_SYNC) instanceof TextIndicator.CCPTextIndicatorSyncPacket, "the text indicator's");
		helper.assertTrue(LeafiaPacketTypes.CCP_METER_V_SYNC == LeafiaPacketTypes.CCP_LARGE_SWITCH_SYNC + 1 && LeafiaPacketTypes.CCP_INDICATOR_SYNC == LeafiaPacketTypes.CCP_METER_V_SYNC + 1,
				"appended after batch 11's three, no id moved");
		helper.assertTrue(LeafiaPacketTypes.count() == 41, "forty-one types");
		for(int id : new int[]{LeafiaPacketTypes.CCP_METER_V_SYNC, LeafiaPacketTypes.CCP_INDICATOR_SYNC})
			helper.assertTrue(!LeafiaPacketTypes.byId(id).acceptsServerbound(), "type " + id + " is not a packet a client may send");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aMeterAndAnIndicatorStateTravelToTheClientCopyAndHostilePacketsAreRefused(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		MeterVertical meter = add(te.panel, "leafia_meter_v", 0, 0);
		TextIndicator indicator = add(te.panel, "leafia_textindicator", 10, 0);
		NixieDisplay other = add(te.panel, "leafia_nixie", 20, 0);
		ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
		var client = LeafiaControlPanelKit.context(player, PacketFlow.CLIENTBOUND);
		var server = LeafiaControlPanelKit.context(player, PacketFlow.SERVERBOUND);
		BlockPos abs = te.getBlockPos();

		meter.setCurAngle(123.5F);
		var mp = meter.syncPacket();
		helper.assertTrue(mp.pos.equals(abs) && mp.idx == 0, "the meter names its panel and its place on it");
		meter.setCurAngle(1);
		viaWire(mp).accept(client);
		helper.assertTrue(meter.getCurAngle() == 123.5F, "its drum's angle arrives");

		indicator.setCurrent(2);
		var ip = indicator.syncPacket();
		helper.assertTrue(ip.idx == 1, "the indicator is control 1");
		indicator.setCurrent(0);
		viaWire(ip).accept(client);
		helper.assertTrue(indicator.getCurrent() == 2 && meter.getCurAngle() == 123.5F, "and its glow step, touching nothing else");

		// 1. a client sending one: dropped by the hub before it is decoded
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 0, 999)), server);
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(new TextIndicator.CCPTextIndicatorSyncPacket(abs, 1, 0)), server);
		helper.assertTrue(meter.getCurAngle() == 123.5F && indicator.getCurrent() == 2, "arriving serverbound they move nothing");
		LeafiaCustomPacket.handle(LeafiaCustomPacket.__start(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 0, 999)), client);
		helper.assertTrue(meter.getCurAngle() == 999, "the same bytes the other way round apply");
		meter.setCurAngle(123.5F);
		// 2. a callback handed a serverbound context refuses (the gate is not the only gate)
		viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 0, 5)).accept(server);
		viaWire(new TextIndicator.CCPTextIndicatorSyncPacket(abs, 1, 0)).accept(server);
		helper.assertTrue(meter.getCurAngle() == 123.5F && indicator.getCurrent() == 2, "a callback run with a serverbound context does nothing");

		// 3. targets that are not the instrument
		BlockPos stone = abs.above(3);
		helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		BlockPos far = new BlockPos(2_000_000, 64, 2_000_000);
		for(BlockPos pos : new BlockPos[]{abs, stone, far, abs.west(20)}) {
			for(int idx : new int[]{3, 4, -1, 32767, -32768}) {
				viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(pos, idx, 77)).accept(client);
				viaWire(new TextIndicator.CCPTextIndicatorSyncPacket(pos, idx, 0)).accept(client);
			}
		}
		viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 1, 77)).accept(client); // index 1 is the indicator
		viaWire(new TextIndicator.CCPTextIndicatorSyncPacket(abs, 0, 0)).accept(client);  // index 0 is the meter
		viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 2, 77)).accept(client);  // index 2 is a nixie
		helper.assertTrue(meter.getCurAngle() == 123.5F && indicator.getCurrent() == 2 && other.getLength() == 1, "none of them changed anything");
		helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());

		// 4. values that are not what a server sends
		viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 0, Float.NaN)).accept(client);
		helper.assertTrue(meter.getCurAngle() == 123.5F, "a NaN angle is ignored");
		viaWire(new MeterVertical.CCPMeterVerticalSyncPacket(abs, 0, 1e30F)).accept(client);
		helper.assertTrue(meter.getCurAngle() == MeterVertical.MAX_ANGLE, "a huge one is the stop");
		for(int step : new int[]{100, 127, -128, -1}) {
			indicator.setCurrent(1);
			viaWire(new TextIndicator.CCPTextIndicatorSyncPacket(abs, 1, step)).accept(client);
			helper.assertTrue(indicator.getCurrent() == (step > 0 ? 3 : 0), "a step of " + step + " is held to 0..3, got " + indicator.getCurrent());
		}

		// 5. the prototype is not written to by a decode
		MeterVertical.CCPMeterVerticalSyncPacket proto = (MeterVertical.CCPMeterVerticalSyncPacket) LeafiaPacketTypes.byId(LeafiaPacketTypes.CCP_METER_V_SYNC);
		BlockPos before = proto.pos;
		int idxBefore = proto.idx;
		viaWire(mp);
		helper.assertTrue(proto.pos == before && proto.idx == idxBefore, "decoding leaves the registered prototype untouched");

		// 6. a truncated packet decodes to nothing harmful
		LeafiaBuf buf = new LeafiaBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeVec3i(abs);
		buf.writeShort(0);
		buf.resetReaderIndex();
		boolean threw = false;
		try {
			new MeterVertical.CCPMeterVerticalSyncPacket().decode(buf);
		} catch(RuntimeException e) {
			threw = true; // the framing's own bounds; the point is that nothing was applied
		}
		helper.assertTrue(threw || meter.getCurAngle() == MeterVertical.MAX_ANGLE, "a short packet changes nothing");
		helper.assertTrue(CCPSyncPacketBase.sendFor(meter, meter.syncPacket()) && CCPSyncPacketBase.sendFor(indicator, indicator.syncPacket()), "both send from a placed panel");
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aHostilePanelOfTheNewInstrumentsIsClampedByTheServer(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

		CompoundTag honest = LeafiaControlPanelKit.edit(te, panel -> {
			int n = 0;
			for(Inst i : INSTRUMENTS) {
				Control c = ControlRegistry.getNew(i.key, panel);
				c.posX = 8 * n++;
				c.posY = 0;
				c.populateDefaultNodes(new ArrayList<>());
				panel.controls.add(c);
			}
		});
		helper.assertTrue(te.applyFullSet(player, honest) == null, "a panel holding all five is accepted");
		helper.assertTrue(te.panel.controls.size() == 5 && te.panel.controls.get(0) instanceof NixieDisplay && te.panel.controls.get(4) instanceof MeterVertical, "and read back as five instruments");

		CompoundTag hostile = LeafiaControlPanelKit.edit(te, panel -> {
			MeterHorizontal h = (MeterHorizontal) ControlRegistry.getNew("leafia_meter_h", panel);
			Map<String, DataValue> cfg = new LinkedHashMap<>(h.getConfigs());
			cfg.put("length", new DataValueFloat(1e30F));
			cfg.put("divisions", new DataValueFloat(0));
			cfg.put("decimals", new DataValueFloat(2_000_000_000F));
			h.applyConfigs(cfg);
			panel.controls.add(h);
			NixieDisplay n = (NixieDisplay) ControlRegistry.getNew("leafia_nixie", panel);
			n.posX = 10;
			Map<String, DataValue> nc = new LinkedHashMap<>(n.getConfigs());
			nc.put("length", new DataValueFloat(2_000_000_000F));
			n.applyConfigs(nc);
			panel.controls.add(n);
			RBMKDisplay r = (RBMKDisplay) ControlRegistry.getNew("leafia_rbmk", panel);
			r.posX = 20;
			Map<String, DataValue> rc = new LinkedHashMap<>(r.getConfigs());
			rc.put("width", new DataValueFloat(2_000_000_000F));
			rc.put("height", new DataValueFloat(-2_000_000_000F));
			r.applyConfigs(rc);
			panel.controls.add(r);
		});
		helper.assertTrue(te.applyFullSet(player, hostile) == null, "the guard has no opinion on numbers it cannot judge");
		MeterHorizontal h = (MeterHorizontal) te.panel.controls.get(0);
		NixieDisplay n = (NixieDisplay) te.panel.controls.get(1);
		RBMKDisplay r = (RBMKDisplay) te.panel.controls.get(2);
		helper.assertTrue(h.getLength() == 10 && h.getDivisions() == 2 && h.getDecimals() == 3 && h.label(0).equals("0.000"), "the meter is the editor's 10, 2 and 3");
		helper.assertTrue(n.getLength() == 8 && n.getTubeTexts().length == 8, "the nixie is the editor's 8 tubes");
		helper.assertTrue(r.getMapWidth() == 27 && r.getMapHeight() == 1, "the RBMK map is 27 x 1 at most");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the control torch

	private static BlockState torch(boolean lit, Direction facing) {
		return (lit ? LeafiaBlocks.control_torch : LeafiaBlocks.control_torch_unlit).get().defaultBlockState().setValue(ControlTorchBlock.FACING, facing);
	}

	private static void send(GameTestHelper helper, BlockPos at, Object isOn) {
		ControlEvent evt = ControlEvent.newEvent("torch_set_state");
		if(isOn instanceof Number num)
			evt.setVar("isOn", num.floatValue());
		ControlEventSystem.get(helper.getLevel()).broadcastEvent(helper.absolutePos(new BlockPos(5, 1, 5)), evt, helper.absolutePos(at));
	}

	@GameTest(template = EMPTY)
	public static void theControlTorchExistsInBothFormsWithUpstreamsProperties(GameTestHelper helper) {
		ControlTorchBlock lit = LeafiaBlocks.control_torch.get();
		ControlTorchBlock unlit = LeafiaBlocks.control_torch_unlit.get();
		helper.assertTrue(lit.isOn() && !unlit.isOn(), "lit and unlit");
		helper.assertTrue(lit.defaultBlockState().getLightEmission() == 7 && unlit.defaultBlockState().getLightEmission() == 0, "the lit one gives light 7 (0.5 x 15), the unlit none");
		helper.assertTrue(lit.getDescriptionId().equals("block.leafia.control_torch") && unlit.getDescriptionId().equals("block.leafia.control_torch"), "both are 'Control Panel Torch'");
		helper.assertTrue(lit.defaultBlockState().getValue(ControlTorchBlock.FACING) == Direction.UP, "a torch stands up by default");
		helper.assertTrue(lit.defaultBlockState().getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 0, "it breaks at once");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("leafia", "control_torch_unlit")), "the unlit torch is an item");
		helper.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("leafia", "control_torch")), "the lit one is not");
		var type = LeafiaBlockEntities.CONTROL_TORCH.get();
		helper.assertTrue(type.isValid(lit.defaultBlockState()) && type.isValid(unlit.defaultBlockState()), "one block entity type serves both");
		helper.assertTrue(unlit.getCloneItemStack(helper.getLevel(), BlockPos.ZERO, lit.defaultBlockState()).is(unlit.asItem()), "the lit one picks as the unlit");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlTorchFollowsTorchSetStateAndKeepsItsFacing(GameTestHelper helper) {
		setup();
		for(Direction facing : new Direction[]{Direction.UP, Direction.EAST, Direction.NORTH}) {
			helper.setBlock(AT, torch(false, facing));
			BlockEntity be = helper.getBlockEntity(AT);
			helper.assertTrue(be instanceof ControlTorchTE te && !te.isOn(), facing + ": an unlit torch has its block entity, dark");
			IControllable c = (IControllable) be;
			helper.assertTrue(c.getInEvents().equals(List.of("torch_set_state")) && c.getOutEvents().isEmpty(), "one in-event, no out-events");
			helper.assertTrue(describe(c.getQueryData()).equals(List.of("isOn=f:0.0")), "isOn reads 0");
			helper.assertTrue(ControlEventSystem.get(helper.getLevel()).resolve(helper.absolutePos(AT)) != null, "the system finds it");

			send(helper, AT, 1);
			helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch.get() && helper.getBlockState(AT).getValue(ControlTorchBlock.FACING) == facing,
					facing + ": isOn 1 lights it, facing kept, got " + helper.getBlockState(AT));
			IControllable after = (IControllable) helper.getBlockEntity(AT);
			helper.assertTrue(after instanceof ControlTorchTE lit && lit.isOn() && describe(after.getQueryData()).equals(List.of("isOn=f:1.0")), "the new block entity reads 1");

			send(helper, AT, 1);
			helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch.get(), "lighting a lit torch changes nothing");
			send(helper, AT, 0.5F);
			helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch_unlit.get() && helper.getBlockState(AT).getValue(ControlTorchBlock.FACING) == facing,
					"anything under 1 puts it out, facing kept");
			send(helper, AT, 0);
			send(helper, AT, Float.NaN);
			helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch_unlit.get(), "a NaN changes nothing");
			send(helper, AT, null);
			helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch_unlit.get(), "an event with no isOn changes nothing (Leafia's threw)");
			helper.setBlock(AT, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlTorchSurvivesAReloadAndALitOneStaysLit(GameTestHelper helper) {
		setup();
		helper.setBlock(AT, torch(true, Direction.WEST));
		ControlTorchTE be = (ControlTorchTE) helper.getBlockEntity(AT);
		helper.assertTrue(be.isOn(), "a lit torch placed by a world load reads lit before any event, with no flag to reconcile");
		CompoundTag saved = be.saveWithFullMetadata(helper.getLevel().registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), helper.getBlockState(AT), saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded instanceof ControlTorchTE lt && lt.isOn(), "rebuilt from its save it is the same torch, lit");
		helper.assertTrue(((IControllable) loaded).getQueryData().get("isOn").getNumber() == 1, "and answers the panel as such");
		BlockState unlitState = torch(false, Direction.WEST);
		helper.setBlock(AT, unlitState);
		BlockEntity again = BlockEntity.loadStatic(helper.absolutePos(AT), unlitState, saved, helper.getLevel().registryAccess());
		helper.assertTrue(again instanceof ControlTorchTE ut && !ut.isOn(), "its answer is the block's, not a saved flag: the same save under an unlit block reads dark");
		// an event through the reloaded block entity still works
		send(helper, AT, 1);
		helper.assertTrue(helper.getBlockState(AT).getBlock() == LeafiaBlocks.control_torch.get(), "and a panel still drives it");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLitControlTorchGivesRedstoneLikeUpstreamsAndDropsTheUnlitOne(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(AT);
		for(Direction facing : new Direction[]{Direction.UP, Direction.EAST, Direction.SOUTH}) {
			BlockState lit = torch(true, facing);
			BlockState unlit = torch(false, facing);
			for(Direction side : Direction.values()) {
				helper.assertTrue(lit.getSignal(level, pos, side) == (side == facing ? 0 : 15), facing + " lit, " + side + ": 15 on every side but the one it leans away from");
				helper.assertTrue(unlit.getSignal(level, pos, side) == 0, facing + " unlit gives nothing");
			}
			helper.assertTrue(lit.getDirectSignal(level, pos, Direction.DOWN) == lit.getSignal(level, pos, Direction.DOWN) && lit.getDirectSignal(level, pos, Direction.UP) == 0
					&& lit.getDirectSignal(level, pos, Direction.NORTH) == 0, "the strong signal goes down only");
			helper.assertTrue(lit.isSignalSource() && unlit.isSignalSource(), "both are signal sources");
		}
		helper.setBlock(AT, torch(true, Direction.UP));
		var drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(AT), level, pos, helper.getBlockEntity(AT));
		helper.assertTrue(drops.size() == 1 && drops.get(0).is(LeafiaBlocks.control_torch_unlit.get().asItem()), "breaking the lit torch drops the unlit one, got " + drops);
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aControlTorchNeedsSomethingToHoldItAndFallsWhenItGoes(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos stoneAt = new BlockPos(2, 1, 3);
		BlockPos torchAt = new BlockPos(2, 2, 3);
		BlockPos wallAt = new BlockPos(3, 2, 3);
		helper.setBlock(stoneAt, Blocks.STONE);
		helper.setBlock(torchAt, Blocks.AIR);
		helper.setBlock(wallAt, Blocks.AIR);
		BlockPos abs = helper.absolutePos(torchAt);
		helper.assertTrue(ControlTorchBlock.canHold(level, abs, Direction.UP), "a standing torch rests on stone");
		helper.assertTrue(!ControlTorchBlock.canHold(level, abs.above(), Direction.UP), "but not on the air over it");
		for(Direction side : Direction.Plane.HORIZONTAL)
			helper.assertTrue(!ControlTorchBlock.canHold(level, abs, side), "and has nothing at its " + side + " side");

		// a wall torch is held by the side of the block behind it: a torch facing west is on the block to its east
		helper.setBlock(wallAt, Blocks.STONE);
		helper.assertTrue(ControlTorchBlock.canHold(level, abs, Direction.WEST), "facing west it is held by the stone to its east");
		helper.assertTrue(!ControlTorchBlock.canHold(level, abs, Direction.EAST), "and facing east it has nothing behind it");
		helper.setBlock(torchAt, torch(false, Direction.WEST));
		helper.assertTrue(helper.getBlockState(torchAt).getBlock() == LeafiaBlocks.control_torch_unlit.get(), "placed");
		helper.setBlock(wallAt, Blocks.AIR);
		helper.assertTrue(helper.getBlockState(torchAt).isAir(), "it falls when the block behind it goes");

		helper.setBlock(torchAt, torch(true, Direction.UP));
		helper.setBlock(stoneAt, Blocks.AIR);
		helper.assertTrue(helper.getBlockState(torchAt).isAir(), "and a standing one when the block under it goes");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the 1.G clauses

	@GameTest(template = EMPTY)
	public static void theCcpCompatibleLineAppearsOnlyOnBlocksThePanelCanLinkTo(GameTestHelper helper) {
		setup();
		helper.assertTrue(!LeafiaComponentTooltips.ccpLines(LeafiaBlocks.control_torch_unlit.get().asItem()).isEmpty(), "the control torch is compatible");
		helper.assertTrue(LeafiaComponentTooltips.ccpLines(LeafiaBlocks.control_torch_unlit.get().asItem()).get(0).getString().equals("[CCP Compatible]"), "the line is upstream's");
		helper.assertTrue(!LeafiaComponentTooltips.ccpLines(ModBlocks.machine_turbine.get().asItem()).isEmpty(), "a base turbine is (through its adapter)");
		helper.assertTrue(LeafiaComponentTooltips.ccpLines(LeafiaBlocks.control_panel_custom.get().asItem()).isEmpty(), "the panel itself is not marked");
		helper.assertTrue(LeafiaComponentTooltips.ccpLines(LeafiaBlocks.control_panel_front.get().asItem()).isEmpty(), "nor its front");
		helper.assertTrue(LeafiaComponentTooltips.ccpLines(Blocks.STONE.asItem()).isEmpty() && LeafiaComponentTooltips.ccpLines(Blocks.CHEST.asItem()).isEmpty(), "stone and a chest are not");
		helper.assertTrue(LeafiaComponentTooltips.ccpLines(net.minecraft.world.item.Items.STICK).isEmpty(), "an item that is not a block is not");
		boolean was = com.leafia.config.LeafiaFeatures.controlPanel;
		try {
			com.leafia.config.LeafiaFeatures.controlPanel = false;
			helper.assertTrue(LeafiaComponentTooltips.ccpLines(LeafiaBlocks.control_torch_unlit.get().asItem()).isEmpty(), "and none with the control panel switched off");
		} finally {
			com.leafia.config.LeafiaFeatures.controlPanel = was;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the assets, and no client type on the server

	private static String resource(String path) throws IOException {
		try(InputStream in = LeafiaControlPanelInstrumentTwoTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null)
				throw new IOException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static boolean exists(String path) {
		try(InputStream in = LeafiaControlPanelInstrumentTwoTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			return in != null;
		} catch(IOException e) {
			return false;
		}
	}

	@GameTest(template = EMPTY)
	public static void theNewInstrumentModelsAreTheGeneratedPartsOfLeafiasObjs(GameTestHelper helper) throws Exception {
		setup();
		Map<String, List<String>> expected = new LinkedHashMap<>();
		expected.put(AddonInstrumentModels.NIXIE, List.of("Border", "BorderLeft", "InsideLeft", "Inside", "Tube", "Screws", "ClipPlane"));
		expected.put(AddonInstrumentModels.METER_V, List.of("Base", "Meter", "Needle"));
		expected.put(AddonInstrumentModels.TEXT_INDICATOR, List.of("Plane", "Plane.001"));
		Map<String, List<String>> table = ControlPanelParts.all();
		String manifest = resource("control_panel_models.txt");
		for(Map.Entry<String, List<String>> e : expected.entrySet()) {
			helper.assertTrue(table.containsKey(e.getKey()) && table.get(e.getKey()).equals(e.getValue()), e.getKey() + " is in the part table with Leafia's objects, got " + table.get(e.getKey()));
			List<String> objects = new ArrayList<>();
			String obj = resource("models/blocks/ccp_" + e.getKey() + ".obj");
			for(String line : obj.split("\n"))
				if(line.startsWith("o "))
					objects.add(line.substring(2).trim());
			helper.assertTrue(new java.util.HashSet<>(objects).equals(new java.util.HashSet<>(e.getValue())), e.getKey() + ": the OBJ holds " + objects);
			helper.assertTrue(obj.contains("usemtl"), e.getKey() + " has a material, or it bakes to nothing");
			for(String part : e.getValue()) {
				String name = ControlPanelParts.modelName(e.getKey(), part);
				JsonObject json = JsonParser.parseString(resource("models/block/" + name + ".json")).getAsJsonObject();
				helper.assertTrue(json.get("model").getAsString().equals("leafia:models/blocks/ccp_" + e.getKey() + ".obj"), name + " points at its OBJ");
				JsonObject hidden = json.getAsJsonObject("visibility");
				for(String o : objects)
					helper.assertTrue(hidden.has(o) == !o.equals(part), name + ": " + o + (o.equals(part) ? " is shown" : " is hidden"));
				String tex = json.getAsJsonObject("textures").get("0").getAsString();
				String want = e.getKey().equals(AddonInstrumentModels.TEXT_INDICATOR) && part.equals("Plane.001") ? "leafia:block/ccp/" + AddonInstrumentModels.TEXT_INDICATOR_LIGHT : "leafia:block/ccp/white";
				helper.assertTrue(tex.equals(want), name + " wears " + tex + ", expected " + want);
				helper.assertTrue(exists("textures/" + tex.substring("leafia:".length()) + ".png"), "the texture " + tex + " ships");
				helper.assertTrue(manifest.contains(name + "\n"), "the generator lists " + name);
			}
		}
		helper.assertTrue(exists("textures/block/ccp/" + AddonInstrumentModels.RBMK_TEXTURE + ".png"), "the RBMK display's checkerboard ships");
		helper.assertTrue(ControlPanelParts.modelName("text_indicator", "Plane.001").equals("ccp_text_indicator_plane.001"), "Blender's dot survives in the file name, its capital does not");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlTorchsAssetsAreUpstreamsStandingAndWallModelsAndBlockstates(GameTestHelper helper) throws Exception {
		for(String lit : new String[]{"control_torch", "control_torch_unlit"}) {
			JsonObject states = JsonParser.parseString(resource("blockstates/" + lit + ".json")).getAsJsonObject().getAsJsonObject("variants");
			String suffix = lit.equals("control_torch") ? "lit" : "unlit";
			helper.assertTrue(states.keySet().equals(java.util.Set.of("facing=up", "facing=east", "facing=south", "facing=west", "facing=north")), lit + " has the five facings");
			helper.assertTrue(states.getAsJsonObject("facing=up").get("model").getAsString().equals("leafia:block/control_torch_" + suffix), "up is the standing model");
			int[] turns = {0, 90, 180, 270};
			String[] dirs = {"east", "south", "west", "north"};
			for(int i = 0; i < 4; i++) {
				JsonObject v = states.getAsJsonObject("facing=" + dirs[i]);
				helper.assertTrue(v.get("model").getAsString().equals("leafia:block/control_torch_wall_" + suffix) && (v.has("y") ? v.get("y").getAsInt() : 0) == turns[i], dirs[i] + " is the wall model turned " + turns[i]);
			}
			for(String prefix : new String[]{"control_torch_", "control_torch_wall_"}) {
				JsonObject model = JsonParser.parseString(resource("models/block/" + prefix + suffix + ".json")).getAsJsonObject();
				helper.assertTrue(model.getAsJsonObject("textures").get("torch").getAsString().equals(lit.equals("control_torch") ? "leafia:block/control_torch_on" : "leafia:block/control_torch_off"), prefix + suffix + " wears its texture");
			}
		}
		for(String geometry : new String[]{"ccp_torch", "ccp_torch_wall"}) {
			JsonObject g = JsonParser.parseString(resource("models/block/" + geometry + ".json")).getAsJsonObject();
			helper.assertTrue(g.get("render_type").getAsString().equals("minecraft:cutout") && g.getAsJsonArray("elements").size() == 4, geometry + " is four boxes, cut out");
		}
		for(String t : new String[]{"textures/block/control_torch_on.png", "textures/block/control_torch_off.png", "textures/item/control_torch_icon.png"})
			helper.assertTrue(exists(t), t + " ships");
		helper.assertTrue(resource("lang/en_us.json").contains("\"block.leafia.control_torch\": \"Control Panel Torch\""), "and is named in the language file");
		helper.succeed();
	}

	private static final String ROOT = "com/leafia/contents/machines/controlpanel";

	private static byte[] classBytes(String internalName) throws Exception {
		try(InputStream in = LeafiaControlPanelInstrumentTwoTests.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	}

	private static boolean mentionsClient(byte[] bytes) {
		String s = new String(bytes, StandardCharsets.ISO_8859_1);
		return s.contains("net/minecraft/client") || s.contains("com/mojang/blaze3d") || s.contains("org/lwjgl") || s.contains("com/mojang/realmsclient")
				|| s.contains(ROOT + "/core/client") || s.contains(ROOT + "/instruments/client");
	}

	@GameTest(template = EMPTY)
	public static void noNewInstrumentOrTorchClassOnTheServerNamesAClientType(GameTestHelper helper) throws Exception {
		byte[] known = classBytes("com/leafia/client/LeafiaClient");
		helper.assertTrue(known != null && mentionsClient(known), "the scanner flags LeafiaClient");
		for(String client : new String[]{"MeterDraw", "NixieDraw", "RBMKDisplayDraw", "MeterHorizontalCSE", "NixieCSE"}) {
			byte[] draw = classBytes(ROOT + "/instruments/client/" + client);
			helper.assertTrue(draw != null && mentionsClient(draw), "and this batch's own client class " + client);
		}
		List<String> names = new ArrayList<>(List.of(
				ROOT + "/instruments/types/meters/horizontal/MeterHorizontal", ROOT + "/instruments/types/meters/vertical/MeterVertical",
				ROOT + "/instruments/types/nixie/NixieDisplay", ROOT + "/instruments/types/rbmk/RBMKDisplay",
				ROOT + "/instruments/types/textindicator/TextIndicator",
				"com/leafia/contents/machines/panel/controltorch/ControlTorchBlock", "com/leafia/contents/machines/panel/controltorch/ControlTorchTE",
				"com/leafia/contents/machines/controlpanel/core/reach/TurbineGeneration"));
		URL url = LeafiaControlPanelInstrumentTwoTests.class.getClassLoader().getResource("com/leafia/contents/machines/panel");
		if(url != null) {
			Path root = Paths.get(url.toURI());
			try(Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					String internal = "com/leafia/contents/machines/panel/" + rel.substring(0, rel.length() - ".class".length());
					if(!names.contains(internal))
						names.add(internal);
				});
			}
		}
		for(String internal : names) {
			byte[] bytes = classBytes(internal);
			helper.assertTrue(bytes != null, "class file found: " + internal);
			helper.assertTrue(!mentionsClient(bytes), internal + " names a client class, a dedicated server would fail to load it");
			if(!internal.contains("$"))
				Class.forName(internal.replace('/', '.'), true, LeafiaControlPanelInstrumentTwoTests.class.getClassLoader());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aMeterAndANixieSitOnAPlacedPanelAndLetAClickThrough(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		MeterHorizontal m = add(te.panel, "leafia_meter_h", 3, 2);
		NixieDisplay n = add(te.panel, "leafia_nixie", 3, 5);
		AABB none = m.getBoundingBox();
		helper.assertTrue(none == null && n.getBoundingBox() == null, "a click passes through a meter and a nixie");
		helper.assertTrue(te.panel.controls.size() == 2 && te.panel.controls.get(0) == m, "they sit on the panel by index, as the sync packets address them");
		helper.succeed();
	}
}
