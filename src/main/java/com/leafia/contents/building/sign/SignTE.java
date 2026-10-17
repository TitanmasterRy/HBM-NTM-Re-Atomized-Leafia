package com.leafia.contents.building.sign;

import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One letter sign's state, which is a single dye colour.
 *
 * <p>Upstream: {@code com/leafia/contents/building/sign/SignTE.java}.
 *
 * <h2>The first receiver that uses {@code onPlayerValidate}</h2>
 *
 * A sign changes colour rarely and then keeps that colour forever, so broadcasting it every tick
 * would be absurd — it is sent once when it is dyed, and once more to each client that comes into
 * range and asks. That asking is what {@code _validate} and {@link #onPlayerValidate} are for, and
 * this is the first thing in the port to implement either: the voider and the turbine both push
 * every tick and leave {@code onPlayerValidate} empty (D-1026, D-1028).
 *
 * <p>It is also why the colour is worth syncing at all rather than reading off the block: the
 * colour is block-entity state, and a client that walked up to an already-dyed sign has never
 * seen the packet that set it.
 *
 * <h2>...and vanilla's sync as well</h2>
 *
 * Upstream drives {@code _validate} from a per-tick sweep in {@code LeafiaClientListener}, which
 * walks the loaded block entities and validates each receiver as the player comes within its
 * {@code affectionRange}. That sweep was unported when this block landed, so the colour also rides
 * <b>vanilla's</b> block-entity sync: {@code getUpdateTag} and {@code getUpdatePacket} send it with
 * the chunk. That is a deviation in mechanism and none in behaviour, and strictly cheaper than a
 * per-player round trip, so it stays. The sweep has since landed ({@code LeafiaValidateSweep},
 * D-3040), and {@link #onPlayerValidate} now answers as well. See D-1029.
 */
public class SignTE extends BlockEntity implements LeafiaPacketReceiver {

	public DyeColor color = DyeColor.WHITE;

	public SignTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.LETTER_SIGN.get(), pos, state);
	}

	/**
	 * Upstream stores {@code EnumDyeColor.getMetadata()}, which is 1.12.2's colour index and is
	 * exactly {@link DyeColor#getId()} here — same sixteen colours in the same order, so a saved
	 * byte means the same thing. {@code byMetadata} clamps out-of-range values to white by masking;
	 * {@code DyeColor.byId} does the same modulo, so a corrupt byte still yields a colour rather
	 * than a crash.
	 */
	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		color = DyeColor.byId(compound.getByte("color"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putByte("color", (byte) color.getId());
	}

	@Override
	public String getPacketIdentifier() {
		return "SIGN";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) color = DyeColor.byId((int) value);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	/**
	 * A client that has just come into range asks; this is the answer, to that client alone.
	 *
	 * <p>Asked for by the client's validate sweep (D-3040); the chunk's update tag usually got there
	 * first - see the class note.
	 */
	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket._start(this).__write(0, color.getId()).__sendToClient(plr);
	}

	/*
	 * Vanilla's block-entity sync, which is what actually gets the colour to a client today.
	 * getUpdateTag is what rides the chunk; getUpdatePacket is what carries a change to players
	 * already watching. Both go through saveAdditional, so there is one definition of what a
	 * sign's state is.
	 */

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
