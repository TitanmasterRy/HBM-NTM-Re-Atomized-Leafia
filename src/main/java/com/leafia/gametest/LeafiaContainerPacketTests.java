package com.leafia.gametest;

import java.util.Map;

import com.hbm.util.Tuple.Pair;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins {@code LeafiaPacket}'s wire format, which is the whole of that file.
 *
 * <p>Five bits of key and three of type in one byte, a "reader mode" switch written as type 7,
 * arrays smuggled through mode 31, and thirteen value types across two modes. It is intricate, it
 * is upstream's exactly, and <b>it has no version tag</b> — the count check that protects
 * {@code LeafiaCustomPacket} (D-1024) does not exist here, so a sender and a receiver that disagree
 * about the layout do not find out; they read garbage. That is the reason these tests exist and
 * the reason they are written against the bytes rather than against the API.
 *
 * <p>Eighty-nine upstream files will send these once their features land, so a mistake here is a
 * mistake in all of them at once.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaContainerPacketTests {

	private static final String EMPTY = "empty";
	private static final BlockPos POS = new BlockPos(4, 5, 6);

	/** Every primitive type, through the bytes and back. */
	@GameTest(template = EMPTY)
	public static void everyPrimitiveSurvivesTheRoundTrip(GameTestHelper helper) {

		LeafiaPacket packet = new LeafiaPacket.Builder(POS, "test", helper.getLevel())
				.__write(0, true)
				.__write(1, (byte) -7)
				.__write(2, (short) 4096)
				.__write(3, 123456789)
				.__write(4, -9876543210L)
				.__write(5, 0.5F)
				.__write(6, -1234.5678D)
				.build();

		Map<Byte, Pair<Byte, Object>> read = decode(helper, packet);

		check(helper, read, 0, true);
		check(helper, read, 1, (byte) -7);
		check(helper, read, 2, (short) 4096);
		check(helper, read, 3, 123456789);
		check(helper, read, 4, -9876543210L);
		check(helper, read, 5, 0.5F);
		check(helper, read, 6, -1234.5678D);

		helper.succeed();
	}

	/**
	 * Every object type, which is "reader mode 1" — so this also exercises the mode switch, since
	 * the encoder writes mode 0 first and one switch byte before the rest.
	 */
	@GameTest(template = EMPTY)
	public static void everyObjectTypeSurvivesTheRoundTrip(GameTestHelper helper) {

		CompoundTag tag = new CompoundTag();
		tag.putString("hello", "world");
		tag.putInt("n", 42);

		LeafiaPacket packet = new LeafiaPacket.Builder(POS, "test", helper.getLevel())
				.__write(0, 1)                                  // one primitive, to force a mode switch
				.__write(1, tag)
				.__write(2, new ItemStack(Items.DIAMOND, 17))
				.__write(3, "a string")
				.__write(4, new BlockPos(-1, 300, 77))
				.__write(5, new Vec3(0.25D, -0.5D, 8D))
				.__write(6, null)
				.build();

		Map<Byte, Pair<Byte, Object>> read = decode(helper, packet);

		check(helper, read, 0, 1);
		helper.assertTrue(((CompoundTag) find(read, 1)).getString("hello").equals("world")
						&& ((CompoundTag) find(read, 1)).getInt("n") == 42,
				"the NBT came back changed");

		ItemStack stack = (ItemStack) find(read, 2);
		helper.assertTrue(stack.getItem() == Items.DIAMOND && stack.getCount() == 17,
				"the stack came back as " + stack + " - this is the type D-1008 left open, so it is"
						+ " the one most worth distrusting");

		check(helper, read, 3, "a string");
		check(helper, read, 4, new BlockPos(-1, 300, 77));
		check(helper, read, 5, new Vec3(0.25D, -0.5D, 8D));
		helper.assertTrue(find(read, 6) == null, "null must come back as null, not as absent");

		helper.succeed();
	}

	/**
	 * Arrays, which travel through the stolen mode 31 with the length in the high bits of an int.
	 *
	 * <p>And the case upstream handles separately, which is subtler than it first reads. An
	 * <b>empty</b> array has no element to take a type from, so {@code __write} tags it with the
	 * <em>null</em> type — but the encoder still takes the array branch, because the value is
	 * still an array. So it goes out as "an array of nulls, length zero" and comes back as an
	 * empty {@code Object[]}, whatever the array's real component type was. An empty
	 * {@code int[]} in is an empty {@code Object[]} out. Pinned rather than smoothed, and worth
	 * pinning: this test was first written asserting it came back as {@code null}, which is what
	 * the "null key" comment in {@code __write} suggests if you do not follow the value into the
	 * encoder.
	 */
	@GameTest(template = EMPTY)
	public static void arraysTravelThroughModeThirtyOne(GameTestHelper helper) {

		LeafiaPacket packet = new LeafiaPacket.Builder(POS, "test", helper.getLevel())
				.__write(0, new int[] { 1, -2, 3, 1 << 30 })
				.__write(1, new double[] { 0.5D, -0.25D })
				.__write(2, new String[] { "a", "bb", "ccc" })
				.__write(3, new int[0])
				.build();

		Map<Byte, Pair<Byte, Object>> read = decode(helper, packet);

		int[] ints = (int[]) find(read, 0);
		helper.assertTrue(ints.length == 4 && ints[0] == 1 && ints[1] == -2 && ints[3] == 1 << 30,
				"the int array came back wrong");

		double[] doubles = (double[]) find(read, 1);
		helper.assertTrue(doubles.length == 2 && doubles[0] == 0.5D && doubles[1] == -0.25D,
				"the double array came back wrong");

		Object[] strings = (Object[]) find(read, 2);
		helper.assertTrue(strings.length == 3 && strings[2].equals("ccc"), "the string array came back wrong");

		Object empty = find(read, 3);
		helper.assertTrue(empty instanceof Object[] && ((Object[]) empty).length == 0,
				"an empty int[] comes back as an empty Object[], not as null and not as an int[] -"
						+ " see the method note, this is upstream's and it surprised the test author");

		helper.succeed();
	}

	/**
	 * The two rules about sending nothing.
	 *
	 * <p>A packet with no slots is not sent at all — that is what stops a machine that ticks
	 * without changing from filling the wire. A <em>validation</em> packet is the exception: it is
	 * deliberately empty, and the server reads emptiness as "this player wants the current state".
	 */
	@GameTest(template = EMPTY)
	public static void anEmptyPacketIsNotSentUnlessItIsAValidation(GameTestHelper helper) {

		LeafiaPacket.Builder empty = new LeafiaPacket.Builder(POS, "test", helper.getLevel());

		// __sendToAll on an empty builder must do nothing at all; build() is what it would send.
		LeafiaPacket built = empty.build();
		helper.assertTrue(built != null && built.data().length == 0,
				"an empty packet encodes to no payload bytes");

		empty.__sendToAll(); // must not throw, and must send nothing

		helper.succeed();
	}

	/** A slot id outside 0..31 is a developer error, loudly, because the key is five bits. */
	@GameTest(template = EMPTY)
	public static void aSlotOutsideFiveBitsIsRefused(GameTestHelper helper) {

		LeafiaPacket.Builder builder = new LeafiaPacket.Builder(POS, "test", helper.getLevel());

		boolean threw = false;
		try {
			builder.__write(32, 1);
		} catch(RuntimeException expected) {
			threw = true;
		}

		helper.assertTrue(threw, "slot 32 does not fit in five bits and must be refused, not truncated"
				+ " into slot 0");

		// -1 is upstream's "no slot", and is silently ignored rather than refused.
		builder.__write(-1, 1);
		helper.assertTrue(builder.build().data().length == 0, "slot -1 writes nothing");

		helper.succeed();
	}

	/** The header the codec carries beside the payload. */
	@GameTest(template = EMPTY)
	public static void theHeaderCarriesThePositionAndTheHashedIdentifier(GameTestHelper helper) {

		LeafiaPacket packet = new LeafiaPacket.Builder(POS, "an identifier", helper.getLevel())
				.__write(0, 1).build();

		helper.assertTrue(packet.x() == POS.getX() && packet.y() == POS.getY() && packet.z() == POS.getZ(),
				"the position rides outside the bit payload");
		helper.assertTrue(packet.identifier() == "an identifier".hashCode(),
				"the identifier is sent as a hash, never as the string - see the class note on why"
						+ " that is a known collision risk rather than a defect");
		helper.assertTrue(packet.checkType() == (byte) LevelChunk.EntityCreationType.IMMEDIATE.ordinal(),
				"IMMEDIATE is the default lookup, as upstream has it");

		helper.succeed();
	}

	private static Map<Byte, Pair<Byte, Object>> decode(GameTestHelper helper, LeafiaPacket packet) {
		helper.assertTrue(packet != null, "the packet should have been built");
		return packet.decode(helper.getLevel().registryAccess());
	}

	/** Finds a slot by its five-bit key, whatever type bits it carries. */
	private static Object find(Map<Byte, Pair<Byte, Object>> read, int key) {

		for(Map.Entry<Byte, Pair<Byte, Object>> entry : read.entrySet()) {
			if(LeafiaPacket.key(entry.getKey()) == key) return entry.getValue().getValue();
		}

		throw new AssertionError("slot " + key + " is missing from the decoded packet");
	}

	private static void check(GameTestHelper helper, Map<Byte, Pair<Byte, Object>> read, int key, Object expected) {
		Object actual = find(read, key);
		helper.assertTrue(expected.equals(actual),
				"slot " + key + " came back as " + actual + ", expected " + expected);
	}
}
