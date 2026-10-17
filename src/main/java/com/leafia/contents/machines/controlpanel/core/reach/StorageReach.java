package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blockentity.machine.storage.BlockEntityBatterySocket;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * The storage blocks NTM-CE and Leafia made controllable, as adapters (batch 8, D-3068): the fluid tank (CE's
 * {@code TileEntityMachineFluidTank}), the barrel (Leafia's {@code MixinTileEntityBarrel}) and the battery socket (Leafia's
 * {@code MixinTileEntityBatterySocket}).
 *
 * <h2>Keys</h2>
 * <table>
 * <caption>query keys, in-events, out-events</caption>
 * <tr><td>tank</td><td>{@code t0_fluidType} (omitted while the tank is empty of a type), {@code t0_fluidAmount}, {@code mode}</td>
 * <td>in and out {@code tank_set_mode}</td></tr>
 * <tr><td>barrel</td><td>{@code fill}, {@code maxFill}, {@code ioMode}</td><td>in {@code setIOMode}</td></tr>
 * <tr><td>battery socket</td><td>{@code charge}, {@code maxCharge}, {@code ioMode}</td><td>in {@code setIOMode}</td></tr>
 * </table>
 *
 * <h2>Divergences</h2>
 * <ol>
 * <li>{@code t0_fluidType} is the fluid's <b>translation key</b> (or its override), not CE's localised display name: the server
 * has no language file, and {@code I18nUtil} answers a constant there on purpose (D-003), so a localised string would be the same
 * useless text for every fluid.</li>
 * <li>The tank's mode is reduced with {@code floorMod} into {@code 0..3}; CE's {@code %} gave a negative mode for a negative
 * input, which the tank's mode switch treats as none of the four. The barrel's and the socket's are clamped to {@code 0..3}, as
 * Leafia's are.</li>
 * </ol>
 */
public final class StorageReach {

	private StorageReach() {}

	/** {@code TileEntityMachineFluidTank}. */
	public static final class Tank extends ReachAdapter<BlockEntityMachineFluidTank> {

		public static final String MODE = "tank_set_mode";

		public Tank(BlockEntityMachineFluidTank be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			if(be.tank.getTankType() != Fluids.NONE)
				data.put("t0_fluidType", str(be.tank.getTankType().getConditionalName()));
			data.put("t0_fluidAmount", num(be.tank.getFill()));
			data.put("mode", num(be.mode));
			return data;
		}

		@Override public List<String> getInEvents() { return Collections.singletonList(MODE); }
		@Override public List<String> getOutEvents() { return Collections.singletonList(MODE); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals(MODE)) {
				be.mode = (short) Math.floorMod(whole(e, "mode"), (int) BlockEntityMachineFluidTank.MODES);
				be.setChanged();
				echo(modeEvent());
			}
		}

		private ControlEvent modeEvent() {
			return ControlEvent.newEvent(MODE).setVar("mode", num(be.mode));
		}

		@Override public boolean polled() { return true; }
		@Override public Object snapshot() { return (int) be.mode; }

		@Override
		public List<ControlEvent> diff(Object prev, Object now) {
			return List.of(ControlEvent.newEvent(MODE).setVar("mode", num((Integer) now)));
		}
	}

	/** {@code TileEntityBarrel}, which is {@code BlockEntityBarrel} here. */
	public static final class Barrel extends ReachAdapter<BlockEntityBarrel> {

		public Barrel(BlockEntityBarrel be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("fill", num(be.tank.getFill()));
			data.put("maxFill", num(be.tank.getMaxFill()));
			data.put("ioMode", num(be.mode));
			return data;
		}

		@Override public List<String> getInEvents() { return Collections.singletonList("setIOMode"); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals("setIOMode")) {
				be.mode = (short) Mth.clamp(whole(e, "mode"), 0, BlockEntityBarrel.MODES - 1);
				be.setChanged();
			}
		}
	}

	/** {@code TileEntityBatterySocket}. */
	public static final class Socket extends ReachAdapter<BlockEntityBatterySocket> {

		public Socket(BlockEntityBatterySocket be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("charge", num(be.getPower()));
			data.put("maxCharge", num(be.getMaxPower()));
			data.put("ioMode", num(be.getRelevantMode(true)));
			return data;
		}

		@Override public List<String> getInEvents() { return Collections.singletonList("setIOMode"); }

		/** Leafia sets {@code redLow} (the mode a socket has without a redstone signal) and re-reads the relevant mode. */
		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals("setIOMode")) {
				be.redLow = (short) Mth.clamp(whole(e, "mode"), 0, 3);
				be.getRelevantMode(false);
				be.markChanged();
			}
		}
	}
}
