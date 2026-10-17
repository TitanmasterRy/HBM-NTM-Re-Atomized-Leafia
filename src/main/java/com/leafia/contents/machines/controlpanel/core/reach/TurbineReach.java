package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Map;
import java.util.function.ToLongFunction;

import com.hbm.blockentity.machine.BlockEntityChungus;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.hbm.blockentity.machine.BlockEntityMachineIndustrialTurbine;
import com.hbm.blockentity.machine.BlockEntityMachineLargeTurbine;
import com.hbm.blockentity.machine.BlockEntityMachineTurbine;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The four steam turbines Leafia's mixins made controllable ({@code MixinTileEntityMachineTurbine},
 * {@code MixinTileEntityMachineLargeTurbine}, {@code MixinTileEntityTurbineBase} for the industrial turbine and the Chungus), as
 * adapters (batch 8, D-3068; the first two answer {@code generated} since batch 9, D-3069).
 *
 * <h2>{@code generated}</h2>
 *
 * Every one of Leafia's turbine mixins has a single key, {@code generated}. The small and the large turbine answer it from the
 * per-tick generation seam master added for it (D-2275, hand-off H-CCP-1: {@code lastTickGeneration}, what {@code updateEntity} added
 * to {@code power} that tick, 0 on a tick that makes nothing), and it is Leafia's <b>mean of the last 20 ticks</b> (owner decision
 * 2026-10-17, D-3072; D-3069 had answered the last tick's value). The adapter holds no history, so {@link TurbineGeneration} keeps
 * the ring per position, samples the seam once a tick and forgets a removed machine.
 *
 * <p>The industrial turbine and the Chungus take it from {@code powerBuffer} sampled at the end of each update, which D-2275 did not
 * give a seam for. They are still registered, so they are controllable targets a panel can link to, with the key <b>absent</b>
 * rather than 0: a node that queries it reads "no value", not a reassuring zero (D-953's lesson about NaN reading as 0).
 * {@code LeafiaControlPanelReachTests} pins both halves.
 */
public final class TurbineReach {

	private TurbineReach() {}

	/** Leafia's key. */
	public static final String GENERATED = "generated";

	/** One class for all four; the two with a generation seam answer {@link #GENERATED}, the other two answer nothing. */
	public static final class Steam<T extends BlockEntity> extends ReachAdapter<T> {

		private final ToLongFunction<T> generation;

		public Steam(T be, ToLongFunction<T> generation) {
			super(be);
			this.generation = generation;
			if(generation != null)
				TurbineGeneration.track(be, generation);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			if(generation != null)
				data.put(GENERATED, num(TurbineGeneration.mean(be)));
			return data;
		}
	}

	public static Steam<BlockEntityMachineTurbine> turbine(BlockEntityMachineTurbine be) { return new Steam<>(be, t -> t.lastTickGeneration); }
	public static Steam<BlockEntityMachineLargeTurbine> largeTurbine(BlockEntityMachineLargeTurbine be) { return new Steam<>(be, t -> t.lastTickGeneration); }
	public static Steam<BlockEntityMachineIndustrialTurbine> industrialTurbine(BlockEntityMachineIndustrialTurbine be) { return new Steam<>(be, null); }
	public static Steam<BlockEntityChungus> chungus(BlockEntityChungus be) { return new Steam<>(be, null); }
}
