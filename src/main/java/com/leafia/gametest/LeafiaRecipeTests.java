package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's crafting recipes, from a generator script (D-1064).
 *
 * <p>A recipe file that names an item which does not exist fails to load with one log line and
 * no other symptom, and a tag ingredient whose tag is empty loads fine and can never be
 * satisfied. Both are silent, so both are asserted here against the live recipe manager rather
 * than the JSON.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRecipeTests {

	private static final String EMPTY = "empty";

	/**
	 * Every recipe the generator writes, by id, with the feature that owns it. A recipe whose
	 * feature is off is absent on purpose (its {@code neoforge:conditions}), so it is skipped.
	 */
	private static Map<String, Boolean> expected() {
		Map<String, Boolean> ids = new java.util.LinkedHashMap<>();
		ids.put("fuzzy_identifier", LeafiaFeatures.fuzzyIdentifier);
		ids.put("amat_duct", LeafiaFeatures.amatPipes);
		ids.put("amat_duct_charger", LeafiaFeatures.amatPipes);
		ids.put("spk_cable", LeafiaFeatures.spkCable);
		ids.put("fluid_duct_valve_mdl", LeafiaFeatures.fluidDuctEquipment);
		ids.put("fluid_duct_valve_mdl_rs", LeafiaFeatures.fluidDuctEquipment);
		ids.put("broof", LeafiaFeatures.broof);
		ids.put("regex_filter", LeafiaFeatures.regexFilter);
		ids.put("elevator_shaft", LeafiaFeatures.elevators);
		ids.put("ev_spawn", LeafiaFeatures.elevators);
		ids.put("weight_spawn", LeafiaFeatures.elevators);
		ids.put("slop_reactor", LeafiaFeatures.slopReactor);
		ids.put("slop_reactor_casing", LeafiaFeatures.slopReactor);
		ids.put("slop_reactor_glass", LeafiaFeatures.slopReactor);
		ids.put("block_welded_osmiridium", LeafiaFeatures.osmiridiumBlocks);
		ids.put("plate_welded_osmiridium_from_block", LeafiaFeatures.osmiridiumBlocks);
		for(String ingot : new String[] {"xanaxium", "nc279", "taintium", "chydalium"})
			ids.put("smelt_ingot_" + ingot, LeafiaFeatures.resourceItems);
		for(String shield : new String[] {"steel_shield", "titanium_shield", "elec_shield", "pu238_shield", "fissite_shield"})
			ids.put(shield, LeafiaFeatures.shields);
		for(char c : "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray())
			ids.put("plate_iron_from_letter_sign_" + c, LeafiaFeatures.letterSigns);
		// D-3026: the rest of AddonCraftingRecipes whose results are ported. Each is on when the item it makes (or, for a
		// recipe that makes a base item, the addon item it takes) is registered; the overhaul ones also need their switch.
		for(String id : new String[] {"dfc_receiver", "nugget_schraranium_from_ingot", "ingot_schraranium_from_nuggets", "particle_taint", "particle_chydalium",
				"grenade_cloud", "grenade_pink_cloud", "vent_cloud", "vent_pink_cloud", "pill_meltdown", "blanket_fissite", "blanket_mysticite",
				"cells_fissite", "cells_mysticite_filled", "powder_digammitite", "diverter_unlit", "fix_survival", "reactor_hatch", "reactor_hatch_alt",
				"lwr_occs_in", "lwr_occs_out", "lwr_port", "ff_duct_solid_shielded", "fluid_duct_gauge_mdl", "radglasses"})
			ids.put(id, owns(id));
		for(String m : new String[] {"nc279", "fissium", "chydalium"}) {
			ids.put("ingot_" + m + "_from_billets", LeafiaFeatures.resourceItems);
			ids.put("billet_" + m + "_from_ingots", LeafiaFeatures.resourceItems);
		}
		for(String id : new String[] {"ams_focus_blank", "ams_lens", "ams_focus_omega", "ams_focus_booster", "ams_focus_limiter", "ams_focus_safe"})
			ids.put(id, LeafiaFeatures.dfcOverhaul && owns(id));
		for(String id : new String[] {"detonator_laser", "detonator_multi", "five_htp", "xanax"})
			ids.put(id, LeafiaFeatures.recipeOverhaul && owns(id));
		ids.put("shield_decoration", LeafiaFeatures.shields);
		// D-3028: the pink door, and the AM rifle (which takes the filled mysticite cell, a resource item)
		ids.put("door_fuckoff", LeafiaFeatures.pinkDoor);
		ids.put("gun_leafia_amrifle", LeafiaFeatures.amRifle && LeafiaFeatures.resourceItems);
		// D-3031: the two grenade fillings SEDNA registered
		ids.put("grenade_filling_leafia_null", LeafiaFeatures.leafiaGrenades);
		ids.put("grenade_filling_leafia_sol", LeafiaFeatures.leafiaGrenades);
		return ids;
	}

	/** Whether the addon item a recipe is named for (or the one it is made of) is registered. */
	private static boolean owns(String recipe) {
		String item = switch(recipe) {
			case "dfc_receiver" -> "dfcsh_beam";
			case "ingot_schraranium_from_nuggets", "nugget_schraranium_from_ingot" -> "nugget_schraranium";
			case "vent_cloud", "vent_pink_cloud" -> "grenade_cloud";
			case "diverter_unlit" -> "meteor_diverter_unlit";
			case "ams_lens" -> "ams_focus_blank";
			case "five_htp" -> "powder_digammitite";
			case "xanax" -> "powder_digammitite_tiny";
			case "detonator_laser", "detonator_multi" -> null;   // only base items: the switch alone decides
			default -> recipe;
		};
		return item == null || BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, item));
	}

	/**
	 * Ingredients known to have no item in this port, each with why. Anything else that resolves
	 * to nothing is a broken mapping in the generator.
	 */
	private static final Set<String> KNOWN_EMPTY_BASE = LeafiaFeatures.resourceItems ? Set.of() : Set.of(
			// BSCCO.wireFine(): NTM-CE has it, the 1.7.10 base mod autogenerates dense BSCCO wire only. The addon registers the
			// fine wire with the rest of its materials (AddonMats, D-3026), so the tag is empty only with the resource items off.
			"amat_duct/#c:wire_fines/bscco");

	/*
	 * The AM rifle's schraranium crystal was known-empty here until D-3031 (D-3028): NTM-CE's SRN.crystal(). The addon now
	 * tags the base mod's own crystal_schraranium as c:crystals/schraranium, so the rifle's recipe resolves and the tag is
	 * held to it like every other ingredient.
	 */
	private static final Set<String> KNOWN_EMPTY = KNOWN_EMPTY_BASE;

	/**
	 * Whether any real item satisfies it. Not {@code getItems()}: an empty tag answers that with a
	 * placeholder barrier named "Empty Tag", and {@code test} compares against the same list, so
	 * the placeholder passes its own ingredient. The first version of this test did exactly that.
	 */
	private static boolean satisfiable(Ingredient ingredient) {
		// a custom ingredient (a full fluid tank of one fluid, matched by its custom data) lists what it matches itself
		if(ingredient.isCustom()) return ingredient.getCustomIngredient().getItems().findAny().isPresent();
		for(Ingredient.Value value : ingredient.getValues()) {
			if(value instanceof Ingredient.TagValue t) {
				if(BuiltInRegistries.ITEM.getTag(t.tag()).map(set -> set.size() > 0).orElse(false)) return true;
			} else if(value instanceof Ingredient.ItemValue i) {
				if(!i.item().isEmpty()) return true;
			}
		}
		return false;
	}

	private static String describe(Ingredient ingredient) {
		if(ingredient.isCustom()) return "custom:" + ingredient.getCustomIngredient().getType().getClass().getSimpleName();
		var values = ingredient.getValues();
		if(values.length == 1 && values[0] instanceof Ingredient.TagValue tag)
			return "#" + tag.tag().location();
		List<String> items = new ArrayList<>();
		for(ItemStack stack : ingredient.getItems()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		return String.join("|", items);
	}

	@GameTest(template = EMPTY)
	public static void everyRecipeLoadsAndEveryIngredientExists(GameTestHelper helper) {

		var recipes = helper.getLevel().getRecipeManager();
		List<String> problems = new ArrayList<>();
		int checked = 0;

		for(var entry : expected().entrySet()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, entry.getKey());
			var holder = recipes.byKey(id);
			if(!entry.getValue()) {
				if(holder.isPresent()) problems.add(id + " loaded although its feature is off");
				continue;
			}
			if(holder.isEmpty()) {
				problems.add(id + " did not load - an item it names does not exist");
				continue;
			}
			checked++;
			for(Ingredient ingredient : holder.get().value().getIngredients()) {
				if(ingredient.isEmpty()) continue; // a blank cell in a shaped pattern
				String name = entry.getKey() + "/" + describe(ingredient);
				boolean ok = satisfiable(ingredient);
				if(!ok && !KNOWN_EMPTY.contains(name)) problems.add(name + " matches no item");
				if(ok && KNOWN_EMPTY.contains(name)) problems.add(name + " is listed as empty but now has an item - take it off the list");
			}
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(checked > 0 || !LeafiaFeatures.letterSigns, "no recipe was checked");
		helper.succeed();
	}

	/**
	 * The broof's grid, crafted for real: three carpets over two steel beams with a gap. The
	 * carpet is magenta, not the green upstream's code names - see D-1064.
	 */
	@GameTest(template = EMPTY)
	public static void theBroofCraftsFromMagentaCarpetAndSteelBeams(GameTestHelper helper) {

		if(!LeafiaFeatures.broof) { helper.succeed(); return; }

		ItemStack carpet = new ItemStack(Items.MAGENTA_CARPET);
		ItemStack beam = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "steel_beam")));
		helper.assertTrue(!beam.isEmpty() && !beam.is(Items.AIR), "hbm:steel_beam does not exist");

		CraftingInput grid = CraftingInput.of(3, 2, List.of(carpet, carpet, carpet, beam, ItemStack.EMPTY, beam));
		var level = helper.getLevel();
		var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
		helper.assertTrue(found.isPresent(), "three magenta carpets over two steel beams craft nothing");
		ItemStack out = found.map(RecipeHolder::value).get().assemble(grid, level.registryAccess());
		helper.assertTrue(out.is(LeafiaBlocks.broof.get().asItem()) && out.getCount() == 1, "crafted " + out);

		CraftingInput green = CraftingInput.of(3, 2, List.of(new ItemStack(Items.GREEN_CARPET), new ItemStack(Items.GREEN_CARPET),
				new ItemStack(Items.GREEN_CARPET), beam, ItemStack.EMPTY, beam));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, green, level).isEmpty(),
				"green carpet crafts a broof - upstream's recipe takes magenta");
		helper.succeed();
	}
}
