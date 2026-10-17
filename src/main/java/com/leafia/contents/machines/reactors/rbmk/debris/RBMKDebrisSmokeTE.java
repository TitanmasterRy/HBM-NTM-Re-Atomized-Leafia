package com.leafia.contents.machines.reactors.rbmk.debris;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The smoking debris' size, and the smoke it sends up.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/debris/RBMKDebrisSmokeTE.java}. Its only state
 * is {@code scale}, half the reactor's mean width ({@code ((maxX - minX) + (maxZ - minZ)) / 2 / 2}), saved as
 * {@code scale}. Upstream re-sent it to every client within 4,096 blocks every server tick ("yeah fuck off")
 * and had a new client ask for it on its first tick; here it is the block entity's update tag, which a client
 * gets with the chunk and again whenever {@link #setScale} changes it, so the server does not tick at all.
 *
 * <p>The client puffs once every {@code floor(sqrt(scale)) + 2} ticks: {@code if(timer++ > sqrt(scale))},
 * reset on a puff. Each puff is a {@link ParticleRBMKSmoke} one block above the debris, growing from
 * {@code scale} to {@code 3 x scale}, rising at 0.65, and living {@code (int)(100 s) + rand((int)(50 s)) + 1}
 * ticks where {@code s = scale^0.75}.
 */
public class RBMKDebrisSmokeTE extends BlockEntity {

	float scale = 0;
	int timer = 0;

	public RBMKDebrisSmokeTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = scale;
		this.setChanged();
		if(this.level != null && !this.level.isClientSide)
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
	}

	/** Upstream's {@code timer++ > Math.pow(scale, 0.5)}: whether this tick puffs, given the timer before it counts. */
	public static boolean puffs(int timer, float scale) {
		return timer > Math.pow(scale, 0.5);
	}

	/** {@code (int)(100 x scale^0.75)}: a puff's shortest life, less one. */
	public static int lifeBase(float scale) {
		return (int) (100 * Math.pow(scale, 0.75));
	}

	/**
	 * {@code (int)(50 x scale^0.75)}: the bound of a puff's random extra life. Upstream hands it to
	 * {@code nextInt} unguarded, which throws below a scale of about 0.006 - smaller than any reactor makes,
	 * since the smallest melted reactor with a width is 0.25 - so it is floored at 1 here rather than crash a
	 * client over a hand-edited block.
	 */
	public static int lifeSpread(float scale) {
		return Math.max(1, (int) (50 * Math.pow(scale, 0.75)));
	}

	void clientTick() {
		if(this.scale <= 0) return;
		if(puffs(this.timer++, this.scale)) {
			this.timer = 0;
			int life = lifeBase(this.scale) + this.level.random.nextInt(lifeSpread(this.scale)) + 1;
			ParticleRBMKSmoke.puff(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1,
					this.worldPosition.getZ() + 0.5, this.scale, life);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.scale = nbt.getFloat("scale");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putFloat("scale", this.scale);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
