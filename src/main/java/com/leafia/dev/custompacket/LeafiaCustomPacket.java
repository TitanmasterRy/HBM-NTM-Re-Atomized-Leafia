package com.leafia.dev.custompacket;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.LeafiaMod;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.dev.optimization.diagnosis.LeafiaPacketStats;
import com.leafia.lib.LeafiaRef;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The addon's one packet, carrying whichever of its packets is being sent.
 *
 * <p>Upstream: {@code com/leafia/dev/custompacket/LeafiaCustomPacket.java}. <b>50 of the addon's
 * files name this class or its encoder</b>, and it had sat in the deferred table as "the packet
 * layer, on {@code IPayloadContext}" since D-1011. It is <em>not</em> the addon's largest blocker:
 * that is {@code com/leafia/dev/container_utility/LeafiaPacket.java}, a different class named by
 * 89. The two were counted together as one number when this batch was chosen — see D-1024.
 *
 * <h2>One payload, many packets — kept on purpose</h2>
 *
 * 1.21.1 would let every addon packet be its own {@link CustomPacketPayload} with its own id, and
 * that is the idiomatic answer. It is not the one taken, because upstream's call sites are
 * {@code LeafiaCustomPacket.__start(new FooPacket(x)).__sendToAllAround(...)} and there are
 * fifty files of them. Keeping the hub means those port mechanically; splitting it means rewriting
 * every one. The hub also keeps what the addon actually gains from this layer: the payload is a
 * {@link LeafiaBuf}, so a boolean on the wire costs one bit rather than one byte.
 *
 * <p>What did change is the registry. Upstream's is an enum, and an enum could not be ported —
 * see {@link LeafiaPacketTypes}.
 *
 * <h2>The protocol check, kept exactly</h2>
 *
 * Upstream packs the type id and the <em>number of known types</em> into a single short, and a
 * receiver whose count disagrees with the sender's disconnects rather than misreading every
 * packet that follows. That is a real safeguard rather than a flourish — the id is an index, so a
 * one-off count means every packet after a version mismatch is decoded as the wrong type — and it
 * is reproduced here down to the message, on {@link IPayloadContext#disconnect}.
 *
 * <h2>Where the threading went</h2>
 *
 * Upstream sends through {@code PacketThreading}, NTM-CE's queue for building packets off the
 * server thread, and extends {@code ThreadedPacket}. Neither exists here: D-062 concluded
 * {@code ThreadedPacket} is not needed at all on this port, and a {@code CustomPacketPayload} is
 * a value that NeoForge encodes on the network thread by itself. So {@code __sendToAll} and its
 * siblings call {@link PacketDistributor} directly.
 *
 * <p>{@code RecordablePacket}, upstream's superclass, dissolves into the codec below: its whole
 * body is the bit-length framing, and its byte counters live on {@link LeafiaPacketStats}.
 *
 * <h2>The signatures that had to change</h2>
 *
 * Upstream's send helpers take an {@code int} dimension id, which 1.21.1 does not have — they take
 * a {@link ServerLevel} instead. Every call site has to name the level it is sending in, which it
 * always had to hand anyway.
 */
public record LeafiaCustomPacket(int bitLength, byte[] data) implements CustomPacketPayload {

	/**
	 * A generous cap on one packet, matching what the base mod allows its machine sync
	 * (D-030's {@code BufPacket}). It exists so a runaway {@code encode} is refused at the source
	 * with a log line naming the packet, rather than eating the connection's budget.
	 */
	public static final int MAX_PAYLOAD = 262144;

	public static final CustomPacketPayload.Type<LeafiaCustomPacket> TYPE =
			new CustomPacketPayload.Type<>(LeafiaRef.rl("custom"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LeafiaCustomPacket> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, LeafiaCustomPacket::bitLength,
					ByteBufCodecs.byteArray(MAX_PAYLOAD), LeafiaCustomPacket::data,
					LeafiaCustomPacket::new);

	@Override
	public CustomPacketPayload.Type<LeafiaCustomPacket> type() {
		return TYPE;
	}

	/**
	 * Builds a packet from an encoder. Upstream's {@code __start}.
	 *
	 * <p>Upstream throws {@code LeafiaDevFlaw} when the encoder is not in its enum. That stays a
	 * hard failure in spirit but not in kind: an unregistered packet is a developer mistake that
	 * cannot be recovered from at runtime, so it is logged loudly and dropped rather than taking
	 * the server down with it. A dropped packet is a visible bug; a crashed server is a worse one.
	 *
	 * @return the packet, or null if the encoder is unregistered or wrote past {@link #MAX_PAYLOAD}.
	 */
	@Nullable
	public static LeafiaCustomPacket __start(LeafiaCustomPacketEncoder encoder) {

		int id = LeafiaPacketTypes.idOf(encoder);

		if(id < 0) {
			LeafiaMod.LOGGER.error("{} is not registered in LeafiaPacketTypes; packet dropped.",
					encoder.getClass().getName());
			return null;
		}

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());

		// Upstream's framing: the high byte is the type, the low byte is how many types this
		// build knows about. See the class note on the protocol check.
		buf.writeShort((short) (id << 8 | LeafiaPacketTypes.count()));
		encoder.encode(buf);

		int length = buf.payloadLength();

		if(length > MAX_PAYLOAD) {
			LeafiaMod.LOGGER.error("{} tried to send {} bytes, over the {} byte limit. Packet dropped.",
					encoder.getClass().getSimpleName(), length, MAX_PAYLOAD);
			return null;
		}

		byte[] data = new byte[length];
		System.arraycopy(buf.bytes, buf.payloadOffset(), data, 0, length);

		LeafiaPacketStats.record(length);
		return new LeafiaCustomPacket(buf.writerIndex, data);
	}

	/**
	 * A packet opened far enough to see what it is: who sent it, what it claims to be, and the
	 * buffer positioned at its contents.
	 *
	 * <p>Split out of {@link #handle} so the framing can be tested. {@code handle} needs an
	 * {@link IPayloadContext}, which only a live connection has — and the arithmetic worth pinning
	 * is upstream's bit packing, which does not.
	 *
	 * @param senderTypeCount how many packet types the sender knows about
	 * @param prototype the registered encoder, or null for {@code NONE}, an unknown id, or a
	 *        count mismatch
	 */
	public record Opened(int senderTypeCount, @Nullable LeafiaCustomPacketEncoder prototype, LeafiaBuf buf) {

		/** True when the two sides disagree about how many packet types exist. */
		public boolean mismatched() {
			return senderTypeCount != LeafiaPacketTypes.count();
		}
	}

	/** Reads the header. See {@link Opened}. */
	public static Opened open(LeafiaCustomPacket packet) {

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
		buf.wrapOwned(packet.data);
		buf.writerIndex = packet.bitLength;

		int protocol = buf.readShort() & 0xFFFF;
		int senderTypeCount = protocol & 0xFF;

		if(senderTypeCount != LeafiaPacketTypes.count()) return new Opened(senderTypeCount, null, buf);

		return new Opened(senderTypeCount, LeafiaPacketTypes.byId(protocol >> 8), buf);
	}

	/**
	 * Reads the packet and runs it on the game thread. Upstream's {@code Handler.onMessage}.
	 *
	 * <p>Two things here are not upstream's (D-1094). A packet arriving <em>serverbound</em> is
	 * dropped unless its type says {@link LeafiaCustomPacketEncoder#acceptsServerbound it accepts
	 * that}: the payload is registered both ways, so without this a client could address a server
	 * with any of the forty. And anything a decoder throws on a serverbound packet - a hand ordinal
	 * out of range, a count that runs past the buffer - drops the packet instead of reaching the
	 * network thread's handler, which would disconnect the sender with a stack trace.
	 */
	public static void handle(LeafiaCustomPacket packet, IPayloadContext context) {

		LeafiaPacketStats.record(packet.data.length);

		boolean serverbound = context.flow() == PacketFlow.SERVERBOUND;
		Consumer<IPayloadContext> callback;

		try {
			Opened opened = open(packet);

			if(opened.mismatched()) {
				context.disconnect(protocolMismatch(opened.senderTypeCount()));
				return;
			}

			if(opened.prototype() == null) return;

			if(serverbound && !opened.prototype().acceptsServerbound()) {
				PacketGuard.reject(opened.prototype().getClass().getSimpleName(), context.player(),
						"not a packet a client may send");
				return;
			}

			callback = opened.prototype().decode(opened.buf());
		} catch(RuntimeException exc) {
			if(!serverbound) throw exc;
			PacketGuard.reject("LeafiaCustomPacket", context.player(), "undecodable: " + exc);
			return;
		}

		if(callback == null) return;

		if(!serverbound) {
			context.enqueueWork(() -> callback.accept(context));
			return;
		}

		context.enqueueWork(() -> {
			try {
				callback.accept(context);
			} catch(RuntimeException exc) {
				LeafiaMod.LOGGER.warn("A serverbound addon packet from {} threw and was dropped",
						context.player().getName().getString(), exc);
			}
		});
	}

	/** Upstream's disconnect screen, line for line. */
	private static Component protocolMismatch(int theirs) {
		return Component.literal("########").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" NTM:LCE FATAL ERROR ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal("########\nInvalid protocol on LeafiaCustomPacket").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("\nThe other side supports " + theirs
						+ " custom packet variations. You support " + LeafiaPacketTypes.count())
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\n\nPossible reasons are:").withStyle(ChatFormatting.AQUA))
				.append(Component.literal("\n- Your client is outdated. Check for any updates on github"
						+ "\n- Or the server is outdated. Contact server owner"
						+ "\n- Else perhaps it's some unpredictable fucks going on idfk")
						.withStyle(ChatFormatting.GRAY));
	}

	// Upstream's send helpers. The leading underscores are its own naming and are kept so the
	// 129 call sites port without edits.

	public void __sendToAll() {
		PacketDistributor.sendToAllPlayers(this);
	}

	public void __sendToAllAround(ServerLevel level, BlockPos pos, double range) {
		__sendToAllAround(level, new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D), range);
	}

	public void __sendToAllAround(ServerLevel level, Vec3 pos, double range) {
		PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, range, this);
	}

	public void __sendToAllInDimension(ServerLevel level) {
		PacketDistributor.sendToPlayersInDimension(level, this);
	}

	public void __sendToServer() {
		PacketDistributor.sendToServer(this);
	}

	/**
	 * Upstream sends to every player when handed something that is not a {@code ServerPlayer},
	 * which happens when a caller passes the client-side player object by mistake. Kept: it is
	 * reachable, and turning it into a no-op would silently drop packets that currently arrive.
	 */
	public void __sendToClient(Player player) {

		if(player instanceof ServerPlayer server) {
			PacketDistributor.sendToPlayer(server, this);
		} else {
			PacketDistributor.sendToAllPlayers(this);
		}
	}

	/**
	 * Upstream's only packet that is declared inside this class, and the one this layer is tested
	 * with: the server tells a client to move.
	 */
	public static class SyncVelocityPacket implements LeafiaCustomPacketEncoder {

		public Vec3 velocity;

		public SyncVelocityPacket() { }

		public SyncVelocityPacket(Vec3 velocity) {
			this.velocity = velocity;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeVec3d(velocity);
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			Vec3 read = buf.readVec3d();
			return context -> {
				if(FMLEnvironment.dist.isClient()) ClientOnly.setVelocity(read);
			};
		}

		/**
		 * The dist check is not upstream's, and it restores a safety upstream had rather than
		 * adding one.
		 *
		 * <p>1.12.2's SimpleImpl registers each message class in one direction, so upstream's
		 * {@code SyncVelocityPacket} could only ever be decoded on a client and its
		 * {@code @SideOnly(CLIENT)} handler was safe. This port has <em>one</em> payload type for
		 * all of them, registered both ways because the addon's forty packets go both ways — so a
		 * client can put this id on the wire toward a server, and the class it names is not there.
		 * The guard turns that into nothing happening instead of a server-side
		 * {@code NoClassDefFoundError}, which is what upstream's design already gave it.
		 */
		@OnlyIn(Dist.CLIENT)
		private static final class ClientOnly {
			static void setVelocity(Vec3 velocity) {
				net.minecraft.client.Minecraft.getInstance().player.setDeltaMovement(velocity);
			}
		}
	}
}
