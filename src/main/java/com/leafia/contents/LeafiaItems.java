package com.leafia.contents;

import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity.DebrisType;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisItem;
import com.leafia.contents.consumable.medication.PillMeltdownItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.control.battery.AddonBatteryPackItem;
import com.leafia.contents.control.battery.AddonEnumBatteryPack;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.leafia.contents.minerals.MysticiteIngotItem;
import com.leafia.init.LeafiaToolMaterials;
import com.hbm.items.ItemCustomLore;
import com.leafia.contents.machines.powercores.dfc.CrucifixItem;
import com.leafia.contents.machines.powercores.dfc.LCEItemLens;
import com.hbm.items.tool.NtmTiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.elevators.car.chips.EvChipItem;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem.StyleType;
import com.leafia.contents.machines.elevators.items.EvSpawnItem;
import com.leafia.contents.machines.elevators.items.WeightSpawnItem;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.items.itembase.AddonItemHazardBase;
import com.leafia.lib.LeafiaRef;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's item registry, mirroring {@code com.hbm.items.ModItems} and upstream's
 * {@code AddonItems}. <b>The addon's first</b> — until now every registered thing here was a
 * block, and {@link LeafiaBlocks} carried its own {@code BlockItem}s.
 *
 * <h2>The gate</h2>
 *
 * Same shape as {@link LeafiaBlocks}, for the same reason: content registers inside
 * {@link #register}, behind its feature switch, rather than in a static field initialiser that
 * would run unconditionally whenever the class is touched (D-1002). A gated item is null when its
 * feature is off, and every consumer of one must be gated by the same switch.
 *
 * <h2>Upstream's nesting</h2>
 *
 * {@code AddonItems} groups its items in nested classes — {@code ElevatorStyles},
 * {@code Resources}, {@code Guns}, {@code DepletedFuels}. Those become sections here rather than
 * nested types: the nesting exists upstream so that a 1.12.2 static initialiser fires in a
 * controlled order, and {@code DeferredRegister} has no such ordering to control. Where a nested
 * class also holds a *list* the rest of the mod reads — {@code ElevatorStyles.styleItems} — that
 * list is kept, because it is real API and not grouping.
 */
public final class LeafiaItems {

	private LeafiaItems() {}

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LeafiaRef.MODID);

	/**
	 * Every cabin style item, in registration order — upstream's {@code ElevatorStyles.styleItems},
	 * which the car reads to decide what a style slot will accept.
	 *
	 * <p>Upstream fills it from {@link EvStyleItem}'s own constructor. Here the registry fills it,
	 * because a {@code DeferredItem} is built lazily and a constructor that registered itself into
	 * a static list would run at an unpredictable point in load.
	 */
	public static final List<DeferredItem<EvStyleItem>> style_items = new ArrayList<>();

	/** The same, keyed by the style id the car stores in NBT ({@code s6wall}, {@code s6floor}, …). */
	public static final Map<String, DeferredItem<EvStyleItem>> styles_by_id = new LinkedHashMap<>();

	/** The two control chips: slot 0 of a cabin decides how it behaves. */
	public static DeferredItem<EvChipItem> ev_chip_s6;
	public static DeferredItem<EvChipItem> ev_chip_skylift;

	/** Places a cabin in a shaft. Null when {@code features.elevators} is off. */
	public static DeferredItem<EvSpawnItem> ev_spawn;

	/** The radio that goes in the cabin's slot 7 (D-1088). Null when {@code features.elevators} is off. */
	public static DeferredItem<EvRadioItem> ev_radio;

	/** Places a counterweight on a shaft rail, and what a destroyed one drops. */
	public static DeferredItem<WeightSpawnItem> weight_spawn;

	/**
	 * The fuzzy identifier: one fluid on a stack, stamped over a whole run of ducts in a click.
	 *
	 * <p>Null when {@code features.fuzzyIdentifier} is off — and with it off, the base mod's five
	 * fluid-identifier slots go back to having no implementor at all. See
	 * {@link com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem}.
	 */
	public static DeferredItem<FuzzyIdentifierItem> fuzzy_identifier;

	/** The control upgrade (D-3021). Null when {@code features.upgradeControl} is off. */
	public static DeferredItem<com.leafia.contents.control.upgrades.AddonUpgradeItem> upgrade_control;

	/** The battery packs, by variant. Empty when {@code features.batteryPacks} is off. */
	public static final java.util.Map<AddonEnumBatteryPack, DeferredItem<AddonBatteryPackItem>> battery_packs =
			new java.util.EnumMap<>(AddonEnumBatteryPack.class);

	/** The meltdown pill. Null when {@code features.meltdownPill} is off. */
	public static DeferredItem<PillMeltdownItem> pill_meltdown;

	/** The Nuclium-279 RTG pellet (D-3020). Null when {@code features.rtg} is off. */
	public static DeferredItem<com.leafia.contents.control.fuel.AddonRTGPelletItem> pellet_rtg_nc279;

	/** The anti-mass rifle (D-3028), upstream's {@code AddonItems.Guns.am_rifle}. Null when {@code features.amRifle} is off. */
	public static DeferredItem<com.leafia.contents.gear.combat.guns.am_rifle.AMRifle> am_rifle;

	/** The pink door's item (D-3028). Null when {@code features.pinkDoor} is off. */
	public static DeferredItem<com.leafia.contents.building.pinkdoor.ItemPinkDoor> door_fuckoff;

	/** The two grenade fillings (D-3028). Null when {@code features.leafiaGrenades} is off. */
	public static DeferredItem<com.hbm.items.weapon.grenade.ItemGrenadeFilling> grenade_filling_leafia_null;
	public static DeferredItem<com.hbm.items.weapon.grenade.ItemGrenadeFilling> grenade_filling_leafia_sol;

	/** The custom nuke missile (D-3022). Null when {@code features.addonBombs} is off. */
	public static DeferredItem<com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem> missile_customnuke;

	/** The fluid bottle. Null when {@code features.fluidBottles} is off. */
	public static DeferredItem<ItemNTMFBottle> ntmfbottle;

	/**
	 * The High-Tech Advisor. Null when {@code features.advisor} is off - and then nothing hands
	 * one out either, because the login gift is gated by the same switch.
	 */
	public static DeferredItem<AdvisorItem> advisor;

	/**
	 * What the antimatter reaction chamber takes in its item slots. Null when {@code features.ams} is off.
	 *
	 * <p>{@code ams_muzzle} is NTM-CE's {@code ModItems.ams_muzzle}, an {@code ItemCustomLore} in the
	 * control tab that the emitter needs in its middle slot and that this port's base mod does not
	 * have (it is not in {@code hbm-src}); it is registered here, under the addon's own id.
	 * {@code ams_focus_limiter} and {@code ams_focus_booster} are upstream's {@code LCEItemLens}es
	 * of 2.5 billion and 100 million wear, which the stabilizer reads for its mode, with their field,
	 * drain and energy modifiers (D-3002, D-3011).
	 */
	public static DeferredItem<ItemCustomLore> ams_muzzle;

	/** The PWR's debris items (D-3010). Null unless {@code LeafiaFeatures.pwrReactor()}. */
	public static DeferredItem<PWRDebrisItem> lwr_piece;
	public static DeferredItem<PWRDebrisItem> lwr_shrapnel;
	public static DeferredItem<PWRDebrisItem> lwr_shard;
	public static DeferredItem<LCEItemLens> ams_focus_limiter;
	public static DeferredItem<LCEItemLens> ams_focus_booster;
	/** The other three stabilizer lenses, beside the two the AMC reads (DFC-2, D-3011). Null when {@code features.ams} is off. */
	public static DeferredItem<LCEItemLens> ams_focus_blank;
	public static DeferredItem<LCEItemLens> ams_focus_omega;
	public static DeferredItem<LCEItemLens> ams_focus_safe;

	/**
	 * The two crucifixes a collapsing addon DFC is stopped with ({@code fix_tool}, {@code fix_survival}).
	 * Null unless the DFC overhaul's content is registered ({@code LeafiaFeatures.dfcOverhaulContent()}).
	 */
	public static DeferredItem<CrucifixItem> fix_tool;
	public static DeferredItem<CrucifixItem> fix_survival;

	/** The six shields, by kind. Empty when {@code features.shields} is off. */
	public static final Map<Kind, DeferredItem<AddonShieldItem>> shields = new java.util.EnumMap<>(Kind.class);

	/**
	 * The fissite and mysticite ingots, which the shields are repaired with. Null when both
	 * {@code features.shields} and {@code features.resourceItems} are off; registered by
	 * {@link LeafiaResourceItems}, whichever of the two let them in (D-1085).
	 */
	public static DeferredItem<AddonItemHazardBase> ingot_fissite;
	public static DeferredItem<MysticiteIngotItem> ingot_mysticite;

	/**
	 * Two items NTM-CE has and the 1.7.10 base mod this port follows does not, which addon recipes
	 * name as {@code ModItems.powder_osmiridium} and {@code ModItems.nugget_u238m2} (D-1097 #3,
	 * D-3001). They are registered here, in the addon's namespace, and never added to the base: the
	 * electronium battery pack's recipe (and the xanaxium block's centrifuge row, the osmiridium
	 * powder's furnace row) needs them. Null when {@code features.resourceItems} is off, which is
	 * the switch upstream's own material items ride.
	 */
	public static DeferredItem<Item> powder_osmiridium;
	public static DeferredItem<Item> nugget_u238m2;

	/**
	 * Two more of them, which the recipes batch needed (D-3026): {@code plate_tungsten_carbide}, what NTM-CE's
	 * {@code OreDictManager.getReflector()} names (the RT heater's anvil row, the PWR overhaul's legacy fuel row; its
	 * ore name is {@code LeafiaOreDict.getReflector()}), and {@code sat_head_laser}, the DFC pulser's plasma forge
	 * ingredient. Null when {@code features.resourceItems} is off.
	 */
	public static DeferredItem<Item> plate_tungsten_carbide;

	/** The radiation locator goggles (D-3026). Null when {@code features.radGlasses} is off. */
	public static DeferredItem<com.leafia.contents.gear.utility.radglasses.RadGlassesItem> radglasses;
	public static DeferredItem<Item> sat_head_laser;

	/**
	 * Declares this jar's items. Called from the mod constructor <em>after</em>
	 * {@link LeafiaFeatures#refresh()}, so every switch below already holds the player's value.
	 */
	public static void register(IEventBus modBus) {

		if(LeafiaFeatures.elevators) {

			/*
			 * The six cabin styles, upstream's ids and upstream's types. Upstream derives the style
			 * id by chopping the "ev_" off the registry name (s.substring(3)); that is done here in
			 * EvStyleItem's constructor exactly as upstream does it, rather than being passed in,
			 * so the two can never disagree.
			 *
			 * D-1035 and D-1036 registered these with a NULL tab, because all six are drawn by
			 * EvStyleItemRender out of the cabin OBJ and per-part texturing had no settled shape
			 * here. It has one now: a (part, texture) pair is a model and an item is a
			 * neoforge:composite over several, which D-1041 proved over 111 of them. So the
			 * renderer does not port at all - it is eight generated item models - and all six
			 * styles go in the tab. D-1043.
			 */
			style("ev_s6floor", StyleType.FLOOR);
			style("ev_s6ceiling", StyleType.CEILING);
			style("ev_s6door", StyleType.WALL);
			style("ev_s6wall", StyleType.WALL);
			style("ev_s6window", StyleType.WALL);
			style("ev_skyliftdoor", StyleType.WALL);

			/*
			 * The two control chips. Unlike the styles these DO have a flat texture upstream -
			 * items/leafia/elevators/chip_s6.png and chip_skylift.png, copied in under 1.21.1's
			 * textures/item/ - so they are the first addon items a player can actually see, and
			 * they go in the tab. Upstream's tab is MainRegistry.controlTab, inherited through
			 * AddonItemBase; this jar has one tab.
			 */
			ev_chip_s6 = chip("ev_chip_s6");
			ev_chip_skylift = chip("ev_chip_skylift");

			/*
			 * Both are drawn now, by a composite item model over the parts their upstream
			 * renderers drew - the cabin's bare frame and the counterweight itself (D-1043). So
			 * both go in the tab, which is where upstream puts them.
			 */
			ev_spawn = reg("ev_spawn", EvSpawnItem::new, LeafiaCreativeTabs.MAIN, new Item.Properties());
			weight_spawn = reg("weight_spawn", WeightSpawnItem::new, LeafiaCreativeTabs.MAIN, new Item.Properties());

			/*
			 * Upstream: new EvRadioItem("ev_radio"), with AddonItemBaked's default stack of 64 (the
			 * three track choices live in the stack's tag, so two radios set differently do not
			 * stack). The music it selects is played through LCEAudioProxy (D-1088).
			 */
			ev_radio = reg("ev_radio", EvRadioItem::new, LeafiaCreativeTabs.MAIN, new Item.Properties());
		}

		if(LeafiaFeatures.fuzzyIdentifier) {

			/*
			 * Upstream: new FuzzyIdentifierItem("fuzzy_identifier").setMaxStackSize(1)
			 * .setCreativeTab(MainRegistry.consumableTab). The stack limit is upstream's and
			 * matters - the fluid it remembers lives on the stack, so two in a pile would be two
			 * different tools wearing one name. The tab is the addon's one tab, as everything
			 * else here is.
			 */
			fuzzy_identifier = reg("fuzzy_identifier", FuzzyIdentifierItem::new,
					LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.upgradeControl) {

			/*
			 * Upstream: new AddonUpgradeItem("upgrade_control").setMaxStackSize(1).setCreativeTab(MainRegistry.controlTab).
			 * A SPECIAL upgrade of tier 0 (the base class's no-argument constructor), which upstream
			 * registers under the base mod's namespace; the addon's own here. The tab is the addon's one.
			 */
			upgrade_control = reg("upgrade_control", com.leafia.contents.control.upgrades.AddonUpgradeItem::new,
					LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.fluidBottles) {

			/*
			 * Upstream: new ItemNTMFBottle("ntmfbottle"), setMaxStackSize(1) in its constructor -
			 * read the class, not just the call site. One item; the fluid is on the stack,
			 * so the tab gets one stack per drinkable fluid rather than the item itself.
			 */
			ntmfbottle = reg("ntmfbottle", ItemNTMFBottle::new, null, new Item.Properties().stacksTo(1));
			LeafiaCreativeTabs.addStacks(LeafiaCreativeTabs.MAIN, () -> ItemNTMFBottle.allBottles(ntmfbottle.get()));
		}

		if(LeafiaFeatures.batteryPacks) {

			/*
			 * Upstream's packs were metadata 12-17 of the base mod's battery_pack, appended by
			 * MixinEnumBatteryPack, so they inherit ItemBatteryPack's setMaxStackSize(1) and
			 * controlTab. Here each is its own item, named the base mod's battery_pack_<texture>
			 * way, in the addon's tab.
			 */
			for(AddonEnumBatteryPack pack : AddonEnumBatteryPack.values())
				battery_packs.put(pack, reg(pack.itemName(), p -> new AddonBatteryPackItem(p, pack),
						LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1)));
		}

		if(LeafiaFeatures.meltdownPill) {

			/*
			 * Upstream: new PillMeltdownItem("pill_meltdown").setMaxStackSize(1), a zero-food
			 * AddonFoodBaked set always-edible in its constructor (rule 7: read the class too).
			 */
			pill_meltdown = reg("pill_meltdown", PillMeltdownItem::new, LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1).food(PillMeltdownItem.FOOD));
		}

		if(LeafiaFeatures.amRifle) {

			/*
			 * Upstream (GunInit): new AMRifle(WeaponQuality.SECRET, "gun_leafia_amrifle", ...): a SECRET gun has no creative
			 * tab (ItemGunBaseNT's constructor rule) and goes on the hidden list; a sedna gun stacks to one.
			 */
			am_rifle = reg("gun_leafia_amrifle",
					p -> new com.leafia.contents.gear.combat.guns.am_rifle.AMRifle(p, com.leafia.contents.gear.combat.guns.GunInit.QUALITY,
							com.leafia.contents.gear.combat.guns.GunInit.rifleConfig()),
					null, new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.pinkDoor) {

			/*
			 * Upstream (AddonItems): new ItemPinkDoor("door_fuckoff"), an NTM-CE ItemModDoor. Its recipe makes three
			 * (AddonCraftingRecipes line 154), which 1.12.2 could hand out as an overstacked stack and 1.21.1 refuses to
			 * load for an item that stacks to one (the base's own door_metal does stack to one, and its recipe makes one);
			 * so this door stacks as a vanilla door does, to 64 (D-3028). Filed in the addon's tab.
			 */
			door_fuckoff = reg("door_fuckoff",
					p -> new com.leafia.contents.building.pinkdoor.ItemPinkDoor(LeafiaBlocks.door_fuckoff.get(), p),
					LeafiaCreativeTabs.MAIN, new Item.Properties());
		}

		if(LeafiaFeatures.leafiaGrenades) {

			/*
			 * The two fillings appended to the base mod's EnumGrenadeFilling (MixinEnumGrenadeFilling, D-3028): an
			 * ItemGrenadeFilling of the appended constant, in the addon's namespace, which ItemGrenadeUniversal.partOf finds.
			 */
			grenade_filling_leafia_null = reg("grenade_filling_leafia_null",
					p -> new com.hbm.items.weapon.grenade.ItemGrenadeFilling(p, com.leafia.overwrite_contents.LeafiaGrenadeFillings.nullFilling()),
					LeafiaCreativeTabs.MAIN, new Item.Properties());
			grenade_filling_leafia_sol = reg("grenade_filling_leafia_sol",
					p -> new com.hbm.items.weapon.grenade.ItemGrenadeFilling(p, com.leafia.overwrite_contents.LeafiaGrenadeFillings.solFilling()),
					LeafiaCreativeTabs.MAIN, new Item.Properties());
		}

		if(LeafiaFeatures.addonBombs) {

			/*
			 * Upstream (AddonItems): new CustomNukeMissileItem("missile_customnuke").setCreativeTab(missileTab), an
			 * NTM-CE ItemMissileStandard(MICRO, TIER0); the base mod's missiles stack to one (D-3022).
			 */
			missile_customnuke = reg("missile_customnuke", com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem::new,
					LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.rtg) {

			/*
			 * Upstream (AddonItems): new AddonRTGPelletItem(120, "pellet_rtg_nc279")
			 * .setDecays(pellet_rtg_fissium, 2000000L, 4).setCreativeTab(controlTab). The base pellet's
			 * constructor makes it stack to one. The fissium pellet registers with this switch too
			 * (LeafiaResourceItems.gated), so the supplier always resolves.
			 */
			pellet_rtg_nc279 = reg("pellet_rtg_nc279",
					p -> new com.leafia.contents.control.fuel.AddonRTGPelletItem(p, 120).setDecays(
							() -> LeafiaResourceItems.get(LeafiaResourceItems.RTG_DECAY_PELLET).get(), 2000000L, 4),
					LeafiaCreativeTabs.MAIN, new Item.Properties());
		}

		if(LeafiaFeatures.advisor) {

			/*
			 * Upstream: new AdvisorItem("advisor").setCreativeTab(MainRegistry.consumableTab) -
			 * and AddonItemBase sets nothing else, so the stack size is the default 64 and not
			 * the 1 its sibling tools have. Kept: the cover's open state is a tag on the stack,
			 * so two stacked advisors would be two copies of one open lid, but upstream allows it.
			 * The tab is the addon's one tab.
			 */
			advisor = reg("advisor", AdvisorItem::new, LeafiaCreativeTabs.MAIN, new Item.Properties());
		}

		if(LeafiaFeatures.ams) {

			/*
			 * Upstream: ModItems.ams_muzzle = new ItemCustomLore("ams_muzzle").setMaxStackSize(1) in the
			 * control tab; AddonItems.ams_focus_limiter = new LCEItemLens(2500000000L, 1.5F, 1.75F, 0.8F,
			 * "ams_focus_limiter") and ams_focus_booster = (100000000L, 0.8F, 0.5F, 1.35F), both
			 * setMaxStackSize(1) in the control tab. The addon has the one tab.
			 */
			ams_muzzle = reg("ams_muzzle", ItemCustomLore::new, LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1));
			// Upstream's AddonItems order: blank, limiter, booster, omega, safe (D-3011).
			ams_focus_blank = reg("ams_focus_blank", p -> new LCEItemLens(p, 400000L, 0.5F, 1.75F, 1), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
			ams_focus_limiter = reg("ams_focus_limiter", p -> new LCEItemLens(p, 2500000000L, 1.5F, 1.75F, 0.8F), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
			ams_focus_booster = reg("ams_focus_booster", p -> new LCEItemLens(p, 100000000L, 0.8F, 0.5F, 1.35F), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
			ams_focus_omega = reg("ams_focus_omega", p -> new LCEItemLens(p, 1000000000L, 5.0F, 10.0F, 3.5F), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
			ams_focus_safe = reg("ams_focus_safe", p -> new LCEItemLens(p, 5000000000L, 8F, 4.5F, 0.1F), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.dfcOverhaulContent()) {

			/*
			 * Upstream: new CrucifixItem("fix_tool", "leafia/crucifix") and ("fix_survival",
			 * "leafia/fix_survival"), setMaxStackSize(1), in the control tab (D-3011).
			 */
			fix_tool = reg("fix_tool", p -> new CrucifixItem(p, true), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
			fix_survival = reg("fix_survival", p -> new CrucifixItem(p, false), LeafiaCreativeTabs.MAIN,
					new Item.Properties().stacksTo(1));
		}

		if(LeafiaFeatures.pwrReactor()) {

			/*
			 * Upstream's AddonItems: new PWRDebrisItem("lwr_piece", DebrisType.CONCRETE), ("lwr_shrapnel",
			 * SHRAPNEL) and ("lwr_shard", BLANK).disableCrafting(). No creative tab: they come from the
			 * wreck and the debris, and their name comes from the block they name.
			 */
			lwr_piece = reg("lwr_piece", p -> new PWRDebrisItem(p, "lwr_piece", DebrisType.CONCRETE), null, new Item.Properties());
			lwr_shrapnel = reg("lwr_shrapnel", p -> new PWRDebrisItem(p, "lwr_shrapnel", DebrisType.SHRAPNEL), null, new Item.Properties());
			lwr_shard = reg("lwr_shard", p -> new PWRDebrisItem(p, "lwr_shard", DebrisType.BLANK).disableCrafting(), null, new Item.Properties());
		}

		if(LeafiaFeatures.shields) {

			/*
			 * Upstream's Shields: AddonShieldItem(oreDict, ToolMaterial or int, name). The only thing a
			 * shield reads off the material is getMaxUses(), so that is the durability. Steel and
			 * titanium use the base mod's tiers (750 and 1000), not NTM-CE's 500 and 750 - D-1000,
			 * and both are in D-1083. Mysticite's 0 means "never wears out": no durability component.
			 * ItemShield sets stack size 1 in its own constructor; AddonShieldItem's tab is
			 * CreativeTabs.COMBAT, and this jar has one tab.
			 */
			shield(Kind.STEEL, NtmTiers.STEEL.getUses());
			shield(Kind.TITANIUM, NtmTiers.TITANIUM.getUses());
			shield(Kind.ELEC, 450);
			shield(Kind.PU238, LeafiaToolMaterials.PU238.getUses());
			shield(Kind.FISSITE, LeafiaToolMaterials.FISSITE.getUses());
			shield(Kind.MYSTICITE, LeafiaToolMaterials.MYSTICITE.getUses());
		}

		/*
		 * The simple material items, which include the shields' two ingots - upstream's
		 * AddonItemHazardBaked("ingot_fissite").addRad(ItemRads.fissite) and an AddonItemBaked with a
		 * tooltip, both partsTab. They moved out of the shields block above (D-1083) into the table
		 * (D-1085) so that one registration serves both switches.
		 */
		LeafiaResourceItems.register();
		ingot_fissite = LeafiaResourceItems.get("ingot_fissite");
		ingot_mysticite = LeafiaResourceItems.get("ingot_mysticite");

		powder_osmiridium = null;
		nugget_u238m2 = null;
		plate_tungsten_carbide = null;
		sat_head_laser = null;
		if(LeafiaFeatures.resourceItems) {

			/*
			 * NTM-CE's powder_osmiridium: the refined powder the osmiridium ingot is smelted from,
			 * where the base mod has only powder_impure_osmiridium. A plain item in the parts tab, as
			 * every powder is. nugget_u238m2 is the DFC's nugget of uranium-238m2, and like the base
			 * mod's ingot_u238m2 beside it (setCreativeTab(null)) it is not in a tab: a player gets
			 * it from the DFC, which is not ported, or from /give.
			 */
			powder_osmiridium = reg("powder_osmiridium", Item::new, LeafiaCreativeTabs.MAIN, new Item.Properties());
			nugget_u238m2 = reg("nugget_u238m2", Item::new, null, new Item.Properties());
			plate_tungsten_carbide = reg("plate_tungsten_carbide", Item::new, LeafiaCreativeTabs.MAIN, new Item.Properties());
			sat_head_laser = reg("sat_head_laser", Item::new, LeafiaCreativeTabs.MAIN, new Item.Properties().stacksTo(1));
			// The autogen plates, receivers and wire of the addon's nine materials (AddonMats, D-3026).
			com.leafia.init.LeafiaMats.registerItems(ITEMS);
		}

		radglasses = null;
		if(LeafiaFeatures.radGlasses) {
			// Upstream: new RadGlassesItem(ArmorMaterial.IRON, -1, HEAD, "radglasses").setMaxStackSize(1); the creative tab is the default (none).
			radglasses = reg("radglasses", com.leafia.contents.gear.utility.radglasses.RadGlassesItem::new, LeafiaCreativeTabs.MAIN,
					com.leafia.contents.gear.utility.radglasses.RadGlassesItem.properties());
		}

		/*
		 * The fuel rods, the empty assembly and the five depleted fuels (D-3001). After the material
		 * items, because kys-3000 is made from the billet this jar registers above.
		 */
		LeafiaRods.register();

		ITEMS.register(modBus);
	}

	private static void shield(Kind kind, int uses) {
		Item.Properties props = new Item.Properties().stacksTo(1);
		if(uses > 0) props.durability(uses);
		shields.put(kind, reg(kind.id, p -> new AddonShieldItem(p, kind), LeafiaCreativeTabs.MAIN, props));
	}

	/** One control chip. Upstream's {@code setMaxStackSize(1)}: a cabin holds exactly one. */
	private static DeferredItem<EvChipItem> chip(String name) {
		return reg(name, props -> new EvChipItem(props, name), LeafiaCreativeTabs.MAIN,
				new Item.Properties().stacksTo(1));
	}

	/** One cabin style, registered hidden and filed in both lookups. */
	private static void style(String name, StyleType type) {
		DeferredItem<EvStyleItem> item = reg(name,
				props -> new EvStyleItem(props, name, type), LeafiaCreativeTabs.MAIN,
				// Upstream's setMaxStackSize(1): a cabin has one of each panel, so a stack of
				// them would be meaningless, and the car's style slots hold exactly one.
				new Item.Properties().stacksTo(1));
		style_items.add(item);
		styles_by_id.put(name.substring(3), item);
	}

	/**
	 * Registers an item and files it under {@code tab}.
	 *
	 * <p>A null {@code tab} means "registered, but a player cannot reach it from creative" —
	 * {@link LeafiaCreativeTabs#add} drops it, which is upstream's {@code setCreativeTab(null)}.
	 */
	private static <T extends Item> DeferredItem<T> reg(String name, Function<Item.Properties, T> factory,
			String tab, Item.Properties props) {
		DeferredItem<T> item = ITEMS.registerItem(name, factory, props);
		LeafiaCreativeTabs.add(tab, item);
		return item;
	}
}
