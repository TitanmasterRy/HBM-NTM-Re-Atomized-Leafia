package com.leafia.gametest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.hbm.uninos.UniNodespace;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.network.BlockCable;
import com.leafia.contents.network.spk_cable.SPKCableTE;
import com.leafia.contents.network.spk_cable.uninos.ISPKConnector;
import com.leafia.contents.network.spk_cable.uninos.ISPKProvider;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;
import com.leafia.contents.network.spk_cable.uninos.SPKNet;
import com.leafia.contents.network.spk_cable.uninos.SPKNode;
import com.leafia.gametest.LeafiaGameTestFixtures.SpkProvider;
import com.leafia.gametest.LeafiaGameTestFixtures.SpkReceiver;
import com.leafia.lib.LeafiaRef;
import com.llib.exceptions.LeafiaDevFlaw;

import api.hbm.energymk2.Nodespace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The SPK cable and its network (D-3008).
 *
 * <p>Nothing in the addon provides or receives SPK yet - the Dark Fusion Core's parts that will are
 * a later batch - so the network is exercised through {@link LeafiaGameTestFixtures}' provider and
 * receiver, which implement the same interfaces those parts will.
 *
 * <p>What is worth saying about how the numbers are asserted: SPK's transfer is a handful of
 * integer divisions whose rounding goes somewhere, and **where it goes is the thing to pin**. The
 * share each receiver gets is rounded down and the remainder stays in the provider (it is charged
 * for what was accepted, not what was offered), so a split of 5 between two receivers wanting 3
 * each gives 2 and 2 and leaves 1 behind - not 3 and 2, and not 4 lost. Each of those variants is a
 * one-character edit that looks like tidying.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSpkCableTests {

	private static final String EMPTY = "empty";

	/** Nodes get their net on the server tick after they are created, and a block entity needs a tick to make its node. */
	private static final int SETTLE = 6;

	/// helpers ///

	private static boolean off() {
		return !LeafiaFeatures.spkCable;
	}

	private static SpkProvider provider(GameTestHelper helper, BlockPos pos) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the SPK fixtures are registered only when leafia.gametest.fixtures is set");
		helper.setBlock(pos, LeafiaGameTestFixtures.spkProvider);
		return (SpkProvider) helper.getBlockEntity(pos);
	}

	private static SpkReceiver receiver(GameTestHelper helper, BlockPos pos) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the SPK fixtures are registered only when leafia.gametest.fixtures is set");
		helper.setBlock(pos, LeafiaGameTestFixtures.spkReceiver);
		return (SpkReceiver) helper.getBlockEntity(pos);
	}

	private static void cable(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, LeafiaBlocks.spk_cable.get());
	}

	private static SPKNode nodeAt(GameTestHelper helper, BlockPos pos) {
		return (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(pos), SPKNet.THE_NETWORK_PROVIDER);
	}

	private static SPKNet netAt(GameTestHelper helper, BlockPos pos) {
		SPKNode node = nodeAt(helper, pos);
		return node == null ? null : node.net;
	}

	/** An anonymous receiver that is not a block entity, for the arithmetic that needs no world. */
	private static final class Buffer implements ISPKReceiver {
		long spk;
		final long max;

		Buffer(long spk, long max) {
			this.spk = spk;
			this.max = max;
		}

		@Override public long getSPK() { return spk; }
		@Override public void setSPK(long power) { this.spk = power; }
		@Override public long getMaxSPK() { return max; }
	}

	/// the interfaces' arithmetic ///

	/** A receiver takes what fits, reports the overshoot, and a simulated transfer changes nothing. */
	@GameTest(template = EMPTY)
	public static void aReceiverTakesWhatFitsAndReportsTheOvershoot(GameTestHelper helper) {

		Buffer b = new Buffer(0, 100);

		helper.assertTrue(b.transferSPK(60, false) == 0 && b.spk == 60, "60 into an empty 100 all fits, got spk " + b.spk);
		long over = b.transferSPK(60, false);
		helper.assertTrue(over == 20, "60 more into 60/100 overshoots by 20, got " + over);
		helper.assertTrue(b.spk == 100, "and fills the buffer exactly, got " + b.spk);

		Buffer s = new Buffer(40, 100);
		helper.assertTrue(s.transferSPK(50, true) == 0, "a simulated 50 into 40/100 fits");
		helper.assertTrue(s.transferSPK(80, true) == 20, "a simulated 80 into 40/100 overshoots by 20");
		helper.assertTrue(s.spk == 40, "and a simulation never changes the buffer, got " + s.spk);

		helper.succeed();
	}

	/** The interface defaults, which every implementor inherits unless it says otherwise. */
	@GameTest(template = EMPTY)
	public static void theInterfaceDefaultsAreUpstreams(GameTestHelper helper) {

		ISPKConnector plain = new ISPKConnector() { };
		helper.assertTrue(!plain.canConnectSPK(null), "no side (upstream's UNKNOWN) never connects");
		for(Direction dir : Direction.values())
			helper.assertTrue(plain.canConnectSPK(dir), "every real side connects by default: " + dir);

		Buffer b = new Buffer(30, 100);
		helper.assertTrue(b.isInputPreferrable(Direction.UP), "a receiver prefers input on every side by default");
		helper.assertTrue(b.getSPKReceiverSpeed() == 100, "and takes the whole buffer at once by default, got " + b.getSPKReceiverSpeed());

		final long[] spk = { 30 };
		ISPKProvider p = new ISPKProvider() {
			@Override public long getSPK() { return spk[0]; }
			@Override public void setSPK(long power) { spk[0] = power; }
			@Override public long getMaxSPK() { return 100; }
			@Override public long getSendable() { return spk[0]; }
		};
		helper.assertTrue(p.getSPKProviderSpeed() == 100, "a provider sends its whole buffer at once by default");
		p.setTransferredSpk(12);
		helper.assertTrue(spk[0] == 18, "being charged for a transfer subtracts it, got " + spk[0]);

		helper.succeed();
	}

	/// the cable and its node ///

	/** A cable's node is on the SPK network and nowhere else, and gets a net. */
	@GameTest(template = EMPTY)
	public static void aPlacedCableMakesItsNodeOnTheSpkNetworkOnly(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		cable(helper, pos);

		helper.succeedWhen(() -> {

			helper.assertTrue(helper.getBlockEntity(pos) instanceof SPKCableTE, "the block entity is an SPKCableTE");
			helper.assertTrue(netAt(helper, pos) != null, "the cable's node should have joined a net");
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), helper.absolutePos(pos), Nodespace.THE_POWER_PROVIDER) == null,
					"the cable must NOT be on the power network");
		});
	}

	/** Breaking a cable takes its node with it; a leaked node would fail silently and forever. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void breakingTheCableDestroysItsNode(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		cable(helper, pos);

		TickWindow.runAfterDelay(helper, SETTLE, () -> {

			helper.assertTrue(nodeAt(helper, pos) != null, "precondition: the node exists before the block is broken");

			helper.setBlock(pos, Blocks.AIR);

			helper.runAfterDelay(3, () -> {
				helper.assertTrue(nodeAt(helper, pos) == null, "the node should be gone with the block");
				helper.succeed();
			});
		});
	}

	/** A cable joins a neighbour on every one of the six sides - the node declares six connections. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aCableJoinsANeighbourOnEachOfTheSixSides(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos centre = new BlockPos(2, 2, 2);
		cable(helper, centre);
		for(Direction dir : Direction.values()) cable(helper, centre.relative(dir));

		TickWindow.runAfterDelay(helper, SETTLE + 4, () -> {

			SPKNet net = netAt(helper, centre);
			helper.assertTrue(net != null, "the centre cable should be on a net");

			for(Direction dir : Direction.values()) {
				helper.assertTrue(netAt(helper, centre.relative(dir)) == net,
						"the cable " + dir + " of the centre must share its net");
			}
			helper.assertTrue(net.links.size() == 7, "seven cables, one net: got " + net.links.size() + " links");
			helper.succeed();
		});
	}

	/** Adjacent cables share a net; a gap keeps two runs apart; closing the gap joins them. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void adjacentCablesShareANetAndAGapKeepsThemApart(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		cable(helper, new BlockPos(0, 1, 1));
		cable(helper, new BlockPos(1, 1, 1));
		cable(helper, new BlockPos(3, 1, 1));
		cable(helper, new BlockPos(4, 1, 1));

		TickWindow.runAfterDelay(helper, SETTLE, () -> {

			SPKNet left = netAt(helper, new BlockPos(0, 1, 1));
			SPKNet right = netAt(helper, new BlockPos(3, 1, 1));

			helper.assertTrue(left != null && right != null, "both runs should be on nets");
			helper.assertTrue(netAt(helper, new BlockPos(1, 1, 1)) == left, "the two cables of the left run share a net");
			helper.assertTrue(netAt(helper, new BlockPos(4, 1, 1)) == right, "and so do the right run's");
			helper.assertTrue(left != right, "a one-block gap keeps the runs on separate nets");

			cable(helper, new BlockPos(2, 1, 1));

			helper.runAfterDelay(SETTLE, () -> {
				SPKNet joined = netAt(helper, new BlockPos(2, 1, 1));
				for(int x = 0; x <= 4; x++)
					helper.assertTrue(netAt(helper, new BlockPos(x, 1, 1)) == joined, "closing the gap puts x=" + x + " on one net");
				helper.assertTrue(joined.links.size() == 5, "five cables, one net: got " + joined.links.size());
				helper.succeed();
			});
		});
	}

	/** A power cable beside an SPK cable is not joined, not drawn at, and not on its network. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aPowerCableBesideItIsNotJoined(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos spk = new BlockPos(2, 1, 1);
		BlockPos power = new BlockPos(3, 1, 1);
		helper.setBlock(power, ModBlocks.red_cable.get());
		cable(helper, spk);

		TickWindow.runAfterDelay(helper, SETTLE, () -> {

			helper.assertTrue(!helper.getBlockState(spk).getValue(BlockCable.EAST), "the SPK cable draws no arm at a power cable");
			helper.assertTrue(!helper.getBlockState(power).getValue(com.hbm.blocks.network.BlockCable.WEST),
					"and the power cable draws none at it");
			helper.assertTrue(nodeAt(helper, power) == null, "the power cable has no SPK node");
			helper.assertTrue(Nodespace.getNode(helper.getLevel(), helper.absolutePos(spk)) == null,
					"and the SPK cable has no power node");
			helper.assertTrue(netAt(helper, spk).links.size() == 1, "the SPK net is the SPK cable alone");
			helper.succeed();
		});
	}

	/** Arms follow the neighbours that are SPK connectors - cables, providers and receivers - and nothing else. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void armsFollowTheSpkConnectorsAroundIt(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 2, 2);
		provider(helper, mid.west());
		receiver(helper, mid.east());
		cable(helper, mid.north());
		helper.setBlock(mid.south(), Blocks.STONE);
		cable(helper, mid);

		TickWindow.runAfterDelay(helper, 4, () -> {

			BlockState state = helper.getBlockState(mid);
			helper.assertTrue(state.getValue(BlockCable.WEST), "an arm at the provider");
			helper.assertTrue(state.getValue(BlockCable.EAST), "an arm at the receiver");
			helper.assertTrue(state.getValue(BlockCable.NORTH), "an arm at the cable");
			helper.assertTrue(!state.getValue(BlockCable.SOUTH), "none at stone");
			helper.assertTrue(!state.getValue(BlockCable.UP) && !state.getValue(BlockCable.DOWN), "none at air");

			helper.setBlock(mid.east(), Blocks.AIR);
			helper.setBlock(mid.south(), LeafiaGameTestFixtures.spkReceiver);

			helper.runAfterDelay(4, () -> {
				BlockState after = helper.getBlockState(mid);
				helper.assertTrue(!after.getValue(BlockCable.EAST), "removing the receiver takes its arm");
				helper.assertTrue(after.getValue(BlockCable.SOUTH), "and a receiver placed where the stone was gives one");
				helper.succeed();
			});
		});
	}

	/** One box: the core, stretched to the block's edge on each connected side (upstream's getBB). */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void theShapeIsOneBoxStretchedToEachConnectedSide(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos lone = new BlockPos(0, 1, 0);
		BlockPos linked = new BlockPos(3, 1, 3);
		cable(helper, lone);
		cable(helper, linked);
		cable(helper, linked.east());
		cable(helper, linked.above());

		TickWindow.runAfterDelay(helper, 4, () -> {

			double lo = 5.5 / 16, hi = 10.5 / 16;

			AABB a = helper.getBlockState(lone).getShape(helper.getLevel(), helper.absolutePos(lone)).bounds();
			helper.assertTrue(near(a.minX, lo) && near(a.minY, lo) && near(a.minZ, lo) && near(a.maxX, hi) && near(a.maxY, hi) && near(a.maxZ, hi),
					"a lone cable is the 5/16 core, got " + a);

			AABB b = helper.getBlockState(linked).getShape(helper.getLevel(), helper.absolutePos(linked)).bounds();
			helper.assertTrue(near(b.minX, lo) && near(b.maxX, 1.0), "connected east: x runs from the core to the edge, got " + b);
			helper.assertTrue(near(b.minY, lo) && near(b.maxY, 1.0), "connected up: y runs from the core to the edge, got " + b);
			helper.assertTrue(near(b.minZ, lo) && near(b.maxZ, hi), "and z stays the core, got " + b);
			helper.succeed();
		});
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	/// the transfer ///

	/**
	 * One provider, one cable, three receivers wanting 300, 100 and nothing: the provider has 200,
	 * so it sends 200 split 3:1 - 150 and 50 - and is charged all 200.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void theSplitIsProportionalToDemandAndCappedByTheProvider(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		SpkReceiver b = receiver(helper, mid.south());
		SpkReceiver full = receiver(helper, mid.north());
		cable(helper, mid);

		p.spk = 200;
		a.max = 300;
		b.max = 100;
		full.max = 50;
		full.spk = 50;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			ServerLevel level = helper.getLevel();

			a.trySubscribeSPK(level, abs, Direction.WEST);
			b.trySubscribeSPK(level, abs, Direction.NORTH);
			full.trySubscribeSPK(level, abs, Direction.SOUTH);
			helper.assertTrue(netAt(helper, mid).receiverEntries.size() == 3, "all three subscribed");

			p.tryProvideSPK(level, abs, Direction.EAST, false);

			helper.assertTrue(a.spk == 150, "the 300 receiver gets 3/4 of 200 = 150, got " + a.spk);
			helper.assertTrue(b.spk == 50, "the 100 receiver gets 1/4 of 200 = 50, got " + b.spk);
			helper.assertTrue(full.spk == 50, "the full receiver wanted nothing and got nothing, got " + full.spk);
			helper.assertTrue(p.spk == 0, "the provider is charged the 200 that was accepted, has " + p.spk);
			helper.succeed();
		});
	}

	/** Demand smaller than supply: everyone is filled and the provider keeps the rest. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void demandBelowSupplyFillsEveryoneAndTheProviderKeepsTheRest(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		SpkReceiver b = receiver(helper, mid.south());
		cable(helper, mid);

		p.spk = 1000;
		a.max = 300;
		b.max = 100;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			a.trySubscribeSPK(helper.getLevel(), abs, Direction.WEST);
			b.trySubscribeSPK(helper.getLevel(), abs, Direction.NORTH);

			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);

			helper.assertTrue(a.spk == 300 && b.spk == 100, "both filled, got " + a.spk + " and " + b.spk);
			helper.assertTrue(p.spk == 600, "the provider keeps the other 600, has " + p.spk);
			helper.succeed();
		});
	}

	/** The rounding stays in the provider: 5 between two receivers wanting 3 each is 2 + 2, and 1 is not spent. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void roundingStaysInTheProvider(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		SpkReceiver b = receiver(helper, mid.south());
		cable(helper, mid);

		p.spk = 5;
		a.max = 3;
		b.max = 3;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			a.trySubscribeSPK(helper.getLevel(), abs, Direction.WEST);
			b.trySubscribeSPK(helper.getLevel(), abs, Direction.NORTH);

			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);

			helper.assertTrue(a.spk == 2 && b.spk == 2, "floor(5 x 3/6) = 2 each, got " + a.spk + " and " + b.spk);
			helper.assertTrue(p.spk == 1, "the 1 that did not divide stays in the provider, has " + p.spk);
			helper.succeed();
		});
	}

	/** The receiver's intake speed and the provider's send speed each cap a transfer. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void speedsCapATransfer(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		cable(helper, mid);

		p.spk = 1000;
		a.max = 1000;
		a.speed = 50;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			a.trySubscribeSPK(helper.getLevel(), abs, Direction.WEST);

			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);
			helper.assertTrue(a.spk == 50 && p.spk == 950, "a receiver speed of 50 caps the transfer at 50, got " + a.spk + " / " + p.spk);

			a.speed = -1;
			p.speed = 20;
			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);
			helper.assertTrue(a.spk == 70 && p.spk == 930, "a provider speed of 20 caps the next at 20, got " + a.spk + " / " + p.spk);
			helper.succeed();
		});
	}

	/** Nothing to send, or nobody wanting it, moves nothing. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void anEmptyProviderOrAFullReceiverMovesNothing(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		SpkReceiver b = receiver(helper, mid.south());
		cable(helper, mid);

		a.max = 100;
		b.max = 100;
		b.spk = 100;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			a.trySubscribeSPK(helper.getLevel(), abs, Direction.WEST);
			b.trySubscribeSPK(helper.getLevel(), abs, Direction.NORTH);

			p.spk = 0;
			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);
			helper.assertTrue(a.spk == 0 && b.spk == 100 && p.spk == 0, "an empty provider sends nothing");

			p.spk = 500;
			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);
			helper.assertTrue(a.spk == 100 && b.spk == 100, "only the one with room is filled, got " + a.spk + " and " + b.spk);
			helper.assertTrue(p.spk == 400, "and the provider is charged 100, has " + p.spk);

			p.tryProvideSPK(helper.getLevel(), abs, Direction.EAST, false);
			helper.assertTrue(p.spk == 400, "with everyone full, nothing more is spent");
			helper.succeed();
		});
	}

	/** A receiver that never subscribed gets nothing, however close it sits to the cable. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aReceiverThatNeverSubscribedGetsNothing(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver a = receiver(helper, mid.east());
		cable(helper, mid);
		p.spk = 500;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			p.tryProvideSPK(helper.getLevel(), helper.absolutePos(mid), Direction.EAST, false);
			helper.assertTrue(a.spk == 0 && p.spk == 500, "no subscription, no share");
			helper.succeed();
		});
	}

	/**
	 * A subscription lapses after three seconds without a refresh, and a removed receiver is dropped.
	 * The old timestamp is written into the entry rather than waited for.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void staleAndRemovedSubscribersAreDropped(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		SpkReceiver stale = receiver(helper, mid.east());
		SpkReceiver doomed = receiver(helper, mid.south());
		SpkReceiver live = receiver(helper, mid.north());
		cable(helper, mid);
		p.spk = 1000;
		p.max = 1000;
		stale.max = 100;
		doomed.max = 100;
		live.max = 100;

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			BlockPos abs = helper.absolutePos(mid);
			ServerLevel level = helper.getLevel();
			SPKNet net = netAt(helper, mid);

			stale.trySubscribeSPK(level, abs, Direction.WEST);
			doomed.trySubscribeSPK(level, abs, Direction.NORTH);
			live.trySubscribeSPK(level, abs, Direction.SOUTH);
			net.receiverEntries.put(stale, System.currentTimeMillis() - 10_000L);

			helper.setBlock(mid.south(), Blocks.AIR);

			p.tryProvideSPK(level, abs, Direction.EAST, false);

			helper.assertTrue(stale.spk == 0, "a subscription ten seconds old is stale and gets nothing, got " + stale.spk);
			helper.assertTrue(!net.isSubscribed(stale), "and is dropped from the net");
			helper.assertTrue(!net.isSubscribed(doomed), "a receiver whose block entity was removed is dropped too");
			helper.assertTrue(live.spk == 100, "the live one is filled, got " + live.spk);
			helper.assertTrue(p.spk == 900, "the provider is charged only for it, has " + p.spk);
			helper.succeed();
		});
	}

	/** A provider touching a receiver directly feeds it one-to-one, if the receiver accepts that side. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aProviderFeedsAnAdjacentReceiverDirectlyIfItAcceptsTheSide(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, at);
		SpkReceiver r = receiver(helper, at.east());
		p.spk = 500;
		r.max = 300;

		TickWindow.runAfterDelay(helper, 2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos target = helper.absolutePos(at.east());

			// It sits at the receiver's WEST face: the receiver is asked about WEST.
			r.prefers.remove(Direction.WEST);
			p.tryProvideSPK(level, target, Direction.EAST, false);
			helper.assertTrue(r.spk == 0, "a receiver that does not prefer input on that side gets nothing");

			r.prefers.add(Direction.WEST);
			r.accepts.remove(Direction.WEST);
			p.tryProvideSPK(level, target, Direction.EAST, false);
			helper.assertTrue(r.spk == 0, "nor does one that refuses the connection");

			r.accepts.add(Direction.WEST);
			p.tryProvideSPK(level, target, Direction.EAST, false);
			helper.assertTrue(r.spk == 300 && p.spk == 200, "otherwise it takes min(sendable, room) = 300, got " + r.spk + " / " + p.spk);

			r.spk = 0;
			r.speed = 40;
			p.tryProvideSPK(level, target, Direction.EAST, false);
			helper.assertTrue(r.spk == 40 && p.spk == 160, "and the receiver's speed caps it, got " + r.spk + " / " + p.spk);
			helper.succeed();
		});
	}

	/** The targeted overload: simulate answers without moving anything, and a real call charges the provider what was accepted. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void targetedProvisionCanBeSimulated(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, at);
		SpkReceiver r = receiver(helper, at.east());
		p.spk = 500;
		r.max = 120;

		TickWindow.runAfterDelay(helper, 2, () -> {
			long said = p.tryProvideSPK(r, Direction.EAST, 200, true);
			helper.assertTrue(said == 120, "a simulation says the room, 120, got " + said);
			helper.assertTrue(r.spk == 0 && p.spk == 500, "and moves nothing");

			long gave = p.tryProvideSPK(r, Direction.EAST, 80, false);
			helper.assertTrue(gave == 80 && r.spk == 80 && p.spk == 420, "a real 80 moves 80 and charges 80, got " + gave + " / " + r.spk + " / " + p.spk);

			gave = p.tryProvideSPK(r, Direction.EAST, 500, false);
			helper.assertTrue(gave == 40 && r.spk == 120 && p.spk == 380, "the rest is clipped to the room, got " + gave + " / " + r.spk + " / " + p.spk);

			helper.assertTrue(p.tryProvideSPK(null, Direction.EAST, 10, false) == 0, "no target, no transfer");
			helper.assertTrue(p.tryProvideSPK(p, Direction.EAST, 10, false) == 0, "a provider is never its own target");
			helper.succeed();
		});
	}

	/**
	 * End to end, with the real tick loop: a provider pushing every tick, three cables, a receiver
	 * subscribing every tick. It fills the receiver, conserves the total, and fills it again after it
	 * is emptied.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 300)
	public static void energyFlowsFromAProviderThroughCablesToAReceiver(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		SpkProvider p = provider(helper, new BlockPos(0, 1, 1));
		for(int x = 1; x <= 3; x++) cable(helper, new BlockPos(x, 1, 1));
		SpkReceiver r = receiver(helper, new BlockPos(4, 1, 1));

		p.spk = 1000;
		p.max = 1000;
		p.auto = true;
		p.sides.clear();
		p.sides.add(Direction.EAST);
		r.max = 400;
		r.auto = true;

		helper.succeedWhen(() -> {
			helper.assertTrue(r.spk == 400, "the receiver should fill to its 400, has " + r.spk);
			helper.assertTrue(p.spk == 600, "and the provider should have been charged 400, has " + p.spk);
			helper.assertTrue(p.spk + r.spk == 1000, "nothing is lost on the way");
		});
	}

	@GameTest(template = EMPTY, timeoutTicks = 300)
	public static void anEmptiedReceiverIsRefilledAndNothingIsLost(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		SpkProvider p = provider(helper, new BlockPos(0, 1, 1));
		for(int x = 1; x <= 3; x++) cable(helper, new BlockPos(x, 1, 1));
		SpkReceiver r = receiver(helper, new BlockPos(4, 1, 1));

		p.spk = 1000;
		p.max = 1000;
		p.auto = true;
		p.sides.clear();
		p.sides.add(Direction.EAST);
		r.max = 400;
		r.auto = true;

		TickWindow.runAfterDelay(helper, 40, () -> {
			helper.assertTrue(r.spk == 400 && p.spk == 600, "precondition: the first fill, got " + r.spk + " / " + p.spk);

			r.spk = 0; // the receiver spends it
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(r.spk == 400, "refilled to 400, has " + r.spk);
				helper.assertTrue(p.spk == 200, "the provider has paid 400 more, has " + p.spk);

				r.spk = 0;
				helper.runAfterDelay(20, () -> {
					helper.assertTrue(r.spk == 200, "the last 200 is all there is, has " + r.spk);
					helper.assertTrue(p.spk == 0, "and the provider is dry, has " + p.spk);
					helper.succeed();
				});
			});
		});
	}

	/// the deprecated net update ///

	/**
	 * Upstream's SPKNet.update throws once a net has both a provider and a receiver, and the
	 * provider-driven transfer exists so that it never gets that far. Kept, so a revival of
	 * net-driven transfer is a decision and not an accident. A standalone net, destroyed before the
	 * tick ends, because a live one with both would throw in the nodespace's own update.
	 */
	@GameTest(template = EMPTY)
	public static void theDeprecatedNetUpdateThrowsOnlyWithBothSidesPresent(GameTestHelper helper) {

		SPKNet net = new SPKNet();
		try {
			net.update(); // both empty: returns
			net.addReceiver(new Buffer(0, 10));
			net.update(); // a receiver alone: returns

			SPKNet providersOnly = new SPKNet();
			try {
				providersOnly.addProvider(new ISPKProvider() {
					@Override public long getSPK() { return 0; }
					@Override public void setSPK(long power) { }
					@Override public long getMaxSPK() { return 0; }
					@Override public long getSendable() { return 0; }
				});
				providersOnly.update(); // a provider alone: returns
			} finally {
				providersOnly.destroy();
			}

			net.addProvider(new ISPKProvider() {
				@Override public long getSPK() { return 0; }
				@Override public void setSPK(long power) { }
				@Override public long getMaxSPK() { return 0; }
				@Override public long getSendable() { return 0; }
			});

			boolean threw = false;
			try {
				net.update();
			} catch(LeafiaDevFlaw e) {
				threw = true;
			}
			helper.assertTrue(threw, "update() with a provider and a receiver must throw LeafiaDevFlaw, as upstream's does");
		} finally {
			net.destroy();
		}
		helper.succeed();
	}

	/** tryLinkSPK registers the provider with the cable's net - which is why a provider must not call it. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void tryLinkSpkRegistersTheProviderOnTheCablesNet(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockPos mid = new BlockPos(2, 1, 2);
		SpkProvider p = provider(helper, mid.west());
		cable(helper, mid);

		TickWindow.runAfterDelay(helper, SETTLE, () -> {
			SPKNet net = netAt(helper, mid);
			BlockPos abs = helper.absolutePos(mid);
			p.tryLinkSPK(helper.getLevel(), abs, helper.getBlockEntity(mid), Direction.EAST);
			try {
				helper.assertTrue(net.isProvider(p), "linked: the provider is now a provider entry of the net");
			} finally {
				net.removeProvider(p); // no receiver here, but leave the net as found
			}
			helper.succeed();
		});
	}

	/// the client's spark links ///

	/** Exactly three of the 64 combinations are straight pieces. */
	@GameTest(template = EMPTY)
	public static void onlyThePairsOfOppositeSidesAreStraight(GameTestHelper helper) {

		int straight = 0;
		for(int bits = 0; bits < 64; bits++) {
			boolean pX = (bits & 1) != 0, nX = (bits & 2) != 0, pY = (bits & 4) != 0, nY = (bits & 8) != 0, pZ = (bits & 16) != 0, nZ = (bits & 32) != 0;
			boolean expected = Integer.bitCount(bits) == 2 && ((pX && nX) || (pY && nY) || (pZ && nZ));
			boolean got = SPKCableTE.isStraight(pX, pY, pZ, nX, nY, nZ);
			helper.assertTrue(got == expected, "bits " + bits + ": straight should be " + expected + ", was " + got);
			if(got) straight++;
		}
		helper.assertTrue(straight == 3, "three straight pieces, got " + straight);
		helper.succeed();
	}

	/**
	 * Sparks run between corners: the two ends of a straight run link to each other and exactly one
	 * of them draws the arc; the straight pieces between are not corners at all.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void cornersLinkAcrossAStraightRunAndOneEndDrawsIt(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		for(int x = 0; x <= 4; x++) cable(helper, new BlockPos(x, 1, 1));

		TickWindow.runAfterDelay(helper, 4, () -> {

			SPKCableTE west = (SPKCableTE) helper.getBlockEntity(new BlockPos(0, 1, 1));
			SPKCableTE east = (SPKCableTE) helper.getBlockEntity(new BlockPos(4, 1, 1));

			for(int x = 0; x <= 4; x++) {
				SPKCableTE te = (SPKCableTE) helper.getBlockEntity(new BlockPos(x, 1, 1));
				te.refreshShape();
				helper.assertTrue(te.isCorner == (x == 0 || x == 4), "x=" + x + ": only the two ends are corners, isCorner=" + te.isCorner);
			}

			west.updateLinks();
			east.updateLinks();

			var toEast = west.links[Direction.EAST.ordinal()];
			var toWest = east.links[Direction.WEST.ordinal()];

			helper.assertTrue(helper.absolutePos(new BlockPos(4, 1, 1)).equals(toEast.link), "the west end links to the east end");
			helper.assertTrue(helper.absolutePos(new BlockPos(0, 1, 1)).equals(toWest.link), "and the east end to the west end");
			helper.assertTrue(!toEast.nonCable && !toWest.nonCable, "both are cable links");
			helper.assertTrue(toEast.emit ^ toWest.emit, "exactly one end draws the arc, got " + toEast.emit + " / " + toWest.emit);

			// One end loses its link (a client that missed a tick) while the other still points at it:
			// it must find the other again WITHOUT taking over the drawing, or the arc is drawn twice
			// and, worse, the end that was drawing it is told to stop.
			SPKCableTE drawer = toEast.emit ? west : east;
			SPKCableTE quiet = toEast.emit ? east : west;
			Direction towardDrawer = toEast.emit ? Direction.WEST : Direction.EAST;
			Direction towardQuiet = towardDrawer.getOpposite();

			quiet.links[towardDrawer.ordinal()].reset();
			quiet.updateLinks();

			helper.assertTrue(drawer.getBlockPos().equals(quiet.links[towardDrawer.ordinal()].link), "the quiet end finds the other again");
			helper.assertTrue(!quiet.links[towardDrawer.ordinal()].emit, "and does not start drawing it");
			helper.assertTrue(drawer.links[towardQuiet.ordinal()].emit, "while the drawing end keeps drawing it");
			helper.succeed();
		});
	}

	/** A run that ends at a receiver arcs all the way to it, and the link goes when the receiver does. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aRunEndingAtAReceiverArcsToItAndLetsGo(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		for(int x = 0; x <= 2; x++) cable(helper, new BlockPos(x, 1, 1));
		receiver(helper, new BlockPos(3, 1, 1));

		TickWindow.runAfterDelay(helper, 4, () -> {

			SPKCableTE start = (SPKCableTE) helper.getBlockEntity(new BlockPos(0, 1, 1));
			start.refreshShape();
			// x=0 touches only x=1 (nothing is west of it): it is a corner.
			helper.assertTrue(start.isCorner, "the cable at the end of the run is a corner");
			start.updateLinks();

			var link = start.links[Direction.EAST.ordinal()];
			helper.assertTrue(helper.absolutePos(new BlockPos(3, 1, 1)).equals(link.link), "the arc runs through the straight pieces to the receiver");
			helper.assertTrue(link.nonCable && link.emit, "a link to a receiver is always drawn, and by this end");

			helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);

			helper.runAfterDelay(4, () -> {
				start.updateLinks();
				helper.assertTrue(link.link == null || !link.nonCable, "with the receiver gone it is no longer linked to a receiver, got " + link.link);
				helper.succeed();
			});
		});
	}

	/// assets ///

	private static JsonObject json(String path) {
		try(InputStream in = LeafiaSpkCableTests.class.getResourceAsStream(path)) {
			if(in == null) throw new IllegalStateException("missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Each OBJ object's bounds: {minX, minY, minZ, maxX, maxY, maxZ}. */
	private static Map<String, double[]> objBounds() {
		Map<String, double[]> out = new HashMap<>();
		List<double[]> vertices = new ArrayList<>();
		try(InputStream in = LeafiaSpkCableTests.class.getResourceAsStream("/assets/leafia/models/blocks/cable_spk.obj");
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line, current = null;
			while((line = reader.readLine()) != null) {
				String[] t = line.trim().split("\\s+");
				if(t[0].equals("v")) {
					vertices.add(new double[] { Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]) });
				} else if(t[0].equals("o")) {
					current = t[1];
					out.put(current, new double[] { 1e9, 1e9, 1e9, -1e9, -1e9, -1e9 });
				} else if(t[0].equals("f") && current != null) {
					double[] b = out.get(current);
					for(int i = 1; i < t.length; i++) {
						double[] v = vertices.get(Integer.parseInt(t[i].split("/")[0]) - 1);
						for(int a = 0; a < 3; a++) {
							b[a] = Math.min(b[a], v[a]);
							b[a + 3] = Math.max(b[a + 3], v[a]);
						}
					}
				}
			}
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		return out;
	}

	/**
	 * The geometry a cable draws reaches exactly the faces it is connected on.
	 *
	 * <p>This is the test that does not trust the part names. Upstream's {@code negZ} object sits at
	 * +z and the generator swaps the Z arms to match, which reads as a bug and is not; a test that
	 * re-derived the names would pass with the arms mirrored. This one reads the shipped blockstate,
	 * the shipped model each variant names, and the shipped OBJ, and measures where the visible
	 * parts actually are: toward every connected side the cable must reach the block's face, toward
	 * every other side it must stop at the core.
	 */
	@GameTest(template = EMPTY)
	public static void everyVariantDrawsGeometryThatReachesExactlyItsConnectedFaces(GameTestHelper helper) {

		JsonObject variants = json("/assets/leafia/blockstates/spk_cable.json").getAsJsonObject("variants");
		helper.assertTrue(variants.size() == 64, "a six-boolean block has 64 states and every one needs a variant, got " + variants.size());

		Map<String, double[]> parts = objBounds();
		helper.assertTrue(parts.size() == 10, "cable_spk.obj has ten objects, got " + parts.keySet());

		Direction[] order = { Direction.DOWN, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.WEST };

		for(int bits = 0; bits < 64; bits++) {
			boolean[] on = new boolean[6];
			StringBuilder key = new StringBuilder();
			for(int i = 0; i < 6; i++) {
				on[i] = (bits & (1 << i)) != 0;
				key.append(i == 0 ? "" : ",").append(order[i].getName()).append('=').append(on[i]);
			}

			var variant = variants.getAsJsonObject(key.toString());
			helper.assertTrue(variant != null, "no variant for " + key);
			String model = variant.get("model").getAsString().substring("leafia:block/".length());
			JsonObject modelJson = json("/assets/leafia/models/block/" + model + ".json");
			JsonObject hidden = modelJson.has("visibility") ? modelJson.getAsJsonObject("visibility") : new JsonObject();

			double[] reach = { 1e9, 1e9, 1e9, -1e9, -1e9, -1e9 };
			for(var e : parts.entrySet()) {
				if(hidden.has(e.getKey()) && !hidden.get(e.getKey()).getAsBoolean()) continue;
				for(int a = 0; a < 3; a++) {
					reach[a] = Math.min(reach[a], e.getValue()[a]);
					reach[a + 3] = Math.max(reach[a + 3], e.getValue()[a + 3]);
				}
			}

			boolean pX = on[1], nX = on[5], pY = on[4], nY = on[0], pZ = on[3], nZ = on[2];
			boolean straight = SPKCableTE.isStraight(pX, pY, pZ, nX, nY, nZ);

			for(int i = 0; i < 6; i++) {
				Direction dir = order[i];
				int axis = dir.getAxis().ordinal();
				boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
				double extent = positive ? reach[axis + 3] : -reach[axis];
				if(on[i])
					helper.assertTrue(extent > 0.499, key + ": connected " + dir + " must reach the face, reaches " + extent);
				else
					helper.assertTrue(extent < 0.2, key + ": unconnected " + dir + " must stop at the core, reaches " + extent);
			}
			if(straight)
				helper.assertTrue(model.equals("spk_cable_cx") || model.equals("spk_cable_cy") || model.equals("spk_cable_cz"),
						key + ": a straight piece uses the single stretched object, got " + model);
		}

		helper.succeed();
	}

	/** The item shows the core and the four horizontal arms, and has a display block to sit in a slot. */
	@GameTest(template = EMPTY)
	public static void theItemModelDrawsTheCoreAndFourArms(GameTestHelper helper) {

		JsonObject item = json("/assets/leafia/models/item/spk_cable.json");
		helper.assertTrue("leafia:block/spk_cable_item".equals(item.get("parent").getAsString()), "the item parents the item block model");
		helper.assertTrue(item.has("display") && item.getAsJsonObject("display").has("gui"), "and carries a display block");

		JsonObject hidden = json("/assets/leafia/models/block/spk_cable_item.json").getAsJsonObject("visibility");
		for(String part : new String[] { "CX", "CY", "CZ", "posY", "negY" })
			helper.assertTrue(hidden.has(part) && !hidden.get(part).getAsBoolean(), part + " is hidden on the item");
		for(String part : new String[] { "Core", "posX", "negX", "posZ", "negZ" })
			helper.assertTrue(!hidden.has(part), part + " is shown on the item");
		helper.succeed();
	}

	/** A structure turning the cable turns its arms with it. */
	@GameTest(template = EMPTY)
	public static void aRotatedCableTurnsItsArms(GameTestHelper helper) {

		if(off()) { helper.succeed(); return; }

		BlockState east = LeafiaBlocks.spk_cable.get().defaultBlockState().setValue(BlockCable.EAST, true).setValue(BlockCable.UP, true);
		BlockState turned = east.rotate(Rotation.CLOCKWISE_90);

		helper.assertTrue(turned.getValue(BlockCable.SOUTH) && !turned.getValue(BlockCable.EAST), "east turned clockwise is south");
		helper.assertTrue(turned.getValue(BlockCable.UP), "and up stays up");
		helper.succeed();
	}

	/** The block, its item and its block entity type all exist exactly when the switch is on, and its assets ship. */
	@GameTest(template = EMPTY)
	public static void theSpkSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.spkCable;
		ResourceLocation id = LeafiaRef.rl("spk_cable");

		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id) == on, "leafia:spk_cable should be in the block registry exactly when the switch is on");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) == on, "its BlockItem should follow the same switch");
		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id) == on,
				"and so should its BlockEntityType - a disabled feature registers nothing (D-1002)");

		helper.assertTrue(LeafiaSpkCableTests.class.getResource("/assets/leafia/textures/block/cable_spk.png") != null, "the texture ships");
		helper.assertTrue(LeafiaSpkCableTests.class.getResource("/assets/leafia/models/blocks/cable_spk.obj") != null, "and the model");
		helper.succeed();
	}

	/// the beam ///

	/** The colour blend of the beam layers: inner at 0, outer at 1, linear between. */
	@GameTest(template = EMPTY)
	public static void theBeamsLayerColoursBlendFromInnerToOuter(GameTestHelper helper) {

		int inner = 0x9A9A9A, outer = 0x64001E;
		helper.assertTrue(com.custom_hbm.util.LCEColor.interpolateColor(inner, outer, 0F) == (0xFF000000 | inner), "t=0 is the inner colour");
		helper.assertTrue(com.custom_hbm.util.LCEColor.interpolateColor(inner, outer, 1F) == (0xFF000000 | outer), "t=1 is the outer colour");
		int mid = com.custom_hbm.util.LCEColor.interpolateColor(0x000000, 0xFEFEFE, 0.5F);
		helper.assertTrue(mid == 0xFF7F7F7F, "halfway between black and 0xFEFEFE is 0x7F7F7F, got " + Integer.toHexString(mid));
		helper.succeed();
	}
}
