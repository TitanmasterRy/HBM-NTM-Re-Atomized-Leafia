package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.storage.broof.BroofTE;
import com.leafia.contents.building.storage.broof.container.BroofMenu;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The broof — a rug with a double chest under it, and the addon's first menu.
 *
 * <h2>What these cover</h2>
 *
 * The storage and the multiblock are entirely server-side and are checked here in a world:
 * the 3x3 footprint, the offset core, the absent collision, the fifty-four slots surviving a
 * save and load, and the menu's slot layout.
 *
 * <p>What they cannot cover is the <em>screen</em>. {@code BroofScreen} is
 * {@code @OnlyIn(Dist.CLIENT)} and a dedicated server cannot load the class, so the pairing
 * between menu type and screen is checked by the client sweep rather than here. The menu type
 * itself is server-side and is asserted.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaBroofTests {

	private static final String EMPTY = "empty";

	/**
	 * Places a broof <b>core</b> and returns its position.
	 *
	 * <p>The PART has to be set: {@code BlockDummyable}'s default state is {@code DUMMY}, which
	 * has no block entity by design, so a plain {@code setBlock} gives a broof with nowhere to
	 * put anything. Placing the core directly is what {@code setPlacedBy} would have produced,
	 * minus the eight dummies around it.
	 */
	private static BlockPos place(GameTestHelper helper, BlockPos clicked) {
		helper.setBlock(clicked, LeafiaBlocks.broof.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		return clicked;
	}

	/**
	 * A broof is three by three, and only the middle has the block entity.
	 *
	 * <p>Upstream's {@code getDimensions()} is {@code {1, 0, 1, 1, 1, 1}} and its
	 * {@code createNewTileEntity} answers only for the core range. Both halves are asserted,
	 * because a multiblock whose dummies each had a block entity would have nine inventories
	 * and eight of them unreachable.
	 */
	@GameTest(template = EMPTY)
	public static void aBroofIsThreeByThreeWithOneInventory(GameTestHelper helper) {

		BlockPos core = new BlockPos(2, 1, 2);
		helper.setBlock(core, LeafiaBlocks.broof.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));

		helper.assertTrue(helper.getBlockEntity(core) instanceof BroofTE,
				"the core must carry the inventory");

		List<String> problems = new ArrayList<>();
		for(Direction face : Direction.Plane.HORIZONTAL) {
			BlockPos dummy = core.relative(face);
			helper.setBlock(dummy, LeafiaBlocks.broof.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.DUMMY)
					.setValue(BlockDummyable.DIR, face));
			// Through the level, not the helper: helper.getBlockEntity throws on null, which is
			// the opposite of what this asks.
			if(helper.getLevel().getBlockEntity(helper.absolutePos(dummy)) != null)
				problems.add("the dummy at " + face + " has a block entity of its own");
		}

		int[] dims = ((com.leafia.contents.building.storage.broof.BroofBlock)
				LeafiaBlocks.broof.get()).getDimensions();
		if(dims.length != 6 || dims[0] != 1 || dims[1] != 0
				|| dims[2] != 1 || dims[3] != 1 || dims[4] != 1 || dims[5] != 1)
			problems.add("upstream's dimensions are {1,0,1,1,1,1}, got " + java.util.Arrays.toString(dims));

		if(LeafiaBlocks.broof.get().getOffset() != 1)
			problems.add("upstream's offset is 1, got " + LeafiaBlocks.broof.get().getOffset());

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * You walk over a broof, not into it — and you can still select and break it.
	 *
	 * <p>Upstream's {@code addCollisionBoxToList} adds nothing at all, which is the whole point
	 * of a rug. It does <b>not</b> override {@code getBoundingBox}, so the outline stays a full
	 * cube; a block with no outline cannot be broken, and losing that would be a far worse bug
	 * than the one being ported.
	 */
	@GameTest(template = EMPTY)
	public static void aBroofHasNoCollisionButStillHasAnOutline(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.broof.get());
		BlockState state = helper.getBlockState(pos);
		BlockPos abs = helper.absolutePos(pos);

		helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs).isEmpty(),
				"a broof must have no collision at all");
		helper.assertTrue(!state.getShape(helper.getLevel(), abs).isEmpty(),
				"a broof must still be selectable, or it cannot be broken");
		helper.succeed();
	}

	// ------------------------------------------------------------------ storage

	/** Fifty-four slots, and they survive a save and load. */
	@GameTest(template = EMPTY)
	public static void theInventoryHoldsFiftyFourStacksAcrossASaveAndLoad(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		BroofTE broof = (BroofTE) helper.getBlockEntity(pos);

		helper.assertTrue(broof.inventory.getSlots() == BroofTE.SLOTS,
				"upstream's broof has " + BroofTE.SLOTS + " slots, got " + broof.inventory.getSlots());

		broof.inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
		broof.inventory.setStackInSlot(53, new ItemStack(Items.STICK, 3));

		var registries = helper.getLevel().registryAccess();
		var tag = broof.saveWithFullMetadata(registries);

		BroofTE reloaded = new BroofTE(helper.absolutePos(pos), broof.getBlockState());
		reloaded.loadWithComponents(tag, registries);

		helper.assertTrue(reloaded.inventory.getStackInSlot(0).getCount() == 7
						&& reloaded.inventory.getStackInSlot(0).is(Items.DIAMOND),
				"slot 0 did not survive: " + reloaded.inventory.getStackInSlot(0));
		helper.assertTrue(reloaded.inventory.getStackInSlot(53).getCount() == 3,
				"the last slot did not survive: " + reloaded.inventory.getStackInSlot(53));
		helper.succeed();
	}

	/**
	 * A hopper or a pipe can reach every slot from any side.
	 *
	 * <p>Upstream's {@code getAccessibleSlotsFromSide} returns all fifty-four for every face, so
	 * its side wrapper is an identity and this port exposes the handler directly. Asserted from
	 * more than one side, because "exposes something" and "exposes the same thing everywhere"
	 * are different claims and the second is the one that was dropped.
	 */
	@GameTest(template = EMPTY)
	public static void everySideSeesTheWholeInventory(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		BlockPos abs = helper.absolutePos(pos);
		((BroofTE) helper.getBlockEntity(pos)).inventory
				.setStackInSlot(40, new ItemStack(Items.DIAMOND, 1));

		List<String> problems = new ArrayList<>();
		for(Direction face : Direction.values()) {
			var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, face);
			if(handler == null) {
				problems.add("no item handler on " + face);
				continue;
			}
			if(handler.getSlots() != BroofTE.SLOTS)
				problems.add(face + " sees " + handler.getSlots() + " slots, not " + BroofTE.SLOTS);
			else if(!handler.getStackInSlot(40).is(Items.DIAMOND))
				problems.add(face + " cannot see what is in slot 40");
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " capability problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the menu

	/**
	 * The menu is ninety slots at upstream's coordinates, and its type is registered.
	 *
	 * <p>The coordinates are not decoration: the screen draws vanilla's {@code generic_54}
	 * texture and the slots have to land on the holes in it. They are checked against the
	 * arithmetic upstream writes rather than against a copied table, so a changed formula
	 * fails and a changed <em>layout</em> has to be deliberate.
	 */
	@GameTest(template = EMPTY)
	public static void theMenuIsUpstreamsLayout(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		BroofTE broof = (BroofTE) helper.getBlockEntity(pos);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		BroofMenu menu = new BroofMenu(1, player.getInventory(), broof);

		List<String> problems = new ArrayList<>();

		if(menu.slots.size() != BroofTE.SLOTS + 36)
			problems.add("fifty-four plus the player's thirty-six is 90 slots, got " + menu.slots.size());

		if(menu.getType() != LeafiaMenuTypes.BROOF.get())
			problems.add("the menu does not carry the registered type");

		// The broof's six rows of nine: 8 + j*18 across, 18 + i*18 down.
		for(int i = 0; i < 6; i++) for(int j = 0; j < 9; j++) {
			var slot = menu.slots.get(j + i * 9);
			if(slot.x != 8 + j * 18 || slot.y != 18 + i * 18)
				problems.add("storage slot " + (j + i * 9) + " is at " + slot.x + "," + slot.y
						+ ", should be " + (8 + j * 18) + "," + (18 + i * 18));
		}

		// The player's three rows, then the hotbar - upstream's offsets, which put them where
		// vanilla's generic_54 texture draws them.
		for(int i = 0; i < 3; i++) for(int j = 0; j < 9; j++) {
			var slot = menu.slots.get(BroofTE.SLOTS + j + i * 9);
			if(slot.y != 84 + i * 18 + (18 * 3) + 2)
				problems.add("player row " + i + " is at y=" + slot.y
						+ ", should be " + (84 + i * 18 + (18 * 3) + 2));
		}
		for(int i = 0; i < 9; i++) {
			var slot = menu.slots.get(BroofTE.SLOTS + 27 + i);
			if(slot.y != 142 + (18 * 3) + 2)
				problems.add("hotbar slot " + i + " is at y=" + slot.y
						+ ", should be " + (142 + (18 * 3) + 2));
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " layout problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** Shift-click moves a stack into the broof, and back out again. */
	@GameTest(template = EMPTY)
	public static void shiftClickMovesBothWays(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		BroofTE broof = (BroofTE) helper.getBlockEntity(pos);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));

		BroofMenu menu = new BroofMenu(1, player.getInventory(), broof);

		// The hotbar's first slot is the last nine of the menu.
		menu.quickMoveStack(player, BroofTE.SLOTS + 27);
		helper.assertTrue(broof.inventory.getStackInSlot(0).getCount() == 5,
				"shift-click from the player should fill the broof's first slot, got "
						+ broof.inventory.getStackInSlot(0));
		helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
				"and should empty the player's");

		menu.quickMoveStack(player, 0);
		helper.assertTrue(broof.inventory.getStackInSlot(0).isEmpty(),
				"shift-click from the broof should empty its slot, got "
						+ broof.inventory.getStackInSlot(0));
		helper.assertTrue(!player.getInventory().isEmpty(),
				"and should give the stack back to the player");
		helper.succeed();
	}

	/**
	 * A broof stays open however far the player walks — upstream returns true unconditionally.
	 *
	 * <p>Vanilla's {@code stillValid} closes a container at eight blocks. Tightening this to
	 * vanilla's rule would be a behaviour change to a shipped block, so it is pinned rather
	 * than left to whoever reads the method next and thinks it looks wrong (D-021).
	 */
	@GameTest(template = EMPTY)
	public static void theMenuNeverClosesOnDistance(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		BroofTE broof = (BroofTE) helper.getBlockEntity(pos);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		BroofMenu menu = new BroofMenu(1, player.getInventory(), broof);

		player.setPos(player.getX() + 5000, player.getY(), player.getZ());
		helper.assertTrue(menu.stillValid(player),
				"upstream's canInteractWith returns true unconditionally");
		helper.succeed();
	}

	/**
	 * The client-side constructor builds a menu of the same shape with no block entity.
	 *
	 * <p>1.12.2 built both sides from a shared reference to the tile entity; here the client
	 * has none. A mismatch between the two constructors is a desynchronised inventory rather
	 * than a crash, so the shapes are compared.
	 */
	@GameTest(template = EMPTY)
	public static void theClientConstructorMatchesTheServerOne(GameTestHelper helper) {

		BlockPos pos = place(helper, new BlockPos(1, 1, 1));
		var player = helper.makeMockPlayer(GameType.SURVIVAL);

		BroofMenu server = new BroofMenu(1, player.getInventory(), (BroofTE) helper.getBlockEntity(pos));
		BroofMenu client = new BroofMenu(1, player.getInventory(), (net.minecraft.network.RegistryFriendlyByteBuf) null);

		helper.assertTrue(server.slots.size() == client.slots.size(),
				"the two constructors disagree: " + server.slots.size() + " against " + client.slots.size());
		helper.assertTrue(client.getInventory().getSlots() == BroofTE.SLOTS,
				"the client's placeholder inventory must be the same size");
		helper.succeed();
	}
}
