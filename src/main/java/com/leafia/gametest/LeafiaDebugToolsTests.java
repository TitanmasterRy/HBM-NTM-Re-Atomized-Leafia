package com.leafia.gametest;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.hbm.gametest.TickWindow;
import com.hbm.interfaces.IBomb;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.debug.blackhole_test.DebugBHTE;
import com.leafia.contents.debug.circle_test.CircleTestBlock;
import com.leafia.contents.debug.explosion_test.DebugBoomBlock;
import com.leafia.contents.debug.ff_test.source.DebugSourceTE;
import com.leafia.contents.debug.ff_test.tank.DebugTankTE;
import com.leafia.contents.debug.render_test.DebugRenderTestTE;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaMeteorCrater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Upstream's eight other debug blocks (DEBUG-TOOLS, D-3031): what registers under which switch and with which of
 * upstream's properties, the Forge-fluid source filling the tank directly and through a duct run with upstream's test
 * salt, the circle test's torus, the Tom test removing itself, the boom digging the meteor crater, and the two renderers'
 * blocks being invisible with their block entities. Every block placed is removed in the same body, and the boom digs at
 * a far spot of its own and cleans up after itself.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDebugToolsTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	private static final List<String> BASE_BLOCKS = List.of("test_boom", "test_rbmk_jet", "test_blackhole_render", "test_render", "test_tom");

	private static boolean registered(String id) {
		return BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(id));
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(LeafiaRef.rl(id));
	}

	private static boolean inMainTab(Block block) {
		for(Supplier<? extends ItemLike> s : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN))
			if(s.get().asItem() == block.asItem()) return true;
		return false;
	}

	@SuppressWarnings("deprecation")
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/**
	 * Each block is there exactly when its gate is: five on {@code debugBlocks}, the source and tank on
	 * {@code debugFFBlocks()} and the circle on {@code debugCircle()}. Upstream's {@code Material.ANVIL} with no hardness:
	 * instant to break, immovable. The {@code AddonBlockBase} six need a pickaxe and are in the tab; the two
	 * {@code AddonBlockContainer}s need nothing and are in no tab. The black hole and the render test draw nothing of
	 * their own.
	 */
	@GameTest(template = EMPTY)
	public static void theDebugBlocksAreUpstreamsAndFollowTheirSwitches(GameTestHelper helper) {
		for(String id : BASE_BLOCKS)
			helper.assertTrue(registered(id) == LeafiaFeatures.debugBlocks, id + " registered " + registered(id) + ", switch " + LeafiaFeatures.debugBlocks);
		for(String id : List.of("test_ff_source", "test_ff_tank"))
			helper.assertTrue(registered(id) == LeafiaFeatures.debugFFBlocks(), id + " registered " + registered(id) + ", gate " + LeafiaFeatures.debugFFBlocks());
		helper.assertTrue(registered("test_circle") == LeafiaFeatures.debugCircle(), "test_circle follows debugCircle()");

		for(String id : List.of("test_boom", "test_rbmk_jet", "test_blackhole_render", "test_render", "test_tom", "test_circle", "test_ff_source", "test_ff_tank")) {
			if(!registered(id)) continue;
			Block b = block(id);
			BlockState s = b.defaultBlockState();
			helper.assertTrue(s.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 0F && b.getExplosionResistance() == 0F, id + " has no hardness or resistance");
			helper.assertTrue(s.getPistonPushReaction() == PushReaction.BLOCK, id + " cannot be pushed");
			boolean container = id.startsWith("test_ff_");
			helper.assertTrue(s.requiresCorrectToolForDrops() == !container, id + (container ? " drops by hand" : " needs AddonBlockBase's pickaxe"));
			helper.assertTrue(inMainTab(b) == !container, id + (container ? " is in no tab" : " is in the addon's tab"));
		}
		for(String id : List.of("test_blackhole_render", "test_render"))
			if(registered(id)) helper.assertTrue(block(id).defaultBlockState().getRenderShape() == RenderShape.INVISIBLE, id + " is drawn by its renderer only");
		helper.succeed();
	}

	/**
	 * The source beside the tank: one update empties and refills the source with 10,000 mB of FLiBe carrying upstream's
	 * test mixture (thorium-232 4, uranium-235 2, no heat key) and hands it straight across, so the tank holds the
	 * 10,000 and the mixture; the source takes nothing and the tank gives nothing.
	 */
	@GameTest(template = EMPTY)
	public static void theSourceFillsATankBesideIt(GameTestHelper helper) {
		if(!LeafiaFeatures.debugFFBlocks() || !LeafiaFF.enabled()) { helper.succeed(); return; }
		helper.assertTrue(DebugSourceTE.CAPACITY == 10000 && DebugSourceTE.FILL == 10000 && DebugTankTE.CAPACITY == 10000, "10,000 mB each");
		BlockPos s = new BlockPos(1, 2, 1), t = s.east();
		helper.setBlock(s, LeafiaBlocks.test_ff_source.get());
		helper.setBlock(t, LeafiaBlocks.test_ff_tank.get());
		try {
			DebugSourceTE source = (DebugSourceTE) helper.getBlockEntity(s);
			DebugTankTE tank = (DebugTankTE) helper.getBlockEntity(t);
			source.update();
			helper.assertTrue(tank.zaza.getFluidAmount() == 10000, "the tank took the 10,000: " + tank.zaza.getFluidAmount());
			helper.assertTrue(LeafiaFF.fromFF(tank.zaza.getFluid().getFluid()) == LeafiaFluids.FLUORIDE, "of FLiBe");
			Map<String, Double> mix = MSRTEBase.readMixture(LeafiaFF.getTag(tank.zaza.getFluid()));
			helper.assertTrue(mix.size() == 2 && mix.get("th232") == 4D && mix.get("u235") == 2D, "upstream's test mixture: " + mix);
			helper.assertTrue(!LeafiaFF.getTag(tank.zaza.getFluid()).contains("heat"), "and no heat key, as upstream writes none");
			helper.assertTrue(source.zaza.getFluidAmount() == 0, "the source gave all it had this tick");
			source.update();
			helper.assertTrue(source.zaza.getFluidAmount() == 10000, "and is full again the next, the tank having no room");
			helper.assertTrue(source.fill(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 10), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0,
					"the source takes nothing");
			helper.assertTrue(tank.drain(10, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "the tank gives nothing");
		} finally {
			helper.setBlock(s, Blocks.AIR);
			helper.setBlock(t, Blocks.AIR);
		}
		helper.succeed();
	}

	/** The whole path, the voider's pattern: the source, three FLiBe ducts and the tank; the salt arrives, mixture and all. */
	@GameTest(template = BIG, timeoutTicks = TickWindow.TIMEOUT)
	public static void theSourceFillsTheTankThroughADuctRun(GameTestHelper helper) {
		if(!LeafiaFeatures.debugFFBlocks() || !LeafiaFeatures.ffDuct || !LeafiaFF.enabled()) { helper.succeed(); return; }
		BlockPos s = new BlockPos(3, 2, 8), t = s.east(4);
		helper.setBlock(s, LeafiaBlocks.test_ff_source.get());
		for(int i = 1; i <= 3; i++) helper.setBlock(s.east(i), LeafiaBlocks.ff_duct.get());
		helper.setBlock(t, LeafiaBlocks.test_ff_tank.get());
		FuzzyIdentifierItem.spreadTypeFF(helper.getLevel(), helper.absolutePos(s.east()), LeafiaFluids.FLUORIDE, Fluids.NONE, 8);
		DebugTankTE tank = (DebugTankTE) helper.getBlockEntity(t);
		TickWindow.sequence(helper).thenWaitUntil(() -> {
			helper.assertTrue(tank.zaza.getFluidAmount() > 0, "the tank has " + tank.zaza.getFluidAmount() + " mB");
			Map<String, Double> mix = MSRTEBase.readMixture(LeafiaFF.getTag(tank.zaza.getFluid()));
			helper.assertTrue(mix.get("th232") != null && mix.get("u235") != null, "with the mixture: " + mix);
		}).thenExecute(() -> {
			for(int i = 0; i <= 4; i++) helper.setBlock(s.east(i), Blocks.AIR);
		}).thenSucceed();
	}

	/** The circle test sets every block of the APR torus of radius 6 round itself, and nothing upstream's helper does not name. */
	@GameTest(template = BIG)
	public static void theCircleTestBuildsTheAprTorus(GameTestHelper helper) {
		if(!LeafiaFeatures.debugCircle()) { helper.succeed(); return; }
		helper.assertTrue(CircleTestBlock.RADIUS == 6, "radius 6");
		BlockPos rel = new BlockPos(8, 7, 8);
		BlockPos abs = helper.absolutePos(rel);
		helper.setBlock(rel, LeafiaBlocks.test_circle.get());
		ServerPlayer player = mockPlayer(helper);
		Map<BlockPos, BlockState> torus = CircleTestBlock.torus(abs);
		try {
			helper.assertTrue(!torus.isEmpty(), "the torus names blocks");
			InteractionResult r = helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
					new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(r.consumesAction(), "the click is used");
			int wrong = 0;
			for(Map.Entry<BlockPos, BlockState> e : torus.entrySet())
				if(!helper.getLevel().getBlockState(e.getKey()).equals(e.getValue())) wrong++;
			helper.assertTrue(wrong == 0, wrong + " of " + torus.size() + " torus blocks not set");
		} finally {
			for(BlockPos p : torus.keySet()) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			helper.setBlock(rel, Blocks.AIR);
			player.discard();
		}
		helper.succeed();
	}

	/** The Tom test's body upstream is commented out but its last line: used, it becomes air. The RBMK jet does nothing on a server. */
	@GameTest(template = EMPTY)
	public static void theTomTestRemovesItselfAndTheJetIsClientOnly(GameTestHelper helper) {
		if(!LeafiaFeatures.debugBlocks) { helper.succeed(); return; }
		ServerPlayer player = mockPlayer(helper);
		try {
			BlockPos rel = new BlockPos(1, 2, 1), abs = helper.absolutePos(rel);
			helper.setBlock(rel, LeafiaBlocks.test_tom.get());
			InteractionResult r = helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
					new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(r.consumesAction() && helper.getLevel().getBlockState(abs).isAir(), "the Tom test is gone after a use");

			helper.setBlock(rel, LeafiaBlocks.test_rbmk_jet.get());
			int before = helper.getLevel().getEntitiesOfClass(Entity.class, new AABB(abs).inflate(8)).size();
			r = helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(r.consumesAction() && helper.getLevel().getBlockState(abs).is(LeafiaBlocks.test_rbmk_jet.get()), "the jet's click is used and it stays");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(Entity.class, new AABB(abs).inflate(8)).size() == before, "and a server makes nothing");
			helper.setBlock(rel, Blocks.AIR);

			helper.setBlock(rel, LeafiaBlocks.test_blackhole_render.get());
			helper.assertTrue(helper.getBlockEntity(rel) instanceof DebugBHTE, "the black hole has its block entity");
			helper.setBlock(rel, LeafiaBlocks.test_render.get());
			helper.assertTrue(helper.getBlockEntity(rel) instanceof DebugRenderTestTE, "the render test has its block entity");
			helper.setBlock(rel, Blocks.AIR);
		} finally {
			player.discard();
		}
		helper.succeed();
	}

	/**
	 * The boom is a bomb that digs the addon's meteor crater ({@code LeafiaMeteorCrater}, D-3022) and answers
	 * {@code DETONATED}; dug at a far spot into a slab of stone, so nothing near a test is touched.
	 */
	@GameTest(template = EMPTY)
	public static void theBoomDigsTheMeteorCrater(GameTestHelper helper) {
		if(!LeafiaFeatures.debugBlocks) { helper.succeed(); return; }
		helper.assertTrue(LeafiaBlocks.test_boom.get() instanceof IBomb, "the boom is a bomb");
		ServerLevel level = helper.getLevel();
		RandomSource r = level.random;
		int x = (70_000 + r.nextInt(4000) * 16) * (r.nextBoolean() ? 1 : -1);
		int z = (70_000 + r.nextInt(4000) * 16) * (r.nextBoolean() ? 1 : -1);
		for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
		BlockPos far = new BlockPos(x + 8, 280, z + 8);
		level.getChunk(0, 0);
		int originTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		try {
			for(BlockPos p : BlockPos.betweenClosed(far.offset(-14, -12, -14), far.offset(14, 0, 14))) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2 | 16);
			DebugBoomBlock boom = LeafiaBlocks.test_boom.get();
			helper.assertTrue(boom.explode(level, far) == IBomb.BombReturnCode.DETONATED, "detonated");
			for(int dy = 0; dy < LeafiaMeteorCrater.MAX_DEPTH - 2; dy++)
				helper.assertTrue(level.getBlockState(far.below(dy)).isAir(), "the centre is dug " + dy + " down: " + level.getBlockState(far.below(dy)));
			helper.assertTrue(level.getBlockState(far.offset(13, 0, 13)).is(Blocks.STONE), "the corner is not dug");
		} finally {
			for(BlockPos p : BlockPos.betweenClosed(far.offset(-32, -14, -32), far.offset(32, 6, 32))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			for(Entity e : level.getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(far.offset(-32, -14, -32), far.offset(32, 6, 32)).inflate(4))) e.discard();
			// upstream's centre-column debris lands on the world origin column (D-3022); take it back off
			for(int y = originTop; y < originTop + 16; y++) {
				BlockPos o = new BlockPos(0, y, 0);
				if(level.getBlockState(o).is(Blocks.STONE)) level.setBlock(o, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		helper.succeed();
	}
}
