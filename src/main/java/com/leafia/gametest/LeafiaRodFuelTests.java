package com.leafia.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.custom_hbm.contents.torex.LCETorex;
import com.hbm.blockentity.machine.BlockEntityReactorResearch;
import com.hbm.blockentity.machine.BlockEntityWasteDrum;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.handler.radiation.ChunkRadiationHandler;
import com.hbm.handler.radiation.ChunkRadiationHandlerSimple;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodFuel;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's rods in the base mod's research reactor and waste drum (D-3021).
 *
 * <p>The flux table is checked against numbers worked out by hand from upstream's source, not against the code:
 * the total weight each slot's rods give (all twelve slots full) and the weight of one neighbour in particular. The
 * reaction is checked on a real reactor with the control rods out, ticked by hand as the base mod's own tests do,
 * against the rod's own tags. The two endings (a rod over ten million degrees, a rod that has spilled for more than
 * a hundred ticks) explode for real, so they run on a radiation handler of their own that is thrown away, and what
 * they spawn is discarded before it can tick.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRodFuelTests {

	private static final String BIG = "empty16";
	private static final String EMPTY = "empty";
	private static final BlockPos CENTRE = new BlockPos(8, 6, 8);

	private static boolean on() {
		return LeafiaFeatures.rods;
	}

	private static LeafiaRodItem rod(String fid) {
		return LeafiaRodItem.fromResourceMap.get("leafia_rod_" + fid);
	}

	private static ItemStack stack(String fid) {
		return new ItemStack(rod(fid));
	}

	/** A rod with a heat, so its flux is not the zero of a rod that has never stepped. */
	private static ItemStack warm(String fid, double heat) {
		ItemStack s = stack(fid);
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", heat);
		t.putDouble("depletion", 0);
		t.putBoolean("melting", false);
		t.putInt("generosityTimer", 90);
		t.putInt("spillage", 0);
		t.putDouble("decay", 0);
		LeafiaRodItem.putData(s, t);
		return s;
	}

	/** A reactor core with lead over the four corners, so a running one does not leak into the chunk. */
	private static BlockEntityReactorResearch reactor(GameTestHelper helper) {
		helper.setBlock(CENTRE, ModBlocks.reactor_research.get().defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE));
		for(Direction d : Direction.Plane.HORIZONTAL) helper.setBlock(CENTRE.relative(d).above(), ModBlocks.block_lead.get().defaultBlockState());
		return (BlockEntityReactorResearch) helper.getBlockEntity(CENTRE);
	}

	private static void withHandler(GameTestHelper helper, Consumer<ChunkRadiationHandlerSimple> body) {
		ChunkRadiationHandler saved = ChunkRadiationManager.proxy;
		ChunkRadiationHandlerSimple handler = new ChunkRadiationHandlerSimple();
		handler.trackWorld(helper.getLevel());
		ChunkRadiationManager.proxy = handler;
		try {
			body.accept(handler);
		} finally {
			ChunkRadiationManager.proxy = saved;
		}
	}

	private static <T extends Entity> int takeAway(GameTestHelper helper, Class<T> type) {
		Level level = helper.getLevel();
		List<T> found = level.getEntitiesOfClass(type, new AABB(helper.absolutePos(CENTRE)).inflate(128));
		int n = found.size();
		found.forEach(Entity::discard);
		return n;
	}

	private static void cleanBlasts(GameTestHelper helper) {
		takeAway(helper, EntityNukeExplosionMK5.class);
		takeAway(helper, EntityNukeTorex.class);
		for(LCETorex t : new java.util.ArrayList<>(LCETorex.serverTorexes(helper.getLevel()))) LCETorex.forget(t);
	}

	// ------------------------------------------------------------------ the seams

	@GameTest(template = EMPTY)
	public static void bothSeamsClaimAddonRodsAndNothingElse(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack rod = stack("nu");
		helper.assertTrue(BlockEntityReactorResearch.fuelHandlerFor(rod) == LeafiaRodFuel.RESEARCH, "the research reactor claims a rod");
		helper.assertTrue(BlockEntityWasteDrum.fuelHandlerFor(rod) == LeafiaRodFuel.DRUM, "the waste drum claims a rod");
		for(ItemStack other : new ItemStack[] { new ItemStack(Items.STICK), new ItemStack(com.hbm.items.ModItems.plate_fuel_u235.get()), new ItemStack(com.hbm.items.ModItems.rbmk_fuel_ueu.get()) }) {
			helper.assertTrue(BlockEntityReactorResearch.fuelHandlerFor(other) != LeafiaRodFuel.RESEARCH, other + ": not a research reactor rod");
			helper.assertTrue(BlockEntityWasteDrum.fuelHandlerFor(other) != LeafiaRodFuel.DRUM, other + ": not a drum rod");
		}
		helper.assertTrue(BlockEntityReactorResearch.fuelHandlerFor(ItemStack.EMPTY) == null, "nothing is no fuel");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theReactorAndTheDrumTakeARodInAnySlot(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		ItemStack rod = stack("nu");
		helper.assertTrue(reactor.canPlaceItem(0, rod) && reactor.canPlaceItem(1, rod) && reactor.canPlaceItem(11, rod), "any of the twelve slots, as the base mod's fixed guard has it (D-2286)");

		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.machine_waste_drum.get());
		BlockEntityWasteDrum drum = (BlockEntityWasteDrum) helper.getBlockEntity(new BlockPos(2, 2, 2));
		helper.assertTrue(drum.canPlaceItem(0, rod) && drum.canPlaceItem(7, rod), "any slot of the drum");
		drum.slots[3] = rod.copy();
		helper.assertTrue(drum.canTakeItemThroughFace(3, drum.slots[3], Direction.DOWN), "and a hopper may take it out, as the handler's default says");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSwitchTakesBothHandlersOut(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		boolean before = LeafiaFeatures.rods;
		try {
			LeafiaFeatures.rods = false;
			LeafiaRodFuel.commonSetup();
			helper.assertTrue(BlockEntityReactorResearch.fuelHandlerFor(stack("nu")) == null && BlockEntityWasteDrum.fuelHandlerFor(stack("nu")) == null, "off: unclaimed");
		} finally {
			LeafiaFeatures.rods = before;
			LeafiaRodFuel.commonSetup();
		}
		helper.assertTrue(BlockEntityReactorResearch.fuelHandlerFor(stack("nu")) == LeafiaRodFuel.RESEARCH && BlockEntityWasteDrum.fuelHandlerFor(stack("nu")) == LeafiaRodFuel.DRUM, "and back on");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the flux table

	/**
	 * With a rod in every slot, a slot's flux is a fixed multiple of one rod's: worked out by hand from
	 * upstream's code - a column sum (halved for each step away), the row neighbours at full strength and the
	 * knight's-move ones at half - and written down here as one number per slot.
	 */
	@GameTest(template = BIG)
	public static void withEverySlotFullEachSlotSeesUpstreamsTotalWeight(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		for(int i = 0; i < 12; i++) reactor.slots[i] = warm("nu", 500);
		double each = rod("nu").getFlux(reactor.slots[0]);
		helper.assertTrue(each > 0, "a warm rod has a flux: " + each);

		double[] weight = { 3.5, 3.5, 3.5, 5, 3.5, 5, 5, 3.5, 5, 3.5, 3.5, 3.5 };
		for(int slot = 0; slot < 12; slot++) {
			double seen = LeafiaRodFuel.detectedHeat(reactor, slot, rod("nu"));
			helper.assertTrue(Math.abs(seen - weight[slot] * each) < 1e-9 * each, "slot " + slot + ": " + seen / each + " rods' worth, upstream's is " + weight[slot]);
		}
		helper.succeed();
	}

	/** One rod, one neighbour: (target slot, source slot, weight) for every kind of neighbour upstream's table has, and a few that are not. */
	@GameTest(template = BIG)
	public static void eachNeighbourCountsForItsOwnWeight(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		double[][] pairs = {
				// the column: 5 apart at full, 10 apart at a half
				{ 0, 5, 1 }, { 0, 10, 0.5 }, { 5, 0, 1 }, { 5, 10, 1 }, { 10, 0, 0.5 }, { 10, 5, 1 }, { 1, 6, 1 }, { 1, 11, 0.5 }, { 11, 1, 0.5 }, { 7, 2, 1 }, { 2, 7, 1 }, { 9, 4, 1 }, { 4, 9, 1 },
				// the row
				{ 0, 1, 1 }, { 1, 0, 1 }, { 0, 2, 0.5 }, { 0, 3, 0.5 }, { 3, 2, 1 }, { 3, 4, 1 }, { 2, 3, 1 }, { 4, 3, 1 }, { 8, 7, 1 }, { 8, 9, 1 }, { 11, 10, 1 }, { 10, 11, 1 },
				// the half-strength diagonals
				{ 3, 1, 0.5 }, { 3, 5, 0.5 }, { 3, 0, 0.5 }, { 3, 6, 0.5 }, { 7, 9, 0.5 }, { 7, 5, 0.5 }, { 7, 10, 0.5 }, { 2, 0, 0.5 }, { 2, 4, 0.5 }, { 2, 5, 0.5 },
				{ 4, 2, 0.5 }, { 4, 1, 0.5 }, { 4, 6, 0.5 }, { 11, 9, 0.5 }, { 11, 8, 0.5 }, { 10, 8, 0.5 }, { 10, 7, 0.5 }, { 5, 3, 0.5 }, { 5, 2, 0.5 }, { 5, 7, 0.5 }, { 5, 8, 0.5 },
				// not neighbours at all
				{ 0, 4, 0 }, { 0, 6, 0 }, { 0, 7, 0 }, { 0, 11, 0 }, { 2, 1, 0 }, { 2, 6, 0 }, { 2, 8, 0 }, { 4, 0, 0 }, { 7, 6, 0 }, { 9, 10, 0 }, { 10, 4, 0 }, { 11, 0, 0 }
		};
		for(double[] pair : pairs) {
			int target = (int) pair[0];
			int source = (int) pair[1];
			for(int i = 0; i < 12; i++) reactor.slots[i] = ItemStack.EMPTY;
			reactor.slots[target] = warm("nu", 500);
			reactor.slots[source] = warm("nu", 500);
			double each = rod("nu").getFlux(reactor.slots[source]);
			double seen = LeafiaRodFuel.detectedHeat(reactor, target, rod("nu"));
			helper.assertTrue(Math.abs(seen - pair[2] * each) < 1e-9 * each, "slot " + target + " from slot " + source + ": " + seen / each + " rods' worth, expected " + pair[2]);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the reaction

	@GameTest(template = BIG)
	public static void aTickRunsEachRodAtTheControlLevelAndFeedsTheReactor(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		reactor.slots[0] = warm("nu", 500);
		reactor.slots[5] = warm("nu", 800);
		double flux5 = rod("nu").getFlux(reactor.slots[5]);
		reactor.controlLevel = reactor.targetLevel = 0.5;
		reactor.heat = 1000;
		try {
			reactor.updateEntity();

			CompoundTag a = LeafiaRodItem.data(reactor.slots[0]);
			CompoundTag b = LeafiaRodItem.data(reactor.slots[5]);
			double incoming0 = flux5 * 0.5;
			helper.assertTrue(Math.abs(a.getDouble("incoming") - incoming0) < 1e-9 * incoming0, "slot 0 saw slot 5's flux times the control level: " + a.getDouble("incoming") + " of " + incoming0);
			// and slot 0 was stepped as HeatFunction(rod, true, flux x level, 1, 20, 400), the constants upstream's loop passes
			ItemStack reference = warm("nu", 500);
			rod("nu").HeatFunction(reference, true, flux5 * 0.5, 1, 20, 400);
			CompoundTag ref = LeafiaRodItem.data(reference);
			helper.assertTrue(a.getDouble("heat") == ref.getDouble("heat") && a.getDouble("cooled") == ref.getDouble("cooled"),
					"slot 0 is stepped with coolant 1, 20 degrees and 400: " + a.getDouble("heat") + " against " + ref.getDouble("heat"));
			// slot 5 stepped after slot 0 had, and saw slot 0's flux as it was after that step
			double flux0After = rod("nu").getFlux(reactor.slots[0]);
			helper.assertTrue(Math.abs(b.getDouble("incoming") - flux0After * 0.5) < 1e-9 * Math.max(1, flux0After), "slot 5 saw slot 0's flux after slot 0's step: " + b.getDouble("incoming"));

			int expectedHeat = 1000 + (int) (a.getDouble("cooled") * 50) + (int) (b.getDouble("cooled") * 50) - BlockEntityReactorResearch.DRY_COOLING;
			helper.assertTrue(reactor.heat == expectedHeat, "the heat is the rods' cooling times 50, less the dry reactor's one: " + reactor.heat + " against " + expectedHeat);
			int expectedFlux = (int) (a.getDouble("incoming") / 80) + (int) (b.getDouble("incoming") / 80);
			helper.assertTrue(reactor.totalFlux == expectedFlux, "the flux is the rods' incoming over 80: " + reactor.totalFlux + " against " + expectedFlux);
			helper.assertTrue(a.getDouble("cooled") > 0, "full coolant at 20 degrees takes heat off a warm rod: " + a.getDouble("cooled"));
		} finally {
			reactor.controlLevel = reactor.targetLevel = 0;
			for(int i = 0; i < 12; i++) reactor.slots[i] = ItemStack.EMPTY;
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void withTheControlRodsInNothingReacts(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		reactor.slots[0] = warm("nu", 500);
		reactor.controlLevel = reactor.targetLevel = 0;
		reactor.updateEntity();
		helper.assertTrue(LeafiaRodItem.data(reactor.slots[0]).getDouble("heat") == 500 && !LeafiaRodItem.data(reactor.slots[0]).contains("cooled"), "the rod was not stepped");
		reactor.slots[0] = ItemStack.EMPTY;
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aSpentRodIsSwappedForItsDecayProductInPlace(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		ItemStack spent = warm("u238", 100);
		CompoundTag t = LeafiaRodItem.data(spent);
		t.putDouble("depletion", rod("u238").life + 1);
		LeafiaRodItem.putData(spent, t);
		reactor.slots[4] = spent;
		reactor.controlLevel = reactor.targetLevel = 1;
		try {
			reactor.updateEntity();
			helper.assertTrue(reactor.slots[4].getItem() == rod("depleteduranium238"), "slot 4 holds " + reactor.slots[4]);
			helper.assertTrue(LeafiaRodItem.data(reactor.slots[4]).getDouble("depletion") == 0, "with its depletion reset");
			helper.assertTrue(!reactor.isRemoved(), "and the reactor goes on");
		} finally {
			reactor.controlLevel = reactor.targetLevel = 0;
			for(int i = 0; i < 12; i++) reactor.slots[i] = ItemStack.EMPTY;
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aRodOverTenMillionDegreesEmptiesTheReactorAndBlowsOneBlockAbove(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		withHandler(helper, handler -> {
			BlockEntityReactorResearch reactor = reactor(helper);
			ItemStack hot = warm("nu", 20_000_000);
			reactor.slots[2] = hot;
			reactor.slots[9] = warm("nu", 300);
			reactor.controlLevel = reactor.targetLevel = 1;
			cleanBlasts(helper);
			try {
				reactor.updateEntity();

				helper.assertTrue(helper.getBlockState(CENTRE).isAir() && reactor.isRemoved(), "the reactor block is gone");
				boolean empty = true;
				for(int i = 0; i < 12; i++) empty &= reactor.slots[i].isEmpty();
				helper.assertTrue(empty, "every slot is empty");
				// emptied before the block went, so the rods were not spilled where it stood
				List<net.minecraft.world.entity.item.ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new AABB(helper.absolutePos(CENTRE)).inflate(6));
				boolean rods = false;
				for(net.minecraft.world.entity.item.ItemEntity item : spilled) {
					rods |= item.getItem().getItem() instanceof LeafiaRodItem;
					item.discard();
				}
				helper.assertTrue(!rods, "no rod was dropped");
				List<EntityNukeExplosionMK5> blasts = helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class, new AABB(helper.absolutePos(CENTRE)).inflate(8));
				helper.assertTrue(blasts.size() == 1 && Math.abs(blasts.get(0).getY() - (helper.absolutePos(CENTRE).getY() + 1.5)) <= 0.01,
						"one blast, a block above the reactor: " + blasts.size());
				helper.assertTrue(takeAway(helper, EntityNukeTorex.class) == 1, "and one cloud");
			} finally {
				cleanBlasts(helper);
			}
		});
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aRodThatHasSpilledForMoreThanAHundredTicksDetonatesSmallAndThenTheReactorBlows(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		withHandler(helper, handler -> {
			BlockEntityReactorResearch reactor = reactor(helper);
			ItemStack melting = warm("nu", rod("nu").meltingPoint + 500);
			CompoundTag t = LeafiaRodItem.data(melting);
			t.putBoolean("melting", true);
			t.putInt("spillage", 100);
			t.putInt("generosityTimer", 0);
			LeafiaRodItem.putData(melting, t);
			reactor.slots[1] = melting;
			reactor.slots[6] = warm("nu", 300);
			reactor.controlLevel = reactor.targetLevel = 1;
			LeafiaRodItem nu = rod("nu");
			cleanBlasts(helper);
			try {
				reactor.updateEntity();

				helper.assertTrue(LeafiaRodItem.data(melting).getInt("spillage") == 101, "the step added one to 100");
				// the reactor's own meltdown then leaves its steel-corium-steel stack where it stood
				helper.assertTrue(reactor.isRemoved() && helper.getBlockState(CENTRE).getBlock() != ModBlocks.reactor_research.get(), "the reactor block is gone");
				boolean empty = true;
				for(int i = 0; i < 12; i++) empty &= reactor.slots[i].isEmpty();
				helper.assertTrue(empty, "every slot is empty");
				// upstream: resetDetonate, radius 2, detonate (which grows it by nothing for an ordinary rod), then explode()
				helper.assertTrue(nu.detonateRadius == 2F, "the detonation ran at radius 2: " + nu.detonateRadius);
				helper.assertTrue(handler.getRadiation(helper.getLevel(), helper.absolutePos(CENTRE).getX(), helper.absolutePos(CENTRE).getY(), helper.absolutePos(CENTRE).getZ()) > 0,
						"and the chunk was irradiated");
			} finally {
				nu.resetDetonate();
				cleanBlasts(helper);
			}
		});
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aRodSpillingForExactlyAHundredTicksDoesNotYetBlow(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityReactorResearch reactor = reactor(helper);
		ItemStack melting = warm("nu", rod("nu").meltingPoint + 500);
		CompoundTag t = LeafiaRodItem.data(melting);
		t.putBoolean("melting", true);
		t.putInt("spillage", 98);
		t.putInt("generosityTimer", 0);
		LeafiaRodItem.putData(melting, t);
		reactor.slots[1] = melting;
		reactor.controlLevel = reactor.targetLevel = 1;
		try {
			reactor.updateEntity();
			helper.assertTrue(LeafiaRodItem.data(reactor.slots[1]).getInt("spillage") == 99 && !reactor.isRemoved(), "99 is not over 100");
			reactor.updateEntity();
			helper.assertTrue(LeafiaRodItem.data(reactor.slots[1]).getInt("spillage") == 100 && !reactor.isRemoved(), "100 is not over 100 either: upstream's test is a strict >");
		} finally {
			reactor.controlLevel = reactor.targetLevel = 0;
			for(int i = 0; i < 12; i++) reactor.slots[i] = ItemStack.EMPTY;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the drum

	/** The decay a rod holds after one idle step of the heat function and one drum cooling of interval {@code r}. */
	private static double decayAfter(double decay, int r) {
		return Math.max(decay * 0.99992694932 - r / 60d / 60 / 20 / 30, 0);
	}

	@GameTest(template = EMPTY)
	public static void aRodInADrumBesideWaterCoolsAndLosesItsDecay(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockPos where = new BlockPos(2, 2, 2);
		helper.setBlock(where, ModBlocks.machine_waste_drum.get());
		BlockEntityWasteDrum drum = (BlockEntityWasteDrum) helper.getBlockEntity(where);
		ItemStack rod = warm("nu", 300);
		CompoundTag t = LeafiaRodItem.data(rod);
		t.putDouble("decay", 1.0);
		LeafiaRodItem.putData(rod, t);
		drum.slots[4] = rod;
		try {
			drum.updateEntity();
			helper.assertTrue(LeafiaRodItem.data(drum.slots[4]).getDouble("heat") == 300 && LeafiaRodItem.data(drum.slots[4]).getDouble("decay") == 1.0, "with no water nothing happens");

			helper.setBlock(where.above(), Blocks.WATER.defaultBlockState());
			int r = BlockEntityWasteDrum.coolingInterval(1);
			drum.updateEntity();
			CompoundTag after = LeafiaRodItem.data(drum.slots[4]);
			helper.assertTrue(after.getDouble("incoming") == 0, "no incoming flux: a drum has none");
			helper.assertTrue(Math.abs(after.getDouble("decay") - decayAfter(1.0, r)) < 1e-12, "the decay fell by the step's own and r / 2,160,000: " + after.getDouble("decay") + " against " + decayAfter(1.0, r));
			helper.assertTrue(after.contains("cooled") && after.getDouble("heat") <= 300 + 1e-9, "it was cooled, not heated: " + after.getDouble("heat"));
		} finally {
			drum.slots[4] = ItemStack.EMPTY;
			helper.setBlock(where.above(), Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDrumsCoolingIsUpstreamsArithmeticOnTheTag(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(int r : new int[] { 1, 20, 400 }) {
			ItemStack rod = warm("nu", 400);
			CompoundTag t = LeafiaRodItem.data(rod);
			t.putDouble("decay", 1.0);
			LeafiaRodItem.putData(rod, t);
			ItemStack reference = rod.copy();
			rod("nu").HeatFunction(reference, true, 0, r / 18000f, 20, 100);
			LeafiaRodFuel.coolInDrum(rod, r);
			helper.assertTrue(LeafiaRodItem.data(rod).getDouble("heat") == LeafiaRodItem.data(reference).getDouble("heat"),
					"interval " + r + ": the heat is HeatFunction(rod, true, 0, r / 18000, 20, 100)'s: " + LeafiaRodItem.data(rod).getDouble("heat"));
			helper.assertTrue(Math.abs(LeafiaRodItem.data(rod).getDouble("decay") - decayAfter(1.0, r)) < 1e-12, "interval " + r + ": the decay is " + LeafiaRodItem.data(rod).getDouble("decay"));
		}

		// floored at zero, never below
		ItemStack tiny = warm("nu", 400);
		CompoundTag t = LeafiaRodItem.data(tiny);
		t.putDouble("decay", 1e-9);
		LeafiaRodItem.putData(tiny, t);
		LeafiaRodFuel.coolInDrum(tiny, 400);
		helper.assertTrue(LeafiaRodItem.data(tiny).getDouble("decay") == 0, "a nearly spent decay goes to zero and no lower");

		// a stack that is not a rod, or nothing, is left alone
		ItemStack stick = new ItemStack(Items.STICK);
		LeafiaRodFuel.coolInDrum(stick, 20);
		LeafiaRodFuel.coolInDrum(ItemStack.EMPTY, 20);
		helper.assertTrue(!stick.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA), "a stick is left alone");
		helper.succeed();
	}
}
