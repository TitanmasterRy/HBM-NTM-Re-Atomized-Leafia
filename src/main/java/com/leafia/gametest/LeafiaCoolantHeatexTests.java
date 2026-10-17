package com.leafia.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.ModItems;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexBlock;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexTE;
import com.leafia.contents.machines.misc.heatex.container.CoolantHeatexMenu;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.dev.LeafiaUtil;
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
 * The coolant heat exchanger: what each side converts and at what rate, the Forge handler, the six ports, the
 * three controls and the menu.
 *
 * <p>Every number is upstream's, read through the machine's own lookups ({@code getHeatable}, {@code getCoolable},
 * {@code getHeatRequired}) and a real conversion; a few run in the world on a real tick ({@link TickWindow}).
 * Four things about upstream are pinned because they surprise: the default coolant cannot boil on this port's
 * fluid table, a partly cooled tank of salt loses its whole heat, the heat counter is not saved, and the
 * shift-click chain counts the machine as three slots.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCoolantHeatexTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	private static boolean on() {
		return LeafiaFeatures.coolantHeatexMachine();
	}

	// ---- the rig ---------------------------------------------------------------------------

	/** A bare core, facing south, with nothing around it. */
	private static CoolantHeatexTE core(GameTestHelper helper, BlockPos pos, Direction dir) {
		helper.setBlock(pos, LeafiaBlocks.coolant_heatex.get().defaultBlockState()
				.setValue(BlockDummyable.PART, Part.CORE)
				.setValue(BlockDummyable.DIR, dir));
		return (CoolantHeatexTE) helper.getBlockEntity(pos);
	}

	private static CoolantHeatexTE core(GameTestHelper helper) {
		return core(helper, new BlockPos(2, 1, 2), Direction.SOUTH);
	}

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

	/** Places the machine as a player does: the block in the clicked cell, rebuilt around a core one step on where the player looks. */
	private static CoolantHeatexTE place(GameTestHelper helper, BlockPos core, Direction looking) {
		Block block = LeafiaBlocks.coolant_heatex.get();
		ServerPlayer player = mockPlayer(helper);
		player.setYRot(yawFor(looking));
		player.setXRot(0F);
		BlockPos at = core.relative(looking.getOpposite());
		helper.setBlock(at, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), block.defaultBlockState(), player, new ItemStack(block));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof CoolantHeatexTE, "the machine was not placed at " + core + " looking " + looking);
		return (CoolantHeatexTE) be;
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

	private static double heatOf(FluidStack stack) {
		return MSRTEBase.nbtProtocol(stack).getDouble("heat");
	}

	// ---- the constants and the lookups -----------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theConstantsAreUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		helper.assertTrue(CoolantHeatexTE.powerConsumption == 5 && CoolantHeatexTE.maxPower == 10_000, "5 HE a tick of 10,000");
		helper.assertTrue(CoolantHeatexTE.capacity == 2_400_000, "tanks of 2,400,000 mB");
		helper.assertTrue(heatex.ffInputA.getCapacity() == 2_400_000 && heatex.ffOutputA.getCapacity() == 2_400_000, "the Forge tanks");
		helper.assertTrue(heatex.ntmfInputA.getMaxFill() == 2_400_000 && heatex.ntmfOutputB.getMaxFill() == 2_400_000, "the ordinary tanks");
		helper.assertTrue(heatex.amountToCool == 24_000 && heatex.tickDelay == 1 && heatex.coolantMode == 2 && !heatex.ntmfMode, "the defaults");
		helper.assertTrue(heatex.inputFilter == LeafiaFluids.FLUORIDE && heatex.outputFilter == LeafiaFluids.FLUORIDE, "FLiBe in, FLiBe out");
		helper.assertTrue(CoolantHeatexTE.coolant(0) == Fluids.AIR && CoolantHeatexTE.coolant(1) == Fluids.WATER
				&& CoolantHeatexTE.coolant(2) == Fluids.COOLANT, "the cold side's coolants");
		helper.assertTrue(CoolantHeatexTE.hotCoolant(0) == LeafiaFluids.HOT_AIR && CoolantHeatexTE.hotCoolant(1) == LeafiaFluids.HOT_WATER
				&& CoolantHeatexTE.hotCoolant(2) == Fluids.COOLANT_HOT, "and what they boil to");
		helper.assertTrue(heatex.ntmfInputB.getTankType() == Fluids.COOLANT && heatex.ntmfOutputB.getTankType() == Fluids.COOLANT_HOT, "the default cold tanks");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void airAndWaterBoilOnUpstreamsVirtualSteps(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		HeatingStep air = CoolantHeatexTE.getHeatable(Fluids.AIR).getFirstStep();
		helper.assertTrue(air.amountReq == 2 && air.heatReq == 50 && air.amountProduced == 3 && air.typeProduced == LeafiaFluids.HOT_AIR,
				"2 air and 50 TU make 3 warm air");
		HeatingStep water = CoolantHeatexTE.getHeatable(Fluids.WATER).getFirstStep();
		helper.assertTrue(water.amountReq == 1 && water.heatReq == 70 && water.amountProduced == 1 && water.typeProduced == LeafiaFluids.HOT_WATER,
				"1 water and 70 TU make 1 warm water");
		helper.assertTrue(CoolantHeatexTE.getHeatable(Fluids.AIR).getEfficiency(HeatingType.BOILER) == 1.0
				&& CoolantHeatexTE.getHeatable(Fluids.WATER).getEfficiency(HeatingType.BOILER) == 1.0, "both at full boiler efficiency");

		heatex.coolantMode = 0;
		helper.assertTrue(heatex.getHeatRequired() == 50 / 3.0 * 2, "air: heat / produced x required, " + heatex.getHeatRequired());
		heatex.coolantMode = 1;
		helper.assertTrue(heatex.getHeatRequired() == 70.0, "water: " + heatex.getHeatRequired());
		heatex.coolantMode = 2;
		HeatingStep coolant = Fluids.COOLANT.getTrait(FT_Heatable.class).getFirstStep();
		helper.assertTrue(heatex.getHeatRequired() == coolant.heatReq / (double) coolant.amountProduced * coolant.amountReq
				&& coolant.heatReq == 300, "coolant: the base table's own step, 300 TU");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void spentSteamIsTheOnlyVirtualCoolable(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		FT_Coolable lps = CoolantHeatexTE.getCoolable(Fluids.SPENTSTEAM);
		helper.assertTrue(lps != null && lps.coolsTo == Fluids.WATER && lps.amountReq == 1 && lps.amountProduced == 1 && lps.heatEnergy == 20
				&& lps.getEfficiency(CoolingType.HEATEXCHANGER) == 1.0, "spent steam: 1 mB to 1 mB of water, 20 heat");

		FT_Coolable hot = CoolantHeatexTE.getCoolable(Fluids.COOLANT_HOT);
		helper.assertTrue(hot != null && hot.coolsTo == Fluids.COOLANT && hot.heatEnergy == 300, "hot coolant is the base table's own, 300");
		helper.assertTrue(CoolantHeatexTE.getCoolable(Fluids.DIESEL) == null, "diesel has no heat-exchanger cooling");

		helper.assertTrue(heatex.isValidFluid(Fluids.SPENTSTEAM) && heatex.isValidFluid(Fluids.COOLANT_HOT)
				&& heatex.isValidFluid(LeafiaFluids.FLUORIDE), "a coolable fluid and the salt are valid");
		helper.assertTrue(!heatex.isValidFluid(Fluids.DIESEL) && !heatex.isValidFluid(Fluids.WATER), "diesel and water are not");
		helper.succeed();
	}

	/**
	 * <b>The default coolant does not boil here.</b> Upstream's coolant mode 2 is the base mod's COOLANT, and this
	 * port's fluid table (D-951 verified it line for line) gives it heat-exchanger, PWR and ICF efficiencies and no
	 * boiler one, so the cold side's default mode converts nothing and the heat counter climbs until it halts the hot
	 * side. Air and water, the other two modes, work. If this fails because COOLANT gained a boiler efficiency, the
	 * default mode has come alive and D-3016 is stale.
	 */
	@GameTest(template = EMPTY)
	public static void theDefaultCoolantCannotBoilOnThisPortsTable(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		helper.assertTrue(Fluids.COOLANT.getTrait(FT_Heatable.class).getEfficiency(HeatingType.BOILER) == 0.0,
				"COOLANT has gained a boiler efficiency: the default mode works now, update D-3016");

		heatex.ntmfInputB.setFill(10_000);
		heatex.heatEnergy = 100_000;
		heatex.tryConvertB();
		helper.assertTrue(heatex.ntmfInputB.getFill() == 10_000 && heatex.ntmfOutputB.getFill() == 0 && heatex.heatEnergy == 100_000,
				"the default mode moved something: " + heatex.ntmfInputB.getFill() + "/" + heatex.ntmfOutputB.getFill());
		helper.succeed();
	}

	// ---- the cold side ---------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theColdSideBoilsWaterByTheHeatItHas(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.coolantMode = 1;
		heatex.updateCoolantMode();
		helper.assertTrue(heatex.ntmfInputB.getTankType() == Fluids.WATER && heatex.ntmfOutputB.getTankType() == LeafiaFluids.HOT_WATER, "retyped");

		heatex.ntmfInputB.setFill(1000);
		heatex.heatEnergy = 700;
		heatex.tryConvertB();
		helper.assertTrue(heatex.ntmfInputB.getFill() == 990 && heatex.ntmfOutputB.getFill() == 10 && heatex.heatEnergy == 0,
				"700 TU boil 10 mB: " + heatex.ntmfInputB.getFill() + "/" + heatex.ntmfOutputB.getFill() + "/" + heatex.heatEnergy);

		// short of heat, the remainder stays
		heatex.heatEnergy = 169;
		heatex.tryConvertB();
		helper.assertTrue(heatex.ntmfOutputB.getFill() == 12 && heatex.heatEnergy == 29, "169 TU boil 2 mB and keep 29: " + heatex.heatEnergy);

		// and a full output stops it
		heatex.ntmfOutputB.setFill(heatex.ntmfOutputB.getMaxFill());
		heatex.heatEnergy = 7000;
		heatex.tryConvertB();
		helper.assertTrue(heatex.heatEnergy == 7000, "no room, no boiling");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theColdSideBoilsAirTwoForThree(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.coolantMode = 0;
		heatex.updateCoolantMode();
		heatex.ntmfInputB.setFill(100);
		heatex.heatEnergy = 10_000;
		heatex.tryConvertB();
		// 100 air is 50 operations of 50 TU: 2,500 TU, and 150 warm air
		helper.assertTrue(heatex.ntmfInputB.getFill() == 0 && heatex.ntmfOutputB.getFill() == 150 && heatex.heatEnergy == 7_500,
				heatex.ntmfInputB.getFill() + "/" + heatex.ntmfOutputB.getFill() + "/" + heatex.heatEnergy);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCoolantModeKeepsATankAlreadyOfItsType(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		var before = heatex.ntmfInputB;
		heatex.ntmfInputB.setFill(500);
		heatex.updateCoolantMode();
		helper.assertTrue(heatex.ntmfInputB == before && heatex.ntmfInputB.getFill() == 500, "the same mode changes nothing");

		heatex.coolantMode = 0;
		heatex.updateCoolantMode();
		helper.assertTrue(heatex.ntmfInputB != before && heatex.ntmfInputB.getFill() == 0 && heatex.ntmfInputB.getTankType() == Fluids.AIR
				&& heatex.ntmfInputB.getMaxFill() == 2_400_000, "another mode is a new, empty tank of the same size");
		helper.succeed();
	}

	// ---- the hot side ----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void retypingTheHotSideFollowsTheTraitOrRefuses(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ffInputA.setFluid(salt(300, 10, null, 0));
		heatex.ffOutputA.setFluid(salt(200, 10, null, 0));

		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		helper.assertTrue(heatex.outputFilter == Fluids.WATER, "spent steam cools to water");
		helper.assertTrue(heatex.ntmfInputA.getTankType() == Fluids.SPENTSTEAM && heatex.ntmfOutputA.getTankType() == Fluids.WATER, "the ordinary tanks follow");
		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 0 && heatex.ffOutputA.getFluidAmount() == 0, "the Forge tanks held something else and are emptied");

		heatex.inputFilter = Fluids.DIESEL;
		heatex.updateResource();
		helper.assertTrue(heatex.outputFilter == Fluids.WATER && heatex.ntmfInputA.getTankType() == Fluids.SPENTSTEAM,
				"diesel is neither coolable nor a salt: refused, nothing changes");

		heatex.inputFilter = LeafiaFluids.FLUORIDE;
		heatex.updateResource();
		helper.assertTrue(heatex.outputFilter == LeafiaFluids.FLUORIDE, "a salt comes out as itself");

		heatex.inputFilter = Fluids.COOLANT_HOT;
		heatex.ffInputA.setFluid(LeafiaFF.toFF(Fluids.COOLANT_HOT, 100));
		heatex.updateResource();
		helper.assertTrue(heatex.outputFilter == Fluids.COOLANT, "hot coolant cools to coolant");
		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 100, "a tank already of the type is kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theIdentifierSetsTheHotSideThroughATick(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		ItemStack id = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(id, Fluids.SPENTSTEAM);
		heatex.slots[CoolantHeatexTE.SLOT_IDENTIFIER] = id;
		heatex.updateEntity();
		helper.assertTrue(heatex.inputFilter == Fluids.SPENTSTEAM && heatex.outputFilter == Fluids.WATER, "a coolable fluid is taken");

		ItemStack bad = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(bad, Fluids.DIESEL);
		heatex.slots[CoolantHeatexTE.SLOT_IDENTIFIER] = bad;
		heatex.updateEntity();
		helper.assertTrue(heatex.inputFilter == Fluids.SPENTSTEAM, "diesel is refused");

		ItemStack salt = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(salt, LeafiaFluids.FLUORIDE);
		heatex.slots[CoolantHeatexTE.SLOT_IDENTIFIER] = salt;
		heatex.updateEntity();
		helper.assertTrue(heatex.inputFilter == LeafiaFluids.FLUORIDE, "and the salt is taken");
		helper.assertTrue(!heatex.slots[CoolantHeatexTE.SLOT_IDENTIFIER].isEmpty(), "the identifier stays in its slot");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHotSideCoolsInNtmfMode(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ntmfMode = true;
		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ntmfInputA.setFill(1000);
		heatex.tryConvert();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 0 && heatex.ntmfOutputA.getFill() == 1000, "all 1,000 mB of spent steam became water");
		helper.assertTrue(heatex.heatEnergy == 20_000, "at 20 heat each: " + heatex.heatEnergy);

		// the cycle size caps the operations
		heatex.heatEnergy = 0;
		heatex.ntmfInputA.setFill(1000);
		heatex.ntmfOutputA.setFill(0);
		heatex.amountToCool = 100;
		heatex.tryConvert();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 900 && heatex.ntmfOutputA.getFill() == 100 && heatex.heatEnergy == 2000,
				"a cycle of 100: " + heatex.ntmfInputA.getFill() + "/" + heatex.heatEnergy);

		// the delay runs it only on a game time that divides
		long now = helper.getLevel().getGameTime();
		if(now > 0) {
			heatex.tickDelay = (int) now + 1;
			heatex.tryConvert();
			helper.assertTrue(heatex.ntmfInputA.getFill() == 900, "a delay that does not divide the game time converts nothing");
			heatex.tickDelay = (int) now;
			heatex.tryConvert();
			helper.assertTrue(heatex.ntmfInputA.getFill() == 800, "one that does converts");
		}

		// a delay of zero is a delay of one
		heatex.tickDelay = 0;
		heatex.tryConvert();
		helper.assertTrue(heatex.tickDelay == 1 && heatex.ntmfInputA.getFill() == 700, "zero becomes one");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHotSideCoolsCoolableFluidsInFFMode(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ffInputA.setFluid(LeafiaFF.toFF(Fluids.SPENTSTEAM, 1000));
		heatex.tryConvertFF();
		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 0, "the input is used");
		helper.assertTrue(heatex.ffOutputA.getFluidAmount() == 1000 && LeafiaFF.fromFF(heatex.ffOutputA.getFluid()) == Fluids.WATER,
				"1,000 mB of water out: " + heatex.ffOutputA.getFluid());
		helper.assertTrue(heatex.heatEnergy == 20_000, "20 heat each");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHotSideTakesTheSaltsHeatOutOfItsTag(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ffInputA.setFluid(salt(500, 100, "u233", 0.5));
		heatex.tryConvertFF();

		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 0, "all 500 mB are cooled");
		helper.assertTrue(heatex.ffOutputA.getFluidAmount() == 500 && LeafiaFF.fromFF(heatex.ffOutputA.getFluid()) == LeafiaFluids.FLUORIDE, "and leave as salt");
		helper.assertTrue(heatex.heatEnergy == 100_000, "twice the stack's heat, a mB at a time: 200 x 500, got " + heatex.heatEnergy);
		FluidStack out = heatex.ffOutputA.getFluid();
		helper.assertTrue(heatOf(out) == 0.0, "the salt leaves with no heat");
		helper.assertTrue(MSRTEBase.readMixture(MSRTEBase.nbtProtocol(out)).equals(Map.of("u233", 0.5)), "and with its fuel");
		helper.succeed();
	}

	/**
	 * Upstream's {@code nbtProtocol} returns the stack's own tag, so the heat it zeroes is zeroed on the tank's salt
	 * too: a partly cooled tank loses all of its heat, though only the cooled millibuckets were counted (D-021).
	 */
	@GameTest(template = EMPTY)
	public static void aPartlyCooledTankOfSaltLosesAllItsHeat(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ffInputA.setFluid(salt(800, 100, "u233", 0.5));
		heatex.amountToCool = 300;
		heatex.tryConvertFF();

		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 500, "300 mB cooled");
		helper.assertTrue(heatex.heatEnergy == 60_000, "counted for 300 only: 200 x 300, got " + heatex.heatEnergy);
		helper.assertTrue(heatOf(heatex.ffInputA.getFluid()) == 0.0, "the 500 mB left behind have no heat: " + heatOf(heatex.ffInputA.getFluid()));
		helper.assertTrue(MSRTEBase.readMixture(MSRTEBase.nbtProtocol(heatex.ffInputA.getFluid())).equals(Map.of("u233", 0.5)), "their fuel is kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHeatCounterIsAnIntAndWrapsAsOne(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		// heat x ops is multiplied as two ints before the efficiency: a salt of 100,000 degrees is 200,000 heat a mB,
		// and 24,000 mB of it is 4.8e9, which an int cannot hold.
		heatex.ffInputA.setFluid(salt(24_000, 100_000, null, 0));
		heatex.tryConvertFF();
		int wrapped = (int) (200_000 * 24_000 * 1.0);
		helper.assertTrue((long) 200_000 * 24_000 > Integer.MAX_VALUE && wrapped == 505_032_704, "the product that wraps: " + wrapped);
		helper.assertTrue(heatex.heatEnergy == wrapped, "the counter holds the wrapped int " + wrapped + ", got " + heatex.heatEnergy);
		helper.succeed();
	}

	// ---- the tick: power and the ceiling ---------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aTickSpendsFiveHEAndOnlyWithFive(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ntmfMode = true;
		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ntmfInputA.setFill(1000);

		heatex.power = 4;
		heatex.updateEntity();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 1000 && heatex.power == 4, "4 HE is not enough");

		heatex.power = 5;
		heatex.updateEntity();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 0 && heatex.power == 0, "5 HE runs a cycle and is spent: " + heatex.power);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void thePastTheCeilingTheHotSideStopsAndKeepsItsPower(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.ntmfMode = true;
		heatex.coolantMode = 1; // water: 70 x 2,400,000 = 168,000,000
		heatex.updateCoolantMode();
		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ntmfInputA.setFill(1000);
		heatex.power = 100;

		heatex.heatEnergy = 168_000_001;
		heatex.updateEntity();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 1000 && heatex.power == 100, "one over the ceiling: nothing converts and no power is spent");

		heatex.heatEnergy = 168_000_000;
		heatex.updateEntity();
		helper.assertTrue(heatex.ntmfInputA.getFill() == 0, "at the ceiling it still runs (the test is <=)");
		helper.succeed();
	}

	// ---- the Forge handler -----------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theForgeHandlerTakesOnlyTheSetFluidInFFMode(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		helper.assertTrue(heatex.fill(salt(500, 0, null, 0), FluidAction.SIMULATE) == 500, "the set salt is taken");
		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 0, "a simulation changes nothing");
		helper.assertTrue(heatex.fill(salt(500, 0, null, 0), FluidAction.EXECUTE) == 500 && heatex.ffInputA.getFluidAmount() == 500, "and executed");
		helper.assertTrue(heatex.fill(LeafiaFF.toFF(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "another fluid is refused");

		helper.assertTrue(heatex.drain(100, FluidAction.EXECUTE).isEmpty() && heatex.drain(salt(100, 0, null, 0), FluidAction.EXECUTE).isEmpty(),
				"nothing is given back");
		helper.assertTrue(heatex.ffInputA.getFluidAmount() == 500, "and nothing was");

		heatex.ntmfMode = true;
		helper.assertTrue(heatex.fill(salt(100, 0, null, 0), FluidAction.EXECUTE) == 0, "NTMF mode takes nothing through it");
		helper.assertTrue(heatex.getTanks() == 2 && heatex.getTankCapacity(0) == 2_400_000, "two tanks");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theNetworkFacesAreTheModesTanks(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		helper.assertTrue(heatex.getReceivingTanks().length == 1 && heatex.getReceivingTanks()[0] == heatex.ntmfInputB
				&& heatex.getSendingTanks().length == 1 && heatex.getSendingTanks()[0] == heatex.ntmfOutputB
				&& heatex.getAllTanks().length == 2, "FF mode: the cold side only");

		heatex.ntmfMode = true;
		helper.assertTrue(heatex.getReceivingTanks().length == 2 && heatex.getReceivingTanks()[0] == heatex.ntmfInputA
				&& heatex.getSendingTanks().length == 2 && heatex.getSendingTanks()[0] == heatex.ntmfOutputA
				&& heatex.getAllTanks().length == 4, "NTMF mode: both");

		helper.assertTrue(heatex.getCorrespondingTank(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1)) == heatex.ffInputA
				&& heatex.getCorrespondingTank(LeafiaFF.toFF(Fluids.WATER, 1)) == null && heatex.getSendingTank(LeafiaFF.toFF(Fluids.WATER, 1)) == heatex.ffOutputA,
				"the duct network's tank for the set fluid, and the output for anything");
		helper.succeed();
	}

	// ---- the ports and the placement -------------------------------------------------------

	@GameTest(template = BIG)
	public static void thePortsAreUpstreamsSixOneBlockUp(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(8, 2, 8);
		CoolantHeatexTE heatex = core(helper, at, Direction.SOUTH);
		BlockPos abs = helper.absolutePos(at);

		// facing south, the clockwise rotation is west: four out left and right, two out ahead and behind on each column
		DirPos[] expected = {
				new DirPos(abs.offset(-4, 1, 0), Direction.WEST),
				new DirPos(abs.offset(4, 1, 0), Direction.EAST),
				new DirPos(abs.offset(1, 1, 2), Direction.SOUTH),
				new DirPos(abs.offset(-1, 1, 2), Direction.SOUTH),
				new DirPos(abs.offset(1, 1, -2), Direction.NORTH),
				new DirPos(abs.offset(-1, 1, -2), Direction.NORTH) };
		DirPos[] got = heatex.getConPos();
		helper.assertTrue(got.length == 6, "six ports");
		for(int i = 0; i < 6; i++)
			helper.assertTrue(got[i].equals(expected[i]) && got[i].getDir() == expected[i].getDir(),
					"port " + i + " is " + got[i] + " " + got[i].getDir() + ", wanted " + expected[i] + " " + expected[i].getDir());

		// turned a quarter, the six turn with it
		CoolantHeatexTE east = core(helper, new BlockPos(3, 2, 3), Direction.EAST);
		BlockPos eastAbs = helper.absolutePos(new BlockPos(3, 2, 3));
		DirPos[] turned = east.getConPos();
		// east's clockwise is south: four out to the south and north
		helper.assertTrue(turned[0].equals(eastAbs.offset(0, 1, 4)) && turned[0].getDir() == Direction.SOUTH
				&& turned[1].equals(eastAbs.offset(0, 1, -4)) && turned[1].getDir() == Direction.NORTH
				&& turned[2].equals(eastAbs.offset(2, 1, -1)) && turned[2].getDir() == Direction.EAST,
				"facing east: " + turned[0] + " " + turned[1] + " " + turned[2]);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void placingBuildsTheSevenByThreeByThreeAndSixPorts(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos core = new BlockPos(8, 1, 8);
		CoolantHeatexTE heatex = place(helper, core, Direction.SOUTH);
		Block block = LeafiaBlocks.coolant_heatex.get();

		int parts = 0, extras = 0;
		for(int x = 0; x < 16; x++)
			for(int y = 0; y < 16; y++)
				for(int z = 0; z < 16; z++) {
					var state = helper.getBlockState(new BlockPos(x, y, z));
					if(state.getBlock() != block) continue;
					parts++;
					if(state.getValue(BlockDummyable.PART) == Part.EXTRA) extras++;
				}
		helper.assertTrue(parts == 63, "7 x 3 x 3 blocks, got " + parts);
		helper.assertTrue(extras == 6, "six ports, got " + extras);

		// the core is where the player looked, one on from the cell clicked, and the ports are one up
		helper.assertTrue(heatex.getBlockPos().equals(helper.absolutePos(core)), "the core");
		for(BlockPos port : new BlockPos[] {
				core.offset(-3, 1, 0), core.offset(3, 1, 0), core.offset(1, 1, 1), core.offset(-1, 1, 1), core.offset(1, 1, -1), core.offset(-1, 1, -1) }) {
			helper.assertTrue(helper.getBlockState(port).getValue(BlockDummyable.PART) == Part.EXTRA, "an EXTRA at " + port);
			BlockEntity be = helper.getBlockEntity(port);
			helper.assertTrue(be instanceof BlockEntityProxyCombo && be.getType() == LeafiaBlockEntities.COOLANT_HEATEX_DUMMY.get(), "a combo proxy at " + port);
		}
		// the top row spans 7 and the layers are 3 high: 2 up
		helper.assertTrue(helper.getBlockState(core.offset(3, 2, 1)).getBlock() == block, "the far top corner");
		helper.assertTrue(helper.getBlockState(core.offset(4, 0, 0)).getBlock() != block && helper.getBlockState(core.offset(0, 3, 0)).getBlock() != block, "and no further");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aSpecialPipeJoinsAPortAndNothingElse(GameTestHelper helper) {
		if(!on() || !LeafiaFeatures.ffBridge()) { helper.succeed(); return; }
		BlockPos core = new BlockPos(8, 1, 8);
		place(helper, core, Direction.SOUTH);

		BlockPos port = helper.absolutePos(core.offset(3, 1, 0));
		helper.assertTrue(LeafiaUtil.canConnectFF(helper.getLevel(), port, LeafiaFluids.FLUORIDE, Direction.EAST),
				"a duct beside a port connects: the port shows the core's handler");
		helper.assertTrue(LeafiaUtil.canConnectFF(helper.getLevel(), helper.absolutePos(core), LeafiaFluids.FLUORIDE, Direction.EAST),
				"and so does the core itself");

		BlockPos plain = helper.absolutePos(core.offset(3, 0, 0));
		helper.assertTrue(!LeafiaUtil.canConnectFF(helper.getLevel(), plain, LeafiaFluids.FLUORIDE, Direction.EAST),
				"a plain block of the machine does not");

		var handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, port, null);
		helper.assertTrue(handler instanceof CoolantHeatexTE, "the port's handler is the core");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void thePortsCarryPowerToTheCore(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos core = new BlockPos(8, 1, 8);
		CoolantHeatexTE heatex = place(helper, core, Direction.NORTH);

		BlockEntity port = helper.getBlockEntity(core.offset(3, 1, 0));
		helper.assertTrue(port instanceof BlockEntityProxyCombo, "a port");
		var proxy = (BlockEntityProxyCombo) port;
		helper.assertTrue(proxy.getMaxPower() == CoolantHeatexTE.maxPower, "a port reports the core's capacity");
		long left = proxy.transferPower(1500);
		helper.assertTrue(left == 0 && heatex.power == 1500, "and passes the power to the core: " + heatex.power);
		helper.assertTrue(proxy.canConnect(Direction.EAST) && proxy.canConnect(Direction.NORTH), "on every side");
		helper.succeed();
	}

	// ---- persistence -----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aReloadKeepsTheTanksAndTheSettingsButNotTheHeat(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		heatex.power = 777;
		heatex.ntmfMode = true;
		heatex.coolantMode = 1;
		heatex.updateCoolantMode();
		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ntmfInputA.setFill(1234);
		heatex.ntmfOutputB.setFill(55);
		heatex.ntmfInputB.setFill(66);
		heatex.amountToCool = 321;
		heatex.tickDelay = 7;
		heatex.heatEnergy = 99_999;
		CompoundTag saved = heatex.saveWithFullMetadata(helper.getLevel().registryAccess());

		BlockPos other = new BlockPos(2, 1, 3);
		CoolantHeatexTE copy = core(helper, other, Direction.SOUTH);
		copy.loadWithComponents(saved, helper.getLevel().registryAccess());

		helper.assertTrue(copy.power == 777 && copy.ntmfMode && copy.coolantMode == 1, "power, mode, coolant");
		helper.assertTrue(copy.inputFilter == Fluids.SPENTSTEAM && copy.outputFilter == Fluids.WATER, "the filter, and the output it implies");
		helper.assertTrue(copy.ntmfInputA.getFill() == 1234 && copy.ntmfInputA.getTankType() == Fluids.SPENTSTEAM, "the hot input");
		helper.assertTrue(copy.ntmfInputB.getTankType() == Fluids.WATER && copy.ntmfInputB.getFill() == 66
				&& copy.ntmfOutputB.getTankType() == LeafiaFluids.HOT_WATER && copy.ntmfOutputB.getFill() == 55, "the cold side");
		helper.assertTrue(copy.amountToCool == 321 && copy.tickDelay == 7, "the cycle");
		helper.assertTrue(copy.heatEnergy == 0, "the heat counter is not saved, as upstream's: " + copy.heatEnergy);

		// the Forge tanks, tags and all
		heatex.ffInputA.setFluid(salt(250, 12.5, "th232", 0.25));
		CompoundTag withSalt = heatex.saveWithFullMetadata(helper.getLevel().registryAccess());
		CoolantHeatexTE again = core(helper, new BlockPos(1, 1, 1), Direction.SOUTH);
		again.loadWithComponents(withSalt, helper.getLevel().registryAccess());
		helper.assertTrue(again.ffInputA.getFluidAmount() == 250 && heatOf(again.ffInputA.getFluid()) == 12.5
				&& MSRTEBase.readMixture(MSRTEBase.nbtProtocol(again.ffInputA.getFluid())).equals(Map.of("th232", 0.25)), "the salt keeps its tag");
		helper.succeed();
	}

	// ---- the controls ----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theControlsAreValidated(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);
		BlockPos pos = heatex.getBlockPos();

		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(pos);
		player.setPos(at.x, at.y, at.z);

		// no screen open
		player.containerMenu = player.inventoryMenu;
		heatex.onReceivePacketServer((byte) 0, false, player);
		heatex.onReceivePacketServer((byte) 1, 5, player);
		helper.assertTrue(!heatex.ntmfMode && heatex.amountToCool == 24_000, "a player with no screen open changed it");

		player.containerMenu = new CoolantHeatexMenu(1, player.getInventory(), heatex);

		heatex.onReceivePacketServer((byte) 0, false, player);
		helper.assertTrue(heatex.ntmfMode, "key 0 false flips the mode");
		heatex.onReceivePacketServer((byte) 0, false, player);
		helper.assertTrue(!heatex.ntmfMode, "and back");

		heatex.onReceivePacketServer((byte) 1, 500, player);
		heatex.onReceivePacketServer((byte) 2, 4, player);
		helper.assertTrue(heatex.amountToCool == 500 && heatex.tickDelay == 4, "the cycle and the delay");

		for(Object bad : new Object[] { 0, -1, Integer.MIN_VALUE, "9", 5L, 2.5, true }) {
			heatex.onReceivePacketServer((byte) 1, bad, player);
			heatex.onReceivePacketServer((byte) 2, bad, player);
		}
		helper.assertTrue(heatex.amountToCool == 500 && heatex.tickDelay == 4, "zero, negatives and the wrong types were stored");
		heatex.onReceivePacketServer((byte) 0, "yes", player);
		heatex.onReceivePacketServer((byte) 0, 1, player);
		heatex.onReceivePacketServer((byte) 9, 1, player);
		helper.assertTrue(!heatex.ntmfMode && heatex.coolantMode == 2, "a non-boolean on key 0 or another key acted");

		player.setPos(at.x + 100, at.y, at.z);
		heatex.onReceivePacketServer((byte) 1, 77, player);
		helper.assertTrue(heatex.amountToCool == 500, "a player a hundred blocks away changed it");

		// a screen with no exchanger is not this exchanger's
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new CoolantHeatexMenu(2, player.getInventory(), (CoolantHeatexTE) null);
		heatex.onReceivePacketServer((byte) 1, 77, player);
		helper.assertTrue(heatex.amountToCool == 500, "another exchanger's screen let a player change this one");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCoolantButtonStepsAtTheNextTick(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);

		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(heatex.getBlockPos());
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new CoolantHeatexMenu(1, player.getInventory(), heatex);

		heatex.coolantMode = 1;
		heatex.updateCoolantMode();
		heatex.ntmfInputB.setFill(100);
		heatex.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(heatex.coolantMode == 1, "the step is queued, not made");
		PWRTicks.runQueue();
		helper.assertTrue(heatex.coolantMode == 2 && heatex.ntmfInputB.getTankType() == Fluids.COOLANT && heatex.ntmfInputB.getFill() == 0,
				"air, water, coolant: water steps to coolant and its tank is a new one");

		heatex.onReceivePacketServer((byte) 0, true, player);
		PWRTicks.runQueue();
		helper.assertTrue(heatex.coolantMode == 0, "and coolant wraps to air");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theNumberBoxesSendAtLeastOne(GameTestHelper helper) {
		helper.assertTrue(CoolantHeatexTE.parseCount("500") == 500 && CoolantHeatexTE.parseCount(" 12 ") == 12, "numbers");
		helper.assertTrue(CoolantHeatexTE.parseCount("0") == 1 && CoolantHeatexTE.parseCount("-5") == 1, "zero and negatives are one");
		helper.assertTrue(CoolantHeatexTE.parseCount("") == 1 && CoolantHeatexTE.parseCount("abc") == 1 && CoolantHeatexTE.parseCount("1e3") == 1,
				"anything that is not a number is one");
		helper.succeed();
	}

	// ---- the menu --------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theMenuHasUpstreamsSlots(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);
		ServerPlayer player = mockPlayer(helper);
		CoolantHeatexMenu menu = new CoolantHeatexMenu(1, player.getInventory(), heatex);

		helper.assertTrue(menu.slots.size() == 2 + 36, "two machine slots and the player's 36");
		helper.assertTrue(menu.slots.get(0).x == 17 && menu.slots.get(0).y == 72, "the identifier at (17, 72)");
		helper.assertTrue(menu.slots.get(1).x == 152 && menu.slots.get(1).y == 72, "the battery at (152, 72)");
		helper.assertTrue(menu.slots.get(2).x == 8 && menu.slots.get(2).y == 122 && menu.slots.get(37).x == 8 + 8 * 18 && menu.slots.get(37).y == 180,
				"the inventory at 122 and the hotbar at 180");

		helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT)), "the identifier slot takes anything by hand");
		helper.assertTrue(!menu.slots.get(1).mayPlace(new ItemStack(Items.DIRT)) && menu.slots.get(1).mayPlace(new ItemStack(ModItems.battery_potato.get())),
				"the battery slot takes batteries");
		helper.assertTrue(LeafiaMenuTypes.COOLANT_HEATEX.get() == menu.getType(), "its type");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theShiftClickCountsTheMachineAsThreeSlots(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = core(helper);
		ServerPlayer player = mockPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		CoolantHeatexMenu menu = new CoolantHeatexMenu(1, inventory, heatex);

		// a machine slot goes to the inventory from the far end: the last hotbar slot
		heatex.slots[1] = new ItemStack(ModItems.battery_potato.get());
		ItemStack moved = menu.quickMoveStack(player, 1);
		helper.assertTrue(!moved.isEmpty() && heatex.slots[1].isEmpty() && inventory.getItem(8).is(ModItems.battery_potato.get()),
				"the battery went to the hotbar's last slot");
		inventory.clearContent();

		// from the inventory: slot 0 takes anything, so a battery lands there and not in the battery slot
		inventory.setItem(10, new ItemStack(ModItems.battery_potato.get()));
		menu.quickMoveStack(player, 2 + 1); // inventory slot 10 is menu slot 3
		helper.assertTrue(heatex.slots[0].is(ModItems.battery_potato.get()) && heatex.slots[1].isEmpty(),
				"a battery shift-clicked in the inventory goes to the identifier slot first");

		// with slot 0 taken by something else, a battery goes to its own slot
		inventory.clearContent();
		heatex.slots[0] = new ItemStack(Items.STONE);
		heatex.slots[1] = ItemStack.EMPTY;
		inventory.setItem(10, new ItemStack(ModItems.battery_potato.get()));
		menu.quickMoveStack(player, 2 + 1);
		helper.assertTrue(heatex.slots[1].is(ModItems.battery_potato.get()), "and then to the battery slot");

		// anything else, with slot 0 taken, moves to menu slot 2: the first slot of the player's own inventory
		inventory.clearContent();
		heatex.slots[1] = ItemStack.EMPTY;
		inventory.setItem(12, new ItemStack(Items.DIRT));
		menu.quickMoveStack(player, 2 + 3);
		helper.assertTrue(inventory.getItem(9).is(Items.DIRT) && inventory.getItem(12).isEmpty() && heatex.slots[1].isEmpty(),
				"a dirt block shuffled into the first inventory slot, upstream's third 'machine' slot");

		// the first inventory slot is menu slot 2, upstream's third 'machine' slot, and the chain treats a click there as an inventory click:
		// it goes to the identifier slot, not to the far end of the inventory
		inventory.clearContent();
		heatex.slots[0] = ItemStack.EMPTY;
		heatex.slots[1] = ItemStack.EMPTY;
		inventory.setItem(9, new ItemStack(Items.DIRT));
		menu.quickMoveStack(player, 2);
		helper.assertTrue(heatex.slots[0].is(Items.DIRT) && inventory.getItem(9).isEmpty() && inventory.getItem(8).isEmpty(),
				"a click in menu slot 2 is an inventory click: it goes to slot 0");
		helper.succeed();
	}

	// ---- the switch ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theSwitchDecidesWhatRegisters(GameTestHelper helper) {
		helper.assertTrue((LeafiaBlocks.coolant_heatex != null) == on(), "the block exists exactly when the machine is on");
		helper.assertTrue((LeafiaBlockEntities.COOLANT_HEATEX != null) == on() && (LeafiaBlockEntities.COOLANT_HEATEX_DUMMY != null) == on(),
				"and so do its block entities");
		helper.assertTrue(on() == (LeafiaFeatures.coolantHeatex && LeafiaFeatures.addonFluids), "the gate is the switch and the fluids");
		if(on()) helper.assertTrue(LeafiaFeatures.ffBridge(), "the machine keeps Forge tanks, so it brings the bridge");
		helper.succeed();
	}

	// ---- a real tick -----------------------------------------------------------------------

	/** The whole machine, in the world: cooled steam makes heat, the heat boils water, and the power pays for every tick. */
	@GameTest(template = BIG, timeoutTicks = TickWindow.TIMEOUT)
	public static void aPlacedExchangerCoolsAndBoilsOnRealTicks(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		CoolantHeatexTE heatex = place(helper, new BlockPos(8, 1, 8), Direction.SOUTH);

		heatex.ntmfMode = true;
		heatex.coolantMode = 1;
		heatex.updateCoolantMode();
		heatex.inputFilter = Fluids.SPENTSTEAM;
		heatex.updateResource();
		heatex.ntmfInputA.setFill(1000);
		heatex.ntmfInputB.setFill(5000);
		heatex.power = 5000;

		TickWindow.sequence(helper).thenWaitUntil(() -> {
			helper.assertTrue(heatex.ntmfInputA.getFill() == 0 && heatex.ntmfOutputA.getFill() == 1000,
					"the steam is cooled: " + heatex.ntmfInputA.getFill() + "/" + heatex.ntmfOutputA.getFill());
			// 1,000 x 20 = 20,000 TU boil 285 mB of water at 70 each, leaving 50
			helper.assertTrue(heatex.ntmfOutputB.getFill() == 285 && heatex.ntmfInputB.getFill() == 5000 - 285 && heatex.heatEnergy == 50,
					"the water is boiled: " + heatex.ntmfOutputB.getFill() + " out, " + heatex.heatEnergy + " TU left");
			helper.assertTrue(heatex.power < 5000 && (5000 - heatex.power) % 5 == 0, "5 HE was paid on every tick it ran: " + heatex.power);
		}).thenSucceed();
	}
}
