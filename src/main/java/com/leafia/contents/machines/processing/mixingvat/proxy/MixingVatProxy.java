package com.leafia.contents.machines.processing.mixingvat.proxy;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.leafia.contents.machines.processing.mixingvat.MixingVatTE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * One of the mixing vat's three port blocks: it passes power to the vat and is the Forge-fluid handler the vat shows
 * on one side.
 *
 * <p>Upstream: {@code .../mixingvat/proxy/MixingVatProxy.java}, a {@code TileEntityProxyBase} that is an
 * {@code IEnergyReceiverMK2} and an {@code IFluidHandler}. Here it is the base mod's
 * {@link BlockEntityProxyCombo} with power on - which is the energy half, forwarding to the core exactly as
 * upstream's hand-written methods did - plus the fluid half below.
 *
 * <h2>Where the ports are</h2>
 *
 * The three extras are {@code core + right} (beside the core, in the back row), and the two blocks of the front row
 * (two blocks forward: {@code core + 2 front} and {@code core + 2 front + right}). Upstream finds out which one it is by
 * putting its own position in the core's object space: {@link #frontDistance} is how many blocks it is in front of the
 * core, {@link #rightDistance} how many to its right.
 *
 * <ul>
 * <li><b>The back row</b> (nothing in front of the core) takes salt - {@link MixingVatTE#fill} - and its side is the
 *     machine's back. It cannot be drained.</li>
 * <li><b>The front row</b> (more than one block in front) gives the output tank's salt - {@link MixingVatTE#drainOut} -
 *     and cannot be filled. The left block's side is the machine's left and the right block's is its right.</li>
 * </ul>
 *
 * <p>The fluid is shown on that one side or to an unsided query, as upstream's {@code hasCapability}. The two thresholds are
 * strict and in opposite directions - fill below one block, drain beyond one - so the row at exactly one block would do neither;
 * the machine has no such row.
 */
public class MixingVatProxy extends BlockEntityProxyCombo implements IFluidHandler {

	public MixingVatProxy(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		power();
	}

	@Nullable
	public MixingVatTE vat() {
		return getTile() instanceof MixingVatTE vat ? vat : null;
	}

	/** Blocks in front of the core: 0 for the back row, 2 for the front row. */
	public int frontDistance(MixingVatTE vat) {
		Direction front = vat.getFacingForward();
		BlockPos d = worldPosition.subtract(vat.getBlockPos());
		return d.getX() * front.getStepX() + d.getZ() * front.getStepZ();
	}

	/** Blocks to the core's right (looking toward its front): 0 or 1. */
	public int rightDistance(MixingVatTE vat) {
		Direction right = vat.getFacingForward().getClockWise();
		BlockPos d = worldPosition.subtract(vat.getBlockPos());
		return d.getX() * right.getStepX() + d.getZ() * right.getStepZ();
	}

	/**
	 * The side this port faces: the back for the back row, else right or left by which half of the front row it
	 * is in ({@code relative.x > 0.5} upstream).
	 */
	@Nullable
	public Direction facing() {
		MixingVatTE vat = vat();
		if(vat == null) return null;
		if(frontDistance(vat) < 1) return vat.getFacing();
		return rightDistance(vat) > 0.5 ? vat.getFacingForward().getClockWise() : vat.getFacingForward().getClockWise().getOpposite();
	}

	// ---- the Forge handler -----------------------------------------------------------------

	@Override
	public int getTanks() {
		MixingVatTE vat = vat();
		return vat == null ? 0 : vat.getTanks();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		MixingVatTE vat = vat();
		return vat == null ? FluidStack.EMPTY : vat.getFluidInTank(tank);
	}

	@Override
	public int getTankCapacity(int tank) {
		MixingVatTE vat = vat();
		return vat == null ? 0 : vat.getTankCapacity(tank);
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		MixingVatTE vat = vat();
		return vat != null && vat.isFluidValid(tank, stack);
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		MixingVatTE vat = vat();
		if(vat == null) return 0;
		if(frontDistance(vat) < 1)
			return vat.fill(resource, action);
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		MixingVatTE vat = vat();
		if(vat == null) return FluidStack.EMPTY;
		if(frontDistance(vat) > 1)
			return vat.drainOut(resource, action);
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		MixingVatTE vat = vat();
		if(vat == null) return FluidStack.EMPTY;
		if(frontDistance(vat) > 1)
			return vat.drainOut(maxDrain, action);
		return FluidStack.EMPTY;
	}
}
