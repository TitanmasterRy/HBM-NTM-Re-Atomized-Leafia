package com.leafia.gametest;

import java.util.Random;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.Wind;
import com.leafia.passive.Wind.WindSavedData;
import com.leafia.passive.Wind.WindSyncPacket;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The wind, which is fourteen numbers and four timers and nothing else.
 *
 * <p>Everything in {@code Wind} is a balance constant that fails silently: a clamp with the wrong
 * bound gives a wind that is merely calmer than it should be, a timer with the wrong range gives
 * gusts that are merely too frequent, and nothing in the game says so — the turbines that read it
 * are not ported, so <b>a wrong number here would not be visible anywhere at all</b> until they
 * are. That is the argument for pinning it now rather than when something consumes it.
 *
 * <h2>These tests are about ranges, not values</h2>
 *
 * The simulation is driven by an unseeded {@link Random}, so the honest claims are the invariants
 * upstream's arithmetic guarantees for <em>every</em> roll, and each is checked by sweeping rather
 * than by one lucky draw. The one place a fixed value is used, it is fed in through a
 * {@code Random} that returns it.
 *
 * <p>Every test restores the static wind it found, because a GameTest server is ticking the real
 * one while these run.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaWindTests {

	private static final String EMPTY = "empty";

	/** Every saved field survives a save and a load. Fourteen of them, and they are the world. */
	@GameTest(template = EMPTY)
	public static void theWholeWindSurvivesASaveAndLoad(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());
		try {
			Wind.power = 61.5D;
			Wind.randomShortWindCounter = 7;
			Wind.randomShortWindLength = 210;
			Wind.randomLongWindCounter = 9;
			Wind.randomLongWindLength = 2100;
			Wind.strongWind = true;
			Wind.startPower = 12.25D;
			Wind.endPower = 71.75D;
			Wind.angle = 123.5D;
			Wind.lastAngle = 120.25D;
			Wind.randomAngleCounter = 3;
			Wind.randomAngleLength = 195;
			Wind.startAngle = 100D;
			Wind.endAngle = 140D;

			CompoundTag saved = Wind.writeToNBT(new CompoundTag());

			Wind.power = 0;
			Wind.strongWind = false;
			Wind.angle = 0;
			Wind.randomAngleLength = 0;
			Wind.endPower = 0;

			Wind.readFromNBT(saved);

			helper.assertTrue(Wind.power == 61.5D, "power came back as " + Wind.power);
			helper.assertTrue(Wind.strongWind, "the strong-wind flag was lost");
			helper.assertTrue(Wind.angle == 123.5D && Wind.lastAngle == 120.25D,
					"the angle came back as " + Wind.angle + "/" + Wind.lastAngle);
			helper.assertTrue(Wind.randomAngleLength == 195 && Wind.randomAngleCounter == 3,
					"the angle timer came back as " + Wind.randomAngleCounter + "/" + Wind.randomAngleLength);
			helper.assertTrue(Wind.randomShortWindLength == 210 && Wind.randomLongWindLength == 2100,
					"the gust timers came back as " + Wind.randomShortWindLength + "/" + Wind.randomLongWindLength);
			helper.assertTrue(Wind.startPower == 12.25D && Wind.endPower == 71.75D,
					"the gust endpoints came back as " + Wind.startPower + ".." + Wind.endPower);

			helper.assertTrue(saved.getAllKeys().size() == 14,
					"fourteen fields are saved, the tag holds " + saved.getAllKeys().size()
							+ " - a field added without a save line loses its value on reload");

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * The power clamp, for every roll rather than one.
	 *
	 * <p>Upstream's four bounds say the target never leaves one {@code range} either side of the
	 * base power, and never leaves 0..100. Swept across the whole power domain and both wind
	 * moods, because the clamps are written as four independent {@code if}s and the two that only
	 * fire in one mood are exactly the ones a transcription error would silently drop.
	 */
	@GameTest(template = EMPTY)
	public static void theTargetPowerNeverLeavesItsBand(GameTestHelper helper) {

		double savedPower = Wind.power;
		boolean savedStrong = Wind.strongWind;

		try {
			for(boolean strong : new boolean[] { false, true }) {

				Wind.strongWind = strong;
				double base = strong ? 80D : 5D; // 100 - range, and the calm target
				double low = Math.max(0D, base - 20D);
				double high = Math.min(100D, base + 20D);

				for(int p = 0; p <= 100; p++) {
					Wind.power = p;

					for(double roll : new double[] { 0D, 0.5D, 0.999D }) {

						double target = Wind.calculateTargetPower(fixed(roll));

						helper.assertTrue(target >= low && target <= high,
								"power " + p + (strong ? " strong" : " calm") + " roll " + roll
										+ " gave " + target + ", outside " + low + ".." + high);
					}
				}
			}
		} finally {
			Wind.power = savedPower;
			Wind.strongWind = savedStrong;
		}

		helper.succeed();
	}

	/**
	 * The three timer ranges, read off the reseeds rather than asserted from the source.
	 *
	 * <p>Each timer reseeds itself with {@code base + rand.nextInt(span)} when it expires, so
	 * driving the simulation past enough expiries samples every one of them. The bounds are
	 * upstream's: gusts 150..349 ticks, moods 1750..2749, direction 180..219.
	 */
	@GameTest(template = EMPTY)
	public static void theTimersReseedInsideUpstreamsRanges(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			// Force all three to expire on the next update, repeatedly.
			for(int i = 0; i < 400; i++) {

				Wind.randomShortWindCounter = Wind.randomShortWindLength + 1;
				Wind.randomLongWindCounter = Wind.randomLongWindLength + 1;
				Wind.randomAngleCounter = Wind.randomAngleLength + 1;

				Wind.update();

				helper.assertTrue(Wind.randomShortWindLength >= 150 && Wind.randomShortWindLength <= 349,
						"gust length " + Wind.randomShortWindLength + " is outside 150..349");
				helper.assertTrue(Wind.randomLongWindLength >= 1750 && Wind.randomLongWindLength <= 2749,
						"mood length " + Wind.randomLongWindLength + " is outside 1750..2749");
				helper.assertTrue(Wind.randomAngleLength >= 180 && Wind.randomAngleLength <= 219,
						"direction length " + Wind.randomAngleLength + " is outside 180..219");
				helper.assertTrue(Wind.power >= 0D && Wind.power <= 100D,
						"power left 0..100 at " + Wind.power);
			}
		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * A direction change is at most 45 degrees either way, and the wrap happens before it.
	 *
	 * <p>The {@code positiveModulo} on both angles is what keeps them from drifting to huge values
	 * over a long-running world, and it is applied only on the reseed — so this checks that the
	 * angle after a reseed is back inside one turn, which a dropped modulo would not be.
	 */
	@GameTest(template = EMPTY)
	public static void aDirectionChangeIsAtMostFortyFiveDegrees(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			for(int i = 0; i < 200; i++) {

				Wind.angle = 3600.5D + i; // deliberately many turns out
				Wind.randomAngleCounter = Wind.randomAngleLength + 1;

				double before = Wind.angle;
				Wind.update();

				if(Wind.randomAngleCounter != 0) continue; // that update did not roll the angle

				helper.assertTrue(Wind.startAngle >= 0D && Wind.startAngle < 360D,
						"the reseed should wrap the angle into one turn, it started from "
								+ Wind.startAngle + " (was " + before + ")");
				helper.assertTrue(Math.abs(Wind.endAngle - Wind.startAngle) <= 45D,
						"a swing of " + (Wind.endAngle - Wind.startAngle) + " degrees is more than 45");
			}
		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * The sync packet, through the real framing.
	 *
	 * <p>This is the addon's <b>first upstream packet type</b> — {@code SYNC_VELOCITY} was the one
	 * the layer was tested with. So it also checks the thing the append-only registry exists to
	 * protect: that {@code WIND_SYNC} dispatches back to itself and not to its neighbour.
	 */
	@GameTest(template = EMPTY)
	public static void theSyncPacketCarriesTheWind(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			Wind.power = 42.5D;
			Wind.angle = 217.25D;
			Wind.lastAngle = 216.75D;

			LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new WindSyncPacket());
			helper.assertTrue(packet != null, "WIND_SYNC must be registered, or nothing sends the wind");

			LeafiaCustomPacket.Opened opened = LeafiaCustomPacket.open(packet);
			helper.assertTrue(!opened.mismatched(), "a packet this build wrote cannot mismatch its own count");
			helper.assertTrue(opened.prototype() instanceof WindSyncPacket,
					"id " + LeafiaPacketTypes.WIND_SYNC + " must dispatch back to WindSyncPacket, got "
							+ (opened.prototype() == null ? "null" : opened.prototype().getClass().getSimpleName()));

			helper.assertTrue(opened.buf().readDouble() == 42.5D, "power did not survive the wire");
			helper.assertTrue(opened.buf().readDouble() == 217.25D, "angle did not survive the wire");
			helper.assertTrue(opened.buf().readDouble() == 216.75D, "lastAngle did not survive the wire");

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * The quirk, pinned rather than fixed: <b>the save file is a copy of static state.</b>
	 *
	 * <p>{@code WindSavedData} holds no fields — it reads and writes {@code Wind}'s statics — so
	 * every dimension's file is a copy of one global wind and the last one loaded wins. This test
	 * asserts that mechanism directly, because it is the thing a later "tidy-up" would break by
	 * making the data per-level and quietly changing what a player sees. See D-021 and D-1027.
	 */
	@GameTest(template = EMPTY)
	public static void theSaveIsAViewOfGlobalState(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			WindSavedData data = WindSavedData.forLevel(helper.getLevel());
			helper.assertTrue(data != null, "the level should have a wind save");

			Wind.power = 88.25D;
			CompoundTag written = data.save(new CompoundTag(), helper.getLevel().registryAccess());

			helper.assertTrue(written.getDouble("power") == 88.25D,
					"the save wrote " + written.getDouble("power") + " - it should be reading the statics,"
							+ " which is upstream's design and the reason the wind is global");

			Wind.power = 0D;
			Wind.readFromNBT(written);
			helper.assertTrue(Wind.power == 88.25D, "and loading it writes them straight back");

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/** A {@link Random} that always rolls the same number, so a clamp can be aimed at. */
	private static Random fixed(double value) {
		return new Random() {
			@Override public double nextDouble() { return value; }
		};
	}
}
