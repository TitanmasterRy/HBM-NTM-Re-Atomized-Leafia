package com.leafia.contents.network.ff_duct.utility;

import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Forge-fluid duct's in-line utilities share: the pump, the filter and the converter each
 * sit in a duct run set to one fluid.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/FFDuctUtilityTEBase.java}, line for
 * line. The fluid travels on the addon's own packet channel ({@link LeafiaPacket}) under key 31, as
 * the duct's did upstream: sent to everyone near when it changes, and to one player when that player's
 * client asks ({@link #onPlayerValidate}). Saved as the NTM fluid's id under {@code "fluid"}.
 *
 * <p>Upstream's {@code getType()} is {@link #getFluidType()}: a block entity's {@code getType} is its
 * {@code BlockEntityType} in 1.21.1.
 *
 * <h2>Two additions to upstream's class (D-3019)</h2>
 *
 * <ul>
 * <li><b>The type reaches a client the way the duct's does</b> (D-3012): vanilla's block entity update tag, on
 *     chunk load and on {@code sendBlockUpdated}, with a redraw when it changes. {@link #onPlayerValidate} is
 *     asked for too since the validate sweep landed (D-3040), but later than the chunk, and the utilities are drawn by a
 *     baked model whose colour handler reads {@link #getFluidType()}: a client that did not know the type
 *     would draw every utility white until the next packet. The {@code LeafiaPacket} on key 31 is kept as
 *     upstream has it.</li>
 * <li><b>{@link #setType} marks the chunk dirty.</b> Upstream does not, so a type set by an identifier is lost
 *     if nothing else dirties the chunk before it is saved.</li>
 * </ul>
 */
public abstract class FFDuctUtilityTEBase extends BlockEntity implements LeafiaPacketReceiver {

	protected FluidType type = Fluids.NONE;

	protected FFDuctUtilityTEBase(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state) {
		super(blockEntityType, pos, state);
	}

	public void setType(FluidType type) {
		this.type = type;
		sendTypeUpdatePacket();
		setChanged();
		if(level != null && !level.isClientSide) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	public FluidType getFluidType() {
		return type;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		type = Fluids.fromID(compound.getInt("fluid"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putInt("fluid", type.getID());
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		FluidType before = this.type;
		super.onDataPacket(net, pkt, registries);
		redrawIfChanged(before);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		FluidType before = this.type;
		super.handleUpdateTag(tag, registries);
		redrawIfChanged(before);
	}

	/** The colour handler reads the type when a chunk section is meshed, so a new type needs a re-mesh. */
	protected void redrawIfChanged(FluidType before) {
		if(level != null && level.isClientSide && before != this.type) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** Upstream's {@code ITickable.update}: one server tick of the utility. */
	public abstract void update();

	/** Lines the utility adds to its look overlay. None by default. */
	public void addInfo(List<String> info) {}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 31) {
			FluidType before = type;
			type = Fluids.fromID((int) value);
			redrawIfChanged(before);
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {}

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket._start(this).__write(31, type.getID()).__sendToClient(plr);
	}

	public void sendTypeUpdatePacket() {
		if(level == null || level.isClientSide) return;
		LeafiaPacket._start(this).__write(31, type.getID()).__sendToAffectedClients();
	}
}
