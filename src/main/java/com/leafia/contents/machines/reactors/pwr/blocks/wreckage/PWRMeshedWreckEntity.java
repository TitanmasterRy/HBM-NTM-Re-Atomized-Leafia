package com.leafia.contents.machines.reactors.pwr.blocks.wreckage;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Erosion;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Variation;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisItem;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wreck's memory: the block it replaced, and how broken and how burnt it is.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/wreckage/PWRMeshedWreckEntity.java}.
 * The three numbers pack into upstream's byte, {@code [v v v|E E|s s s]} - variation, erosion, scorch -
 * saved as {@code detail} ({@link #toBits}, {@link #fromBits}), and the replaced block is saved as
 * {@code block} (its registry name, upstream's key) plus {@code state}, which takes the place of
 * upstream's {@code meta}.
 *
 * <p>Upstream's own {@code LeafiaPacket} sync (sent on {@code markDirty} and on a player's validate, to
 * 2048 blocks - "Crisps computer :D") is vanilla's block entity update packet here: the client needs the
 * same three things, once, and whenever they change.
 */
public class PWRMeshedWreckEntity extends BlockEntity {

	/** The block this wreck replaced. Null until set (upstream's null {@code resourceLocation}). */
	@Nullable
	public BlockState display = null;
	/** 0 to 7. */
	public int scorch = 0;
	/** 0 to 7: an index into the erosion's list. */
	public int variation = 0;
	public Erosion erosion = Erosion.NORMAL;

	public PWRMeshedWreckEntity(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.PWR_WRECK.get(), pos, state);
	}

	public byte toBits() {
		return (byte) (scorch + erosion.getBits() + (variation << 5));
	}

	public PWRMeshedWreckEntity fromBits(int bits) {
		scorch = bits & 0b111;
		erosion = Erosion.values()[(bits >> 3) & 0b11];
		variation = (bits >> 5) & 0b111;
		return this;
	}

	/** The variation drawn, or null when this block is not a wreck (any more). */
	@Nullable
	public Variation getVariation() {
		Block block = getBlockState().getBlock();
		if(!(block instanceof PWRMeshedWreck wreck)) return null;
		Variation[] list = wreck.getVariations().list(erosion);
		return variation >= 0 && variation < list.length ? list[variation] : null;
	}

	/** Upstream's {@code markDirty} override: save, and send the client the three numbers and the block. */
	public void sync() {
		setChanged();
		if(level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("state"))
			display = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), compound.getCompound("state"));
		else if(compound.contains("block"))
			display = PWRDebrisItem.blockOf(compound.getString("block")).defaultBlockState();
		if(compound.contains("detail")) fromBits(compound.getByte("detail"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		if(display != null) {
			compound.putString("block", PWRDebrisItem.blockName(display));
			compound.put("state", NbtUtils.writeBlockState(display));
		}
		compound.putByte("detail", toBits());
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
