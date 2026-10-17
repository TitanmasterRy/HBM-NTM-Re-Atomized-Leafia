package com.leafia.gametest;

import java.util.List;

import com.hbm.gametest.SpawnWindow;
import com.hbm.items.ModItems;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEditMenu;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlGeometry;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import com.hbm.blocks.BlockDummyable;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The control panel editor's menu and what it gates (CCP-EDITOR, D-3066): the slots and their rules, the menu as the session
 * the server's trust check looks for, and hostile saves sent through the <i>real</i> menu and the base mod's packet handler. The
 * screen and its pages are client code and are checked in a running client; their arithmetic that has no client type in it
 * ({@link ControlGeometry}, {@link ControlLinks}) is pinned here.
 *
 * <p>Every mock player is detached by {@link LeafiaControlPanelKit#mockPlayer}, stood near its panel, and the one entity a test
 * spawns is discarded.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelEditorTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	private static ControlEditMenu open(ServerPlayer player, ControlPanelTE te) {
		ControlEditMenu menu = new ControlEditMenu(77, player.getInventory(), te);
		player.containerMenu = menu;
		return menu;
	}

	private static ItemStack detonator() {
		return new ItemStack(ModItems.detonator_multi.get());
	}

	// ------------------------------------------------------------------ the menu

	@GameTest(template = EMPTY)
	public static void theMenuIsTheLinkerSlotAndThePlayersInventory(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		ControlEditMenu menu = open(player, te);

		helper.assertTrue(menu.getType() == LeafiaMenuTypes.CONTROL_EDIT.get(), "it is the registered menu type");
		helper.assertTrue(menu.slots.size() == ControlEditMenu.SLOT_COUNT && ControlEditMenu.SLOT_COUNT == 37, "one linker slot, 27 inventory slots, 9 hotbar slots");
		Slot linker = menu.slots.get(0);
		helper.assertTrue(linker.x == 8 && linker.y == 13 && linker == menu.input, "the linker slot is at CE's (8, 13)");
		helper.assertTrue(menu.slots.get(1).x == 73 && menu.slots.get(1).y == 162, "the inventory starts at (73, 162)");
		helper.assertTrue(menu.slots.get(9).x == 73 + 8 * 18 && menu.slots.get(10).x == 73 && menu.slots.get(10).y == 162 + 18, "nine to a row, rows 18 apart");
		helper.assertTrue(menu.slots.get(28).x == 73 && menu.slots.get(28).y == 220 && menu.slots.get(36).x == 73 + 8 * 18, "the hotbar is at y 220");
		helper.assertTrue(menu.slots.get(1).getContainerSlot() == 9 && menu.slots.get(27).getContainerSlot() == 35 && menu.slots.get(28).getContainerSlot() == 0,
				"the inventory slots are the player's 9..35 and then the hotbar 0..8");
		helper.assertTrue(menu.slots.get(1).container == player.getInventory(), "and they are the player's own");
		helper.assertTrue(menu.invSlots.size() == 36 && menu.editedPanel() == te, "36 switchable slots, and the menu names its panel");
		helper.assertTrue(te.isEditedBy(player), "the panel sees the player as editing it: the menu is the session the server's check looks for");

		// The screen switches slots on and off; the server's menu has them all off until it does, and still works (the server does not ask).
		helper.assertTrue(!linker.isActive() && !menu.slots.get(1).isActive(), "off until a page switches them on");
		menu.input.enabled = true;
		helper.assertTrue(linker.isActive(), "and on when it does");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLinkerSlotTakesOnlyAMultiDetonatorOneAtATime(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		ControlEditMenu menu = open(player, te);
		Slot linker = menu.input;

		helper.assertTrue(linker.mayPlace(detonator()), "a multi-detonator may be placed");
		helper.assertTrue(!linker.mayPlace(new ItemStack(Items.APPLE)) && !linker.mayPlace(new ItemStack(Items.STONE, 3))
				&& !linker.mayPlace(new ItemStack(ModItems.detonator.get())), "nothing else, the single detonator included");
		helper.assertTrue(linker.getMaxStackSize() == 1, "one at a time");

		// Through the real click path, as a client's click packet would drive it.
		menu.setCarried(new ItemStack(Items.APPLE));
		menu.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty() && menu.getCarried().is(Items.APPLE), "clicking an apple onto the slot does nothing");
		menu.setCarried(detonator());
		menu.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(te.inventory.getStackInSlot(0).getItem() == ModItems.detonator_multi.get() && menu.getCarried().isEmpty(),
				"clicking a multi-detonator onto it puts it in the panel's linker slot");
		menu.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty() && menu.getCarried().getItem() == ModItems.detonator_multi.get(), "and clicking again takes it back");
		menu.setCarried(ItemStack.EMPTY);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void shiftClickMovesTheLinkerInAndOutAndNothingElse(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		ControlEditMenu menu = open(player, te);

		player.getInventory().setItem(10, detonator()); // menu slot 2
		player.getInventory().setItem(11, new ItemStack(Items.APPLE)); // menu slot 3
		ItemStack moved = menu.quickMoveStack(player, 2);
		helper.assertTrue(moved.getItem() == ModItems.detonator_multi.get() && te.inventory.getStackInSlot(0).getItem() == ModItems.detonator_multi.get()
				&& player.getInventory().getItem(10).isEmpty(), "shift-clicking a multi-detonator in the inventory fills the linker slot");
		helper.assertTrue(menu.quickMoveStack(player, 3).isEmpty() && player.getInventory().getItem(11).is(Items.APPLE), "an apple stays where it was");
		player.getInventory().setItem(12, detonator());
		helper.assertTrue(menu.quickMoveStack(player, 4).isEmpty() && player.getInventory().getItem(12).getItem() == ModItems.detonator_multi.get(),
				"with the slot taken, a second one does not move");

		ItemStack out = menu.quickMoveStack(player, 0);
		helper.assertTrue(out.getItem() == ModItems.detonator_multi.get() && te.inventory.getStackInSlot(0).isEmpty(), "shift-clicking the linker slot moves it out");
		long count = 0;
		for(int i = 0; i < player.getInventory().getContainerSize(); i++)
			if(player.getInventory().getItem(i).getItem() == ModItems.detonator_multi.get())
				count++;
		helper.assertTrue(count == 2, "and back to the inventory: the two detonators are both there, got " + count);
		helper.succeed();
	}

	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void closingTheEditorGivesTheLinkerBackOrDropsIt(GameTestHelper helper) {
		SpawnWindow.run(helper, () -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			ControlEditMenu menu = open(player, te);

			te.inventory.setStackInSlot(0, detonator());
			menu.removed(player);
			helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "the slot is empty after closing");
			helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.detonator_multi.get())), "the detonator is in the player's inventory");

			// A full inventory drops it at the player's feet instead of destroying it. (A mock player is in creative mode, where vanilla's
			// Inventory.add swallows what does not fit; the rule under test is a survival player's.)
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			for(int i = 0; i < player.getInventory().items.size(); i++)
				player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
			te.inventory.setStackInSlot(0, detonator());
			menu.removed(player);
			AABB around = new AABB(player.blockPosition()).inflate(3);
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().getItem() == ModItems.detonator_multi.get());
			boolean found = dropped.size() == 1;
			for(ItemEntity e : dropped)
				e.discard();
			helper.assertTrue(found, "with no room, the detonator is dropped, once; found " + dropped.size());
			helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "and the slot is empty");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void theMenuStaysOpenOnlyWhileThePanelIsThereAndInReach(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
		ControlEditMenu menu = open(player, te);

		helper.assertTrue(menu.stillValid(player), "valid beside the panel");
		LeafiaControlPanelKit.stand(player, te, 60);
		helper.assertTrue(!menu.stillValid(player), "not valid sixty blocks away (CE's was always true)");
		LeafiaControlPanelKit.stand(player, te, 3);
		helper.assertTrue(menu.stillValid(player), "valid again in reach");
		helper.setBlock(AT, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		helper.assertTrue(!menu.stillValid(player), "and not once the panel is broken");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theClientConstructorFindsThePanelFromItsPosition(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		buf.writeBlockPos(te.getBlockPos());
		ControlEditMenu menu = new ControlEditMenu(5, player.getInventory(), buf);
		helper.assertTrue(menu.control == te && menu.containerId == 5, "built from the payload, it edits the panel at that position");

		RegistryFriendlyByteBuf wrong = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		wrong.writeBlockPos(helper.absolutePos(AT.east(5)));
		boolean threw = false;
		try {
			new ControlEditMenu(6, player.getInventory(), wrong);
		} catch(IllegalStateException e) {
			threw = true;
		}
		helper.assertTrue(threw, "a position with no panel is not a menu");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theScrewdriverOpenerIsRegisteredAndWantsReach(GameTestHelper helper) {
		helper.assertTrue(ControlPanelTE.editorOpener != null, "the mod's setup registers the editor as the opener");
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
		LeafiaControlPanelKit.stand(player, te, 60);
		ControlEditMenu.open(player, te);
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "a player out of reach is not given the editor");
		helper.succeed();
	}

	// ------------------------------------------------------------------ hostile saves through the real menu

	private static final class Rig {
		final ControlPanelTE te;
		final ServerPlayer player;
		final BlockPos door;

		Rig(GameTestHelper helper) {
			te = LeafiaControlPanelKit.place(helper, AT);
			player = LeafiaControlPanelKit.editor(helper, te);
			open(player, te);
			door = helper.absolutePos(AT.east(4));
		}

		/** The edit as the editor sends it, with the one control linked to {@code link}. */
		CompoundTag edit(BlockPos link) {
			return LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 2, 3);
				c.taggedLinks.put("door", link);
			});
		}

		void send(GameTestHelper helper, CompoundTag data) {
			NBTControlPacket.handle(new NBTControlPacket(te.getBlockPos(), data), LeafiaControlPanelKit.context(player));
		}
	}

	private static void assertRefused(GameTestHelper helper, Rig rig, CompoundTag edit, String why) {
		CompoundTag before = rig.te.panel.writeToNBT(new CompoundTag());
		ControlPanel panelBefore = rig.te.panel;
		rig.send(helper, edit);
		helper.assertTrue(rig.te.panel == panelBefore && rig.te.panel.writeToNBT(new CompoundTag()).equals(before), "refused and untouched: " + why);
	}

	@GameTest(template = EMPTY)
	public static void aSavedEditThroughTheRealMenuIsAppliedWhenTheLinkerIsAnywhereInTheInventory(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Rig rig = new Rig(helper);
			// The editor hands the linker back to the inventory the moment it has read it: it is in a slot, not a hand.
			rig.player.getInventory().setItem(20, LeafiaControlPanelKit.linker(rig.door));
			LeafiaControlPanelKit.hold(rig.player, ItemStack.EMPTY);
			rig.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);

			rig.send(helper, rig.edit(rig.door));
			helper.assertTrue(rig.te.panel.controls.size() == 1 && rig.te.panel.controls.get(0).taggedLinks.get("door").equals(rig.door),
					"the panel took the edit and its link");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSavedEditWithALinkNobodyPresentedIsRefusedThroughTheMenu(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Rig rig = new Rig(helper);
			assertRefused(helper, rig, rig.edit(rig.door), "no linker names the door");

			rig.player.getInventory().setItem(20, LeafiaControlPanelKit.linker(rig.door.north(9)));
			assertRefused(helper, rig, rig.edit(rig.door), "a linker that names another position");

			// An item that merely carries a detonator's tag is not a detonator.
			ItemStack fake = LeafiaControlPanelKit.linker(rig.door);
			ItemStack apple = new ItemStack(Items.APPLE);
			apple.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, fake.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
			rig.player.getInventory().setItem(21, apple);
			assertRefused(helper, rig, rig.edit(rig.door), "an apple wearing a detonator's tag");

			// The slot of the menu counts, as the editor's own linker slot.
			rig.te.inventory.setStackInSlot(0, LeafiaControlPanelKit.linker(rig.door));
			rig.send(helper, rig.edit(rig.door));
			helper.assertTrue(rig.te.panel.controls.size() == 1, "a detonator in the linker slot presents it");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSavedEditNeedsThisPanelsEditorOpenAndStillValid(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Rig rig = new Rig(helper);
			ControlPanelTE other = LeafiaControlPanelKit.place(helper, AT.east(8));
			rig.player.getInventory().setItem(20, LeafiaControlPanelKit.linker(rig.door));
			CompoundTag edit = rig.edit(rig.door);

			rig.player.containerMenu = new ControlEditMenu(78, rig.player.getInventory(), other);
			assertRefused(helper, rig, edit, "the open editor is another panel's");

			rig.player.containerMenu = rig.player.inventoryMenu;
			assertRefused(helper, rig, edit, "no editor open");

			ControlEditMenu closed = open(rig.player, rig.te);
			closed.removed(rig.player);
			rig.player.containerMenu = rig.player.inventoryMenu;
			assertRefused(helper, rig, edit, "the editor was closed");

			open(rig.player, rig.te);
			LeafiaControlPanelKit.stand(rig.player, rig.te, 60);
			assertRefused(helper, rig, edit, "the player walked away with the editor open");
			LeafiaControlPanelKit.stand(rig.player, rig.te, 2);
			rig.send(helper, edit);
			helper.assertTrue(rig.te.panel.controls.size() == 1, "and the same edit goes through from the right place");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void hostileContentSentFromAnOpenEditorIsStillRefused(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Rig rig = new Rig(helper);
			BlockPos far = rig.door.offset(40_000, 0, 0);
			rig.player.getInventory().setItem(20, LeafiaControlPanelKit.linker(far, rig.door));
			assertRefused(helper, rig, rig.edit(far), "a presented link forty thousand blocks away");

			CompoundTag many = LeafiaControlPanelKit.edit(rig.te, panel -> {
				for(int i = 0; i <= ControlPanelGuard.MAX_CONTROLS; i++)
					LeafiaControlPanelKit.control(panel, i % 10, i / 10);
			});
			assertRefused(helper, rig, many, "more controls than the editor can place");

			CompoundTag bad = LeafiaControlPanelKit.edit(rig.te, panel -> {
				panel.height = 5;
			});
			assertRefused(helper, rig, bad, "a slab the resize page cannot make");

			// A packet carrying a click instead of an edit is not an edit either.
			CompoundTag click = new CompoundTag();
			click.putInt("click_control", 0);
			rig.send(helper, click);
			helper.assertTrue(rig.te.panel.controls.isEmpty(), "and nothing was made by any of it");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aLinkerNamingPartOfAMultiblockPresentsItsCore(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			Rig rig = new Rig(helper);
			BlockPos core = new BlockPos(8, 1, 8);
			helper.setBlock(core, LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
			helper.setBlock(core.above(), LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.DUMMY).setValue(BlockDummyable.DIR, Direction.UP));
			BlockPos absCore = helper.absolutePos(core);

			helper.assertTrue(ControlLinks.resolveCore(helper.getLevel(), absCore.above()).equals(absCore), "a dummy resolves to its core");
			helper.assertTrue(ControlLinks.resolveCore(helper.getLevel(), absCore).equals(absCore), "a core to itself");
			BlockPos plain = helper.absolutePos(AT.east(2));
			helper.assertTrue(ControlLinks.resolveCore(helper.getLevel(), plain).equals(plain), "a block that is no multiblock to itself");
			BlockPos unloaded = new BlockPos(3_000_000, 70, 3_000_000);
			helper.assertTrue(ControlLinks.resolveCore(helper.getLevel(), unloaded).equals(unloaded) && ControlLinks.controllableAt(helper.getLevel(), unloaded) == null,
					"an unloaded position is left alone and answers nothing");
			helper.assertTrue(ControlLinks.controllableAt(helper.getLevel(), rig.te.getBlockPos()) == rig.te, "a panel is a controllable");
			helper.assertTrue(ControlLinks.controllableAt(helper.getLevel(), plain) == null, "air is not");

			// The detonator was clicked on the dummy; the editor links the core; the server accepts the core.
			rig.player.getInventory().setItem(20, LeafiaControlPanelKit.linker(absCore.above()));
			assertRefused(helper, rig, rig.edit(absCore.north(3)), "a block next to it that no linker named");
			rig.send(helper, rig.edit(absCore));
			helper.assertTrue(rig.te.panel.controls.size() == 1 && rig.te.panel.controls.get(0).taggedLinks.get("door").equals(absCore),
					"the core was presented by a linker that named only its dummy");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ pure arithmetic the editor leans on

	@GameTest(template = EMPTY)
	public static void theEditorsBoxArithmeticIsStrictWhereCeWasStrict(GameTestHelper helper) {
		float[] a = {0, 0, 1, 1};
		helper.assertTrue(ControlGeometry.boxesOverlap(a, new float[]{0.5F, 0.5F, 1.5F, 1.5F}), "boxes sharing area overlap");
		helper.assertTrue(!ControlGeometry.boxesOverlap(a, new float[]{1, 0, 2, 1}) && !ControlGeometry.boxesOverlap(a, new float[]{0, 1, 1, 2}),
				"boxes sharing only an edge do not (controls may touch)");
		helper.assertTrue(ControlGeometry.boxContainsOther(new float[]{0, 0, 10, 10}, new float[]{0, 0, 1, 1}) && ControlGeometry.boxContainsOther(new float[]{0, 0, 10, 10}, new float[]{9, 9, 10, 10}),
				"a box touching the edge of the slab is inside it");
		helper.assertTrue(!ControlGeometry.boxContainsOther(new float[]{0, 0, 10, 10}, new float[]{9.5F, 9, 10.5F, 10}), "one over the edge is not");
		helper.assertTrue(ControlGeometry.intersects2DBox(0.5F, 0.5F, a) && !ControlGeometry.intersects2DBox(1, 0.5F, a) && !ControlGeometry.intersects2DBox(0, 0.5F, a),
				"a point on the edge is not in the box");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void thePanelsFootprintOnTheGridIsCesFormula(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		// The desk block: height 0.25, angle 20 degrees, insets a 0, b 0, c 0.25, d 0. Worked by hand: the slab is 1 wide and
		// 0.75 deep; the grid's box is ten wide and ten over cos 20 tall (10.6418), centred, and the slab runs 7.9813 of it from the top.
		float[] box = te.getBox();
		helper.assertTrue(Math.abs(box[0] + 5) < 1E-4F && Math.abs(box[2] - 5) < 1E-4F, "ten wide, centred, got " + box[0] + " to " + box[2]);
		helper.assertTrue(Math.abs(box[1] + 5.3209F) < 1E-3F && Math.abs(box[3] - 2.6604F) < 1E-3F, "and from -5.3209 to 2.6604, got " + box[1] + " to " + box[3]);
		helper.assertTrue(java.util.Arrays.equals(box, ControlGeometry.panelBox(te.panel)), "the block entity answers with the same arithmetic");

		te.panel.d_off = 0.2F;
		te.panel.b_off = 0.1F;
		te.panel.a_off = 0.1F;
		float[] shrunk = ControlGeometry.panelBox(te.panel);
		helper.assertTrue(Math.abs(shrunk[0] + 3) < 1E-4F && Math.abs(shrunk[2] - 4) < 1E-4F, "insets d and b trim the left and right: -3 to 4, got " + shrunk[0] + " to " + shrunk[2]);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theReceivableEventsAreInTheOrderTheDefaultGraphsIndexThem(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		Control button = ControlRegistry.getNew("button_push", te.panel);
		List<ControlEvent> events = ControlLinks.accumulateReceiveEvents(button, List.of());
		helper.assertTrue(events.get(0).name.equals("ctrl_press"), "the control's own out-event is first (three default graphs index it as 0), got " + events.get(0).name);
		for(String in : button.getInEvents())
			helper.assertTrue(events.stream().anyMatch(e -> e.name.equals(in)), "then its in-events, including " + in);
		helper.assertTrue(events.stream().map(e -> e.name).distinct().count() == events.size(), "without repeats");

		IControllable machine = new IControllable() {
			@Override public List<String> getOutEvents() { return List.of("door_open_state", "no_such_event", "ctrl_press"); }
			@Override public BlockPos getControlPos() { return BlockPos.ZERO; }
			@Override public net.minecraft.world.level.Level getControlWorld() { return null; }
		};
		List<ControlEvent> withMachine = ControlLinks.accumulateReceiveEvents(button, List.of(machine));
		helper.assertTrue(withMachine.size() == events.size() + 1 && withMachine.get(withMachine.size() - 1).name.equals("door_open_state"),
				"a linked machine adds its events after, skipping one already there and one nobody registered");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the owner's NodeSetVar decision

	@GameTest(template = EMPTY)
	public static void aSetVariableNodeKeepsItsSavedLiteralAndFallsBackToTheVariable(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		TestControl c = new TestControl(te.panel);
		c.vars.put("x", new DataValueFloat(7));
		NodeSystem sys = new NodeSystem(c);
		NodeSetVar node = new NodeSetVar(0, 0, c).setData("x", false);
		sys.addNode(node);
		node.inputs.get(0).setDefault(new DataValueFloat(3));
		CompoundTag saved = node.writeToNBT(new CompoundTag(), sys);

		NodeSetVar back = new NodeSetVar(0, 0, c);
		back.readFromNBT(saved, sys);
		helper.assertTrue(back.inputs.get(0).defaultValue.getNumber() == 3, "the typed 3 survives a load even though the variable holds 7 (owner, 2026-10-16)");

		CompoundTag noDefault = saved.copy();
		noDefault.getCompound("in").getCompound("con0").remove("D");
		NodeSetVar fallback = new NodeSetVar(0, 0, c);
		fallback.readFromNBT(noDefault, sys);
		helper.assertTrue(fallback.inputs.get(0).defaultValue.getNumber() == 7, "with nothing saved, the variable's current value is the fallback, as before");
		helper.succeed();
	}
}
