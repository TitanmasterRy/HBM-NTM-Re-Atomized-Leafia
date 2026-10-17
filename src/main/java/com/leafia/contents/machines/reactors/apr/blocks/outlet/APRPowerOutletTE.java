package com.leafia.contents.machines.reactors.apr.blocks.outlet;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.energymk2.IEnergyProviderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The APR's power port: each tick it holds exactly what the core five blocks above it produces this tick and offers
 * it to the cable below.
 *
 * <p>Upstream: {@code .../apr/blocks/outlet/APRPowerOutletTE.java}. Its buffer is redefined every tick to the
 * core's {@link APRCoreTE#getPowerOutput()} - capacity and contents alike - so what the network does not take this
 * tick is gone next tick, as with the DFC receiver. With no core above it, it keeps offering what it last held.
 * One {@code tryProvide} a tick, down; it is the transfer, so it is not collapsed into {@code autoPort} (D-969).
 */
public class APRPowerOutletTE extends BlockEntityLoadedBase implements IEnergyProviderMK2 {

	public long power = 0;

	public APRPowerOutletTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public APRPowerOutletTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.APR_OUTLET.get(), pos, state);
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public void setPower(long l) {
		power = l;
	}

	@Override
	public long getMaxPower() {
		return power;
	}

	public void update() {
		if(level.getBlockEntity(worldPosition.above(5)) instanceof APRCoreTE core)
			power = core.getPowerOutput();
		tryProvide(level, worldPosition.below(), Direction.DOWN);
	}
}
