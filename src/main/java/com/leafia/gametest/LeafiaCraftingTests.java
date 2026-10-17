package com.leafia.gametest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidTank;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The crafting recipes the RECIPES batch added to {@code AddonCraftingRecipes} (D-3026): each is crafted for real
 * through the recipe manager from the grid upstream's call describes, and worked by hand from upstream's source rather
 * than read back from the JSON. A generator script is the other half: it asks that every upstream
 * statement is a recipe here, a base recipe overridden, or held with a reason.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCraftingTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ helpers

	private static Item item(String id) {
		ResourceLocation key = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("leafia", id);
		Item item = BuiltInRegistries.ITEM.get(key);
		if(item == Items.AIR) throw new IllegalStateException(key + " is not registered");
		return item;
	}

	private static ItemStack stack(String id) { return new ItemStack(item(id)); }

	/** Whether an addon item is registered under the switches the game was started with. */
	private static boolean has(String id) {
		return BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("leafia", id));
	}

	/** Any item of a common tag, which is what a tag ingredient accepts. */
	private static ItemStack tagged(String path) {
		var tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ResourceLocation.parse(path)));
		Item first = tag.flatMap(set -> set.stream().findFirst()).map(Holder::value).orElseThrow(() -> new IllegalStateException("#" + path + " is empty"));
		return new ItemStack(first);
	}

	/** What the crafting table gives for the grid (empty when it matches nothing). */
	private static ItemStack craft(GameTestHelper helper, int width, int height, ItemStack... cells) {
		var level = helper.getLevel();
		CraftingInput grid = CraftingInput.of(width, height, List.of(cells));
		Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
		return found.map(h -> h.value().assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	private static void gives(GameTestHelper helper, String what, ItemStack out, String id, int count) {
		helper.assertTrue(out.is(item(id)) && out.getCount() == count, what + ": crafted " + out + ", expected " + count + " x " + id);
	}

	private static void nothing(GameTestHelper helper, String what, ItemStack out) {
		helper.assertTrue(out.isEmpty(), what + ": crafted " + out);
	}

	private static ItemStack n(ItemStack s, int count) { return s.copyWithCount(count); }

	private static final ItemStack E = ItemStack.EMPTY;

	// ------------------------------------------------------------------ the recipes

	/** `addShapelessAuto(dfc_receiver, beam, cable, corner, core, STEEL.heavyBarrel(), front, corner, beam, beam)`. */
	@GameTest(template = EMPTY)
	public static void theDfcReceiverIsNineParts(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack beam = stack("dfcsh_beam"), cable = stack("dfcsh_cable"), corner = stack("dfcsh_corner"), core = stack("dfcsh_core");
		ItemStack front = stack("dfcsh_front"), barrel = tagged("c:barrel_heavies/steel");
		gives(helper, "the receiver", craft(helper, 3, 3, beam, cable, corner, core, barrel, front, corner, beam, beam), "hbm:dfc_receiver", 1);
		gives(helper, "in another order", craft(helper, 3, 3, barrel, beam, beam, beam, front, core, corner, corner, cable), "hbm:dfc_receiver", 1);
		nothing(helper, "with a third corner for the cable", craft(helper, 3, 3, beam, corner, corner, core, barrel, front, corner, beam, beam));
		nothing(helper, "with a light barrel", craft(helper, 3, 3, beam, cable, corner, core, tagged("c:barrel_lights/steel"), front, corner, beam, beam));
		helper.succeed();
	}

	/** `addRecipeAuto(ff_duct_solid_shielded x8, "SAS", "ADA", "SAS", compound brick, ff_duct, ducttape)`. */
	@GameTest(template = EMPTY)
	public static void shieldedSpecialPipeMakesEight(GameTestHelper helper) {
		if(!has("ff_duct_solid_shielded")) { helper.succeed(); return; }
		ItemStack s = stack("hbm:brick_compound"), a = stack("ff_duct_neo"), d = stack("hbm:ducttape");
		gives(helper, "shielded pipe", craft(helper, 3, 3, s, a, s, a, d, a, s, a, s), "ff_duct_solid_shielded", 8);
		nothing(helper, "with the colored finish at the edges", craft(helper, 3, 3, s, stack("ff_duct_colored"), s, a, d, a, s, a, s));
		helper.succeed();
	}

	/** `addShapelessAuto(fluid_duct_gauge_mdl, new ItemStack(fluid_duct_neo, 1, 2), IRON.plate())`: the colored pipe is meta 2. */
	@GameTest(template = EMPTY)
	public static void theFlowGaugeIsAColoredPipeAndAnIronPlate(GameTestHelper helper) {
		if(!has("fluid_duct_gauge_mdl")) { helper.succeed(); return; }
		gives(helper, "gauge", craft(helper, 2, 1, stack("hbm:fluid_duct_neo_colored"), tagged("c:plates/iron")), "fluid_duct_gauge_mdl", 1);
		nothing(helper, "from the plain finish", craft(helper, 2, 1, stack("hbm:fluid_duct_neo_neo"), tagged("c:plates/iron")));
		helper.succeed();
	}

	/** `add1To9Pair(ingot_schraranium, nugget_schraranium)`: one ingot is nine nuggets, a 3x3 of nuggets an ingot. */
	@GameTest(template = EMPTY)
	public static void schraraniumIngotsAndNuggetsPair(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack ingot = stack("hbm:ingot_schraranium"), nugget = stack("nugget_schraranium");
		gives(helper, "ingot to nuggets", craft(helper, 1, 1, ingot), "nugget_schraranium", 9);
		gives(helper, "nuggets to ingot", craft(helper, 3, 3, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget), "hbm:ingot_schraranium", 1);
		nothing(helper, "eight nuggets", craft(helper, 3, 3, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget, E));
		helper.succeed();
	}

	/** `MineralRecipes.addBilletToIngot(billet, ingot)` for nuclium, fissium and chydalium: three billets make two ingots, two ingots in a row three billets. */
	@GameTest(template = EMPTY)
	public static void billetsAndIngotsConvertThreeForTwo(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		for(String m : new String[] {"nc279", "fissium", "chydalium"}) {
			ItemStack billet = stack("billet_" + m), ingot = stack("ingot_" + m);
			gives(helper, m + " billets", craft(helper, 3, 1, billet, billet, billet), "ingot_" + m, 2);
			gives(helper, m + " ingots", craft(helper, 2, 1, ingot, ingot), "billet_" + m, 3);
			nothing(helper, m + " two billets", craft(helper, 2, 1, billet, billet));
		}
		helper.succeed();
	}

	/** `addRecipeAuto(grenade_cloud x2, "SPS", "CTC", "SPS", S.dust(), powder_poison, particle_cloud, fluid_tank_full[PEROXIDE])`. */
	@GameTest(template = EMPTY)
	public static void cloudGrenadesNeedThePeroxideTank(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack s = tagged("c:dusts/sulfur"), p = stack("hbm:powder_poison"), c = stack("particle_cloud");
		Item tank = item("hbm:fluid_tank_full");
		ItemStack peroxide = ItemFluidTank.make(tank, Fluids.PEROXIDE), water = ItemFluidTank.make(tank, Fluids.WATER);
		gives(helper, "grenades", craft(helper, 3, 3, s, p, s, c, peroxide, c, s, p, s), "grenade_cloud", 2);
		nothing(helper, "with a tank of water", craft(helper, 3, 3, s, p, s, c, water, c, s, p, s));
		nothing(helper, "with an empty tank", craft(helper, 3, 3, s, p, s, c, stack("hbm:fluid_tank_empty"), c, s, p, s));
		helper.succeed();
	}

	/**
	 * D-3031: AddonCraftingRecipes lines 150-151, the two fillings MixinEnumGrenadeFilling appends - four NULL fillings from
	 * two undefined items round a saturnite ingot, and one SOL filling from a controller, four solinium propellants, two
	 * CMB steel ingots, the solinium core and its igniter.
	 */
	@GameTest(template = EMPTY)
	public static void theTwoAppendedGrenadeFillings(GameTestHelper helper) {
		if(!LeafiaFeatures.leafiaGrenades) { helper.succeed(); return; }
		ItemStack u = stack("hbm:undefined"), sat = tagged("c:ingots/saturnite");
		gives(helper, "null filling", craft(helper, 1, 3, u, sat, u), "grenade_filling_leafia_null", 4);
		nothing(helper, "null filling on its side", craft(helper, 3, 1, u, sat, u));
		ItemStack c = stack("hbm:circuit_controller"), prop = stack("hbm:solinium_propellant"), b = tagged("c:ingots/cmbsteel"),
				core = stack("hbm:solinium_core"), t = stack("hbm:solinium_igniter");
		gives(helper, "sol filling", craft(helper, 3, 3, c, prop, b, prop, core, prop, b, prop, t), "grenade_filling_leafia_sol", 1);
		nothing(helper, "sol filling with core and igniter swapped", craft(helper, 3, 3, c, prop, b, prop, t, prop, b, prop, core));
		helper.succeed();
	}

	/** The pink grenade, and the two vents that take a grenade each. */
	@GameTest(template = EMPTY)
	public static void theCloudVentsAndThePinkGrenade(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack spark = stack("hbm:powder_spark_mix"), magic = stack("hbm:powder_magic"), cloud = stack("grenade_cloud"), pink = stack("grenade_pink_cloud");
		gives(helper, "pink grenade", craft(helper, 3, 3, E, spark, E, magic, cloud, magic, E, magic, E), "grenade_pink_cloud", 1);
		ItemStack plate = tagged("c:plates/iron"), bars = new ItemStack(Items.IRON_BARS), dispenser = new ItemStack(Items.DISPENSER);
		gives(helper, "cloud vent", craft(helper, 3, 3, plate, bars, plate, plate, cloud, plate, plate, dispenser, plate), "hbm:vent_cloud", 1);
		gives(helper, "pink cloud vent", craft(helper, 3, 3, plate, bars, plate, plate, pink, plate, plate, dispenser, plate), "hbm:vent_pink_cloud", 1);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theMeltdownPillAndTheBlanketBundles(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		if(has("pill_meltdown"))
			gives(helper, "meltdown pill", craft(helper, 2, 2, tagged("c:dusts/coal"), tagged("c:dusts/uranium"), tagged("c:dusts/fluorite"), stack("hbm:powder_magic")), "pill_meltdown", 1);
		gives(helper, "fissite blanket", craft(helper, 1, 1, stack("blanket_fissite_bundle")), "blanket_fissite", 16);
		gives(helper, "mysticite blanket", craft(helper, 1, 1, stack("blanket_mysticite_bundle")), "blanket_mysticite", 16);
		helper.succeed();
	}

	/** `am_rifle_cell_fissite`: a ring of fissite plates and ingots; `..._mysticite_filled`: the empty cell, a gold-198 cell and four euphemium. */
	@GameTest(template = EMPTY)
	public static void theRifleCells(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack plate = stack("plate_fissite"), ingot = stack("ingot_fissite");
		gives(helper, "fissite cell", craft(helper, 3, 3, plate, plate, plate, ingot, E, ingot, plate, plate, plate), "cells_fissite", 1);
		nothing(helper, "with the ingots on top", craft(helper, 3, 3, ingot, ingot, ingot, plate, E, plate, ingot, ingot, ingot));
		ItemStack euph = tagged("c:ingots/euphemium");
		gives(helper, "filled mysticite cell", craft(helper, 3, 2, stack("cells_mysticite"), stack("hbm:battery_sc_au198"), euph, euph, euph, euph), "cells_mysticite_filled", 1);
		nothing(helper, "with three euphemium", craft(helper, 3, 2, stack("cells_mysticite"), stack("hbm:battery_sc_au198"), euph, euph, euph, E));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theTaintAndChydaliumParticles(GameTestHelper helper) {
		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }
		ItemStack empty = stack("hbm:particle_empty"), charged = stack("hbm:pellet_charged");
		gives(helper, "taint particle", craft(helper, 3, 1, empty, tagged("c:dusts/taintium"), charged), "particle_taint", 1);
		gives(helper, "chydalium particle", craft(helper, 3, 1, empty, tagged("c:dusts/chydalium"), charged), "particle_chydalium", 1);
		nothing(helper, "taint with chydalium dust", craft(helper, 3, 1, empty, tagged("c:dusts/chydalium"), tagged("c:dusts/xanaxium")));
		helper.succeed();
	}

	/** Nine tiny digammitite powders are one. */
	@GameTest(template = EMPTY)
	public static void nineTinyDigammititePowdersAreOne(GameTestHelper helper) {
		if(!has("powder_digammitite")) { helper.succeed(); return; }
		ItemStack tiny = stack("powder_digammitite_tiny");
		gives(helper, "nine tiny", craft(helper, 3, 3, tiny, tiny, tiny, tiny, tiny, tiny, tiny, tiny, tiny), "powder_digammitite", 1);
		nothing(helper, "eight tiny", craft(helper, 3, 3, tiny, tiny, tiny, tiny, tiny, tiny, tiny, tiny, E));
		helper.succeed();
	}

	/** The PWR's hatch skins, and the three-way cycle of its port: port to OC input to OC output to port. */
	@GameTest(template = EMPTY)
	public static void theReactorHatchesAndThePortCycle(GameTestHelper helper) {
		if(!has("reactor_hatch") || !has("lwr_occs_in")) { helper.succeed(); return; }
		gives(helper, "hatch to alt", craft(helper, 1, 1, stack("reactor_hatch")), "reactor_hatch_alt", 1);
		gives(helper, "alt to hatch", craft(helper, 1, 1, stack("reactor_hatch_alt")), "reactor_hatch", 1);
		gives(helper, "port to input", craft(helper, 1, 1, stack("lwr_port")), "lwr_occs_in", 1);
		gives(helper, "input to output", craft(helper, 1, 1, stack("lwr_occs_in")), "lwr_occs_out", 1);
		gives(helper, "output to port", craft(helper, 1, 1, stack("lwr_occs_out")), "lwr_port", 1);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSurvivalCrucifixAndTheLitDiverter(GameTestHelper helper) {
		if(has("fix_survival")) {
			ItemStack o = stack("hbm:ingot_osmiridium"), t = stack("hbm:crystal_trixite");
			gives(helper, "crucifix", craft(helper, 3, 3, E, o, E, o, t, o, E, o, E), "fix_survival", 1);
		}
		if(has("meteor_diverter_unlit"))
			gives(helper, "protection beacon", craft(helper, 2, 1, stack("hbm:protection_charm"), new ItemStack(Items.TORCH)), "meteor_diverter_unlit", 1);
		helper.succeed();
	}

	/**
	 * The radiation goggles: two rubber, a pane on each side of a plastic, an advanced circuit under it, as upstream's
	 * {@code "I I", "GPG", " C "}. The item is iron's helmet in everything but its look: one to a stack, 165 uses, two armour,
	 * worn on the head.
	 */
	@GameTest(template = EMPTY)
	public static void theRadiationGogglesAreCraftedAndWornAsAnIronHelmet(GameTestHelper helper) {
		if(!has("radglasses")) { helper.succeed(); return; }
		ItemStack i = tagged("c:ingots/any_rubber"), g = new ItemStack(Items.GLASS_PANE), p = tagged("c:ingots/any_plastic"), c = stack("hbm:circuit_advanced");
		gives(helper, "goggles", craft(helper, 3, 3, i, E, i, g, p, g, E, c, E), "radglasses", 1);
		nothing(helper, "no circuit", craft(helper, 3, 3, i, E, i, g, p, g, E, E, E));
		nothing(helper, "a basic circuit", craft(helper, 3, 3, i, E, i, g, p, g, E, stack("hbm:circuit_basic"), E));
		nothing(helper, "no plastic", craft(helper, 3, 3, i, E, i, g, g, g, E, c, E));
		nothing(helper, "no panes", craft(helper, 3, 3, i, E, i, E, p, E, E, c, E));

		ItemStack goggles = stack("radglasses");
		helper.assertTrue(goggles.getMaxStackSize() == 1, "goggles stack to " + goggles.getMaxStackSize());
		helper.assertTrue(goggles.getMaxDamage() == 165, "goggles last " + goggles.getMaxDamage() + ", iron's helmet is 165");
		helper.assertTrue(goggles.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getType() == net.minecraft.world.item.ArmorItem.Type.HELMET,
				"goggles are not a helmet");
		helper.assertTrue(goggles.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getDefense() == 2, "iron's helmet is 2 armour");
		helper.assertTrue(goggles.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getEquipmentSlot() == net.minecraft.world.entity.EquipmentSlot.HEAD, "goggles are not worn on the head");
		java.util.List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
		goggles.getItem().appendHoverText(goggles, net.minecraft.world.item.Item.TooltipContext.EMPTY, tip, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(tip.stream().anyMatch(t -> t.getString().equals("Locates sources of chunk radiations")), "tooltip is " + tip);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the base recipes the overhauls drop

	private static Optional<?> parse(GameTestHelper helper, JsonElement json) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		return Recipe.CONDITIONAL_CODEC.parse(new ConditionalOps<>(ops, ICondition.IContext.EMPTY), json).getOrThrow();
	}

	private static JsonObject resource(String path) {
		try(InputStream in = LeafiaCraftingTests.class.getResourceAsStream(path)) {
			if(in == null) throw new IllegalStateException(path + " is not in the addon's resources");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The base mod's own file for a recipe, read from its mod file so the addon's same-named override cannot answer. */
	private static JsonObject baseRecipe(String id) {
		try {
			Path p = ModList.get().getModFileById("hbm").getFile().findResource("data", "hbm", "recipe", id + ".json");
			return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final String[][] OVERRIDES = {
			{"ams_lens", "dfcOverhaul"}, {"detonator_laser", "recipeOverhaul"}, {"detonator_multi", "recipeOverhaul"},
			{"five_htp", "recipeOverhaul"}, {"xanax", "recipeOverhaul"}, {"powder_ice", "recipeOverhaul"}};

	/**
	 * The addon's copy of each base recipe is the base's own file, key for key, with one condition added. Off, it loads and
	 * is the base's recipe; on, it does not load, which is what {@code removeRecipesForItem} was. If the base ever changes a
	 * recipe, this fails until a generator script is run again.
	 */
	@GameTest(template = EMPTY)
	public static void aBaseRecipeTheOverhaulsDropIsKeptByteForByteWhileTheyAreOff(GameTestHelper helper) {
		boolean dfc = LeafiaFeatures.dfcOverhaul, tweaks = LeafiaFeatures.recipeOverhaul;
		try {
			for(String[] o : OVERRIDES) {
				JsonObject mine = resource("/data/hbm/recipe/" + o[0] + ".json");
				JsonObject base = baseRecipe(o[0]);
				JsonObject stripped = mine.deepCopy();
				var conditions = stripped.remove("neoforge:conditions").getAsJsonArray();
				helper.assertTrue(stripped.equals(base), o[0] + ": the addon's copy is not the base's recipe any more");
				helper.assertTrue(conditions.size() == 1 && conditions.get(0).getAsJsonObject().get("type").getAsString().equals("neoforge:not")
						&& conditions.get(0).getAsJsonObject().getAsJsonObject("value").get("feature").getAsString().equals(o[1]), o[0] + ": condition " + conditions);

				for(boolean on : new boolean[] {false, true}) {
					LeafiaFeatures.dfcOverhaul = on;
					LeafiaFeatures.recipeOverhaul = on;
					boolean loads = ((Optional<?>) parse(helper, mine)).isPresent();
					helper.assertTrue(loads == !on, o[0] + " with " + o[1] + (on ? " on" : " off") + ": " + (loads ? "loads" : "does not load"));
				}
			}
		} finally {
			LeafiaFeatures.dfcOverhaul = dfc;
			LeafiaFeatures.recipeOverhaul = tweaks;
		}
		helper.succeed();
	}

	/** The replacements are the other half: they load with their switch on and only then. */
	@GameTest(template = EMPTY)
	public static void theReplacementsLoadOnlyWithTheirSwitch(GameTestHelper helper) {
		boolean dfc = LeafiaFeatures.dfcOverhaul, tweaks = LeafiaFeatures.recipeOverhaul;
		try {
			String[][] replacements = {{"detonator_laser", "recipeOverhaul"}, {"detonator_multi", "recipeOverhaul"}, {"five_htp", "recipeOverhaul"},
					{"xanax", "recipeOverhaul"}, {"ams_lens", "dfcOverhaul"}, {"ams_focus_blank", "dfcOverhaul"}, {"ams_focus_omega", "dfcOverhaul"},
					{"ams_focus_booster", "dfcOverhaul"}, {"ams_focus_limiter", "dfcOverhaul"}, {"ams_focus_safe", "dfcOverhaul"}};
			for(String[] r : replacements) {
				JsonObject json = resource("/data/leafia/recipe/" + r[0] + ".json");
				for(boolean on : new boolean[] {false, true}) {
					// its own switch alone: the other is the opposite, so a recipe gated by the wrong one is caught
					LeafiaFeatures.dfcOverhaul = r[1].equals("dfcOverhaul") ? on : !on;
					LeafiaFeatures.recipeOverhaul = r[1].equals("recipeOverhaul") ? on : !on;
					// the recipes also name addon items, which exist at the defaults only for the features that are on
					boolean loads = ((Optional<?>) parse(helper, json)).isPresent();
					helper.assertTrue(loads == on, r[0] + " with " + r[1] + (on ? " on" : " off") + ": " + (loads ? "loads" : "does not load"));
				}
			}
			// and the live manager, at the switches the game was started with
			var recipes = helper.getLevel().getRecipeManager();
			for(String[] r : replacements) {
				boolean on = r[1].equals("dfcOverhaul") ? dfc : tweaks;
				helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("leafia", r[0])).isPresent() == on, "leafia:" + r[0] + " in the live manager");
			}
			for(String[] o : OVERRIDES) {
				boolean on = o[1].equals("dfcOverhaul") ? dfc : tweaks;
				helper.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("hbm", o[0])).isPresent() == !on, "hbm:" + o[0] + " in the live manager");
			}
		} finally {
			LeafiaFeatures.dfcOverhaul = dfc;
			LeafiaFeatures.recipeOverhaul = tweaks;
		}
		helper.succeed();
	}

	/** Worked by hand from upstream: the laser detonator takes a rangefinder, rubber, fine gold wire and two basic circuits, and nothing else. */
	@GameTest(template = EMPTY)
	public static void theCheapDetonatorsAndTheDigammititeMedicines(GameTestHelper helper) {
		JsonObject laser = resource("/data/leafia/recipe/detonator_laser.json");
		JsonObject multi = resource("/data/leafia/recipe/detonator_multi.json");
		JsonObject htp = resource("/data/leafia/recipe/five_htp.json");
		JsonObject xanax = resource("/data/leafia/recipe/xanax.json");
		helper.assertTrue(ingredientNames(laser).equals(List.of("hbm:rangefinder", "#c:ingots/any_rubber", "#c:wire_fines/gold", "hbm:circuit_basic", "hbm:circuit_basic")), "laser " + ingredientNames(laser));
		helper.assertTrue(ingredientNames(multi).equals(List.of("hbm:detonator", "hbm:circuit_basic", "hbm:circuit_basic")), "multi " + ingredientNames(multi));
		helper.assertTrue(ingredientNames(htp).equals(List.of("#c:dusts/coal", "#c:dusts/euphemium", "hbm:canteen_vodka", "leafia:powder_digammitite")), "5-HTP " + ingredientNames(htp));
		helper.assertTrue(ingredientNames(xanax).equals(List.of("#c:dusts/coal", "#c:dusts/saltpeter", "#c:dusts/bromine", "leafia:powder_digammitite_tiny")), "xanax " + ingredientNames(xanax));
		for(JsonObject o : new JsonObject[] {laser, multi, htp, xanax})
			helper.assertTrue(o.getAsJsonObject("result").get("count").getAsInt() == 1, "one each");
		helper.succeed();
	}

	private static List<String> ingredientNames(JsonObject recipe) {
		List<String> out = new ArrayList<>();
		for(JsonElement e : recipe.getAsJsonArray("ingredients")) {
			JsonObject o = e.getAsJsonObject();
			out.add(o.has("tag") ? "#" + o.get("tag").getAsString() : o.get("item").getAsString());
		}
		return out;
	}

	// ------------------------------------------------------------------ the shield decoration

	@GameTest(template = EMPTY)
	public static void aBannerDecoratesAnAddonShield(GameTestHelper helper) {
		if(!LeafiaFeatures.shields) { helper.succeed(); return; }
		var level = helper.getLevel();
		Holder<BannerPattern> stripe = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.STRIPE_BOTTOM);
		BannerPatternLayers layers = new BannerPatternLayers.Builder().add(stripe, DyeColor.BLUE).build();

		ItemStack banner = new ItemStack(Items.RED_BANNER);
		banner.set(DataComponents.BANNER_PATTERNS, layers);
		ItemStack shield = stack("steel_shield");

		ItemStack out = craft(helper, 2, 1, shield, banner);
		helper.assertTrue(out.is(shield.getItem()), "the shield comes out: " + out);
		helper.assertTrue(out.get(DataComponents.BASE_COLOR) == DyeColor.RED, "its base is the banner's colour: " + out.get(DataComponents.BASE_COLOR));
		helper.assertTrue(layers.equals(out.get(DataComponents.BANNER_PATTERNS)), "and it wears the banner's patterns");
		helper.assertTrue(!shield.has(DataComponents.BASE_COLOR), "the ingredient is not changed");

		gives(helper, "in the other order", craft(helper, 2, 1, banner, shield), "steel_shield", 1);

		ItemStack plain = craft(helper, 2, 1, shield, new ItemStack(Items.WHITE_BANNER));
		helper.assertTrue(plain.is(shield.getItem()) && plain.get(DataComponents.BASE_COLOR) == DyeColor.WHITE
				&& plain.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty(), "a plain banner paints the base only");

		// upstream refuses a shield that already has a BlockEntityTag, and writes one (Base) every time
		nothing(helper, "an already decorated shield", craft(helper, 2, 1, out, banner));
		nothing(helper, "a plainly painted shield", craft(helper, 2, 1, plain, banner));
		nothing(helper, "two banners", craft(helper, 3, 1, shield, banner, banner));
		nothing(helper, "two shields", craft(helper, 3, 1, shield, stack("titanium_shield"), banner));
		nothing(helper, "a stick beside them", craft(helper, 3, 1, shield, banner, new ItemStack(Items.STICK)));
		helper.succeed();
	}
}
