package com.leafia.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.custom_hbm.contents.torex.LCETorex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityBalefire;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.inventory.recipes.PUREXRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemDepletedFuel;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.leafia.config.LeafiaFeatures;
import com.leafia.config.LeafiaRodConfig;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodCrafting;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem.ItemType;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem.Purity;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodRender;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.control.fuel.nuclearfuel.ShitfuckiumData;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.database.ReactorTiers;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.init.recipes.LeafiaRecipeInjector;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's fuel rods (D-3001): what registered, what each rod's numbers are, the heat function that
 * is the rod's whole physics, the uncrafting recipe, the hazards, the models that replace
 * {@code LeafiaRodRender}, the reactor-tier table and the two CE-only items.
 *
 * <p>Two kinds of pin, kept apart on purpose. The generated table is pinned wholesale (59 rods, 45
 * with life, 41 with a melting point, 3 hidden, 56 with a base item - each counted from upstream's
 * source by a second method, not from the generator), and a spread of rods is pinned <b>by hand</b>
 * from upstream's {@code AddonItems} declarations, so a parser that misread a chain would be caught by
 * the hand-written number rather than agreeing with itself. The heat function is checked against the
 * formula written out again in the test, one case per curve family, because the switch's risk is a
 * case labelled with the wrong constants - and its fall-through, which upstream relies on, is pinned
 * on its own.
 *
 * <p>The tests that explode something discard what they spawn in the same tick, before it can tick.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRodTests {

	private static final String EMPTY = "empty";
	private static final double EPS = 1e-6;

	// ---------------------------------------------------------------- scaffolding

	private static LeafiaRodItem rod(String fid) {
		return LeafiaRodItem.fromResourceMap.get("leafia_rod_" + fid);
	}

	private static ItemStack stack(String fid) {
		return new ItemStack(rod(fid));
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= Math.max(EPS, Math.abs(expected) * 1e-9), what + ": expected " + expected + ", got " + actual);
	}

	private static CompoundTag tag(ItemStack stack) {
		return LeafiaRodItem.data(stack);
	}

	private static JsonObject json(String path) {
		try(InputStream in = LeafiaRodTests.class.getResourceAsStream("/" + path)) {
			if(in == null) throw new IllegalStateException("no resource " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Whether a resource exists. The base mod's live in another module, which a class in this one cannot
	 * see through {@code getResource}, so they are found through the mod's own file.
	 */
	private static boolean exists(String path) {
		if(path.startsWith("assets/hbm/") || path.startsWith("data/hbm/")) {
			var hbm = net.neoforged.fml.ModList.get().getModContainerById("hbm").orElseThrow().getModInfo().getOwningFile().getFile();
			return java.nio.file.Files.exists(hbm.findResource(path.split("/")));
		}
		return LeafiaRodTests.class.getResource("/" + path) != null;
	}

	private static ResourceLocation rl(String name) {
		return ResourceLocation.fromNamespaceAndPath("leafia", name);
	}

	/** Whether the rods feature is on; every test that needs a rod is a no-op without it. */
	private static boolean on() {
		return LeafiaFeatures.rods;
	}

	// ---------------------------------------------------------------- the registry

	/**
	 * With the switch off nothing registers; on, the empty rod, 59 rods and 5 depleted fuels do. A
	 * switch that reads on while the registry is empty (or the other way) is the silent failure D-1003
	 * describes.
	 */
	@GameTest(template = EMPTY)
	public static void theSwitchAndTheRegistryAgree(GameTestHelper helper) {

		boolean any = BuiltInRegistries.ITEM.containsKey(rl("leafia_rod"));
		helper.assertTrue(any == on(), "features.rods is " + on() + " but the empty rod is " + (any ? "" : "not ") + "registered");
		helper.assertTrue(LeafiaRodItem.fromResourceMap.isEmpty() == !on(), "the rod map and the switch disagree");
		if(!on()) {
			helper.assertTrue(LeafiaRods.leafRod == null && LeafiaRods.RODS.isEmpty() && LeafiaRods.DEPLETED.isEmpty(), "a disabled rods feature left a holder behind");
			helper.assertTrue(com.leafia.init.LeafiaRecipeSerializers.GENERIC_FUEL_HANDLER == null, "the uncrafting serializer is registered with the rods off");
			helper.assertTrue(!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(rl("generic_fuel_handler")), "the serializer is in the registry");
			long ours = BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals("leafia")
					&& (k.getPath().startsWith("leafia_rod") || k.getPath().startsWith("waste_"))).count();
			helper.assertTrue(ours == 0, ours + " rod items are registered with the switch off");
			helper.succeed();
			return;
		}
		helper.assertTrue(LeafiaRods.RODS.size() == 59, "rods: " + LeafiaRods.RODS.size());
		helper.assertTrue(LeafiaRodItem.fromResourceMap.size() == 59, "rod map: " + LeafiaRodItem.fromResourceMap.size());
		helper.assertTrue(LeafiaRods.DEPLETED.size() == 5, "depleted fuels: " + LeafiaRods.DEPLETED.size());
		long rodItems = BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals("leafia") && k.getPath().startsWith("leafia_rod_")).count();
		helper.assertTrue(rodItems == 59, "registry holds " + rodItems + " leafia_rod_* items");
		helper.succeed();
	}

	/** Every rod: a stack of one, the empty rod as its container, and in the tab unless upstream hid it. */
	@GameTest(template = EMPTY)
	public static void everyRodStacksToOneAndHandsBackTheEmptyAssembly(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		Set<String> hidden = Set.of("leafia_rod_kys3000", "leafia_rod_catsmilium", "leafia_rod_confusium");
		List<String> tab = new ArrayList<>();
		for(Supplier<?> s : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN))
			if(s.get() instanceof Item i && BuiltInRegistries.ITEM.getKey(i).getPath().startsWith("leafia_rod")) tab.add(BuiltInRegistries.ITEM.getKey(i).getPath());
		for(var e : LeafiaRodItem.fromResourceMap.entrySet()) {
			LeafiaRodItem r = e.getValue();
			ItemStack s = new ItemStack(r);
			helper.assertTrue(s.getMaxStackSize() == 1, e.getKey() + " stacks to " + s.getMaxStackSize());
			helper.assertTrue(s.hasCraftingRemainingItem() && s.getCraftingRemainingItem().getItem() == LeafiaRods.leafRod.get(), e.getKey() + " does not hand back the empty rod");
			helper.assertTrue(tab.contains(e.getKey()) == !hidden.contains(e.getKey()), e.getKey() + " tab membership: " + tab.contains(e.getKey()) + ", hidden upstream: " + hidden.contains(e.getKey()));
		}
		helper.assertTrue(tab.contains("leafia_rod") && new ItemStack(LeafiaRods.leafRod.get()).getMaxStackSize() == 64, "the empty rod is in the tab and stacks to 64");
		helper.assertTrue(tab.size() == 59 - 3 + 1, "the tab holds " + tab.size() + " rod entries");
		helper.succeed();
	}

	/** The upstream-wide counts, taken from the source by hand-written greps rather than the generator. */
	@GameTest(template = EMPTY)
	public static void theTableHasUpstreamsCounts(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		int life = 0, melt = 0, withBase = 0;
		for(LeafiaRodItem r : LeafiaRodItem.fromResourceMap.values()) {
			if(r.life > 0) life++;
			if(r.meltingPoint != 0) melt++;
			if(r.baseItem != null) withBase++;
		}
		helper.assertTrue(life == 45, "rods with life: " + life);
		helper.assertTrue(melt == 41, "rods with a melting point: " + melt);
		helper.assertTrue(withBase == 56, "rods with a base item: " + withBase + " (blazing balefire, catsmilium and confusium have none)");
		helper.assertTrue(LeafiaRods.CONFIG.length == 45, "config rows: " + LeafiaRods.CONFIG.length);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the numbers, by hand

	private record Pin(String fid, String label, double life, double melt, double emission, double reactivity) {}

	/** Read off upstream's {@code AddonItems.LeafiaRods}, one by one. */
	private static final Pin[] PINS = {
			new Pin("u238", "U-238", 4_000_000, 0, 1, 1),
			new Pin("nu", "NU", 4_000_000, 2865, 1, 0.4),
			new Pin("meu235", "MEU-235", 4_000_000, 2865, 1, 1),
			new Pin("heu233", "HEU-233", 1_000_000, 2865, 1, 2),
			new Pin("heu235", "HEU-235", 3_000_000, 2865, 1, 1.75),
			new Pin("thmeu", "ThMEU", 3_500_000, 3350, 1, 1),
			new Pin("lepu239", "LEPu-239", 5_000_000, 2744, 1, 1 / 1.5),
			new Pin("hepu241", "HEPu-241", 18_000_000, 2744, 1, 1.45),
			new Pin("heam241", "HEAm-241", 18_000_000, 3986, 1, 1),
			new Pin("menp237", "MENp-237", 5_200_000, 2800, 1, 1),
			new Pin("henp237", "HENp-237", 3_200_000, 2800, 1, 1),
			new Pin("mox", "MOX", 6_000_000, 2815, 1, 1),
			new Pin("lesa326", "LESa-326", 3_500_000, 2500, 0.75, 1),
			new Pin("mesa326", "MESa-326", 3_000_000, 2750, 1, 1),
			new Pin("hesa326", "HESa-326", 2_500_000, 3000, 1.25, 1),
			new Pin("sa326", "Sa-326", 2_000_000, 3250, 2.25, 1),
			new Pin("leaus", "LEAus", 5_500_000, 7029, 0.3, 1),
			new Pin("heaus", "HEAus", 5_500_000, 5211, 1.2, 1),
			new Pin("ra226be", "Ra-226Be", 400_000, 700, 1, 1),
			new Pin("po210be", "Po-210Be", 2_000_000, 1287, 1, 1),
			new Pin("pu238be", "Pu-238Be", 2_000_000, 2744, 1, 1),
			new Pin("pu238", "Pu-238", 3_500_000, 2744, 1, 1),
			new Pin("po210", "Po-210", 3_000_000, 1287, 1, 1),
			new Pin("sa327", "Sa-327", 8_000_000, 2250, 65, 1 / 45d),
			new Pin("lithium", "Lithium", 3_000, 0, 1, 1),
			new Pin("pu240", "Pu-240", 65_000_000, 2744, 1, 1),
			new Pin("lead", "Lead", 99_999_999, 0, 1, 1),
			new Pin("balefire", "Balefire", 200, 8652, 8, 1 / 6d),
			new Pin("blazingbalefire", "Blazing Balefire", 65_000, 8652, 12, 1 / 9d),
			new Pin("npu", "NPu", 2_600_000, 2744, 1, 1),
			new Pin("th232", "Th-232", 90_000, 0, 1, 1),
			new Pin("au198", "Au-198", 900_000, 3850, 1, 1),
			new Pin("pb209", "Pb-209", 600_000, 4300, 1, 1),
			new Pin("debug", "Debug", 0, 0, 1, 1),
			new Pin("potentialinstantblowoutapplicator", "Potential Instant Blowout Applicator", 95_000_000, 650_000_000, 1, 1),
			new Pin("yhxxx", "Yh-XXX", 6_666_666, 3200, 1, 1),
			new Pin("dgomega", "DG-OMEGA", Double.MAX_VALUE, 1_916_169, 1, 2),
			new Pin("kys3000", "Kys-3000", Double.MAX_VALUE, 22, 114, 514),
			new Pin("shitfuckium", "Shitfuckium", Double.MAX_VALUE, 3200, 1, 1),
			new Pin("catsmilium", "Catsmilium", Double.MAX_VALUE, Double.MAX_VALUE, 1, 1),
			new Pin("confusium", "Confusium", Double.MAX_VALUE, 1300, 1, 1),
			new Pin("depleteduranium238", "Depleted Uranium-238", 0, 0, 1, 1),
	};

	@GameTest(template = EMPTY)
	public static void rodConstantsAreUpstreams(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(Pin p : PINS) {
			LeafiaRodItem r = rod(p.fid());
			helper.assertTrue(r != null, p.fid() + " is not registered");
			// the one rename upstream does: leafRodWasteUranium238 is "Depleted Uranium-238", relabelled "Bred Uranium-238"
			String label = p.fid().equals("depleteduranium238") ? "Bred Uranium-238" : p.label();
			helper.assertTrue(r.label.equals(label), p.fid() + " label " + r.label);
			helper.assertTrue(r.functionId.equals(p.fid()), p.fid() + " function id " + r.functionId);
			helper.assertTrue(r.life == p.life(), p.fid() + " life " + r.life + ", expected " + p.life());
			helper.assertTrue(r.meltingPoint == p.melt(), p.fid() + " melting point " + r.meltingPoint + ", expected " + p.melt());
			helper.assertTrue(r.emission == p.emission(), p.fid() + " emission " + r.emission + ", expected " + p.emission());
			helper.assertTrue(r.reactivity == p.reactivity(), p.fid() + " reactivity " + r.reactivity + ", expected " + p.reactivity());
		}
		helper.succeed();
	}

	/** Which neutrons a rod splits with, what it is made from and what it decays into. */
	@GameTest(template = EMPTY)
	public static void rodBehaviourFlagsAreUpstreams(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		// splitIntoFast / splitWithFast / splitWithAny
		for(LeafiaRodItem r : LeafiaRodItem.fromResourceMap.values()) {
			boolean moderated = Set.of("lesa326", "ra226be", "po210be", "pu238be").contains(r.functionId);
			boolean fast = Set.of("heam241", "henp237").contains(r.functionId);
			boolean any = r.functionId.equals("menp237");
			helper.assertTrue(r.splitIntoFast == !moderated, r.functionId + " splitIntoFast " + r.splitIntoFast);
			helper.assertTrue(r.splitWithFast == fast, r.functionId + " splitWithFast " + r.splitWithFast);
			helper.assertTrue(r.splitWithAny == any, r.functionId + " splitWithAny " + r.splitWithAny);
		}
		// the decay products
		String[][] decays = {
				{"u238", "depleteduranium238"}, {"nu", "depletednaturaluranium"}, {"meu235", "depleteduranium"}, {"heu233", "depleteduranium233"},
				{"heu235", "depleteduranium235"}, {"thmeu", "depletedthorium"}, {"lepu239", "depletedplutonium"}, {"mepu239", "depletedplutonium"},
				{"hepu239", "pu240"}, {"hepu241", "depletedplutonium"}, {"lesa326", "depletedschrabidium"}, {"mesa326", "depletedmesa326"},
				{"hesa326", "depletedhesa326"}, {"sa326", "depletedsa326"}, {"sa327", "depletedsa327"}, {"leaus", "heaus"}, {"lead", "pb209"},
				{"balefire", "blazingbalefire"}, {"blazingbalefire", "balefire"}, {"th232", "thmeu"},
		};
		Set<String> decaying = new HashSet<>();
		for(String[] d : decays) {
			LeafiaRodItem r = rod(d[0]);
			helper.assertTrue(r.decayProductBuffer != null && r.decayProductBuffer.equals(d[1]), d[0] + " decays into " + r.decayProductBuffer + ", upstream's is " + d[1]);
			helper.assertTrue(r.newFuel() == rod(d[1]), d[0] + "'s decay product does not resolve to the rod " + d[1]);
			decaying.add(d[0]);
		}
		for(LeafiaRodItem r : LeafiaRodItem.fromResourceMap.values())
			if(!decaying.contains(r.functionId)) helper.assertTrue(r.decayProductBuffer == null, r.functionId + " has a decay product upstream does not give it");
		LeafiaRodItem.confirmDecayProducts();

		// appearance: what each rod is built from, as the icon the renderer draws
		helper.assertTrue(rod("u238").baseItemType == ItemType.BILLET && rod("u238").purity == Purity.ISOTOPE && rod("u238").baseItem.get() == ModItems.billet_u238.get(), "u238 appearance");
		helper.assertTrue(rod("nu").purity == Purity.RAW && rod("meu235").purity == Purity.FUEL && rod("ra226be").purity == Purity.SOURCE, "purity of nu, meu235, ra226be");
		helper.assertTrue(rod("debug").purity == Purity.UNSTABLE && rod("kys3000").purity == Purity.UNSTABLE && rod("potentialinstantblowoutapplicator").purity == Purity.UNSTABLE, "the three unstable rods");
		helper.assertTrue(rod("depleteduranium").baseItemType == ItemType.DEPLETED && rod("depleteduranium").baseMeta == 1
				&& rod("depleteduranium").baseItem.get() == ModItems.waste_uranium.get(), "depleted uranium is waste_uranium at damage 1");
		helper.assertTrue(rod("depleteduranium238").baseItem.get() == LeafiaRods.depleted("waste_u238"), "bred uranium-238 is the addon's waste_u238");
		helper.assertTrue(rod("kys3000").baseItem.get() == LeafiaResourceItems.get("billet_kys").get(), "kys-3000 is made from the addon's billet");
		helper.assertTrue(rod("balefire").baseItem.get() == ModItems.egg_balefire.get() && rod("dgomega").baseItem.get() == ModItems.particle_digamma.get(), "balefire and digamma rods' base items");
		helper.assertTrue(rod("blazingbalefire").baseItem == null && rod("catsmilium").baseItem == null && rod("confusium").baseItem == null, "the three rods with no base item");
		helper.assertTrue(rod("shitfuckium").baseItem.get() == ModItems.rbmk_pellet_drx.get(), "shitfuckium is the drx pellet");
		// the intro lines of the three anonymous subclasses
		helper.assertTrue(rod("kys3000").intro.size() == 2 && rod("kys3000").intro.get(0).equals("Inspired by Quasar!"), "kys intro");
		helper.assertTrue(rod("catsmilium").intro.size() == 3 && rod("catsmilium").intro.get(2).equals("Never melts down"), "catsmilium intro");
		helper.assertTrue(rod("confusium").intro.size() == 1 && rod("confusium").intro.get(0).startsWith("???") && rod("confusium").intro.get(0).length() > 100, "confusium intro");
		helper.assertTrue(rod("u238").intro.isEmpty(), "an ordinary rod has no intro");
		// meltdown priority: the balefires outrank everything, set by detonate(null, null) in the constructor
		helper.assertTrue(rod("balefire").meltdownPriority == 20 && rod("blazingbalefire").meltdownPriority == 20 && rod("nu").meltdownPriority == 0, "meltdown priorities");
		helper.succeed();
	}

	/** The 45 config rows are the rods' own numbers, and every one has a TOML path. */
	@GameTest(template = EMPTY)
	public static void theConfigRowsAreTheRodsOwnNumbers(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(LeafiaRods.RodDefaults row : LeafiaRods.CONFIG) {
			LeafiaRodItem r = rod(row.key());
			helper.assertTrue(r != null, "config row for a rod that does not exist: " + row.key());
			helper.assertTrue(row.life() == r.life && row.emission() == r.emission && row.reactivity() == r.reactivity,
					row.key() + ": row " + row + " but rod " + r.life + "/" + r.emission + "/" + r.reactivity);
			for(String leaf : new String[] {"life", "emission", "reactivity"})
				helper.assertTrue(LeafiaRodConfig.SPEC.getSpec().get(List.of("rods", row.key(), leaf)) != null, "no config path rods." + row.key() + "." + leaf);
		}
		helper.assertTrue(LeafiaRodConfig.SPEC.getSpec().get(List.of("rods", "depleteduranium", "life")) == null, "a rod with no life has a config entry");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the heat function

	/** One case per curve family, against the formula written out again here. */
	@GameTest(template = EMPTY)
	public static void theCurvesAreTheSwitchCases(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		double x = 1234.5;
		double hot = Math.max(x - 2500, 0);
		near(helper, rod("nu").curve(x), Math.pow(x * 8, 0.56) * 3, "nu");
		near(helper, rod("meu235").curve(x), Math.pow(x * 8, 0.56) * 3, "meu235");
		near(helper, rod("heu235").curve(3000), Math.pow(3000 * 8, 0.56) * 3 + Math.pow(500 / 1000d, 3), "heu235 past 2500");
		near(helper, rod("heu235").curve(x), Math.pow(x * 8, 0.56) * 3, "heu235 below 2500 has no cubic term");
		near(helper, rod("mox").curve(x), Math.pow(x * 8, 0.56) * 2, "mox");
		near(helper, rod("thmeu").curve(x), Math.pow(x * 65, 0.35) * 3, "thmeu (the label says 64, the formula 65)");
		near(helper, rod("lepu239").curve(x), Math.pow(x * 8, 0.6) * 3, "lepu239");
		near(helper, rod("hepu241").curve(4000), Math.pow(4000 * 8, 0.6) * 3 + Math.pow(1500 / 800d, 3), "hepu241 past 2500");
		near(helper, rod("leam242").curve(x), Math.pow(x * 8, 0.64) * 3, "leam242");
		near(helper, rod("heam241").curve(4000), Math.pow(4000 * 8, 0.64) * 3 + Math.pow(1500 / 800d, 3), "heam241 past 2500");
		near(helper, rod("menp237").curve(x), Math.pow(x * 8, 0.52) * 3, "menp237");
		near(helper, rod("sa326").curve(4100), Math.pow(4100, 0.65) * 12 + Math.pow(1600 / 1600d, 3), "sa326 past 2500");
		near(helper, rod("ra226be").curve(x), 300 / (1 + Math.pow(Math.E, -0.02 * x)) - 150, "ra226be");
		near(helper, rod("po210").curve(x), 700 + Math.pow(x * 2, 0.69), "po210");
		near(helper, rod("au198").curve(x), 1580 + Math.pow(x, 0.75) * 2.5, "au198");
		near(helper, rod("pb209").curve(x), 2300 + x * 0.4, "pb209");
		near(helper, rod("pu238").curve(x), 1330 + Math.pow(x * 2, 0.62), "pu238");
		near(helper, rod("leaus").curve(x), Math.pow(x * 5, 0.65) * 2, "leaus");
		near(helper, rod("yhxxx").curve(x), Math.pow(x, 0.75) * 8, "yhxxx");
		near(helper, rod("dgomega").curve(x), Math.pow(1.01916169, x), "dgomega");
		near(helper, rod("kys3000").curve(x), Math.tan(x), "kys3000");
		near(helper, rod("confusium").curve(x), Math.abs(Math.tan(x)) * 1000, "confusium");
		// balefire: a cubic in the flux, and blazing is the same cubic shifted by a thousand
		double b = 2000;
		near(helper, rod("balefire").curve(b), 100 * Math.pow(b / 1000 - 2, 3) - 500 * Math.pow(b / 1000 - 2, 2) + b / 600 + 2800, "balefire");
		double z = b + 1000;
		near(helper, rod("blazingbalefire").curve(b), 100 * Math.pow(z / 1000 - 2, 3) - 500 * Math.pow(z / 1000 - 2, 2) + z / 600 + 2800, "blazing balefire");
		// the depleted rods are constants: 80-20, 90-20, 60-20, 100-20
		for(String f : new String[] {"depleteduranium", "depletedmox", "depletednaturaluranium", "depleteduranium233", "depleteduranium235", "depleteduranium238"})
			near(helper, rod(f).curve(x), 60, f);
		near(helper, rod("depletedplutonium").curve(x), 70, "depletedplutonium");
		near(helper, rod("depletedthorium").curve(x), 40, "depletedthorium");
		for(String f : new String[] {"depletedschrabidium", "depletedmesa326", "depletedhesa326", "depletedsa326", "depletedsa327"})
			near(helper, rod(f).curve(x), 80, f);
		// a rod with no case is flat zero: u238 has no curve (upstream comments its case out)
		near(helper, rod("u238").curve(x), 0, "u238");
		near(helper, rod("lithium").curve(x), 0, "lithium");
		near(helper, hot, 0, "scaffold");
		helper.succeed();
	}

	/**
	 * Catsmilium has no curve of its own: it sets {@code disableDecay} and <em>falls through</em> into
	 * the NU / MEU-235 case, and the two behaviours must both hold.
	 */
	@GameTest(template = EMPTY)
	public static void catsmiliumFallsThroughIntoTheUraniumCurve(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem cat = rod("catsmilium");
		near(helper, cat.curve(777), Math.pow(777 * 8, 0.56) * 3, "catsmilium follows the uranium curve");
		helper.assertTrue(cat.lastDisableDecay, "catsmilium does not decay");
		rod("nu").curve(777);
		helper.assertTrue(!rod("nu").lastDisableDecay, "nu decays");
		helper.succeed();
	}

	/** Shitfuckium reads a hand-drawn graph: 652 samples, flipped. */
	@GameTest(template = EMPTY)
	public static void shitfuckiumReadsItsGraph(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		helper.assertTrue(ShitfuckiumData.width == 652 && ShitfuckiumData.height == 440, "graph size " + ShitfuckiumData.width + "x" + ShitfuckiumData.height);
		helper.assertTrue(ShitfuckiumData.evalute(0) == 440 - 403, "the left edge reads the last sample, 403: " + ShitfuckiumData.evalute(0));
		helper.assertTrue(ShitfuckiumData.evalute(0.9999) == 440 - 117, "the right edge reads the first sample, 117: " + ShitfuckiumData.evalute(0.9999));
		helper.assertTrue(ShitfuckiumData.evalute(5) == 440 - 117, "past the end clamps to index 0");
		LeafiaRodItem r = rod("shitfuckium");
		double flux = 1000;
		near(helper, r.curve(flux), (double) ShitfuckiumData.evalute(flux / 3200 * 0.6) * 3200 / 440 * 1.25, "shitfuckium's curve");
		helper.succeed();
	}

	/** A fresh rod, one step: every key the step writes, against the arithmetic worked out by hand. */
	@GameTest(template = EMPTY)
	public static void aFreshRodTakesItsFirstStep(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack s = stack("nu");
		helper.assertTrue(tag(s) == null, "a fresh rod has no tag");
		double x = 1000;
		rod("nu").HeatFunction(s, true, x, 0, 0, 0);
		CompoundTag t = tag(s);
		double y = Math.pow(x * 8, 0.56) * 3;
		double heatMg = Math.pow(y + 1, 0.25) - 1;
		double depletion = Math.max(heatMg / 2 + Math.pow(x, 0.95) / 2000, 0);
		double decay = (heatMg / 2) * 0.01 * 0.99992694932;
		near(helper, t.getDouble("heat"), 20 + heatMg + Math.max(decay - heatMg, 0), "heat");
		near(helper, t.getDouble("depletion"), depletion, "depletion");
		near(helper, t.getDouble("decay"), decay, "decay");
		near(helper, t.getDouble("incoming"), x, "incoming flux");
		near(helper, t.getDouble("cooled"), 0, "cooled");
		helper.assertTrue(t.getInt("generosityTimer") == 90 && t.getInt("spillage") == 0 && !t.getBoolean("melting") && !t.contains("nuke"), "the grace timer, spillage and flags start clean: " + t);
		helper.succeed();
	}

	/** Coolant: the formula's 'cooled' term, and the floor at absolute zero. */
	@GameTest(template = EMPTY)
	public static void coolantTakesHeatOutBySpec(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack s = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 2000);
		t.putDouble("depletion", 0);
		t.putDouble("decay", 0);
		t.putInt("generosityTimer", 90);
		LeafiaRodItem.putData(s, t);
		rod("nu").HeatFunction(s, true, 0, 1, 300, 100);
		double heatMg = -(Math.pow(Math.abs(20 + 0 - 2000) + 1, 0.25) - 1);
		double newTemp = 2000 + heatMg;
		double cooled = (Math.pow(Math.max(newTemp - 300, 0) + 1, Math.sqrt(100) / 100) - 1) * 1;
		near(helper, tag(s).getDouble("cooled"), cooled, "cooled");
		near(helper, tag(s).getDouble("heat"), newTemp - cooled, "heat after cooling");

		// minimumRequired: a step that would cool less than the minimum cools nothing
		ItemStack s2 = stack("nu");
		LeafiaRodItem.putData(s2, t.copy());
		rod("nu").HeatFunction(s2, true, 0, 1, 300, 100, cooled + 1);
		near(helper, tag(s2).getDouble("cooled"), 0, "cooled below the minimum");
		near(helper, tag(s2).getDouble("heat"), newTemp, "heat with no cooling");

		// the floor: nothing goes below -273.15
		ItemStack s3 = stack("nu");
		CompoundTag cold = new CompoundTag();
		cold.putDouble("heat", -270);
		LeafiaRodItem.putData(s3, cold);
		rod("nu").HeatFunction(s3, true, 0, 1000, -1000, 1_000_000);
		helper.assertTrue(tag(s3).getDouble("heat") >= -273.15, "heat fell through absolute zero: " + tag(s3).getDouble("heat"));
		helper.succeed();
	}

	/** Past the melting point and still rising for 90 steps, the rod melts; once it cools below, it recovers. */
	@GameTest(template = EMPTY)
	public static void aRodMeltsAfterNinetyStepsPastItsMeltingPoint(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem r = rod("nu");
		ItemStack s = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 2866);
		t.putInt("generosityTimer", 90);
		LeafiaRodItem.putData(s, t);
		for(int i = 1; i <= 89; i++) r.HeatFunction(s, true, 1_000_000, 0, 0, 0);
		helper.assertTrue(!tag(s).getBoolean("melting") && tag(s).getInt("generosityTimer") == 1, "after 89 hot steps: timer " + tag(s).getInt("generosityTimer") + ", melting " + tag(s).getBoolean("melting"));
		r.HeatFunction(s, true, 1_000_000, 0, 0, 0);
		helper.assertTrue(tag(s).getBoolean("melting") && tag(s).getInt("generosityTimer") == 0, "the 90th step melts it");
		r.HeatFunction(s, true, 1_000_000, 0, 0, 0);
		helper.assertTrue(tag(s).getInt("spillage") == 1, "a melting rod spills one more each step");
		double depletionMelting = tag(s).getDouble("depletion");
		r.HeatFunction(s, true, 1_000_000, 0, 0, 0);
		near(helper, tag(s).getDouble("depletion"), depletionMelting, "a melting rod stops depleting");

		// cooled below the melting point: the flag clears and the spillage resets
		CompoundTag cool = tag(s);
		cool.putDouble("heat", 100);
		LeafiaRodItem.putData(s, cool);
		r.HeatFunction(s, true, 0, 0, 0, 0);
		helper.assertTrue(!tag(s).getBoolean("melting") && tag(s).getInt("spillage") == 0, "a cooled rod stops melting");

		// a rod with melting point 0 never melts, however hot
		LeafiaRodItem lead = rod("lead");
		ItemStack l = stack("lead");
		CompoundTag lt = new CompoundTag();
		lt.putDouble("heat", 5000);
		lt.putInt("generosityTimer", 90);
		LeafiaRodItem.putData(l, lt);
		for(int i = 0; i < 200; i++) lead.HeatFunction(l, true, 0, 0, 0, 0);
		helper.assertTrue(!tag(l).getBoolean("melting"), "lead melted although its melting point is zero");

		// 10 million degrees raises the nuke flag
		ItemStack n = stack("nu");
		CompoundTag nt = new CompoundTag();
		nt.putDouble("heat", 20_000_000);
		LeafiaRodItem.putData(n, nt);
		r.HeatFunction(n, true, 0, 0, 0, 0);
		helper.assertTrue(tag(n).getBoolean("nuke"), "a rod above ten million degrees is flagged to blow");
		helper.succeed();
	}

	/** A spent rod stops heating; a rod at its life becomes its decay product, with the heat carried over. */
	@GameTest(template = EMPTY)
	public static void aSpentRodBecomesItsDecayProduct(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem r = rod("u238");
		ItemStack s = stack("u238");
		helper.assertTrue(r.getDecayProduct(s) == null, "a fresh rod has no decay product");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 321);
		t.putDouble("depletion", r.life - 1);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(r.getDecayProduct(s) == null, "one short of spent");
		t.putDouble("depletion", r.life);
		t.putDouble("decay", 5);
		LeafiaRodItem.putData(s, t);
		ItemStack out = r.getDecayProduct(s);
		helper.assertTrue(out != null && out.getItem() == rod("depleteduranium238"), "u238 at its life becomes depleted uranium-238");
		near(helper, tag(out).getDouble("heat"), 321, "heat carried over");
		near(helper, tag(out).getDouble("decay"), 5, "decay carried over");
		near(helper, tag(out).getDouble("depletion"), 0, "depletion reset");
		// melting: no product
		t.putBoolean("melting", true);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(r.getDecayProduct(s) == null, "a melting rod does not decay");
		// a spent rod's target temperature is zero: it cools
		t.putBoolean("melting", false);
		t.putDouble("heat", 500);
		t.putDouble("decay", 0); // decay heat would warm a spent rod: it is upstream's, and tested on its own
		LeafiaRodItem.putData(s, t);
		r.HeatFunction(s, true, 1000, 0, 0, 0);
		helper.assertTrue(tag(s).getDouble("heat") < 500, "a spent rod still heated: " + tag(s).getDouble("heat"));
		// a rod with no decay product at its life: nothing
		ItemStack lith = stack("lithium");
		CompoundTag lt = new CompoundTag();
		lt.putDouble("depletion", 99999);
		LeafiaRodItem.putData(lith, lt);
		helper.assertTrue(rod("lithium").getDecayProduct(lith) == null, "lithium has nothing to decay into");
		helper.succeed();
	}

	/** The durability bar: depletion over life, green fading as it goes. */
	@GameTest(template = EMPTY)
	public static void theBarIsDepletionOverLife(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem r = rod("nu");
		ItemStack s = stack("nu");
		helper.assertTrue(!r.isBarVisible(s) && r.getBarColor(s) == (255 << 8), "a fresh rod shows no bar and reads 255 green");
		CompoundTag t = new CompoundTag();
		t.putDouble("depletion", r.life / 2);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(r.isBarVisible(s), "a half-spent rod shows its bar");
		near(helper, r.getDurabilityForDisplay(s), 0.5, "half spent");
		helper.assertTrue(r.getBarWidth(s) == Math.round(13 - 6.5F), "bar width " + r.getBarWidth(s));
		int green = (int) (Math.pow(0.5, 1.25) * 155 + 100);
		helper.assertTrue(r.getBarColor(s) == green << 8, "bar colour " + Integer.toHexString(r.getBarColor(s)) + ", expected green " + green);
		t.putDouble("depletion", r.life * 3);
		LeafiaRodItem.putData(s, t);
		near(helper, r.getDurabilityForDisplay(s), 1, "over-spent clamps to 1");
		helper.assertTrue(r.getBarColor(s) == 100 << 8, "a fully spent rod reads 100 green");
		helper.succeed();
	}

	/** getFlux: the neutron a rod gives its neighbour, three times as readily when the speeds agree. */
	@GameTest(template = EMPTY)
	public static void fluxFollowsTheNeutronCompatibility(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack other = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 1000);
		LeafiaRodItem.putData(other, t);
		// compat is 3 when the neighbour's speed is the one this rod prefers (plain rods prefer slow, so a
		// moderated neighbour is the 3x one), else 0.5; times the neighbour's emission and this rod's reactivity
		near(helper, rod("nu").getFlux(other), 1000 * 0.5 * 1 * 0.4, "nu takes from nu");
		ItemStack mod = stack("lesa326");
		LeafiaRodItem.putData(mod, t.copy());
		near(helper, rod("nu").getFlux(mod), 1000 * 3 * 0.75 * 0.4, "nu from a moderated rod");
		near(helper, rod("heam241").getFlux(mod), 1000 * 0.5 * 0.75 * 1, "a fast-preferring rod takes a moderated rod's neutrons at 0.5");
		near(helper, rod("heam241").getFlux(other), 1000 * 3 * 1 * 1, "a fast-preferring rod takes a plain rod's at 3x");
		near(helper, rod("menp237").getFlux(mod), 1000 * 3 * 0.75, "a rod that takes any takes them at 3x");
		// the flag form: moderated != splitWithFast
		near(helper, rod("nu").getFlux(other, true), 1000 * 3 * 1 * 0.4, "slow neighbours, plain taker");
		near(helper, rod("nu").getFlux(other, false), 1000 * 0.5 * 1 * 0.4, "fast neighbours, plain taker");
		near(helper, rod("heam241").getFlux(other, false), 1000 * 3 * 1 * 1, "fast neighbours, fast taker");
		// the floor of 20, and nothing from a non-rod or an untagged rod
		LeafiaRodItem.putData(other, new CompoundTag());
		near(helper, rod("nu").getFlux(other), 20 * 0.5 * 0.4, "a rod at zero heat counts as 20");
		near(helper, rod("nu").getFlux(new ItemStack(ModItems.billet_u238.get())), 0, "a billet gives no flux");
		near(helper, rod("nu").getFlux(stack("nu")), 0, "a rod with no tag gives none");
		near(helper, rod("nu").getFlux(null), 0, "null gives none");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the rod in a pocket

	private static Player carrier(GameTestHelper helper) {
		Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		for(int i = 0; i < 36; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
		return p;
	}

	@GameTest(template = EMPTY)
	public static void aHotRodSetsItsHolderAlightAndBurnsAbove1500(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		// 500 degrees: alight for 2 + floor(heat/100) seconds, no damage
		Player p = carrier(helper);
		ItemStack s = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 500);
		LeafiaRodItem.putData(s, t);
		p.getInventory().setItem(0, s);
		float hp = p.getHealth();
		rod("nu").inventoryTick(s, helper.getLevel(), p, 0, false);
		double heatNow = tag(s).getDouble("heat");
		helper.assertTrue(heatNow < 500 && heatNow >= 80, "the rod cooled a little: " + heatNow);
		int expected = (2 + (int) Math.floor(heatNow / 100)) * 20;
		helper.assertTrue(p.getRemainingFireTicks() == expected, "fire ticks " + p.getRemainingFireTicks() + ", expected " + expected);
		helper.assertTrue(p.getHealth() == hp, "500 degrees hurt the holder outright");

		// 1600 degrees: also six damage
		Player q = carrier(helper);
		ItemStack hot = stack("nu");
		CompoundTag ht = new CompoundTag();
		ht.putDouble("heat", 1600);
		LeafiaRodItem.putData(hot, ht);
		q.getInventory().setItem(0, hot);
		float qhp = q.getHealth();
		rod("nu").inventoryTick(hot, helper.getLevel(), q, 0, false);
		helper.assertTrue(q.getHealth() <= qhp - 6F + 0.01F || q.isDeadOrDying(), "1600 degrees did not cost six health: " + qhp + " -> " + q.getHealth());

		// under 80 degrees: nothing
		Player r = carrier(helper);
		ItemStack warm = stack("nu");
		CompoundTag wt = new CompoundTag();
		wt.putDouble("heat", 60);
		LeafiaRodItem.putData(warm, wt);
		r.getInventory().setItem(0, warm);
		rod("nu").inventoryTick(warm, helper.getLevel(), r, 0, false);
		helper.assertTrue(r.getRemainingFireTicks() <= 0, "a 60 degree rod set its holder alight");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theReacherKeepsTheRodOffYourHands(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack s = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 500);
		LeafiaRodItem.putData(s, t);

		Player bare = carrier(helper);
		bare.getInventory().setItem(0, s.copy());
		rod("nu").inventoryTick(bare.getInventory().getItem(0), helper.getLevel(), bare, 0, false);
		helper.assertTrue(bare.getRemainingFireTicks() > 0, "no reacher: should burn");

		Player held = carrier(helper);
		held.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.reacher.get())); // Library.checkForHeld reads the main hand
		held.getInventory().setItem(1, s.copy());
		rod("nu").inventoryTick(held.getInventory().getItem(1), helper.getLevel(), held, 0, false);
		helper.assertTrue(held.getRemainingFireTicks() <= 0, "holding the reacher should keep a rod under 900 degrees from burning you");

		// above 900 the reacher no longer helps
		CompoundTag t2 = new CompoundTag();
		t2.putDouble("heat", 1000);
		ItemStack s2 = stack("nu");
		LeafiaRodItem.putData(s2, t2);
		Player held2 = carrier(helper);
		held2.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.reacher.get()));
		held2.getInventory().setItem(1, s2);
		rod("nu").inventoryTick(s2, helper.getLevel(), held2, 1, false);
		helper.assertTrue(held2.getRemainingFireTicks() > 0, "the reacher does not help past 900 degrees");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSpentRodTurnsIntoItsDecayProductInThePocket(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		Player p = carrier(helper);
		ItemStack s = stack("u238");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 20);
		t.putDouble("depletion", rod("u238").life);
		LeafiaRodItem.putData(s, t);
		p.getInventory().setItem(3, s);
		rod("u238").inventoryTick(s, helper.getLevel(), p, 3, false);
		helper.assertTrue(p.getInventory().getItem(3).getItem() == rod("depleteduranium238"), "slot 3 holds " + p.getInventory().getItem(3));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the blast

	/** Spawns nothing that can tick: counts the entities of {@code type} near the origin, then discards them. */
	private static <T extends net.minecraft.world.entity.Entity> int takeAway(GameTestHelper helper, Class<T> type) {
		Level level = helper.getLevel();
		List<T> found = level.getEntitiesOfClass(type, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64));
		int n = found.size();
		found.forEach(net.minecraft.world.entity.Entity::discard);
		return n;
	}

	private static void clearTorex(Level level) {
		for(LCETorex t : new ArrayList<>(LCETorex.serverTorexes(level))) LCETorex.forget(t);
	}

	@GameTest(template = EMPTY)
	public static void aRodWithNoSpecialBlastIsAHundredBlockMK5(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
		takeAway(helper, EntityNukeExplosionMK5.class);
		takeAway(helper, EntityNukeTorex.class);
		rod("nu").nuke(helper.getLevel(), p);
		List<EntityNukeExplosionMK5> mk5 = helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class, new AABB(p).inflate(64));
		helper.assertTrue(mk5.size() == 1, "one MK5 blast, got " + mk5.size());
		// the rod passes float coordinates, which at the test structure's 13 million blocks out round to a whole block
		helper.assertTrue(Math.abs(mk5.get(0).getX() - (p.getX() + 0.5)) <= 1 && Math.abs(mk5.get(0).getY() - (p.getY() + 0.5)) <= 1, "the blast is at the block's centre");
		int torex = takeAway(helper, EntityNukeTorex.class);
		takeAway(helper, EntityNukeExplosionMK5.class);
		helper.assertTrue(torex == 1, "one mushroom cloud, got " + torex);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBalefireAndSolsticeBlastsAreTheirOwn(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
		takeAway(helper, EntityBalefire.class);
		takeAway(helper, EntityNukeTorex.class);
		rod("balefire").nuke(helper.getLevel(), p);
		List<EntityBalefire> bf = helper.getLevel().getEntitiesOfClass(EntityBalefire.class, new AABB(p).inflate(64));
		helper.assertTrue(bf.size() == 1 && bf.get(0).destructionRange == 280, "a balefire blast of 280: " + bf.size() + (bf.isEmpty() ? "" : " range " + bf.get(0).destructionRange));
		takeAway(helper, EntityBalefire.class);
		takeAway(helper, EntityNukeTorex.class);

		// the blazing rod blasts the same way
		rod("blazingbalefire").nuke(helper.getLevel(), p);
		helper.assertTrue(takeAway(helper, EntityBalefire.class) == 1, "blazing balefire also blasts with a balefire");
		takeAway(helper, EntityNukeTorex.class);

		// sa-326: a 50 block fleija blast and its cloud, at the base mod's blast speed
		takeAway(helper, EntityNukeExplosionMK3.class);
		rod("sa326").nuke(helper.getLevel(), p);
		List<EntityNukeExplosionMK3> mk3 = helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK3.class, new AABB(p).inflate(64));
		helper.assertTrue(mk3.size() == 1 && mk3.get(0).destructionRange == 50 && !mk3.get(0).waste && mk3.get(0).coefficient == 1.0F, "sa326 blast: " + mk3.size());
		helper.assertTrue(takeAway(helper, EntityCloudFleija.class) == 1, "and one fleija cloud");
		takeAway(helper, EntityNukeExplosionMK3.class);
		helper.succeed();
	}

	/**
	 * The digamma rod: everything living in the level is brought up to {@code min(50 - d/20, 9.99999999)} (the cap holds out to 800 blocks, further than a test can place a cow)
	 * digamma and never reduced. The MK5 beside it is marked for digamma fallout (D-3009, which finished
	 * the line D-3001 deferred); the advancement is {@code DEFERRED(ADV)}.
	 */
	@GameTest(template = EMPTY)
	public static void theDigammaRodBringsEveryoneUpToItsLevel(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
		Cow near = EntityType.COW.create(helper.getLevel());
		near.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		helper.getLevel().addFreshEntity(near);
		Cow haunted = EntityType.COW.create(helper.getLevel());
		haunted.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5 + 100);
		helper.getLevel().addFreshEntity(haunted);
		HbmLivingProps.setDigamma(haunted, 9.99999F);
		try {
			takeAway(helper, EntityNukeExplosionMK5.class);
			rod("dgomega").nuke(helper.getLevel(), p);
			float dn = HbmLivingProps.getDigamma(near);
			helper.assertTrue(dn >= 9.99F, "the nearest cow reads " + dn + " digamma, min(50 - 0.5/20, 9.99999999) is just under 10");
			helper.assertTrue(HbmLivingProps.getDigamma(haunted) >= 9.99F, "an entity above the level keeps its own");
			List<EntityNukeExplosionMK5> blasts = helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class,
					new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(64));
			helper.assertTrue(blasts.size() == 1, "a 50 block MK5 beside it, found " + blasts.size());
			helper.assertTrue(com.leafia.overwrite_contents.LeafiaFalloutVariants.DIGAMMA.equals(blasts.get(0).getFalloutVariant()),
					"its fallout is digamma, got " + blasts.get(0).getFalloutVariant());
			helper.assertTrue(takeAway(helper, EntityNukeExplosionMK5.class) == 1, "a 50 block MK5 beside it");
		} finally {
			near.discard();
			haunted.discard();
			takeAway(helper, EntityNukeExplosionMK5.class);
			clearTorex(helper.getLevel());
		}
		helper.succeed();
	}

	/**
	 * The digamma rod's blast walks a copy of the level's entities taken before it starts (D-3031, D-3025 #2): an entity
	 * that joins the level afterwards is not in what it walks, and one that was there is. A live view would show both.
	 */
	@GameTest(template = EMPTY)
	public static void theDigammaBlastWalksACopyOfTheEntityList(GameTestHelper helper) {
		BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
		Cow before = EntityType.COW.create(helper.getLevel());
		before.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		helper.getLevel().addFreshEntity(before);
		Cow after = EntityType.COW.create(helper.getLevel());
		try {
			Iterable<net.minecraft.world.entity.Entity> walked = LeafiaRodItem.digammaTargets(helper.getLevel());
			after.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 1.5);
			helper.getLevel().addFreshEntity(after);
			boolean sawBefore = false, sawAfter = false;
			for(net.minecraft.world.entity.Entity e : walked) {
				if(e == before) sawBefore = true;
				if(e == after) sawAfter = true;
			}
			helper.assertTrue(sawBefore, "the cow that was there is walked");
			helper.assertTrue(!sawAfter, "the cow that joined afterwards is not: the walk is a copy");
		} finally {
			before.discard();
			after.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void detonateReturnsItsRadiusWithoutGrowingTheField(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem nu = rod("nu");
		helper.assertTrue(nu.detonate(null, null) == 5F, "an ordinary rod's radius is 5");
		LeafiaRodItem bf = rod("balefire");
		float before = bf.detonateRadius;
		helper.assertTrue(bf.detonate(null, null) == before, "detonate without a world does not grow the radius");
		// with visuals only: no blast, no radiation, but the radius still grows by 2.5 for balefire
		BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
		bf.detonateVisualsOnly = true;
		try {
			takeAway(helper, EntityNukeTorex.class);
			float r = bf.detonate(helper.getLevel(), p);
			helper.assertTrue(r == before * 2.5F, "balefire's blast radius is 2.5 times the field: " + r);
			helper.assertTrue(bf.detonateRadius == before, "the field was grown by the call (D-3075): " + bf.detonateRadius);
			helper.assertTrue(takeAway(helper, EntityBalefire.class) == 0, "visuals only spawns no blast");
		} finally {
			bf.resetDetonate();
			takeAway(helper, EntityNukeTorex.class);
		}
		helper.assertTrue(bf.detonateRadius == 5F && !bf.detonateVisualsOnly, "resetDetonate puts it back");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBalefireRodsOutrankTheRestWhenTwoMelt(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack nu = stack("nu");
		ItemStack bf = stack("balefire");
		ItemStack bf2 = stack("blazingbalefire");
		ItemStack junk = new ItemStack(ModItems.billet_u238.get());
		helper.assertTrue(LeafiaRodItem.comparePriority(nu, bf) == bf, "the balefire rod melts first");
		helper.assertTrue(LeafiaRodItem.comparePriority(bf, nu) == bf, "in either order");
		helper.assertTrue(LeafiaRodItem.comparePriority(bf, bf2) == bf, "a tie goes to the first");
		helper.assertTrue(LeafiaRodItem.comparePriority(nu, null) == nu && LeafiaRodItem.comparePriority(junk, null) == null, "against nothing");
		helper.assertTrue(LeafiaRodItem.comparePriority(junk, nu) == nu && LeafiaRodItem.comparePriority(nu, junk) == nu && LeafiaRodItem.comparePriority(junk, junk) == null, "a non-rod never wins");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBalefireRodsMeltIntoBalecoriumWhenThereIsSome(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		helper.assertTrue(rod("nu").corium() == com.hbm.blocks.ModBlocks.block_corium.get(), "an ordinary rod melts into corium");
		helper.assertTrue(rod("balefire").coriumName().equals("fluid_balecorium") && rod("blazingbalefire").coriumName().equals("fluid_balecorium"), "the balefire rods name balecorium");
		// D-3004: the fluid exists whenever the world fluids do, and the rods find it under its registry name
		boolean registered = BuiltInRegistries.BLOCK.containsKey(rl("balecorium_fluid"));
		helper.assertTrue(registered == com.leafia.config.LeafiaFeatures.worldFluids, "balecorium registered iff the world fluids are");
		net.minecraft.world.level.block.Block expected = registered
				? BuiltInRegistries.BLOCK.get(rl("balecorium_fluid")) : com.hbm.blocks.ModBlocks.block_corium.get();
		helper.assertTrue(rod("balefire").corium() == expected && rod("blazingbalefire").corium() == expected,
				"the balefire rods melt into " + expected + ", not " + rod("balefire").corium());
		helper.succeed();
	}

	// ---------------------------------------------------------------- hazards

	@GameTest(template = EMPTY)
	public static void aRodsRadiationIsHalfItsMaterialsAndItsNeutronsFollowTheFlux(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack u = stack("u238");
		// ItemRads.uranium238 is (alpha 0.25, radon 0.1); a rod gives half
		near(helper, HazardSystem.getHazardLevelFromStack(u, Alpha.INSTANCE), 0.125, "u238 alpha");
		near(helper, HazardSystem.getHazardLevelFromStack(u, Neutrons.INSTANCE), 0, "no flux, no neutrons (the -1 placeholder reads zero)");
		CompoundTag t = new CompoundTag();
		t.putDouble("incoming", 100);
		LeafiaRodItem.putData(u, t);
		near(helper, HazardSystem.getHazardLevelFromStack(u, Neutrons.INSTANCE), Math.pow(100, 0.65) / 2, "100 flux gives 100^0.65 / 2 neutrons");
		// a rod that is not a hazard material still has the neutron entry
		ItemStack lead = stack("lead");
		LeafiaRodItem.putData(lead, t.copy());
		near(helper, HazardSystem.getHazardLevelFromStack(lead, Neutrons.INSTANCE), Math.pow(100, 0.65) / 2, "lead's neutrons");
		// digamma and blinding
		near(helper, HazardSystem.getHazardLevelFromStack(stack("dgomega"), com.leafia.init.LeafiaHazards.DIGAMMA), 0.333, "dgomega digamma");
		helper.assertTrue(HazardSystem.getHazardLevelFromStack(stack("sa326"), com.hbm.hazard.HazardRegistry.BLINDING) == 110, "sa326 blinds");
		// the declared-but-not-wired ones are listed, not lost
		helper.assertTrue(rod("lithium").unwired().equals(List.of("alkaline")) && rod("lead").unwired().equals(List.of("toxic")), "lithium's alkaline and lead's toxic are listed as unwired");
		helper.assertTrue(rod("lithium").alkaline == 1 && rod("lead").toxic == 2, "their values");
		helper.succeed();
	}

	// ---------------------------------------------------------------- crafting

	private static Optional<RecipeHolder<CraftingRecipe>> recipeFor(GameTestHelper helper, List<ItemStack> grid, int w, int h) {
		return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(w, h, grid), helper.getLevel());
	}

	@GameTest(template = EMPTY)
	public static void theEmptyRodIsFourOfAZirconiumBilletOverANugget(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		Item billet = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("hbm", "billet_zirconium")).orElse(null);
		Item nugget = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("hbm", "nugget_zirconium")).orElse(null);
		helper.assertTrue(billet != null && nugget != null, "the base mod has zirconium billets and nuggets");
		var found = recipeFor(helper, List.of(new ItemStack(billet), new ItemStack(nugget), new ItemStack(billet)), 1, 3);
		helper.assertTrue(found.isPresent(), "O over I over O is a recipe");
		ItemStack out = found.get().value().assemble(CraftingInput.of(1, 3, List.of(new ItemStack(billet), new ItemStack(nugget), new ItemStack(billet))), helper.getLevel().registryAccess());
		helper.assertTrue(out.getItem() == LeafiaRods.leafRod.get() && out.getCount() == 4, "four empty rods: " + out);
		helper.succeed();
	}

	/** Every rod with a base item: the empty rod and that item, shapeless, make the rod. */
	@GameTest(template = EMPTY)
	public static void everyRodIsMadeFromTheEmptyRodAndItsBase(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		int made = 0;
		for(LeafiaRodItem r : LeafiaRodItem.fromResourceMap.values()) {
			if(r.baseItem == null) continue;
			ItemStack base = r.baseStack();
			if(base.isEmpty()) continue; // the base item's switch is off
			CraftingInput in = CraftingInput.of(2, 1, List.of(new ItemStack(LeafiaRods.leafRod.get()), base));
			var found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, in, helper.getLevel());
			helper.assertTrue(found.isPresent(), r.functionId + ": the empty rod and " + base + " make nothing");
			ItemStack out = found.get().value().assemble(in, helper.getLevel().registryAccess());
			helper.assertTrue(out.getItem() == r && out.getCount() == 1, r.functionId + ": made " + out);
			made++;
		}
		helper.assertTrue(made == 56, "recipes exercised: " + made);

		// the two DNA recipes
		var cat = recipeFor(helper, List.of(new ItemStack(LeafiaRods.leafRod.get()), new ItemStack(LeafiaResourceItems.get("dna_felid").get()), new ItemStack(ModItems.heart_piece.get())), 3, 1);
		helper.assertTrue(cat.isPresent() && cat.get().value().assemble(CraftingInput.of(3, 1, List.of(new ItemStack(LeafiaRods.leafRod.get()), new ItemStack(LeafiaResourceItems.get("dna_felid").get()), new ItemStack(ModItems.heart_piece.get()))),
				helper.getLevel().registryAccess()).getItem() == rod("catsmilium"), "empty rod + felid DNA + heart piece = catsmilium");
		var conf = recipeFor(helper, List.of(new ItemStack(LeafiaRods.leafRod.get()), new ItemStack(LeafiaResourceItems.get("dna_canid").get())), 2, 1);
		helper.assertTrue(conf.isPresent() && conf.get().value().assemble(CraftingInput.of(2, 1, List.of(new ItemStack(LeafiaRods.leafRod.get()), new ItemStack(LeafiaResourceItems.get("dna_canid").get()))),
				helper.getLevel().registryAccess()).getItem() == rod("confusium"), "empty rod + canid DNA = confusium");
		helper.succeed();
	}

	/**
	 * The depleted rods are made from the waste at damage 1, which here is "still cooling"; a waste
	 * that has finished cooling is not an ingredient.
	 */
	@GameTest(template = EMPTY)
	public static void aDepletedRodNeedsStillCoolingWaste(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack hot = new ItemStack(ModItems.waste_uranium.get());
		ItemDepletedFuel.setCooling(hot, true);
		ItemStack cold = new ItemStack(ModItems.waste_uranium.get());
		var yes = recipeFor(helper, List.of(new ItemStack(LeafiaRods.leafRod.get()), hot), 2, 1);
		helper.assertTrue(yes.isPresent() && yes.get().value().assemble(CraftingInput.of(2, 1, List.of(new ItemStack(LeafiaRods.leafRod.get()), hot)), helper.getLevel().registryAccess()).getItem() == rod("depleteduranium"),
				"cooling waste makes the depleted uranium rod");
		helper.assertTrue(recipeFor(helper, List.of(new ItemStack(LeafiaRods.leafRod.get()), cold), 2, 1).isEmpty(), "waste that has cooled does not");
		// and the addon's own waste, which is the same state under the same rule
		ItemStack bred = new ItemStack(LeafiaRods.depleted("waste_u238"));
		ItemDepletedFuel.setCooling(bred, true);
		var bredRod = recipeFor(helper, List.of(new ItemStack(LeafiaRods.leafRod.get()), bred), 2, 1);
		helper.assertTrue(bredRod.isPresent() && bredRod.get().value().assemble(CraftingInput.of(2, 1, List.of(new ItemStack(LeafiaRods.leafRod.get()), bred)), helper.getLevel().registryAccess()).getItem() == rod("depleteduranium238"),
				"cooling bred uranium makes the bred uranium rod");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aRodAloneInTheGridUncraftsToItsBaseAndHandsBackTheAssembly(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack nu = stack("nu");
		CraftingInput in = CraftingInput.of(1, 1, List.of(nu));
		var found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, in, helper.getLevel());
		helper.assertTrue(found.isPresent() && found.get().value() instanceof LeafiaRodCrafting, "a rod alone matches the uncrafting recipe");
		ItemStack out = found.get().value().assemble(in, helper.getLevel().registryAccess());
		helper.assertTrue(out.getItem() == ModItems.billet_uranium.get() && out.getCount() == 1, "the base billet comes back: " + out);
		var left = found.get().value().getRemainingItems(in);
		helper.assertTrue(left.size() == 1 && left.get(0).getItem() == LeafiaRods.leafRod.get(), "and the empty assembly: " + left);

		// the depleted rod gives its waste back still cooling
		ItemStack dep = stack("depleteduranium");
		ItemStack waste = LeafiaRodCraftingProbe.assemble(helper, dep);
		helper.assertTrue(waste.getItem() == ModItems.waste_uranium.get() && ItemDepletedFuel.isCooling(waste), "the depleted rod uncrafts to cooling waste: " + waste);

		// two rods in the grid: no
		helper.assertTrue(LeafiaRodCraftingProbe.match(helper, List.of(stack("nu"), stack("nu"))) == false, "two rods do not uncraft");
		helper.assertTrue(LeafiaRodCraftingProbe.match(helper, List.of(new ItemStack(ModItems.billet_u238.get()))) == false, "a billet is not a rod");
		helper.assertTrue(LeafiaRodCraftingProbe.match(helper, List.of(ItemStack.EMPTY, stack("nu"), ItemStack.EMPTY)) == true, "empty slots do not count");
		helper.succeed();
	}

	/** At or past five percent of its life a rod will not uncraft; the boundary is upstream's. */
	@GameTest(template = EMPTY)
	public static void aBurntRodWillNotUncraft(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem nu = rod("nu");
		ItemStack s = stack("nu");
		CompoundTag t = new CompoundTag();
		t.putDouble("depletion", nu.life * 0.05 - 1);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(LeafiaRodCraftingProbe.match(helper, List.of(s)), "just under 5% still uncrafts");
		t.putDouble("depletion", nu.life * 0.05);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(!LeafiaRodCraftingProbe.match(helper, List.of(s)), "exactly 5% matches no more (>=)");
		// ...and (D-3075) the result step agrees with the match instead of using a strict >
		helper.assertTrue(LeafiaRodCraftingProbe.assemble(helper, s).isEmpty(), "at exactly 5% the result step refuses too (>=)");
		t.putDouble("depletion", nu.life * 0.05 + 1);
		LeafiaRodItem.putData(s, t);
		helper.assertTrue(LeafiaRodCraftingProbe.assemble(helper, s).isEmpty(), "past 5% yields nothing");
		// a rod with no life is never refused
		ItemStack dep = stack("depleteduranium");
		CompoundTag dt = new CompoundTag();
		dt.putDouble("depletion", 1e9);
		LeafiaRodItem.putData(dep, dt);
		helper.assertTrue(LeafiaRodCraftingProbe.match(helper, List.of(dep)), "a depleted rod has no life to burn");
		// a rod with no base item no longer matches (D-3075; it used to match and yield nothing)
		helper.assertTrue(!LeafiaRodCraftingProbe.match(helper, List.of(stack("blazingbalefire"))) && LeafiaRodCraftingProbe.assemble(helper, stack("blazingbalefire")).isEmpty(),
				"blazing balefire must not match the uncrafting recipe");
		helper.succeed();
	}

	/** Straight calls on the recipe class, bypassing the recipe manager. */
	private static final class LeafiaRodCraftingProbe {
		static final LeafiaRodCrafting RECIPE = new LeafiaRodCrafting(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);

		static boolean match(GameTestHelper helper, List<ItemStack> grid) {
			return RECIPE.matches(CraftingInput.of(grid.size(), 1, grid), helper.getLevel());
		}

		static ItemStack assemble(GameTestHelper helper, ItemStack one) {
			return RECIPE.assemble(CraftingInput.of(1, 1, List.of(one)), helper.getLevel().registryAccess());
		}
	}

	// ---------------------------------------------------------------- the five depleted fuels

	@GameTest(template = EMPTY)
	public static void theFiveDepletedFuelsAreCoolingWaste(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		String[] names = {"waste_u238", "waste_mes", "waste_hes", "waste_sch", "waste_sol"};
		for(String n : names) {
			Item i = LeafiaRods.depleted(n);
			helper.assertTrue(i instanceof ItemDepletedFuel, n + " is an ItemDepletedFuel");
			ItemStack s = new ItemStack(i);
			helper.assertTrue(((ItemDepletedFuel) i).getColor(s) == 0xFFFFFF, n + " is white once cooled");
			ItemDepletedFuel.setCooling(s, true);
			helper.assertTrue(((ItemDepletedFuel) i).getColor(s) == 0xFFBFA5, n + " is tan while cooling");
			helper.assertTrue(i.hasCustomEntity(s), n + " makes the indestructible item entity");
			JsonObject m = json("assets/leafia/models/item/" + n + ".json");
			String tex = m.getAsJsonObject("textures").get("layer0").getAsString();
			helper.assertTrue(tex.equals(n.equals("waste_u238") ? "hbm:item/waste_uranium" : "hbm:item/waste_schrabidium"), n + " texture " + tex);
			helper.assertTrue(exists("assets/hbm/textures/item/" + tex.substring(tex.indexOf('/') + 1) + ".png"), n + "'s texture exists in the base mod");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the models

	private static final String[] SIDES = {"south", "north", "up", "down", "west", "east"};

	/** All 59 rods have a model whose every face is a well-formed flat quad over a texture that exists. */
	@GameTest(template = EMPTY)
	public static void everyRodModelIsWellFormed(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		List<String> problems = new ArrayList<>();
		for(LeafiaRodItem r : LeafiaRodItem.fromResourceMap.values()) {
			String id = BuiltInRegistries.ITEM.getKey(r).getPath();
			String path = "assets/leafia/models/item/" + id + ".json";
			if(!exists(path)) { problems.add(id + ": no model"); continue; }
			JsonObject m = json(path);
			boolean generated = m.has("parent");
			JsonObject tex = m.getAsJsonObject("textures");
			for(var e : tex.entrySet()) {
				String t = e.getValue().getAsString();
				int c = t.indexOf(':');
				if(!exists("assets/" + t.substring(0, c) + "/textures/" + t.substring(c + 1) + ".png")) problems.add(id + ": texture " + e.getKey() + " = " + t + " does not exist");
			}
			boolean glow = r.meltingPoint != 0;
			if(generated) {
				// catsmilium and confusium: item/generated over the photograph, glow as layer1
				boolean hasLayer1 = tex.has("layer1");
				if(hasLayer1 != glow) problems.add(id + ": glow layer " + hasLayer1 + ", melting point " + r.meltingPoint);
				if(!r.specialRodModel) problems.add(id + ": generated model on a rod that is not special");
				continue;
			}
			JsonArray els = m.getAsJsonArray("elements");
			int tinted = 0;
			for(JsonElement je : els) {
				JsonObject el = je.getAsJsonObject();
				JsonArray f = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
				for(var face : el.getAsJsonObject("faces").entrySet()) {
					String dir = face.getKey();
					JsonObject fo = face.getValue().getAsJsonObject();
					JsonArray uv = fo.getAsJsonArray("uv");
					String ft = fo.get("texture").getAsString();
					if(!ft.startsWith("#") || !tex.has(ft.substring(1))) problems.add(id + ": face texture " + ft + " is not a declared texture");
					for(JsonElement u : uv) if(u.getAsDouble() < 0 || u.getAsDouble() > 16) problems.add(id + ": uv outside 0..16 " + uv);
					int axis = switch(dir) { case "south", "north" -> 2; case "up", "down" -> 1; default -> 0; };
					boolean painted = ft.equals("#billet") || ft.equals("#glow");
					if(painted && f.get(axis).getAsDouble() != to.get(axis).getAsDouble()) problems.add(id + ": a painted " + dir + " face on an element that is not flat in that axis");
					if(fo.has("tintindex")) { tinted++; if(fo.get("tintindex").getAsInt() != 1) problems.add(id + ": tint index " + fo.get("tintindex")); }
					boolean known = false;
					for(String s : SIDES) if(s.equals(dir)) known = true;
					if(!known) problems.add(id + ": face " + dir);
				}
			}
			if((tinted > 0) != glow) problems.add(id + ": " + tinted + " glow faces for melting point " + r.meltingPoint);
			if(glow && tinted != 2) problems.add(id + ": glow faces " + tinted);
			if(!m.has("display") || !m.get("gui_light").getAsString().equals("front")) problems.add(id + ": no display or gui_light");
			if(!r.specialRodModel && !tex.has("billet")) problems.add(id + ": no base item texture");
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " model problems: " + problems);
		helper.succeed();
	}

	private static boolean sameFace(JsonObject element, String dir, double[] from, double[] to, double[] uv) {
		JsonObject faces = element.getAsJsonObject("faces");
		if(faces.size() != 1 || !faces.has(dir)) return false;
		for(int i = 0; i < 3; i++)
			if(element.getAsJsonArray("from").get(i).getAsDouble() != from[i] || element.getAsJsonArray("to").get(i).getAsDouble() != to[i]) return false;
		JsonArray u = faces.getAsJsonObject(dir).getAsJsonArray("uv");
		for(int i = 0; i < 4; i++) if(u.get(i).getAsDouble() != uv[i]) return false;
		return true;
	}

	/**
	 * Hand-derived from {@code LeafiaRodRender} for one (type, purity), not from the generator: the
	 * first thing the brush paints is the top outline, {@code setBrush(7,3,2,1); set(7,0,2,1)} - which,
	 * drawing the rod, nudges rows 0 and 1 to 1 and 2 - painted as the front, the back, the top and the
	 * two sides in that order. The "I" icon of an isotope is {@code set(1,12,3,1)}, which for the icon
	 * lifts the row by one and samples one row down.
	 */
	@GameTest(template = EMPTY)
	public static void theBrushPaintsWhatTheRendererPainted(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		JsonArray els = json("assets/leafia/models/item/leafia_rod_u238.json").getAsJsonArray("elements");
		int first = -1;
		for(int i = 0; i < els.size(); i++) {
			if(els.get(i).getAsJsonObject().getAsJsonObject("faces").entrySet().iterator().next().getValue().getAsJsonObject().get("texture").getAsString().equals("#billet")) { first = i; break; }
		}
		helper.assertTrue(first > 0, "the brush's first face follows the bracket's elements: " + first);
		helper.assertTrue(sameFace(els.get(first).getAsJsonObject(), "south", new double[] {7, 14, 8.5}, new double[] {9, 15, 8.5}, new double[] {7, 3, 9, 4}), "front of the top outline");
		helper.assertTrue(sameFace(els.get(first + 1).getAsJsonObject(), "north", new double[] {7, 14, 7.5}, new double[] {9, 15, 7.5}, new double[] {9, 3, 7, 4}), "back of the top outline");
		helper.assertTrue(sameFace(els.get(first + 2).getAsJsonObject(), "up", new double[] {7, 15, 7.5}, new double[] {9, 15, 8.5}, new double[] {7, 3, 9, 4}), "top of the top outline");
		helper.assertTrue(sameFace(els.get(first + 3).getAsJsonObject(), "west", new double[] {7, 14, 7.5}, new double[] {7, 15, 8.5}, new double[] {7, 3, 8, 4}), "left of the top outline");
		helper.assertTrue(sameFace(els.get(first + 4).getAsJsonObject(), "east", new double[] {9, 14, 7.5}, new double[] {9, 15, 8.5}, new double[] {8, 3, 9, 4}), "right of the top outline");
		boolean icon = false;
		for(JsonElement e : els) icon |= sameFace(e.getAsJsonObject(), "south", new double[] {1, 4, 8.5}, new double[] {4, 5, 8.5}, new double[] {1, 12, 4, 13});
		helper.assertTrue(icon, "the isotope's I icon, front face of the first bar");

		// a depleted rod starts from its own window onto the waste texture: setBrush(7,1,2,1)
		JsonArray dep = json("assets/leafia/models/item/leafia_rod_depleteduranium.json").getAsJsonArray("elements");
		boolean depFirst = false;
		for(JsonElement e : dep) depFirst |= sameFace(e.getAsJsonObject(), "south", new double[] {7, 14, 8.5}, new double[] {9, 15, 8.5}, new double[] {7, 1, 9, 2});
		helper.assertTrue(depFirst, "a depleted rod's top outline reads (7,1)");
		// and a RAW rod has no icon: the isotope's bar is absent from nu
		JsonArray raw = json("assets/leafia/models/item/leafia_rod_nu.json").getAsJsonArray("elements");
		boolean rawIcon = false;
		for(JsonElement e : raw) rawIcon |= sameFace(e.getAsJsonObject(), "south", new double[] {1, 4, 8.5}, new double[] {4, 5, 8.5}, new double[] {1, 12, 4, 13});
		helper.assertTrue(!rawIcon, "a raw rod draws no icon");

		// element counts, so a changed brush is noticed
		int counts = 0;
		for(String f : new String[] {"u238", "nu", "meu235", "ra226be", "debug", "depleteduranium", "balefire", "dgomega"})
			counts += json("assets/leafia/models/item/leafia_rod_" + f + ".json").getAsJsonArray("elements").size();
		helper.assertTrue(counts == EXPECTED_ELEMENT_COUNTS, "element counts of eight models: " + counts);
		helper.succeed();
	}

	/** The sum of the element counts of eight models: a regression pin for the generator, looked at in a client run. */
	private static final int EXPECTED_ELEMENT_COUNTS = 730;

	// ---------------------------------------------------------------- the glow

	@GameTest(template = EMPTY)
	public static void theGlowFollowsHeatPastHalfTheMeltingPoint(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem nu = rod("nu"); // melts at 2865
		ItemStack s = stack("nu");
		near(helper, LeafiaRodRender.glowOpacity(nu, s), 0, "a rod with no tag does not glow");
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", 2865 / 2d);
		LeafiaRodItem.putData(s, t);
		near(helper, LeafiaRodRender.glowOpacity(nu, s), 0, "at exactly half the melting point there is no glow yet");
		t.putDouble("heat", 2865 * 0.75);
		LeafiaRodItem.putData(s, t);
		near(helper, LeafiaRodRender.glowOpacity(nu, s), 0.5, "three quarters of the way is half opacity");
		helper.assertTrue(LeafiaRodRender.glowColour(nu, s) == ((128 << 24) | 0xFFFFFF), "colour " + Integer.toHexString(LeafiaRodRender.glowColour(nu, s)));
		t.putDouble("heat", 99999);
		LeafiaRodItem.putData(s, t);
		near(helper, LeafiaRodRender.glowOpacity(nu, s), 1, "clamped at 1");
		helper.assertTrue(LeafiaRodRender.glowColour(nu, s) == 0xFFFFFFFF, "full colour");
		t.putDouble("heat", -50);
		LeafiaRodItem.putData(s, t);
		near(helper, LeafiaRodRender.glowOpacity(nu, s), 0, "a frozen rod does not glow");
		// no melting point: never
		ItemStack u = stack("u238");
		t.putDouble("heat", 99999);
		LeafiaRodItem.putData(u, t);
		near(helper, LeafiaRodRender.glowOpacity(rod("u238"), u), 0, "a rod with no melting point has no glow");
		helper.succeed();
	}

	// ---------------------------------------------------------------- small pure functions

	@GameTest(template = EMPTY)
	public static void heatMultipliersFormatAsMultiplyOrDivide(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		LeafiaRodItem r = rod("nu");
		helper.assertTrue(r.formatHeatMultiplier(2).equals("×2"), r.formatHeatMultiplier(2));
		helper.assertTrue(r.formatHeatMultiplier(65).equals("×65"), r.formatHeatMultiplier(65));
		helper.assertTrue(r.formatHeatMultiplier(0.5).equals("/2"), r.formatHeatMultiplier(0.5));
		helper.assertTrue(r.formatHeatMultiplier(1 / 1.5).equals("/1.5"), r.formatHeatMultiplier(1 / 1.5));
		helper.assertTrue(r.formatHeatMultiplier(1 / 45d).equals("/45"), r.formatHeatMultiplier(1 / 45d));
		helper.assertTrue(r.formatHeatMultiplier(1.75).equals("×1.75"), r.formatHeatMultiplier(1.75));
		helper.succeed();
	}

	/** Each tooltip key a rod resolves is in the lang file. */
	@GameTest(template = EMPTY)
	public static void everyRodTooltipKeyIsTranslated(GameTestHelper helper) {

		JsonObject lang = json("assets/leafia/lang/en_us.json");
		String[] keys = {"item.leafiarod", "item.leafiarodempty", "info.leafiarod.empty", "item.leafiarod.decayheat", "item.leafiarod.decays", "item.leafiarod.heatfunc",
				"item.leafiarod.heatfunc_shift", "item.leafiarod.life", "item.leafiarod.meltingpt", "item.leafiarod.moderated", "item.leafiarod.pref.all",
				"item.leafiarod.pref.fast", "item.leafiarod.reac.in", "item.leafiarod.reac.out", "item.leafiarod.temp",
				"item.leafiarod.status.0", "item.leafiarod.status.1", "item.leafiarod.status.2", "item.leafiarod.status.3", "item.leafiarod.status.4",
				"item.leafiarod.status.0dg", "item.leafiarod.status.1dg", "item.leafiarod.status.2dg", "item.leafiarod.status.3dg", "item.leafiarod.status.4dg",
				"item.leafia.waste_u238", "item.leafia.waste_mes", "item.leafia.waste_hes", "item.leafia.waste_sch", "item.leafia.waste_sol",
				"item.leafia.powder_osmiridium", "item.leafia.nugget_u238m2",
				"desc.leafia._reactortier.desc.title", "desc.leafia._reactortier.desc.next", "desc.leafia._reactortier.desc.previous", "desc.leafia._reactortier.desc.shift"};
		List<String> missing = new ArrayList<>();
		for(String k : keys) if(!lang.has(k)) missing.add(k);
		helper.assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
		helper.assertTrue(lang.get("item.leafiarod").getAsString().equals("Nuclear Fuel Rod \"%s\""), "the rod's name format");
		helper.assertTrue(lang.get("item.leafia.waste_u238").getAsString().equals("Bred Uranium-238 Fuel"), "waste_u238's name");
		// the names of a rod and of the empty assembly, through the real translation path
		if(on()) {
			Component name = new ItemStack(rod("heu235")).getHoverName();
			helper.assertTrue(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().equals("item.leafiarod")
					&& tc.getArgs()[0].equals("HEU-235"), "a rod is named by its label: " + name);
			helper.assertTrue(((net.minecraft.network.chat.contents.TranslatableContents) new ItemStack(LeafiaRods.leafRod.get()).getHoverName().getContents()).getKey().equals("item.leafiarodempty"), "the empty rod's name");
		}
		for(String tier : new String[] {"zirnox", "pwr", "rbmk", "watz", "apr", "fusion", "icf", "ams", "dfc"})
			helper.assertTrue(lang.has("desc.leafia._reactortier.name." + tier), "no name for tier " + tier);
		for(String cls : new String[] {"fission", "fusion", "particle", "core"})
			helper.assertTrue(lang.has("desc.leafia._reactortier.class." + cls), "no name for class " + cls);
		helper.succeed();
	}

	// ---------------------------------------------------------------- reactor tiers

	@GameTest(template = EMPTY)
	public static void theReactorTiersAreUpstreamsNineInOrder(GameTestHelper helper) {

		ReactorTiers.register();
		helper.assertTrue(ReactorTiers.names.equals(List.of("zirnox", "pwr", "rbmk", "watz", "apr", "fusion", "icf", "ams", "dfc")), "tier names " + ReactorTiers.names);
		helper.assertTrue(ReactorTiers.classes.equals(List.of("fission", "fission", "fission", "fission", "particle", "fusion", "fusion", "core", "core")), "tier classes " + ReactorTiers.classes);
		Item rbmk = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "rbmk_rod"));
		helper.assertTrue(rbmk != net.minecraft.world.item.Items.AIR && ReactorTiers.redirection.get(rbmk) == 2, "the RBMK fuel channel is tier 3 (index 2)");
		Item zirnox = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "machine_zirnox"));
		helper.assertTrue(zirnox != net.minecraft.world.item.Items.AIR && ReactorTiers.redirection.get(zirnox) == 0, "the ZIRNOX is tier 1");
		Item dfc = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "dfc_core"));
		helper.assertTrue(dfc != net.minecraft.world.item.Items.AIR && ReactorTiers.redirection.get(dfc) == 8, "the DFC core is tier 9");
		if(LeafiaFeatures.realerSim)
			helper.assertTrue(ReactorTiers.redirection.get(BuiltInRegistries.ITEM.get(rl("rbmk_rod_realersim"))) == 2, "RealerSim's channel joins the RBMK tier");
		// what is not registered is listed, not lost; the PWR's core blocks (D-3006)
		// and its terminal and hatches (D-3010) are, and join the PWR tier
		if(LeafiaFeatures.pwrReactor()) {
			helper.assertTrue(ReactorTiers.unresolved.getOrDefault("pwr", List.of()).isEmpty(), "unresolved pwr: " + ReactorTiers.unresolved.get("pwr"));
			helper.assertTrue(ReactorTiers.redirection.get(BuiltInRegistries.ITEM.get(rl("lwr_element"))) == 1, "the PWR fuel rod is tier 2");
			helper.assertTrue(ReactorTiers.redirection.get(BuiltInRegistries.ITEM.get(rl("reactor_hatch_alt"))) == 1, "the old hatch is tier 2");
		}
		// the APR (D-3027) joins its tier, its component block through every variant item
		if(LeafiaFeatures.aprReactor()) {
			helper.assertTrue(ReactorTiers.unresolved.getOrDefault("apr", List.of()).isEmpty(), "unresolved apr: " + ReactorTiers.unresolved.get("apr"));
			for(String id : new String[] {"apr_core", "apr_port", "apr_outlet", "apr_component_hull", "apr_component_coil"})
				helper.assertTrue(ReactorTiers.redirection.get(BuiltInRegistries.ITEM.get(rl(id))) == 4, id + " is tier 5");
		} else
			helper.assertTrue(ReactorTiers.unresolved.getOrDefault("apr", List.of()).size() == 4, "unresolved apr: " + ReactorTiers.unresolved.get("apr"));
		// idempotent: a second call does not double anything
		int size = ReactorTiers.redirection.size();
		ReactorTiers.register();
		helper.assertTrue(ReactorTiers.redirection.size() == size && ReactorTiers.names.size() == 9, "register() twice");
		helper.succeed();
	}

	/** The tooltip lines: one title, then the hint to hold shift, or the two neighbours. */
	@GameTest(template = EMPTY)
	public static void theTierLineShowsTheNeighboursOnShift(GameTestHelper helper) {

		ReactorTiers.register();
		Item rbmk = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "rbmk_rod"));
		Item zirnox = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "machine_zirnox"));
		Item dfc = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "dfc_core"));
		List<Component> l = new ArrayList<>();
		MachineTooltip.addInfoASM(rbmk, l, false);
		helper.assertTrue(l.size() == 2, "title and hint: " + l.size());
		l.clear();
		MachineTooltip.addInfoASM(rbmk, l, true);
		helper.assertTrue(l.size() == 3, "title, next and previous: " + l.size());
		l.clear();
		MachineTooltip.addInfoASM(zirnox, l, true);
		helper.assertTrue(l.size() == 2, "the first tier has only a next: " + l.size());
		l.clear();
		MachineTooltip.addInfoASM(dfc, l, true);
		helper.assertTrue(l.size() == 2, "the last tier has only a previous: " + l.size());
		l.clear();
		MachineTooltip.addInfoASM(net.minecraft.world.item.Items.STONE, l, true);
		helper.assertTrue(l.isEmpty(), "an item that is no reactor gets nothing");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the two CE-only items and the rows held for them

	@GameTest(template = EMPTY)
	public static void theTwoCEItemsAreTheAddonsOwn(GameTestHelper helper) {

		boolean on = LeafiaFeatures.resourceItems;
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(rl("powder_osmiridium")) == on && BuiltInRegistries.ITEM.containsKey(rl("nugget_u238m2")) == on,
				"the CE items follow features.resourceItems (" + on + ")");
		helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("hbm", "powder_osmiridium"))
				&& !BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("hbm", "nugget_u238m2")), "the base mod does not get them");
		if(!on) {
			helper.assertTrue(LeafiaItems.powder_osmiridium == null && LeafiaItems.nugget_u238m2 == null, "holders are null with the switch off");
			helper.succeed();
			return;
		}
		// the powder is in the parts tab (the addon's one), the nugget hidden like the base mod's ingot beside it
		List<Item> tab = new ArrayList<>();
		for(Supplier<?> s : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN)) if(s.get() instanceof Item i) tab.add(i);
		helper.assertTrue(tab.contains(LeafiaItems.powder_osmiridium.get()) && !tab.contains(LeafiaItems.nugget_u238m2.get()), "powder in the tab, nugget hidden");
		for(String n : new String[] {"powder_osmiridium", "nugget_u238m2"}) {
			helper.assertTrue(exists("assets/leafia/textures/item/" + n + ".png") && exists("assets/leafia/models/item/" + n + ".json"), n + " has a texture and a model");
		}
		JsonObject tagJson = json("data/c/tags/item/dusts/osmiridium.json");
		helper.assertTrue(tagJson.getAsJsonArray("values").get(0).getAsJsonObject().get("id").getAsString().equals("leafia:powder_osmiridium"), "OSMIRIDIUM.dust(powder_osmiridium) is c:dusts/osmiridium");
		helper.succeed();
	}

	/** AddonSmeltingRecipes' first row: powder_osmiridium to ingot_osmiridium, 5 xp, a furnace's 200 ticks. */
	@GameTest(template = EMPTY)
	public static void theOsmiridiumPowderSmelts(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(LeafiaItems.powder_osmiridium.get())), helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "powder_osmiridium does not smelt");
		var r = recipe.get().value();
		helper.assertTrue(r.getResultItem(helper.getLevel().registryAccess()).getItem() == ModItems.ingot_osmiridium.get(), "it smelts into the base mod's ingot");
		helper.assertTrue(r.getExperience() == 5.0F && r.getCookingTime() == 200, r.getExperience() + " xp, " + r.getCookingTime() + " ticks");
		helper.succeed();
	}

	/** The last row: the electronium pack, at upstream's numbers, with the two new items in. */
	@GameTest(template = EMPTY)
	public static void theElectroniumPackIsCraftableInTheChemicalPlant(GameTestHelper helper) {

		if(!(LeafiaFeatures.batteryPacks && LeafiaFeatures.resourceItems)) { helper.succeed(); return; }

		GenericRecipe r = ChemicalPlantRecipes.INSTANCE.recipeNameMap.get("chem.leafia.battery_electro");
		helper.assertTrue(r != null, "chem.leafia.battery_electro is not in the chemical plant's list");
		helper.assertTrue(r.duration == 100 && r.power == 1_000_000, "100 ticks at 1,000,000: " + r.duration + " / " + r.power);
		helper.assertTrue(r.inputItem.length == 3, "three item inputs");
		helper.assertTrue(r.inputItem[0] instanceof com.hbm.inventory.RecipesCommon.ComparableStack a && a.toStack().getItem() == ModItems.ingot_electronium.get() && a.stacksize == 64, "64 electronium ingots");
		helper.assertTrue(r.inputItem[1] instanceof com.hbm.inventory.RecipesCommon.ComparableStack b && b.toStack().getItem() == LeafiaItems.powder_osmiridium.get() && b.stacksize == 48, "48 osmiridium powder");
		helper.assertTrue(r.inputItem[2] instanceof com.hbm.inventory.RecipesCommon.ComparableStack c && c.toStack().getItem() == LeafiaItems.nugget_u238m2.get() && c.stacksize == 16, "16 uranium-238m2 nuggets");
		helper.assertTrue(r.inputFluid.length == 1 && r.inputFluid[0].type == Fluids.PERFLUOROMETHYL_COLD && r.inputFluid[0].fill == 16_000, "16000 cold perfluoromethyl in");
		helper.assertTrue(r.outputFluid.length == 1 && r.outputFluid[0].type == Fluids.PERFLUOROMETHYL_HOT && r.outputFluid[0].fill == 16_000, "16000 hot perfluoromethyl out");
		helper.assertTrue(r.outputItem.length == 1 && r.outputItem[0] instanceof com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput co
				&& co.stack.getItem() == LeafiaItems.battery_packs.get(com.leafia.contents.control.battery.AddonEnumBatteryPack.BATTERY_ELECTRO).get() && co.stack.getCount() == 1, "gives the electronium pack");
		// it is the sixth pack with a recipe
		int packs = 0;
		for(String n : new String[] {"desh", "saturnite", "euphemium", "slop", "spk", "electro"})
			if(ChemicalPlantRecipes.INSTANCE.recipeNameMap.get("chem.leafia.battery_" + n) != null) packs++;
		helper.assertTrue(packs == (LeafiaFeatures.addonFluids ? 6 : 5), "packs craftable in the chemical plant: " + packs);
		helper.succeed();
	}

	/** The xanaxium block, centrifuged: its row was held for the powder. */
	@GameTest(template = EMPTY)
	public static void theXanaxiumBlockCentrifugesIntoOsmiridiumPowder(GameTestHelper helper) {

		if(!(LeafiaFeatures.resourceItems && LeafiaFeatures.osmiridiumBlocks)) { helper.succeed(); return; }

		var key = new com.hbm.inventory.RecipesCommon.ComparableStack(BuiltInRegistries.ITEM.get(rl("block_xanaxium")));
		ItemStack[] out = CentrifugeRecipes.recipes.get(key);
		helper.assertTrue(out != null && out.length == 4, "a four output centrifuge row for the block");
		helper.assertTrue(out[0].getItem() == LeafiaResourceItems.get("powder_xanaxium").get() && out[0].getCount() == 2 && out[1].getItem() == out[0].getItem() && out[1].getCount() == 2, "two stacks of two xanaxium powder");
		helper.assertTrue(out[2].getItem() == LeafiaItems.powder_osmiridium.get() && out[2].getCount() == 25 && out[3].getItem() == LeafiaItems.powder_osmiridium.get() && out[3].getCount() == 25, "two stacks of 25 osmiridium powder");
		helper.succeed();
	}

	/** Registering the depleted fuels released the five PUREX rows that named them. */
	@GameTest(template = EMPTY)
	public static void theFivePurexRowsRecycleTheDepletedFuels(GameTestHelper helper) {

		if(!(on() && LeafiaFeatures.resourceItems)) { helper.succeed(); return; }

		String[][] rows = {
				{"zirnoxu238", "waste_u238", "nugget_u238:1", "nugget_pu239:4", "nugget_schraranium:1", "nuclear_waste_tiny:1"},
				{"zirnoxmes", "waste_mes", "nugget_solinium:1", "nugget_beryllium:2", "nuclear_waste_tiny:1", "nuclear_waste_tiny:2"},
				{"zirnoxhes", "waste_hes", "nugget_solinium:2", "nugget_beryllium:1", "nuclear_waste_tiny:1", "nuclear_waste_tiny:2"},
				{"zirnoxsch", "waste_sch", "nugget_solinium:2", "nugget_solinium:1", "nuclear_waste_tiny:1", "nuclear_waste_tiny:2"},
				{"zirnoxsol", "waste_sol", "nugget_australium:2", "nugget_euphemium:1", "nuclear_waste_tiny:1", "nuclear_waste_tiny:2"},
		};
		for(String[] row : rows) {
			GenericRecipe r = PUREXRecipes.INSTANCE.recipeNameMap.get("purex.leafia." + row[0]);
			helper.assertTrue(r != null, "purex.leafia." + row[0] + " is not registered");
			helper.assertTrue(r.duration == 100 && r.power == 1_000, row[0] + ": " + r.duration + " ticks at " + r.power);
			helper.assertTrue(r.inputItem.length == 1 && r.inputItem[0] instanceof com.hbm.inventory.RecipesCommon.ComparableStack a && a.toStack().getItem() == LeafiaRods.depleted(row[1]), row[0] + " takes " + row[1]);
			helper.assertTrue(r.inputFluid.length == 2 && r.inputFluid[0].type == Fluids.KEROSENE && r.inputFluid[0].fill == 500
					&& r.inputFluid[1].type == Fluids.NITRIC_ACID && r.inputFluid[1].fill == 250, row[0] + ": 500 kerosene and 250 nitric acid");
			helper.assertTrue(r.outputItem.length == 4, row[0] + " has four outputs");
			for(int i = 0; i < 4; i++) {
				String[] want = row[2 + i].split(":");
				Item item = want[0].equals("nugget_schraranium") ? LeafiaResourceItems.get("nugget_schraranium").get()
						: BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", want[0]));
				helper.assertTrue(r.outputItem[i] instanceof com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput co && co.stack.getItem() == item
						&& co.stack.getCount() == Integer.parseInt(want[1]), row[0] + " output " + i + " should be " + row[2 + i]);
			}
		}
		helper.assertTrue(LeafiaRecipeInjector.rows("purex") == 5, "five purex rows injected: " + LeafiaRecipeInjector.rows("purex"));
		helper.succeed();
	}

	/** Upstream's AddonOreDict puts powder_osmiridium on the OSMIRIDIUM frame; the recipes' ingredient tag. */
	@GameTest(template = EMPTY)
	public static void theOsmiridiumDustTagHoldsTheAddonsPowder(GameTestHelper helper) {

		if(!LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		ItemStack powder = new ItemStack(LeafiaItems.powder_osmiridium.get());
		helper.assertTrue(powder.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dusts/osmiridium"))), "the powder is in c:dusts/osmiridium");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the stack data helper

	@GameTest(template = EMPTY)
	public static void aStackWithNoTagReadsAsNull(GameTestHelper helper) {

		ItemStack s = new ItemStack(net.minecraft.world.item.Items.STONE);
		helper.assertTrue(LeafiaRodItem.data(s) == null, "no custom data: null");
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
		helper.assertTrue(LeafiaRodItem.data(s) != null && LeafiaRodItem.data(s).isEmpty(), "an empty tag is a tag");
		helper.succeed();
	}
}
