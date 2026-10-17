package com.leafia.gametest;

import java.lang.reflect.Field;

import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.EvBuffer;
import com.leafia.contents.machines.elevators.EvPulley;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.car.chips.EvChipBase;
import com.leafia.contents.machines.elevators.car.chips.EvChipItem;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaEntities;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The elevator car and the three blocks that carry it: every number that decides how a lift
 * behaves, and the two upstream bugs kept under D-021.
 *
 * <p>These constants are the whole point of the batch. A cabin's acceleration, its brake distance,
 * how long its doors stay open and the crawl speed it closes the last block with are what make an
 * S6 feel like an S6 and a Skylift like a Skylift, and every one of them is a literal that a
 * plausible-looking edit could change without anything failing to compile or any other test
 * noticing.
 *
 * <h2>Why some of this is reflection</h2>
 *
 * The chips' constants are {@code private final} fields on their own classes, exactly as upstream
 * has them, and this test lives in a different package. Widening them to make them testable would
 * change the shipped class to suit the test; reading them reflectively does not. Where the value
 * <em>is</em> reachable through real API — the block dimensions, the pulley's power, the entity
 * types — the test goes through the API.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaElevatorCarTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ chips

	/**
	 * The S6's five timings, all upstream's.
	 *
	 * <p>{@code accelerateTime} and {@code brakeDistance} come from {@link EvChipBase}'s defaults
	 * — the S6 is the chip that does not override them, which is only visible by checking that it
	 * still holds the base's values.
	 */
	@GameTest(template = EMPTY)
	public static void theS6KeepsUpstreamsTimings(GameTestHelper helper) {
		EvChipBase chip = controllerFor(helper, LeafiaItems.ev_chip_s6.get());
		helper.assertTrue(chip.getType().equals("s6"), "ev_chip_s6 built a " + chip.getType());
		assertField(helper, chip, "accelerateTime", 1.5);
		assertField(helper, chip, "brakeDistance", 2);
		assertField(helper, chip, "doorOpenTime", 10);
		assertField(helper, chip, "closeTime", 60);
		helper.succeed();
	}

	/** The Skylift is the slow one: it accelerates for twice as long and brakes four times sooner. */
	@GameTest(template = EMPTY)
	public static void theSkyliftKeepsUpstreamsTimings(GameTestHelper helper) {
		EvChipBase chip = controllerFor(helper, LeafiaItems.ev_chip_skylift.get());
		helper.assertTrue(chip.getType().equals("skylift"), "ev_chip_skylift built a " + chip.getType());
		assertField(helper, chip, "accelerateTime", 3.0);
		assertField(helper, chip, "brakeDistance", 8);
		assertField(helper, chip, "doorOpenTime", 20);
		assertField(helper, chip, "closeTime", 100);
		helper.succeed();
	}

	/**
	 * The two chips really do differ, in every timing.
	 *
	 * <p>The pair of tests above would both still pass if a copy-paste gave the Skylift the S6's
	 * numbers and the S6 the Skylift's — each only checks its own. This one pins that no timing is
	 * shared, which is the property a copy-paste actually breaks.
	 */
	@GameTest(template = EMPTY)
	public static void noTimingIsSharedBetweenTheTwoChips(GameTestHelper helper) {
		EvChipBase s6 = controllerFor(helper, LeafiaItems.ev_chip_s6.get());
		EvChipBase sky = controllerFor(helper, LeafiaItems.ev_chip_skylift.get());
		for(String name : new String[]{"accelerateTime", "brakeDistance", "doorOpenTime", "closeTime"}) {
			Object a = field(helper, s6, name);
			Object b = field(helper, sky, name);
			helper.assertTrue(!a.equals(b), name + " is the same on both chips (" + a + ")");
		}
		helper.succeed();
	}

	/**
	 * Each chip item builds its own controller class, matched on a <em>suffix</em>.
	 *
	 * <p>Upstream's {@code getController} is {@code res.endsWith("s6")} rather than an equality
	 * check, which is kept — so a future {@code ev_lifts6} would silently take the S6's behaviour.
	 * This pins that the two ids in the build today land on different classes.
	 *
	 * <p><b>What is not pinned here, and should be:</b> the {@code ratio * 0.08} in
	 * {@code EvChipS6.onUpdate}, which is the single most consequential literal in the batch —
	 * dropping it makes an S6 twelve times too fast. Reaching it needs a cabin hung on a live
	 * pulley with a target floor, which is a rig this batch does not build, and asserting anything
	 * weaker under that name would be a test that reads as covering it and does not. It is listed
	 * as an open item in D-1039 instead.
	 */
	@GameTest(template = EMPTY)
	public static void eachChipBuildsItsOwnController(GameTestHelper helper) {
		EvChipBase s6 = controllerFor(helper, LeafiaItems.ev_chip_s6.get());
		EvChipBase sky = controllerFor(helper, LeafiaItems.ev_chip_skylift.get());
		helper.assertTrue(!s6.getClass().equals(sky.getClass()),
				"both chips built the same controller class, " + s6.getClass().getSimpleName());
		helper.succeed();
	}

	// ------------------------------------------------------------------ blocks

	/** Upstream's three sets of multiblock dimensions, each different and each load-bearing. */
	@GameTest(template = EMPTY)
	public static void theThreeBlocksKeepUpstreamsDimensions(GameTestHelper helper) {

		assertDims(helper, "pulley", ((EvPulley) LeafiaBlocks.elevator_pulley.get()).getDimensions(),
				new int[]{0, 0, 1, 1, 1, 1});
		assertDims(helper, "buffer", ((EvBuffer) LeafiaBlocks.elevator_buffer.get()).getDimensions(),
				new int[]{2, 0, 0, 0, 0, 0});
		assertDims(helper, "s6 floor", ((EvFloor) LeafiaBlocks.elevator_s6floor.get()).getDimensions(),
				new int[]{2, 0, 0, 0, 1, 1});

		// The two landings are separate registrations of the same class, so they must agree.
		assertDims(helper, "skylift floor",
				((EvFloor) LeafiaBlocks.elevator_skyliftfloor.get()).getDimensions(),
				new int[]{2, 0, 0, 0, 1, 1});

		helper.succeed();
	}

	/**
	 * A landing's facing becomes the same yaw upstream's {@code getMatrix(meta)} gives it.
	 *
	 * <p>Upstream switches on {@code meta - 10}, which is an {@code EnumFacing} index: 2 is north
	 * and gets 180, 3 is south and gets 0, 4 is west and gets 270, 5 is east and gets 90. Getting
	 * that mapping wrong puts a landing's call button on the wrong wall, and the four cases are
	 * easy to transpose because north/south and east/west are not in the order the enum declares.
	 */
	@GameTest(template = EMPTY)
	public static void aLandingsFacingBecomesUpstreamsYaw(GameTestHelper helper) {
		assertYaw(helper, Direction.NORTH, 180);
		assertYaw(helper, Direction.SOUTH, 0);
		assertYaw(helper, Direction.WEST, 270);
		assertYaw(helper, Direction.EAST, 90);

		// Upstream's switch has no case for the two vertical facings and falls through to the
		// identity. A landing cannot be placed facing up or down, so this is unreachable today
		// and pinned only so that adding a case is a deliberate act.
		assertYaw(helper, Direction.UP, 0);
		assertYaw(helper, Direction.DOWN, 0);
		helper.succeed();
	}

	/**
	 * The pulley draws 10 HE a tick and buffers five ticks of it.
	 *
	 * <p>{@code getMaxPower} is derived from {@code consumption} rather than being a second
	 * literal, which is upstream's own arrangement; pinning both catches an edit that changes one
	 * and leaves the other.
	 */
	@GameTest(template = EMPTY)
	public static void thePulleyDrawsTenAndBuffersFive(GameTestHelper helper) {
		helper.assertTrue(EvPulleyTE.consumption == 10,
				"the pulley's consumption is 10, got " + EvPulleyTE.consumption);
		EvPulleyTE te = new EvPulleyTE(helper.absolutePos(net.minecraft.core.BlockPos.ZERO),
				LeafiaBlocks.elevator_pulley.get().defaultBlockState());
		helper.assertTrue(te.getMaxPower() == 50,
				"the pulley buffers consumption x 5 = 50, got " + te.getMaxPower());
		helper.assertTrue(te.affectionRange() == 256,
				"the pulley's packet range is 256, got " + te.affectionRange());
		helper.succeed();
	}

	// ------------------------------------------------------------------ entity types

	/**
	 * Both entity types exist, both track to upstream's range and both update every tick.
	 *
	 * <p>Upstream's tracking range is 1000 <em>blocks</em> and 1.21.1's is in <em>chunks</em>, so
	 * 63 is 1000/16 rounded up. An update interval above 1 would make a cabin a player is riding
	 * stutter, which is exactly the kind of thing that looks like a rendering bug.
	 */
	@GameTest(template = EMPTY)
	public static void theTwoEntityTypesAreRegisteredWithUpstreamsTracking(GameTestHelper helper) {
		for(EntityType<?> type : new EntityType<?>[]{LeafiaEntities.ELEVATOR.get(), LeafiaEntities.ELEVATOR_WEIGHT.get()}) {
			helper.assertTrue(type.clientTrackingRange() == 63,
					type + ": tracking range is 63 chunks (upstream's 1000 blocks), got "
							+ type.clientTrackingRange());
			helper.assertTrue(type.updateInterval() == 1,
					type + ": update interval is every tick, got " + type.updateInterval());
		}
		helper.succeed();
	}

	/**
	 * The cabin is 30/16 wide, and taller on the server than on the client.
	 *
	 * <p>This is upstream's {@code height = world.isRemote ? 0.1f : 2.5f} and it is not a mistake:
	 * the server wants a box tall enough for entities to ride, the client wants a flat one so the
	 * player's own collision does not fight the cabin they are standing in. A GameTest server is
	 * the server side, so 2.5 is what must come back here.
	 */
	@GameTest(template = EMPTY)
	public static void theCabinIsTallOnTheServer(GameTestHelper helper) {
		Entity cabin = LeafiaEntities.ELEVATOR.get().create(helper.getLevel());
		helper.assertTrue(cabin != null, "the elevator type would not create an entity");
		EntityDimensions dims = cabin.getDimensions(Pose.STANDING);
		helper.assertTrue(Math.abs(dims.width() - 30 / 16f) < 1e-6,
				"the cabin is 30/16 wide, got " + dims.width());
		helper.assertTrue(Math.abs(dims.height() - 2.5f) < 1e-6,
				"the cabin is 2.5 tall on the server, got " + dims.height());
		cabin.discard();
		helper.succeed();
	}

	/**
	 * The style slots are in upstream's order, which is not the order they are declared in.
	 *
	 * <p>Floor, ceiling, front, left, <b>back</b>, right. {@code getPanel} indexes this with
	 * {@code side + 2}, so swapping the last two rotates every cabin's walls by two and nothing
	 * else notices.
	 */
	@GameTest(template = EMPTY)
	public static void theStyleSlotsAreInUpstreamsOrder(GameTestHelper helper) {
		helper.assertTrue(ElevatorEntity.styleParams.length == 6,
				"a cabin has six style slots, got " + ElevatorEntity.styleParams.length);
		helper.assertTrue(ElevatorEntity.styleParams[0] == ElevatorEntity.STYLE_FLOOR, "slot 0 is the floor");
		helper.assertTrue(ElevatorEntity.styleParams[1] == ElevatorEntity.STYLE_CEILING, "slot 1 is the ceiling");
		helper.assertTrue(ElevatorEntity.styleParams[2] == ElevatorEntity.STYLE_FRONT, "slot 2 is the front");
		helper.assertTrue(ElevatorEntity.styleParams[3] == ElevatorEntity.STYLE_LEFT, "slot 3 is the left");
		helper.assertTrue(ElevatorEntity.styleParams[4] == ElevatorEntity.STYLE_BACK,
				"slot 4 is the BACK, not the right - upstream's order");
		helper.assertTrue(ElevatorEntity.styleParams[5] == ElevatorEntity.STYLE_RIGHT, "slot 5 is the right");
		helper.succeed();
	}

	// ------------------------------------------------------------------ registries

	/**
	 * The three block entity types exist and the floor type is valid for both landings.
	 *
	 * <p>A {@code BlockEntityType} that does not name a block silently refuses to attach to it, and
	 * the symptom is a landing that places and then does nothing at all.
	 */
	@GameTest(template = EMPTY)
	public static void theFloorTypeIsValidForBothLandings(GameTestHelper helper) {
		helper.assertTrue(LeafiaBlockEntities.EV_PULLEY.get()
						.isValid(LeafiaBlocks.elevator_pulley.get().defaultBlockState()),
				"EV_PULLEY is not valid for elevator_pulley");
		helper.assertTrue(LeafiaBlockEntities.EV_BUFFER.get()
						.isValid(LeafiaBlocks.elevator_buffer.get().defaultBlockState()),
				"EV_BUFFER is not valid for elevator_buffer");
		helper.assertTrue(LeafiaBlockEntities.EV_FLOOR.get()
						.isValid(LeafiaBlocks.elevator_s6floor.get().defaultBlockState()),
				"EV_FLOOR is not valid for elevator_s6floor");
		helper.assertTrue(LeafiaBlockEntities.EV_FLOOR.get()
						.isValid(LeafiaBlocks.elevator_skyliftfloor.get().defaultBlockState()),
				"EV_FLOOR is not valid for elevator_skyliftfloor - the two landings share one type");
		helper.succeed();
	}

	/**
	 * The packet ids are append-only, and the elevators' seven sit above the two that were there.
	 *
	 * <p>The id <em>is</em> the index, and the count is the protocol version both ends compare. An
	 * insert above an existing line silently renumbers every packet after it, and the count check
	 * cannot catch that because the count does not change.
	 */
	@GameTest(template = EMPTY)
	public static void theElevatorPacketsAppendedRatherThanInserted(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.NONE == 0, "NONE must stay at 0");
		helper.assertTrue(LeafiaPacketTypes.SYNC_VELOCITY == 1, "SYNC_VELOCITY must stay at 1");
		helper.assertTrue(LeafiaPacketTypes.WIND_SYNC == 2, "WIND_SYNC must stay at 2");
		helper.assertTrue(LeafiaPacketTypes.ELEVATOR_BUTTON_SYNC == 3,
				"the elevators start at 3, got " + LeafiaPacketTypes.ELEVATOR_BUTTON_SYNC);
		helper.assertTrue(LeafiaPacketTypes.ELEVATOR_INVENTORY_SYNC == 9,
				"the elevators end at 9, got " + LeafiaPacketTypes.ELEVATOR_INVENTORY_SYNC);
		// The count is expected to grow - it moves by one for every packet that lands, and the
		// fuzzy identifier's pair took it from 10 to 12 and the cabin screen's
		// modify packet to 13, the advisor's pair to 15, the radio's pair to 17, the torex's three to 20 and
		// fallout sync, backstab and schizo (D-3009) to 23, the DFC's shock and explosion (D-3011) to 25, and the Folkvangr's vacuum (D-3013) to 26, the impact collapse (D-3022) to 27, and the crate label's text and the control upgrade's channel (D-3021) to 28 (29 with a merge), and the APR's highlight and the laser detonator's pair (D-3027) to 32, and the AM rifle's attached sound and lock-on (D-3028) to 34. What must NOT move is the ten ids
		// asserted above; this line only catches a change that leaves the total where it was,
		// which is the shape an insert-plus-removal would have.
		helper.assertTrue(LeafiaPacketTypes.count() == 41,
				"forty-one types registered (thirty-nine and D-3072's two), got " + LeafiaPacketTypes.count());
		helper.succeed();
	}

	/**
	 * Only what the addon can actually draw is reachable from the creative tab.
	 *
	 * <p><b>Everything the elevators add is now in the tab</b>, which is what this test was
	 * counting down to: the chips have flat textures, the four blocks have renderers and item
	 * models (D-1041), and the styles and spawn items have composite item models (D-1043). The
	 * name is kept because the <em>rule</em> is the point — a thing goes in the tab when it can
	 * be drawn — and the next content to land will need it again. The positive assertions are
	 * what stop this passing on an empty tab, which is the trap
	 * {@code LeafiaElevatorStyleTests} fell into.
	 */
	@GameTest(template = EMPTY)
	public static void onlyWhatCanBeDrawnIsReachableFromTheTab(GameTestHelper helper) {

		var contents = com.leafia.creativetabs.LeafiaCreativeTabs
				.contentsOf(com.leafia.creativetabs.LeafiaCreativeTabs.MAIN);
		helper.assertTrue(!contents.isEmpty(), "the addon's tab is empty; the rest of this proves nothing");

		assertInTab(helper, contents, LeafiaItems.ev_chip_s6.get(), true);
		assertInTab(helper, contents, LeafiaItems.ev_chip_skylift.get(), true);
		// Both gained a composite item model in D-1043, so both are reachable now.
		assertInTab(helper, contents, LeafiaItems.ev_spawn.get(), true);
		assertInTab(helper, contents, LeafiaItems.weight_spawn.get(), true);
		// Both gained a renderer and a composite item model in D-1040, so both moved into the
		// tab. The two landings did not: EvFloorRender is still unported.
		assertInTab(helper, contents, LeafiaBlocks.elevator_pulley.get().asItem(), true);
		assertInTab(helper, contents, LeafiaBlocks.elevator_buffer.get().asItem(), true);
		// Both landings gained EvFloorRender and a composite item model in D-1041.
		assertInTab(helper, contents, LeafiaBlocks.elevator_s6floor.get().asItem(), true);
		assertInTab(helper, contents, LeafiaBlocks.elevator_skyliftfloor.get().asItem(), true);

		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static void assertInTab(GameTestHelper helper,
			java.util.List<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>> contents,
			net.minecraft.world.item.Item item, boolean expected) {
		boolean found = contents.stream().anyMatch(s -> s.get().asItem() == item);
		helper.assertTrue(found == expected,
				item + (expected ? " should be in the tab" : " should not be in the tab yet"));
	}

	private static EvChipBase controllerFor(GameTestHelper helper, net.minecraft.world.item.Item item) {
		helper.assertTrue(item instanceof EvChipItem, item + " is not a chip");
		Entity cabin = LeafiaEntities.ELEVATOR.get().create(helper.getLevel());
		helper.assertTrue(cabin != null, "the elevator type would not create an entity");
		EvChipBase chip = ((EvChipItem) item).getController((ElevatorEntity) cabin);
		helper.assertTrue(chip != null, item + " built no controller");
		cabin.discard();
		return chip;
	}

	private static void assertField(GameTestHelper helper, EvChipBase chip, String name, Object expected) {
		Object got = field(helper, chip, name);
		helper.assertTrue(numeric(got) == numeric(expected),
				chip.getType() + "." + name + " is " + expected + ", got " + got);
	}

	private static Object field(GameTestHelper helper, EvChipBase chip, String name) {
		for(Class<?> c = chip.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(chip);
			} catch(NoSuchFieldException ignored) {
				// keep walking up: accelerateTime and brakeDistance live on EvChipBase
			} catch(IllegalAccessException e) {
				helper.fail("could not read " + name + ": " + e);
			}
		}
		helper.fail(chip.getType() + " has no field " + name + " on it or any superclass");
		return null;
	}

	private static double numeric(Object o) {
		return ((Number) o).doubleValue();
	}

	private static void assertDims(GameTestHelper helper, String what, int[] got, int[] expected) {
		helper.assertTrue(java.util.Arrays.equals(got, expected),
				what + ": dimensions are " + java.util.Arrays.toString(expected)
						+ ", got " + java.util.Arrays.toString(got));
	}

	/**
	 * A landing facing {@code face} builds the matrix upstream's {@code rotateY(angle)} builds.
	 *
	 * <p>Compared against a matrix rotated by the expected angle rather than against a hand-written
	 * vector, deliberately: the claim being tested is the <em>facing-to-angle mapping</em>, which is
	 * what a transposition breaks, and {@code FiaMatrixTests} already pins what {@code rotateY}
	 * does with an angle. Writing the vectors out here would make this test a copy of the
	 * implementation and would fail for a reason that is not this file's.
	 */
	private static void assertYaw(GameTestHelper helper, Direction face, double angle) {
		var got = EvFloor.getMatrix(face).frontVector;
		var want = new com.leafia.dev.math.FiaMatrix().rotateY(angle).frontVector;
		helper.assertTrue(got.distanceToSqr(want) < 1e-12,
				face + ": upstream rotates it by " + angle + " (front " + want + "), got " + got);
	}
}
