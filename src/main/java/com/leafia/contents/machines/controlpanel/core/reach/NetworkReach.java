package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import com.hbm.blockentity.network.BlockEntityCableGauge;
import com.hbm.blockentity.network.BlockEntityFluidPump;
import com.hbm.blockentity.network.BlockEntityPipeGauge;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Leafia's three network mixins as adapters (batch 8, D-3068): the two gauges and the fluid pump.
 *
 * <ul>
 * <li>cable gauge and duct gauge: {@code perTick}, {@code perSecond}. The base keeps both as getters ({@code getDeltaTick},
 * {@code getDeltaLastSecond}), which are what Leafia's mixin shadowed as fields;</li>
 * <li>fluid pump: {@code fill}; in {@code set_pump_buffer} ({@code size}, 0 to 10,000), {@code set_pump_pressure} ({@code pu},
 * 0 to 5), {@code set_pump_priority} ({@code priority}, by ordinal). These are the bounds the pump's own control packet enforces,
 * and Leafia's mixin clamped to the same.</li>
 * </ul>
 */
public final class NetworkReach {

	private NetworkReach() {}

	/** The pump's control-packet bounds. */
	public static final int PUMP_BUFFER_MAX = 10_000;
	public static final int PUMP_PRESSURE_MAX = 5;

	public static final class CableGauge extends ReachAdapter<BlockEntityCableGauge> {

		public CableGauge(BlockEntityCableGauge be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("perTick", num(be.getDeltaTick()));
			data.put("perSecond", num(be.getDeltaLastSecond()));
			return data;
		}
	}

	public static final class PipeGauge extends ReachAdapter<BlockEntityPipeGauge> {

		public PipeGauge(BlockEntityPipeGauge be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("perTick", num(be.getDeltaTick()));
			data.put("perSecond", num(be.getDeltaLastSecond()));
			return data;
		}
	}

	public static final class Pump extends ReachAdapter<BlockEntityFluidPump> {

		public Pump(BlockEntityFluidPump be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("fill", num(be.tank[0].getFill()));
			return data;
		}

		@Override
		public List<String> getInEvents() {
			return Arrays.asList("set_pump_buffer", "set_pump_pressure", "set_pump_priority");
		}

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			switch(e.name) {
			case "set_pump_buffer" -> be.bufferSize = Mth.clamp(Math.round(number(e, "size", 0F)), 0, PUMP_BUFFER_MAX);
			case "set_pump_pressure" -> be.tank[0].withPressure(Mth.clamp(Math.round(number(e, "pu", 0F)), 0, PUMP_PRESSURE_MAX));
			case "set_pump_priority" -> {
				ConnectionPriority[] all = ConnectionPriority.values();
				be.priority = all[Mth.clamp(Math.round(number(e, "priority", 2F)), 0, all.length - 1)];
			}
			default -> { return; }
			}
			be.setChanged();
		}
	}
}
