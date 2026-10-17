package com.leafia.contents;

import com.hbm.blocks.ModSoundType;
import com.hbm.sound.NtmSounds;

import com.leafia.contents.machines.powercores.dfc.OsmiridiumBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.hbm.blocks.IBlockMulti;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.ModSoundTypes;
import com.hbm.items.block.ItemBlockBase;
import com.leafia.contents.bomb.balefire.AshBalefire;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;
import com.leafia.contents.bomb.digamma.DigammititeBlock;
import com.leafia.dev.blocks.blockbase.AddonBlockPowder;
import com.leafia.dev.blocks.legacy.LegacyBlockHazardMeta;
import com.leafia.dev.blocks.legacy.LegacyWasteEarth;
import com.leafia.dev.blocks.legacy.LegacyWasteIce;
import com.leafia.dev.blocks.legacy.LegacyWasteSand;
import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.dev.blocks.legacy.MetaLevel;
import com.leafia.dev.blocks.legacy.RadImmuneSpawn;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.building.catwalk.railing.types.CatwalkRailingCurved;
import com.leafia.contents.building.generic.ConcreteBricks;
import com.leafia.contents.building.generic.lined_asphalt.LinedAsphaltBlock;
import com.leafia.contents.building.generic.mixed.BlockMixedConcrete;
import com.custom_hbm.contents.oilycoal.BlockCoalBurning;
import com.custom_hbm.contents.oilycoal.BlockCoalOil;
import com.leafia.contents.building.storage.broof.BroofBlock;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerBlock;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexBlock;
import com.leafia.contents.machines.processing.mixingvat.MixingVatBlock;
import com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorBlock;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.PWRHullBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.PWRReflectorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRChannelBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRConductorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRExchangerBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.computer.PWRComputerBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.occs.inlet.PWROCCSInBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.occs.outlet.PWROCCSOutBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.port.PWRPortBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRWreckMetal;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRWreckStone;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterBlock;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerBlock;
import com.leafia.contents.machines.processing.solblaster.SolBlasterBlock;
import com.leafia.contents.miscellanous.slop.SlopBlock;
import com.leafia.contents.miscellanous.slop.SlopGlass;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterBlock;
import com.leafia.contents.minerals.corium.CoriumOreBase;
import com.leafia.contents.building.light.LightBlock;
import com.leafia.contents.building.sign.SignBlock;
import com.leafia.dev.blocks.ICustomItemBlockProvider;
import com.leafia.contents.building.light.LightEmitter;
import com.leafia.contents.debug.fluid_voider.VoiderBlock;
import com.leafia.contents.machines.elevators.EvBuffer;
import com.leafia.contents.machines.elevators.EvPulley;
import com.leafia.contents.machines.elevators.EvShaftNeo;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.contents.machines.elevators.car.ElevatorLight;
import com.leafia.contents.machines.misc.modular_turbine.MTShaftBlock;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.blades.*;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreBlock;
import com.leafia.contents.machines.misc.modular_turbine.flywheel.*;
import com.leafia.contents.machines.misc.modular_turbine.generator.MTGenerator3x3;
import com.leafia.contents.machines.misc.modular_turbine.ports.*;
import com.leafia.contents.machines.misc.modular_turbine.power_port.MTPowerPortBlock;
import com.leafia.contents.machines.misc.modular_turbine.separator.*;
import com.leafia.contents.machines.misc.wind_turbines.medium.WindTurbineMediumBlock;
import com.leafia.contents.building.doors.AddonDoorDecl;
import com.leafia.contents.building.doors.AddonDoorGeneric;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorBlock;
import com.leafia.contents.network.fluid.gauges.FluidDuctGauge;
import com.leafia.contents.network.fluid.valves.FluidDuctValve;
import com.leafia.contents.network.fluid.valves.FluidDuctValveRS;
import com.leafia.contents.network.pipe_amat.AmatDuctStandard;
import com.leafia.contents.network.spk_cable.SPKCableBlock;
import com.leafia.contents.network.ff_duct.FFDuctRadShielded;
import com.leafia.contents.network.ff_duct.FFDuctStandard;
import com.leafia.contents.network.ff_duct.utility.converter.FFConverterBlock;
import com.leafia.contents.network.ff_duct.utility.filter.FFFilterBlock;
import com.leafia.contents.network.ff_duct.utility.pump.FFPumpBlock;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryBlock;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlBlock;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlExtension;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.hbm.blocks.machine.CoreComponent;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.contents.network.pipe_amat.charger.AmatDuctChargerBlock;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import com.leafia.contents.machines.reactors.rbmk.columns.realersim.RBMKRealerSimBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's block registry, mirroring {@code com.hbm.blocks.ModBlocks}.
 *
 * <h2>The gate</h2>
 *
 * Content is registered inside {@link #register}, behind its feature switch, rather than in
 * static field initialisers. That is the difference D-1002 turns on: a static
 * {@code public static final DeferredBlock<...> foo = reg(...)} runs whenever the class is
 * touched, so it cannot be conditional. Registering from a method that has already read
 * {@link LeafiaFeatures} can be.
 *
 * <p>The cost is that a gated block cannot be a {@code static final} field a caller
 * dereferences directly — it is null when its feature is off, and any consumer must be gated
 * by the same switch. That is the correct shape anyway: code that runs only when a feature is
 * on should not be reachable when it is off.
 */
public final class LeafiaBlocks {

	private LeafiaBlocks() {}

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LeafiaRef.MODID);
	public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(LeafiaRef.MODID);

	/**
	 * `amat_duct`, the antimatter pipe. Null when {@code features.amatPipes} is off.
	 *
	 * <p>Upstream's registry name, kept, even though its class and its model are named
	 * {@code pipe_amat} after the package — a player's world keys should match upstream's
	 *. Upstream's own numbers too: {@code setHardness(5.0F)} and
	 * {@code setResistance(10.0F)}, which is 6.0F of 1.21.1 explosion resistance by the same
	 * ×3/÷5 mapping the base mod's identical {@code fluid_duct_neo} line already uses.
	 */
	public static DeferredBlock<AmatDuctStandard> amat_duct;

	/**
	 * `amat_duct_charger`, which keeps the pipe's magnetic field up. Null when
	 * {@code features.amatPipes} is off — it shares the pipe's switch because neither is any
	 * use without the other.
	 */
	public static DeferredBlock<AmatDuctChargerBlock> amat_duct_charger;

	/**
	 * `spk_cable`, the DFC Redirection Cable. Null when {@code features.spkCable} is off.
	 *
	 * <p>Upstream's numbers: {@code setHardness(5.0F)} and {@code setResistance(10.0F)} on
	 * {@code Material.IRON}, which needs no tool, so there is no {@code requiresCorrectToolForDrops}
	 * - the amat pipe's line, not the charger's. Resistance 10 is 6.0F by the same x0.6 mapping.
	 */
	public static DeferredBlock<SPKCableBlock> spk_cable;

	/**
	 * `ff_duct`, upstream's "Special Pipe", in the base duct's three finishes, and
	 * `ff_duct_solid_shielded`, the radiation-shielded one. Null when {@code features.ffDuct} is off.
	 * Upstream's numbers: the duct {@code setHardness(5.0F).setResistance(10.0F)} - 6.0F by the x0.6
	 * mapping, as the amat pipe - and the shielded one {@code setHardness(15.0F).setResistance(COMPOUND_MESH.v)},
	 * a {@code GenericBlockResistance(240)} that reads back 240 (D-012, the PWR's casing note). Both
	 * {@code Material.IRON}, which needed a pickaxe in 1.12.2; the duct sounds like a pipe, the shielded
	 * one like stone (no {@code setSoundType}). See D-3012.
	 */
	public static DeferredBlock<FFDuctStandard> ff_duct;
	public static DeferredBlock<FFDuctRadShielded> ff_duct_solid_shielded;

	/**
	 * The duct's in-line utilities: `ff_pump`, `ff_converter` and `ff_filter`, upstream's `AddonBlocks`
	 * {@code new FFPumpBlock(Material.IRON, ...).setHardness(5.0F).setResistance(10.0F)} - 5 and 6 by the x0.6 mapping,
	 * a pickaxe to drop (D-1074's reading), stone to the ear (no {@code setSoundType}), not full cubes. Null when
	 * {@code features.ffDuct} is off. See D-3019.
	 */
	public static DeferredBlock<FFPumpBlock> ff_pump;
	public static DeferredBlock<FFConverterBlock> ff_converter;
	public static DeferredBlock<FFFilterBlock> ff_filter;

	/**
	 * The molten-salt reactor's six blocks, upstream's {@code AddonBlocks.LFTR}: all {@code Material.IRON}
	 * at {@code generalHardness} 12 with no resistance set (12, D-012), a pickaxe to drop, stone to the ear.
	 * Null unless {@code LeafiaFeatures.lftrReactor()}. See D-3012.
	 */
	public static DeferredBlock<MSRElementBlock> msr_element;
	public static DeferredBlock<MSRPlugBlock> msr_plug;
	public static DeferredBlock<MSRControlBlock> msr_control;
	public static DeferredBlock<MSRControlExtension> msr_control_extension;
	public static DeferredBlock<MSRArbitraryBlock> msr_arbitrary;
	public static DeferredBlock<MSREjectorBlock> msr_ejector;

	/**
	 * `salt_separator`, the Salt Fuel Separation Plant (D-3015). Upstream's {@code Material.IRON}
	 * {@code SaltSeparatorBlock} at {@code setHardness(5.0F).setResistance(20.0F)} in the machine tab:
	 * 20 x 0.6 = 12 by D-012. The same multiblock rules as the HP boiler's (noSpawns, noOcclusion, the
	 * sound {@code AddonBlockDummyable} gives, no tool to drop - D-1072), and it joins the LFTR's switch,
	 * since the salt it separates is the reactor's. Null unless {@code LeafiaFeatures.lftrReactor()}.
	 */
	public static DeferredBlock<SaltSeparatorBlock> salt_separator;

	/**
	 * `dfc_pulser`, the DFC Pulse Detonator. Null unless the DFC overhaul's content is registered
	 * ({@code LeafiaFeatures.dfcOverhaulContent()}).
	 *
	 * <p>Upstream: {@code new AddonCoreComponent(Material.IRON, "dfc_pulser").setHardness(5.0F)
	 * .setResistance(50.0F)} in the machine tab, whose block entity {@code MixinCoreComponent} swaps for
	 * a {@code CoreDetonatorTE}. Here it is the base mod's own {@link CoreComponent} built with the
	 * detonator's type and factory, which is what that block's constructor takes (D-968, D-3011).
	 * Resistance 50 is 30 by the x0.6 mapping; the rest is the base mod's components' properties.
	 */
	public static DeferredBlock<CoreComponent> dfc_pulser;

	/**
	 * The DFC overhaul's three other component blocks (D-3014): `dfc_reinforced` (the reinforced absorber,
	 * the overhauled receiver's block entity in a block that does not strain), `dfc_exchanger` (the heat
	 * exchanger) and `dfc_cemitter` (the creative booster). Null unless
	 * {@code LeafiaFeatures.dfcOverhaulContent()}. Upstream registers each as {@code new AddonCoreComponent
	 * (Material.IRON, ...).setHardness(5.0F).setResistance(50.0F)}, exactly as the pulse detonator; here each
	 * is the base mod's {@link CoreComponent} with its block entity's type and factory passed in, so
	 * {@code AddonCoreComponent} (which only moved the block between two creative-tab lists) dissolves.
	 */
	public static DeferredBlock<CoreComponent> dfc_reinforced;
	public static DeferredBlock<CoreComponent> dfc_exchanger;
	public static DeferredBlock<CoreComponent> dfc_cemitter;

	/**
	 * The smoking RBMK debris a melted reactor's centre becomes under the RBMK overhaul (D-3018). Null
	 * unless {@code LeafiaFeatures.rbmkOverhaulContent()}. Upstream: {@code new RBMKDebrisSmoke("pribris_smoke")
	 * .setCreativeTab(MainRegistry.machineTab).setHardness(50.0F).setResistance(60000000000F)} - "dont let the
	 * corium destroy it" - which is {@code 3.6E10F} by the x0.6 mapping; the rest is the base mod's
	 * {@code pribris}, which it extends.
	 */
	public static DeferredBlock<com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmoke> pribris_smoke;

	/**
	 * The two fluid valves, upstream's ids and all. Null when {@code features.fluidDuctEquipment}
	 * is off. Upstream's numbers: hardness 5.0, resistance 10.0 — the base mod's own duct line
	 * to the digit.
	 */
	public static DeferredBlock<FluidDuctValve> fluid_duct_valve_mdl;
	public static DeferredBlock<FluidDuctValveRS> fluid_duct_valve_mdl_rs;

	/**
	 * The flow gauge, upstream's {@code fluid_duct_gauge_mdl}: hardness 5.0, resistance 10.0 - the
	 * valves' own line. Null when {@code features.fluidDuctEquipment} is off (D-3005).
	 */
	public static DeferredBlock<FluidDuctGauge> fluid_duct_gauge_mdl;

	/**
	 * The three doors (D-3005). Null when {@code features.doors} is off.
	 *
	 * <p>Upstream: {@code new ReactorDoorBlock(Material.IRON, REACTOR_DOOR, true, "reactor_door")
	 * .setHardness(150.0F).setResistance(13500.0F)} and the two crimson doors,
	 * {@code setHardness(150).setResistance(135000)}, all in the machine tab and all radiation resistant.
	 * Resistance x 0.6 by D-012: 8,100 and 81,000.
	 */
	public static DeferredBlock<ReactorDoorBlock> reactor_door;
	public static DeferredBlock<AddonDoorGeneric> crimson_door_small;
	public static DeferredBlock<AddonDoorGeneric> crimson_door_large;

	/**
	 * The fluorescent light and the invisible emitter it drops beneath itself. Upstream
	 * registers {@code light_unlit} and {@code light_lit}; here the lit half is a blockstate
	 * property and only {@code light_unlit} - the id of the item a player actually holds -
	 * is registered. See D-1017.
	 */
	public static DeferredBlock<LightBlock> light_unlit;
	public static DeferredBlock<LightEmitter> light_emitter;

	/**
	 * `test_fluid_voider`. Null when {@code features.debugBlocks} is off.
	 *
	 * <p>Upstream builds it with {@code Material.ANVIL} and never calls {@code setHardness} or
	 * {@code setResistance}, so it keeps {@code Block}'s defaults: zero of both, which is why it
	 * breaks instantly. The map colour and "pistons cannot move me" come from the material.
	 *
	 * <h2>What is deliberately <em>not</em> carried over, and why</h2>
	 *
	 * {@code AddonBlockBase} calls {@code setHarvestLevel("pickaxe", 0)} on every addon block, so
	 * upstream needs a pickaxe to drop any of them. The literal translation is
	 * {@code requiresCorrectToolForDrops()} — and on 1.21.1 that flag means <b>nothing drops at
	 * all</b> unless the block is also in {@code minecraft:mineable/pickaxe}, because the tool
	 * requirement lives in tags now rather than on the block. <b>This addon ships no block tags</b>
	 * (only damage types), so adding the flag here would have made the voider unharvestable by any
	 * tool — strictly worse than either upstream behaviour.
	 *
	 * <p>So the flag was left off, as on the four addon blocks already landed, until the addon
	 * had a pickaxe tag. It has one since D-1063, and D-1072 put the flag and the tag row on every
	 * block that inherits {@code AddonBlockBase}'s requirement, this one included.
	 */
	public static DeferredBlock<VoiderBlock> test_fluid_voider;

	/**
	 * Upstream's other eight debug blocks (D-3031), all {@code Material.ANVIL} with no hardness set - so, like the voider,
	 * instant to break, immovable by pistons and metal on the map. The six that are {@code AddonBlockBase}s need a pickaxe
	 * to drop and sit in the addon's tab beside the voider; the Forge-fluid source and tank are {@code AddonBlockContainer}s,
	 * which set neither, so they drop by hand and are in no tab ({@code /give} only), as upstream. The source and tank need
	 * the bridge ({@code LeafiaFeatures.debugFFBlocks()}) and the circle test the APR ({@code debugCircle()}).
	 */
	public static DeferredBlock<com.leafia.contents.debug.ff_test.source.DebugSourceBlock> test_ff_source;
	public static DeferredBlock<com.leafia.contents.debug.ff_test.tank.DebugTankBlock> test_ff_tank;
	public static DeferredBlock<com.leafia.contents.debug.explosion_test.DebugBoomBlock> test_boom;
	public static DeferredBlock<com.leafia.contents.debug.rbmk_jet.DebugRBMKJetEmitter> test_rbmk_jet;
	public static DeferredBlock<com.leafia.contents.debug.blackhole_test.DebugBHBlock> test_blackhole_render;
	public static DeferredBlock<com.leafia.contents.debug.render_test.DebugRenderTestBlock> test_render;
	public static DeferredBlock<com.leafia.contents.debug.tom_test.TomTest> test_tom;
	public static DeferredBlock<com.leafia.contents.debug.circle_test.CircleTestBlock> test_circle;

	/** {@code Material.ANVIL} with no hardness: the voider's properties, less the tool requirement. */
	private static BlockBehaviour.Properties debugProps() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.STONE)
				.instabreak()
				.pushReaction(PushReaction.BLOCK);
	}

	/**
	 * `wind_turbine_medium`. Null when {@code features.turbines} is off — it shares that switch
	 * with the modular turbine, because both are what upstream files under "turbines".
	 *
	 * <p>Upstream's numbers: {@code setHardness(5.0F)} and {@code setResistance(10.0F)}, the same
	 * pair every other addon machine uses, which is 6.0F of 1.21.1 explosion resistance.
	 */
	public static DeferredBlock<WindTurbineMediumBlock> wind_turbine_medium;

	/**
	 * `deco_turbine`: a plain cube in the modular turbine's casing texture, for building around a
	 * turbine. Upstream's {@code AddonBlocks.ModularTurbines.deco_turbine} - an
	 * {@code AddonBlockBase} with no class of its own, so it has no ledger row. Upstream gives it
	 * connected textures through the CTM mod, which this port does not have (D-1059), so it is the
	 * plain face. {@code setHardness(5)}/{@code setResistance(10)}, so 6.0F. On {@code turbines}
	 * alone: it needs no steam table.
	 */
	public static DeferredBlock<Block> deco_turbine;

	/**
	 * `modular_turbine_core`. Null unless {@link LeafiaFeatures#modularTurbine()} — the turbine
	 * switch <em>and</em> the addon's fluids, because its steam table starts at deathsteam.
	 *
	 * <p>Upstream's numbers: {@code setHardness(5)} and {@code setResistance(50)} in
	 * {@code ModularTurbineBlockBase}'s constructor, overriding the 10 its own base sets. 1.12.2
	 * triples a resistance and an explosion divides it by five, so 50 is 30.0F here.
	 */
	public static DeferredBlock<MTCoreBlock> modular_turbine_core;

	/**
	 * `modular_turbine_port`, the electricity port. Same gate as the core. Upstream's
	 * {@code setHardness(5.0F)}/{@code setResistance(10.0F)}, so 6.0F here.
	 */
	public static DeferredBlock<MTPowerPortBlock> modular_turbine_port;

	/**
	 * The turbine's multiblock parts, by registry name, in upstream's registration order: the
	 * shaft, the blades in their four looks and the flywheels (each 2x2 up to 9x9), then the
	 * separators, top ports, side ports and inline ports (each 3x3 up to 9x9). Empty
	 * unless {@link LeafiaFeatures#modularTurbine()}.
	 *
	 * <p>In the creative tab since T6, when the component renderer landed (D-1062); before it every
	 * part was invisible and registered on no tab, as the elevators were until theirs (D-1039).
	 */
	public static final Map<String, DeferredBlock<? extends ModularTurbineBlockBase>> modular_turbine_parts = new LinkedHashMap<>();

	/**
	 * The thirty-six letter signs, keyed by their character. Empty when
	 * {@code features.letterSigns} is off.
	 *
	 * <p>A map rather than thirty-six fields, because nothing dereferences one by name — they are
	 * looked up by letter or iterated, which is how upstream holds them too
	 * ({@code AddonBlocks.LetterSigns.signs}).
	 */
	public static final Map<String, DeferredBlock<SignBlock>> letter_signs = new LinkedHashMap<>();

	/**
	 * The fifty-five lined asphalts, keyed by their line set ({@code x}, {@code nw},
	 * {@code xznswe}, …). Empty when {@code features.linedAsphalt} is off.
	 *
	 * <p>Upstream's {@code AddonBlocks.LinedAsphalts.blocks}, and like upstream's it also holds
	 * the <b>empty</b> key, mapped to the base mod's plain asphalt. That entry is what makes
	 * "the painted block for this set" a total function: a set with no segments is a real block,
	 * it just is not one of this addon's.
	 */
	public static final Map<String, DeferredBlock<? extends Block>> lined_asphalt = new LinkedHashMap<>();

	/**
	 * Upstream's {@code AsphaltLine.getBlock}: the block drawing {@code lines}, or null.
	 *
	 * <p>Eight sets are folded onto shorter ones that draw the same picture, so the lookup
	 * redirects before it reads the map — see {@code REPLACEMENTS} below. Returns null for a set
	 * nothing draws, and for every set at all when the feature is off, which is what stops a
	 * disabled feature being reachable through a dye (D-1002).
	 */
	public static Block linedAsphalt(String lines) {
		String canonical = REPLACEMENTS.getOrDefault(lines, lines);
		DeferredBlock<? extends Block> block = lined_asphalt.get(canonical);
		return block == null ? null : block.get();
	}

	/**
	 * Upstream's {@code LinedAsphalts.replacementMap}, verbatim — and its comment, "i'm too
	 * stupid to make this an algorithm", is fair: each of these eight sets draws the same
	 * picture as its value, because a full line through the block already covers the half lines
	 * on both sides of it. No block is registered for a key, so the lookup redirects instead.
	 *
	 * <p>Kept in step with a generator script by a GameTest that reads
	 * the generator's own manifest, not by discipline.
	 */
	private static final Map<String, String> REPLACEMENTS = Map.of(
			"xzns", "xns",
			"xzwe", "zwe",
			"xnswe", "xznswe",
			"znswe", "xznswe",
			"xznse", "xnse",
			"xzswe", "zswe",
			"xznwe", "znwe",
			"xznsw", "xnsw");

	/**
	 * Upstream's {@code idMap}, in its own {@code put} order: the base mod's block name, the
	 * short name a mixed block is called by, and <b>the texture in this repo</b>.
	 *
	 * <p>Two of the three columns differ from upstream and both were traps.
	 * 1.12.2's {@code EnumDyeColor.SILVER.getName()} is {@code "silver"}, not the modern
	 * {@code light_gray}; and {@code ducrete_brick} is NTM-CE's name for a texture 1.7.10 — and
	 * so this port — calls {@code brick_ducrete}. Kept in step with
	 * a generator script by a GameTest that reads its manifest.
	 */
	private static final String[][] CONCRETES = {
			{"brick_concrete", "brick", "brick_concrete"},
			{"brick_concrete_mossy", "brickm", "brick_concrete_mossy"},
			{"brick_concrete_cracked", "brickc", "brick_concrete_cracked"},
			{"brick_concrete_broken", "brickb", "brick_concrete_broken"},
			{"ducrete_brick", "ducrete", "brick_ducrete"},
			{"concrete", "raw", "concrete"},
			{"concrete_white", "white", "concrete_white"},
			{"concrete_orange", "orange", "concrete_orange"},
			{"concrete_magenta", "magenta", "concrete_magenta"},
			{"concrete_light_blue", "light_blue", "concrete_light_blue"},
			{"concrete_yellow", "yellow", "concrete_yellow"},
			{"concrete_lime", "lime", "concrete_lime"},
			{"concrete_pink", "pink", "concrete_pink"},
			{"concrete_gray", "gray", "concrete_gray"},
			{"concrete_silver", "silver", "concrete_silver"},
			{"concrete_cyan", "cyan", "concrete_cyan"},
			{"concrete_purple", "purple", "concrete_purple"},
			{"concrete_blue", "blue", "concrete_blue"},
			{"concrete_brown", "brown", "concrete_brown"},
			{"concrete_green", "green", "concrete_green"},
			{"concrete_red", "red", "concrete_red"},
			{"concrete_black", "black", "concrete_black"}};

	/**
	 * The 231 mixed concretes, keyed by block name. Empty when {@code features.mixedConcrete}
	 * is off.
	 */
	public static final Map<String, DeferredBlock<BlockMixedConcrete>> mixed_concrete = new LinkedHashMap<>();

	/**
	 * The high-pressure boiler. Null when {@code features.hpBoiler} is off.
	 *
	 * <p>Upstream registers it as {@code ngf_hpboiler}; the registry name here is
	 * {@code hp_boiler}, which is what its own lang key {@code tile.hp_boiler.name} already
	 * called it.
	 */
	public static DeferredBlock<HPBoilerBlock> hp_boiler;

	/** The radioisotope thermal heater (D-3020). Null when {@code features.rtg} is off. */
	public static DeferredBlock<com.leafia.contents.machines.heat.rtheater.HeaterRTGBlock> heater_rt;

	/**
	 * The Meteor Protection Beacon (D-3022), lit ({@code meteor_diverter}, no item) and waiting
	 * ({@code meteor_diverter_unlit}). Null when {@code features.meteorDiverter} is off.
	 */
	public static DeferredBlock<com.leafia.contents.miscellanous.diverter.DiverterBlock> diverter;
	public static DeferredBlock<com.leafia.contents.miscellanous.diverter.DiverterBlock> diverter_unlit;

	/** The pink door (D-3028), placed by {@code LeafiaItems.door_fuckoff}. Null when {@code features.pinkDoor} is off. */
	public static DeferredBlock<com.leafia.contents.building.pinkdoor.BlockPinkDoor> door_fuckoff;

	/** The Fat Chud (D-3022). Null when {@code features.addonBombs} is off. */
	public static DeferredBlock<com.leafia.contents.bomb.chud.NukeChudBlock> nuke_chud;

	/** The mixing vat (D-3016). Null unless {@code LeafiaFeatures.mixingVatMachine()}. Upstream's registry name, {@code mixingvat}. */
	public static DeferredBlock<MixingVatBlock> mixingvat;

	/** The coolant heat exchanger (D-3016). Null unless {@code LeafiaFeatures.coolantHeatexMachine()}. */
	public static DeferredBlock<CoolantHeatexBlock> coolant_heatex;

	/**
	 * The slop reactor's three blocks. Null when {@code features.slopReactor} is off.
	 *
	 * <p>The controller, the frame (and its corners), and the walls of the 5x5x5.
	 */
	public static DeferredBlock<SlopBlock> slop_reactor;
	public static DeferredBlock<Block> slop_reactor_casing;
	public static DeferredBlock<SlopGlass> slop_reactor_glass;

	/**
	 * The dark concrete bricks in their four finishes (D-1090). Null when {@code features.concreteBricks}
	 * is off. Upstream's one block {@code brick_concrete_dark} with four metas, as four blocks - see
	 * {@link ConcreteBricks}.
	 */
	public static DeferredBlock<ConcreteBricks> brick_concrete_dark;
	public static DeferredBlock<ConcreteBricks> brick_concrete_dark_mossy;
	public static DeferredBlock<ConcreteBricks> brick_concrete_dark_cracked;
	public static DeferredBlock<ConcreteBricks> brick_concrete_dark_broken;

	/**
	 * The Neutron Blaster. Null when {@code features.solBlaster} is off.
	 *
	 * <p>Upstream registers it as {@code sol_blaster}, in the machine tab.
	 */
	public static DeferredBlock<SolBlasterBlock> sol_blaster;

	/**
	 * The antimatter reaction chamber's three machines (D-3002). Null when {@code features.ams} is off.
	 *
	 * <p>Upstream registers them as {@code ams_base}, {@code ams_emitter} and {@code ams_limiter} in
	 * the machine tab, all {@code Material.IRON} with {@code setHardness(5.0F).setResistance(100.0F)} -
	 * 100 x 0.6 = 60 by D-012.
	 */
	public static DeferredBlock<AMSBaseBlock> ams_base;

	/**
	 * The absorption particle reactor (D-3027): upstream's {@code AddonBlocks.APR}. Null unless
	 * {@code LeafiaFeatures.aprReactor()}.
	 */
	public static DeferredBlock<com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreBlock> apr_core;
	public static DeferredBlock<com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOBlock> apr_port;
	public static DeferredBlock<com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock> apr_component;
	public static DeferredBlock<com.leafia.contents.machines.reactors.apr.blocks.outlet.APRPowerOutletBlock> apr_outlet;
	public static DeferredBlock<AMSEmitterBlock> ams_emitter;
	public static DeferredBlock<AMSStabilizerBlock> ams_limiter;

	/**
	 * The anti-mass spectrometer's two blocks (DFC-4, D-3017). Null when {@code features.amsp} is off.
	 *
	 * <p>Upstream: {@code AddonBlocks.Research.amsp_analyzer} and {@code amsp_receiver}, both
	 * {@code Material.IRON}, {@code setHardness(25.0F).setResistance(60)} in the machine tab - 60 x 0.6 = 36
	 * by D-012.
	 */
	public static DeferredBlock<com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerBlock> amsp_analyzer;
	public static DeferredBlock<com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverBlock> amsp_receiver;

	/**
	 * The addon PWR's fourteen blocks (D-3006). Null unless {@code LeafiaFeatures.pwrReactor()}. Upstream's
	 * {@code AddonBlocks.PWR}, by upstream's registry names - including the three older skins,
	 * {@code reactor_element}, {@code reactor_element_blank} and {@code reactor_control}, and the integrated
	 * boiler's {@code reactor_conductor}. PWR-2 added the terminal, the hatches and the wreckage (D-3010).
	 */
	public static DeferredBlock<PWRHullBlock> lwr_casing;
	public static DeferredBlock<PWRReflectorBlock> lwr_reflector;
	public static DeferredBlock<PWRElementBlock> lwr_element;
	public static DeferredBlock<PWRElementBlock> reactor_element;
	public static DeferredBlock<PWRElementBlock> reactor_element_blank;
	public static DeferredBlock<PWRControlBlock> lwr_control;
	public static DeferredBlock<PWRControlBlock> reactor_control;
	public static DeferredBlock<PWRChannelBlock> lwr_channel;
	public static DeferredBlock<PWRConductorBlock> reactor_conductor;
	public static DeferredBlock<PWRExchangerBlock> lwr_exchanger;
	public static DeferredBlock<PWRPortBlock> lwr_port;
	public static DeferredBlock<PWRComputerBlock> lwr_signal;
	public static DeferredBlock<PWROCCSInBlock> lwr_occs_in;
	public static DeferredBlock<PWROCCSOutBlock> lwr_occs_out;
	/** PWR-2 (D-3010): the control terminal and its two older skins, and the meltdown's two wreck blocks. */
	public static DeferredBlock<PWRTerminalBlock> lwr_terminal;
	public static DeferredBlock<PWRTerminalBlock> reactor_hatch;
	public static DeferredBlock<PWRTerminalBlock> reactor_hatch_alt;
	public static DeferredBlock<PWRWreckStone> lwrwreck_stone;
	public static DeferredBlock<PWRWreckMetal> lwrwreck_metal;

	/** The two osmiridium blocks. Null when {@code features.osmiridiumBlocks} is off. */
	public static DeferredBlock<OsmiridiumBlock> block_welded_osmiridium;
	public static DeferredBlock<OsmiridiumBlock> block_xanaxium;

	/** The two RealerSim fuel channels. Null when {@code features.realerSim} is off. */
	public static DeferredBlock<RBMKRealerSimBlock> rbmk_rod_realersim;
	public static DeferredBlock<RBMKRealerSimBlock> rbmk_rod_realersim_mod;

	/** Chernobylite and zetalite ore. Null when {@code features.coriumOres} is off. */
	public static DeferredBlock<CoriumOreBase> ore_corium_chernobylite;
	public static DeferredBlock<CoriumOreBase> ore_corium_zetalite;

	/** Oily coal ore and its burning form. Null when {@code features.oilyCoal} is off. */
	public static DeferredBlock<BlockCoalOil> ore_coal_oil;
	public static DeferredBlock<BlockCoalBurning> ore_coal_oil_burning;

	/** The broof. Null when {@code features.broof} is off. */
	public static DeferredBlock<BroofBlock> broof;

	/** The Custom Control Panel's desk block (D-3063). Null when {@code features.controlPanel} is off. */
	public static DeferredBlock<com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock> control_panel_custom;
	/** The control panel front, which upstream never registered (D-3063). Null when {@code features.controlPanel} is off. */
	public static DeferredBlock<com.leafia.contents.machines.controlpanel.core.block.ControlPanelFrontBlock> control_panel_front;

	/**
	 * The control panel torch (CCP-INSTRUMENTS-2, D-3072), lit ({@code control_torch}, no item) and unlit ({@code control_torch_unlit}):
	 * a panel's {@code torch_set_state} event swaps one for the other. Null when {@code features.controlPanel} is off.
	 */
	public static DeferredBlock<com.leafia.contents.machines.panel.controltorch.ControlTorchBlock> control_torch;
	public static DeferredBlock<com.leafia.contents.machines.panel.controltorch.ControlTorchBlock> control_torch_unlit;

	/** The storage rack (D-3021). Null when {@code features.rack} is off. */
	public static DeferredBlock<com.leafia.contents.building.storage.rack.RackBlock> sellacity_rack;

	/** The pneumatic tube regex filter. Null when {@code features.regexFilter} is off. */
	public static DeferredBlock<RegexFilterBlock> regex_filter;

	/** The catwalk railing. Null when {@code features.catwalks} is off. */
	public static DeferredBlock<CatwalkRailingCurved> catwalk_railing_curved;

	/**
	 * Baleonite, thermonuclear ash and digammitite: what balefire and digamma leave in the ground
	 * (D-3004). Upstream's field for baleonite is {@code baleonitite}; its registry name is
	 * {@code baleonite}, which is what is kept. Null when the feature that owns each is off.
	 */
	public static DeferredBlock<BaleoniteBlock> baleonite;
	public static DeferredBlock<AshBalefire> ash_balefire;
	public static DeferredBlock<DigammititeBlock> digammitite;

	/**
	 * The eleven contamination blocks fallout converts to - upstream's {@code AddonBlocks.LegacyBlocks},
	 * minus the two oily coals, which landed with their own feature (D-1063). Null when
	 * {@code features.legacyWaste} is off.
	 */
	public static DeferredBlock<LegacyBlockHazardMeta> waste_terracotta;
	public static DeferredBlock<LegacyWasteSand> waste_sand_red;
	public static DeferredBlock<LegacyBlockHazardMeta> waste_red_sandstone;
	public static DeferredBlock<LegacyWasteSand> waste_sand;
	public static DeferredBlock<LegacyBlockHazardMeta> waste_sandstone;
	public static DeferredBlock<LegacyWasteSand> waste_gravel;
	public static DeferredBlock<LegacyWasteEarth> waste_dirt;
	public static DeferredBlock<AddonBlockPowder> waste_snow;
	public static DeferredBlock<LegacyBlockHazardMeta> waste_snow_block;
	public static DeferredBlock<LegacyWasteIce> waste_ice;
	public static DeferredBlock<LegacyWasteEarth> scorched_earth;

	/**
	 * Every item of every metadata block, by block name, level 0 first: a block with seven finishes
	 * has seven items ({@code waste_dirt_0} to {@code waste_dirt_6}), as the base mod's multi blocks do.
	 */
	public static final Map<String, List<DeferredItem<ItemBlockBase.Variant<MetaLevel>>>> META_ITEMS = new LinkedHashMap<>();

	/** Upstream's character set, in upstream's order: the order decides nothing but the tab. */
	public static final String SIGN_LETTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	/**
	 * `elevator_shaft`, the rail an elevator car runs up. Null when {@code features.elevators} is
	 * off.
	 *
	 * <p>Upstream's {@code setHardness(15)} and no resistance call, so it keeps {@code Block}'s
	 * default resistance of zero — an odd pairing (tough to mine, free to blow up) but upstream's.
	 */
	public static DeferredBlock<EvShaftNeo> elevator_shaft;

	/**
	 * `elevator_light`, the invisible marker an elevator car leaves under itself to light the
	 * shaft. Null when {@code features.elevators} is off.
	 *
	 * <p>No {@code BlockItem} and no creative-tab entry, because upstream passes
	 * {@code setCreativeTab(null)}: the car places it and a player never holds one. Same shape as
	 * {@code light_emitter} above, and registered the same way — through {@code registerBlock}
	 * rather than {@code reg}, which is what adds the item and the tab entry.
	 */
	public static DeferredBlock<ElevatorLight> elevator_light;

	/**
	 * `elevator_pulley`, the winch at the head of a shaft. Null when {@code features.elevators} is
	 * off.
	 *
	 * <p>Upstream's {@code setHardness(15)} and no resistance call, as with every other elevator
	 * block — tough to mine, free to blow up.
	 */
	public static DeferredBlock<EvPulley> elevator_pulley;

	/** `elevator_buffer`, the spring at the bottom of a shaft. */
	public static DeferredBlock<EvBuffer> elevator_buffer;

	/**
	 * The two landings. <b>Upstream registers two blocks, not one</b> —
	 * {@code elevator_s6floor} and {@code elevator_skyliftfloor} — both plain {@link EvFloor} with
	 * no per-block difference in Java at all. What differs is the model each one draws, which is
	 * why they are two registrations rather than one block with a property: 1.12.2 hangs the model
	 * off the block, and so does 1.21.1.
	 */
	public static DeferredBlock<EvFloor> elevator_s6floor;
	public static DeferredBlock<EvFloor> elevator_skyliftfloor;

	/**
	 * Declares this jar's content. Called from the mod constructor <em>after</em>
	 * {@link LeafiaFeatures#refresh()}, so every switch below already holds the player's value
	 * rather than its default.
	 */
	public static void register(IEventBus modBus) {

		if(LeafiaFeatures.amatPipes) {
			amat_duct = reg("amat_duct", AmatDuctStandard::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion());

			amat_duct_charger = reg("amat_duct_charger", AmatDuctChargerBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.requiresCorrectToolForDrops()); // AddonBlockBaked -> AddonBlockBase's pickaxe, D-1072
		}

		if(LeafiaFeatures.ffDuct) {
			ff_duct = regVariants("ff_duct", FFDuctStandard::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(ModSoundTypes.pipe)
							.strength(5.0F, 6.0F)
							.noOcclusion()
							.requiresCorrectToolForDrops(),
					com.hbm.blocks.network.FluidDuctStandard.PipeMaterial.class);
			ff_duct_solid_shielded = reg("ff_duct_solid_shielded", FFDuctRadShielded::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(15.0F, 240.0F)
							.requiresCorrectToolForDrops());
			ff_pump = reg("ff_pump", FFPumpBlock::new, LeafiaCreativeTabs.MAIN, ffUtility());
			ff_converter = reg("ff_converter", FFConverterBlock::new, LeafiaCreativeTabs.MAIN, ffUtility());
			ff_filter = regCustomItem("ff_filter", FFFilterBlock::new, LeafiaCreativeTabs.MAIN, ffUtility());
		}

		if(LeafiaFeatures.lftrReactor()) {
			msr_element = reg("msr_element", MSRElementBlock::new, LeafiaCreativeTabs.MAIN, msr());
			msr_plug = reg("msr_plug", MSRPlugBlock::new, LeafiaCreativeTabs.MAIN, msr());
			msr_control = reg("msr_control", MSRControlBlock::new, LeafiaCreativeTabs.MAIN, msr());
			// CE's BlockPipe: not a full cube, drawn on the cutout layer.
			msr_control_extension = reg("msr_control_extension", MSRControlExtension::new, LeafiaCreativeTabs.MAIN, msr().noOcclusion());
			msr_arbitrary = reg("msr_arbitrary", MSRArbitraryBlock::new, LeafiaCreativeTabs.MAIN, msr());
			msr_ejector = reg("msr_ejector", MSREjectorBlock::new, LeafiaCreativeTabs.MAIN, msr());
			salt_separator = reg("salt_separator", SaltSeparatorBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 12.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)));
		}

		if(LeafiaFeatures.spkCable) {
			spk_cable = reg("spk_cable", SPKCableBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion());
		}

		if(LeafiaFeatures.dfcOverhaulContent()) {
			dfc_pulser = reg("dfc_pulser", p -> new CoreComponent(p, () -> LeafiaBlockEntities.DFC_PULSER.get(),
					(pos, state) -> new CoreDetonatorTE(LeafiaBlockEntities.DFC_PULSER.get(), pos, state)), LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.noOcclusion()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(5.0F, 30.0F)
							.requiresCorrectToolForDrops());
			dfc_reinforced = reg("dfc_reinforced", p -> new CoreComponent(p, () -> LeafiaBlockEntities.DFC_RECEIVER.get(),
					(pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE(LeafiaBlockEntities.DFC_RECEIVER.get(), pos, state)),
					LeafiaCreativeTabs.MAIN, dfcComponentProperties());
			dfc_exchanger = reg("dfc_exchanger", p -> new CoreComponent(p, () -> LeafiaBlockEntities.DFC_EXCHANGER.get(),
					(pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE(LeafiaBlockEntities.DFC_EXCHANGER.get(), pos, state)),
					LeafiaCreativeTabs.MAIN, dfcComponentProperties());
			dfc_cemitter = reg("dfc_cemitter", p -> new CoreComponent(p, () -> LeafiaBlockEntities.DFC_CEMITTER.get(),
					(pos, state) -> new com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterTE(LeafiaBlockEntities.DFC_CEMITTER.get(), pos, state)),
					LeafiaCreativeTabs.MAIN, dfcComponentProperties());
		}

		if(LeafiaFeatures.rbmkOverhaulContent()) {
			pribris_smoke = reg("pribris_smoke", com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmoke::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(50.0F, 3.6E10F)
							.noOcclusion()
							.requiresCorrectToolForDrops());
		}

		if(LeafiaFeatures.fluidDuctEquipment) {

			fluid_duct_valve_mdl = reg("fluid_duct_valve_mdl", FluidDuctValve::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion());

			fluid_duct_valve_mdl_rs = reg("fluid_duct_valve_mdl_rs", FluidDuctValveRS::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion());

			fluid_duct_gauge_mdl = reg("fluid_duct_gauge_mdl", FluidDuctGauge::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion());
		}

		if(LeafiaFeatures.doors) {

			reactor_door = reg("reactor_door",
					p -> new ReactorDoorBlock(p, AddonDoorDecl.REACTOR_DOOR, true), LeafiaCreativeTabs.MAIN,
					doorProperties(150.0F, 8100.0F));
			crimson_door_small = reg("crimson_door_small",
					p -> new AddonDoorGeneric(p, AddonDoorDecl.CRIMSON_DOOR_SMALL, true), LeafiaCreativeTabs.MAIN,
					doorProperties(150.0F, 81000.0F));
			crimson_door_large = reg("crimson_door_large",
					p -> new AddonDoorGeneric(p, AddonDoorDecl.CRIMSON_DOOR_LARGE, true), LeafiaCreativeTabs.MAIN,
					doorProperties(150.0F, 81000.0F));
		}

		if(LeafiaFeatures.lights) {

			light_unlit = reg("light_unlit", LightBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion()
							.lightLevel(state -> state.getValue(LightBlock.LIT) ? 15 : 0));

			/*
			 * No BlockItem and no creative-tab entry: upstream gives it neither, because it is
			 * placed by the fixture rather than by a player. Registering the block alone is the
			 * whole of it, which is why this does not go through reg().
			 */
			light_emitter = BLOCKS.registerBlock("light_emitter", LightEmitter::new,
					BlockBehaviour.Properties.of()
							.replaceable()
							.noCollission()
							.noLootTable()
							.air()
							.instabreak()
							.lightLevel(state -> 15));
		}

		if(LeafiaFeatures.debugBlocks) {

			test_fluid_voider = reg("test_fluid_voider", VoiderBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.instabreak()
							.requiresCorrectToolForDrops() // AddonBlockBase's pickaxe, D-1072
							.pushReaction(PushReaction.BLOCK));
			// D-3031: upstream's TestBlocks, in its order; the AddonBlockBase ones need AddonBlockBase's pickaxe (D-1072)
			test_boom = reg("test_boom", com.leafia.contents.debug.explosion_test.DebugBoomBlock::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops());
			test_rbmk_jet = reg("test_rbmk_jet", com.leafia.contents.debug.rbmk_jet.DebugRBMKJetEmitter::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops());
			test_blackhole_render = reg("test_blackhole_render", com.leafia.contents.debug.blackhole_test.DebugBHBlock::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops().noOcclusion());
			test_render = reg("test_render", com.leafia.contents.debug.render_test.DebugRenderTestBlock::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops().noOcclusion());
			test_tom = reg("test_tom", com.leafia.contents.debug.tom_test.TomTest::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops());
		}

		if(LeafiaFeatures.debugFFBlocks()) {

			// AddonBlockContainer: no harvest level, no creative tab (D-3031)
			test_ff_source = reg("test_ff_source", com.leafia.contents.debug.ff_test.source.DebugSourceBlock::new, null, debugProps());
			test_ff_tank = reg("test_ff_tank", com.leafia.contents.debug.ff_test.tank.DebugTankBlock::new, null, debugProps());
		}

		if(LeafiaFeatures.debugCircle()) {

			test_circle = reg("test_circle", com.leafia.contents.debug.circle_test.CircleTestBlock::new, LeafiaCreativeTabs.MAIN,
					debugProps().requiresCorrectToolForDrops());
		}

		if(LeafiaFeatures.turbines) {

			wind_turbine_medium = reg("wind_turbine_medium", WindTurbineMediumBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion()));
		}

		if(LeafiaFeatures.turbines) {
			deco_turbine = reg("deco_turbine", LeafiaCreativeTabs.MAIN, BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(5.0F, 6.0F)
					.requiresCorrectToolForDrops()); // a bare AddonBlockBase upstream, so its pickaxe, D-1072
		}

		if(LeafiaFeatures.modularTurbine()) {

			/*
			 * noSpawns because every turbine part is a multiblock (D-1028) - the core is one
			 * block, but upstream's three spawn overrides sit on the shared base and so cover it.
			 * noOcclusion because the model is a round housing, not a full cube.
			 */
			modular_turbine_core = reg("modular_turbine_core", MTCoreBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 30.0F)
							.noOcclusion()));

			modular_turbine_port = reg("modular_turbine_port", MTPowerPortBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F));

			/*
			 * ModularTurbineBlockBase's constructor: setHardness(5), setResistance(50), so 30.0F.
			 * Literal names rather than a loop so a generator script can read them.
			 */
			BlockBehaviour.Properties part = noSpawns(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(5.0F, 30.0F)
					.noOcclusion());
			/*
			 * The flywheels' collision depends on where the block sits in the disc, and vanilla
			 * caches a block's collision shape once per state - computed against an empty world,
			 * where findCore finds nothing - unless the block says its shape is dynamic. Movement
			 * asks live and was never affected; everything reading the cache (suffocation, mob
			 * pathing, the two-argument getCollisionShape) saw every flywheel block as empty.
			 * A separate Properties because the object is shared by reference with every block
			 * registered from it. D-1060.
			 */
			BlockBehaviour.Properties flywheel = noSpawns(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(5.0F, 30.0F)
					.noOcclusion()
					.dynamicShape());
			turbinePart(reg("modular_turbine_shaft", MTShaftBlock::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_2x2_blades", p -> new MTBlades2x2(p, "default"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_blades", p -> new MTBlades3x3(p, "default"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_blades", p -> new MTBlades5x5(p, "default"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_blades", p -> new MTBlades7x7(p, "default"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_blades", p -> new MTBlades9x9(p, "default"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_2x2_blades_glass", p -> new MTBlades2x2(p, "glass"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_blades_glass", p -> new MTBlades3x3(p, "glass"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_blades_glass", p -> new MTBlades5x5(p, "glass"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_blades_glass", p -> new MTBlades7x7(p, "glass"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_blades_glass", p -> new MTBlades9x9(p, "glass"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_2x2_blades_smooth", p -> new MTBlades2x2(p, "smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_blades_smooth", p -> new MTBlades3x3(p, "smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_blades_smooth", p -> new MTBlades5x5(p, "smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_blades_smooth", p -> new MTBlades7x7(p, "smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_blades_smooth", p -> new MTBlades9x9(p, "smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_2x2_blades_glass_smooth", p -> new MTBlades2x2(p, "glass_smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_blades_glass_smooth", p -> new MTBlades3x3(p, "glass_smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_blades_glass_smooth", p -> new MTBlades5x5(p, "glass_smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_blades_glass_smooth", p -> new MTBlades7x7(p, "glass_smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_blades_glass_smooth", p -> new MTBlades9x9(p, "glass_smooth"), LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_2x2_flywheel", MTFlywheel2x2::new, LeafiaCreativeTabs.MAIN, flywheel));
			turbinePart(reg("modular_turbine_3x3_flywheel", MTFlywheel3x3::new, LeafiaCreativeTabs.MAIN, flywheel));
			turbinePart(reg("modular_turbine_5x5_flywheel", MTFlywheel5x5::new, LeafiaCreativeTabs.MAIN, flywheel));
			turbinePart(reg("modular_turbine_7x7_flywheel", MTFlywheel7x7::new, LeafiaCreativeTabs.MAIN, flywheel));
			turbinePart(reg("modular_turbine_9x9_flywheel", MTFlywheel9x9::new, LeafiaCreativeTabs.MAIN, flywheel));
			turbinePart(reg("modular_turbine_3x3_separator", MTSeparator3x3::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_separator", MTSeparator5x5::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_separator", MTSeparator7x7::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_separator", MTSeparator9x9::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_port_top", MTPort3x3Top::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_port_top", MTPort5x5Top::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_port_top", MTPort7x7Top::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_port_top", MTPort9x9Top::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_port_side", MTPort3x3Side::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_port_side", MTPort5x5Side::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_port_side", MTPort7x7Side::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_port_side", MTPort9x9Side::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_port_inline", MTPort3x3Inline::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_5x5_port_inline", MTPort5x5Inline::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_7x7_port_inline", MTPort7x7Inline::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_9x9_port_inline", MTPort9x9Inline::new, LeafiaCreativeTabs.MAIN, part));
			turbinePart(reg("modular_turbine_3x3_generator", MTGenerator3x3::new, LeafiaCreativeTabs.MAIN, part));
		}

		if(LeafiaFeatures.letterSigns) {

			for(int i = 0; i < SIGN_LETTERS.length(); i++) {

				String letter = SIGN_LETTERS.substring(i, i + 1);

				letter_signs.put(letter, reg("letter_sign_" + letter.toLowerCase(),
						props -> new SignBlock(props, letter), LeafiaCreativeTabs.MAIN,
						BlockBehaviour.Properties.of()
								.mapColor(MapColor.METAL)
								.sound(SoundType.METAL)
								.strength(5.0F)
								.requiresCorrectToolForDrops() // SignBlock's own setHarvestLevel("pickaxe", 0), D-1072
								.noOcclusion()
								.noCollission()
								.pushReaction(PushReaction.DESTROY)));
			}
		}

		if(LeafiaFeatures.catwalks) {

			/*
			 * Upstream: `new CatwalkRailingCurved("catwalk_railing_curved")`, which is
			 * `super(Material.IRON, s)` and nothing else. Neither the block, nor
			 * CatwalkRailingBase, nor AddonBlockBase calls setHardness, setResistance or
			 * setSoundType, so all three are 1.12.2 Block's defaults: 0, 0 and SoundType.STONE.
			 * Checked up the whole chain rather than at the call site, which is D-945's rule
			 * and the reason the sound here is STONE and not the METAL the material suggests.
			 *
			 * Zero strength means a railing breaks instantly and any blast erases it. That is
			 * what upstream ships.
			 *
			 * AddonBlockBase's setHarvestLevel("pickaxe", 0) is honoured since D-1072: a hand
			 * breaks it instantly and gets nothing back, a pickaxe gets the railing.
			 */
			catwalk_railing_curved = regCustomItem("catwalk_railing_curved", CatwalkRailingCurved::new,
					LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(0.0F, 0.0F)
							.requiresCorrectToolForDrops()
							.noOcclusion()
							// Thin open geometry: light and sight go straight through it, and
							// without this a railing darkens the block it stands on.
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false));
		}

		if(LeafiaFeatures.linedAsphalt) {

			/*
			 * Upstream's six nested loops over the bits x z n s w e, in upstream's order. 2^6 is
			 * 64; the empty set is the base mod's own asphalt and eight more draw a picture a
			 * shorter set already draws, so 55 blocks are registered.
			 *
			 * The numbers come from the REGISTRATION, not the class: LinedAsphaltBlock's
			 * constructor says setResistance(CONCRETE.v) and the registration says
			 * setResistance(CONCRETE.half()), and in 1.12.2 the later call wins. Reading only
			 * the constructor would have doubled every one of these.
			 *
			 * CONCRETE is 84, and the enum multiplies by 5/3 precisely so 1.12.2's own 3/5 gives
			 * the number back - so half() is 42 explosion resistance here, exactly 84/2.
			 */
			lined_asphalt.put("", ModBlocks.asphalt);
			for(int x = 0; x < 2; x++)
			for(int z = 0; z < 2; z++)
			for(int n = 0; n < 2; n++)
			for(int s = 0; s < 2; s++)
			for(int w = 0; w < 2; w++)
			for(int e = 0; e < 2; e++) {

				StringBuilder builder = new StringBuilder();
				if(x == 1) builder.append('x');
				if(z == 1) builder.append('z');
				if(n == 1) builder.append('n');
				if(s == 1) builder.append('s');
				if(w == 1) builder.append('w');
				if(e == 1) builder.append('e');
				String lines = builder.toString();

				if(lines.isEmpty() || REPLACEMENTS.containsKey(lines)) continue;

				lined_asphalt.put(lines, reg("asphalt_lined_" + lines,
						props -> new LinedAsphaltBlock(props, lines), LeafiaCreativeTabs.MAIN,
						BlockBehaviour.Properties.of()
								.mapColor(MapColor.STONE)
								.sound(SoundType.STONE)
								.strength(15.0F, 42.0F)));
			}
		}

		if(LeafiaFeatures.hpBoiler) {

			/*
			 * Upstream: Material.IRON with AddonBlockDummyable's defaults - setHardness(5.0F)
			 * and setResistance(10.0F), which is what every LCE machine block inherits - 6.0F
			 * here by D-012's x0.6 (D-1070: this carried the raw 10 until then). noSpawns
			 * because it is a multiblock (D-1028), and noOcclusion because the block itself is
			 * invisible: the whole machine is drawn by HPBoilerRender.
			 */
			hp_boiler = reg("hp_boiler", HPBoilerBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)));
		}

		if(LeafiaFeatures.meteorDiverter) {

			/*
			 * Upstream (AddonBlocks): new DiverterBlock("meteor_diverter", true).setCreativeTab(null).setLightLevel(0.5F)
			 * and new DiverterBlock("meteor_diverter_unlit", false) - AddonBlockBase(Material.ROCK) with no hardness,
			 * so instant, but a pickaxe to drop it (setHarvestLevel("pickaxe", 0); D-1248). Light 0.5 is level 7.
			 * No collision (NULL_AABB), cutout, and the lit one has no item: both pick and drop the unlit.
			 */
			diverter = BLOCKS.registerBlock("meteor_diverter", p -> new com.leafia.contents.miscellanous.diverter.DiverterBlock(p, true),
					diverterProperties().lightLevel(state -> 7));
			diverter_unlit = reg("meteor_diverter_unlit", p -> new com.leafia.contents.miscellanous.diverter.DiverterBlock(p, false),
					LeafiaCreativeTabs.MAIN, diverterProperties());
		}

		if(LeafiaFeatures.pinkDoor) {

			/*
			 * Upstream (AddonBlocks): new BlockPinkDoor(Material.WOOD, "door_fuckoff").setHardness(3), no creative tab, its
			 * sound set to wood in its constructor. Material.WOOD: no tool needed, an axe is faster, a piston breaks it, and
			 * setHardness(3) leaves a resistance of 3. No block item: the door item of the same name places it (D-015).
			 */
			door_fuckoff = BLOCKS.registerBlock("door_fuckoff", com.leafia.contents.building.pinkdoor.BlockPinkDoor::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(3.0F)
							.noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
		}

		if(LeafiaFeatures.addonBombs) {

			/*
			 * Upstream (AddonBlocks): new NukeChudBlock(Material.IRON, "nuke_chud").setCreativeTab(nukeTab)
			 * .setHardness(5.0F).setResistance(6000.0F) - 3,600 by D-012's x0.6; Material.IRON needs a pickaxe to
			 * drop it (D-1248), and AddonBlockBase sets no sound, so stone. Drawn by its renderer.
			 */
			nuke_chud = reg("nuke_chud", com.leafia.contents.bomb.chud.NukeChudBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(5.0F, 3600.0F)
							.requiresCorrectToolForDrops()
							.noOcclusion());
		}

		if(LeafiaFeatures.rtg) {

			/*
			 * Upstream (AddonBlocks): new HeaterRTGBlock(Material.IRON, "heater_rt").setHardness(5.0F)
			 * .setResistance(10.0F).setCreativeTab(machineTab) - 6.0F by D-012's x0.6, the hp_boiler's
			 * properties for the same reasons (a multiblock drawn whole by its renderer, D-3020).
			 */
			heater_rt = reg("heater_rt", com.leafia.contents.machines.heat.rtheater.HeaterRTGBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(5.0F, 6.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)));
		}

		/*
		 * Upstream (AddonBlocks): MixingVatBlock and CoolantHeatexBlock, both Material.IRON with
		 * setHardness(5.0F).setResistance(10.0F) - the multiblock defaults every machine inherits - which is 6.0F
		 * of resistance here by D-012's x0.6. Material.IRON needs no tool. noSpawns because they are multiblocks
		 * (D-1028), and noOcclusion because the blocks themselves are invisible: each is drawn by its renderer.
		 */
		if(LeafiaFeatures.mixingVatMachine()) {
			mixingvat = reg("mixingvat", MixingVatBlock::new, LeafiaCreativeTabs.MAIN, machine());
		}

		if(LeafiaFeatures.coolantHeatexMachine()) {
			coolant_heatex = reg("coolant_heatex", CoolantHeatexBlock::new, LeafiaCreativeTabs.MAIN, machine());
		}

		if(LeafiaFeatures.slopReactor) {

			/*
			 * Upstream (AddonBlocks): SlopBlock on BlockMachineBase, AddonBlockBase for the casing and
			 * SlopGlass on BlockNTMGlass - all three Material.IRON, none with a setHardness or a
			 * setResistance. So none has either: 0 and 0, which D-012 reads as the destroy time and
			 * the blast resistance of a block that breaks at a touch and survives nothing. Stone's
			 * sound is 1.12.2 Block's default, none of the three bases overriding it.
			 *
			 * Only the casing needs a pickaxe: AddonBlockBase's setHarvestLevel("pickaxe", 0), the
			 * flag and the pickaxe-tag row together (D-1072). BlockMachineBase and BlockNTMGlass set
			 * no harvest level, and the addon follows the base mod's convention of requiring a tool
			 * only where upstream asked for one.
			 *
			 * The glass must not occlude, or the cube would hide its own inside, and is not a place a
			 * mob can spawn. Its light opacity of 0 is TransparentBlock's.
			 */
			slop_reactor = reg("slop_reactor", SlopBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(0.0F, 0.0F));
			slop_reactor_casing = reg("slop_reactor_casing", LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(0.0F, 0.0F)
							.requiresCorrectToolForDrops());
			slop_reactor_glass = reg("slop_reactor_glass", SlopGlass::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(0.0F, 0.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)
							.isRedstoneConductor((state, level, pos) -> false)));
		}

		if(LeafiaFeatures.solBlaster) {

			/*
			 * Upstream: SolBlasterBlock(Material.IRON, "sol_blaster").setHardness(15.0F).setResistance(160.0F),
			 * in the machine tab - 160 x 0.6 = 96 by D-012. The step sound is the one AddonBlockDummyable's
			 * base gives every machine, which the boiler records as metal. It needs no pickaxe: neither
			 * AddonBlockDummyable nor SolBlasterBlock calls setHarvestLevel (D-1072). noSpawns because it
			 * is a multiblock, noOcclusion because the block is invisible and HPBoilerRender-style
			 * drawing is the renderer's: SolBlasterRender.
			 */
			sol_blaster = reg("sol_blaster", SolBlasterBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(15.0F, 96.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)));
		}

		if(LeafiaFeatures.ams) {

			/*
			 * Upstream: AMSBaseBlock/AMSEmitterBlock/AMSStabilizerBlock(Material.IRON, ...)
			 * .setHardness(5.0F).setResistance(100.0F), in the machine tab. The step sound is the one
			 * AddonBlockDummyable gives every machine, which the boiler records as metal; no tool
			 * is required (D-1072). noSpawns because they are multiblocks (D-1028) and noOcclusion
			 * because the blocks are invisible: the renderers draw them.
			 */
			ams_base = reg("ams_base", AMSBaseBlock::new, LeafiaCreativeTabs.MAIN, amsProperties());
			ams_emitter = reg("ams_emitter", AMSEmitterBlock::new, LeafiaCreativeTabs.MAIN, amsProperties());
			ams_limiter = reg("ams_limiter", AMSStabilizerBlock::new, LeafiaCreativeTabs.MAIN, amsProperties());
		}

		if(LeafiaFeatures.aprReactor()) {

			/*
			 * Upstream: APRCoreBlock(Material.IRON).setHardness(5).setResistance(30), the other three
			 * setHardness(5) alone, which 1.12.2 makes a resistance of 25 (hardness x 5): 18 and 5 here
			 * (x 0.6, D-012). All in the machine tab and metal-sounding. The three AddonBlockBase blocks
			 * need a pickaxe (its setHarvestLevel); the core is an AddonBlockDummyable, which sets none, so
			 * no tool, as the AMC's (D-1072). The core is drawn by its renderer, so noSpawns and
			 * noOcclusion; the component's canCreatureSpawn false is noSpawns too.
			 */
			apr_core = reg("apr_core", com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0F, 18.0F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)));
			apr_port = reg("apr_port", com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0F, 5.0F).requiresCorrectToolForDrops());
			apr_component = regVariants("apr_component", com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0F, 5.0F).requiresCorrectToolForDrops()),
					com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock.APRComponentType.class);
			apr_outlet = reg("apr_outlet", com.leafia.contents.machines.reactors.apr.blocks.outlet.APRPowerOutletBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0F, 5.0F).requiresCorrectToolForDrops());
		}

		if(LeafiaFeatures.amsp) {

			/*
			 * Upstream's AddonBlocks.Research: Material.IRON, hardness 25, resistance 60 x 0.6. The same
			 * kind of block as the AMC's three (AddonBlockDummyable: metal, no tool, no spawns, invisible
			 * because the renderer draws it).
			 */
			amsp_analyzer = reg("amsp_analyzer", com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerBlock::new,
					LeafiaCreativeTabs.MAIN, amspProperties());
			amsp_receiver = reg("amsp_receiver", com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverBlock::new,
					LeafiaCreativeTabs.MAIN, amspProperties());
		}

		if(LeafiaFeatures.pwrReactor()) {

			/*
			 * Upstream's AddonBlocks.PWR. generalHardness 24, innerHardness 8; PWR_CASING and PWR_INSIDE
			 * are GenericBlockResistance(165) and (27), whose constructor multiplies by 5/3 so that 1.12.2's
			 * x3/5 gives the literal back: 165 and 27 here (D-012). The port, control port and OCCS take
			 * PWR_CASING.v (275) as their *hardness* too, upstream's choice. The old skins are 5 and 10 x 0.6.
			 * Every block is Material.IRON, which needed a pickaxe in 1.12.2 (D-1074's reading), so each
			 * requires one, with a tag row. Sounds: the hull and the ports set none
			 * (stone); the reflector, exchanger and controls set metal; the fuel and channels set
			 * soundTypePWRTube - pipe placed, metal block broken and stepped on, volume 0.5.
			 */
			SoundType tube = ModSoundType.placeBreakStep(NtmSounds.BLOCK_PIPE_PLACED, NtmSounds.STEP_METAL_BLOCK, NtmSounds.STEP_METAL_BLOCK, 0.5F, 1.0F);
			lwr_casing = reg("lwr_casing", PWRHullBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 24F, 165F));
			lwr_reflector = reg("lwr_reflector", PWRReflectorBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.METAL, 24F, 165F));
			lwr_element = reg("lwr_element", PWRElementBlock::new, LeafiaCreativeTabs.MAIN, pwr(tube, 8F, 27F));
			reactor_element = reg("reactor_element", PWRElementBlock::new, LeafiaCreativeTabs.MAIN, pwr(tube, 5F, 6F));
			reactor_element_blank = reg("reactor_element_blank", PWRElementBlock::new, LeafiaCreativeTabs.MAIN, pwr(tube, 5F, 6F));
			lwr_control = reg("lwr_control", PWRControlBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.METAL, 8F, 27F).noOcclusion());
			reactor_control = reg("reactor_control", PWRControlBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.METAL, 5F, 6F).noOcclusion());
			lwr_channel = reg("lwr_channel", PWRChannelBlock::new, LeafiaCreativeTabs.MAIN, pwr(tube, 8F, 27F));
			reactor_conductor = reg("reactor_conductor", PWRConductorBlock::new, LeafiaCreativeTabs.MAIN, pwr(tube, 8F, 27F));
			lwr_exchanger = reg("lwr_exchanger", PWRExchangerBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.METAL, 8F, 27F));
			lwr_port = reg("lwr_port", PWRPortBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 275F, 165F));
			lwr_signal = reg("lwr_signal", PWRComputerBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 275F, 165F));
			lwr_occs_in = reg("lwr_occs_in", PWROCCSInBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 275F, 165F));
			lwr_occs_out = reg("lwr_occs_out", PWROCCSOutBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 275F, 165F));

			/*
			 * PWR-2 (D-3010). The terminal is setHardness(PWR_CASING.v).setResistance(PWR_CASING.v) - 275 and
			 * 165, as the port; the hatches setHardness(5.0F).setResistance(CONCRETE_BRICKS.v), whose
			 * GenericBlockResistance(96) reads back 96. All three are a BlockMachineBase(Material.IRON), stone
			 * to the ear. The wrecks are an AddonBlockBase(Material.IRON, sound) with setHardness(5), which
			 * leaves 1.12.2's resistance at 5 (D-012), hidden from the creative tab; dynamic shape because
			 * the collision boxes are the block entity's variation's. No loot table: their drops are read off
			 * the block entity (PWRMeshedWreck.getDrops).
			 */
			lwr_terminal = reg("lwr_terminal", PWRTerminalBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 275F, 165F));
			reactor_hatch = reg("reactor_hatch", PWRTerminalBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 5F, 96F));
			reactor_hatch_alt = reg("reactor_hatch_alt", PWRTerminalBlock::new, LeafiaCreativeTabs.MAIN, pwr(SoundType.STONE, 5F, 96F));
			lwrwreck_stone = reg("lwrwreck_stone", PWRWreckStone::new, null,
					pwr(SoundType.STONE, PWRMeshedWreck.HARDNESS, PWRMeshedWreck.HARDNESS).noOcclusion().dynamicShape().noLootTable());
			lwrwreck_metal = reg("lwrwreck_metal", PWRWreckMetal::new, null,
					pwr(SoundType.METAL, PWRMeshedWreck.HARDNESS, PWRMeshedWreck.HARDNESS).noOcclusion().dynamicShape().noLootTable());
		}

		if(LeafiaFeatures.osmiridiumBlocks) {

			/*
			 * Upstream: new OsmiridiumBlock(Material.IRON, ...).setHardness(15).setResistance(6500000),
			 * both. D-012: 6,500,000 x 0.6 = 3,900,000. Material.IRON is a metal map colour; the
			 * sound is 1.12.2 Block's default, stone. AddonBlockBase's setHarvestLevel("pickaxe", 0)
			 * is honoured - the addon has a pickaxe tag since D-1063, which is what D-1026 said the
			 * requirement was waiting for (D-1071).
			 */
			BlockBehaviour.Properties osmiridium = BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.STONE)
					.strength(15.0F, 3_900_000.0F)
					.requiresCorrectToolForDrops()
					.randomTicks(); // upstream's needsRandomTick: the digamma (D-1073)
			block_welded_osmiridium = reg("block_welded_osmiridium", OsmiridiumBlock::new, LeafiaCreativeTabs.MAIN, osmiridium);
			block_xanaxium = reg("block_xanaxium", OsmiridiumBlock::new, LeafiaCreativeTabs.MAIN, osmiridium);
		}

		if(LeafiaFeatures.concreteBricks) {

			/*
			 * Upstream: new ConcreteBricks(Material.ROCK, "brick_concrete_dark", ...).setHardness(15.0F)
			 * .setResistance(160.0F), the same for every meta. D-012: 160 x 0.6 = 96. Material.ROCK is a
			 * stone map colour, and the sound is 1.12.2 Block's default, stone. AddonBlockBase's
			 * setHarvestLevel("pickaxe", 0) is honoured, flag and tag row together (D-1072).
			 */
			BlockBehaviour.Properties bricks = BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.sound(SoundType.STONE)
					.strength(ConcreteBricks.HARDNESS, ConcreteBricks.RESISTANCE)
					.requiresCorrectToolForDrops();
			brick_concrete_dark = reg("brick_concrete_dark", p -> new ConcreteBricks(p, ConcreteBricks.Type.NORMAL), LeafiaCreativeTabs.MAIN, bricks);
			brick_concrete_dark_mossy = reg("brick_concrete_dark_mossy", p -> new ConcreteBricks(p, ConcreteBricks.Type.MOSSY), LeafiaCreativeTabs.MAIN, bricks);
			brick_concrete_dark_cracked = reg("brick_concrete_dark_cracked", p -> new ConcreteBricks(p, ConcreteBricks.Type.CRACKED), LeafiaCreativeTabs.MAIN, bricks);
			brick_concrete_dark_broken = reg("brick_concrete_dark_broken", p -> new ConcreteBricks(p, ConcreteBricks.Type.BROKEN), LeafiaCreativeTabs.MAIN, bricks);
		}

		if(LeafiaFeatures.realerSim) {

			/*
			 * Upstream: new RBMKRealerSimBlock(moderated, ...).setHardness(15 or 5).setResistance(100)
			 * - the moderated one is the softer, upstream's numbers. D-012: 100 x 0.6 = 60.
			 * RBMKBase's Material.IRON is a metal map colour, and in 1.12.2 a material that
			 * setRequiresTool()s: a bare hand drops nothing, any pickaxe drops it - the same reading
			 * D-1063 gave oily coal's Material.ROCK. The sound is 1.12.2 Block's default, stone.
			 * The base mod's own RBMK columns drop to a bare hand; that is its convention, not
			 * upstream's, and is noted in D-1074 rather than copied.
			 */
			rbmk_rod_realersim = reg("rbmk_rod_realersim", p -> new RBMKRealerSimBlock(p, false), LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.STONE).strength(15.0F, 60.0F)
							.requiresCorrectToolForDrops().noOcclusion());
			rbmk_rod_realersim_mod = reg("rbmk_rod_realersim_mod", p -> new RBMKRealerSimBlock(p, true), LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.STONE).strength(5.0F, 60.0F)
							.requiresCorrectToolForDrops().noOcclusion());
		}

		if(LeafiaFeatures.coriumOres) {

			/*
			 * Upstream (AddonBlocks.Ores): new CoriumOreBase(name, harvestLevel, texture) with
			 * setHardness(20) / setResistance(9000) for chernobylite at harvest level 2, and
			 * setHardness(50) / setResistance(9000) for zetalite at level 3. D-012: 9000 x 0.6 =
			 * 5400. BlockOre passes Material.ROCK, so stone's map colour and sound, and a tool is
			 * required: the harvest level is a needs_iron_tool / needs_diamond_tool tag (written by
			 * a generator script) beside the pickaxe tag. randomTicks() is upstream's
			 * setTickRandomly(true); AddonOreBase's setTickRandomly(false) is overridden by it.
			 */
			ore_corium_chernobylite = reg("ore_corium_chernobylite", CoriumOreBase::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.STONE)
							.sound(SoundType.STONE)
							.strength(20.0F, 5_400.0F)
							.requiresCorrectToolForDrops()
							.randomTicks());
			ore_corium_zetalite = reg("ore_corium_zetalite", CoriumOreBase::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.STONE)
							.sound(SoundType.STONE)
							.strength(50.0F, 5_400.0F)
							.requiresCorrectToolForDrops()
							.randomTicks());
		}

		if(LeafiaFeatures.oilyCoal) {

			/*
			 * Upstream: two BlockOres in AddonBlocks.LegacyBlocks, each with setHardness(5.0F) and
			 * setResistance(15.0F) at the registration - which is 9.0F here by the 3/5 every
			 * resistance in this port goes through (D-1070 corrects D-1063, which used 15 raw);
			 * the burning one adds setLightLevel(10F/15F),
			 * which is light 10. Material.ROCK is what BlockOre passes, so stone's map colour and
			 * sound, and a pickaxe to drop anything (the block's tag and the requirement go
			 * together, D-1063). The burning ore has noLootTable(): nothing reaches its drops -
			 * see BlockCoalBurning.
			 */
			ore_coal_oil = reg("ore_coal_oil", BlockCoalOil::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.STONE)
							.sound(SoundType.STONE)
							.strength(5.0F, 9.0F)
							.requiresCorrectToolForDrops());
			ore_coal_oil_burning = reg("ore_coal_oil_burning", BlockCoalBurning::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.STONE)
							.sound(SoundType.STONE)
							.strength(5.0F, 9.0F)
							.lightLevel(state -> 10)
							.requiresCorrectToolForDrops()
							.noLootTable());
		}

		if(LeafiaFeatures.broof) {

			/*
			 * Upstream: Material.CARPET, setHardness(0.1F), setSoundType(SoundType.CLOTH), and
			 * no setResistance. That is not a resistance of 0: 1.12.2's setHardness raises the
			 * stored resistance to hardness x 5, which reads back as the hardness, so 0.1 - D-012's
			 * rule, which D-1070 found this line had missed. Cloth tears at a touch and almost
			 * any blast takes it.
			 *
			 * noSpawns because it is a multiblock (D-1028), and noOcclusion because the canopy
			 * must not darken the floor under it.
			 */
			broof = reg("broof", BroofBlock::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.WOOL)
							.sound(SoundType.WOOL)
							.strength(0.1F, 0.1F)
							.noOcclusion()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)));
		}

		if(LeafiaFeatures.controlPanel) {

			/*
			 * NTM-CE: new BlockControlPanel(Material.IRON, "control_panel_custom").setHardness(0.25F).setResistance(2.0F), in the
			 * block tab. 1.12.2 stores a resistance of 3x what it is given and divides by 5 when asked, so 2.0F is 1.2F here, and
			 * no setSoundType leaves the 1.12.2 default, stone. Not a full cube and drawn by its own block entity renderer
			 * (batch 5), so noOcclusion and a dynamic shape: the slab's height, slope and insets are per instance.
			 */
			control_panel_custom = reg("control_panel_custom", com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock::new,
					LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(0.25F, 1.2F)
							.noOcclusion()
							.dynamicShape()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)
							.noLootTable());

			/*
			 * The front: Material.IRON, setHardness(3F), setResistance(30F) in its commented-out constructor, so 3.0F and 18.0F.
			 * Registered with an item and no creative tab entry, because upstream's registry never had it (D-3063).
			 */
			control_panel_front = BLOCKS.registerBlock("control_panel_front",
					com.leafia.contents.machines.controlpanel.core.block.ControlPanelFrontBlock::new,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.STONE)
							.strength(3.0F, 18.0F)
							.noOcclusion()
							.dynamicShape()
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false)
							.noLootTable());
			BLOCK_ITEMS.registerSimpleBlockItem(control_panel_front);

			/*
			 * NTM-CE-era Leafia (AddonBlocks): new ControlTorchBlock("control_torch", true).setCreativeTab(null).setLightLevel(0.5F) and
			 * new ControlTorchBlock("control_torch_unlit", false) in the base mod's machine tab - a BlockTorch of Material.CIRCUITS
			 * with no hardness set (instant, no tool), the default stone sound, no collision, see-through. Light 0.5 is level 7.
			 * The lit one has no item: both pick and drop the unlit one.
			 */
			control_torch = BLOCKS.registerBlock("control_torch", p -> new com.leafia.contents.machines.panel.controltorch.ControlTorchBlock(p, true),
					controlTorchProperties().lightLevel(state -> 7));
			control_torch_unlit = reg("control_torch_unlit", p -> new com.leafia.contents.machines.panel.controltorch.ControlTorchBlock(p, false),
					LeafiaCreativeTabs.MAIN, controlTorchProperties());
		}

		if(LeafiaFeatures.rack) {

			/*
			 * Upstream (AddonBlocks): new RackBlock(Material.IRON, "sellacity_rack").setHardness(5.0F).setResistance(10.0F),
			 * the multiblock defaults, in the block tab. 6.0F of resistance here by D-012's x0.6; Material.IRON needs no
			 * tool; noSpawns because it is a multiblock (D-1028) and noOcclusion because RackRender draws it.
			 */
			sellacity_rack = reg("sellacity_rack", com.leafia.contents.building.storage.rack.RackBlock::new,
					LeafiaCreativeTabs.MAIN, machine());
		}

		if(LeafiaFeatures.mixedConcrete) {

			/*
			 * Upstream's `for i < j` double loop over idMap: every unordered pair, 231 of them.
			 *
			 * Upstream iterates a HashMap, so which member becomes A and which becomes B is
			 * Java's hash order - unreproducible, and different again the moment the map gains
			 * an entry. This iterates CONCRETES in upstream's own `put` order, which gives a
			 * stable set of names; some name the same pair as upstream with A and B swapped.
			 * There is no 1.12.2 world to stay compatible with. D-1050.
			 *
			 * The numbers come from the CLASS, not the registration: setHardness(15.0F) and
			 * setResistance(CONCRETE.v) - the FULL 84, where the lined asphalts' registration
			 * overrode the same constructor call with half() (D-1047). Read both, every time.
			 */
			for(int i = 0; i < CONCRETES.length - 1; i++)
			for(int j = i + 1; j < CONCRETES.length; j++) {

				String block = "concrete__mixed_" + CONCRETES[i][1] + "_" + CONCRETES[j][1];

				mixed_concrete.put(block, reg(block, BlockMixedConcrete::new, LeafiaCreativeTabs.MAIN,
						BlockBehaviour.Properties.of()
								.mapColor(MapColor.STONE)
								.sound(SoundType.STONE)
								.strength(15.0F, 84.0F)));
			}
		}

		if(LeafiaFeatures.elevators) {

			// Upstream: setHardness(15) and nothing else, so 15 and 15 by D-012 - not 0 (D-1070).
			elevator_shaft = reg("elevator_shaft", EvShaftNeo::new, LeafiaCreativeTabs.MAIN,
					noSpawns(BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(SoundType.METAL)
							.strength(15.0F, 15.0F)
							.requiresCorrectToolForDrops() // AddonBlockBase's pickaxe, D-1072
							.noOcclusion()));

			/*
			 * Upstream's setLightLevel(1) is 1.12.2's 0..1 float, so it is level 15 here. Every
			 * other line is one of the nine "be absent" overrides upstream writes by hand.
			 */
			elevator_light = BLOCKS.registerBlock("elevator_light", ElevatorLight::new,
					BlockBehaviour.Properties.of()
							.replaceable()
							.noCollission()
							.noLootTable()
							.air()
							.instabreak()
							.lightLevel(state -> 15));

			// All four take upstream's setHardness(15) and no setResistance - which in 1.12.2 is a
			// resistance of 15, not 0: setHardness raises the stored value to hardness x 5, and
			// D-012's rule reads that back as the hardness. D-1070 corrected these and the shaft,
			// which had been erased by any explosion. All four are multiblocks, so noSpawns (D-1028).
			BlockBehaviour.Properties machine = noSpawns(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(15.0F, 15.0F)
					.noOcclusion());

			/*
			 * The pulley and the buffer are drawn now - EvPulleyRender and EvBufferRender landed
			 * in D-1040 - and both have a composite item model over their OBJ parts, so both go
			 * in the tab. That is the whole change D-1039 said this would need.
			 *
			 * The two landings join them in D-1041, which ports EvFloorRender and the cabin's.
			 * All four elevator blocks are drawn now.
			 */
			elevator_pulley = reg("elevator_pulley", EvPulley::new, LeafiaCreativeTabs.MAIN, machine);
			elevator_buffer = reg("elevator_buffer", EvBuffer::new, LeafiaCreativeTabs.MAIN, machine);
			elevator_s6floor = reg("elevator_s6floor", EvFloor::new, LeafiaCreativeTabs.MAIN, machine);
			elevator_skyliftfloor = reg("elevator_skyliftfloor", EvFloor::new, LeafiaCreativeTabs.MAIN, machine);
		}

		if(LeafiaFeatures.regexFilter) {

			/*
			 * Upstream: Material.IRON, ModSoundTypes.pipe, setHardness(0.1F), setResistance(10.0F)
			 * - 6.0F here by D-012's x0.6 - and no harvest level. Material.IRON needed a pickaxe
			 * in 1.12.2 whatever the hardness, the same reason RBMKBase's columns get one (D-1074),
			 * so it is in the pickaxe tag and requires the correct tool for drops.
			 *
			 * noOcclusion and the three false predicates are upstream's isOpaqueCube, isFullCube,
			 * isNormalCube and isFullBlock overrides: it is an 8-pixel rod, not a cube.
			 */
			regex_filter = reg("regex_filter", RegexFilterBlock::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.METAL)
							.sound(ModSoundTypes.pipe)
							.strength(0.1F, 6.0F)
							.requiresCorrectToolForDrops()
							.noOcclusion()
							.isRedstoneConductor((state, level, pos) -> false)
							.isViewBlocking((state, level, pos) -> false)
							.isSuffocating((state, level, pos) -> false));
		}

		if(LeafiaFeatures.balefireResidue) {

			/*
			 * Upstream: BaleoniteBlock(Material.ROCK, SoundType.STONE, "baleonite").setHardness(5.0F)
			 * .setResistance(6F). D-012: 6 x 0.6 = 3.6. Material.ROCK needs a tool in 1.12.2, and
			 * needsRandomTick is randomTicks(). All six levels sit in the creative tab (BlockMeta's
			 * showMetaInCreative defaults to true).
			 */
			baleonite = regMeta("baleonite", BaleoniteBlock::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(5.0F, 3.6F)
							.requiresCorrectToolForDrops().randomTicks(),
					0, 5, true);

			/*
			 * Upstream: AshBalefire(Material.SAND, "ash_balefire", SoundType.SAND).setLightLevel(9F/12F)
			 * .setHardness(0.5F). 1.12.2 truncates 15 x 0.75 = 11.25 to light 11; hardness 0.5 reads back
			 * as a resistance of 0.5 (D-012). It ticks randomly, and it is not a full cube.
			 */
			ash_balefire = reg("ash_balefire", AshBalefire::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.SAND).strength(0.5F, 0.5F)
							.lightLevel(state -> 11).randomTicks().noOcclusion());
		}

		if(LeafiaFeatures.digammitite) {

			// Upstream: DigammititeBlock(Material.ROCK, SoundType.STONE, "digammitite").setHardness(5.0F).setResistance(6F).
			digammitite = regMeta("digammitite", DigammititeBlock::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(5.0F, 3.6F)
							.requiresCorrectToolForDrops().randomTicks(),
					0, 5, true);
		}

		if(LeafiaFeatures.legacyWaste) {

			/*
			 * Upstream's LegacyBlocks, in upstream's order, each with its own setHardness/setResistance.
			 * D-012: a resistance x 0.6, and a hardness with no resistance reads back as itself. The
			 * material decides the rest: ROCK and SNOW need a tool in 1.12.2 (the flag here and a row in
			 * the pickaxe or shovel tag), SAND and GROUND do not. Material.GROUND's step sound is
			 * SoundType.GROUND, which is 1.21.1's gravel.
			 *
			 * The ones with a LegacyBlockHazardMeta or a BlockMeta parent show all seven levels in the
			 * creative tab; the ones on WasteEarth/WasteSand/Block show level 0 only (scorched earth,
			 * whose levels start at 2, shows its first).
			 */
			waste_terracotta = regMeta("waste_terracotta", LegacyBlockHazardMeta::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE).strength(4.0F, 4.8F)
							.requiresCorrectToolForDrops(),
					0, 6, true);
			waste_sand_red = regMeta("waste_sand_red", LegacyWasteSand::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).sound(SoundType.SAND).strength(0.5F, 0.6F)
							.isValidSpawn(RadImmuneSpawn.PREDICATE),
					0, 6, false);
			waste_red_sandstone = regMeta("waste_red_sandstone", LegacyBlockHazardMeta::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).sound(SoundType.STONE).strength(3.0F, 3.6F)
							.requiresCorrectToolForDrops(),
					0, 6, true);
			waste_sand = regMeta("waste_sand", LegacyWasteSand::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.SAND).strength(0.5F, 0.6F)
							.isValidSpawn(RadImmuneSpawn.PREDICATE),
					0, 6, false);
			waste_sandstone = regMeta("waste_sandstone", LegacyBlockHazardMeta::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.STONE).strength(3.0F, 3.6F)
							.requiresCorrectToolForDrops(),
					0, 6, true);
			waste_gravel = regMeta("waste_gravel", LegacyWasteSand::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.GRAVEL).strength(0.5F, 0.6F)
							.isValidSpawn(RadImmuneSpawn.PREDICATE),
					0, 6, false);
			waste_dirt = regMeta("waste_dirt", LegacyWasteEarth::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).sound(SoundType.GRAVEL).strength(0.5F, 0.6F)
							.isValidSpawn(RadImmuneSpawn.PREDICATE),
					0, 6, false);
			waste_snow = regMeta("waste_snow", AddonBlockPowder::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).sound(SoundType.SNOW).strength(0.1F, 0.1F)
							.noOcclusion().replaceable().noLootTable().isValidSpawn(RadImmuneSpawn.PREDICATE),
					0, 6, false);
			waste_snow_block = regMeta("waste_snow_block", LegacyBlockHazardMeta::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).sound(SoundType.SNOW).strength(0.2F, 0.2F)
							.requiresCorrectToolForDrops(),
					0, 6, true);
			// BlockIce: slippery, translucent and random-ticking; setHardness(0.2F) reads back as a resistance of 0.2.
			waste_ice = reg("waste_ice", LegacyWasteIce::new, LeafiaCreativeTabs.MAIN,
					BlockBehaviour.Properties.of().mapColor(MapColor.ICE).sound(SoundType.GLASS).strength(0.2F, 0.2F)
							.friction(0.98F).randomTicks().noOcclusion().isRedstoneConductor((state, level, pos) -> false));
			// Its default state is level 2: it has no level 0 or 1 upstream, and its first item is level 2.
			scorched_earth = regMeta("scorched_earth", p -> new LegacyWasteEarth(p, MetaLevel.L2),
					BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).sound(SoundType.GRAVEL).strength(0.5F, 0.6F)
							.isValidSpawn(RadImmuneSpawn.PREDICATE),
					2, 6, false);
		}

		BLOCKS.register(modBus);
		BLOCK_ITEMS.register(modBus);
	}

	private static void turbinePart(DeferredBlock<? extends ModularTurbineBlockBase> block) {
		modular_turbine_parts.put(block.getId().getPath(), block);
	}

	/**
	 * "No mob may spawn on this" — upstream's three overrides on {@code AddonBlockDummyable},
	 * whose comment is <em>"crappers spawning on large machines pmo"</em>.
	 *
	 * <p>They are here rather than on a block base because 1.21.1 has no override point for it:
	 * spawning is a {@code BlockBehaviour.Properties} predicate, decided once when the block is
	 * built. Every addon multiblock should be registered through this. See D-1028.
	 */
	/** The properties of an invisible multiblock machine with upstream's 5 / 10: metal, no spawns, drawn by its renderer. */
	private static BlockBehaviour.Properties machine() {
		return noSpawns(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.METAL)
				.strength(5.0F, 6.0F)
				.noOcclusion()
				.isViewBlocking((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false));
	}

	/** The control panel torch's: circuits, instant, no tool, no collision, see-through, a pushed one breaks (D-3072). */
	private static BlockBehaviour.Properties controlTorchProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.NONE)
				.sound(SoundType.STONE)
				.instabreak()
				.noCollission()
				.noOcclusion()
				.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
	}

	/** The Meteor Protection Beacon's: rock, instant, a pickaxe to drop, no collision, see-through (D-3022). */
	private static BlockBehaviour.Properties diverterProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.STONE)
				.sound(SoundType.STONE)
				.instabreak()
				.requiresCorrectToolForDrops()
				.noCollission()
				.noOcclusion();
	}

	private static BlockBehaviour.Properties noSpawns(BlockBehaviour.Properties props) {
		return props.isValidSpawn((state, level, pos, type) -> false);
	}

	/** Upstream's {@code AddonCoreComponent(Material.IRON, ...).setHardness(5.0F).setResistance(50.0F)}, as {@code dfc_pulser}'s. */
	private static BlockBehaviour.Properties dfcComponentProperties() {
		return BlockBehaviour.Properties.of()
				.noOcclusion()
				.mapColor(MapColor.METAL)
				.sound(SoundType.STONE)
				.strength(5.0F, 30.0F)
				.requiresCorrectToolForDrops();
	}

	/** As {@link #reg(String, String, BlockBehaviour.Properties)}, for a block with its own class. */
	private static <T extends Block> DeferredBlock<T> reg(String name, Function<BlockBehaviour.Properties, T> factory,
			String tab, BlockBehaviour.Properties props) {
		DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, props);
		DeferredItem<BlockItem> item = BLOCK_ITEMS.registerSimpleBlockItem(block);
		LeafiaCreativeTabs.add(tab, item);
		return block;
	}

	/**
	 * As {@link #reg(String, Function, String, BlockBehaviour.Properties)}, for a block that
	 * supplies its own {@link BlockItem}.
	 *
	 * <p>{@link ICustomItemBlockProvider}'s first consumer in this port. Upstream's registrar
	 * calls {@code provideItem()} while walking its block list; here the item is a separate
	 * registry entry, so the call is deferred behind the supplier rather than made at
	 * registration time - the block does not exist yet when this runs.
	 */
	private static <T extends Block & ICustomItemBlockProvider> DeferredBlock<T> regCustomItem(
			String name, Function<BlockBehaviour.Properties, T> factory, String tab,
			BlockBehaviour.Properties props) {
		DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, props);
		DeferredItem<BlockItem> item = BLOCK_ITEMS.register(name, () -> block.get().provideItem());
		LeafiaCreativeTabs.add(tab, item);
		return block;
	}

	/**
	 * A block with upstream-metadata levels: one block carrying {@code meta}, and one item per level
	 * from {@code first} to {@code last} ({@code <name>_<level>}), each placing its own level and
	 * answering to {@code block.leafia.<name>.<level>}. See D-034 for the base mod's rule, which this
	 * is. {@code allInCreative} is BlockMeta's {@code showMetaInCreative}; otherwise only the first
	 * level is shown, which is what an unflagged 1.12.2 block's single item amounted to.
	 */
	private static <T extends Block & MetaBlock> DeferredBlock<T> regMeta(String name, Function<BlockBehaviour.Properties, T> factory,
			BlockBehaviour.Properties props, int first, int last, boolean allInCreative) {

		DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, props);
		List<DeferredItem<ItemBlockBase.Variant<MetaLevel>>> items = new ArrayList<>();

		for(int i = first; i <= last; i++) {
			MetaLevel level = MetaLevel.of(i);
			DeferredItem<ItemBlockBase.Variant<MetaLevel>> item = BLOCK_ITEMS.registerItem(
					IBlockMulti.variantItemName(name, level),
					p -> new ItemBlockBase.Variant<MetaLevel>(block.get(), p, level));
			items.add(item);
			if(allInCreative || i == first) LeafiaCreativeTabs.add(LeafiaCreativeTabs.MAIN, item);
		}

		META_ITEMS.put(name, items);
		return block;
	}

	/**
	 * What all three doors share: {@code Material.IRON}, so a pickaxe, and the stone step sound
	 * {@code BlockDummyable} leaves a 1.12.2 block with. Not suffocating or view-blocking, because a
	 * door's shape is its own and an open one is a doorway a player stands in; and a <em>dynamic</em>
	 * shape, because the collision box depends on the door's state in the world and
	 * {@code BlockState.getCollisionShape(level, pos)} answers from a cache built against an empty
	 * level unless the block says otherwise (D-3005).
	 */
	private static BlockBehaviour.Properties doorProperties(float hardness, float resistance) {
		return noSpawns(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.STONE)
				.strength(hardness, resistance)
				.noOcclusion()
				.requiresCorrectToolForDrops()
				.dynamicShape()
				.isViewBlocking((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false));
	}

	/** A PWR block: Material.IRON's metal colour, a pickaxe to drop, and upstream's sound and strength. */
	private static BlockBehaviour.Properties pwr(SoundType sound, float hardness, float resistance) {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(sound)
				.strength(hardness, resistance)
				.requiresCorrectToolForDrops();
	}

	/** A Forge-fluid duct utility: {@code Material.IRON}, 5 / 10 x 0.6, stone sound, a thin box that is no full cube. */
	private static BlockBehaviour.Properties ffUtility() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.STONE)
				.strength(5.0F, 6.0F)
				.noOcclusion()
				.isViewBlocking((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false)
				.requiresCorrectToolForDrops();
	}

	/** An MSR block: {@code Material.IRON}, upstream's {@code generalHardness} 12 for both numbers (D-012), stone sound. */
	private static BlockBehaviour.Properties msr() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.STONE)
				.strength(12.0F, 12.0F)
				.requiresCorrectToolForDrops();
	}

	/**
	 * A block with one item per value of an enum property, as the base mod's {@code regMulti}
	 * (D-034): {@code <name>_<variant>} items, each placing its own variant, all in the tab.
	 */
	private static <E extends Enum<E> & net.minecraft.util.StringRepresentable, T extends Block & IBlockMulti<E>> DeferredBlock<T> regVariants(
			String name, Function<BlockBehaviour.Properties, T> factory, String tab, BlockBehaviour.Properties props, Class<E> variants) {
		DeferredBlock<T> block = BLOCKS.registerBlock(name, factory, props);
		for(E variant : variants.getEnumConstants()) {
			DeferredItem<ItemBlockBase.Variant<E>> item = BLOCK_ITEMS.registerItem(
					IBlockMulti.variantItemName(name, variant),
					p -> new ItemBlockBase.Variant<E>(block.get(), p, variant));
			LeafiaCreativeTabs.add(tab, item);
		}
		return block;
	}

	/** What all three AMC machines share: hardness 5, resistance 100 x 0.6, metal, drawn by their renderers. */
	private static BlockBehaviour.Properties amsProperties() {
		return noSpawns(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.METAL)
				.strength(5.0F, 60.0F)
				.noOcclusion()
				.isViewBlocking((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false));
	}

	/** Hardness 25, resistance 60 x 0.6; otherwise the AMC's. */
	private static BlockBehaviour.Properties amspProperties() {
		return noSpawns(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.sound(SoundType.METAL)
				.strength(25.0F, 36.0F)
				.noOcclusion()
				.isViewBlocking((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false));
	}

	private static DeferredBlock<Block> reg(String name, String tab, BlockBehaviour.Properties props) {
		DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, props);
		DeferredItem<BlockItem> item = BLOCK_ITEMS.registerSimpleBlockItem(block);
		LeafiaCreativeTabs.add(tab, item);
		return block;
	}
}
