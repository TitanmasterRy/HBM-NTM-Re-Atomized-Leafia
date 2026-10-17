package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Liquid;
import com.hbm.render.util.EnumSymbol;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.AddonFluidType;
import com.leafia.contents.fluids.traits.FT_APRCoolable;
import com.leafia.contents.fluids.traits.FT_APRCoolant;
import com.leafia.init.LeafiaFluidTraits;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the fourteen fluids the addon adds, and the one property of them that could damage a
 * world.
 *
 * <p>The dangerous one first: <b>ids</b>. Every NTM fluid is stored by id, and the base mod goes
 * as far as halting the game if an insertion shifts one — {@code FluidType}'s {@code forcedId}
 * constructor exists for nothing else. So the claim worth testing is not "the addon's fluids have
 * the right ids" but "the addon's fluids did not move anybody else's", and it is checked by
 * looking at the base mod's fluids rather than at the addon's.
 *
 * <p>The rest is balance: fourteen colours, temperatures and hazard triples, the traits each
 * carries, and the two heat-step chains that <em>replace</em> base-mod ones. Nothing produces or
 * consumes any of these fluids yet, which is the reason to pin them now — a wrong number would
 * sit unnoticed until the LFTR or the turbine lands and then read as a bug in that machine.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFluidTests {

	private static final String EMPTY = "empty";

	/**
	 * The base mod's fluids are exactly where they were.
	 *
	 * <p>This is the test that would catch the addon doing real damage. NTM stores fluids by id
	 * in tanks, in items and on the wire, so a fluid that moves takes every player's tank
	 * contents with it. The addon's own ids start at 1121 precisely so the two ranges cannot
	 * meet.
	 */
	@GameTest(template = EMPTY)
	public static void addonFluidsDoNotMoveTheBaseModsIds(GameTestHelper helper) {

		// A spread of base-mod fluids across the id range, by name rather than by number, so
		// this test says "these did not move" and not "these have these ids".
		for(String name : new String[] { "NONE", "WATER", "STEAM", "UF6", "AMAT", "CRYOGEL", "COOLANT_HOT" }) {

			FluidType fluid = Fluids.fromName(name);
			helper.assertTrue(fluid != Fluids.NONE || name.equals("NONE"), name + " should exist");
			helper.assertTrue(Fluids.fromID(fluid.getID()) == fluid,
					name + " no longer answers to its own id - an addon fluid has displaced it");
			helper.assertTrue(fluid.getID() < AddonFluidType.ID_BASE,
					name + " is a base-mod fluid and must sit below the addon's id range");
		}

		if(LeafiaFluids.present()) {
			for(FluidType fluid : addonFluids()) {
				helper.assertTrue(fluid.getID() >= AddonFluidType.ID_BASE,
						fluid.getName() + " must sit in the addon's id range, at 1121 or above");
			}
		}

		helper.succeed();
	}

	/**
	 * {@code metaOrder} still holds every fluid exactly once.
	 *
	 * <p>Upstream's own invariant, quoted in {@code Fluids}: "you may screw with metaOrder as
	 * much as you like, as long as you keep all fluids in the list exactly once". The base mod
	 * throws on a mismatch during its own init, but that check has already run by the time the
	 * addon adds anything — so if {@code AddonFluidType} ever forgot to add itself, or added
	 * itself twice, nothing would notice until {@code getInNiceOrder} threw much later.
	 */
	@GameTest(template = EMPTY)
	public static void metaOrderHoldsEveryFluidExactlyOnce(GameTestHelper helper) {

		List<FluidType> seen = new ArrayList<>();

		for(FluidType fluid : Fluids.metaOrder) {
			helper.assertTrue(!seen.contains(fluid), fluid.getName() + " appears twice in metaOrder");
			seen.add(fluid);
		}

		helper.assertTrue(Fluids.getAll().size() == Fluids.metaOrder.size(),
				"registry and metaOrder disagree: " + Fluids.getAll().size()
						+ " vs " + Fluids.metaOrder.size());

		helper.succeed();
	}

	/** Thirteen fluids, or none, and never some. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasCreated(GameTestHelper helper) {

		boolean on = LeafiaFeatures.addonFluids;

		helper.assertTrue(LeafiaFluids.present() == on,
				"LeafiaFluids.present() should follow features.addonFluids");

		for(FluidType fluid : addonFluids()) {
			helper.assertTrue((fluid != null) == on,
					"every addon fluid exists exactly when the switch is on - a half-created set"
							+ " would leave the traits pass naming a null");
		}

		if(on) {
			helper.assertTrue(Fluids.fromName("DEATHSTEAM") == LeafiaFluids.DEATHSTEAM,
					"and each is findable by name, like any other fluid");
		}

		helper.succeed();
	}

	/** Upstream's fourteen, verbatim: colour, hazard triple, temperature. */
	@GameTest(template = EMPTY)
	public static void theFourteenFluidsMatchUpstream(GameTestHelper helper) {

		if(!LeafiaFluids.present()) { helper.succeed(); return; }

		check(helper, LeafiaFluids.FLUORIDE, "FLIBE", 0xd3d8b9, 5, 0, 0, 500, EnumSymbol.NONE);
		check(helper, LeafiaFluids.RADSPICE_SLOP, "RADSPICE_SLOP", 0x8baf2d, 9999999, 99999999, 9999999,
				FluidType.ROOM_TEMPERATURE, EnumSymbol.RADIATION);
		check(helper, LeafiaFluids.COOLANT_MAL, "COOLANT_MAL", 0x880f12, 1, 0, 0, 1000, EnumSymbol.NONE);
		check(helper, LeafiaFluids.DEATHSTEAM, "DEATHSTEAM", 0x7c0000, 4, 0, 0, 900, EnumSymbol.NONE);
		check(helper, LeafiaFluids.HF, "HF", 0x3ea7ff, 4, 0, 1, FluidType.ROOM_TEMPERATURE, EnumSymbol.ACID);
		check(helper, LeafiaFluids.N2O, "N2O", 0x6faf30, 2, 0, 0, FluidType.ROOM_TEMPERATURE, EnumSymbol.OXIDIZER);
		check(helper, LeafiaFluids.FLUORINE, "FLUORINE", 0xc5b055, 4, 0, 4, FluidType.ROOM_TEMPERATURE, EnumSymbol.NOWATER);
		check(helper, LeafiaFluids.PYROGEL, "PYROGEL", 0xffa53b, 5, 0, 0, 2250, EnumSymbol.NOWATER);
		check(helper, LeafiaFluids.CRYOINTER, "CRYOINTER", 0x92cabe, 2, 0, 0, -90, EnumSymbol.CROYGENIC);
		check(helper, LeafiaFluids.OXYGEN_GAS, "OXYGEN_GAS", 0x9fd5eb, 0, 0, 0, 20, EnumSymbol.OXIDIZER);

		// The fourteenth arrived after the other thirteen were ported, and it must not have moved
		// them: declared last, it takes the last id (D-1068).
		helper.assertTrue(LeafiaFluids.OXYGEN_GAS.getID() == AddonFluidType.ID_BASE + 13,
				"oxygen gas has id " + LeafiaFluids.OXYGEN_GAS.getID() + ", not the fourteenth slot");
		helper.assertTrue(LeafiaFluids.CRYOINTER.getID() == AddonFluidType.ID_BASE + 12,
				"cryointer moved to " + LeafiaFluids.CRYOINTER.getID());
		helper.assertTrue(LeafiaFluids.OXYGEN_GAS.hasTrait(FT_Gaseous.class), "oxygen gas is a gas");

		helper.succeed();
	}

	/**
	 * The four copies are their base fluid with a different name — and two of them deliberately
	 * are <em>not</em> quite.
	 *
	 * <p>Hot water and hot air reject the boil/condense chain, which is what stops warm water
	 * turning into steam by itself. That predicate is the whole reason the copy constructor takes
	 * one, so it is the half worth testing.
	 */
	@GameTest(template = EMPTY)
	public static void theFourCopiesTakeWhatUpstreamSays(GameTestHelper helper) {

		if(!LeafiaFluids.present()) { helper.succeed(); return; }

		helper.assertTrue(LeafiaFluids.UF6_233.getColor() == Fluids.UF6.getColor()
				&& LeafiaFluids.UF6_235.getColor() == Fluids.UF6.getColor(),
				"both enriched hexafluorides are UF6's colour");
		helper.assertTrue(LeafiaFluids.UF6_233.getTexture() == Fluids.UF6.getTexture(),
				"and reuse UF6's texture rather than shipping one");

		helper.assertTrue(LeafiaFluids.HOT_WATER.temperature == 70, "hot water is 70");
		helper.assertTrue(LeafiaFluids.HOT_AIR.temperature == 50, "hot air is 50");

		helper.assertTrue(LeafiaFluids.HOT_WATER.hasTrait(FT_Liquid.class),
				"hot water is still a liquid - the copy pass carried water's traits over");
		helper.assertTrue(LeafiaFluids.HOT_AIR.hasTrait(FT_Gaseous.class), "and hot air a gas");

		helper.assertTrue(!LeafiaFluids.HOT_WATER.hasTrait(FT_Heatable.class)
				&& !LeafiaFluids.HOT_WATER.hasTrait(FT_Coolable.class),
				"but hot water must NOT boil or condense - upstream's rejectBoiling predicate is"
						+ " the reason the copy constructor takes one at all");
		helper.assertTrue(!LeafiaFluids.HOT_AIR.hasTrait(FT_Heatable.class)
				&& !LeafiaFluids.HOT_AIR.hasTrait(FT_Coolable.class), "same for hot air");

		helper.succeed();
	}

	/**
	 * The two heat-step chains that replace base-mod ones, and the temperature drop that comes
	 * with them.
	 *
	 * <p>These are the addon's real rebalance of the base mod's boiler and heat exchanger, and
	 * they only exist when {@code fluidTraits} is on — so this test says which of the two states
	 * it is in rather than assuming one.
	 */
	@GameTest(template = EMPTY)
	public static void theHeatChainsReplaceTheBaseModsOnlyWhenSwitchedOn(GameTestHelper helper) {

		boolean on = LeafiaFeatures.fluidTraits && LeafiaFluids.present();

		helper.assertTrue((Fluids.COOLANT_HOT.temperature == LeafiaFluidTraits.COOLANT_HOT_TEMPERATURE) == on,
				"COOLANT_HOT drops from 600 to 400 exactly when the traits are applied");

		if(!on) { helper.succeed(); return; }

		FT_Heatable coolant = Fluids.COOLANT_HOT.getTrait(FT_Heatable.class);
		helper.assertTrue(coolant != null, "COOLANT_HOT gains a heat step");
		helper.assertTrue(coolant.getFirstStep().heatReq == 700
						&& coolant.getFirstStep().amountReq == 1
						&& coolant.getFirstStep().typeProduced == LeafiaFluids.COOLANT_MAL
						&& coolant.getFirstStep().amountProduced == 1,
				"700 heat, 1 in, 1 malfunctioning coolant out");

		FT_Heatable steam = Fluids.ULTRAHOTSTEAM.getTrait(FT_Heatable.class);
		helper.assertTrue(steam != null && steam.getFirstStep().heatReq == 960
						&& steam.getFirstStep().amountReq == 10
						&& steam.getFirstStep().typeProduced == LeafiaFluids.DEATHSTEAM
						&& steam.getFirstStep().amountProduced == 1,
				"960 heat, ten ultrahot steam to one deathsteam");

		FT_Coolable death = LeafiaFluids.DEATHSTEAM.getTrait(FT_Coolable.class);
		helper.assertTrue(death != null && death.getEfficiency(FT_Coolable.CoolingType.TURBINE) == 0D,
				"deathsteam has NO turbine efficiency - upstream comments that line out on purpose,"
						+ " and uncommenting it is a balance change");

		helper.succeed();
	}

	/** The two DFC ratings and the description that name addon fluids. */
	@GameTest(template = EMPTY)
	public static void theAddonFluidsOwnDfcRatings(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidTraits || !LeafiaFluids.present()) { helper.succeed(); return; }

		helper.assertTrue(LeafiaFluids.N2O.getTrait(com.leafia.contents.fluids.traits.FT_DFCFuel.class)
				.getModifier() == 1.4F, "N2O burns at 1.4");
		helper.assertTrue(LeafiaFluids.PYROGEL.getTrait(com.leafia.contents.fluids.traits.FT_DFCFuel.class)
				.getModifier() == 2.0F, "pyrogel at 2.0");
		helper.assertTrue(LeafiaFluids.FLUORIDE.hasTrait(com.leafia.contents.fluids.traits.FT_LFTRCoolant.class),
				"and FLIBE is the LFTR's coolant, declared where the fluid is");

		helper.succeed();
	}

	/**
	 * The APR's two traits and the boiler step that makes oxygen gas. All three change what a
	 * fluid does, so they ride {@code fluidTraits} with the rest of upstream's trait pass; this
	 * says which state it is in rather than assuming one.
	 */
	@GameTest(template = EMPTY)
	public static void theAprTraitsAndTheOxygenBoilRideTheTraitSwitch(GameTestHelper helper) {

		boolean on = LeafiaFeatures.fluidTraits && LeafiaFluids.present();

		helper.assertTrue(Fluids.OXYGEN.hasTrait(FT_Heatable.class) == on,
				"liquid oxygen should boil exactly when the traits are applied");
		if(!on) { helper.succeed(); return; }

		FT_Heatable.HeatingStep boil = Fluids.OXYGEN.getTrait(FT_Heatable.class).getFirstStep();
		helper.assertTrue(boil.amountReq == 1 && boil.heatReq == 1 && boil.typeProduced == LeafiaFluids.OXYGEN_GAS
				&& boil.amountProduced == 50, "liquid oxygen boils 1 mB to 50 of gas for 1 TU");

		FT_APRCoolable coolable = LeafiaFluids.PYROGEL.getTrait(FT_APRCoolable.class);
		helper.assertTrue(coolable != null && coolable.conversion == Fluids.CRYOGEL && coolable.in == 1 && coolable.out == 1,
				"pyrogel cools to cryogel 1:1 in the APR");
		FT_APRCoolant coolant = LeafiaFluids.OXYGEN_GAS.getTrait(FT_APRCoolant.class);
		helper.assertTrue(coolant != null && coolant.conversion == Fluids.OXYGEN && coolant.in == 50 && coolant.out == 1,
				"oxygen gas runs back to liquid oxygen 50:1 in the APR");
		helper.assertTrue(com.hbm.inventory.fluid.trait.FluidTrait.traitNameMap.get("aprcoolant") == FT_APRCoolant.class
				&& com.hbm.inventory.fluid.trait.FluidTrait.traitNameMap.get("aprcoolable") == FT_APRCoolable.class,
				"both traits are registered by upstream's names");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, FluidType fluid, String name, int colour,
			int poison, int flammability, int reactivity, int temperature, EnumSymbol symbol) {

		helper.assertTrue(fluid.getName().equals(name), name + " is named " + fluid.getName());
		helper.assertTrue(fluid.getColor() == colour,
				name + " colour is " + Integer.toHexString(fluid.getColor()) + ", expected " + Integer.toHexString(colour));
		helper.assertTrue(fluid.poison == poison, name + " poison is " + fluid.poison + ", expected " + poison);
		helper.assertTrue(fluid.flammability == flammability,
				name + " flammability is " + fluid.flammability + ", expected " + flammability);
		helper.assertTrue(fluid.reactivity == reactivity,
				name + " reactivity is " + fluid.reactivity + ", expected " + reactivity);
		helper.assertTrue(fluid.temperature == temperature,
				name + " temperature is " + fluid.temperature + ", expected " + temperature);
		helper.assertTrue(fluid.symbol == symbol, name + " symbol is " + fluid.symbol + ", expected " + symbol);
	}

	private static FluidType[] addonFluids() {
		return new FluidType[] {
				LeafiaFluids.FLUORIDE, LeafiaFluids.UF6_233, LeafiaFluids.UF6_235,
				LeafiaFluids.HOT_WATER, LeafiaFluids.HOT_AIR, LeafiaFluids.RADSPICE_SLOP,
				LeafiaFluids.COOLANT_MAL, LeafiaFluids.DEATHSTEAM, LeafiaFluids.HF,
				LeafiaFluids.N2O, LeafiaFluids.FLUORINE, LeafiaFluids.PYROGEL,
				LeafiaFluids.CRYOINTER };
	}
}
