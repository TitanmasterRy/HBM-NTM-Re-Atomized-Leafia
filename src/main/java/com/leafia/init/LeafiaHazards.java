package com.leafia.init;

import com.hbm.blocks.ModBlocks;
import java.util.Map;

import javax.annotation.Nullable;

import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeCoal;
import com.hbm.hazard.type.HazardTypeDigamma;
import com.hbm.hazard.type.HazardTypeAsbestos;
import com.hbm.hazard.type.HazardTypeHydroactive;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.items.ModItems;
import com.hbm.lib.NtmTags;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import java.util.function.Predicate;
import com.leafia.config.LeafiaFeatures;
import com.leafia.database.AddonOreDictHazards;
import com.leafia.init.hazards.types.HazardTypeAlkaline;
import com.leafia.init.hazards.types.HazardTypeSharpEdges;
import com.leafia.init.hazards.types.HazardTypeToxic;
import com.leafia.init.hazards.types.LCERad;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.dev.items.itembase.AddonItemHazardBase;
import com.leafia.init.hazards.ItemRads;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Beta;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.init.hazards.types.radiation.Radon;
import com.leafia.init.hazards.types.radiation.XRay;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * What the addon makes hazardous, registered through the base mod's public hazard API - no mixin,
 * no edit to {@code com.hbm} (D-1001). The partial port of upstream's {@code AddonHazards}
 * (D-1078): <b>only the registrations whose targets exist as items and blocks</b>.
 *
 * <p>Runs from {@code FMLCommonSetupEvent}'s work queue, after the base mod's own
 * {@code HazardRegistry.registerItems()} - which it has to: {@link ItemRads} <em>replaces</em> the
 * radiation entry that registry put on each item, and one that ran first would find nothing to
 * replace and then be overwritten. The addon loads after {@code hbm}, so its listener is queued
 * after the base mod's; {@link #baseWasFirst} records what it saw, and a GameTest reads it.
 *
 * <h2>Two switches, and they are not the same switch</h2>
 *
 * <ul>
 *   <li>{@code features.radiationClassification} (off) gates every {@link ItemRads} line. It is
 *       upstream's {@code enableHealthMod}, and it replaces the base mod's radiation values on
 *       base-mod items, so it is an overhaul and defaults off like the others (D-1002).</li>
 *   <li>The digamma on the osmiridium blocks is <b>not</b> classification. Upstream registers it
 *       unconditionally; here it rides {@code features.osmiridiumBlocks}, the feature that owns
 *       the blocks.</li>
 * </ul>
 *
 * <h2>What is not here, and why</h2>
 *
 * Every line naming an ore-dictionary frame ({@code OreDictManager.PU239}, {@code AddonOreDict.FR},
 * the alkaline metals, lead, cadmium, arsenic and beryllium's toxicity) waits for
 * {@code OreDictManager} (D-950) and for {@code AddonOreDictHazards}' tables, which the base mod's
 * ore dictionary fills. {@code pellet_rtg_nc279} landed with the RT heater (D-3020); the digammitite block metas landed in D-3004. The sharp-edge lines wait for
 * {@code LeafiaServerListener}, which is what makes an edge cut - among them the PWR's debris, ported in
 * D-3010 ({@code lwr_shrapnel} SHARP 35, {@code lwr_shard} SHARP 85). See D-1078's table.
 * {@link #registerItemHazards} is the one section added since: the fifty material items' own
 * hazards and the five {@code dfcsh_*} (D-1085).
 */
public final class LeafiaHazards {

	private LeafiaHazards() {}

	/** The digamma a digammitite item carries, by level (D-3004): upstream's {@code 0.2F, 0.4F, 0.6F, 0.8F, 1F}. */
	public static final float[] DIGAMMITITE_DIGAMMA = {0F, 0.2F, 0.4F, 0.6F, 0.8F, 1F};

	/**
	 * Digamma as a carried hazard: the base mod's own type. (The addon carried a stand-in until the
	 * base mod ported {@code HazardTypeDigamma}; D-3023 retired it. The field stays so a caller reads
	 * {@code LeafiaHazards.DIGAMMA} as before.)
	 */
	public static final HazardTypeBase DIGAMMA = HazardRegistry.DIGAMMA;

	/** The addon's four hazard types, singletons as the base mod's are (identity is how an entry is found). */
	public static final HazardTypeBase SHARP = HazardTypeSharpEdges.INSTANCE;
	public static final HazardTypeBase ALKALINE = HazardTypeAlkaline.INSTANCE;
	public static final HazardTypeBase TOXIC = HazardTypeToxic.INSTANCE;
	/** Upstream's {@code CONTAINER_ENDO}: the APR's endothermic blast when the item is destroyed (D-3027). */
	public static final HazardTypeBase CONTAINER_ENDO = com.leafia.init.hazards.types.containment.HazardTypeEndothermic.INSTANCE;

	/**
	 * Whether the base mod's radiation table was already in place the <em>first</em> time
	 * {@link #register} ran - the real one, from the setup queue. Later calls (a test's) do not
	 * overwrite it, or it could not catch the order being wrong.
	 */
	public static boolean baseWasFirst = false;
	private static boolean ran = false;

	public static void register() {

		if(!ran) {
			ran = true;
			baseWasFirst = HazardSystem.itemMap.containsKey(ModItems.egg_balefire.get());
		}

		// ---- radiation classification: ItemRads, in upstream's order (every line is a no-op while the switch is off) ----
		ItemRads.actinium227.register(OreDictManager.AC227);
		ItemRads.americium241.register(OreDictManager.AM241);
		ItemRads.americium242.register(OreDictManager.AM242);
		ItemRads.americiumRG.register(OreDictManager.AMRG);
		ItemRads.balefire.register(ModItems.egg_balefire.get());
		ItemRads.balefire.multiply(0.1).register(ModItems.egg_balefire_shard.get());
		ItemRads.cobalt60.register(OreDictManager.CO60);
		ItemRads.corium.register(ModBlocks.block_corium.get());
		ItemRads.corium.register(ModBlocks.block_corium_cobble.get());
		if(LeafiaBlocks.ore_corium_chernobylite != null) ItemRads.corium.register(LeafiaBlocks.ore_corium_chernobylite.get());
		if(LeafiaBlocks.ore_corium_zetalite != null) ItemRads.corium.register(LeafiaBlocks.ore_corium_zetalite.get());
		ItemRads.fissium.register(LeafiaOreDict.FS);
		ItemRads.fissite.register(LeafiaOreDict.FSALLOY);
		ItemRads.francium.register(LeafiaOreDict.FR);
		ItemRads.gold198.register(OreDictManager.AU198);
		ItemRads.lead209.register(OreDictManager.PB209);
		ItemRads.neptunium237.register(OreDictManager.NP237);
		ItemRads.nuclium279.register(LeafiaOreDict.NC279);
		// features.rtg (D-3020): half nuclium's rating, upstream's line 75
		if(LeafiaItems.pellet_rtg_nc279 != null) ItemRads.nuclium279.multiply(0.5).register(LeafiaItems.pellet_rtg_nc279.get());
		ItemRads.plutonium.register(OreDictManager.PU);
		ItemRads.plutoniumRG.register(OreDictManager.PURG);
		ItemRads.plutonium238.register(OreDictManager.PU238);
		ItemRads.plutonium239.register(OreDictManager.PU239);
		ItemRads.plutonium240.register(OreDictManager.PU240);
		ItemRads.plutonium241.register(OreDictManager.PU241);
		ItemRads.polonium210.register(OreDictManager.PO210);
		ItemRads.radium226.register(OreDictManager.RA226);
		ItemRads.schrabidium326.register(OreDictManager.SA326);
		ItemRads.solinium327.register(OreDictManager.SA327);
		ItemRads.schrabidate.register(OreDictManager.SBD);
		ItemRads.schraranium.register(OreDictManager.SRN);
		ItemRads.technetium99.register(OreDictManager.TC99);
		ItemRads.thorium232.register(OreDictManager.TH232);
		ItemRads.uranium.register(OreDictManager.U);
		ItemRads.uranium233.register(OreDictManager.U233);
		ItemRads.uranium235.register(OreDictManager.U235);
		ItemRads.uranium238.register(OreDictManager.U238);
		ItemRads.waste.multiply(3).register(ModItems.nuclear_waste.get());
		ItemRads.waste_v.multiply(3).register(ModItems.nuclear_waste_vitrified.get());
		ItemRads.waste.multiply(0.3).register(ModItems.nuclear_waste_tiny.get());
		ItemRads.waste_v.multiply(0.3).register(ModItems.nuclear_waste_vitrified_tiny.get());
		ItemRads.waste.register(ModItems.billet_nuclear_waste.get());

		// ---- the DFC's shrapnels (hot and digamma; the sharp edges only with the hazard overhaul) ----
		registerItemHazards();

		// ---- the alkali metals and the toxic metals, by ore name (features.hazardOverhaul) ----
		if(LeafiaFeatures.hazardOverhaul) {
			registerHazardRegular(OreDictManager.LI, new HazardEntry(ALKALINE, 1F));
			registerHazardRegular(OreDictManager.NA, new HazardEntry(ALKALINE, 2F));
			registerHazardRegular(LeafiaOreDict.K, new HazardEntry(ALKALINE, 3F));
			registerHazardRegular(LeafiaOreDict.RB, new HazardEntry(ALKALINE, 4F));
			registerHazardRegular(OreDictManager.CS, new HazardEntry(ALKALINE, 5F));
			registerHazardRegular(OreDictManager.CS137, new HazardEntry(ALKALINE, 5F));
			registerHazardRegular(LeafiaOreDict.FR, new HazardEntry(ALKALINE, 6F));
			registerHazardRegular(OreDictManager.SR, new HazardEntry(ALKALINE, 1.5F));
			registerHazardRegular(OreDictManager.SR90, new HazardEntry(ALKALINE, 1.5F));

			registerHazardRegular(OreDictManager.PB, new HazardEntry(TOXIC, 1.75F));
			registerHazardRegular(OreDictManager.BE, new HazardEntry(TOXIC, 1.75F));
			registerHazardRegular(OreDictManager.CD, new HazardEntry(TOXIC, 5F));
			registerHazardRegular(OreDictManager.AS, new HazardEntry(TOXIC, 10F));

			// sharp edges on the base mod's things
			HazardSystem.register(ModItems.blades_desh.get(), makeData(SHARP, 40F));
			HazardSystem.register(ModItems.blades_steel.get(), makeData(SHARP, 40F));
			HazardSystem.register(ModItems.blades_titanium.get(), makeData(SHARP, 40F));
			HazardSystem.register(ModItems.nugget_zirconium.get(), makeData(SHARP, 10F));
			HazardSystem.register(ModItems.debris_shrapnel.get(), makeData(SHARP, 1F));
			// and on the PWR's debris (upstream: pwr_shrapnel 35, pwr_shard 85)
			if(LeafiaItems.lwr_shrapnel != null) HazardSystem.register(LeafiaItems.lwr_shrapnel.get(), makeData(SHARP, 35F));
			if(LeafiaItems.lwr_shard != null) HazardSystem.register(LeafiaItems.lwr_shard.get(), makeData(SHARP, 85F));

			// osmiridium is the base mod's frame, so the digamma on it is an overhaul too
			registerHazard(OreDictManager.OSMIRIDIUM, new HazardEntry(HazardRegistry.DIGAMMA, 0.004F));
		}

		// ---- xanaxium, the addon's own: digamma on its names, then its classified radiation ----
		if(LeafiaFeatures.resourceItems) registerHazard(LeafiaOreDict.XN, new HazardEntry(HazardRegistry.DIGAMMA, 0.004F));
		ItemRads.xanaxium.register(LeafiaOreDict.XN);

		// ---- digamma, on the osmiridium blocks (features.osmiridiumBlocks) ----
		if(LeafiaBlocks.block_welded_osmiridium != null) registerDigamma(LeafiaBlocks.block_welded_osmiridium.get(), 0.04F);
		if(LeafiaBlocks.block_xanaxium != null) registerDigamma(LeafiaBlocks.block_xanaxium.get(), 0.04F);

		// ---- digamma, on the digammitite items (features.digammitite; D-3004) ----
		// Upstream registers levels 1 to 5 and leaves the coolest bare: 0.2, 0.4, 0.6, 0.8 and 1.
		if(LeafiaBlocks.digammitite != null) {
			for(int level = 1; level <= 5; level++)
				registerDigamma(LeafiaBlocks.META_ITEMS.get("digammitite").get(level).get(), DIGAMMITITE_DIGAMMA[level]);
		}

		// ---- the absorption particle capsule's containment: an endothermic blast of 15 when destroyed (features.apr; D-3027) ----
		if(LeafiaFeatures.aprReactor() && LeafiaResourceItems.get("particle_absorption") != null)
			HazardSystem.register(LeafiaResourceItems.get("particle_absorption").get(), makeData(CONTAINER_ENDO, 15F));

		// ---- and the last pass: alkaline replaces hydroreactive on everything (features.hazardOverhaul) ----
		if(LeafiaFeatures.hazardOverhaul) removeHydroactive();

		// ---- the mono filter takes fine particles, which the radon rework asks for (ArmorInit) ----
		registerArmor();
	}

	/**
	 * Upstream's {@code ArmorInit.postInit}: the mono gas-mask filter protects against fine particles
	 * as well as coarse ones and carbon monoxide. It replaces the base mod's list, so it is part of the
	 * hazard overhaul - the reworked radon asks for exactly this class.
	 */
	public static void registerArmor() {
		if(!LeafiaFeatures.hazardOverhaul) return;
		ArmorRegistry.registerHazard(ModItems.gas_mask_filter_mono.get(),
				HazardClass.PARTICLE_COARSE, HazardClass.GAS_MONOXIDE, HazardClass.PARTICLE_FINE);
	}

	/** Upstream's final {@code compute(...)}: every {@code HazardTypeHydroactive} entry in the three maps, deleted. */
	public static void removeHydroactive() {
		for(HazardData data : HazardSystem.oreMap.values()) data.getEntries().removeIf(e -> e.getType() instanceof HazardTypeHydroactive);
		for(HazardData data : HazardSystem.itemMap.values()) data.getEntries().removeIf(e -> e.getType() instanceof HazardTypeHydroactive);
		for(HazardData data : HazardSystem.stackMap.values()) data.getEntries().removeIf(e -> e.getType() instanceof HazardTypeHydroactive);
	}

	private static HazardData makeData(HazardTypeBase type, float level) {
		return new HazardData().addEntry(type, level);
	}

	/** Upstream's {@code registerHazardRegular}: the names an alkali or toxic metal is handled in - ingot, dust, block, plate, bedrock, billet and crystal. */
	public static void registerHazardRegular(DictFrame frame, HazardEntry... entries) {
		registerHazard(frame, s -> s.startsWith("ingot") || s.startsWith("dust") || s.startsWith("block")
				|| s.startsWith("plate") || s.startsWith("bedrock") || s.startsWith("billet") || s.startsWith("crystal"), entries);
	}

	public static void registerHazard(DictFrame frame, HazardEntry... entries) {
		registerHazard(frame, s -> true, entries);
	}

	/**
	 * Upstream's {@code registerHazard(frame, processor, entries)}: for every name the frame goes by
	 * that {@code processor} accepts, adds each entry to that name's data. Radiation, digamma, coal and
	 * asbestos scale by the name's shape multiplier; toxic by a quarter of it ({@code level + (level x mult - level) x 0.25}).
	 * <b>Idempotent here:</b> an entry of the same type already on the name is replaced, where upstream
	 * would pile a second on (it is only ever run once).
	 */
	public static void registerHazard(DictFrame frame, Predicate<String> processor, HazardEntry... entries) {
		for(java.util.Map.Entry<String, Float> name : AddonOreDictHazards.table(frame).entrySet()) {
			if(!processor.test(name.getKey())) continue;
			HazardData data = HazardSystem.oreMap.computeIfAbsent(NtmTags.ore(name.getKey()), k -> new HazardData());
			for(HazardEntry hazard : entries) {
				double level = hazard.getBaseLevel();
				HazardTypeBase type = hazard.getType();
				if(type instanceof HazardTypeRadiation || type instanceof LCERad || type instanceof HazardTypeDigamma
						|| type instanceof HazardTypeCoal || type instanceof HazardTypeAsbestos)
					level *= name.getValue();
				if(type instanceof HazardTypeToxic)
					level = level + (level * name.getValue() - level) * 0.25;
				data.getEntries().removeIf(e -> e.getType() == type);
				data.addEntry(new HazardEntry(type, (float) level));
			}
		}
	}

	/**
	 * Upstream's loop over {@code AddonItemHazardBase.ALL_HAZARD_ITEMS}, and the five explicit
	 * {@code dfcsh_*} registrations beside it (D-1085).
	 *
	 * <p><b>Not behind {@code radiationClassification}.</b> The loop in {@code AddonHazards} has no
	 * {@code enableHealthMod} test - only {@code ItemRads.apply} has one, and this does not go
	 * through it. These are the addon's <em>own</em> items, so there is no base-mod figure to
	 * replace, and the same reasoning that put the digamma on the osmiridium blocks on its
	 * block's switch (D-1078) puts these on the item feature's: an item exists only when its
	 * switch is on, and carries what it was declared with. The corium ores, which D-1078 did put
	 * behind classification, reach their radiation through {@code ItemRads.register}; these do not.
	 * <b>A human may want it the other way</b> - it is a one-line test at the top of this method.
	 *
	 * <p>Entries are added in upstream's order - alpha, beta, gamma, x-ray, neutrons, activation,
	 * radon, digamma, fire, cryogenic, toxic, blinding, asbestos, coal, alkaline, explosive, sharp -
	 * with no {@code NBTModifier}, which only {@code ItemRads.apply} attaches. Cryogenic has no type
	 * to land on (the base mod's cold hazard is not ported) and is skipped; toxic, alkaline and sharp are
	 * skipped while {@code features.hazardOverhaul} is off (D-3023); and
	 * {@link AddonItemHazardBase#unwired()} names the ones an item asked for. Idempotent: it
	 * replaces an item's data rather than adding to it.
	 */
	public static void registerItemHazards() {

		for(DeferredHolder<Item, ? extends Item> holder : LeafiaItems.ITEMS.getEntries()) {
			if(!holder.isBound() || !(holder.get() instanceof AddonItemHazardBase item)) continue;
			HazardData data = itemHazards(item);
			if(!data.getEntries().isEmpty()) HazardSystem.register(item, data);
		}

		// The DFC's absorber shrapnels: upstream registers these by hand, sharp edges first (5, 5 and 25
		// on the cable, the corner and the beam). The SHARP entries are the hazard overhaul's: the
		// type's damage is LeafiaSharpEdges, and a "+5%" line that did nothing would be a lie.
		registerShrapnel("dfcsh_cable", 5F, null, 0.003F);
		registerShrapnel("dfcsh_core", null, 10F, 0F);
		registerShrapnel("dfcsh_corner", 5F, null, 0.005F);
		registerShrapnel("dfcsh_front", null, null, 0.004F);
		registerShrapnel("dfcsh_beam", 25F, null, 0.002F);
	}

	private static void registerShrapnel(String name, @Nullable Float sharp, @Nullable Float hot, float digamma) {
		DeferredHolder<Item, Item> holder = LeafiaResourceItems.get(name);
		if(holder == null) return;
		HazardData data = new HazardData();
		if(sharp != null && LeafiaFeatures.hazardOverhaul) data.addEntry(SHARP, sharp);
		if(hot != null) data.addEntry(HazardRegistry.HOT, hot);
		if(digamma != 0F) data.addEntry(DIGAMMA, digamma);
		HazardSystem.register(holder.get(), data);
	}

	/** What one {@link AddonItemHazardBase} declares, as hazard data. Upstream's loop body. */
	public static HazardData itemHazards(AddonItemHazardBase item) {

		Map<Class<? extends HazardTypeBase>, HazardModifier> mods = item.mods;
		HazardData data = new HazardData();

		if(item.radContainer != null) {
			add(data, generateHazEntry(Alpha.INSTANCE, item.radContainer.alpha, mods));
			add(data, generateHazEntry(Beta.INSTANCE, item.radContainer.beta, mods));
			add(data, generateHazEntry(Gamma.INSTANCE, item.radContainer.gamma, mods));
			add(data, generateHazEntry(XRay.INSTANCE, item.radContainer.x, mods));
			add(data, generateHazEntry(Neutrons.INSTANCE, item.radContainer.neutrons, mods));
			add(data, generateHazEntry(HazardRegistry.RADIATION, item.radContainer.activation, mods));
			add(data, generateHazEntry(Radon.INSTANCE, item.radContainer.radon, mods));
		}
		add(data, generateHazEntry(DIGAMMA, item.digamma, mods));
		add(data, generateHazEntry(HazardRegistry.HOT, item.fire, mods));
		// cryogenic: no type to land on (the base mod's HazardTypeCold is not ported either)
		if(LeafiaFeatures.hazardOverhaul) add(data, generateHazEntry(TOXIC, item.toxic, mods));
		add(data, generateHazEntry(HazardRegistry.BLINDING, item.blinding ? 110 : 0, mods));
		add(data, generateHazEntry(HazardRegistry.ASBESTOS, item.asbestos, mods));
		add(data, generateHazEntry(HazardRegistry.COAL, item.coal, mods));
		if(LeafiaFeatures.hazardOverhaul) add(data, generateHazEntry(ALKALINE, item.alkaline, mods));
		add(data, generateHazEntry(HazardRegistry.EXPLOSIVE, item.explosive, mods));
		if(LeafiaFeatures.hazardOverhaul) add(data, generateHazEntry(SHARP, item.sharp, mods));
		return data;
	}

	private static void add(HazardData data, @Nullable HazardEntry entry) {
		if(entry != null) data.addEntry(entry);
	}

	/** Upstream's {@code generateHazEntry}: nothing for zero, else the type at that level with its modifier, if any. */
	@Nullable
	public static HazardEntry generateHazEntry(HazardTypeBase template, double value,
			@Nullable Map<Class<? extends HazardTypeBase>, HazardModifier> mods) {
		if(value == 0) return null;
		HazardEntry entry = new HazardEntry(template, (float) value);
		if(mods != null && mods.containsKey(template.getClass())) entry.addMod(mods.get(template.getClass()));
		return entry;
	}

	private static void registerDigamma(ItemLike item, float level) {
		HazardSystem.register(item, new HazardData().addEntry(DIGAMMA, level));
	}
}
