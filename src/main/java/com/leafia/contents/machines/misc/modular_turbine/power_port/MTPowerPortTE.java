package com.leafia.contents.machines.misc.modular_turbine.power_port;

import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;

import api.hbm.energymk2.IEnergyProviderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The electricity port's block entity: a window onto the turbine core's output.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/power_port/MTPowerPortTE.java}.
 *
 * <h2>It stores nothing</h2>
 *
 * Every tick it finds the core of whatever turbine part is behind it, and its power, maximum and
 * setter all read and write that core's {@code powerOutput} — the HE the shaft generated this
 * tick. So the network drains the core directly, and whatever it does not take is gone next tick
 * when the core recomputes. A turbine with no network attached simply wastes its output, which is
 * upstream's design: the turbine is a generator, not a battery.
 *
 * <p>It only connects on its facing, and it looks for the turbine one block the other way.
 */
public class MTPowerPortTE extends BlockEntity implements IEnergyProviderMK2 {

	public MTPowerPortTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public Direction getDirection() {
		BlockState state = level.getBlockState(worldPosition);
		if(state.getBlock() instanceof MTPowerPortBlock)
			return state.getValue(MTPowerPortBlock.FACING);
		return Direction.DOWN;
	}

	@Override
	public boolean canConnect(Direction dir) {
		return dir.equals(getDirection());
	}

	/** The core of the turbine part behind the port, or the core itself, or null. */
	public MTCoreTE getCore() {
		BlockPos p = worldPosition.relative(getDirection().getOpposite());
		if(level.getBlockState(p).getBlock() instanceof ModularTurbineBlockBase base) {
			BlockPos core = base.findCore(level, p);
			if(core != null) {
				BlockEntity te = level.getBlockEntity(core);
				if(te instanceof ModularTurbineComponentTE component)
					return component.core;
				else if(te instanceof MTCoreTE corete)
					return corete;
			}
		}
		return null;
	}

	MTCoreTE lastCore;

	@Override
	public long getPower() {
		if(lastCore != null && !lastCore.isRemoved())
			return lastCore.powerOutput;
		return 0;
	}

	@Override
	public void setPower(long power) {
		if(lastCore != null && !lastCore.isRemoved())
			lastCore.powerOutput = power;
	}

	@Override
	public long getMaxPower() {
		if(lastCore != null && !lastCore.isRemoved())
			return lastCore.powerOutput;
		return 0;
	}

	boolean isLoaded = true;

	@Override
	public void onChunkUnloaded() {
		isLoaded = false;
		super.onChunkUnloaded();
	}

	@Override
	public boolean isLoaded() {
		return isLoaded;
	}

	/** Upstream's {@code update()}. */
	public void updateEntity() {
		if(!level.isClientSide) {
			lastCore = getCore();
			Direction dir = getDirection();
			tryProvide(level, worldPosition.relative(dir), dir);
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MTPowerPortTE port) {
		port.updateEntity();
	}
}
