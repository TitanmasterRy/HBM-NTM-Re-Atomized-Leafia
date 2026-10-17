package com.leafia.contents.machines.reactors.pwr.blocks.components.computer;

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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.hbm.util.Tuple.Triplet;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR control port's block entity: the reactor's numbers and its control rods, for a computer.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/computer/PWRComputerTE.java}
 * is an OpenComputers {@code SimpleComponent} named {@code "pwr"} with seven {@code @Callback}s, and a CE
 * control panel {@code IControllable} with two in-events and ten query values. Neither OpenComputers nor
 * the control panel exists here (D-1093 #8: strip {@code IControllable}, keep the values reachable), so
 * every callback is a plain public method with the same name, arguments and answer, and
 * {@link #queryValues()} keeps the ten values under their names as floats. Whatever integration lands
 * later (a computer mod, or the control panel if it is ever adopted) calls these.
 *
 * <p>{@link #getTankCapacities()} answers the three tank capacities (D-3076: upstream's body answered the
 * <em>fills</em>, the same as {@link #getTankFills()}, whatever its doc said); {@link #queryValues()}'s
 * {@code capacity_*} values are the same numbers.
 */
public class PWRComputerTE extends PWRAssignableEntity implements IControllable {

	public PWRComputerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_COMPUTER";
	}

	/** Upstream's OpenComputers component name. */
	public String getComponentName() {
		return "pwr";
	}

	/** A rod group's demand in percent, or null with no reactor or no such group. */
	@Nullable
	public Double getControl(String name) {
		PWRData core = getCoreByCorePos();
		if(core != null && core.controlDemand.containsKey(name)) return core.controlDemand.get(name) * 100;
		return null;
	}

	/** Sets a rod group's demand, in percent, clamped to 0..100; ignored for a group no rod carries. */
	public void setControl(String name, double percent) {
		PWRData core = getCoreByCorePos();
		if(core != null) {
			double value = Mth.clamp(percent / 100, 0, 1);
			if(!core.controlDemand.containsKey(name) && !groupExists(core, name)) return;
			core.controlDemand.put(name, value);
			core.manipulateRod(name);
			core.sendControlPositions();
		}
	}

	@Nullable
	public Double getMasterControl() {
		PWRData core = getCoreByCorePos();
		return core != null ? core.masterControl * 100 : null;
	}

	public void setMasterControl(double percent) {
		PWRData core = getCoreByCorePos();
		if(core != null) {
			core.masterControl = Mth.clamp(percent / 100, 0, 1);
			core.manipulateRod(null);
			core.sendControlPositions();
		}
	}

	/** "Returns lowest fuel temperature, highest fuel temperature, and average fuel temperature in order." */
	public double[] getFuelTemperatures() {
		PWRData core = getCoreByCorePos();
		if(core != null) {
			Triplet<Double, Double, Double> temps = getTemperatures(core);
			return new double[] {temps.getX(), temps.getY(), temps.getZ()};
		}
		return new double[] {20, 20, 0};
	}

	/** "Returns coolant tank fill, hot coolant tank fill, and emergency buffer fill in order." */
	public int[] getTankFills() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new int[] {core.tanks[0].getFill(), core.tanks[1].getFill(), core.tanks[2].getFill()};
		return new int[] {0, 0, 0};
	}

	/** The three tanks' capacities (D-3076: upstream's body answered the fills, whatever its doc said). */
	public int[] getTankCapacities() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new int[] {core.tanks[0].getMaxFill(), core.tanks[1].getMaxFill(), core.tanks[2].getMaxFill()};
		return new int[] {0, 0, 0};
	}

	/** The control panel's two in-events, by upstream's names. */
	public void receiveEvent(String event, double level, @Nullable String name) {
		PWRData core = getCoreByCorePos();
		if(core == null) return;
		if(event.equals("pwr_computer_set_master")) {
			core.masterControl = Mth.clamp(level / 100, 0, 1);
			core.manipulateRod(null);
			core.sendControlPositions();
		} else if(event.equals("pwr_computer_set_level") && name != null) {
			double value = Mth.clamp(level / 100, 0, 1);
			if(!core.controlDemand.containsKey(name) && !groupExists(core, name)) return;
			core.controlDemand.put(name, value);
			core.manipulateRod(name);
			core.sendControlPositions();
		}
	}

	private boolean groupExists(PWRData core, String name) {
		for(BlockPos pos : core.controls) {
			if(level.getBlockEntity(pos) instanceof PWRControlTE control && control.name.equals(name)) return true;
		}
		return false;
	}

	/** (lowest, highest, average) heat over the fuel columns holding a rod; 20, 20, 0 with none. */
	Triplet<Double, Double, Double> getTemperatures(PWRData core) {
		boolean firstMin = true;
		boolean firstMax = true;
		double min = 20;
		double max = 20;
		double avg = 0;
		double divide = 0;
		for(BlockPos fuel : core.fuels) {
			if(level.getBlockEntity(fuel) instanceof PWRElementTE element) {
				ItemStack rod = element.inventory.getStackInSlot(0);
				if(rod.getItem() instanceof LeafiaRodItem) {
					CompoundTag compound = LeafiaRodItem.data(rod);
					double heat = 20;
					if(compound != null) heat = compound.getDouble("heat");
					divide++;
					avg += heat;
					min = Math.min(heat, min);
					max = Math.max(heat, max);
					if(firstMin) {
						min = heat;
						firstMin = false;
					}
					if(firstMax) {
						max = heat;
						firstMax = false;
					}
				}
			}
		}
		if(divide > 0) avg /= divide;
		return new Triplet<>(min, max, avg);
	}

	/** The control panel's ten query values, by upstream's names. */
	public Map<String, Float> queryValues() {
		Map<String, Float> map = new HashMap<>();
		PWRData core = getCoreByCorePos();
		map.put("master_level", 0F);
		map.put("temp_lowest", 20F);
		map.put("temp_highest", 20F);
		map.put("temp_average", 0F);
		map.put("tank_coolant", 0F);
		map.put("tank_hotcoolant", 0F);
		map.put("tank_e_buffer", 0F);
		map.put("capacity_coolant", 0F);
		map.put("capacity_hotcoolant", 0F);
		map.put("capacity_e_buffer", 0F);
		if(core != null) {
			map.put("master_level", (float) (core.masterControl * 100));
			Triplet<Double, Double, Double> temps = getTemperatures(core);
			map.put("temp_lowest", temps.getX().floatValue());
			map.put("temp_highest", temps.getY().floatValue());
			map.put("temp_average", temps.getZ().floatValue());
			map.put("tank_coolant", (float) core.tanks[0].getFill());
			map.put("tank_hotcoolant", (float) core.tanks[1].getFill());
			map.put("tank_e_buffer", (float) core.tanks[2].getFill());
			map.put("capacity_coolant", (float) core.tanks[0].getMaxFill());
			map.put("capacity_hotcoolant", (float) core.tanks[1].getMaxFill());
			map.put("capacity_e_buffer", (float) core.tanks[2].getMaxFill());
		}
		return map;
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's ten query keys, from {@link #queryValues()}. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.floats(queryValues());
	}

	@Override
	public List<String> getInEvents() {
		return Arrays.asList("pwr_computer_set_master", "pwr_computer_set_level");
	}

	/** The two in-events, percent clamped to 0..100 by {@link #receiveEvent(String, double, String)}; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		double level = ControllableLifecycle.number(e, "level");
		if(Double.isNaN(level))
			return;
		if(e.name.equals("pwr_computer_set_master"))
			receiveEvent(e.name, level, null);
		else if(e.name.equals("pwr_computer_set_level")) {
			DataValue name = e.vars.get("name");
			if(name != null)
				receiveEvent(e.name, level, name.toString());
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
