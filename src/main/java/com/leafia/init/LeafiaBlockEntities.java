package com.leafia.init;

import com.leafia.config.LeafiaFeatures;
import com.hbm.blockentity.BlockEntityProxyCombo;
import com.leafia.contents.building.storage.broof.BroofTE;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexTE;
import com.leafia.contents.machines.processing.mixingvat.MixingVatTE;
import com.leafia.contents.machines.processing.mixingvat.proxy.MixingVatProxy;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.computer.PWRComputerTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRProxyTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.occs.inlet.PWROCCSInTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.occs.outlet.PWROCCSOutTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.port.PWRPortTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalTE;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreckEntity;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterTE;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerTE;
import com.leafia.contents.machines.processing.solblaster.SolBlasterTE;
import com.leafia.contents.miscellanous.slop.SlopTE;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.contents.LeafiaBlocks;
import com.hbm.blockentity.BlockEntityProxyCombo;
import com.leafia.contents.building.light.LightTE;
import com.leafia.contents.building.sign.SignTE;
import com.leafia.contents.debug.fluid_voider.VoiderTE;
import com.leafia.contents.machines.elevators.EvBufferTE;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.floors.EvFloorTE;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.misc.modular_turbine.flywheel.MTFlywheelBase;
import com.leafia.contents.machines.misc.modular_turbine.flywheel.MTFlywheelTE;
import com.leafia.contents.machines.misc.modular_turbine.ports.IMTPortBlock;
import com.leafia.contents.machines.misc.modular_turbine.ports.MTComponentPortTE;
import com.leafia.contents.machines.misc.modular_turbine.power_port.MTPowerPortTE;
import com.leafia.contents.machines.misc.wind_turbines.medium.WindTurbineMediumTE;
import com.leafia.contents.building.doors.AddonDoorTE;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorTE;
import com.leafia.contents.network.fluid.gauges.FluidDuctGaugeTE;
import com.leafia.contents.network.fluid.valves.FluidDuctValveTE;
import com.leafia.contents.network.pipe_amat.AmatDuctTE;
import com.leafia.contents.network.pipe_amat.charger.AmatDuctChargerTE;
import com.leafia.contents.network.spk_cable.SPKCableTE;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.hbm.blocks.ModBlocks;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.contents.machines.reactors.rbmk.columns.realersim.RBMKRealerSimTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's block entity types, mirroring the base mod's per-slice holders.
 *
 * <h2>The gate again</h2>
 *
 * Like {@link LeafiaBlocks}, everything is registered from {@link #register} behind its feature
 * switch rather than from a static field initialiser, because D-1002 says a disabled feature
 * registers <em>nothing</em> — and a {@code BlockEntityType} left behind by a switched-off
 * block is exactly the dead registry entry that rule exists to prevent. The holders below are
 * therefore null when their feature is off, and every consumer is gated by the same switch.
 *
 * <p>Ordering matters here in a way it does not for blocks: {@code BlockEntityType.Builder.of}
 * names the blocks its type is valid for, so {@link LeafiaBlocks#register} has to have run
 * first. {@code LeafiaMod} calls them in that order. The block itself is only dereferenced when
 * the deferred supplier runs, well after both registers are populated.
 */
public final class LeafiaBlockEntities {

	private LeafiaBlockEntities() {}

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LeafiaRef.MODID);

	/** The antimatter pipe's own type. Null when {@code features.amatPipes} is off. */
	/** Both Forge-fluid ducts' block entity (D-3012). Null when {@code features.ffDuct} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.network.ff_duct.FFDuctTE>> FF_DUCT;

	/** The duct utilities' block entities (D-3019). Null when {@code features.ffDuct} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.network.ff_duct.utility.pump.FFPumpTE>> FF_PUMP;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.network.ff_duct.utility.converter.FFConverterTE>> FF_CONVERTER;
	/** The filter's block entity is {@code FFFilterTE2}, upstream's registered one; {@code FFFilterTE} is not made by anything. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.network.ff_duct.utility.filter.FFFilterTE2>> FF_FILTER;

	/** The molten-salt reactor's five block entities (D-3012). Null unless {@code LeafiaFeatures.lftrReactor()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE>> MSR_ELEMENT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE>> MSR_PLUG;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.components.control.MSRControlTE>> MSR_CONTROL;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryTE>> MSR_ARBITRARY;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorTE>> MSR_EJECTOR;

	/**
	 * The salt separator's core and its inventory, power and fluid proxies (D-3015); the proxy has its own type
	 * for the reason {@link #HP_BOILER_DUMMY} gives. Null unless {@code LeafiaFeatures.lftrReactor()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE>> SALT_SEPARATOR;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> SALT_SEPARATOR_DUMMY;

	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AmatDuctTE>> AMAT_DUCT;

	/** The charger's. Null when {@code features.amatPipes} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AmatDuctChargerTE>> AMAT_DUCT_CHARGER;

	/** The SPK cable's. Null when {@code features.spkCable} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SPKCableTE>> SPK_CABLE;

	/**
	 * The addon's Dark Fusion Core, a subclass of the base mod's core valid for the base mod's own
	 * {@code dfc_core} block, which {@code BlockEntityOverrides} places there while
	 * {@code overhauls.dfcOverhaul} is on; and the pulse detonator's. Null unless
	 * {@code LeafiaFeatures.dfcOverhaulContent()} (D-3011).
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<LeafiaCoreTE>> DFC_CORE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CoreDetonatorTE>> DFC_PULSER;

	/**
	 * The overhauled DFC components (D-3014): four subclasses of the base mod's components, each valid for
	 * its base block (the receiver's also for {@code dfc_reinforced}), placed by {@code BlockEntityOverrides}
	 * while {@code overhauls.dfcOverhaul} is on; and the creative booster's and the heat exchanger's, on the
	 * addon's own blocks. Null unless {@code LeafiaFeatures.dfcOverhaulContent()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE>> DFC_EMITTER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE>> DFC_RECEIVER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.injector.LeafiaCoreInjectorTE>> DFC_INJECTOR;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE>> DFC_STABILIZER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterTE>> DFC_CEMITTER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE>> DFC_EXCHANGER;

	/** The storage rack's core (D-3021). Null when {@code features.rack} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.building.storage.rack.RackTE>> RACK;

	/**
	 * The tungsten crate that takes SPK: a subclass of the base mod's crate, valid for the base mod's own
	 * {@code crate_tungsten} block, which {@code BlockEntityOverrides} places there while
	 * {@code overhauls.tungstenCrate} is on (D-3021). Null unless {@code LeafiaFeatures.tungstenCrateContent()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.nonmachines.storage.items.TungstenCrateSpk>> CRATE_TUNGSTEN;

	/** The smoking RBMK debris (D-3018). Null unless {@code LeafiaFeatures.rbmkOverhaulContent()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmokeTE>> PRIBRIS_SMOKE;

	/**
	 * The two valves share one block entity class and need two types anyway, because a type
	 * names the blocks it is valid for and {@code createTickerHelper} compares types by identity.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidDuctValveTE>> FLUID_DUCT_VALVE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidDuctValveTE>> FLUID_DUCT_VALVE_RS;

	/** The flow gauge's. Null when {@code features.fluidDuctEquipment} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidDuctGaugeTE>> FLUID_DUCT_GAUGE;

	/**
	 * The doors' (D-3005). Null when {@code features.doors} is off. Neither takes the base door's type:
	 * the block entities subclass the base door and give themselves these through {@code getType()}
	 * (see {@link AddonDoorTE}), so each type lists its own blocks and is what a saved door is
	 * recreated from. The two crimson doors share one; the reactor door's has extra state and has its own.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AddonDoorTE>> ADDON_DOOR;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorDoorTE>> REACTOR_DOOR;

	/** The fluorescent light's core, and the proxy its two arms carry. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<LightTE>> LIGHT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> LIGHT_PROXY;

	/** The fluid voider's. Null when {@code features.debugBlocks} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<VoiderTE>> FLUID_VOIDER;

	/** The debug Forge-fluid source and tank (D-3031). Null unless {@code LeafiaFeatures.debugFFBlocks()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.debug.ff_test.source.DebugSourceTE>> FF_SOURCE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.debug.ff_test.tank.DebugTankTE>> FF_TANK;

	/** The black hole and the render test (D-3031). Null when {@code features.debugBlocks} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.debug.blackhole_test.DebugBHTE>> DEBUG_BLACKHOLE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.debug.render_test.DebugRenderTestTE>> DEBUG_RENDER_TEST;

	/** The medium wind turbine's core. Null when {@code features.turbines} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<WindTurbineMediumTE>> WIND_TURBINE_MEDIUM;

	/** The modular turbine's core. Null unless {@code LeafiaFeatures.modularTurbine()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MTCoreTE>> MODULAR_TURBINE_CORE;

	/**
	 * Every turbine part that is neither a steam port nor a flywheel: the shaft, the separators
	 * and the blades, and the generator when T5 adds it. Upstream's {@code mt_component_te}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<ModularTurbineComponentTE>> MODULAR_TURBINE_COMPONENT;

	/** The five flywheels. Upstream's {@code mt_flywheel_te}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MTFlywheelTE>> MODULAR_TURBINE_FLYWHEEL;

	/** The twelve steam ports. Upstream's {@code mt_component_port_te}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MTComponentPortTE>> MODULAR_TURBINE_PORT;

	/**
	 * The fluid proxies a port plants where its pipes meet it: upstream's
	 * {@code TileEntityProxyCombo(false, false, true)}. Valid on every separator and port, because
	 * the ports inherit the separators' {@code newBlockEntity} shape.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> MODULAR_TURBINE_PROXY;

	/** The electricity port. Upstream's {@code mt_power_port_te}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MTPowerPortTE>> MODULAR_TURBINE_POWER_PORT;

	/**
	 * One type for all thirty-six letter signs. Null when {@code features.letterSigns} is off.
	 *
	 * <p>They differ only in which character they draw, and that is on the <em>block</em>, so one
	 * type valid for all thirty-six is right — unlike the two fluid valves, which need a type each
	 * because {@code createTickerHelper} compares types by identity and they tick differently.
	 * Nothing ticks a sign.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SignTE>> LETTER_SIGN;

	/**
	 * The RealerSim channel's core, and the inventory proxy on its top block. Its own pair rather
	 * than the base mod's {@code rbmk_rod_reasim}/{@code rbmk_dummy}, because a type may only
	 * name blocks that construct it. Null when {@code features.realerSim} is off.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<RBMKRealerSimTE>> RBMK_REALERSIM;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> RBMK_REALERSIM_PROXY;

	/** The pulley's core. Null when {@code features.elevators} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<EvPulleyTE>> EV_PULLEY;

	/** The buffer's. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<EvBufferTE>> EV_BUFFER;

	/** The broof's fifty-four slots. Null when {@code features.broof} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BroofTE>> BROOF;

	/** The Custom Control Panel's, shared by the desk block and the front (D-3063). Null when {@code features.controlPanel} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE>> CONTROL_PANEL;

	/** The control panel torch's, on both of its blocks (CCP-INSTRUMENTS-2, D-3072). Null when {@code features.controlPanel} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.panel.controltorch.ControlTorchTE>> CONTROL_TORCH;

	/** The pneumatic tube regex filter's buffer and filter list. Null when {@code features.regexFilter} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<RegexFilterTE>> REGEX_FILTER;

	/** The high-pressure boiler's core. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<HPBoilerTE>> HP_BOILER;

	/**
	 * The Meteor Protection Beacon's state, on both of its blocks (upstream {@code diverter_te}), and the Fat Chud's,
	 * which exists to be drawn (upstream {@code nuke_chud_te}) (D-3022). Null when their switches are off.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.miscellanous.diverter.DiverterTE>> DIVERTER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.bomb.chud.NukeChudTE>> NUKE_CHUD;

	/**
	 * The RT heater's core and the proxy on its five extras (heat above, inventory at the sides), its own type
	 * for the reason {@link #HP_BOILER_DUMMY} gives (D-3020). Null when {@code features.rtg} is off.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.heat.rtheater.HeaterRTGTE>> HEATER_RT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> HEATER_RT_DUMMY;

	/**
	 * The overhauled ZIRNOX, on the base mod's {@code reactor_zirnox} (D-3020). Null unless
	 * {@code LeafiaFeatures.zirnoxOverhaulContent()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE>> ZIRNOX;

	/**
	 * The siren, arc welder, soldering station and free-electron laser overhauls, on the base mod's blocks (D-3027).
	 * Each null unless its {@code LeafiaFeatures.*OverhaulContent()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.overwrite_contents.siren.LeafiaSirenTE>> SIREN;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.overwrite_contents.welding.LeafiaArcWelderTE>> ARC_WELDER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.overwrite_contents.welding.LeafiaSolderingStationTE>> SOLDERING_STATION;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.overwrite_contents.fel.LeafiaFELTE>> FEL;

	/**
	 * The RTG blast furnace with the fuel-rod slot, on both of the base mod's furnace blocks (D-3020). Null
	 * unless {@code LeafiaFeatures.rtgFurnaceOverhaulContent()}.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGTE>> DIFURNACE_RTG;

	/**
	 * The antimatter reaction chamber's three cores, and the proxy each plants on its extras:
	 * upstream's {@code TileEntityProxyCombo(true, true, true)}, one type per machine because a
	 * type may only name the block that builds it. Null when {@code features.ams} is off.
	 */
	/** The APR's core, its fluid-only proxy above it, the fluid port and the power outlet (D-3027). Null unless {@code LeafiaFeatures.aprReactor()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE>> APR_CORE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> APR_CORE_DUMMY;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOTE>> APR_PORT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.reactors.apr.blocks.outlet.APRPowerOutletTE>> APR_OUTLET;

	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AMSBaseTE>> AMS_BASE;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> AMS_BASE_DUMMY;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AMSEmitterTE>> AMS_EMITTER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> AMS_EMITTER_DUMMY;
	/** The anti-mass spectrometer's two cores (DFC-4, D-3017). Null unless {@code LeafiaFeatures.amsp}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerTE>> AMSP_ANALYZER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverTE>> AMSP_RECEIVER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AMSStabilizerTE>> AMS_STABILIZER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> AMS_STABILIZER_DUMMY;

	/**
	 * The addon PWR's seven (D-3006), and PWR-2's two (D-3010). Null unless {@code LeafiaFeatures.pwrReactor()}. A fuel column's top
	 * block is a {@link PWRElementTE} and its stacked blocks {@link PWRProxyTE}s, two types over the same
	 * three blocks; a control rod's stacked blocks have none.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRElementTE>> PWR_ELEMENT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRProxyTE>> PWR_ELEMENT_PROXY;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRControlTE>> PWR_CONTROL;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRComputerTE>> PWR_COMPUTER;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRPortTE>> PWR_PORT;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWROCCSInTE>> PWR_OCCS_IN;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWROCCSOutTE>> PWR_OCCS_OUT;
	/** PWR-2 (D-3010): upstream's {@code lwr_terminal_te} (all three terminal skins) and {@code lwr_wreck_te}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRTerminalTE>> PWR_TERMINAL;
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PWRMeshedWreckEntity>> PWR_WRECK;

	/** The slop reactor's controller. Null when {@code features.slopReactor} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SlopTE>> SLOP;

	/** The Neutron Blaster's core. Null when {@code features.solBlaster} is off. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SolBlasterTE>> SOL_BLASTER;

	/**
	 * The Neutron Blaster's four inventory-only corner ports: a {@link BlockEntityProxyCombo} that
	 * forwards its slots to the core, with a type of its own for the reason {@link #HP_BOILER_DUMMY}
	 * gives. Null when {@code features.solBlaster} is off.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> SOL_BLASTER_DUMMY;

	/**
	 * The boiler's two side ports.
	 *
	 * <p>A plain {@link BlockEntityProxyCombo}, configured fluid-only where it is built. It needs
	 * a type of the addon's own because a {@code BlockEntityType}'s validity is a fixed list of
	 * blocks and the base mod's {@code MULTIBLOCK_DUMMY} does not — and must not — carry this
	 * one (rule 11). Same shape as {@code Slice3.MULTIBLOCK_DUMMY}, with one block on the list.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> HP_BOILER_DUMMY;

	/** The mixing vat's core (D-3016). Null unless {@code LeafiaFeatures.mixingVatMachine()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MixingVatTE>> MIXING_VAT;

	/**
	 * The mixing vat's three ports: {@link MixingVatProxy}, the base mod's combo proxy with power and the vat's
	 * per-side Forge-fluid handler, on a type of its own for the reason {@link #HP_BOILER_DUMMY} gives.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MixingVatProxy>> MIXING_VAT_DUMMY;

	/** The coolant heat exchanger's core (D-3016). Null unless {@code LeafiaFeatures.coolantHeatexMachine()}. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolantHeatexTE>> COOLANT_HEATEX;

	/** The exchanger's six ports: a power-and-fluid combo proxy that also exposes the core's Forge handler to ducts. */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityProxyCombo>> COOLANT_HEATEX_DUMMY;

	/**
	 * One type for both landings.
	 *
	 * <p>The two floor blocks differ only in the model they draw — nothing in {@link EvFloorTE}
	 * distinguishes them and both tick identically — so one type valid for both is right, the same
	 * call the letter signs make. The two fluid valves above need a type each for the opposite
	 * reason: {@code createTickerHelper} compares types by identity and they tick differently.
	 */
	public static DeferredHolder<BlockEntityType<?>, BlockEntityType<EvFloorTE>> EV_FLOOR;

	/** The turbine parts a block entity type is valid on. Resolved inside the type's supplier. */
	private static Block[] turbineParts(java.util.function.Predicate<Block> which) {
		return LeafiaBlocks.modular_turbine_parts.values().stream()
				.map(DeferredBlock::get)
				.filter(which)
				.toArray(Block[]::new);
	}

	public static void register(IEventBus modBus) {

		if(LeafiaFeatures.amatPipes) {
			AMAT_DUCT = BLOCK_ENTITIES.register("amat_duct", () -> BlockEntityType.Builder
					.of(LeafiaBlockEntities::newAmatDuct, LeafiaBlocks.amat_duct.get())
					.build(null));

			AMAT_DUCT_CHARGER = BLOCK_ENTITIES.register("amat_duct_charger", () -> BlockEntityType.Builder
					.of(LeafiaBlockEntities::newAmatDuctCharger, LeafiaBlocks.amat_duct_charger.get())
					.build(null));
		}

		if(LeafiaFeatures.ffDuct) {
			// Upstream's TEInit name is ff_duct_te; the type is named after the block, as every addon type is.
			FF_DUCT = BLOCK_ENTITIES.register("ff_duct", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.network.ff_duct.FFDuctTE(FF_DUCT.get(), pos, state),
							LeafiaBlocks.ff_duct.get(), LeafiaBlocks.ff_duct_solid_shielded.get())
					.build(null));
			// Upstream's TEInit names: ff_pump_te, ff_converter_te, ff_filter_te (D-3019).
			FF_PUMP = BLOCK_ENTITIES.register("ff_pump", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.network.ff_duct.utility.pump.FFPumpTE(FF_PUMP.get(), pos, state),
							LeafiaBlocks.ff_pump.get())
					.build(null));
			FF_CONVERTER = BLOCK_ENTITIES.register("ff_converter", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.network.ff_duct.utility.converter.FFConverterTE(FF_CONVERTER.get(), pos, state),
							LeafiaBlocks.ff_converter.get())
					.build(null));
			FF_FILTER = BLOCK_ENTITIES.register("ff_filter", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.network.ff_duct.utility.filter.FFFilterTE2(FF_FILTER.get(), pos, state),
							LeafiaBlocks.ff_filter.get())
					.build(null));
		}

		if(LeafiaFeatures.lftrReactor()) {
			MSR_ELEMENT = BLOCK_ENTITIES.register("msr_element", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE(MSR_ELEMENT.get(), pos, state),
							LeafiaBlocks.msr_element.get())
					.build(null));
			MSR_PLUG = BLOCK_ENTITIES.register("msr_plug", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE(MSR_PLUG.get(), pos, state),
							LeafiaBlocks.msr_plug.get())
					.build(null));
			MSR_CONTROL = BLOCK_ENTITIES.register("msr_control", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.components.control.MSRControlTE(MSR_CONTROL.get(), pos, state),
							LeafiaBlocks.msr_control.get())
					.build(null));
			MSR_ARBITRARY = BLOCK_ENTITIES.register("msr_arbitrary", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryTE(MSR_ARBITRARY.get(), pos, state),
							LeafiaBlocks.msr_arbitrary.get())
					.build(null));
			MSR_EJECTOR = BLOCK_ENTITIES.register("msr_ejector", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorTE(MSR_EJECTOR.get(), pos, state),
							LeafiaBlocks.msr_ejector.get())
					.build(null));
			SALT_SEPARATOR = BLOCK_ENTITIES.register("salt_separator", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE(SALT_SEPARATOR.get(), pos, state),
							LeafiaBlocks.salt_separator.get())
					.build(null));
			SALT_SEPARATOR_DUMMY = BLOCK_ENTITIES.register("salt_separator_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(SALT_SEPARATOR_DUMMY.get(), pos, state),
							LeafiaBlocks.salt_separator.get())
					.build(null));
		}

		if(LeafiaFeatures.spkCable) {
			SPK_CABLE = BLOCK_ENTITIES.register("spk_cable", () -> BlockEntityType.Builder
					.of((pos, state) -> new SPKCableTE(SPK_CABLE.get(), pos, state), LeafiaBlocks.spk_cable.get())
					.build(null));
		}

		if(LeafiaFeatures.rack) {
			RACK = BLOCK_ENTITIES.register("rack", () -> BlockEntityType.Builder
					.of(com.leafia.contents.building.storage.rack.RackTE::new, LeafiaBlocks.sellacity_rack.get())
					.build(null));
		}

		if(LeafiaFeatures.tungstenCrateContent()) {
			CRATE_TUNGSTEN = BLOCK_ENTITIES.register("crate_tungsten", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.nonmachines.storage.items.TungstenCrateSpk(CRATE_TUNGSTEN.get(), pos, state),
							ModBlocks.crate_tungsten.get())
					.build(null));
		}

		if(LeafiaFeatures.rbmkOverhaulContent()) {
			PRIBRIS_SMOKE = BLOCK_ENTITIES.register("pribris_smoke", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmokeTE(PRIBRIS_SMOKE.get(), pos, state),
							LeafiaBlocks.pribris_smoke.get())
					.build(null));
		}

		if(LeafiaFeatures.dfcOverhaulContent()) {
			DFC_CORE =BLOCK_ENTITIES.register("dfc_core", () -> BlockEntityType.Builder
					.of((pos, state) -> new LeafiaCoreTE(DFC_CORE.get(), pos, state), ModBlocks.dfc_core.get())
					.build(null));
			DFC_PULSER = BLOCK_ENTITIES.register("dfc_pulser", () -> BlockEntityType.Builder
					.of((pos, state) -> new CoreDetonatorTE(DFC_PULSER.get(), pos, state), LeafiaBlocks.dfc_pulser.get())
					.build(null));
			DFC_EMITTER = BLOCK_ENTITIES.register("dfc_emitter", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE(DFC_EMITTER.get(), pos, state),
							ModBlocks.dfc_emitter.get())
					.build(null));
			DFC_RECEIVER = BLOCK_ENTITIES.register("dfc_receiver", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE(DFC_RECEIVER.get(), pos, state),
							ModBlocks.dfc_receiver.get(), LeafiaBlocks.dfc_reinforced.get())
					.build(null));
			DFC_INJECTOR = BLOCK_ENTITIES.register("dfc_injector", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.injector.LeafiaCoreInjectorTE(DFC_INJECTOR.get(), pos, state),
							ModBlocks.dfc_injector.get())
					.build(null));
			DFC_STABILIZER = BLOCK_ENTITIES.register("dfc_stabilizer", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE(DFC_STABILIZER.get(), pos, state),
							ModBlocks.dfc_stabilizer.get())
					.build(null));
			DFC_CEMITTER = BLOCK_ENTITIES.register("dfc_cemitter", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterTE(DFC_CEMITTER.get(), pos, state),
							LeafiaBlocks.dfc_cemitter.get())
					.build(null));
			DFC_EXCHANGER = BLOCK_ENTITIES.register("dfc_exchanger", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE(DFC_EXCHANGER.get(), pos, state),
							LeafiaBlocks.dfc_exchanger.get())
					.build(null));
		}

		if(LeafiaFeatures.fluidDuctEquipment) {

			FLUID_DUCT_VALVE = BLOCK_ENTITIES.register("fluid_duct_valve", () -> BlockEntityType.Builder
					.of((pos, state) -> new FluidDuctValveTE(FLUID_DUCT_VALVE.get(), pos, state),
							LeafiaBlocks.fluid_duct_valve_mdl.get())
					.build(null));

			FLUID_DUCT_VALVE_RS = BLOCK_ENTITIES.register("fluid_duct_valve_rs", () -> BlockEntityType.Builder
					.of((pos, state) -> new FluidDuctValveTE(FLUID_DUCT_VALVE_RS.get(), pos, state),
							LeafiaBlocks.fluid_duct_valve_mdl_rs.get())
					.build(null));

			FLUID_DUCT_GAUGE = BLOCK_ENTITIES.register("fluid_duct_gauge", () -> BlockEntityType.Builder
					.of((pos, state) -> new FluidDuctGaugeTE(FLUID_DUCT_GAUGE.get(), pos, state),
							LeafiaBlocks.fluid_duct_gauge_mdl.get())
					.build(null));
		}

		if(LeafiaFeatures.doors) {

			ADDON_DOOR = BLOCK_ENTITIES.register("addon_door", () -> BlockEntityType.Builder
					.of(AddonDoorTE::new, LeafiaBlocks.crimson_door_small.get(), LeafiaBlocks.crimson_door_large.get())
					.build(null));

			REACTOR_DOOR = BLOCK_ENTITIES.register("reactor_door", () -> BlockEntityType.Builder
					.of(ReactorDoorTE::new, LeafiaBlocks.reactor_door.get())
					.build(null));
		}

		if(LeafiaFeatures.lights) {

			LIGHT = BLOCK_ENTITIES.register("light", () -> BlockEntityType.Builder
					.of((pos, state) -> new LightTE(LIGHT.get(), pos, state), LeafiaBlocks.light_unlit.get())
					.build(null));

			LIGHT_PROXY = BLOCK_ENTITIES.register("light_proxy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(LIGHT_PROXY.get(), pos, state).power(),
							LeafiaBlocks.light_unlit.get())
					.build(null));
		}

		if(LeafiaFeatures.debugBlocks) {

			FLUID_VOIDER = BLOCK_ENTITIES.register("fluid_voider", () -> BlockEntityType.Builder
					.of((pos, state) -> new VoiderTE(FLUID_VOIDER.get(), pos, state),
							LeafiaBlocks.test_fluid_voider.get())
					.build(null));
			// D-3031: the black hole and the render test, two empty block entities their renderers draw at
			DEBUG_BLACKHOLE = BLOCK_ENTITIES.register("debug_blackhole", () -> BlockEntityType.Builder
					.of(com.leafia.contents.debug.blackhole_test.DebugBHTE::new, LeafiaBlocks.test_blackhole_render.get())
					.build(null));
			DEBUG_RENDER_TEST = BLOCK_ENTITIES.register("debug_render_test", () -> BlockEntityType.Builder
					.of(com.leafia.contents.debug.render_test.DebugRenderTestTE::new, LeafiaBlocks.test_render.get())
					.build(null));
		}

		if(LeafiaFeatures.debugFFBlocks()) {

			// D-3031: the debug Forge-fluid source and tank
			FF_SOURCE = BLOCK_ENTITIES.register("debug_ff_source", () -> BlockEntityType.Builder
					.of(com.leafia.contents.debug.ff_test.source.DebugSourceTE::new, LeafiaBlocks.test_ff_source.get())
					.build(null));
			FF_TANK = BLOCK_ENTITIES.register("debug_ff_tank", () -> BlockEntityType.Builder
					.of(com.leafia.contents.debug.ff_test.tank.DebugTankTE::new, LeafiaBlocks.test_ff_tank.get())
					.build(null));
		}

		if(LeafiaFeatures.turbines) {

			WIND_TURBINE_MEDIUM = BLOCK_ENTITIES.register("wind_turbine_medium", () -> BlockEntityType.Builder
					.of((pos, state) -> new WindTurbineMediumTE(WIND_TURBINE_MEDIUM.get(), pos, state),
							LeafiaBlocks.wind_turbine_medium.get())
					.build(null));
		}

		if(LeafiaFeatures.modularTurbine()) {

			MODULAR_TURBINE_CORE = BLOCK_ENTITIES.register("modular_turbine_core", () -> BlockEntityType.Builder
					.of((pos, state) -> new MTCoreTE(MODULAR_TURBINE_CORE.get(), pos, state),
							LeafiaBlocks.modular_turbine_core.get())
					.build(null));

			MODULAR_TURBINE_COMPONENT = BLOCK_ENTITIES.register("modular_turbine_component", () -> BlockEntityType.Builder
					.of((pos, state) -> new ModularTurbineComponentTE(MODULAR_TURBINE_COMPONENT.get(), pos, state),
							turbineParts(block -> !(block instanceof IMTPortBlock) && !(block instanceof MTFlywheelBase)))
					.build(null));

			MODULAR_TURBINE_PORT = BLOCK_ENTITIES.register("modular_turbine_port", () -> BlockEntityType.Builder
					.of((pos, state) -> new MTComponentPortTE(MODULAR_TURBINE_PORT.get(), pos, state),
							turbineParts(block -> block instanceof IMTPortBlock))
					.build(null));

			MODULAR_TURBINE_FLYWHEEL = BLOCK_ENTITIES.register("modular_turbine_flywheel", () -> BlockEntityType.Builder
					.of((pos, state) -> new MTFlywheelTE(MODULAR_TURBINE_FLYWHEEL.get(), pos, state),
							turbineParts(block -> block instanceof MTFlywheelBase))
					.build(null));

			MODULAR_TURBINE_PROXY = BLOCK_ENTITIES.register("modular_turbine_proxy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(MODULAR_TURBINE_PROXY.get(), pos, state).fluid(),
							LeafiaBlocks.modular_turbine_parts.values().stream()
									.map(DeferredBlock::get).toArray(Block[]::new))
					.build(null));

			MODULAR_TURBINE_POWER_PORT = BLOCK_ENTITIES.register("modular_turbine_power_port", () -> BlockEntityType.Builder
					.of((pos, state) -> new MTPowerPortTE(MODULAR_TURBINE_POWER_PORT.get(), pos, state),
							LeafiaBlocks.modular_turbine_port.get())
					.build(null));
		}

		if(LeafiaFeatures.letterSigns) {

			LETTER_SIGN = BLOCK_ENTITIES.register("letter_sign", () -> BlockEntityType.Builder
					.of(SignTE::new, LeafiaBlocks.letter_signs.values().stream()
							.map(DeferredBlock::get).toArray(Block[]::new))
					.build(null));
		}

		if(LeafiaFeatures.realerSim) {

			RBMK_REALERSIM = BLOCK_ENTITIES.register("rbmk_realersim", () -> BlockEntityType.Builder
					.of(RBMKRealerSimTE::new, LeafiaBlocks.rbmk_rod_realersim.get(), LeafiaBlocks.rbmk_rod_realersim_mod.get())
					.build(null));

			RBMK_REALERSIM_PROXY = BLOCK_ENTITIES.register("rbmk_realersim_proxy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(RBMK_REALERSIM_PROXY.get(), pos, state).inventory(),
							LeafiaBlocks.rbmk_rod_realersim.get(), LeafiaBlocks.rbmk_rod_realersim_mod.get())
					.build(null));
		}

		if(LeafiaFeatures.elevators) {

			EV_PULLEY = BLOCK_ENTITIES.register("ev_pulley", () -> BlockEntityType.Builder
					.of(EvPulleyTE::new, LeafiaBlocks.elevator_pulley.get())
					.build(null));

			EV_BUFFER = BLOCK_ENTITIES.register("ev_buffer", () -> BlockEntityType.Builder
					.of(EvBufferTE::new, LeafiaBlocks.elevator_buffer.get())
					.build(null));

			EV_FLOOR = BLOCK_ENTITIES.register("ev_floor", () -> BlockEntityType.Builder
					.of(EvFloorTE::new,
							LeafiaBlocks.elevator_s6floor.get(),
							LeafiaBlocks.elevator_skyliftfloor.get())
					.build(null));
		}

		if(LeafiaFeatures.broof) {

			BROOF = BLOCK_ENTITIES.register("broof", () -> BlockEntityType.Builder
					.of(BroofTE::new, LeafiaBlocks.broof.get())
					.build(null));
		}

		if(LeafiaFeatures.controlPanel) {

			CONTROL_PANEL = BLOCK_ENTITIES.register("control_panel", () -> BlockEntityType.Builder
					.of(com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE::new,
							LeafiaBlocks.control_panel_custom.get(),
							LeafiaBlocks.control_panel_front.get())
					.build(null));

			CONTROL_TORCH = BLOCK_ENTITIES.register("control_torch_te", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.panel.controltorch.ControlTorchTE(CONTROL_TORCH.get(), pos, state),
							LeafiaBlocks.control_torch.get(), LeafiaBlocks.control_torch_unlit.get())
					.build(null));
		}

		if(LeafiaFeatures.hpBoiler) {

			HP_BOILER = BLOCK_ENTITIES.register("hp_boiler", () -> BlockEntityType.Builder
					.of((pos, state) -> new HPBoilerTE(HP_BOILER.get(), pos, state),
							LeafiaBlocks.hp_boiler.get())
					.build(null));

			HP_BOILER_DUMMY = BLOCK_ENTITIES.register("hp_boiler_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(HP_BOILER_DUMMY.get(), pos, state),
							LeafiaBlocks.hp_boiler.get())
					.build(null));
		}

		if(LeafiaFeatures.meteorDiverter) {
			DIVERTER = BLOCK_ENTITIES.register("diverter_te", () -> BlockEntityType.Builder
					.of(com.leafia.contents.miscellanous.diverter.DiverterTE::new, LeafiaBlocks.diverter.get(), LeafiaBlocks.diverter_unlit.get())
					.build(null));
		}

		if(LeafiaFeatures.addonBombs) {
			NUKE_CHUD = BLOCK_ENTITIES.register("nuke_chud_te", () -> BlockEntityType.Builder
					.of(com.leafia.contents.bomb.chud.NukeChudTE::new, LeafiaBlocks.nuke_chud.get())
					.build(null));
		}

		if(LeafiaFeatures.rtg) {

			HEATER_RT = BLOCK_ENTITIES.register("heater_rt", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.heat.rtheater.HeaterRTGTE(HEATER_RT.get(), pos, state),
							LeafiaBlocks.heater_rt.get())
					.build(null));

			HEATER_RT_DUMMY = BLOCK_ENTITIES.register("heater_rt_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(HEATER_RT_DUMMY.get(), pos, state),
							LeafiaBlocks.heater_rt.get())
					.build(null));
		}

		if(LeafiaFeatures.sirenOverhaulContent())
			SIREN = BLOCK_ENTITIES.register("machine_siren", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.overwrite_contents.siren.LeafiaSirenTE(SIREN.get(), pos, state), com.hbm.blocks.ModBlocks.machine_siren.get())
					.build(null));
		if(LeafiaFeatures.arcWelderOverhaulContent())
			ARC_WELDER = BLOCK_ENTITIES.register("machine_arc_welder", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.overwrite_contents.welding.LeafiaArcWelderTE(ARC_WELDER.get(), pos, state), com.hbm.blocks.ModBlocks.machine_arc_welder.get())
					.build(null));
		if(LeafiaFeatures.solderingOverhaulContent())
			SOLDERING_STATION = BLOCK_ENTITIES.register("machine_soldering_station", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.overwrite_contents.welding.LeafiaSolderingStationTE(SOLDERING_STATION.get(), pos, state), com.hbm.blocks.ModBlocks.machine_soldering_station.get())
					.build(null));
		if(LeafiaFeatures.felOverhaulContent())
			FEL = BLOCK_ENTITIES.register("machine_fel", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.overwrite_contents.fel.LeafiaFELTE(FEL.get(), pos, state), com.hbm.blocks.ModBlocks.machine_fel.get())
					.build(null));

		if(LeafiaFeatures.zirnoxOverhaulContent()) {
			ZIRNOX = BLOCK_ENTITIES.register("zirnox", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE(ZIRNOX.get(), pos, state),
							com.hbm.blocks.ModBlocks.reactor_zirnox.get())
					.build(null));
		}

		if(LeafiaFeatures.rtgFurnaceOverhaulContent()) {
			// valid for both blocks: the furnace swaps between them as it starts and stops, keeping its block entity
			DIFURNACE_RTG = BLOCK_ENTITIES.register("difurnace_rtg", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGTE(DIFURNACE_RTG.get(), pos, state),
							com.hbm.blocks.ModBlocks.machine_difurnace_rtg_off.get(), com.hbm.blocks.ModBlocks.machine_difurnace_rtg_on.get())
					.build(null));
		}

		if(LeafiaFeatures.mixingVatMachine()) {

			MIXING_VAT = BLOCK_ENTITIES.register("mixing_vat", () -> BlockEntityType.Builder
					.of((pos, state) -> new MixingVatTE(MIXING_VAT.get(), pos, state), LeafiaBlocks.mixingvat.get())
					.build(null));

			MIXING_VAT_DUMMY = BLOCK_ENTITIES.register("mixing_vat_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new MixingVatProxy(MIXING_VAT_DUMMY.get(), pos, state), LeafiaBlocks.mixingvat.get())
					.build(null));
		}

		if(LeafiaFeatures.coolantHeatexMachine()) {

			COOLANT_HEATEX = BLOCK_ENTITIES.register("coolant_heatex", () -> BlockEntityType.Builder
					.of((pos, state) -> new CoolantHeatexTE(COOLANT_HEATEX.get(), pos, state), LeafiaBlocks.coolant_heatex.get())
					.build(null));

			COOLANT_HEATEX_DUMMY = BLOCK_ENTITIES.register("coolant_heatex_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(COOLANT_HEATEX_DUMMY.get(), pos, state),
							LeafiaBlocks.coolant_heatex.get())
					.build(null));
		}

		if(LeafiaFeatures.slopReactor) {

			SLOP = BLOCK_ENTITIES.register("slop_te", () -> BlockEntityType.Builder
					.of((pos, state) -> new SlopTE(SLOP.get(), pos, state),
							LeafiaBlocks.slop_reactor.get())
					.build(null));
		}

		if(LeafiaFeatures.solBlaster) {

			SOL_BLASTER = BLOCK_ENTITIES.register("sol_blaster", () -> BlockEntityType.Builder
					.of((pos, state) -> new SolBlasterTE(SOL_BLASTER.get(), pos, state),
							LeafiaBlocks.sol_blaster.get())
					.build(null));

			SOL_BLASTER_DUMMY = BLOCK_ENTITIES.register("sol_blaster_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(SOL_BLASTER_DUMMY.get(), pos, state),
							LeafiaBlocks.sol_blaster.get())
					.build(null));
		}

		if(LeafiaFeatures.amsp) {

			AMSP_ANALYZER = BLOCK_ENTITIES.register("amsp_analyzer", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerTE(AMSP_ANALYZER.get(), pos, state),
							LeafiaBlocks.amsp_analyzer.get())
					.build(null));
			AMSP_RECEIVER = BLOCK_ENTITIES.register("amsp_receiver", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverTE(AMSP_RECEIVER.get(), pos, state),
							LeafiaBlocks.amsp_receiver.get())
					.build(null));
		}

		if(LeafiaFeatures.aprReactor()) {
			APR_CORE = BLOCK_ENTITIES.register("apr_core", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE(APR_CORE.get(), pos, state), LeafiaBlocks.apr_core.get())
					.build(null));
			APR_CORE_DUMMY = BLOCK_ENTITIES.register("apr_core_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(APR_CORE_DUMMY.get(), pos, state), LeafiaBlocks.apr_core.get())
					.build(null));
			APR_PORT = BLOCK_ENTITIES.register("apr_port", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOTE(APR_PORT.get(), pos, state), LeafiaBlocks.apr_port.get())
					.build(null));
			APR_OUTLET = BLOCK_ENTITIES.register("apr_outlet", () -> BlockEntityType.Builder
					.of((pos, state) -> new com.leafia.contents.machines.reactors.apr.blocks.outlet.APRPowerOutletTE(APR_OUTLET.get(), pos, state), LeafiaBlocks.apr_outlet.get())
					.build(null));
		}

		if(LeafiaFeatures.ams) {

			AMS_BASE = BLOCK_ENTITIES.register("ams_base", () -> BlockEntityType.Builder
					.of((pos, state) -> new AMSBaseTE(AMS_BASE.get(), pos, state), LeafiaBlocks.ams_base.get())
					.build(null));
			AMS_BASE_DUMMY = BLOCK_ENTITIES.register("ams_base_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(AMS_BASE_DUMMY.get(), pos, state), LeafiaBlocks.ams_base.get())
					.build(null));

			AMS_EMITTER = BLOCK_ENTITIES.register("ams_emitter", () -> BlockEntityType.Builder
					.of((pos, state) -> new AMSEmitterTE(AMS_EMITTER.get(), pos, state), LeafiaBlocks.ams_emitter.get())
					.build(null));
			AMS_EMITTER_DUMMY = BLOCK_ENTITIES.register("ams_emitter_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(AMS_EMITTER_DUMMY.get(), pos, state), LeafiaBlocks.ams_emitter.get())
					.build(null));

			AMS_STABILIZER = BLOCK_ENTITIES.register("ams_limiter", () -> BlockEntityType.Builder
					.of((pos, state) -> new AMSStabilizerTE(AMS_STABILIZER.get(), pos, state), LeafiaBlocks.ams_limiter.get())
					.build(null));
			AMS_STABILIZER_DUMMY = BLOCK_ENTITIES.register("ams_limiter_dummy", () -> BlockEntityType.Builder
					.of((pos, state) -> new BlockEntityProxyCombo(AMS_STABILIZER_DUMMY.get(), pos, state), LeafiaBlocks.ams_limiter.get())
					.build(null));
		}

		if(LeafiaFeatures.pwrReactor()) {

			PWR_ELEMENT = BLOCK_ENTITIES.register("lwr_element", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRElementTE(PWR_ELEMENT.get(), pos, state),
							LeafiaBlocks.lwr_element.get(), LeafiaBlocks.reactor_element.get(), LeafiaBlocks.reactor_element_blank.get())
					.build(null));
			PWR_ELEMENT_PROXY = BLOCK_ENTITIES.register("lwr_element_proxy", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRProxyTE(PWR_ELEMENT_PROXY.get(), pos, state),
							LeafiaBlocks.lwr_element.get(), LeafiaBlocks.reactor_element.get(), LeafiaBlocks.reactor_element_blank.get())
					.build(null));
			PWR_CONTROL = BLOCK_ENTITIES.register("lwr_control", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRControlTE(PWR_CONTROL.get(), pos, state),
							LeafiaBlocks.lwr_control.get(), LeafiaBlocks.reactor_control.get())
					.build(null));
			PWR_COMPUTER = BLOCK_ENTITIES.register("lwr_signal", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRComputerTE(PWR_COMPUTER.get(), pos, state), LeafiaBlocks.lwr_signal.get())
					.build(null));
			PWR_PORT = BLOCK_ENTITIES.register("lwr_port", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRPortTE(PWR_PORT.get(), pos, state), LeafiaBlocks.lwr_port.get())
					.build(null));
			PWR_OCCS_IN = BLOCK_ENTITIES.register("lwr_occs_in", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWROCCSInTE(PWR_OCCS_IN.get(), pos, state), LeafiaBlocks.lwr_occs_in.get())
					.build(null));
			PWR_OCCS_OUT = BLOCK_ENTITIES.register("lwr_occs_out", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWROCCSOutTE(PWR_OCCS_OUT.get(), pos, state), LeafiaBlocks.lwr_occs_out.get())
					.build(null));
			PWR_TERMINAL = BLOCK_ENTITIES.register("lwr_terminal", () -> BlockEntityType.Builder
					.of((pos, state) -> new PWRTerminalTE(PWR_TERMINAL.get(), pos, state),
							LeafiaBlocks.lwr_terminal.get(), LeafiaBlocks.reactor_hatch.get(), LeafiaBlocks.reactor_hatch_alt.get())
					.build(null));
			PWR_WRECK = BLOCK_ENTITIES.register("lwr_wreck", () -> BlockEntityType.Builder
					.of(PWRMeshedWreckEntity::new, LeafiaBlocks.lwrwreck_stone.get(), LeafiaBlocks.lwrwreck_metal.get())
					.build(null));
		}

		if(LeafiaFeatures.regexFilter) {

			REGEX_FILTER = BLOCK_ENTITIES.register("regex_filter", () -> BlockEntityType.Builder
					.of(RegexFilterTE::new, LeafiaBlocks.regex_filter.get())
					.build(null));
		}

		BLOCK_ENTITIES.register(modBus);
	}

	private static AmatDuctTE newAmatDuct(BlockPos pos, BlockState state) {
		return new AmatDuctTE(AMAT_DUCT.get(), pos, state);
	}

	private static AmatDuctChargerTE newAmatDuctCharger(BlockPos pos, BlockState state) {
		return new AmatDuctChargerTE(AMAT_DUCT_CHARGER.get(), pos, state);
	}
}
