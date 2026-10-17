package com.leafia.contents.network.ff_duct.utility.converter;

import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Special Pipe Adapter: where the base mod's fluid network ends and a Forge duct run begins.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/converter/FFConverterTE.java}. It holds the
 * fluid twice: an NTM tank of 10,000 mB ({@code ntmf}, a base {@link com.hbm.inventory.fluid.tank.FluidTank}) on
 * the <b>back</b> and a Forge tank of 10,000 mB ({@code ff}) on the <b>front</b>, both of the utility's fluid
 * (set by an identifier; changing it empties both). Every tick, with a fluid whose Forge twin exists:
 *
 * <ol>
 * <li>offer the Forge tank to the Forge duct (or machine) in front ({@link #tryProvide});</li>
 * <li>subscribe, as a receiver of the base mod's network, to the NTM duct behind - which is how fluid gets into
 *     {@code ntmf}, by the base network's own transfer into {@link #getReceivingTanks()};</li>
 * <li>move as much of {@code ntmf} into {@code ff} as fits, one millibucket for one. Nothing changes the
 *     amount: the bridge between the two worlds is 1:1, and the tag of the Forge stack stays what it was
 *     (none).</li>
 * </ol>
 *
 * The step order is upstream's: the network is asked first, and the conversion runs after it, so fluid that
 * arrived last tick crosses this tick. The NTM tank is also declared a sender, but nothing offers it
 * ({@code tryProvide} on the back is commented out upstream), so the conversion only goes one way. The handler it
 * exposes is a decoy, as the pump's, on the facing's axis.
 *
 * <p>The tanks go to every client near each tick for the look overlay (key 0 the Forge amount, key 1 its tag, key 2
 * the NTM amount).
 */
public class FFConverterTE extends FFDuctUtilityTEBase implements IFluidHandler, IFFProvider, IFluidStandardReceiverMK2, IFluidStandardSenderMK2 {

	public final FluidTank ff = new FluidTank(10000);
	public final com.hbm.inventory.fluid.tank.FluidTank ntmf = new com.hbm.inventory.fluid.tank.FluidTank(Fluids.NONE, 10000);

	/** Upstream's {@code loaded}: cleared on unload so the base network drops the subscription. */
	private boolean loaded = true;

	public FFConverterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void setType(FluidType type) {
		super.setType(type);
		ff.drain(ff.getCapacity() * 2, IFluidHandler.FluidAction.EXECUTE);
		ntmf.setTankType(type);
	}

	/** The facing, or north for a state that is not a converter's. */
	public Direction getFacing() {
		BlockState state = getBlockState();
		return state.getBlock() instanceof FFConverterBlock ? state.getValue(FFDuctUtilityBase.FACING) : Direction.NORTH;
	}

	void doUpdate() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		BlockState state = level.getBlockState(worldPosition);
		if(state.getBlock() instanceof FFConverterBlock) {
			Direction facing = state.getValue(FFDuctUtilityBase.FACING);
			tryProvide(ff, level, worldPosition.relative(facing), facing);
			// upstream: if (ntmf.getFill() > 0) tryProvide(ntmf, ..., behind) else - commented out, the NTM tank only receives
			trySubscribe(getFluidType(), level, worldPosition.relative(facing.getOpposite()), facing.getOpposite());
		}
	}

	@Override
	public void update() {
		if(LeafiaFF.getFF(ntmf.getTankType()) != null) {
			doUpdate();
			int filled = ff.fill(LeafiaFF.toFF(ntmf.getTankType(), ntmf.getFill()), IFluidHandler.FluidAction.EXECUTE);
			if(filled > 0)
				ntmf.setFill(ntmf.getFill() - filled);
		}
		LeafiaPacket.Builder packet = LeafiaPacket._start(this).__write(0, ff.getFluidAmount());
		if(!ff.getFluid().isEmpty() && LeafiaFF.hasTag(ff.getFluid()))
			packet.__write(1, LeafiaFF.getTag(ff.getFluid()));
		packet.__write(2, ntmf.getFill());
		packet.__sendToAffectedClients();
	}

	@Override
	public void addInfo(List<String> info) {
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_converter.ntmf", ntmf.getFill() + "mB"));
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_converter.ff", ff.getFluidAmount() + "mB"));
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		ff.readFromNBT(registries, compound.getCompound("ff"));
		ntmf.readFromNBT(compound, "ntmf");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.put("ff", ff.writeToNBT(registries, new CompoundTag()));
		ntmf.writeToNBT(compound, "ntmf");
		super.saveAdditional(compound, registries);
	}

	@Override
	public String getPacketIdentifier() {
		return "FF_CNVRTR";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) {
			net.minecraft.world.level.material.Fluid forge = LeafiaFF.getFF(getFluidType());
			if(forge == null)
				ff.setFluid(FluidStack.EMPTY);
			else
				ff.setFluid(new FluidStack(forge, (int) value));
		} else if(key == 1 && !ff.getFluid().isEmpty())
			LeafiaFF.setTag(ff.getFluid(), (CompoundTag) value);
		else if(key == 2)
			ntmf.setFill((int) value);
	}

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return ff;
	}

	// The Forge handler is a decoy: upstream reports no tank properties and refuses every fill and drain.

	@Override
	public int getTanks() {
		return 0;
	}

	@Override
	public FluidStack getFluidInTank(int i) {
		return FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int i) {
		return 0;
	}

	@Override
	public boolean isFluidValid(int i, FluidStack stack) {
		return false;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return FluidStack.EMPTY;
	}

	// The base mod's network side.

	@Override
	public com.hbm.inventory.fluid.tank.FluidTank[] getReceivingTanks() {
		return new com.hbm.inventory.fluid.tank.FluidTank[] { ntmf };
	}

	@Override
	public com.hbm.inventory.fluid.tank.FluidTank[] getAllTanks() {
		return new com.hbm.inventory.fluid.tank.FluidTank[] { ntmf };
	}

	@Override
	public com.hbm.inventory.fluid.tank.FluidTank[] getSendingTanks() {
		return new com.hbm.inventory.fluid.tank.FluidTank[] { ntmf };
	}

	@Override
	public void onChunkUnloaded() {
		loaded = false;
		super.onChunkUnloaded();
	}

	@Override
	public boolean isLoaded() {
		return loaded;
	}
}
