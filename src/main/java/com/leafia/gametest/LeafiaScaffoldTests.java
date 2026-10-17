package com.leafia.gametest;

import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What L0 actually proves.
 *
 * <p>A scaffold phase is easy to declare done and hard to check: the jar compiles, the game
 * starts, and nothing tells you whether the second mod was loaded at all or whether its config
 * was read before or after its content registered. These tests run headless in
 * {@code runGameTestServer} and answer both, which is why they exist before any content does.
 *
 * <p>The scaffold block they were written around is <b>gone</b> — five real blocks have landed,
 * which is the condition the plan set for removing it. What remains here are the
 * claims that were always permanent: the jar loads beside {@code hbm}, the dependency points
 * one way, and the balance defaults are upstream's.
 *
 * <p>The gate check that used to live here went with it. It is not lost: every feature that has
 * landed carries its own {@code featureSwitchAgreesWithWhatWasRegistered}, so D-1003's mechanism
 * is now guarded four times over against real content rather than once against a placeholder.
 * See D-1018.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaScaffoldTests {

	private static final String EMPTY = "empty";

	/**
	 * The addon jar loaded, and so did the mod it depends on. A dedicated server reaching this
	 * point at all means {@code neoforge.mods.toml} parsed and the {@code hbm} dependency
	 * resolved, which is most of what L0 claims.
	 */
	@GameTest(template = EMPTY)
	public static void addonLoadsBesideTheBaseMod(GameTestHelper helper) {

		helper.assertTrue(ModList.get().isLoaded(LeafiaRef.MODID),
				"the addon should be loaded, its own gametest is running");
		helper.assertTrue(ModList.get().isLoaded("hbm"),
				"the base mod should be loaded: the addon declares it as a required dependency");

		helper.succeed();
	}

	/**
	 * The balance defaults are upstream's, to the number.
	 *
	 * <p>Standing rule 4: a default that differs from {@code com.leafia.settings.AddonConfig}
	 * is a deviation and needs a D-9xx entry. These are the values that file declares.
	 */
	@GameTest(template = EMPTY)
	public static void balanceDefaultsMatchUpstream(GameTestHelper helper) {

		helper.assertTrue(LeafiaConfig.dfcComponentRange == 50,
				"dfcComponentRange should be upstream's 50, got " + LeafiaConfig.dfcComponentRange);
		helper.assertTrue(LeafiaConfig.governedRPS == 60.0,
				"governedRPS should be upstream's 60.0, got " + LeafiaConfig.governedRPS);
		helper.assertTrue(LeafiaConfig.maxOptimalTurbineLength == 5,
				"maxOptimalTurbineLength should be upstream's 5, got " + LeafiaConfig.maxOptimalTurbineLength);
		helper.assertTrue(LeafiaConfig.surgeTurbulenceMultiplier == 1.0,
				"surgeTurbulenceMultiplier should be upstream's 1.0, got " + LeafiaConfig.surgeTurbulenceMultiplier);
		helper.assertTrue(LeafiaConfig.meteorDiverterMinAliveTime == 1800,
				"meteorDiverterMinAliveTime should be upstream's 1800, got " + LeafiaConfig.meteorDiverterMinAliveTime);
		helper.assertTrue(LeafiaConfig.meteorDiverterProtectionRadius == 3,
				"meteorDiverterProtectionRadius should be upstream's 3, got " + LeafiaConfig.meteorDiverterProtectionRadius);
		helper.assertTrue(LeafiaConfig.ic10MaxStack == 512,
				"ic10MaxStack should be upstream's 512, got " + LeafiaConfig.ic10MaxStack);
		helper.assertTrue(LeafiaConfig.ic10MaxRegisters == 64,
				"ic10MaxRegisters should be upstream's 64, got " + LeafiaConfig.ic10MaxRegisters);

		helper.succeed();
	}

	/**
	 * Overhauls are off by default; additive content is on.
	 *
	 * <p>The rule from D-1002: installing the jar gives you the addon's content, and does not
	 * silently rewrite base-mod machines you already use. A future feature that defaults its
	 * overhaul to on has to change this test, which is the point.
	 */
	@GameTest(template = EMPTY)
	public static void overhaulsDefaultOff(GameTestHelper helper) {

		helper.assertTrue(!LeafiaFeatures.dfcOverhaul, "the DFC overhaul should default off");
		helper.assertTrue(!LeafiaFeatures.zirnoxOverhaul, "the ZIRNOX overhaul should default off");
		helper.assertTrue(!LeafiaFeatures.radiationClassification,
				"radiation classification should default off");
		helper.assertTrue(!LeafiaFeatures.structuralIntegrity,
				"structural integrity should default off - upstream itself warns it is very laggy");
		helper.assertTrue(!LeafiaFeatures.explosionVfx, "the explosion, corium and fire overhaul should default off (D-3013)");
		helper.assertTrue(!LeafiaFeatures.folkvangr, "Folkvangr replacing every F.L.E.I.J.A. should default off (D-3013)");
		helper.assertTrue(!LeafiaFeatures.falloutRain, "the fallout-rain overhaul should default off (D-3009)");
		helper.assertTrue(!LeafiaFeatures.leafiaTorex, "the addon mushroom cloud should default off (D-3009)");
		helper.assertTrue(!LeafiaFeatures.explosiveGasRebalance, "the explosive-gas rebalance should default off (D-1097)");
		helper.assertTrue(!LeafiaFeatures.rbmkOverhaul, "the RBMK overhaul should default off (D-3018)");
		helper.assertTrue(!LeafiaFeatures.meteorOverhaul, "the meteor craters and shake should default off (D-3022)");
		helper.assertTrue(!LeafiaFeatures.meteorStrikeRebalance, "the meteor strike rebalance should default off (D-1097, D-3022)");
		helper.assertTrue(!LeafiaFeatures.missileOverhaul, "airbursting base missiles should default off (D-3022)");
		helper.assertTrue(!LeafiaFeatures.landmineOverhaul, "the landmine rebalance should default off (D-1097, D-3022)");
		helper.assertTrue(!LeafiaFeatures.impactSeismic, "the impact collapse should default off (D-3022)");
		helper.assertTrue(LeafiaFeatures.meteorDiverter && LeafiaFeatures.addonBombs, "the beacon and the two bombs are additive and should default on (D-3022)");
		helper.assertTrue(!LeafiaFeatures.sirenOverhaul && !LeafiaFeatures.laserDetonatorOverhaul && !LeafiaFeatures.arcWelderOverhaul
				&& !LeafiaFeatures.solderingOverhaul && !LeafiaFeatures.felOverhaul, "the siren, detonator, welder, soldering and laser overhauls should default off (D-3027)");
		helper.assertTrue(LeafiaFeatures.apr, "the absorption particle reactor is additive and should default on (D-3027)");

		helper.assertTrue(LeafiaFeatures.elevators, "elevators are additive and should default on");
		helper.assertTrue(LeafiaFeatures.ffDuct, "the fluid duct is additive and should default on");
		helper.assertTrue(LeafiaFeatures.turbines, "turbines are additive and should default on");

		helper.succeed();
	}
}
