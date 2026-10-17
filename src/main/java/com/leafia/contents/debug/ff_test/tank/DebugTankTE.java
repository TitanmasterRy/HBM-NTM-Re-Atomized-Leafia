package com.leafia.contents.debug.ff_test.tank;

import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The debug Forge-fluid tank: 10,000 mB that a duct run fills and nothing drains.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/ff_test/tank/DebugTankTE.java}. Every server tick it subscribes on all
 * six sides to whatever duct is there, asking with FLiBe while it is empty ({@link IFFReceiver#trySubscribe}; the
 * stand-in stack is 1 mB, not upstream's 0, for the reason that interface gives), and sends its tank under key 0 for
 * the look overlay. It takes any fluid ({@link #fill}) and gives none. Upstream does not save the tank and neither does
 * this: it is a meter, emptied by breaking it.
 */
public class DebugTankTE extends BlockEntity implements IFFReceiver, LeafiaPacketReceiver {

	public static final int CAPACITY = 10000;

	public final FluidTank zaza = new FluidTank(CAPACITY);

	public DebugTankTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public DebugTankTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.FF_TANK.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, DebugTankTE te) {
		te.update();
	}

	/** What a subscription asks with while the tank is empty: upstream's {@code new FluidStack(AddonFF.fluoride, 0)}. */
	public static FluidStack askWith() {
		Fluid ff = LeafiaFluids.FLUORIDE == null ? null : LeafiaFF.getFF(LeafiaFluids.FLUORIDE);
		return ff == null ? FluidStack.EMPTY : new FluidStack(ff, 1);
	}

	/** Upstream's {@code update}, server side. */
	public void update() {
		if(level == null || level.isClientSide) return;
		FluidStack ask = askWith();
		for(Direction facing : Direction.values())
			if(!ask.isEmpty() || !zaza.getFluid().isEmpty()) trySubscribe(zaza, ask, level, worldPosition.relative(facing), facing);
		LeafiaPacket._start(this).__write(0, zaza.writeToNBT(level.registryAccess(), new CompoundTag())).__sendToAffectedClients();
	}

	@Override
	public String getPacketIdentifier() {
		return "debug_ff_tank";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0 && level != null) {
			zaza.setFluid(FluidStack.EMPTY);
			zaza.readFromNBT(level.registryAccess(), (CompoundTag) value);
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	@Override
	public FluidTank getCorrespondingTank(FluidStack stack) {
		return zaza;
	}

	/// IFluidHandler ///

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return zaza.getFluid();
	}

	@Override
	public int getTankCapacity(int tank) {
		return zaza.getCapacity();
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return zaza.fill(resource, action);
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
