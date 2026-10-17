package com.leafia.contents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.minerals.AbsorptionParticleItem;
import com.leafia.contents.minerals.KysBilletItem;
import com.leafia.contents.minerals.MysticiteIngotItem;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.items.itembase.AddonItemHazardBase;
import com.leafia.init.hazards.ItemRads;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's simple material items: ingots, powders, billets, plates, blankets, anti-mass cells,
 * particle capsules, the DFC absorber shrapnels and a few parts (D-1085).
 *
 * <p>Upstream: the {@code Resources} nested class of {@code AddonItems} plus twenty-odd loose
 * fields, every one {@code new AddonItemBaked(name[, texture])} or {@code new
 * AddonItemHazardBaked(...)} with a chain of {@code addRad}/{@code addFire}/... calls. None of
 * them has behaviour; each is a name, a texture, a tab and up to a few hazard numbers. Their
 * sources - the crucible, the solar blaster, the DFC, SILEX, the cyclotron - are not ported, so a
 * player can take them from the creative tab and nothing else.
 *
 * <p><b>The declarations are generated.</b> a generator script reads
 * upstream's {@code AddonItems.java} and rewrites the block between the two markers below, in
 * upstream's order (which is the creative tab's order). It also copies the textures, writes the
 * item models, the lang keys and the item tags. Retyping 54 rows is what the script is for; an
 * edit inside the markers is lost on the next run.
 *
 * <h2>The gate</h2>
 *
 * Everything registers behind {@code features.resourceItems}, with one exception: the fissite and
 * mysticite ingots also register when {@code features.shields} is on, because the shields are
 * repaired with them (D-1083). Whichever switch is on, the ingot is registered <em>once</em>, from
 * this table, and {@link LeafiaItems#ingot_fissite} and {@link LeafiaItems#ingot_mysticite} are
 * handed it.
 *
 * <h2>What a row means</h2>
 *
 * <ul>
 *   <li>{@code plain(name, tab)} - {@code AddonItemBaked}: a stack of 64 with a flat model.</li>
 *   <li>{@code hazard(name, tab, ...)} - {@code AddonItemHazardBaked}: the same, as an
 *       {@link AddonItemHazardBase} carrying upstream's {@code add*} chain. Hazards are turned into
 *       {@code HazardData} by {@link com.leafia.init.LeafiaHazards#registerItemHazards}.</li>
 *   <li>{@code custom(name, tab, factory)} - upstream's anonymous subclasses, which the script
 *       refuses to translate itself and takes from its {@code CUSTOM} table.</li>
 *   <li>{@code tab == false} - upstream's {@code setCreativeTab(null)}: registered, unreachable
 *       from creative.</li>
 * </ul>
 */
public final class LeafiaResourceItems {

	private LeafiaResourceItems() {}

	/** Every item this class registered, by registry name, in registration order. */
	public static final Map<String, DeferredItem<? extends Item>> BY_NAME = new LinkedHashMap<>();

	/** The two ingots a switch other than {@code resourceItems} can bring in. */
	private static final Set<String> SHIELD_INGOTS = Set.of("ingot_fissite", "ingot_mysticite");

	/** The registry name of every item in the table, whether or not a switch let it register. */
	public static final java.util.List<String> NAMES = new java.util.ArrayList<>();

	/** The pellet {@code features.rtg} needs whatever this table's own switch says: its Nuclium-279 pellet decays into it (D-3020). */
	public static final String RTG_DECAY_PELLET = "pellet_rtg_fissium";

	/** Whether {@code name} registers under the current switches. */
	public static boolean gated(String name) {
		return LeafiaFeatures.resourceItems || (LeafiaFeatures.shields && SHIELD_INGOTS.contains(name))
				|| (LeafiaFeatures.rtg && RTG_DECAY_PELLET.equals(name));
	}

	/** The registered item, or null when its switch is off. */
	@SuppressWarnings("unchecked")
	public static <T extends Item> DeferredItem<T> get(String name) {
		return (DeferredItem<T>) BY_NAME.get(name);
	}

	/** Where {@link #put} is writing: the real registry, or a scratch one during {@link #trial}. */
	private static DeferredRegister.Items sink;
	private static Map<String, DeferredItem<? extends Item>> into;
	private static boolean real;

	/** Declares the table. Called from {@link LeafiaItems#register} before the registry is handed over. */
	static void register() {
		BY_NAME.clear();
		NAMES.clear();
		sink = LeafiaItems.ITEMS;
		into = BY_NAME;
		real = true;
		declare();
	}

	/**
	 * Runs the whole table against a scratch registry under whatever the switches say now, touching
	 * neither the real registry, the creative tab nor {@link #BY_NAME}; returns what it registered.
	 * A test seam, and the only way to ask "does a switched-off feature register nothing" without
	 * restarting the game: the switches are read once, at registration.
	 */
	public static Map<String, DeferredItem<? extends Item>> trial(DeferredRegister.Items scratch) {
		Map<String, DeferredItem<? extends Item>> out = new LinkedHashMap<>();
		sink = scratch;
		into = out;
		real = false;
		try {
			declare();
		} finally {
			sink = LeafiaItems.ITEMS;
			into = BY_NAME;
			real = true;
		}
		return out;
	}

	private static String tab(boolean inTab) {
		return inTab ? LeafiaCreativeTabs.MAIN : null;
	}

	private static <T extends Item> void put(String name, boolean inTab, Function<Item.Properties, T> factory) {
		if(real) NAMES.add(name);
		if(!gated(name)) return;
		DeferredItem<T> item = sink.registerItem(name, factory, new Item.Properties());
		if(real) LeafiaCreativeTabs.add(tab(inTab), item);
		into.put(name, item);
	}

	private static void plain(String name, boolean inTab) {
		put(name, inTab, Item::new);
	}

	private static void hazard(String name, boolean inTab, Consumer<AddonItemHazardBase> chain) {
		put(name, inTab, p -> {
			AddonItemHazardBase item = new AddonItemHazardBase(p);
			chain.accept(item);
			return item;
		});
	}

	private static <T extends Item> void custom(String name, boolean inTab, Function<Item.Properties, T> factory) {
		put(name, inTab, factory);
	}

	// BEGIN GENERATED
	private static void declare() {
		plain("dfcsh_cable", true);
		plain("dfcsh_core", true);
		plain("dfcsh_corner", true);
		plain("dfcsh_front", true);
		plain("dfcsh_beam", true);
		plain("ingot_potassium", true);
		plain("ingot_rubidium", true);
		plain("ingot_francium", true);
		hazard("nugget_schraranium", true, h -> {});
		plain("ingot_xanaxium", true);
		plain("powder_xanaxium", true);
		custom("billet_kys", false, p -> new KysBilletItem(p).addCryogenic(5));
		hazard("powder_digammitite", true, h -> h.addDigamma(0.1));
		hazard("powder_digammitite_tiny", true, h -> h.addDigamma(0.01));
		hazard("ingot_nc279", true, h -> h.addRad(ItemRads.nuclium279).addFire(4));
		hazard("powder_nc279", true, h -> h.addRad(ItemRads.nuclium279.multiply(3)).addFire(4));
		hazard("billet_nc279", true, h -> h.addRad(ItemRads.nuclium279.multiply(0.5)).addFire(4));
		plain("ingot_taintium", true);
		plain("powder_taintium", true);
		hazard("ingot_fissium", true, h -> h.addRad(ItemRads.fissium));
		hazard("billet_fissium", true, h -> h.addRad(ItemRads.fissium.multiply(0.5)));
		plain("ingot_chydalium", true);
		plain("powder_chydalium", true);
		plain("billet_chydalium", true);
		hazard("ingot_fissite", true, h -> h.addRad(ItemRads.fissite));
		hazard("plate_fissite", true, h -> h.addRad(ItemRads.fissite));
		custom("ingot_mysticite", true, MysticiteIngotItem::new);
		plain("plate_mysticite", true);
		plain("ingot_tnalloy", true);
		plain("component_limiter", true);
		plain("component_emitter", true);
		plain("supercooler", true);
		plain("dna_felid", false);
		plain("dna_canid", false);
		plain("dna_eevee", false);
		plain("particle_dineutron", true);
		hazard("pellet_rtg_fissium", true, h -> h.addRad(ItemRads.fissium.multiply(0.5)).setContainerItem(ModItems.plate_iron));
		hazard("cells_fissite", true, h -> h.addRad(ItemRads.fissite.multiply(5)));
		plain("cells_mysticite", true);
		plain("cells_mysticite_filled", true);
		plain("particle_taint", true);
		plain("particle_cloud", true);
		plain("grenade_cloud", true);
		plain("grenade_pink_cloud", true);
		hazard("blanket_fissite", true, h -> h.addRad(ItemRads.fissite.multiply(1/16d)));
		plain("blanket_mysticite", true);
		hazard("blanket_fissite_bundle", true, h -> h.addRad(ItemRads.fissite.multiply(1/2d)));
		plain("blanket_mysticite_bundle", true);
		plain("particle_chydalium", true);
		custom("particle_absorption", true, AbsorptionParticleItem::new);
	}
	// END GENERATED
}
