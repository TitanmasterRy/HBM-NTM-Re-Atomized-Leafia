package com.leafia.contents.machines.misc.modular_turbine.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.misc.modular_turbine.IMTMachineUpgradeContributor;
import com.leafia.contents.machines.misc.modular_turbine.IMTStageUpgradeContributor;
import com.leafia.contents.machines.misc.modular_turbine.MTPacketId;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase.TurbineComponentType;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.CompiledMachineStats;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.CompiledStageStats;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.MachineUpgradeSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageRuntimeData;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageUpgradeContext;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageUpgradeRelation;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageUpgradeSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.TickSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.AssemblyErrorReason;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeError;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeSuccess;
import com.leafia.contents.machines.misc.modular_turbine.ports.MTComponentPortTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The modular turbine's core: it walks the shaft to find what the player built, compiles that
 * into a model, and runs the model every tick.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/core/MTCoreTE.java}, 1,394
 * lines. Every constant and every formula is upstream's; what changed is listed below.
 *
 * <h2>How it works</h2>
 *
 * <b>Assembly.</b> {@link #reassemble()} walks from the core along the shaft one block at a time,
 * up to 300, and splits what it finds into <em>stages</em> ({@link TurbineAssembly}): runs of
 * blades and ports between two separators. Each stage takes one steam, named by a fluid identifier
 * on one of its ports, and gets two tanks sized by {@link #calculateBufferSize}. Anything wrong —
 * an open end, a misaligned part, two identifiers that disagree — stops the walk with an
 * {@link AssemblyErrorReason} and leaves the turbine disassembled.
 *
 * <p><b>The model.</b> Each stage's steam is worth a specific work, read straight off the steam's
 * {@link FT_Coolable} chain: {@link #getSteamSpecificRemainingWork} is the turbine work left in a
 * unit of steam all the way down to spent steam, per unit of spent-steam-equivalent mass, and a
 * decompressing stage extracts the difference between its steam and the next one down. That work
 * sets the stage's inlet whirl, and the stage's torque falls linearly as the blade speed catches it.
 *
 * <p><b>The tick.</b> Drive torque from every stage, minus the generator's load, Coulomb and
 * viscous friction and windage, over the rotor's weight, integrates the shaft speed. A PI governor
 * throttles steam admission to hold {@link #getGovernedRPS()}, and turbulence — from inverted
 * blades, too many stacked blades, or a surge of steam — bleeds speed away.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 * <li><b>Facing.</b> Upstream's {@code EnumFacing.byIndex(getBlockMetadata() - 10).getOpposite()}
 *     is {@code DIR.getOpposite()} here — the metadata minus ten <em>is</em>
 *     {@link BlockDummyable#DIR}.</li>
 * <li><b>"Next tick" on load.</b> Upstream defers {@code assembleFromNBT} with
 *     {@code LeafiaPassiveServer.queueFunction}, because the world is not ready while NBT loads.
 *     That class is an accumulator and is not coming (D-1056). D-1056 suggested
 *     {@code level.getServer().execute(...)}, and <b>that would not defer at all</b>: called on the
 *     server thread, {@code BlockableEventLoop.execute} runs its task immediately. The compound is
 *     held in {@link #pendingAssembly} and applied at the top of the next tick instead, which is
 *     what upstream's queue did. On a client it waits until both ends of the shaft are loaded.</li>
 * <li><b>The governed speed</b> is read from {@link LeafiaConfig} on each call. Upstream copies it
 *     into a static field at class initialisation, which here would run during registration —
 *     before a COMMON config has loaded — and freeze the default (D-1003's trap, in a new place).</li>
 * <li><b>Unloading is not removal.</b> As on {@link ModularTurbineComponentTE}: upstream
 *     disassembles from {@code invalidate}, which 1.21.1 also calls on chunk unload.</li>
 * <li><b>The client sync</b> also rides vanilla's update tag, because {@code onPlayerValidate} is
 *     dead in this port (D-1029) and a player arriving later would see an unassembled turbine.</li>
 * </ul>
 *
 * <p>The steam that runs along each stage on a client is {@link MTCoreClient} (T6).
 *
 * <h2>What does not port yet</h2>
 *
 * <ul>
 * <li><b>The control panel.</b> Upstream implements {@code IControllable} and publishes rps,
 *     turbulence and power per second to it. The Custom Control Panel is not in this port's
 *     specification at all (D-1030), so the interface is dropped and the three values stay public
 *     fields — {@link #rps}, {@link #turbulence}, {@link #generatedPerSec} — for whichever lands
 *     first. A hand-off, recorded in D-1058.</li>
 * <li><b>Sound.</b> The looping hum per ten blocks of shaft needs {@code getLoopedSoundStartStop}
 *     on the client proxy, which does not exist here (D-1037's radio has the same gap). The
 *     stress groan above 65 rps is a plain one-shot and <em>is</em> ported.</li>
 * <li><b>Debug traces</b> — {@code LeafiaDebug.Tracker} and {@code debugPos} — are the author's
 *     scaffolding and are left out.</li>
 * </ul>
 */
public class MTCoreTE extends BlockEntity implements LeafiaPacketReceiver, IControllable {

	public static double PARTIAL_EXPANSION_FRACTION = 0.2D;
	/** nozzle-to-inlet swirl gain */
	public static double INLET_WHIRL_COEFFICIENT = 32D;
	/** exit-swirl recovery */
	public static double EXIT_WHIRL_RECOVERY_FACTOR = 0.82D;
	public static double ANGULAR_MOMENTUM_TORQUE_COEFFICIENT = 0.02D;
	/** Effective linear electrical load slope */
	public static double GENERATOR_LOAD_COEFFICIENT = 0.000001D;
	/** Saturation cap applied to the effective generator counter-torque. */
	public static double GENERATOR_TORQUE_LIMIT = Double.MAX_VALUE;
	public static double COULOMB_FRICTION_TORQUE = 0.7D;
	public static double FRICTION_RPS_EPSILON = 0.25D;
	public static double VISCOUS_FRICTION_COEFFICIENT = 0.05D;
	public static double WINDAGE_COEFFICIENT = 0.01D;
	public static double INCIDENCE_DESIGN_SPEED_RATIO = 1D;
	public static double INCIDENCE_LOSS_FACTOR = 0.25D; // UNUSED
	public static double INCIDENCE_EFFICIENCY_FLOOR = 0.05D; // UNUSED
	public static double POWER_SCALE = 0.04; //0.00232478632*2;
	public static double ADMISSION_NOMINAL_TAU_TICKS = 4D;
	public static double ADMISSION_STABLE_RELATIVE_ERROR = 0.03D;
	// DEATHSTEAM -> ULTRAHOTSTEAM -> SUPERHOTSTEAM -> HOTSTEAM -> STEAM -> SPENTSTEAM stage-work scalars
	// (all five are unused upstream: getStageWorkMultiplier returns a flat 125)
	public static double DEATHSTEAM_STAGE_WORK_MULTIPLIER = 5000D;
	public static double ULTRAHOTSTEAM_STAGE_WORK_MULTIPLIER = 1000D;
	public static double SUPERHOTSTEAM_STAGE_WORK_MULTIPLIER = 500D;
	public static double HOTSTEAM_STAGE_WORK_MULTIPLIER = 250D;
	public static double STEAM_STAGE_WORK_MULTIPLIER = 125D;
	public static double BUFFER_CAPACITY_MULTIPLIER = 12D;
	public static double ADMISSION_MAX_BUFFER_TICKS = 2D;
	public static double ADMISSION_FLOW_EPSILON = 1.0E-9D;
	public static double ADMISSION_BUFFER_EPSILON = 1.0E-6D;
	public static double EQUILIBRIUM_RPS_EPSILON = 1.0E-6D;
	public static double EQUILIBRIUM_RPS_LIMIT = 1.0E6D;
	public static int EQUILIBRIUM_SOLVER_ITERATIONS = 48;
	public static double DEFAULT_GLOBAL_GEAR_SCALE = 0.05D;
	// Upstream's GOVERNED_RPS = AddonConfig.governedRPS is read live instead; see getGovernedRPS.
	public static double THROTTLE_GOVERNOR_PROPORTIONAL_GAIN = 0.04D;
	public static double THROTTLE_GOVERNOR_INTEGRAL_GAIN = 0.0025D;
	public static double THROTTLE_GOVERNOR_ADMISSION_RATE = 0.02D;
	public static double ROTOR_INERTIA_SCALE = 0.8D;
	public static double TWO_PI = Math.PI * 2D;
	public static double TICK_SECONDS = 1D / 20D;

	/** The longest shaft the walk will follow before giving up with {@code TOO_LONG}. */
	public static final int MAX_LENGTH = 300;

	public int overdrive = 0;

	/** Extra governed RPS per overdrive upgrade tier. */
	public static int getRPSBoost(int level) {
		return switch(level) {
			case 1 -> 10;
			case 2 -> 25;
			case 3 -> 40;
			default -> 0;
		};
	}

	/**
	 * The speed the governor holds: the config's governed RPS plus the overdrive boost.
	 *
	 * <p>Read from {@link LeafiaConfig} each call rather than copied into a static at class
	 * initialisation as upstream does — see the class doc.
	 */
	public double getGovernedRPS() {
		return LeafiaConfig.governedRPS + getRPSBoost(overdrive);
	}

	public double rps;
	public double lastTargetRPS = 0;
	public double lastDriveTorque = 0;
	public double lastGeneratorTorque = 0;
	public double lastFrictionTorque = 0;
	public double lastWindageTorque = 0;
	public double globalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
	public double lastGlobalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
	public double admission = 0;
	public double lastAdmission = 0;
	public final List<ModularTurbineComponentTE> components = new ArrayList<>();
	public final Map<Integer, ModularTurbineComponentTE> positionToComponentMap = new HashMap<>();
	public final List<TurbineAssembly> assemblies = new ArrayList<>();
	public int length = 0;
	// Rotor inertia scalar
	public double weight = 0;
	private double governorIntegral = 0;

	public double local$shaftAngle = 0;
	public double local$shaftAnglePrev = 0;
	public int stressSoundTimer = 0;

	/** An assembly loaded from NBT, applied on the next tick. Upstream's {@code queueFunction}. */
	CompoundTag pendingAssembly = null;

	/** Set by {@link #onChunkUnloaded}, so {@link #setRemoved} can tell an unload from a break. */
	private boolean unloading = false;

	private CompiledMachineStats compiledMachineStats;

	/**
	 * The steams the turbine accepts, most compressed first. Filled by {@link #initSteamTypes()}.
	 *
	 * <p><b>The order is balance.</b> {@link #calculateBufferSize} raises 2.5 to a steam's index
	 * here, and {@link #getNextSteam} decompresses one step down the list, so a missing or
	 * reordered entry silently resizes every buffer. Upstream fills it from
	 * {@code AddonFluidTraits}; here it is the turbine's own, because the list is the turbine's
	 * data and does not change a base-mod fluid. See D-1058.
	 */
	public static final List<FluidType> steamTypes = new ArrayList<>();

	/**
	 * Upstream's six, in upstream's order. Deathsteam is the addon's own fluid, which is why the
	 * turbine registers only when the addon's fluids do. Idempotent.
	 */
	public static void initSteamTypes(FluidType deathsteam) {
		if(!steamTypes.isEmpty()) return;
		steamTypes.add(deathsteam);
		steamTypes.add(Fluids.ULTRAHOTSTEAM);
		steamTypes.add(Fluids.SUPERHOTSTEAM);
		steamTypes.add(Fluids.HOTSTEAM);
		steamTypes.add(Fluids.STEAM);
		steamTypes.add(Fluids.SPENTSTEAM);
	}

	public MTCoreTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static FluidType getNextSteam(FluidType type, boolean decompress) {
		int index = steamTypes.indexOf(type);
		if(index == -1) return null;
		if(decompress) {
			if(index + 1 < steamTypes.size())
				return steamTypes.get(index + 1);
			else
				return null;
		} else
			return type;
	}

	public static FT_Coolable getSteamExpansion(FluidType type) {
		if(type.hasTrait(FT_Coolable.class))
			return type.getTrait(FT_Coolable.class);
		return null;
	}

	public static double getSteamMassEquivalent(FluidType type) {
		if(type.equals(Fluids.SPENTSTEAM))
			return 1;
		FT_Coolable expansion = getSteamExpansion(type);
		if(expansion == null)
			return 0;
		return getSteamMassEquivalent(expansion.coolsTo) * expansion.amountProduced / (double) expansion.amountReq;
	}

	public static double getTurbineStepWork(FluidType type) {
		FT_Coolable expansion = getSteamExpansion(type);
		if(expansion == null)
			return 0;
		double eff = expansion.getEfficiency(FT_Coolable.CoolingType.TURBINE);
		if(eff == 0)
			eff = 1;
		return expansion.heatEnergy * eff / (double) expansion.amountReq;
	}

	public static double getSteamRemainingWork(FluidType type) {
		FT_Coolable expansion = getSteamExpansion(type);
		if(expansion == null)
			return 0;
		return getTurbineStepWork(type) + getSteamRemainingWork(expansion.coolsTo) * expansion.amountProduced / (double) expansion.amountReq;
	}

	public static double getSteamSpecificRemainingWork(FluidType type) {
		return getSteamRemainingWork(type) / getSteamMassEquivalent(type);
	}

	public static double getStageSpecificWork(FluidType type, boolean decompress) {
		return getStageSpecificWork(type, decompress, PARTIAL_EXPANSION_FRACTION);
	}

	private static double getStageSpecificWork(FluidType type, boolean decompress, double partialExpansionFraction) {
		double specificWork = getSteamSpecificRemainingWork(type);
		if(!decompress)
			return specificWork * partialExpansionFraction;
		FluidType nextSteam = getNextSteam(type, true);
		if(nextSteam == null)
			return specificWork;
		return Math.max(specificWork - getSteamSpecificRemainingWork(nextSteam), 0);
	}

	private static double getStageSpecificWork(TurbineAssembly assembly, CompiledStageStats compiledStats) {
		return getStageSpecificWork(assembly.typeIn, assembly.decompress, compiledStats.partialExpansionFraction) * getStageWorkMultiplier(assembly.typeIn);
	}

	/**
	 * A flat 125 for every steam. Upstream has two earlier per-steam tables commented out here
	 * — the five {@code *_STAGE_WORK_MULTIPLIER} constants above are the first of them — and
	 * neither is live.
	 */
	public static double getStageWorkMultiplier(FluidType type) {
		return 125;
	}

	private static int getStageInputAmount(FluidType type, boolean decompress) {
		if(!decompress)
			return 1;
		FT_Coolable expansion = getSteamExpansion(type);
		if(expansion == null)
			return 0;
		return expansion.amountReq;
	}

	private static int getStageOutputAmount(FluidType type, boolean decompress) {
		if(!decompress)
			return 1;
		FT_Coolable expansion = getSteamExpansion(type);
		if(expansion == null)
			return 0;
		return expansion.amountProduced;
	}

	private static double getStageOutputRatio(FluidType type, boolean decompress) {
		int inputAmount = getStageInputAmount(type, decompress);
		if(inputAmount <= 0)
			return 0;
		return getStageOutputAmount(type, decompress) / (double) inputAmount;
	}

	public static double getStageRadius(int size) {
		return Math.max(size, 2) * 0.35D;
	}

	private static double getStageInletWhirl(CompiledStageStats compiledStats) {
		return getStageInletWhirl(compiledStats.stageSpecificWork, compiledStats.inletWhirlCoefficient);
	}

	public static double getStageInletWhirl(double stageSpecificWork, double inletWhirlCoefficient) {
		if(stageSpecificWork <= 0)
			return 0;
		return Math.sqrt(2 * Math.max(stageSpecificWork, 0)) * inletWhirlCoefficient;
	}

	private static void assignBaseGearRatios(List<TurbineAssembly> assemblies) {
		double weightedLogSum = 0;
		double totalWeight = 0;
		for(TurbineAssembly assembly : assemblies) {
			CompiledStageStats compiledStats = assembly.compiledStats;
			double inletWhirl = compiledStats.inletWhirl;
			double stageRadius = compiledStats.stageRadius;
			if(inletWhirl <= 0 || stageRadius <= 0) {
				assembly.baseGearRatio = 1;
				compiledStats.baseGearRatio = 1;
				continue;
			}
			double speedIndex = inletWhirl / stageRadius;
			double authorityWeight = compiledStats.authorityWeight;
			weightedLogSum += authorityWeight * Math.log(speedIndex);
			totalWeight += authorityWeight;
		}
		if(totalWeight <= 0) {
			for(TurbineAssembly assembly : assemblies) {
				assembly.baseGearRatio = 1;
				assembly.compiledStats.baseGearRatio = 1;
			}
			return;
		}
		double referenceSpeedIndex = Math.exp(weightedLogSum / totalWeight);
		for(TurbineAssembly assembly : assemblies) {
			CompiledStageStats compiledStats = assembly.compiledStats;
			double inletWhirl = compiledStats.inletWhirl;
			double stageRadius = compiledStats.stageRadius;
			if(inletWhirl <= 0 || stageRadius <= 0) {
				assembly.baseGearRatio = 1;
				compiledStats.baseGearRatio = 1;
				continue;
			}
			double speedIndex = inletWhirl / stageRadius;
			assembly.baseGearRatio = speedIndex / referenceSpeedIndex;
			compiledStats.baseGearRatio = assembly.baseGearRatio;
		}
	}

	private static double getEffectiveMassFlow(TurbineAssembly assembly, CompiledStageStats compiledStats, double rawMassFlow) {
		double flowCapacity = compiledStats.flowCapacity;
		double cappedRawMassFlow = Math.min(rawMassFlow, flowCapacity);
		if(assembly.nominalMassFlow <= ADMISSION_FLOW_EPSILON && assembly.admissionBufferMass <= ADMISSION_BUFFER_EPSILON) {
			assembly.nominalMassFlow = cappedRawMassFlow;
			assembly.admissionBufferMass = Math.max(rawMassFlow - cappedRawMassFlow, 0);
			if(assembly.admissionBufferMass <= ADMISSION_BUFFER_EPSILON)
				assembly.admissionBufferMass = 0;
			return cappedRawMassFlow;
		}

		assembly.nominalMassFlow += (rawMassFlow - assembly.nominalMassFlow) * (1D - Math.exp(-1D / ADMISSION_NOMINAL_TAU_TICKS));
		assembly.nominalMassFlow = Math.min(assembly.nominalMassFlow, flowCapacity);
		double stableBand = Math.max(assembly.nominalMassFlow * ADMISSION_STABLE_RELATIVE_ERROR, ADMISSION_FLOW_EPSILON);
		double maxBufferMass = Math.max(assembly.nominalMassFlow, 0) * ADMISSION_MAX_BUFFER_TICKS;
		if(Math.abs(rawMassFlow - assembly.nominalMassFlow) <= stableBand && assembly.admissionBufferMass <= ADMISSION_BUFFER_EPSILON) {
			assembly.admissionBufferMass = 0;
			return cappedRawMassFlow;
		}

		double availableMass = rawMassFlow + assembly.admissionBufferMass;
		double effectiveMassFlow = Math.min(assembly.nominalMassFlow, availableMass);
		assembly.admissionBufferMass = Math.max(availableMass - effectiveMassFlow, 0);
		if(assembly.admissionBufferMass > maxBufferMass) {
			double overflowMass = assembly.admissionBufferMass - maxBufferMass;
			effectiveMassFlow += overflowMass;
			assembly.admissionBufferMass = maxBufferMass;
		}
		if(assembly.admissionBufferMass <= ADMISSION_BUFFER_EPSILON && Math.abs(effectiveMassFlow - rawMassFlow) <= stableBand) {
			assembly.admissionBufferMass = 0;
			return cappedRawMassFlow;
		}
		return effectiveMassFlow;
	}

	private static double getGeneratorTorqueAtOmega(CompiledMachineStats machineStats, double omega) {
		double generatorTorque = machineStats.generatorLoadCoefficient * omega;
		return Math.min(Math.max(generatorTorque, -machineStats.generatorTorqueLimit), machineStats.generatorTorqueLimit);
	}

	private static double getFrictionTorqueAtRPS(CompiledMachineStats machineStats, double rps) {
		return machineStats.coulombFrictionTorque * Math.tanh(rps / machineStats.frictionRpsEpsilon) + machineStats.viscousFrictionCoefficient * rps;
	}

	private static double getWindageTorqueAtRPS(CompiledMachineStats machineStats, double rps) {
		return machineStats.windageCoefficient * rps * Math.abs(rps);
	}

	/**
	 * Always 1. Upstream computes the deviation and then returns 1 with the real formula commented
	 * out, which is why {@link #INCIDENCE_LOSS_FACTOR} and {@link #INCIDENCE_EFFICIENCY_FLOOR}
	 * carry upstream's own "UNUSED".
	 */
	static double getIncidenceEfficiency(double speedRatio) {
		return 1; //Math.max(1-INCIDENCE_LOSS_FACTOR*deviation*deviation,INCIDENCE_EFFICIENCY_FLOOR);
	}

	private static double getNetTorqueAtRPS(CompiledMachineStats machineStats, double rps, double driveTorqueIntercept, double driveTorqueOmegaSlope) {
		double omega = rps * TWO_PI;
		double driveTorque = driveTorqueIntercept - driveTorqueOmegaSlope * omega;
		return driveTorque - getGeneratorTorqueAtOmega(machineStats, omega) - getFrictionTorqueAtRPS(machineStats, rps) - getWindageTorqueAtRPS(machineStats, rps);
	}

	/**
	 * The speed at which net torque is zero, by doubling an upper bound and then bisecting
	 * {@link #EQUILIBRIUM_SOLVER_ITERATIONS} times. Net torque falls monotonically with speed, so
	 * there is one root and bisection finds it.
	 */
	private static double solveEquilibriumRPS(CompiledMachineStats machineStats, double driveTorqueIntercept, double driveTorqueOmegaSlope) {
		double zeroNetTorque = getNetTorqueAtRPS(machineStats, 0, driveTorqueIntercept, driveTorqueOmegaSlope);
		if(zeroNetTorque <= 0)
			return 0;

		double linearNoLoadRPS = 0;
		if(driveTorqueOmegaSlope > EQUILIBRIUM_RPS_EPSILON)
			linearNoLoadRPS = driveTorqueIntercept / (driveTorqueOmegaSlope * TWO_PI);
		double low = 0;
		double high = Math.max(linearNoLoadRPS, 1);
		double highNetTorque = getNetTorqueAtRPS(machineStats, high, driveTorqueIntercept, driveTorqueOmegaSlope);
		while(highNetTorque > 0 && high < EQUILIBRIUM_RPS_LIMIT) {
			low = high;
			high *= 2;
			highNetTorque = getNetTorqueAtRPS(machineStats, high, driveTorqueIntercept, driveTorqueOmegaSlope);
		}
		if(highNetTorque > 0)
			return high;
		for(int i = 0; i < EQUILIBRIUM_SOLVER_ITERATIONS; i++) {
			double mid = (low + high) / 2;
			double midNetTorque = getNetTorqueAtRPS(machineStats, mid, driveTorqueIntercept, driveTorqueOmegaSlope);
			if(midNetTorque > 0)
				low = mid;
			else
				high = mid;
		}
		return (low + high) / 2;
	}

	/**
	 * The equilibrium speed of an <em>unmodified</em> machine for a given linear drive line —
	 * upstream's solver over upstream's base constants, exposed so a test can check the solver
	 * without building a turbine.
	 */
	public static double solveBaseEquilibriumRPS(double driveTorqueIntercept, double driveTorqueOmegaSlope) {
		return solveEquilibriumRPS(compileMachineStats(new MachineUpgradeSummary()), driveTorqueIntercept, driveTorqueOmegaSlope);
	}

	private static int requireTankCapacity(long bufferSize, FluidType fluid) {
		if(bufferSize > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		return (int) bufferSize;
	}

	@Override
	public String getPacketIdentifier() {
		return "MT_CORE";
	}

	@Override
	public double affectionRange() {
		return 512;
	}

	public boolean assembleFromNBT(CompoundTag compound) {
		disassemble();
		boolean ret = assembleFromNBT_internal(compound);
		if(!ret)
			disassemble();
		return ret;
	}

	protected boolean assembleFromNBT_internal(CompoundTag compound) {
		length = compound.getInt("length");
		weight = compound.getDouble("weight");
		globalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
		admission = compound.contains("admission") ? compound.getDouble("admission") : 0;
		governorIntegral = compound.contains("governorIntegral") ? compound.getDouble("governorIntegral") : admission;
		Direction dir = getAssemblyFacing();
		Map<Integer, TurbineAssembly> assemblyMap = new HashMap<>();
		boolean missingGearRatio = false;

		// LOAD ASSEMBLIES
		for(Tag nbtBase : compound.getList("assemblies", Tag.TAG_COMPOUND)) {
			if(nbtBase instanceof CompoundTag tag) {
				TurbineAssembly assembly = new TurbineAssembly();
				assemblies.add(assembly);
				for(Tag nbtBase1 : tag.getList("positions", Tag.TAG_SHORT)) {
					if(nbtBase1 instanceof ShortTag p) {
						assembly.positionsInOrder.add(p.getAsInt());
						assemblyMap.put(p.getAsInt(), assembly);
					} else
						return false;
				}
				for(Tag nbtBase1 : tag.getList("bladeMap", Tag.TAG_COMPOUND)) {
					if(nbtBase1 instanceof CompoundTag bladeTag)
						assembly.bladeDirections.put((int) bladeTag.getShort("pos"), bladeTag.getBoolean("opposite"));
					else
						return false;
				}
				assembly.input = new FluidTank(Fluids.NONE, 0);
				assembly.output = new FluidTank(Fluids.NONE, 0);
				assembly.input.readFromNBT(tag, "input");
				assembly.output.readFromNBT(tag, "output");
				assembly.typeIn = Fluids.fromName(tag.getString("typeIn"));
				assembly.decompress = tag.getBoolean("decompress");
				assembly.size = tag.getByte("size");
				if(tag.contains("gearRatio"))
					assembly.baseGearRatio = tag.getDouble("gearRatio");
				else
					missingGearRatio = true;
			} else return false;
		}
		// HOOK UP BLOCK ENTITIES
		for(int i = 0; i < length; i++) {
			BlockPos offs = worldPosition.relative(dir, i + 1);
			if(level.getBlockState(offs).getBlock() instanceof ModularTurbineBlockBase turbine) {
				BlockPos core = turbine.findCore(level, offs);
				if(core != null) {
					if(level.getBlockEntity(core) instanceof ModularTurbineComponentTE te) {
						positionToComponentMap.put(i, te);
						if(!components.contains(te)) {
							components.add(te);
							te.core = this;
							if(assemblyMap.containsKey(i))
								te.assembly = assemblyMap.get(i);
						}
					} else return false;
				} else return false;
			} else
				return false;
		}
		compileAssemblyModel(missingGearRatio);
		return true;
	}

	public CompoundTag writeAssemblyToNBT(CompoundTag compound) {
		compound.putInt("length", length);
		compound.putDouble("weight", weight);
		compound.putDouble("admission", admission);
		compound.putDouble("governorIntegral", governorIntegral);
		ListTag assemList = new ListTag();
		for(TurbineAssembly assembly : assemblies) {
			CompoundTag tag = new CompoundTag();
			ListTag posList = new ListTag();
			for(Integer p : assembly.positionsInOrder)
				posList.add(ShortTag.valueOf(p.shortValue()));
			tag.put("positions", posList);
			ListTag bladeMap = new ListTag();
			for(Entry<Integer, Boolean> entry : assembly.bladeDirections.entrySet()) {
				CompoundTag e = new CompoundTag();
				e.putShort("pos", entry.getKey().shortValue());
				e.putBoolean("opposite", entry.getValue());
				bladeMap.add(e);
			}
			tag.put("bladeMap", bladeMap);
			assembly.input.writeToNBT(tag, "input");
			assembly.output.writeToNBT(tag, "output");
			tag.putString("typeIn", assembly.typeIn.getName());
			tag.putBoolean("decompress", assembly.decompress);
			tag.putByte("size", (byte) assembly.size);
			tag.putDouble("gearRatio", assembly.baseGearRatio);
			assemList.add(tag);
		}
		compound.put("assemblies", assemList);
		return compound;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket._start(this)
				.__write(MTPacketId.CORE_ASSEMBLY_SYNC.id, writeAssemblyToNBT(new CompoundTag()))
				.__sendToClient(plr);
	}

	/** The turbine's axis: the way the player faced when placing the core. */
	public Direction getAssemblyFacing() {
		return getBlockState().getValue(BlockDummyable.DIR).getOpposite();
	}

	private CompiledMachineStats getCompiledMachineStats() {
		if(compiledMachineStats == null)
			compiledMachineStats = compileMachineStats(summarizeMachineUpgrades());
		return compiledMachineStats;
	}

	private void compileAssemblyModel(boolean recomputeGearRatios) {
		compiledMachineStats = compileMachineStats(summarizeMachineUpgrades());
		for(int assemblyIndex = 0; assemblyIndex < assemblies.size(); assemblyIndex++) {
			TurbineAssembly assembly = assemblies.get(assemblyIndex);
			assembly.compiledStats = compileStageStats(assembly, summarizeStageUpgrades(assemblyIndex, assembly));
		}
		if(recomputeGearRatios)
			assignBaseGearRatios(assemblies);
		else
			for(TurbineAssembly assembly : assemblies)
				assembly.compiledStats.baseGearRatio = assembly.baseGearRatio;
	}

	private MachineUpgradeSummary summarizeMachineUpgrades() {
		MachineUpgradeSummary summary = new MachineUpgradeSummary();
		for(ModularTurbineComponentTE component : components)
			collectMachineUpgradeOffsets(summary, component);
		return summary;
	}

	private StageUpgradeSummary summarizeStageUpgrades(int targetAssemblyIndex, TurbineAssembly targetAssembly) {
		StageUpgradeSummary summary = new StageUpgradeSummary();
		for(int componentPosition = 0; componentPosition < components.size(); componentPosition++) {
			ModularTurbineComponentTE component = components.get(componentPosition);
			StageUpgradeContext context = resolveStageUpgradeContext(targetAssemblyIndex, targetAssembly, componentPosition, component);
			if(context == null)
				continue;
			collectStageUpgradeOffsets(summary, context, component);
		}
		return summary;
	}

	private StageUpgradeContext resolveStageUpgradeContext(int targetAssemblyIndex, TurbineAssembly targetAssembly, int componentPosition, ModularTurbineComponentTE component) {
		if(component.assembly != null) {
			if(component.assembly != targetAssembly)
				return null;
			return new StageUpgradeContext(
					targetAssembly,
					targetAssemblyIndex > 0 ? assemblies.get(targetAssemblyIndex - 1) : null,
					targetAssemblyIndex + 1 < assemblies.size() ? assemblies.get(targetAssemblyIndex + 1) : null,
					componentPosition,
					StageUpgradeRelation.MEMBER
			);
		}
		TurbineAssembly previousAssembly = findPreviousAssembly(componentPosition);
		if(previousAssembly == targetAssembly) {
			return new StageUpgradeContext(
					targetAssembly,
					previousAssembly,
					findNextAssembly(componentPosition),
					componentPosition,
					StageUpgradeRelation.AFTER_TARGET
			);
		}
		TurbineAssembly nextAssembly = findNextAssembly(componentPosition);
		if(nextAssembly == targetAssembly) {
			return new StageUpgradeContext(
					targetAssembly,
					findPreviousAssembly(componentPosition),
					nextAssembly,
					componentPosition,
					StageUpgradeRelation.BEFORE_TARGET
			);
		}
		return null;
	}

	private TurbineAssembly findPreviousAssembly(int componentPosition) {
		TurbineAssembly previousAssembly = null;
		for(TurbineAssembly assembly : assemblies) {
			if(assembly.getEndPosition() >= componentPosition)
				break;
			previousAssembly = assembly;
		}
		return previousAssembly;
	}

	private TurbineAssembly findNextAssembly(int componentPosition) {
		for(TurbineAssembly assembly : assemblies) {
			if(assembly.getStartPosition() > componentPosition)
				return assembly;
		}
		return null;
	}

	private void collectMachineUpgradeOffsets(MachineUpgradeSummary summary, ModularTurbineComponentTE component) {
		component.contributeMachineUpgradeOffsets(summary, this, component);
		((IMTMachineUpgradeContributor) level.getBlockState(component.getBlockPos()).getBlock()).contributeMachineUpgradeOffsets(summary, this, component);
	}

	private void collectStageUpgradeOffsets(StageUpgradeSummary summary, StageUpgradeContext context, ModularTurbineComponentTE component) {
		component.contributeStageUpgradeOffsets(summary, this, context, component);
		((IMTStageUpgradeContributor) level.getBlockState(component.getBlockPos()).getBlock()).contributeStageUpgradeOffsets(summary, this, context, component);
	}

	private static CompiledMachineStats compileMachineStats(MachineUpgradeSummary upgrades) {
		CompiledMachineStats machineStats = new CompiledMachineStats();
		machineStats.generatorLoadCoefficient = GENERATOR_LOAD_COEFFICIENT + upgrades.getGeneratorLoadCoefficientOffset();
		machineStats.generatorTorqueLimit = GENERATOR_TORQUE_LIMIT * (1D + upgrades.getGeneratorTorqueLimitOffset());
		machineStats.coulombFrictionTorque = COULOMB_FRICTION_TORQUE * (1D + upgrades.getCoulombFrictionTorqueOffset());
		machineStats.frictionRpsEpsilon = FRICTION_RPS_EPSILON * (1D + upgrades.getFrictionRpsEpsilonOffset());
		machineStats.viscousFrictionCoefficient = VISCOUS_FRICTION_COEFFICIENT * (1D + upgrades.getViscousFrictionCoefficientOffset());
		machineStats.windageCoefficient = WINDAGE_COEFFICIENT * (1D + upgrades.getWindageCoefficientOffset());
		machineStats.powerScale = POWER_SCALE * (1D + upgrades.getPowerScaleOffset());
		machineStats.rotorInertiaScale = ROTOR_INERTIA_SCALE * (1D + upgrades.getRotorInertiaScaleOffset());
		return machineStats;
	}

	private CompiledStageStats compileStageStats(TurbineAssembly assembly, StageUpgradeSummary upgrades) {
		CompiledStageStats compiledStats = new CompiledStageStats();
		compiledStats.partialExpansionFraction = PARTIAL_EXPANSION_FRACTION + upgrades.getPartialExpansionFractionOffset();
		compiledStats.inletWhirlCoefficient = INLET_WHIRL_COEFFICIENT * (1D + upgrades.getInletWhirlCoefficientOffset());
		compiledStats.exitWhirlRecoveryFactor = EXIT_WHIRL_RECOVERY_FACTOR * (1D + upgrades.getExitWhirlRecoveryFactorOffset());
		compiledStats.angularMomentumTorqueCoefficient = ANGULAR_MOMENTUM_TORQUE_COEFFICIENT * (1D + upgrades.getAngularMomentumTorqueCoefficientOffset());
		compiledStats.inputAmount = getStageInputAmount(assembly.typeIn, assembly.decompress);
		compiledStats.outputAmount = getStageOutputAmount(assembly.typeIn, assembly.decompress);
		compiledStats.division = getStageOutputRatio(assembly.typeIn, assembly.decompress);
		compiledStats.steamMassEquivalent = getSteamMassEquivalent(assembly.typeIn);
		compiledStats.stageSpecificWork = getStageSpecificWork(assembly, compiledStats);
		compiledStats.stageRadius = getStageRadius(assembly.size);
		compiledStats.inletWhirl = getStageInletWhirl(compiledStats);
		compiledStats.torqueResponseFactor = compiledStats.angularMomentumTorqueCoefficient * (1D + compiledStats.exitWhirlRecoveryFactor);
		compiledStats.bladeCount = assembly.bladeDirections.size();
		compiledStats.bladeArea = Math.max(compiledStats.bladeCount + upgrades.getBladeArea(), ADMISSION_FLOW_EPSILON);
		compiledStats.flowCapacity = upgrades.getFlowCapacity() > ADMISSION_FLOW_EPSILON ? upgrades.getFlowCapacity() : Double.POSITIVE_INFINITY;
		compiledStats.authorityWeight = Math.max(compiledStats.bladeArea * compiledStats.steamMassEquivalent, ADMISSION_FLOW_EPSILON);
		compiledStats.baseGearRatio = assembly.baseGearRatio;
		return compiledStats;
	}

	private List<StageRuntimeData> evaluateStages(double admission) {
		List<StageRuntimeData> stageData = new ArrayList<>();
		for(TurbineAssembly assembly : assemblies)
			stageData.add(evaluateStage(assembly, admission));
		return stageData;
	}

	private StageRuntimeData evaluateStage(TurbineAssembly assembly, double admission) {
		CompiledStageStats compiledStats = assembly.compiledStats;
		int inputConsumed = transferStageFluids(assembly, compiledStats, admission);
		int wrongBlades = countWrongAndStackedBlades(assembly);
		assembly.lastWrongBlades = wrongBlades;
		// applyWrongBladeTurbulence
		double turbAddWrongBlades = wrongBlades * 2d / assembly.compiledStats.bladeArea * Math.min(inputConsumed, 1);
		if(turbAddWrongBlades > 0.3) turbulenceReasonInverseBlades = true;
		turbulence = Math.min(turbulence + turbAddWrongBlades, 100);

		// apply blade count turbulence
		double turbAddBladeCount = Math.pow(assembly.maxStackedBlades / (double) LeafiaConfig.maxOptimalTurbineLength, 5) * 0.015 * Math.min(inputConsumed, 1);
		maxTurbAddBladeCount = Math.max(maxTurbAddBladeCount, turbAddBladeCount);

		double bladeEfficiency = Math.max(compiledStats.bladeCount - wrongBlades, 0) / (double) compiledStats.bladeCount;
		assembly.lastBladeEfficiency = bladeEfficiency;

		double rawMassFlow = inputConsumed * compiledStats.steamMassEquivalent;
		double massFlow = getEffectiveMassFlow(assembly, compiledStats, rawMassFlow);
		assembly.lastRawMassFlow = rawMassFlow;
		assembly.lastEffectiveMassFlow = massFlow;
		assembly.lastAdmissionBufferMass = assembly.admissionBufferMass;
		assembly.lastNominalMassFlow = assembly.nominalMassFlow;

		double stageTorqueScale = 0;
		if(massFlow > 0 && compiledStats.stageSpecificWork > 0)
			stageTorqueScale = massFlow * compiledStats.stageRadius * bladeEfficiency * compiledStats.getBladeAreaFactor() * compiledStats.torqueResponseFactor;
		return new StageRuntimeData(assembly, compiledStats, massFlow, stageTorqueScale);
	}

	/**
	 * Moves one tick's steam through a stage and returns how much of it the governor admitted.
	 *
	 * <p>Note the split, which is upstream's and is deliberate: the tanks are drained and filled by
	 * the <em>full</em> {@code maxOps}, while only the admitted share drives the shaft. The author's
	 * comment: "the steams are meant to be processed instantaneously, no matter what — the only
	 * bottleneck is meant to be the buffer capacity". So throttling wastes steam rather than
	 * backing it up, and a governed turbine passes everything it is given.
	 */
	private int transferStageFluids(TurbineAssembly assembly, CompiledStageStats compiledStats, double admission) {
		int inputOps = compiledStats.inputAmount > 0 ? assembly.input.getFill() / compiledStats.inputAmount : 0;
		int outputOps = compiledStats.outputAmount > 0 ? (assembly.output.getMaxFill() - assembly.output.getFill()) / compiledStats.outputAmount : 0;
		int maxOps = Math.min(inputOps, outputOps);
		double admittedOps = maxOps * admission + assembly.admissionOpCarry;
		int ops = (int) Math.floor(admittedOps);
		assembly.admissionOpCarry = admittedOps - ops;
		if(!LeafiaConfig.enableGovernedRPS)
			ops = maxOps;
		int inputConsumed = ops * compiledStats.inputAmount;
		// ntmleafia: the steams are meant to be processed instantaneously, no matter what
		// the only bottleneck is meant to be the buffer capacity
		assembly.input.setFill(assembly.input.getFill() - /*inputConsumed*/maxOps * compiledStats.inputAmount);
		assembly.output.setFill(assembly.output.getFill() + /*outputProduced*/maxOps * compiledStats.outputAmount);
		assembly.lastOps = inputConsumed;
		assembly.lastDivision = compiledStats.division;
		return inputConsumed;
	}

	/**
	 * Counts blades facing the wrong way for where steam entered, and the longest run of blades
	 * between two inlets.
	 *
	 * <p>A blade before the first inlet should face against the shaft and every blade after it
	 * with it; a blade sitting between two inlets is wrong whichever way it faces. Then it
	 * consumes the tick's inlet positions, so a stage nobody filled this tick reads as clean.
	 */
	private int countWrongAndStackedBlades(TurbineAssembly assembly) {
		int wrongBlades = 0;
		assembly.maxStackedBlades = 0;
		if(!assembly.receivingPositions.isEmpty()) {
			int startIndex = assembly.getStartPosition();
			int endIndex = assembly.getEndPosition();
			int flowCounterStart = assembly.receivingPositions.size();
			int flowCounter = flowCounterStart;
			boolean shouldBeOpposite = true;
			int stackCounter = 0;
			for(int i = startIndex; i <= endIndex; i++) {
				if(assembly.receivingPositions.contains(i)) {
					flowCounter--;
					shouldBeOpposite = false;
					assembly.maxStackedBlades = Math.max(assembly.maxStackedBlades, stackCounter);
					stackCounter = 0;
				}
				if(assembly.bladeDirections.containsKey(i)) {
					stackCounter++;
					boolean isOpposite = assembly.bladeDirections.get(i);
					if(shouldBeOpposite != isOpposite || (flowCounter > 0 && flowCounter < flowCounterStart))
						wrongBlades++;
				}
			}
			assembly.maxStackedBlades = Math.max(assembly.maxStackedBlades, stackCounter);
			assembly.server$lastReceivingPositions.clear();
			assembly.server$lastReceivingPositions.addAll(assembly.receivingPositions);
			assembly.receivingPositions.clear();
		}
		return wrongBlades;
	}

	/**
	 * A PI controller on steam admission, with anti-windup: the integral only accumulates while
	 * the output is not saturated, or while the error would pull it back out of saturation. The
	 * admission then slews at most {@link #THROTTLE_GOVERNOR_ADMISSION_RATE} per tick.
	 */
	void updateThrottleGovernor(TickSummary tickSummary) {
		double error = getGovernedRPS() - rps;
		double proportionalAdmission = error * THROTTLE_GOVERNOR_PROPORTIONAL_GAIN;
		double unclampedAdmission = governorIntegral + proportionalAdmission;
		double targetAdmission = Math.min(Math.max(unclampedAdmission, 0D), 1D);
		if(targetAdmission == unclampedAdmission || (targetAdmission <= 0D && error > 0) || (targetAdmission >= 1D && error < 0))
			governorIntegral += error * THROTTLE_GOVERNOR_INTEGRAL_GAIN;
		targetAdmission = Math.min(Math.max(governorIntegral + proportionalAdmission, 0D), 1D);
		double admissionDelta = targetAdmission - admission;
		admissionDelta = Math.min(Math.max(admissionDelta, -THROTTLE_GOVERNOR_ADMISSION_RATE), THROTTLE_GOVERNOR_ADMISSION_RATE);
		admission += admissionDelta;
	}

	/** One tick of the governor on a turbine with no steam, for tests. Returns the new admission. */
	public double stepGovernorForTest() {
		updateThrottleGovernor(new TickSummary());
		return admission;
	}

	private void applyRotationalStep(CompiledMachineStats machineStats, List<StageRuntimeData> stageData, TickSummary tickSummary) {
		double driveTorqueIntercept = 0;
		double driveTorqueOmegaSlope = 0;
		for(StageRuntimeData data : stageData) {
			double actualGearRatio = data.compiledStats.getActualGearRatio(globalGearScale);
			driveTorqueIntercept += data.stageTorqueScale * data.compiledStats.inletWhirl * actualGearRatio;
			driveTorqueOmegaSlope += data.stageTorqueScale * data.compiledStats.stageRadius * actualGearRatio * actualGearRatio;
		}
		tickSummary.targetRPS = Math.min(solveEquilibriumRPS(machineStats, driveTorqueIntercept, driveTorqueOmegaSlope), getGovernedRPS());

		double omega = rps * TWO_PI;
		for(StageRuntimeData data : stageData)
			tickSummary.driveTorque += data.getDriveTorque(omega, globalGearScale);
		tickSummary.generatorTorque = getGeneratorTorqueAtOmega(machineStats, omega);
		tickSummary.frictionTorque = getFrictionTorqueAtRPS(machineStats, rps);
		tickSummary.windageTorque = getWindageTorqueAtRPS(machineStats, rps);

		double netTorque = tickSummary.driveTorque - tickSummary.generatorTorque - tickSummary.frictionTorque - tickSummary.windageTorque;
		double effectiveWeight = weight * machineStats.rotorInertiaScale;
		omega += netTorque / effectiveWeight * TICK_SECONDS;
		rps = omega / TWO_PI;

		double outputOmega = rps * TWO_PI;
		tickSummary.powerGenerated += (long) Math.max(tickSummary.generatorTorque * outputOmega * machineStats.powerScale, 0);
	}

	public long powerOutput = 0;
	public long displayPowerGenerated = 0;

	/** Degrees the shaft turns per tick on screen. Sub-linear in speed, so a fast shaft does not strobe. */
	public double getVisualDeltaAngle() {
		return Math.pow(rps * 360, 0.9) / 20;
	}

	/** Upstream's {@code update()}. Runs on both sides. */
	public void updateEntity() {
		if(pendingAssembly != null && canApplyPendingAssembly()) {
			CompoundTag pending = pendingAssembly;
			pendingAssembly = null;
			boolean assembled = assembleFromNBT(pending);
			// Not upstream's: assembleFromNBT opens with disassemble(), which on a server tells
			// every watching client the turbine is empty. Upstream then relied on
			// onPlayerValidate to put it back, which is dead here (D-1029), so the good news is
			// sent explicitly - otherwise a client that applied its own copy first would have it
			// wiped by the empty one.
			if(assembled && !level.isClientSide) {
				LeafiaPacket._start(this)
						.__write(MTPacketId.CORE_ASSEMBLY_SYNC.id, writeAssemblyToNBT(new CompoundTag()))
						.__sendToAffectedClients();
			}
		}
		if(level.isClientSide) {
			local$shaftAnglePrev = local$shaftAngle;
			local$shaftAngle += getVisualDeltaAngle();
			if(local$shaftAngle >= 360) {
				local$shaftAngle -= 360;
				local$shaftAnglePrev -= 360;
			}
			// Upstream also tunes the looping hum to the shaft speed here; not ported, see the
			// class doc. The steam is, in MTCoreClient, so this class never links a client type.
			MTCoreClient.spawnSteam(this);
			return;
		}
		if(stressSoundTimer <= 0) { // just fuck off
			stressSoundTimer = 70;
			double vol = Math.pow((Math.max(rps - 65, 0) / 35), 3) * 0.4;
			if(vol > 0) {
				int interval = 5;
				Direction dir = getAssemblyFacing();
				for(int i = 0; i < length; i += interval) {
					level.playSound(
							null,
							worldPosition.relative(dir, 1 + i),
							LeafiaSounds.pipestressed.get(),
							SoundSource.BLOCKS,
							(float) vol, 0.65f
					);
				}
			}
		}
		stressSoundTimer--;

		powerOutput = 0;
		displayPowerGenerated = 0;
		TickSummary tickSummary = new TickSummary();
		maxTurbAddBladeCount = 0;
		turbulenceReasonInputSurge = false;
		turbulenceReasonInverseBlades = false;
		turbulenceReasonTooManyBlades = false;

		// Simulate the current shaft state.
		if(weight > 0) {
			updateThrottleGovernor(tickSummary);
			CompiledMachineStats machineStats = getCompiledMachineStats();
			List<StageRuntimeData> stageData = evaluateStages(admission);
			applyRotationalStep(machineStats, stageData, tickSummary);
		}

		// Apply turbulence damping and transient spike accumulation.
		double turbulenceAdd = Math.pow(Math.max(Math.min(tickSummary.targetRPS, LeafiaConfig.governedRPS) - rps, 0) / 24, 5) / 5;
		turbulence = Math.max(turbulence - turbulence * 0.008 - 0.008, 0);
		turbulence = Math.min(turbulence + maxTurbAddBladeCount, 100);
		double turbMul2 = Math.pow(Math.max(tickSummary.generatorTorque, 0) / 10000, 0.5);
		turbulence = Math.min(turbulence + turbulenceAdd * LeafiaConfig.surgeTurbulenceMultiplier * Math.pow(turbMul2, 2.25), 100);
		if(turbulenceAdd > 0.3) turbulenceReasonInputSurge = true;
		if(maxTurbAddBladeCount > 0.3) turbulenceReasonTooManyBlades = true;

		rps -= rps * Math.pow(turbulence / 100, 1.35) * 0.05;

		// Commit the visible summary for overlays/debug output.
		lastTargetRPS = tickSummary.targetRPS;
		lastDriveTorque = tickSummary.driveTorque;
		lastGeneratorTorque = tickSummary.generatorTorque;
		lastFrictionTorque = tickSummary.frictionTorque;
		lastWindageTorque = tickSummary.windageTorque;
		lastGlobalGearScale = globalGearScale;
		lastAdmission = admission;

		powerOutput = tickSummary.powerGenerated;
		displayPowerGenerated = powerOutput;

		// Sync steam buffers and incoming-flow markers to clients.
		CompoundTag syncCompound = new CompoundTag();
		ListTag syncList = new ListTag();
		for(TurbineAssembly assembly : assemblies) {
			CompoundTag tag = new CompoundTag();
			assembly.input.writeToNBT(tag, "i");
			assembly.output.writeToNBT(tag, "o");
			tag.putInt("f", assembly.lastOps);
			ListTag flowList = new ListTag();
			for(Integer receiving : assembly.server$lastReceivingPositions)
				flowList.add(ShortTag.valueOf(receiving.shortValue()));
			tag.put("r", flowList);
			syncList.add(tag);
		}
		syncCompound.put("a", syncList);
		LeafiaPacket._start(this)
				.__write(MTPacketId.CORE_STEAM_SYNC.id, syncCompound)
				.__write(MTPacketId.CORE_TURBULENCE_REASONS.id, new boolean[] {
						turbulenceReasonInputSurge,
						turbulenceReasonInverseBlades,
						turbulenceReasonTooManyBlades
				})
				.__write(MTPacketId.CORE_WEIGHT.id, weight)
				.__write(MTPacketId.CORE_TURBULENCE.id, turbulence)
				.__write(MTPacketId.CORE_GENERATION.id, displayPowerGenerated)
				.__write(MTPacketId.CORE_RPS.id, rps) // forgor
				.__write(MTPacketId.CORE_OVERDRIVE.id, overdrive)
				.__sendToAffectedClients();
		generateds[needle] = displayPowerGenerated;
		needle = (needle + 1) % 20;
		generatedPerSec = 0;
		for(long gen : generateds)
			generatedPerSec += gen;
	}

	/**
	 * Whether a pending NBT assembly can be applied now. Always on a server — reading a block in
	 * an unloaded chunk loads it there, as upstream's did. On a client an unloaded block reads as
	 * air and the walk would fail, so it waits until the far end of the shaft is in view.
	 */
	private boolean canApplyPendingAssembly() {
		if(!level.isClientSide) return true;
		int pendingLength = pendingAssembly.getInt("length");
		return level.isLoaded(worldPosition.relative(getAssemblyFacing(), pendingLength));
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MTCoreTE core) {
		core.updateEntity();
	}

	long[] generateds = new long[20];
	int needle = 0;
	/** HE generated over the last 20 ticks. What upstream hands the control panel as "generated". */
	public long generatedPerSec = 0;

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == MTPacketId.CORE_ASSEMBLY_SYNC.id) {
			pendingAssembly = null; // the packet is newer than whatever the update tag carried
			disassemble();
			if(value != null)
				assembleFromNBT((CompoundTag) value);
		} else if(key == MTPacketId.CORE_STEAM_SYNC.id) {
			CompoundTag compound = (CompoundTag) value;
			ListTag list = compound.getList("a", Tag.TAG_COMPOUND);
			for(int i = 0; i < assemblies.size(); i++) {
				if(i < list.size()) {
					TurbineAssembly assembly = assemblies.get(i);
					CompoundTag tag = list.getCompound(i);
					assembly.input.readFromNBT(tag, "i");
					assembly.output.readFromNBT(tag, "o");
					assembly.lastOps = tag.getInt("f");
					ListTag receiving = tag.getList("r", Tag.TAG_SHORT);
					assembly.receivingPositions.clear();
					for(Tag j : receiving)
						assembly.receivingPositions.add(((ShortTag) j).getAsInt());
				}
			}
		} else if(key == MTPacketId.CORE_TURBULENCE_REASONS.id) {
			boolean[] values = (boolean[]) value;
			turbulenceReasonInputSurge = values[0];
			turbulenceReasonInverseBlades = values[1];
			turbulenceReasonTooManyBlades = values[2];
		} else if(key == MTPacketId.CORE_WEIGHT.id)
			weight = (double) value;
		else if(key == MTPacketId.CORE_TURBULENCE.id)
			turbulence = (double) value;
		else if(key == MTPacketId.CORE_GENERATION.id)
			displayPowerGenerated = (long) value;
		else if(key == MTPacketId.CORE_GLOBAL_GEAR.id)
			globalGearScale = (double) value;
		else if(key == MTPacketId.CORE_RPS.id)
			rps = (double) value;
		else if(key == MTPacketId.CORE_OVERDRIVE.id)
			overdrive = (int) value;
	}

	/** One steam stage: a run of blades and ports between two separators, with its two tanks. */
	public static class TurbineAssembly {
		/// NOTE: The values stored here are actually 1 lower than actual offset
		public List<Integer> positionsInOrder = new ArrayList<>();
		public FluidTank input;
		public FluidTank output;
		public FluidType typeIn;
		public boolean decompress = false;
		public int size = -1;
		public double baseGearRatio = 1;
		private CompiledStageStats compiledStats;
		public Set<Integer> receivingPositions = new HashSet<>();
		public Set<Integer> server$lastReceivingPositions = new HashSet<>();
		public Map<Integer, Boolean> bladeDirections = new HashMap<>(); // false: normal, true: opposite
		public int lastOps = 0;
		public double lastDivision = 1;
		public int lastWrongBlades = 0;
		public double lastBladeEfficiency = 0;
		public double admissionBufferMass = 0;
		public double admissionOpCarry = 0;
		public double nominalMassFlow = 0;
		public double lastRawMassFlow = 0;
		public double lastEffectiveMassFlow = 0;
		public double lastAdmissionBufferMass = 0;
		public double lastNominalMassFlow = 0;
		public int maxStackedBlades = 0;

		int getStartPosition() {
			return positionsInOrder.get(0);
		}

		int getEndPosition() {
			return positionsInOrder.get(positionsInOrder.size() - 1);
		}
	}

	public boolean turbulenceReasonInputSurge = false;
	public boolean turbulenceReasonInverseBlades = false;
	public boolean turbulenceReasonTooManyBlades = false;
	public double maxTurbAddBladeCount = 0;
	public double turbulence;

	public void disassemble() {
		for(ModularTurbineComponentTE component : components) {
			component.assembly = null;
			component.core = null;
		}
		assemblies.clear();
		components.clear();
		positionToComponentMap.clear();
		length = 0;
		weight = 0;
		compiledMachineStats = null;
		globalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
		lastGlobalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
		admission = 0;
		lastAdmission = 0;
		governorIntegral = 0;
		if(level != null && !level.isClientSide) {
			LeafiaPacket._start(this)
					.__write(MTPacketId.CORE_ASSEMBLY_SYNC.id, writeAssemblyToNBT(new CompoundTag()))
					.__sendToAffectedClients();
		}
	}

	public static class AssemblyReturnCode {
		public static class ReturnCodeSuccess extends AssemblyReturnCode { }

		public static class ReturnCodeError extends AssemblyReturnCode {
			public final BlockPos errorPosA;
			public final BlockPos errorPosB;
			public final AssemblyErrorReason reason;

			public ReturnCodeError(BlockPos errorPosA, BlockPos errorPosB, AssemblyErrorReason reason) {
				this.errorPosA = errorPosA;
				this.errorPosB = errorPosB;
				this.reason = reason;
			}
		}

		/** Each one's {@code name().toLowerCase()} is a {@code chat.turbine.error.*} key. */
		public enum AssemblyErrorReason {
			OPEN_END,
			NOT_ALIGNED,
			INCOMPATIBLE_SIZE,
			BUG,
			NO_IDENTIFIER,
			IDENTIFIER_MISMATCH,
			CANT_DECOMPRESS,
			INVALID_IDENTIFIER,
			NO_BLADES,
			TOO_LONG,
			SIZE_MISMATCH, // Only the same sized components may be used for each types of steam
		}
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		unloading = true;
		super.onChunkUnloaded();
	}

	/** Upstream's {@code invalidate}: breaking the core takes the assembly with it. Not on unload. */
	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		if(!unloading)
			disassemble();
		super.setRemoved();
	}

	public AssemblyReturnCode reassemble() {
		AssemblyReturnCode code = reassemble_internal();
		if(code instanceof ReturnCodeError)
			disassemble();
		LeafiaPacket._start(this)
				.__write(MTPacketId.CORE_ASSEMBLY_SYNC.id, writeAssemblyToNBT(new CompoundTag()))
				.__sendToAffectedClients();
		return code;
	}

	protected AssemblyReturnCode reassemble_internal() {
		disassemble();
		int[] connectable = new int[] {};
		boolean prevWasSeparator = false;
		int prevSize = 1;
		Direction dir = getAssemblyFacing();
		BlockPos offs = worldPosition;
		BlockPos prevPos = offs;
		TurbineAssembly lastAssembly = null;
		BlockPos previousPortPos = null;
		int i = 0;
		int leastCompression = -1;
		int mostCompression = -1;
		while(true) {
			if(i > MAX_LENGTH) // Who in the sane mind does this?
				return new ReturnCodeError(worldPosition, offs, AssemblyErrorReason.TOO_LONG);
			offs = offs.relative(dir);
			if(level.getBlockState(offs).getBlock() instanceof ModularTurbineBlockBase turbine) {
				boolean aligned = true;
				BlockPos core = turbine.findCore(level, offs);
				if(core == null)
					return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.BUG);
				else {
					Direction partDir = level.getBlockState(core).getValue(BlockDummyable.DIR).getOpposite();
					if(level.getBlockEntity(core) instanceof ModularTurbineComponentTE te) {
						positionToComponentMap.put(i, te);
						if(!components.contains(te)) {
							weight += turbine.weight();
							components.add(te);
							te.core = this;

							// ALIGNMENT CHECK
							if(dir.getAxis() == Axis.X)
								aligned = core.getZ() == offs.getZ();
							else if(dir.getAxis() == Axis.Z)
								aligned = core.getX() == offs.getX();
							aligned = aligned && (core.getY() + turbine.shaftHeight() == offs.getY());
							if(!aligned)
								return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.NOT_ALIGNED);

							// DIRECTION CHECK
							if(!partDir.getAxis().equals(dir.getAxis()))
								return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.NOT_ALIGNED); // bruh

							// COMPATIBILITY CHECK
							int size = turbine.size();
							boolean compatible = connectable.length == 0 || turbine.canConnectTo().length == 0;
							for(int s : connectable) {
								if(s == size) {
									compatible = true;
									break;
								}
							}
							for(int s : turbine.canConnectTo()) {
								if(s == prevSize) {
									compatible = true;
									break;
								}
							}
							if(!compatible)
								return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.INCOMPATIBLE_SIZE);
							prevSize = size;

							// UPDATING PREVIOUS PARAMETERS & FORMING ASSEMBLY
							connectable = turbine.canConnectTo();
							boolean isSeparator = turbine.componentType().isSeparator;
							if(turbine.componentType().equals(TurbineComponentType.INLINE_PORT)) {
								// IF IT'S INLINE PORT, OVERRIDE isSeparator SO
								// ONE OF THEM WILL FORM AN ASSEMBLY WHEN 2 OF THEM ARE PLACED IN A ROW
								if(prevWasSeparator)
									isSeparator = false;
							}
							boolean joinsSteamAssembly = !isSeparator;

							// IF IT'S INLINE PORT, EXPAND PREVIOUS ASSEMBLY
							// THIS IS THE ONLY WAY WE CAN GET 2 ASSEMBLIES ADJACENT
							// WITHOUT HAVING TO PLACE A SEPARATOR IN-BETWEEN
							if(turbine.componentType().equals(TurbineComponentType.INLINE_PORT)) {
								if(lastAssembly != null)
									joinsSteamAssembly = true;
							}

							if(!isSeparator && !prevWasSeparator && i == 0)
								return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.OPEN_END);

							if(joinsSteamAssembly) {
								// ELSE, CREATE A NEW ASSEMBLY OR EXPAND PREVIOUS ONE
								if(lastAssembly == null) {
									lastAssembly = new TurbineAssembly();
									assemblies.add(lastAssembly);
								}
								lastAssembly.positionsInOrder.add(i);
								te.assembly = lastAssembly;
								if(turbine.componentType().equals(TurbineComponentType.BLADES)) {
									lastAssembly.bladeDirections.put(i, !dir.equals(partDir));
									lastAssembly.size = turbine.size();
								}

								// CHECK FOR IDENTIFIERS
								if(te instanceof MTComponentPortTE port) {
									if(port.identifier != null) {
										// CHECK IF THERE WAS ALREADY A COMPONENT WITH IDENTIFIER
										if(lastAssembly.typeIn != null) {
											// IF SO, CHECK IF THE IDENTIFIER MATCHES
											// previousPortPos IS ALWAYS NOT NULL HERE BECAUSE
											// WHEN typeIn IS SET previousPortPos IS ALSO SET
											if(!lastAssembly.typeIn.equals(port.identifier) || lastAssembly.decompress != port.decompress)
												return new ReturnCodeError(previousPortPos, offs, AssemblyErrorReason.IDENTIFIER_MISMATCH);
										}
										lastAssembly.typeIn = port.identifier;
										lastAssembly.decompress = port.decompress;
										previousPortPos = offs;

										// SET LEAST/MOST COMPRESSION FOR LATER USE
										FluidType nextSteam = getNextSteam(port.identifier, port.decompress);
										if(nextSteam == null) {
											if(port.decompress)
												return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.CANT_DECOMPRESS);
											else
												return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.INVALID_IDENTIFIER);
										}
										int steamIndex = steamTypes.indexOf(port.identifier);
										int nextSteamIndex = steamTypes.indexOf(nextSteam);
										if(steamIndex == -1)
											return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.INVALID_IDENTIFIER);
										if(mostCompression == -1 || nextSteamIndex < mostCompression)
											mostCompression = nextSteamIndex;
										if(leastCompression == -1 || steamIndex > leastCompression)
											leastCompression = steamIndex;
									}
								}
							}
							if(isSeparator) {
								// SEPARATE ASSEMBLY IF IT'S A SEPARATOR
								if(lastAssembly != null) {
									if(lastAssembly.typeIn == null)
										return new ReturnCodeError(worldPosition.relative(dir, lastAssembly.getStartPosition() + 1), prevPos, AssemblyErrorReason.NO_IDENTIFIER);

									if(lastAssembly.bladeDirections.isEmpty())
										return new ReturnCodeError(worldPosition.relative(dir, lastAssembly.getStartPosition() + 1), prevPos, AssemblyErrorReason.NO_BLADES);
								}
								lastAssembly = null;
							}
							// DON'T REPLACE THIS WITH isSeparator VARIABLE AS IT GETS
							// OVERRIDDEN ON INLINE_PORT TYPE
							prevWasSeparator = turbine.componentType().isSeparator;
						}
					} else
						return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.BUG);
				}
			} else {
				if(!prevWasSeparator)
					return new ReturnCodeError(prevPos, offs, AssemblyErrorReason.OPEN_END);
				if(lastAssembly != null) {
					if(lastAssembly.typeIn == null)
						return new ReturnCodeError(worldPosition.relative(dir, lastAssembly.getStartPosition() + 1), offs, AssemblyErrorReason.NO_IDENTIFIER);

					if(lastAssembly.bladeDirections.isEmpty())
						return new ReturnCodeError(worldPosition.relative(dir, lastAssembly.getStartPosition() + 1), prevPos, AssemblyErrorReason.NO_BLADES);
				}
				break;
			}
			prevPos = offs;
			i++;
		}
		length = i;
		// CALCULATE BUFFERS
		for(TurbineAssembly assembly : assemblies) {
			FluidType nextSteam = getNextSteam(assembly.typeIn, assembly.decompress);
			long inputCapacity = calculateBufferSize(assembly.typeIn, mostCompression, assembly.bladeDirections.keySet().size(), assembly.size);
			long outputCapacity = calculateBufferSize(nextSteam, mostCompression, assembly.bladeDirections.keySet().size() * 4, assembly.size);
			assembly.input = new FluidTank(assembly.typeIn, requireTankCapacity(inputCapacity, assembly.typeIn));
			assembly.output = new FluidTank(nextSteam, requireTankCapacity(outputCapacity, nextSteam));
		}
		compileAssemblyModel(true);
		globalGearScale = DEFAULT_GLOBAL_GEAR_SCALE;
		admission = 0;
		lastAdmission = 0;
		governorIntegral = 0;
		return new ReturnCodeSuccess();
	}

	/**
	 * A stage tank's capacity: {@code 2.5^index × blades × 8^((size-1)/2) × (4500 / T₀) × 12}, where
	 * {@code index} is the steam's place in {@link #steamTypes} and {@code T₀} is the first
	 * entry's temperature. Spent steam's is a thousandth of that.
	 *
	 * <p>{@code mostCompression} is overwritten with 0 on the first line — upstream's "ah forget
	 * it" — so the argument is ignored and every buffer is sized from deathsteam. Kept.
	 */
	public long calculateBufferSize(FluidType fluid, int mostCompression, int blades, int size) {
		// mostCompression IS A SMALLER VALUE BECAUSE THE LOWER THE INDEX IS, THE MORE COMPRESSED THE STEAM IS
		int index = steamTypes.indexOf(fluid);
		mostCompression = 0; // ah forget it
		int delta = index - mostCompression;
		double buffer = Math.pow(10, delta) / Math.pow(4, delta) * blades * Math.pow(8, (size - 1) / 2d) * (4500d / steamTypes.get(mostCompression).temperature) * BUFFER_CAPACITY_MULTIPLIER;
		if(fluid.equals(Fluids.SPENTSTEAM))
			buffer /= 1000;
		return (long) buffer;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		rps = compound.getDouble("rps");
		turbulence = compound.getDouble("turbulence");
		overdrive = compound.getInt("overdrive");
		if(compound.contains("assembly"))
			pendingAssembly = compound.getCompound("assembly");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putDouble("rps", rps);
		compound.putDouble("turbulence", turbulence);
		compound.put("assembly", writeAssemblyToNBT(new CompoundTag()));
		compound.putInt("overdrive", overdrive);
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's three query keys; {@code generated} is {@link #generatedPerSec}, the sum over the last 20 ticks. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("rps", (float) rps, "turbulence", (float) turbulence, "generated", (float) generatedPerSec);
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}
}
