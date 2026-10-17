package com.leafia.gametest;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.eventbuses.StructuralIntegrityListener;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.StructuralIntegrityHandler;
import com.leafia.unsorted.StructuralIntegrityHandler.SimulationData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Structural integrity (D-1086): a building that is not held up falls down, behind a switch that
 * is off.
 *
 * <p><b>The world under a test.</b> The 16-block template sits with its floor cell at relative
 * {@code y = 0}, and below it the test world is solid down to the bottom of the world, so a block
 * at {@code y = 0} (or any column that reaches it) is <em>grounded</em>. The first test asserts
 * that, because every number below depends on it and a different test world would otherwise give
 * confident wrong answers.
 *
 * <p><b>The numbers are worked out by hand, not read off the code.</b> A beam of {@code L} blocks
 * stuck out sideways from a grounded wall passes its load down the chain: every link carries the
 * mass of everything beyond it (the search hands each column the mass so far), and every link is
 * held by exactly one neighbour's glue. So an {@code L}-long beam of mass {@code m} and glue
 * {@code g} holds when {@code m * L <= g}. Stone is mass 2, glue 25: twelve holds, thirteen does
 * not. The table in {@link #BEAMS} is that arithmetic for six materials.
 *
 * <p><b>The structure of the file.</b> Tests that only ask a question (the tables, the verdict of
 * a simulation, one collapse) run in the default batch. Tests that flip the switch, queue events
 * or spend the per-tick budget each have a batch of their own, because those are process-wide
 * statics and two tests in a batch run side by side: one finishing would restore the switch
 * under the other. Every one restores what it wrote in a {@code finally}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaStructuralIntegrityTests {

	private static final String EMPTY16 = "empty16";

	/** The wall's column and the beam's row, in template coordinates. */
	private static final int WALL_X = 1;
	private static final int BEAM_Y = 2;

	private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, message);
	}

	private static BlockState state(Supplier<? extends Block> block) {
		return block.get().defaultBlockState();
	}

	private static SimulationData simulate(GameTestHelper helper, BlockPos relative) {
		SimulationData sim = StructuralIntegrityHandler.handleBlockSimulate(helper.getLevel(), helper.absolutePos(relative), null);
		helper.assertTrue(sim != null, "the simulation answered at " + relative);
		return sim;
	}

	private static boolean collapses(GameTestHelper helper, BlockPos relative) {
		return simulate(helper, relative).maxRatio >= 1;
	}

	private static void column(GameTestHelper helper, Block block, int x, int z, int y0, int y1) {
		for (int y = y0; y <= y1; y++)
			helper.setBlock(new BlockPos(x, y, z), block);
	}

	/** A grounded wall of {@code block} at the west edge, and a beam of it stuck out to the east. */
	private static void cantilever(GameTestHelper helper, Block block, int z, int length) {
		column(helper, block, WALL_X, z, 0, BEAM_Y);
		for (int i = 0; i < length; i++)
			helper.setBlock(new BlockPos(WALL_X + 1 + i, BEAM_Y, z), block);
	}

	private static void clear(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++)
				for (int z = z0; z <= z1; z++)
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
	}

	// ------------------------------------------------------------------ the tables

	/** The world the numbers assume: the floor of the template is grounded, the air above it is not. */
	@GameTest(template = EMPTY16)
	public static void theFloorIsGroundedAndTheAirIsNot(GameTestHelper helper) {
		helper.setBlock(new BlockPos(5, 0, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(9, 5, 9), Blocks.STONE);
		assertTrue(helper, !collapses(helper, new BlockPos(5, 0, 5)), "a block on the floor is grounded");
		assertTrue(helper, collapses(helper, new BlockPos(9, 5, 9)), "a block in the air is not");
		helper.succeed();
	}

	/** Glue, per family. Upstream's literals: iron and anvil 40, wood 6, rock 25, anything else 12. */
	@GameTest(template = EMPTY16)
	public static void glueFollowsTheMaterialFamily(GameTestHelper helper) {
		Object[][] table = {
				{"stone is rock", Blocks.STONE, 25},
				{"obsidian is rock", Blocks.OBSIDIAN, 25},
				{"a stone slab is rock", Blocks.STONE_SLAB, 25},
				{"an iron block is iron", Blocks.IRON_BLOCK, 40},
				{"iron bars are iron", Blocks.IRON_BARS, 40},
				{"an anvil is anvil", Blocks.ANVIL, 40},
				{"planks are wood", Blocks.OAK_PLANKS, 6},
				{"a log is wood", Blocks.OAK_LOG, 6},
				{"dirt is neither", Blocks.DIRT, 12},
				{"sand is neither", Blocks.SAND, 12},
				{"glass is neither", Blocks.GLASS, 12},
				{"ice sounds like glass and is not rock", Blocks.ICE, 12},
		};
		for (Object[] row : table) {
			int glue = StructuralIntegrityHandler.getGlue(((Block) row[1]).defaultBlockState(), null, null);
			assertTrue(helper, glue == (int) row[2], row[0] + ": glue " + row[2] + ", was " + glue);
		}
		helper.succeed();
	}

	/** Mass: iron 5, wood 1, everything else 1, a third of that when not a whole cube, and rock by blast resistance. */
	@GameTest(template = EMPTY16)
	public static void massFollowsTheMaterialAndTheShape(GameTestHelper helper) {
		Object[][] table = {
				{"stone: 1 + (int)6^0.45 / 2", Blocks.STONE, 2},
				{"obsidian: 1 + (int)1200^0.45 / 2", Blocks.OBSIDIAN, 13},
				{"bedrock hits the cap of 15", Blocks.BEDROCK, 16},
				{"an iron block", Blocks.IRON_BLOCK, 5},
				{"iron bars are a third, floored at one", Blocks.IRON_BARS, 1},
				{"planks", Blocks.OAK_PLANKS, 1},
				{"dirt", Blocks.DIRT, 1},
				{"a stone slab is rock, which is not divided", Blocks.STONE_SLAB, 2},
		};
		for (Object[] row : table) {
			int mass = StructuralIntegrityHandler.getMass(((Block) row[1]).defaultBlockState(), null, null);
			assertTrue(helper, mass == (int) row[2], row[0] + ": mass " + row[2] + ", was " + mass);
		}
		helper.succeed();
	}

	/**
	 * The base mod's blocks are classified by the 1.7.10 Material the generator read, not by
	 * sound. {@code steel_scaffold} is {@code Material.iron} and sounds like stone: by sound it
	 * would hold 25, where upstream gives 40 x 4. {@code concrete_rebar} is rock with its x8 (D-3075).
	 */
	@GameTest(template = EMPTY16)
	public static void aBaseModBlockIsClassifiedByItsOldMaterial(GameTestHelper helper) {
		assertTrue(helper, StructuralIntegrityHandler.getGlue(state(ModBlocks.steel_scaffold), null, null) == 160,
				"steel_scaffold: iron 40 x 4");
		assertTrue(helper, StructuralIntegrityHandler.getGlue(state(ModBlocks.concrete_rebar), null, null) == 200,
				"concrete_rebar is rock: 25 x 8 (D-3075; upstream returned 25 before the multiplier)");
		assertTrue(helper, StructuralIntegrityHandler.getMass(state(ModBlocks.concrete_rebar), null, null) == 5,
				"concrete_rebar: 1 + (int)144^0.45 / 2");
		assertTrue(helper, StructuralIntegrityHandler.getGlue(state(ModBlocks.dfc_core), null, null) == 0, "dfc_core holds nothing");
		assertTrue(helper, StructuralIntegrityHandler.getMass(state(ModBlocks.dfc_core), null, null) == 0, "and weighs nothing");
		if (LeafiaBlocks.elevator_s6floor != null) {
			// D-3075: EvFloor is tested before BlockDummyable, so its own lines (glue 1, mass 0) are reached.
			for (BlockDummyable.Part part : BlockDummyable.Part.values()) {
				BlockState floor = state(LeafiaBlocks.elevator_s6floor).setValue(BlockDummyable.PART, part);
				assertTrue(helper, StructuralIntegrityHandler.getGlue(floor, null, null) == 1, "an elevator landing's " + part + " holds 1");
				assertTrue(helper, StructuralIntegrityHandler.getMass(floor, null, null) == 0, "and weighs nothing");
			}
		}
		helper.succeed();
	}

	/** The multiblock table: the core holds nothing, every other part holds its machine's number. */
	@GameTest(template = EMPTY16)
	public static void aMachineHoldsWhatItsTableLineSaysAndItsCoreHoldsNothing(GameTestHelper helper) {
		Object[][] table = {
				{"machine_chungus", ModBlocks.machine_chungus, 22, 10},
				{"machine_well", ModBlocks.machine_well, 22, 10},
				{"machine_coker", ModBlocks.machine_coker, 32, 10},
				{"rbmk_blank", ModBlocks.rbmk_blank, 20, 1},
				{"machine_ammo_press: a machine with no line", ModBlocks.machine_ammo_press, 0, 10},
		};
		for (Object[] row : table) {
			Block block = ((Supplier<? extends Block>) row[1]).get();
			BlockState core = block.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE);
			BlockState dummy = block.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.DUMMY);
			BlockState extra = block.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.EXTRA);
			assertTrue(helper, StructuralIntegrityHandler.getGlue(core, null, null) == 0, row[0] + " core: glue 0");
			assertTrue(helper, StructuralIntegrityHandler.getGlue(dummy, null, null) == (int) row[2], row[0] + " dummy: glue " + row[2]);
			assertTrue(helper, StructuralIntegrityHandler.getGlue(extra, null, null) == (int) row[2], row[0] + " extra: glue " + row[2]);
			for (BlockState s : List.of(core, dummy, extra))
				assertTrue(helper, StructuralIntegrityHandler.getMass(s, null, null) == (int) row[3], row[0] + ": mass " + row[3]);
		}
		helper.succeed();
	}

	/**
	 * The machines of upstream's glue table the base mod has since ported (merged 2026-10-07) hold
	 * their table value in every non-core part: electrolyser 22, excavator 42, fracking tower 62,
	 * and the two silo hatches 22 (doors here, not upstream's CE classes).
	 */
	@GameTest(template = EMPTY16)
	public static void theNewlyPortedMachinesOfTheGlueTableHoldTheirValue(GameTestHelper helper) {
		Object[][] rows = {{"machine_electrolyser", 22}, {"machine_excavator", 42},
				{"machine_fracking_tower", 62}, {"silo_hatch", 22}, {"silo_hatch_large", 22},
				{"icf", 22}, {"machine_mining_laser", 22}};
		for (Object[] row : rows) {
			Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("hbm", (String) row[0]));
			assertTrue(helper, block instanceof BlockDummyable, row[0] + " is a multiblock");
			BlockState dummy = block.defaultBlockState();
			if (dummy.hasProperty(BlockDummyable.PART)) dummy = dummy.setValue(BlockDummyable.PART, BlockDummyable.Part.DUMMY);
			int glue = StructuralIntegrityHandler.getGlue(dummy, null, null);
			assertTrue(helper, glue == (int) row[1], row[0] + ": glue " + row[1] + ", got " + glue);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY16)
	public static void theNetherAndTheEndAreNeverChecked(GameTestHelper helper) {
		assertTrue(helper, StructuralIntegrityHandler.blacklistedDimensions.equals(java.util.Set.of(Level.NETHER, Level.END)),
				"exactly the nether and the end: " + StructuralIntegrityHandler.blacklistedDimensions);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the verdict

	/** material, wall-and-beam block, the longest beam that holds. One more must not. */
	private static final Object[][] BEAMS = {
			{"stone: 25 / 2", Blocks.STONE, 12},
			{"iron block: 40 / 5", Blocks.IRON_BLOCK, 8},
			{"planks: 6 / 1", Blocks.OAK_PLANKS, 6},
			{"dirt: 12 / 1", Blocks.DIRT, 12},
			{"obsidian: 25 / 13", Blocks.OBSIDIAN, 1},
	};

	@GameTest(template = EMPTY16)
	public static void aBeamHoldsUpToItsLimitAndNoFurther(GameTestHelper helper) {
		for (Object[] row : BEAMS) {
			Block block = (Block) row[1];
			int limit = (int) row[2];
			BlockPos tip = new BlockPos(WALL_X + limit, BEAM_Y, 8);
			cantilever(helper, block, 8, limit);
			assertTrue(helper, !collapses(helper, tip), row[0] + ": " + limit + " blocks holds");
			clear(helper, 1, 0, 8, 15, 3, 8);

			tip = new BlockPos(WALL_X + limit + 1, BEAM_Y, 8);
			cantilever(helper, block, 8, limit + 1);
			assertTrue(helper, collapses(helper, tip), row[0] + ": " + (limit + 1) + " blocks collapses");
			clear(helper, 1, 0, 8, 15, 3, 8);
		}
		helper.succeed();
	}

	/** The verdict is the root's: the block before the tip carries twelve, which is within the limit, so it holds. */
	@GameTest(template = EMPTY16)
	public static void aRootNearTheWallHoldsWhereTheTipDoesNot(GameTestHelper helper) {
		cantilever(helper, Blocks.STONE, 8, 13);
		assertTrue(helper, collapses(helper, new BlockPos(14, BEAM_Y, 8)), "the tip of thirteen collapses");
		assertTrue(helper, !collapses(helper, new BlockPos(13, BEAM_Y, 8)), "the block before it holds: its twelve is within the limit");
		assertTrue(helper, !collapses(helper, new BlockPos(2, BEAM_Y, 8)), "and the one at the wall");
		helper.succeed();
	}

	/** A second wall at the far end turns the same thirteen blocks from a collapse into a bridge. */
	@GameTest(template = EMPTY16)
	public static void aSecondSupportHoldsWhatOneCannot(GameTestHelper helper) {
		cantilever(helper, Blocks.STONE, 8, 13);
		assertTrue(helper, collapses(helper, new BlockPos(14, BEAM_Y, 8)), "a thirteen-block cantilever collapses");
		column(helper, Blocks.STONE, 15, 8, 0, BEAM_Y);
		for (int x : new int[] {14, 8, 2})
			assertTrue(helper, !collapses(helper, new BlockPos(x, BEAM_Y, 8)), "with a wall at both ends, x = " + x + " holds");
		helper.succeed();
	}

	/**
	 * Glue is <em>added</em> over every supported neighbour, one per block of the column. A beam
	 * two blocks tall is held by two blocks of wall at every link, so the same thirteen blocks
	 * that fell as a single row hold. (With the sum taken as the last neighbour's glue alone the
	 * second row would add nothing.)
	 */
	@GameTest(template = EMPTY16)
	public static void aTallerBeamIsHeldByEveryBlockOfWallItTouches(GameTestHelper helper) {
		cantilever(helper, Blocks.STONE, 8, 13);
		column(helper, Blocks.STONE, WALL_X, 8, 0, BEAM_Y + 1);
		assertTrue(helper, collapses(helper, new BlockPos(14, BEAM_Y, 8)), "one row of thirteen collapses");
		for (int x = 2; x <= 14; x++)
			helper.setBlock(new BlockPos(x, BEAM_Y + 1, 8), Blocks.STONE);
		assertTrue(helper, !collapses(helper, new BlockPos(14, BEAM_Y, 8)), "two rows of thirteen hold");
		assertTrue(helper, !collapses(helper, new BlockPos(14, BEAM_Y + 1, 8)), "from the top row too");
		helper.succeed();
	}

	/** The search is bounded by {@code MAX_DEPTH}: a floating plate of 196 blocks is searched fifty-one deep and no further. */
	@GameTest(template = EMPTY16)
	public static void theSearchStopsFiftyOneDeep(GameTestHelper helper) {
		for (int x = 1; x <= 14; x++)
			for (int z = 1; z <= 14; z++)
				helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
		StructuralIntegrityHandler.LOCAL.deepest = 0;
		SimulationData sim = simulate(helper, new BlockPos(1, 5, 1));
		assertTrue(helper, sim.maxRatio >= 1, "a floating plate collapses");
		int deepest = StructuralIntegrityHandler.LOCAL.deepest;
		assertTrue(helper, deepest == 51, "the search of 196 blocks reached depth 51 and not " + deepest);
		helper.succeed();
	}

	/** A chunk that is not loaded is never asked for, loaded or generated: the answer is nothing. */
	@GameTest(template = EMPTY16)
	public static void aChunkThatIsNotLoadedIsLeftAlone(GameTestHelper helper) {
		BlockPos far = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 20_000);
		Level level = helper.getLevel();
		assertTrue(helper, !level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "the far chunk is not loaded to begin with");
		assertTrue(helper, StructuralIntegrityHandler.SERVER.handleBlock(level, far, false, false, null) == null, "nothing to say about it");
		assertTrue(helper, StructuralIntegrityHandler.LOCAL.handleBlock(level, far, true, false, null) == null, "nor in a simulation");
		assertTrue(helper, !level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "and it is still not loaded");
		helper.succeed();
	}

	/** A cube with an inventory takes it down with it (D-3076): the block entity rides in the falling block, and the cell drops nothing. */
	@GameTest(template = EMPTY16)
	public static void aFallingCubeKeepsItsBlockEntity(GameTestHelper helper) {
		BlockPos at = new BlockPos(5, 8, 5);
		helper.setBlock(at, Blocks.BARREL);
		net.minecraft.world.level.block.entity.BlockEntity entity = helper.getBlockEntity(at);
		assertTrue(helper, entity instanceof net.minecraft.world.Container, "a barrel has an inventory");
		((net.minecraft.world.Container) entity).setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
		Level level = helper.getLevel();
		StructuralIntegrityHandler.collapse(level, helper.absolutePos(at));
		helper.assertBlock(at, b -> b == Blocks.AIR, "the barrel left its cell");
		List<FallingBlockEntity> falling = helper.getEntities(EntityType.FALLING_BLOCK, at, 1.5);
		assertTrue(helper, falling.size() == 1 && falling.get(0).getBlockState().is(Blocks.BARREL), "and fell as a block");
		assertTrue(helper, falling.get(0).blockData != null && falling.get(0).blockData.contains("Items"), "carrying its contents");
		assertTrue(helper, helper.getEntities(EntityType.ITEM, at, 2.5).isEmpty(), "nothing was dropped on the spot");
		falling.get(0).discard();
		helper.succeed();
	}

	/** A whole solid cube falls; anything else breaks and drops; a cell of air is nothing. */
	@GameTest(template = EMPTY16)
	public static void aCubeFallsAndAnythingElseBreaks(GameTestHelper helper) {
		helper.setBlock(new BlockPos(5, 8, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(9, 8, 5), Blocks.OAK_SLAB);
		Level level = helper.getLevel();
		StructuralIntegrityHandler.collapse(level, helper.absolutePos(new BlockPos(5, 8, 5)));
		StructuralIntegrityHandler.collapse(level, helper.absolutePos(new BlockPos(9, 8, 5)));
		StructuralIntegrityHandler.collapse(level, helper.absolutePos(new BlockPos(12, 8, 5)));

		helper.assertBlock(new BlockPos(5, 8, 5), b -> b == Blocks.AIR, "the stone left its cell");
		helper.assertBlock(new BlockPos(9, 8, 5), b -> b == Blocks.AIR, "the slab left its cell");
		List<FallingBlockEntity> falling = helper.getEntities(EntityType.FALLING_BLOCK, new BlockPos(5, 8, 5), 1.5);
		assertTrue(helper, falling.size() == 1 && falling.get(0).getBlockState().is(Blocks.STONE), "the stone is a falling block");
		assertTrue(helper, falling.get(0).time == 1, "which has already begun to fall (fallTime = 1)");
		assertTrue(helper, helper.getEntities(EntityType.FALLING_BLOCK, new BlockPos(9, 8, 5), 1.5).isEmpty(), "the slab is not");
		List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, new BlockPos(9, 8, 5), 2.5);
		assertTrue(helper, drops.size() == 1 && drops.get(0).getItem().is(Blocks.OAK_SLAB.asItem()), "the slab dropped itself");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the switch, the queue, the budget

	private static void synthetic(GameTestHelper helper, BlockPos absolute) {
		NeoForge.EVENT_BUS.post(new BlockEvent.NeighborNotifyEvent(helper.getLevel(), absolute, Blocks.STONE.defaultBlockState(),
				EnumSet.allOf(Direction.class), false));
	}

	/**
	 * The default, and the case that matters most: with the switch off, nothing at all happens. A
	 * cantilever of thirteen, a beam that should fall, and a stone floating in the air are built;
	 * nothing is queued as they go up, and nothing falls in the ticks after.
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityOff", timeoutTicks = 100)
	public static void withTheSwitchOffNothingHappensAtAll(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.structuralIntegrity, "the switch is off by default");
		cantilever(helper, Blocks.STONE, 8, 13);
		column(helper, Blocks.STONE, 8, 12, 5, 7);
		helper.assertTrue(StructuralIntegrityListener.pending() == 0, "building it queued nothing: " + StructuralIntegrityListener.pending());

		helper.runAfterDelay(20, () -> {
			helper.assertBlock(new BlockPos(14, BEAM_Y, 8), b -> b == Blocks.STONE, "the tip of the thirteen is still there");
			for (int y = 5; y <= 7; y++)
				helper.assertBlock(new BlockPos(8, y, 12), b -> b == Blocks.STONE, "so is the floating column");
			helper.assertTrue(helper.getEntities(EntityType.FALLING_BLOCK, new BlockPos(8, 6, 8), 20).isEmpty(), "nothing fell");
			helper.assertTrue(StructuralIntegrityListener.pending() == 0, "nothing is waiting");
			helper.assertTrue(StructuralIntegrityHandler.calculations == 0, "no calculation was made: " + StructuralIntegrityHandler.calculations);
			helper.succeed();
		});
	}

	/**
	 * With the switch on, in one world, four things at once, in plots that do not touch:
	 * <ul>
	 *   <li>z = 8: a cantilever of thirteen falls at the tip, and only the tip;
	 *   <li>z = 4: a cantilever of twelve does not;
	 *   <li>z = 2: a beam of six holds until its wall's top block is broken, and then comes down
	 *       one block a tick from the wall outward - which only works if the neighbours of a
	 *       changed block are checked as well as the block;
	 *   <li>z = 12: three stones floating in the air all fall.
	 * </ul>
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityOn", timeoutTicks = 200)
	public static void withTheSwitchOnAnUnsupportedBuildingComesDown(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.structuralIntegrity, "the switch starts off");
		LeafiaFeatures.structuralIntegrity = true;
		try {
			cantilever(helper, Blocks.STONE, 8, 13);
			cantilever(helper, Blocks.STONE, 4, 12);
			cantilever(helper, Blocks.STONE, 2, 6);
			column(helper, Blocks.STONE, 8, 12, 3, 5);
			helper.assertTrue(StructuralIntegrityListener.pending() > 0, "building it queued the changes");
		} catch (Throwable t) {
			LeafiaFeatures.structuralIntegrity = false;
			StructuralIntegrityListener.clearQueue();
			throw t;
		}

		helper.runAfterDelay(8, () -> {
			try {
				helper.assertBlock(new BlockPos(6, BEAM_Y, 2), b -> b == Blocks.STONE, "the beam of six has held so far");
				helper.setBlock(new BlockPos(WALL_X, BEAM_Y, 2), Blocks.AIR);
			} catch (Throwable t) {
				LeafiaFeatures.structuralIntegrity = false;
				StructuralIntegrityListener.clearQueue();
				throw t;
			}
		});

		helper.runAfterDelay(90, () -> {
			try {
				helper.assertBlock(new BlockPos(14, BEAM_Y, 8), b -> b == Blocks.AIR, "the tip of the thirteen came down");
				for (int x = 2; x <= 13; x++)
					helper.assertBlock(new BlockPos(x, BEAM_Y, 8), b -> b == Blocks.STONE, "and nothing else of it");
				for (int x = 2; x <= 13; x++)
					helper.assertBlock(new BlockPos(x, BEAM_Y, 4), b -> b == Blocks.STONE, "the twelve holds, at x = " + x);
				for (int x = 2; x <= 7; x++)
					helper.assertBlock(new BlockPos(x, BEAM_Y, 2), b -> b == Blocks.AIR, "the beam whose wall was broken is gone, at x = " + x);
				for (int y = 3; y <= 5; y++)
					helper.assertBlock(new BlockPos(8, y, 12), b -> b == Blocks.AIR, "the floating column fell, at y = " + y);
				helper.assertTrue(StructuralIntegrityListener.pending() == 0, "and the queue is quiet");
				helper.succeed();
			} finally {
				LeafiaFeatures.structuralIntegrity = false;
				StructuralIntegrityListener.clearQueue();
			}
		});
	}

	/**
	 * The gate on the queue's far end. A change is queued with the switch on, the switch goes off
	 * before the tick, and the tick must not act on it. The tick is driven by hand so no real one
	 * can slip in between.
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityGate")
	public static void aQueuedChangeIsDroppedIfTheSwitchGoesOff(GameTestHelper helper) {
		LeafiaFeatures.structuralIntegrity = true;
		try {
			helper.setBlock(new BlockPos(5, 6, 5), Blocks.STONE);
			helper.assertTrue(StructuralIntegrityListener.pending() > 0, "the floating stone was queued");
			LeafiaFeatures.structuralIntegrity = false;
			StructuralIntegrityListener.onServerTick(null);
			helper.assertBlock(new BlockPos(5, 6, 5), b -> b == Blocks.STONE, "it was not acted on");
			helper.assertTrue(StructuralIntegrityListener.pending() == 0, "and the queue was emptied");
			helper.assertTrue(StructuralIntegrityHandler.calculations == 0, "with no calculation");
			helper.succeed();
		} finally {
			LeafiaFeatures.structuralIntegrity = false;
			StructuralIntegrityListener.clearQueue();
		}
	}

	/**
	 * 201 calculations a tick, and one per position. The floor of the template, 255 grounded
	 * blocks, is handed to the handler twice; upstream stops after the 201st because it tests
	 * {@code calculations > 200} before it adds one.
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityBudget")
	public static void aTickHasABudgetOfTwoHundredAndOneCalculations(GameTestHelper helper) {
		try {
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++)
					if (x != 0 || z != 0) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			StructuralIntegrityHandler.blockedPoses.clear();
			StructuralIntegrityHandler.calculations = 0;
			for (int pass = 0; pass < 2; pass++)
				for (int x = 0; x < 16; x++)
					for (int z = 0; z < 16; z++)
						if (x != 0 || z != 0)
							StructuralIntegrityHandler.handleBlock(helper.getLevel(), helper.absolutePos(new BlockPos(x, 0, z)));
			helper.assertTrue(StructuralIntegrityHandler.calculations == 201, "201 calculations, not " + StructuralIntegrityHandler.calculations);
			helper.assertTrue(StructuralIntegrityHandler.blockedPoses.size() == 201, "201 positions blocked, not " + StructuralIntegrityHandler.blockedPoses.size());

			StructuralIntegrityListener.onServerTick(null);
			helper.assertTrue(StructuralIntegrityHandler.calculations == 0, "the next tick begins with the budget restored");
			helper.assertTrue(StructuralIntegrityHandler.blockedPoses.isEmpty(), "and no position blocked");
			helper.succeed();
		} finally {
			StructuralIntegrityHandler.blockedPoses.clear();
			StructuralIntegrityHandler.calculations = 0;
		}
	}

	/**
	 * What the budget cannot reach in one tick waits for the next (D-3076; upstream dropped it). 255 changes are
	 * queued, a tick calculates 201 of them, and the rest are still queued; ticks go on until every one has been seen.
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityDeferred")
	public static void whatTheBudgetCannotReachWaitsForTheNextTick(GameTestHelper helper) {
		try {
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++)
					if (x != 0 || z != 0) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			LeafiaFeatures.structuralIntegrity = true;
			StructuralIntegrityListener.clearQueue();
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++)
					if (x != 0 || z != 0) synthetic(helper, helper.absolutePos(new BlockPos(x, 0, z)));
			int queued = StructuralIntegrityListener.pending();
			helper.assertTrue(queued == 255, "255 positions queued, not " + queued);
			int total = 0;
			int ticks = 0;
			while (StructuralIntegrityListener.pending() > 0 && ticks < 20) {
				StructuralIntegrityListener.onServerTick(null);
				total += StructuralIntegrityHandler.calculations;
				ticks++;
				if (ticks == 1)
					helper.assertTrue(StructuralIntegrityListener.pending() > 0, "the first tick left some waiting");
			}
			helper.assertTrue(ticks >= 2 && StructuralIntegrityListener.pending() == 0, "and they were all taken in " + ticks + " ticks");
			helper.assertTrue(total >= 255, "every floor block was calculated: " + total);
			helper.succeed();
		} finally {
			LeafiaFeatures.structuralIntegrity = false;
			StructuralIntegrityListener.clearQueue();
			StructuralIntegrityHandler.blockedPoses.clear();
			StructuralIntegrityHandler.calculations = 0;
		}
	}

	/**
	 * A storm of neighbour updates: 70,000 distinct positions notify in one tick. The queue keeps
	 * the first 65,536, and the same position notifying a thousand times is one entry.
	 */
	@GameTest(template = EMPTY16, batch = "structuralIntegrityStorm")
	public static void aStormOfNotificationsIsDeduplicatedAndCapped(GameTestHelper helper) {
		LeafiaFeatures.structuralIntegrity = true;
		try {
			BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
			for (int i = 0; i < 70_000; i++)
				synthetic(helper, origin.offset(i, 0, 0));
			helper.assertTrue(StructuralIntegrityListener.pending() == StructuralIntegrityListener.MAX_QUEUED,
					"capped at " + StructuralIntegrityListener.MAX_QUEUED + ", was " + StructuralIntegrityListener.pending());
			StructuralIntegrityListener.clearQueue();

			for (int i = 0; i < 1000; i++)
				synthetic(helper, origin);
			helper.assertTrue(StructuralIntegrityListener.pending() == 1, "one position, however often: " + StructuralIntegrityListener.pending());
			synthetic(helper, origin.above());
			helper.assertTrue(StructuralIntegrityListener.pending() == 2, "a second position is a second entry");
			helper.succeed();
		} finally {
			LeafiaFeatures.structuralIntegrity = false;
			StructuralIntegrityListener.clearQueue();
		}
	}
}
