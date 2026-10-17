package com.leafia.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSetup;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonEmergencyPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonEncasedPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonPush;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.controls.DialLarge;
import com.leafia.contents.machines.controlpanel.core.controls.DialSquare;
import com.leafia.contents.machines.controlpanel.core.controls.DisplaySevenSeg;
import com.leafia.contents.machines.controlpanel.core.controls.DisplayText;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLamp;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLampRGB;
import com.leafia.contents.machines.controlpanel.core.controls.KnobControl;
import com.leafia.contents.machines.controlpanel.core.controls.Label;
import com.leafia.contents.machines.controlpanel.core.controls.SliderVertical;
import com.leafia.contents.machines.controlpanel.core.controls.SwitchRotaryToggle;
import com.leafia.contents.machines.controlpanel.core.controls.SwitchToggle;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fourteen stock controls of the Custom Control Panel and their registry (CCP-CONTROLS, D-3064): registration order and
 * display names, each control's size, family, variables, configuration, events and default graphs against NTM-CE's source, the
 * hardening of the configuration values a client could author, and the buttons, switches, slider and knob doing their jobs when a
 * click or a tick reaches their default graphs, both directly and through a block entity's real edit and click paths.
 *
 * <p>Every body is synchronous on the server thread, so nothing interleaves; the one test that touches the process-wide registry
 * and the addon list puts them back in a {@code finally} (D-952). The expected numbers are typed from CE's
 * constructors, not read back from the port.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelControlTests {

	private static final String EMPTY = "empty16";
	private static final float EPS = 1E-5F;

	/** Registry name, display name, class, family, size. In CE's registration order. */
	private record Stock(String key, String display, Class<? extends Control> type, ControlType family, float[] size) {}

	private static final List<Stock> STOCK = List.of(
			new Stock("button_push", "Push Button", ButtonPush.class, ControlType.BUTTON, new float[]{1, 1, .4F}),
			new Stock("button_emergency_push", "Emergency Push Button", ButtonEmergencyPush.class, ControlType.BUTTON, new float[]{1.5F, 1.5F, 1.13F}),
			new Stock("button_encased_push", "Encased Push Button", ButtonEncasedPush.class, ControlType.BUTTON, new float[]{1.5F, 1.5F, 1F}),
			new Stock("switch_toggle", "Toggle Switch", SwitchToggle.class, ControlType.SWITCH, new float[]{1, 1, .62F}),
			new Stock("switch_rotary_toggle", "Rotary Toggle Switch", SwitchRotaryToggle.class, ControlType.SWITCH, new float[]{1, 1, .68F}),
			new Stock("slider_vertical", "Vertical Slider", SliderVertical.class, ControlType.SLIDER, new float[]{0.8125F, 2.3125F, .31F}),
			new Stock("knob_control", "Control Knob", KnobControl.class, ControlType.KNOB, new float[]{2, 2, .4F}),
			new Stock("display_7seg", "7-seg Display", DisplaySevenSeg.class, ControlType.DISPLAY, new float[]{.75F, 1.125F, .06F}),
			new Stock("display_text", "Text Display", DisplayText.class, ControlType.DISPLAY, new float[]{0, 0, 0}),
			new Stock("dial_square", "Square Dial", DialSquare.class, ControlType.DIAL, new float[]{2.25F, 2.25F, .4F}),
			new Stock("dial_large", "Large Dial", DialLarge.class, ControlType.DIAL, new float[]{4.5F, 2.25F, .4F}),
			new Stock("indicator_lamp", "Indicator Lamp", IndicatorLamp.class, ControlType.INDICATOR, new float[]{.5F, .5F, .18F}),
			new Stock("indicator_lamp_rgb", "Indicator Lamp (RGB)", IndicatorLampRGB.class, ControlType.INDICATOR, new float[]{.5F, .5F, .18F}),
			new Stock("label", "Label", Label.class, ControlType.LABEL, new float[]{0, 0, 0}));

	/** Each control's variables and configuration, in declaration order, as {@code name=kind:value} (CE's constructors). */
	private static final Map<String, List<String>> VARS = new LinkedHashMap<>();
	private static final Map<String, List<String>> CONFIGS = new LinkedHashMap<>();

	static {
		VARS.put("button_push", List.of("isPushed=f:0.0", "isLit=f:0.0", "color=e:RED"));
		VARS.put("button_emergency_push", List.of("isPushed=f:0.0"));
		VARS.put("button_encased_push", List.of("isPushed=f:0.0", "isLit=f:0.0", "isCoverOpen=f:0.0"));
		VARS.put("switch_toggle", List.of("isOn=f:0.0"));
		VARS.put("switch_rotary_toggle", List.of("isOn=f:0.0"));
		VARS.put("slider_vertical", List.of("value=f:0.0"));
		VARS.put("knob_control", List.of("value=f:0.0"));
		VARS.put("display_7seg", List.of("value=f:0.0"));
		VARS.put("display_text", List.of("isLit=f:0.0", "text=s:text", "color=e:WHITE"));
		VARS.put("dial_square", List.of("value=f:0.0"));
		VARS.put("dial_large", List.of("value=f:0.0"));
		VARS.put("indicator_lamp", List.of("isLit=f:0.0", "color=e:RED"));
		VARS.put("indicator_lamp_rgb", List.of("red=f:0.0", "green=f:0.0", "blue=f:0.0"));
		VARS.put("label", List.of("isLit=f:0.0"));

		for(Stock s : STOCK)
			CONFIGS.put(s.key, List.of());
		CONFIGS.put("knob_control", List.of("positions=f:2.0"));
		CONFIGS.put("display_7seg", List.of("colorR=f:1.0", "colorG=f:1.0", "colorB=f:1.0", "digitCount=f:1.0", "isDecimal=f:0.0"));
		CONFIGS.put("display_text", List.of("scale=f:25.0", "width=f:20.0"));
		CONFIGS.put("dial_square", List.of("label=s:POWER    (RS/10)"));
		CONFIGS.put("dial_large", List.of("label=s:    POWER            (RS)"));
		CONFIGS.put("label", List.of("colorR=f:1.0", "colorG=f:1.0", "colorB=f:1.0", "text=s:label", "scale=f:25.0"));
	}

	/** Controls with a ctrl_press out-event (CE's {@code getOutEvents}); the other five send nothing. */
	private static final List<String> PRESSABLE = List.of("button_push", "button_emergency_push", "button_encased_push", "switch_toggle",
			"switch_rotary_toggle", "slider_vertical", "knob_control");

	private static String describe(DataValue v) {
		if(v instanceof DataValueString s)
			return "s:" + s;
		if(v instanceof DataValueEnum<?> e)
			return "e:" + e.value.name();
		return "f:" + v.getNumber();
	}

	private static List<String> describe(Map<String, DataValue> map) {
		List<String> out = new ArrayList<>();
		for(Map.Entry<String, DataValue> e : map.entrySet())
			out.add(e.getKey() + "=" + describe(e.getValue()));
		return out;
	}

	private static void setup() {
		ControlPanelSetup.commonSetup();
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

	/** A live control of this registry name on this panel, with its default graphs, added to the panel. */
	private static Control add(ControlPanel panel, String key, float x, float y) {
		Control c = ControlRegistry.getNew(key, panel);
		c.posX = x;
		c.posY = y;
		c.populateDefaultNodes(editorEvents(c));
		panel.controls.add(c);
		return c;
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

	// ------------------------------------------------------------------ the registry

	@GameTest(template = EMPTY)
	public static void theFourteenStockControlsAreRegisteredInUpstreamsOrder(GameTestHelper helper) {
		setup();
		List<String> expected = new ArrayList<>();
		for(Stock s : STOCK)
			expected.add(s.key);
		List<String> have = new ArrayList<>();
		for(String key : ControlRegistry.registry.keySet())
			if(expected.contains(key))
				have.add(key);
		helper.assertTrue(have.equals(expected), "all fourteen, in CE's order, got " + have);

		for(Stock s : STOCK) {
			Control proto = ControlRegistry.registry.get(s.key);
			helper.assertTrue(ControlRegistry.isRegistered(s.key) && proto.getClass() == s.type, s.key + " is a " + s.type.getSimpleName());
			helper.assertTrue(proto.registryName.equals(s.key) && proto.name.equals(s.display), s.key + " is named '" + s.display + "', got '" + proto.name + "'");
			helper.assertTrue(proto.panel == null, "a prototype belongs to no panel");
		}

		// The placement list groups by family; the counts are CE's (METER is unused upstream).
		Map<ControlType, Integer> counts = new LinkedHashMap<>();
		counts.put(ControlType.BUTTON, 3);
		counts.put(ControlType.SWITCH, 2);
		counts.put(ControlType.SLIDER, 1);
		counts.put(ControlType.KNOB, 1);
		counts.put(ControlType.DIAL, 2);
		counts.put(ControlType.DISPLAY, 2);
		counts.put(ControlType.INDICATOR, 2);
		counts.put(ControlType.LABEL, 1);
		for(Map.Entry<ControlType, Integer> e : counts.entrySet()) {
			int n = 0;
			for(String key : ControlRegistry.getAllControlsOfType(e.getKey()))
				if(expected.contains(key))
					n++;
			helper.assertTrue(n == e.getValue(), e.getKey().name + " has " + e.getValue() + " stock controls, got " + n);
		}
		helper.assertTrue(ControlRegistry.getAllControlsOfType(ControlType.METER).stream().noneMatch(expected::contains), "no stock control is a meter");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void initIsIdempotentAndFoldsInAddonControls(GameTestHelper helper) {
		setup();
		List<String> order = new ArrayList<>(ControlRegistry.registry.keySet());
		Control before = ControlRegistry.registry.get("button_push");
		ControlRegistry.init();
		helper.assertTrue(new ArrayList<>(ControlRegistry.registry.keySet()).equals(order), "a second init changes neither the names nor their order");
		helper.assertTrue(ControlRegistry.getAllControls().size() == order.size(), "getAllControls lists each once");

		Control addon = new Label("Addon label", "ccp_test_addon", null);
		ControlRegistry.addonControls.add(addon);
		try {
			ControlRegistry.init();
			helper.assertTrue(ControlRegistry.isRegistered("ccp_test_addon") && ControlRegistry.registry.get("ccp_test_addon") == addon,
					"a control on the addon list is registered by init");
			List<String> now = new ArrayList<>(ControlRegistry.registry.keySet());
			helper.assertTrue(now.indexOf("ccp_test_addon") > now.indexOf("label"), "after the stock fourteen");
		} finally {
			ControlRegistry.addonControls.remove(addon);
			ControlRegistry.unregister("ccp_test_addon");
		}
		helper.assertTrue(!ControlRegistry.isRegistered("ccp_test_addon") && ControlRegistry.getNew("ccp_test_addon", null) == null, "and gone again");
		helper.assertTrue(ControlRegistry.getNew("no_such_control", null) == null && !ControlRegistry.isRegistered("no_such_control"), "an unknown name gives null");
		helper.assertTrue(before != null, "the stock prototypes are there");
		helper.succeed();
	}

	// ------------------------------------------------------------------ each control against CE

	@GameTest(template = EMPTY)
	public static void eachControlHasUpstreamsSizeFamilyVariablesAndConfigs(GameTestHelper helper) {
		setup();
		ControlPanel panel = new ControlPanel(null);
		for(Stock s : STOCK) {
			Control c = ControlRegistry.getNew(s.key, panel);
			helper.assertTrue(c != null && c.getClass() == s.type && c.panel == panel, s.key + " is made on the panel");
			helper.assertTrue(c.getControlType() == s.family, s.key + " is in the " + s.family.name + " family");
			float[] size = c.getSize();
			helper.assertTrue(size.length == 3 && size[0] == s.size[0] && size[1] == s.size[1] && size[2] == s.size[2],
					s.key + "'s size is " + java.util.Arrays.toString(s.size) + ", got " + java.util.Arrays.toString(size));
			helper.assertTrue(describe(c.vars).equals(VARS.get(s.key)), s.key + "'s variables are " + VARS.get(s.key) + ", got " + describe(c.vars));
			helper.assertTrue(describe(c.getConfigs()).equals(CONFIGS.get(s.key)), s.key + "'s config is " + CONFIGS.get(s.key) + ", got " + describe(c.getConfigs()));
			helper.assertTrue(c.customVarNames.isEmpty() && c.taggedLinks.isEmpty() && c.receiveNodeMap.isEmpty() && c.sendNodeMap.isEmpty(),
					s.key + " starts with no custom variables, links or graphs");

			List<String> out = PRESSABLE.contains(s.key) ? List.of("ctrl_press") : List.<String>of();
			helper.assertTrue(c.getOutEvents().equals(out), s.key + "'s out-events are " + out + ", got " + c.getOutEvents());
			helper.assertTrue(c.getInEvents().equals(List.of("tick", "initialize", "redstone_input")), s.key + " takes the three base in-events, got " + c.getInEvents());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNewControlIsALiveCopyThatSharesNothingWithItsPrototype(GameTestHelper helper) {
		setup();
		ControlPanel panel = new ControlPanel(null);
		for(Stock s : STOCK) {
			Control proto = ControlRegistry.registry.get(s.key);
			Control a = ControlRegistry.getNew(s.key, panel);
			Control b = ControlRegistry.getNew(s.key, panel);
			helper.assertTrue(a != b && a != proto && a.vars != b.vars && a.configMap != b.configMap, s.key + ": each is its own object");
			a.vars.put("scratch", new DataValueFloat(9));
			a.configMap.put("scratch", new DataValueFloat(9));
			helper.assertTrue(!b.vars.containsKey("scratch") && !proto.vars.containsKey("scratch") && !proto.configMap.containsKey("scratch"),
					s.key + ": writing one does not show in another or in the prototype");
			Control c = a.newControl(null);
			helper.assertTrue(c.getClass() == a.getClass() && c.registryName.equals(s.key) && c.name.equals(s.display) && !c.vars.containsKey("scratch"),
					s.key + ": newControl is a fresh one of the same kind and name");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ default graphs

	/** Receive graphs after {@code populateDefaultNodes}, then (node count, sub-graph node count) of each. CE's {@code populateDefaultNodes}. */
	private static final Map<String, Map<String, int[]>> GRAPHS = new LinkedHashMap<>();

	static {
		GRAPHS.put("button_push", Map.of("ctrl_press", new int[]{4, 0}, "tick", new int[]{3, 2}));
		GRAPHS.put("button_emergency_push", Map.of("ctrl_press", new int[]{3, 0}, "tick", new int[]{3, 1}));
		GRAPHS.put("button_encased_push", Map.of("ctrl_press", new int[]{6, 3 + 4}, "tick", new int[]{3, 2}));
		GRAPHS.put("switch_toggle", Map.of("ctrl_press", new int[]{3, 0}));
		GRAPHS.put("switch_rotary_toggle", Map.of("ctrl_press", new int[]{3, 0}));
		GRAPHS.put("slider_vertical", Map.of("ctrl_press", new int[]{6, 0}));
		GRAPHS.put("knob_control", Map.of("ctrl_press", new int[]{6, 0}));
	}

	@GameTest(template = EMPTY)
	public static void theDefaultGraphsAreUpstreamsAndSurviveTheTrustBoundary(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		for(Stock s : STOCK) {
			Control c = add(te.panel, s.key, 1, 1);
			Map<String, int[]> want = GRAPHS.getOrDefault(s.key, Map.of());
			helper.assertTrue(c.receiveNodeMap.keySet().equals(want.keySet()), s.key + " has receive graphs " + want.keySet() + ", got " + c.receiveNodeMap.keySet());
			helper.assertTrue(c.sendNodeMap.isEmpty(), s.key + " has no send graphs");
			for(Map.Entry<String, int[]> g : want.entrySet()) {
				NodeSystem sys = c.receiveNodeMap.get(g.getKey());
				int sub = 0;
				for(NodeSystem inner : sys.subSystems.values())
					sub += inner.nodes.size();
				helper.assertTrue(sys.nodes.size() == g.getValue()[0] && sub == g.getValue()[1],
						s.key + "'s " + g.getKey() + " graph is " + g.getValue()[0] + " nodes with " + g.getValue()[1] + " inside functions, got "
								+ sys.nodes.size() + " and " + sub);
			}
			helper.assertTrue(ControlPanelGuard.graphProblem(c) == null, s.key + "'s graphs pass the cycle and depth guard: " + ControlPanelGuard.graphProblem(c));

			// Saved and read back through the panel's loader: the same control, not a placeholder.
			CompoundTag saved = c.writeToNBT(new CompoundTag());
			Control back = te.panel.readControl(saved);
			helper.assertTrue(back.getClass() == s.type && !(back instanceof UnknownControl), s.key + " reads back as itself, got " + back.getClass().getSimpleName());
			CompoundTag loaded = back.writeToNBT(new CompoundTag());
			// NodeSetVar keeps a saved literal across a load (owner-approved divergence from CE, D-3066), so a load is the identity.
			CompoundTag expected = saved.copy();
			helper.assertTrue(loaded.equals(expected), s.key + " writes the same tree after a trip through the loader: " + firstDifference(expected, loaded, ""));
			Control again = te.panel.readControl(loaded);
			helper.assertTrue(again.writeToNBT(new CompoundTag()).equals(loaded), s.key + " is stable from then on");
			Control copy = te.panel.cloneControl(c);
			helper.assertTrue(copy.getClass() == s.type && copy.writeToNBT(new CompoundTag()).equals(loaded), s.key + " clones to an equal control");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aPanelHoldingEveryControlIsAcceptedFromAnEditorAndReloadsUnchanged(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		CompoundTag edit = LeafiaControlPanelKit.edit(te, panel -> {
			float y = 0;
			for(Stock s : STOCK) {
				add(panel, s.key, 0, y);
				y += 1;
			}
		});
		String why = te.applyFullSet(player, edit);
		helper.assertTrue(why == null, "an edit with all fourteen controls and their default graphs is applied, got: " + why);
		helper.assertTrue(te.panel.controls.size() == STOCK.size(), "all are on the panel");
		for(int i = 0; i < STOCK.size(); i++)
			helper.assertTrue(te.panel.controls.get(i).getClass() == STOCK.get(i).type, STOCK.get(i).key + " survived the edit as itself");

		CompoundTag saved = te.panel.writeToNBT(new CompoundTag());
		ControlPanel reread = new ControlPanel(te);
		helper.assertTrue(reread.readFromNBT(saved), "the saved panel reads");
		helper.assertTrue(reread.writeToNBT(new CompoundTag()).equals(saved), "and writes the same tree");
		helper.succeed();
	}

	// ------------------------------------------------------------------ behaviour: the graphs run

	/** The delay a tick graph's buffer is set to, read from the graph. */
	private static int bufferDelay(Control c) {
		for(Node n : c.receiveNodeMap.get("tick").nodes)
			if(n instanceof NodeBuffer b)
				return (int) b.inputs.get(1).defaultValue.getNumber();
		throw new IllegalStateException("no buffer in the tick graph");
	}

	@GameTest(template = EMPTY)
	public static void thePushButtonLatchesAndLetsGoAfterItsBufferDelay(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control c = add(te.panel, "button_push", 0, 0);
		int delay = bufferDelay(c);
		helper.assertTrue(delay == 10, "the push button's buffer is 10 ticks, got " + delay);

		tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 0 && v(c, "isLit") == 0, "idle ticks change nothing");
		press(te.panel, c, false);
		helper.assertTrue(v(c, "isPushed") == 1 && v(c, "isLit") == 1, "a press pushes it and lights it");

		// The buffer counts `delay` ticks and answers on the one after, so the button lets go on tick delay + 1.
		for(int t = 1; t <= delay; t++) {
			tick(te.panel, c);
			helper.assertTrue(v(c, "isPushed") == 1 && v(c, "isLit") == 1, "still down on tick " + t);
		}
		tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 0 && v(c, "isLit") == 0, "released and dark on tick " + (delay + 1));
		tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 0, "and stays released");

		press(te.panel, c, true);
		helper.assertTrue(v(c, "isPushed") == 1, "a sneaking press is the same press");
		press(te.panel, c, false);
		helper.assertTrue(v(c, "isPushed") == 0 && v(c, "isLit") == 0, "a second press while down toggles it back up (NOT of isPushed)");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theEmergencyButtonLatchesAndLetsGoAfterFifteenTicks(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control c = add(te.panel, "button_emergency_push", 0, 0);
		int delay = bufferDelay(c);
		helper.assertTrue(delay == 15, "the emergency button's buffer is 15 ticks, got " + delay);
		press(te.panel, c, false);
		helper.assertTrue(v(c, "isPushed") == 1, "a press pushes it");
		for(int t = 1; t <= delay; t++)
			tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 1, "still down after " + delay + " ticks");
		tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 0, "released on tick " + (delay + 1));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theEncasedButtonNeedsItsCoverOpenAndASneakOpensIt(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control c = add(te.panel, "button_encased_push", 0, 0);
		int delay = bufferDelay(c);
		helper.assertTrue(delay == 15, "its buffer is 15 ticks, got " + delay);

		press(te.panel, c, false);
		helper.assertTrue(v(c, "isPushed") == 0 && v(c, "isLit") == 0 && v(c, "isCoverOpen") == 0, "a plain press on a closed cover does nothing");
		press(te.panel, c, true);
		helper.assertTrue(v(c, "isCoverOpen") == 1 && v(c, "isPushed") == 0, "a sneaking press opens the cover and does not push");
		press(te.panel, c, true);
		helper.assertTrue(v(c, "isCoverOpen") == 0, "and the next sneaking press closes it");
		press(te.panel, c, true);
		press(te.panel, c, false);
		helper.assertTrue(v(c, "isCoverOpen") == 1 && v(c, "isPushed") == 1 && v(c, "isLit") == 1, "a plain press under an open cover pushes and lights it, leaving the cover open");

		for(int t = 1; t <= delay; t++)
			tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 1, "down for " + delay + " ticks");
		tick(te.panel, c);
		helper.assertTrue(v(c, "isPushed") == 0 && v(c, "isLit") == 0 && v(c, "isCoverOpen") == 1, "released and dark on tick " + (delay + 1) + ", the cover still open");
		helper.succeed();
	}

	/**
	 * D-3064 flagged NTM-CE's {@code NodeSetVar.readFromNBT} overwriting a typed literal with the variable's current value, which
	 * left the encased button's lamp dark after any load. Owner decision 2026-10-16 (D-3066): a saved literal is kept.
	 */
	@GameTest(template = EMPTY)
	public static void aReloadedEncasedButtonStillLightsItsLamp(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control fresh = add(te.panel, "button_encased_push", 0, 0);
		Control loaded = te.panel.readControl(fresh.writeToNBT(new CompoundTag()));
		te.panel.controls.add(loaded);
		press(te.panel, fresh, true);
		press(te.panel, fresh, false);
		helper.assertTrue(v(fresh, "isPushed") == 1 && v(fresh, "isLit") == 1, "a control built in code lights its lamp when pushed");
		press(te.panel, loaded, true);
		press(te.panel, loaded, false);
		helper.assertTrue(v(loaded, "isPushed") == 1 && v(loaded, "isLit") == 1, "the same control after a load still lights its lamp (owner-approved divergence from CE)");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void bothSwitchesFlipOnEveryPress(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		for(String key : List.of("switch_toggle", "switch_rotary_toggle")) {
			Control c = add(te.panel, key, 0, 0);
			boolean[] sneak = {false, true, false, false};
			float on = 0;
			for(int i = 0; i < sneak.length; i++) {
				press(te.panel, c, sneak[i]);
				on = 1 - on;
				helper.assertTrue(v(c, "isOn") == on, key + " press " + (i + 1) + " leaves isOn " + on + ", got " + v(c, "isOn"));
			}
			tick(te.panel, c);
			helper.assertTrue(v(c, "isOn") == on, key + " has no tick graph, so a tick changes nothing");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSliderStepsUpOnAPressAndDownOnASneakWithinZeroToFive(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control c = add(te.panel, "slider_vertical", 0, 0);
		for(int i = 1; i <= 7; i++) {
			press(te.panel, c, false);
			float want = Math.min(i, 5);
			helper.assertTrue(v(c, "value") == want, "plain press " + i + " gives " + want + ", got " + v(c, "value"));
		}
		for(int i = 1; i <= 7; i++) {
			press(te.panel, c, true);
			float want = Math.max(5 - i, 0);
			helper.assertTrue(v(c, "value") == want, "sneaking press " + i + " gives " + want + ", got " + v(c, "value"));
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theKnobStepsUpOnAPressAndDownOnASneakWithinZeroToTen(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		Control c = add(te.panel, "knob_control", 0, 0);
		for(int i = 1; i <= 12; i++) {
			press(te.panel, c, false);
			float want = Math.min(i, 10);
			helper.assertTrue(v(c, "value") == want, "plain press " + i + " gives " + want + ", got " + v(c, "value"));
		}
		for(int i = 1; i <= 12; i++) {
			press(te.panel, c, true);
			float want = Math.max(10 - i, 0);
			helper.assertTrue(v(c, "value") == want, "sneaking press " + i + " gives " + want + ", got " + v(c, "value"));
		}
		// The positions setting does not change the clamp: CE's graph is fixed at 10 and the renderer draws eleven (kept).
		Map<String, DataValue> cfg = new LinkedHashMap<>(c.getConfigs());
		cfg.put("positions", new DataValueFloat(3));
		c.applyConfigs(cfg);
		helper.assertTrue(((KnobControl) c).getPositions() == 3, "the setting is read");
		for(int i = 0; i < 12; i++)
			press(te.panel, c, false);
		helper.assertTrue(v(c, "value") == 10, "but the graph still clamps at 10");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void controlsWithoutGraphsIgnoreEveryEvent(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		for(String key : List.of("display_7seg", "display_text", "dial_square", "dial_large", "indicator_lamp", "indicator_lamp_rgb", "label")) {
			Control c = add(te.panel, key, 0, 0);
			CompoundTag before = c.writeToNBT(new CompoundTag());
			press(te.panel, c, false);
			tick(te.panel, c);
			te.panel.receiveDirectEvent(c, ControlEvent.newEvent("initialize"));
			helper.assertTrue(c.writeToNBT(new CompoundTag()).equals(before), key + " has no graph, so events leave it as it was");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the click path, end to end

	private static CompoundTag click(int index, boolean sneaking) {
		CompoundTag tag = ControlEvent.newEvent("ctrl_press").setVar("isSneaking", sneaking).writeToNBT(new CompoundTag());
		tag.putInt("click_control", index);
		return tag;
	}

	@GameTest(template = EMPTY)
	public static void aClickOnAPlacedPanelReachesTheControlsDefaultGraph(GameTestHelper helper) {
		setup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, LeafiaControlPanelKit.AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		// Put the controls on through the editor path, graphs and all, as a player would.
		CompoundTag edit = LeafiaControlPanelKit.edit(te, panel -> {
			add(panel, "switch_toggle", 0, 0);
			add(panel, "button_encased_push", 2, 0);
			add(panel, "slider_vertical", 4, 0);
			add(panel, "indicator_lamp", 6, 0);
		});
		helper.assertTrue(te.applyFullSet(player, edit) == null, "the edit is applied");
		Control toggle = te.panel.controls.get(0), encased = te.panel.controls.get(1), slider = te.panel.controls.get(2), lamp = te.panel.controls.get(3);

		helper.assertTrue(te.applyClick(player, click(0, false)) && v(toggle, "isOn") == 1, "a click flips the switch");
		helper.assertTrue(te.applyClick(player, click(0, false)) && v(toggle, "isOn") == 0, "and flips it back");
		helper.assertTrue(te.applyClick(player, click(1, true)) && v(encased, "isCoverOpen") == 1, "a sneaking click lifts the encased button's cover");
		helper.assertTrue(te.applyClick(player, click(1, false)) && v(encased, "isPushed") == 1, "and the next click pushes it");
		helper.assertTrue(te.applyClick(player, click(2, false)) && te.applyClick(player, click(2, false)) && v(slider, "value") == 2, "two clicks raise the slider to 2");
		helper.assertTrue(te.applyClick(player, click(3, false)) && v(lamp, "isLit") == 0, "a click on a lamp, which has no graph, does nothing");

		// What the clients tracking the chunk are told is a variable diff, and applying it to their copy makes the copy agree.
		te.panel.update();
		ControlPanel clientCopy = new ControlPanel(te);
		helper.assertTrue(clientCopy.readFromNBT(te.panel.writeToNBT(new CompoundTag())), "a client copy of the panel reads");
		helper.assertTrue(te.applyClick(player, click(2, false)), "one more click on the slider");
		te.panel.update();
		helper.assertTrue(te.panel.changedVars.size() == 1, "changes exactly one variable, got " + te.panel.changedVars.size());
		var change = te.panel.changedVars.get(0);
		helper.assertTrue(change.varListIdx() == 2 && change.varName().equals("value") && change.data().getNumber() == 3, "the slider's value, now 3");
		clientCopy.applyUpdates(te.panel.changedVars);
		helper.assertTrue(clientCopy.controls.get(2).getVar("value").getNumber() == 3, "and the client copy follows");
		helper.succeed();
	}

	// ------------------------------------------------------------------ configuration and geometry

	@GameTest(template = EMPTY)
	public static void theSevenSegmentDisplayReadsItsConfigAndWidensItsBoxPerDigit(GameTestHelper helper) {
		setup();
		DisplaySevenSeg d = (DisplaySevenSeg) ControlRegistry.getNew("display_7seg", new ControlPanel(null));
		d.posX = 5;
		d.posY = 7;
		float[] box = d.getBox();
		helper.assertTrue(near(box[0], 5F) && near(box[1], 7F) && near(box[2], 5.75F) && near(box[3], 8.125F),
				"one digit: the control's own 0.75 by 1.125 box, got " + java.util.Arrays.toString(box));
		helper.assertTrue(d.getBoundingBox() == null, "it has no collision box");

		Map<String, DataValue> cfg = new LinkedHashMap<>(d.getConfigs());
		cfg.put("digitCount", new DataValueFloat(3));
		cfg.put("isDecimal", new DataValueFloat(1));
		cfg.put("colorR", new DataValueFloat(0.25F));
		cfg.put("colorG", new DataValueFloat(0.5F));
		cfg.put("colorB", new DataValueFloat(0.75F));
		d.applyConfigs(cfg);
		helper.assertTrue(d.getDigitCount() == 3 && d.isDecimal(), "digits and base are read");
		float[] color = d.getColor();
		helper.assertTrue(color[0] == 0.25F && color[1] == 0.5F && color[2] == 0.75F, "and the colour");
		box = d.getBox();
		// CE: minX = posX - (w * n - (n - 1) * 0.125) + w = 5 - (2.25 - 0.25) + 0.75.
		helper.assertTrue(near(box[0], 3.75F) && near(box[2], 5.75F), "three digits widen the box leftwards by two digits less two eighths, got " + java.util.Arrays.toString(box));

		// The segment table is CE's: eight is every segment, one is the two on the right.
		helper.assertTrue(DisplaySevenSeg.SEGMENTS.length == 16 && DisplaySevenSeg.SEGMENTS[8] == 0x7F && DisplaySevenSeg.SEGMENTS[1] == 0x30
				&& DisplaySevenSeg.SEGMENTS[0] == 0x7E && DisplaySevenSeg.SEGMENTS[15] == 0x47, "the seven-segment table");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void configValuesNoEditorCouldMakeAreClampedWhenRead(GameTestHelper helper) {
		setup();
		ControlPanel panel = new ControlPanel(null);
		float[] hostile = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 2E9F, -2E9F, -5, 1000};

		for(float bad : hostile) {
			DisplaySevenSeg d = (DisplaySevenSeg) ControlRegistry.getNew("display_7seg", panel);
			d.applyConfigs(cfg("digitCount", new DataValueFloat(bad)));
			helper.assertTrue(d.getDigitCount() >= 0 && d.getDigitCount() <= 8, "digitCount " + bad + " is read as " + d.getDigitCount() + ", inside the editor's 0..8");

			DisplayText t = (DisplayText) ControlRegistry.getNew("display_text", panel);
			t.applyConfigs(cfg("scale", new DataValueFloat(bad), "width", new DataValueFloat(bad)));
			helper.assertTrue(t.getScale() >= 10 && t.getScale() <= 100 && t.getConfiguredWidth() >= 10 && t.getConfiguredWidth() <= 100,
					"text scale and width " + bad + " are read inside 10..100, got " + t.getScale() + " and " + t.getConfiguredWidth());

			Label l = (Label) ControlRegistry.getNew("label", panel);
			l.applyConfigs(cfg("scale", new DataValueFloat(bad)));
			helper.assertTrue(l.getScale() >= 10 && l.getScale() <= 100, "label scale " + bad + " is read inside 10..100, got " + l.getScale());

			KnobControl k = (KnobControl) ControlRegistry.getNew("knob_control", panel);
			k.applyConfigs(cfg("positions", new DataValueFloat(bad)));
			helper.assertTrue(k.getPositions() >= 1 && k.getPositions() <= 11, "knob positions " + bad + " are read inside 1..11, got " + k.getPositions());
		}

		// The editor's own extremes are taken as written.
		DisplaySevenSeg d = (DisplaySevenSeg) ControlRegistry.getNew("display_7seg", panel);
		d.applyConfigs(cfg("digitCount", new DataValueFloat(0)));
		helper.assertTrue(d.getDigitCount() == 0, "zero digits is a legal editor setting");
		d.applyConfigs(cfg("digitCount", new DataValueFloat(8)));
		helper.assertTrue(d.getDigitCount() == 8, "and eight");

		// The raw value stays in the config map, so a panel round-trips byte for byte even with a setting the editor could not make.
		Control k = ControlRegistry.getNew("knob_control", panel);
		k.applyConfigs(cfg("positions", new DataValueFloat(99)));
		helper.assertTrue(k.getConfigs().get("positions").getNumber() == 99, "the stored setting is untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void textControlsDependOnTheClientForTheirBoxHeight(GameTestHelper helper) {
		setup();
		ControlPanel panel = new ControlPanel(null);

		DisplayText t = (DisplayText) ControlRegistry.getNew("display_text", panel);
		t.posX = 2;
		t.posY = 3;
		float[] box = t.getBox();
		// CE: x from posX - 0.1 to posX + width * 1.5 * scale / 500 + 0.1 = 20 * 1.5 * 25 / 500 = 1.5; y from posY - 0.1 to posY + height * ... + 0.1.
		helper.assertTrue(near(box[0], 1.9F) && near(box[1], 2.9F) && near(box[2], 3.6F) && near(box[3], 3.1F),
				"unmeasured, the box is as wide as the setting and as tall as its padding, got " + java.util.Arrays.toString(box));
		t.setMeasured(40, 9);
		box = t.getBox();
		helper.assertTrue(near(box[2], 3.6F) && near(box[3], 3F + 9 * 25 / 500F + .1F),
				"measured, the height follows the font (the measured width is not used, as in CE), got " + java.util.Arrays.toString(box));
		helper.assertTrue(t.getBoundingBox() == null && t.getSize()[0] == 0, "no collision box, size zero");

		Label l = (Label) ControlRegistry.getNew("label", panel);
		l.posX = 2;
		l.posY = 3;
		box = l.getBox();
		helper.assertTrue(near(box[0], 2F) && near(box[1], 3F) && near(box[2], 2F) && near(box[3], 3F), "an unmeasured label is a point, got " + java.util.Arrays.toString(box));
		l.setMeasured(60, 9);
		box = l.getBox();
		helper.assertTrue(near(box[2], 2F + 60 * 25 / 500F) && near(box[3], 3F + 9 * 25 / 500F), "measured, it is the text's size at the scale, got " + java.util.Arrays.toString(box));

		Map<String, DataValue> cfg = new LinkedHashMap<>(l.getConfigs());
		cfg.put("text", new DataValueString("Reactor 1"));
		cfg.put("colorR", new DataValueFloat(0.5F));
		cfg.put("scale", new DataValueFloat(50));
		l.applyConfigs(cfg);
		helper.assertTrue(l.getText().equals("Reactor 1") && l.getScale() == 50 && l.getColor()[0] == 0.5F && l.getColor()[1] == 1F, "label text, scale and colour are read");
		box = l.getBox();
		helper.assertTrue(near(box[2], 2F + 60 * 50 / 500F), "and the box follows the new scale");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDialsReadTheirLabelsAndKeepNoCollisionBox(GameTestHelper helper) {
		setup();
		ControlPanel panel = new ControlPanel(null);
		DialSquare square = (DialSquare) ControlRegistry.getNew("dial_square", panel);
		DialLarge large = (DialLarge) ControlRegistry.getNew("dial_large", panel);
		helper.assertTrue(square.getLabel().equals("POWER    (RS/10)") && large.getLabel().equals("    POWER            (RS)"), "the default labels keep CE's spacing");
		square.applyConfigs(cfg("label", new DataValueString("FLUX")));
		helper.assertTrue(square.getLabel().equals("FLUX") && large.getLabel().startsWith("    POWER"), "a label is read, per control");
		AABB none = square.getBoundingBox();
		helper.assertTrue(none == null && large.getBoundingBox() == null, "neither dial can be clicked");
		for(String key : List.of("indicator_lamp", "indicator_lamp_rgb", "display_7seg", "display_text", "label"))
			helper.assertTrue(ControlRegistry.getNew(key, panel).getBoundingBox() == null, key + " has no collision box");
		for(String key : PRESSABLE)
			helper.assertTrue(ControlRegistry.getNew(key, panel).getBoundingBox() != null, key + " has one");

		// A pressable control's box is the size on the panel, offset as CE does for controls that are not 1 by 1.
		Control b = ControlRegistry.getNew("button_emergency_push", panel);
		b.posX = 1;
		b.posY = 1;
		AABB box = b.getBoundingBox();
		helper.assertTrue(near((float) (box.maxX - box.minX), 1.5F) && near((float) (box.maxZ - box.minZ), 1.5F) && near((float) box.maxY, 1.13F),
				"the emergency button's box is 1.5 by 1.5 by 1.13, got " + box);
		helper.succeed();
	}

	/** A config map from alternating names and numbers or strings. */
	private static Map<String, DataValue> cfg(Object... kv) {
		Map<String, DataValue> map = new LinkedHashMap<>();
		for(int i = 0; i < kv.length; i += 2)
			map.put((String) kv[i], (DataValue) kv[i + 1]);
		return map;
	}

	/** Where two tags first differ, for a failure message. */
	private static String firstDifference(net.minecraft.nbt.Tag a, net.minecraft.nbt.Tag b, String path) {
		if(a instanceof CompoundTag ca && b instanceof CompoundTag cb) {
			for(String k : ca.getAllKeys()) {
				if(!cb.contains(k))
					return path + "/" + k + " missing on the right";
				String d = firstDifference(ca.get(k), cb.get(k), path + "/" + k);
				if(d != null)
					return d;
			}
			for(String k : cb.getAllKeys())
				if(!ca.contains(k))
					return path + "/" + k + " missing on the left";
			return null;
		}
		return a.equals(b) ? null : path + ": " + a + " vs " + b;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < EPS;
	}
}
