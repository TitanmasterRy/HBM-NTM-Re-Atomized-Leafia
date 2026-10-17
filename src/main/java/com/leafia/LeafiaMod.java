package com.leafia;

import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.init.LeafiaEntities;
import com.leafia.init.LeafiaSounds;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.init.LeafiaFluidTraits;
import com.leafia.init.LeafiaHazards;
import com.leafia.lib.LeafiaRef;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for Leafia's Cursed Addon, the optional second jar.
 *
 * <p>Upstream's equivalent is {@code com.leafia.AddonBase}, a 1.12.2 {@code @Mod} class with
 * {@code @SidedProxy}, {@code preInit}/{@code init}/{@code postInit} and a
 * {@code GameRegistry} call per content type. None of that survives: NeoForge has no proxy
 * pattern (client-only code lives behind a {@code Dist.CLIENT} entry point — see
 * {@code com.leafia.client.LeafiaClient}) and registration is a DeferredRegister holder per
 * content type, so this class stays small on purpose.
 *
 * <h2>Ordering, which is the one thing here that is not obvious</h2>
 *
 * {@link LeafiaFeatures} is a STARTUP config and is therefore already loaded by the time
 * {@code registerConfig} returns; it is refreshed immediately, before any content registers.
 * {@link LeafiaConfig} is COMMON and loads <em>after</em> registration, so it is refreshed from
 * a {@code ModConfigEvent} listener like the base mod's configs. Swapping the two would break
 * the D-1002 gate silently — see the note on {@code LeafiaFeatures} and D-1003.
 *
 * <p>This class never touches {@code com.hbm} state. The dependency runs one way: the addon
 * requires the base mod, and the base mod does not know the addon exists. That is what keeps
 * the jar genuinely optional.
 */
@Mod(LeafiaRef.MODID)
public final class LeafiaMod {

	public static final Logger LOGGER = LoggerFactory.getLogger(LeafiaRef.NAME);

	public LeafiaMod(IEventBus modBus, ModContainer container) {

		/*
		 * STARTUP: ConfigTracker.registerConfig opens this one synchronously, so the values are
		 * readable as soon as the call returns. That is the only reason the gate below can work
		 * - registry events fire before COMMON configs load.
		 */
		container.registerConfig(ModConfig.Type.STARTUP, LeafiaFeatures.SPEC, "leafia-features.toml");
		LeafiaFeatures.refresh();
		// Before any save builds its GameRules; GameRules.register is a static table.
		com.leafia.init.LeafiaGameRules.register();

		LOGGER.info("{} {} loading (upstream {}, built against {})",
				LeafiaRef.NAME, LeafiaRef.VERSION, LeafiaRef.UPSTREAM_VERSION, LeafiaRef.UPSTREAM_BASE);

		// The addon's materials and ore-dictionary groups: after the base mod's constructor built Mats and
		// OreDictManager, before the registries (the autogen items name the materials) and long before
		// common setup walks Mats.orderedList (D-3026).
		com.leafia.init.LeafiaMats.init();
		com.leafia.init.LeafiaOreDict.registerGroups();

		// The anti-mass rifle's beam config (always: a BulletConfig takes the next process-wide id) and the lock-on sweep (D-3028).
		com.leafia.contents.gear.combat.guns.GunInit.init();

		LeafiaSounds.register(modBus);
		LeafiaCreativeTabs.register(modBus);
		LeafiaBlocks.register(modBus);
		com.leafia.init.LeafiaArmorMaterials.register(modBus);
		LeafiaItems.register(modBus);
		// The rod uncrafting recipe's serializer: gated by features.rods, like the rods themselves.
		com.leafia.init.LeafiaRecipeSerializers.register(modBus);
		// Fluid types, fluids, their blocks and buckets: its own four registries, gated by worldFluids.
		com.leafia.contents.fluids.LeafiaLiquids.register(modBus);
		com.leafia.contents.potion.LeafiaPotion.register(modBus);
		// After the blocks: a BlockEntityType names the blocks it is valid for.
		LeafiaBlockEntities.register(modBus);
		LeafiaMenuTypes.register(modBus);
		LeafiaEntities.register(modBus);
		// Data attachments on base-mod objects (the landmine's "placed by a player", D-3022). Not gated: no content.
		com.leafia.init.LeafiaAttachments.register(modBus);
		// The leafia:feature condition, which gates the digamma crater's datapack biome (D-3009).
		com.leafia.config.LeafiaFeatureCondition.register(modBus);
		// The addon's fallout variants, MK5 hook, fallout finish listener and torex spawn delegate:
		// process-wide registrations on the base mod's nuke seams, each reading its switch (D-3009).
		com.leafia.overwrite_contents.LeafiaNukeSeams.install();
		// The explosion, corium and cloud seams (D-3013); each reads its switch when it runs.
		com.leafia.overwrite_contents.LeafiaExplosionSeams.install();
		// The RBMK hook and the debris smash handler (D-3018); each reads overhauls.rbmkOverhaul when it runs.
		com.leafia.overwrite_contents.LeafiaRBMKSeams.install();
		// The hazard seams (D-3023): hot advisor, classified tooltip and geiger, nine-click geiger, radon, craters.
		com.leafia.overwrite_contents.LeafiaHazardSeams.install();
		// The cosmetic seams (D-3023): sludge-unit energy bar, darker slaked sellafield, the rod of discord's creative range.
		com.leafia.overwrite_contents.LeafiaCosmeticSeams.install();
		// The fluid network, barrel, tank, icon and boiler seams, and firestorm's chemical plant hook (D-3025);
		// each reads its switch when it runs.
		com.leafia.overwrite_contents.LeafiaFluidSeams.install();
		com.leafia.overwrite_contents.LeafiaFirestorm.install();
		// The turret friendly-fire veto, the shimmer sledge's destroy hook and tooltip (D-3028); and the two appended grenade
		// fillings, which lose their shells while features.leafiaGrenades is off.
		com.leafia.overwrite_contents.LeafiaSednaSeams.install();
		com.leafia.overwrite_contents.LeafiaGrenadeFillings.install();
		// The rad goggles' locating boxes: an observer on the base mod's chunk radiation increments (D-3041).
		com.leafia.overwrite_contents.LeafiaRadObserver.install();

		/*
		 * Not a registry event: NTM's fluids are plain objects built by Fluids.init() in the
		 * base mod's constructor, which has already run because this jar declares hbm
		 * ordering="AFTER". So the traits are hung on them here, directly, rather than from a
		 * listener. Gated like everything else - see LeafiaFluidTraits.
		 */
		LeafiaFluids.init();
		LeafiaFluidTraits.init();
		LeafiaFluids.applyCopies();

		// The Forge-fluid bridge: maps every NTM fluid to a NeoForge one and registers the ff/
		// fluids. After the addon's fourteen exist and after LeafiaLiquids picked its holders,
		// because both are seeds of the map. Gated on ffBridge(). See LeafiaFF, D-3003.
		com.leafia.contents.fluids.ff.LeafiaFF.register(modBus);
		// The glass-bottle loader at the head of the base mod's canister chain (MixinFluidTankNTM's
		// <clinit> injection, as a list insert). Gated on fluidBottles.
		com.leafia.unsorted.fluids.FluidLoaderBottle.install();

		// The modular turbine's steam table. Its own data rather than a trait on a base-mod
		// fluid, so it rides the turbine's gate and not fluidTraits'; it names deathsteam, so it
		// runs after LeafiaFluids.init(). See D-1058.
		if(LeafiaFeatures.modularTurbine()) MTCoreTE.initSteamTypes(LeafiaFluids.DEATHSTEAM);

		// The addon's ore names in the base mod's binding table, before the base mod's own enqueued task builds it (D-3026):
		// a direct handler, not enqueued work, runs ahead of every mod's enqueued work.
		modBus.addListener(FMLCommonSetupEvent.class, event -> com.leafia.init.LeafiaOreDict.registerOres());

		// Hazards name items, so they wait for registration - and for the base mod's own hazard
		// table, which its listener queued first. See LeafiaHazards.
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(LeafiaHazards::register));
		// The reactor-tier lookup names blocks by registry name, so it waits for every registry too.
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.database.ReactorTiers::register));
		// The base-mod missiles that burst in the air (D-3022); names items, so it waits for every registry.
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.database.AirbustMissiles::init));
		// The AM rifle's two weapon mods and their per-gun overrides; names items, after the base mod's mod table (D-3028).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.gear.combat.guns.GunInit::initMods));
		// The DFC overhaul: the base core's block makes the addon's core while the switch is on (D-3011).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.machines.powercores.dfc.LeafiaDFC::commonSetup));
		// The ZIRNOX overhaul and the RTG furnace's rod slot, the same way (D-3020).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.machines.reactors.zirnox.LeafiaZirnox::commonSetup));
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.overwrite_contents.difurnace.LeafiaRTGFurnace::commonSetup));
		// The Custom Control Panel's event table, control registry and stock node loader (D-3063).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.machines.controlpanel.core.ControlPanelSetup::commonSetup));
		// The siren, arc welder, soldering station and free-electron laser overhauls, the same way (D-3027).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.overwrite_contents.LeafiaMachineOverrides::commonSetup));

		// The fuel handlers on the research reactor and the waste drum, the control upgrade's level provider and the
		// tungsten crate's hooks and override: all of them seams on the base mod's block entities (D-3021).
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.control.upgrades.ControlUpgrade::commonSetup));
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodFuel::commonSetup));
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(com.leafia.contents.nonmachines.storage.items.TungstenCrateSpk::commonSetup));

		// The addon's rows for the base mod's machines. Load complete, not common setup: the base
		// mod builds its recipes in a deferred common-setup task, and this has to come after it.
		// See LeafiaRecipeInjector (D-1095).
		modBus.addListener(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent.class,
				event -> event.enqueueWork(com.leafia.init.recipes.LeafiaRecipeInjector::onLoadComplete));

		// COMMON, matching the base mod's configs: loaded after registration, refreshed on the
		// event. Nothing that decides *whether* content exists may read this one.
		container.registerConfig(ModConfig.Type.COMMON, LeafiaConfig.SPEC, "leafia-balance.toml");
		// Per-rod life, emission and reactivity (upstream's FuelLives), only when there are rods.
		if(LeafiaFeatures.rods)
			container.registerConfig(ModConfig.Type.COMMON, com.leafia.config.LeafiaRodConfig.SPEC, "leafia-fuel-rods.toml");
		modBus.addListener(ModConfigEvent.Loading.class, event -> refreshConfig(event.getConfig()));
		modBus.addListener(ModConfigEvent.Reloading.class, event -> refreshConfig(event.getConfig()));
	}

	/**
	 * Copies whichever config file just loaded into its class's static fields.
	 *
	 * <p>Dispatched on which spec fired rather than refreshing both, because the event fires
	 * once per file and a spec's values throw until that file is in — the same trap
	 * {@code MainRegistry.refreshConfig} documents.
	 */
	private static void refreshConfig(ModConfig config) {
		if(config.getSpec() == LeafiaConfig.SPEC) {
			LeafiaConfig.refresh();
		} else if(LeafiaFeatures.rods && config.getSpec() == com.leafia.config.LeafiaRodConfig.SPEC) {
			com.leafia.config.LeafiaRodConfig.refresh();
		}
	}
}
