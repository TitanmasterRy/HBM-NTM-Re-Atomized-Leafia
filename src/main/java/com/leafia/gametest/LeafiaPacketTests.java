package com.leafia.gametest;

import java.util.function.Consumer;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket.SyncVelocityPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.dev.optimization.diagnosis.LeafiaPacketStats;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the addon's packet layer: the framing, the registry and the protocol check.
 *
 * <p>What is <b>not</b> here is a packet actually crossing a connection — a GameTest server has no
 * client to send one to, and {@code handle} wants an {@link IPayloadContext} that only a live
 * connection has. So the seam is {@code LeafiaCustomPacket.open}, which reads the header and stops:
 * everything below the game thread is covered, and what is left uncovered is NeoForge's own
 * delivery.
 *
 * <p>The claims worth pinning are all about <b>ids on the wire</b>. The type id is an index into
 * {@link LeafiaPacketTypes}, so a wrong one does not fail — it decodes the packet as a different
 * packet, which is the failure mode upstream's count check exists to prevent.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaPacketTests {

	private static final String EMPTY = "empty";

	/** A packet written and read back is the same packet, contents and all. */
	@GameTest(template = EMPTY)
	public static void aPacketSurvivesTheRoundTrip(GameTestHelper helper) {

		Vec3 sent = new Vec3(1.5D, -2.25D, 1024.125D);

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new SyncVelocityPacket(sent));
		helper.assertTrue(packet != null, "a registered encoder must produce a packet");

		LeafiaCustomPacket.Opened opened = LeafiaCustomPacket.open(packet);

		helper.assertTrue(!opened.mismatched(), "a packet we just wrote cannot mismatch our own count");
		helper.assertTrue(opened.prototype() instanceof SyncVelocityPacket,
				"and must dispatch back to the type that wrote it, not to its neighbour");

		Vec3 read = opened.buf().readVec3d();
		helper.assertTrue(read.equals(sent), "velocity came back as " + read + ", sent " + sent);

		helper.succeed();
	}

	/**
	 * The header is upstream's bit packing: type in the high byte, type <em>count</em> in the low
	 * one.
	 *
	 * <p>Read straight off the bytes rather than through {@code open}, so that this fails if the
	 * layout changes even when reader and writer change together and agree with each other.
	 */
	@GameTest(template = EMPTY)
	public static void theHeaderPacksTheTypeAndTheCountUpstreamsWay(GameTestHelper helper) {

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new SyncVelocityPacket(Vec3.ZERO));

		LeafiaBuf buf = new LeafiaBuf(io.netty.buffer.Unpooled.buffer());
		buf.wrapOwned(packet.data());
		buf.writerIndex = packet.bitLength();

		int header = buf.readShort() & 0xFFFF;

		helper.assertTrue((header >> 8) == LeafiaPacketTypes.SYNC_VELOCITY,
				"high byte is the type id, got " + (header >> 8) + " for id " + LeafiaPacketTypes.SYNC_VELOCITY);
		helper.assertTrue((header & 0xFF) == LeafiaPacketTypes.count(),
				"low byte is how many types this build knows, got " + (header & 0xFF)
						+ " of " + LeafiaPacketTypes.count());

		helper.succeed();
	}

	/**
	 * A sender that knows a different number of packet types is caught.
	 *
	 * <p>This is the check that keeps a version mismatch from silently decoding every packet as
	 * the wrong type. Built by hand rather than by {@code __start}, because {@code __start} can
	 * only write this build's own count — which is the point.
	 */
	@GameTest(template = EMPTY)
	public static void aSenderWithADifferentTypeCountIsRejected(GameTestHelper helper) {

		LeafiaBuf buf = new LeafiaBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeShort(LeafiaPacketTypes.SYNC_VELOCITY << 8 | (LeafiaPacketTypes.count() + 1));
		buf.writeVec3d(Vec3.ZERO);

		byte[] data = new byte[buf.payloadLength()];
		System.arraycopy(buf.bytes, buf.payloadOffset(), data, 0, data.length);

		LeafiaCustomPacket.Opened opened =
				LeafiaCustomPacket.open(new LeafiaCustomPacket(buf.writerIndex, data));

		helper.assertTrue(opened.mismatched(),
				"a packet claiming " + (LeafiaPacketTypes.count() + 1) + " types must be rejected by a"
						+ " build that knows " + LeafiaPacketTypes.count());
		helper.assertTrue(opened.prototype() == null,
				"and must not be dispatched anyway - the id it carries means something else");

		helper.succeed();
	}

	/** The registry: NONE is id 0 and is not a packet; a stranger has no id at all. */
	@GameTest(template = EMPTY)
	public static void theRegistryKeepsNoneAtZeroAndRejectsStrangers(GameTestHelper helper) {

		helper.assertTrue(LeafiaPacketTypes.NONE == 0, "upstream's NONE is ordinal 0");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.NONE) == null,
				"and carries no encoder, so a zero-initialised id names nothing");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.count()) == null,
				"an id past the end is null rather than an exception - a hostile packet must not"
						+ " take the connection down");

		LeafiaCustomPacketEncoder stranger = new LeafiaCustomPacketEncoder() {
			@Override public void encode(LeafiaBuf buf) { }
			@Override public Consumer<IPayloadContext> decode(LeafiaBuf buf) { return null; }
		};

		helper.assertTrue(LeafiaPacketTypes.idOf(stranger) == -1, "an unregistered encoder has no id");
		helper.assertTrue(LeafiaCustomPacket.__start(stranger) == null,
				"and __start drops it rather than sending a packet nothing can read");

		helper.succeed();
	}

	/** The byte counters upstream keeps, which are the evidence for bit-packing the wire at all. */
	@GameTest(template = EMPTY)
	public static void theByteCountersCountBothDirections(GameTestHelper helper) {

		LeafiaPacketStats.reset();

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new SyncVelocityPacket(Vec3.ZERO));
		double afterSend = LeafiaPacketStats.bytesUsage;

		helper.assertTrue(afterSend == packet.data().length,
				"encoding counts the payload once: " + afterSend + " for " + packet.data().length + " bytes");

		LeafiaPacketStats.rollSecond();
		helper.assertTrue(LeafiaPacketStats.previousByteUsageSec == afterSend
						&& LeafiaPacketStats.bytesUsageSec == 0,
				"rolling the second moves the tally and starts a new one");

		LeafiaPacketStats.reset();
		helper.succeed();
	}

	/**
	 * Three doubles fit in fewer bytes than three doubles.
	 *
	 * <p>Not a balance number, but the reason this whole layer exists rather than using the base
	 * mod's channel: {@code LeafiaBuf} packs bits, so the header — a type id and a count — costs
	 * two bytes rather than the four a plain int would, and the packet is smaller than the same
	 * fields written flat. If this ever fails, the addon is paying for a bit-packed format and
	 * getting a byte-aligned one.
	 */
	@GameTest(template = EMPTY)
	public static void theWireFormatIsActuallyPacked(GameTestHelper helper) {

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new SyncVelocityPacket(Vec3.ZERO));

		// 2 bytes of header + 3 doubles, if nothing is wasted.
		int flat = 2 + 3 * Double.BYTES;

		helper.assertTrue(packet.data().length <= flat,
				"a packed header plus three doubles should be at most " + flat + " bytes, got "
						+ packet.data().length);
		helper.assertTrue(packet.bitLength() == flat * 8,
				"and the bit length should be exactly " + (flat * 8) + ", got " + packet.bitLength());

		helper.succeed();
	}
}
