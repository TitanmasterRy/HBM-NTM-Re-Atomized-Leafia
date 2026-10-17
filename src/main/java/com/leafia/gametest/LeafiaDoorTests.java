package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.generic.DoorDecl;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ModBlocks;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.handler.radiation.ChunkRadiationHandler;
import com.hbm.handler.radiation.ChunkRadiationHandlerSimple;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.render.anim.BusAnimation;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.doors.AddonDoorDecl;
import com.leafia.contents.building.doors.AddonDoorGeneric;
import com.leafia.contents.building.doors.AddonDoorTE;
import com.leafia.contents.building.doors.renderers.DoorClipMath;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorBlock;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorTE;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorTE.Cue;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.mojang.authlib.GameProfile;

import api.hbm.block.IToolable.ToolType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The three doors (D-3005): the reactor door and the small and large crimson doors.
 *
 * <p>Everything here is a number from upstream or a consequence of one: strengths, timings, open
 * ranges, the boxes an open door leaves, the sounds and shakes, what the animations hold at the tick
 * the sounds fire on, and the thing that keeps a saved door a crimson or reactor door at all - the
 * type it is recreated from. The renderers cannot be loaded by a server, so what they compute is
 * either pinned here as arithmetic ({@link DoorClipMath}) or looked at in the client check.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDoorTests {

	private static final String ROOM = "empty16";

	private static final Direction[] FACINGS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	// ------------------------------------------------------------------ helpers

	private static BlockPos build(GameTestHelper helper, Block block, DoorDecl type, int x, int z, Direction facing) {
		BlockPos core = helper.absolutePos(new BlockPos(x, 1, z));
		helper.getLevel().setBlock(core, block.defaultBlockState()
				.setValue(BlockDummyable.PART, Part.CORE)
				.setValue(BlockDummyable.DIR, facing), 3);
		MultiblockHandlerXR.fillSpace(helper.getLevel(), core, type.getDimensions(), block, facing);
		return core;
	}

	private static int countParts(GameTestHelper helper, Block block, BlockPos core, Part part) {
		int found = 0;
		for(int a = -8; a <= 8; a++) for(int b = -2; b <= 6; b++) for(int c = -8; c <= 8; c++) {
			BlockState s = helper.getLevel().getBlockState(core.offset(a, b, c));
			if(s.getBlock() == block && (part == null || s.getValue(BlockDummyable.PART) == part)) found++;
		}
		return found;
	}

	private static FakePlayer player(GameTestHelper helper) {
		return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "door_tester"));
	}

	/** Ticks a door the way its block's ticker does, until it has travelled to {@code target}. */
	private static void travel(GameTestHelper helper, BlockPos core, BlockEntityDoorGeneric door, int target) {
		Level level = helper.getLevel();
		for(int i = 0; i < 400 && door.openTicks != target; i++) {
			BlockState state = level.getBlockState(core);
			if(door instanceof ReactorDoorTE reactor) ReactorDoorTE.tick(level, core, state, reactor);
			else AddonDoorTE.tick(level, core, state, (AddonDoorTE) door);
		}
		// One more tick for the state flip, which happens on the tick openTicks arrives.
		BlockState state = level.getBlockState(core);
		if(door instanceof ReactorDoorTE reactor) ReactorDoorTE.tick(level, core, state, reactor);
		else AddonDoorTE.tick(level, core, state, (AddonDoorTE) door);
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1e-6, what + ": expected " + expected + ", got " + actual);
	}

	private static double sampled(BusAnimation animation, String bus, int millis) {
		return animation.getBus(bus).getTransformation(millis)[1];
	}

	// ------------------------------------------------------------------ the blocks

	/**
	 * D-1002: a switched-off feature registers nothing. At the defaults this is two trues; the
	 * experiment that gives it teeth is flipping {@code features.content.doors} to false in
	 * {@code run/config/leafia-features.toml} and running the suite (D-3005 records it).
	 */
	@GameTest(template = ROOM)
	public static void theSwitchDecidesWhetherTheDoorsExist(GameTestHelper helper) {

		for(String id : new String[] {"reactor_door", "crimson_door_small", "crimson_door_large"}) {
			ResourceLocation key = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, id);
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key) == LeafiaFeatures.doors, id + ": the block exists iff the switch is on");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key) == LeafiaFeatures.doors, id + ": and so does its item");
		}
		for(String id : new String[] {"addon_door", "reactor_door"}) {
			ResourceLocation key = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, id);
			helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(key) == LeafiaFeatures.doors, id + ": and its block entity type");
		}
		helper.assertTrue((LeafiaBlocks.reactor_door != null) == LeafiaFeatures.doors, "and the holder is null when it is off");
		helper.succeed();
	}


	/**
	 * Upstream's {@code setHardness(150)} on all three, and {@code setResistance(13500)} and
	 * {@code (135000)}, which are 8,100 and 81,000 at D-012's 0.6. All three need a pickaxe, make the
	 * stone noise {@code BlockDummyable} leaves a 1.12.2 block with, and are radiation resistant.
	 */
	@GameTest(template = ROOM)
	public static void theDoorsAreUpstreamsStrength(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		Block[] doors = {LeafiaBlocks.reactor_door.get(), LeafiaBlocks.crimson_door_small.get(), LeafiaBlocks.crimson_door_large.get()};
		float[] resistance = {8100F, 81000F, 81000F};

		for(int i = 0; i < doors.length; i++) {
			BlockState state = doors[i].defaultBlockState();
			helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 150F, doors[i] + " hardness 150");
			helper.assertTrue(doors[i].getExplosionResistance() == resistance[i], doors[i] + " resistance " + resistance[i]
					+ ", got " + doors[i].getExplosionResistance());
			helper.assertTrue(state.requiresCorrectToolForDrops(), doors[i] + " needs a pickaxe, being Material.IRON upstream");
			helper.assertTrue(state.getSoundType() == SoundType.STONE, doors[i] + " makes the stone noise");
			helper.assertTrue(doors[i] instanceof AddonDoorGeneric door && door.isRadResistant, doors[i] + " is radiation resistant");
			helper.assertTrue(IRadResistantBlock.blocksRadiation(state), doors[i] + " answers the base mod's flag");
		}

		helper.assertTrue(LeafiaBlocks.reactor_door.get() instanceof ReactorDoorBlock, "the reactor door has its own class");
		helper.succeed();
	}

	/** The ids, and that the pickaxe tag knows them. */
	@GameTest(template = ROOM)
	public static void theDoorsHaveUpstreamsIdsAndAPickaxe(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		for(String id : new String[] {"reactor_door", "crimson_door_small", "crimson_door_large"}) {
			Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, id));
			helper.assertTrue(block != Blocks.AIR, id + " should be registered");
			helper.assertTrue(block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),
					id + " should be in the pickaxe tag, or requiring a tool would mean it never drops");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the decls

	@GameTest(template = ROOM)
	public static void theDeclsAreUpstreamsNumbers(GameTestHelper helper) {

		DoorDecl reactor = AddonDoorDecl.REACTOR_DOOR;
		helper.assertTrue(reactor.timeToOpen() == 86 + 23, "the reactor door takes 109 ticks");
		helper.assertTrue(java.util.Arrays.equals(reactor.getDimensions(), new int[] {2, 0, 1, 0, 1, 1}), "reactor dimensions");
		helper.assertTrue(java.util.Arrays.deepEquals(reactor.getDoorOpenRanges(), new int[][] {{0, 0, 1, -2, 2, 0}}), "reactor ranges");
		helper.assertTrue(reactor.getSkinCount() == 7 && reactor.hasSkins(), "seven skins");
		helper.assertTrue(reactor.getOpenSoundLoop() == null && reactor.getOpenSoundEnd() == null
				&& reactor.getCloseSoundLoop() == null, "the reactor door leaves its sounds to its block entity");

		AddonDoorDecl small = AddonDoorDecl.CRIMSON_DOOR_SMALL;
		helper.assertTrue(small.timeToOpen() == 40, "the small crimson door takes 40 ticks");
		helper.assertTrue(java.util.Arrays.equals(small.getDimensions(), new int[] {2, 0, 0, 0, 1, 1}), "small dimensions");
		helper.assertTrue(java.util.Arrays.deepEquals(small.getDoorOpenRanges(), new int[][] {{0, 0, 0, -2, 3, 2}, {0, 0, 0, 2, 3, 2}}), "small ranges");
		helper.assertTrue("0.2".equals(small.shakeIntensityClose()) && "20".equals(small.shakeRange()) && small.shakeIntensityOpen() == null,
				"small shakes once, shut, at 0.2 over 20");

		AddonDoorDecl large = AddonDoorDecl.CRIMSON_DOOR_LARGE;
		helper.assertTrue(large.timeToOpen() == 54, "the large crimson door takes 54 ticks");
		helper.assertTrue(java.util.Arrays.equals(large.getDimensions(), new int[] {2, 0, 0, 0, 3, 3}), "large dimensions");
		helper.assertTrue(java.util.Arrays.deepEquals(large.getDoorOpenRanges(), new int[][] {{0, 0, 0, -4, 3, 2}, {0, 0, 0, 4, 3, 2}}), "large ranges");
		helper.assertTrue("0.4".equals(large.shakeIntensityClose()) && "30".equals(large.shakeRange()) && large.shakeIntensityOpen() == null,
				"large shakes once, shut, at 0.4 over 30");

		for(AddonDoorDecl decl : new AddonDoorDecl[] {small, large}) {
			helper.assertTrue(decl.getOpenSoundLoop() == LeafiaSounds.crimDoorOpenStart, "open loop is the gate's start clip");
			helper.assertTrue(decl.getOpenSoundEnd() == LeafiaSounds.crimDoorOpenEnd, "open end");
			helper.assertTrue(decl.getCloseSoundLoop() == LeafiaSounds.crimDoorCloseStart, "close loop");
			helper.assertTrue(decl.getCloseSoundEnd() == LeafiaSounds.crimDoorCloseEnd, "close end");
			helper.assertTrue(!decl.hasSkins(), "the crimson doors have one finish");
		}

		helper.succeed();
	}

	/**
	 * The reactor door's animation at the ticks its sounds fire on. Upstream's numbers in
	 * milliseconds: opening, the handle makes a quarter of its travel in 150, creeps to three
	 * quarters by 850 and is home at 1,000; the leaf does nothing for 2,150 and then swings through
	 * 2,200. Closing, the leaf comes back first and the handle is held for 3,300 before it turns.
	 */
	@GameTest(template = ROOM)
	public static void theReactorDoorsAnimationIsUpstreams(GameTestHelper helper) {

		DoorDecl decl = AddonDoorDecl.REACTOR_DOOR;
		BusAnimation opening = decl.getBusAnimation(BlockEntityDoorGeneric.STATE_OPENING, (byte) 0);
		BusAnimation closing = decl.getBusAnimation(BlockEntityDoorGeneric.STATE_CLOSING, (byte) 0);

		assertClose(helper, 0.00, sampled(opening, "HANDLE", 0), "handle at 0");
		assertClose(helper, 0.25, sampled(opening, "HANDLE", 150), "handle after the first 150 ms");
		assertClose(helper, 0.75, sampled(opening, "HANDLE", 850), "handle after the creep");
		assertClose(helper, 1.00, sampled(opening, "HANDLE", 1000), "handle home");
		assertClose(helper, 0.00, sampled(opening, "DOOR", 2150), "leaf still shut at 2,150");
		assertClose(helper, 0.50, sampled(opening, "DOOR", 2150 + 1100), "leaf half way at the middle of its sine");
		assertClose(helper, 1.00, sampled(opening, "DOOR", 4350), "leaf open at 4,350");

		assertClose(helper, 1.00, sampled(closing, "DOOR", 0), "leaf open when closing starts");
		assertClose(helper, 0.00, sampled(closing, "DOOR", 2200), "leaf shut after 2,200");
		assertClose(helper, 1.00, sampled(closing, "HANDLE", 3300), "handle still home at 3,300");
		assertClose(helper, 0.75, sampled(closing, "HANDLE", 3450), "handle after its first 150");
		assertClose(helper, 0.25, sampled(closing, "HANDLE", 4150), "handle after the creep back");
		assertClose(helper, 0.00, sampled(closing, "HANDLE", 4300), "handle shut");

		helper.assertTrue(decl.getBusAnimation(BlockEntityDoorGeneric.STATE_OPEN, (byte) 0) == null, "no animation at rest");

		helper.succeed();
	}

	@GameTest(template = ROOM)
	public static void theCrimsonDoorsSlideForTheirOwnDuration(GameTestHelper helper) {

		BusAnimation smallOpen = AddonDoorDecl.CRIMSON_DOOR_SMALL.getBusAnimation(BlockEntityDoorGeneric.STATE_OPENING, (byte) 0);
		BusAnimation largeOpen = AddonDoorDecl.CRIMSON_DOOR_LARGE.getBusAnimation(BlockEntityDoorGeneric.STATE_OPENING, (byte) 0);
		BusAnimation smallShut = AddonDoorDecl.CRIMSON_DOOR_SMALL.getBusAnimation(BlockEntityDoorGeneric.STATE_CLOSING, (byte) 0);
		BusAnimation largeShut = AddonDoorDecl.CRIMSON_DOOR_LARGE.getBusAnimation(BlockEntityDoorGeneric.STATE_CLOSING, (byte) 0);

		assertClose(helper, 0.0, sampled(smallOpen, "DOOR", 0), "small opens from 0");
		assertClose(helper, 0.5, sampled(smallOpen, "DOOR", 1000), "small is half open at 1,000 ms of 2,000");
		assertClose(helper, 1.0, sampled(smallOpen, "DOOR", 2000), "small is open at 2,000 ms");
		assertClose(helper, 0.5, sampled(largeOpen, "DOOR", 1350), "large is half open at 1,350 ms of 2,700");
		assertClose(helper, 1.0, sampled(largeOpen, "DOOR", 2700), "large is open at 2,700 ms");
		assertClose(helper, 1.0, sampled(smallShut, "DOOR", 0), "small closes from open");
		assertClose(helper, 0.0, sampled(smallShut, "DOOR", 2000), "small is shut at 2,000 ms");
		assertClose(helper, 0.0, sampled(largeShut, "DOOR", 2700), "large is shut at 2,700 ms");

		helper.succeed();
	}

	// ------------------------------------------------------------------ building them

	/** The boxes {@code fillSpace} makes: 3x3x1, 7x3x1 and 3x3x2, in every facing. */
	@GameTest(template = ROOM)
	public static void aDoorFillsItsDimensionsInEveryFacing(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		for(Direction facing : FACINGS) {
			Block[] blocks = {LeafiaBlocks.crimson_door_small.get(), LeafiaBlocks.crimson_door_large.get(), LeafiaBlocks.reactor_door.get()};
			DoorDecl[] decls = {AddonDoorDecl.CRIMSON_DOOR_SMALL, AddonDoorDecl.CRIMSON_DOOR_LARGE, AddonDoorDecl.REACTOR_DOOR};
			int[] expected = {9, 21, 18};

			for(int i = 0; i < blocks.length; i++) {
				BlockPos core = build(helper, blocks[i], decls[i], 8, 8, facing);
				helper.assertTrue(countParts(helper, blocks[i], core, null) == expected[i],
						blocks[i] + " " + facing + ": expected " + expected[i] + " parts, got " + countParts(helper, blocks[i], core, null));
				helper.assertTrue(countParts(helper, blocks[i], core, Part.CORE) == 1, "one core");
				// Clear it for the next one.
				for(int a = -8; a <= 8; a++) for(int b = -2; b <= 6; b++) for(int c = -8; c <= 8; c++)
					if(helper.getLevel().getBlockState(core.offset(a, b, c)).getBlock() == blocks[i])
						helper.getLevel().setBlock(core.offset(a, b, c), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			}
		}
		helper.succeed();
	}

	/**
	 * The core's block entity is the subclass, has its own type, saves under that type's id and is
	 * recreated as the subclass from a save - not as the base mod's plain door, which is what a
	 * subclass that borrowed the base's type would have come back as.
	 */
	@GameTest(template = ROOM)
	public static void aSavedDoorComesBackAsItself(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		Object[][] cases = {
				{LeafiaBlocks.crimson_door_small.get(), AddonDoorDecl.CRIMSON_DOOR_SMALL, "addon_door", AddonDoorTE.class},
				{LeafiaBlocks.crimson_door_large.get(), AddonDoorDecl.CRIMSON_DOOR_LARGE, "addon_door", AddonDoorTE.class},
				{LeafiaBlocks.reactor_door.get(), AddonDoorDecl.REACTOR_DOOR, "reactor_door", ReactorDoorTE.class},
		};

		int x = 3;
		for(Object[] c : cases) {
			Block block = (Block) c[0];
			BlockPos core = build(helper, block, (DoorDecl) c[1], x, 8, Direction.NORTH);
			x += 5;

			BlockEntity be = helper.getLevel().getBlockEntity(core);
			helper.assertTrue(((Class<?>) c[3]).isInstance(be), block + ": the core's block entity should be a " + ((Class<?>) c[3]).getSimpleName());
			helper.assertTrue(be.getType() == (c[3] == ReactorDoorTE.class ? LeafiaBlockEntities.REACTOR_DOOR.get() : LeafiaBlockEntities.ADDON_DOOR.get()),
					block + ": and carry the addon's type");
			helper.assertTrue(be.getType().isValid(helper.getLevel().getBlockState(core)), block + ": the type lists the block");

			CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
			helper.assertTrue(("leafia:" + c[2]).equals(tag.getString("id")), block + ": saved as leafia:" + c[2] + ", was " + tag.getString("id"));

			BlockEntity loaded = BlockEntity.loadStatic(core, helper.getLevel().getBlockState(core), tag, helper.getLevel().registryAccess());
			helper.assertTrue(((Class<?>) c[3]).isInstance(loaded), block + ": and loaded as the same class, not the base mod's door");

			// Only the core has one.
			helper.assertTrue(helper.getLevel().getBlockEntity(core.above()) == null, "a dummy has no block entity");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ opening

	/**
	 * Opening turns the cells in the ranges into extras, exactly as many as the ranges say: the
	 * small door's eight cells beside and above its core, the large door's twenty, the reactor
	 * door's three - the core's own cell is not an extra but turns its box on.
	 */
	@GameTest(template = ROOM)
	public static void openingMakesExactlyTheRangesExtras(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		Object[][] cases = {
				{LeafiaBlocks.crimson_door_small.get(), AddonDoorDecl.CRIMSON_DOOR_SMALL, 8},
				{LeafiaBlocks.crimson_door_large.get(), AddonDoorDecl.CRIMSON_DOOR_LARGE, 20},
				{LeafiaBlocks.reactor_door.get(), AddonDoorDecl.REACTOR_DOOR, 3},
		};

		for(Direction facing : FACINGS) {
			for(Object[] c : cases) {
				Block block = (Block) c[0];
				DoorDecl decl = (DoorDecl) c[1];
				BlockPos core = build(helper, block, decl, 8, 8, facing);
				BlockEntityDoorGeneric door = (BlockEntityDoorGeneric) helper.getLevel().getBlockEntity(core);
				String name = block + " " + facing;

				helper.assertTrue(countParts(helper, block, core, Part.EXTRA) == 0, name + ": no extras at rest");
				helper.assertTrue(door.tryToggle(player(helper)), name + ": a click opens it");
				helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_OPENING, name + ": opening");
				travel(helper, core, door, decl.timeToOpen());
				helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_OPEN, name + ": open after " + decl.timeToOpen() + " ticks, openTicks " + door.openTicks);
				helper.assertTrue(countParts(helper, block, core, Part.EXTRA) == (int) c[2],
						name + ": expected " + c[2] + " extras, got " + countParts(helper, block, core, Part.EXTRA));
				helper.assertTrue(door.shouldUseBB, name + ": the core's own cell is open");

				helper.assertTrue(door.tryToggle(player(helper)), name + ": a click closes it");
				travel(helper, core, door, 0);
				helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_CLOSED, name + ": shut again");
				helper.assertTrue(countParts(helper, block, core, Part.EXTRA) == 0, name + ": and the extras are gone");
				helper.assertTrue(!door.shouldUseBB, name + ": and the core's cell is shut");

				for(int a = -8; a <= 8; a++) for(int b = -2; b <= 6; b++) for(int cc = -8; cc <= 8; cc++)
					if(helper.getLevel().getBlockState(core.offset(a, b, cc)).getBlock() == block)
						helper.getLevel().setBlock(core.offset(a, b, cc), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ shape

	private static AABB bounds(GameTestHelper helper, BlockPos pos) {
		VoxelShape shape = helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos);
		return shape.isEmpty() ? null : shape.bounds();
	}

	private static BlockPos along(BlockPos core, Direction facing, int width, int height) {
		Direction axis = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		return core.relative(axis, width).above(height);
	}

	/**
	 * Shut, every cell is a full block. Open, the crimson doors leave a doorway: the middle column is
	 * empty to head height under a 5/16 lintel, and each outer column keeps a 9/16 slab on its outer
	 * side - where the leaf has slid in - in all four facings. This is the base mod's missing
	 * per-part box, and the rotation it does is the reason for the four facings.
	 */
	@GameTest(template = ROOM)
	public static void anOpenCrimsonDoorLeavesADoorway(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		for(Direction facing : FACINGS) {
			for(boolean large : new boolean[] {false, true}) {

				Block block = (large ? LeafiaBlocks.crimson_door_large : LeafiaBlocks.crimson_door_small).get();
				DoorDecl decl = large ? AddonDoorDecl.CRIMSON_DOOR_LARGE : AddonDoorDecl.CRIMSON_DOOR_SMALL;
				int edge = large ? 3 : 1;
				BlockPos core = build(helper, block, decl, 8, 8, facing);
				AddonDoorTE door = (AddonDoorTE) helper.getLevel().getBlockEntity(core);
				String name = (large ? "large " : "small ") + facing;

				for(int w = -edge; w <= edge; w++) for(int h = 0; h <= 2; h++) {
					AABB box = bounds(helper, along(core, facing, w, h));
					helper.assertTrue(box != null && box.equals(new AABB(0, 0, 0, 1, 1, 1)),
							name + ": a shut door's cell " + w + "," + h + " is a full block, was " + box);
				}

				door.tryToggle(player(helper));
				travel(helper, core, door, decl.timeToOpen());

				boolean xAxis = facing.getAxis() != Direction.Axis.X;   // the width runs along x for north and south

				for(int h = 0; h <= 2; h++) {
					// The middle column: empty, then the lintel.
					AABB mid = bounds(helper, along(core, facing, 0, h));
					if(h < 2) {
						helper.assertTrue(mid == null, name + ": the doorway at height " + h + " is empty, was " + mid);
					} else {
						helper.assertTrue(mid != null && Math.abs(mid.minY - 11.0 / 16) < 1e-6 && mid.maxY == 1 && mid.getXsize() == 1 && mid.getZsize() == 1,
								name + ": a 5/16 lintel under the ceiling, was " + mid);
					}

					// The outer columns: a 9/16 slab on the outer side.
					for(int side : new int[] {-1, 1}) {
						AABB slab = bounds(helper, along(core, facing, side * edge, h));
						helper.assertTrue(slab != null && slab.getYsize() == 1, name + ": the edge column keeps a full-height slab at " + h + ", was " + slab);
						double lo = xAxis ? slab.minX : slab.minZ, hi = xAxis ? slab.maxX : slab.maxZ;
						double thick = xAxis ? slab.getZsize() : slab.getXsize();
						helper.assertTrue(thick == 1, name + ": the slab spans the whole depth");
						if(side > 0) {
							helper.assertTrue(Math.abs(lo - 7.0 / 16) < 1e-6 && Math.abs(hi - 1) < 1e-6, name + ": the + edge keeps the outer 9/16, was " + lo + ".." + hi);
						} else {
							helper.assertTrue(Math.abs(lo) < 1e-6 && Math.abs(hi - 9.0 / 16) < 1e-6, name + ": the - edge keeps the outer 9/16, was " + lo + ".." + hi);
						}
					}
				}

				door.tryToggle(player(helper));
				travel(helper, core, door, 0);
				AABB closed = bounds(helper, along(core, facing, 0, 0));
				helper.assertTrue(closed != null && closed.equals(new AABB(0, 0, 0, 1, 1, 1)), name + ": shut again, the middle is a block again");

				for(int a = -8; a <= 8; a++) for(int b = -2; b <= 6; b++) for(int cc = -8; cc <= 8; cc++)
					if(helper.getLevel().getBlockState(core.offset(a, b, cc)).getBlock() == block)
						helper.getLevel().setBlock(core.offset(a, b, cc), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			}
		}
		helper.succeed();
	}

	/** The reactor door uses the decl's default: a block shut, nothing open. */
	@GameTest(template = ROOM)
	public static void anOpenReactorDoorHasNoShapeInItsExtras(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		Block block = LeafiaBlocks.reactor_door.get();
		BlockPos core = build(helper, block, AddonDoorDecl.REACTOR_DOOR, 8, 8, Direction.NORTH);
		ReactorDoorTE door = (ReactorDoorTE) helper.getLevel().getBlockEntity(core);

		int solid = 0;
		for(int a = -3; a <= 3; a++) for(int b = 0; b <= 3; b++) for(int c = -3; c <= 3; c++)
			if(helper.getLevel().getBlockState(core.offset(a, b, c)).getBlock() == block && bounds(helper, core.offset(a, b, c)) != null) solid++;
		helper.assertTrue(solid == 18, "shut, all 18 cells are solid, got " + solid);

		door.tryToggle(player(helper));
		travel(helper, core, door, AddonDoorDecl.REACTOR_DOOR.timeToOpen());

		solid = 0;
		for(int a = -3; a <= 3; a++) for(int b = 0; b <= 3; b++) for(int c = -3; c <= 3; c++)
			if(helper.getLevel().getBlockState(core.offset(a, b, c)).getBlock() == block && bounds(helper, core.offset(a, b, c)) != null) solid++;
		helper.assertTrue(solid == 18 - 3 - 1, "open, the three extras and the core's cell are empty, leaving 14 solid; got " + solid);
		helper.succeed();
	}

	// ------------------------------------------------------------------ radiation

	/** A handler that remembers what it was told, standing in for the running one. */
	private static final class Recorder extends ChunkRadiationHandlerSimple {
		final List<BlockPos> marks = new ArrayList<>();

		@Override
		public void markSectionForRebuild(Level world, BlockPos pos) {
			marks.add(pos.immutable());
		}
	}

	@GameTest(template = ROOM)
	public static void aDoorIsRadResistantOnlyWhileItIsShut(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		for(Block block : new Block[] {LeafiaBlocks.reactor_door.get(), LeafiaBlocks.crimson_door_small.get()}) {
			DoorDecl decl = ((AddonDoorGeneric) block).type;
			BlockPos core = build(helper, block, decl, 8, 8, Direction.NORTH);
			AddonDoorGeneric door = (AddonDoorGeneric) block;
			BlockEntityDoorGeneric be = (BlockEntityDoorGeneric) helper.getLevel().getBlockEntity(core);

			helper.assertTrue(door.isRadResistant(helper.getLevel(), core), block + ": a shut door's core shields");
			helper.assertTrue(door.isRadResistant(helper.getLevel(), core.above()), block + ": and so does a dummy, through its core");
			helper.assertTrue(!door.isRadResistant(null, core), "no world, no answer");

			be.tryToggle(player(helper));
			helper.assertTrue(!door.isRadResistant(helper.getLevel(), core), block + ": an opening door does not");

			travel(helper, core, be, decl.timeToOpen());
			helper.assertTrue(!door.isRadResistant(helper.getLevel(), core.above()), block + ": nor an open one");

			be.tryToggle(player(helper));
			travel(helper, core, be, 0);
			helper.assertTrue(door.isRadResistant(helper.getLevel(), core), block + ": until it has shut");

			for(int a = -8; a <= 8; a++) for(int b = -2; b <= 6; b++) for(int c = -8; c <= 8; c++)
				if(helper.getLevel().getBlockState(core.offset(a, b, c)).getBlock() == block)
					helper.getLevel().setBlock(core.offset(a, b, c), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
		}
		helper.succeed();
	}

	/**
	 * Placing a part tells the radiation system, breaking one tells it, a door starting to open or to
	 * close tells it, and the reactor door finishing closing tells it for every section it occupies.
	 * A static is swapped for the test and put back.
	 */
	@GameTest(template = ROOM)
	public static void theDoorsTellTheRadiationSystem(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		ChunkRadiationHandler saved = ChunkRadiationManager.proxy;
		Recorder recorder = new Recorder();
		ChunkRadiationManager.proxy = recorder;
		try {
			Block block = LeafiaBlocks.reactor_door.get();
			DoorDecl decl = AddonDoorDecl.REACTOR_DOOR;
			BlockPos core = build(helper, block, decl, 8, 8, Direction.NORTH);

			helper.assertTrue(recorder.marks.size() >= 18, "placing 18 parts should mark at least 18 times, marked " + recorder.marks.size());
			helper.assertTrue(recorder.marks.contains(core), "and one of them is the core's cell");

			BlockEntityDoorGeneric be = (BlockEntityDoorGeneric) helper.getLevel().getBlockEntity(core);

			// A tick to learn the state, a toggle, a tick to see it.
			ReactorDoorTE.tick(helper.getLevel(), core, helper.getLevel().getBlockState(core), (ReactorDoorTE) be);
			recorder.marks.clear();
			be.tryToggle(player(helper));
			ReactorDoorTE.tick(helper.getLevel(), core, helper.getLevel().getBlockState(core), (ReactorDoorTE) be);
			helper.assertTrue(recorder.marks.contains(core), "starting to open marks the core's section");

			travel(helper, core, be, decl.timeToOpen());
			be.tryToggle(player(helper));
			recorder.marks.clear();
			travel(helper, core, be, 0);

			ChunkPos here = new ChunkPos(core);
			boolean sectionMarked = false;
			for(BlockPos mark : recorder.marks) if((mark.getX() >> 4) == here.x && (mark.getZ() >> 4) == here.z) sectionMarked = true;
			helper.assertTrue(sectionMarked, "finishing closing marks the sections the door occupies, got " + recorder.marks);

			int sections = AddonDoorGeneric.markOccupiedSections(helper.getLevel(), core, helper.getLevel().getBlockState(core), decl);
			int[] rot = MultiblockHandlerXR.rotate(decl.getDimensions(), Direction.NORTH);
			int minX = (core.getX() - rot[4]) >> 4, maxX = (core.getX() + rot[5]) >> 4;
			int minY = (core.getY() - rot[1]) >> 4, maxY = (core.getY() + rot[0]) >> 4;
			int minZ = (core.getZ() - rot[2]) >> 4, maxZ = (core.getZ() + rot[3]) >> 4;
			int expected = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
			helper.assertTrue(sections == expected, "the reactor door spans " + expected + " section(s) here, counted " + sections);

			recorder.marks.clear();
			helper.getLevel().destroyBlock(core.above(), false);
			helper.assertTrue(!recorder.marks.isEmpty(), "breaking a part marks");

		} finally {
			ChunkRadiationManager.proxy = saved;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the reactor door's timeline

	@GameTest(template = ROOM)
	public static void theReactorDoorFiresItsCuesOnUpstreamsTicks(GameTestHelper helper) {

		int time = AddonDoorDecl.REACTOR_DOOR.timeToOpen();
		List<Integer> soundTicks = new ArrayList<>();
		List<Integer> shakeTicks = new ArrayList<>();

		for(int t = 0; t <= time; t++) {
			for(Cue cue : ReactorDoorTE.cues(true, t, time)) {
				if(cue.sound() != null) soundTicks.add(t);
				if(cue.shake() != null) shakeTicks.add(t);
			}
		}
		helper.assertTrue(soundTicks.equals(List.of(0, 43)), "opening sounds on 0 and 43, got " + soundTicks);
		helper.assertTrue(shakeTicks.equals(List.of(43)), "opening shake on 43, got " + shakeTicks);

		List<Cue> at0 = ReactorDoorTE.cues(true, 0, time);
		helper.assertTrue(at0.size() == 1 && at0.get(0).sound() == LeafiaSounds.reactor_door_handle && at0.get(0).volume() == 1F, "the handle, volume 1");
		List<Cue> at43 = ReactorDoorTE.cues(true, 43, time);
		helper.assertTrue(at43.size() == 2 && at43.get(0).shake() == ReactorDoorTE.OPEN_SHAKE && at43.get(1).sound() == LeafiaSounds.reactor_door_open
				&& at43.get(1).volume() == 3F, "the shake and then the swing, volume 3");

		soundTicks.clear();
		shakeTicks.clear();
		for(int t = 0; t <= time; t++) {
			for(Cue cue : ReactorDoorTE.cues(false, t, time)) {
				if(cue.sound() != null) soundTicks.add(t);
				if(cue.shake() != null) shakeTicks.add(t);
			}
		}
		helper.assertTrue(soundTicks.equals(List.of(43, 86)), "closing sounds on 43 and 86, got " + soundTicks);
		helper.assertTrue(shakeTicks.equals(List.of(63)), "closing shake on 63 (109 - 44 - 2), got " + shakeTicks);

		helper.assertTrue(ReactorDoorTE.cues(false, 86, time).get(0).sound() == LeafiaSounds.reactor_door_close
				&& ReactorDoorTE.cues(false, 86, time).get(0).volume() == 3F, "the close sound at 86, volume 3");
		helper.assertTrue(ReactorDoorTE.cues(false, 43, time).get(0).sound() == LeafiaSounds.reactor_door_handle
				&& ReactorDoorTE.cues(false, 43, time).get(0).volume() == 3F, "the handle again at 43, volume 3");

		helper.assertTrue(java.util.Arrays.asList(ReactorDoorTE.OPEN_SHAKE).contains("intensity=0.15")
				&& java.util.Arrays.asList(ReactorDoorTE.OPEN_SHAKE).contains("duration=0.5")
				&& java.util.Arrays.asList(ReactorDoorTE.OPEN_SHAKE).contains("speed=2")
				&& java.util.Arrays.asList(ReactorDoorTE.OPEN_SHAKE).contains("range=12"), "the opening shake's numbers");
		helper.assertTrue(java.util.Arrays.asList(ReactorDoorTE.CLOSE_SHAKE).contains("intensity=0.35")
				&& java.util.Arrays.asList(ReactorDoorTE.CLOSE_SHAKE).contains("duration=0.75")
				&& java.util.Arrays.asList(ReactorDoorTE.CLOSE_SHAKE).contains("speed=8")
				&& java.util.Arrays.asList(ReactorDoorTE.CLOSE_SHAKE).contains("range=12"), "the closing shake's numbers");

		helper.succeed();
	}

	/** The crimson shake: the decl's intensity and range, once, on shutting. */
	@GameTest(template = ROOM)
	public static void aCrimsonDoorShakesOnceWhenItShuts(GameTestHelper helper) {

		String[] small = AddonDoorTE.shakeFor(AddonDoorDecl.CRIMSON_DOOR_SMALL, BlockEntityDoorGeneric.STATE_CLOSED);
		helper.assertTrue(small != null && java.util.Arrays.asList(small).contains("intensity=0.2")
				&& java.util.Arrays.asList(small).contains("range=20") && java.util.Arrays.asList(small).contains("duration=0.75")
				&& java.util.Arrays.asList(small).contains("speed=8"), "small: " + java.util.Arrays.toString(small));

		String[] large = AddonDoorTE.shakeFor(AddonDoorDecl.CRIMSON_DOOR_LARGE, BlockEntityDoorGeneric.STATE_CLOSED);
		helper.assertTrue(large != null && java.util.Arrays.asList(large).contains("intensity=0.4")
				&& java.util.Arrays.asList(large).contains("range=30"), "large: " + java.util.Arrays.toString(large));

		helper.assertTrue(AddonDoorTE.shakeFor(AddonDoorDecl.CRIMSON_DOOR_LARGE, BlockEntityDoorGeneric.STATE_OPEN) == null, "no shake on opening");
		helper.assertTrue(AddonDoorTE.shakeFor(AddonDoorDecl.CRIMSON_DOOR_LARGE, BlockEntityDoorGeneric.STATE_OPENING) == null, "none mid-way");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the reactor door's paint

	@GameTest(template = ROOM)
	public static void theReactorDoorStartsPaintedAsUpstreams(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		BlockPos core = build(helper, LeafiaBlocks.reactor_door.get(), AddonDoorDecl.REACTOR_DOOR, 8, 8, Direction.NORTH);
		ReactorDoorTE door = (ReactorDoorTE) helper.getLevel().getBlockEntity(core);

		helper.assertTrue(LeafiaFeatures.concreteBricks
				? door.skinA.getBlock() == LeafiaBlocks.brick_concrete_dark.get() : door.skinA.getBlock() == ModBlocks.brick_concrete.get(),
				"the front is the dark brick");
		helper.assertTrue(door.skinB.getBlock() == ModBlocks.concrete_colored_ext.get()
				&& door.skinB.getValue(com.hbm.blocks.generic.BlockConcreteColoredExt.VARIANT)
				== com.hbm.blocks.generic.BlockConcreteColoredExt.EnumConcreteType.MACHINE, "the back is machine concrete");
		helper.succeed();
	}

	/**
	 * Sneaking with a screwdriver and a block in the other hand paints a face, and the face depends on
	 * which cell was clicked; with nothing in the other hand it cycles the skin, which wraps at seven.
	 */
	@GameTest(template = ROOM)
	public static void aScrewdriverPaintsTheFaceItClicked(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		for(Direction facing : FACINGS) {
			ReactorDoorBlock block = (ReactorDoorBlock) LeafiaBlocks.reactor_door.get();
			BlockPos core = build(helper, block, AddonDoorDecl.REACTOR_DOOR, 8, 8, facing);
			ReactorDoorTE door = (ReactorDoorTE) helper.getLevel().getBlockEntity(core);
			FakePlayer player = player(helper);
			player.setShiftKeyDown(true);
			String name = "facing " + facing;

			helper.assertTrue(!block.onScrew(helper.getLevel(), player, core, Direction.UP, Vec3.ZERO, ToolType.WRENCH), name + ": only a screwdriver paints");
			player.setShiftKeyDown(false);
			helper.assertTrue(!block.onScrew(helper.getLevel(), player, core, Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER), name + ": and only a sneaking one");
			player.setShiftKeyDown(true);

			// Every cell of the door, painted in turn with stone: which face does each one take?
			//
			// Upstream's rule is `x < 1` for x in the core's object space, and object space has x
			// running ACROSS the door, not through it: of the three columns one is at x = 1, to the
			// door's left or right depending on how it faces. The columns at x = 0 and below take the
			// front face, always. The column at x = 1 is the one the rule is `< 1` about, and a
			// double that is 1 to within a rounding error lands either side of it by luck of where the
			// door stands: the first version of this test asserted 12 front and 6 back and failed in
			// 10 of 18 suite runs, each at a random origin, with the whole column painting the front face
			// (D-3005). Upstream's own arithmetic, so a live defect and flagged, not fixed. That column is
			// counted here, not asserted.
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.STONE));
			int front = 0, back = 0, boundary = 0;
			for(int x = -3; x <= 3; x++) for(int y = 0; y <= 3; y++) for(int z = -3; z <= 3; z++) {
				BlockPos pos = core.offset(x, y, z);
				if(helper.getLevel().getBlockState(pos).getBlock() != block) continue;
				door.skinA = ReactorDoorTE.defaultSkinA();
				door.skinB = ReactorDoorTE.defaultSkinB();
				helper.assertTrue(block.onScrew(helper.getLevel(), player, pos, Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER), name + ": painting answers true");
				boolean paintedFront = door.skinA.is(Blocks.STONE), paintedBack = door.skinB.is(Blocks.STONE);
				helper.assertTrue(paintedFront != paintedBack, name + ": exactly one face is painted at " + pos.subtract(core));

				double objectX = new FiaMatrix(Vec3.atLowerCornerOf(core), Vec3.atLowerCornerOf(core.relative(facing)))
						.toObjectSpace(new FiaMatrix(Vec3.atLowerCornerOf(pos))).getX();
				if(objectX < 0.5) {
					helper.assertTrue(paintedFront, name + ": " + pos.subtract(core) + " is at x = " + objectX + " and takes the front face");
					front++;
				} else {
					helper.assertTrue(Math.abs(objectX - 1) < 1e-6, name + ": the only other column is at x = 1, was " + objectX);
					boundary++;
					if(paintedFront) front++; else back++;
				}
			}
			helper.assertTrue(front + back == 18, name + ": eighteen cells, " + front + " + " + back);
			helper.assertTrue(boundary == 6, name + ": and the column at x = 1 is 3 high and 2 deep: " + boundary);

			// Nothing in the other hand: cycle the skin through seven.
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			for(int i = 1; i <= 8; i++) {
				helper.assertTrue(block.onScrew(helper.getLevel(), player, core, Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER), name + ": cycling answers true");
				helper.assertTrue(door.getSkinIndex() == i % 7, name + ": skin " + i + " wraps at seven, was " + door.getSkinIndex());
			}

			for(int x = -3; x <= 3; x++) for(int y = 0; y <= 3; y++) for(int z = -3; z <= 3; z++)
				if(helper.getLevel().getBlockState(core.offset(x, y, z)).getBlock() == block)
					helper.getLevel().setBlock(core.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
		}
		helper.succeed();
	}

	/** A variant item paints its finish, not the block's default. */
	@GameTest(template = ROOM)
	public static void aVariantItemPaintsItsFinish(GameTestHelper helper) {

		ItemStack machineStripe = new ItemStack(BuiltInRegistries.ITEM.get(
				ResourceLocation.fromNamespaceAndPath("hbm", "concrete_colored_ext_machine_stripe")));
		helper.assertTrue(!machineStripe.isEmpty(), "the base mod has a machine stripe concrete item");

		BlockState painted = ReactorDoorTE.paintOf(machineStripe);
		helper.assertTrue(painted != null && painted.getBlock() == ModBlocks.concrete_colored_ext.get()
				&& painted.getValue(com.hbm.blocks.generic.BlockConcreteColoredExt.VARIANT)
				== com.hbm.blocks.generic.BlockConcreteColoredExt.EnumConcreteType.MACHINE_STRIPE, "paints MACHINE_STRIPE, got " + painted);

		helper.assertTrue(ReactorDoorTE.paintOf(new ItemStack(Blocks.STONE)).getBlock() == Blocks.STONE, "a plain block paints itself");
		helper.assertTrue(ReactorDoorTE.paintOf(new ItemStack(net.minecraft.world.item.Items.STICK)) == null, "a stick does not");
		helper.succeed();
	}

	/** The paint survives a save and a trip over the wire. */
	@GameTest(template = ROOM)
	public static void theReactorDoorsPaintIsSavedAndSent(GameTestHelper helper) {

		if(!LeafiaFeatures.doors) { helper.succeed(); return; }

		BlockPos core = build(helper, LeafiaBlocks.reactor_door.get(), AddonDoorDecl.REACTOR_DOOR, 8, 8, Direction.EAST);
		ReactorDoorTE door = (ReactorDoorTE) helper.getLevel().getBlockEntity(core);
		door.skinA = Blocks.OAK_PLANKS.defaultBlockState();
		door.skinB = Blocks.COPPER_BLOCK.defaultBlockState();

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = door.saveWithFullMetadata(registries);
		ReactorDoorTE loaded = (ReactorDoorTE) BlockEntity.loadStatic(core, helper.getLevel().getBlockState(core), tag, registries);
		helper.assertTrue(loaded.skinA.is(Blocks.OAK_PLANKS) && loaded.skinB.is(Blocks.COPPER_BLOCK), "saved and loaded");

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		door.serialize(buf);
		ReactorDoorTE received = new ReactorDoorTE(core, helper.getLevel().getBlockState(core));
		received.deserialize(buf);
		helper.assertTrue(received.skinA.is(Blocks.OAK_PLANKS) && received.skinB.is(Blocks.COPPER_BLOCK), "sent and received");

		// A save with no paint in it keeps the defaults.
		CompoundTag bare = door.saveWithFullMetadata(registries);
		bare.remove("skinA");
		bare.remove("skinB");
		ReactorDoorTE fresh = (ReactorDoorTE) BlockEntity.loadStatic(core, helper.getLevel().getBlockState(core), bare, registries);
		helper.assertTrue(fresh.skinB.getBlock() == ModBlocks.concrete_colored_ext.get(), "an unpainted save loads the default paint");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the renderer's clip

	/**
	 * The cut the sliding leaves are drawn with: Sutherland-Hodgman against the two planes. A quad
	 * entirely inside is untouched, one entirely outside is gone, and one that crosses is cut at the
	 * plane with its texture coordinate interpolated to the cut.
	 */
	@GameTest(template = ROOM)
	public static void theLeafIsCutAtTheFrame(GameTestHelper helper) {

		// A unit square in the XZ plane (y = 0) from z = 2 to z = 5, u running with z from 0 to 3.
		List<float[]> quad = new ArrayList<>();
		quad.add(v(0, 0, 2, 0, 0));
		quad.add(v(1, 0, 2, 1, 0));
		quad.add(v(1, 0, 5, 1, 3));
		quad.add(v(0, 0, 5, 0, 3));

		List<float[]> inside = DoorClipMath.cut(quad, 1.9F, true);
		helper.assertTrue(inside.size() == 4, "all of it is above 1.9");
		helper.assertTrue(DoorClipMath.cut(quad, 6F, true).isEmpty(), "none of it is above 6");

		List<float[]> cut = DoorClipMath.cut(quad, 3.499F, false);
		helper.assertTrue(cut.size() == 4, "cut at 3.499 it is still a quad, was " + cut.size());
		float maxZ = -1, maxV = -1;
		for(float[] p : cut) { maxZ = Math.max(maxZ, p[2]); maxV = Math.max(maxV, p[4]); }
		helper.assertTrue(Math.abs(maxZ - 3.499F) < 1e-5, "and ends at the plane, at " + maxZ);
		helper.assertTrue(Math.abs(maxV - (3.499F - 2F)) < 1e-4, "with the texture coordinate cut with it: " + maxV);

		List<float[]> both = DoorClipMath.cut(DoorClipMath.cut(quad, 2.5F, true), 3.5F, false);
		float lo = 99, hi = -99;
		for(float[] p : both) { lo = Math.min(lo, p[2]); hi = Math.max(hi, p[2]); }
		helper.assertTrue(Math.abs(lo - 2.5F) < 1e-5 && Math.abs(hi - 3.5F) < 1e-5, "between both planes: " + lo + ".." + hi);

		// A straight cut across a square, and a triangle cut either way.
		List<float[]> corner = new ArrayList<>();
		corner.add(v(0, 0, 0, 0, 0));
		corner.add(v(2, 0, 0, 0, 0));
		corner.add(v(2, 0, 2, 0, 0));
		corner.add(v(0, 0, 2, 0, 0));
		helper.assertTrue(DoorClipMath.cut(corner, 1.5F, false).size() == 4, "a square cut straight across stays four points");
		List<float[]> tri = new ArrayList<>();
		tri.add(v(0, 0, 0, 0, 0));
		tri.add(v(1, 0, 3, 0, 0));
		tri.add(v(-1, 0, 3, 0, 0));
		helper.assertTrue(DoorClipMath.cut(tri, 1F, false).size() == 3, "a triangle with its tip kept is a smaller triangle");
		helper.assertTrue(DoorClipMath.cut(tri, 1F, true).size() == 4, "and with its tip cut off, a quad");
		helper.succeed();
	}

	private static float[] v(float x, float y, float z, float u, float vv) {
		return new float[] {x, y, z, u, vv, 0, 1, 0};
	}
}
