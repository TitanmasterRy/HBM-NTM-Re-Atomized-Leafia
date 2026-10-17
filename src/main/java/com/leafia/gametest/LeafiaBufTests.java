package com.leafia.gametest;

import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;
import com.llib.technical.FifthString;
import com.llib.technical.LeafiaBitByteUTF;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins {@link LeafiaBuf}, the addon's bit-packed wire format (L2.5).
 *
 * <p>A serialiser is the worst thing in a port to get subtly wrong. It does not crash — it
 * writes a value and reads back a different one, in a packet, on a machine nobody is looking
 * at, and the symptom surfaces as a GUI showing the wrong number weeks later. Every test here
 * is a round trip for that reason: a round trip cannot be passed by an encoder and decoder that
 * are both wrong in the same direction only if the bit widths also agree, and the width
 * assertions below check those separately.
 *
 * <p>Nothing in the addon calls this class yet — its consumer is the packet layer, which is
 * base-blocked. It is tested now precisely because it is easier to get a wire format right
 * before there is a machine sitting on top of it.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaBufTests {

	private static final String EMPTY = "empty";

	private static LeafiaBuf buf() {
		return new LeafiaBuf(Unpooled.buffer());
	}

	/** Every primitive comes back as it went in, and costs the bits it should. */
	@GameTest(template = EMPTY)
	public static void primitivesRoundTripAtTheirDeclaredWidths(GameTestHelper helper) {

		LeafiaBuf b = buf();
		int at = b.writerIndex;

		b.writeByte(-7);
		helper.assertTrue(b.writerIndex - at == 8, "a byte costs 8 bits");
		at = b.writerIndex;
		b.writeShort(-30000);
		helper.assertTrue(b.writerIndex - at == 16, "a short costs 16");
		at = b.writerIndex;
		b.writeMedium(0xABCDEF);
		helper.assertTrue(b.writerIndex - at == 24, "a medium costs 24");
		at = b.writerIndex;
		b.writeInt(Integer.MIN_VALUE);
		helper.assertTrue(b.writerIndex - at == 32, "an int costs 32");
		at = b.writerIndex;
		b.writeLong(Long.MIN_VALUE + 12345);
		helper.assertTrue(b.writerIndex - at == 64, "a long costs 64");
		at = b.writerIndex;
		b.writeFloat(1.5F);
		helper.assertTrue(b.writerIndex - at == 32, "a float costs 32");
		at = b.writerIndex;
		b.writeDouble(-2.75);
		helper.assertTrue(b.writerIndex - at == 64, "a double costs 64");
		at = b.writerIndex;
		b.writeChar('Z');
		helper.assertTrue(b.writerIndex - at == 16, "a char costs 16");

		helper.assertTrue(b.readByte() == -7, "byte");
		helper.assertTrue(b.readShort() == -30000, "short");
		helper.assertTrue(b.readUnsignedMedium() == 0xABCDEF, "medium");
		helper.assertTrue(b.readInt() == Integer.MIN_VALUE, "int");
		helper.assertTrue(b.readLong() == Long.MIN_VALUE + 12345, "long");
		helper.assertTrue(b.readFloat() == 1.5F, "float");
		helper.assertTrue(b.readDouble() == -2.75, "double");
		helper.assertTrue(b.readChar() == 'Z', "char");

		helper.assertTrue(!b.isReadable(), "and the buffer is exhausted, so nothing was mis-sized");

		helper.succeed();
	}

	/**
	 * The reason this class exists: a boolean costs one bit, not eight.
	 *
	 * <p>Eight booleans in a row occupy a single byte, and the value after them still reads
	 * back — which is the part that would break if the bit cursor were byte-rounded anywhere.
	 */
	@GameTest(template = EMPTY)
	public static void booleansCostOneBitEach(GameTestHelper helper) {

		LeafiaBuf b = buf();
		boolean[] flags = {true, false, true, true, false, false, true, false};
		for(boolean f : flags) b.writeBoolean(f);

		helper.assertTrue(b.writerIndex == 8,
				"eight booleans should occupy eight bits, got " + b.writerIndex);

		b.writeInt(0x0BADF00D); // a value straddling the byte boundary behind those flags
		for(boolean f : flags)
			helper.assertTrue(b.readBoolean() == f, "flag round trip");
		helper.assertTrue(b.readInt() == 0x0BADF00D, "and the int behind them survives");

		helper.succeed();
	}

	/** Unsigned reads, which are where a sign extension bug would hide. */
	@GameTest(template = EMPTY)
	public static void unsignedReadsDoNotSignExtend(GameTestHelper helper) {

		LeafiaBuf b = buf();
		b.writeByte(0xFF);
		b.writeShort(0xFFFF);
		b.writeInt(-1);

		helper.assertTrue(b.readUnsignedByte() == 255, "unsigned byte");
		helper.assertTrue(b.readUnsignedShort() == 65535, "unsigned short");
		helper.assertTrue(b.readUnsignedInt() == 4294967295L, "unsigned int");

		helper.succeed();
	}

	/** Positions and vectors, which the addon sends constantly. */
	@GameTest(template = EMPTY)
	public static void positionsAndVectorsRoundTrip(GameTestHelper helper) {

		LeafiaBuf b = buf();
		b.writeVec3i(new Vec3i(-40, 300, 7));
		b.writeVec3d(new Vec3(1.25, -0.5, 1e6));
		b.writeInt(-8).writeInt(64).writeInt(9); // a BlockPos, which reads as three ints

		helper.assertTrue(b.readVec3i().equals(new Vec3i(-40, 300, 7)), "Vec3i");
		helper.assertTrue(b.readVec3d().equals(new Vec3(1.25, -0.5, 1e6)), "Vec3");
		helper.assertTrue(b.readPos().equals(new BlockPos(-8, 64, 9)), "BlockPos");

		helper.succeed();
	}

	/** Bulk byte writes take an aligned fast path; the slow path has to agree with it. */
	@GameTest(template = EMPTY)
	public static void byteArraysAgreeOnBothAlignmentPaths(GameTestHelper helper) {

		byte[] payload = {1, -2, 3, -4, 5, 6, 7, -8, 9};

		// Aligned: the writer sits on a byte boundary and the bulk copy is used.
		LeafiaBuf aligned = buf();
		aligned.writeBytes(payload);
		byte[] backA = new byte[payload.length];
		aligned.readBytes(backA);
		for(int i = 0; i < payload.length; i++)
			helper.assertTrue(backA[i] == payload[i], "aligned byte " + i);

		// Unaligned: one boolean first, so every byte straddles two.
		LeafiaBuf unaligned = buf();
		unaligned.writeBoolean(true);
		unaligned.writeBytes(payload);
		helper.assertTrue(unaligned.readBoolean(), "the leading flag");
		byte[] backB = new byte[payload.length];
		unaligned.readBytes(backB);
		for(int i = 0; i < payload.length; i++)
			helper.assertTrue(backB[i] == payload[i], "unaligned byte " + i);

		helper.succeed();
	}

	/** NBT goes through vanilla's NbtIo over the buffer's DataInput/DataOutput adapters. */
	@GameTest(template = EMPTY)
	public static void nbtRoundTripsThroughTheStreamAdapters(GameTestHelper helper) {

		CompoundTag tag = new CompoundTag();
		tag.putInt("count", 42);
		tag.putString("name", "reactor");
		tag.putBoolean("scrammed", true);
		CompoundTag nested = new CompoundTag();
		nested.putDouble("temp", 431.5);
		tag.put("core", nested);

		LeafiaBuf b = buf();
		b.writeNBT(tag);
		CompoundTag back = b.readNBT();

		helper.assertTrue(back != null, "a written tag reads back");
		helper.assertTrue(back.getInt("count") == 42, "int field");
		helper.assertTrue(back.getString("name").equals("reactor"), "string field");
		helper.assertTrue(back.getBoolean("scrammed"), "boolean field");
		helper.assertTrue(back.getCompound("core").getDouble("temp") == 431.5, "nested compound");
		helper.assertTrue(back.equals(tag), "and the whole tag compares equal");

		// A null tag is a single zero byte, and reads back as null rather than throwing.
		LeafiaBuf empty = buf();
		empty.writeNBT(null);
		helper.assertTrue(empty.writerIndex == 8, "a null tag costs one byte");
		helper.assertTrue(empty.readNBT() == null, "and comes back null");

		helper.succeed();
	}

	/** Both string encodings survive a round trip, including non-ASCII. */
	@GameTest(template = EMPTY)
	public static void bothUtfEncodingsRoundTrip(GameTestHelper helper) {

		String[] cases = {"", "hello", "reactor_core_1", "éüñ", "中文テスト", "mixed é 中 ascii"};

		for(String s : cases) {
			LeafiaBuf std = buf();
			LeafiaBitByteUTF.writeUTF(std, s);
			helper.assertTrue(LeafiaBitByteUTF.readUTF(std).equals(s), "standard UTF: " + s);

			LeafiaBuf lea = buf();
			LeafiaBitByteUTF.writeUTFLeafia(lea, s);
			helper.assertTrue(LeafiaBitByteUTF.readUTFLeafia(lea).equals(s), "Leafia UTF: " + s);
		}

		// The buffer's own varint-prefixed UTF-8 path, which is a third encoding again.
		LeafiaBuf v = buf();
		v.writeUTF8String("varint prefixed é");
		helper.assertTrue(v.readUTF8String().equals("varint prefixed é"), "UTF8String");

		helper.succeed();
	}

	/**
	 * The point of upstream's own encoding: it is smaller than standard UTF for anything
	 * outside ASCII, because it tags a character with one or two bits instead of a whole
	 * leading byte.
	 */
	@GameTest(template = EMPTY)
	public static void leafiaUtfBeatsStandardUtfOffAscii(GameTestHelper helper) {

		// Pure ASCII: both spend 8 bits a character, so they tie.
		LeafiaBuf a1 = buf(), a2 = buf();
		int stdAscii = LeafiaBitByteUTF.writeUTF(a1, "abc");
		LeafiaBitByteUTF.writeUTFLeafia(a2, "abc");
		helper.assertTrue(stdAscii == 40, "standard: 16-bit length + 3 bytes, got " + stdAscii);
		helper.assertTrue(a2.writerIndex == 40, "Leafia ties on ASCII, got " + a2.writerIndex);

		// Two-byte range: standard spends 16 bits a character, Leafia 13.
		LeafiaBuf b1 = buf(), b2 = buf();
		LeafiaBitByteUTF.writeUTF(b1, "ééé");
		LeafiaBitByteUTF.writeUTFLeafia(b2, "ééé");
		helper.assertTrue(b1.writerIndex == 16 + 3 * 16, "standard, got " + b1.writerIndex);
		helper.assertTrue(b2.writerIndex == 16 + 3 * 13, "Leafia, got " + b2.writerIndex);
		helper.assertTrue(b2.writerIndex < b1.writerIndex, "so Leafia's is the smaller one");

		helper.succeed();
	}

	/** FifthString over the wire: five bits a letter, which is the whole reason it exists. */
	@GameTest(template = EMPTY)
	public static void fifthStringSurvivesTheBuffer(GameTestHelper helper) {

		for(String s : new String[] {"hello world", "Reactor_Core", "HELLO", "with é unicode"}) {
			LeafiaBuf b = buf();
			b.writeFifthString(new FifthString(s));
			helper.assertTrue(b.readFifthString().toString().equals(s), "round trip: " + s);
		}

		// A lowercase run costs 5 bits a character plus the 5-bit END code - well under the
		// 8 bits a character even the compact UTF path would spend.
		LeafiaBuf b = buf();
		b.writeFifthString(new FifthString("hello"));
		helper.assertTrue(b.writerIndex == 6 * 5,
				"five letters and an END code at 5 bits each, got " + b.writerIndex);

		helper.succeed();
	}

	/** Marks, skips and the readable-bit accounting the packet layer will drive this with. */
	@GameTest(template = EMPTY)
	public static void cursorsMarkAndReset(GameTestHelper helper) {

		LeafiaBuf b = buf();
		b.writeInt(11).writeInt(22).writeInt(33);

		helper.assertTrue(b.readableBits() == 96, "three ints are 96 bits");
		helper.assertTrue(b.isReadable(96) && !b.isReadable(97), "readability is exact");

		helper.assertTrue(b.readInt() == 11, "first");
		b.markReaderIndex();
		helper.assertTrue(b.readInt() == 22, "second");
		b.resetReaderIndex();
		helper.assertTrue(b.readInt() == 22, "reset returns to the mark");

		b.skipBits(32);
		helper.assertTrue(!b.isReadable(), "skipping the last int exhausts the buffer");

		helper.succeed();
	}
}
