package com.leafia.contents.machines.reactors.lftr.components.ejector;

import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The LFTR Fuel Ejector: salt out of the reactor, through its front.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/ejector/MSREjectorTE.java}.
 * A 1,000 mB MSR block that, after its ordinary tick ({@link MSRTEBase#update}), offers its whole tank to
 * whatever is in front of it ({@link #getDirection}, any of six ways): a duct of the salt's fluid, or a
 * receiver directly. A NeoForge handler on its front (and on no other side, {@code LeafiaCapabilities})
 * can drain it and cannot fill it. Upstream's {@code fillFluid}, a commented-out {@code FFUtils} call,
 * is not here.
 */
public class MSREjectorTE extends MSRTEBase implements IFFProvider {

	public MSREjectorTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tank = new FluidTank(1000);
	}

	public Direction getDirection() {
		BlockState state = level.getBlockState(worldPosition);
		if(state.getBlock() instanceof MSREjectorBlock)
			return state.getValue(MSREjectorBlock.FACING);
		return Direction.NORTH;
	}

	@Override
	public void update() {
		super.update();
		if(level != null && !level.isClientSide && !isRemoved()) {
			Direction direction = getDirection();
			tryProvide(tank, level, worldPosition.relative(direction), direction);
		}
	}

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return tank;
	}

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int i) {
		return tank.getFluid();
	}

	@Override
	public int getTankCapacity(int i) {
		return tank.getCapacity();
	}

	@Override
	public boolean isFluidValid(int i, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return tank.drain(resource, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return tank.drain(maxDrain, action);
	}

	@Override
	public String getPacketIdentifier() {
		return "LFTR_EJECTOR";
	}
}
