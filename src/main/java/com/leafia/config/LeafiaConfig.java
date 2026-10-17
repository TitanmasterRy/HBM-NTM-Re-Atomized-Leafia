package com.leafia.config;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The numbers the addon runs on. A COMMON config, built the way
 * {@code com.hbm.config.GeneralConfig} is — a {@code Values} holder for the spec objects, plain
 * static fields for consumers, and {@link #refresh} copying one into the other once the file
 * loads. A consumer reads {@code LeafiaConfig.dfcComponentRange}, never a {@code ConfigValue},
 * because a spec value throws until its file is in.
 *
 * <p>Balance is COMMON rather than STARTUP because it is read while the game runs, not while it
 * registers, and COMMON is the type with the semantics upstream's single {@code .cfg} had.
 * Which features <em>exist</em> is a different question with a different answer — see
 * {@link LeafiaFeatures}.
 *
 * <p><b>Every default here is upstream's own value</b>, read out of
 * {@code com.leafia.settings.AddonConfig}. Standing rule 4 binds: a default that differs from
 * upstream's is a deviation and needs a D-10xx entry saying why. The options are declared even
 * where the feature that reads them is not ported yet, so that the file a player sees does not
 * grow section by section as phases land.
 *
 * <p>What is deliberately <em>not</em> carried over is upstream's config format — a custom
 * {@code config/hbm/leafia2.lcfg} whose own header reads "The configs will not apply by
 * default! Remove ? on start of each configs to apply". TOML on a {@code ModConfigSpec} is what
 * every other config in this port uses, and a config that silently ignores what the player
 * typed is a support burden rather than a feature.
 */
public final class LeafiaConfig {

	private LeafiaConfig() {}

	/** How far DFC components can reach, in blocks. Upstream {@code dfcComponentRange} = 50. */
	public static int dfcComponentRange = 50;
	/** Whether the modular turbine RPS is capped. Upstream {@code enableGovernedRPS} = true. */
	public static boolean enableGovernedRPS = true;
	/** Minimum governed RPS for modular turbines. Upstream {@code governedRPS} = 60.0. */
	public static double governedRPS = 60.0;
	/** Blades per side before turbulence skyrockets. Upstream {@code maxOptimalTurbineLength} = 5. */
	public static int maxOptimalTurbineLength = 5;
	/** Steam-input surge turbulence multiplier. Upstream {@code surgeTurbulenceMultiplier} = 1.0. */
	public static double surgeTurbulenceMultiplier = 1.0;
	/** Seconds the Meteor Protection Beacon's placer must have been alive. Upstream = 1800. */
	public static int meteorDiverterMinAliveTime = 1800;
	/** Chunks the Meteor Protection Beacon protects. Upstream = 3. */
	public static int meteorDiverterProtectionRadius = 3;
	/** Whether meteors carve the addon's custom craters. Upstream {@code enableMeteorCraters} = true. */
	public static boolean meteorCraters = true;
	/** Whether barrels gain side ports. Upstream {@code enableBarrelSidePorts} = true. */
	public static boolean barrelSidePorts = true;
	/** IC10 stack depth before StackOverflow. Upstream {@code ic10maxstack} = 512. */
	public static int ic10MaxStack = 512;
	/** IC10 register index bound before OutOfRegisterBounds. Upstream {@code ic10maxregisters} = 64. */
	public static int ic10MaxRegisters = 64;
	/**
	 * How far from a Custom Control Panel a link may point, in blocks (D-3063). Not upstream's: NTM-CE has no limit, so a modified
	 * client could link a panel to anything in the world. The plan's default was "the multi-detonator's own range", but the
	 * multi-detonator has none (it stores any position), so the value is the addon's own and an owner question.
	 */
	public static int controlPanelLinkRadius = 128;
	/**
	 * Whether the addon's radon hazard does anything. NTM-CE's own {@code enableRadon} (default
	 * true), which the base mod does not have - radon gas is CE's, not {@code hbm-src}'s (D-1078).
	 */
	public static boolean enableRadon = true;
	/** NTM-CE's {@code RadiationConfig.disableToxic} (default false), which the base mod does not have: the addon's toxic type does nothing while true. */
	public static boolean disableToxic = false;

	/**
	 * Upstream's {@code enableSludgeUnits}: show a fuel rod's temperatures and rates in the addon's joke
	 * units (grilles, stevejobs) instead of degrees and seconds. Cosmetic; off.
	 */
	public static boolean bullshitUnits = false;

	/**
	 * Upstream's {@code enableSchizoMode} (default false): every biome acts like the digamma crater - its fog, its music,
	 * its figures and its rain. On, it switches {@link com.leafia.contents.worldgen.biomes.artificial.DigammaCrater#schizoMode}
	 * on when the file loads; off, it leaves that flag alone, because the static countdown can also turn it on for the rest of
	 * a session, as upstream's did (D-3040).
	 */
	public static boolean schizoMode = false;

	public static final ModConfigSpec SPEC;
	private static final Values VALUES;

	static {
		Pair<Values, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Values::new);
		VALUES = pair.getLeft();
		SPEC = pair.getRight();
	}

	/** The spec values themselves. Read through the static fields above, not from here. */
	private static class Values {

		final ModConfigSpec.IntValue vDfcComponentRange;
		final ModConfigSpec.BooleanValue vEnableGovernedRPS;
		final ModConfigSpec.DoubleValue vGovernedRPS;
		final ModConfigSpec.IntValue vMaxOptimalTurbineLength;
		final ModConfigSpec.DoubleValue vSurgeTurbulenceMultiplier;
		final ModConfigSpec.IntValue vMeteorDiverterMinAliveTime;
		final ModConfigSpec.IntValue vMeteorDiverterProtectionRadius;
		final ModConfigSpec.BooleanValue vMeteorCraters;
		final ModConfigSpec.BooleanValue vBarrelSidePorts;
		final ModConfigSpec.IntValue vIc10MaxStack;
		final ModConfigSpec.IntValue vIc10MaxRegisters;
		final ModConfigSpec.IntValue vControlPanelLinkRadius;
		final ModConfigSpec.BooleanValue vEnableRadon;
		final ModConfigSpec.BooleanValue vDisableToxic;
		final ModConfigSpec.BooleanValue vBullshitUnits;
		final ModConfigSpec.BooleanValue vSchizoMode;

		Values(ModConfigSpec.Builder builder) {

			builder.comment(
					"Balance for Leafia's Cursed Addon.",
					"",
					"Every default here is the addon author's own value. Changing one is a deviation",
					"from the addon as it was balanced, which is fine - but it is a change, not a",
					"preference. Which features exist at all is a separate file, leafia-features.toml.")
					.push("balance");

			builder.push("dfc");
			vDfcComponentRange = builder.comment("How far DFC components can reach, in blocks.")
					.defineInRange("componentRange", 50, 1, 512);
			builder.pop();

			builder.push("turbine");
			vEnableGovernedRPS = builder.comment("Whether the modular turbine RPS is capped.")
					.define("enableGovernedRPS", true);
			vGovernedRPS = builder.comment("Minimum governed RPS for modular turbines.")
					.defineInRange("governedRPS", 60.0, 0.0, 100000.0);
			vMaxOptimalTurbineLength = builder
					.comment("Blades per side before turbulence skyrockets.")
					.defineInRange("maxOptimalLength", 5, 1, 64);
			vSurgeTurbulenceMultiplier = builder
					.comment("Multiplier on steam-input surge turbulence.")
					.defineInRange("surgeTurbulenceMultiplier", 1.0, 0.0, 1000.0);
			builder.pop();

			builder.push("meteor");
			vMeteorDiverterMinAliveTime = builder
					.comment("Seconds the Meteor Protection Beacon's placer must have been alive.")
					.defineInRange("diverterMinAliveTime", 1800, 0, Integer.MAX_VALUE);
			vMeteorDiverterProtectionRadius = builder
					.comment("Chunks the Meteor Protection Beacon protects.")
					.defineInRange("diverterProtectionRadius", 3, 0, 256);
			vMeteorCraters = builder.comment("Whether meteors carve the addon's custom craters.")
					.define("customCraters", true);
			builder.pop();

			builder.push("machines");
			vBarrelSidePorts = builder.comment("Whether barrels gain side ports.")
					.define("barrelSidePorts", true);
			builder.pop();

			builder.push("ic10");
			vIc10MaxStack = builder
					.comment("IC10 nodes throw StackOverflow past this stack depth.")
					.defineInRange("maxStack", 512, 1, 1048576);
			vIc10MaxRegisters = builder
					.comment("IC10 nodes throw OutOfRegisterBounds past this register index.")
					.defineInRange("maxRegisters", 64, 1, 4096);
			builder.pop();

			builder.push("controlPanel");
			vControlPanelLinkRadius = builder
					.comment("How far from a Custom Control Panel a link may point, in blocks. The server refuses an edit that links",
							"farther. Not in the original, which has no limit; the figure is this port's own (D-3063).")
					.defineInRange("linkRadius", 128, 1, 4096);
			builder.pop();

			builder.push("hazards");
			vEnableRadon = builder
					.comment("Whether items carrying the addon's radon figure irradiate their holder.")
					.define("enableRadon", true);
			vDisableToxic = builder
					.comment("Switches the addon's toxic hazard (lead, beryllium, cadmium, arsenic) off.")
					.define("disableToxic", false);
			builder.pop();

			builder.push("units");
			vBullshitUnits = builder
					.comment("(WIP) Changes this mod from using units you know to some bullshit units that god knows.")
					.define("enableSludgeUnits", false);
			builder.pop();

			builder.push("digamma");
			vSchizoMode = builder
					.comment("Every biome acts like the digamma crater biome.")
					.define("enableSchizoMode", false);
			builder.pop();

			builder.pop(); // balance
		}
	}

	/** Copies the loaded config into the static fields above. */
	public static void refresh() {
		dfcComponentRange = VALUES.vDfcComponentRange.get();
		enableGovernedRPS = VALUES.vEnableGovernedRPS.get();
		governedRPS = VALUES.vGovernedRPS.get();
		maxOptimalTurbineLength = VALUES.vMaxOptimalTurbineLength.get();
		surgeTurbulenceMultiplier = VALUES.vSurgeTurbulenceMultiplier.get();
		meteorDiverterMinAliveTime = VALUES.vMeteorDiverterMinAliveTime.get();
		meteorDiverterProtectionRadius = VALUES.vMeteorDiverterProtectionRadius.get();
		meteorCraters = VALUES.vMeteorCraters.get();
		barrelSidePorts = VALUES.vBarrelSidePorts.get();
		ic10MaxStack = VALUES.vIc10MaxStack.get();
		ic10MaxRegisters = VALUES.vIc10MaxRegisters.get();
		controlPanelLinkRadius = VALUES.vControlPanelLinkRadius.get();
		enableRadon = VALUES.vEnableRadon.get();
		disableToxic = VALUES.vDisableToxic.get();
		bullshitUnits = VALUES.vBullshitUnits.get();
		schizoMode = VALUES.vSchizoMode.get();
		if(schizoMode) com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.schizoMode = true;
	}
}
