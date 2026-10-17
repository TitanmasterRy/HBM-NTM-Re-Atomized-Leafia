package com.leafia.contents.machines.reactors.pwr.blocks.components.port;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR fluid port's block entity: takes coolant (tank 0) and water (tank 3) from any pipe, and gives
 * hot coolant (tank 1) and steam (tank 4) to any pipe, on all six faces.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/port/PWRPortTE.java}. Its
 * per-face {@code trySubscribe}/{@code tryProvide} calls are {@code autoPort(ALL_AROUND)}, the base mod's
 * idiom for the same thing, which reads the same four tanks. Upstream's commented-out Forge fluid
 * capability is not carried.
 */
public class PWRPortTE extends PWRAssignableEntity implements IFluidStandardReceiverMK2, IFluidStandardSenderMK2 {

	public PWRPortTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new FluidTank[] {core.tanks[0], core.tanks[3]};
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new FluidTank[] {core.tanks[1], core.tanks[4]};
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return core.tanks;
		return new FluidTank[0];
	}

	@Override
	public void tick() {
		super.tick();
		if(!level.isClientSide && getCoreByCorePos() != null) autoPort(ALL_AROUND);
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_PORT";
	}
}
