package com.leafia.gametest;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Custom Control Panel's data core (CCP-DATA, D-3061): the four value types and the composite, the event table, the
 * control families, and the proof that nothing the common half loads can name a client class.
 *
 * <p>Every number here is NTM-CE's or Leafia's (the spec for this subsystem, D-3060) and is pinned as a literal, so a
 * change to one is a failing test and not a quiet rebalance. The NBT shapes are pinned too, because they are the format a saved
 * panel is in.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelDataTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ DataValueFloat

	@GameTest(template = EMPTY)
	public static void floatsRoundTripBitForBit(GameTestHelper helper) {
		float[] samples = {0F, 1.5F, -2.25F, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_NORMAL,
				Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
		for(float f : samples) {
			DataValueFloat v = new DataValueFloat(f);
			Tag tag = v.writeToNBT();
			helper.assertTrue(tag.getId() == Tag.TAG_FLOAT && tag instanceof FloatTag, "a number is a float tag on the wire: " + f);
			DataValue back = DataValue.newFromNBT(tag);
			helper.assertTrue(back instanceof DataValueFloat, "a float tag reads back as a number: " + f);
			helper.assertTrue(Float.floatToRawIntBits(((DataValueFloat) back).num) == Float.floatToRawIntBits(f),
					"the bits survive, including NaN and the infinities: " + f);
			helper.assertTrue(back.equals(v) && back.hashCode() == v.hashCode(), "and equals agrees: " + f);
			DataValue copy = v.copy();
			helper.assertTrue(copy != v && copy.equals(v), "a copy is a separate equal object: " + f);
		}
		// FloatTag.valueOf hands back one shared tag for any zero, so the sign of -0 does not survive the wire.
		DataValue negZero = DataValue.newFromNBT(new DataValueFloat(-0F).writeToNBT());
		helper.assertTrue(negZero instanceof DataValueFloat nz && Float.floatToRawIntBits(nz.num) == Float.floatToRawIntBits(0F),
				"-0 reads back as +0: the tag collapses zeros, and a panel cannot tell the difference");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void floatReadings(GameTestHelper helper) {
		helper.assertTrue(new DataValueFloat(true).num == 1F && new DataValueFloat(false).num == 0F, "booleans are 1 and 0");
		helper.assertTrue(new DataValueFloat(3F).getType() == DataType.NUMBER, "its type is NUMBER");
		helper.assertTrue(!new DataValueFloat(0F).getBoolean() && !new DataValueFloat(-0F).getBoolean(), "0 and -0 are false");
		helper.assertTrue(new DataValueFloat(0.001F).getBoolean() && new DataValueFloat(-1F).getBoolean(), "anything else is true");
		helper.assertTrue(new DataValueFloat(Float.NaN).getBoolean(), "NaN is true (NaN != 0), upstream's reading and a live trap (D-953)");
		helper.assertTrue("1.0".equals(new DataValueFloat(true).toString()) && "-2.5".equals(new DataValueFloat(-2.5F).toString()),
				"a number prints as Float.toString");
		helper.assertTrue(!new DataValueFloat(Float.NaN).equals(null) && new DataValueFloat(Float.NaN).equals(new DataValueFloat(Float.NaN)),
				"NaN equals NaN here (Float.compare), or a variable holding it would be 'changed' every tick");
		helper.assertTrue(!new DataValueFloat(0F).equals(new DataValueFloat(-0F)), "and 0 differs from -0 by the same rule");
		helper.assertTrue(new DataValueFloat(2.9F).getEnum(Direction.class) == Direction.NORTH, "an enum reading truncates: 2.9 is ordinal 2");
		helper.assertTrue(new DataValueFloat(-1F).getEnum(Direction.class) == Direction.DOWN, "below range is the first constant");
		helper.assertTrue(new DataValueFloat(99F).getEnum(Direction.class) == Direction.DOWN, "above range is the first constant");
		helper.assertTrue(new DataValueFloat(Float.NaN).getEnum(Direction.class) == Direction.DOWN, "NaN truncates to 0, the first constant");
		helper.assertTrue(new DataValueFloat(5F).getEnum(Direction.class) == Direction.EAST, "the last in range is the last constant");
		helper.succeed();
	}

	// ------------------------------------------------------------------ DataValueString

	@GameTest(template = EMPTY)
	public static void stringsRoundTripAndReadAsNumbers(GameTestHelper helper) {
		String[] samples = {"", "abc", "12.5", "piλ \"quoted\" é", "  3 ", "1e3", "NaN", "true"};
		for(String s : samples) {
			DataValueString v = new DataValueString(s);
			Tag tag = v.writeToNBT();
			helper.assertTrue(tag instanceof StringTag, "a string is a string tag on the wire: " + s);
			DataValue back = DataValue.newFromNBT(tag);
			helper.assertTrue(back instanceof DataValueString && ((DataValueString) back).str.equals(s), "it reads back verbatim: " + s);
			helper.assertTrue(back.equals(v) && back.hashCode() == v.hashCode(), "and equals agrees: " + s);
		}
		helper.assertTrue(new DataValueString("12.5").getNumber() == 12.5F, "numeric text reads as its number");
		helper.assertTrue(new DataValueString("abc").getNumber() == 0F, "other text reads as 0");
		helper.assertTrue(new DataValueString("  3 ").getNumber() == 3F, "parseFloat trims whitespace, so padded text reads");
		helper.assertTrue(new DataValueString("1e3").getNumber() == 1000F, "exponents read");
		helper.assertTrue(Float.isNaN(new DataValueString("NaN").getNumber()), "the text NaN reads as NaN, parseFloat's rule");
		DataValueString s = new DataValueString("1");
		s.set("7");
		helper.assertTrue(s.getNumber() == 7F && s.str.equals("7"), "set() refreshes the cached reading");
		helper.assertTrue(new DataValueString("true").getBoolean(), "only the exact text true is true");
		helper.assertTrue(!new DataValueString("True").getBoolean() && !new DataValueString("1").getBoolean()
				&& !new DataValueString("").getBoolean(), "Capitalised, 1 and empty are all false");
		helper.assertTrue(new DataValueString("x").getType() == DataType.STRING, "its type is STRING");
		helper.assertTrue(new DataValueString("north").getEnum(Direction.class) == Direction.NORTH, "an enum reading matches the constant name, ignoring case");
		helper.assertTrue(new DataValueString("NoRtH").getEnum(Direction.class) == Direction.NORTH, "mixed case too");
		helper.assertTrue(new DataValueString("bogus").getEnum(Direction.class) == Direction.DOWN, "no match is the first constant");
		DataValueString orig = new DataValueString("q");
		DataValue copy = orig.copy();
		((DataValueString) copy).set("r");
		helper.assertTrue(orig.str.equals("q"), "a copy is independent");
		helper.succeed();
	}

	// ------------------------------------------------------------------ DataValueEnum

	@GameTest(template = EMPTY)
	public static void directionsAndDyesRoundTrip(GameTestHelper helper) {
		for(Direction d : Direction.values()) {
			DataValue back = DataValue.newFromNBT(new DataValueEnum<>(d).writeToNBT());
			helper.assertTrue(back instanceof DataValueEnum<?> e && e.value == d, "direction round trips: " + d);
			helper.assertTrue(back.getEnum(Direction.class) == d && back.getNumber() == d.ordinal(), "and reads as itself and its ordinal: " + d);
		}
		for(DyeColor c : DyeColor.values()) {
			DataValue back = DataValue.newFromNBT(new DataValueEnum<>(c).writeToNBT());
			helper.assertTrue(back instanceof DataValueEnum<?> e && e.value == c, "dye colour round trips: " + c);
		}
		helper.succeed();
	}

	/**
	 * A stored ordinal must mean the same constant as it did in 1.12.2, where {@code EnumFacing} and {@code EnumDyeColor}
	 * had this order ({@code SILVER} is today's {@code LIGHT_GRAY}).
	 */
	@GameTest(template = EMPTY)
	public static void enumOrdinalsAreCEs(GameTestHelper helper) {
		String[] facing = {"DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"};
		Direction[] d = Direction.values();
		helper.assertTrue(d.length == facing.length, "six directions");
		for(int i = 0; i < facing.length; i++)
			helper.assertTrue(d[i].name().equals(facing[i]), "direction " + i + " is " + facing[i] + ", got " + d[i].name());
		String[] dye = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN",
				"PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
		DyeColor[] c = DyeColor.values();
		helper.assertTrue(c.length == dye.length, "sixteen dye colours");
		for(int i = 0; i < dye.length; i++)
			helper.assertTrue(c[i].name().equals(dye[i]), "dye " + i + " is " + dye[i] + ", got " + c[i].name());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void enumWireShape(GameTestHelper helper) {
		Tag tag = new DataValueEnum<>(Direction.EAST).writeToNBT();
		helper.assertTrue(tag instanceof CompoundTag, "an enum is a compound");
		CompoundTag c = (CompoundTag) tag;
		helper.assertTrue(c.getAllKeys().equals(Set.of("enum", "ordinal")), "with exactly the keys enum and ordinal, got " + c.getAllKeys());
		helper.assertTrue(c.getString("enum").equals("direction") && c.getInt("ordinal") == 5 && c.contains("ordinal", Tag.TAG_INT),
				"east is direction / int 5");
		CompoundTag dye = (CompoundTag) new DataValueEnum<>(DyeColor.RED).writeToNBT();
		helper.assertTrue(dye.getString("enum").equals("dye_color") && dye.getInt("ordinal") == 14, "red is dye_color / 14");
		DataValue e = new DataValueEnum<>(Direction.NORTH);
		helper.assertTrue(e.getType() == DataType.ENUM, "its type is ENUM");
		helper.assertTrue(e.getEnum(DyeColor.class) == DyeColor.WHITE, "read as another enum it is that enum's first constant");
		helper.assertTrue(!e.getBoolean(), "an enum is not 'true'");
		helper.assertTrue("north".equals(e.toString()), "it prints as the constant's own toString, which is lower case for a direction, got " + e);
		helper.assertTrue(((DataValueEnum<?>) e).getPossibleValues().length == 6, "and lists its class's constants");
		helper.assertTrue(e.copy().equals(e) && e.copy() != e, "a copy equals");
		helper.assertTrue(!e.equals(new DataValueEnum<>(Direction.SOUTH)), "and different constants differ");
		helper.succeed();
	}

	/** Set when {@link Sentinel}'s static initialiser runs: the proof that a class name off the wire is never loaded. */
	static volatile boolean sentinelLoaded = false;

	/** A class whose initialisation is observable. */
	public static final class Sentinel {
		static {
			sentinelLoaded = true;
		}
	}

	private enum Probe { A, B }

	/**
	 * CE read {@code clazz} with {@code Class.forName}, on client-authored NBT. The port has a closed registry, so every one of
	 * these hostile tags is simply refused, and none loads a class.
	 */
	@GameTest(template = EMPTY)
	public static void enumRegistryIsClosed(GameTestHelper helper) {
		Set<String> ids = DataValueEnum.registeredIds();
		helper.assertTrue(ids.contains("direction") && ids.contains("dye_color"), "the core's two enums are registered, got " + ids);
		helper.assertTrue(DataValueEnum.classOf("direction") == Direction.class && DataValueEnum.idOf(DyeColor.class).equals("dye_color"),
				"and map both ways");
		helper.assertTrue(DataValueEnum.classOf("java.lang.Thread") == null && DataValueEnum.classOf("") == null, "an arbitrary string is not an id");

		CompoundTag legacy = new CompoundTag();
		legacy.putString("clazz", Sentinel.class.getName());
		legacy.putInt("ordinal", 0);
		sentinelLoaded = false;
		helper.assertTrue(DataValue.newFromNBT(legacy) == null, "CE's clazz tag is refused, not resolved");
		helper.assertTrue(!sentinelLoaded, "and the class it named was not initialised");

		CompoundTag unknown = new CompoundTag();
		unknown.putString("enum", Sentinel.class.getName());
		unknown.putInt("ordinal", 0);
		helper.assertTrue(DataValue.newFromNBT(unknown) == null && !sentinelLoaded, "a class name in the enum key is refused too");

		CompoundTag nope = new CompoundTag();
		nope.putString("enum", "no_such_enum");
		nope.putInt("ordinal", 0);
		helper.assertTrue(DataValue.newFromNBT(nope) == null, "an unregistered id is refused");

		for(int bad : new int[]{6, 7, 100, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			CompoundTag t = new CompoundTag();
			t.putString("enum", "direction");
			t.putInt("ordinal", bad);
			helper.assertTrue(DataValue.newFromNBT(t) == null, "ordinal " + bad + " is out of range for six directions and refused");
		}
		CompoundTag wrongKind = new CompoundTag();
		wrongKind.putString("enum", "direction");
		wrongKind.putString("ordinal", "2");
		helper.assertTrue(DataValue.newFromNBT(wrongKind) instanceof DataValueComposite, "a string 'ordinal' is not an enum; it is a pack, as CE read it");
		CompoundTag noOrdinal = new CompoundTag();
		noOrdinal.putString("enum", "direction");
		helper.assertTrue(DataValue.newFromNBT(noOrdinal) instanceof DataValueComposite, "no ordinal means a pack");

		boolean threw = false;
		try {
			new DataValueEnum<>(Probe.A).writeToNBT();
		} catch(IllegalStateException e) {
			threw = true;
		}
		helper.assertTrue(threw, "writing an enum nobody registered fails loudly, so it cannot be saved as something unreadable");

		DataValueEnum.register("direction", Direction.class);
		boolean clash = false;
		try {
			DataValueEnum.register("direction", Probe.class);
		} catch(IllegalArgumentException e) {
			clash = true;
		}
		helper.assertTrue(clash && DataValueEnum.classOf("direction") == Direction.class, "an id cannot be taken over by another class");
		helper.succeed();
	}

	// ------------------------------------------------------------------ DataValueComposite

	@GameTest(template = EMPTY)
	public static void compositesPackStrings(GameTestHelper helper) {
		DataValueComposite c = new DataValueComposite();
		c.setValueOf("a", "1");
		c.setValueOf("b", "two");
		c.setValueOf("", "ignored");
		c.setValueOf("   ", "ignored");
		helper.assertTrue(c.values().equals(Set.of("a", "b")), "a blank key is ignored, got " + c.values());
		c.setValueOf("b", "  ");
		helper.assertTrue(c.values().equals(Set.of("a")) && c.getValueOf("b").isEmpty(), "a blank value removes the entry");
		helper.assertTrue(c.getValueOf("zzz").isEmpty(), "an absent key reads as the empty string");
		helper.assertTrue(c.getType() == DataType.COMPOSITE && c.getNumber() == 0F && !c.getBoolean(), "its type, number and boolean");
		helper.assertTrue("< PACKED >".equals(c.toString()), "it prints as a placeholder");
		boolean threw = false;
		try {
			c.getEnum(Direction.class);
		} catch(UnsupportedOperationException e) {
			threw = true;
		}
		helper.assertTrue(threw, "it has no enum reading");

		c.setValueOf("ordinal", "5");
		CompoundTag tag = (CompoundTag) c.writeToNBT();
		helper.assertTrue(tag.getAllKeys().equals(Set.of("a", "ordinal")) && tag.getString("a").equals("1"), "it is a compound of strings");
		DataValue back = DataValue.newFromNBT(tag);
		helper.assertTrue(back instanceof DataValueComposite && back.equals(c) && back.hashCode() == c.hashCode(),
				"a string 'ordinal' key does not turn it into an enum, and it round trips");

		DataValueComposite copy = (DataValueComposite) c.copy();
		copy.setValueOf("a", "9");
		helper.assertTrue(c.getValueOf("a").equals("1"), "a copy is independent");
		Map<String, String> snap = c.snapshot();
		snap.put("x", "y");
		helper.assertTrue(!c.values().contains("x"), "a snapshot is a copy");
		Map<Object, Object> lua = new LinkedHashMap<>();
		lua.put("k", 4);
		lua.put(2, "v");
		DataValueComposite fromLua = DataValueComposite.fromLuaTable(lua);
		helper.assertTrue(fromLua.getValueOf("k").equals("4") && fromLua.getValueOf("2").equals("v"), "fromLuaTable stringifies keys and values");
		helper.assertTrue(c.getValueOf("a").equals("1") && new DataValueFloat(1).getValueOf("a").equals("ERROR"),
				"a non-composite answers ERROR to getValueOf, and ignores setValueOf");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aTagThatIsNotAValueIsNull(GameTestHelper helper) {
		helper.assertTrue(DataValue.newFromNBT(null) == null, "null reads as null");
		helper.assertTrue(DataValue.newFromNBT(IntTag.valueOf(3)) == null, "an int is not a value");
		helper.assertTrue(DataValue.newFromNBT(ByteTag.valueOf((byte) 1)) == null, "nor is a byte");
		helper.assertTrue(DataValue.newFromNBT(new CompoundTag()) instanceof DataValueComposite, "an empty compound is an empty pack");
		helper.succeed();
	}

	// ------------------------------------------------------------------ DataType

	@GameTest(template = EMPTY)
	public static void dataTypeColoursAndOrder(GameTestHelper helper) {
		DataType[] v = DataType.VALUES;
		helper.assertTrue(v.length == 5 && v[0] == DataType.GENERIC && v[1] == DataType.NUMBER && v[2] == DataType.STRING
				&& v[3] == DataType.ENUM && v[4] == DataType.COMPOSITE, "GENERIC, NUMBER, STRING, ENUM, COMPOSITE: the order is the wire's (a socket stores its ordinal)");
		float[][] want = {{0.5F, 0.5F, 0.5F}, {0.4F, 0.6F, 0F}, {0F, 1F, 1F}, {0.5F, 0.29F, 0F}, {1F, 0.399F, 0.842F}};
		for(int i = 0; i < want.length; i++)
			helper.assertTrue(java.util.Arrays.equals(v[i].getColor(), want[i]), v[i] + " has its colour");
		helper.succeed();
	}

	// ------------------------------------------------------------------ ControlType

	@GameTest(template = EMPTY)
	public static void controlTypesAreCEsNine(GameTestHelper helper) {
		String[] want = {"Button", "Switch", "Slider", "Knob", "Dial", "Display", "Indicator", "Meter", "Label"};
		ControlType[] have = {ControlType.BUTTON, ControlType.SWITCH, ControlType.SLIDER, ControlType.KNOB, ControlType.DIAL,
				ControlType.DISPLAY, ControlType.INDICATOR, ControlType.METER, ControlType.LABEL};
		for(int i = 0; i < want.length; i++) {
			helper.assertTrue(have[i].name.equals(want[i]), "type " + i + " is " + want[i]);
			helper.assertTrue(ControlType.ALL_VALUES.indexOf(have[i]) == i, want[i] + " is registered at position " + i);
			helper.assertTrue(ControlType.getByName(want[i]) == have[i] && ControlType.registerOrGet(want[i]) == have[i],
					want[i] + " is found by name and registerOrGet returns the same one");
		}
		helper.assertTrue(ControlType.getByName("Nope") == null, "an unknown name is null");
		helper.succeed();
	}

	// ------------------------------------------------------------------ ControlEvent

	private static String describe(ControlEvent e) {
		StringBuilder b = new StringBuilder();
		for(Map.Entry<String, DataValue> v : e.vars.entrySet()) {
			if(b.length() > 0) b.append(',');
			b.append(v.getKey()).append('=');
			switch(v.getValue().getType()) {
				case STRING -> b.append('"').append(v.getValue()).append('"');
				default -> b.append(v.getValue());
			}
		}
		return b.toString();
	}

	/** CE's {@code ControlEvent.init()} then Leafia's {@code MixinControlEvent} tail, in order, with every default. */
	private static final String[][] EVENTS = {
			{"tick", "time=0.0"},
			{"door_open_state", "state=0.0"},
			{"door_toggle", "passcode=0.0"},
			{"turret_set_target", "players=0.0,passive=0.0,hostile=1.0,machines=1.0"},
			{"turret_switch", "isOn=1.0,isBlacklistFilter=1.0"},
			{"lever_toggle", "isOn=0.0"},
			{"spinny_light_power", "isOn=0.0"},
			{"siren_set_state", "isOn=0.0"},
			{"tank_set_mode", "mode=0.0"},
			{"rbmk_ctrl_set_color", "color=0.0"},
			{"rbmk_ctrl_set_level", "level=0.0"},
			{"rbmk_crane_move", "up=0.0,down=0.0,left=0.0,right=0.0"},
			{"rbmk_crane_load", ""},
			{"ctrl_press", "isSneaking=0.0"},
			{"redstone_input", "facing=north,power=0.0,weak=0.0,strong=0.0,isPowered=0.0,isWeaklyPowered=0.0,isStronglyPowered=0.0"},
			{"initialize", ""},
			{"set_booster_level", "level=0.0"},
			{"set_booster_active", "active=0.0"},
			{"set_absorber_level", "level=0.0"},
			{"set_stabilizer_level", "level=0.0"},
			{"torch_set_state", "isOn=0.0"},
			{"pwr_ctrl_set_level", "level=0.0"},
			{"lftr_ctrl_set_level", "level=0.0"},
			{"pwr_computer_set_master", "level=0.0"},
			{"pwr_computer_set_level", "name=\"\",level=0.0"},
			{"set_pump_buffer", "size=0.0"},
			{"set_pump_pressure", "pu=0.0"},
			{"set_pump_priority", "priority=2.0"},
			{"zirnox_ctrl_set_level", "level=0.0"},
			{"setGlobalVar", "name=\"\",value=\"\""},
			{"setIOMode", "mode=0.0"},
			{"heater_set", "heatPerTick=0.0"},
			{"burner_set", "isOn=0.0"},
			{"set_pulser_active", "active=0.0,code=\"\""},
			{"set_pulser_detonation", "charge=0.0,code=\"\""},
	};

	@GameTest(template = EMPTY)
	public static void theEventTableAndItsDefaults(GameTestHelper helper) {
		helper.assertTrue(EVENTS.length == 35, "sixteen of CE's and nineteen of Leafia's");
		Set<String> names = new LinkedHashSet<>();
		for(String[] row : EVENTS) {
			names.add(row[0]);
			helper.assertTrue(ControlEvent.isRegistered(row[0]), row[0] + " is registered");
			String got = describe(ControlEvent.getRegisteredEvent(row[0]));
			helper.assertTrue(got.equals(row[1]), row[0] + " defaults to [" + row[1] + "], got [" + got + "]");
			helper.assertTrue(ControlEvent.getRegisteredEvent(row[0]).name.equals(row[0]), row[0] + " carries its own name");
		}
		helper.assertTrue(names.size() == 35, "no name appears twice");
		helper.assertTrue(ControlEvent.registeredNames().containsAll(names), "and the registry holds them all");
		helper.assertTrue(!ControlEvent.isRegistered("not_an_event"), "an unknown name is not registered");
		boolean threw = false;
		try {
			ControlEvent.getRegisteredEvent("not_an_event");
		} catch(RuntimeException e) {
			threw = e.getMessage().contains("not_an_event");
		}
		helper.assertTrue(threw, "asking for it throws, naming it");

		ControlEvent tick = ControlEvent.getRegisteredEvent("tick");
		ControlEvent.init();
		helper.assertTrue(ControlEvent.getRegisteredEvent("tick") == tick, "init() a second time re-registers nothing");

		helper.assertTrue(ControlEvent.getRegisteredEvent("redstone_input").vars.get("facing").getType() == DataType.ENUM,
				"redstone_input's facing is an enum");
		helper.assertTrue(((DataValueEnum<?>) ControlEvent.getRegisteredEvent("redstone_input").vars.get("facing")).value == Direction.NORTH,
				"and defaults to north");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNewEventIsACopyOfTheTemplate(GameTestHelper helper) {
		ControlEvent a = ControlEvent.newEvent("lever_toggle");
		ControlEvent b = ControlEvent.newEvent("lever_toggle");
		helper.assertTrue(a != b && a.vars != b.vars, "two new events share nothing");
		a.setVar("isOn", true);
		helper.assertTrue(b.vars.get("isOn").getNumber() == 0F, "setting one does not set the other");
		helper.assertTrue(describe(ControlEvent.getRegisteredEvent("lever_toggle")).equals("isOn=0.0"), "nor the template");
		ControlEvent c = ControlEvent.newEvent("redstone_input");
		((DataValueEnum<?>) c.vars.get("facing")).value = null;
		helper.assertTrue(((DataValueEnum<?>) ControlEvent.getRegisteredEvent("redstone_input").vars.get("facing")).value == Direction.NORTH,
				"a copy's enum value is its own too");

		ControlEvent set = new ControlEvent("x").setVar("n", 3).setVar("b", true).setVar("s", "hi").setVar("e", Direction.UP)
				.setVar("v", new DataValueString("z"));
		helper.assertTrue(describe(set).equals("n=3.0,b=1.0,s=\"hi\",e=up,v=\"z\""), "the five setVar overloads, in insertion order, got " + describe(set));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anEventRoundTripsThroughNbt(GameTestHelper helper) {
		ControlEvent e = ControlEvent.newEvent("pwr_computer_set_level");
		e.setVar("name", "rod 7").setVar("level", 42.5F);
		CompoundTag tag = e.writeToNBT(new CompoundTag());
		helper.assertTrue(tag.getString("name").equals("pwr_computer_set_level") && tag.contains("vars", Tag.TAG_COMPOUND),
				"a name and a vars compound");
		ControlEvent back = ControlEvent.readFromNBT(tag);
		helper.assertTrue(back.name.equals(e.name) && describe(back).equals(describe(e)), "it reads back as written: " + describe(back));

		CompoundTag vars = tag.getCompound("vars");
		vars.put("junk", IntTag.valueOf(1));
		vars.put("extra", FloatTag.valueOf(9F));
		ControlEvent lenient = ControlEvent.readFromNBT(tag);
		helper.assertTrue(!lenient.vars.containsKey("junk"), "a var that is not a value is dropped");
		helper.assertTrue(lenient.vars.get("extra").getNumber() == 9F, "and one that is a value is kept even if the template lacks it");

		CompoundTag bad = new CompoundTag();
		bad.putString("name", "not_an_event");
		boolean threw = false;
		try {
			ControlEvent.readFromNBT(bad);
		} catch(RuntimeException ex) {
			threw = true;
		}
		helper.assertTrue(threw, "an event of an unregistered name throws: the loaders turn that into a placeholder");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the class-load sweep (§2.2)

	private static final String CORE = "com/leafia/contents/machines/controlpanel/core";

	/** Every common class of the subsystem as of this batch; the discovery below adds anything else under {@code core}. */
	private static final String[] COMMON = {
			"ControlEvent", "ControlEventSystem", "IControllable", "Control", "ControlPanel", "NodeConnection", "NodeElement",
			"NodeSystem", "UnknownNode", "types/DataValue", "types/DataValueFloat", "types/DataValueString", "types/DataValueEnum",
			"types/DataValueComposite", "controls/ControlType", "nodes/Node", "nodes/IInputNode", "nodes/IOutputNode",
			"nodes/IFunctionNode", "modular/INodeLoader", "modular/NTMControlPanelRegistry", "ItemList", "NodeDropdown", "NodeButton",
			"nodes/NodeInput", "nodes/NodeOutput", "nodes/NodeMath", "nodes/NodeBoolean", "nodes/NodeFunction", "nodes/NodeBuffer",
			"nodes/NodeConditional", "nodes/NodeCompositeRead", "nodes/NodeCompositeWrite", "nodes/NodeCancelEvent", "nodes/NodeGetVar",
			"nodes/NodeSetVar", "nodes/NodeEventBroadcast", "nodes/NodeQueryBlock", "modular/StockNodesRegister", "modular/StockNodeLoader",
			// CCP-NODE-EDITOR (D-3067): the editor's logic is common, its pages and picture are not
			"ITypableNode", "NodeTextBox", "NodeClipboard", "NodeSystemEditor", "ControlVariables", "modular/INodeMenuCreator", "modular/NodeEditContext",
			"modular/NodeAddMenu", "modular/categories/NCStockInput", "modular/categories/NCStockOutput", "modular/categories/NCStockMath",
			"modular/categories/NCStockBoolean", "modular/categories/NCStockLogic", "modular/categories/NCStockComposite",
	};

	private static byte[] classBytes(String internalName) throws Exception {
		try(InputStream in = LeafiaControlPanelDataTests.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	}

	private static boolean mentionsClient(byte[] bytes) {
		String s = new String(bytes, StandardCharsets.ISO_8859_1);
		return s.contains("net/minecraft/client") || s.contains("com/mojang/blaze3d") || s.contains("org/lwjgl")
				|| s.contains("com/mojang/realmsclient") || s.contains(CORE + "/client");
	}

	/** Every class file under {@code core} that is not in {@code core/client}, found on disk; empty if the classpath cannot be walked. */
	private static List<String> discovered() {
		List<String> found = new ArrayList<>();
		try {
			URL url = LeafiaControlPanelDataTests.class.getClassLoader().getResource(CORE);
			if(url == null) return found;
			Path root = Paths.get(url.toURI());
			try(Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
					String rel = root.relativize(p).toString().replace('\\', '/');
					rel = rel.substring(0, rel.length() - ".class".length());
					if(!rel.startsWith("client/")) found.add(rel);
				});
			}
		} catch(Exception e) {
			found.clear();
		}
		return found;
	}

	/**
	 * §2.2: no class outside {@code core.client} names a {@code net.minecraft.client} type, even in a method body. Proved on the
	 * class files themselves (a constant-pool reference is what loads a class on a dedicated server), and by initialising every
	 * one, so a static initialiser that reached for one would fail here too.
	 */
	@GameTest(template = EMPTY)
	public static void noCommonClassNamesAClientType(GameTestHelper helper) throws Exception {
		// A positive control first: the scan must flag a class that does name client types, or a clean result means nothing.
		byte[] known = classBytes("com/leafia/client/LeafiaClient");
		helper.assertTrue(known != null && mentionsClient(known), "the scanner flags LeafiaClient, which is a client class");

		Set<String> all = new LinkedHashSet<>();
		for(String s : COMMON) all.add(s);
		all.addAll(discovered());
		helper.assertTrue(all.size() >= COMMON.length, "the list is not empty");

		for(String rel : all) {
			String internal = CORE + "/" + rel;
			byte[] bytes = classBytes(internal);
			helper.assertTrue(bytes != null, "class file found: " + internal);
			helper.assertTrue(!mentionsClient(bytes), internal + " names a client class, a dedicated server would fail to load it");
			if(!rel.contains("$")) {
				Class.forName(internal.replace('/', '.'), true, LeafiaControlPanelDataTests.class.getClassLoader());
			}
		}
		helper.succeed();
	}
}
