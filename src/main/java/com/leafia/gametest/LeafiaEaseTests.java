package com.leafia.gametest;

import com.leafia.lib.LeafiaRef;
import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins {@link LeafiaEase}. The four upstream bugs it once preserved were fixed in D-3075; the tests below assert the corrected curves.
 *
 * <p>Standing rule 4 and D-021: a bug players can reach is flagged for a human, not quietly
 * fixed, because fixing it changes behaviour and balance. D-1004 is that flag, and
 * it lists all five. The tests below assert the <b>buggy</b> results on purpose, so that:
 *
 * <ul>
 *   <li>a later porter cannot "clean up" the easing call and silently change how every particle,
 *       camera shake and cloud in the addon moves; and</li>
 *   <li>if the decision is ever taken to fix them, exactly these assertions fail, which makes
 *       the blast radius of the fix visible before it ships.</li>
 * </ul>
 *
 * <p>Every number asserted as buggy below was measured against upstream's own arithmetic,
 * not derived by reading it.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaEaseTests {

	private static final String EMPTY = "empty";

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-9,
				what + ": expected " + expected + ", got " + actual);
	}

	/** The curves that behave, and the two ends every curve is supposed to be anchored at. */
	@GameTest(template = EMPTY)
	public static void curvesInAndOutAreAnchored(GameTestHelper helper) {

		near(helper, new LeafiaEase(Ease.LINEAR, Direction.I).get(0.25), 0.25, "linear is the identity");
		near(helper, new LeafiaEase(Ease.QUAD, Direction.I).get(0.5), 0.25, "quad in is t squared");
		near(helper, new LeafiaEase(Ease.CUBE, Direction.I).get(0.5), 0.125, "cube in is t cubed");
		near(helper, new LeafiaEase(Ease.QUAD, Direction.O).get(0.5), 0.75, "quad out mirrors quad in");

		// Every curve except BOUNCE runs 0 to 1 in both the I and O directions.
		for(Ease ease : Ease.values()) {
			if(ease == Ease.BOUNCE) continue; // see bounceStaysInTheUnitInterval
			for(Direction dir : new Direction[] {Direction.I, Direction.O}) {
				LeafiaEase e = new LeafiaEase(ease, dir);
				near(helper, e.get(0), 0.0, ease + " " + dir + " at t=0");
				near(helper, e.get(1), 1.0, ease + " " + dir + " at t=1");
			}
		}

		// Three of the thirteen have an explicit in-out case and are correct end to end.
		for(Ease ease : new Ease[] {Ease.SINE, Ease.SMOOTH_STEP, Ease.SMOOTHER_STEP}) {
			LeafiaEase e = new LeafiaEase(ease, Direction.IO);
			near(helper, e.get(0), 0.0, ease + " IO at t=0");
			near(helper, e.get(0.5), 0.5, ease + " IO at the midpoint");
			near(helper, e.get(1), 1.0, ease + " IO at t=1");
		}

		helper.succeed();
	}

	/**
	 * <b>Live bug 1.</b> The default in-out branch overshoots by half.
	 *
	 * <p>{@code io}'s fallback reads {@code 1-i(1-(x*2-1))/2+0.5}. The trailing {@code +0.5} does
	 * not belong: the standard form is {@code 1-i(2-2x)/2}. So the curve is discontinuous at the
	 * midpoint — it reaches 0.5 from below and resumes at 1.0 — and finishes at <b>1.5</b>
	 * instead of 1.0.
	 *
	 * <p>Nine of the thirteen eases take that branch: everything except SINE, BOUNCE,
	 * SMOOTH_STEP and SMOOTHER_STEP, which have explicit cases. Two upstream call sites are
	 * affected, {@code QUAD/IO} and {@code LINEAR/IO}, so anything animating on those travels
	 * half again as far as it should.
	 */
	@GameTest(template = EMPTY)
	public static void defaultInOutFinishesAtOne(GameTestHelper helper) {

		Ease[] defaulted = {Ease.LINEAR, Ease.QUAD, Ease.CUBE, Ease.QUART, Ease.QUINT,
				Ease.EXPO, Ease.CIRC, Ease.BACK, Ease.ELASTIC};

		for(Ease ease : defaulted) {
			LeafiaEase e = new LeafiaEase(ease, Direction.IO);
			near(helper, e.get(0), 0.0, ease + " IO still starts at 0");
			near(helper, e.get(0.5), 0.5, ease + " IO still passes through the midpoint");
			// If this ever reads 1.0 the bug has been fixed - see D-1004 first.
			near(helper, e.get(1), 1.0,
					"D-3075: " + ease + " IO finishes at 1");
			near(helper, e.get(0.25), new LeafiaEase(ease, Direction.I).get(0.5) / 2, ease + " IO first half is half the in curve");
		}

		helper.succeed();
	}

	/**
	 * <b>Live bug 2.</b> BOUNCE is wrong everywhere, not just at the ends.
	 *
	 * <p>Its branches subtract {@code 1.5}, {@code 2.25} and {@code 2.625} where the standard
	 * curve subtracts those over {@code d1} (2.75), and multiply {@code n1*(x/d1)*x} where it
	 * should be {@code n1*x*x}. The result leaves the unit interval entirely: bounce-out ends at
	 * <b>8.25</b>, bounce-in at <b>-7.25</b>, and the in-out form swings from -3.62 to 4.62.
	 *
	 * <p>This one is <em>not</em> reachable by construction — no upstream file builds a BOUNCE
	 * ease. It is reachable through {@code parseEase}, so only the {@code /hbmleaf} command can
	 * produce it, which is why it is filed as a bug to flag rather than a broken feature.
	 */
	@GameTest(template = EMPTY)
	public static void bounceStaysInTheUnitInterval(GameTestHelper helper) {

		for(Direction dir : Direction.values()) {
			LeafiaEase e = new LeafiaEase(Ease.BOUNCE, dir);
			near(helper, e.get(0), 0.0, "bounce " + dir + " at t=0");
			near(helper, e.get(1), 1.0, "bounce " + dir + " at t=1");
			for(int k = 0; k <= 20; k++) {
				double v = e.get(k / 20.0);
				helper.assertTrue(v >= -1e-9 && v <= 1 + 1e-9, "bounce " + dir + " leaves the unit interval at " + k + "/20: " + v);
			}
		}
		near(helper, new LeafiaEase(Ease.BOUNCE, Direction.O).get(0.5), 0.765625, "bounce out at 0.5");

		helper.succeed();
	}

	/**
	 * <b>Live bug 3.</b> The min/max overloads never apply the easing — {@code get(t,min,max)}
	 * returns a plain lerp and the clamped form returns {@code Mth.clampedLerp}, so the curve
	 * the caller chose is discarded. Six upstream call sites use this form, among them the
	 * Fleija vacuum force, the shake camera and the cloud shrink, so every one of those is
	 * linear despite naming a curve.
	 */
	@GameTest(template = EMPTY)
	public static void minMaxOverloadsApplyTheCurve(GameTestHelper helper) {

		LeafiaEase quad = new LeafiaEase(Ease.QUAD, Direction.I);

		near(helper, quad.get(0.5), 0.25, "the single-argument form does ease");

		// If either of these ever reads 0.25, the bug has been fixed - see D-1004 before
		// updating the number, because six call sites change behaviour with it.
		near(helper, quad.get(0.5, 0.0, 1.0), 0.25, "D-3075: the min/max form eases");
		near(helper, quad.get(0.5, 0.0, 1.0, true), 0.25, "D-3075: the clamped form eases");
		near(helper, quad.get(0.5, 0.0, 1.0, false), 0.25, "D-3075: and the unclamped form too");

		// It does at least interpolate between the bounds it was given.
		near(helper, quad.get(0.5, 10.0, 20.0), 12.5, "eased lerp");
		near(helper, quad.get(0.0, 10.0, 20.0), 10.0, "at t=0");
		near(helper, quad.get(1.0, 10.0, 20.0), 20.0, "at t=1");

		helper.succeed();
	}

	/**
	 * <b>Live bugs 4 and 5.</b> {@link LeafiaEase#listEasesForCommands} offers 37 strings as
	 * command completions and {@link LeafiaEase#parseEase} rejects 13 of them.
	 *
	 * <ul>
	 *   <li>{@code "linear"} is compared with {@code ==} against a literal, which is false for
	 *       any string a command parser built, so it falls through to the suffix checks — and
	 *       "linear" ends in none of them.</li>
	 *   <li>Every {@code InOut} name is checked against {@code endsWith("Out")} <em>before</em>
	 *       {@code endsWith("InOut")}, so {@code "sineInOut"} is stripped to {@code "sineIn"},
	 *       which matches no ease. The {@code InOut} branch is unreachable, and with it all
	 *       twelve in-out curves.</li>
	 * </ul>
	 *
	 * That is 24 of 37 completions working. The counts are asserted exactly, so a fix to either
	 * bug fails here and has to be taken deliberately.
	 */
	@GameTest(template = EMPTY)
	public static void parseAcceptsAllOfItsOwnCompletions(GameTestHelper helper) {

		String[] offered = LeafiaEase.listEasesForCommands();
		helper.assertTrue(offered.length == 37,
				"13 eases, one of them linear: 1 + 12*3 = 37 completions, got " + offered.length);

		int accepted = 0;
		int rejected = 0;
		for(String s : offered) {
			try {
				LeafiaEase.parseEase(s);
				accepted++;
			} catch(IllegalArgumentException e) {
				rejected++;
			}
		}
		helper.assertTrue(accepted == 37 && rejected == 0, "D-3075: every completion parses, accepted " + accepted);

		// The two shapes that do work.
		LeafiaEase in = LeafiaEase.parseEase("quadIn");
		helper.assertTrue(in.ease == Ease.QUAD && in.dir == Direction.I, "quadIn parses");
		LeafiaEase out = LeafiaEase.parseEase("sineOut");
		helper.assertTrue(out.ease == Ease.SINE && out.dir == Direction.O, "sineOut parses");

		LeafiaEase io = LeafiaEase.parseEase(new StringBuilder("sineInOut").toString());
		helper.assertTrue(io.ease == Ease.SINE && io.dir == Direction.IO, "sineInOut parses");
		LeafiaEase lin = LeafiaEase.parseEase(new StringBuilder("linear").toString());
		helper.assertTrue(lin.ease == Ease.LINEAR, "a runtime-built linear parses");

		// Genuine nonsense is rejected too, which is the intended behaviour rather than a bug.
		assertRejected(helper, "nonsenseIn", "not an ease at all");

		helper.succeed();
	}

	private static void assertRejected(GameTestHelper helper, String input, String why) {
		try {
			LeafiaEase.parseEase(input);
			helper.fail("expected \"" + input + "\" to be rejected (" + why + ")");
		} catch(IllegalArgumentException expected) {
			// as upstream behaves
		}
	}

	@GameTest(template = EMPTY)
	public static void updateEaseReportsWhetherAnythingChanged(GameTestHelper helper) {

		LeafiaEase e = new LeafiaEase(Ease.QUAD, Direction.I);
		helper.assertTrue(!e.updateEase(Ease.QUAD, Direction.I), "no change reports false");
		helper.assertTrue(e.updateEase(Ease.CUBE, Direction.I), "a changed curve reports true");
		helper.assertTrue(e.updateEase(Ease.CUBE, Direction.O), "a changed direction reports true");
		helper.assertTrue(e.ease == Ease.CUBE && e.dir == Direction.O, "and both stuck");

		helper.succeed();
	}
}
