package com.leafia.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.hbm.handler.neutron.NeutronHandler;
import com.hbm.handler.neutron.NeutronNodeWorld;
import com.hbm.handler.neutron.NeutronNodeWorld.StreamWorld;
import com.hbm.handler.neutron.NeutronStream;
import com.hbm.handler.neutron.RBMKNeutronHandler;
import com.hbm.handler.neutron.RBMKNeutronHandler.RBMKNeutronNode;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.rbmk.columns.realersim.RBMKRealerSimTE;
import com.leafia.contents.machines.reactors.rbmk.columns.realersim.RealerSimNeutronStream;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaGameRules;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The RealerSim fuel channel (D-1074): a ReaSim channel whose streams fly in random directions,
 * twice as far, bend toward absorbers and control rods, and mostly ignore what they pass.
 *
 * <p>Like the base mod's ReaSim tests, nothing here may assert a particular direction - every
 * stream's is drawn from the whole circle. What is pinned is what the randomness does not change:
 * the count and the dial behind it, each stream's share, the length of the walk, where a walk
 * goes once a direction is given, and what each kind of column does to a stream that reaches it.
 *
 * <p>Every test that walks a stream uses the 16-block template with its source on the west edge,
 * so a walk of up to twelve cells stays inside its own plot: a neighbouring test's absorber would
 * bend it.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRealerSimTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	/** The source column's spot in the 16-block template. */
	private static final BlockPos S = new BlockPos(1, 1, 8);

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-6D, what + ": expected " + expected + ", was " + actual);
	}

	private static BlockEntityRBMKBase place(GameTestHelper helper, Supplier<? extends Block> block, BlockPos relative) {
		BlockState core = block.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, RBMKBase.DIR_NO_LID);
		helper.setBlock(relative, core);
		return (BlockEntityRBMKBase) helper.getBlockEntity(relative);
	}

	private static RBMKRealerSimTE placeRealerSim(GameTestHelper helper, BlockPos relative) {
		return (RBMKRealerSimTE) place(helper, LeafiaBlocks.rbmk_rod_realersim, relative);
	}

	/** Runs a body with {@code dialReasimCount} set, and puts the dial back whatever happens. */
	private static void withCount(GameTestHelper helper, int count, Runnable body) {
		GameRules.IntegerValue rule = helper.getLevel().getGameRules().getRule(LeafiaGameRules.REASIM_COUNT);
		int before = rule.get();
		try {
			rule.set(count, null);
			body.run();
		} finally {
			rule.set(before, null);
		}
	}

	private static int throwCount(GameTestHelper helper, RBMKRealerSimTE source) {
		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
		world.removeAllStreams();
		source.spreadFlux(20D, 0.5D);
		int n = world.streamCount();
		world.removeAllStreams();
		return n;
	}

	/// THE CHANNEL ///

	/**
	 * <b>Six streams by default, each a RealerSim stream at three quarters of the outflux.</b>
	 * The default is NTM-CE's {@code KEY_REASIM_COUNT}, and it is a gamerule's default, so it is
	 * read from a fresh {@code GameRules} rather than from the test level, which a player could
	 * have changed.
	 */
	@GameTest(template = EMPTY)
	public static void aChannelThrowsSixRealerSimStreamsAtThreeQuarters(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		helper.assertTrue(new GameRules().getInt(LeafiaGameRules.REASIM_COUNT) == 6, "dialReasimCount defaults to 6");

		RBMKRealerSimTE source = placeRealerSim(helper, new BlockPos(1, 1, 1));
		withCount(helper, 6, () -> {
			StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
			world.removeAllStreams();
			source.spreadFlux(20D, 0.25D);
			helper.assertTrue(world.streamCount() == 6, "six streams at the default, threw " + world.streamCount());
			for(NeutronStream stream : world.streams()) {
				helper.assertTrue(stream instanceof RealerSimNeutronStream, "a " + stream.getClass().getSimpleName() + " is not a RealerSim stream");
				assertClose(helper, 15D, stream.fluxQuantity, "one stream's share of 20");
				assertClose(helper, 0.25D, stream.fluxRatio, "the fast/slow ratio is passed through");
			}
			world.removeAllStreams();
		});

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/** The dial sets the count, clamped to 1..24 as upstream clamps it. */
	@GameTest(template = EMPTY)
	public static void theDialSetsTheCountBetweenOneAndTwentyFour(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		RBMKRealerSimTE source = placeRealerSim(helper, new BlockPos(1, 1, 1));
		withCount(helper, 12, () -> helper.assertTrue(throwCount(helper, source) == 12, "the dial at 12"));
		withCount(helper, 0, () -> helper.assertTrue(throwCount(helper, source) == 1, "the dial at 0 clamps to 1"));
		withCount(helper, 99, () -> helper.assertTrue(throwCount(helper, source) == 24, "the dial at 99 clamps to 24"));

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/**
	 * <b>Each stream points anywhere.</b> Unit vectors in the horizontal plane, and over 600 of
	 * them more than a hundred different whole-degree headings - which a fixed fan of any size up
	 * to 24 cannot produce.
	 */
	@GameTest(template = EMPTY)
	public static void streamsFlyInEveryDirection(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		RBMKRealerSimTE source = placeRealerSim(helper, new BlockPos(1, 1, 1));
		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
		Set<Long> headings = new HashSet<>();

		withCount(helper, 6, () -> {
			for(int n = 0; n < 100; n++) {
				world.removeAllStreams();
				source.spreadFlux(20D, 0.5D);
				for(NeutronStream stream : world.streams()) {
					Vec3 v = stream.vector;
					assertClose(helper, 0D, v.y, "a stream stays in the horizontal plane");
					helper.assertTrue(Math.abs(Math.sqrt(v.x * v.x + v.z * v.z) - 1D) < 1.0e-3D, "a stream's vector is a unit vector, was " + v);
					headings.add(Math.round(Math.toDegrees(Math.atan2(v.z, v.x))));
				}
			}
			world.removeAllStreams();
		});

		helper.assertTrue(headings.size() > 100, "600 streams should cover far more than 100 headings, saw " + headings.size());

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/** Zero flux is still the signal to leave the cache. */
	@GameTest(template = EMPTY)
	public static void aChannelWithNoFluxLeavesTheCache(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		BlockPos where = new BlockPos(1, 1, 1);
		RBMKRealerSimTE source = placeRealerSim(helper, where);
		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());

		world.removeAllStreams();
		source.spreadFlux(20D, 0.5D);
		helper.assertTrue(world.getNode(helper.absolutePos(where)) != null, "flux caches it");

		world.removeAllStreams();
		source.spreadFlux(0D, 0D);
		helper.assertTrue(world.getNode(helper.absolutePos(where)) == null, "no flux takes it back out");
		helper.assertTrue(world.streamCount() == 0, "and throws nothing");

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/**
	 * It is still a fuel channel, and its own: the core carries the addon's block entity and ticks
	 * on the addon's type, the top carries the addon's inventory proxy, a console reads it as a
	 * simulated fuel channel, and the moderated one is moderated.
	 */
	@GameTest(template = EMPTY)
	public static void theChannelIsWiredToItsOwnTypes(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		RBMKRealerSimTE plain = placeRealerSim(helper, new BlockPos(1, 1, 1));
		BlockEntityRBMKBase moderated = place(helper, LeafiaBlocks.rbmk_rod_realersim_mod, new BlockPos(3, 1, 3));

		helper.assertTrue(plain.getType() == LeafiaBlockEntities.RBMK_REALERSIM.get(), "the core is the addon's type");
		helper.assertTrue(plain instanceof BlockEntityRBMKRod, "and a fuel channel");
		helper.assertTrue(plain.getConsoleType() == BlockEntityRBMKConsole.ColumnType.FUEL_SIM, "a console sees FUEL_SIM");
		helper.assertTrue(!plain.isModerated() && moderated.isModerated(), "only the _mod block is moderated");

		BlockState core = plain.getBlockState();
		helper.assertTrue(core.getTicker(helper.getLevel(), LeafiaBlockEntities.RBMK_REALERSIM.get()) != null, "the core ticks");

		BlockState extra = core.setValue(BlockDummyable.PART, BlockDummyable.Part.EXTRA);
		var proxy = ((RBMKBase) core.getBlock()).newBlockEntity(helper.absolutePos(new BlockPos(1, 2, 1)), extra);
		helper.assertTrue(proxy instanceof BlockEntityProxyCombo && proxy.getType() == LeafiaBlockEntities.RBMK_REALERSIM_PROXY.get(),
				"the top is the addon's proxy, was " + proxy);

		helper.succeed();
	}

	/// THE STREAM ///

	private static List<BlockPos> walk(GameTestHelper helper, BlockEntityRBMKBase source, Vec3 vector, int range) {
		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
		RBMKNeutronNode origin = RBMKNeutronHandler.makeNode(world, source);
		BlockPos base = source.getBlockPos();
		List<BlockPos> out = new ArrayList<>();
		Iterator<BlockPos> it = new RealerSimNeutronStream(origin, vector).getBlocks(range);
		while(it.hasNext()) {
			BlockPos p = it.next();
			out.add(new BlockPos(p.getX() - base.getX(), 0, p.getZ() - base.getZ()));
		}
		return out;
	}

	/** <b>Twice the range</b>, and straight when there is nothing to bend it. */
	@GameTest(template = EMPTY16)
	public static void aStreamWalksTwiceTheRange(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		List<BlockPos> path = walk(helper, placeRealerSim(helper, S), new Vec3(1, 0, 0), 5);
		helper.assertTrue(path.size() == 10, "range 5 walks 10 cells, walked " + path.size());
		for(int k = 0; k < path.size(); k++)
			helper.assertTrue(path.get(k).equals(new BlockPos(k + 1, 0, 0)), "cell " + k + " is " + path.get(k));

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/**
	 * <b>An absorber beside the path pulls the stream into it.</b> Heading east with an absorber
	 * one cell north of the third cell: the second step sees it in its 3x3, re-aims from (1, 0)
	 * at its centre, and the third cell is the absorber's - (3, 1) - where a straight walk goes
	 * to (3, 0). A control column pulls the same way.
	 */
	@GameTest(template = EMPTY16)
	public static void anAbsorberOrControlRodPullsTheStream(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		BlockEntityRBMKBase source = placeRealerSim(helper, S);

		place(helper, ModBlocks.rbmk_absorber, S.offset(3, 0, 1));
		List<BlockPos> path = walk(helper, source, new Vec3(1, 0, 0), 5);
		helper.assertTrue(path.size() == 10, "bending does not shorten the walk");
		helper.assertTrue(path.get(2).equals(new BlockPos(3, 0, 1)), "the third cell is the absorber's, was " + path);

		helper.setBlock(S.offset(3, 0, 1), net.minecraft.world.level.block.Blocks.AIR);
		place(helper, ModBlocks.rbmk_control, S.offset(3, 0, -1));
		path = walk(helper, source, new Vec3(1, 0, 0), 5);
		helper.assertTrue(path.get(2).equals(new BlockPos(3, 0, -1)), "a control rod pulls too, was " + path);

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	private static RealerSimNeutronStream fire(GameTestHelper helper, BlockEntityRBMKBase source, double ratio) {
		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
		RBMKNeutronNode origin = RBMKNeutronHandler.makeNode(world, source);
		world.addNode(origin);
		RealerSimNeutronStream stream = new RealerSimNeutronStream(origin, new Vec3(1, 0, 0), 100D, ratio);
		stream.runStreamInteraction(helper.getLevel(), world);
		world.removeAllStreams();
		return stream;
	}

	/**
	 * <b>A fast stream that has not been moderated passes a fuelled channel by; a slow one feeds
	 * it.</b> Upstream's {@code moderatedCount > 0 || fluxRatio == 0}, the condition that makes a
	 * RealerSim lattice need its moderators.
	 */
	@GameTest(template = EMPTY16)
	public static void onlyAModeratedOrSlowStreamFeedsAChannel(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		NeutronHandler.cacheDials(helper.getLevel());
		BlockEntityRBMKBase source = placeRealerSim(helper, S);
		BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, ModBlocks.rbmk_rod, S.offset(2, 0, 0));
		rod.hasRod = true;

		rod.fluxQuantity = 0;
		fire(helper, source, 1.0D);
		assertClose(helper, 0D, rod.fluxQuantity, "an unmoderated fast stream passes the channel");

		rod.fluxQuantity = 0;
		fire(helper, source, 0.0D);
		assertClose(helper, 100D, rod.fluxQuantity, "a slow stream feeds it");

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/**
	 * <b>A moderator moderates one time in four.</b> A fast stream crossing one moderator to a
	 * fuelled channel feeds it only when the moderator took, so over 400 streams about 100 feed it.
	 * The bounds are 50 and 150, nearly six standard deviations either side; a moderator that
	 * always took would feed 400, one that never did 0.
	 */
	@GameTest(template = EMPTY16)
	public static void aModeratorTakesOneTimeInFour(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		NeutronHandler.cacheDials(helper.getLevel());
		BlockEntityRBMKBase source = placeRealerSim(helper, S);
		place(helper, ModBlocks.rbmk_moderator, S.offset(1, 0, 0));
		BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, ModBlocks.rbmk_rod, S.offset(2, 0, 0));
		rod.hasRod = true;

		int fed = 0;
		for(int n = 0; n < 400; n++) {
			rod.fluxQuantity = 0;
			fire(helper, source, 1.0D);
			if(rod.fluxQuantity > 0) fed++;
		}
		helper.assertTrue(fed >= 50 && fed <= 150, "about a quarter of 400 should be moderated, " + fed + " were");

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}

	/**
	 * <b>An absorber stops a RealerSim stream and heats from it exactly as from the base mod's.</b>
	 * Upstream's copy is NTM-CE's method, whose absorber takes no heat; the copy follows this
	 * port's base method instead (D-1000, D-1074), so {@code 0.05 x 100} each time.
	 */
	@GameTest(template = EMPTY16)
	public static void anAbsorberHeatsAsTheBaseModsDoes(GameTestHelper helper) {
		if(!LeafiaFeatures.realerSim) { helper.succeed(); return; }

		NeutronHandler.cacheDials(helper.getLevel());
		BlockEntityRBMKBase source = placeRealerSim(helper, S);
		BlockEntityRBMKBase absorber = place(helper, ModBlocks.rbmk_absorber, S.offset(2, 0, 0));
		double conversion = com.hbm.blockentity.machine.rbmk.RBMKDials.getAbsorberHeatConversion(helper.getLevel());

		double before = absorber.heat;
		fire(helper, source, 1.0D);
		assertClose(helper, before + 100D * conversion, absorber.heat, "a RealerSim stream heats the absorber");

		StreamWorld world = NeutronNodeWorld.getOrAddWorld(helper.getLevel());
		new RBMKNeutronHandler.RBMKNeutronStream(RBMKNeutronHandler.makeNode(world, source), new Vec3(1, 0, 0), 100D, 1.0D)
				.runStreamInteraction(helper.getLevel(), world);
		world.removeAllStreams();
		assertClose(helper, before + 200D * conversion, absorber.heat, "by the same amount as the base mod's stream");

		NeutronNodeWorld.removeAllWorlds();
		helper.succeed();
	}
}
