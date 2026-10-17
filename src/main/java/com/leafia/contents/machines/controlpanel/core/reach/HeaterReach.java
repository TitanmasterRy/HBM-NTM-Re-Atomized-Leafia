package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.machine.BlockEntityHeaterElectric;
import com.hbm.blockentity.machine.BlockEntityHeaterFirebox;
import com.hbm.blockentity.machine.BlockEntityHeaterHeatex;
import com.hbm.blockentity.machine.BlockEntityHeaterOilburner;
import com.hbm.blockentity.machine.BlockEntityHeaterOven;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Leafia's five heater mixins as adapters (batch 8, D-3068). Every heater answers {@code heat} and {@code maxHeat}; the electric
 * heater and the oil burner also take an in-event.
 *
 * <table>
 * <caption>keys</caption>
 * <tr><td>electric</td><td>heat = {@code heatEnergy}, maxHeat = 2147483647</td><td>in {@code heater_set} ({@code heatPerTick})</td></tr>
 * <tr><td>heat exchanger</td><td>heat, maxHeat = 2147483647</td><td></td></tr>
 * <tr><td>firebox</td><td>heat, maxHeat = the firebox's {@code maxHeatEnergy}</td><td></td></tr>
 * <tr><td>oven</td><td>heat, maxHeat = the oven's {@code maxHeatEnergy}</td><td></td></tr>
 * <tr><td>oil burner</td><td>heat, maxHeat = 100,000</td><td>in {@code burner_set} ({@code isOn})</td></tr>
 * </table>
 *
 * <p>The electric heater's {@code maxHeat} is {@code (float) 2147483647}, which is 2^31 as a float, as Leafia wrote it (the base has
 * no cap: the buffer is an int that is never clamped). {@code heater_set} turns a wanted heat per tick into the dial: the dial is
 * worth 100 heat a tick a step, so {@code setting = clamp(ceil(heatPerTick / 100), 0, 10)}, the same 0 to 10 the heater's own
 * button cycles through.
 */
public final class HeaterReach {

	private HeaterReach() {}

	/** {@code (float) Integer.MAX_VALUE}: Leafia's "maxHeat" for the two heaters whose buffer has no cap. */
	public static final float UNCAPPED = 2147483647F;

	public static final class Electric extends ReachAdapter<BlockEntityHeaterElectric> {

		public Electric(BlockEntityHeaterElectric be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num(be.heatEnergy));
			data.put("maxHeat", num(UNCAPPED));
			return data;
		}

		@Override public List<String> getInEvents() { return Collections.singletonList("heater_set"); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals("heater_set")) {
				float set = number(e, "heatPerTick", 0F) / 100;
				be.setting = Mth.clamp((int) Math.ceil(set), 0, 10);
				be.setChanged();
			}
		}
	}

	public static final class Heatex extends ReachAdapter<BlockEntityHeaterHeatex> {

		public Heatex(BlockEntityHeaterHeatex be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num(be.heatEnergy));
			data.put("maxHeat", num(UNCAPPED));
			return data;
		}
	}

	public static final class Firebox extends ReachAdapter<BlockEntityHeaterFirebox> {

		public Firebox(BlockEntityHeaterFirebox be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num(be.heatEnergy));
			data.put("maxHeat", num(BlockEntityHeaterFirebox.maxHeatEnergy));
			return data;
		}
	}

	public static final class Oven extends ReachAdapter<BlockEntityHeaterOven> {

		public Oven(BlockEntityHeaterOven be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num(be.heatEnergy));
			data.put("maxHeat", num(BlockEntityHeaterOven.maxHeatEnergy));
			return data;
		}
	}

	public static final class Oilburner extends ReachAdapter<BlockEntityHeaterOilburner> {

		public Oilburner(BlockEntityHeaterOilburner be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num(be.heatEnergy));
			data.put("maxHeat", num(BlockEntityHeaterOilburner.maxHeatEnergy));
			return data;
		}

		@Override public List<String> getInEvents() { return Collections.singletonList("burner_set"); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals("burner_set")) {
				be.isOn = flag(e, "isOn", false);
				be.setChanged();
			}
		}
	}
}
