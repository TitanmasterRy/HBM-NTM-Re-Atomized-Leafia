package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blockentity.machine.storage.BlockEntityCrateDesh;
import com.hbm.blockentity.machine.storage.BlockEntityCrateIron;
import com.hbm.blockentity.machine.storage.BlockEntityCrateSteel;
import com.hbm.blockentity.machine.storage.BlockEntityCrateTungsten;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerCrateIron;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.storage.rack.RackBlock;
import com.leafia.contents.building.storage.rack.RackTE;
import com.leafia.contents.nonmachines.storage.items.CrateLabels;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

/**
 * The storage rack (D-3021): the multiblock, which place a click lands on, what a place holds and keeps, and
 * what breaking it does.
 *
 * <p>The frame is drawn by a renderer a server cannot load, so what is pinned here is the geometry it draws
 * <em>against the blocks the multiblock occupies and the matrix the click is read in</em>: the model's footprint
 * has to lie inside the rack's own blocks for every facing, and a click at the middle of a drawn block has to be
 * that block's place. Those two tests are the renderer's and the interaction's agreement with the placement,
 * checked the way the renderer's own transform composes: a half block in, the facing's turn about +Y, upstream's
 * half-block step back.
 *
 * <p>A mock player's connection refuses the extra-data open packet, so a click that opens a crate is checked by
 * what happens before the open (the crate is given remote access) and by the refusal itself.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRackTests {

	private static final String BIG = "empty16";
	private static final BlockPos CORE = new BlockPos(8, 1, 8);

	private static boolean on() {
		return LeafiaFeatures.rack && LeafiaBlocks.sellacity_rack != null;
	}

	private static float yawFor(Direction looking) {
		return switch(looking) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> 270F;
			default -> throw new IllegalArgumentException(looking.toString());
		};
	}

	/** Places the rack as a player does, facing {@code facing} (the player looks the other way). */
	private static RackTE place(GameTestHelper helper, BlockPos core, Direction facing) {
		Block block = LeafiaBlocks.sellacity_rack.get();
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setYRot(yawFor(facing.getOpposite()));
		player.setXRot(0F);
		helper.setBlock(core, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(core), block.defaultBlockState(), player, new ItemStack(block));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof RackTE, "placing a rack at " + core + " facing " + facing + " made no core there: " + be);
		return (RackTE) be;
	}

	private static RackBlock block() {
		return (RackBlock) LeafiaBlocks.sellacity_rack.get();
	}

	/** The renderer's transform: a half block in, the facing's turn, upstream's step back half a block. */
	private static Vec3 world(BlockPos absCore, Direction facing, double x, double y, double z) {
		Vector3f v = new Vector3f((float) x, (float) y, (float) (z - 0.5)).rotateY((float) Math.toRadians(RackBlock.facingDegrees(facing)));
		return new Vec3(absCore.getX() + 0.5 + v.x, absCore.getY() + v.y, absCore.getZ() + 0.5 + v.z);
	}

	private static ItemStack crateItem(Block crate) {
		return new ItemStack(crate);
	}

	private static List<ItemEntity> drops(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(4));
	}

	// ------------------------------------------------------------------ the multiblock

	@GameTest(template = BIG)
	public static void aRackIsThreeWideTwoDeepAndThreeTallWithOneBlockEntity(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(Direction facing : Direction.Plane.HORIZONTAL) {
			place(helper, CORE, facing);
			int blocks = 0;
			int entities = 0;
			for(int x = 4; x <= 12; x++)
				for(int y = 1; y <= 5; y++)
					for(int z = 4; z <= 12; z++) {
						BlockPos p = new BlockPos(x, y, z);
						if(helper.getBlockState(p).getBlock() != LeafiaBlocks.sellacity_rack.get()) continue;
						blocks++;
						if(helper.getLevel().getBlockEntity(helper.absolutePos(p)) != null) entities++;
					}
			helper.assertTrue(blocks == 18, facing + ": a 3 x 2 x 3 rack is 18 blocks, got " + blocks);
			helper.assertTrue(entities == 1, facing + ": one block entity, the core's, got " + entities);
			helper.assertTrue(helper.getBlockState(CORE).getValue(BlockDummyable.PART) == Part.CORE && helper.getBlockState(CORE).getValue(BlockDummyable.DIR) == facing,
					facing + ": the core faces the way it was placed");
			helper.setBlock(CORE, Blocks.AIR);
			clear(helper);
		}

		helper.assertTrue(java.util.Arrays.equals(block().getDimensions(), new int[] { 2, 0, 1, 0, 1, 1 }) && block().getOffset() == 0, "upstream's {2, 0, 1, 0, 1, 1} with an offset of 0");
		helper.succeed();
	}

	/** Takes every rack block in the test's playing field away, so the next facing starts clean. */
	private static void clear(GameTestHelper helper) {
		for(int x = 3; x <= 13; x++)
			for(int y = 1; y <= 6; y++)
				for(int z = 3; z <= 13; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if(helper.getBlockState(p).getBlock() == LeafiaBlocks.sellacity_rack.get()) {
						BlockDummyable.safeRem = true;
						try {
							helper.getLevel().setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), 2);
						} finally {
							BlockDummyable.safeRem = false;
						}
					}
				}
	}

	/** The OBJ's footprint, x -1.5..1.5 and z -1..0.75 and y 0..2.94, lies inside the blocks the rack occupies. */
	@GameTest(template = BIG)
	public static void theFramesFootprintLiesInsideTheRacksBlocksForEveryFacing(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(Direction facing : Direction.Plane.HORIZONTAL) {
			place(helper, CORE, facing);
			BlockPos abs = helper.absolutePos(CORE);
			List<String> outside = new ArrayList<>();
			java.util.Set<BlockPos> columns = new java.util.HashSet<>();
			for(double x = -1.45; x <= 1.46; x += 0.3)
				for(double z = -0.95; z <= 0.71; z += 0.3)
					for(double y : new double[] { 0.05, 1.5, 2.9 }) {
						Vec3 w = world(abs, facing, x, y, z);
						BlockPos cell = BlockPos.containing(w);
						if(helper.getLevel().getBlockState(cell).getBlock() != LeafiaBlocks.sellacity_rack.get()) outside.add(String.format("(%.2f, %.2f, %.2f)", x, y, z));
						columns.add(new BlockPos(cell.getX(), 0, cell.getZ()));
					}
			helper.assertTrue(outside.isEmpty(), facing + ": the frame leaves the rack's blocks at " + outside);
			helper.assertTrue(columns.size() == 6, facing + ": the frame spans all six columns of the rack, got " + columns.size());
			helper.setBlock(CORE, Blocks.AIR);
			clear(helper);
		}
		helper.succeed();
	}

	/** A click at the middle of a drawn block is that block's place, in every facing: the renderer and the click agree. */
	@GameTest(template = BIG)
	public static void aClickAtTheMiddleOfADrawnBlockIsThatPlaceForEveryFacing(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(Direction facing : Direction.Plane.HORIZONTAL) {
			place(helper, CORE, facing);
			BlockPos abs = helper.absolutePos(CORE);
			for(int i = 0; i < RackTE.PLACES; i++) {
				// the block's cell is [x, x+1] by [y, y+1] by [z - 1, z] after upstream's half-turn about its own corner
				double[] at = RackTE.PLACES_AT[i];
				Vec3 middle = world(abs, facing, at[0] + 0.5, at[1] + 0.5, at[2] - 0.5);
				helper.assertTrue(RackBlock.placeOf(abs, facing, middle) == i, facing + ": the middle of place " + i + " reads as " + RackBlock.placeOf(abs, facing, middle));
				// and the clicked cell is one of the rack's blocks
				helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(middle)).getBlock() == LeafiaBlocks.sellacity_rack.get(),
						facing + ": place " + i + " is drawn outside the rack's blocks");
			}
			helper.setBlock(CORE, Blocks.AIR);
			clear(helper);
		}
		helper.succeed();
	}

	/** Upstream's two thresholds: left is x <= 0, upper is y >= 1.5, in the rack's own frame, whichever way it faces. */
	/** Upstream's {@code invalidate}: a rack that goes - unloaded, say, with no block break to spill it - takes its live crates with it. */
	@GameTest(template = BIG)
	public static void aRacksLiveCratesAreInvalidatedWhenItIs(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		rack.storeCrate(crateItem(ModBlocks.crate_steel.get()), 0, true);
		rack.storeCrate(crateItem(ModBlocks.crate_desh.get()), 3, true);
		BlockEntityCrateBase a = rack.crate(0);
		BlockEntityCrateBase b = rack.crate(3);
		helper.assertTrue(!a.isRemoved() && !b.isRemoved(), "live while it stands");
		rack.setRemoved();
		helper.assertTrue(a.isRemoved() && b.isRemoved(), "both went with the rack");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theQuartersMeetAtTheMiddleAndAtOnePointFiveHigh(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		for(Direction facing : Direction.Plane.HORIZONTAL) {
			place(helper, CORE, facing);
			BlockPos abs = helper.absolutePos(CORE);
			double[][] cases = { { -0.2, 1.4, 0 }, { 0.2, 1.4, 1 }, { -0.2, 1.6, 2 }, { 0.2, 1.6, 3 }, { -1.4, 0.2, 0 }, { 1.4, 2.8, 3 } };
			for(double[] c : cases) {
				int got = RackBlock.placeOf(abs, facing, world(abs, facing, c[0], c[1], 0.5));
				helper.assertTrue(got == (int) c[2], facing + ": (" + c[0] + ", " + c[1] + ") is place " + (int) c[2] + ", read as " + got);
			}
			helper.setBlock(CORE, Blocks.AIR);
			clear(helper);
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theRackIsDrawnByItsRendererAndSwallowsPlacingAgainstIt(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		helper.assertTrue(block().defaultBlockState().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED, "the renderer draws it");
		RackTE rack = place(helper, CORE, Direction.SOUTH);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		ItemStack sticks = new ItemStack(Items.STICK, 5);
		BlockPos abs = helper.absolutePos(CORE);
		block().interact(helper.getLevel(), abs, player, sticks, world(abs, Direction.SOUTH, -0.6, 0.9, 0));
		helper.assertTrue(sticks.getCount() == 5 && !rack.has(0), "a stick is not stored and is not used up");
		helper.succeed();
	}

	// ------------------------------------------------------------------ what a place holds

	@GameTest(template = BIG)
	public static void onlyABlockDrawnAsAModelCanBeStored(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		helper.assertTrue(rack.storeCrate(new ItemStack(Items.STONE), 0, true) && rack.has(0) && rack.crate(0) == null, "stone is shown and has no live crate");
		helper.assertTrue(rack.storeCrate(new ItemStack(ModBlocks.crate_lead.get()), 1, true), "another crate block is just a block");
		helper.assertTrue(!rack.storeCrate(new ItemStack(Items.STICK), 2, true) && !rack.has(2), "an item is not a block");
		helper.assertTrue(!rack.storeCrate(new ItemStack(Items.CHEST), 2, true) && !rack.has(2), "a chest is drawn by an entity renderer");
		helper.assertTrue(!rack.storeCrate(new ItemStack(LeafiaBlocks.sellacity_rack.get()), 2, true) && !rack.has(2), "nor is a rack");
		helper.assertTrue(!rack.storeCrate(ItemStack.EMPTY, 2, true), "nor is nothing");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theFourCratesGetALiveCrateOfTheirOwnKind(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		helper.assertTrue(rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 0, true), "iron");
		helper.assertTrue(rack.storeCrate(crateItem(ModBlocks.crate_steel.get()), 1, true), "steel");
		helper.assertTrue(rack.storeCrate(crateItem(ModBlocks.crate_desh.get()), 2, true), "desh");
		helper.assertTrue(rack.storeCrate(crateItem(ModBlocks.crate_tungsten.get()), 3, true), "tungsten");
		helper.assertTrue(rack.crate(0) instanceof BlockEntityCrateIron && rack.crate(0).getContainerSize() == 36, "iron: 36 slots");
		helper.assertTrue(rack.crate(1) instanceof BlockEntityCrateSteel && rack.crate(1).getContainerSize() == 54, "steel: 54 slots");
		helper.assertTrue(rack.crate(2) instanceof BlockEntityCrateDesh && rack.crate(2).getContainerSize() == 104, "desh: 104 slots");
		helper.assertTrue(rack.crate(3) instanceof BlockEntityCrateTungsten && rack.crate(3).getContainerSize() == 27, "tungsten: 27 slots");
		for(int i = 0; i < 4; i++)
			helper.assertTrue(rack.crate(i).hasRemoteAccess() && !rack.crate(i).isRemoved() && rack.crate(i).getLevel() == helper.getLevel(),
					"place " + i + ": a live crate with the rack's level that stays valid at any distance");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aCrateKeepsWhatItemHeldAndGivesItBack(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);

		// a crate item as a broken crate drops it: slot 3, a lock, a label, a name
		ItemStack item = crateItem(ModBlocks.crate_iron.get());
		CompoundTag data = new CompoundTag();
		data.put("slot3", new ItemStack(Items.DIAMOND, 9).save(helper.getLevel().registryAccess(), new CompoundTag()));
		data.putInt("lock", 4321);
		data.putDouble("lockMod", 0.5D);
		CompoundTag extra = new CompoundTag();
		extra.putString("leafia_label_upper", "stuff");
		data.put("extraData", extra);
		data.putBoolean("spiders", true);
		data.putLong("stacklock", 77L);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Vault"));

		helper.assertTrue(rack.storeCrate(item, 2, true), "stored");
		BlockEntityCrateBase crate = rack.crate(2);
		helper.assertTrue(crate.getItem(3).is(Items.DIAMOND) && crate.getItem(3).getCount() == 9, "the contents are in the live crate");
		helper.assertTrue(crate.isLocked() && crate.getPins() == 4321 && crate.getMod() == 0.5D, "and the lock");
		helper.assertTrue(CrateLabels.of(crate).upper().equals("stuff"), "and the label");
		helper.assertTrue(crate.hasSpiders, "and the spider trap");
		helper.assertTrue(crate.hasCustomInventoryName() && crate.getDisplayName().getString().equals("Vault"), "and the name");
		helper.assertTrue(!rack.crates[2].has(DataComponents.CUSTOM_DATA), "the stored item itself is a plain crate");

		crate.setItem(5, new ItemStack(Items.GOLD_INGOT, 2));
		ItemStack back = rack.removeCrate(2, true);
		helper.assertTrue(back.is(ModBlocks.crate_iron.get().asItem()) && !rack.has(2) && rack.crate(2) == null, "off the rack as an iron crate item");
		CompoundTag out = back.get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(out.contains("slot3") && out.contains("slot5") && out.getInt("lock") == 4321 && out.getDouble("lockMod") == 0.5D
				&& out.getCompound("extraData").getString("leafia_label_upper").equals("stuff"), "with the contents, the lock and the label: " + out.getAllKeys());
		helper.assertTrue(out.contains("stacklock") && out.getBoolean("spiders"), "and a stack lock so two do not merge, and the trap");
		// a second crate that goes off gets a stack lock of its own
		ItemStack second = new ItemStack(ModBlocks.crate_iron.get());
		CompoundTag secondData = new CompoundTag();
		secondData.put("slot0", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess(), new CompoundTag()));
		second.set(DataComponents.CUSTOM_DATA, CustomData.of(secondData));
		rack.storeCrate(second, 1, true);
		helper.assertTrue(rack.removeCrate(1, true).get(DataComponents.CUSTOM_DATA).copyTag().getLong("stacklock") != out.getLong("stacklock"),
				"two crates taken off get different stack locks");
		helper.assertTrue(back.get(DataComponents.CUSTOM_NAME).getString().equals("Vault"), "and the name");
		helper.assertTrue(crate.isRemoved(), "the live crate is gone");
		helper.assertTrue(rack.removeCrate(2, true).isEmpty(), "an empty place gives nothing");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void anEmptyCrateComesOffAsAPlainItem(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		rack.storeCrate(crateItem(ModBlocks.crate_steel.get()), 1, true);
		ItemStack back = rack.removeCrate(1, true);
		helper.assertTrue(ItemStack.isSameItemSameComponents(back, new ItemStack(ModBlocks.crate_steel.get())), "a plain steel crate item with nothing on it: " + back.getComponents());
		rack.storeCrate(new ItemStack(Items.STONE), 0, true);
		helper.assertTrue(ItemStack.isSameItemSameComponents(rack.removeCrate(0, true), new ItemStack(Items.STONE)), "so is a stone");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aRackSavesItsCratesAndLoadsThemBack(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.WEST);
		rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 0, true);
		rack.storeCrate(crateItem(ModBlocks.crate_desh.get()), 3, true);
		rack.storeCrate(new ItemStack(Items.STONE), 1, true);
		rack.crate(0).setItem(7, new ItemStack(Items.EMERALD, 4));
		CrateLabels.of(rack.crate(3)).setText(new String[] { "", "", "", "" }, "desh!", "", "");
		rack.crate(3).setPins(99);
		rack.crate(3).setMod(1.5D);
		rack.crate(3).lock();
		rack.crate(0).setCustomName("Green");

		var access = helper.getLevel().registryAccess();
		CompoundTag saved = rack.saveWithFullMetadata(access);
		BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(CORE), helper.getBlockState(CORE), saved, access);
		helper.assertTrue(loaded instanceof RackTE, "did not load");
		RackTE back = (RackTE) loaded;
		back.setLevel(helper.getLevel());
		back.onLoad();

		helper.assertTrue(back.has(0) && back.has(1) && !back.has(2) && back.has(3), "the same places are filled");
		helper.assertTrue(back.crate(0) instanceof BlockEntityCrateIron && back.crate(0).getItem(7).is(Items.EMERALD) && back.crate(0).getItem(7).getCount() == 4, "the iron crate's contents");
		helper.assertTrue(back.crate(0).getDisplayName().getString().equals("Green"), "and its name");
		helper.assertTrue(back.crate(1) == null && back.block(1) == Blocks.STONE, "the stone is a stone");
		helper.assertTrue(back.crate(3) instanceof BlockEntityCrateDesh && CrateLabels.of(back.crate(3)).upper().equals("desh!"), "the desh crate's label");
		helper.assertTrue(back.crate(3).isLocked() && back.crate(3).getPins() == 99 && back.crate(3).getMod() == 1.5D, "and its lock");
		helper.assertTrue(back.crate(0).hasRemoteAccess() && back.crate(3).hasRemoteAccess(), "and they stay open from any distance");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theClientIsSentTheBlocksAndNoContents(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.NORTH);
		rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 1, true);
		rack.storeCrate(new ItemStack(Items.STONE), 2, true);
		rack.crate(1).setItem(0, new ItemStack(Items.DIAMOND));

		var access = helper.getLevel().registryAccess();
		CompoundTag update = rack.getUpdateTag(access);
		helper.assertTrue(update.getAllKeys().equals(java.util.Set.of("crate1", "crate2")), "two places: " + update.getAllKeys());
		helper.assertTrue(!update.getCompound("crate1").contains("data") && !update.toString().contains("diamond"), "and no contents: " + update);

		RackTE client = new RackTE(helper.absolutePos(CORE), helper.getBlockState(CORE));
		client.handleUpdateTag(update, access);
		helper.assertTrue(client.has(1) && client.has(2) && !client.has(0) && client.crate(1) == null && client.block(1) == ModBlocks.crate_iron.get(),
				"the client knows the blocks only");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the clicks

	@GameTest(template = BIG)
	public static void aClickStoresOneBlockInThePlaceClickedAndTheNextClickOpensIt(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		BlockPos abs = helper.absolutePos(CORE);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		ItemStack hand = new ItemStack(ModBlocks.crate_iron.get(), 3);

		// place 3: upper right
		Vec3 upperRight = world(abs, Direction.SOUTH, 0.625, 2.25, 0);
		block().interact(helper.getLevel(), abs, player, hand, upperRight);
		helper.assertTrue(rack.has(3) && rack.crate(3) instanceof BlockEntityCrateIron && hand.getCount() == 2, "one crate went into the upper right: " + hand);
		helper.assertTrue(!rack.has(0) && !rack.has(1) && !rack.has(2), "and no other place");

		// a second click on the same place does not store a second; it opens the crate
		boolean refused = false;
		try {
			block().interact(helper.getLevel(), abs, player, hand, upperRight);
		} catch(UnsupportedOperationException e) {
			refused = true; // a mock player's connection refuses the extra-data open packet: the open was attempted
		}
		helper.assertTrue(hand.getCount() == 2, "the held stack was not used a second time");
		helper.assertTrue(rack.crate(3).hasRemoteAccess(), "the crate it opens is open from any distance");
		helper.assertTrue(refused || player.containerMenu instanceof ContainerCrateIron, "and the open was attempted");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void sneakingWithAnEmptyHandTakesTheCrateOffIntoTheSelectedSlot(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.EAST);
		BlockPos abs = helper.absolutePos(CORE);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		rack.storeCrate(crateItem(ModBlocks.crate_steel.get()), 0, true);
		rack.crate(0).setItem(2, new ItemStack(Items.COAL, 5));

		Vec3 lowerLeft = world(abs, Direction.EAST, -0.625, 0.875, 0);
		// not sneaking: it opens, it does not come off
		try {
			block().interact(helper.getLevel(), abs, player, ItemStack.EMPTY, lowerLeft);
		} catch(UnsupportedOperationException ignored) { }
		helper.assertTrue(rack.has(0), "an empty hand that is not sneaking leaves the crate");

		player.setShiftKeyDown(true);
		player.getInventory().selected = 4;
		block().interact(helper.getLevel(), abs, player, ItemStack.EMPTY, lowerLeft);
		helper.assertTrue(!rack.has(0), "sneaking with an empty hand takes it");
		ItemStack got = player.getInventory().getItem(4);
		helper.assertTrue(got.is(ModBlocks.crate_steel.get().asItem()) && got.get(DataComponents.CUSTOM_DATA).copyTag().contains("slot2"),
				"into the selected slot, with what it held: " + got);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aLockedCrateOpensFromARackWithoutAskingAboutTheLock(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		// upstream's RackBlock.onBlockActivated opens a crate with FMLNetworkHandler.openGui and never asks isLocked
		RackTE rack = place(helper, CORE, Direction.SOUTH);
		BlockPos abs = helper.absolutePos(CORE);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 1, true);
		rack.crate(1).setPins(1234);
		rack.crate(1).lock();
		helper.assertTrue(!rack.crate(1).canAccess(player), "the crate itself refuses a player with no key");

		boolean attempted = false;
		try {
			block().interact(helper.getLevel(), abs, player, ItemStack.EMPTY, world(abs, Direction.SOUTH, 0.625, 0.875, 0));
			attempted = player.containerMenu instanceof ContainerCrateIron;
		} catch(UnsupportedOperationException e) {
			attempted = true;
		}
		helper.assertTrue(attempted, "a locked crate on a rack opened to a player without the key; upstream's rack does not ask");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theMenuOfARackedCrateIsTheLiveCratesAndStaysValidUntilItLeaves(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 2, true);
		BlockEntityCrateBase crate = rack.crate(2);

		var menu = crate.createMenu(5, player.getInventory(), player);
		helper.assertTrue(menu instanceof ContainerCrateIron iron && iron.tile == crate, "the menu is over the live crate");
		player.setPos(player.getX() + 400, player.getY(), player.getZ());
		helper.assertTrue(menu.stillValid(player), "it stays open at any distance (upstream's isUseableByPlayer is just !isInvalid)");
		rack.removeCrate(2, true);
		helper.assertTrue(!menu.stillValid(player), "and closes when the crate leaves the rack");
		helper.succeed();
	}

	// ------------------------------------------------------------------ breaking

	@GameTest(template = BIG)
	public static void breakingTheRackDropsEveryCrateWithWhatItHeld(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		RackTE rack = place(helper, CORE, Direction.SOUTH);
		rack.storeCrate(crateItem(ModBlocks.crate_iron.get()), 0, true);
		rack.storeCrate(new ItemStack(Items.STONE), 3, true);
		rack.crate(0).setItem(1, new ItemStack(Items.IRON_INGOT, 12));
		BlockEntityCrateBase live = rack.crate(0);

		// from a part that is not the core: the chain takes the core, and the core's removal spills the rack
		BlockPos part = CORE.offset(1, 2, 0);
		helper.assertTrue(helper.getBlockState(part).getBlock() == LeafiaBlocks.sellacity_rack.get(), "a part of the rack is at " + part);
		helper.getLevel().removeBlock(helper.absolutePos(part), false);

		int crates = 0;
		int stones = 0;
		boolean contents = false;
		for(ItemEntity entity : drops(helper, CORE)) {
			ItemStack stack = entity.getItem();
			if(stack.is(ModBlocks.crate_iron.get().asItem())) {
				crates++;
				CustomData data = stack.get(DataComponents.CUSTOM_DATA);
				contents = data != null && data.copyTag().contains("slot1");
			}
			if(stack.is(Items.STONE)) stones++;
			entity.discard();
		}
		helper.assertTrue(crates == 1 && contents, "the iron crate dropped with its iron: " + crates);
		helper.assertTrue(stones == 1, "and the stone");
		helper.assertTrue(helper.getBlockState(CORE).isAir(), "the core went with it");
		helper.assertTrue(live.isRemoved(), "and the live crate went with the rack");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theRackItemHasItsModelAndItsOwnItem(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		helper.assertTrue(LeafiaBlocks.sellacity_rack.get().asItem() != Items.AIR, "the block has an item");
		for(String asset : new String[] { "models/item/sellacity_rack.json", "blockstates/sellacity_rack.json", "models/block/sellacity_rack_item.json",
				"models/blocks/sellacity_rack.obj", "textures/block/sellacity_rack.png" })
			helper.assertTrue(LeafiaRackTests.class.getResource("/assets/leafia/" + asset) != null, "missing " + asset);
		// the OBJ the renderer parses has upstream's five objects
		helper.assertTrue(com.leafia.contents.building.storage.rack.RackObj.meshes().keySet().equals(java.util.Set.of("rack", "poles", "support", "palletLower", "palletUpper")),
				"the rack's objects: " + com.leafia.contents.building.storage.rack.RackObj.meshes().keySet());
		helper.succeed();
	}
}
