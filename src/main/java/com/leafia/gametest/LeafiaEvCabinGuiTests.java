package com.leafia.gametest;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.BellButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.CloseButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.ElevatorButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonModifyPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FireButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FloorButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.OpenButton;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.contents.machines.elevators.floors.EvFloorTE;
import com.leafia.contents.machines.elevators.gui.EvCabinMenu;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.init.LeafiaEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable.ToolType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The elevator screens (D-1077): the cabin's menu and button-editor packet, the floor screen's
 * server half, and the arithmetic of the two base classes behind them.
 *
 * <h2>What cannot be asserted here</h2>
 *
 * A screen is client code and a GameTest server has no client, so nothing below draws, clicks or
 * types. What is pinned is everything a screen stands on: the slot layout it is drawn over, the
 * packet it sends and what the server does with it, the menu's shift-click, the opener, and the
 * rectangle arithmetic its hit tests use. The screens themselves - the panel, the text fields,
 * the add-button overlay, the drag - were checked by eye in a running client (D-1077).
 *
 * <h2>"Reached the packet" is the evidence for an opener</h2>
 *
 * A mock player's connection negotiated no channels, so the packet an opener ends in - the screen
 * payload for the cabin, {@code leafia:container} for the floor - is refused with an
 * {@link UnsupportedOperationException} naming it (the base mod's cabinet tests hit the same wall
 * and build the menu directly). That refusal is precisely the moment after every decision the
 * opener makes and before the part that needs a client, so {@link #reachedSending} reads it as
 * "the opener opened", and no exception as "it did not".
 *
 * <h2>The screwdriver is borrowed</h2>
 *
 * Nothing in this port is a screwdriver: {@code ItemTooling} is unported, and it is its
 * constructor that registers the item with {@link ToolType}. The opener tests register a vanilla
 * stick for the length of one test and take it out again, <b>and then force the lookup table to
 * rebuild</b>: {@code ToolType.register} clears its cache, but {@code stacksForDisplay.remove}
 * does not, so a stale entry would outlive the test and make every stick a screwdriver for the
 * rest of the run. {@link #withScrewdriver} does both halves.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaEvCabinGuiTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ FiaUIRect

	/**
	 * The rectangle is half-open: left and top edges in, right and bottom out. Upstream's
	 * {@code isMouseIn}, and the screens' hit tests lean on it - the cabin's add and remove
	 * buttons, its three save buttons and each button's 6 by 3 hit box.
	 */
	@GameTest(template = EMPTY)
	public static void aRectangleIsHalfOpen(GameTestHelper helper) {

		FiaUIRect rect = new FiaUIRect(5, 6, 10, 4).updateGuiPosition(100, 200);

		helper.assertTrue(rect.absX() == 105 && rect.absY() == 206,
				"absX/absY add the panel's origin: " + rect.absX() + "," + rect.absY());

		helper.assertTrue(rect.isMouseIn(105, 206), "the top-left pixel is inside");
		helper.assertTrue(rect.isMouseIn(114, 209), "the bottom-right pixel of the 10 by 4 is inside");
		helper.assertTrue(!rect.isMouseIn(104, 206), "one left of it is outside");
		helper.assertTrue(!rect.isMouseIn(105, 205), "one above it is outside");
		helper.assertTrue(!rect.isMouseIn(115, 209), "x = left + width is outside");
		helper.assertTrue(!rect.isMouseIn(114, 210), "y = top + height is outside");
		helper.succeed();
	}

	/** {@code updateGuiPosition} returns the rectangle itself, so it chains. */
	@GameTest(template = EMPTY)
	public static void movingTheOriginChains(GameTestHelper helper) {
		FiaUIRect rect = new FiaUIRect(1, 2, 3, 4);
		helper.assertTrue(rect.updateGuiPosition(7, 9) == rect, "updateGuiPosition(int, int) returns this");
		helper.assertTrue(rect.guiLeft == 7 && rect.guiTop == 9, "and moves the origin");
		helper.succeed();
	}

	// ------------------------------------------------------------------ assets

	/**
	 * Both panels are 256 by 256, which is what {@code GuiGraphics.blit}'s default sheet size
	 * assumes - a sheet of any other size would put every sprite in the wrong place without a
	 * single error.
	 */
	@GameTest(template = EMPTY)
	public static void bothPanelsAre256Square(GameTestHelper helper) throws Exception {
		for(String name : new String[]{"floor", "cab_002"}) {
			try(InputStream in = LeafiaEvCabinGuiTests.class
					.getResourceAsStream("/assets/leafia/textures/gui/elevators/" + name + ".png")) {
				helper.assertTrue(in != null, name + ".png is not in the jar");
				var image = ImageIO.read(in);
				helper.assertTrue(image.getWidth() == 256 && image.getHeight() == 256,
						name + ".png is " + image.getWidth() + "x" + image.getHeight());
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the menu

	/**
	 * Eight cabin slots in upstream's order and pixels, then the player's 27 and 9.
	 *
	 * <p>These have to land on {@code cab_002.png}; moving any is invisible to every check but
	 * a screenshot.
	 */
	@GameTest(template = EMPTY)
	public static void theMenuLaysOutUpstreamsFortyFourSlots(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		EvCabinMenu menu = new EvCabinMenu(1, mockPlayer(helper).getInventory(), cabin);

		helper.assertTrue(menu.slots.size() == 44, "44 slots, got " + menu.slots.size());

		int[][] cabinSlots = {{30, 35}, {9, 56}, {9, 14}, {30, 14}, {9, 35}, {30, 56}, {51, 35}, {51, 56}};
		for(int i = 0; i < 8; i++) {
			Slot slot = menu.slots.get(i);
			helper.assertTrue(slot instanceof SlotItemHandler, "slot " + i + " is not on the cabin's inventory");
			helper.assertTrue(slot.x == cabinSlots[i][0] && slot.y == cabinSlots[i][1],
					"slot " + i + " is at " + slot.x + "," + slot.y + ", expected "
							+ cabinSlots[i][0] + "," + cabinSlots[i][1]);
		}

		Slot first = menu.slots.get(8);
		Slot lastMain = menu.slots.get(34);
		Slot firstHotbar = menu.slots.get(35);
		Slot lastHotbar = menu.slots.get(43);
		helper.assertTrue(first.x == 8 && first.y == 91, "main inventory starts at 8,91, got " + first.x + "," + first.y);
		helper.assertTrue(lastMain.x == 8 + 8 * 18 && lastMain.y == 91 + 2 * 18,
				"main inventory ends at 152,127, got " + lastMain.x + "," + lastMain.y);
		helper.assertTrue(firstHotbar.x == 8 && firstHotbar.y == 149, "hotbar starts at 8,149");
		helper.assertTrue(lastHotbar.x == 152 && lastHotbar.y == 149, "hotbar ends at 152,149");

		cabin.discard();
		helper.succeed();
	}

	/** The menu is registered under its own type and is never closed by distance. */
	@GameTest(template = EMPTY)
	public static void theMenuHasItsOwnTypeAndNeverExpires(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		helper.assertTrue(menu.getType() == LeafiaMenuTypes.EV_CABIN.get(), "the menu is not of type ev_cabin");
		helper.assertTrue(menu.entity == cabin, "the menu lost its cabin");

		player.setPos(player.position().add(500, 0, 500));
		helper.assertTrue(menu.stillValid(player), "upstream's canInteractWith is true however far you stand");
		cabin.discard();
		helper.succeed();
	}

	/**
	 * The client builds the menu from a payload that carries the cabin's entity id, and finds the
	 * cabin by it. Run here against the server level, which is the same lookup.
	 */
	@GameTest(template = EMPTY)
	public static void theClientConstructorFindsTheCabinByItsId(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		buf.writeVarInt(cabin.getId());
		EvCabinMenu found = new EvCabinMenu(2, player.getInventory(), buf);
		helper.assertTrue(found.entity == cabin, "the id did not find the cabin");

		RegistryFriendlyByteBuf wrong = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		wrong.writeVarInt(-12345);
		EvCabinMenu none = new EvCabinMenu(3, player.getInventory(), wrong);
		helper.assertTrue(none.entity == null, "an unknown id found " + none.entity);
		helper.assertTrue(none.slots.size() == 44, "an orphaned menu still has its 44 slots, got " + none.slots.size());

		cabin.discard();
		helper.succeed();
	}

	/**
	 * Shift-click from the player into the cabin goes only where the slot accepts it: a chip into
	 * slot 0, anything else into the radio slot, which upstream lets take anything.
	 */
	@GameTest(template = EMPTY)
	public static void shiftClickingFromTheInventoryRespectsEachSlotsRules(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		player.getInventory().setItem(9, new ItemStack(LeafiaItems.ev_chip_s6.get()));
		player.getInventory().setItem(10, new ItemStack(Items.STONE, 5));

		// Inventory slot 9 is menu slot 8.
		ItemStack moved = menu.quickMoveStack(player, 8);
		helper.assertTrue(moved.getItem() == LeafiaItems.ev_chip_s6.get(), "the chip was not reported moved: " + moved);
		helper.assertTrue(cabin.inventory.getStackInSlot(0).getItem() == LeafiaItems.ev_chip_s6.get(),
				"the chip did not land in slot 0, which is the only slot that takes one");

		menu.quickMoveStack(player, 9);
		helper.assertTrue(cabin.inventory.getStackInSlot(7).is(Items.STONE)
				&& cabin.inventory.getStackInSlot(7).getCount() == 5,
				"stone should have gone to the radio slot, the one that takes anything; slot 7 holds "
						+ cabin.inventory.getStackInSlot(7));
		for(int i = 1; i < 7; i++)
			helper.assertTrue(cabin.inventory.getStackInSlot(i).isEmpty(), "slot " + i + " took a stone");

		cabin.discard();
		helper.succeed();
	}

	/** Shift-click from the cabin into the player moves the stack out and leaves the slot empty. */
	@GameTest(template = EMPTY)
	public static void shiftClickingFromTheCabinGivesTheStackBack(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		cabin.inventory.setStackInSlot(0, new ItemStack(LeafiaItems.ev_chip_s6.get()));
		ItemStack moved = menu.quickMoveStack(player, 0);

		helper.assertTrue(moved.getItem() == LeafiaItems.ev_chip_s6.get(), "reported " + moved);
		helper.assertTrue(cabin.inventory.getStackInSlot(0).isEmpty(), "the chip is still in the cabin");
		helper.assertTrue(player.getInventory().countItem(LeafiaItems.ev_chip_s6.get()) == 1,
				"the player did not get the chip");
		helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "an empty slot moves nothing");
		cabin.discard();
		helper.succeed();
	}

	/**
	 * <b>An upstream defect, pinned so nobody tidies it by accident (D-021).</b> The second of
	 * upstream's two moves starts at the highest cabin slot (7) instead of the first player slot
	 * (8), so with the player's inventory full the radio slot's stack is "moved" into slots 0-7 -
	 * of which 7 is itself. Vanilla's merge sets the count to the doubled value on the one
	 * object, and its shift-click loop would repeat that until the stack limit.
	 */
	@GameTest(template = EMPTY)
	public static void shiftClickingTheRadioSlotWithAFullInventoryDoublesIt(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		for(int i = 0; i < 36; i++)
			player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		cabin.inventory.setStackInSlot(7, new ItemStack(Items.STONE, 3));

		menu.quickMoveStack(player, 7);

		int count = cabin.inventory.getStackInSlot(7).getCount();
		helper.assertTrue(count == 6, "upstream's off-by-one doubles the radio slot's 3 stone to 6, slot 7 holds " + count);
		cabin.discard();
		helper.succeed();
	}

	/** With room in the inventory the same click does what it says: the stack leaves slot 7. */
	@GameTest(template = EMPTY)
	public static void shiftClickingTheRadioSlotWithRoomMovesIt(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		cabin.inventory.setStackInSlot(7, new ItemStack(Items.STONE, 3));
		menu.quickMoveStack(player, 7);

		helper.assertTrue(cabin.inventory.getStackInSlot(7).isEmpty(), "slot 7 still holds the stone");
		helper.assertTrue(player.getInventory().countItem(Items.STONE) == 3, "the player did not get all three");
		cabin.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ EvButtonModifyPacket

	/**
	 * The packet is registered after the fuzzy identifier's pair, at id 12 - upstream's ordinal
	 * is 13, and the elevators' seven keep 3 to 9, so nothing already on the wire moved.
	 */
	@GameTest(template = EMPTY)
	public static void theModifyPacketAppendedAfterTheFuzzyPair(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.ELEVATOR_BUTTON_MODIFY == 12,
				"the modify packet is at " + LeafiaPacketTypes.ELEVATOR_BUTTON_MODIFY);
		helper.assertTrue(LeafiaPacketTypes.FUZZY_RESPONSE == 11, "the fuzzy pair did not move");
		helper.assertTrue(LeafiaPacketTypes.ELEVATOR_INVENTORY_SYNC == 9, "the elevators' seven did not move");
		helper.assertTrue(LeafiaPacketTypes.byId(12) instanceof EvButtonModifyPacket, "id 12 is not the modify packet");
		helper.succeed();
	}

	/**
	 * The packet's size on the wire, per mode, in bits: a 16-bit header, the 32-bit entity id, the
	 * 2-bit mode, then 32 more for a target on every mode but add, and the mode's own payload.
	 * Upstream bit-packs these, and a client and server that disagree by one bit misread
	 * everything after.
	 */
	@GameTest(template = EMPTY)
	public static void theWireSizesAreUpstreams(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);

		EvButtonModifyPacket move = packet(cabin, 0);
		move.localTarget = 1;
		assertBits(helper, "move", move, 16 + 32 + 2 + 32 + 8 + 8);

		EvButtonModifyPacket add = packet(cabin, 1);
		assertBits(helper, "add", add, 16 + 32 + 2 + 3);

		EvButtonModifyPacket remove = packet(cabin, 2);
		remove.localTarget = 1;
		assertBits(helper, "remove", remove, 16 + 32 + 2 + 32);

		EvButtonModifyPacket floor = packet(cabin, 3);
		floor.localProperty = 0;
		assertBits(helper, "modify floor", floor, 16 + 32 + 2 + 32 + 2 + 8);

		EvButtonModifyPacket label = packet(cabin, 3);
		label.localProperty = 2;
		label.localLabel = "AB";
		assertBits(helper, "modify label", label, 16 + 32 + 2 + 32 + 2 + 8 + 16);

		cabin.discard();
		helper.succeed();
	}

	/** Mode 1: each of the five kinds is added at upstream's default spot, (0, 3). */
	@GameTest(template = EMPTY)
	public static void addingMakesEachKindAtZeroThree(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);

		for(int type = 0; type < 5; type++) {
			EvButtonModifyPacket p = packet(cabin, 1);
			p.localButtonType = type;
			deliver(p, player);
		}

		helper.assertTrue(cabin.buttons.size() == 5, "five adds should make five buttons, got " + cabin.buttons.size());
		helper.assertTrue(cabin.buttons.get(0) instanceof FloorButton, "0 is not a floor button");
		helper.assertTrue(cabin.buttons.get(1) instanceof FireButton, "1 is not the fire button");
		helper.assertTrue(cabin.buttons.get(2) instanceof OpenButton, "2 is not open");
		helper.assertTrue(cabin.buttons.get(3) instanceof CloseButton, "3 is not close");
		helper.assertTrue(cabin.buttons.get(4) instanceof BellButton, "4 is not the bell");

		for(ElevatorButton b : cabin.buttons)
			helper.assertTrue(b.x == 0 && b.y == 3, b.id + " was added at " + b.x + "," + b.y + ", not 0,3");

		FloorButton floor = (FloorButton) cabin.buttons.get(0);
		helper.assertTrue(floor.floor == 1 && floor.label.equals("1") && floor.id.equals("floor1"),
				"a new floor button is floor 1 labelled \"1\"; got " + floor.floor + " \"" + floor.label + "\" " + floor.id);
		cabin.discard();
		helper.succeed();
	}

	/** Mode 0: a move writes both coordinates as signed bytes and nothing else. */
	@GameTest(template = EMPTY)
	public static void movingWritesBothCoordinatesSigned(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		EvButtonModifyPacket p = packet(cabin, 0);
		p.localTarget = 2;
		p.localX = -5;
		p.localY = 9;
		deliver(p, player);

		helper.assertTrue(cabin.buttons.get(2).x == -5 && cabin.buttons.get(2).y == 9,
				"button 2 is at " + cabin.buttons.get(2).x + "," + cabin.buttons.get(2).y + ", expected -5,9");
		helper.assertTrue(cabin.buttons.get(1).x == 1 && cabin.buttons.get(1).y == 14, "a neighbour moved too");
		cabin.discard();
		helper.succeed();
	}

	/** Mode 2: removal takes the named button and keeps the order of the rest. */
	@GameTest(template = EMPTY)
	public static void removingKeepsTheRestInOrder(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		EvButtonModifyPacket p = packet(cabin, 2);
		p.localTarget = 1;
		deliver(p, player);

		helper.assertTrue(cabin.buttons.size() == 3, "removing one of four leaves three, got " + cabin.buttons.size());
		helper.assertTrue(cabin.buttons.get(0).id.equals("floor1")
				&& cabin.buttons.get(1).id.equals("floor3") && cabin.buttons.get(2).id.equals("open"),
				"the survivors are " + cabin.buttons.get(0).id + ", " + cabin.buttons.get(1).id + ", "
						+ cabin.buttons.get(2).id);
		cabin.discard();
		helper.succeed();
	}

	/** A target past the end of the list changes nothing. */
	@GameTest(template = EMPTY)
	public static void aTargetPastTheEndChangesNothing(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		for(int mode : new int[]{0, 2, 3}) {
			EvButtonModifyPacket p = packet(cabin, mode);
			p.localTarget = 4;
			p.localX = 33;
			p.localFloor = 99;
			deliver(p, player);
		}

		helper.assertTrue(cabin.buttons.size() == 4, "the list changed size to " + cabin.buttons.size());
		for(ElevatorButton b : cabin.buttons)
			helper.assertTrue(b.x != 33, b.id + " was moved by an out-of-range packet");
		cabin.discard();
		helper.succeed();
	}

	/**
	 * <b>Upstream's missing lower bound, closed (D-1094).</b> {@code size() > target} had no
	 * {@code target >= 0}, so a hostile -1 reached {@code List.get(-1)} and threw on the game
	 * thread, which NeoForge answers by disconnecting the sender. It is dropped now, in all three
	 * modes that take a target.
	 */
	@GameTest(template = EMPTY)
	public static void aNegativeTargetIsDroppedNotThrown(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		for(int mode : new int[]{0, 2, 3}) {
			EvButtonModifyPacket p = packet(cabin, mode);
			p.localTarget = -1;
			p.localX = 33;
			p.localFloor = 99;
			deliver(p, player);
		}

		helper.assertTrue(cabin.buttons.size() == 4, "a negative target changed the list");
		for(ElevatorButton b : cabin.buttons)
			helper.assertTrue(b.x != 33, b.id + " was moved by a negative target");
		cabin.discard();
		helper.succeed();
	}

	/** Mode 3, property 0: the floor becomes the button's floor and its id, and is one signed byte. */
	@GameTest(template = EMPTY)
	public static void editingTheFloorRenamesTheButtonsId(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		EvButtonModifyPacket p = packet(cabin, 3);
		p.localTarget = 0;
		p.localProperty = 0;
		p.localFloor = 12;
		deliver(p, player);

		FloorButton button = (FloorButton) cabin.buttons.get(0);
		helper.assertTrue(button.floor == 12 && button.id.equals("floor12"),
				"floor is " + button.floor + " and id " + button.id);

		p = packet(cabin, 3);
		p.localTarget = 0;
		p.localProperty = 0;
		p.localFloor = 200;
		deliver(p, player);

		helper.assertTrue(button.floor == -56 && button.id.equals("floor-56"),
				"the floor is one signed byte, so 200 arrives as -56; got " + button.floor + " " + button.id);
		cabin.discard();
		helper.succeed();
	}

	/** Mode 3, property 2: the label. */
	@GameTest(template = EMPTY)
	public static void editingTheLabelChangesOnlyTheLabel(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		EvButtonModifyPacket p = packet(cabin, 3);
		p.localTarget = 1;
		p.localProperty = 2;
		p.localLabel = "L2";
		deliver(p, player);

		FloorButton button = (FloorButton) cabin.buttons.get(1);
		helper.assertTrue(button.label.equals("L2"), "label is " + button.label);
		helper.assertTrue(button.floor == 2 && button.id.equals("floor2"), "the floor and id changed too");
		cabin.discard();
		helper.succeed();
	}

	/**
	 * Mode 3, property 1: the indicator override. Text equal to the floor's own number removes
	 * the override; anything else stores it.
	 */
	@GameTest(template = EMPTY)
	public static void theIndicatorStoresAnythingButTheFloorsOwnNumber(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		indicator(cabin, player, 1, "B");
		helper.assertTrue("B".equals(cabin.specialDisplayFloors.get(2)),
				"floor 2 should show B, got " + cabin.specialDisplayFloors);

		indicator(cabin, player, 1, "2");
		helper.assertTrue(!cabin.specialDisplayFloors.containsKey(2),
				"text equal to the floor number removes the override, got " + cabin.specialDisplayFloors);
		cabin.discard();
		helper.succeed();
	}

	/**
	 * <b>Upstream's {@code s == ""}, pinned.</b> Clearing the indicator field is meant to remove
	 * the override and does not: the comparison is by reference and never holds for a decoded
	 * string, so an empty indicator is stored. Flagged in D-1077, not fixed (D-021).
	 */
	@GameTest(template = EMPTY)
	public static void clearingTheIndicatorStoresAnEmptyOneAsUpstreamDoes(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		indicator(cabin, player, 1, "B");
		indicator(cabin, player, 1, "");

		helper.assertTrue("".equals(cabin.specialDisplayFloors.get(2)),
				"an empty indicator should be stored (upstream's reference comparison), got "
						+ cabin.specialDisplayFloors);
		cabin.discard();
		helper.succeed();
	}

	/** Mode 3 on a button that is not a floor button reads its payload and does nothing. */
	@GameTest(template = EMPTY)
	public static void editingAnOpenButtonDoesNothing(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ServerPlayer player = mockPlayer(helper);

		indicator(cabin, player, 3, "Z");

		helper.assertTrue(cabin.specialDisplayFloors.isEmpty(), "a non-floor button took an indicator");
		helper.assertTrue(cabin.buttons.size() == 4, "the list changed");
		cabin.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ the openers

	/**
	 * A screwdriver on the cabin opens the menu on the server; anything else does not - but the
	 * click is consumed either way, because upstream's {@code processInitialInteract} returns
	 * true unconditionally.
	 */
	@GameTest(template = EMPTY)
	public static void aScrewdriverOpensTheCabinMenu(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ItemStack tool = new ItemStack(Items.STICK);

		ServerPlayer plain = mockPlayer(helper);
		plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		InteractionResult[] consumed = new InteractionResult[1];
		boolean opened = reachedSending(() -> consumed[0] = cabin.interact(plain, InteractionHand.MAIN_HAND),
				"neoforge:advanced_open_screen");
		helper.assertTrue(!opened, "a stick that is not a screwdriver tried to open a menu");
		helper.assertTrue(consumed[0] != null && consumed[0].consumesAction(),
				"a click with no screwdriver is still consumed, got " + consumed[0]);

		ServerPlayer player = mockPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		boolean[] reached = new boolean[1];
		withScrewdriver(tool, () -> reached[0] = reachedSending(
				() -> cabin.interact(player, InteractionHand.MAIN_HAND), "neoforge:advanced_open_screen"));
		helper.assertTrue(reached[0], "a screwdriver should open the cabin's menu, which sends the screen payload");

		// The borrowed registration must be gone, or every stick in the run is a screwdriver.
		ServerPlayer after = mockPlayer(helper);
		after.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(!reachedSending(() -> cabin.interact(after, InteractionHand.MAIN_HAND),
				"neoforge:advanced_open_screen"), "the test's screwdriver registration leaked past withScrewdriver");

		cabin.discard();
		helper.succeed();
	}

	/** An empty hand is consumed too, and never reaches the tool lookup. */
	@GameTest(template = EMPTY)
	public static void anEmptyHandIsConsumedAndOpensNothing(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);

		InteractionResult result = cabin.interact(player, InteractionHand.MAIN_HAND);

		helper.assertTrue(result.consumesAction(), "an empty-handed click is consumed, got " + result);
		helper.assertTrue(!(player.containerMenu instanceof EvCabinMenu), "an empty hand opened the menu");
		cabin.discard();
		helper.succeed();
	}

	/**
	 * A screwdriver on any part of a landing finds its core and answers true; one that is not a
	 * screwdriver, or a click on a block that is not part of a landing, answers false.
	 */
	@GameTest(template = EMPTY)
	public static void aScrewdriverOnALandingFindsItsCore(GameTestHelper helper) {

		BlockPos core = new BlockPos(2, 1, 2);
		helper.setBlock(core, LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		helper.setBlock(core.above(), LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.DUMMY)
				.setValue(BlockDummyable.DIR, Direction.UP));

		EvFloor floor = (EvFloor) LeafiaBlocks.elevator_s6floor.get();
		ServerPlayer player = mockPlayer(helper);
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(core);

		helper.assertTrue(reachedSending(
				() -> floor.onScrew(level, player, abs, Direction.NORTH, Vec3.ZERO, ToolType.SCREWDRIVER),
				"leafia:container"), "a screwdriver on the core should send the open-the-screen packet");
		helper.assertTrue(reachedSending(
				() -> floor.onScrew(level, player, abs.above(), Direction.NORTH, Vec3.ZERO, ToolType.SCREWDRIVER),
				"leafia:container"), "and on a dummy above it - findCore walks back");
		helper.assertTrue(!floor.onScrew(level, player, abs, Direction.NORTH, Vec3.ZERO, ToolType.WRENCH),
				"a wrench is not a screwdriver");
		helper.assertTrue(!floor.onScrew(level, player, abs.east(5), Direction.NORTH, Vec3.ZERO, ToolType.SCREWDRIVER),
				"a position that is no part of the landing has no core");
		helper.succeed();
	}

	/**
	 * What the floor screen sends is stored by the server and re-broadcast: {@code idFloor} is
	 * key 0, and a client that sends it sets the landing's floor number.
	 */
	@GameTest(template = EMPTY)
	public static void theFloorScreensSaveReachesTheLanding(GameTestHelper helper) {

		BlockPos core = new BlockPos(2, 1, 2);
		helper.setBlock(core, LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));

		EvFloorTE te = (EvFloorTE) helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(te != null, "the core has no block entity");
		helper.assertTrue(te.floor == 1, "a new landing is floor 1, got " + te.floor);

		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(core));
		player.setPos(at.x, at.y, at.z);
		te.editors.add(player.getUUID()); // what EvFloorTE.openGui does when the screwdriver opens the screen

		te.onReceivePacketServer((byte) 0, 17, player);
		helper.assertTrue(te.floor == 17, "key 0 should set the floor, got " + te.floor);

		te.onReceivePacketServer((byte) 31, 99, player);
		helper.assertTrue(te.floor == 17, "key 31 is the open-the-screen signal and is for clients only");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * A server player, taken back off the player list.
	 *
	 * <p>{@code makeMockServerPlayerInLevel} logs the player in for real, on a connection that
	 * negotiated no channels. The addon broadcasts {@code leafia:custom} to every player on its
	 * wind tick, and a connection that never registered it makes NeoForge throw
	 * "Payload leafia:custom may not be sent to the client" out of the <b>server tick loop</b> -
	 * which crashes the whole GameTest server rather than failing one test. So the player is
	 * removed from {@code PlayerList.players}, the list every broadcast walks, and stays in the
	 * level, which is all these tests need of it.
	 */
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			// getPlayers() is a read-only view; the list it wraps is the field.
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static ElevatorEntity cabin(GameTestHelper helper) {
		ElevatorEntity cabin = (ElevatorEntity) LeafiaEntities.ELEVATOR.get().create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		cabin.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(cabin);
		return cabin;
	}

	/** Four buttons: floors 1 and 2 and 3, and an open button. */
	private static ElevatorEntity cabinWithButtons(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		cabin.buttons.clear();
		cabin.buttons.add(new FloorButton(cabin, 1, "1", -2, 14));
		cabin.buttons.add(new FloorButton(cabin, 2, "2", 1, 14));
		cabin.buttons.add(new FloorButton(cabin, 3, "3", -2, 17));
		cabin.buttons.add(new OpenButton(cabin, 1, 9));
		return cabin;
	}

	private static EvButtonModifyPacket packet(ElevatorEntity cabin, int mode) {
		EvButtonModifyPacket p = new EvButtonModifyPacket();
		p.localEntity = cabin;
		p.localMode = mode;
		return p;
	}

	private static void indicator(ElevatorEntity cabin, ServerPlayer player, int target, String text) {
		EvButtonModifyPacket p = packet(cabin, 3);
		p.localTarget = target;
		p.localProperty = 1;
		p.localLabel = text;
		deliver(p, player);
	}

	private static void assertBits(GameTestHelper helper, String what, EvButtonModifyPacket p, int expected) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(p);
		helper.assertTrue(packet != null, what + " packet was dropped");
		helper.assertTrue(packet.bitLength() == expected,
				what + " is " + packet.bitLength() + " bits on the wire, expected " + expected);
	}

	/** Sends a packet through the real encoder and decoder, as the network thread would, then runs its callback. */
	private static void deliver(EvButtonModifyPacket p, ServerPlayer player) {
		// D-1094: the editor's packets are only heard from a player with that cabin's screen open.
		player.setPos(p.localEntity.getX(), p.localEntity.getY(), p.localEntity.getZ());
		if(!(player.containerMenu instanceof EvCabinMenu open && open.entity == p.localEntity))
			player.containerMenu = new EvCabinMenu(1, player.getInventory(), p.localEntity);
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(p);
		var opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		if(callback != null) callback.accept(context(player));
	}

	/**
	 * Runs a call and reports whether it got as far as sending the named payload to a mock
	 * connection, which refuses every payload it did not negotiate. See the class note.
	 */
	private static boolean reachedSending(Runnable call, String payload) {
		try {
			call.run();
			return false;
		} catch(UnsupportedOperationException e) {
			return e.getMessage() != null && e.getMessage().contains(payload);
		}
	}

	private static IPayloadContext context(Player player) {
		return new IPayloadContext() {
			@Override public net.minecraft.network.protocol.common.ClientCommonPacketListener listener() {
				throw new UnsupportedOperationException();
			}
			@Override public Player player() { return player; }
			@Override public CompletableFuture<Void> enqueueWork(Runnable task) {
				task.run();
				return CompletableFuture.completedFuture(null);
			}
			@Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
				return CompletableFuture.completedFuture(task.get());
			}
			@Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
			@Override public void handle(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
				throw new UnsupportedOperationException();
			}
			@Override public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Runs a body with a stack registered as a screwdriver, then takes it out and rebuilds the
	 * lookup - see the class note for why the rebuild is the half that matters.
	 */
	private static void withScrewdriver(ItemStack tool, Runnable body) {
		ToolType.SCREWDRIVER.register(tool);
		try {
			body.run();
		} finally {
			ToolType.SCREWDRIVER.stacksForDisplay.remove(tool);
			ItemStack scratch = new ItemStack(Items.BARRIER);
			ToolType.SCREWDRIVER.register(scratch);
			ToolType.SCREWDRIVER.stacksForDisplay.remove(scratch);
		}
	}
}
