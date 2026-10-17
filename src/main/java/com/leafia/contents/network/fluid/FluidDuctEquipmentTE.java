package com.leafia.contents.network.fluid;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared half of the addon's in-line duct equipment: a length of the base mod's fluid pipe
 * that also remembers which way it was placed.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/FluidDuctEquipmentTE.java}. Three
 * fields, and they exist because a valve is not symmetric — the handwheel has to point at the
 * player who placed it, and the collision box has to lie along the pipe run rather than across
 * it.
 *
 * <h2>Why the orientation is on the block entity and not the blockstate</h2>
 *
 * Upstream says why itself, in the last line of the file: <em>"since shit can't have more than
 * 16 metadatas, here we are some shit"</em>. Four horizontal facings × two verticals × three
 * faces is 24 states, and 1.12.2 block metadata held 16 — so it went in the tile entity. **The
 * Flattening removed that limit**, and D-034 and D-700 both moved exactly this kind of packed
 * state onto the blockstate where it belongs.
 *
 * <p>It is kept on the block entity here anyway, and the reason is no longer the one first
 * written down. That reason was "the renderer is not ported, so a blockstate rotation could not
 * be looked at"; {@link FluidDuctEquipmentRender} has since landed and the answer changed rather
 * than arrived. Blockstate orientation buys one thing — a baked model rotated by it — and a
 * baked model rotated by a blockstate <b>cannot also turn a handwheel</b>. The renderer has to
 * apply the orientation itself so the wheel's spin composes on top of it, so the orientation has
 * to be somewhere the renderer can read per block entity. It stays here. See D-1016, D-1021.
 *
 * <p>Sync rides {@code serialize}/{@code deserialize} over {@code networkPackNT}, the base
 * mod's channel, rather than upstream's {@code LeafiaPacket} — the third such substitution, and
 * the same one-for-one shape as the amat charger's (D-1015).
 */
public abstract class FluidDuctEquipmentTE extends BlockEntityPipeBaseNT {

	/** Upstream's, and horizontal only — it is stored as a horizontal index. */
	public Direction direction = Direction.NORTH;
	public boolean vertical = false;
	public int face = 0;

	protected FluidDuctEquipmentTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.direction = Direction.from2DDataValue(nbt.getInt("direction"));
		this.vertical = nbt.getBoolean("vertical");
		this.face = nbt.getByte("face");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("direction", this.direction.get2DDataValue());
		nbt.putBoolean("vertical", this.vertical);
		nbt.putByte("face", (byte) this.face);
	}

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeByte(this.direction.get2DDataValue());
		buf.writeBoolean(this.vertical);
		buf.writeByte(this.face);
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		this.direction = Direction.from2DDataValue(buf.readByte());
		this.vertical = buf.readBoolean();
		this.face = buf.readByte();
	}

	/**
	 * Upstream's {@code affectionRange}, which is what its packets are sent within. 256 is a
	 * long way for a per-tick packet; {@code networkPackNT} does not resend an unchanged
	 * payload more than once a second, which is what makes it affordable.
	 */
	public static final int SYNC_RANGE = 256;
}
