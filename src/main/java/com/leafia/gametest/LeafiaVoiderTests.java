package com.leafia.gametest;

import java.util.Map;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.util.Tuple.Pair;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.debug.fluid_voider.VoiderTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid voider — and, through it, the first end-to-end exercise of the addon's own packet
 * channel.
 *
 * <p>{@code LeafiaPacket} landed with 89 files waiting on it and <b>nothing implementing
 * {@code LeafiaPacketReceiver}</b>, so D-1025 could pin its wire format against its own bytes and
 * nothing else. The gap that left is the one that matters most in a format with no version tag:
 * the sender writes slot 0 and slot 1, the receiver separately believes what those mean, and
 * <em>nothing checks that they agree</em>. {@link #theSyncPacketDrivesTheReceiverAtTheOtherEnd}
 * is that check, and it is the reason this two-file block was worth a batch.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaVoiderTests {

	private static final String EMPTY = "empty";
	private static final BlockPos WHERE = new BlockPos(2, 1, 2);
	private static final BlockPos ELSEWHERE = new BlockPos(4, 1, 2);

	private static VoiderTE place(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, LeafiaBlocks.test_fluid_voider.get().defaultBlockState());
		return (VoiderTE) helper.getBlockEntity(pos);
	}

	/**
	 * The buffer upstream gives it: spent steam, and a tank big enough that its advertised demand
	 * is never the limiting factor.
	 */
	@GameTest(template = EMPTY)
	public static void theBufferStartsOnSpentSteamAndIsBottomless(GameTestHelper helper) {

		VoiderTE voider = place(helper, WHERE);

		helper.assertTrue(voider.buffer.getTankType() == Fluids.SPENTSTEAM,
				"upstream starts it on spent steam, it starts on " + voider.buffer.getTankType().getName());
		helper.assertTrue(voider.buffer.getMaxFill() == Integer.MAX_VALUE,
				"the tank is Integer.MAX_VALUE mB, which is how 'eats anything' is spelled - it is "
						+ voider.buffer.getMaxFill());

		// The mechanism, not the number: demand is maxFill - fill, so an empty bottomless tank
		// asks for everything any pipe could offer.
		helper.assertTrue(voider.getDemand(Fluids.SPENTSTEAM, 0) == Integer.MAX_VALUE,
				"an empty voider should demand its whole tank, demands " + voider.getDemand(Fluids.SPENTSTEAM, 0));

		helper.assertTrue(voider.getReceivingTanks()[0] == voider.buffer
						&& voider.getAllTanks()[0] == voider.buffer,
				"both tank lists are the one buffer");

		helper.succeed();
	}

	/** What goes in is gone by the end of the next tick, which is the whole of the block. */
	@GameTest(template = EMPTY)
	public static void whateverGoesInIsGoneNextTick(GameTestHelper helper) {

		VoiderTE voider = place(helper, WHERE);

		long left = voider.transferFluid(Fluids.SPENTSTEAM, 0, 24_000L);
		helper.assertTrue(left == 0, "the voider should swallow all 24000mB, refused " + left);
		helper.assertTrue(voider.buffer.getFill() == 24_000,
				"it should be holding 24000mB until it ticks, holds " + voider.buffer.getFill());

		voider.updateEntity();

		helper.assertTrue(voider.buffer.getFill() == 0,
				"and nothing after, holds " + voider.buffer.getFill());

		helper.succeed();
	}

	/**
	 * It takes only the type it is set to. Worth a test because the tank is bottomless: a voider
	 * that accepted anything would silently eat a neighbour's water main.
	 */
	@GameTest(template = EMPTY)
	public static void itOnlyTakesTheTypeItIsSetTo(GameTestHelper helper) {

		VoiderTE voider = place(helper, WHERE);

		helper.assertTrue(voider.getDemand(Fluids.WATER, 0) == 0,
				"a spent-steam voider must ask for no water at all");

		long left = voider.transferFluid(Fluids.WATER, 0, 1_000L);
		helper.assertTrue(left == 1_000L, "and must refuse all 1000mB of it, refused " + left);
		helper.assertTrue(voider.buffer.getFill() == 0,
				"leaving the buffer alone, which now holds " + voider.buffer.getFill());

		helper.succeed();
	}

	/**
	 * The claim this batch exists for: the packet the voider really sends, decoded, and handed to
	 * a real receiver — which then holds what the sender held.
	 *
	 * <p>The two ends are never compared anywhere else. The sender's slot numbers live in
	 * {@code syncPacket}, the receiver's in {@code onReceivePacketLocal}, and the format carries
	 * neither names nor a version, so swapping the two writes would produce a packet that encodes,
	 * decodes and dispatches perfectly while setting the fluid type from an integer. That is why
	 * this drives the block entity's own builder rather than re-typing the two writes here: a
	 * replica would agree with itself.
	 */
	@GameTest(template = EMPTY)
	public static void theSyncPacketDrivesTheReceiverAtTheOtherEnd(GameTestHelper helper) {

		VoiderTE sender = place(helper, WHERE);
		VoiderTE receiver = place(helper, ELSEWHERE);

		sender.buffer.setTankType(Fluids.WATER);
		sender.buffer.setFill(1_234);

		LeafiaPacket packet = sender.syncPacket().build();
		helper.assertTrue(packet != null, "the voider should have a packet to send");

		helper.assertTrue(packet.identifier() == "DEBUG_VOIDER".hashCode(),
				"it should be addressed to upstream's DEBUG_VOIDER identifier");

		Map<Byte, Pair<Byte, Object>> read = packet.decode(helper.getLevel().registryAccess());
		helper.assertTrue(read.size() == 2, "two slots go out, " + read.size() + " came back");

		for(Map.Entry<Byte, Pair<Byte, Object>> entry : read.entrySet()) {
			receiver.onReceivePacketLocal(LeafiaPacket.key(entry.getKey()), entry.getValue().getValue());
		}

		helper.assertTrue(receiver.buffer.getTankType() == Fluids.WATER,
				"the receiver should be on water, it is on " + receiver.buffer.getTankType().getName()
						+ " - the sender's slot numbers and the receiver's have drifted apart");
		helper.assertTrue(receiver.buffer.getFill() == 1_234,
				"and holding 1234mB, it holds " + receiver.buffer.getFill());

		helper.succeed();
	}

	/**
	 * The fill on the wire is what was swallowed, sent <em>before</em> the buffer is emptied.
	 *
	 * <p>Upstream's ordering, and it is the difference between a look overlay that reports the
	 * throughput and one that permanently reads zero.
	 */
	@GameTest(template = EMPTY)
	public static void theTickSendsWhatItAteBeforeThrowingItAway(GameTestHelper helper) {

		VoiderTE voider = place(helper, WHERE);
		voider.buffer.setFill(7_000);

		LeafiaPacket before = voider.syncPacket().build();
		voider.updateEntity();
		LeafiaPacket after = voider.syncPacket().build();

		helper.assertTrue(fillIn(helper, before) == 7_000,
				"the packet sent during that tick should carry the 7000mB it ate");
		helper.assertTrue(fillIn(helper, after) == 0,
				"and the next one zero, because the tick emptied the buffer afterwards");

		helper.succeed();
	}

	/** The buffer is saved, so a voider does not forget what it was set to across a reload. */
	@GameTest(template = EMPTY)
	public static void theBufferSurvivesASaveAndLoad(GameTestHelper helper) {

		VoiderTE voider = place(helper, WHERE);
		voider.buffer.setTankType(Fluids.WATER);
		voider.buffer.setFill(500);

		CompoundTag nbt = voider.saveWithoutMetadata(helper.getLevel().registryAccess());

		VoiderTE reloaded = place(helper, ELSEWHERE);
		reloaded.loadWithComponents(nbt, helper.getLevel().registryAccess());

		helper.assertTrue(reloaded.buffer.getTankType() == Fluids.WATER,
				"the saved type should come back, came back as " + reloaded.buffer.getTankType().getName());
		helper.assertTrue(reloaded.buffer.getFill() == 500,
				"and the saved fill, came back as " + reloaded.buffer.getFill());

		helper.succeed();
	}

	/** Slot 1 out of a decoded voider packet. */
	private static int fillIn(GameTestHelper helper, LeafiaPacket packet) {

		helper.assertTrue(packet != null, "the voider always has both slots to send");

		for(Map.Entry<Byte, Pair<Byte, Object>> entry : packet.decode(helper.getLevel().registryAccess()).entrySet()) {
			if(LeafiaPacket.key(entry.getKey()) == 1) return (int) entry.getValue().getValue();
		}

		throw new AssertionError("the voider's packet is missing slot 1, the fill");
	}
}
