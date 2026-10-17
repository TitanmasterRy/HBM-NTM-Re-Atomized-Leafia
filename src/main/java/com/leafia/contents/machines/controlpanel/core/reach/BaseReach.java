package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hbm.slices.OrdnanceSystem;
import com.hbm.slices.Slice1;
import com.hbm.slices.Slice3;
import com.hbm.slices.Slice4;
import com.hbm.slices.Slice6;
import com.hbm.slices.Slice7;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers the Custom Control Panel's adapters on the base mod's block entity types (batch 8, D-3068, D-3060 #4).
 * Called from {@code LeafiaCapabilities.registerCapabilities} when
 * {@code LeafiaFeatures.controlPanel} is on.
 *
 * <p>Capability registration against a type the addon does not own needs no change to that type, and nothing on master names the
 * addon. Every type below is a public holder on a {@code Slice}, so a rename or removal there fails this file's compile.
 *
 * <p>What is registered, by family: the two doors; the fluid tank, barrel and battery socket; the two gauges and the fluid pump; the
 * five heaters; the four steam turbines (the small two answer {@code generated}, see {@link TurbineReach}); every turret core;
 * thirteen RBMK column types; the crane console; the siren ({@link SirenReach}).
 */
public final class BaseReach {

	private BaseReach() {}

	/** Every type this class registers an adapter on, in registration order, for tests and for the ledger count. */
	private static final List<BlockEntityType<?>> TYPES = new ArrayList<>();

	public static synchronized List<BlockEntityType<?>> registeredTypes() {
		return new ArrayList<>(TYPES);
	}

	private static synchronized <T extends BlockEntity> void reg(RegisterCapabilitiesEvent event, Supplier<BlockEntityType<T>> type,
			Function<T, IControllable> adapter) {
		BlockEntityType<T> t = type.get();
		TYPES.add(t);
		event.registerBlockEntity(ControlEventSystem.CONTROLLABLE, t, (be, ctx) -> adapter.apply(be));
	}

	public static void register(RegisterCapabilitiesEvent event) {
		synchronized(BaseReach.class) {
			TYPES.clear();
		}

		// doors
		reg(event, Slice1.DOOR_GENERIC, DoorReach.Generic::new);
		reg(event, Slice1.BLAST_DOOR, DoorReach.Blast::new);

		// storage
		reg(event, Slice3.MACHINE_FLUIDTANK, StorageReach.Tank::new);
		reg(event, Slice3.BARREL, StorageReach.Barrel::new);
		reg(event, Slice1.BATTERY_SOCKET, StorageReach.Socket::new);

		// network
		reg(event, Slice7.CABLE_GAUGE, NetworkReach.CableGauge::new);
		reg(event, Slice7.FLUID_DUCT_GAUGE, NetworkReach.PipeGauge::new);
		reg(event, Slice7.FLUID_PUMP, NetworkReach.Pump::new);

		// heaters
		reg(event, Slice3.HEATER_ELECTRIC, HeaterReach.Electric::new);
		reg(event, Slice3.HEATER_HEATEX, HeaterReach.Heatex::new);
		reg(event, Slice3.HEATER_FIREBOX, HeaterReach.Firebox::new);
		reg(event, Slice3.HEATER_OVEN, HeaterReach.Oven::new);
		reg(event, Slice3.HEATER_OILBURNER, HeaterReach.Oilburner::new);

		// turbines
		reg(event, Slice3.MACHINE_TURBINE, TurbineReach::turbine);
		reg(event, Slice3.MACHINE_LARGE_TURBINE, TurbineReach::largeTurbine);
		reg(event, Slice3.MACHINE_INDUSTRIAL_TURBINE, TurbineReach::industrialTurbine);
		reg(event, Slice3.MACHINE_CHUNGUS, TurbineReach::chungus);

		// turrets: every core type (they all extend the base class, and the base class is the CE one)
		reg(event, OrdnanceSystem.TURRET_JEREMY, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_MAXWELL, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_ARTY, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_HIMARS, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_HOWARD, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_HOWARD_DAMAGED, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_CHEKHOV, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_FRIENDLY, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_FRITZ, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_RICHARD, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_TAUON, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_SENTRY, TurretReach::new);
		reg(event, OrdnanceSystem.TURRET_SENTRY_DAMAGED, TurretReach::new);

		// RBMK columns
		reg(event, Slice4.RBMK_ROD, RBMKReach.Rod::new);
		reg(event, Slice4.RBMK_ROD_REASIM, RBMKReach.Rod::new);
		reg(event, Slice4.RBMK_MODERATOR, RBMKReach.Column::new);
		reg(event, Slice4.RBMK_REFLECTOR, RBMKReach.Column::new);
		reg(event, Slice4.RBMK_ABSORBER, RBMKReach.Column::new);
		reg(event, Slice4.RBMK_BLANK, RBMKReach.Column::new);
		reg(event, Slice4.RBMK_STORAGE, RBMKReach.Column::new);
		reg(event, Slice4.RBMK_CONTROL, RBMKReach.ControlManual::new);
		reg(event, Slice4.RBMK_CONTROL_AUTO, RBMKReach.Control::new);
		reg(event, Slice4.RBMK_BOILER, RBMKReach.Boiler::new);
		reg(event, Slice4.RBMK_COOLER, RBMKReach.Cooler::new);
		reg(event, Slice4.RBMK_HEATER, RBMKReach.Heater::new);
		reg(event, Slice4.RBMK_OUTGASSER, RBMKReach.Outgasser::new);

		// the siren (H-CCP-2, D-2275)
		reg(event, Slice1.SIREN, SirenReach::new);

		// the crane console
		reg(event, Slice6.CRANE_CONSOLE, CraneConsoleReach::new);
	}
}
