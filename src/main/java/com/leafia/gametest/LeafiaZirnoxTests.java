package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.machine.BlockEntityReactorZirnox;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.projectile.EntityZirnoxDebris.DebrisType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemZirnoxRod;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE;
import com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE.PacketKeys;
import com.leafia.contents.machines.reactors.zirnox.ZirnoxMenu;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The ZIRNOX overhaul (D-3020): the switch, upstream's arithmetic, the control rods, compression, the
 * failure timer, the meltdown's two upstream peculiarities, the screen's packet checks and the save.
 *
 * <p>Every test that switches {@code zirnoxOverhaul} on does it around the one {@code setBlock} that makes the
 * core and restores what it found. A tick is one {@code updateEntity()} inside the body
 * (D-953), and every reactor is removed before the body ends, because a core left standing keeps ticking.
 * The meltdown itself is never run: it converts terrain out to 35 blocks (D-965). Expected numbers were worked
 * by hand from upstream's expressions and are written as literals where the arithmetic is short.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaZirnoxTests {

	private static final String EMPTY = "empty";
	private static final BlockPos CORE = new BlockPos(2, 1, 2);

	// ---- the rig ---------------------------------------------------------------------------

	private static LeafiaZirnoxTE overhauled(GameTestHelper helper) {
		boolean was = LeafiaFeatures.zirnoxOverhaul;
		LeafiaFeatures.zirnoxOverhaul = true;
		try {
			helper.setBlock(CORE, ModBlocks.reactor_zirnox.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
					.setValue(BlockDummyable.DIR, Direction.NORTH));
		} finally {
			LeafiaFeatures.zirnoxOverhaul = was;
		}
		BlockEntity be = helper.getBlockEntity(CORE);
		helper.assertTrue(be instanceof LeafiaZirnoxTE, "the overhaul did not put the addon reactor in the base block: " + be);
		return (LeafiaZirnoxTE) be;
	}

	private static void clear(GameTestHelper helper) {
		helper.setBlock(CORE, Blocks.AIR);
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static boolean near(double a, double b, double eps) {
		return Math.abs(a - b) <= eps;
	}

	private static LeafiaRodItem rod() {
		LeafiaRodItem rod = LeafiaRods.get("leafRodU38");
		if(rod == null) throw new IllegalStateException("features.rods is off: no addon rod to test with");
		return rod;
	}

	// ---- the switch ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOffTheBaseReactorIsTheBaseMods(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.zirnoxOverhaul, "the overhaul must default off");
		helper.setBlock(CORE, ModBlocks.reactor_zirnox.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		BlockEntity be = helper.getBlockEntity(CORE);
		helper.assertTrue(be != null && be.getClass() == BlockEntityReactorZirnox.class, "expected the base mod's own reactor, got " + be);
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOnTheReactorIsTheAddonsWithUpstreamsTanksAndMenu(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		helper.assertTrue(!LeafiaFeatures.zirnoxOverhaul, "the switch was not restored");
		helper.assertTrue(z.getType() == LeafiaBlockEntities.ZIRNOX.get(), "the reactor is not on the addon's type");
		helper.assertTrue(z.getBlockState().is(ModBlocks.reactor_zirnox.get()), "the block must stay the base mod's");
		helper.assertTrue(z.steam.getTankType() == Fluids.STEAM && z.steam.getMaxFill() == 8000, "steam is plain steam, 8,000 mB");
		helper.assertTrue(z.carbonDioxide.getMaxFill() == 16000 && z.water.getMaxFill() == 32000, "CO2 16,000 and water 32,000, the base reactor's");
		helper.assertTrue(z.hulltemp == 20 && z.meltingPoint == 800 && z.maxPressureLCE == 30 && z.generosityTimer == 100 && z.stressTimer == 300,
				"hull 20, melting 800, 30 atm, 100 ticks of grace, a 300 stress timer");
		helper.assertTrue(LeafiaZirnoxTE.HULL_FAILURE == 1300, "the hull fails at 1,300");

		ServerPlayer player = mockPlayer(helper);
		AbstractContainerMenu menu = z.createMenu(0, player.getInventory(), player);
		helper.assertTrue(menu instanceof ZirnoxMenu, "the reactor's menu is the addon's: " + menu);
		helper.assertTrue(menu.slots.size() == 28 + 36, "28 machine slots and the player's 36, got " + menu.slots.size());
		// upstream's arithmetic: two slot-widths apart, rows of four set back half a step
		helper.assertTrue(menu.slots.get(0).x == 67 && menu.slots.get(0).y == 20, "channel 0 at (67, 20)");
		helper.assertTrue(menu.slots.get(2).x == 131 && menu.slots.get(2).y == 20, "channel 2 at (131, 20)");
		helper.assertTrue(menu.slots.get(3).x == 51 && menu.slots.get(3).y == 36, "channel 3 at (51, 36)");
		helper.assertTrue(menu.slots.get(6).x == 147 && menu.slots.get(6).y == 36, "channel 6 at (147, 36)");
		helper.assertTrue(menu.slots.get(23).x == 131 && menu.slots.get(23).y == 116, "channel 23 at (131, 116)");
		helper.assertTrue(menu.slots.get(24).x == 6 && menu.slots.get(24).y == 81 && menu.slots.get(27).x == 193 && menu.slots.get(27).y == 99,
				"the canister slots at (6, 81) and (193, 99)");
		helper.assertTrue(!menu.slots.get(25).mayPlace(new ItemStack(Items.DIRT)), "slot 25 is take-only");
		helper.assertTrue(menu.slots.get(28).x == 27 && menu.slots.get(28).y == 161, "the inventory from (27, 161)");
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void addonRodsGoIntoTheChannelsAndNowhereElse(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		ItemStack rod = new ItemStack(rod());
		helper.assertTrue(z.canPlaceItem(0, rod) && z.canPlaceItem(23, rod), "an addon rod goes into channel 0 and 23");
		helper.assertTrue(!z.canPlaceItem(24, rod), "but not into the canister slots");
		helper.assertTrue(z.canPlaceItem(5, new ItemStack(ModItems.rod_zirnox_natural_uranium_fuel.get())), "a base rod still goes in");
		helper.assertTrue(!z.canPlaceItem(5, new ItemStack(Items.DIRT)), "dirt does not");
		clear(helper);
		helper.succeed();
	}

	// ---- the arithmetic -------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void thePressureIsTwiceTheCO2PlusItsShareOfTheHull(GameTestHelper helper) {
		helper.assertTrue(LeafiaZirnoxTE.pressureOf(16000, 16000, 20) == 32000 / 3333.0, "a full cold tank is 32,000 / 3,333");
		helper.assertTrue(LeafiaZirnoxTE.pressureOf(8000, 16000, 420) == 41000 / 3333.0, "half full at 420 degrees: (16,000 + 400 x 125 x 0.5) / 3,333");
		helper.assertTrue(LeafiaZirnoxTE.pressureOf(0, 16000, 1000) == 0, "no CO2, no pressure, however hot");

		// in a tick: the pressure is read before the hull moves, so it is exactly the formula on the old hull
		LeafiaZirnoxTE z = overhauled(helper);
		z.carbonDioxide.setFill(8000);
		z.hulltemp = 420;
		z.updateEntity();
		helper.assertTrue(z.pressureLCE == 41000 / 3333.0, "one tick at 420 degrees and half a tank, got " + z.pressureLCE);
		helper.assertTrue(z.lastPressureLCE == 0, "and the last pressure is the one before, got " + z.lastPressureLCE);
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theVentAndTheGroanFollowThePressure(GameTestHelper helper) {
		helper.assertTrue(LeafiaZirnoxTE.ventDrain(5) == 10 && LeafiaZirnoxTE.ventDrain(0) == 10, "ten mB at or below 5 atm");
		helper.assertTrue(LeafiaZirnoxTE.ventDrain(20) == 123, "(15^3 / 30 + 10) rounded up is 123 at 20 atm, got " + LeafiaZirnoxTE.ventDrain(20));
		helper.assertTrue(LeafiaZirnoxTE.ventDrain(35) == 910, "910 at 35 atm, got " + LeafiaZirnoxTE.ventDrain(35));
		helper.assertTrue(LeafiaZirnoxTE.stressStep(22) == 0 && LeafiaZirnoxTE.stressStep(10) == 0, "no stress at or below 22 atm");
		helper.assertTrue(LeafiaZirnoxTE.stressStep(28) == 64, "64 a tick at 28 atm, got " + LeafiaZirnoxTE.stressStep(28));
		helper.assertTrue(LeafiaZirnoxTE.stressVolume(16) == 0.25F && LeafiaZirnoxTE.stressVolume(30) == 4F && LeafiaZirnoxTE.stressVolume(23) == 0.484375F,
				"the groan: 0.25 at 16 atm, 4 at 30, 0.484375 at 23");

		// open vent, a full cold tank: 9.6 atm lets 14 mB out
		LeafiaZirnoxTE z = overhauled(helper);
		z.carbonDioxide.setFill(16000);
		z.valveOpen = true;
		z.updateEntity();
		helper.assertTrue(z.carbonDioxide.getFill() == 16000 - 14, "the vent at 9.6 atm takes 14 mB, left " + z.carbonDioxide.getFill());
		z.valveOpen = false;
		z.updateEntity();
		helper.assertTrue(z.carbonDioxide.getFill() == 16000 - 14, "shut, it takes nothing");
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHullBoilsWaterIntoSteamAboveTheSteamsBoilingPoint(GameTestHelper helper) {
		helper.assertTrue(LeafiaZirnoxTE.boilBase(100, 100, 1) == 0 && LeafiaZirnoxTE.boilBase(50, 100, 1) == 0, "nothing at or below the boiling point");
		helper.assertTrue(near(LeafiaZirnoxTE.boilBase(116, 100, 1), 2.0 / 3, 1e-12), "16 degrees over at full feed: 16^0.25 / 3 = 2/3");
		helper.assertTrue(near(LeafiaZirnoxTE.boilBase(116, 100, 0.5), 1.0 / 3, 1e-12), "half the feed, half the boil");

		// a dry-cooled 420-degree hull with no rods: the hull first falls 400^0.25 toward 20, then boils 33 mB
		LeafiaZirnoxTE z = overhauled(helper);
		z.water.setFill(32000);
		z.hulltemp = 420;
		z.updateEntity();
		helper.assertTrue(z.steam.getFill() == 33, "33 mB of steam, got " + z.steam.getFill());
		helper.assertTrue(z.water.getFill() == 32000, "at 1x compression 33 / 100 rounds to no water, left " + z.water.getFill());
		helper.assertTrue(near(z.hulltemp, 414.122987, 1e-5), "the hull ends near 414.123, got " + z.hulltemp);
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void compressionRetypesTheSteamAndScalesWhatIsInIt(GameTestHelper helper) {
		helper.assertTrue(LeafiaZirnoxTE.compressionLevel(Fluids.STEAM) == 1 && LeafiaZirnoxTE.compressionLevel(Fluids.HOTSTEAM) == 10
				&& LeafiaZirnoxTE.compressionLevel(Fluids.SUPERHOTSTEAM) == 100 && LeafiaZirnoxTE.compressionLevel(Fluids.ULTRAHOTSTEAM) == 1000,
				"1, 10, 100, 1000");
		helper.assertTrue(LeafiaZirnoxTE.waterCost(250, 0) == 2 && LeafiaZirnoxTE.waterCost(250, 1) == 25
				&& LeafiaZirnoxTE.waterCost(250, 2) == 250 && LeafiaZirnoxTE.waterCost(250, 3) == 2500, "water a hundredth, a tenth, one and ten times");

		LeafiaZirnoxTE z = overhauled(helper);
		z.steam.setFill(1000);
		z.compression = 1;
		z.updateCompression();
		helper.assertTrue(z.steam.getTankType() == Fluids.HOTSTEAM && z.steam.getFill() == 100, "1,000 steam is 100 dense steam, got " + z.steam.getFill());
		z.compression = 3;
		z.updateCompression();
		helper.assertTrue(z.steam.getTankType() == Fluids.ULTRAHOTSTEAM && z.steam.getFill() == 1, "and 1 ultra dense, got " + z.steam.getFill());
		z.compression = 0;
		z.updateCompression();
		helper.assertTrue(z.steam.getTankType() == Fluids.STEAM && z.steam.getFill() == 1000, "and back to 1,000, got " + z.steam.getFill());
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlRodsMoveOneStepEveryOtherTickAndGateTheBaseRods(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		ItemStack baseRod = new ItemStack(ModItems.rod_zirnox_natural_uranium_fuel.get());
		z.setItem(8, baseRod);
		z.setItem(9, new ItemStack(ModItems.meteorite_sword_bred.get()));
		z.rodsTarget = 100;
		for(int i = 0; i < 10; i++) z.updateEntity();
		helper.assertTrue(z.rods == 5, "ten ticks move the rods five steps, got " + z.rods);
		helper.assertTrue(ItemZirnoxRod.getLifeTime(z.getItem(8)) == 0, "a base rod does not burn until the rods are fully out");
		helper.assertTrue(z.getItem(9).getItem() == ModItems.meteorite_sword_bred.get(), "nor does the sword cook");

		z.rods = 100;
		z.updateEntity();
		helper.assertTrue(ItemZirnoxRod.getLifeTime(z.getItem(8)) == 1, "fully out, a lone base rod advances once, got " + ItemZirnoxRod.getLifeTime(z.getItem(8)));
		helper.assertTrue(z.getItem(9).getItem() == ModItems.meteorite_sword_irradiated.get(), "and the sword comes out irradiated");
		helper.assertTrue(z.heat == 0, "the base heat is moved into the hull and zeroed every tick");
		clear(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void fiveSecondsAtThirtyAtmospheresFailTheReactor(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		z.pressureLCE = 31;
		z.lastPressureLCE = 31;
		for(int i = 0; i < 99; i++) helper.assertTrue(!z.pressureFailing(), "failed early, at tick " + (i + 1));
		helper.assertTrue(z.pressureFailing(), "the hundredth tick at 31 atm fails it");

		z.generosityTimer = 50;
		z.pressureLCE = 30.5;
		z.lastPressureLCE = 31;
		helper.assertTrue(!z.pressureFailing() && z.generosityTimer == 100, "a drop of half an atmosphere resets the timer to 100");
		z.generosityTimer = 50;
		z.pressureLCE = 29;
		z.lastPressureLCE = 29;
		helper.assertTrue(!z.pressureFailing() && z.generosityTimer == 100, "and so does any pressure under 30");
		clear(helper);
		helper.succeed();
	}

	// ---- upstream's walk and meltdown ------------------------------------------------------

	private static int rowOf(int slot) {
		int[] ends = { 2, 6, 9, 13, 16, 20, 23 };
		int row = 0;
		while(slot > ends[row]) row++;
		return row;
	}

	private static List<String> walk(int slot) {
		List<String> out = new ArrayList<>();
		LeafiaZirnoxTE.forEachNeighbour(slot, (s, d) -> out.add(s + "/" + (int) d));
		return out;
	}

	@GameTest(template = EMPTY)
	public static void theNeighbourWalkStaysInItsRowBounds(GameTestHelper helper) {
		// slot 0: down its column at 1, 2, 4; along its row at 1, 2; the two diagonals that exist, at 2
		helper.assertTrue(walk(0).equals(List.of("7/1", "14/2", "21/4", "1/1", "2/2", "4/2", "3/2")), "slot 0: " + walk(0));
		// slot 10 starts the fourth row (10..13): the row is its own bound (D-3076; upstream's absolute 3..7 made it read back
		// along 9, 8, 7 ... 3 and meet 3 and 7 twice), and it has no diagonal on its left
		helper.assertTrue(walk(10).equals(List.of("3/1", "17/1", "11/1", "12/2", "13/4", "7/2", "14/2")),
				"slot 10: " + walk(10));
		// slot 4: the diagonal to slot 0 is guarded by ">= 0" now (upstream's "> 0" never read it)
		helper.assertTrue(walk(4).contains("0/2"), "slot 4 reads slot 0 diagonally: " + walk(4));
		// every channel's neighbours are in its own row, in the rows beside it, or in its column
		for(int slot = 0; slot < 24; slot++) {
			final int s = slot;
			LeafiaZirnoxTE.forEachNeighbour(slot, (n, d) -> {
				int rowS = rowOf(s), rowN = rowOf(n);
				helper.assertTrue(rowS == rowN || Math.abs(rowS - rowN) == 1 || (n - s) % 7 == 0, "slot " + s + " reads " + n + " out of its reach");
			});
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theMeltdownClearsTheFiveByFiveAboveAndThrows152Pieces(GameTestHelper helper) {
		BlockPos o = BlockPos.ZERO;
		List<BlockPos> cells = LeafiaZirnoxTE.upstreamClearedCells(o);
		List<BlockPos> expected = new ArrayList<>();
		for(int ox = -2; ox <= 2; ox++) for(int oz = -2; oz <= 2; oz++) for(int oy = 2; oy <= 5; oy++) expected.add(o.offset(ox, oy, oz));
		helper.assertTrue(cells.equals(expected), "the 5x5 from two to five up (D-3075): " + cells);

		List<DebrisType> plan = LeafiaZirnoxTE.debrisPlan();
		int[] n = new int[DebrisType.values().length];
		for(DebrisType t : plan) n[t.ordinal()]++;
		helper.assertTrue(plan.size() == 152, "152 pieces, got " + plan.size());
		helper.assertTrue(n[DebrisType.EXCHANGER.ordinal()] == 2 && n[DebrisType.CONCRETE.ordinal()] == 50 && n[DebrisType.BLANK.ordinal()] == 50
				&& n[DebrisType.SHRAPNEL.ordinal()] == 30 && n[DebrisType.ELEMENT.ordinal()] == 10 && n[DebrisType.GRAPHITE.ordinal()] == 10,
				"2 exchangers, 50 concrete, 50 blank, 30 shrapnel, 10 elements, 10 graphite");
		helper.assertTrue(plan.subList(0, 4).equals(List.of(DebrisType.EXCHANGER, DebrisType.EXCHANGER, DebrisType.CONCRETE, DebrisType.BLANK)),
				"in upstream's order");
		helper.succeed();
	}

	// ---- the screen's packets --------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theServerTakesOnlyItsOwnScreensControls(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		ServerPlayer player = mockPlayer(helper);
		try {
			BlockPos at = helper.absolutePos(CORE);
			player.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);

			z.onReceivePacketServer(PacketKeys.CONTROL_RODS.key, (byte) 50, player);
			helper.assertTrue(z.rodsTarget == 0, "without the screen open nothing moves");

			player.containerMenu = z.createMenu(1, player.getInventory(), player);
			z.onReceivePacketServer(PacketKeys.CONTROL_RODS.key, (byte) 50, player);
			helper.assertTrue(z.rodsTarget == 50, "with it, the rods' target moves, got " + z.rodsTarget);
			z.onReceivePacketServer(PacketKeys.CONTROL_RODS.key, (byte) 101, player);
			z.onReceivePacketServer(PacketKeys.CONTROL_RODS.key, (byte) -1, player);
			helper.assertTrue(z.rodsTarget == 50, "101 and -1 are refused");
			z.onReceivePacketServer(PacketKeys.COMPRESSION.key, (byte) 4, player);
			helper.assertTrue(z.compression == 0, "compression 4 is refused");
			z.onReceivePacketServer(PacketKeys.COMPRESSION.key, (byte) 2, player);
			helper.assertTrue(z.compression == 2 && z.steam.getTankType() == Fluids.SUPERHOTSTEAM, "compression 2 makes super dense steam");
			z.onReceivePacketServer(PacketKeys.OPENVALVE.key, true, player);
			helper.assertTrue(z.valveOpen, "the vent opens");

			player.setPos(at.getX() + 40.5, at.getY(), at.getZ());
			z.onReceivePacketServer(PacketKeys.OPENVALVE.key, false, player);
			helper.assertTrue(z.valveOpen, "and a player 40 blocks off cannot shut it");
		} finally {
			player.containerMenu = player.inventoryMenu;
		}
		clear(helper);
		helper.succeed();
	}

	// ---- persistence and the readouts -------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theSaveKeepsUpstreamsKeysAndTheReadoutsReadTheHull(GameTestHelper helper) {
		LeafiaZirnoxTE z = overhauled(helper);
		z.hulltemp = 512.25;
		z.pressureLCE = 17.5;
		z.rods = 40;
		z.rodsTarget = 60;
		z.compression = 2;
		CompoundTag tag = z.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(tag.getTagType("pressure") == Tag.TAG_DOUBLE && tag.getDouble("pressure") == 17.5,
				"\"pressure\" is upstream's double, over the base reactor's int");
		helper.assertTrue(tag.getDouble("hulltemp") == 512.25 && tag.getByte("rods") == 40 && tag.getByte("rodsD") == 60 && tag.getByte("compression") == 2,
				"hulltemp, rods, rodsD and compression under upstream's keys");

		LeafiaZirnoxTE copy = new LeafiaZirnoxTE(LeafiaBlockEntities.ZIRNOX.get(), z.getBlockPos(), z.getBlockState());
		copy.loadWithComponents(tag, helper.getLevel().registryAccess());
		helper.assertTrue(copy.hulltemp == 512.25 && copy.pressureLCE == 17.5 && copy.rods == 40 && copy.rodsTarget == 60 && copy.compression == 2,
				"and they load back");

		helper.assertTrue(z.getTemp() == 512 && z.getPressure() == 18, "getTemp and getPressure read the hull and the CO2 pressure, rounded");
		helper.assertTrue(z.setControl(150) == 100 && z.rodsTarget == 100 && z.setControl(-5) == 0, "setControl clamps to 0-100");
		helper.assertTrue(z.methods().length == 8 && z.getInfo().length == 6, "upstream's eight callbacks, six values in getInfo");
		clear(helper);
		helper.succeed();
	}
}
