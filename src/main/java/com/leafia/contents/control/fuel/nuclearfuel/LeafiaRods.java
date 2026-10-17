package com.leafia.contents.control.fuel.nuclearfuel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem.EmptyLeafiaRod;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem.ItemType;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem.Purity;
import com.leafia.contents.parts.depleteds.AddonDepletedFuelItem;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.hazards.ItemRads;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The addon's fuel rods: the empty rod assembly, fifty-nine rods, and the five depleted fuels some of
 * them are made from (D-3001).
 *
 * <p>Upstream: the {@code LeafiaRods} and {@code DepletedFuels} nested classes of {@code AddonItems}.
 * Each rod is {@code new LeafiaRodItem(label, life, meltingPoint)} and a fluent chain; the chain is
 * copied as written, so a rod's declaration reads as upstream's. <b>The declarations are generated</b>:
 * a generator script reads upstream's {@code AddonItems.java} and rewrites the block
 * between the two markers, in upstream's order (which is the creative tab's order), together with each
 * rod's model, its lang keys and its recipes. An edit inside the markers is lost on the next run.
 *
 * <h2>The gate</h2>
 *
 * Everything registers inside {@link #register}, behind {@code features.rods} (D-1002). With the switch
 * off {@link #leafRod} is null, {@link #RODS} is empty and so is {@link LeafiaRodItem#fromResourceMap};
 * nothing that reads a rod may assume one exists without asking the switch.
 *
 * <h2>Named by the field, looked up by the registry name</h2>
 *
 * Upstream names a rod by its field ({@code LeafiaRods.leafRodU38}); the registry name is
 * {@code leafia_rod_u238}, the label with the dashes and spaces taken out. {@link #RODS} is keyed by
 * the <em>field</em>, so a later port of a consumer can write {@code LeafiaRods.get("leafRodU38")} and
 * read like upstream; {@link LeafiaRodItem#fromResourceMap} is keyed by registry name.
 */
public final class LeafiaRods {

	private LeafiaRods() {}

	/** The empty rod assembly (registry name {@code leafia_rod}), which every rod is crafted from. Null when {@code features.rods} is off. */
	public static DeferredItem<EmptyLeafiaRod> leafRod;

	/**
	 * What upstream's {@code AddonConfig.FuelLives} lets a pack override per rod, with the default each
	 * had: life (the heat it can generate before it is spent), the neutrons it emits and how readily it
	 * takes them. The key is the rod's function id, as upstream's is.
	 */
	public record RodDefaults(String key, double life, double emission, double reactivity) {}

	/** Every rod, by upstream's field name ({@code leafRodU38}), in registration order. */
	public static final Map<String, DeferredItem<LeafiaRodItem>> RODS = new LinkedHashMap<>();

	/** The five depleted fuels, by registry name ({@code waste_u238} ...). */
	public static final Map<String, DeferredItem<AddonDepletedFuelItem>> DEPLETED = new LinkedHashMap<>();

	/** The rod upstream calls {@code field}, or null when it is not registered. */
	public static LeafiaRodItem get(String field) {
		DeferredItem<LeafiaRodItem> item = RODS.get(field);
		return item == null ? null : item.get();
	}

	/** The depleted fuel called {@code name}, or null. */
	public static AddonDepletedFuelItem depleted(String name) {
		DeferredItem<AddonDepletedFuelItem> item = DEPLETED.get(name);
		return item == null ? null : item.get();
	}

	/** Declares the rods. Called from {@link LeafiaItems#register}, after {@link LeafiaResourceItems}, which some rods are made from. */
	public static void register() {
		RODS.clear();
		DEPLETED.clear();
		leafRod = null;
		if(!LeafiaFeatures.rods) return;

		/*
		 * Upstream: new EmptyLeafiaRod() - an AddonItemBase named "leafia_rod", in the control tab. No
		 * stack limit is set, so it stacks to 64; the filled rods set theirs to 1.
		 */
		leafRod = LeafiaItems.ITEMS.registerItem("leafia_rod", EmptyLeafiaRod::new, new Item.Properties());
		LeafiaCreativeTabs.add(LeafiaCreativeTabs.MAIN, leafRod);

		declareDepleted();
		declare();
	}

	private static void rod(String field, String label, double life, double meltingPoint, boolean inTab, Consumer<LeafiaRodItem> chain) {
		String name = "leafia_rod_" + label.replace("-", "").replace(" ", "").toLowerCase();
		DeferredItem<LeafiaRodItem> item = LeafiaItems.ITEMS.registerItem(name, props -> {
			LeafiaRodItem rod = new LeafiaRodItem(props, label, life, meltingPoint);
			rod.setContainerItem(leafRod); // upstream's setContainerItem(LeafiaRods.leafRod): crafting hands the empty rod back
			chain.accept(rod);
			return rod;
		}, new Item.Properties());
		LeafiaCreativeTabs.add(inTab ? LeafiaCreativeTabs.MAIN : null, item);
		RODS.put(field, item);
	}

	private static void depleted(String name, String namespace, String texture) {
		// AddonDepletedFuelItem(String, String, String) sets partsTab, which is this jar's one tab.
		DeferredItem<AddonDepletedFuelItem> item = LeafiaItems.ITEMS.registerItem(name,
				props -> new AddonDepletedFuelItem(props, namespace, texture), new Item.Properties());
		LeafiaCreativeTabs.add(LeafiaCreativeTabs.MAIN, item);
		DEPLETED.put(name, item);
	}

	// The depleted fuels a rod names as its base item resolve through these, at the moment the rod is built.
	private static DeferredItem<AddonDepletedFuelItem> waste_u238() { return DEPLETED.get("waste_u238"); }
	private static DeferredItem<AddonDepletedFuelItem> waste_mes() { return DEPLETED.get("waste_mes"); }
	private static DeferredItem<AddonDepletedFuelItem> waste_hes() { return DEPLETED.get("waste_hes"); }
	private static DeferredItem<AddonDepletedFuelItem> waste_sch() { return DEPLETED.get("waste_sch"); }
	private static DeferredItem<AddonDepletedFuelItem> waste_sol() { return DEPLETED.get("waste_sol"); }

	// BEGIN GENERATED
	private static void declare() {
		rod("leafRodU38", "U-238", 4000000d, 0d, true, r -> { r.setAppearance(ModItems.billet_u238, 0, ItemType.BILLET, Purity.ISOTOPE); r.setDecayProduct("depleteduranium238"); r.addRad(ItemRads.uranium238); });
		rod("leafRodNU", "NU", 4000000d, 2865d, true, r -> { r.setAppearance(ModItems.billet_uranium, 0, ItemType.BILLET, Purity.RAW); r.setReactivity(0.4); r.setDecayProduct("depletednaturaluranium"); r.addRad(ItemRads.uranium); });
		rod("leafRodMEU235", "MEU-235", 4000000d, 2865d, true, r -> { r.setAppearance(ModItems.billet_uranium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depleteduranium"); r.addRad(ItemRads.uraniumFuel); });
		rod("leafRodHEU233", "HEU-233", 1000000d, 2865d, true, r -> { r.setAppearance(ModItems.billet_u233, 0, ItemType.BILLET, Purity.ISOTOPE); r.setReactivity(2); r.setDecayProduct("depleteduranium233"); r.addRad(ItemRads.uranium233); });
		rod("leafRodHEU235", "HEU-235", 3000000d, 2865d, true, r -> { r.setAppearance(ModItems.billet_u235, 0, ItemType.BILLET, Purity.ISOTOPE); r.setReactivity(1.75); r.setDecayProduct("depleteduranium235"); r.addRad(ItemRads.uranium235); });
		rod("leafRodTHMEU", "ThMEU", 3500000d, 3350d, true, r -> { r.setAppearance(ModItems.billet_thorium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depletedthorium"); r.addRad(ItemRads.thoriumFuel); });
		rod("leafRodLEP239", "LEPu-239", 5000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_plutonium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.setReactivity(1/1.5); r.setDecayProduct("depletedplutonium"); r.addRad(ItemRads.plutoniumFuel); });
		rod("leafRodMEP239", "MEPu-239", 5000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu_mix, 0, ItemType.BILLET, Purity.ISOTOPE); r.setDecayProduct("depletedplutonium"); r.addRad(ItemRads.plutoniumRG); });
		rod("leafRodHEP239", "HEPu-239", 5000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu239, 0, ItemType.BILLET, Purity.ISOTOPE); r.setReactivity(1.25); r.setDecayProduct("pu240"); r.addRad(ItemRads.plutonium239); });
		rod("leafRodHEP241", "HEPu-241", 18000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu241, 0, ItemType.BILLET, Purity.ISOTOPE); r.setReactivity(1.45); r.setDecayProduct("depletedplutonium"); r.addRad(ItemRads.plutonium241); });
		rod("leafRodLEA242", "LEAm-242", 18000000d, 3986d, true, r -> { r.setAppearance(ModItems.billet_americium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.addRad(ItemRads.americiumFuel); });
		rod("leafRodMEA242", "MEAm-242", 18000000d, 3986d, true, r -> { r.setAppearance(ModItems.billet_am_mix, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.americiumRG); });
		rod("leafRodHEA241", "HEAm-241", 18000000d, 3986d, true, r -> { r.setAppearance(ModItems.billet_am241, 0, ItemType.BILLET, Purity.ISOTOPE); r.preferFast(); r.addRad(ItemRads.americium241); });
		rod("leafRodHEA242", "HEAm-242", 18000000d, 3986d, true, r -> { r.setAppearance(ModItems.billet_am242, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.americium242); });
		rod("leafRodMEN237", "MENp-237", 5200000d, 2800d, true, r -> { r.setAppearance(ModItems.billet_neptunium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.preferAny(); r.addRad(ItemRads.neptuniumFuel); });
		rod("leafRodHEN237", "HENp-237", 3200000d, 2800d, true, r -> { r.setAppearance(ModItems.billet_neptunium, 0, ItemType.BILLET, Purity.RAW); r.preferFast(); r.addRad(ItemRads.neptunium237); });
		rod("leafRodMOX", "MOX", 6000000d, 2815d, true, r -> { r.setAppearance(ModItems.billet_mox_fuel, 0, ItemType.BILLET, Purity.FUEL); r.addRad(ItemRads.moxie); });
		rod("leafRodLES236", "LESa-326", 3500000d, 2500d, true, r -> { r.setAppearance(ModItems.billet_les, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depletedschrabidium"); r.setEmission(0.75); r.setModerated(); r.addBlinding(); r.addRad(ItemRads.schrabidiumLow); });
		rod("leafRodMES326", "MESa-326", 3000000d, 2750d, true, r -> { r.setAppearance(ModItems.billet_schrabidium_fuel, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depletedmesa326"); r.addBlinding(); r.addRad(ItemRads.schrabidiumMedium); });
		rod("leafRodHES326", "HESa-326", 2500000d, 3000d, true, r -> { r.setAppearance(ModItems.billet_hes, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depletedhesa326"); r.setEmission(1.25); r.addBlinding(); r.addRad(ItemRads.schrabidiumHigh); });
		rod("leafRodSa326", "Sa-326", 2000000d, 3250d, true, r -> { r.setAppearance(ModItems.billet_schrabidium, 0, ItemType.BILLET, Purity.FUEL); r.setDecayProduct("depletedsa326"); r.setEmission(2.25); r.addBlinding(); r.addRad(ItemRads.schrabidium326); });
		rod("leafRodLEAus", "LEAus", 5500000d, 7029d, true, r -> { r.setAppearance(ModItems.billet_australium_lesser, 0, ItemType.BILLET, Purity.ISOTOPE); r.setDecayProduct("heaus"); r.setEmission(0.3); });
		rod("leafRodHEAus", "HEAus", 5500000d, 5211d, true, r -> { r.setAppearance(ModItems.billet_australium_greater, 0, ItemType.BILLET, Purity.ISOTOPE); r.setEmission(1.2); });
		rod("leafRodRa226Be", "Ra-226Be", 400000d, 700d, true, r -> { r.setAppearance(ModItems.billet_ra226be, 0, ItemType.BILLET, Purity.SOURCE); r.setModerated(); r.addRad(ItemRads.radium226be); });
		rod("leafRodPo210Be", "Po-210Be", 2000000d, 1287d, true, r -> { r.setAppearance(ModItems.billet_po210be, 0, ItemType.BILLET, Purity.SOURCE); r.setModerated(); r.addRad(ItemRads.polonium210be); });
		rod("leafRodPu238Be", "Pu-238Be", 2000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu238be, 0, ItemType.BILLET, Purity.SOURCE); r.setModerated(); r.addRad(ItemRads.plutonium238be); });
		rod("leafRodPu238", "Pu-238", 3500000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu238, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.plutonium238); });
		rod("leafRodPo210", "Po-210", 3000000d, 1287d, true, r -> { r.setAppearance(ModItems.billet_polonium, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.polonium210); });
		rod("leafRodSa327", "Sa-327", 8000000d, 2250d, true, r -> { r.setAppearance(ModItems.billet_solinium, 0, ItemType.BILLET, Purity.RAW); r.setDecayProduct("depletedsa327"); r.setEmission(65); r.setReactivity(1/45d); r.addBlinding(); r.addRad(ItemRads.solinium327); });
		rod("leafRodLithium", "Lithium", 3000d, 0d, true, r -> { r.setAppearance(ModItems.lithium, 0, ItemType.BILLET, Purity.RAW); r.addAlkaline(1); });
		rod("leafRodPu240", "Pu-240", 65000000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_pu240, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.plutonium240); });
		rod("leafRodWasteUranium", "Depleted Uranium", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_uranium, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteUranium); });
		rod("leafRodWasteUraniumNatural", "Depleted Natural Uranium", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_natural_uranium, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteUranium); });
		rod("leafRodWasteUranium233", "Depleted Uranium-233", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_u233, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteUranium); });
		rod("leafRodWasteUranium235", "Depleted Uranium-235", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_u235, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteUranium); });
		rod("leafRodWasteUranium238", "Depleted Uranium-238", 0d, 0d, true, r -> { r.setAppearance(waste_u238(), 1, ItemType.DEPLETED, Purity.RAW); r.overrideLabel("Bred Uranium-238"); r.addRad(ItemRads.wasteUranium); });
		rod("leafRodWastePlutonium", "Depleted Plutonium", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_plutonium, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wastePlutonium); });
		rod("leafRodWasteThorium", "Depleted Thorium", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_thorium, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteThorium); });
		rod("leafRodWasteMOX", "Depleted MOX", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_mox, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteMOX); });
		rod("leafRodWasteSchrabidium", "Depleted Schrabidium", 0d, 0d, true, r -> { r.setAppearance(ModItems.waste_schrabidium, 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteSchrabidium); });
		rod("leafRodWasteMESa326", "Depleted MESa-326", 0d, 0d, true, r -> { r.setAppearance(waste_mes(), 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteSchrabidium); });
		rod("leafRodWasteHESa326", "Depleted HESa-326", 0d, 0d, true, r -> { r.setAppearance(waste_hes(), 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteSchrabidium); });
		rod("leafRodWasteSa326", "Depleted Sa-326", 0d, 0d, true, r -> { r.setAppearance(waste_sch(), 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteSchrabidium); });
		rod("leafRodWasteSa327", "Depleted Sa-327", 0d, 0d, true, r -> { r.setAppearance(waste_sol(), 1, ItemType.DEPLETED, Purity.RAW); r.addRad(ItemRads.wasteSchrabidium); });
		rod("leafRodLead", "Lead", 99999999d, 0d, true, r -> { r.setAppearance(ModItems.ingot_lead, 0, ItemType.BILLET, Purity.RAW); r.setDecayProduct("pb209"); r.addToxic(2); });
		rod("leafRodBF", "Balefire", 200d, 8652d, true, r -> { r.setBaseItem(ModItems.egg_balefire); r.setSpecialRodModel(); r.setDecayProduct("blazingbalefire"); r.setEmission(8); r.setReactivity(1/6d); r.setCorium("fluid_balecorium"); r.addRad(ItemRads.balefire); });
		rod("leafRodBFblazing", "Blazing Balefire", 65000d, 8652d, true, r -> { r.setSpecialRodModel(); r.setDecayProduct("balefire"); r.setEmission(12); r.setReactivity(1/9d); r.setCorium("fluid_balecorium"); r.addRad(ItemRads.balefire.multiply(2)); });
		rod("leafRodNPu", "NPu", 2600000d, 2744d, true, r -> { r.setAppearance(ModItems.billet_plutonium, 0, ItemType.BILLET, Purity.RAW); r.addRad(ItemRads.plutonium); });
		rod("leafRodTh232", "Th-232", 90000d, 0d, true, r -> { r.setAppearance(ModItems.billet_th232, 0, ItemType.BILLET, Purity.RAW); r.setDecayProduct("thmeu"); r.addRad(ItemRads.thorium232); });
		rod("leafRodGold198", "Au-198", 900000d, 3850d, true, r -> { r.setAppearance(ModItems.billet_au198, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.gold198); });
		rod("leafRodLead209", "Pb-209", 600000d, 4300d, true, r -> { r.setAppearance(ModItems.billet_pb209, 0, ItemType.BILLET, Purity.ISOTOPE); r.addRad(ItemRads.lead209); });
		rod("leafRodDebug", "Debug", 0d, 0d, true, r -> { r.setAppearance(ModItems.wand_d, 0, ItemType.BILLET, Purity.UNSTABLE); });
		rod("leafRodDyatlov", "Potential Instant Blowout Applicator", 95000000d, 650000000d, true, r -> { r.setAppearance(ModItems.meltdown_tool, 0, ItemType.BILLET, Purity.UNSTABLE); });
		rod("leafRodYharonite", "Yh-XXX", 6666666d, 3200d, true, r -> { r.setAppearance(ModItems.billet_yharonite, 0, ItemType.BILLET, Purity.FUEL); });
		rod("leafRodDGOmega", "DG-OMEGA", Double.MAX_VALUE, 1916169d, true, r -> { r.setBaseItem(ModItems.particle_digamma); r.setSpecialRodModel(); r.setReactivity(2); r.addDigamma(0.333); });
		rod("leafRodKys", "Kys-3000", Double.MAX_VALUE, 22d, false, r -> { r.setAppearance(LeafiaResourceItems.get("billet_kys"), 0, ItemType.BILLET, Purity.UNSTABLE); r.setEmission(114); r.setReactivity(514); r.addIntro("Inspired by Quasar!"); r.addIntro("Corrstud will pay their life for this very rod."); });
		rod("leafRodShitfuckium", "Shitfuckium", Double.MAX_VALUE, 3200d, true, r -> { r.setAppearance(ModItems.rbmk_pellet_drx, 0, ItemType.BILLET, Purity.RAW); });
		rod("leafRodCatsmile", "Catsmilium", Double.MAX_VALUE, Double.MAX_VALUE, false, r -> { r.setSpecialRodModel(); r.addIntro("Image by nootles"); r.addIntro("Generates power with pure happiness"); r.addIntro("Never melts down"); });
		rod("leafRodConfusion", "Confusium", Double.MAX_VALUE, 1300d, false, r -> { r.setSpecialRodModel(); r.addIntro("??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"); });
	}

	/** The five depleted fuels: (registry name, texture namespace, texture). Upstream's {@code DepletedFuels}. */
	private static void declareDepleted() {
		depleted("waste_u238", "hbm", "waste_uranium");
		depleted("waste_mes", "hbm", "waste_schrabidium");
		depleted("waste_hes", "hbm", "waste_schrabidium");
		depleted("waste_sch", "hbm", "waste_schrabidium");
		depleted("waste_sol", "hbm", "waste_schrabidium");
	}

	/** One row per rod whose life is above zero: upstream's {@code FuelLives}, the numbers a config may override. */
	public static final RodDefaults[] CONFIG = {
		new RodDefaults("u238", 4000000d, 1d, 1d),
		new RodDefaults("nu", 4000000d, 1d, 0.4),
		new RodDefaults("meu235", 4000000d, 1d, 1d),
		new RodDefaults("heu233", 1000000d, 1d, 2d),
		new RodDefaults("heu235", 3000000d, 1d, 1.75),
		new RodDefaults("thmeu", 3500000d, 1d, 1d),
		new RodDefaults("lepu239", 5000000d, 1d, 1/1.5),
		new RodDefaults("mepu239", 5000000d, 1d, 1d),
		new RodDefaults("hepu239", 5000000d, 1d, 1.25),
		new RodDefaults("hepu241", 18000000d, 1d, 1.45),
		new RodDefaults("leam242", 18000000d, 1d, 1d),
		new RodDefaults("meam242", 18000000d, 1d, 1d),
		new RodDefaults("heam241", 18000000d, 1d, 1d),
		new RodDefaults("heam242", 18000000d, 1d, 1d),
		new RodDefaults("menp237", 5200000d, 1d, 1d),
		new RodDefaults("henp237", 3200000d, 1d, 1d),
		new RodDefaults("mox", 6000000d, 1d, 1d),
		new RodDefaults("lesa326", 3500000d, 0.75, 1d),
		new RodDefaults("mesa326", 3000000d, 1d, 1d),
		new RodDefaults("hesa326", 2500000d, 1.25, 1d),
		new RodDefaults("sa326", 2000000d, 2.25, 1d),
		new RodDefaults("leaus", 5500000d, 0.3, 1d),
		new RodDefaults("heaus", 5500000d, 1.2, 1d),
		new RodDefaults("ra226be", 400000d, 1d, 1d),
		new RodDefaults("po210be", 2000000d, 1d, 1d),
		new RodDefaults("pu238be", 2000000d, 1d, 1d),
		new RodDefaults("pu238", 3500000d, 1d, 1d),
		new RodDefaults("po210", 3000000d, 1d, 1d),
		new RodDefaults("sa327", 8000000d, 65d, 1/45d),
		new RodDefaults("lithium", 3000d, 1d, 1d),
		new RodDefaults("pu240", 65000000d, 1d, 1d),
		new RodDefaults("lead", 99999999d, 1d, 1d),
		new RodDefaults("balefire", 200d, 8d, 1/6d),
		new RodDefaults("blazingbalefire", 65000d, 12d, 1/9d),
		new RodDefaults("npu", 2600000d, 1d, 1d),
		new RodDefaults("th232", 90000d, 1d, 1d),
		new RodDefaults("au198", 900000d, 1d, 1d),
		new RodDefaults("pb209", 600000d, 1d, 1d),
		new RodDefaults("potentialinstantblowoutapplicator", 95000000d, 1d, 1d),
		new RodDefaults("yhxxx", 6666666d, 1d, 1d),
		new RodDefaults("dgomega", Double.MAX_VALUE, 1d, 2d),
		new RodDefaults("kys3000", Double.MAX_VALUE, 114d, 514d),
		new RodDefaults("shitfuckium", Double.MAX_VALUE, 1d, 1d),
		new RodDefaults("catsmilium", Double.MAX_VALUE, 1d, 1d),
		new RodDefaults("confusium", Double.MAX_VALUE, 1d, 1d),
	};
	// END GENERATED
}
