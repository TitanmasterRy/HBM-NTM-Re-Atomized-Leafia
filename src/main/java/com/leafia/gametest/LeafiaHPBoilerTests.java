package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE.BoilData;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The high-pressure boiler: what it boils, what that costs, and the two things about it that are
 * true of <em>this</em> port rather than of upstream.
 *
 * <h2>The finding these tests exist to pin</h2>
 *
 * The boiler's headline feature is chaining — run a fluid up several {@link FT_Heatable} steps in
 * one block, for a heat bonus. <b>On this port's fluid table it can only ever reach one step</b>,
 * because 1.7.10's {@code STEAM} carries {@code FT_Coolable} and no {@code FT_Heatable}, so the
 * walk stops the moment it leaves water. The machine is ported correctly and the arithmetic is
 * upstream's; what is missing is rungs to climb. See {@link #theChainStopsAtSteamOnThisPortsTable}
 * and D-1054.
 *
 * <p>That is a <em>lineage</em> difference, not a defect to fix. The addon is written against
 * NTM-CE's 1.12.2 fluid table and this port's base is 1.7.10's, which D-951 verified line for
 * line. Adding {@code FT_Heatable} to steam to make the addon's machine shine would be changing
 * base-mod balance from an addon, which rule 1 and D-1001 both forbid.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaHPBoilerTests {

	private static final String EMPTY = "empty";

	/**
	 * Water boils, and it costs something.
	 *
	 * <p>This is the precondition {@code HPBoilerScreen}'s heat bar divides by — the bar is
	 * upstream's {@code heat * 16 / heatReq} with no guard — so it is pinned here rather than
	 * worked around there.
	 */
	@GameTest(template = EMPTY)
	public static void waterGivesTheBoilerARealHeatCost(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));
		BoilData dat = boiler.getBoiledFluid(Fluids.WATER, 4);

		helper.assertTrue(dat.compression >= 1, "water must boil, or every boiler is inert");
		helper.assertTrue(dat.outType == Fluids.STEAM,
				"water's first heating step produces STEAM, got " + dat.outType.getName());
		helper.assertTrue(dat.heatReq > 0,
				"heatReq is what the GUI's heat bar divides by; zero is a client crash");
		helper.assertTrue(dat.inAmt > 0 && dat.outAmt > 0, "both amounts must be positive");

		helper.succeed();
	}

	/**
	 * Upstream's own numbers for the one step this port can take.
	 *
	 * <p>{@code WATER.addStep(200, 1, STEAM, 100)} is {@code (heat, req, type, prod)}, so one
	 * millibucket of water and 200 TU make a hundred of steam. With a chain of one the bonus
	 * divisor is {@code 1.5^0 = 1} and the gcd reduction finds nothing, so the fused numbers are
	 * the step's own — which is the only case in which that is true.
	 */
	@GameTest(template = EMPTY)
	public static void theFusedNumbersAreUpstreamsForAChainOfOne(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));
		BoilData dat = boiler.getBoiledFluid(Fluids.WATER, 1);

		List<String> problems = new ArrayList<>();

		if(dat.compression != 1) problems.add("compression is " + dat.compression + ", wanted 1");
		if(dat.inAmt != 1) problems.add("inAmt is " + dat.inAmt + ", wanted 1");
		if(dat.outAmt != 100) problems.add("outAmt is " + dat.outAmt + ", wanted 100");
		if(dat.heatReq != 200) problems.add("heatReq is " + dat.heatReq + ", wanted 200");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " number(s) off upstream's step: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Asking for more compression than the fluid table can supply changes nothing.
	 *
	 * <p>The class doc says why: nothing downstream of water is heatable here. If this test ever
	 * fails because the chain got <em>longer</em>, that is the base mod's fluid table gaining
	 * rungs and the boiler coming into its own — read D-1054 before changing the expectation.
	 */
	@GameTest(template = EMPTY)
	public static void theChainStopsAtSteamOnThisPortsTable(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));

		helper.assertTrue(Fluids.STEAM.getTrait(FT_Heatable.class) == null,
				"STEAM has gained an FT_Heatable; the boiler can now chain and D-1054 is stale");

		List<String> problems = new ArrayList<>();

		for(int asked = 1; asked <= 8; asked++) {
			BoilData dat = boiler.getBoiledFluid(Fluids.WATER, asked);
			if(dat.compression != 1)
				problems.add("asking for " + asked + " gave " + dat.compression);
			if(dat.heatReq != 200)
				problems.add("asking for " + asked + " cost " + dat.heatReq + " TU, not 200");
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s) — the chain is no longer one step deep: "
						+ String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A fluid with no heating step at all leaves the tanks exactly as they were.
	 *
	 * <p>{@code setFluid} only rebuilds when the chain produced something, which is what stops an
	 * identifier pointed at an unboilable fluid from emptying a running boiler. Upstream's guard,
	 * and the reason {@code heatReq} keeps its last good value.
	 */
	@GameTest(template = EMPTY)
	public static void anUnboilableFluidLeavesTheTanksAlone(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));

		FluidType before = boiler.input.getTankType();
		int heatReqBefore = boiler.heatReq;

		helper.assertTrue(Fluids.NONE.getTrait(FT_Heatable.class) == null,
				"NONE must not be heatable, or this test proves nothing");

		boiler.setFluid(Fluids.NONE, 4);

		helper.assertTrue(boiler.input.getTankType() == before,
				"the input tank retyped to " + boiler.input.getTankType().getName());
		helper.assertTrue(boiler.heatReq == heatReqBefore,
				"heatReq moved to " + boiler.heatReq + "; the GUI would then divide by it");

		helper.succeed();
	}

	/**
	 * The fuzzy identifier in slot 4 is what tells the boiler its fluid.
	 *
	 * <p>This is the link the batch was chosen for: {@code IItemFluidIdentifier} has exactly one
	 * implementor in the project (D-1053), and this is the first <em>addon</em> machine to read
	 * one. Oil is used rather than another steam grade because oil is heatable here and steam is
	 * not — see the class doc.
	 */
	@GameTest(template = EMPTY)
	public static void theIdentifierInSlotFourSetsWhatItBoils(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));

		helper.assertTrue(boiler.input.getTankType() == Fluids.WATER,
				"a fresh boiler starts on water, got " + boiler.input.getTankType().getName());

		ItemStack identifier = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(identifier, Fluids.OIL);
		boiler.slots[HPBoilerTE.SLOT_IDENTIFIER] = identifier;

		boiler.updateEntity();

		helper.assertTrue(boiler.input.getTankType() == Fluids.OIL,
				"the identifier must retype the input tank, got "
						+ boiler.input.getTankType().getName());
		helper.assertTrue(boiler.output.getTankType() == Fluids.HOTOIL,
				"and the output to what oil boils into, got "
						+ boiler.output.getTankType().getName());

		helper.succeed();
	}

	/**
	 * One tick's boiling: water in and heat down, steam out, in upstream's ratio.
	 *
	 * <p>Asserted as a <em>relationship</em> rather than against literals written a moment
	 * earlier, which is D-953's rule: the boiler also bleeds heat and may take some from below,
	 * so what is fixed is that every millibucket consumed produced {@code outAmt} and cost
	 * {@code heatReq}.
	 */
	@GameTest(template = EMPTY)
	public static void itBoilsWaterIntoSteam(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 1, 1));

		boiler.input.setFill(1000);
		boiler.heat = 1_000_000;

		int waterBefore = boiler.input.getFill();
		int steamBefore = boiler.output.getFill();
		int heatBefore = boiler.heat;

		boiler.tryConvert();

		int consumed = waterBefore - boiler.input.getFill();
		int produced = boiler.output.getFill() - steamBefore;
		int spent = heatBefore - boiler.heat;

		List<String> problems = new ArrayList<>();

		if(consumed <= 0) problems.add("no water was consumed");
		if(produced <= 0) problems.add("no steam was produced");
		if(!boiler.isOn) problems.add("the boiler did not report itself running");

		// The per-operation amounts are read back from getBoiledFluid rather than off the block
		// entity's package-private fields, which keeps the test outside the class's own head:
		// if setFluid and getBoiledFluid ever disagreed, this would catch it rather than hide it.
		BoilData dat = boiler.getBoiledFluid(boiler.input.getTankType(), boiler.compression);

		int ops = consumed / dat.inAmt;
		if(produced != ops * dat.outAmt)
			problems.add(ops + " operations produced " + produced + ", wanted " + ops * dat.outAmt);
		if(spent != ops * dat.heatReq)
			problems.add(ops + " operations cost " + spent + " TU, wanted " + ops * dat.heatReq);

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** With no heat source below, the buffer bleeds by a thousandth and never goes negative. */
	@GameTest(template = EMPTY)
	public static void heatBleedsAwayWithNothingUnderneath(GameTestHelper helper) {

		HPBoilerTE boiler = core(helper, new BlockPos(1, 2, 1));

		boiler.heat = 10_000;
		boiler.updateEntity();

		helper.assertTrue(boiler.heat < 10_000, "the buffer must bleed, got " + boiler.heat);
		helper.assertTrue(boiler.heat >= 10_000 - 10 - 1,
				"the bleed is max(heat/1000, 1) per tick, not " + (10_000 - boiler.heat));

		boiler.heat = 0;
		boiler.updateEntity();
		helper.assertTrue(boiler.heat == 0, "an empty buffer must stay at zero, got " + boiler.heat);

		helper.succeed();
	}

	/**
	 * The two side ports exist and are fluid proxies of the addon's own type.
	 *
	 * <p>{@code BlockEntityType} validity is a fixed list of blocks, so a proxy planted with the
	 * base mod's {@code MULTIBLOCK_DUMMY} is not valid for this block. Seeded as a regression,
	 * that turns out to be <b>loud</b> rather than silent — vanilla refuses it with "Invalid
	 * block entity hbm:multiblock_dummy ... state at", naming both sides. Worth writing down,
	 * because the reason this test exists was the assumption that it would be quiet.
	 */
	@GameTest(template = EMPTY)
	public static void theSidePortsAreProxiesOfTheAddonsOwnType(GameTestHelper helper) {

		BlockPos core = new BlockPos(2, 1, 2);

		helper.setBlock(core, LeafiaBlocks.hp_boiler.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));

		List<String> problems = new ArrayList<>();

		for(Direction side : new Direction[] { Direction.EAST, Direction.WEST }) {

			BlockPos port = core.above().relative(side);

			helper.setBlock(port, LeafiaBlocks.hp_boiler.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.EXTRA)
					.setValue(BlockDummyable.DIR, Direction.NORTH));

			var te = helper.getLevel().getBlockEntity(helper.absolutePos(port));

			if(!(te instanceof BlockEntityProxyCombo proxy)) {
				problems.add(side + " port is " + te + ", not a proxy");
			} else if(proxy.getType() != LeafiaBlockEntities.HP_BOILER_DUMMY.get()) {
				problems.add(side + " port is a proxy of " + proxy.getType()
						+ ", not the addon's own type");
			}
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every boiler efficiency in the base mod is 1, which is why upstream's discarded one is
	 * invisible.
	 *
	 * <p>{@code getBoiledFluid} multiplies each step's {@code getEfficiency(BOILER)} into a local
	 * and then passes the literal {@code 1} to its result instead — the computed value is dead on
	 * both return paths. Preserved (D-021), and it changes nothing today <b>only because every
	 * heatable fluid here declares a boiler efficiency of exactly 1</b>. This test is the tripwire
	 * for that stopping being true: when it fails, the bug has become live and D-1054 says what to
	 * do about it.
	 */
	@GameTest(template = EMPTY)
	public static void everyBoilerEfficiencyIsOneSoTheDiscardedValueIsInvisible(GameTestHelper helper) {

		List<String> offenders = new ArrayList<>();

		for(FluidType fluid : Fluids.getAll()) {

			FT_Heatable trait = fluid.getTrait(FT_Heatable.class);
			if(trait == null) continue;

			double eff = trait.getEfficiency(HeatingType.BOILER);

			// Zero means "this fluid is not boilable at all", which the chain already stops on.
			if(eff != 0.0D && eff != 1.0D) offenders.add(fluid.getName() + " = " + eff);
		}

		helper.assertTrue(offenders.isEmpty(),
				offenders.size() + " fluid(s) now declare a boiler efficiency that is neither 0 "
						+ "nor 1, so the HP boiler's discarded efficiency has become a live bug: "
						+ String.join("; ", offenders));
		helper.succeed();
	}

	/// HELPERS ///

	/**
	 * Places a boiler core and returns its block entity.
	 *
	 * <p>{@code PART} has to be set: {@link BlockDummyable}'s default is {@code DUMMY}, which has
	 * no block entity by design, and {@code helper.getBlockEntity} throws on null — so a plain
	 * {@code setBlock} fails here as a missing block entity rather than as a wrong one.
	 */
	private static HPBoilerTE core(GameTestHelper helper, BlockPos pos) {

		helper.setBlock(pos, LeafiaBlocks.hp_boiler.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));

		return (HPBoilerTE) helper.getBlockEntity(pos);
	}
}
