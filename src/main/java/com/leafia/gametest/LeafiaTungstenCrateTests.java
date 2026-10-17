package com.leafia.gametest;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blockentity.machine.storage.BlockEntityCrateTungsten;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.slices.Slice1;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.network.spk_cable.uninos.SPKNet;
import com.leafia.contents.network.spk_cable.uninos.SPKNode;
import com.leafia.contents.nonmachines.storage.items.CrateLabels;
import com.leafia.contents.nonmachines.storage.items.TungstenCrateSpk;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.recipes.DFCRecipes;
import com.leafia.lib.LeafiaRef;

import com.hbm.gametest.TickWindow;
import com.hbm.uninos.UniNodespace;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tungsten crate the addon makes (D-3021): SPK into a crate, and a beam's smelt reading the DFC table.
 *
 * <p>Every test that switches {@code overhauls.tungstenCrate} puts it back. The random roll of the smelt is
 * never probed inside its band (D-3004): the arithmetic is checked against a source that always answers the same,
 * on either side of the boundary it makes, and a real beam is given so much more than the need that the roll
 * cannot fail.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaTungstenCrateTests {

	private static final String EMPTY = "empty";

	private static final BlockPos AT = new BlockPos(1, 1, 1);

	/** Runs a body with the switch set, and puts it back. */
	private static void with(boolean on, Runnable body) {
		boolean before = LeafiaFeatures.tungstenCrate;
		LeafiaFeatures.tungstenCrate = on;
		try {
			body.run();
		} finally {
			LeafiaFeatures.tungstenCrate = before;
		}
	}

	private static BlockEntityCrateTungsten crate(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.crate_tungsten.get());
		BlockEntity be = helper.getBlockEntity(pos);
		helper.assertTrue(be instanceof BlockEntityCrateTungsten, "the tungsten crate made " + be);
		return (BlockEntityCrateTungsten) be;
	}

	/** A source that answers every double the same. */
	private static RandomSource fixed(double value) {
		return new LegacyRandomSource(0L) {
			@Override
			public double nextDouble() {
				return value;
			}
		};
	}

	// ------------------------------------------------------------------ the table

	@GameTest(template = EMPTY)
	public static void theTableHoldsUpstreamsRowsByItemBlockAndOreName(GameTestHelper helper) {

		// the rows this port can write (28 of the 30; the roasted marshmallow and the unobtainium nuggets do not exist here)
		helper.assertTrue(DFCRecipes.size() >= 26, "the table has its rows: " + DFCRecipes.size());

		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.powder_chlorophyte.get())) == 20_000_000L, "chlorophyte: 20,000,000");
		helper.assertTrue(DFCRecipes.getOutput(new ItemStack(ModItems.powder_chlorophyte.get())).is(ModItems.powder_balefire.get()), "gives balefire dust");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.powder_balefire.get())) == 60_000_000L
				&& DFCRecipes.getOutput(new ItemStack(ModItems.powder_balefire.get())).is(ModItems.egg_balefire_shard.get()), "balefire dust: 60,000,000, an egg shard");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(Items.STICK)) == 120_000_000L && DFCRecipes.getOutput(new ItemStack(Items.STICK)).is(Items.OAK_LOG), "a stick: a log, 120,000,000");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(Items.STONE)) == 120_000_000L && DFCRecipes.getOutput(new ItemStack(Items.STONE)).is(Items.IRON_ORE), "stone: iron ore");
		helper.assertTrue(DFCRecipes.getOutput(new ItemStack(Items.GRAVEL)).is(Items.COAL_ORE) && DFCRecipes.getOutput(new ItemStack(Items.NETHERRACK)).is(Items.NETHER_QUARTZ_ORE), "gravel and netherrack");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.particle_hydrogen.get())) == 500_000_000L, "a hydrogen particle: 500,000,000");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.particle_amat.get())) == 3_000_000_000L
				&& DFCRecipes.getOutput(new ItemStack(ModItems.particle_amat.get())).is(ModItems.particle_aschrab.get()), "an antimatter particle: 3,000,000,000");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.undefined.get())) == 80_000_000_000L
				&& DFCRecipes.getOutput(new ItemStack(ModItems.undefined.get())).is(ModItems.glitch.get()), "undefined: 80,000,000,000, a glitch");

		// by ore name: redstone dust and the three forms of uranium
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(Items.REDSTONE)) == 200_000L && DFCRecipes.getOutput(new ItemStack(Items.REDSTONE)).is(ModItems.ingot_mercury.get()),
				"redstone dust: mercury, 200,000");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.nugget_uranium.get())) == 200_000_000L
				&& DFCRecipes.getOutput(new ItemStack(ModItems.nugget_uranium.get())).is(ModItems.nugget_schrabidium.get()), "a uranium nugget: schrabidium, 200,000,000");
		helper.assertTrue(DFCRecipes.getOutput(new ItemStack(ModItems.ingot_uranium.get())).is(ModItems.ingot_schrabidium.get()), "an ingot");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.ingot_plutonium.get())) == 2_000_000_000L
				&& DFCRecipes.getOutput(new ItemStack(ModItems.ingot_plutonium.get())).is(ModItems.ingot_euphemium.get()), "plutonium: euphemium, 2,000,000,000");
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(ModItems.ingot_u238.get())) == 40_000_000_000L, "uranium-238 ingot: 40,000,000,000");

		// an item with no row, and an empty stack
		helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(Items.DIRT)) == -1 && DFCRecipes.getOutput(new ItemStack(Items.DIRT)) == null, "dirt has none");
		helper.assertTrue(DFCRecipes.getRequiredFlux(ItemStack.EMPTY) == -1, "nor does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aRowIsKeptOnceAndItsOutputIsACopy(GameTestHelper helper) {

		int before = DFCRecipes.size();
		try {
			DFCRecipes.setRecipe(5L, Items.DIRT, new ItemStack(Items.GRAVEL));
			DFCRecipes.setRecipe(7L, Items.DIRT, new ItemStack(Items.SAND, 2));
			helper.assertTrue(DFCRecipes.size() == before + 1, "a row written twice is one row");
			helper.assertTrue(DFCRecipes.getRequiredFlux(new ItemStack(Items.DIRT, 33)) == 7L, "the later write wins, and a stack of any size matches");
			ItemStack out = DFCRecipes.getOutput(new ItemStack(Items.DIRT));
			out.setCount(60);
			helper.assertTrue(DFCRecipes.getOutput(new ItemStack(Items.DIRT)).getCount() == 2, "the output is a copy");
			ItemStack given = new ItemStack(Items.SAND, 2);
			DFCRecipes.setRecipe(9L, Items.DIRT, given);
			given.setCount(60);
			helper.assertTrue(DFCRecipes.getOutput(new ItemStack(Items.DIRT)).getCount() == 2, "the output given in is copied too");
			DFCRecipes.setRecipe(11L, new ItemStack(Items.DIRT, 5), new ItemStack(Items.SAND, 2));
			helper.assertTrue(DFCRecipes.size() == before + 1 && DFCRecipes.getRequiredFlux(new ItemStack(Items.DIRT)) == 11L, "a stack as the key matches a stack of any size, and is the same row");
			try {
				DFCRecipes.setRecipe(1L, 12, new ItemStack(Items.SAND));
				helper.fail("a number was taken as an input");
			} catch(IllegalArgumentException expected) { }
		} finally {
			DFCRecipes.remove(Items.DIRT);
		}
		helper.assertTrue(DFCRecipes.size() == before, "the table is as it was: " + DFCRecipes.size() + " against " + before);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the smelt

	@GameTest(template = EMPTY)
	public static void theRollFollowsUpstreamsArithmeticOnEitherSideOfItsBoundary(GameTestHelper helper) {

		ItemStack chlorophyte = new ItemStack(ModItems.powder_chlorophyte.get(), 1);
		ItemStack furnace = new ItemStack(Items.IRON_INGOT);

		// the need is 20,000,000; the beam must beat 0.9 of it, and 0.001 > count x roll x (0.9 need / beam)
		// a roll of 0.5 and one dust: the boundary is a beam of 9 x 10^9
		ItemStack above = TungstenCrateSpk.smelt(fixed(0.5), chlorophyte, 9_100_000_000L, furnace);
		helper.assertTrue(above.is(ModItems.powder_balefire.get()), "a beam over the boundary makes balefire dust: " + above);
		ItemStack below = TungstenCrateSpk.smelt(fixed(0.5), chlorophyte, 8_900_000_000L, furnace);
		helper.assertTrue(below == furnace, "a beam under it leaves the furnace's answer");
		ItemStack exactly = TungstenCrateSpk.smelt(fixed(0.5), chlorophyte, 9_000_000_000L, furnace);
		helper.assertTrue(exactly == furnace, "at the boundary 0.001 is not greater than 0.001");

		// ten dust need ten times the beam
		ItemStack ten = new ItemStack(ModItems.powder_chlorophyte.get(), 10);
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.5), ten, 91_000_000_000L, furnace).is(ModItems.powder_balefire.get()), "ten dust under a beam of 9.1 x 10^10");
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.5), ten, 89_000_000_000L, furnace) == furnace, "and not under 8.9 x 10^10");

		// a roll of nothing always passes; a roll of one needs a beam of 1.8 x 10^10 for one dust
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.0), ten, 18_000_001L, furnace).is(ModItems.powder_balefire.get()), "a roll of 0 passes once the beam beats 0.9 of the need");
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.0), ten, 18_000_000L, furnace) == furnace, "a beam of exactly 0.9 of the need does not");
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.0), ten, 17_999_999L, furnace) == furnace, "nor one below it");
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(1.0), chlorophyte, 17_999_999_999L, furnace) == furnace, "a roll of 1 for one dust needs more than 1.8 x 10^10");
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(1.0), chlorophyte, 18_100_000_000L, furnace).is(ModItems.powder_balefire.get()), "and passes over it");

		// an item with no row is left alone, however large the beam
		helper.assertTrue(TungstenCrateSpk.smelt(fixed(0.0), new ItemStack(Items.DIRT), Long.MAX_VALUE, furnace) == furnace, "dirt is left alone");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBeamOnAnOverhauledCrateSmeltsTheTableAndRemembersTheBeam(GameTestHelper helper) {

		with(true, () -> {
			BlockEntityCrateTungsten crate = crate(helper, AT);
			helper.assertTrue(crate instanceof TungstenCrateSpk, "the overhaul made the addon's crate: " + crate.getClass().getSimpleName());
			TungstenCrateSpk spk = (TungstenCrateSpk) crate;
			crate.setItem(0, new ItemStack(ModItems.powder_chlorophyte.get(), 20));
			crate.setItem(1, new ItemStack(Items.COBBLESTONE, 8));
			crate.setItem(2, new ItemStack(Items.DIRT, 3));
			crate.setItem(4, new ItemStack(ModItems.billet_polonium.get(), 2));

			crate.addEnergy(helper.getLevel(), helper.absolutePos(AT), 1_000_000_000_000L, null);

			helper.assertTrue(crate.getItem(0).is(ModItems.powder_balefire.get()) && crate.getItem(0).getCount() == 20, "chlorophyte became balefire dust, all twenty: " + crate.getItem(0));
			helper.assertTrue(crate.getItem(1).is(Items.STONE) && crate.getItem(1).getCount() == 8, "cobblestone still smelts to stone: " + crate.getItem(1));
			helper.assertTrue(crate.getItem(2).is(Items.DIRT) && crate.getItem(2).getCount() == 3, "dirt is left");
			helper.assertTrue(crate.getItem(4).is(ModItems.billet_yharonite.get()) && crate.getItem(4).getCount() == 2, "and the base mod's polonium rule still runs: " + crate.getItem(4));
			helper.assertTrue(spk.joules == 1_000_000_000_000L && spk.getSPK() == 1_000_000_000_000L, "the last beam is the crate's SPK");
			helper.assertTrue(crate.getHeatTimer() == BlockEntityCrateTungsten.HEAT_TICKS, "and it glows");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBeamUnderNinetyPercentOfTheNeedChangesNothing(GameTestHelper helper) {

		with(true, () -> {
			BlockEntityCrateTungsten crate = crate(helper, AT);
			crate.setItem(0, new ItemStack(ModItems.powder_chlorophyte.get(), 5));
			crate.addEnergy(helper.getLevel(), helper.absolutePos(AT), 17_999_999L, null);
			helper.assertTrue(crate.getItem(0).is(ModItems.powder_chlorophyte.get()), "under 18,000,000: " + crate.getItem(0));
			crate.addEnergy(helper.getLevel(), helper.absolutePos(AT), 18_000_000L, null);
			helper.assertTrue(crate.getItem(0).is(ModItems.powder_chlorophyte.get()), "at exactly 18,000,000 the beam must beat it: " + crate.getItem(0));
		});
		helper.succeed();
	}

	/** Upstream draws its random number before it looks for an output, so a stack with no row costs one draw. */
	@GameTest(template = EMPTY)
	public static void aStackWithNoRowStillDrawsOneRollAndOneWithTooSmallABeamNone(GameTestHelper helper) {

		int[] draws = { 0 };
		RandomSource counting = new LegacyRandomSource(0L) {
			@Override
			public double nextDouble() {
				draws[0]++;
				return 0.0;
			}
		};
		ItemStack furnace = new ItemStack(Items.IRON_INGOT);
		helper.assertTrue(TungstenCrateSpk.smelt(counting, new ItemStack(Items.DIRT), 5L, furnace) == furnace && draws[0] == 1,
				"dirt has no row: left alone, after one draw (" + draws[0] + ")");
		TungstenCrateSpk.smelt(counting, new ItemStack(ModItems.powder_chlorophyte.get()), 17_999_999L, furnace);
		helper.assertTrue(draws[0] == 1, "a beam under 0.9 of a row's need draws nothing (" + draws[0] + ")");
		TungstenCrateSpk.smelt(counting, new ItemStack(Items.DIRT), 0L, furnace);
		helper.assertTrue(draws[0] == 1, "and no beam at all draws nothing (" + draws[0] + ")");
		helper.succeed();
	}

	/**
	 * The hook's own gate: a crate that exists as the addon's, with the switch since turned off, takes nothing from the cable.
	 * Asked synchronously once the cable has its net, so that no other test's switch can be what answers.
	 */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void anOverhauledCrateStopsSubscribingWhenTheSwitchGoesOff(GameTestHelper helper) {

		if(!LeafiaFeatures.spkCable) { helper.succeed(); return; }

		BlockPos centre = new BlockPos(2, 2, 2);
		helper.setBlock(centre.east(), LeafiaBlocks.spk_cable.get());

		TickWindow.runAfterDelay(helper, 12, () -> {
			BlockEntityCrateTungsten[] crate = new BlockEntityCrateTungsten[1];
			with(true, () -> crate[0] = crate(helper, centre));
			helper.assertTrue(crate[0] instanceof TungstenCrateSpk, "made as the addon's");
			SPKNode node = (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(centre.east()), SPKNet.THE_NETWORK_PROVIDER);
			helper.assertTrue(node != null && node.net != null, "the cable has its net");

			with(false, () -> TungstenCrateSpk.HOOK.tick(crate[0]));
			helper.assertTrue(node.net.receiverEntries.isEmpty(), "nothing subscribed while the switch is off");
			with(true, () -> TungstenCrateSpk.HOOK.tick(crate[0]));
			helper.assertTrue(node.net.receiverEntries.containsKey(crate[0]), "and the same call subscribes once it is on");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void aStackThatWouldOverflowItsOutputIsLeftAlone(GameTestHelper helper) {

		// upstream's rule, shared with the base crate: result count x stack size must fit one stack of the result
		DFCRecipes.setRecipe(1L, Items.DIRT, new ItemStack(Items.SAND, 2));
		try {
			with(true, () -> {
				BlockEntityCrateTungsten crate = crate(helper, AT);
				crate.setItem(0, new ItemStack(Items.DIRT, 32));
				crate.setItem(1, new ItemStack(Items.DIRT, 33));
				crate.addEnergy(helper.getLevel(), helper.absolutePos(AT), Long.MAX_VALUE, null);
				helper.assertTrue(crate.getItem(0).is(Items.SAND) && crate.getItem(0).getCount() == 64, "thirty-two dirt fit sixty-four sand: " + crate.getItem(0));
				helper.assertTrue(crate.getItem(1).is(Items.DIRT) && crate.getItem(1).getCount() == 33, "thirty-three would be sixty-six: " + crate.getItem(1));
			});
		} finally {
			DFCRecipes.remove(Items.DIRT);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void withTheSwitchOffTheBaseCrateIsUpstreams(GameTestHelper helper) {

		with(false, () -> {
			BlockEntityCrateTungsten crate = crate(helper, AT);
			helper.assertTrue(crate.getClass() == BlockEntityCrateTungsten.class, "the base mod's own block entity: " + crate.getClass().getSimpleName());
			crate.setItem(0, new ItemStack(ModItems.powder_chlorophyte.get(), 5));
			crate.setItem(1, new ItemStack(ModItems.billet_polonium.get()));
			crate.addEnergy(helper.getLevel(), helper.absolutePos(AT), 1_000_000_000_000L, null);
			helper.assertTrue(crate.getItem(0).is(ModItems.powder_chlorophyte.get()), "the table is not read");
			helper.assertTrue(crate.getItem(1).is(ModItems.billet_yharonite.get()), "the base's own polonium rule is");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCrateMadeBeforeTheSwitchWasOnSmeltsTheTableOnceItIs(GameTestHelper helper) {

		BlockEntityCrateTungsten[] crate = new BlockEntityCrateTungsten[1];
		with(false, () -> crate[0] = crate(helper, AT));
		helper.assertTrue(crate[0].getClass() == BlockEntityCrateTungsten.class, "made while off");
		with(true, () -> {
			crate[0].setItem(0, new ItemStack(ModItems.powder_chlorophyte.get(), 4));
			crate[0].addEnergy(helper.getLevel(), helper.absolutePos(AT), 1_000_000_000_000L, null);
			helper.assertTrue(crate[0].getItem(0).is(ModItems.powder_balefire.get()), "the hook applies to the crates that already exist");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ SPK

	@GameTest(template = EMPTY)
	public static void theCrateTakesAllTheSpkItIsOfferedAndKeepsItsLastBeam(GameTestHelper helper) {

		with(true, () -> {
			TungstenCrateSpk crate = (TungstenCrateSpk) crate(helper, AT);
			crate.setItem(0, new ItemStack(Items.STICK, 10));

			helper.assertTrue(crate.getMaxSPK() == Long.MAX_VALUE, "its capacity is a long's");
			helper.assertTrue(crate.transferSPK(500_000L, true) == 0 && crate.getSPK() == 0 && crate.getItem(0).is(Items.STICK), "a simulated transfer changes nothing");
			helper.assertTrue(crate.transferSPK(500_000L, false) == 0, "nothing overshoots");
			helper.assertTrue(crate.getSPK() == 500_000L, "the beam is the stored SPK");
			helper.assertTrue(crate.getItem(0).is(Items.STICK), "500,000 is not enough for a stick");
			crate.setSPK(12L);
			helper.assertTrue(crate.getSPK() == 12L, "set");
			// a beam of 10^15, so that the roll (0.001 > 10 x roll x 1.08 x 10^-7) cannot fail for a stack of ten sticks (D-3004)
			helper.assertTrue(crate.transferSPK(1_000_000_000_000_000L, false) == 0 && crate.getSPK() == 1_000_000_000_000_000L, "a second beam replaces the first, it does not add");
			helper.assertTrue(crate.getItem(0).is(Items.OAK_LOG) && crate.getItem(0).getCount() == 10, "and a big enough one turned the sticks to logs: " + crate.getItem(0));
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void anOverhauledCrateSubscribesToACableOnAnyOfItsSixFaces(GameTestHelper helper) {

		if(!LeafiaFeatures.spkCable) { helper.succeed(); return; }

		boolean before = LeafiaFeatures.tungstenCrate;
		LeafiaFeatures.tungstenCrate = true;
		BlockPos centre = new BlockPos(2, 2, 2);
		BlockEntityCrateTungsten crate = crate(helper, centre);
		helper.assertTrue(crate instanceof TungstenCrateSpk, "the overhaul's crate");
		BlockPos[] cables = { centre.above(), centre.below(), centre.north(), centre.south(), centre.east(), centre.west() };
		for(BlockPos cable : cables) helper.setBlock(cable, LeafiaBlocks.spk_cable.get());

		TickWindow.runAfterDelay(helper, 12, () -> {
			try {
				int found = 0;
				for(BlockPos cable : cables) {
					SPKNode node = (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(cable), SPKNet.THE_NETWORK_PROVIDER);
					helper.assertTrue(node != null && node.net != null, "the cable at " + cable + " has a net");
					if(node.net.receiverEntries.containsKey(crate)) found++;
				}
				// the six cables are not joined to each other (each touches only the crate), so each is its own net with the crate in it
				helper.assertTrue(found == 6, "the crate subscribed on all six faces, got " + found);
			} finally {
				LeafiaFeatures.tungstenCrate = before;
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void withTheSwitchOffTheCrateSubscribesToNothing(GameTestHelper helper) {

		if(!LeafiaFeatures.spkCable) { helper.succeed(); return; }

		BlockPos centre = new BlockPos(2, 2, 2);
		BlockEntityCrateTungsten[] made = new BlockEntityCrateTungsten[1];
		with(false, () -> made[0] = crate(helper, centre));
		helper.assertTrue(!(made[0] instanceof TungstenCrateSpk), "a plain crate");
		helper.setBlock(centre.east(), LeafiaBlocks.spk_cable.get());

		TickWindow.runAfterDelay(helper, 12, () -> {
			SPKNode node = (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(centre.east()), SPKNet.THE_NETWORK_PROVIDER);
			helper.assertTrue(node != null && node.net != null && node.net.receiverEntries.isEmpty(), "nothing subscribed");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ the type and the labels

	@GameTest(template = EMPTY)
	public static void theOverrideIsOnlyAskedForWhileTheSwitchIsOn(GameTestHelper helper) {

		helper.assertTrue(LeafiaBlockEntities.CRATE_TUNGSTEN != null, "the type exists on a test server");
		with(true, () -> helper.assertTrue(crate(helper, AT).getType() == LeafiaBlockEntities.CRATE_TUNGSTEN.get(), "on: the addon's type"));
		with(false, () -> helper.assertTrue(crate(helper, new BlockPos(3, 1, 1)).getType() == Slice1.CRATE_TUNGSTEN.get(), "off: the base mod's"));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOverhauledCrateKeepsItsLabelsAndItsInventoryThroughASaveAndLoad(GameTestHelper helper) {

		with(true, () -> {
			BlockEntityCrateTungsten crate = crate(helper, AT);
			crate.setItem(5, new ItemStack(Items.DIAMOND, 3));
			CrateLabels.of(crate).setText(new String[] { "", "", "", "" }, "hot", "", "");
			var access = helper.getLevel().registryAccess();
			CompoundTag saved = crate.saveWithFullMetadata(access);
			helper.assertTrue(saved.getString("id").equals("leafia:crate_tungsten"), "saved under the addon's type: " + saved.getString("id"));
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(AT), helper.getBlockState(AT), saved, access);
			helper.assertTrue(loaded instanceof TungstenCrateSpk back && back.getItem(5).is(Items.DIAMOND) && CrateLabels.of((BlockEntityCrateBase) loaded).upper().equals("hot"),
					"loaded as the addon's crate with both: " + loaded);
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void whatARackAsksTheBlockForIsTheAddonsCrateWhileTheSwitchIsOn(GameTestHelper helper) {

		with(true, () -> {
			var state = ModBlocks.crate_tungsten.get().defaultBlockState();
			BlockEntity be = ((net.minecraft.world.level.block.EntityBlock) ModBlocks.crate_tungsten.get()).newBlockEntity(helper.absolutePos(AT), state);
			helper.assertTrue(be instanceof TungstenCrateSpk, "the block makes the addon's crate for anyone who asks: " + be);
		});
		helper.succeed();
	}
}
