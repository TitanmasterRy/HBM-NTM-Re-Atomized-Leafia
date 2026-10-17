package com.leafia.contents.machines.reactors.pwr.blocks.components.occs.inlet;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.container_utility.LeafiaPacket;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR OCCS inlet's block entity: it takes water into the core's OCCS tank (tank 5, 750 mB per fuel column)
 * from any pipe on any side.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/occs/inlet/PWROCCSInTE.java}. Its six
 * per-face {@code trySubscribe} calls are {@code autoPort(ALL_AROUND)}, the base mod's idiom for the same thing.
 * The core's position is sent to clients on every diagnosis (key 0, null allowed) so the look overlay can
 * find the tanks.
 */
public class PWROCCSInTE extends PWRAssignableEntity implements IFluidStandardReceiverMK2, IFluidStandardSenderMK2 {

	public PWROCCSInTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** The linked core, starting a diagnosis first if there is none (which does nothing on a client). */
	@Nullable
	public PWRData getLinkedCoreDiagnosis() {
		PWRData data = getCoreByCorePos();
		if(data == null && getBlockState().getBlock() instanceof PWRComponentBlock pwr) {
			pwr.beginDiagnosis(level, worldPosition, worldPosition);
			data = getCoreByCorePos();
		}
		return data;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new FluidTank[] {core.tanks[5]};
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
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
	public void onDiagnosis() {
		LeafiaPacket._start(this).__write(0, corePos).__sendToAffectedClients();
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) corePos = (BlockPos) value; // Now supports null values!
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_OCCS_IN";
	}

	@Override
	public void onPlayerValidate(Player plr) {
		addDataToPacket(LeafiaPacket._start(this).__write(0, corePos)).__sendToClient(plr);
	}
}
