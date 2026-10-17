package com.leafia.contents.machines.controlpanel.core.packet;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What a panel tells the players watching it: either this tick's changed variables, or, after an edit, the whole panel.
 * Adapted from NTM-CE's {@code ControlPanelUpdatePacket} (D-3063), as an addon {@link CustomPacketPayload} with a
 * {@link StreamCodec}, <b>server to client only</b>, sent to the players tracking the panel's chunk.
 *
 * <p>The addon's {@code LeafiaPacket} is the wrong fit (thirty-two slots and a hash for an identifier, against a variable-length
 * list of variable updates), so this is a payload of its own. Registered in
 * {@code LeafiaPacketDispatcher} whether or not the panel is switched on: the handler does nothing for a position with no panel.
 *
 * <h2>Differences from CE</h2>
 * <ul>
 * <li>The full set that follows an edit is <b>the server's re-serialised panel</b>, not the tag the client sent (CE rebroadcast the
 * client's bytes), so what other players see is what the server accepted.</li>
 * <li>A delta is a list of {@link VarUpdate}; a value that does not parse is dropped rather than read as null, which CE took to
 * mean "remove this variable".</li>
 * <li>Both directions are bounded on read: at most {@link #MAX_UPDATES} deltas, names up to {@link #MAX_NAME} characters.</li>
 * </ul>
 */
public record ControlPanelUpdatePacket(BlockPos pos, List<VarUpdate> updates, CompoundTag full) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ControlPanelUpdatePacket> TYPE =
			new CustomPacketPayload.Type<>(LeafiaRef.rl("control_panel_update"));

	public static final int MAX_UPDATES = 8192;
	public static final int MAX_NAME = 128;

	public static final StreamCodec<RegistryFriendlyByteBuf, ControlPanelUpdatePacket> STREAM_CODEC =
			StreamCodec.of(ControlPanelUpdatePacket::write, ControlPanelUpdatePacket::read);

	/** One variable that changed: {@code varListIdx} is -1 for a panel global, else the control's index. A null value removes it. */
	public record VarUpdate(int varListIdx, String varName, DataValue data) {}

	public ControlPanelUpdatePacket(BlockPos pos, List<VarUpdate> updates) {
		this(pos, List.copyOf(updates), null);
	}

	public ControlPanelUpdatePacket(BlockPos pos, CompoundTag full) {
		this(pos, List.of(), full);
	}

	private static void write(RegistryFriendlyByteBuf buf, ControlPanelUpdatePacket p) {
		BlockPos.STREAM_CODEC.encode(buf, p.pos);
		buf.writeBoolean(p.full != null);
		if(p.full != null) {
			ByteBufCodecs.COMPOUND_TAG.encode(buf, p.full);
			return;
		}
		buf.writeVarInt(p.updates.size());
		for(VarUpdate u : p.updates) {
			buf.writeVarInt(u.varListIdx + 1);
			buf.writeUtf(u.varName, MAX_NAME);
			buf.writeBoolean(u.data != null);
			if(u.data != null)
				ByteBufCodecs.TAG.encode(buf, u.data.writeToNBT());
		}
	}

	private static ControlPanelUpdatePacket read(RegistryFriendlyByteBuf buf) {
		BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
		if(buf.readBoolean())
			return new ControlPanelUpdatePacket(pos, ByteBufCodecs.COMPOUND_TAG.decode(buf));

		int size = buf.readVarInt();
		if(size < 0 || size > MAX_UPDATES)
			throw new IllegalArgumentException("A control panel update of " + size + " variables is past " + MAX_UPDATES);
		List<VarUpdate> updates = new ArrayList<>(size);
		for(int i = 0; i < size; i++) {
			int idx = buf.readVarInt() - 1;
			String name = buf.readUtf(MAX_NAME);
			DataValue value = null;
			boolean present = buf.readBoolean();
			if(present) {
				Tag tag = ByteBufCodecs.TAG.decode(buf);
				value = DataValue.newFromNBT(tag);
				if(value == null)
					continue; // a value that does not parse is dropped, not read as "remove"
			}
			updates.add(new VarUpdate(idx, name, value));
		}
		return new ControlPanelUpdatePacket(pos, updates, null);
	}

	@Override
	public CustomPacketPayload.Type<ControlPanelUpdatePacket> type() {
		return TYPE;
	}

	/** Sends to every player tracking the panel's chunk. */
	public static void sendToTracking(ServerLevel level, BlockPos pos, ControlPanelUpdatePacket packet) {
		PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), packet);
	}

	/** The client half. Looks only at an already-loaded position, and only at a panel there. */
	public static void handle(ControlPanelUpdatePacket packet, IPayloadContext context) {
		Level level = context.player().level();
		if(level == null || !level.isLoaded(packet.pos))
			return;
		BlockEntity be = level.getBlockEntity(packet.pos);
		if(!(be instanceof ControlPanelTE panel))
			return;
		panel.applyServerUpdate(packet);
	}
}
