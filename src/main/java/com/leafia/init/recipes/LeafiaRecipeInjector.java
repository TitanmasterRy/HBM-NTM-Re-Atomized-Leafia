package com.leafia.init.recipes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.material.MatDistribution;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;
import com.hbm.inventory.recipes.ArcFurnaceRecipes.ArcFurnaceRecipe;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.CyclotronRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes.ExposureChamberRecipe;
import com.hbm.inventory.recipes.FuelPoolRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.PseudoFluidType;
import com.hbm.inventory.recipes.ParticleAcceleratorRecipes;
import com.hbm.inventory.recipes.ParticleAcceleratorRecipes.ParticleAcceleratorRecipe;
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
import com.hbm.util.Tuple.Pair;
import com.leafia.LeafiaMod;
import com.leafia.contents.LeafiaItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipes;
import com.leafia.contents.control.battery.AddonEnumBatteryPack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Puts the addon's rows into the base mod's recipe handlers (D-1093 #1, D-1095).
 *
 * <p>Upstream does this from a mixin at the tail of {@code SerializableRecipe.initialize()}. The
 * addon never mixes into {@code com.hbm} (D-1001), and {@code IRecipeRegisterListener} is ported
 * but never dispatched, so wiring it up would be a base-mod edit. Neither is needed, because
 * {@code initialize()} is already a hook: it walks the public {@code recipeHandlers} list, and for
 * each handler deletes its recipes, reads its file or registers its defaults, and calls
 * {@link #initAfterLoading()}. This class is a {@link SerializableRecipe} that owns the <b>last</b>
 * slot in that list, so its {@code initAfterLoading()} runs after every base handler has been
 * rebuilt - on the first boot, on a pack's recipe file replacing a handler's defaults, and on every
 * later {@code initialize()} - and puts the rows back.
 *
 * <h2>When it first runs</h2>
 *
 * The base mod calls {@code registerAllHandlers()} and {@code initialize()} once, from a deferred
 * task in {@code FMLCommonSetupEvent}. Which mod's task is queued first is not something to build
 * on, so the addon does its first pass on {@code FMLLoadCompleteEvent}, which fires after all
 * common-setup work: it appends this handler, then injects directly because that
 * {@code initialize()} has already gone by.
 *
 * <h2>Idempotence</h2>
 *
 * Every helper here leaves one copy of a row however many times it runs, because the pass runs
 * once directly and again for every {@code initialize()}, and a pack's recipe file written from the
 * template ({@code _hbmRecipes}) already contains the addon's rows. Keyed maps overwrite; a named
 * {@code GenericRecipes} entry is removed before it is registered; lists drop an entry with the
 * same output first.
 *
 * <h2>The file</h2>
 *
 * It writes an empty {@code leafiaInjected.json} template and reads nothing: the rows are not a
 * thing a recipe file can edit, because they are re-added after it is read. A pack that wants one
 * gone removes it in a script after {@code initialize()}, or switches the content off.
 *
 * <p><b>KubeJS:</b> nothing in the base mod's recipe code changed. The {@code kubejs} branch never
 * contains {@code addon-leafia}, so its round trip through each handler's JSON does not see this
 * class.
 */
public final class LeafiaRecipeInjector extends SerializableRecipe {

	public static final LeafiaRecipeInjector INSTANCE = new LeafiaRecipeInjector();

	/** Rows written by the last pass, by family. */
	private static final Map<String, Integer> ROWS = new LinkedHashMap<>();
	private static final Map<String, Integer> REMOVALS = new LinkedHashMap<>();
	private static final Map<String, Integer> OPS = new LinkedHashMap<>();
	private static int passes = 0;

	private LeafiaRecipeInjector() {}

	// ------------------------------------------------------------------ the handler

	@Override public String getFileName() { return "leafiaInjected.json"; }
	@Override public void registerDefaults() { }
	@Override public void deleteRecipes() { }
	@Override public Object getRecipeObject() { return List.of(); }
	/** The template this handler writes is deliberately empty. */
	@Override public boolean allowEmptyRecipeList() { return true; }
	@Override public String getComment() {
		return "Leafia's Cursed Addon: nothing to edit here. The addon re-adds its rows to the other recipe files after they are read.";
	}
	@Override public void readRecipe(JsonElement element) { }
	@Override public void writeRecipe(Object recipe, JsonWriter writer) throws IOException { }

	/** Runs after the recipes are in, whether they came from a file or from defaults. */
	@Override
	public void initAfterLoading() {
		injectAll();
	}

	/** The first pass, from {@code FMLLoadCompleteEvent}; also takes the last slot in the handler list. */
	public static void onLoadComplete() {
		addHandlers();
		if(!SerializableRecipe.recipeHandlers.contains(INSTANCE)) SerializableRecipe.recipeHandlers.add(INSTANCE);
		injectAll();
	}

	/**
	 * The addon's own recipe handlers, which upstream's {@code AddonSerializableRecipe} adds to the tail of
	 * {@code registerAllHandlers}: the salt separator's table (D-3015). They go in <b>before</b> this
	 * injector's slot, so a later {@code initialize()} reads them like any base handler and this one still
	 * runs last; and because the base's {@code initialize()} has already gone by when the addon first runs,
	 * each is brought up here the way {@code initialize()} would have (a synced file, a recipe file, or its
	 * defaults, then {@code initAfterLoading}) and its template written. A handler already in the list is
	 * left alone, so a second call changes nothing.
	 */
	public static void addHandlers() {
		if(LeafiaFeatures.lftrReactor()) addHandler(SaltSeparatorRecipes.INSTANCE);
	}

	/** Adds {@code handler} ahead of this injector and initialises it as {@code SerializableRecipe.initialize()} does one. */
	static void addHandler(SerializableRecipe handler) {
		List<SerializableRecipe> handlers = SerializableRecipe.recipeHandlers;
		if(handlers.contains(handler)) return;
		int at = handlers.indexOf(INSTANCE);
		handlers.add(at < 0 ? handlers.size() : at, handler);

		File recDir = new File(FMLPaths.CONFIGDIR.get().toFile(), "hbmRecipes");
		File recFile = new File(recDir, handler.getFileName());
		handler.deleteRecipes();
		if(SerializableRecipe.recipeSyncHandlers.containsKey(handler.getFileName())) {
			try {
				InputStream stream = SerializableRecipe.recipeSyncHandlers.get(handler.getFileName());
				stream.reset();
				handler.readRecipeStream(new InputStreamReader(stream));
				handler.modified = true;
			} catch(Throwable ex) {
				LeafiaMod.LOGGER.error("Failed to read the synced recipe stream {}", handler.getFileName(), ex);
			}
		} else if(recFile.exists()) {
			handler.readRecipeFile(recFile);
			handler.modified = true;
		} else {
			handler.registerDefaults();
		}
		handler.initAfterLoading();

		File template = new File(recDir.getParentFile(), "_hbmRecipes");
		if(template.exists() || template.mkdirs()) handler.writeTemplateFile(new File(template, handler.getFileName()));
	}

	/** Resets the counters and writes every row. */
	public static synchronized void injectAll() {
		ROWS.clear();
		REMOVALS.clear();
		OPS.clear();
		LeafiaBaseRecipes.injectAll();
		passes++;
		int rows = rowsInjected();
		LeafiaMod.LOGGER.info("Injected {} addon recipe rows into {} base handlers.", rows, ROWS.size());
	}

	// ------------------------------------------------------------------ counters

	static void family(String family) { ROWS.putIfAbsent(family, 0); }
	static void row(String family) { ROWS.merge(family, 1, Integer::sum); }
	static void removal(String family) { REMOVALS.merge(family, 1, Integer::sum); }
	static void op(String family) { OPS.merge(family, 1, Integer::sum); }

	/** Rows the last pass wrote into {@code family}'s handler. */
	public static int rows(String family) { return ROWS.getOrDefault(family, 0); }
	/** Base recipes the last pass removed from {@code family}'s handler. */
	public static int removals(String family) { return REMOVALS.getOrDefault(family, 0); }
	/** Edits to existing base recipes the last pass made (the assembler's replaced outputs). */
	public static int ops(String family) { return OPS.getOrDefault(family, 0); }
	public static int rowsInjected() { return ROWS.values().stream().mapToInt(Integer::intValue).sum(); }
	public static int passes() { return passes; }

	// ------------------------------------------------------------------ symbols

	/** True when every holder is present. A null is a row whose content its switch left out. */
	static boolean ok(Object... holders) {
		for(Object h : holders) if(h == null) return false;
		return true;
	}

	/** The addon item {@code leafia:name}, or null when it is not registered. */
	public static Item leafiaItem(String name) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("leafia", name)).orElse(null);
	}

	/**
	 * The base mod's bobblehead item {@code hbm:bobblehead_<figure>}, or null when there is none. The addon's own figure is
	 * {@code leafia_leafia} (the enum constant {@code LEAFIA_LEAFIA}, appended through {@code enumextensions.json}, D-3041).
	 */
	public static Item bobbleItem(String figure) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("hbm", "bobblehead_" + figure)).orElse(null);
	}

	/** A simple material item from {@link LeafiaResourceItems}, or null when its switch is off. */
	public static Item res(String name) {
		var holder = LeafiaResourceItems.get(name);
		return holder == null ? null : holder.get();
	}

	/** One of the addon's six battery packs, or null when {@code features.batteryPacks} is off. */
	static Item pack(AddonEnumBatteryPack pack) {
		var holder = LeafiaItems.battery_packs.get(pack);
		return holder == null ? null : holder.get();
	}

	// ------------------------------------------------------------------ GenericRecipes

	/** Registers {@code recipe}, first removing any recipe of the same name (the registry throws on a duplicate). */
	static <T extends GenericRecipe> void replace(GenericRecipes<T> set, T recipe) {
		String name = recipe.getInternalName();
		set.recipeOrderedList.removeIf(r -> r.getInternalName().equals(name));
		set.recipeNameMap.remove(name);
		for(List<GenericRecipe> group : set.autoSwitchGroups.values()) group.removeIf(r -> r.getInternalName().equals(name));
		set.register(recipe);
	}

	/** Upstream's {@code replaceOutput}: swaps the solid outputs of a base recipe, keeping its first output's count. */
	static void replaceOutput(AssemblyMachineRecipes set, String name, ItemStack... outputs) {
		GenericRecipe recipe = set.recipeNameMap.get(name);
		if(recipe == null) {
			LeafiaMod.LOGGER.warn("Could not find assembler recipe \"{}\" to replace.", name);
			return;
		}
		int count = recipe.outputItem[0] instanceof GenericRecipes.ChanceOutput single ? single.stack.getCount() : 1;
		for(ItemStack output : outputs) output.setCount(count);
		recipe.outputItems(outputs);
	}

	/** Upstream's {@code remove}. */
	static void removeRecipe(AssemblyMachineRecipes set, String name) {
		GenericRecipe recipe = set.recipeNameMap.remove(name);
		if(recipe != null) set.recipeOrderedList.remove(recipe);
	}

	// ------------------------------------------------------------------ list-backed handlers

	static void addSoldering(SolderingRecipe recipe) {
		SolderingRecipes.recipes.removeIf(r -> r.output.getItem() == recipe.output.getItem());
		SolderingRecipes.recipes.add(recipe);
	}

	static void addExposure(ExposureChamberRecipe recipe) {
		ExposureChamberRecipes.recipes.removeIf(r -> r.output.getItem() == recipe.output.getItem());
		ExposureChamberRecipes.recipes.add(recipe);
	}

	/** Upstream's {@code removeRecipeFor(item)}. */
	static void removeExposure(Item item) {
		ExposureChamberRecipes.recipes.removeIf(r -> r.output.getItem() == item);
	}

	static void addAccelerator(ParticleAcceleratorRecipe recipe) {
		ParticleAcceleratorRecipes.recipes.removeIf(r -> r.output1.getItem() == recipe.output1.getItem()
				&& (r.output2 == null ? recipe.output2 == null : recipe.output2 != null && r.output2.getItem() == recipe.output2.getItem()));
		ParticleAcceleratorRecipes.recipes.add(recipe);
	}

	static void addAnvil(AnvilConstructionRecipe recipe) {
		AnvilRecipes.constructionRecipes.removeIf(r -> r.output.size() == recipe.output.size()
				&& r.output.get(0).stack.getItem() == recipe.output.get(0).stack.getItem());
		AnvilRecipes.constructionRecipes.add(recipe);
	}

	static void addArcWelder(ArcWelderRecipe recipe) {
		ArcWelderRecipes.recipes.removeIf(r -> r.output.getItem() == recipe.output.getItem() && ItemStack.isSameItemSameComponents(r.output, recipe.output));
		ArcWelderRecipes.recipes.add(recipe);
	}

	static void addPyro(PyroOvenRecipe recipe) {
		PyroOvenRecipes.recipes.removeIf(r -> sameFluid(r.outputFluid, recipe.outputFluid) && r.outputItem == recipe.outputItem
				&& sameFluid(r.inputFluid, recipe.inputFluid));
		PyroOvenRecipes.recipes.add(recipe);
	}

	private static boolean sameFluid(FluidStack a, FluidStack b) {
		if(a == null || b == null) return a == b;
		return a.type == b.type && a.fill == b.fill;
	}

	// ------------------------------------------------------------------ keyed maps

	static void cyclotron(ComparableStack part, AStack in, ItemStack out, int amat) {
		CyclotronRecipes.recipes.put(new Pair<>(part, in), new Pair<>(out, amat));
	}

	// ------------------------------------------------------------------ SILEX and the gas centrifuge

	/**
	 * A SILEX recipe, through the base mod's {@code SILEXRecipes.addRecipe} (the table is private there, public in NTM-CE,
	 * which is what upstream wrote against; D-1095 reached it by reflection until the base gained the write seam, D-3026).
	 * Keyed, so a second pass replaces and never doubles.
	 */
	static void silex(Object key, SILEXRecipe recipe) {
		SILEXRecipes.addRecipe(key, recipe);
	}

	/**
	 * A pseudo-fluid type for the gas centrifuge. The constructor is public in this port (upstream put a subclass in the base
	 * mod's own package to reach its package-private one; split packages cannot cross mod jars), and it registers itself by
	 * name, so an existing one is returned rather than building a second.
	 */
	static PseudoFluidType definePseudo(String name, int consumed, int produced, PseudoFluidType out, boolean fast, ItemStack... items) {
		PseudoFluidType existing = PseudoFluidType.types.get(name);
		if(existing != null) return existing;
		return new PseudoFluidType(name, consumed, produced, out, fast, items);
	}

	static PseudoFluidType pseudo(String name) {
		return PseudoFluidType.types.get(name);
	}

	static void convert(FluidType type, String pseudoName) {
		GasCentrifugeRecipes.fluidConversions.put(type, PseudoFluidType.types.get(pseudoName));
	}

	/**
	 * A row of the gas centrifuge's display table. The table is keyed by identity, so an earlier entry for the same fluid is
	 * dropped first ({@code removeDisplayRows}) and a second pass leaves one.
	 */
	static void gasCent(FluidStack key, Object[] value) {
		GasCentrifugeRecipes.removeDisplayRows(key.type);
		GasCentrifugeRecipes.addDisplayRow(key, (ItemStack[]) value[0], (Boolean) value[1], (Integer) value[2]);
	}

	// ------------------------------------------------------------------ the arc furnace, the waste drum, material distribution

	/**
	 * An arc furnace recipe for {@code input}. The base's {@code register} refuses an input whose items are already
	 * occupied, which is how a second pass would silently do nothing, so the earlier row and its claim on the items go
	 * first and a pass leaves exactly one.
	 */
	static void arcFurnace(AStack input, ArcFurnaceRecipe recipe) {
		ArcFurnaceRecipes.recipeList.removeIf(p -> p.getKey().equals(input));
		for(ItemStack stack : ArcFurnaceRecipes.stacksOf(input)) { // the stacks register claimed (public since D-2207)
			ComparableStack key = new ComparableStack(stack);
			if(recipe.solidOutput != null) ArcFurnaceRecipes.occupiedSolid.remove(key);
			if(recipe.fluidOutput != null) ArcFurnaceRecipes.occupiedLiquid.remove(key);
		}
		ArcFurnaceRecipes.fastCacheSolid.clear();
		ArcFurnaceRecipes.fastCacheLiquid.clear();
		ArcFurnaceRecipes.register(input, recipe);
	}

	/**
	 * Upstream's {@code WasteDrumRecipes.addRecipe(new ComparableStack(item, 1, 1), new ItemStack(item))}: the still-cooling
	 * variant of a depleted fuel cools into the cold one. {@code FuelPoolRecipes} is that table here (damage 1 is the stack's
	 * {@code cooling} flag, so the key is its {@code coolingKey}); keyed, so a second pass overwrites.
	 */
	static void wasteDrum(Item item) {
		FuelPoolRecipes.recipes.put(FuelPoolRecipes.coolingKey(item), new ItemStack(item));
	}

	/** {@code MatDistribution.registerOre}: keyed by ore name, so a second pass replaces. */
	static void matOre(String key, Object... definition) {
		MatDistribution.registerOre(key, definition);
	}

	/** Test seam: a copy of the rows written, by family. */
	public static Map<String, Integer> snapshot() { return new LinkedHashMap<>(ROWS); }

	static List<String> families() { return new ArrayList<>(ROWS.keySet()); }
}
