package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.contents.machines.processing.solblaster.SolBlasterBlock;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterTE;
import com.leafia.contents.machines.processing.solblaster.container.SolBlasterMenu;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMats;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Neutron Blaster (D-1087): the machine's shape, its slots and what a hopper may do to them,
 * the door and the firing sequence, every transmutation, and the screen's link to it.
 *
 * <p>Every test that runs the machine does it inside one body. A block entity ticks on its own every
 * game tick, so a test that spread a sequence across ticks would be racing the machine it is
 * testing; inside one body the world does not advance, and a count of {@code updateEntity()} calls
 * is a count of ticks (D-953). The two tests that need real ticks - a hopper moving an item and a
 * comparator re-reading - use {@code succeedWhen} and nothing else in them changes while they wait.
 *
 * <p>The machine is placed by its own {@code setPlacedBy}, standing a mock player in the facing
 * wanted, because the placement is the thing with the quirks. A mock player is taken off the
 * server's list at once and never left on the machine's.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSolBlasterTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	// ---- the rig ---------------------------------------------------------------------------

	/** A placed machine: where its core is (template-relative) and the block entity there. */
	private record Rig(GameTestHelper helper, BlockPos core, Direction looking, SolBlasterTE te) {

		BlockPos at(int x, int y, int z) {
			return core.offset(x, y, z);
		}

		BlockPos abs(int x, int y, int z) {
			return helper.absolutePos(at(x, y, z));
		}
	}

	/** A player the server does not broadcast to - see {@code LeafiaEvCabinGuiTests.mockPlayer}. */
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
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
	 * A player who is not creative. The helper's mock overrides {@code isCreative} to true, and a
	 * creative player is not given an item back; this one is never placed in the level or on the
	 * server's list, so there is nothing to detach.
	 */
	private static ServerPlayer survivor(GameTestHelper helper) {
		return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "w13-survivor"),
				net.minecraft.server.level.ClientInformation.createDefault()) {
			@Override public boolean isSpectator() { return false; }
			@Override public boolean isCreative() { return false; }
		};
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

	/**
	 * Places the machine the way a player does: the block goes in the clicked cell and
	 * {@code setPlacedBy} rebuilds it around a core one step further along where the player looks.
	 * Returns null when the placement was refused (and the player has the item back).
	 */
	private static Rig tryPlace(GameTestHelper helper, ServerPlayer player, BlockPos core, Direction looking) {

		Block block = LeafiaBlocks.sol_blaster.get();
		BlockPos at = core.relative(looking.getOpposite());

		player.setYRot(yawFor(looking));
		player.setXRot(0F);

		helper.setBlock(at, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), block.defaultBlockState(), player, new ItemStack(block));

		if(!(helper.getLevel().getBlockEntity(helper.absolutePos(core)) instanceof SolBlasterTE te)) return null;
		return new Rig(helper, core, looking, te);
	}

	private static Rig place(GameTestHelper helper, BlockPos core, Direction looking) {
		Rig rig = tryPlace(helper, mockPlayer(helper), core, looking);
		helper.assertTrue(rig != null, "the machine was not placed at " + core + " looking " + looking);
		return rig;
	}

	/** The usual rig in the small template: core at (2, 0, 2), the player looking south. */
	private static Rig place(GameTestHelper helper) {
		return place(helper, new BlockPos(2, 0, 2), Direction.SOUTH);
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}

	private static Item core() {
		return ModItems.solinium_core.get();
	}

	private static Item earlyLens() {
		return ModItems.early_explosive_lenses.get();
	}

	private static Item lens() {
		return ModItems.explosive_lenses.get();
	}

	/** A solinium core and four lenses, a mix of both kinds. */
	private static void arm(SolBlasterTE te) {
		te.setItem(0, stack(core()));
		te.setItem(1, stack(earlyLens()));
		te.setItem(2, stack(lens()));
		te.setItem(3, stack(lens()));
		te.setItem(4, stack(earlyLens()));
	}

	/** Puts a redstone block beside the east face of the machine: the cell beside the core's east dummy. */
	private static BlockPos power(Rig rig) {
		BlockPos at = rig.at(2, 0, 0);
		rig.helper.setBlock(at, Blocks.REDSTONE_BLOCK);
		return at;
	}

	// ---- registration ----------------------------------------------------------------------

	/** One block in the tab, two block entity types, one menu type, on a switch that defaults on. */
	@GameTest(template = EMPTY)
	public static void theBlockAndItsTypesAreRegisteredAndInTheTab(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.solBlaster, "solBlaster defaults on");

		var block = BuiltInRegistries.BLOCK.getOptional(LeafiaRef.rl("sol_blaster"));
		helper.assertTrue(block.isPresent() && block.get() instanceof SolBlasterBlock, "sol_blaster is not registered as upstream's block");
		helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
				.anyMatch(s -> s.get().asItem() == block.get().asItem()), "the blaster is in no creative tab");

		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(LeafiaBlockEntities.SOL_BLASTER.get())
				.equals(LeafiaRef.rl("sol_blaster")), "the core's type is not sol_blaster");
		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(LeafiaBlockEntities.SOL_BLASTER_DUMMY.get())
				.equals(LeafiaRef.rl("sol_blaster_dummy")), "the corner proxies' type is not sol_blaster_dummy");
		helper.assertTrue(BuiltInRegistries.MENU.getKey(LeafiaMenuTypes.SOL_BLASTER.get()).equals(LeafiaRef.rl("sol_blaster")),
				"the menu type is not sol_blaster");
		helper.succeed();
	}

	/**
	 * Hardness 15 and resistance 160 through D-012, no tool needed (neither base calls
	 * {@code setHarvestLevel}), no mob spawns on it, and the proxy type is valid for the block and
	 * for nothing else.
	 */
	@GameTest(template = EMPTY)
	public static void theBlockIsUpstreamsStrengthAndTheProxyTypeIsItsOwn(GameTestHelper helper) {

		Block block = LeafiaBlocks.sol_blaster.get();
		BlockState state = block.defaultBlockState();

		helper.assertTrue(Math.abs(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) - 15F) < 1e-4,
				"hardness is " + state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO));
		helper.assertTrue(Math.abs(block.getExplosionResistance() - 96F) < 1e-3, "blast resistance is " + block.getExplosionResistance());
		helper.assertTrue(!state.requiresCorrectToolForDrops(), "upstream asks for no tool");
		helper.assertTrue(!state.isValidSpawn(helper.getLevel(), BlockPos.ZERO, net.minecraft.world.entity.EntityType.ZOMBIE),
				"a mob may spawn on a large machine");

		helper.assertTrue(LeafiaBlockEntities.SOL_BLASTER_DUMMY.get().isValid(state), "the proxy type does not accept the block");
		helper.assertTrue(!LeafiaBlockEntities.SOL_BLASTER_DUMMY.get().isValid(ModBlocks.machine_chemical_plant.get().defaultBlockState()),
				"the proxy type is valid for another machine");
		helper.assertTrue(!com.leafia.init.LeafiaBlockEntities.SOL_BLASTER.get().isValid(Blocks.STONE.defaultBlockState()),
				"the core type is valid for stone");
		helper.succeed();
	}

	// ---- the shape -------------------------------------------------------------------------

	/**
	 * A 3x3x3 with its core in the middle of the bottom layer and a two-block tower above the core's
	 * column, in every facing: 29 blocks, one core, four corner extras, and every cell finds the core.
	 */
	@GameTest(template = BIG)
	public static void itPlacesATowerOnAThreeByThreeByThreeInEveryFacing(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		int slot = 0;

		for(Direction looking : new Direction[] { Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST }) {

			BlockPos coreAt = new BlockPos(2 + 6 * (slot % 2), 0, 2 + 6 * (slot / 2));
			slot++;

			Rig rig = place(helper, coreAt, looking);
			Block block = LeafiaBlocks.sol_blaster.get();
			Set<BlockPos> mine = new HashSet<>();

			for(int x = -3; x <= 3; x++)
				for(int y = -1; y <= 5; y++)
					for(int z = -3; z <= 3; z++)
						if(helper.getBlockState(rig.at(x, y, z)).is(block)) mine.add(rig.at(x, y, z));

			if(mine.size() != 29) problems.add(looking + ": " + mine.size() + " blocks, not 29");

			for(int x = -1; x <= 1; x++)
				for(int z = -1; z <= 1; z++)
					for(int y = 0; y <= 2; y++)
						if(!mine.contains(rig.at(x, y, z))) problems.add(looking + ": the box is missing " + x + "," + y + "," + z);
			if(!mine.contains(rig.at(0, 3, 0)) || !mine.contains(rig.at(0, 4, 0))) problems.add(looking + ": the tower is incomplete");
			if(mine.contains(rig.at(0, 5, 0))) problems.add(looking + ": the tower is taller than two");

			int cores = 0, extras = 0, dummies = 0;
			for(BlockPos p : mine) {
				BlockState state = helper.getBlockState(p);
				switch(state.getValue(BlockDummyable.PART)) {
					case CORE -> cores++;
					case EXTRA -> extras++;
					case DUMMY -> dummies++;
				}
				BlockPos found = ((SolBlasterBlock) block).findCore(helper.getLevel(), helper.absolutePos(p));
				if(!helper.absolutePos(rig.core).equals(found)) problems.add(looking + ": " + p + " finds its core at " + found);
			}
			if(cores != 1 || extras != 4 || dummies != 24) problems.add(looking + ": " + cores + " core, " + extras + " extra, " + dummies + " dummy");

			for(int dx : new int[] { -1, 1 })
				for(int dz : new int[] { -1, 1 })
					if(helper.getBlockState(rig.at(dx, 0, dz)).getValue(BlockDummyable.PART) != Part.EXTRA)
						problems.add(looking + ": the corner " + dx + "," + dz + " is not an extra");

			BlockState coreState = helper.getBlockState(rig.core);
			if(coreState.getValue(BlockDummyable.DIR) != looking.getOpposite())
				problems.add(looking + ": the core faces " + coreState.getValue(BlockDummyable.DIR));
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The corners are inventory-only proxies; nothing else of the machine has a block entity but the core. */
	@GameTest(template = EMPTY)
	public static void theCornersAreInventoryOnlyProxiesAndNothingElseHasABlockEntity(GameTestHelper helper) {

		Rig rig = place(helper);
		List<String> problems = new ArrayList<>();

		for(int x = -1; x <= 1; x++)
			for(int z = -1; z <= 1; z++)
				for(int y = 0; y <= 4; y++) {
					if(y > 2 && (x != 0 || z != 0)) continue;
					var be = helper.getLevel().getBlockEntity(rig.abs(x, y, z));
					boolean corner = y == 0 && x != 0 && z != 0;
					if(x == 0 && y == 0 && z == 0) {
						if(!(be instanceof SolBlasterTE)) problems.add("the core is " + be);
					} else if(corner) {
						if(!(be instanceof BlockEntityProxyCombo) || be.getType() != LeafiaBlockEntities.SOL_BLASTER_DUMMY.get())
							problems.add("corner " + x + "," + z + " is " + be);
					} else if(be != null) {
						problems.add("a dummy at " + x + "," + y + "," + z + " has " + be);
					}
				}

		BlockEntityProxyCombo corner = (BlockEntityProxyCombo) helper.getLevel().getBlockEntity(rig.abs(1, 0, 1));
		rig.te.setItem(7, stack(ModItems.ingot_schrabidium.get()));
		if(corner.getContainerSize() != 20) problems.add("a corner shows " + corner.getContainerSize() + " slots, not 20");
		if(!corner.getItem(7).is(ModItems.ingot_schrabidium.get())) problems.add("a corner does not forward the slots");
		if(corner.getAllTanks().length != 0) problems.add("a corner forwards fluid");
		if(corner.getMaxPower() != 0 || corner.getPower() != 0) problems.add("a corner forwards power");

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Upstream's slip, kept: the core's own cell is passed as the "free" origin of both checks, so a
	 * solid block exactly there does not stop the placement and is replaced. Any other cell does.
	 */
	@GameTest(template = EMPTY)
	public static void theCoresOwnCellIsNeverChecked(GameTestHelper helper) {

		BlockPos coreAt = new BlockPos(2, 0, 2);
		helper.setBlock(coreAt, Blocks.STONE);
		Rig rig = place(helper, coreAt, Direction.SOUTH);
		helper.assertTrue(helper.getBlockState(rig.core).getValue(BlockDummyable.PART) == Part.CORE,
				"the core did not replace the stone");
		helper.succeed();
	}

	/** A solid block in the tower or the box refuses the placement and gives the player the item back. */
	@GameTest(template = EMPTY)
	public static void aSolidBlockAnywhereElseRefusesThePlacementAndRefundsIt(GameTestHelper helper) {

		BlockPos coreAt = new BlockPos(2, 0, 2);
		List<String> problems = new ArrayList<>();

		for(BlockPos blocker : new BlockPos[] { coreAt.offset(0, 4, 0), coreAt.offset(0, 3, 0), coreAt.offset(1, 2, -1), coreAt.offset(-1, 0, 0) }) {

			helper.setBlock(blocker, Blocks.STONE);
			ServerPlayer player = survivor(helper);
			Rig rig = tryPlace(helper, player, coreAt, Direction.SOUTH);

			if(rig != null) problems.add("stone at " + blocker + " did not stop the placement");

			int mine = 0;
			for(int x = 0; x <= 4; x++)
				for(int y = 0; y <= 4; y++)
					for(int z = 0; z <= 4; z++)
						if(helper.getBlockState(new BlockPos(x, y, z)).is(LeafiaBlocks.sol_blaster.get())) mine++;
			if(mine != 0) problems.add("stone at " + blocker + " left " + mine + " blocks of the machine");

			if(!player.getInventory().contains(new ItemStack(LeafiaBlocks.sol_blaster.get())))
				problems.add("stone at " + blocker + ": the item was not given back");

			helper.setBlock(blocker, Blocks.AIR);
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Breaking the core, an edge, a corner or the top of the tower takes the whole machine with it. */
	@GameTest(template = EMPTY)
	public static void breakingAnyPartTakesTheWholeMachine(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		int[][] victims = { { 0, 0, 0 }, { 1, 1, 0 }, { 1, 0, 1 }, { 0, 4, 0 }, { 0, 3, 0 } };

		for(int[] v : victims) {
			Rig rig = place(helper);
			helper.getLevel().destroyBlock(rig.abs(v[0], v[1], v[2]), false);

			int left = 0;
			for(int x = 0; x <= 4; x++)
				for(int y = 0; y <= 4; y++)
					for(int z = 0; z <= 4; z++)
						if(helper.getBlockState(new BlockPos(x, y, z)).is(LeafiaBlocks.sol_blaster.get())) left++;
			if(left != 0) problems.add("breaking " + v[0] + "," + v[1] + "," + v[2] + " left " + left + " blocks");
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Upstream's {@code breakBlock} scatters the inventory, and this port's {@code BlockDummyable} does not. */
	@GameTest(template = EMPTY)
	public static void breakingTheMachineScattersEverythingInIt(GameTestHelper helper) {

		Rig rig = place(helper);
		arm(rig.te);
		for(int i = 5; i < 20; i++) rig.te.setItem(i, stack(i % 2 == 0 ? Items.DIRT : Items.GOLD_INGOT));

		// Broken from a corner, the way a player who never found the core would.
		helper.getLevel().destroyBlock(rig.abs(1, 0, -1), false);

		AABB area = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 0, 0))), Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(5, 5, 5))));
		int dropped = 0;
		for(ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) dropped += e.getItem().getCount();

		helper.assertTrue(dropped == 20, "a full machine dropped " + dropped + " items, not 20");
		helper.succeed();
	}

	// ---- the slots -------------------------------------------------------------------------

	/** Slot 0 takes only the solinium core, slots 1-4 only the two lenses, and the fifteen anything. */
	@GameTest(template = EMPTY)
	public static void slotZeroTakesOnlyACoreAndOneToFourOnlyLenses(GameTestHelper helper) {

		SolBlasterTE te = place(helper).te;
		List<String> problems = new ArrayList<>();

		if(te.getContainerSize() != 20) problems.add(te.getContainerSize() + " slots, not 20");

		for(int slot = 0; slot < 20; slot++) {
			boolean wantCore = slot == 0;
			boolean wantLens = slot >= 1 && slot <= 4;
			boolean wantAny = slot >= 5;
			if(te.canPlaceItem(slot, stack(core())) != (wantCore || wantAny)) problems.add("the core in " + slot);
			if(te.canPlaceItem(slot, stack(earlyLens())) != (wantLens || wantAny)) problems.add("the early lens in " + slot);
			if(te.canPlaceItem(slot, stack(lens())) != (wantLens || wantAny)) problems.add("the lens in " + slot);
			if(te.canPlaceItem(slot, stack(Items.DIRT)) != wantAny) problems.add("dirt in " + slot);
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Each slot holds a single item, whatever the item's own stack size. */
	@GameTest(template = EMPTY)
	public static void everySlotHoldsExactlyOneItem(GameTestHelper helper) {

		SolBlasterTE te = place(helper).te;

		helper.assertTrue(te.getMaxStackSize() == 1, "the slot limit is " + te.getMaxStackSize());
		te.setItem(6, new ItemStack(Items.DIRT, 64));
		helper.assertTrue(te.getItem(6).getCount() == 1, "a stack of 64 was kept whole: " + te.getItem(6));

		var menu = new SolBlasterMenu(1, mockPlayer(helper).getInventory(), te);
		helper.assertTrue(menu.slots.get(6).getMaxStackSize() == 1, "the menu slot allows " + menu.slots.get(6).getMaxStackSize());
		te.listeners.clear();
		helper.succeed();
	}

	/**
	 * A hopper puts into the fifteen only what has a <em>recipe</em>, takes out of them only what has
	 * not, and never takes from the core or the lenses; slots 0-4 take only their own items from it.
	 * Every one of the six faces reaches all twenty.
	 */
	@GameTest(template = EMPTY)
	public static void aHopperPutsInOnlyRecipeInputsAndTakesOutOnlyWhatIsNot(GameTestHelper helper) {

		SolBlasterTE te = place(helper).te;
		List<String> problems = new ArrayList<>();

		ItemStack ingot = stack(ModItems.ingot_schrabidium.get());      // a recipe input
		ItemStack nugget = stack(ModItems.nugget_euphemium.get());      // what it makes: not an input
		ItemStack dirt = stack(Items.DIRT);                             // not an input
		ItemStack plate = stack(ModItems.ingot_solinium.get());         // a recipe input (solinium)
		ItemStack egg = stack(ModItems.egg_balefire.get());             // a fallback only: not an input

		for(Direction side : Direction.values()) {

			if(te.getSlotsForFace(side).length != 20) problems.add(side + " reaches " + te.getSlotsForFace(side).length + " slots");

			for(int slot = 5; slot < 20; slot++) {
				if(!te.canPlaceItemThroughFace(slot, ingot, side)) problems.add(side + " refused a schrabidium ingot into " + slot);
				if(!te.canPlaceItemThroughFace(slot, plate, side)) problems.add(side + " refused a solinium ingot into " + slot);
				if(te.canPlaceItemThroughFace(slot, dirt, side)) problems.add(side + " put dirt into " + slot);
				if(te.canPlaceItemThroughFace(slot, nugget, side)) problems.add(side + " put an euphemium nugget into " + slot);
				if(te.canPlaceItemThroughFace(slot, egg, side)) problems.add(side + " put a balefire egg into " + slot);

				if(te.canTakeItemThroughFace(slot, ingot, side)) problems.add(side + " took a schrabidium ingot out of " + slot);
				if(!te.canTakeItemThroughFace(slot, nugget, side)) problems.add(side + " could not take a nugget out of " + slot);
				if(!te.canTakeItemThroughFace(slot, dirt, side)) problems.add(side + " could not take dirt out of " + slot);
				if(!te.canTakeItemThroughFace(slot, egg, side)) problems.add(side + " could not take a balefire egg out of " + slot);
			}

			if(!te.canPlaceItemThroughFace(0, stack(core()), side)) problems.add(side + " refused a core into slot 0");
			if(te.canPlaceItemThroughFace(0, ingot, side)) problems.add(side + " put an ingot into slot 0");
			for(int slot = 1; slot <= 4; slot++) {
				if(!te.canPlaceItemThroughFace(slot, stack(lens()), side)) problems.add(side + " refused a lens into " + slot);
				if(!te.canPlaceItemThroughFace(slot, stack(earlyLens()), side)) problems.add(side + " refused an early lens into " + slot);
				if(te.canPlaceItemThroughFace(slot, stack(core()), side)) problems.add(side + " put a core into " + slot);
			}
			for(int slot = 0; slot <= 4; slot++) {
				if(te.canTakeItemThroughFace(slot, stack(core()), side) || te.canTakeItemThroughFace(slot, stack(lens()), side))
					problems.add(side + " took the core or a lens out of " + slot);
				if(te.canTakeItemThroughFace(slot, dirt, side)) problems.add(side + " took dirt out of " + slot);
			}
		}

		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Real hoppers, through the corner proxies: one beside a corner pushes a schrabidium ingot in and
	 * keeps a dirt; one beneath a corner pulls the euphemium nugget out of slot 6 and leaves the
	 * ingot in slot 7.
	 */
	@GameTest(template = BIG, timeoutTicks = 100)
	public static void realHoppersFollowTheSameRules(GameTestHelper helper) {

		Rig rig = place(helper, new BlockPos(4, 2, 4), Direction.SOUTH);

		rig.te.setItem(6, stack(ModItems.nugget_euphemium.get()));
		rig.te.setItem(7, stack(ModItems.ingot_schrabidium.get()));

		BlockPos feeder = rig.at(2, 0, 1);   // beside the (1, 0, 1) corner, pushing west into it
		BlockPos spoiler = rig.at(2, 0, -1); // the same, with something it may not put in
		BlockPos taker = rig.at(1, -1, 1);   // under the corner, pulling up out of it

		helper.setBlock(feeder, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(spoiler, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(taker, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

		((HopperBlockEntity) helper.getBlockEntity(feeder)).setItem(0, stack(ModItems.ingot_solinium.get()));
		((HopperBlockEntity) helper.getBlockEntity(spoiler)).setItem(0, stack(Items.DIRT));

		helper.succeedWhen(() -> {
			int inputs = 0;
			for(int i = 5; i < 20; i++) if(rig.te.getItem(i).is(ModItems.ingot_solinium.get())) inputs++;
			helper.assertTrue(inputs == 1, "the feeding hopper has not put its ingot in (" + inputs + ")");

			helper.assertTrue(((HopperBlockEntity) helper.getBlockEntity(spoiler)).getItem(0).is(Items.DIRT),
					"the hopper holding dirt gave it up, or lost it");
			helper.assertTrue(rig.te.getItem(7).is(ModItems.ingot_schrabidium.get()), "a hopper took a recipe input out");
			helper.assertTrue(rig.te.getItem(6).isEmpty(), "the pulling hopper has not taken the nugget");
			helper.assertTrue(((HopperBlockEntity) helper.getBlockEntity(taker)).getItem(0).is(ModItems.nugget_euphemium.get()),
					"the nugget is not in the pulling hopper");
			for(int i = 5; i < 20; i++)
				helper.assertTrue(!rig.te.getItem(i).is(Items.DIRT), "dirt got into slot " + i);
		});
	}

	/** Slot 0-4 take their items from the player's hand through the menu's own rules, and nothing else. */
	@GameTest(template = EMPTY)
	public static void theMenuLaysOutUpstreamsSlots(GameTestHelper helper) {

		Rig rig = place(helper);
		try {
			ServerPlayer player = mockPlayer(helper);
			SolBlasterMenu menu = new SolBlasterMenu(1, player.getInventory(), rig.te);

			helper.assertTrue(menu.getType() == LeafiaMenuTypes.SOL_BLASTER.get(), "the menu has the wrong type");
			helper.assertTrue(menu.slots.size() == 20 + 36, "56 slots, got " + menu.slots.size());

			int[][] want = { { 23, 23 }, { 8, 77 }, { 26, 77 }, { 8, 95 }, { 26, 95 } };
			for(int i = 0; i < 5; i++)
				helper.assertTrue(menu.slots.get(i).x == want[i][0] && menu.slots.get(i).y == want[i][1],
						"slot " + i + " is at " + menu.slots.get(i).x + "," + menu.slots.get(i).y);

			// The grid fills column by column, slot 5 + x + 5y: the first column is 5, 10, 15.
			for(int x = 0; x < 5; x++)
				for(int y = 0; y < 3; y++) {
					var s = menu.slots.get(5 + x * 3 + y); // added column by column
					helper.assertTrue(s.x == 80 + x * 18 && s.y == 59 + y * 18 && s.getContainerSlot() == 5 + x + y * 5,
							"grid cell " + x + "," + y + " is slot " + s.getContainerSlot() + " at " + s.x + "," + s.y);
				}

			helper.assertTrue(menu.slots.get(20).x == 8 && menu.slots.get(20).y == 145, "the inventory starts at " + menu.slots.get(20).x + "," + menu.slots.get(20).y);
			helper.assertTrue(menu.slots.get(47).x == 8 && menu.slots.get(47).y == 203, "the hotbar starts at " + menu.slots.get(47).x + "," + menu.slots.get(47).y);

			helper.assertTrue(menu.slots.get(0).mayPlace(stack(core())) && !menu.slots.get(0).mayPlace(stack(lens())), "slot 0's rule");
			helper.assertTrue(menu.slots.get(3).mayPlace(stack(lens())) && !menu.slots.get(3).mayPlace(stack(Items.DIRT)), "slot 3's rule");
			helper.assertTrue(menu.slots.get(12).mayPlace(stack(Items.DIRT)), "the grid refused dirt by hand");

			helper.assertTrue(rig.te.listeners.contains(player), "the server's menu did not register its player");
			menu.removed(player);
			helper.assertTrue(rig.te.listeners.isEmpty(), "removing the menu left a listener");

			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			buf.writeBlockPos(rig.abs(0, 0, 0));
			SolBlasterMenu found = new SolBlasterMenu(2, player.getInventory(), buf);
			helper.assertTrue(found.blaster == rig.te, "the position did not find the machine");
			found.removed(player);

			RegistryFriendlyByteBuf wrong = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			wrong.writeBlockPos(helper.absolutePos(new BlockPos(0, 4, 0)));
			SolBlasterMenu none = new SolBlasterMenu(3, player.getInventory(), wrong);
			helper.assertTrue(none.blaster == null && none.slots.size() == 56, "an orphaned menu is not the empty one");
			helper.succeed();
		} finally {
			rig.te.listeners.clear();
		}
	}

	/** Shift-click puts a core in slot 0, a lens in the first free of 1-4, anything else in the grid, and takes it back. */
	@GameTest(template = EMPTY)
	public static void shiftClickSortsItemsIntoTheirSlotsAndBack(GameTestHelper helper) {

		Rig rig = place(helper);
		try {
			ServerPlayer player = mockPlayer(helper);
			SolBlasterMenu menu = new SolBlasterMenu(1, player.getInventory(), rig.te);
			int hotbar = 20 + 27;

			menu.slots.get(hotbar).set(stack(core()));
			menu.slots.get(hotbar + 1).set(stack(lens()));
			menu.slots.get(hotbar + 2).set(stack(earlyLens()));
			menu.slots.get(hotbar + 3).set(stack(Items.DIRT));
			menu.slots.get(hotbar + 4).set(new ItemStack(Items.DIRT, 5));
			for(int i = 0; i < 5; i++) menu.quickMoveStack(player, hotbar + i);

			helper.assertTrue(rig.te.getItem(0).is(core()), "the core did not go to slot 0: " + rig.te.getItem(0));
			helper.assertTrue(rig.te.getItem(1).is(lens()), "the first lens did not go to slot 1: " + rig.te.getItem(1));
			helper.assertTrue(rig.te.getItem(2).is(earlyLens()), "the second did not go to slot 2: " + rig.te.getItem(2));
			helper.assertTrue(rig.te.getItem(5).is(Items.DIRT) && rig.te.getItem(6).is(Items.DIRT),
					"dirt did not go to the grid: " + rig.te.getItem(5) + ", " + rig.te.getItem(6));
			int dirt = 0;
			for(int i = 5; i < 20; i++) if(rig.te.getItem(i).is(Items.DIRT)) dirt++;
			StringBuilder all = new StringBuilder();
			for(int i = 0; i < 20; i++) all.append(i).append('=').append(rig.te.getItem(i)).append(' ');
			helper.assertTrue(dirt == 6, "six dirt were loaded one to a slot, got " + dirt + ": " + all);

			menu.quickMoveStack(player, 0);
			helper.assertTrue(rig.te.getItem(0).isEmpty(), "the core stayed in the machine");
			boolean held = false;
			for(ItemStack s : player.getInventory().items) if(s.is(core())) held = true;
			helper.assertTrue(held, "the player does not hold the core after the round trip");
			menu.removed(player);
			helper.succeed();
		} finally {
			rig.te.listeners.clear();
		}
	}

	/** A menu is valid for a machine that is still there and a player in reach. */
	@GameTest(template = EMPTY)
	public static void theMenuIsValidOnlyForAPlayerInReach(GameTestHelper helper) {

		Rig rig = place(helper);
		try {
			ServerPlayer player = mockPlayer(helper);
			SolBlasterMenu menu = new SolBlasterMenu(1, player.getInventory(), rig.te);
			Vec3 at = Vec3.atCenterOf(rig.abs(0, 0, 0));
			player.setPos(at.x, at.y, at.z);
			helper.assertTrue(menu.stillValid(player), "a player beside the machine cannot use it");
			player.setPos(at.x + 100, at.y, at.z);
			helper.assertTrue(!menu.stillValid(player), "a player a hundred blocks off is in reach");
			menu.removed(player);
			helper.succeed();
		} finally {
			rig.te.listeners.clear();
		}
	}

	/**
	 * Runs a call and reports whether it got as far as sending the screen payload to a mock
	 * connection, which refuses every payload it did not negotiate (the same wall
	 * {@code LeafiaSlopTests} reads as "the opener opened").
	 */
	private static boolean reachedSending(Runnable call) {
		try {
			call.run();
			return false;
		} catch(UnsupportedOperationException e) {
			return e.getMessage() != null && e.getMessage().contains("neoforge:advanced_open_screen");
		}
	}

	/** A click on any block of the machine opens the screen for the core; a sneaking click does nothing but is consumed. */
	@GameTest(template = EMPTY)
	public static void aClickOnAnyPartOpensTheScreenUnlessSneaking(GameTestHelper helper) {

		Rig rig = place(helper);
		try {
			List<String> problems = new ArrayList<>();
			int[][] parts = { { 0, 0, 0 }, { 1, 1, 0 }, { 1, 0, 1 }, { 0, 4, 0 } };

			for(int[] p : parts) {
				ServerPlayer player = mockPlayer(helper);
				BlockPos abs = rig.abs(p[0], p[1], p[2]);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
				BlockState state = helper.getLevel().getBlockState(abs);

				boolean opened = reachedSending(() -> state.useWithoutItem(helper.getLevel(), player, hit));
				if(!opened) problems.add("a click on " + p[0] + "," + p[1] + "," + p[2] + " did not open the screen");
				if(!rig.te.listeners.contains(player)) problems.add("the menu from " + p[0] + "," + p[1] + "," + p[2] + " was not the core's");
				rig.te.listeners.clear();

				player.setShiftKeyDown(true);
				InteractionResult[] result = new InteractionResult[1];
				boolean sneaked = reachedSending(() -> result[0] = state.useWithoutItem(helper.getLevel(), player, hit));
				if(sneaked || !rig.te.listeners.isEmpty()) problems.add("a sneaking click on " + p[0] + "," + p[1] + "," + p[2] + " opened it");
				if(result[0] == null || !result[0].consumesAction()) problems.add("a sneaking click was not consumed: " + result[0]);
				rig.te.listeners.clear();
			}

			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		} finally {
			rig.te.listeners.clear();
		}
	}

	// ---- redstone --------------------------------------------------------------------------

	/** Any block of the machine with a signal beside it is "active", and loses it when the signal does. */
	@GameTest(template = EMPTY)
	public static void everyBlockOfTheMachineReportsItsRedstone(GameTestHelper helper) {

		Rig rig = place(helper);
		List<String> problems = new ArrayList<>();

		helper.assertTrue(rig.te.activePoses.isEmpty(), "a fresh machine is already powered");

		// (offset of the machine block, offset of the redstone block beside it)
		int[][] cases = { { 1, 1, 0, 2, 1, 0 }, { 0, 4, 0, 0, 5, 0 }, { 1, 0, 1, 2, 0, 1 }, { -1, 2, -1, -2, 2, -1 }, { 0, 3, 0, 1, 3, 0 } };

		for(int[] c : cases) {
			BlockPos part = rig.at(c[0], c[1], c[2]);
			BlockPos source = rig.at(c[3], c[4], c[5]);

			helper.setBlock(source, Blocks.REDSTONE_BLOCK);
			if(!rig.te.activePoses.contains(helper.absolutePos(part))) problems.add(part + " did not report a signal");

			helper.setBlock(source, Blocks.AIR);
			if(rig.te.activePoses.contains(helper.absolutePos(part))) problems.add(part + " kept a signal that was removed");
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Two powered blocks are two entries, and the set empties only when both are cut. */
	@GameTest(template = EMPTY)
	public static void theMachineIsPoweredWhileAnyBlockOfItIs(GameTestHelper helper) {

		Rig rig = place(helper);

		BlockPos a = rig.at(2, 0, 0);
		BlockPos b = rig.at(-2, 1, 0);
		helper.setBlock(a, Blocks.REDSTONE_BLOCK);
		helper.setBlock(b, Blocks.REDSTONE_BLOCK);
		helper.assertTrue(rig.te.activePoses.size() == 2, "two sources made " + rig.te.activePoses.size() + " entries");

		helper.setBlock(a, Blocks.AIR);
		helper.assertTrue(rig.te.activePoses.size() == 1, "cutting one left " + rig.te.activePoses.size());
		helper.setBlock(b, Blocks.AIR);
		helper.assertTrue(rig.te.activePoses.isEmpty(), "cutting both left " + rig.te.activePoses.size());
		helper.succeed();
	}

	// ---- the door and the firing -----------------------------------------------------------

	/**
	 * While anyone has the screen up the door climbs a tick at a time to fifty and stops; once they
	 * leave it falls the same way, and the cooldown is held at fifteen the whole time it is open.
	 */
	@GameTest(template = EMPTY)
	public static void theDoorOpensWhileTheScreenIsUpAndClosesAfter(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		try {
			te.listeners.add(mockPlayer(helper));
			te.doorPos = 0;
			te.cooldown = 0;

			for(int i = 1; i <= 60; i++) {
				te.updateEntity();
				int want = Math.min(50, i);
				helper.assertTrue(te.doorPos == want, "after " + i + " ticks open the door is at " + te.doorPos + ", not " + want);
				helper.assertTrue(te.cooldown == 15, "the cooldown is " + te.cooldown + " while the door is open");
			}

			te.listeners.clear();
			for(int i = 1; i <= 50; i++) {
				te.updateEntity();
				helper.assertTrue(te.doorPos == 50 - i, "after " + i + " ticks closing the door is at " + te.doorPos);
			}
			te.updateEntity();
			helper.assertTrue(te.doorPos == 0, "the door went below zero: " + te.doorPos);
			helper.succeed();
		} finally {
			te.listeners.clear();
		}
	}

	/** A player who left the server with the screen up is not a listener for ever. */
	@GameTest(template = EMPTY)
	public static void aPlayerWhoDisconnectedDoesNotHoldTheDoorOpen(GameTestHelper helper) {

		SolBlasterTE te = place(helper).te;
		try {
			ServerPlayer gone = mockPlayer(helper);
			te.listeners.add(gone);
			te.updateEntity();
			helper.assertTrue(te.doorPos == 1, "a listener did not open the door");

			gone.discard();
			te.updateEntity();
			helper.assertTrue(te.listeners.isEmpty() && te.doorPos == 0, "a removed player still holds the door: " + te.listeners.size() + " listeners, door " + te.doorPos);
			helper.succeed();
		} finally {
			te.listeners.clear();
		}
	}

	/**
	 * The sequence a player sees: close the screen, the door swings shut, fifteen more ticks pass,
	 * and then it goes off - and not before. Counted from the tick the door reaches zero.
	 */
	@GameTest(template = EMPTY)
	public static void itFiresFifteenTicksAfterTheDoorShuts(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		try {
			arm(te);
			te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
			power(rig);

			te.listeners.add(mockPlayer(helper));
			for(int i = 0; i < 3; i++) te.updateEntity();
			helper.assertTrue(te.doorPos == 3, "the door is at " + te.doorPos);
			te.listeners.clear();

			// Three ticks to shut: the door is at 2, 1, then 0.
			for(int i = 0; i < 3; i++) {
				te.updateEntity();
				helper.assertTrue(te.isArmed(), "it went off with the door still moving, on closing tick " + (i + 1));
			}
			helper.assertTrue(te.doorPos == 0, "the door did not shut");

			// The shutting tick itself counted as the first of the fifteen waits.
			for(int i = 1; i <= 14; i++) {
				te.updateEntity();
				helper.assertTrue(te.isArmed(), "it went off " + i + " ticks after the door shut, before fifteen");
			}

			te.updateEntity();
			helper.assertTrue(!te.isArmed() && te.getItem(0).isEmpty(), "it did not go off on the fifteenth tick after the door shut");
			helper.assertTrue(te.getItem(5).is(ModItems.nugget_euphemium.get()), "the ingot did not become a nugget: " + te.getItem(5));
			helper.succeed();
		} finally {
			te.listeners.clear();
		}
	}

	/** A machine whose door has never moved fires on the very tick it is powered, which is how a hopper loads it. */
	@GameTest(template = EMPTY)
	public static void aMachineThatWasNeverOpenedFiresAtOnce(GameTestHelper helper) {

		Rig rig = place(helper);
		arm(rig.te);
		rig.te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
		rig.te.cooldown = 0;
		rig.te.doorPos = 0;

		rig.te.updateEntity();
		helper.assertTrue(rig.te.getItem(5).is(ModItems.ingot_schrabidium.get()), "it fired with no power");

		power(rig);
		rig.te.updateEntity();
		helper.assertTrue(rig.te.getItem(5).is(ModItems.nugget_euphemium.get()), "it did not fire the tick it was powered: " + rig.te.getItem(5));
		helper.succeed();
	}

	/** Missing the core, or any one of the four lenses, or the power: nothing happens. */
	@GameTest(template = EMPTY)
	public static void itNeedsTheCoreAllFourLensesAndPower(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		power(rig);
		List<String> problems = new ArrayList<>();

		for(int missing = 0; missing < 5; missing++) {
			arm(te);
			te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
			te.setItem(missing, ItemStack.EMPTY);
			te.cooldown = 0;
			te.updateEntity();
			if(!te.getItem(5).is(ModItems.ingot_schrabidium.get())) problems.add("it fired without slot " + missing);
			if(te.isArmed()) problems.add("slot " + missing + " empty yet armed");
		}

		// A lens of either kind in every position counts; a core in a lens slot does not.
		for(Item first : new Item[] { earlyLens(), lens() }) {
			arm(te);
			te.setItem(1, stack(first));
			if(!te.isArmed()) problems.add("a " + BuiltInRegistries.ITEM.getKey(first) + " in slot 1 does not arm it");
		}
		te.slots[3] = stack(Items.DIRT);
		if(te.isArmed()) problems.add("dirt for a lens arms it");

		helper.setBlock(rig.at(2, 0, 0), Blocks.AIR);
		arm(te);
		te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
		te.cooldown = 0;
		te.updateEntity();
		if(!te.getItem(5).is(ModItems.ingot_schrabidium.get())) problems.add("it fired with the power removed");

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The core and the four lenses are spent; nothing else in the first five slots survives, and the grid is transmuted. */
	@GameTest(template = EMPTY)
	public static void theDetonationSpendsTheCoreAndLensesAndProcessesTheGrid(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		arm(te);
		te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
		te.setItem(6, stack(Items.IRON_INGOT));
		te.setItem(7, stack(Items.APPLE));
		te.setItem(19, stack(ModItems.ingot_uranium.get()));
		power(rig);
		te.cooldown = 0;

		te.updateEntity();

		for(int i = 0; i <= 4; i++) helper.assertTrue(te.getItem(i).isEmpty(), "slot " + i + " survived: " + te.getItem(i));
		helper.assertTrue(te.getItem(5).is(ModItems.nugget_euphemium.get()), "slot 5: " + te.getItem(5));
		helper.assertTrue(te.getItem(6).is(Items.IRON_INGOT), "iron changed: " + te.getItem(6));
		helper.assertTrue(te.getItem(7).isEmpty(), "the apple survived: " + te.getItem(7));
		helper.assertTrue(te.getItem(19).is(ModBlocks.sellafield_slaked.get().asItem()), "uranium: " + te.getItem(19));
		helper.assertTrue(!te.isArmed(), "it is still armed after going off");

		// And it stays quiet: nothing left to arm it, so a second tick changes nothing.
		te.updateEntity();
		helper.assertTrue(te.getItem(5).is(ModItems.nugget_euphemium.get()), "a second tick changed the nugget");
		helper.succeed();
	}

	/** The burst is upstream's {@code LargeExplode(5, 4)} with motion (0, 1, 0), in the base mod's own keys. */
	@GameTest(template = EMPTY)
	public static void theBurstIsUpstreamsFiveAndFour(GameTestHelper helper) {

		CompoundTag tag = SolBlasterTE.explosionTag();
		helper.assertTrue("vanillaExt".equals(tag.getString("type")), "type " + tag.getString("type"));
		helper.assertTrue("largeexplode".equals(tag.getString("mode")), "mode " + tag.getString("mode"));
		helper.assertTrue(tag.getByte("count") == 5, "count " + tag.getByte("count"));
		helper.assertTrue(tag.getFloat("size") == 4F, "size " + tag.getFloat("size"));
		helper.assertTrue(tag.getDouble("mX") == 0D && tag.getDouble("mY") == 1D && tag.getDouble("mZ") == 0D, "motion");
		helper.succeed();
	}

	// ---- every transmutation ---------------------------------------------------------------

	private static Item output(ItemStack in) {
		ItemStack out = SolBlasterRecipes.processItem(in);
		return out.isEmpty() ? null : out.getItem();
	}

	private static Item resource(String name) {
		var item = LeafiaResourceItems.get(name);
		return item == null ? null : item.get();
	}

	/**
	 * The nine recipes, one by one, against upstream's {@code register()}: what each makes, that it
	 * makes exactly one, and that a hopper may feed each in. {@code SA326} and {@code SA327} are
	 * schrabidium and solinium; {@code FS} and {@code FSALLOY} are fissium and fissite.
	 */
	@GameTest(template = EMPTY)
	public static void everyRecipeIsUpstreams(GameTestHelper helper) {

		SolBlasterRecipes.register();

		Object[][] recipes = {
				{ ModItems.ingot_schrabidium.get(), ModItems.nugget_euphemium.get() },
				{ ModItems.ingot_solinium.get(), ModItems.nugget_euphemium.get() },
				{ ModBlocks.block_schrabidium.get().asItem(), ModItems.ingot_euphemium.get() },
				{ ModBlocks.block_solinium.get().asItem(), ModItems.ingot_euphemium.get() },
				{ resource("ingot_fissium"), resource("ingot_chydalium") },
				{ resource("billet_fissium"), resource("billet_chydalium") },
				{ resource("ingot_fissite"), resource("ingot_mysticite") },
				{ resource("blanket_fissite"), resource("blanket_mysticite") },
				{ resource("blanket_fissite_bundle"), resource("blanket_mysticite_bundle") },
		};

		List<String> problems = new ArrayList<>();
		for(Object[] r : recipes) {
			Item in = (Item) r[0];
			Item want = (Item) r[1];
			if(in == null || want == null) { problems.add("a recipe's item does not exist: " + in + " -> " + want); continue; }
			String name = BuiltInRegistries.ITEM.getKey(in).getPath();
			ItemStack out = SolBlasterRecipes.processItem(new ItemStack(in));
			if(!out.is(want)) problems.add(name + " became " + out + ", not " + BuiltInRegistries.ITEM.getKey(want).getPath());
			if(out.getCount() != 1) problems.add(name + " made " + out.getCount());
			if(!SolBlasterRecipes.isValidInput(new ItemStack(in))) problems.add(name + " is not an input");
		}

		helper.assertTrue(SolBlasterRecipes.RECIPES.size() == 9, "upstream registers nine recipes, got " + SolBlasterRecipes.RECIPES.size());
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The five fallbacks: the fissite plate, light receiver and cells become mysticite ones, the
	 * balefire egg and its shard are deleted - and none of them is an input, so a hopper cannot feed
	 * them in and the comparator does not count them. The receiver row was held until the addon's
	 * materials landed (D-3050).
	 */
	@GameTest(template = EMPTY)
	public static void theFallbacksTransmuteOrDeleteAndAreNotInputs(GameTestHelper helper) {

		SolBlasterRecipes.register();
		List<String> problems = new ArrayList<>();

		Object[][] fallbacks = {
				{ resource("plate_fissite"), resource("plate_mysticite") },
				{ LeafiaMats.item("part_receiver_light_fissite"), LeafiaMats.item("part_receiver_light_mysticite") },
				{ resource("cells_fissite"), resource("cells_mysticite") },
				{ ModItems.egg_balefire.get(), null },
				{ ModItems.egg_balefire_shard.get(), null },
		};

		for(Object[] f : fallbacks) {
			Item in = (Item) f[0];
			if(in == null) { problems.add("a fallback's input does not exist"); continue; }
			String name = BuiltInRegistries.ITEM.getKey(in).getPath();
			ItemStack out = SolBlasterRecipes.processItem(new ItemStack(in));
			if(f[1] == null ? !out.isEmpty() : !out.is((Item) f[1])) problems.add(name + " became " + out);
			if(SolBlasterRecipes.isValidInput(new ItemStack(in))) problems.add(name + " is an input");
		}

		helper.assertTrue(SolBlasterRecipes.FALLBACK.size() == 5, "upstream registers five fallbacks, got " + SolBlasterRecipes.FALLBACK.size());
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Upstream's nuclear-fuel rule, asked after the shield and before the organics and the
	 * radioactivity (D-3050): an RTG pellet becomes its spent pellet rather than slaked sellafield,
	 * and an addon fuel rod becomes its decay product at once - however little of it was burnt -
	 * keeping its data with {@code depletion} reset. A rod with no decay product is still slaked.
	 */
	@GameTest(template = EMPTY)
	public static void nuclearFuelsBecomeWhatTheyDecayInto(GameTestHelper helper) {

		SolBlasterRecipes.register();

		var pellet = (com.hbm.items.machine.ItemRTGPellet) ModItems.pellet_rtg.get();
		ItemStack spent = SolBlasterRecipes.processItem(new ItemStack(pellet));
		helper.assertTrue(!spent.isEmpty() && ItemStack.isSameItem(spent, pellet.getDecayItem()),
				"an RTG pellet became " + spent + ", not its spent pellet " + pellet.getDecayItem());

		LeafiaRodItem u238 = LeafiaRodItem.fromResourceMap.get("leafia_rod_u238");
		LeafiaRodItem depleted = LeafiaRodItem.fromResourceMap.get("leafia_rod_depleteduranium238");
		helper.assertTrue(u238 != null && depleted != null, "the u238 rod and its decay product are not registered");
		ItemStack rod = new ItemStack(u238);
		CompoundTag data = new CompoundTag();
		data.putDouble("depletion", 12.5);
		data.putDouble("heat", 345);
		LeafiaRodItem.putData(rod, data);
		ItemStack out = SolBlasterRecipes.processItem(rod);
		CompoundTag got = LeafiaRodItem.data(out);
		helper.assertTrue(out.is(depleted) && out.getCount() == 1, "a u238 rod became " + out);
		helper.assertTrue(got != null && got.getDouble("depletion") == 0 && got.getDouble("heat") == 345,
				"the rod's data did not carry over with depletion reset: " + got);

		ItemStack fresh = SolBlasterRecipes.processItem(new ItemStack(u238));
		helper.assertTrue(fresh.is(depleted) && LeafiaRodItem.data(fresh) != null && LeafiaRodItem.data(fresh).getDouble("depletion") == 0,
				"a fresh rod with no data became " + fresh);

		helper.succeed();
	}

	/**
	 * A recipe answers before the radioactivity rule: schrabidium is radioactive and becomes a
	 * nugget, not sellafield. And a fissite shield becomes a mysticite one, new, before anything.
	 */
	@GameTest(template = EMPTY)
	public static void recipesAnswerBeforeTheRules(GameTestHelper helper) {

		helper.assertTrue(output(stack(ModItems.ingot_schrabidium.get())) == ModItems.nugget_euphemium.get(),
				"a radioactive recipe input became " + output(stack(ModItems.ingot_schrabidium.get())));

		var fissite = LeafiaItems.shields.get(Kind.FISSITE);
		var mysticite = LeafiaItems.shields.get(Kind.MYSTICITE);
		helper.assertTrue(fissite != null && mysticite != null, "the shields are not registered");

		ItemStack worn = new ItemStack(fissite.get());
		worn.setDamageValue(100);
		ItemStack out = SolBlasterRecipes.processItem(worn);
		helper.assertTrue(out.is(mysticite.get()) && out.getDamageValue() == 0 && out.getCount() == 1,
				"a worn fissite shield became " + out + " damage " + out.getDamageValue());
		helper.succeed();
	}

	/** Anything with a radiation hazard becomes a block of slaked sellafield, and nothing else does. */
	@GameTest(template = EMPTY)
	public static void aRadioactiveItemBecomesSlakedSellafield(GameTestHelper helper) {

		Item slaked = ModBlocks.sellafield_slaked.get().asItem();
		List<String> problems = new ArrayList<>();

		// The base mod's radiation type, and the addon's own (ingot_nc279 carries beta and gamma).
		List<Item> radioactive = new ArrayList<>(List.of(ModItems.ingot_uranium.get(), ModItems.ingot_plutonium.get()));
		Item nc279 = resource("ingot_nc279");
		helper.assertTrue(nc279 != null, "ingot_nc279 is not registered");
		radioactive.add(nc279);

		for(Item item : radioactive) {
			ItemStack out = SolBlasterRecipes.processItem(new ItemStack(item));
			if(!out.is(slaked) || out.getCount() != 1)
				problems.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " became " + out);
		}

		for(Item item : new Item[] { Items.IRON_INGOT, Items.DIAMOND, Items.COBBLESTONE, Items.GOLD_INGOT, ModItems.ingot_steel.get() }) {
			ItemStack in = new ItemStack(item);
			if(SolBlasterRecipes.processItem(in) != in)
				problems.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " was changed");
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Food and the five organic materials are deleted; what only sounds or looks organic is left alone. */
	@GameTest(template = EMPTY)
	public static void organicsAreDeleted(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();

		Item[] gone = {
				Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.ROTTEN_FLESH,          // food
				Items.OAK_PLANKS, Items.OAK_LOG, Items.OAK_SLAB, Items.CHEST, Items.BOOKSHELF, // wood
				Items.RED_MUSHROOM_BLOCK,                                                 // upstream's huge mushroom is wood
				Items.POPPY, Items.OAK_SAPLING, Items.RED_MUSHROOM, Items.LILY_PAD,       // plants
				Items.CACTUS, Items.COBWEB, Items.DRAGON_EGG,                             // cactus, web, dragon egg
				ModBlocks.waste_planks.get().asItem(), ModBlocks.frozen_planks.get().asItem(), ModBlocks.crate.get().asItem(), // the base mod's wood
		};
		for(Item item : gone) {
			ItemStack out = SolBlasterRecipes.processItem(new ItemStack(item));
			if(!out.isEmpty()) problems.add(BuiltInRegistries.ITEM.getKey(item) + " survived as " + out);
		}

		Item[] stays = {
				Items.COBBLESTONE, Items.DIRT, Items.GRASS_BLOCK, Items.OAK_LEAVES, Items.GLASS, Items.WHITE_WOOL, // other materials
				Items.SHORT_GRASS, Items.FERN, Items.TORCH, Items.LEVER, Items.OAK_BUTTON, Items.WHITE_BED,       // sound or look wooden or planty, were not
				Items.WHEAT_SEEDS, Items.STICK, Items.IRON_INGOT,                                                 // seeds are not blocks
				ModBlocks.block_steel.get().asItem(),
		};
		for(Item item : stays) {
			ItemStack in = new ItemStack(item);
			if(SolBlasterRecipes.processItem(in) != in)
				problems.add(BuiltInRegistries.ITEM.getKey(item) + " was changed");
		}

		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** An empty slot stays empty and nothing is invented for it. */
	@GameTest(template = EMPTY)
	public static void anEmptyStackIsLeftAlone(GameTestHelper helper) {
		helper.assertTrue(SolBlasterRecipes.processItem(ItemStack.EMPTY).isEmpty(), "an empty stack became something");
		helper.assertTrue(!SolBlasterRecipes.isValidInput(ItemStack.EMPTY), "an empty stack is an input");
		helper.succeed();
	}

	// ---- the comparator --------------------------------------------------------------------

	/** The signal is the number of input slots holding something with a recipe, from every block of the machine. */
	@GameTest(template = EMPTY)
	public static void theComparatorCountsInputsWithARecipe(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		BlockState core = helper.getBlockState(rig.core);
		helper.assertTrue(core.hasAnalogOutputSignal(), "the block has no analog output");

		List<String> problems = new ArrayList<>();
		int[][] parts = { { 0, 0, 0 }, { 1, 1, 0 }, { 1, 0, 1 }, { 0, 4, 0 } };

		int expect = 0;
		for(int i = 5; i < 20; i++) {
			// Alternating: a recipe input, dirt, an egg (a fallback, so not counted), a nugget (an output).
			ItemStack s = switch(i % 4) {
				case 0 -> stack(ModItems.ingot_schrabidium.get());
				case 1 -> stack(Items.DIRT);
				case 2 -> stack(ModItems.egg_balefire.get());
				default -> stack(ModItems.nugget_euphemium.get());
			};
			te.setItem(i, s);
			if(i % 4 == 0) expect++;

			for(int[] p : parts) {
				int got = helper.getBlockState(rig.at(p[0], p[1], p[2])).getAnalogOutputSignal(helper.getLevel(), rig.abs(p[0], p[1], p[2]));
				if(got != expect) problems.add("slot " + i + ", from " + p[0] + "," + p[1] + "," + p[2] + ": " + got + ", not " + expect);
			}
		}

		// Core and lenses are not counted however full they are.
		arm(te);
		int got = core.getAnalogOutputSignal(helper.getLevel(), rig.abs(0, 0, 0));
		if(got != expect) problems.add("arming it changed the signal to " + got);

		helper.assertTrue(expect == 3, "the rig is wrong: " + expect);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A real comparator beside a corner re-reads when a slot changes: that is what the neighbour nudge
	 * is for. The comparator is given time to settle first, because placing one schedules a read of
	 * its own that would otherwise find the items by itself.
	 */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void aComparatorBesideTheMachineFollowsTheSlots(GameTestHelper helper) {

		Rig rig = place(helper);
		// One layer up on the east face, on a stone: vanilla's own container hook (setChanged) reaches only a
		// comparator in the core's row, and this one is not, so only the machine's own nudge can wake it.
		helper.setBlock(rig.at(2, 0, 0), Blocks.STONE);
		BlockPos comparator = rig.at(2, 1, 0);
		helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST)); // FACING is the input side

		java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean(false);

		TickWindow.runAfterDelay(helper, 10, () -> {
			var be = helper.getBlockEntity(comparator);
			helper.assertTrue(be instanceof ComparatorBlockEntity cbe && cbe.getOutputSignal() == 0, "the empty machine reads nonzero");
			rig.te.setItem(5, stack(ModItems.ingot_schrabidium.get()));
			rig.te.setItem(6, stack(ModItems.ingot_solinium.get()));
			rig.te.setItem(9, stack(ModItems.ingot_schrabidium.get()));
			loaded.set(true);
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(loaded.get(), "not loaded yet");
			var be = helper.getBlockEntity(comparator);
			helper.assertTrue(be instanceof ComparatorBlockEntity cbe && cbe.getOutputSignal() == 3,
					"the comparator reads " + (be instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1) + ", not 3");
		});
	}

	// ---- saving and the client -------------------------------------------------------------

	/** Twenty slots and the powered blocks survive a save; the door and the cooldown do not, as upstream's do not. */
	@GameTest(template = EMPTY)
	public static void itSavesItsSlotsAndItsPoweredBlocks(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		arm(te);
		te.setItem(12, stack(ModItems.ingot_schrabidium.get()));
		te.activePoses.add(helper.absolutePos(new BlockPos(0, 1, 2)));
		te.activePoses.add(helper.absolutePos(new BlockPos(4, 3, 1)));
		te.doorPos = 33;
		te.cooldown = 9;

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = te.saveWithoutMetadata(registries);
		SolBlasterTE other = new SolBlasterTE(helper.absolutePos(rig.core), te.getBlockState());
		other.loadWithComponents(tag, registries);

		helper.assertTrue(other.isArmed(), "the armed state did not survive");
		helper.assertTrue(other.getItem(12).is(ModItems.ingot_schrabidium.get()), "slot 12 did not survive");
		helper.assertTrue(other.activePoses.equals(te.activePoses), "the powered blocks did not survive: " + other.activePoses);
		helper.assertTrue(other.doorPos == 0 && other.cooldown == 0, "the door or cooldown was saved");
		helper.succeed();
	}

	/**
	 * A client that loads the chunk after the fact reads the slots - the renderer draws a cover over
	 * each lens - and where the door stands from the update tag.
	 */
	@GameTest(template = EMPTY)
	public static void theUpdateTagCarriesTheSlotsAndTheDoor(GameTestHelper helper) {

		Rig rig = place(helper);
		SolBlasterTE te = rig.te;
		arm(te);
		te.doorPos = 27;

		var registries = helper.getLevel().registryAccess();
		SolBlasterTE client = new SolBlasterTE(helper.absolutePos(rig.core), te.getBlockState());
		client.handleUpdateTag(te.getUpdateTag(registries), registries);

		helper.assertTrue(client.isArmed(), "the client did not get the slots");
		helper.assertTrue(client.doorPos == 27 && client.prevDoorPos == 27 && client.nextDoorPos == 27, "the client's door is at " + client.doorPos);
		helper.assertTrue(te.getUpdatePacket() != null, "there is no update packet");
		helper.succeed();
	}

	/** The packet is upstream's: identifier, one key, and the client eases a tick behind what it was told. */
	@GameTest(template = EMPTY)
	public static void thePacketIsUpstreamsAndTheClientEases(GameTestHelper helper) {

		SolBlasterTE te = place(helper).te;

		helper.assertTrue("SOL_BLAST".equals(te.getPacketIdentifier()), "the identifier is " + te.getPacketIdentifier());
		helper.assertTrue(SolBlasterTE.SolBlasterPackets.DOOR_SYNC.ordinal() == 0, "DOOR_SYNC is not key 0");
		helper.assertTrue(SolBlasterTE.doorDuration == 50, "the door takes " + SolBlasterTE.doorDuration + " ticks");

		te.onReceivePacketLocal((byte) 0, 12);
		helper.assertTrue(te.nextDoorPos == 12 && te.doorPos == 0, "the packet moved more than the target");
		te.onReceivePacketLocal((byte) 1, 40);
		helper.assertTrue(te.nextDoorPos == 12, "an unknown key moved the target");
		helper.succeed();
	}

	/** The door's sounds are the addon's reactor door events, registered under upstream's names. */
	@GameTest(template = EMPTY)
	public static void theDoorSoundsAreTheReactorDoors(GameTestHelper helper) {
		helper.assertTrue(LeafiaSounds.reactor_door_open.get().getLocation().equals(LeafiaRef.rl("external.reactor_door_open")), "open");
		helper.assertTrue(LeafiaSounds.reactor_door_close.get().getLocation().equals(LeafiaRef.rl("external.reactor_door_close")), "close");
		helper.succeed();
	}

	/** Upstream's box: three out each way and six up. */
	@GameTest(template = EMPTY)
	public static void theRenderBoxIsUpstreams(GameTestHelper helper) {

		Rig rig = place(helper);
		BlockPos p = rig.abs(0, 0, 0);
		AABB box = rig.te.getRenderBoundingBox();
		helper.assertTrue(box.minX == p.getX() - 3 && box.minY == p.getY() && box.minZ == p.getZ() - 3
				&& box.maxX == p.getX() + 4 && box.maxY == p.getY() + 6 && box.maxZ == p.getZ() + 4, "the box is " + box);
		helper.succeed();
	}

	/**
	 * Upstream's block says it provides a tooltip and has a description in its lang, and never
	 * overrides {@code addInformation}: nothing is shown. Pinned so a fix is a decision.
	 */
	@GameTest(template = EMPTY)
	public static void upstreamsBlockShowsNoTooltipAndHasTheTextAnyway(GameTestHelper helper) {

		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		LeafiaBlocks.sol_blaster.get().appendHoverText(new ItemStack(LeafiaBlocks.sol_blaster.get()),
				net.minecraft.world.item.Item.TooltipContext.EMPTY, lines, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(lines.isEmpty(), "the block shows a tooltip: " + lines);

		JsonObject lang = json("lang/en_us.json");
		helper.assertTrue(lang.get("tile.sol_blaster.desc").getAsString().startsWith("Uses solinium core implosion propellants to$"),
				"the description is " + lang.get("tile.sol_blaster.desc"));
		helper.assertTrue("Neutron Blaster".equals(lang.get("tile.sol_blaster.name").getAsString()), "name");
		helper.assertTrue("Neutron Blaster".equals(lang.get("block.leafia.sol_blaster").getAsString()), "block name");
		helper.succeed();
	}

	// ---- the assets ------------------------------------------------------------------------

	private static String text(String path) {
		try(InputStream in = LeafiaSolBlasterTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(text(path)).getAsJsonObject();
	}

	private static boolean exists(String path) {
		try(InputStream in = LeafiaSolBlasterTests.class.getResourceAsStream(path)) {
			return in != null;
		} catch(IOException e) {
			return false;
		}
	}

	/** The OBJ's object names, in order. */
	private static List<String> objects() {
		return new ArrayList<>(SolBlasterObj.meshes().keySet());
	}

	/**
	 * The OBJ is upstream's nineteen objects, each with geometry; every texture the renderer binds is
	 * there (ours, and the base mod's three by path); and the renderer asks for exactly these
	 * objects. The first version baked parts against the block atlas and a screenshot showed it
	 * cannot - the coordinates are outside 0..1 - so what is pinned here is the property that
	 * forced it: the containment's run past the sprite.
	 */
	@GameTest(template = EMPTY)
	public static void theObjHasUpstreamsNineteenObjectsAndEveryTextureIsThere(GameTestHelper helper) {

		var meshes = SolBlasterObj.meshes();
		List<String> want = List.of("Base", "Frame", "Containment", "ContainmentInside", "Hemisphere", "Tower", "Cover1",
				"Charge1", "Charge2", "Charge3", "Charge4", "Stain", "SolidBlack", "Door", "DoorAlt", "Handle", "Cover4",
				"Cover3", "Cover2");
		helper.assertTrue(meshes.keySet().containsAll(want) && meshes.size() == 19, "the objects are " + meshes.keySet());

		List<String> problems = new ArrayList<>();
		int faces = 0;
		for(var e : meshes.entrySet()) {
			if(e.getValue().faces() == 0) problems.add(e.getKey() + " has no faces");
			if(e.getValue().data().length != e.getValue().faces() * 4 * 8) problems.add(e.getKey() + " is not four vertices a face");
			faces += e.getValue().faces();
		}
		helper.assertTrue(faces > 1000, "the whole machine is " + faces + " faces");

		// Every face is a quad whose fourth corner repeats the third: that is how a triangle is drawn.
		for(var e : meshes.entrySet()) {
			float[] d = e.getValue().data();
			for(int f = 0; f < e.getValue().faces(); f++)
				for(int k = 0; k < 8; k++)
					if(d[f * 32 + 16 + k] != d[f * 32 + 24 + k]) { problems.add(e.getKey() + " face " + f + " does not repeat its last corner"); f = e.getValue().faces(); break; }
		}

		// The brick concrete is tiled: its v coordinates leave 0..1 by several units, which no atlas sprite can show.
		float[] c = meshes.get("Containment").data();
		float lo = 0, hi = 0;
		for(int i = 3; i < c.length; i += 8) { lo = Math.min(lo, c[i]); hi = Math.max(hi, c[i]); }
		helper.assertTrue(hi - lo > 3, "the containment's u runs " + lo + " to " + hi + ": it does not tile");

		// And the hemisphere is the single point of a one-pixel texture, flipped to v = 1.
		float[] h = meshes.get("Hemisphere").data();
		helper.assertTrue(h[3] == 0F && h[4] == 1F, "the hemisphere's first coordinate is " + h[3] + "," + h[4]);

		for(String tex : List.of("door", "charge", "stain", "reactordoor", "solid"))
			if(!exists("/assets/leafia/textures/entity/sol_blaster/" + tex + ".png")) problems.add("missing " + tex);
		for(String path : List.of("/assets/hbm/textures/models/machines/chemical_plant.png",
				"/assets/hbm/textures/block/brick_concrete.png", "/assets/hbm/textures/block/brick_ducrete.png"))
			if(ModBlocks.class.getResourceAsStream(path) == null) problems.add("the base mod has no " + path);

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Upstream's three-turn handle and swinging door are separate objects, since the renderer turns each on its own. */
	@GameTest(template = EMPTY)
	public static void theDoorAndItsHandleAreSeparateObjects(GameTestHelper helper) {

		var meshes = SolBlasterObj.meshes();
		helper.assertTrue(meshes.containsKey("Door") && meshes.containsKey("DoorAlt") && meshes.containsKey("Handle"), "no door objects");
		// The handle sits in front of the door on x = 0 and y = 1.6875 - the axis the renderer turns it about.
		float[] d = meshes.get("Handle").data();
		float lo = 9, hi = -9, x0 = 9, x1 = -9;
		for(int i = 0; i < d.length; i += 8) { lo = Math.min(lo, d[i + 1]); hi = Math.max(hi, d[i + 1]); x0 = Math.min(x0, d[i]); x1 = Math.max(x1, d[i]); }
		helper.assertTrue(Math.abs((lo + hi) / 2 - 1.688) < 0.01 && Math.abs((x0 + x1) / 2) < 0.01,
				"the handle is centred on " + (x0 + x1) / 2 + "," + (lo + hi) / 2 + ", not 0,1.688");
		helper.succeed();
	}

	/** The sheet is 256 square - the slot coordinates are drawn on it - and the item is a block-entity item. */
	@GameTest(template = EMPTY)
	public static void theSheetAndTheItemModelAreUpstreams(GameTestHelper helper) {

		try(InputStream in = LeafiaSolBlasterTests.class.getResourceAsStream("/assets/leafia/textures/gui/sol_blaster.png")) {
			BufferedImage image = ImageIO.read(in);
			helper.assertTrue(image.getWidth() == 256 && image.getHeight() == 256, "the sheet is " + image.getWidth() + "x" + image.getHeight());
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}

		JsonObject item = json("models/item/sol_blaster.json");
		helper.assertTrue("builtin/entity".equals(item.get("parent").getAsString()), "the item is not drawn by its renderer");
		helper.assertTrue(item.getAsJsonObject("display").has("gui") && item.getAsJsonObject("display").has("ground"), "no display block");

		JsonObject state = json("blockstates/sol_blaster.json");
		helper.assertTrue("leafia:block/sol_blaster".equals(state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString()), "blockstate");
		helper.succeed();
	}

	/** The loot table drops the block itself, as every addon block's does. */
	@GameTest(template = EMPTY)
	public static void theBlockDropsItself(GameTestHelper helper) {

		String table;
		try(InputStream in = LeafiaSolBlasterTests.class.getResourceAsStream("/data/leafia/loot_table/blocks/sol_blaster.json")) {
			if(in == null) throw new IllegalStateException("no loot table for the blaster");
			table = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(table.contains("\"leafia:sol_blaster\""), "the loot table is " + table);
		helper.succeed();
	}

	/**
	 * The door's camera shake is upstream's (D-1087): a smooth shake that parses cleanly to
	 * intensity 0.15, half a second, speed 8, range 12, curve 0.5 and dulling 50, sent within ten
	 * blocks. Wired once {@code LeafiaShakecam} landed (D-1089).
	 */
	@GameTest(template = EMPTY)
	public static void theDoorShakeIsUpstreams(GameTestHelper helper) {
		var parsed = com.leafia.passive.effects.LeafiaShakecam.parse(
				com.leafia.contents.machines.processing.solblaster.SolBlasterTE.DOOR_SHAKE);
		helper.assertTrue(parsed.warnings().isEmpty(), "the parameters parse cleanly: " + parsed.warnings());
		var shake = parsed.shake();
		helper.assertTrue(shake != null, "a shake");
		helper.assertTrue(shake.intensity == 0.15F && shake.duration == 0.5F && shake.speed == 8F
				&& shake.range == 12F && shake.curve == 0.5F, "intensity 0.15, 0.5 s, speed 8, range 12, curve 0.5");
		helper.assertTrue(shake.blurDulling == 50 && shake.bloomDulling == 50, "dulling 50");
		helper.assertTrue(com.leafia.contents.machines.processing.solblaster.SolBlasterTE.DOOR_SHAKE_RANGE == 10,
				"sent within ten blocks");
		helper.succeed();
	}
}
