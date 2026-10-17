package com.leafia.contents.machines.reactors.lftr.components.arbitrary;

import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The LFTR Arbitrary Block: an MSR block that carries salt like any other and holds one block of the
 * player's choosing, which the reaction chambers treat as if it stood there (graphite moderates, a
 * PWR reflector reflects, a shielding block shields) and which its renderer draws on its six faces.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/arbitrary/MSRArbitraryTE.java}.
 * The held block is a one-slot handler saved under {@code "inventory"}. Every {@code setChanged} on
 * the server sends the whole block entity to nearby clients on the addon's packet channel (key 0), as
 * upstream's {@code markDirty} did; a client that has just loaded the block gets the same through the
 * vanilla update tag, which stands in for {@code onPlayerValidate}. No capability exposes the slot,
 * as upstream exposed none.
 */
public class MSRArbitraryTE extends MSRTEBase {

	public final ItemStackHandler inventory = new ItemStackHandler(1);

	public MSRArbitraryTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.put("inventory", inventory.serializeNBT(registries));
		super.saveAdditional(compound, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(compound.contains("inventory"))
			inventory.deserializeNBT(registries, compound.getCompound("inventory"));
		super.loadAdditional(compound, registries);
	}

	@Override
	public String getPacketIdentifier() {
		return "LFTR_ARB";
	}

	public LeafiaPacket.Builder generateSyncPacket() {
		return LeafiaPacket._start(this).__write(0, saveWithoutMetadata(level.registryAccess()));
	}

	public void syncLocals() {
		generateSyncPacket().__sendToAffectedClients();
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if(level != null && !level.isClientSide)
			syncLocals();
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) {
			if(value instanceof CompoundTag nbt && level != null)
				loadAdditional(nbt, level.registryAccess());
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {}

	@Override
	public void onPlayerValidate(Player plr) {
		generateSyncPacket().__sendToClient(plr);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}
}
