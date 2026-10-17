package com.leafia.dev.container_utility;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.hbm.util.Tuple.Pair;
import com.leafia.LeafiaMod;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.dev.optimization.diagnosis.LeafiaPacketStats;
import com.leafia.lib.LeafiaRef;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.technical.FiaLatch;
import com.llib.technical.FifthString;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The addon's block-entity sync packet: up to 32 named slots of typed data, sent to whatever is
 * at a position.
 *
 * <p>Upstream: {@code com/leafia/dev/container_utility/LeafiaPacket.java}. <b>89 files name it</b>
 * — block entities, containers and GUIs across the elevators, the control panel, the turbine and
 * the reactors — which makes it the addon's largest single blocker, ahead of the custom packet
 * layer that landed beside it (D-1024).
 *
 * <h2>What it is, in one sentence</h2>
 *
 * {@code _start(blockEntity).__write(0, someInt).__write(1, someStack).__sendToAffectedClients()},
 * and the block entity's {@link LeafiaPacketReceiver#onReceivePacketLocal} is called with key 0
 * and key 1 on every client near it.
 *
 * <h2>The wire format, which is the whole file</h2>
 *
 * A slot's key byte is <b>five bits of key and three bits of type</b>, which is where the
 * thirty-two-slot limit comes from: eight types per "reader mode", and a mode switch is written as
 * type 7 with the new mode in the key bits. Mode 0 is the primitives, mode 1 the objects, and the
 * encoder emits everything in mode 0 before switching — so a packet of mixed types costs one extra
 * byte for the switch rather than a mode tag on every slot.
 *
 * <p>Type 7 with key 31 is an array: the next int carries the slot's real key byte in its low
 * eight bits and the length in the rest. All of this is upstream's and it is preserved bit for
 * bit, because both sides of a connection have to agree and there is no version tag here to catch
 * a disagreement — {@code LeafiaCustomPacket}'s count check protects that packet and not this one.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 *   <li>{@code Chunk.EnumCreateEntityType} is {@link LevelChunk.EntityCreationType}, with the same
 *       three constants and the same meaning.</li>
 *   <li>The dimension is a {@link ServerLevel} rather than an {@code int} id, which is what
 *       1.21.1 has; {@code PacketThreading} becomes {@link PacketDistributor}, since
 *       {@code ThreadedPacket} is not needed on this port (D-062).</li>
 *   <li>{@code ItemStack} needs registry access to encode at all now, so the buffer carries a
 *       {@link RegistryAccess} — the gap D-1008 left open for this batch. See
 *       {@link LeafiaBuf#registries}.</li>
 *   <li>Upstream's {@code Handler} does the receiving work inside {@code addScheduledTask};
 *       {@link IPayloadContext#enqueueWork} is the same thing and is where it goes.</li>
 * </ul>
 *
 * <h2>The identifier is a hash, and that is upstream's design rather than an accident</h2>
 *
 * A receiver names itself with a string, and the packet carries {@code identifier.hashCode()}.
 * The receiver at the far end compares hashes, so two receivers whose identifiers collide would
 * accept each other's packets. Upstream ships it that way — the string itself is kept only for
 * crash logs and is deliberately never sent — and it is kept, because changing it changes the wire
 * format for something that has never bitten. Named here so it is a known risk rather than a
 * surprise.
 */
public record LeafiaPacket(int x, int y, int z, int identifier, byte checkType, int bitLength, byte[] data)
		implements CustomPacketPayload {

	/** As {@code LeafiaCustomPacket}'s, and for the same reason. */
	public static final int MAX_PAYLOAD = 262144;

	public static final CustomPacketPayload.Type<LeafiaPacket> TYPE =
			new CustomPacketPayload.Type<>(LeafiaRef.rl("container"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LeafiaPacket> STREAM_CODEC =
			StreamCodec.of(LeafiaPacket::write, LeafiaPacket::read);

	private static void write(RegistryFriendlyByteBuf buf, LeafiaPacket packet) {
		buf.writeVarInt(packet.x);
		buf.writeVarInt(packet.y);
		buf.writeVarInt(packet.z);
		buf.writeInt(packet.identifier);
		buf.writeByte(packet.checkType);
		buf.writeVarInt(packet.bitLength);
		ByteBufCodecs.byteArray(MAX_PAYLOAD).encode(buf, packet.data);
	}

	private static LeafiaPacket read(RegistryFriendlyByteBuf buf) {
		return new LeafiaPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readInt(), buf.readByte(), buf.readVarInt(),
				ByteBufCodecs.byteArray(MAX_PAYLOAD).decode(buf));
	}

	@Override
	public CustomPacketPayload.Type<LeafiaPacket> type() {
		return TYPE;
	}

	/** The builder. Upstream's {@code LeafiaPacket} before it is sent — this is the mutable half. */
	public static class Builder {

		private final BlockPos pos;
		private final int identifier;
		/** Upstream keeps the string for crash logs and never sends it. */
		private final String identifierString;

		private final RegistryAccess registries;
		private final Level level;
		private final BlockEntity te;

		private final Map<Byte, Pair<Byte, Object>> signal = new HashMap<>();
		private final List<FiaLatch<?>> latches = new ArrayList<>();

		private LevelChunk.EntityCreationType checkType = LevelChunk.EntityCreationType.IMMEDIATE;
		private boolean isValidation = false;

		/**
		 * Upstream's {@code new LeafiaPacket(BlockPos, String)}: a packet aimed at a position
		 * without a block entity in hand. Upstream uses it internally; it is public here because
		 * it is also the only way to exercise the wire format without a live receiver, and a
		 * format this intricate needs exercising. See D-1025.
		 */
		public Builder(BlockPos pos, String identifier, Level level) {
			this(pos, identifier, level, null);
		}

		private Builder(BlockPos pos, String identifier, Level level, BlockEntity te) {
			this.pos = pos;
			this.identifier = identifier.hashCode();
			this.identifierString = identifier;
			this.level = level;
			this.registries = level.registryAccess();
			this.te = te;
		}

		public Builder __setTileEntityQueryType(LevelChunk.EntityCreationType type) {
			this.checkType = type;
			return this;
		}

		/**
		 * Adds one slot. Upstream's {@code __write}, types and errors included.
		 *
		 * <p>A {@link FiaLatch} is unwrapped here rather than by the caller: the latch's value is
		 * written and the latch is remembered, and if it reports no change the slot is skipped
		 * entirely. That is how upstream sends nothing when nothing moved, and it is why
		 * {@code onSending} updates the latches only once the packet is actually going out.
		 *
		 * <p>An id of -1 is silently ignored, which upstream uses as "no slot for this".
		 */
		public Builder __write(int id, @Nullable Object valueIn) {

			Object value = valueIn;

			if(value instanceof FiaLatch<?> latch) {
				value = latch.getInterest();
				if(latch.needsUpdate()) latches.add(latch);
				else return this;
			}

			if(id == -1) return this;

			if(id < 0 || id > 0b00011111) {
				throw new LeafiaDevFlaw("LeafiaPacket >> [Sender, identifier: " + identifierString
						+ "] Entry ID given for LeafiaPacket wasn't in the range of 0 ~ 31");
			}

			byte key = (byte) id;
			byte mode = 0;
			boolean skip = false;

			if(valueIn != null && valueIn.getClass().isArray()) {
				if(Array.getLength(valueIn) <= 0) {
					// An empty array has no element to read a type from, so it is sent as the
					// null type and comes back as null rather than as a zero-length array.
					skip = true;
					mode = 1;
					key += 5 << 5;
				} else {
					value = Array.get(valueIn, 0);
				}
			}

			if(!skip) {
				if(value instanceof Boolean) {
					// type 0
				} else if(value instanceof Byte) { key += 1 << 5;
				} else if(value instanceof Short) { key += 2 << 5;
				} else if(value instanceof Integer) { key += 3 << 5;
				} else if(value instanceof Long) { key += 4 << 5;
				} else if(value instanceof Float) { key += 5 << 5;
				} else if(value instanceof Double) { key += 6 << 5;
				} else {
					mode = 1;
					if(value instanceof CompoundTag) {
						// type 0 of mode 1
					} else if(value instanceof ItemStack) { key += 1 << 5;
					} else if(value instanceof String || value instanceof FifthString) { key += 2 << 5;
					} else if(value instanceof BlockPos) { key += 3 << 5;
					} else if(value instanceof Vec3) { key += 4 << 5;
					} else if(value == null) { key += 5 << 5;
					} else {
						throw new LeafiaDevFlaw("LeafiaPacket >> [Sender, identifier: " + identifierString
								+ "] Given type not supported for LeafiaPacket.. (" + value.getClass().getName() + ")");
					}
				}
			}

			this.signal.put(key, new Pair<>(mode, valueIn));
			return this;
		}

		/**
		 * Upstream's {@code onSending}: an empty packet is not sent at all, and the latches are
		 * updated only once it is.
		 *
		 * @return true when there is nothing to send
		 */
		private boolean nothingToSend() {

			if(signal.isEmpty() && !isValidation) return true;

			for(FiaLatch<?> latch : latches) latch.update();
			return false;
		}

		/** Encodes the slots. Public for the same reason the constructor above is. */
		@Nullable
		public LeafiaPacket build() {

			LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
			buf.registries = registries;

			encodeByMode(0, signal.keySet(), buf);

			int length = buf.payloadLength();

			if(length > MAX_PAYLOAD) {
				throw new LeafiaDevFlaw("LeafiaPacket >> [Sender, identifier: " + identifierString
						+ "] wrote " + length + " bytes, over the " + MAX_PAYLOAD + " byte limit");
			}

			byte[] data = new byte[length];
			System.arraycopy(buf.bytes, buf.payloadOffset(), data, 0, length);

			LeafiaPacketStats.record(length);
			return new LeafiaPacket(pos.getX(), pos.getY(), pos.getZ(), identifier,
					(byte) checkType.ordinal(), buf.writerIndex, data);
		}

		/**
		 * Writes every slot of one mode, then recurses into the lowest mode left.
		 *
		 * <p>Upstream's, recursion and all. The point is that the mode is written once per group
		 * rather than per slot, so a packet of nothing but ints carries no mode tag at all.
		 */
		private void encodeByMode(int mode, Set<Byte> entries, LeafiaBuf buf) {

			int nextMode = 31;
			Set<Byte> remaining = new HashSet<>();

			for(Byte entry : entries) {

				Pair<Byte, Object> pair = signal.get(entry);

				if(pair.getKey() != (byte) mode) {
					nextMode = Math.min(nextMode, pair.getKey());
					remaining.add(entry);
					continue;
				}

				int c = type(entry) + mode * 10;
				int arrayLength = -1;
				Object valueIn = pair.getValue();

				if(valueIn != null && valueIn.getClass().isArray()) {
					// Upstream's comment: "theres no way we would need 31 fucking entire
					// readerset modes anyway" - so mode 31 is stolen to mean "an array follows".
					buf.writeByte((7 << 5) + 31);
					arrayLength = Array.getLength(valueIn);
					buf.writeInt((entry & 0xFF) | arrayLength << 8);
				} else {
					buf.writeByte(entry);
				}

				for(int i = 0; arrayLength < 0 ? i == 0 : i < arrayLength; i++) {

					Object value = arrayLength < 0 ? valueIn : Array.get(valueIn, i);

					switch(c) {
						case 0 -> buf.writeBoolean((boolean) value);
						case 1 -> buf.writeByte((byte) value);
						case 2 -> buf.writeShort((short) value);
						case 3 -> buf.writeInt((int) value);
						case 4 -> buf.writeLong((long) value);
						case 5 -> buf.writeFloat((float) value);
						case 6 -> buf.writeDouble((double) value);
						case 10 -> buf.writeNBT((CompoundTag) value);
						case 11 -> buf.writeItemStack((ItemStack) value);
						case 12 -> buf.writeFifthString(new FifthString(value.toString()));
						case 13 -> {
							BlockPos bpos = (BlockPos) value;
							buf.writeInt(bpos.getX());
							buf.writeInt(bpos.getY());
							buf.writeInt(bpos.getZ());
						}
						case 14 -> {
							Vec3 vec = (Vec3) value;
							buf.writeDouble(vec.x);
							buf.writeDouble(vec.y);
							buf.writeDouble(vec.z);
						}
						case 15 -> { /* null carries no payload */ }
						default -> throw new LeafiaDevFlaw("LeafiaPacket >> [Sender, identifier: "
								+ identifierString + "] Unrecognized data type " + c);
					}
				}
			}

			if(!remaining.isEmpty()) {
				buf.writeByte((7 << 5) + nextMode);
				encodeByMode(nextMode, remaining, buf);
			}
		}

		// Upstream's send helpers, underscores and all, so its call sites port unedited.

		public void __sendToAll() {
			LeafiaPacket packet = ready();
			if(packet != null) PacketDistributor.sendToAllPlayers(packet);
		}

		/** Upstream marks this {@code @Deprecated} without saying why; kept as it is. */
		@Deprecated
		public void __sendToAllInDimension() {
			LeafiaPacket packet = ready();
			if(packet != null && level instanceof ServerLevel server) {
				PacketDistributor.sendToPlayersInDimension(server, packet);
			}
		}

		/**
		 * The usual one: everybody near enough to have the block loaded.
		 *
		 * <p>The 1.3 multiplier on the receiver's own {@code affectionRange} is upstream's, and
		 * so is switching the lookup to {@code CHECK} — a client that is only just in range may
		 * not have created the block entity yet, and {@code IMMEDIATE} would make one.
		 */
		public void __sendToAffectedClients() {
			this.checkType = LevelChunk.EntityCreationType.CHECK;
			__sendToClients(receiver().affectionRange() * 1.3D);
		}

		public void __sendToListeners() {
			LeafiaPacket packet = ready();
			if(packet == null) return;
			for(Player player : receiver().getListeners()) sendTo(packet, player);
		}

		public void __sendToServer() {
			LeafiaPacket packet = ready();
			if(packet != null) PacketDistributor.sendToServer(packet);
		}

		public void __sendToClients(double range) {
			LeafiaPacket packet = ready();
			if(packet != null && level instanceof ServerLevel server) {
				PacketDistributor.sendToPlayersNear(server, null, x(packet), y(packet), z(packet), range, packet);
			}
		}

		public void __sendToClient(Player player) {
			LeafiaPacket packet = ready();
			if(packet != null) sendTo(packet, player);
		}

		@Nullable
		private LeafiaPacket ready() {
			return nothingToSend() ? null : build();
		}

		private LeafiaPacketReceiver receiver() {
			if(te instanceof LeafiaPacketReceiver rec) return rec;
			throw new LeafiaDevFlaw("LeafiaPacket used on a BlockEntity that isn't a LeafiaPacketReceiver. How?!");
		}
	}

	private static double x(LeafiaPacket p) { return p.x + 0.5D; }
	private static double y(LeafiaPacket p) { return p.y + 0.5D; }
	private static double z(LeafiaPacket p) { return p.z + 0.5D; }

	/**
	 * Upstream's static sender, kept because it is public API that call sites use directly: a
	 * player who is not a {@code ServerPlayer} means the caller handed over the client-side
	 * object, and upstream broadcasts to everyone rather than dropping it.
	 */
	public static void sendTo(LeafiaPacket packet, Player player) {
		if(player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server, packet);
		else PacketDistributor.sendToAllPlayers(packet);
	}

	/** Upstream's {@code _start}. */
	public static Builder _start(BlockEntity entity) {
		return new Builder(entity.getBlockPos(), identifierOf(entity), entity.getLevel(), entity);
	}

	/**
	 * Upstream's {@code _validate}: a client asking the server to bring it up to date.
	 *
	 * <p>It sends an <em>empty</em> packet on purpose — the server side reads a slot count of
	 * zero as "this player wants the current state" and calls {@code onPlayerValidate}. That is
	 * why {@code isValidation} exists: an empty packet is otherwise not sent at all.
	 */
	public static void _validate(BlockEntity entity) {

		if(entity.getLevel() == null || !entity.getLevel().isClientSide) return; // upstream: "ayo you're lazy"

		Builder builder = _start(entity);
		builder.isValidation = true;
		builder.__setTileEntityQueryType(LevelChunk.EntityCreationType.CHECK);
		builder.__sendToServer();
	}

	private static String identifierOf(BlockEntity entity) {
		if(entity instanceof LeafiaPacketReceiver rec) return rec.getPacketIdentifier();
		throw new LeafiaDevFlaw("LeafiaPacket._start on a BlockEntity that isn't a LeafiaPacketReceiver");
	}

	private static byte type(byte entry) { return (byte) ((entry & 255) >>> 5); }

	/** The five low bits: the slot the receiver sees. */
	public static byte key(byte entry) { return (byte) (entry & 0b00011111); }

	/**
	 * Reads the slots back out. Upstream's {@code fromBits}, which is the mirror of
	 * {@code encodeByMode} — including that a mode switch is a slot with type 7.
	 */
	public Map<Byte, Pair<Byte, Object>> decode(RegistryAccess registries) {

		Map<Byte, Pair<Byte, Object>> signal = new HashMap<>();

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
		buf.registries = registries;
		buf.wrapOwned(data);
		buf.writerIndex = bitLength;

		byte readMode = 0;

		while(buf.readableBits() >= 1) {

			byte entry = buf.readByte();
			byte type = type(entry);

			Object array = null;
			int arrayLength = -1;

			if(type == 7) {

				byte nextMode = key(entry);

				if(nextMode != 31) {
					readMode = nextMode;
					continue;
				}

				int entryArray = buf.readInt();
				entry = (byte) (entryArray & 0b111_11111);
				type = type(entry);
				arrayLength = entryArray >>> 8;

				// D-1094: an element takes at least a bit, so a length past the bits left is a lie that
				// would otherwise allocate up to 16 million slots for a packet of a few bytes.
				if(arrayLength > buf.readableBits())
					throw new LeafiaDevFlaw("LeafiaPacket >> [Receiver] array of " + arrayLength + " in " + buf.readableBits() + " bits");

				array = switch(type + readMode * 10) {
					case 0 -> new boolean[arrayLength];
					case 1 -> new byte[arrayLength];
					case 2 -> new short[arrayLength];
					case 3 -> new int[arrayLength];
					case 4 -> new long[arrayLength];
					case 5 -> new float[arrayLength];
					case 6 -> new double[arrayLength];
					default -> new Object[arrayLength];
				};
			}

			for(int i = 0; array == null ? i == 0 : i < arrayLength; i++) {

				int c = type + readMode * 10;

				Object value = switch(c) {
					case 0 -> buf.readBoolean();
					case 1 -> buf.readByte();
					case 2 -> buf.readShort();
					case 3 -> buf.readInt();
					case 4 -> buf.readLong();
					case 5 -> buf.readFloat();
					case 6 -> buf.readDouble();
					case 10 -> buf.readNBT();
					case 11 -> buf.readItemStack();
					case 12 -> buf.readFifthString().toString();
					case 13 -> new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
					case 14 -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
					case 15 -> null;
					default -> throw new LeafiaDevFlaw("LeafiaPacket >> [Receiver] Unrecognized data type " + c);
				};

				if(array == null) signal.put(entry, new Pair<>(readMode, value));
				else Array.set(array, i, value);
			}

			if(array != null) signal.put(entry, new Pair<>(readMode, array));
		}

		return signal;
	}

	/**
	 * Delivers to the receiver at the packet's position, on either side.
	 *
	 * <p>Upstream's {@code Handler.onMessage}, with its two guards kept: the chunk is only asked
	 * for the block entity if the position is loaded, and the receiver's identifier hash must
	 * match. A packet for a receiver that has gone away is dropped silently, which is the right
	 * behaviour for a sync packet that raced a block being broken.
	 */
	public static void handle(LeafiaPacket packet, IPayloadContext context) {

		LeafiaPacketStats.record(packet.data.length);

		context.enqueueWork(() -> {

			Player player = context.player();
			Level level = player.level();
			BlockPos pos = new BlockPos(packet.x, packet.y, packet.z);

			if(!level.isLoaded(pos)) return;

			// D-1094: the creation mode is a byte off the wire, and values()[n] throws for any n a
			// modified client chooses. Both sides refuse it; only the server has anyone to log.
			if(packet.checkType < 0 || packet.checkType >= LevelChunk.EntityCreationType.values().length) {
				if(!level.isClientSide) PacketGuard.reject("LeafiaPacket", player, "creation mode " + packet.checkType);
				return;
			}

			LevelChunk chunk = level.getChunkAt(pos);
			BlockEntity entity = chunk.getBlockEntity(pos,
					LevelChunk.EntityCreationType.values()[packet.checkType]);

			if(!(entity instanceof LeafiaPacketReceiver receiver)) return;
			if(receiver.getPacketIdentifier().hashCode() != packet.identifier) return;

			Map<Byte, Pair<Byte, Object>> signal;

			try {
				signal = packet.decode(level.registryAccess());
			} catch(RuntimeException exc) {
				// A malformed body from a client is dropped; from the server it is a bug, and loud.
				if(level.isClientSide) throw exc;
				PacketGuard.reject("LeafiaPacket", player, "undecodable: " + exc);
				return;
			}

			if(level.isClientSide) {
				signal.forEach((entry, value) -> receiver.onReceivePacketLocal(key(entry), value.getValue()));
				return;
			}

			// D-1094: serverbound, so the sender is a client and nothing it says is believed. A packet
			// that changes something must come from a player who could be using the block; the empty
			// "validate" packet only asks for state, and may come from as far as the receiver sends to.
			if(signal.isEmpty()) {
				if(!PacketGuard.within(player, pos, receiver.affectionRange() * 1.3D)) {
					PacketGuard.reject("LeafiaPacket", player, "validate from too far away");
					return;
				}
				receiver.onPlayerValidate(player);
				return;
			}

			if(!PacketGuard.near(player, pos)) {
				PacketGuard.reject("LeafiaPacket", player, "update to " + pos.toShortString() + " from out of reach");
				return;
			}

			signal.forEach((entry, value) -> {
				try {
					receiver.onReceivePacketServer(key(entry), value.getValue(), player);
				} catch(ClassCastException exc) {
					// Upstream logs and carries on rather than letting one malformed slot from a
					// modified client take the others down with it.
					LeafiaMod.LOGGER.error("Malformed packet sent by {}: {}",
							player.getName().getString(), exc.getMessage(), exc);
				}
			});
		});
	}
}
