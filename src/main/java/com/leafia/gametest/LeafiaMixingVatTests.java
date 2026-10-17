package com.leafia.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.processing.mixingvat.MixingVatTE;
import com.leafia.contents.machines.processing.mixingvat.container.MixingVatMenu;
import com.leafia.contents.machines.processing.mixingvat.proxy.MixingVatProxy;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The mixing vat: the batch it mixes and everything that stops one, the identifier, the Forge handler and the three
 * port blocks that show it, the placement, the screen's one button, and a menu whose shift-click is a bug.
 *
 * <p>Every number is upstream's and every batch is run through the machine's own {@code mix()}, with a few on a
 * real tick ({@link TickWindow}). Four things are pinned because they surprise: the mixed salt has no heat, power is
 * not saved, a stuck output stays stuck until it is voided, and the shift-click never reaches the ingredient slots.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaMixingVatTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	private static boolean on() {
		return LeafiaFeatures.mixingVatMachine();
	}

	// ---- the rig ---------------------------------------------------------------------------

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static float yawFor(Direction looking) {
		return switch(looking) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> 270F;
			default -> throw new IllegalArgumentException(looking.toString());
		};
	}

	/** A bare core, with nothing around it. */
	private static MixingVatTE core(GameTestHelper helper, BlockPos pos, Direction dir) {
		helper.setBlock(pos, LeafiaBlocks.mixingvat.get().defaultBlockState()
				.setValue(BlockDummyable.PART, Part.CORE)
				.setValue(BlockDummyable.DIR, dir));
		return (MixingVatTE) helper.getBlockEntity(pos);
	}

	private static MixingVatTE core(GameTestHelper helper) {
		return core(helper, new BlockPos(2, 1, 2), Direction.NORTH);
	}

	/** Places the machine as a player does; the core is the clicked cell (an offset of zero) and the front is where the player looks. */
	private static MixingVatTE place(GameTestHelper helper, BlockPos core, Direction looking) {
		Block block = LeafiaBlocks.mixingvat.get();
		ServerPlayer player = mockPlayer(helper);
		player.setYRot(yawFor(looking));
		player.setXRot(0F);
		helper.setBlock(core, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(core), block.defaultBlockState(), player, new ItemStack(block));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof MixingVatTE, "the machine was not placed at " + core + " looking " + looking);
		return (MixingVatTE) be;
	}

	private static FluidStack salt(int mB, double heat, String fuel, double amount) {
		FluidStack s = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, mB);
		CompoundTag tag = MSRTEBase.nbtProtocol((CompoundTag) null);
		tag.putDouble("heat", heat);
		if(fuel != null) {
			Map<String, Double> m = new LinkedHashMap<>();
			m.put(fuel, amount);
			MSRTEBase.writeMixture(m, tag);
		}
		LeafiaFF.setTag(s, tag);
		return s;
	}

	private static Map<String, Double> mixtureOf(FluidStack stack) {
		return MSRTEBase.readMixture(MSRTEBase.nbtProtocol(stack));
	}

	private static void runBatch(MixingVatTE vat) {
		for(int i = 0; i < MixingVatTE.maxProgress; i++) vat.mix();
	}

	// ---- the constants ---------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theConstantsAreUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		helper.assertTrue(MixingVatTE.SLOTS == 23 && vat.getContainerSize() == 23, "3 + 10 + 10 slots");
		helper.assertTrue(MixingVatTE.maxPower == 100_000 && MixingVatTE.maxProgress == 60 && MixingVatTE.POWER_PER_TICK == 50,
				"100,000 HE, a batch of 60 ticks at 50 HE");
		helper.assertTrue(MixingVatTE.BATCH == 1000 && vat.tankNc0.getCapacity() == 4000 && vat.tankNc1.getCapacity() == 4000, "a bucket from tanks of 4,000");
		helper.assertTrue(vat.nuclearMode && vat.inputTypeNc == LeafiaFluids.FLUORIDE && vat.progress == 0, "the defaults");
		helper.assertTrue(MixingVatTE.FIRST_INGREDIENT == 15 && MixingVatTE.LAST_INGREDIENT == 20 && MixingVatTE.SLOT_IDENTIFIER == 21
				&& MixingVatTE.SLOT_IDENTIFIER_DONE == 22, "the slots");
		helper.assertTrue(vat.getName().equals("tile.mixingvat.name"), "its name");
		helper.succeed();
	}

	// ---- the fuels -------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aNuggetIsItsIsotopesFuel(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }

		helper.assertTrue(MixingVatTE.getFuelType(new ItemStack(ModItems.nugget_th232.get())) == MSRFuel.th232, "thorium 232");
		helper.assertTrue(MixingVatTE.getFuelType(new ItemStack(ModItems.nugget_u233.get())) == MSRFuel.u233, "uranium 233");
		helper.assertTrue(MixingVatTE.getFuelType(new ItemStack(ModItems.nugget_u235.get())) == MSRFuel.u235, "uranium 235");
		helper.assertTrue(MixingVatTE.getFuelType(new ItemStack(Items.STONE)) == null && MixingVatTE.getFuelType(ItemStack.EMPTY) == null, "nothing else is");
		helper.assertTrue(MixingVatTE.getFuelType(new ItemStack(ModItems.ingot_th232.get())) == null, "an ingot is not a nugget");
		for(MSRFuel fuel : new MSRFuel[] { MSRFuel.pa233, MSRFuel.pa234 })
			helper.assertTrue(fuel.items.length == 0 && fuel.dicts.length == 0, fuel.name() + " has nothing that is it: it is bred, never added");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aStackCountsOncePerSlot(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		helper.assertTrue(vat.createMixture().isEmpty(), "nothing in, nothing out");
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get(), 64);
		vat.slots[16] = new ItemStack(ModItems.nugget_th232.get(), 1);
		vat.slots[17] = new ItemStack(ModItems.nugget_u235.get(), 5);
		vat.slots[18] = new ItemStack(Items.DIRT, 64);
		vat.slots[14] = new ItemStack(ModItems.nugget_u233.get());
		vat.slots[21] = new ItemStack(ModItems.nugget_u233.get());
		Map<String, Double> mix = vat.createMixture();
		helper.assertTrue(mix.equals(Map.of("th232", 2.0, "u235", 1.0)), "one per slot whatever its size, and only slots 15 to 20: " + mix);
		helper.succeed();
	}

	// ---- a batch ---------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aBatchMixesTheFuelIntoTheSaltAndDropsItsHeat(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		vat.tankNc0.setFluid(salt(2500, 400, "u233", 0.5));
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get(), 64);
		vat.slots[16] = new ItemStack(ModItems.nugget_u235.get(), 1);
		vat.power = 100_000;

		for(int i = 0; i < MixingVatTE.maxProgress - 1; i++) vat.mix();
		helper.assertTrue(vat.progress == 59 && vat.tankNc1.getFluidAmount() == 0 && vat.tankNc0.getFluidAmount() == 2500, "59 ticks in, nothing has moved");
		helper.assertTrue(vat.power == 100_000 - 59 * 50, "50 HE a tick: " + vat.power);

		vat.mix();
		helper.assertTrue(vat.progress == 0, "the batch ends");
		helper.assertTrue(vat.tankNc0.getFluidAmount() == 1500 && vat.tankNc1.getFluidAmount() == 1000, "a bucket crosses: "
				+ vat.tankNc0.getFluidAmount() + "/" + vat.tankNc1.getFluidAmount());
		helper.assertTrue(vat.power == 100_000 - 3000, "3,000 HE for the batch");
		helper.assertTrue(vat.slots[15].getCount() == 63 && vat.slots[16].isEmpty(), "one of each fuel slot is used");

		FluidStack out = vat.tankNc1.getFluid();
		helper.assertTrue(LeafiaFF.fromFF(out) == LeafiaFluids.FLUORIDE, "it is still salt");
		helper.assertTrue(mixtureOf(out).equals(Map.of("u233", 0.5, "th232", 1.0, "u235", 1.0)), "the salt's fuel plus the items': " + mixtureOf(out));
		helper.assertTrue(MSRTEBase.nbtProtocol(out).getDouble("heat") == 0.0, "and none of the heat it had");
		helper.assertTrue(MSRTEBase.nbtProtocol(vat.tankNc0.getFluid()).getDouble("heat") == 400.0, "the salt left behind keeps its own");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBatchWaitsOnWhatItNeedsAndLosesItsProgress(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		vat.tankNc0.setFluid(salt(2000, 0, null, 0));
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get());
		vat.power = 100_000;

		vat.progress = 5;
		vat.slots[15] = ItemStack.EMPTY;
		vat.mix();
		helper.assertTrue(vat.progress == 0, "no fuel in any slot: the progress is lost");
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get());

		vat.progress = 5;
		vat.tankNc0.setFluid(salt(999, 0, null, 0));
		vat.mix();
		helper.assertTrue(vat.progress == 0, "999 mB is not a bucket");
		vat.tankNc0.setFluid(salt(2000, 0, null, 0));

		vat.progress = 5;
		vat.power = 50;
		vat.mix();
		helper.assertTrue(vat.progress == 0 && vat.power == 50, "50 HE is not more than 50");
		vat.power = 51;
		vat.mix();
		helper.assertTrue(vat.progress == 1 && vat.power == 1, "51 is: " + vat.progress);
		vat.progress = 0;
		vat.power = 100_000;

		// the output must have room for the whole bucket of the same stack: build one the vat's own way
		vat.tankNc1.setFluid(FluidStack.EMPTY);
		vat.progress = 0;
		vat.power = 100_000;
		runBatch(vat);
		FluidStack held = vat.tankNc1.getFluid();
		helper.assertTrue(held.getAmount() == 1000 && vat.tankNc0.getFluidAmount() == 1000, "the first bucket");

		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get(), 5);
		vat.tankNc1.setFluid(held.copyWithAmount(3001));
		vat.progress = 5;
		vat.mix();
		helper.assertTrue(vat.progress == 0, "an output with room for 999 mB of the same stack stops it");

		vat.tankNc1.setFluid(held.copyWithAmount(3000));
		vat.progress = 5;
		vat.mix();
		helper.assertTrue(vat.progress == 6, "and with room for 1,000 it runs");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anOutputOfAnotherMixtureStaysStuckUntilItIsVoided(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		vat.tankNc0.setFluid(salt(4000, 0, null, 0));
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get(), 4);
		vat.power = 100_000;

		runBatch(vat);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 1000, "the first bucket");
		runBatch(vat);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 2000 && mixtureOf(vat.tankNc1.getFluid()).equals(Map.of("th232", 1.0)),
				"the same fuel joins it: a Forge tank takes a stack only with the same tag");

		vat.slots[15] = ItemStack.EMPTY;
		vat.slots[16] = new ItemStack(ModItems.nugget_u235.get(), 4);
		for(int i = 0; i < 3 * MixingVatTE.maxProgress; i++) vat.mix();
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 2000 && vat.progress == 0 && vat.tankNc0.getFluidAmount() == 2000,
				"another fuel is another tag and the output takes none of it: " + vat.tankNc1.getFluidAmount());
		helper.assertTrue(vat.slots[16].getCount() == 4, "and no fuel is used");
		helper.succeed();
	}

	// ---- the identifier --------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aSaltIdentifierSetsTheFluidAndMovesToTheNextSlot(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		// a tank of something else is emptied; one of the right salt is kept
		vat.tankNc0.setFluid(LeafiaFF.toFF(Fluids.WATER, 700));
		vat.tankNc1.setFluid(salt(300, 0, null, 0));

		ItemStack diesel = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(diesel, Fluids.DIESEL);
		vat.slots[MixingVatTE.SLOT_IDENTIFIER] = diesel;
		vat.updateEntity();
		helper.assertTrue(!vat.slots[MixingVatTE.SLOT_IDENTIFIER].isEmpty() && vat.slots[MixingVatTE.SLOT_IDENTIFIER_DONE].isEmpty(),
				"an identifier for something that is not a salt stays put");
		helper.assertTrue(vat.tankNc0.getFluidAmount() == 700, "and empties nothing");

		ItemStack id = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(id, LeafiaFluids.FLUORIDE);
		vat.slots[MixingVatTE.SLOT_IDENTIFIER] = id;
		var identifier = id.getItem(); // an emptied stack's getItem() is air
		vat.updateEntity();
		helper.assertTrue(vat.slots[MixingVatTE.SLOT_IDENTIFIER].isEmpty() && vat.slots[MixingVatTE.SLOT_IDENTIFIER_DONE].getItem() == identifier,
				"a salt identifier moves to slot 22");
		helper.assertTrue(vat.inputTypeNc == LeafiaFluids.FLUORIDE, "and sets the fluid");
		helper.assertTrue(vat.tankNc0.getFluidAmount() == 0, "the water is voided");
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 300, "the salt is kept");

		// with slot 22 taken, the next one waits
		ItemStack second = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(second, LeafiaFluids.FLUORIDE);
		vat.slots[MixingVatTE.SLOT_IDENTIFIER] = second;
		vat.updateEntity();
		helper.assertTrue(!vat.slots[MixingVatTE.SLOT_IDENTIFIER].isEmpty(), "and with slot 22 taken it stays");
		helper.succeed();
	}

	// ---- the Forge handler and the ports ---------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theCoreTakesSaltAndGivesNothingThroughItsHandler(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		helper.assertTrue(vat.fill(salt(500, 0, null, 0), FluidAction.SIMULATE) == 500 && vat.tankNc0.getFluidAmount() == 0, "a simulation");
		helper.assertTrue(vat.fill(salt(500, 0, null, 0), FluidAction.EXECUTE) == 500 && vat.tankNc0.getFluidAmount() == 500, "salt goes to the input");
		helper.assertTrue(vat.fill(LeafiaFF.toFF(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "water does not");
		helper.assertTrue(vat.fill(salt(5000, 0, null, 0), FluidAction.EXECUTE) == 3500, "and only up to 4,000");

		vat.tankNc1.setFluid(salt(800, 0, null, 0));
		helper.assertTrue(vat.drain(100, FluidAction.EXECUTE).isEmpty() && vat.drain(salt(100, 0, null, 0), FluidAction.EXECUTE).isEmpty(),
				"the core itself gives nothing");
		FluidStack out = vat.drainOut(300, FluidAction.EXECUTE);
		helper.assertTrue(out.getAmount() == 300 && vat.tankNc1.getFluidAmount() == 500, "the output is drained by drainOut");
		helper.assertTrue(vat.drainOut(salt(100, 0, null, 0), FluidAction.EXECUTE).getAmount() == 100 && vat.tankNc1.getFluidAmount() == 400, "by stack too");

		vat.nuclearMode = false;
		helper.assertTrue(vat.fill(salt(100, 0, null, 0), FluidAction.EXECUTE) == 0 && vat.drainOut(100, FluidAction.EXECUTE).isEmpty(), "the other mode does neither");
		helper.assertTrue(vat.getTanks() == 2 && vat.getTankCapacity(1) == 4000, "two tanks of 4,000");
		helper.assertTrue(vat.getSendingTank(null) == vat.tankNc1 && vat.getCorrespondingTank(null) == vat.tankNc0, "the duct network's tanks");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void thePortBlocksFillBehindAndDrainAhead(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos core = new BlockPos(8, 1, 6);
		MixingVatTE vat = place(helper, core, Direction.SOUTH);

		// facing south: right is west. The extras are core + right, core + 2 ahead, and core + 2 ahead + right.
		BlockPos backRow = core.west();
		BlockPos frontLeft = core.south(2);
		BlockPos frontRight = core.south(2).west();
		for(BlockPos extra : new BlockPos[] { backRow, frontLeft, frontRight }) {
			helper.assertTrue(helper.getBlockState(extra).getValue(BlockDummyable.PART) == Part.EXTRA, "an EXTRA at " + extra);
			helper.assertTrue(helper.getBlockEntity(extra) instanceof MixingVatProxy proxy && proxy.getType() == LeafiaBlockEntities.MIXING_VAT_DUMMY.get(),
					"a vat proxy at " + extra);
		}

		MixingVatProxy back = (MixingVatProxy) helper.getBlockEntity(backRow);
		MixingVatProxy left = (MixingVatProxy) helper.getBlockEntity(frontLeft);
		MixingVatProxy right = (MixingVatProxy) helper.getBlockEntity(frontRight);

		helper.assertTrue(back.facing() == Direction.NORTH, "the back row faces the machine's back: " + back.facing());
		helper.assertTrue(left.facing() == Direction.EAST, "the front row's inner block faces the machine's left: " + left.facing());
		helper.assertTrue(right.facing() == Direction.WEST, "and its outer block the right: " + right.facing());

		// the back row fills and cannot be drained
		helper.assertTrue(back.fill(salt(400, 0, null, 0), FluidAction.EXECUTE) == 400 && vat.tankNc0.getFluidAmount() == 400, "salt goes in behind");
		helper.assertTrue(back.drain(100, FluidAction.EXECUTE).isEmpty(), "and nothing comes out");
		helper.assertTrue(left.fill(salt(400, 0, null, 0), FluidAction.EXECUTE) == 0 && right.fill(salt(400, 0, null, 0), FluidAction.EXECUTE) == 0,
				"the front row takes none");
		// the front row drains and cannot be filled
		vat.tankNc1.setFluid(salt(900, 0, null, 0));
		helper.assertTrue(left.drain(200, FluidAction.EXECUTE).getAmount() == 200 && vat.tankNc1.getFluidAmount() == 700, "salt comes out of the left");
		helper.assertTrue(right.drain(salt(100, 0, null, 0), FluidAction.EXECUTE).getAmount() == 100 && vat.tankNc1.getFluidAmount() == 600, "and the right");
		helper.assertTrue(back.getTanks() == 2 && right.getFluidInTank(1).getAmount() == 600, "each shows the core's tanks");

		// the capability answers on a port's own side and to an unsided query only
		var level = helper.getLevel();
		BlockPos absBack = helper.absolutePos(backRow);
		helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, absBack, Direction.NORTH) != null
				&& level.getCapability(Capabilities.FluidHandler.BLOCK, absBack, null) != null, "the back row: behind, and unsided");
		for(Direction side : new Direction[] { Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN })
			helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, absBack, side) == null, "the back row shows nothing on " + side);
		BlockPos absLeft = helper.absolutePos(frontLeft);
		helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, absLeft, Direction.EAST) != null
				&& level.getCapability(Capabilities.FluidHandler.BLOCK, absLeft, Direction.WEST) == null, "the left port shows its own side");
		BlockPos absRight = helper.absolutePos(frontRight);
		helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, absRight, Direction.WEST) != null
				&& level.getCapability(Capabilities.FluidHandler.BLOCK, absRight, Direction.EAST) == null, "the right port its own");
		for(Direction side : Direction.values())
			helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(core), side) != null, "the core shows every side: " + side);

		// and power reaches the core through any of them
		helper.assertTrue(back.getMaxPower() == MixingVatTE.maxPower && left.transferPower(2500) == 0 && vat.power == 2500, "power through a port");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void placingBuildsTwoByThreeByTwoAndThreePorts(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos core = new BlockPos(8, 1, 6);
		MixingVatTE vat = place(helper, core, Direction.SOUTH);
		Block block = LeafiaBlocks.mixingvat.get();

		int parts = 0, extras = 0;
		for(int x = 0; x < 16; x++)
			for(int y = 0; y < 16; y++)
				for(int z = 0; z < 16; z++) {
					var state = helper.getBlockState(new BlockPos(x, y, z));
					if(state.getBlock() != block) continue;
					parts++;
					if(state.getValue(BlockDummyable.PART) == Part.EXTRA) extras++;
				}
		helper.assertTrue(parts == 12, "2 x 3 x 2 blocks, got " + parts);
		helper.assertTrue(extras == 3, "three ports, got " + extras);
		helper.assertTrue(helper.getBlockState(core).getValue(BlockDummyable.PART) == Part.CORE && helper.getBlockState(core).getValue(BlockDummyable.DIR) == Direction.NORTH,
				"the core stores the direction behind the machine");
		helper.assertTrue(vat.getFacingForward() == Direction.SOUTH && vat.getFacing() == Direction.NORTH, "and the front is where the player looked");

		// the far top corner (two ahead, one right, one up) belongs; one more in any direction does not
		helper.assertTrue(helper.getBlockState(core.offset(-1, 1, 2)).getBlock() == block, "the far top corner");
		for(BlockPos out : new BlockPos[] { core.offset(0, 2, 0), core.offset(1, 0, 0), core.offset(-2, 0, 0), core.offset(0, 0, 3), core.offset(0, 0, -1) })
			helper.assertTrue(helper.getBlockState(out).getBlock() != block, "nothing at " + out);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void thePortsAreUpstreamsFourBlocksAroundTheMachine(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(8, 1, 6);
		MixingVatTE vat = place(helper, at, Direction.SOUTH);
		BlockPos abs = helper.absolutePos(at);

		// facing south: behind is north and right is west. Power and salt come in behind both blocks of the back row.
		BlockPos[] in = vat.inputPorts();
		helper.assertTrue(in[0].equals(abs.north()) && in[1].equals(abs.north().west()), "in: " + in[0] + " " + in[1]);
		// the salt goes out beside the outer sides of the front row
		BlockPos[] out = vat.outputPorts();
		helper.assertTrue(out[0].equals(abs.south(2).east()) && out[1].equals(abs.south(2).west(2)), "out: " + out[0] + " " + out[1]);

		MixingVatTE east = place(helper, new BlockPos(3, 1, 12), Direction.EAST);
		BlockPos e = helper.absolutePos(new BlockPos(3, 1, 12));
		// facing east: behind is west, right is south
		helper.assertTrue(east.inputPorts()[0].equals(e.west()) && east.inputPorts()[1].equals(e.west().south())
				&& east.outputPorts()[0].equals(e.east(2).north()) && east.outputPorts()[1].equals(e.east(2).south(2)),
				"facing east");
		helper.succeed();
	}

	// ---- persistence -----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aReloadKeepsTheTanksAndTheProgressButNotThePower(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		vat.power = 4321;
		vat.progress = 17;
		vat.tankNc0.setFluid(salt(1500, 12.5, "u233", 0.75));
		vat.tankNc1.setFluid(salt(400, 0, "th232", 1.0));
		CompoundTag saved = vat.saveWithFullMetadata(helper.getLevel().registryAccess());

		MixingVatTE copy = core(helper, new BlockPos(1, 1, 1), Direction.SOUTH);
		copy.loadWithComponents(saved, helper.getLevel().registryAccess());

		helper.assertTrue(copy.nuclearMode && copy.inputTypeNc == LeafiaFluids.FLUORIDE && copy.progress == 17, "mode, fluid, progress");
		helper.assertTrue(copy.tankNc0.getFluidAmount() == 1500 && MSRTEBase.nbtProtocol(copy.tankNc0.getFluid()).getDouble("heat") == 12.5
				&& mixtureOf(copy.tankNc0.getFluid()).equals(Map.of("u233", 0.75)), "the input with its tag");
		helper.assertTrue(copy.tankNc1.getFluidAmount() == 400 && mixtureOf(copy.tankNc1.getFluid()).equals(Map.of("th232", 1.0)), "and the output");
		helper.assertTrue(copy.power == 0, "power is not saved, as upstream's: " + copy.power);
		helper.succeed();
	}

	// ---- the screen's button ---------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theVoidButtonIsValidated(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(vat.getBlockPos());
		player.setPos(at.x, at.y, at.z);
		vat.tankNc1.setFluid(salt(4000, 0, null, 0));

		player.containerMenu = player.inventoryMenu;
		vat.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 4000, "a player with no screen open voided it");

		player.containerMenu = new MixingVatMenu(1, player.getInventory(), vat);
		vat.onReceivePacketServer((byte) 1, true, player);
		vat.onReceivePacketServer((byte) 7, true, player);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 4000, "another key acted");

		player.setPos(at.x + 100, at.y, at.z);
		vat.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 4000, "a player a hundred blocks away voided it");

		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new MixingVatMenu(2, player.getInventory(), (MixingVatTE) null);
		vat.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 4000, "another vat's screen voided this one's");

		player.containerMenu = new MixingVatMenu(3, player.getInventory(), vat);
		vat.tankNc0.setFluid(salt(1000, 0, null, 0));
		vat.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(vat.tankNc1.getFluidAmount() == 0 && vat.tankNc0.getFluidAmount() == 1000, "the valid one empties the output, and only the output");
		helper.succeed();
	}

	// ---- the menu --------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theMenuHasUpstreamsSlots(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);
		ServerPlayer player = mockPlayer(helper);
		MixingVatMenu menu = new MixingVatMenu(1, player.getInventory(), vat);

		helper.assertTrue(menu.slots.size() == 13 + 36, "thirteen machine slots in nuclear mode and the player's 36: " + menu.slots.size());
		int[][] expected = { {186, 55}, {186, 86}, {186, 104}, {44, 17}, {44, 53},
				{71, 17}, {71, 35}, {89, 17}, {89, 35}, {107, 17}, {107, 35}, {7, 17}, {7, 53} };
		int[] slotIndex = { 0, 1, 2, 13, 14, 15, 18, 16, 19, 17, 20, 21, 22 };
		for(int i = 0; i < expected.length; i++) {
			var slot = menu.slots.get(i);
			helper.assertTrue(slot.x == expected[i][0] && slot.y == expected[i][1] && slot.getContainerSlot() == slotIndex[i],
					"slot " + i + " is container slot " + slot.getContainerSlot() + " at (" + slot.x + ", " + slot.y + ")");
		}
		helper.assertTrue(menu.slots.get(13).x == 8 && menu.slots.get(13).y == 121 && menu.slots.get(48).x == 8 + 8 * 18 && menu.slots.get(48).y == 179,
				"the inventory at 121 and the hotbar at 179");

		helper.assertTrue(!menu.slots.get(4).mayPlace(new ItemStack(Items.DIRT)) && !menu.slots.get(12).mayPlace(new ItemStack(Items.DIRT)),
				"the two output slots take nothing by hand");
		helper.assertTrue(menu.slots.get(3).mayPlace(new ItemStack(Items.DIRT)) && menu.slots.get(5).mayPlace(new ItemStack(Items.DIRT)), "the others take anything");

		vat.nuclearMode = false;
		MixingVatMenu slotless = new MixingVatMenu(2, player.getInventory(), vat);
		helper.assertTrue(slotless.slots.size() == 3 + 36, "the other mode has the first three only");
		helper.assertTrue(LeafiaMenuTypes.MIXING_VAT.get() == menu.getType(), "its type");
		helper.succeed();
	}

	/**
	 * Upstream counted the machine as 21 slots in {@code LeafiaItemTransfer} and built a menu of 13, so a shift-click
	 * never reached an ingredient slot. The count is the menu's own now (D-3076): a fuel nugget goes to the ingredients,
	 * anything else to the battery and upgrade slots, and nothing is shuffled inside the inventory.
	 */
	@GameTest(template = EMPTY)
	public static void theShiftClickReachesTheIngredients(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);
		ServerPlayer player = mockPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		MixingVatMenu menu = new MixingVatMenu(1, inventory, vat);

		// a machine slot goes to the inventory from the far end
		vat.slots[13] = new ItemStack(Items.COBBLESTONE, 10);
		menu.quickMoveStack(player, 3);
		helper.assertTrue(vat.slots[13].isEmpty() && inventory.getItem(8).is(Items.COBBLESTONE) && inventory.getItem(8).getCount() == 10, "from the machine, to the far end");
		inventory.clearContent();

		// a click in the first inventory slot is an inventory click now: cobblestone goes to the battery slot, not within the inventory
		inventory.setItem(9, new ItemStack(Items.COBBLESTONE, 10));
		menu.quickMoveStack(player, 13);
		helper.assertTrue(inventory.getItem(9).isEmpty() && vat.slots[0].is(Items.COBBLESTONE), "an inventory click goes to the machine's first slot: " + vat.slots[0]);
		vat.slots[0] = ItemStack.EMPTY;
		inventory.clearContent();

		// a stack that is not a fuel nugget goes to slot 0 first, which takes anything
		inventory.setItem(20, new ItemStack(Items.COBBLESTONE, 10));
		menu.quickMoveStack(player, 13 + 11);
		helper.assertTrue(vat.slots[0].is(Items.COBBLESTONE) && vat.slots[0].getCount() == 10, "to slot 0");
		for(int i = 3; i < MixingVatTE.SLOTS; i++)
			helper.assertTrue(vat.slots[i].isEmpty(), "and none to container slot " + i);

		// with slots 0 to 2 full of something else, nothing moves at all
		inventory.clearContent();
		vat.slots[0] = new ItemStack(Items.STONE);
		vat.slots[1] = new ItemStack(Items.STONE);
		vat.slots[2] = new ItemStack(Items.STONE);
		inventory.setItem(30, new ItemStack(Items.COBBLESTONE, 10));
		menu.quickMoveStack(player, 13 + 21);
		helper.assertTrue(inventory.getItem(30).getCount() == 10 && inventory.getItem(9).isEmpty(), "nothing is shuffled within the inventory");

		// a fuel nugget goes to the ingredient slots first (tile slots 15 to 20)
		vat.slots[0] = ItemStack.EMPTY;
		vat.slots[1] = ItemStack.EMPTY;
		vat.slots[2] = ItemStack.EMPTY;
		inventory.clearContent();
		inventory.setItem(20, new ItemStack(ModItems.nugget_th232.get(), 4));
		menu.quickMoveStack(player, 13 + 11);
		boolean landed = false;
		for(int i = 15; i <= 20; i++) landed |= !vat.slots[i].isEmpty();
		helper.assertTrue(landed && vat.slots[0].isEmpty(), "a fuel nugget lands in an ingredient slot, not the battery slot (D-3076)");
		helper.succeed();
	}

	// ---- what the client draws, here so a server can say ------------------------------------

	@GameTest(template = EMPTY)
	public static void theDrawnSaltIsTheFullerTankTintedByItsFluid(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = core(helper);

		helper.assertTrue(vat.fillLevel() == 0F, "empty");
		vat.tankNc0.setFluid(salt(1000, 0, null, 0));
		vat.tankNc1.setFluid(salt(3000, 0, null, 0));
		helper.assertTrue(vat.fillLevel() == 0.75F, "the greater of the two: " + vat.fillLevel());
		float[] tint = vat.tint();
		int color = LeafiaFluids.FLUORIDE.getColor();
		helper.assertTrue(tint[0] == (color >> 16 & 0xFF) / 255F && tint[1] == (color >> 8 & 0xFF) / 255F && tint[2] == (color & 0xFF) / 255F
				&& color == 0xd3d8b9, "FLiBe's pale colour");

		vat.nuclearMode = false;
		float[] white = vat.tint();
		helper.assertTrue(vat.fillLevel() == 1F && white[0] == 1F && white[1] == 1F && white[2] == 1F, "the other mode draws a full, white tank");
		vat.nuclearMode = true;

		helper.assertTrue(vat.tooltipTank(true) == vat.tankNc0 && vat.tooltipTank(false) == vat.tankNc1,
				"each tank tooltip reads its own tank (D-3076; upstream's read the output twice)");
		helper.succeed();
	}

	// ---- the switch ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theSwitchDecidesWhatRegisters(GameTestHelper helper) {
		helper.assertTrue((LeafiaBlocks.mixingvat != null) == on(), "the block exists exactly when the machine is on");
		helper.assertTrue((LeafiaBlockEntities.MIXING_VAT != null) == on() && (LeafiaBlockEntities.MIXING_VAT_DUMMY != null) == on(), "and so do its block entities");
		helper.assertTrue(on() == (LeafiaFeatures.mixingVat && LeafiaFeatures.addonFluids), "the gate is the switch and the fluids");
		if(on()) helper.assertTrue(LeafiaFeatures.ffBridge(), "the machine keeps Forge tanks, so it brings the bridge");
		helper.succeed();
	}

	// ---- a real tick -----------------------------------------------------------------------

	/** A placed vat, on real ticks, finishes a batch and uses its fuel. */
	@GameTest(template = BIG, timeoutTicks = TickWindow.TIMEOUT + 100)
	public static void aPlacedVatMixesABatchOnRealTicks(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		MixingVatTE vat = place(helper, new BlockPos(8, 1, 6), Direction.SOUTH);

		vat.tankNc0.setFluid(salt(2000, 250, "u233", 0.5));
		vat.slots[15] = new ItemStack(ModItems.nugget_th232.get(), 2);
		vat.power = 50_000;

		TickWindow.sequence(helper).thenWaitUntil(() -> {
			helper.assertTrue(vat.tankNc1.getFluidAmount() == 1000, "waiting for the batch: " + vat.progress + " of " + MixingVatTE.maxProgress);
		}).thenExecute(() -> {
			helper.assertTrue(vat.tankNc0.getFluidAmount() == 1000, "a bucket left the input");
			helper.assertTrue(vat.slots[15].getCount() == 1, "one nugget was used: " + vat.slots[15].getCount());
			helper.assertTrue(mixtureOf(vat.tankNc1.getFluid()).equals(Map.of("u233", 0.5, "th232", 1.0)), "the mixture");
			helper.assertTrue(vat.power <= 50_000 - 3000 && (50_000 - vat.power) % 50 == 0, "50 HE a tick of 60: " + vat.power);
		}).thenSucceed();
	}
}
