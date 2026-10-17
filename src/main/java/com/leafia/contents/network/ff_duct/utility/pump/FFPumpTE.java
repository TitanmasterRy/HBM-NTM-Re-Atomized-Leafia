package com.leafia.contents.network.ff_duct.utility.pump;

import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Special Pipe Pump: a 10,000 mB Forge tank that sits in a duct run, or between a run and a
 * machine that is not on one.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/pump/FFPumpTE.java}. Every tick, with its
 * fluid set (an identifier, {@link #setType}), the first of these that applies - and only the first:
 *
 * <ol>
 * <li><b>Pull from behind.</b> If the block behind is not another utility and answers a fluid handler on the
 *     side facing the pump, the pump asks it (simulated) for as much of its fluid as it has room for. If what
 *     it would give is its fluid and the tank would take some, it takes it, then offers its tank to whatever is
 *     in front. This is the whole of a pump drawing from a tank, a vat or an ejector.</li>
 * <li><b>Push ahead.</b> Otherwise, if the block in front is not another utility and answers a handler, the
 *     pump fills it with its whole tank. While anything is left it offers that to the block behind; once
 *     empty it subscribes to the block behind.</li>
 * <li><b>Duct to duct.</b> Otherwise, with a Forge duct on both sides, it provides to the front duct and
 *     subscribes to the back one. This is the pump as a buffer: fluid in at the back, fluid out of the front,
 *     both through the network.</li>
 * </ol>
 *
 * The handler it exposes is a decoy, as upstream's: no tanks, nothing fills and nothing drains. The pump is a
 * machine end of a duct ({@link IFFProvider}, {@link IFFReceiver}) and the capability exists on the facing's axis
 * so a duct will join it, not so anything can use it ({@code LeafiaCapabilities}). Changing the fluid empties the
 * tank. The tank, with its tag, goes to every client near each tick for the look overlay (key 0 the amount, key 1
 * the tag).
 *
 * <p><b>Upstream's quirks, kept.</b> The pull in 1 asks for a plain stack of the pump's fluid, and 1.12.2's
 * {@code FluidTank.drain(FluidStack)} matches tags as 1.21.1's matches components - so a pump does not draw a
 * tagged salt out of a tank, only out of one that holds it untagged. Branch 2 returns as soon as the block in front
 * answers a handler, whether or not anything moved: a pump facing a handler that takes nothing never reaches 3.
 */
public class FFPumpTE extends FFDuctUtilityTEBase implements IFFProvider, IFFReceiver {

	public final FluidTank tank = new FluidTank(10000);

	public FFPumpTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void setType(FluidType type) {
		super.setType(type);
		tank.drain(tank.getCapacity() * 2, IFluidHandler.FluidAction.EXECUTE);
	}

	/** The facing, or north for a state that is not a pump's (a stale block entity). */
	public Direction getFacing() {
		BlockState state = getBlockState();
		return state.getBlock() instanceof FFPumpBlock ? state.getValue(FFDuctUtilityBase.FACING) : Direction.NORTH;
	}

	void doUpdate() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof FFPumpBlock)) return;

		Direction facing = state.getValue(FFDuctUtilityBase.FACING);
		Direction opposite = facing.getOpposite();
		BlockPos behindPos = worldPosition.relative(opposite);
		BlockPos aheadPos = worldPosition.relative(facing);
		net.minecraft.world.level.material.Fluid ff = LeafiaFF.getFF(getFluidType());

		BlockEntity behind = level.getBlockEntity(behindPos);
		if(behind != null && !(behind instanceof FFDuctUtilityTEBase)) {
			IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, behindPos, facing);
			if(handler != null) {
				FluidStack stack = new FluidStack(ff, tank.getCapacity() - tank.getFluidAmount());
				FluidStack drainlol = handler.drain(stack, IFluidHandler.FluidAction.SIMULATE);
				if(!drainlol.isEmpty() && LeafiaFF.fromFF(drainlol.getFluid()) == getFluidType()) {
					if(tank.fill(drainlol, IFluidHandler.FluidAction.SIMULATE) > 0)
						tank.fill(handler.drain(stack, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
					tryProvide(tank, level, aheadPos, facing);
					return;
				}
			}
		}

		BlockEntity ahead = level.getBlockEntity(aheadPos);
		if(ahead != null && !(ahead instanceof FFDuctUtilityTEBase)) {
			IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, aheadPos, opposite);
			if(handler != null) {
				tank.drain(handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
				if(tank.getFluidAmount() > 0)
					tryProvide(tank, level, behindPos, opposite);
				else
					trySubscribe(tank, new FluidStack(ff, 1), level, behindPos, opposite);
				return;
			}
		}

		if(ahead instanceof FFDuctTE && behind instanceof FFDuctTE) {
			tryProvide(tank, level, aheadPos, facing);
			trySubscribe(tank, new FluidStack(ff, 1), level, behindPos, opposite);
		}
	}

	@Override
	public void update() {
		doUpdate();
		LeafiaPacket.Builder packet = LeafiaPacket._start(this).__write(0, tank.getFluidAmount());
		if(!tank.getFluid().isEmpty() && LeafiaFF.hasTag(tank.getFluid()))
			packet.__write(1, LeafiaFF.getTag(tank.getFluid()));
		packet.__sendToAffectedClients();
	}

	@Override
	public void addInfo(List<String> info) {
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_pump.buffer", tank.getFluidAmount() + "mB"));
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		tank.readFromNBT(registries, compound.getCompound("buffer"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.put("buffer", tank.writeToNBT(registries, new CompoundTag()));
		super.saveAdditional(compound, registries);
	}

	@Override
	public String getPacketIdentifier() {
		return "FF_PUMP";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) {
			net.minecraft.world.level.material.Fluid ff = LeafiaFF.getFF(getFluidType());
			if(ff == null)
				tank.setFluid(FluidStack.EMPTY);
			else
				tank.setFluid(new FluidStack(ff, (int) value));
		} else if(key == 1 && !tank.getFluid().isEmpty())
			LeafiaFF.setTag(tank.getFluid(), (CompoundTag) value);
	}

	@Override
	public FluidTank getCorrespondingTank(FluidStack stack) {
		return tank;
	}

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return tank;
	}

	// The handler is a decoy: upstream reports no tank properties and refuses every fill and drain.

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
}
