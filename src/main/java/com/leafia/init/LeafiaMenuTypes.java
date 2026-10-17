package com.leafia.init;

import com.leafia.contents.machines.reactors.lftr.processing.separator.container.SaltSeparatorMenu;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu;
import com.leafia.contents.building.storage.broof.container.BroofMenu;
import com.leafia.contents.machines.elevators.gui.EvCabinMenu;
import com.leafia.contents.machines.heat.hpboiler.container.HPBoilerMenu;
import com.leafia.contents.machines.misc.heatex.container.CoolantHeatexMenu;
import com.leafia.contents.machines.processing.mixingvat.container.MixingVatMenu;
import com.leafia.contents.machines.powercores.ams.base.container.AMSBaseMenu;
import com.leafia.contents.machines.powercores.ams.emitter.container.AMSEmitterMenu;
import com.leafia.contents.machines.powercores.ams.stabilizer.container.AMSStabilizerMenu;
import com.leafia.contents.machines.powercores.dfc.core.CoreMenu;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorMenu;
import com.leafia.contents.machines.processing.solblaster.container.SolBlasterMenu;
import com.leafia.contents.miscellanous.slop.container.SlopMenu;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.RegexFilterMenu;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's menu types — <b>new, and the first of them is the broof's</b>.
 *
 * <p>1.12.2 had no such registry: a GUI was opened by handing FML a numeric id and letting
 * {@code AddonGuiHandler} switch on it. Upstream's ids live in {@code Elevators.guiIdFloor},
 * {@code guiIdCabin} and the rest; here a menu is a registered thing, and the client builds it
 * from a network payload rather than from a shared reference to the block entity.
 *
 * <h2>Why this registry did not exist until now</h2>
 *
 * Every GUI the addon had reached was blocked at its far end rather than by any missing
 * infrastructure. The elevator's three — {@code EvFloorGUI}, {@code EvCabinContainer} and
 * {@code EvCabinGUI} — all open from a screwdriver, and the base mod's screwdriver is not a
 * tool yet because {@code ItemTooling} is not ported (D-1045); they have since landed behind
 * {@code ToolType} (D-1077), so they wake when it does. The radio's three wait on the client
 * audio proxy (D-1037). The broof opens from a bare right click, so it is the first that could
 * land, and standing this up is most of what it is worth. See D-1049.
 *
 * <h2>Not gated behind a feature switch, deliberately</h2>
 *
 * A {@code MenuType} costs one registry entry and reaches nothing on its own, and a screen
 * registered against a menu that was never registered is a hard error rather than a quiet
 * absence — the same reasoning {@code LeafiaClient} gives for registering the geometry loaders
 * unconditionally. The <em>block</em> is gated; its menu type is not.
 */
public final class LeafiaMenuTypes {

	private LeafiaMenuTypes() {}

	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, LeafiaRef.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<BroofMenu>> BROOF =
			MENUS.register("broof", () -> IMenuTypeExtension.create(BroofMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<HPBoilerMenu>> HP_BOILER =
			MENUS.register("hp_boiler", () -> IMenuTypeExtension.create(HPBoilerMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<SlopMenu>> SLOP =
			MENUS.register("slop", () -> IMenuTypeExtension.create(SlopMenu::new));
	/** The Neutron Blaster's twenty slots. Its payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<SolBlasterMenu>> SOL_BLASTER =
			MENUS.register("sol_blaster", () -> IMenuTypeExtension.create(SolBlasterMenu::new));

	/** The APR core's six slots (D-3027). The payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMainContainer>> APR_MAIN =
			MENUS.register("apr_main", () -> IMenuTypeExtension.create(com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMainContainer::new));

	/** The AMC base's sixteen slots, and the emitter's and stabilizer's four. Each payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<AMSBaseMenu>> AMS_BASE =
			MENUS.register("ams_base", () -> IMenuTypeExtension.create(AMSBaseMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<AMSEmitterMenu>> AMS_EMITTER =
			MENUS.register("ams_emitter", () -> IMenuTypeExtension.create(AMSEmitterMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<AMSStabilizerMenu>> AMS_STABILIZER =
			MENUS.register("ams_limiter", () -> IMenuTypeExtension.create(AMSStabilizerMenu::new));
	/** The addon DFC core's menu and the pulse detonator's (D-3011). */
	public static final DeferredHolder<MenuType<?>, MenuType<CoreMenu>> DFC_CORE =
			MENUS.register("dfc_core", () -> IMenuTypeExtension.create(CoreMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CoreDetonatorMenu>> DFC_PULSER =
			MENUS.register("dfc_pulser", () -> IMenuTypeExtension.create(CoreDetonatorMenu::new));
	/** The overhauled absorber's, the overhauled injector's, the creative booster's and the heat exchanger's (D-3014). */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.powercores.dfc.components.absorber.CoreReceiverMenu>> DFC_RECEIVER =
			MENUS.register("dfc_receiver", () -> IMenuTypeExtension.create(com.leafia.contents.machines.powercores.dfc.components.absorber.CoreReceiverMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.powercores.dfc.components.injector.CoreInjectorMenu>> DFC_INJECTOR =
			MENUS.register("dfc_injector", () -> IMenuTypeExtension.create(com.leafia.contents.machines.powercores.dfc.components.injector.CoreInjectorMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterMenu>> DFC_CEMITTER =
			MENUS.register("dfc_cemitter", () -> IMenuTypeExtension.create(com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerMenu>> DFC_EXCHANGER =
			MENUS.register("dfc_exchanger", () -> IMenuTypeExtension.create(com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerMenu::new));

	/** The regex filter's slot and filter list. Its payload is the block's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<RegexFilterMenu>> REGEX_FILTER =
			MENUS.register("regex_filter", () -> IMenuTypeExtension.create(RegexFilterMenu::new));

	/**
	 * The elevator cabin's eight-slot editor. The payload is the cabin's entity id, which the
	 * client constructor looks up, rather than a block position - the cabin is an entity.
	 */
	public static final DeferredHolder<MenuType<?>, MenuType<EvCabinMenu>> EV_CABIN =
			MENUS.register("ev_cabin", () -> IMenuTypeExtension.create(EvCabinMenu::new));

	/**
	 * The PWR terminal (D-3010). Its payload is the terminal's position and the fuel columns the server
	 * built slots for, so both sides agree on the slots.
	 */
	public static final DeferredHolder<MenuType<?>, MenuType<PWRTerminalMenu>> PWR_TERMINAL =
			MENUS.register("lwr_terminal", () -> IMenuTypeExtension.create(PWRTerminalMenu::new));

	/** The mixing vat's (D-3016) and the coolant heat exchanger's menus. Each payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<MixingVatMenu>> MIXING_VAT =
			MENUS.register("mixing_vat", () -> IMenuTypeExtension.create(MixingVatMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CoolantHeatexMenu>> COOLANT_HEATEX =
			MENUS.register("coolant_heatex", () -> IMenuTypeExtension.create(CoolantHeatexMenu::new));
	/** The salt separator (D-3015). Its payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<SaltSeparatorMenu>> SALT_SEPARATOR =
			MENUS.register("salt_separator", () -> IMenuTypeExtension.create(SaltSeparatorMenu::new));

	/**
	 * The crate labelling screen's (D-3021). Its payload is the crate's position and the face that was clicked.
	 * Not gated: a menu type costs one registry entry (see the class note).
	 */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.nonmachines.storage.items.CrateLabelMenu>> CRATE_LABEL =
			MENUS.register("crate_label", () -> IMenuTypeExtension.create(com.leafia.contents.nonmachines.storage.items.CrateLabelMenu::new));
	/** The RT heater, the overhauled ZIRNOX and the RTG blast furnace with its rod slot (D-3020). Each payload is the core's position. */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.heat.rtheater.container.HeaterRTGMenu>> HEATER_RT =
			MENUS.register("heater_rt", () -> IMenuTypeExtension.create(com.leafia.contents.machines.heat.rtheater.container.HeaterRTGMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.reactors.zirnox.ZirnoxMenu>> ZIRNOX =
			MENUS.register("zirnox", () -> IMenuTypeExtension.create(com.leafia.contents.machines.reactors.zirnox.ZirnoxMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGMenu>> DIFURNACE_RTG =
			MENUS.register("difurnace_rtg", () -> IMenuTypeExtension.create(com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGMenu::new));

	/** The custom nuke missile's 27 slots (D-3022). Its payload is the hand holding the missile. */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.bomb.missile.customnuke.container.CustomNukeMissileContainer>> CUSTOM_NUKE_MISSILE =
			MENUS.register("missile_customnuke", () -> IMenuTypeExtension.create(com.leafia.contents.bomb.missile.customnuke.container.CustomNukeMissileContainer::new));

	/** The Custom Control Panel editor (D-3066). Its payload is the panel's position. Not gated, like the others. */
	public static final DeferredHolder<MenuType<?>, MenuType<com.leafia.contents.machines.controlpanel.core.ControlEditMenu>> CONTROL_EDIT =
			MENUS.register("control_edit", () -> IMenuTypeExtension.create(com.leafia.contents.machines.controlpanel.core.ControlEditMenu::new));

	public static void register(IEventBus modBus) {
		MENUS.register(modBus);
	}
}
