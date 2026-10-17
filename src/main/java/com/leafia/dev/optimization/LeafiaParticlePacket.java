package com.leafia.dev.optimization;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.leafia.client.particle.LeafiaParticleClient;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.dev.optimization.diagnosis.LeafiaPacketStats;
import com.leafia.lib.LeafiaRef;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The server telling clients to draw a particle that vanilla's own particle packet cannot describe.
 *
 * <p>Upstream: {@code com/leafia/dev/optimization/LeafiaParticlePacket.java}. It is the spawner the
 * addon's nineteen particle classes have been waiting on since D-1075: those are client classes
 * constructed and handed to the particle engine, and something has to tell a client to construct one.
 *
 * <h2>The wire</h2>
 *
 * Upstream's, bit for bit, on {@link LeafiaBuf}: the position as three doubles, the motion as three
 * <em>floats</em> (the narrowing is upstream's, and a particle's motion has never needed a double),
 * then one byte saying how many particle kinds the sender knows, one byte naming which kind this is,
 * and the kind's own body. The whole thing travels as a bit count and a byte array exactly as
 * {@code LeafiaCustomPacket} does (D-1024), so the framing is the same code path.
 *
 * <p><b>The count is a protocol check, kept.</b> A receiver whose count disagrees with the sender's
 * cannot trust the id byte, because ids are indices; upstream disconnects with a message rather than
 * misread every particle after it, and so does this ({@link #handle}). The length-prefixed framing
 * means the unreadable body is skipped cleanly first, which upstream got from {@code RecordablePacket}.
 *
 * <h2>The registry is a list, and it is append-only</h2>
 *
 * Upstream builds its registry by reflecting over its own nested classes
 * ({@code getClasses()}), which means the wire id of a kind is whatever order the JVM hands the classes
 * back in and a kind whose class is not ported simply is not there. Neither survives a port that lands
 * in pieces, so the registry is the explicit list in the static block below, and the rule is the same
 * one {@code LeafiaPacketTypes} carries (D-1024): <b>order is load-bearing, so append and never
 * insert</b>. The ids are therefore not upstream's, which nothing outside this jar can see.
 *
 * <p>Fourteen kinds are registered, all of upstream's: nine from D-3000, {@code PinkRBMK} and
 * {@code JumpingRBMKParticle} from the RBMK batch (D-3018), {@code FiaSpark} and {@code DFCShockParticleEditionTM}
 * from DFC-4 (D-3017), and {@code TauSpark} from SEAM-TAIL (D-3031), in the order they landed. A kind is added by
 * writing its class here and appending one line; nothing else moves.
 *
 * <h2>Where the client half went</h2>
 *
 * Upstream's {@code emit} overrides construct a particle in the body of a class the server also loads,
 * behind {@code @SideOnly}, which Forge stripped. NeoForge has no stripping, and a verifier that meets
 * {@code ParticleEngine.add(new ParticleDFC(...))} would load a client class on a dedicated server. So
 * every body that touches the client is a static in {@link LeafiaParticleClient}, called by name and
 * resolved only when it runs; the kinds here are plain data and a server can encode and decode every
 * one of them, which is what the tests do.
 *
 * <h2>Differences, none of them behaviour</h2>
 *
 * <ul>
 * <li>The dimension id of {@code emit} is a {@link ServerLevel}, as everywhere in the addon.</li>
 * <li>The send goes to the players of that level within range that have the channel, the same
 *     filter as the base mod's {@code ClientParticleDispatch.sendPacket} and for the same reason
 *     (D-631): a mock or fake player has no real connection, and sending to one throws out of the
 *     caller. Upstream's {@code PacketThreading} is not needed (D-062).</li>
 * <li>{@code Sweat} carries a block <em>state</em> id where upstream carried a block id and a metadata
 *     value; metadata does not exist, and the base mod's {@code "sweat"} effect reads a state id.</li>
 * <li>{@code MTSteamParticle}'s decoder read its distance from the prototype instead of the buffer,
 *     so a client always drew a distance of zero. That is unreachable (nothing sends this kind,
 *     upstream included: the turbine draws its own steam) and is corrected here - see D-3000.</li>
 * </ul>
 */
public final class LeafiaParticlePacket implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<LeafiaParticlePacket> TYPE =
			new CustomPacketPayload.Type<>(LeafiaRef.rl("particle"));

	/** The most one packet may carry. A shock list of 127 positions is about 3 KB; this is generous. */
	public static final int MAX_PAYLOAD = 65536;

	public static final StreamCodec<RegistryFriendlyByteBuf, LeafiaParticlePacket> STREAM_CODEC =
			StreamCodec.of(LeafiaParticlePacket::write, LeafiaParticlePacket::read);

	/** Upstream's default range for a particle: far enough that nothing visible is missed. */
	public static final double DEFAULT_RANGE = 340;

	private static final List<LeafiaParticle> REGISTRY = new ArrayList<>();

	/**
	 * The particle's data buffer: position and motion (always), plus whatever its kind writes for the
	 * client-side effect. Null when the sender's registry did not match ours.
	 */
	@Nullable
	private CompoundTag nbt = new CompoundTag();

	@Nullable
	private LeafiaParticle particle = null;

	/** How many kinds the sender said it supports. */
	private int supports = 0;

	private LeafiaParticlePacket() { }

	@Override
	public CustomPacketPayload.Type<LeafiaParticlePacket> type() {
		return TYPE;
	}

	/** The position, motion and kind data; null when {@link #mismatched()}. */
	@Nullable
	public CompoundTag nbt() {
		return nbt;
	}

	/** The decoded particle kind, or null when {@link #mismatched()}. */
	@Nullable
	public LeafiaParticle particle() {
		return particle;
	}

	public int supports() {
		return supports;
	}

	/** True when the sender and this build disagree about how many particle kinds exist. */
	public boolean mismatched() {
		return nbt == null;
	}

	/** How many kinds exist. This is the protocol version on the wire. */
	public static int registrySize() {
		return REGISTRY.size();
	}

	/** The id a kind is sent under, or -1 when it was never registered. */
	public static int idOf(LeafiaParticle particle) {
		for(int i = 0; i < REGISTRY.size(); i++) {
			if(REGISTRY.get(i).getClass().equals(particle.getClass())) return i;
		}
		return -1;
	}

	private static void register(LeafiaParticle prototype) {
		REGISTRY.add(prototype);
	}

	// ---- the kinds ---------------------------------------------------------------------------

	/**
	 * A vanilla particle with the knobs turned: {@code EffectNT}'s {@code "vanillaExt"}. The eleven
	 * modes are upstream's, in upstream's order, and the mode is four bits on the wire.
	 */
	public static class VanillaExt extends LeafiaParticle {

		public static final int FLAME = 0, SMOKE = 1, VOLCANO = 2, CLOUD = 3, REDDUST = 4, BLUEDUST = 5,
				GREENDUST = 6, LARGEEXPLODE = 7, TOWNAURA = 8, BLOCKDUST = 9, LAVA = 10;

		/** The mode strings {@code EffectNT} and the base mod's creator read, indexed by mode. */
		public static final String[] MODES = { "flame", "smoke", "volcano", "cloud", "reddust", "bluedust",
				"greendust", "largeexplode", "townaura", "blockdust", "lava" };

		public int mode;
		/** {@code BlockDust}: the block state's id. Upstream carried a block id. */
		public int blockdust_block;
		public byte largeexplode_count;
		public float largeexplode_size;

		protected VanillaExt() { }

		public static VanillaExt Flame() { return of(FLAME); }
		public static VanillaExt Smoke() { return of(SMOKE); }
		public static VanillaExt Volcano() { return of(VOLCANO); }
		public static VanillaExt Cloud() { return of(CLOUD); }
		public static VanillaExt RedDust() { return of(REDDUST); }
		public static VanillaExt BlueDust() { return of(BLUEDUST); }
		public static VanillaExt GreenDust() { return of(GREENDUST); }
		public static VanillaExt TownAura() { return of(TOWNAURA); }
		public static VanillaExt Lava() { return of(LAVA); }

		public static VanillaExt LargeExplode(int count, float size) {
			VanillaExt self = of(LARGEEXPLODE);
			self.largeexplode_count = (byte) count;
			self.largeexplode_size = size;
			return self;
		}

		/** Upstream's {@code BlockDust(int blockId)}, with a state in place of a block and its metadata. */
		public static VanillaExt BlockDust(BlockState state) {
			return BlockDust(Block.getId(state));
		}

		public static VanillaExt BlockDust(int stateId) {
			VanillaExt self = of(BLOCKDUST);
			self.blockdust_block = stateId;
			return self;
		}

		private static VanillaExt of(int mode) {
			VanillaExt self = new VanillaExt();
			self.mode = mode;
			return self;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {

			VanillaExt read = new VanillaExt();
			nbt.putString("type", "vanillaExt");
			read.mode = buf.extract(4);

			// A mode this build does not know is left as an empty string, which no creator claims,
			// so it draws nothing - upstream's switch has no default either.
			if(read.mode < MODES.length) nbt.putString("mode", MODES[read.mode]);

			switch(read.mode) {
			case LARGEEXPLODE:
				read.largeexplode_count = buf.readByte();
				read.largeexplode_size = buf.readFloat();
				nbt.putByte("count", read.largeexplode_count);
				nbt.putFloat("size", read.largeexplode_size);
				break;
			case BLOCKDUST:
				read.blockdust_block = buf.readInt();
				nbt.putInt("block", read.blockdust_block);
				break;
			default:
				break;
			}

			return read;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.insert(mode, 4);
			switch(mode) {
			case LARGEEXPLODE:
				buf.writeByte(largeexplode_count);
				buf.writeFloat(largeexplode_size);
				break;
			case BLOCKDUST:
				buf.writeInt(blockdust_block);
				break;
			default:
				break;
			}
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.vanillaExt(nbt);
		}
	}

	/**
	 * Blood-red specks off a living body: the base mod's {@code "sweat"} effect (a cloud of block
	 * particles around the entity's box). Damaged skin and the pointed-item hazard are its senders.
	 */
	public static class Sweat extends LeafiaParticle {

		public int entityId;
		/** The block state's id; upstream sent a block id and a metadata value. */
		public int stateId;
		public int count;

		protected Sweat() { }

		public Sweat(Entity entity, BlockState appearance, int amount) {
			this.entityId = entity.getId();
			this.stateId = Block.getId(appearance);
			this.count = amount;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {

			Sweat read = new Sweat();
			read.entityId = buf.readInt();
			read.stateId = buf.readInt();
			read.count = buf.readInt();

			nbt.putString("type", "sweat");
			nbt.putInt("entity", read.entityId);
			nbt.putInt("block", read.stateId);
			nbt.putInt("count", read.count);
			return read;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeInt(entityId);
			buf.writeInt(stateId);
			buf.writeInt(count);
		}
	}

	/**
	 * Alkali metal burning: {@code period} 3 is the hot one, drawn with the addon's own two fire
	 * particles; below it is vanilla flame, and from 3 up half the time a lava spark joins.
	 */
	public static class AlkaliFire extends LeafiaParticle {

		public int period = 1;

		protected AlkaliFire() { }

		public AlkaliFire(int period) {
			this.period = period;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new AlkaliFire(buf.readByte());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeByte(period);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.alkaliFire(period, nbt);
		}
	}

	/** The core's blast: {@code amount} {@code ParticleDFC}s in one colour. */
	public static class DFCBlastParticle extends LeafiaParticle {

		public float red;
		public float green;
		public float blue;
		public int amount;

		protected DFCBlastParticle() { }

		public DFCBlastParticle(float red, float green, float blue, int amount) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.amount = amount;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new DFCBlastParticle(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeFloat(red);
			buf.writeFloat(green);
			buf.writeFloat(blue);
			buf.writeInt(amount);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.dfcBlast(red, green, blue, amount, nbt);
		}
	}

	/** A white flash that swells over {@code ticksIn} and fades over {@code ticksOut}. */
	public static class FlashParticle extends LeafiaParticle {

		public int ticksIn = 5;
		public int ticksOut = 10;
		public float scale = 10;

		public FlashParticle() { }

		public FlashParticle(int ticksIn, int ticksOut, float scale) {
			this.ticksIn = ticksIn;
			this.ticksOut = ticksOut;
			this.scale = scale;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new FlashParticle(buf.readShort(), buf.readShort(), buf.readFloat());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeShort(ticksIn);
			buf.writeShort(ticksOut);
			buf.writeFloat(scale);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.flash(ticksIn, ticksOut, scale, nbt);
		}
	}

	/** A ring of smoke racing outward along the ground: four numbers, upstream's defaults. */
	public static class SmokeShockwaveParticle extends LeafiaParticle {

		public double increment = 3;
		public float particleScale = 5;
		public double maxRadius = 200;
		public int particleLifetime = 12;

		public SmokeShockwaveParticle() { }

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			SmokeShockwaveParticle read = new SmokeShockwaveParticle();
			read.increment = buf.readDouble();
			read.particleScale = buf.readFloat();
			read.maxRadius = buf.readDouble();
			read.particleLifetime = buf.readInt();
			return read;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeDouble(increment);
			buf.writeFloat(particleScale);
			buf.writeDouble(maxRadius);
			buf.writeInt(particleLifetime);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.smokeShockwave(increment, particleScale, maxRadius, particleLifetime, nbt);
		}
	}

	/** A jet of RBMK steam. {@code maxAge} is one signed byte on the wire, as upstream's. */
	public static class RBMKJetParticle extends LeafiaParticle {

		public int maxAge;

		protected RBMKJetParticle() { }

		public RBMKJetParticle(int maxAge) {
			this.maxAge = maxAge;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new RBMKJetParticle(buf.readByte());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeByte(maxAge);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.rbmkJet(maxAge, nbt);
		}
	}

	/**
	 * A puff of the modular turbine's steam, moving by the packet's motion for {@code travelDistance}.
	 *
	 * <p>Upstream's {@code fromBits} returns {@code new MTSteamParticle(travelDistance)} - the
	 * <em>prototype's</em> field, which is zero - and never reads the int it was sent. Corrected
	 * here; see the class note and D-3000.
	 */
	public static class MTSteamParticle extends LeafiaParticle {

		public int travelDistance;

		protected MTSteamParticle() { }

		public MTSteamParticle(int travelDistance) {
			this.travelDistance = travelDistance;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new MTSteamParticle(buf.readInt());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeInt(travelDistance);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.mtSteam(travelDistance, nbt);
		}
	}

	/** The DFC's detonation sphere: its radius, lifetime and the ticks of screen flash. */
	public static class DFCNukeParticle extends LeafiaParticle {

		public int radius = 250;
		public int maxAge = 20 * 3;
		public int flashTime = 50;

		public DFCNukeParticle() { }

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			DFCNukeParticle read = new DFCNukeParticle();
			read.radius = buf.readInt();
			read.maxAge = buf.readInt();
			read.flashTime = buf.readInt();
			return read;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeInt(radius);
			buf.writeInt(maxAge);
			buf.writeInt(flashTime);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.dfcNuke(radius, maxAge, flashTime, nbt);
		}
	}

	/**
	 * The RBMK's meltdown mushroom in pink, at size 12: the base mod's {@code ParticleRBMKMush} tinted
	 * {@code (1, 0, 1)} through its tint seam (D-2197), which is what upstream's {@code setPink} drew. No body
	 * on the wire. Sent by the pink door (upstream's {@code BlockPinkDoor}), which is not ported yet (D-3018).
	 */
	public static class PinkRBMK extends LeafiaParticle {

		public PinkRBMK() { }

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new PinkRBMK();
		}

		@Override
		protected void toBits(LeafiaBuf buf) { }

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.pinkRBMK(nbt);
		}
	}

	/**
	 * A damaged RBMK column's lid jumping: the column's position on the wire, and a
	 * {@code ParticleJumpingRBMK} on the client that draws the lid in the air for as long as the column's
	 * synced jump height is above zero. Sent by the RBMK overhaul's tick when a lid leaves its column (D-3018).
	 */
	public static class JumpingRBMKParticle extends LeafiaParticle {

		public BlockPos rbmkPos;

		public JumpingRBMKParticle() { }

		public JumpingRBMKParticle(BlockPos rbmkPos) {
			this.rbmkPos = rbmkPos;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			return new JumpingRBMKParticle(buf.readPos());
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeVec3i(rbmkPos);
		}

		/** Upstream's {@code emitServer}: from the column's base centre, upward, to players within 128. */
		public void emitServer(ServerLevel level) {
			emit(level, new Vec3(rbmkPos.getX() + 0.5, rbmkPos.getY(), rbmkPos.getZ() + 0.5), new Vec3(0, 1, 0), 128);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.jumpingRBMK(rbmkPos.getX(), rbmkPos.getY(), rbmkPos.getZ());
		}
	}

	/**
	 * A spray of {@code ParticleSpark}s: upstream's {@code FiaSpark} (the tau cannon's, "stolen from tau cannon
	 * lmao" in its own comment, but through the addon's own particle and not the base mod's {@code spark}
	 * effect). Each of {@link #count} sparks flies along the packet's motion vector, turned by up to
	 * {@link #spread} degrees on every axis, at a speed between {@link #speedMin} and {@link #speedMax},
	 * trailing {@link #length} segments of {@link #thickness}.
	 */
	public static class FiaSpark extends LeafiaParticle {

		public int color = 0x80EEFF; // RGB format
		public double spread = 45;
		public double speedMin = 0.15;
		public double speedMax = 0.35;
		public int count = 1;
		public int length = 8;
		public float thickness = 0.02f;
		public float segmentsPerTick = 3;

		public FiaSpark() { }

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			FiaSpark spark = new FiaSpark();
			spark.color = buf.readInt();
			spark.spread = buf.readDouble();
			spark.speedMin = buf.readDouble();
			spark.speedMax = buf.readDouble();
			spark.count = buf.readInt();
			spark.length = buf.readInt();
			spark.thickness = buf.readFloat();
			spark.segmentsPerTick = buf.readFloat();
			return spark;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeInt(color);
			buf.writeDouble(spread);
			buf.writeDouble(speedMin);
			buf.writeDouble(speedMax);
			buf.writeInt(count);
			buf.writeInt(length);
			buf.writeFloat(thickness);
			buf.writeFloat(segmentsPerTick);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.fiaSpark(color, spread, speedMin, speedMax, count, length, thickness, segmentsPerTick, nbt);
		}
	}

	/**
	 * One lightning path out of the core, as a particle: upstream's {@code DFCShockParticleEditionTM}, the
	 * "particle edition" of the shock the core sends as a packet of its own. Up to 255 points, three
	 * <em>doubles</em> each (the core's own {@code DFCShockPacket} narrows them to floats).
	 */
	public static class DFCShockParticleEditionTM extends LeafiaParticle {

		public List<Vec3> poses0 = new ArrayList<>();

		public DFCShockParticleEditionTM() { }

		public DFCShockParticleEditionTM(List<Vec3> poses0) {
			this.poses0 = poses0;
		}

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			DFCShockParticleEditionTM particle = new DFCShockParticleEditionTM();
			int length = buf.readByte();
			particle.poses0 = new ArrayList<>(length);
			for(int i = 0; i < length; i++) particle.poses0.add(buf.readVec3d());
			return particle;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeByte(poses0.size());
			for(Vec3 vec : poses0) buf.writeVec3d(vec);
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.dfcShock(poses0, nbt);
		}
	}

	/**
	 * A burst of sparks in a cone: upstream's {@code TauSpark} ("stolen from tau cannon lmao"), which hands NTM-CE's
	 * {@code "spark"} effect a {@code "coneBurst"} mode. Neither the base mod nor {@code hbm-src} has that effect, so the
	 * addon registers its own creator for it (D-3030 #4, D-3031) under {@link #EFFECT}, the one key that differs from
	 * upstream's tag ({@code "spark"}): a bare name in the base mod's creator table would collide with any future base
	 * effect of that name, and a collision there throws at startup. Every other key is upstream's, value for value -
	 * including the alpha, which upstream's colour carries <em>inverted</em> ("i-ARGB": a top byte of 0 is opaque), and the
	 * angle, which upstream writes as a literal {@code 70} whatever {@link #angle} holds. Nothing upstream sends one (its
	 * only caller is commented out in {@code WorldServerLeafia}), so this is reachable only by a sender of the packet.
	 */
	public static class TauSpark extends LeafiaParticle {

		/** The effect type this port's creator answers to. Upstream's tag says {@code "spark"}. */
		public static final String EFFECT = "leafia:spark";

		public int color = 0xE6E6FF; // i-ARGB format
		public int count = 7; // preferrably 5+rand(3)
		public float angle = 70F;
		public float life = 1;
		public float width = 0.01F;

		public TauSpark() { }

		@Override
		protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) {
			TauSpark spark = new TauSpark();
			spark.color = buf.readInt();
			spark.count = buf.readInt();
			spark.angle = buf.readFloat();
			spark.life = buf.readFloat();
			spark.width = buf.readFloat();
			return spark;
		}

		@Override
		protected void toBits(LeafiaBuf buf) {
			buf.writeInt(color);
			buf.writeInt(count);
			buf.writeFloat(angle);
			buf.writeFloat(life);
			buf.writeFloat(width);
		}

		/** Upstream's {@code emit} body: the effect's keys, written into the packet's data buffer. Server-safe. */
		public CompoundTag writeEffect(CompoundTag tag) {
			tag.putString("type", EFFECT);
			tag.putString("mode", "coneBurst");
			tag.putFloat("r", (color >>> 16 & 0xFF) / 255f);
			tag.putFloat("g", (color >>> 8 & 0xFF) / 255f);
			tag.putFloat("b", (color & 0xFF) / 255f);
			tag.putFloat("a", 1 - (color >>> 24 & 0xFF) / 255f);
			tag.putInt("lifetime", (int) (5 * life));
			tag.putInt("randLifetime", (int) (8 * life));
			tag.putFloat("width", width);
			tag.putFloat("length", 0.5F);
			tag.putFloat("gravity", 0.1F);
			tag.putFloat("angle", 70F);
			tag.putInt("count", count);
			tag.putFloat("randomVelocity", 0.1F);
			return tag;
		}

		/**
		 * The {@code coneBurst} creator's aim (D-3031): a direction inside the cone of half-angle {@code halfAngleDeg}
		 * round {@code axis} (straight up for a zero axis), for two uniform numbers - {@code u} picks the angle from the
		 * axis, uniform over the cap's area, and {@code w} the turn round it. Here rather than in the client creator so a
		 * dedicated server's test can pin it.
		 */
		public static Vec3 coneDirection(Vec3 axis, double halfAngleDeg, double u, double w) {
			Vec3 d = axis.lengthSqr() < 1.0E-12 ? new Vec3(0, 1, 0) : axis.normalize();
			Vec3 helper = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
			Vec3 a = d.cross(helper).normalize();
			Vec3 b = d.cross(a);
			double cosMax = Math.cos(Math.toRadians(halfAngleDeg));
			double cos = 1 - u * (1 - cosMax);
			double sin = Math.sqrt(Math.max(0, 1 - cos * cos));
			double phi = w * Math.PI * 2;
			return d.scale(cos).add(a.scale(Math.cos(phi) * sin)).add(b.scale(Math.sin(phi) * sin));
		}

		@Override
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.dispatch(writeEffect(nbt));
		}
	}

	/*
	 * The registry. APPEND ONLY: an id is an index, so a line inserted above another moves every
	 * kind below it and a client and server a build apart decode each other's particles as the
	 * wrong ones (the count check below catches a different length, not a different order).
	 */
	static {
		register(new VanillaExt());
		register(new Sweat());
		register(new AlkaliFire());
		register(new DFCBlastParticle());
		register(new FlashParticle());
		register(new SmokeShockwaveParticle());
		register(new RBMKJetParticle());
		register(new MTSteamParticle());
		register(new DFCNukeParticle());
		// The RBMK batch (D-3018): upstream's two RBMK kinds, in its declaration order.
		register(new PinkRBMK());
		register(new JumpingRBMKParticle());
		// DFC-4 (D-3017): the two whose particles landed with the DFC renderers, in upstream's relative order
		// (FiaSpark before DFCShockParticleEditionTM). Ids 11 and 12: the RBMK batch took 9 and 10 first.
		register(new FiaSpark());
		register(new DFCShockParticleEditionTM());
		// SEAM-TAIL (D-3031): upstream's last one, id 13, once the addon wrote the "coneBurst" creator it hands off to.
		register(new TauSpark());
	}

	// ---- the base every kind extends ----------------------------------------------------------

	/**
	 * One kind of particle. A kind in the registry is a <b>prototype</b>: {@link #fromBits} is called
	 * on it and must return a new instance, never {@code this}, because {@code this} is the one
	 * every later packet will be decoded through.
	 */
	public abstract static class LeafiaParticle {

		protected LeafiaParticle() { }

		/**
		 * Reads this kind's body from a packet.
		 *
		 * @param nbt the packet's data buffer, which the client-side effect reads: a kind that hands
		 *        off to a base-mod effect type writes that effect's keys here
		 * @return a new instance of this kind holding what was read
		 */
		protected abstract LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt);

		/** Writes everything {@link #fromBits} reads, in the same order. */
		protected abstract void toBits(LeafiaBuf buf);

		/**
		 * Draws the particle. Client only, and called only from {@link #handle} or
		 * {@link #emitLocal}. The default is the base mod's effect dispatch, which is what upstream's
		 * {@code MainRegistry.proxy.effectNT} was.
		 */
		protected void emit(CompoundTag nbt) {
			LeafiaParticleClient.dispatch(nbt);
		}

		/** Builds the packet that carries this particle to a client. */
		public final LeafiaParticlePacket packet(Vec3 pos, Vec3 motion) {
			LeafiaParticlePacket packet = new LeafiaParticlePacket();
			packet.nbt = dataFor(pos, motion);
			packet.particle = this;
			packet.supports = REGISTRY.size();
			return packet;
		}

		/** The maximum distance at which this particle is sent. Default is upstream's 340. */
		public double getDefaultRange() {
			return DEFAULT_RANGE;
		}

		public final void emit(ServerLevel level, Vec3 pos, Vec3 motion) {
			emit(level, pos, motion, getDefaultRange());
		}

		/**
		 * Sends the particle to every player in {@code level} within {@code range} blocks of
		 * {@code pos}. A player with no real connection to this channel - a GameTest mock, another
		 * mod's fake player - is skipped: it would see nothing, and sending to it throws out of the
		 * caller (D-631).
		 */
		public final void emit(ServerLevel level, Vec3 pos, Vec3 motion, double range) {

			LeafiaParticlePacket packet = null;
			double rangeSq = range * range;

			for(ServerPlayer player : level.players()) {
				if(player.distanceToSqr(pos) > rangeSq) continue;
				if(player.connection == null || !player.connection.hasChannel(TYPE)) continue;

				if(packet == null) packet = packet(pos, motion);
				PacketDistributor.sendToPlayer(player, packet);
			}
		}

		/** Draws the particle on this client without a packet. Client only. */
		public final void emitLocal(Vec3 pos, Vec3 motion) {
			emit(dataFor(pos, motion));
		}

		private static CompoundTag dataFor(Vec3 pos, Vec3 motion) {
			CompoundTag nbt = new CompoundTag();
			nbt.putDouble("posX", pos.x);
			nbt.putDouble("posY", pos.y);
			nbt.putDouble("posZ", pos.z);
			// Upstream narrows the motion through a float, so what a client sees is what the wire carries.
			nbt.putDouble("mX", (float) motion.x);
			nbt.putDouble("mY", (float) motion.y);
			nbt.putDouble("mZ", (float) motion.z);
			return nbt;
		}
	}

	// ---- the codec ----------------------------------------------------------------------------

	/** Upstream's {@code toBits}. */
	private void toBits(LeafiaBuf buf) {

		CompoundTag data = this.nbt;

		buf.writeDouble(data.getDouble("posX"));
		buf.writeDouble(data.getDouble("posY"));
		buf.writeDouble(data.getDouble("posZ"));
		buf.writeFloat((float) data.getDouble("mX"));
		buf.writeFloat((float) data.getDouble("mY"));
		buf.writeFloat((float) data.getDouble("mZ"));
		buf.writeByte(REGISTRY.size());

		int id = idOf(this.particle);

		if(id < 0) {
			// Upstream throws LeafiaDevFlaw here. A kind that was never registered is a developer
			// mistake that cannot be recovered at runtime, so it fails loudly and at the source.
			throw new IllegalStateException("Invalid particle " + this.particle.getClass().getName()
					+ ": it is not in LeafiaParticlePacket's registry");
		}

		buf.writeByte(id);
		this.particle.toBits(buf);
	}

	/** Upstream's {@code fromBits}. */
	private void fromBits(LeafiaBuf buf) {

		nbt.putDouble("posX", buf.readDouble());
		nbt.putDouble("posY", buf.readDouble());
		nbt.putDouble("posZ", buf.readDouble());
		nbt.putDouble("mX", buf.readFloat());
		nbt.putDouble("mY", buf.readFloat());
		nbt.putDouble("mZ", buf.readFloat());

		supports = buf.readUnsignedByte();

		if(supports != REGISTRY.size()) {
			nbt = null;
			return;
		}

		int id = buf.readUnsignedByte();

		if(id >= REGISTRY.size()) {
			nbt = null;
			return;
		}

		particle = REGISTRY.get(id).fromBits(buf, nbt);
	}

	private static void write(RegistryFriendlyByteBuf out, LeafiaParticlePacket packet) {

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
		packet.toBits(buf);

		int length = buf.payloadLength();
		byte[] data = new byte[length];
		System.arraycopy(buf.bytes, buf.payloadOffset(), data, 0, length);

		LeafiaPacketStats.record(length);
		ByteBufCodecs.VAR_INT.encode(out, buf.writerIndex);
		ByteBufCodecs.byteArray(MAX_PAYLOAD).encode(out, data);
	}

	private static LeafiaParticlePacket read(RegistryFriendlyByteBuf in) {

		int bitLength = ByteBufCodecs.VAR_INT.decode(in);
		byte[] data = ByteBufCodecs.byteArray(MAX_PAYLOAD).decode(in);

		LeafiaPacketStats.record(data.length);

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
		buf.wrapOwned(data);
		buf.writerIndex = bitLength;

		LeafiaParticlePacket packet = new LeafiaParticlePacket();
		packet.fromBits(buf);
		return packet;
	}

	/** Upstream's {@code Handler.onMessage}. */
	public static void handle(LeafiaParticlePacket packet, IPayloadContext context) {

		if(packet.mismatched()) {
			context.disconnect(protocolMismatch(packet.supports));
			return;
		}

		CompoundTag data = packet.nbt;
		LeafiaParticle particle = packet.particle;

		// Particles are added to the engine on the game thread; this arrives on the network thread.
		context.enqueueWork(() -> particle.emit(data));
	}

	/** Upstream's disconnect screen, line for line. */
	private static Component protocolMismatch(int theirs) {
		return Component.literal("########").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" NTM:LCE FATAL ERROR ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal("########\nInvalid protocol on LeafiaParticlePacket").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("\nThe server supports " + theirs + " particles. Your client supports "
						+ REGISTRY.size()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\n\nPossible reasons are:").withStyle(ChatFormatting.AQUA))
				.append(Component.literal("\n- Your client is outdated. Check for any updates on github (I don't add version numbers!)"
						+ "\n- Or the server is outdated. Contact server owner"
						+ "\n- Else perhaps it's some unpredictable fucks going on idfk").withStyle(ChatFormatting.GRAY));
	}
}
