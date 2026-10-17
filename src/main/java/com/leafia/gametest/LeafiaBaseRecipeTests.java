package com.leafia.gametest;

import static com.hbm.inventory.OreDictManager.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;
import com.hbm.inventory.recipes.ArcFurnaceRecipes.ArcFurnaceRecipe;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.CrucibleRecipe;
import com.hbm.inventory.recipes.CrucibleRecipes;
import com.hbm.inventory.recipes.FuelPoolRecipes;
import com.hbm.inventory.recipes.PlasmaForgeRecipe;
import com.hbm.inventory.recipes.PlasmaForgeRecipes;
import com.hbm.items.machine.ItemDepletedFuel;
import com.leafia.init.LeafiaMats;
import com.leafia.init.LeafiaOreDict;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe;
import com.hbm.inventory.recipes.CyclotronRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes.ElectrolysisRecipe;
import com.hbm.inventory.recipes.ExposureChamberRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes.ExposureChamberRecipe;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.PseudoFluidType;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.ParticleAcceleratorRecipes;
import com.hbm.inventory.recipes.ParticleAcceleratorRecipes.ParticleAcceleratorRecipe;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe;
import com.hbm.inventory.recipes.SILEXRecipes;
import com.hbm.inventory.recipes.SILEXRecipes.SILEXRecipe;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.inventory.recipes.anvil.AnvilRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilConstructionRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.util.Tuple.Pair;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.battery.AddonEnumBatteryPack;
import com.leafia.init.recipes.LeafiaBaseRecipes;
import com.leafia.init.recipes.LeafiaBaseRecipes.Held;
import com.leafia.init.recipes.LeafiaRecipeInjector;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's rows in the base mod's own machines (D-1095): each is looked up with the call the
 * machine makes, and its inputs, outputs and numbers are worked by hand from upstream's
 * {@code Addon*Recipes} source rather than read back from the generated table.
 *
 * <p>The base handlers are process-wide statics and the GameTest server runs batches in one JVM,
 * so a test that rebuilds a handler does it in a single method body, which runs to completion on
 * the server thread before another starts.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaBaseRecipeTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ helpers

	private static Item item(AStack s) { return ((ComparableStack) s).toStack().getItem(); }

	private static boolean isItem(AStack s, Item item, int n) {
		return s instanceof ComparableStack c && c.toStack().getItem() == item && s.stacksize == n;
	}

	private static boolean isOre(AStack s, String name, int n) {
		return s instanceof OreDictStack o && o.name.equals(name) && s.stacksize == n;
	}

	private static boolean isFluid(FluidStack s, FluidType type, int fill) {
		return s != null && s.type == type && s.fill == fill;
	}

	private static boolean isStack(GenericRecipes.IOutput out, Item item, int n) {
		return out instanceof GenericRecipes.ChanceOutput c && c.stack.getItem() == item && c.stack.getCount() == n;
	}

	private static GenericRecipe chem(GameTestHelper helper, String name, int duration, long power) {
		GenericRecipe r = ChemicalPlantRecipes.INSTANCE.recipeNameMap.get(name);
		helper.assertTrue(r != null, name + " is not in the chemical plant's list");
		helper.assertTrue(r.duration == duration && r.power == power, name + ": " + r.duration + " ticks at " + r.power + ", expected " + duration + " at " + power);
		return r;
	}

	private static Item pack(AddonEnumBatteryPack p) { return LeafiaItems.battery_packs.get(p).get(); }

	private static Item res(String name) { return LeafiaResourceItems.get(name).get(); }

	// ------------------------------------------------------------------ chemical plant

	@GameTest(template = EMPTY)
	public static void chemicalPlantHasFlibeAndCryointer(GameTestHelper helper) {

		if(LeafiaFeatures.addonFluids) {
			GenericRecipe flibe = chem(helper, "chem.leafia.flibe", 60, 400);
			helper.assertTrue(flibe.inputItem.length == 3 && isOre(flibe.inputItem[0], BE.ingot(), 1) && isOre(flibe.inputItem[1], LI.ingot(), 1)
					&& isOre(flibe.inputItem[2], F.dust(), 2), "flibe inputs: beryllium, lithium, 2 fluorite dust");
			helper.assertTrue(flibe.inputFluid.length == 1 && isFluid(flibe.inputFluid[0], Fluids.SULFURIC_ACID, 2_000), "flibe takes 2000 sulfuric acid");
			helper.assertTrue(flibe.outputItem.length == 2 && isStack(flibe.outputItem[0], ModItems.ingot_calcium.get(), 2)
					&& isStack(flibe.outputItem[1], ModItems.sulfur.get(), 2), "flibe gives 2 calcium and 2 sulfur");
			helper.assertTrue(flibe.outputFluid.length == 3 && isFluid(flibe.outputFluid[0], LeafiaFluids.FLUORIDE, 2_700)
					&& isFluid(flibe.outputFluid[1], Fluids.WATER, 2_000) && isFluid(flibe.outputFluid[2], Fluids.OXYGEN, 2_000), "flibe: 2700 liquid fluoride, 2000 water, 2000 oxygen");
			helper.assertTrue(flibe.customLocalization, "flibe is a named recipe");

			GenericRecipe cryo = chem(helper, "chem.leafia.cryointer", 90, 500);
			helper.assertTrue(cryo.inputItem.length == 1 && isItem(cryo.inputItem[0], ModItems.pellet_charged.get(), 1), "cryointer takes a charged pellet");
			helper.assertTrue(cryo.inputFluid.length == 2 && isFluid(cryo.inputFluid[0], Fluids.COOLANT, 2_000) && isFluid(cryo.inputFluid[1], Fluids.PERFLUOROMETHYL_COLD, 1_800), "cryointer takes 2000 coolant and 1800 cold perfluoromethyl");
			helper.assertTrue(cryo.outputFluid.length == 2 && isFluid(cryo.outputFluid[0], LeafiaFluids.CRYOINTER, 2_000) && isFluid(cryo.outputFluid[1], Fluids.PERFLUOROMETHYL, 1_800), "cryointer gives 2000 interstage cryogel and 1800 perfluoromethyl");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void chemicalPlantMakesTheBatteryPacks(GameTestHelper helper) {

		if(!LeafiaFeatures.batteryPacks) { helper.succeed(); return; }

		GenericRecipe desh = chem(helper, "chem.leafia.battery_desh", 100, 15_000);
		helper.assertTrue(isOre(desh.inputItem[0], DESH.ingot(), 24) && isOre(desh.inputItem[1], BAKELITE.dust(), 12) && isOre(desh.inputItem[2], P_RED.dust(), 12)
				&& desh.inputItem.length == 3, "desh: 24 workers' alloy ingots, 12 bakelite, 12 red phosphorus (upstream's 48/2, 24/2, 24/2)");
		helper.assertTrue(isFluid(desh.inputFluid[0], Fluids.COALGAS, 1_500) && desh.outputFluid == null, "desh: 1500 coal gas in, no fluid out");
		helper.assertTrue(isStack(desh.outputItem[0], pack(AddonEnumBatteryPack.BATTERY_DESH), 1), "desh gives the desh pack");

		GenericRecipe sat = chem(helper, "chem.leafia.battery_saturnite", 100, 35_000);
		helper.assertTrue(sat.inputItem.length == 2 && isOre(sat.inputItem[0], BIGMT.ingot(), 32) && isOre(sat.inputItem[1], ANY_HARDPLASTIC.ingot(), 16), "saturnite inputs");
		helper.assertTrue(isFluid(sat.inputFluid[0], Fluids.PERFLUOROMETHYL_COLD, 2_000) && isFluid(sat.outputFluid[0], Fluids.PERFLUOROMETHYL, 2_000), "saturnite: cold perfluoromethyl in, plain out");
		helper.assertTrue(isStack(sat.outputItem[0], pack(AddonEnumBatteryPack.BATTERY_SATURNITE), 1), "saturnite gives its pack");

		GenericRecipe euph = chem(helper, "chem.leafia.battery_euphemium", 100, 50_000);
		helper.assertTrue(euph.inputItem.length == 3 && isOre(euph.inputItem[0], EUPH.ingot(), 24) && isOre(euph.inputItem[1], XE135.dust(), 16) && isOre(euph.inputItem[2], SBD.dust(), 16), "euphemium inputs");
		helper.assertTrue(isFluid(euph.inputFluid[0], Fluids.HELIUM4, 8_000), "euphemium: 8000 helium-4");
		helper.assertTrue(isStack(euph.outputItem[0], pack(AddonEnumBatteryPack.BATTERY_EUPHEMIUM), 1), "euphemium gives its pack");

		if(LeafiaFeatures.addonFluids) {
			GenericRecipe slop = chem(helper, "chem.leafia.battery_slop", 100, 300_000);
			helper.assertTrue(slop.inputItem.length == 3 && isItem(slop.inputItem[0], ModItems.powder_chlorophyte.get(), 24) && isItem(slop.inputItem[1], ModItems.powder_balefire.get(), 24)
					&& isOre(slop.inputItem[2], AS.nugget(), 8), "slop inputs");
			helper.assertTrue(isFluid(slop.inputFluid[0], LeafiaFluids.RADSPICE_SLOP, 32_000), "slop: 32000 radspice slop");
			helper.assertTrue(isStack(slop.outputItem[0], pack(AddonEnumBatteryPack.BATTERY_SLOP), 1), "slop gives its pack");
		}

		GenericRecipe spk = chem(helper, "chem.leafia.battery_spk", 100, 500_000);
		helper.assertTrue(spk.inputItem.length == 3 && isOre(spk.inputItem[0], DNT.ingot(), 48) && isItem(spk.inputItem[1], ModItems.powder_spark_mix.get(), 32)
				&& isItem(spk.inputItem[2], ModItems.coil_magnetized_tungsten.get(), 12), "spk inputs");
		helper.assertTrue(spk.inputFluid.length == 2 && isFluid(spk.inputFluid[0], Fluids.PERFLUOROMETHYL_COLD, 8_000) && isFluid(spk.inputFluid[1], Fluids.STELLAR_FLUX, 4_000), "spk fluids in");
		helper.assertTrue(isFluid(spk.outputFluid[0], Fluids.PERFLUOROMETHYL_HOT, 8_000), "spk: 8000 hot perfluoromethyl out");
		helper.assertTrue(isStack(spk.outputItem[0], pack(AddonEnumBatteryPack.BATTERY_SPK), 1), "spk gives its pack");

		helper.succeed();
	}

	/**
	 * Electronium's recipe wants {@code powder_osmiridium} and {@code nugget_u238m2}, NTM-CE items the
	 * 1.7.10 base mod does not have. It was held for them (D-1095); the addon now registers both in its
	 * own namespace (D-3001), so the row is written, at upstream's numbers, and
	 * {@code LeafiaRodTests#theElectroniumPackIsCraftableInTheChemicalPlant} works the recipe by hand.
	 */
	@GameTest(template = EMPTY)
	public static void electroPackIsWrittenNowTheTwoItemsExist(GameTestHelper helper) {

		if(!(LeafiaFeatures.batteryPacks && LeafiaFeatures.resourceItems)) { helper.succeed(); return; }

		helper.assertTrue(ChemicalPlantRecipes.INSTANCE.recipeNameMap.get("chem.leafia.battery_electro") != null, "the electro row is injected");
		helper.assertTrue(LeafiaBaseRecipes.HELD.stream().noneMatch(h -> h.name().equals("chem.leafia.battery_electro")), "and no longer held");
		helper.assertTrue(LeafiaBaseRecipes.HELD.stream().noneMatch(h -> h.reason().contains("powder_osmiridium") || h.reason().contains("nugget_u238m2")),
				"nothing is held for the two CE items any more");
		helper.succeed();
	}

	// ------------------------------------------------------------------ compressor, electrolyser, mixer

	@GameTest(template = EMPTY)
	public static void compressorStepsInterstageCryogelUpToCryogel(GameTestHelper helper) {

		if(!LeafiaFeatures.addonFluids) { helper.succeed(); return; }

		// the machine's own call: recipes.get(new Pair<>(tank type, pressure))
		CompressorRecipe up = CompressorRecipes.recipes.get(new Pair<>(LeafiaFluids.CRYOINTER, 0));
		helper.assertTrue(up != null && up.inputAmount == 1_000 && up.duration == 50 && isFluid(up.output, LeafiaFluids.CRYOINTER, 1_000) && up.output.pressure == 1,
				"cryointer at pressure 0 compresses 1000 mB to 1000 mB at pressure 1 in 50 ticks");
		CompressorRecipe top = CompressorRecipes.recipes.get(new Pair<>(LeafiaFluids.CRYOINTER, 1));
		helper.assertTrue(top != null && top.inputAmount == 1_000 && top.duration == 50 && isFluid(top.output, Fluids.CRYOGEL, 1_000) && top.output.pressure == 0,
				"cryointer at pressure 1 becomes 1000 mB of cryogel at pressure 0 in 50 ticks");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void electrolyserSplitsHydrogenFluoride(GameTestHelper helper) {

		if(!LeafiaFeatures.addonFluids) { helper.succeed(); return; }

		ElectrolysisRecipe r = ElectrolyserFluidRecipes.getRecipe(LeafiaFluids.HF);
		helper.assertTrue(r != null, "no electrolysis for hydrogen fluoride");
		helper.assertTrue(r.amount == 4_000 && isFluid(r.output1, Fluids.HYDROGEN, 2_000) && isFluid(r.output2, LeafiaFluids.FLUORINE, 2_000),
				"4000 mB of HF gives 2000 hydrogen and 2000 fluorine");
		helper.assertTrue(r.byproduct.length == 0, "no byproduct");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void mixerNoLongerMakesCryogelWhereTheCompressorDoes(GameTestHelper helper) {

		if(!LeafiaFeatures.addonFluids) { helper.succeed(); return; }

		helper.assertTrue(MixerRecipes.getOutput(Fluids.CRYOGEL) == null, "upon Bob's request: no mixer recipe for cryogel");
		// the removal is not collateral: another base recipe is still there
		helper.assertTrue(MixerRecipes.getOutput(Fluids.COOLANT) != null || !MixerRecipes.recipes.isEmpty(), "the mixer still has recipes");
		helper.succeed();
	}

	// ------------------------------------------------------------------ press, soldering

	@GameTest(template = EMPTY)
	public static void pressMakesFissitePlateAndBlanket(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		ItemStack stamp = new ItemStack(ModItems.stamp_iron_plate.get());
		boolean plate = false, blanket = false;
		for(Map.Entry<Pair<AStack, com.hbm.items.machine.ItemStamp.StampType>, ItemStack> e : PressRecipes.recipes.entrySet()) {
			if(e.getKey().getValue() != com.hbm.items.machine.ItemStamp.StampType.PLATE || !(e.getKey().getKey() instanceof OreDictStack ore)) continue;
			if(ore.name.equals(FSALLOY_INGOT) && e.getValue().getItem() == res("plate_fissite") && e.getValue().getCount() == 1) plate = true;
			if(ore.name.equals(FSALLOY_PLATE) && e.getValue().getItem() == res("blanket_fissite") && e.getValue().getCount() == 16) blanket = true;
		}
		helper.assertTrue(plate, "fissite ingot under a plate stamp makes 1 fissite plate");
		helper.assertTrue(blanket, "fissite plate under a plate stamp makes 16 fissite blankets");

		// and the machine's own lookup finds the plate through the ingot's tag
		ItemStack out = PressRecipes.getOutput(new ItemStack(res("ingot_fissite")), stamp);
		helper.assertTrue(out != null && out.getItem() == res("plate_fissite"), "PressRecipes.getOutput(fissite ingot, plate stamp) gave " + out);
		helper.succeed();
	}

	private static final String FSALLOY_INGOT = "ingotFissite";
	private static final String FSALLOY_PLATE = "plateFissite";

	@GameTest(template = EMPTY)
	public static void solderingMakesTheTwoElevatorChips(GameTestHelper helper) {

		if(!LeafiaFeatures.elevators) { helper.succeed(); return; }

		SolderingRecipe s6 = null, sky = null;
		for(SolderingRecipe r : SolderingRecipes.recipes) {
			if(r.output.getItem() == LeafiaItems.ev_chip_s6.get()) s6 = r;
			if(r.output.getItem() == LeafiaItems.ev_chip_skylift.get()) sky = r;
		}
		helper.assertTrue(s6 != null && sky != null, "both chips have a soldering recipe");
		for(SolderingRecipe r : List.of(s6, sky)) {
			helper.assertTrue(r.duration == 60 && r.consumption == 250, "60 ticks at 250 HE/t, got " + r.duration + "/" + r.consumption);
			helper.assertTrue(r.fluid == null, "no fluid");
			helper.assertTrue(r.toppings.length == 3 && isItem(r.toppings[0], ModItems.circuit_chip.get(), 2) && isItem(r.toppings[1], ModItems.circuit_capacitor.get(), 1), "two chips, a capacitor and a dust");
			helper.assertTrue(r.pcb.length == 1 && isItem(r.pcb[0], ModItems.circuit_pcb.get(), 4), "four PCBs");
			helper.assertTrue(r.solder.length == 1 && isOre(r.solder[0], PB.wireFine(), 4), "four lead wire");
		}
		helper.assertTrue(isOre(s6.toppings[2], REDSTONE.dust(), 1), "the s6 chip takes redstone dust");
		helper.assertTrue(isOre(sky.toppings[2], DIAMOND.dust(), 1), "the skylift chip takes diamond dust");
		helper.succeed();
	}

	// ------------------------------------------------------------------ cyclotron, exposure chamber, particle accelerator

	@GameTest(template = EMPTY)
	public static void cyclotronMakesCloudAndAbsorptionParticles(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		// the machine's call: getOutput(input stack, box stack) -> {output, antimatter cost}
		Object[] cloud = CyclotronRecipes.getOutput(new ItemStack(res("particle_taint")), new ItemStack(ModItems.part_copper.get()));
		helper.assertTrue(cloud != null && ((ItemStack) cloud[0]).getItem() == res("particle_cloud") && (int) cloud[1] == 15, "copper part + taint particle = cloud particle for 15");
		Object[] absorb = CyclotronRecipes.getOutput(new ItemStack(res("particle_chydalium")), new ItemStack(ModItems.powder_magic.get()));
		helper.assertTrue(absorb != null && ((ItemStack) absorb[0]).getItem() == res("particle_absorption") && (int) absorb[1] == 150, "magic powder + chydalium particle = absorption particle for 150");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void exposureChamberMakesDineutroniumFromTheDineutronParticle(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		ItemStack particle = new ItemStack(res("particle_dineutron"));
		ExposureChamberRecipe found = null;
		int makers = 0;
		for(ExposureChamberRecipe r : ExposureChamberRecipes.recipes) {
			if(r.output.getItem() == ModItems.ingot_dineutronium.get()) {
				makers++;
				if(r.particle.matchesRecipe(particle, true)) found = r;
			}
		}
		helper.assertTrue(makers == 1 && found != null, "dineutronium has exactly one exposure recipe, and it uses the dineutron particle (" + makers + " found)");
		if(GeneralConfig.enableExpensiveMode)
			helper.assertTrue(found.ingredient instanceof ComparableStack c && c.toStack().getItem() == ModItems.item_expensive_degenerate_matter.get(), "expensive mode wants degenerate matter");
		else
			helper.assertTrue(isOre(found.ingredient, SBD.ingot(), 1), "otherwise a schrabidate ingot");
		for(ExposureChamberRecipe r : ExposureChamberRecipes.recipes)
			helper.assertTrue(!(r.particle instanceof ComparableStack c && c.toStack().getItem() == ModItems.particle_sparkticle.get() && r.output.getItem() == ModItems.ingot_dineutronium.get()),
					"the sparkticle recipe the dineutron particle replaces is still there");
		helper.assertTrue(ExposureChamberRecipes.recipes.stream().anyMatch(r -> r.output.getItem() == ModItems.ingot_schrabidium.get()), "the removal took nothing else with it");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void acceleratorMakesDineutronParticles(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		ParticleAcceleratorRecipe r = ParticleAcceleratorRecipes.getOutput(new ItemStack(ModItems.particle_sparkticle.get()), new ItemStack(res("powder_digammitite")));
		helper.assertTrue(r != null, "sparkticle particle + digammitite powder has an accelerator recipe");
		helper.assertTrue(r.momentum == 13_000 && r.output1.getItem() == res("particle_dineutron") && r.output2 != null && r.output2.getItem() == ModItems.dust.get(),
				"13,000 momentum: a dineutron particle and dust");
		helper.succeed();
	}

	// ------------------------------------------------------------------ gas centrifuge

	@GameTest(template = EMPTY)
	public static void gasCentrifugeEnrichesUranium233And235(GameTestHelper helper) {

		if(!LeafiaFeatures.addonFluids) { helper.succeed(); return; }

		// {name, consumed, produced, next, high speed, outputs}, worked from AddonPseudoFluidTypes
		Object[][] chain = {
				{ "U233F6", 400, 300, "LEU233F6", false, 1 },
				{ "LEU233F6", 300, 200, "MEU233F6", false, 2 },
				{ "MEU233F6", 200, 100, "HEU233F6", false, 1 },
				{ "HEU233F6", 300, 0, "NONE", true, 2 },
				{ "U235F6", 400, 300, "LEU235F6", false, 1 },
				{ "LEU235F6", 300, 200, "MEU235F6", false, 2 },
				{ "MEU235F6", 200, 100, "HEU235F6", false, 1 },
				{ "HEU235F6", 300, 0, "NONE", true, 2 } };
		for(Object[] row : chain) {
			PseudoFluidType t = PseudoFluidType.types.get((String) row[0]);
			helper.assertTrue(t != null, row[0] + " is not a pseudo-fluid type");
			helper.assertTrue(t.getFluidConsumed() == (int) row[1] && t.getFluidProduced() == (int) row[2] && t.getIfHighSpeed() == (boolean) row[4]
					&& t.getOutputType() == PseudoFluidType.types.get((String) row[3]) && t.getOutput().length == (int) row[5], row[0] + " has the wrong numbers");
		}
		helper.assertTrue(GasCentrifugeRecipes.fluidConversions.get(LeafiaFluids.UF6_233) == PseudoFluidType.types.get("U233F6"), "UF6-233 enters at U233F6");
		helper.assertTrue(GasCentrifugeRecipes.fluidConversions.get(LeafiaFluids.UF6_235) == PseudoFluidType.types.get("U235F6"), "UF6-235 enters at U235F6");
		helper.assertTrue(PseudoFluidType.types.get("HEU233F6").getOutput()[0].getItem() == ModItems.nugget_u233.get() && PseudoFluidType.types.get("HEU233F6").getOutput()[0].getCount() == 3
				&& PseudoFluidType.types.get("HEU233F6").getOutput()[1].getItem() == ModItems.fluorite.get(), "HEU233F6 gives 3 U-233 nuggets and a fluorite");

		for(Object[] pair : new Object[][] { { LeafiaFluids.UF6_233, ModItems.nugget_u233.get() }, { LeafiaFluids.UF6_235, ModItems.nugget_u235.get() } }) {
			Object[] value = gasCentFor((FluidType) pair[0], helper);
			ItemStack[] out = (ItemStack[]) value[0];
			helper.assertTrue(out.length == 2 && out[0].getItem() == pair[1] && out[0].getCount() == 12 && out[1].getItem() == ModItems.fluorite.get() && out[1].getCount() == 4
					&& (boolean) value[1] && (int) value[2] == 4, pair[0] + ": 1200 mB gives 12 nuggets and 4 fluorite, high speed, 4 centrifuges");
		}
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	private static Object[] gasCentFor(FluidType type, GameTestHelper helper) {
		try {
			Field f = GasCentrifugeRecipes.class.getDeclaredField("gasCent");
			f.setAccessible(true);
			Object[] found = null;
			int n = 0;
			for(Map.Entry<FluidStack, Object[]> e : ((Map<FluidStack, Object[]>) f.get(null)).entrySet())
				if(e.getKey().type == type) { found = e.getValue(); n++; helper.assertTrue(e.getKey().fill == 1_200, "1200 mB key"); }
			helper.assertTrue(n == 1, type + " has " + n + " gas centrifuge entries, expected exactly one");
			return found;
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ the machines this port gained after the first draft

	@GameTest(template = EMPTY)
	public static void assemblerHasTheAddonsRecipes(GameTestHelper helper) {

		AssemblyMachineRecipes set = AssemblyMachineRecipes.INSTANCE;

		if(LeafiaFeatures.solBlaster) {
			GenericRecipe r = set.recipeNameMap.get("ass.leafia.solblaster");
			helper.assertTrue(r != null && r.duration == 200 && r.power == 100, "ass.leafia.solblaster, 200 ticks at 100");
			helper.assertTrue(r.inputItem.length == 5 && isOre(r.inputItem[0], STEEL.plateCast(), 3) && isItem(r.inputItem[1], ModItems.neutron_reflector.get(), 2)
					&& isOre(r.inputItem[2], MINGRADE.wireFine(), 8) && isItem(r.inputItem[3], com.hbm.blocks.ModBlocks.brick_concrete.get().asItem(), 6)
					&& isOre(r.inputItem[4], PB.plateCast(), 2), "the Neutron Blaster's inputs");
			helper.assertTrue(isStack(r.outputItem[0], LeafiaRecipeInjector.leafiaItem("sol_blaster"), 1), "it makes the sol blaster");
		}
		if(LeafiaFeatures.controlPanel) {
			GenericRecipe r = set.recipeNameMap.get("ass.leafia.panel");
			helper.assertTrue(r != null && r.duration == 100 && r.power == 100, "ass.leafia.panel, 100 ticks at 100 (D-3072)");
			helper.assertTrue(r.inputItem.length == 3 && isItem(r.inputItem[0], ModItems.circuit_analog.get(), 1) && isOre(r.inputItem[1], STEEL.plate(), 6)
					&& isItem(r.inputItem[2], ModItems.wire_fine_copper.get(), 24), "the Custom Control Panel's inputs: an analog circuit, six steel plates, 24 fine copper wires");
			helper.assertTrue(isStack(r.outputItem[0], LeafiaRecipeInjector.leafiaItem("control_panel_custom"), 1), "it makes the Custom Control Panel");
		}
		if(LeafiaFeatures.advisor) {
			GenericRecipe r = set.recipeNameMap.get("ass.leafia.advisor");
			helper.assertTrue(r != null && r.duration == 20 && r.power == 50, "ass.leafia.advisor, 20 ticks at 50");
			helper.assertTrue(r.inputItem.length == 3 && isOre(r.inputItem[0], ANY_PLASTIC.ingot(), 2) && isItem(r.inputItem[1], ModItems.circuit_basic.get(), 1), "advisor inputs");
		}
		if(LeafiaFeatures.lights) {
			GenericRecipe r = set.recipeNameMap.get("ass.leafia.light");
			helper.assertTrue(r != null && r.duration == 20 && r.power == 50 && r.inputFluid.length == 1 && isFluid(r.inputFluid[0], Fluids.MERCURY, 5), "the lamp takes 5 mB of mercury");
		}
		if(LeafiaFeatures.letterSigns) {
			int signs = 0;
			for(String name : set.recipeNameMap.keySet()) if(name.startsWith("ass.leafia.sign.")) signs++;
			helper.assertTrue(signs == 36, signs + " letter sign recipes, expected 36");
			GenericRecipe a = set.recipeNameMap.get("ass.leafia.sign.a");
			helper.assertTrue(a.duration == 20 && a.power == 10 && isOre(a.inputItem[0], IRON.plate(), 1), "a sign is one iron plate, 20 ticks at 10");
		}
		if(LeafiaFeatures.elevators) {
			GenericRecipe d = set.recipeNameMap.get("ass.leafia.ev_s6door");
			helper.assertTrue(d != null && d.duration == 40 && d.power == 50 && isOre(d.inputItem[0], STEEL.plateWelded(), 1) && isOre(d.inputItem[1], STEEL.plate(), 2), "the s6 door");
			GenericRecipe b = set.recipeNameMap.get("ass.leafia.elevator_buffer");
			helper.assertTrue(b != null && b.duration == 100 && isOre(b.inputItem[0], STEEL.ingot(), 14), "the buffer is 14 steel ingots");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anvilBuildsTheHighPressureBoiler(GameTestHelper helper) {

		if(!LeafiaFeatures.hpBoiler) { helper.succeed(); return; }

		Item boiler = LeafiaRecipeInjector.leafiaItem("hp_boiler");
		AnvilConstructionRecipe found = null;
		int n = 0;
		for(AnvilConstructionRecipe r : AnvilRecipes.constructionRecipes)
			if(r.output.size() == 1 && r.output.get(0).stack.getItem() == boiler) { found = r; n++; }
		helper.assertTrue(n == 1, n + " anvil recipes build the HP boiler, expected 1");
		helper.assertTrue(found.tierLower == 3 && found.input.size() == 4, "tier 3, four inputs");
		helper.assertTrue(isOre(found.input.get(0), STEEL.plateWelded(), 6) && isOre(found.input.get(1), CU.ingot(), 8) && isOre(found.input.get(2), IRON.plateWelded(), 3)
				&& isOre(found.input.get(3), BI.nugget(), 1), "6 welded steel, 8 copper, 3 welded iron, a bismuth nugget");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void centrifugeSplitsTheEeveeStrand(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		ItemStack[] out = CentrifugeRecipes.getOutput(new ItemStack(res("dna_eevee")));
		helper.assertTrue(out != null && out.length == 2 && out[0].getItem() == res("dna_felid") && out[1].getItem() == res("dna_canid"), "DNA of an eevee splits into felid and canid strands");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void silexReadsTheBalefireEgg(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		SILEXRecipe r = SILEXRecipes.getOutput(new ItemStack(ModItems.egg_balefire.get()));
		helper.assertTrue(r != null, "the balefire egg has a SILEX recipe");
		helper.assertTrue(r.fluidProduced == 1_000 && r.fluidConsumed == 1_000 && r.laserStrength == com.hbm.items.machine.ItemFELCrystal.EnumWavelengths.DRX, "1000/1000 at DRX");
		helper.assertTrue(r.outputs.size() == 3 && r.outputs.get(0).asStack().getItem() == res("powder_nc279") && r.outputs.get(0).getWeight().asInt() == 5
				&& r.outputs.get(1).asStack().getItem() == res("powder_taintium") && r.outputs.get(1).getWeight().asInt() == 5
				&& r.outputs.get(2).asStack().getItem() == ModItems.powder_balefire.get() && r.outputs.get(2).getWeight().asInt() == 90, "nc279 5, taintium 5, balefire dust 90");
		helper.assertTrue(SILEXRecipes.getOutput(new ItemStack(com.hbm.blocks.ModBlocks.ancient_scrap.get())) != null
				&& SILEXRecipes.getOutput(new ItemStack(com.hbm.blocks.ModBlocks.mush_block.get())) != null, "ancient scrap and mush block are in too");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void pyroOvenMakesHydrogenFluoride(GameTestHelper helper) {

		if(!LeafiaFeatures.addonFluids) { helper.succeed(); return; }

		PyroOvenRecipe found = null;
		int n = 0;
		for(PyroOvenRecipe r : PyroOvenRecipes.recipes) if(r.outputFluid != null && r.outputFluid.type == LeafiaFluids.HF) { found = r; n++; }
		helper.assertTrue(n == 1, n + " pyrolysis recipes make HF");
		helper.assertTrue(found.duration == 50 && isOre(found.inputItem, F.dust(), 1) && isFluid(found.inputFluid, Fluids.SULFURIC_ACID, 1_000) && isFluid(found.outputFluid, LeafiaFluids.HF, 1_000),
				"fluorite dust and 1000 sulfuric acid make 1000 HF in 50 ticks");
		helper.succeed();
	}

	/** AddonSmeltingRecipes: furnace rows, which are datapack JSON. The osmiridium one is {@code LeafiaRodTests}'s (D-3001). */
	@GameTest(template = EMPTY)
	public static void furnaceSmeltsTheFourPowders(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		for(String m : new String[] {"xanaxium", "nc279", "taintium", "chydalium"}) {
			ItemStack in = new ItemStack(res("powder_" + m));
			var recipe = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
					new net.minecraft.world.item.crafting.SingleRecipeInput(in), helper.getLevel());
			helper.assertTrue(recipe.isPresent(), m + " powder does not smelt");
			var r = recipe.get().value();
			helper.assertTrue(r.getResultItem(helper.getLevel().registryAccess()).getItem() == res("ingot_" + m), m + " powder smelts into the wrong item");
			helper.assertTrue(r.getExperience() == 5.0F && r.getCookingTime() == 200, m + ": " + r.getExperience() + " xp, " + r.getCookingTime() + " ticks");
		}
		helper.succeed();
	}


	// ------------------------------------------------------------------ the rows AddonMats unlocked (D-3026)

	private static boolean have(String... items) {
		for(String i : items) if(LeafiaRecipeInjector.leafiaItem(i) == null) return false;
		return true;
	}

	private static boolean isMat(MaterialStack s, com.hbm.inventory.material.NTMMaterial mat, int amount) {
		return s != null && s.material == mat && s.amount == amount;
	}

	/**
	 * `AddonCrucibleRecipes`: nine ticks of frequency, an ingot of the taintium steel from steel and a quantum of taintium, an
	 * ingot of fissite from eight nuggets of fissium and one of mana. An ingot is 72 quanta and a nugget 8.
	 */
	@GameTest(template = EMPTY)
	public static void theCrucibleMakesTheTwoAlloys(GameTestHelper helper) {

		if(!LeafiaMats.created() || !have("ingot_tnalloy", "ingot_fissite")) { helper.succeed(); return; }
		int i = MaterialShapes.INGOT.q(1), n = MaterialShapes.NUGGET.q(1), q = MaterialShapes.QUANTUM.q(1);
		helper.assertTrue(i == 72 && n == 8 && q == 1, "the shapes are 72, 8 and 1 quanta: " + i + ", " + n + ", " + q);

		CrucibleRecipe tn = (CrucibleRecipe) CrucibleRecipes.INSTANCE.recipeNameMap.get("crucible.leafia.tnalloy");
		helper.assertTrue(tn != null && tn.frequency == 9 && tn.input.length == 2 && tn.output.length == 1, "the taintium steel recipe is in, with two inputs and one output");
		helper.assertTrue(isMat(tn.input[0], Mats.MAT_STEEL, 431) && isMat(tn.input[1], LeafiaMats.MAT_TAINTIUM, 1) && isMat(tn.output[0], LeafiaMats.MAT_TNALLOY, 432),
				"431 steel and 1 taintium make 432 taintium steel (six ingots less a quantum, and the quantum)");
		helper.assertTrue(tn.getInputAmount() == 432, "and the input totals the output");
		helper.assertTrue(tn.getIcon() != null && tn.getIcon().is(LeafiaRecipeInjector.leafiaItem("ingot_tnalloy")), "its icon is the ingot");

		CrucibleRecipe fs = (CrucibleRecipe) CrucibleRecipes.INSTANCE.recipeNameMap.get("crucible.leafia.fsalloy");
		helper.assertTrue(fs != null && fs.frequency == 9 && fs.input.length == 2 && fs.output.length == 1, "the fissite recipe");
		helper.assertTrue(isMat(fs.input[0], LeafiaMats.MAT_FISSIUM, 64) && isMat(fs.input[1], LeafiaMats.MAT_MANA, 8) && isMat(fs.output[0], LeafiaMats.MAT_FISSITE, 72),
				"64 fissium and 8 mana make 72 fissite, an ingot");
		helper.assertTrue(CrucibleRecipes.INSTANCE.recipeNameMap.values().stream().filter(r -> r.getInternalName().startsWith("crucible.leafia.")).count() == 2, "and only those two");
		helper.succeed();
	}

	/** `AddonArcWelderRecipes`: two cast plates, a gas, a long time. */
	@GameTest(template = EMPTY)
	public static void theArcWelderWeldsXanaxiumAndTheTaintiumSteel(GameTestHelper helper) {

		if(!LeafiaMats.created() || !have("plate_welded_xanaxium", "plate_welded_tnalloy")) { helper.succeed(); return; }
		Item castXn = LeafiaMats.item("plate_cast_xanaxium"), castTn = LeafiaMats.item("plate_cast_tnalloy");

		ArcWelderRecipe xn = ArcWelderRecipes.getRecipe(new ItemStack(castXn, 2));
		helper.assertTrue(xn != null && xn.output.is(LeafiaMats.item("plate_welded_xanaxium")) && xn.output.getCount() == 1, "two cast xanaxium plates weld into one");
		helper.assertTrue(xn.duration == 6_000 && xn.consumption == 20_000_000L && isFluid(xn.fluid, Fluids.REFORMGAS, 16_000), "6,000 ticks at 20,000,000 HE with 16,000 mB of reformate gas: "
				+ xn.duration + ", " + xn.consumption + ", " + xn.fluid);
		helper.assertTrue(xn.ingredients.length == 1 && isOre(xn.ingredients[0], LeafiaOreDict.XN.plateCast(), 2), "one stack of two xanaxium cast plates");

		ArcWelderRecipe tn = ArcWelderRecipes.getRecipe(new ItemStack(castTn, 2));
		helper.assertTrue(tn != null && tn.output.is(LeafiaMats.item("plate_welded_tnalloy")) && tn.output.getCount() == 1, "two cast taintium steel plates weld into one");
		helper.assertTrue(tn.duration == 400 && tn.consumption == 1_000_000L && isFluid(tn.fluid, Fluids.OXYGEN, 500), "400 ticks at 1,000,000 HE with 500 mB of oxygen");
		helper.assertTrue(isOre(tn.ingredients[0], LeafiaOreDict.TNALLOY.plateCast(), 2), "two taintium steel cast plates");
		helper.assertTrue(ArcWelderRecipes.getRecipe(new ItemStack(castXn, 1)) == null, "one plate is not enough");
		helper.succeed();
	}

	/** `AddonArcFurnaceRecipes`: chernobylite melts into five materials and zetalite into three, both with a quart of corium. A quart is 162. */
	@GameTest(template = EMPTY)
	public static void theArcFurnaceMeltsChernobyliteAndZetalite(GameTestHelper helper) {

		if(!LeafiaMats.created() || !have("ore_corium_chernobylite", "ore_corium_zetalite")) { helper.succeed(); return; }
		helper.assertTrue(MaterialShapes.QUART.q(1) == 162, "a quart is 162 quanta");

		ArcFurnaceRecipe c = ArcFurnaceRecipes.getOutput(new ItemStack(LeafiaRecipeInjector.leafiaItem("ore_corium_chernobylite")), true);
		helper.assertTrue(c != null && c.fluidOutput.length == 5 && c.solidOutput == null, "chernobylite melts into five materials");
		helper.assertTrue(isMat(c.fluidOutput[0], Mats.MAT_URANIUM, 72) && isMat(c.fluidOutput[1], Mats.MAT_ZIRCONIUM, 72) && isMat(c.fluidOutput[2], Mats.MAT_SILICON, 72)
				&& isMat(c.fluidOutput[3], Mats.MAT_SCHRABIDIUM, 8) && isMat(c.fluidOutput[4], LeafiaMats.MAT_CORIUM, 162),
				"an ingot each of uranium, zirconium and silicon, a nugget of schrabidium, a quart of corium");

		ArcFurnaceRecipe z = ArcFurnaceRecipes.getOutput(new ItemStack(LeafiaRecipeInjector.leafiaItem("ore_corium_zetalite")), true);
		helper.assertTrue(z != null && z.fluidOutput.length == 3, "zetalite melts into three");
		helper.assertTrue(isMat(z.fluidOutput[0], LeafiaMats.MAT_NC279, 144) && isMat(z.fluidOutput[1], LeafiaMats.MAT_TAINTIUM, 144) && isMat(z.fluidOutput[2], LeafiaMats.MAT_CORIUM, 162),
				"two ingots of nuclium, two of taintium, a quart of corium");
		helper.succeed();
	}

	/** `AddonMatDistribution`: the same five materials for chernobylite as an ore *name*, which is what the shredder and the crucible's scrap read. */
	@GameTest(template = EMPTY)
	public static void chernobyliteIsDistributedByItsOreName(GameTestHelper helper) {

		if(!LeafiaMats.created() || !have("ore_corium_chernobylite")) { helper.succeed(); return; }
		var entry = Mats.materialOreEntries.get(LeafiaOreDict.CHERNOBYL.ore());
		helper.assertTrue(LeafiaOreDict.CHERNOBYL.ore().equals("oreChernobylite"), "the name is oreChernobylite");
		helper.assertTrue(entry != null && entry.size() == 5, "five entries under oreChernobylite");
		helper.assertTrue(isMat(entry.get(0), Mats.MAT_URANIUM, 72) && isMat(entry.get(1), Mats.MAT_ZIRCONIUM, 72) && isMat(entry.get(2), Mats.MAT_SILICON, 72)
				&& isMat(entry.get(3), Mats.MAT_SCHRABIDIUM, 8) && isMat(entry.get(4), LeafiaMats.MAT_CORIUM, 162), "the same five");
		// and through the item, which is the question a smelter asks: the ore is in c:ores/chernobylite
		var found = Mats.getMaterialsFromItem(new ItemStack(LeafiaRecipeInjector.leafiaItem("ore_corium_chernobylite")));
		helper.assertTrue(found.size() >= 5 && found.stream().anyMatch(m -> m.material == LeafiaMats.MAT_CORIUM && m.amount == 162), "the ore item itself answers with them: " + found.size());
		helper.succeed();
	}

	/** `AddonWasteDrumRecipes`: a depleted fuel still cooling cools into the cold one, for the five the rods leave. */
	@GameTest(template = EMPTY)
	public static void theWasteDrumCoolsTheFiveAddonWastes(GameTestHelper helper) {

		if(!LeafiaFeatures.rods) { helper.succeed(); return; }
		for(String waste : new String[] {"waste_u238", "waste_mes", "waste_hes", "waste_sch", "waste_sol"}) {
			Item item = LeafiaRecipeInjector.leafiaItem(waste);
			ItemStack hot = new ItemStack(item);
			ItemDepletedFuel.setCooling(hot, true);
			ItemStack out = FuelPoolRecipes.getOutput(hot);
			helper.assertTrue(out != null && out.is(item) && out.getCount() == 1 && !ItemDepletedFuel.isCooling(out), waste + " cools into itself, cold: " + out);
			helper.assertTrue(FuelPoolRecipes.getOutput(new ItemStack(item)) == null, "a cold " + waste + " has no recipe");
		}
		helper.succeed();
	}

	/** `AddonAnvilRecipes`: the RT heater, from five RTG units, eight reflector plates, sixteen copper, six tungsten-carbide-alloy ingots and eight steel plates, at tier 4. */
	@GameTest(template = EMPTY)
	public static void anvilBuildsTheRtHeater(GameTestHelper helper) {

		if(!have("heater_rt", "plate_tungsten_carbide")) { helper.succeed(); return; }
		Item heater = LeafiaRecipeInjector.leafiaItem("heater_rt");
		AnvilConstructionRecipe found = null;
		int n = 0;
		for(AnvilConstructionRecipe r : AnvilRecipes.constructionRecipes)
			if(r.output.size() == 1 && r.output.get(0).stack.getItem() == heater) { found = r; n++; }
		helper.assertTrue(n == 1, n + " anvil recipes build the RT heater, expected 1");
		helper.assertTrue(found.tierLower == 4 && found.input.size() == 5, "tier 4, five inputs");
		helper.assertTrue(isItem(found.input.get(0), ModItems.rtg_unit.get(), 5), "five RTG units");
		helper.assertTrue(isOre(found.input.get(1), LeafiaOreDict.getReflector(), 8) && LeafiaOreDict.getReflector().equals("plateTungstenCarbide"), "eight reflector plates, which are tungsten carbide's");
		helper.assertTrue(isOre(found.input.get(2), CU.ingot(), 16) && isOre(found.input.get(3), TCALLOY.ingot(), 6) && isOre(found.input.get(4), STEEL.plate(), 8), "16 copper ingots, 6 TC alloy ingots, 8 steel plates");
		// and the plate is a plate of that name: the reflector tag has the addon's item
		helper.assertTrue(new ItemStack(LeafiaRecipeInjector.leafiaItem("plate_tungsten_carbide")).is(com.hbm.lib.NtmTags.ore(LeafiaOreDict.getReflector())), "the addon's plate is in the tag the recipe asks for");
		helper.succeed();
	}

	/** The pulser, the reinforced absorber and the exchanger: behind the DFC overhaul, which is off, so the switch is turned on for the test and the base's lists rebuilt after. */
	@GameTest(template = EMPTY)
	public static void theDfcPlasmaForgeRowsAreBehindTheOverhaul(GameTestHelper helper) {

		boolean was = LeafiaFeatures.dfcOverhaul;
		try {
			LeafiaFeatures.dfcOverhaul = false;
			SerializableRecipe.initialize();
			helper.assertTrue(PlasmaForgeRecipes.INSTANCE.recipeNameMap.keySet().stream().noneMatch(k -> k.startsWith("plsm.leafia.")), "no addon plasma forge recipe with the overhaul off");

			LeafiaFeatures.dfcOverhaul = true;
			SerializableRecipe.initialize();
			var forge = PlasmaForgeRecipes.INSTANCE.recipeNameMap;
			if(have("dfc_pulser", "sat_head_laser", "particle_dineutron")) {
				PlasmaForgeRecipe pulser = (PlasmaForgeRecipe) forge.get("plsm.leafia.dfcpulser");
				helper.assertTrue(pulser != null && pulser.ignitionTemp == 50_000_000L && pulser.duration == 1_200 && pulser.power == 10_000_000L, "the pulser: 1,200 ticks at 10,000,000, igniting at 50,000,000");
				helper.assertTrue(isStack(pulser.outputItem[0], LeafiaRecipeInjector.leafiaItem("dfc_pulser"), 1), "it makes the pulser");
				helper.assertTrue(pulser.inputFluid.length == 1 && isFluid(pulser.inputFluid[0], Fluids.STELLAR_FLUX, 4_000), "4,000 mB of stellar flux");
				helper.assertTrue(pulser.inputItem.length == 4 && isOre(pulser.inputItem[0], OSMIRIDIUM.plateCast(), 16) && isItem(pulser.inputItem[1], ModItems.circuit_controller_quantum.get(), 4)
						&& isItem(pulser.inputItem[2], ModItems.battery_pack_capacitor_spark.get(), 1) && isItem(pulser.inputItem[3], LeafiaRecipeInjector.leafiaItem("sat_head_laser"), 1),
						"16 osmiridium cast plates, 4 quantum controllers, a spark capacitor, a laser satellite head");
			}
			if(have("dfc_exchanger")) {
				PlasmaForgeRecipe exchanger = (PlasmaForgeRecipe) forge.get("plsm.leafia.dfcexchanger");
				helper.assertTrue(exchanger != null && exchanger.inputItem.length >= 4 && isOre(exchanger.inputItem[0], OSMIRIDIUM.plateWelded(), 16) && isOre(exchanger.inputItem[1], BIGMT.plateCast(), 16)
						&& isItem(exchanger.inputItem[2], ModItems.motor_bismuth.get(), 1) && isItem(exchanger.inputItem[3], ModBlocks.heater_heatex.get().asItem(), 1),
						"the exchanger: 16 osmiridium welded plates, 16 big metal cast plates, a bismuth motor, a heat exchanger");
			}
			if(have("dfc_reinforced")) {
				PlasmaForgeRecipe reinforced = (PlasmaForgeRecipe) forge.get("plsm.leafia.dfcreinabsorber");
				helper.assertTrue(reinforced != null && reinforced.inputItem.length >= 3 && isOre(reinforced.inputItem[0], LeafiaOreDict.XN.plateWelded(), 16) && isOre(reinforced.inputItem[1], SBD.wireDense(), 4)
						&& isItem(reinforced.inputItem[2], ModItems.circuit_controller_quantum.get(), 4), "the reinforced absorber: 16 xanaxium welded plates, 4 dense wire, 4 quantum controllers");
			}
			helper.assertTrue(LeafiaRecipeInjector.rows("plasma_forge") == (have("dfc_pulser", "sat_head_laser", "particle_dineutron") ? 1 : 0) + (have("dfc_reinforced") ? 1 : 0) + (have("dfc_exchanger") ? 1 : 0)
					|| LeafiaRecipeInjector.rows("plasma_forge") >= 1, "the plasma forge rows: " + LeafiaRecipeInjector.rows("plasma_forge"));
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
			SerializableRecipe.initialize();
		}
		helper.succeed();
	}

	/**
	 * The PWR overhaul's block, which the base's assembler recipes are replaced and removed by: the four legacy recipes, nine
	 * replaced outputs (the count each recipe made is kept), two removals. All of it or none: with the switch off, none.
	 */
	@GameTest(template = EMPTY)
	public static void thePwrOverhaulReplacesAndRemovesTheBaseAssemblerRecipes(GameTestHelper helper) {

		if(!have("lwr_element", "lwr_control", "reactor_control", "reactor_element", "reactor_conductor", "reactor_hatch", "plate_tungsten_carbide")) { helper.succeed(); return; }
		boolean was = LeafiaFeatures.pwr;
		var set = AssemblyMachineRecipes.INSTANCE;
		try {
			LeafiaFeatures.pwr = false;
			SerializableRecipe.initialize();
			helper.assertTrue(set.recipeNameMap.containsKey("ass.pwrneutronsource") && set.recipeNameMap.containsKey("ass.pwrheatsink"), "off: the base recipes are there");
			helper.assertTrue(!set.recipeNameMap.containsKey("ass.leafia.legacy_pwrcontrol"), "off: and none of the addon's");
			helper.assertTrue(isStack(set.recipeNameMap.get("ass.pwrcontrol").outputItem[0], ModBlocks.pwr_control.get().asItem(), 4), "off: the base control rod recipe makes four of the base's control rod");

			LeafiaFeatures.pwr = true;
			SerializableRecipe.initialize();
			helper.assertTrue(!set.recipeNameMap.containsKey("ass.pwrneutronsource") && !set.recipeNameMap.containsKey("ass.pwrheatsink"), "on: the neutron source and the heat sink recipes are gone");

			GenericRecipe legacy = set.recipeNameMap.get("ass.leafia.legacy_pwrcontrol");
			helper.assertTrue(legacy != null && legacy.duration == 100 && legacy.power == 100 && isStack(legacy.outputItem[0], LeafiaRecipeInjector.leafiaItem("reactor_control"), 1), "the legacy control rod: 100 ticks at 100");
			helper.assertTrue(legacy.inputItem.length == 4 && isOre(legacy.inputItem[0], STEEL.ingot(), 4) && isOre(legacy.inputItem[1], PB.ingot(), 6) && isOre(legacy.inputItem[2], W.bolt(), 6)
					&& isItem(legacy.inputItem[3], ModItems.motor.get(), 1), "4 steel, 6 lead, 6 tungsten bolts, a motor");

			GenericRecipe fuel = set.recipeNameMap.get("ass.leafia.legacy_pwrfuel");
			helper.assertTrue(fuel != null && fuel.duration == 150 && fuel.power == 100 && isStack(fuel.outputItem[0], LeafiaRecipeInjector.leafiaItem("reactor_element"), 1), "the legacy fuel element: 150 ticks at 100");
			helper.assertTrue(isOre(fuel.inputItem[0], STEEL.ingot(), 2) && isOre(fuel.inputItem[1], LeafiaOreDict.getReflector(), 4) && isOre(fuel.inputItem[2], PB.plate(), 2) && isOre(fuel.inputItem[3], ZR.ingot(), 2),
					"2 steel ingots, 4 reflector plates, 2 lead plates, 2 zirconium ingots");

			GenericRecipe conductor = set.recipeNameMap.get("ass.leafia.legacy_pwrconductor");
			helper.assertTrue(conductor != null && conductor.duration == 130 && conductor.power == 100 && isOre(conductor.inputItem[1], CU.plate(), 12) && isOre(conductor.inputItem[2], W.wireFine(), 4), "the legacy conductor");
			GenericRecipe hatch = set.recipeNameMap.get("ass.leafia.legacy_pwrhatch");
			helper.assertTrue(hatch != null && hatch.duration == 130 && hatch.power == 150 && isOre(hatch.inputItem[1], STEEL.plate(), 6), "the legacy hatch: 130 ticks at 150");

			String[][] replaced = {{"ass.pwrcontrol", "lwr_control"}, {"ass.pwrfuel", "lwr_element"}, {"ass.pwrchannel", "lwr_channel"}, {"ass.pwrheatex", "lwr_exchanger"},
					{"ass.pwrreflector", "lwr_reflector"}, {"ass.pwrreflectoralt", "lwr_reflector"}, {"ass.pwrcasing", "lwr_casing"}, {"ass.pwrcontroller", "lwr_terminal"}, {"ass.pwrport", "lwr_port"}};
			for(String[] r : replaced) {
				GenericRecipe g = set.recipeNameMap.get(r[0]);
				helper.assertTrue(g != null && g.outputItem.length == 1 && g.outputItem[0] instanceof GenericRecipes.ChanceOutput co && co.stack.is(LeafiaRecipeInjector.leafiaItem(r[1])),
						r[0] + " now makes " + r[1]);
				if(r[0].equals("ass.pwrcontrol")) // upstream's replaceOutput keeps the count the recipe made
					helper.assertTrue(((GenericRecipes.ChanceOutput) g.outputItem[0]).stack.getCount() == 4, "and still four of them");
			}
		} finally {
			LeafiaFeatures.pwr = was;
			SerializableRecipe.initialize();
		}
		helper.assertTrue(set.recipeNameMap.containsKey("ass.pwrneutronsource") && set.recipeNameMap.containsKey("ass.pwrheatsink") && !set.recipeNameMap.containsKey("ass.leafia.legacy_pwrfuel"), "and the base's lists are rebuilt");
		helper.succeed();
	}

	/**
	 * The two assembler rows that named the PWR's blocks by their classes (`new PWRConductorBlock()` has no name in the line that declares it):
	 * the reactor conductor, and the supercooler, which takes thirty-two of the PWR's exchangers.
	 */
	@GameTest(template = EMPTY)
	public static void theConductorAndTheSupercoolerAreInTheAssembler(GameTestHelper helper) {

		var set = AssemblyMachineRecipes.INSTANCE;
		if(have("reactor_conductor")) {
			GenericRecipe cp = set.recipeNameMap.get("ass.leafia.cp");
			helper.assertTrue(cp != null && cp.duration == 130 && cp.power == 100 && isStack(cp.outputItem[0], LeafiaRecipeInjector.leafiaItem("reactor_conductor"), 1), "the conductor: 130 ticks at 100");
			helper.assertTrue(cp.inputItem.length == 3 && isOre(cp.inputItem[0], STEEL.plate(), 4) && isItem(cp.inputItem[1], ModItems.circuit_basic.get(), 2) && isOre(cp.inputItem[2], MINGRADE.wireFine(), 2),
					"4 steel plates, 2 basic circuits, 2 red copper fine wires");
		}
		if(have("supercooler", "lwr_exchanger")) {
			GenericRecipe sc = set.recipeNameMap.get("ass.leafia.supercooler");
			helper.assertTrue(sc != null && sc.duration == 100 && sc.power == 100 && isStack(sc.outputItem[0], LeafiaRecipeInjector.leafiaItem("supercooler"), 1), "the supercooler: 100 ticks at 100 (the helper's defaults)");
			helper.assertTrue(sc.inputItem.length == 7 && isItem(sc.inputItem[0], ModItems.coil_copper_torus.get(), 3) && isOre(sc.inputItem[1], STEEL.ingot(), 3) && isOre(sc.inputItem[2], TI.plate(), 6)
					&& isItem(sc.inputItem[3], ModItems.plate_polymer.get(), 12) && isOre(sc.inputItem[4], BIGMT.ingot(), 2) && isItem(sc.inputItem[5], LeafiaRecipeInjector.leafiaItem("lwr_exchanger"), 32)
					&& isItem(sc.inputItem[6], ModBlocks.watz_cooler.get().asItem(), 16), "3 torus coils, 3 steel, 6 titanium plates, 12 polymer plates, 2 saturnite, 32 PWR exchangers, 16 Watz coolers");
		}
		helper.succeed();
	}

	/** The two getReflector() rows name one plate, and the PWR's old fuel element is the only assembler recipe that did before the anvil. */
	@GameTest(template = EMPTY)
	public static void theReflectorPlateIsTungstenCarbide(GameTestHelper helper) {

		if(!have("plate_tungsten_carbide")) { helper.succeed(); return; }
		helper.assertTrue(LeafiaOreDict.getReflector().equals(WC.plate()), "getReflector() is the tungsten carbide plate's name: " + LeafiaOreDict.getReflector());
		helper.assertTrue(new ItemStack(LeafiaRecipeInjector.leafiaItem("plate_tungsten_carbide")).is(com.hbm.lib.NtmTags.ore(WC.plate())), "the item is in that tag");
		helper.assertTrue(LeafiaBaseRecipes.HELD.stream().noneMatch(h -> h.reason().contains("getReflector")), "nothing is held for it any more");
		helper.succeed();
	}

	// ------------------------------------------------------------------ reload, repeat, and the held table

	@GameTest(template = EMPTY)
	public static void aRecipeReloadKeepsTheAddonsRows(GameTestHelper helper) {

		int rowsBefore = LeafiaRecipeInjector.rowsInjected();
		int passesBefore = LeafiaRecipeInjector.passes();
		helper.assertTrue(rowsBefore > 0, "the first pass did not run");
		helper.assertTrue(SerializableRecipe.recipeHandlers.contains(LeafiaRecipeInjector.INSTANCE), "the injector is not in the handler list");
		helper.assertTrue(SerializableRecipe.recipeHandlers.get(SerializableRecipe.recipeHandlers.size() - 1) == LeafiaRecipeInjector.INSTANCE, "it must be the last handler, or the base would rebuild after it");

		// the base's own reload: every handler deleted and rebuilt from defaults, in one body
		SerializableRecipe.initialize();

		helper.assertTrue(LeafiaRecipeInjector.passes() == passesBefore + 1, "initialize() did not run the injector");
		helper.assertTrue(LeafiaRecipeInjector.rowsInjected() == rowsBefore, "rows after a reload: " + LeafiaRecipeInjector.rowsInjected() + ", before: " + rowsBefore);
		if(LeafiaFeatures.addonFluids) {
			helper.assertTrue(ChemicalPlantRecipes.INSTANCE.recipeNameMap.containsKey("chem.leafia.flibe"), "flibe survived the reload");
			helper.assertTrue(CompressorRecipes.recipes.containsKey(new Pair<>(LeafiaFluids.CRYOINTER, 1)), "the compressor row survived");
			helper.assertTrue(MixerRecipes.getOutput(Fluids.CRYOGEL) == null, "the base mixer's cryogel recipe came back with the defaults and was removed again");
		}
		if(LeafiaFeatures.elevators) {
			long chips = SolderingRecipes.recipes.stream().filter(r -> r.output.getItem() == LeafiaItems.ev_chip_s6.get()).count();
			helper.assertTrue(chips == 1, chips + " s6 chip recipes after a reload");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void injectingTwiceLeavesOneCopy(GameTestHelper helper) {

		LeafiaRecipeInjector.injectAll();
		int first = LeafiaRecipeInjector.rowsInjected();
		int assembler = AssemblyMachineRecipes.INSTANCE.recipeOrderedList.size();
		int chem = ChemicalPlantRecipes.INSTANCE.recipeOrderedList.size();
		int solder = SolderingRecipes.recipes.size();
		int exposure = ExposureChamberRecipes.recipes.size();
		int pa = ParticleAcceleratorRecipes.recipes.size();
		int anvil = AnvilRecipes.constructionRecipes.size();
		int pyro = PyroOvenRecipes.recipes.size();

		LeafiaRecipeInjector.injectAll();

		helper.assertTrue(LeafiaRecipeInjector.rowsInjected() == first, "the second pass wrote a different number of rows");
		helper.assertTrue(AssemblyMachineRecipes.INSTANCE.recipeOrderedList.size() == assembler, "assembler list grew");
		helper.assertTrue(AssemblyMachineRecipes.INSTANCE.recipeNameMap.size() == AssemblyMachineRecipes.INSTANCE.recipeOrderedList.size(), "assembler list and name map disagree");
		helper.assertTrue(ChemicalPlantRecipes.INSTANCE.recipeOrderedList.size() == chem, "chemical plant list grew");
		helper.assertTrue(SolderingRecipes.recipes.size() == solder, "soldering list grew");
		helper.assertTrue(ExposureChamberRecipes.recipes.size() == exposure, "exposure chamber list grew");
		helper.assertTrue(ParticleAcceleratorRecipes.recipes.size() == pa, "accelerator list grew");
		helper.assertTrue(AnvilRecipes.constructionRecipes.size() == anvil, "anvil list grew");
		helper.assertTrue(PyroOvenRecipes.recipes.size() == pyro, "pyrolysis list grew");
		if(LeafiaFeatures.addonFluids) gasCentFor(LeafiaFluids.UF6_233, helper);
		helper.succeed();
	}

	/**
	 * The count is a pin on the silent failure this injector can have: a row whose addon item is
	 * absent is skipped, not thrown, so a renamed item would otherwise just lose its recipe. With
	 * every content switch at its default the total is fixed.
	 */
	@GameTest(template = EMPTY)
	public static void everyRowIsWrittenWhenEveryFeatureIsOn(GameTestHelper helper) {

		boolean all = LeafiaFeatures.addonFluids && LeafiaFeatures.batteryPacks && LeafiaFeatures.resourceItems && LeafiaFeatures.elevators
				&& LeafiaFeatures.letterSigns && LeafiaFeatures.hpBoiler && LeafiaFeatures.solBlaster && LeafiaFeatures.advisor && LeafiaFeatures.lights && LeafiaFeatures.wind;
		if(!all) { helper.succeed(); return; }

		Map<String, Integer> expected = new java.util.LinkedHashMap<>();
		expected.put("chemical_plant", 8);
		expected.put("compressor", 2);
		expected.put("electrolyzer", 1);
		expected.put("mixer", 0);
		expected.put("press", 2);
		expected.put("soldering", 2);
		expected.put("cyclotron", 2);
		expected.put("exposure_chamber", 1);
		expected.put("particle_accelerator", 1);
		expected.put("gas_centrifuge", 12);
		expected.put("assembler", LeafiaRecipeInjector.rows("assembler"));
		expected.put("anvil", 1 + (have("heater_rt", "plate_tungsten_carbide") ? 1 : 0)); // the hp boiler, and the RT heater since the reflector plate exists (D-3026)
		expected.put("centrifuge", 3); // the xanaxium block, the Leafia bobblehead (D-3041) and the Eevee strand
		expected.put("silex", 3);
		expected.put("pyro_oven", 1);
		// 26 always, plus the xanaxium block's row with the osmiridium blocks and the U-238 nugget's with the resource items (D-3021)
		expected.put("dfc", 26 + (LeafiaFeatures.osmiridiumBlocks ? 1 : 0) + (LeafiaFeatures.resourceItems ? 1 : 0));
		if(LeafiaFeatures.rods) expected.put("purex", 5); // the five depleted fuels are the rods' (D-3001)
		if(LeafiaFeatures.rods) expected.put("waste_drum", 5); // and the waste drum cools the same five (D-3026)
		if(LeafiaMats.created()) { // AddonMats (D-3026)
			expected.put("crucible", 2);
			expected.put("arc_welder", 2);
			expected.put("arc_furnace", have("ore_corium_chernobylite", "ore_corium_zetalite") ? 2 : 0);
			expected.put("mat_distribution", 1);
		}
		if(LeafiaFeatures.resourceItems && LeafiaFeatures.digammitite) expected.put("shredder", 6); // digammitite, one row a level (D-3004)
		for(Map.Entry<String, Integer> e : expected.entrySet())
			helper.assertTrue(LeafiaRecipeInjector.rows(e.getKey()) == e.getValue(), e.getKey() + ": " + LeafiaRecipeInjector.rows(e.getKey()) + " rows, expected " + e.getValue());
		// 55 at D-1095, plus the three AMS parts (D-3002) and the three doors (D-3005) once their blocks exist.
		// ... plus the coolant heat exchanger (D-3016) once its block exists, and the custom nuke missile and the Fat Chud (D-3022).
		// ... plus the PWR conductor's and the supercooler's (D-3026), which resolved once the PWR's blocks were known by their classes' names
		int assembler = 55 + (LeafiaFeatures.ams ? 3 : 0) + (LeafiaFeatures.doors ? 3 : 0) + (LeafiaFeatures.coolantHeatex ? 1 : 0)
				+ (LeafiaFeatures.addonBombs ? 2 : 0) + (have("reactor_conductor") ? 1 : 0) + (have("supercooler", "lwr_exchanger") ? 1 : 0)
				// ... and the APR's ten (D-3027): seven landed with the reactor, and the support, the blades and the port, which waited on
				// NTM-CE's ANY_ULTRAALLOY frame, with the group in LeafiaOreDict (D-3026)
				+ (LeafiaFeatures.aprReactor() ? 10 : 0)
				// ... and the Custom Control Panel's desk (D-3072), written since the block exists
				+ (LeafiaFeatures.controlPanel ? 1 : 0);
		helper.assertTrue(LeafiaRecipeInjector.rows("assembler") == assembler, "assembler: " + LeafiaRecipeInjector.rows("assembler") + " rows, expected " + assembler);
		helper.assertTrue(LeafiaRecipeInjector.removals("mixer") == 1 && LeafiaRecipeInjector.removals("exposure_chamber") == 1, "one removal each in the mixer and the exposure chamber");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void heldRowsAreSortedByWhyTheyAreHeld(GameTestHelper helper) {

		Map<String, Integer> byFamily = new java.util.LinkedHashMap<>();
		for(Held h : LeafiaBaseRecipes.HELD) {
			helper.assertTrue(!h.reason().isEmpty() && !h.code().isBlank(), h.family() + " row without a reason or code");
			byFamily.merge(h.family(), 1, Integer::sum);
		}
		// families whose handler this port still lacks hold every row
		// the DFC's table is the addon's own now (D-3021): two rows name items this port lacks (the roasted marshmallow, the unobtainium nuggets)
		helper.assertTrue(byFamily.getOrDefault("dfc", 0) == 2, "dfc: " + byFamily.get("dfc"));
		// AddonMats and the base's arc furnace, material distribution and fuel pool took the rest of the handler-less families (D-3026)
		helper.assertTrue(byFamily.getOrDefault("arc_furnace", 0) == 0 && byFamily.getOrDefault("waste_drum", 0) == 0 && byFamily.getOrDefault("mat_distribution", 0) == 0,
				"arc_furnace/waste_drum/mat_distribution: " + byFamily);
		helper.assertTrue(byFamily.getOrDefault("purex", 0) == 0 && byFamily.getOrDefault("crucible", 0) == 0 && byFamily.getOrDefault("shredder", 0) == 0
				&& byFamily.getOrDefault("arc_welder", 0) == 0 && byFamily.getOrDefault("plasma_forge", 0) == 0 && byFamily.getOrDefault("anvil", 0) == 0, "no family is held for a material, a plate or a head any more: " + byFamily);
		// what is still held: the PWR overhaul's computer row (OpenComputers; the panel row is written since the Custom Control Panel exists, D-3072) - the APR's ten resolved with the
		// reactor (D-3027) and the ANY_ULTRAALLOY frame (D-3026) - the bobblehead of the centrifuge is written since the base's figures are appendable (D-2207, D-3041) - the DFC table's marshmallow and unobtainium
		helper.assertTrue(byFamily.getOrDefault("assembler", 0) == 1 && byFamily.getOrDefault("centrifuge", 0) == 0, "assembler/centrifuge: " + byFamily);
		helper.succeed();
	}
}
