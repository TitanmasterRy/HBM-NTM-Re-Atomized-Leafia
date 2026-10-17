package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.fluids.traits.FT_DFCFuel;
import com.leafia.contents.fluids.traits.FT_Description;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.fluids.traits.FT_Magnetic;
import com.leafia.init.LeafiaFluidTraits;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the addon's four fluid traits and the 26 base-mod fluids they attach to.
 *
 * <p>Two different claims live here and they are worth keeping apart.
 *
 * <p>The first is <b>balance</b>: 22 DFC power ratings, four magnetic fluids, one moderator
 * threshold. These are read out of {@link LeafiaFluidTraits}'s tables directly, so they hold
 * whether or not {@code features.fluidTraits} is on — a balance table only checkable in one
 * configuration is a balance table nobody checks. Nothing consumes these numbers yet (the DFC,
 * the LFTR and the antimatter pipe are all unported), which is exactly why they need pinning
 * now: a wrong entry would sit unnoticed until the machine that reads it lands, and then look
 * like a bug in the machine.
 *
 * <p>The second is <b>optionality</b>: that the gate gates, and that attaching a trait to a
 * base-mod fluid does not knock off the traits the base mod put there. {@code addTraits} keys
 * a map by trait class, so the failure would be silent — deuterium quietly stops being
 * flammable and nobody finds out until something fails to burn.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFluidTraitTests {

	private static final String EMPTY = "empty";

	/** Upstream's DFC table, transcribed from {@code AddonFluidTraits.preInit} and no other source. */
	private static final Object[][] UPSTREAM_DFC = {
			{ "DEUTERIUM", 1.2F }, { "TRITIUM", 1.3F }, { "OXYGEN", 1.1F }, { "HYDROGEN", 1.0F },
			{ "NITAN", 1.6F }, { "UF6", 1.3F }, { "PUF6", 1.4F }, { "SAS3", 1.5F },
			{ "SCHRABIDIC", 1.7F }, { "AMAT", 2.2F }, { "ASCHRAB", 2.5F }, { "PEROXIDE", 1.05F },
			{ "SULFURIC_ACID", 1.3F }, { "NITRIC_ACID", 1.4F }, { "SOLVENT", 1.45F },
			{ "RADIOSOLVENT", 1.6F }, { "NITROGLYCERIN", 1.5F }, { "DEATH", 1.8F },
			{ "WATZ", 1.5F }, { "XENON", 1.25F }, { "BALEFIRE", 2.4F }, { "STELLAR_FLUX", 2.65F },
	};

	/**
	 * All 22 DFC fuel ratings, by fluid, exactly.
	 *
	 * <p>Float equality on purpose: these are literals copied from upstream, not the result of
	 * arithmetic, so anything but an exact match means a digit was mistyped.
	 */
	@GameTest(template = EMPTY)
	public static void dfcFuelRatingsMatchUpstream(GameTestHelper helper) {

		Map<FluidType, Float> actual = LeafiaFluidTraits.dfcFuels();

		helper.assertTrue(actual.size() == UPSTREAM_DFC.length,
				"upstream lists " + UPSTREAM_DFC.length + " DFC fuels, the table has " + actual.size());

		List<FluidType> order = new ArrayList<>(actual.keySet());

		for(int i = 0; i < UPSTREAM_DFC.length; i++) {

			String name = (String) UPSTREAM_DFC[i][0];
			float expected = (Float) UPSTREAM_DFC[i][1];
			FluidType fluid = order.get(i);

			helper.assertTrue(name.equals(fluid.getName()),
					"entry " + i + " should be " + name + ", got " + fluid.getName()
							+ " - the table is in upstream's order and that order is part of the diff");
			helper.assertTrue(actual.get(fluid) == expected,
					name + " should have a DFC modifier of " + expected + ", got " + actual.get(fluid));
		}

		helper.succeed();
	}

	/** The four base fluids a magnetic field can hold, and nothing else. */
	@GameTest(template = EMPTY)
	public static void magneticFluidsMatchUpstream(GameTestHelper helper) {

		List<FluidType> magnetic = LeafiaFluidTraits.magneticFluids();
		String[] expected = { "OXYGEN", "AMAT", "ASCHRAB", "STELLAR_FLUX" };

		helper.assertTrue(magnetic.size() == expected.length,
				"upstream marks " + expected.length + " base fluids magnetic, got " + magnetic.size());

		for(int i = 0; i < expected.length; i++) {
			helper.assertTrue(expected[i].equals(magnetic.get(i).getName()),
					"magnetic fluid " + i + " should be " + expected[i] + ", got " + magnetic.get(i).getName());
		}

		helper.succeed();
	}

	/**
	 * NTM-CE's percentage formatting, reproduced digit for digit.
	 *
	 * <p>This is the one piece of this batch that is not a straight port — CE's
	 * {@code Library.getPercentage} has no counterpart in 1.7.10 {@code hbm-src}, so it was
	 * read out of CE's bytecode and rewritten inside {@link FT_DFCFuel} (D-1012). If it drifts,
	 * every DFC tooltip in the game shows a different number from upstream's, and nothing else
	 * would notice.
	 *
	 * <p>The separator is normalised before comparing because CE builds its {@code
	 * DecimalFormat} with the default locale and this port keeps that; the digits are the
	 * claim, not the punctuation.
	 */
	@GameTest(template = EMPTY)
	public static void percentageFormattingMatchesNtmCe(GameTestHelper helper) {

		// The four shapes that appear in the shipped table: a clean tenth, a value whose float
		// subtraction does not land on the literal (1.05F - 1F is 0.04999995), one above 100%,
		// and the baseline.
		pct(helper, 1.2F - 1F, "20.00");
		pct(helper, 1.05F - 1F, "5.00");
		pct(helper, 2.65F - 1F, "165.00");
		pct(helper, 1.0F - 1F, "0.00");

		// No fluid ships a modifier below 1, but the tooltip branches on the sign, so the
		// negative path is exercised rather than assumed.
		pct(helper, 0.75F - 1F, "-25.00");

		helper.succeed();
	}

	private static void pct(GameTestHelper helper, float value, String expected) {
		String actual = FT_DFCFuel.getPercentage(value).replace(',', '.');
		helper.assertTrue(expected.equals(actual),
				"getPercentage(" + value + ") should be \"" + expected + "\", got \"" + actual + "\"");
	}

	/** A coolant moderates from a multiplier of 2, and not below it. */
	@GameTest(template = EMPTY)
	public static void lftrCoolantModeratesFromTwo(GameTestHelper helper) {

		helper.assertTrue(!new FT_LFTRCoolant(1F).moderated, "a 1x coolant should not moderate");
		helper.assertTrue(!new FT_LFTRCoolant(1.99F).moderated, "a 1.99x coolant should not moderate");
		helper.assertTrue(new FT_LFTRCoolant(2F).moderated, "a 2x coolant should moderate");
		helper.assertTrue(new FT_LFTRCoolant(3F).moderated, "a 3x coolant should moderate");

		helper.succeed();
	}

	/**
	 * A description goes to exactly one half of the tooltip, and carries its line break.
	 *
	 * <p>The split itself cannot be observed from here. {@code FT_Description} runs its text
	 * through {@code I18nUtil}, and on a dedicated server that is {@code I18nServer}, which
	 * answers every call with one constant and no {@code $} in it (D-003) — so a gametest
	 * server sees one line whatever the text was. That is the base mod's deliberate design and
	 * not something this trait should work around, so what is asserted is what is actually checkable
	 * here: the routing, and that upstream's cryogel string does contain the one break the
	 * tooltip relies on.
	 */
	@GameTest(template = EMPTY)
	public static void descriptionRoutesToOneHalfAndCarriesItsBreak(GameTestHelper helper) {

		helper.assertTrue(LeafiaFluidTraits.CRYOGEL_DESCRIPTION.split("\\$").length == 2,
				"cryogel's description should carry exactly one $ line break, giving two lines");

		List<String> shown = new ArrayList<>();
		List<String> hidden = new ArrayList<>();

		FT_Description always = new FT_Description("a$b", false);
		always.addInfo(shown);
		always.addInfoHidden(hidden);
		helper.assertTrue(!shown.isEmpty(), "a visible description belongs in the always-on half");
		helper.assertTrue(hidden.isEmpty(), "a visible description must not also be in the shift half");

		shown.clear();
		hidden.clear();

		FT_Description onShift = new FT_Description("a$b", true);
		onShift.addInfo(shown);
		onShift.addInfoHidden(hidden);
		helper.assertTrue(shown.isEmpty(), "a hidden description must not be in the always-on half");
		helper.assertTrue(!hidden.isEmpty(), "a hidden description belongs in the shift half");

		helper.succeed();
	}

	/**
	 * The gate and reality agree, in whichever configuration this run is in.
	 *
	 * <p>Same mechanism as {@code LeafiaScaffoldTests.featureSwitchAgreesWithWhatWasRegistered}
	 * and the same reason (D-1003): passing with both sides at their default proves nothing, so
	 * this was run against a flipped {@code leafia-features.toml} as well. See D-1012.
	 */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasApplied(GameTestHelper helper) {

		boolean on = LeafiaFeatures.fluidTraits;

		helper.assertTrue(FluidTrait.traitNameMap.containsKey("dfceff") == on,
				"the trait name map should carry \"dfceff\" exactly when the switch is on");
		helper.assertTrue(FluidTrait.traitNameMap.containsKey("magnetic") == on,
				"the trait name map should carry \"magnetic\" exactly when the switch is on");
		helper.assertTrue(Fluids.DEUTERIUM.hasTrait(FT_DFCFuel.class) == on,
				"deuterium should carry a DFC rating exactly when the switch is on");
		helper.assertTrue(Fluids.AMAT.hasTrait(FT_Magnetic.class) == on,
				"antimatter should be magnetic exactly when the switch is on");
		helper.assertTrue(Fluids.CRYOGEL.hasTrait(FT_Description.class) == on,
				"cryogel should carry its description exactly when the switch is on");

		// And when it is on, the value that got attached is the one in the table.
		if(on) {
			helper.assertTrue(Fluids.DEUTERIUM.getTrait(FT_DFCFuel.class).getModifier() == 1.2F,
					"the attached rating should be the table's, got "
							+ Fluids.DEUTERIUM.getTrait(FT_DFCFuel.class).getModifier());
		}

		helper.succeed();
	}

	/**
	 * Attaching a trait to a base-mod fluid leaves the base mod's own traits alone.
	 *
	 * <p>{@code FluidType.addTraits} puts into a map keyed by trait class, so this could only
	 * break by accident — but it would break silently, and it is the whole of the addon's claim
	 * to be additive. Deuterium is the case to watch: it is the first fluid the addon touches
	 * and the base mod gives it three traits of its own.
	 */
	@GameTest(template = EMPTY)
	public static void addingTraitsDoesNotDisplaceTheBaseModsOwn(GameTestHelper helper) {

		helper.assertTrue(Fluids.DEUTERIUM.hasTrait(FT_Flammable.class),
				"deuterium is flammable in the base mod and must stay flammable");
		helper.assertTrue(Fluids.DEUTERIUM.getTrait(FT_Flammable.class).getHeatEnergy() == 5_000L,
				"deuterium's heat energy should still be the base mod's 5000, got "
						+ Fluids.DEUTERIUM.getTrait(FT_Flammable.class).getHeatEnergy());
		helper.assertTrue(Fluids.DEUTERIUM.hasTrait(FT_Gaseous.class),
				"deuterium is gaseous in the base mod and must stay gaseous");

		helper.succeed();
	}
}
