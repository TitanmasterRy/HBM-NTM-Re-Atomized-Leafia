package com.leafia.init;

import com.leafia.contents.fluids.traits.FT_APRCoolable;
import com.leafia.contents.fluids.traits.FT_APRCoolant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.leafia.LeafiaMod;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.traits.FT_DFCFuel;
import com.leafia.contents.fluids.traits.FT_Description;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.fluids.traits.FT_Magnetic;

/**
 * Hangs the addon's four fluid traits on the base mod's fluids.
 *
 * <p>Upstream: {@code com/leafia/init/AddonFluidTraits.java}, and this is a <b>partial</b>
 * port of it, and the upstream file is not counted as ported. What is missing
 * is everything that names a fluid the addon itself adds ({@code AddonFluids}, unported); the
 * modular turbine's steam list moved to {@code MTCoreTE.initSteamTypes} (D-1058). The comment block below
 * {@link #init()} lists those lines rather than leaving them to be rediscovered, because three
 * of them <em>replace</em> traits on base-mod fluids and are the part of this file that will
 * need the most care. See D-1012.
 *
 * <h2>Ordering</h2>
 *
 * Called from the addon's mod constructor. {@code hbm} is declared {@code ordering="AFTER"} in
 * this jar's {@code neoforge.mods.toml}, so {@code MainRegistry}'s constructor — and with it
 * {@code Fluids.init()} — has already run and every {@code Fluids} field below is non-null.
 * NTM's fluids are plain objects rather than registry entries, so there is no registry event to
 * wait for. That is also why the two tables below are built by a <em>method</em> and not held
 * in static fields: a static initialiser here could run before {@code Fluids.init()} and would
 * capture nulls.
 *
 * <h2>The gate</h2>
 *
 * Everything here is behind {@link LeafiaFeatures#fluidTraits}, which defaults <b>off</b>.
 * These traits attach to <em>base-mod</em> fluids, so switching them on changes what the base
 * mod's own fluids say and — once the DFC, the LFTR and the antimatter pipe land — how they
 * behave. That is D-1002's "off for anything that changes base-mod behaviour". With the switch
 * off, not one of the four traits is registered and not one fluid is touched.
 *
 * <p>The tables are readable either way, so {@code LeafiaFluidTraitTests} can pin all 26
 * numbers whether or not the switch is on. A balance table that is only checkable in one
 * configuration is a balance table nobody checks.
 */
public final class LeafiaFluidTraits {

	private LeafiaFluidTraits() {}

	/** The one shared instance upstream keeps, because the trait carries no state. */
	public static final FT_Magnetic MAGNETIC = new FT_Magnetic();


	/** Upstream's, and the answer to its own comment on the N2O field. */
	public static final String N2O_DESCRIPTION = "Will you stop begging me?";

	/**
	 * What upstream drops {@code COOLANT_HOT} to, from the base mod's 600. A base-mod rebalance,
	 * which is why it is applied here rather than with the fluids — see
	 * {@link #applyAddonFluidTraits()}.
	 */
	public static final int COOLANT_HOT_TEMPERATURE = 400;

	/** Upstream's flavour text for cryogel, including the joke and its {@code $} line break. */
	public static final String CRYOGEL_DESCRIPTION =
			"Lord bob asked me to$change his horrendous snowball recipe";

	private static boolean applied = false;

	/**
	 * DFC power output per fuel, upstream's numbers verbatim and in upstream's order.
	 *
	 * <p>1.0 is the baseline rather than a penalty: hydrogen's tooltip reads "0.00%", not
	 * "-100%". Every entry is pinned by {@code LeafiaFluidTraitTests}, because a fuel table
	 * that is quietly wrong is invisible until somebody wonders why their reactor
	 * underperforms.
	 */
	public static Map<FluidType, Float> dfcFuels() {

		Map<FluidType, Float> fuels = new LinkedHashMap<>();
		fuels.put(Fluids.DEUTERIUM, 1.2F);
		fuels.put(Fluids.TRITIUM, 1.3F);
		fuels.put(Fluids.OXYGEN, 1.1F);
		fuels.put(Fluids.HYDROGEN, 1F);
		fuels.put(Fluids.NITAN, 1.6F);
		fuels.put(Fluids.UF6, 1.3F);
		fuels.put(Fluids.PUF6, 1.4F);
		fuels.put(Fluids.SAS3, 1.5F);
		fuels.put(Fluids.SCHRABIDIC, 1.7F);
		fuels.put(Fluids.AMAT, 2.2F);
		fuels.put(Fluids.ASCHRAB, 2.5F);
		fuels.put(Fluids.PEROXIDE, 1.05F);
		fuels.put(Fluids.SULFURIC_ACID, 1.3F);
		fuels.put(Fluids.NITRIC_ACID, 1.4F);
		fuels.put(Fluids.SOLVENT, 1.45F);
		fuels.put(Fluids.RADIOSOLVENT, 1.6F);
		fuels.put(Fluids.NITROGLYCERIN, 1.5F);
		fuels.put(Fluids.DEATH, 1.8F);
		fuels.put(Fluids.WATZ, 1.5F);
		fuels.put(Fluids.XENON, 1.25F);
		fuels.put(Fluids.BALEFIRE, 2.4F);
		fuels.put(Fluids.STELLAR_FLUX, 2.65F);
		return fuels;
	}

	/**
	 * The base-mod fluids a magnetic field can hold. Upstream attaches {@code MAGNETIC} inline
	 * beside the DFC rating on each of these four; splitting it into its own list is the one
	 * structural change this file makes to upstream, and it is what lets the test read the set
	 * without the switch being on.
	 */
	public static List<FluidType> magneticFluids() {
		return Arrays.asList(Fluids.OXYGEN, Fluids.AMAT, Fluids.ASCHRAB, Fluids.STELLAR_FLUX);
	}

	/**
	 * Registers the trait names and attaches them. Idempotent — {@code traitNameMap} is a
	 * {@code HashBiMap} and would throw on a second put rather than no-op.
	 */
	public static void init() {

		if(LeafiaFeatures.aprReactor()) applyAPRTraits();

		if(!LeafiaFeatures.fluidTraits || applied) return;
		applied = true;

		registerTrait("dfceff", FT_DFCFuel.class);
		registerTrait("desc", FT_Description.class);
		registerTrait("lftrcoolant", FT_LFTRCoolant.class);
		registerTrait("magnetic", FT_Magnetic.class);
		registerTrait("aprcoolant", FT_APRCoolant.class);
		registerTrait("aprcoolable", FT_APRCoolable.class);

		for(Map.Entry<FluidType, Float> fuel : dfcFuels().entrySet()) {
			fuel.getKey().addTraits(new FT_DFCFuel(fuel.getValue()));
		}
		for(FluidType magnetic : magneticFluids()) {
			magnetic.addTraits(MAGNETIC);
		}

		Fluids.CRYOGEL.addTraits(new FT_Description(CRYOGEL_DESCRIPTION, false));

		if(LeafiaFluids.present()) applyAddonFluidTraits();

		LeafiaMod.LOGGER.info("Fluid traits applied: {} DFC fuels, {} magnetic, 1 description.",
				dfcFuels().size(), magneticFluids().size());
	}

	/*
	 * Upstream's AddonFluidTraits.preInit also fills the modular turbine's steam table:
	 *
	 *   MTCoreTE.steamTypes = [DEATHSTEAM, ULTRAHOTSTEAM, SUPERHOTSTEAM, HOTSTEAM, STEAM, SPENTSTEAM]
	 *
	 * That line is NOT here, deliberately. It attaches nothing to a base-mod fluid - it is the
	 * turbine's own data - so it rides the turbine's gate (LeafiaFeatures.modularTurbine()) rather
	 * than this file's fluidTraits switch, which defaults off. Here it would have left every
	 * default install with an empty table and a turbine that accepts no steam at all. It is
	 * MTCoreTE.initSteamTypes, called from the mod constructor. See D-1058.
	 */

	/**
	 * The half of upstream's pass that names fluids the addon itself adds.
	 *
	 * <h2>The three that rewrite base-mod fluids</h2>
	 *
	 * {@code COOLANT_HOT}, {@code ULTRAHOTSTEAM} and — by way of {@code CRYOINTER} — the coolant
	 * chain get new {@link FT_Heatable} traits, and a fluid holds one trait per class, so these
	 * <em>replace</em> whatever heat step the base mod gave them. That is a real rebalance of the
	 * base mod's boiler and heat exchanger rather than an addition, and it is why this whole file
	 * sits behind a switch that defaults off.
	 *
	 * <p>{@code COOLANT_HOT.temperature = 400} belongs to that group and lives here rather than
	 * in {@link LeafiaFluids}, where upstream puts it: it drops a base-mod fluid from 600 to 400,
	 * which is the same kind of change as the step chain and should not ride on a switch that
	 * defaults on. See D-1023.
	 *
	 * <h2>Upstream's own note on deathsteam</h2>
	 *
	 * The turbine efficiency line is commented out upstream, with the reason given: <em>"sorry to
	 * break it to you but DEATHSTEAM isn't meant to be used in turbines, the modular turbine
	 * feature is a bonus feature and I don't want to encourage new players into thinking they're
	 * usable"</em>. It stays commented out, because uncommenting it is a balance change.
	 */
	private static void applyAddonFluidTraits() {

		LeafiaFluids.N2O.addTraits(new FT_DFCFuel(1.4F));
		LeafiaFluids.PYROGEL.addTraits(new FT_DFCFuel(2.0F), new FT_APRCoolable(Fluids.CRYOGEL, 1, 1));
		LeafiaFluids.N2O.addTraits(new FT_Description(N2O_DESCRIPTION, false));

		Fluids.COOLANT_HOT.temperature = COOLANT_HOT_TEMPERATURE;

		Fluids.COOLANT_HOT.addTraits(new FT_Heatable()
				.setEff(HeatingType.BOILER, 1.0D)
				.setEff(HeatingType.HEATEXCHANGER, 1.0D)
				.addStep(700, 1, LeafiaFluids.COOLANT_MAL, 1));
		LeafiaFluids.COOLANT_MAL.addTraits(new FT_Coolable(Fluids.COOLANT_HOT, 1, 1, 700)
				.setEff(CoolingType.HEATEXCHANGER, 1.0D));

		Fluids.ULTRAHOTSTEAM.addTraits(new FT_Heatable()
				.setEff(HeatingType.BOILER, 1.0D)
				.setEff(HeatingType.HEATEXCHANGER, 0.25D)
				.addStep(960, 10, LeafiaFluids.DEATHSTEAM, 1));
		LeafiaFluids.DEATHSTEAM.addTraits(new FT_Coolable(Fluids.ULTRAHOTSTEAM, 1, 10, 960)
				/* .setEff(CoolingType.TURBINE, 1.0D) - deliberately off, see the method note */
				.setEff(CoolingType.HEATEXCHANGER, 0.5D));

		LeafiaFluids.CRYOINTER.addTraits(new FT_Heatable()
				.setEff(HeatingType.PA, 2.5D)
				.setEff(HeatingType.BOILER, 1.0D)
				.addStep(750, 1, Fluids.COOLANT, 1));

		/*
		 * Liquid oxygen boils into oxygen gas at 1 TU per mB, 1 mB to 50 - the base mod's OXYGEN
		 * has no FT_Heatable of its own, so this adds one rather than replacing one. And the APR
		 * runs that gas back to liquid at the same 50:1. D-1068.
		 */
		Fluids.OXYGEN.addTraits(new FT_Heatable().setEff(HeatingType.BOILER, 1).addStep(1, 1, LeafiaFluids.OXYGEN_GAS, 50));
		LeafiaFluids.OXYGEN_GAS.addTraits(new FT_APRCoolant(Fluids.OXYGEN, 50, 1));
	}

	private static void registerTrait(String name, Class<? extends FluidTrait> clazz) {
		if(FluidTrait.traitNameMap.containsKey(name)) return; // the APR's pair may already be in (applyAPRTraits)
		FluidTrait.traitNameMap.put(name, clazz);
		FluidTrait.traitList.add(clazz);
	}

	private static boolean aprApplied = false;

	/**
	 * The APR's own pair, on the addon's own fluids, with the reactor (D-3027): gaseous oxygen is its coolant
	 * ({@code FT_APRCoolant(OXYGEN, 50, 1)}: fifty millibuckets of gas make one of liquid oxygen) and pyrogel is
	 * coolable in a chamber ({@code FT_APRCoolable(CRYOGEL, 1, 1)}). These are the two lines of
	 * {@link #applyAddonFluidTraits()} the reactor cannot run without - without the first, assembling one throws -
	 * and both attach to fluids only the addon has, so they are additive and ride {@code features.apr} rather than
	 * waiting on {@code fluidTraits}, which is off because the rest of it retunes base-mod fluids. The values are
	 * the ones the full pass writes, so with both switches on the second write is the same trait again.
	 */
	public static void applyAPRTraits() {
		if(aprApplied || !LeafiaFluids.present()) return;
		aprApplied = true;
		registerTrait("aprcoolant", FT_APRCoolant.class);
		registerTrait("aprcoolable", FT_APRCoolable.class);
		LeafiaFluids.OXYGEN_GAS.addTraits(new FT_APRCoolant(Fluids.OXYGEN, 50, 1));
		LeafiaFluids.PYROGEL.addTraits(new FT_APRCoolable(Fluids.CRYOGEL, 1, 1));
	}
}
