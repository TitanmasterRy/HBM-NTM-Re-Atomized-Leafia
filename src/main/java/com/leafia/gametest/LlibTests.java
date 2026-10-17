package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.leafia.lib.LeafiaRef;
import com.llib.LeafiaLib;
import com.llib.group.LeafiaMap;
import com.llib.group.LeafiaSet;
import com.llib.math.LeafiaColor;
import com.llib.math.MathLeafia;
import com.llib.math.SIPfx;
import com.llib.math.range.RangeDouble;
import com.llib.math.range.RangeInt;
import com.llib.technical.BitByteBuf;
import com.llib.technical.FifthString;
import com.llib.technical.FiaLatch;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins {@code com.llib}, the addon's utility layer (L1).
 *
 * <p>None of this is Minecraft code, so none of it would fail loudly if it were ported wrong —
 * an SI prefix off by one factor of a thousand or an ARGB shift off by eight bits produces a
 * number, not a crash, and the first thing to notice would be a GUI reading the wrong value
 * three batches later. These tests are the noise floor for that.
 *
 * <p>Two things here are load-bearing beyond ordinary coverage:
 * {@link #colourRoundTripsThroughInverseArgb} pins the octal shift literals ({@code 0_20} is
 * octal 16, not twenty), which look like a typo and are not; and
 * {@link #bufferReadsItsPayloadBackwards} pins the reversed byte order that is the entire point
 * of {@code BitByteBuf} and the reason upstream could not use a plain {@code ByteBuf}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LlibTests {

	private static final String EMPTY = "empty";

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-9,
				what + ": expected " + expected + ", got " + actual);
	}

	// ------------------------------------------------------------------ SIPfx

	@GameTest(template = EMPTY)
	public static void siPrefixesScaleByThousands(GameTestHelper helper) {

		helper.assertTrue(SIPfx.getExponent(999) == 0, "999 is below the first prefix");
		helper.assertTrue(SIPfx.getExponent(1500) == 1, "1500 is kilo");
		helper.assertTrue(SIPfx.getExponent(2.5e6) == 2, "2.5 million is mega");
		helper.assertTrue(SIPfx.getExponent(1e40) == 9,
				"the exponent is clamped at 9 (quetta), the last prefix in the table");

		near(helper, SIPfx.scale(1500, 1), 1.5, "scale");
		near(helper, SIPfx.round(1.567, 2), 1.57, "round up");
		near(helper, SIPfx.round(1.564, 2), 1.56, "round down");
		// Not 1.005 -> 1.01: the nearest double to 1.005 is a shade below it, so floor(x*100+0.5)
		// lands on 100 and the answer is 1.0. That is binary floating point, not a porting error,
		// and choosing a test value that hides it would be choosing one that lies.
		near(helper, SIPfx.round(1.005, 2), 1.0, "half-way cases follow the double, not the decimal");

		helper.assertTrue(SIPfx.auto(1500).equals("1.5 k"), "auto(1500) was " + SIPfx.auto(1500));
		helper.assertTrue(SIPfx.auto(2.5e6).equals("2.5 M"), "auto(2.5e6) was " + SIPfx.auto(2.5e6));
		helper.assertTrue(SIPfx.auto(2.5e6, true).equals("2.5 Mega"),
				"the full form spells the prefix out, got " + SIPfx.auto(2.5e6, true));

		// parse is the inverse, and it is what a player types into a control panel field.
		near(helper, SIPfx.parse("2 k"), 2000.0, "parse with a space");
		near(helper, SIPfx.parse("5M"), 5.0e6, "parse without a space");
		near(helper, SIPfx.parse("42"), 42.0, "parse with no prefix at all");

		helper.succeed();
	}

	// ------------------------------------------------------------------ LeafiaColor

	/**
	 * The channel shifts are written {@code 0_20}, {@code 0_10} and {@code 0_30} — Java octal
	 * literals for 16, 8 and 24. Anyone reading them as decimal would "fix" them into shifts of
	 * twenty, ten and thirty and silently corrupt every colour in the addon, so the round trip
	 * is pinned rather than the literals.
	 *
	 * <p>The format is upstream's <b>inverse</b> ARGB: alpha 0x00 is opaque and 0xFF invisible.
	 */
	@GameTest(template = EMPTY)
	public static void colourRoundTripsThroughInverseArgb(GameTestHelper helper) {

		LeafiaColor c = new LeafiaColor(0x00FF8040);
		near(helper, c.red, 1.0, "red channel");
		near(helper, c.green, 128 / 255d, "green channel");
		near(helper, c.blue, 64 / 255d, "blue channel");
		near(helper, c.alpha, 1.0, "alpha 0x00 means fully opaque in this format");

		helper.assertTrue(c.toInARGB() == 0x00FF8040,
				"inverse-ARGB should round trip, got " + Integer.toHexString(c.toInARGB()));
		helper.assertTrue(c.toARGB() == 0xFFFF8040,
				"ordinary ARGB inverts the alpha, got " + Integer.toHexString(c.toARGB()));

		// A transparent colour is 0xFF alpha going in, and comes back the same way.
		LeafiaColor clear = new LeafiaColor(0xFF000000);
		near(helper, clear.alpha, 0.0, "alpha 0xFF means fully transparent in this format");
		helper.assertTrue(clear.toInARGB() == 0xFF000000, "transparent should round trip");

		LeafiaColor mid = new LeafiaColor(1, 0, 0).lerp(new LeafiaColor(0, 0, 1), 0.5);
		near(helper, mid.red, 0.5, "lerp red");
		near(helper, mid.blue, 0.5, "lerp blue");

		LeafiaColor hsv = LeafiaColor.fromHSV(120, 1, 1);
		near(helper, hsv.red, 0.0, "hue 120 is pure green");
		near(helper, hsv.green, 1.0, "hue 120 is pure green");
		near(helper, hsv.blue, 0.0, "hue 120 is pure green");

		helper.succeed();
	}

	// ------------------------------------------------------------------ MathLeafia

	@GameTest(template = EMPTY)
	public static void smoothLinearIsClampedAndSeamless(GameTestHelper helper) {

		near(helper, MathLeafia.smoothLinear(0, 1), 0.0, "starts at 0");
		near(helper, MathLeafia.smoothLinear(1, 1), 1.0, "ends at 1");
		near(helper, MathLeafia.smoothLinear(2, 1), 1.0, "input above 1 clamps");
		near(helper, MathLeafia.smoothLinear(-1, 1), 0.0, "input below 0 clamps");
		near(helper, MathLeafia.smoothLinear(0.5, 0), 0.5,
				"a scale under 1e-6 short-circuits to the identity, avoiding a divide by zero");

		near(helper, MathLeafia.positiveModulo(-1.0, 4.0), 3.0, "negative modulo comes out positive");
		near(helper, MathLeafia.positiveModulo(5.0, 4.0), 1.0, "positive modulo is unchanged");

		helper.succeed();
	}

	// ------------------------------------------------------------------ ranges

	@GameTest(template = EMPTY)
	public static void rangesLerpAndIterate(GameTestHelper helper) {

		RangeInt r = new RangeInt(0, 10);
		near(helper, r.lerp(0.5), 5.0, "lerp");
		near(helper, r.ratio(5.0), 0.5, "ratio is the inverse of lerp");
		helper.assertTrue(r.lerpRound(0.55) == 6, "lerpRound rounds, got " + r.lerpRound(0.55));
		helper.assertTrue(r.lerpFloor(0.55) == 5, "lerpFloor floors, got " + r.lerpFloor(0.55));
		helper.assertTrue(r.isInRange(10) && !r.isInRange(11), "the range is inclusive at both ends");

		int count = 0;
		for(int i : r) count++;
		helper.assertTrue(count == 11, "iterating 0..10 inclusive yields 11 values, got " + count);

		// RangeBase.equals compares through doubleMin/doubleMax, so ranges of different
		// primitive types over the same span are equal. That is deliberate upstream.
		helper.assertTrue(r.equals(new RangeDouble(0, 10)),
				"an int range and a double range over the same span compare equal");

		RangeInt clone = r.clone();
		clone.max = 20;
		helper.assertTrue(r.max == 10, "clone should be independent of the original");

		helper.succeed();
	}

	// ------------------------------------------------------------------ LeafiaSet / LeafiaMap

	/**
	 * The set's headline claim is that it survives being modified while something iterates it —
	 * its iterator works off a snapshot. That is what the addon's tick loops rely on, and a
	 * {@code ConcurrentModificationException} out of one is the failure it exists to prevent.
	 */
	@GameTest(template = EMPTY)
	public static void setToleratesModificationDuringIteration(GameTestHelper helper) {

		LeafiaSet<String> set = new LeafiaSet<>();
		set.add("a");
		set.add("b");
		set.add("c");
		helper.assertTrue(set.size() == 3, "three elements");
		helper.assertTrue(!set.add("b"), "adding a duplicate is refused");
		helper.assertTrue(set.size() == 3, "and does not grow the set");

		List<String> seen = new ArrayList<>();
		for(String s : set) {
			seen.add(s);
			set.remove("c"); // would be a ConcurrentModificationException on a HashSet
		}
		helper.assertTrue(seen.size() == 3, "the iterator walks the snapshot it started with");
		helper.assertTrue(set.size() == 2, "and the removal still took effect");

		set.clear();
		set.add("x");
		helper.assertTrue(set.indexOf("x") == 0, "indexOf reports the backing array position");
		helper.assertTrue(set.indexOf("nope") == -1, "and -1 for an absent element");
		helper.assertTrue(set.get(0).equals("x"), "get indexes the backing array");

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void mapStoresAndRemovesByValueEquality(GameTestHelper helper) {

		LeafiaMap<String, Integer> map = new LeafiaMap<>();
		map.put("one", 1);
		map.put("two", 2);
		helper.assertTrue(map.size() == 2, "two entries");
		helper.assertTrue(map.get("one") == 1, "get by key");
		helper.assertTrue(map.get("missing") == null, "an absent key gives null");
		helper.assertTrue(map.containsValue(2), "containsValue searches the value array");
		helper.assertTrue(map.getKeyFor(2).equals("two"), "and getKeyFor reverses the lookup");

		helper.assertTrue(map.put("one", 11) == 1, "put returns the previous value");
		helper.assertTrue(map.get("one") == 11, "and overwrites it");
		helper.assertTrue(map.putIfAbsent("one", 111) == 11, "putIfAbsent returns the existing value");
		helper.assertTrue(map.get("one") == 11, "and leaves it alone");

		helper.assertTrue(map.remove("one") == 11, "remove returns what it removed");
		helper.assertTrue(map.size() == 1, "and shrinks the map");
		helper.assertTrue(map.get("two") == 2, "leaving the other entry intact and correctly indexed");

		helper.succeed();
	}

	// ------------------------------------------------------------------ FiaLatch

	@GameTest(template = EMPTY)
	public static void latchDetectsChangeSinceLastUpdate(GameTestHelper helper) {

		FiaLatch<String> latch = new FiaLatch<>("start");
		helper.assertTrue(!latch.needsUpdate(), "a fresh latch is in sync");
		latch.set("changed");
		helper.assertTrue(latch.needsUpdate(), "a changed value needs syncing");
		latch.update();
		helper.assertTrue(!latch.needsUpdate(), "and is in sync again once updated");

		helper.succeed();
	}

	// ------------------------------------------------------------------ FifthString

	/**
	 * A five-bit codec: 26 letters plus space and underscore fit in one code each, capitals are
	 * a control code rather than a separate alphabet, and anything else escapes into a UTF run.
	 * Round trips are the only property worth pinning — the exact code sequence is an internal
	 * detail, but a string that does not survive the trip is a corrupted packet.
	 */
	@GameTest(template = EMPTY)
	public static void fifthStringRoundTrips(GameTestHelper helper) {

		String[] cases = {
				"hello world",
				"Hello World",      // one-off capitals
				"HELLO",            // a capital run, which toggles instead
				"snake_case_name",
				"",
				"mixed Case_And 123",  // digits are not in the alphabet, so they escape
				"unicode: éü中",
		};
		for(String s : cases) {
			String out = new FifthString(s).toString();
			helper.assertTrue(out.equals(s), "round trip failed: " + s + " -> " + out);
		}

		helper.succeed();
	}

	// ------------------------------------------------------------------ BitByteBuf

	/**
	 * The buffer's payload runs <em>backwards</em>: stream byte 0 is the last array element.
	 * That is not an accident to be tidied up — it is why the addon can prepend to a packet
	 * without moving what is already in it, and every read offset in the addon assumes it.
	 */
	@GameTest(template = EMPTY)
	public static void bufferReadsItsPayloadBackwards(GameTestHelper helper) {

		BitByteBuf buf = new TestBuf();
		buf.wrapOwned(new byte[] {0x12, 0x34});

		helper.assertTrue(buf.payloadLength() == 2, "two bytes of payload");
		helper.assertTrue(buf.extractBits(0, 7) == 0x34,
				"stream byte 0 is the LAST array byte, got " + Integer.toHexString(buf.extractBits(0, 7)));
		helper.assertTrue(buf.extractBits(8, 15) == 0x12,
				"stream byte 1 is the first array byte, got " + Integer.toHexString(buf.extractBits(8, 15)));
		helper.assertTrue(buf.extractBits(0, 15) == 0x1234,
				"read across both, got " + Integer.toHexString(buf.extractBits(0, 15)));

		byte[] out = buf.toByteArray();
		helper.assertTrue(out.length == 2 && out[0] == 0x12 && out[1] == 0x34,
				"toByteArray gives back the wire order unchanged");

		helper.succeed();
	}

	/**
	 * The constructor is protected: upstream's only subclass is {@code LeafiaBuf}, in another
	 * package, which is deferred with the rest of {@code com.leafia.dev.optimization.bitbyte}.
	 * A subclass here is the same access route, not a widening of it.
	 */
	private static final class TestBuf extends BitByteBuf {}

	// ------------------------------------------------------------------ LeafiaLib

	@GameTest(template = EMPTY)
	public static void stringSwapAndScaleConstants(GameTestHelper helper) {

		helper.assertTrue(LeafiaLib.stringSwap("abcabc", 'a', 'b').equals("bacbac"),
				"stringSwap exchanges the two characters both ways");

		helper.assertTrue(LeafiaLib.NumScale.KILO == 1_000, "kilo");
		helper.assertTrue(LeafiaLib.NumScale.MEGA == 1_000_000, "mega");
		helper.assertTrue(LeafiaLib.NumScale.GIGA == 1_000_000_000, "giga");
		helper.assertTrue(LeafiaLib.NumScale.TERRA == 1_000_000_000_000L, "terra (upstream's spelling)");
		helper.assertTrue(LeafiaLib.NumScale.PETA == 1_000_000_000_000_000L, "peta");
		helper.assertTrue(LeafiaLib.NumScale.EXA == 1_000_000_000_000_000_000L, "exa");

		// The braille plotter: one row of cells, each cell two dot-columns wide.
		String[] graph = LeafiaLib.drawGraph(4, 1, 0, 0, 1, 0, 1, x -> 0.5);
		helper.assertTrue(graph.length == 1, "one row was asked for");
		helper.assertTrue(graph[0].length() == 4, "four cells wide, got " + graph[0].length());
		for(char c : graph[0].toCharArray())
			helper.assertTrue(c >= 0x2800 && c <= 0x28FF,
					"every cell is a braille code point, got " + Integer.toHexString(c));

		helper.succeed();
	}
}
