package com.leafia.contents.machines.reactors.pwr.blocks.components;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of a PWR component that may hold the reactor: the computer, the control rod, the
 * OCCS inlet and outlet, and the port (and PWR-2's terminal).
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/PWRAssignableEntity.java}.
 * The NBT keys ({@code corePosX/Y/Z}, {@code data}) are upstream's. Three things changed shape:
 *
 * <ul>
 * <li><b>Unloading is not removal.</b> 1.21.1 calls {@code setRemoved} on a chunk unload too; upstream's
 * {@code invalidate} - which asks {@link PWRData#invalidate} whether corium replaced the core - runs only
 * on a real removal here, as on {@code MTCoreTE}.</li>
 * <li><b>The client sync also rides vanilla's update tag</b>, because {@code onPlayerValidate} has no
 * caller in this port (D-1029): a client loading the chunk gets the core's data with the block entity.
 * {@link #onPlayerValidate} is implemented anyway.</li>
 * <li>{@code PWRData} needs registries to save the resource slots, so it is given the level's.</li>
 * </ul>
 */
public abstract class PWRAssignableEntity extends BlockEntityLoadedBase implements PWRComponentEntity, LeafiaPacketReceiver {

	protected BlockPos corePos = null;
	protected PWRData data = null;
	private boolean unloading = false;

	protected PWRAssignableEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean canAssignCore() {
		return true;
	}

	@Override
	public void setCoreLink(@Nullable BlockPos pos) {
		corePos = pos;
	}

	@Nullable
	public BlockPos getCoreLink() {
		return corePos;
	}

	@Override
	public PWRData getCoreByCorePos() {
		return PWRComponentEntity.getCoreFromPos(level, corePos);
	}

	@Override
	public void assignCore(@Nullable PWRData data) {
		if(this.data != data && level != null)
			PWRData.addDataToPacket(LeafiaPacket._start(this), data).__sendToAffectedClients();
		this.data = data;
	}

	@Override
	public PWRData getCore() {
		return data;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(compound.contains("corePosX"))
			corePos = new BlockPos(compound.getInt("corePosX"), compound.getInt("corePosY"), compound.getInt("corePosZ"));
		super.loadAdditional(compound, registries);
		if(compound.contains("data")) {
			data = new PWRData(this);
			data.readFromNBT(compound, registries);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(corePos != null) {
			compound.putInt("corePosX", corePos.getX());
			compound.putInt("corePosY", corePos.getY());
			compound.putInt("corePosZ", corePos.getZ());
		}
		if(data != null) data.writeToNBT(compound, registries);
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onChunkUnloaded() {
		unloading = true;
		super.onChunkUnloaded();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(!unloading && this.data != null) this.data.invalidate(level);
	}

	/** Upstream's {@code update()}: the core's data ticks with it, on both sides. */
	public void tick() {
		if(this.data != null) this.data.update();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, PWRAssignableEntity entity) {
		entity.tick();
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 31) data = PWRData.tryLoadFromPacket(this, value);
		if(this.data != null) this.data.onReceivePacketLocal(key, value);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(this.data != null) this.data.onReceivePacketServer(key, value, plr);
	}

	protected LeafiaPacket.Builder addDataToPacket(LeafiaPacket.Builder packet) {
		if(this.data != null) PWRData.addDataToPacket(packet, this.data);
		return packet;
	}

	@Override
	public void onPlayerValidate(Player plr) {
		addDataToPacket(LeafiaPacket._start(this)).__sendToClient(plr);
	}
}
