package com.leafia.gametest;

import java.util.Map;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.util.Tuple.Pair;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.misc.wind_turbines.medium.WindTurbineMediumTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.Wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The medium wind turbine — the first thing in this port that reads {@link Wind}.
 *
 * <p>Its output is four multiplications and a clamp, and every one of them fails silently: a
 * turbine that generates a third of what it should looks exactly like one that does not. So the
 * arithmetic is pinned at the values where it changes shape — the wind's saturation point, a dry
 * tank, a full one — rather than at one arbitrary reading.
 *
 * <p>These tests drive {@code updateEntity} directly rather than letting the block tick, because
 * the tick is where {@code Wind} moves underneath them; every one restores the static wind it
 * found.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaWindTurbineTests {

	private static final String EMPTY = "empty";
	private static final BlockPos WHERE = new BlockPos(2, 2, 2);

	/**
	 * A turbine's core, placed without going through the multiblock: {@code setBlock} with the
	 * CORE part is what {@code setPlacedBy} would have produced, minus the mast.
	 */
	private static WindTurbineMediumTE place(GameTestHelper helper) {

		helper.setBlock(WHERE, LeafiaBlocks.wind_turbine_medium.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE));

		return (WindTurbineMediumTE) helper.getBlockEntity(WHERE);
	}

	/** The tank is an hour of lubricant at this turbine's burn rate, and it starts empty. */
	@GameTest(template = EMPTY)
	public static void theTankIsAnHourOfLubricant(GameTestHelper helper) {

		WindTurbineMediumTE turbine = place(helper);

		helper.assertTrue(turbine.lubeConsumptionPeriod() == 60,
				"upstream burns a millibucket every 60 ticks, this burns one every "
						+ turbine.lubeConsumptionPeriod());
		helper.assertTrue(turbine.lube.getMaxFill() == 3600 * 20 / 60,
				"3600*20/60 = 1200mB, the tank holds " + turbine.lube.getMaxFill());
		helper.assertTrue(turbine.lube.getTankType() == Fluids.LUBRICANT,
				"it should want lubricant, it wants " + turbine.lube.getTankType().getName());
		helper.assertTrue(turbine.getMaxPower() == 100000,
				"the buffer is 100000 HE, it is " + turbine.getMaxPower());

		helper.succeed();
	}

	/**
	 * The output curve, at the three points where it changes shape.
	 *
	 * <p>{@code min(power * 0.5 / 40, 1)} saturates at a wind of 80, so 80 and 100 must generate
	 * the same. The lubricant term is {@code fraction^0.25 * 0.95 + 0.05}, so a dry tank is a
	 * twentieth of a full one — and the {@code * 3} that follows the clamp is what makes the peak
	 * 750 rather than 250.
	 */
	@GameTest(template = EMPTY)
	public static void theOutputCurveIsUpstreams(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			WindTurbineMediumTE turbine = place(helper);
			turbine.obstructed = false;

			Wind.power = 80D;
			turbine.lube.setFill(turbine.lube.getMaxFill());
			long atSaturation = generate(turbine);

			helper.assertTrue(atSaturation == 750,
					"a full tank in a saturating wind is 5000/20 * 1.0 * 3 = 750 HE/t, got " + atSaturation);

			Wind.power = 100D;
			long aboveSaturation = generate(turbine);
			helper.assertTrue(aboveSaturation == atSaturation,
					"the wind clamps at 80, so 100 must give the same " + atSaturation + ", gave " + aboveSaturation);

			Wind.power = 40D;
			long halfWind = generate(turbine);
			helper.assertTrue(halfWind == 375,
					"half the saturating wind is half the output, 375, got " + halfWind);

			Wind.power = 80D;
			turbine.lube.setFill(0);
			long dry = generate(turbine);
			helper.assertTrue(dry == 37,
					"a dry tank is the 0.05 floor: 250 * 0.05 * 3 = 37 after truncation, got " + dry);

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/** An obstructed turbine generates the lubricant floor of nothing, which is nothing. */
	@GameTest(template = EMPTY)
	public static void anObstructedTurbineGeneratesNothing(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			WindTurbineMediumTE turbine = place(helper);
			Wind.power = 100D;
			turbine.lube.setFill(turbine.lube.getMaxFill());

			turbine.obstructed = true;
			helper.assertTrue(generate(turbine) == 0,
					"an obstructed turbine must generate nothing whatever the wind is doing");

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * The raytrace itself: it sees a block that is there and does not see one that is not.
	 *
	 * <p>This is what pins the {@link net.minecraft.world.level.ClipContext} translation — upstream
	 * passes {@code stopOnLiquid} and {@code ignoreBlockWithoutBoundingBox} as booleans to its own
	 * 285-line raytracer, and getting either of them backwards gives a turbine that is obstructed
	 * by air or unobstructed by walls, both of which look like a balance problem rather than a
	 * porting one.
	 *
	 * <p>Range 1 and a due-east ray, so the whole thing happens inside the test structure — and
	 * that is not fussiness. The first version of this used the turbine's real range and asserted
	 * open air was clear; it failed, and the ray turned out to be hitting a
	 * <b>{@code minecraft:barrier} one block outside the structure</b>. GameTest walls every test
	 * structure in barriers, so <b>a raytrace longer than the structure measures the harness</b>,
	 * and it fails looking exactly like the code being wrong. See D-1028.
	 */
	@GameTest(template = EMPTY)
	public static void theRaytraceSeesBlocksAndNotAir(GameTestHelper helper) {

		WindTurbineMediumTE turbine = place(helper);

		helper.assertTrue(turbine.raytrace(0, 0, 1),
				"a ray one block due east through air should be clear");

		helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);

		helper.assertTrue(!turbine.raytrace(0, 0, 1),
				"and blocked once there is stone in it - if this passes and the one above fails,"
						+ " the ClipContext flags are inverted");

		helper.succeed();
	}

	/**
	 * The rule the two rays exist for: <b>both</b> have to be blocked, not one.
	 *
	 * <p>Upstream writes it as a nested {@code if}, which reads like an {@code &&} somebody would
	 * "tidy" into an {@code ||} — and that would turn every turbine with a tree near it off. The
	 * four cases are scripted rather than built out of blocks, because building them needs the two
	 * rays to diverge far enough to land in different blocks, which puts them outside the test
	 * structure and makes the test depend on the world instead of on the rule.
	 */
	@GameTest(template = EMPTY)
	public static void obstructionNeedsBothRaysBlocked(GameTestHelper helper) {

		CompoundTag snapshot = Wind.writeToNBT(new CompoundTag());

		try {
			Wind.angle = 0D;

			helper.assertTrue(!scripted(helper, true, true).checkForObstruction(0, 4),
					"both rays clear is not obstructed");
			helper.assertTrue(!scripted(helper, false, true).checkForObstruction(0, 4),
					"the first ray blocked and the second clear is NOT obstructed - this is the case"
							+ " an && to || rewrite would break");
			helper.assertTrue(!scripted(helper, true, false).checkForObstruction(0, 4),
					"the first ray clear short-circuits, so the second is never even asked");
			helper.assertTrue(scripted(helper, false, false).checkForObstruction(0, 4),
					"both rays blocked is obstructed");

		} finally {
			Wind.readFromNBT(snapshot);
		}

		helper.succeed();
	}

	/**
	 * A turbine whose raytrace answers from a script rather than from the world: the first call
	 * gets {@code first}, the second {@code second}.
	 */
	private static WindTurbineMediumTE scripted(GameTestHelper helper, boolean first, boolean second) {

		return new WindTurbineMediumTE(LeafiaBlockEntities.WIND_TURBINE_MEDIUM.get(),
				helper.absolutePos(WHERE), LeafiaBlocks.wind_turbine_medium.get().defaultBlockState()) {

			int calls = 0;

			@Override
			public boolean raytrace(double height, double angle, int range) {
				return calls++ == 0 ? first : second;
			}
		};
	}

	/**
	 * The three synced slots, through the real packet.
	 *
	 * <p>They count down from 31 rather than up from 0, which is upstream's way of leaving the low
	 * keys free for subclasses. Since {@code LeafiaPacket} has no slot names (D-1025), the only
	 * thing that keeps the base class and its subclasses from colliding is this numbering, so it
	 * is worth a test rather than a comment.
	 */
	@GameTest(template = EMPTY)
	public static void theThreeSyncedSlotsCountDownFrom31(GameTestHelper helper) {

		WindTurbineMediumTE turbine = place(helper);

		turbine.lube.setFill(321);
		turbine.generated = 654;
		turbine.obstructed = true;

		LeafiaPacket packet = LeafiaPacket._start(turbine)
				.__write(29, turbine.lube.getFill())
				.__write(30, turbine.generated)
				.__write(31, turbine.obstructed)
				.build();

		helper.assertTrue(packet != null && packet.identifier() == "WINDTURB_MEDIUM".hashCode(),
				"the packet should carry upstream's identifier");

		// Wipe the turbine and let its own receiver put it back.
		turbine.lube.setFill(0);
		turbine.generated = 0;
		turbine.obstructed = false;

		Map<Byte, Pair<Byte, Object>> read = packet.decode(helper.getLevel().registryAccess());
		for(Map.Entry<Byte, Pair<Byte, Object>> entry : read.entrySet()) {
			turbine.onReceivePacketLocal(LeafiaPacket.key(entry.getKey()), entry.getValue().getValue());
		}

		helper.assertTrue(turbine.lube.getFill() == 321, "slot 29 is the lubricant, got " + turbine.lube.getFill());
		helper.assertTrue(turbine.generated == 654, "slot 30 is the output, got " + turbine.generated);
		helper.assertTrue(turbine.obstructed, "slot 31 is the obstruction flag");

		helper.succeed();
	}

	/** The rotor turns off the synced output, and wraps rather than growing without bound. */
	@GameTest(template = EMPTY)
	public static void theRotorWrapsAtAFullTurn(GameTestHelper helper) {

		WindTurbineMediumTE turbine = place(helper);

		turbine.bladeAngle = 350F;
		turbine.addBladeAngle(20F);

		helper.assertTrue(turbine.bladeAngle == 10F,
				"370 degrees should wrap to 10, is " + turbine.bladeAngle);
		helper.assertTrue(turbine.bladeAnglePrev == -10F,
				"and the previous angle wraps with it, so interpolation does not spin the rotor"
						+ " backwards through a whole turn - it is " + turbine.bladeAnglePrev);

		helper.succeed();
	}

	/**
	 * The output for the state the turbine is in, without the tick around it.
	 *
	 * <p>Deliberately not {@code updateEntity}: that calls {@code updateObstructed}, which
	 * re-rolls the obstruction flag on a one-in-a-hundred schedule, so driving the whole tick
	 * would make every one of these tests fail one run in a hundred for a reason unrelated to
	 * what it is checking.
	 */
	private static long generate(WindTurbineMediumTE turbine) {
		return turbine.computeOutput();
	}
}
